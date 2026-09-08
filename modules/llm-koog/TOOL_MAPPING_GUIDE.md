# Koog 工具映射指南

工具阶段分三部分，按顺序实现：

1. `ToolSchema` → Koog `ToolDescriptor`
2. Harness tool-call/tool-result 历史 → Koog message parts
3. Koog tool-call stream frames → Harness stream chunks

不要把三部分塞进一个 Mapper。schema 是调用配置，消息是历史重放，stream 是本次模型输出；它们的
错误和状态生命周期不同。

## 一、ToolSchema

Harness 保存任意 JSON Schema；Koog 1.1.1 `ToolDescriptor` 使用自己的有限类型系统：

```text
String / Integer / Float / Boolean / Null
Enum / List / Object / AnyOf
```

第一阶段只实现顶层 object 和 primitive properties：string、integer、number、boolean。

`KoogToolMapperContractTest` 与请求 integration 已启用并通过。`DefaultKoogToolMapper` 当前遵循：

1. tools 为 null 或空列表时返回空列表。
2. 拒绝重复 tool name，使用 `KOOG_INVALID_TOOL_SCHEMA`。
3. 顶层 `type` 必须是 `object`；其他类型使用 `KOOG_UNSUPPORTED_TOOL_SCHEMA`。
4. `properties` 必须是 object；缺失时可视为空 object。
5. `required` 必须是字符串数组，并且每个名字都存在于 properties。
6. 每个 property 必须有支持的 `type`；缺失或字段类型错误使用 `KOOG_INVALID_TOOL_SCHEMA`。
7. required 与 optional 的相对顺序保持 properties 原顺序。
8. name、description 和 property description 原样传递。

不要静默删除 `additionalProperties`、`oneOf`、`$ref` 等约束。Koog 1.1.1 `ToolDescriptor` 的根对象
生成器只表达 `type`、`properties` 和 `required`，因此连 `additionalProperties: false` 也无法无损保留，
当前明确使用 `KOOG_UNSUPPORTED_TOOL_SCHEMA` 拒绝；后续扩展时逐个增加契约。

`KoogToolMapper.map(tools, context)` 会收到目标 `KoogStreamContext`，因此同一个 executor 里的多个
Harness route 可以由 semantics registry 精确分派。`KoogToolRequestIntegrationContractTest` 已证明默认
`KoogRequestMapper` 真正把 descriptor 传给 executor 路径；不要重新加入占位错误或 `@Ignore`。

## 二、工具消息

Koog `MessagePart.Tool.Result` 需要：

```text
id + tool name + parts + isError
```

Harness `ToolResultBlock` 只有：

```text
toolCallId + content + isError
```

tool name 必须从前序 assistant `ToolCallBlock` 恢复。`KoogMessageMappingContext` 已按请求消息顺序
维护 `CallId -> name`：

- assistant tool-call 成功映射后调用 `context.observe(message)`；
- tool result 映射时调用 `context.requireToolName(callId)`；
- 结果出现在调用之前、重复 call id、空 tool name 都使用 `KOOG_INVALID_TOOL_HISTORY`；
- 不允许使用 `"unknown"` 回退。

`KoogToolMessageMapperContractTest` 与请求 integration 已启用并通过。`DefaultKoogMessageMapper` 当前保证：

- assistant `TextBlock` → `MessagePart.Text`
- assistant `ToolCallBlock` → `MessagePart.Tool.Call`
- 保持 content 原顺序和 message id
- tool result → 一条 Koog user message，包含一个 `MessagePart.Tool.Result`
- 第一阶段结果 content 只支持 `TextBlock`，多个文本块保持顺序
- reasoning、嵌套 tool-result 或其他不可表示内容显式失败
- raw JSON arguments 不解析、不重排、不重新序列化

`KoogRequestMapper` 在每条消息映射成功后调用 `context.observe`，因此名称只来自历史中的前序调用；
`KoogToolMessageRequestIntegrationContractTest` 已证明该顺序化上下文用于完整请求历史。

## 三、Tool-call 流

Koog 帧：

```text
ToolCallDelta(id?, name?, content?, index?)
ToolCallComplete(id?, name, content, index?)
```

Harness 需要：

```text
BlockStartChunk(index, "tool-call")
ToolCallDeltaChunk(index, CallId, name?, argumentsDelta)
BlockEndChunk(index, ToolCallBlock)
```

`DefaultKoogToolStreamMapper` 当前遵循：

1. index 可为空或为非负显式值；负数以及未完成 block 中途切换显式/隐式 index 使用稳定状态错误。
2. 使用 `KoogStreamBlockType.TOOL_CALL` 打开 block。
3. 每个 frame 先调用 `block.observeToolCallIdentity(frame.id, frame.name)`；身份保存在当前 collection 的
   block state，mapper 实例本身不能保存 mutable map。
4. 首次帧输出一次 block-start。
5. 产生 arguments delta 前调用 `block.requireToolCallId()`；Harness 不允许空 CallId。
6. delta 的 name 使用 `block.takeToolCallNameForDelta()`；即使 Provider 重复发送相同 name，同一 block 也
   最多向 Harness 发出一次。
7. 后续 frame 省略 id/name 时复用已知身份；同一显式或隐式 provider block 改变 id 或 name 时状态机会使用
   `KOOG_INVALID_STREAM_STATE` 失败。
8. complete 先观察权威 id/name，再调用 `block.complete()`；状态机会要求 id/name 都已存在，与已知身份冲突
   或身份缺失时失败。
9. block-end 使用 `requireToolCallId()` / `requireToolCallName()` 和 raw `content` 构造 `ToolCallBlock`，不解析
   JSON。

公开的 `KoogToolStreamMapper.map(frame, state, context)` 会收到目标 `KoogStreamContext`；调用入口是
`toolStreamMapper.map(frame, state, context)`；stream mapper 只负责分派和按返回顺序 emit，
不要复制 tool-call 身份状态。

`ToolCallDelta.content` 在 Koog 类型中可空。Koog 1.1.1 OpenAI client 会把可能缺 id/name/content 的每个
tool-call chunk 直接送入 `emitToolCallDelta`；Anthropic client 的 content-block-start 只发送 id/name，随后
input-json delta 才发送 arguments。因此 identity-only frame 只保存身份并发出一次 block-start，不产生
`ToolCallDeltaChunk`；第一个真实 arguments delta 再携带一次 name。契约与主 stream integration 已启用通过。

`index` 同样可空。共享 `KoogStreamState` 会把空 index 关联到当前尚未完成的隐式 tool block；Koog
`StreamFrameFlowBuilder` 在新 CallId 到来前会先 complete 旧 pending call，因此顺序的无 index tool call 可以
无歧义映射。显式 index 仍支持多个 tool block 交错；若同一个未完成 block 中途从空 index 切到显式 index，
或反向切换，则使用 `KOOG_INVALID_STREAM_STATE` 失败，不能猜测合并。

`KoogStreamStateTest` 已活动验证身份不跨独立 stream state 泄漏、重复 name 只取出一次，以及缺失、空白、
冲突身份使用稳定错误。不要在 `DefaultKoogToolStreamMapper` 增加跨 collection 字段来绕过这些边界。

第一阶段不要“修复”模型产生的畸形 JSON；Harness 约定保存模型原始 arguments 字符串，工具执行层自行决定
是否拒绝。

## Finish reason

只有所有 tool-call block 都完成后，Provider 已验证的 tool-call 终止值才能映射为
`ToolCallsFinishReason`。Koog 不会把所有客户端的 finish 字符串标准化；例如不同客户端可能产生
`tool_calls` 或 `tool_use`。因此由注入的 Provider mapper 根据 `KoogStreamContext` 做精确映射，不能在
全局 mapper 中猜同义词。通用 `StopOnlyKoogFinishReasonMapper` 继续显式拒绝这些值；具体 Provider 通过
注入 mapper 精确映射。

如果某个客户端只发送 tool-call delta、没有独立 complete frame，它还必须在同一个
`KoogProviderSemantics` 中安装精确的 `KoogToolCallTerminalPolicy`。默认 policy 返回 false，所以公共
terminal 不会因为看见 `"tool_calls"` 或其他相似字符串就自行关闭 block；policy 也只能返回决策，实际
`KoogStreamState.completeOpenToolCalls()` 仍由共享 terminal seam 调用。

## 验收

```bash
./kotlin check -m llm-koog
./kotlin build
```

验收条件：

- schema/message/tool-stream 契约按阶段启用；
- 三条 request/stream integration contract 在各自阶段启用；
- JSON Schema 不支持约束不会被静默删除；
- tool result name 只从前序 call 恢复；
- raw arguments 保持字节等价字符串；
- CallId/name 冲突使用稳定错误；
- 顺序无 index tool block 与交错显式 index tool block 都保持独立身份和输出顺序；
- `tool_calls` finish 前所有 tool block 已关闭。
