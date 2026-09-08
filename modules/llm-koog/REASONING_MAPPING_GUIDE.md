# Koog Reasoning 映射指南

最小文本流已完成。provider-neutral 默认 mapper 仍只映射可无损表达为 Harness
`ReasoningBlock(text)` 的纯文本 reasoning；OpenAI Responses route 另有专属 mapper，把
`id/summary/encrypted` 放入版本化 replay state，不把它们混入 durable 正文。

## 类型差异

Koog 1.1.1：

```text
ReasoningDelta
├── id: String?
├── text: String?
├── summary: String?
└── index: Int?

ReasoningComplete
├── id: String?
├── content: List<String>
├── summary: List<String>?
├── encrypted: String?
└── index: Int?
```

Harness 当前只有：

```text
ReasoningDeltaChunk(index, text)
ReasoningBlock(text)
```

因此默认 mapper 只接受：

- `id == null`
- delta 的 `text != null`
- `summary == null`
- complete 的 `encrypted == null`
- `index` 为空，或为非负显式 index

其他组合使用 `KOOG_UNSUPPORTED_REASONING_CONTENT`，不能把 summary 当正文，也不能丢弃
encrypted 或 reasoning id。OpenAI Responses mapper 对非空私有字段使用 replay codec；空 id、空 summary
和空 encrypted 仍显式拒绝。

## 当前状态与契约（已完成）

`KoogReasoningMapperContractTest`、`KoogReasoningMapperTest` 和
`KoogReasoningStreamIntegrationContractTest` 已启用并通过；不要重新添加 `@Ignore` 或旧的
`REASONING_MAPPING_NOT_IMPLEMENTED` 断言。契约包含 delta 顺序、complete-only、权威 complete content、
有损字段、错误 frame 和非法 index。

## ReasoningDelta（已完成）

1. 验证 frame 是 `ReasoningDelta`，且只包含可支持的纯文本字段。
2. 将可空 index 原样交给 `KoogStreamState.open`；负数和未完成 block 中途切换 index 模式由共享状态机拒绝。
3. 使用 `state.open(frame.index, KoogStreamBlockType.REASONING)`；空 index 复用当前隐式 reasoning block。
4. 首次 block 调用 `startIfNeeded()` 并输出 `BlockStartChunk(..., "reasoning")`。
5. `block.append(frame.text)`。
6. 输出 `ReasoningDeltaChunk(block.outputIndex, frame.text)`。

返回列表顺序必须是 start 在 delta 之前，后续 delta 不得重复 start。

## ReasoningComplete（已完成）

1. 先拒绝非空 id、summary 或 encrypted。
2. 用 `content.joinToString("")` 得到完整正文。Koog 自己的 `toStreamFrames()` 会把 content
   中每个字符串依次作为 delta，因此空分隔拼接与其流语义一致。
3. 获取相同 provider index 的 reasoning block。
4. complete-only 时先输出 block-start。
5. 调用 `block.complete()`。
6. 输出 `BlockEndChunk(block.outputIndex, ReasoningBlock(fullText))`。

complete content 是 Provider 的权威块，不要用累计 delta 替代，也不要把完整正文再次输出成 delta。

## StreamMapper 接线（已完成）

公开的 `KoogReasoningMapper.map(frame, state, context)` 会收到目标 `KoogStreamContext`。在
`KoogStreamMapper` 中将：

```text
ReasoningDelta
ReasoningComplete
```

交给 `reasoningMapper.map(frame, state, context)`，并按返回顺序逐个 emit。不要在主 Mapper 中复制
reasoning 状态逻辑；否则 tool/text/reasoning 会产生三套 index 与 block 生命周期实现。

integration contract 已证明它进入主流、复用统一 block 生命周期、关闭 block，并能到达 terminal finish。

## Provider-native replay

Harness 已有 `FinishChunk.replayState` → `BlockAssembler.replayState` →
`ModelMessageSource.replayState` 的私有重放通道，因此不一定要扩展通用 `ReasoningBlock`。
OpenAI Responses 已定义并测试版本化 replay codec：完成帧的完整 `summary` 会替换此前 delta summary，
避免重复；`ReasoningBlock.text` 始终来自 durable Harness content。codec 不保存伪造的 response id/model，
也不把 raw response 或正文复制进 state。其他 Provider 在建立自己的 codec 前，id、summary 和 encrypted
继续显式拒绝，不能只在 Adapter 内私藏或丢弃。

请求侧已经接入 `KoogReplayRestorer`，默认会 fail-loud；完整 writer/restore 顺序、source/target 规则和
安全矩阵见 [REPLAY_MAPPING_GUIDE.md](REPLAY_MAPPING_GUIDE.md)。

## 验收

```bash
./kotlin check -m llm-koog
./kotlin build
```

验收条件：

- reasoning contract 全部执行；
- reasoning stream integration contract 全部执行；
- delta 只启动一次 block；
- complete-only 仍产生 start/end；
- complete content 按原顺序无分隔拼接；
- id/summary/encrypted 显式失败；
- 空 index 按隐式 reasoning 生命周期映射；负 index 或中途切换显式/隐式 index 使用稳定状态错误；
- reasoning block 在成功 finish 前关闭。
