# Koog 文本流实现指南

本阶段实现 `TextDelta`、`TextComplete` 和 `End("stop")`。End 必须接入现有 terminal seam：完全
缺失的 usage 不产生 chunk，标准 input/output usage 产生 `UsageChunk`；reasoning、tool call 和其他 finish
reason 同样不能被静默忽略或猜测。缺失 index 由共享状态机按 Koog builder 的单一同类型 pending block
生命周期处理，不伪造 Provider index。

## 真实类型

Koog 1.1.1 的 `StreamFrame` 是 sealed interface：

| Koog frame | 关键字段 | 本阶段 Harness 输出 |
|---|---|---|
| `TextDelta` | `text: String`, `index: Int?` | 首次先发 `BlockStartChunk`，再发 `TextDeltaChunk` |
| `TextComplete` | `text: String`, `index: Int?` | `BlockEndChunk(TextBlock(frame.text))` |
| `End` | `finishReason: String?`, `metaInfo` | 可选 `UsageChunk`，随后 `FinishChunk(StopFinishReason)` |

`TextComplete.text` 是 Provider 给出的权威完整块；不要用累计 delta 重新拼成 block-end。
累计值只由 `KoogStreamState` 用于状态跟踪和后续诊断。

`KoogLlmAdapter` 会为每次调用传入 `KoogStreamContext`。其中 `provider/model` 是 Harness
路由身份，`koogProvider` 是已解析 `LLModel.provider.id`；上下文不含消息、工具参数或凭据。所有后续
Provider 语义 mapper 必须复用这一份上下文，不能再从 finish 字符串反推 Provider。

DSH 的流协议要求：

- block index 非负；
- delta 之前必须有同类型的 block-start；
- block-end 必须关闭已打开的同类型 block；
- 成功 finish 之前所有 block 必须关闭；
- 无任何内容块的成功终止使用核心 `EMPTY_RESPONSE` 失败；
- End 必须经过 `KoogStreamTerminalMapper`，Provider 已报告的 usage 不能被跳过；
- finish 是唯一终止 chunk，之后不能再输出。

## 当前状态

`KoogStreamMapper` 的 provider-neutral 文本路径已经接好，`KoogStreamMapperTest`、terminal/context
integration 和 cancellation contract 均为活动测试。不要重新添加 `@Ignore`。

运行：

```bash
./kotlin check -m llm-koog
```

`KoogStreamMapperTest` 中 12 条契约全部启用；文本 happy-path 产生
`BlockStart → TextDelta* → BlockEnd → Finish`；reasoning/tool frame 会到达各自已实现的 provider-neutral
mapper。若请求映射、Plugin、settings、finish mapper 或状态机测试失败，先不要扩展 Provider 私有语义。

## 第二步：为每次收集创建独立状态

`Flow` 是冷流，`KoogStreamState()` 必须在返回的 flow 内创建，不能作为
`KoogStreamMapper` 的属性，否则多次 collect 会共享 finished/block 状态。

建议使用 `transformWhile` 处理终止帧：它可以映射 `End` 后立即停止收集上游，避免成功
finish 后又因异常产生第二个 error finish。

当前控制流可概括如下；`mapTextDelta` / `mapTextComplete` 代表后两节列出的内联状态步骤：

```kotlin
flow {
    val state = KoogStreamState()
    emitAll(
        frames.transformWhile { frame ->
            when (frame) {
                is StreamFrame.TextDelta -> mapTextDelta(frame, state)

                is StreamFrame.TextComplete -> mapTextComplete(frame, state)

                is StreamFrame.End -> {
                    terminalMapper.map(frame, state, context).forEach { chunk ->
                        emit(chunk)
                    }
                    false
                }

                is StreamFrame.ReasoningDelta,
                is StreamFrame.ReasoningComplete -> {
                    reasoningMapper.map(frame, state, context).forEach { emit(it) }
                    true
                }

                is StreamFrame.ToolCallDelta,
                is StreamFrame.ToolCallComplete -> {
                    toolStreamMapper.map(frame, state, context).forEach { emit(it) }
                    true
                }
            }
        }
    )
    state.requireFinished()
}
```

不要在这里增加 `catch (Throwable)`；`LlmRuntime` 已负责把普通异常转成 error finish，并保持
`CancellationException` 原样传播。

## 第三步：实现 TextDelta

1. 将可空的 `frame.index` 原样传给共享状态机。
2. 调用 `state.open(frame.index, KoogStreamBlockType.TEXT)`。显式非负 index 按 Provider block 关联；空 index
   只复用当前尚未完成的隐式 text block，完成后再次出现空 index 会创建下一个连续输出 block。
3. `block.startIfNeeded()` 为 true 时发出一次：

   ```kotlin
   BlockStartChunk(block.outputIndex, block.type.harnessType)
   ```

4. `block.append(frame.text)`。
5. 发出 `TextDeltaChunk(block.outputIndex, frame.text)`。

必须使用 `block.outputIndex`，不要直接把 Koog provider index 暴露给 Harness。状态机会按首次发现
顺序分配连续输出 index，为后续交错 reasoning/tool block 留出一致规则。

## 第四步：实现 TextComplete

1. 用相同 provider index 模式和 `TEXT` 类型获取 block。缺失 index 时 complete 会关闭当前隐式 text
   block；若没有先行 delta，则创建 complete-only 隐式 block。
2. 如果这是首次看到该 block，也要先发 `BlockStartChunk`；完整帧可能没有先行 delta。
3. 调用 `block.complete()`。
4. 发出：

   ```kotlin
   BlockEndChunk(
       index = block.outputIndex,
       block = TextBlock(frame.text),
   )
   ```

不要把 `TextComplete.text` 再作为 delta 发出，否则客户端会看到重复文本。

## 第五步：实现 End

1. 调用已经提供的 `terminalMapper.map(frame, state, context)`。它会依次完成状态校验、usage mapping 和
   finish mapping，并且只有全部成功后才返回不可变 terminal chunk 列表。
2. 按列表顺序发出 chunk；usage 非空时顺序固定为 `UsageChunk → FinishChunk`，usage 缺失时只有 finish。
3. `transformWhile` 返回 false，停止收集上游。
4. `emitAll` 返回后调用 `state.requireFinished()`；若上游没有 `End` 就结束，必须以
   `KOOG_INVALID_STREAM_STATE` 失败，不能返回没有 finish 的半截流。

当前 `StopOnlyKoogFinishReasonMapper` 只支持 `"stop"`。`null`、`length`、`tool_calls` 都会以
`KOOG_UNSUPPORTED_FINISH_REASON` 失败；等各自语义和测试存在后再扩展。

`DefaultKoogUsageMapper` 现在支持完整的标准 input/output 计数；完全缺失时返回 null，部分、负数或与
total 不一致时以 `KOOG_INVALID_USAGE` 失败。Provider 私有 cache/reasoning metadata 仍不会被通用层猜测。

## 第六步：保持未支持字段显式失败

reasoning 的不可表示字段应抛出 `LlmException`，错误码使用 `KOOG_UNSUPPORTED_REASONING_CONTENT`；tool
frame 已由专属 mapper 处理。任何新增 frame/metadata 都不能用空分支、`else -> Unit` 或 `mapNotNull`
静默丢弃。

## 第七步：验证取消与提前停止

文本流 12 条契约通过后，以下 integration contract 的 `@Ignore` 已同时移除：

- `KoogUsageStreamIntegrationContractTest`：证明 terminal seam 收到上下文并保持 usage/finish 顺序；
- `KoogAdapterStreamContextIntegrationContractTest`：证明 Harness route 与 Koog client provider 没有混淆；
- `KoogCancellationIntegrationContractTest`：验证下面两种取消路径。

取消的两条契约要求：

- downstream `take(1)` 提前停止时，Provider frame flow 的 `finally` 立即执行；
- caller 取消 collection 时，同样终止 Provider flow；
- 两种控制流都不能进入 `KoogProviderFailureClassifier`，也不能生成 error finish。

不要捕获 Flow 内部用于 early-stop 的取消异常；`catch` 只能用于真正的上游 Provider/mapper 失败。

## 验收

回归时运行：

```bash
./kotlin check -m llm-koog
./kotlin build
```

验收条件：

- JVM、Android Debug、Android Release 的 `KoogStreamMapperTest` 12 条、terminal/context integration 2 条以及
  cancellation integration 2 条契约全部执行并通过；
- `TextDelta("hel") + TextDelta("lo")` 只产生一次 block-start；
- `TextComplete("hello")` 产生权威 block-end；
- `End("stop")` 产生且只产生一个 finish；
- 空 index 按同类型隐式生命周期稳定映射，连续隐式 block 保持首次发现顺序；
- 负 index、未完成 block 中途切换显式/隐式 index、重复 complete、带未关闭 block 的 finish 使用稳定错误码；
- Provider 没有发送 `End` 就结束时使用稳定错误码；
- 零内容成功响应使用 `EMPTY_RESPONSE`；提前停止/取消终止上游且不被分类为 Provider 错误；
- reasoning/tool/未知 finish reason 不会被静默吞掉。
