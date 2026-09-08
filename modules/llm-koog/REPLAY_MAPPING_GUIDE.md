# Koog Replay State 实现指南

Replay state 只保存 Provider-native、下一次请求确实需要恢复的私有元数据；Harness message content 始终是
正文、reasoning 和 tool-call 的 durable source of truth。它不是完整响应缓存，也不能包含凭据、原始 prompt
或无需重放的 usage。

## 当前 fail-loud 边界

- `FinishChunk.replayState` 会经 `BlockAssembler` 进入 `ModelMessageSource.replayState`；
- `DefaultKoogMessageMapper` 遇到非空 replay state 时调用 `KoogReplayRestorer`；
- 默认 `RejectingKoogReplayRestorer` 抛 `KOOG_REPLAY_MAPPING_NOT_IMPLEMENTED`，不会把历史当普通 Koog
  assistant 后静默丢 metadata；
- `KoogReplayContext` 同时携带历史 source provider/model 与本次 target route/model/Koog provider；
- `KoogProviderSemanticsRegistry` 按历史 source route 选择 restorer，而不是按本次 target route；
- source route 不属于当前 Koog Adapter 时，registry 只使用 durable provider-neutral content，把私有 state
  视为另一个 Adapter 的不透明数据。

通用 Koog 仍不会猜测 Provider-native state；没有专属 codec 的 route 继续使用默认 fail-loud restorer。
OpenAI Responses 已实现独立的版本化 writer/restorer：它只保存 `kind/version/provider/model/stopReason`、
与 durable blocks 对齐的类型，以及 Responses reasoning 的 `id/summary/encrypted`，不保存 response raw JSON、
response id/model 或正文副本。

## Koog 1.1.1 可见字段

Koog `Message.Assistant` 除 parts 外还包含：

- `finishReason: String?`
- `rawResponse: JsonObject?`
- `ResponseMetaInfo.modelId/metadata`

`MessagePart.Reasoning` 包含 `id/content/summary/encrypted`。流中对应字段来自
`ReasoningDelta/ReasoningComplete`；`End` 只携带 finish reason 与 response metadata。

这些字段不等于都应该持久化。尤其 `rawResponse` 可能重复正文、包含敏感 Provider 数据或体积很大；不能
把它整体塞进 replay state。先用选定 Provider 的请求代码证明下一次调用实际读取哪些字段，再设计最小投影。

## 第一步：定义版本化 Provider 投影

投影至少必须包含：

1. 唯一 `kind` 与整数 `version`；
2. 生成响应时的 Harness source provider/model；
3. 与 durable content 一一对应的 block 类型序列；
4. 每个 block 真正需要恢复的最小私有字段；
5. 若 Provider API 需要，保存 response id/model 或 API flavor，但不能保存 secret。

不要在通用模块规定 Provider payload 字段名。OpenAI Responses、Chat Completions、Anthropic 等客户端的
重放需要不同，应该各自拥有 codec 与 fixture。

## 第二步：在 stream 侧捕获并写入

writer 必须跟随 `KoogStreamState` 的 block 生命周期捕获 metadata：

- 只在成功 terminal frame 产生 replay state；error/aborted finish 不携带；
- block metadata 与最终 `BlockEndChunk` 的数量、顺序和类型一致；
- state 放入唯一 `FinishChunk.replayState`，不能另发隐藏 chunk；
- plain text/reasoning/tool 映射仍以 Provider complete frame 的 durable content 为准；
- 捕获过程不得改变 chunk 顺序或为了 replay 接受原本无法无损表示的字段。

Koog `End` 没有完整 assistant message，因此不能只在 End 时读取一次 metadata；需要在 reasoning/tool/text
frame 分支保留最小、不可变的 block projection。

## 第三步：严格恢复历史 assistant

实现 `KoogReplayRestorer.restore(message, base, context)`：

1. replay state 必须是预期 JSON 结构；未知 kind/version 使用 `KOOG_INVALID_REPLAY_STATE`；
2. state provider/model 必须等于 `ModelMessageSource`，不能等于本次 target 就算通过；
3. block 数量和类型必须与 durable `message.content` 完全一致；
4. 正文、reasoning text、tool id/name/raw arguments 全部取自 durable content；state 只能补私有 metadata；
5. 返回 message id 必须保持 base id；不得恢复旧 usage 或时间戳来伪装本次响应；
6. target provider/model 可以变化，但 codec 必须显式决定该 Koog client 是否允许把 source metadata 交给
   target；不允许时显式失败或返回严格 provider-neutral base，不能改写 source 身份。

## 第四步：契约矩阵

启用某个 Provider codec 前至少覆盖：

- text、reasoning、tool-call 的成功 write → serialize → restore round trip；
- unknown kind/version、缺字段、错误 JSON 类型；
- state 与 source provider/model 不匹配；
- block 数量、顺序、类型不匹配；
- durable content 被改写后旧 state 被拒绝或由上层清除；
- source 与 target model/provider 相同和不同的明确行为；
- foreign Adapter state 不进入本 codec；
- error/aborted/取消流不写 replay state；
- JSON 中不含正文副本、API key、Authorization header 或完整 raw response。

消息 API 提供 `Message.replaceContent(...)` 与 `withoutReplayState()`，上层改写 assembled assistant content
时应通过这些显式入口清除旧 state；普通 `copy()`/`copyMessage()` 则保留 state，避免无意丢失。

## 验收

```bash
./kotlin check -m llm-koog
./kotlin build
```

OpenAI Responses 的 codec 测试覆盖 writer/JSON/restore round trip、reasoning 私有字段、foreign state、
identity/content mismatch、unknown kind/version、非文本 tool result 和失败 terminal 不写 state。它仍不等于
真实网络或生产持久化 live 验收。
