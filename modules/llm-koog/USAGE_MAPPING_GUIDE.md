# Koog Usage 映射指南

最小文本流已经完成；当前阶段把 Koog `StreamFrame.End.metaInfo` 中可证明的
token 计数转换成 Harness `UsageChunk`，并在 terminal `FinishChunk` 之前发出。

## 字段边界

Koog 1.1.1 `ResponseMetaInfo` 提供：

- `totalTokensCount: Int?`
- `inputTokensCount: Int?`
- `outputTokensCount: Int?`
- `metadata: JsonObject?`

Harness `TokenUsage` 提供：

- `inputTokens: Long`
- `outputTokens: Long`
- `cacheReadTokens: Long?`
- `cacheWriteTokens: Long?`
- `reasoningTokens: Long?`

Harness 的各计数必须互不重叠。Koog 的通用字段没有说明 Provider 的 input 是否已经包含 cache，
也没有通用 cache/reasoning 字段。因此通用 Mapper 只能转换 input/output；其他字段保持 null。
具体 Provider 若在 metadata 中暴露 cache 或 reasoning，必须使用 Provider 专属 mapper，并按其协议
证明是否要从 input/output 中扣除，不能在通用层猜字段名。

`KoogUsageMapper` 是公开且可注入的边界。它同时收到 `KoogStreamContext`：Harness route、requested
model 与 Koog client provider 分开保存，足以让一个 executor 的多 Provider dispatcher 选择精确规则，
又不会暴露消息或凭据。

## 当前状态与契约

标准 usage contract、默认 mapper 主路径和 terminal/context integration 已启用并通过；不要重新添加
`@Ignore`，也不要恢复旧的“reported usage 必须显式失败”占位断言。

`KoogUsageStreamIntegrationContractTest` 和 `KoogAdapterStreamContextIntegrationContractTest` 应已在文本流
阶段随 terminal seam 一起启用；这里确认它们继续执行，不能只让纯 mapper 测试变绿。

## 通用字段（已完成）

`DefaultKoogUsageMapper.map(metaInfo, context)` 已按以下顺序处理：

1. total/input/output 全部为空：返回 null，表示 Provider 没有报告 usage。
2. input 或 output 只有一个存在：抛出 `KOOG_INVALID_USAGE`。
3. input/output 任一为负数：抛出 `KOOG_INVALID_USAGE`。
4. total 存在但为负数：抛出 `KOOG_INVALID_USAGE`。
5. total 存在但不等于 `input + output`：抛出 `KOOG_INVALID_USAGE`。
6. 返回 `TokenUsage(input.toLong(), output.toLong())`。

不要使用 total 推导缺失的 input 或 output；即使算术上可以相减，也无法证明 Provider 的 total 是否
还包含其他类别。

## End 接线（已完成）

`KoogStreamMapper` 的 `StreamFrame.End` 分支应已调用
`terminalMapper.map(frame, state, context)`。`KoogStreamTerminalMapper` 负责：

1. 先调用 `state.finish()`，确认没有未关闭 block；
2. 调用 `usageMapper.map(frame.metaInfo, context)`；
3. 调用 finish mapper；
4. 全部映射成功后返回可选 `UsageChunk` 和唯一 `FinishChunk` 的不可变列表。

通用 mapper 已替换原占位语义；不要把 terminal 编排重新搬回 End 分支。

输出顺序必须是：

```text
BlockEndChunk
UsageChunk       # 仅 Provider 报告 usage 时存在
FinishChunk      # 永远最后
```

## 第四步：Provider 专属计数

等具体 Executor Factory 存在后，再为该 Provider 增加独立 mapper 和测试。例如：

- Provider 的 input 包含 cache read：先验证协议，再从 input 中扣除 cacheReadTokens。
- Provider 报告 reasoning 是 output 子集：保留 output 总量，并额外填 reasoningTokens；不要重复计费。
- metadata 字段缺失或类型错误：使用稳定错误码失败，不能回退为零。

Provider 专属实现不能写回 `ResponseMetaInfo.metadata`，也不能把原始 usage 存进通用配置。
实现后放入对应 route 的 `KoogProviderSemantics.usageMapper`，并由对应 Provider Plugin 通过
`KoogLlmPlugin` 显式安装；默认通用 mapper 不会读取未经验证的 metadata 私有字段。

## 验收

```bash
./kotlin check -m llm-koog
./kotlin build
```

验收条件：

- 通用 usage contract 全部执行；
- usage stream integration contract 执行并收到准确的 `KoogStreamContext`；
- 完整 input/output 映射为 Long；
- 完全缺失返回 null；
- partial、negative、total 不一致使用 `KOOG_INVALID_USAGE`；
- usage 只出现一次且位于 finish 之前；
- 通用 mapper 不制造 cache/reasoning 数值。
