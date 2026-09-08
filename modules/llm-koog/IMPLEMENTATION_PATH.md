# llm-koog 逐阶段实施路径

这条路径把纯转换、主路径接线和 Provider 语义分开验收。provider-neutral 阶段已经全部解除 `@Ignore`；
后续 Provider 阶段仍必须先建立精确 fixture 和失败边界，不能把通用测试变绿外推为具体客户端可用。

## 每个阶段的固定动作

1. 先新增或启用本阶段的精确契约。
2. 运行 `./kotlin check -m llm-koog`，确认预期契约变红且错误来自本阶段入口。
3. 实现最小无损子集；未知字段、非法状态和无法证明的 Provider 语义使用稳定错误失败。
4. 更新或删除本阶段对应的 scaffold test，不能让“未实现”断言与真实实现并存。
5. 同时跑 `./kotlin check -m llm-koog` 和 `./kotlin build`。
6. 不为通过测试而吞掉 frame、metadata 或消息块。

## 阶段 1：最小文本流

`KoogStreamMapperTest` 的 12 条契约已启用并通过；它们覆盖 `TextDelta`、`TextComplete`、`End("stop")`、
显式/隐式 block index 和每次 collection 独立的 `KoogStreamState`。End 已调用现有
`KoogStreamTerminalMapper`；无内容成功终止抛
`EMPTY_RESPONSE`，usage 为空时不产生 chunk，标准 input/output usage 输出 `UsageChunk`，部分/负数/不一致
usage 使用 `INVALID_USAGE`，纯文本 reasoning 与 tool-call stream 已支持；未知 finish 继续失败。

文本契约通过后已启用 `KoogUsageStreamIntegrationContractTest`、
`KoogAdapterStreamContextIntegrationContractTest` 和 `KoogCancellationIntegrationContractTest`。前两条证明
terminal 顺序及三种 route/model 身份没有混淆；后两条证明 downstream early-stop 和 caller cancellation
都会终止 Provider flow，且不会进入 failure classifier 或产生 error finish。

实现细节见 [TEXT_STREAM_GUIDE.md](TEXT_STREAM_GUIDE.md)。真实 Provider 仍需完成后续语义和 live teardown
验收后才能接入生产凭据。

## 阶段 2：通用 usage 与 terminal 顺序（已完成）

`KoogUsageMapperContractTest`、默认 usage 主路径和 terminal/context integration 已通过；证明 input/output
的通用无损子集、Long 拓宽、metadata 不猜测及 `BlockEnd → UsageChunk → FinishChunk` 顺序。本阶段不能重新
绕过或复制 End 编排。

Provider 私有 cache/reasoning 计数仍留给专属 mapper，见
[USAGE_MAPPING_GUIDE.md](USAGE_MAPPING_GUIDE.md)。

## 阶段 3：纯文本 reasoning（已完成）

以下契约已启用并通过：

- `KoogReasoningMapperContractTest`：实现没有 id/summary/encrypted 的纯文本子集；
- `KoogReasoningStreamIntegrationContractTest`：证明主 stream 复用统一 block 生命周期并正常 finish。

纯文本 reasoning 已接入共享 block 生命周期。OpenAI Responses 现在通过版本化 Provider codec 使用已有
`FinishChunk.replayState` / `ModelMessageSource.replayState` 保存并恢复 `id/summary/encrypted`；通用默认 mapper
仍继续拒绝没有专属 codec 的有损字段。
细节见 [REASONING_MAPPING_GUIDE.md](REASONING_MAPPING_GUIDE.md)。

## 阶段 4：工具 schema 进入请求（已完成）

`KoogToolMapperContractTest` 与 `KoogToolRequestIntegrationContractTest` 已启用并通过。默认 mapper
实现顶层 object + primitive properties 的安全子集，并由 `KoogRequestMapper` 保序传出 descriptor。

Koog 1.1.1 `ToolDescriptor` 无法表达顶层 `additionalProperties`；即使值为 `false` 也会明确使用
`KOOG_UNSUPPORTED_TOOL_SCHEMA` 拒绝，不会静默删除。完整支持范围见
[TOOL_MAPPING_GUIDE.md](TOOL_MAPPING_GUIDE.md)。

## 阶段 5：工具历史消息进入请求（已完成）

`KoogToolMessageMapperContractTest` 与 `KoogToolMessageRequestIntegrationContractTest` 已启用并通过。
tool result 名称只从前序 assistant call 恢复，且 `KoogRequestMapper` 的顺序化 mapping context 已进入完整
请求路径。

raw arguments 保持原字符串；多个结果文本 part 保持顺序；缺失/重复 CallId 或无法恢复名称时使用
`KOOG_INVALID_TOOL_HISTORY`。

## 阶段 6：tool-call stream（已完成）

CallId/name 由每个 `KoogStreamBlockState` 持有，mapper 不保存跨 collection mutable identity。Koog 1.1.1
OpenAI 与 Anthropic client 源码都证明 identity-only delta 是真实协议路径：首帧可只提供 id/name，参数由
后续 delta 到达。因此 null content 只记录身份并启动 block，不制造 arguments。

`KoogToolStreamMapperContractTest` 与 `KoogToolStreamIntegrationContractTest` 已启用并通过，覆盖 identity-only、
delta、权威 complete、冲突身份、交错显式 index、顺序缺失 index、主 stream 接线和注入式 Provider
`KoogToolCallTerminalPolicy`。Koog builder 的 text/reasoning 无 index 生命周期也由主 stream integration
直接验证。

Koog 客户端的 `tool_calls`、`tool_use` 等值仍不能在全局 mapper 中按字符串相似度归一化。
公共 policy 默认返回 false；只有具体 Provider bundle 能声明某个精确 End 值允许补齐 open tool-call。

## 阶段 7：Provider-native replay 闭环

OpenAI Responses 已完成第一条闭环：`OpenAiResponsesReplayWriter` 只在成功 terminal 写入最小 state，
`OpenAiResponsesReplayRestorer` 严格校验 JSON、source/target identity、block 对齐后只补 Koog reasoning 私有
metadata。没有专属 codec 的 route 仍由默认 restorer 显式失败；不能直接持久化完整 `rawResponse`。

## 阶段 8：单 Provider 完整装配

只选择一个 Provider，同时实现以下组件：

- `KoogOptionMapper`：stop、reasoning effort 和 Provider 参数子类；
- `KoogMessageMapper`：完整消息/历史映射；自定义实现必须处理 replay context；
- `KoogToolMapper`：ToolSchema 安全子集；
- `KoogReasoningMapper`：已证明的 reasoning frame 子集；
- `KoogToolStreamMapper`：tool-call frame、identity 和 raw arguments；
- `KoogToolCallTerminalPolicy`：该客户端是否以 End 代替 tool-call complete frame；
- `KoogUsageMapper`：已证明的 metadata 私有计数；
- `KoogFinishReasonMapper`：该客户端精确的 stop/max-token/tool-call 终止值；
- `KoogReplayRestorer`：已经完成 writer/round-trip 契约的版本化私有 state；
- `KoogProviderFailureClassifier`：结构化 HTTP/Provider 错误；
- `KoogProviderExecutorFactory`：Host credential → client → executor 生命周期。

把这些语义组件放进同一个 `KoogProviderSemantics(provider = route.id, ...)`，再通过
`providerSemantics = listOf(...)` 安装。多 route 时列表必须与 routes 精确一一对应；不要为各语义组件
分别维护 dispatcher，否则它们可能选择不同 Provider。低层独立 mapper 构造器只用于已经自行证明
dispatcher 一致性的高级场景。

相关指南：

- [REQUEST_MAPPING_GUIDE.md](REQUEST_MAPPING_GUIDE.md)
- [USAGE_MAPPING_GUIDE.md](USAGE_MAPPING_GUIDE.md)
- [REASONING_MAPPING_GUIDE.md](REASONING_MAPPING_GUIDE.md)
- [TOOL_MAPPING_GUIDE.md](TOOL_MAPPING_GUIDE.md)
- [REPLAY_MAPPING_GUIDE.md](REPLAY_MAPPING_GUIDE.md)
- [FAILURE_MAPPING_GUIDE.md](FAILURE_MAPPING_GUIDE.md)
- [PROVIDER_ASSEMBLY_GUIDE.md](PROVIDER_ASSEMBLY_GUIDE.md)

先用真实 Koog client 配合 scripted `KoogHttpClient.Factory` 做无网络集成测试；记录型 executor 仍用于
Adapter 冷流和生命周期契约。只有前六阶段、Provider fixture、取消传播、资源关闭和密钥审计全部通过后，
才使用测试凭据进行 live text/tool 验收；JVM live 验收不能替代 Android/iOS client 支持证明。

### OpenAI JVM/Desktop 进度

OpenAI Chat Completions 已完成 JVM/Desktop 的部分装配：`OpenAiChatOptionMapper`、标准 usage mapper、
`stop`/`length`/`tool_calls` finish mapper、结构化 HTTP failure classifier、环境变量 credential resolver
和 `OpenAiPromptExecutorFactory` 均已安装并由 JVM fixture 覆盖。OpenAI bundle 还显式安装
`OpenAiChatToolCallTerminalPolicy`，由共享 terminal seam 在 `End("tool_calls")` 时补齐没有独立 complete
frame 的 tool block；公共层本身不识别该字符串。`o3-mini` 请求 mapper 与模型 metadata 均只开放
`low`/`medium`/`high` reasoning effort。

Responses API 现在由 `openai-responses` API template 提供；外部 Provider route 由 settings 创建，模型
通过 `api` 选择该 template。`OpenAiResponsesOptionMapper` 返回 Koog 原生 `OpenAIResponsesParams`，
`OpenAILLMClient` 自动请求 `v1/responses`；Responses terminal policy 处理
`response.completed` 的开放文本收尾，tool mapper 等待权威 complete frame。`OpenAiClientFixtureTest` 的
scripted HTTP fixture 已证明两种 API 的请求序列化、SSE、usage/finish、取消传播、executor/client close
和密钥边界；新增 `OpenAiLocalHttpTransportTest` 使用 JDK loopback HTTP server，不注入 scripted factory，
已验证默认 Ktor transport 的两种 API POST、Bearer 鉴权、请求体密钥隔离、真实 SSE 解析，以及 caller cancellation
和 consumer early-stop 后的上游连接断开；另有 loopback 空闲流证明 `socketTimeoutMillis` 会产生稳定
`TIMEOUT` failure 并关闭上游连接。它仍不能替代外部 live 网络或
结构化 HTTP 错误的外部 live 变化、Provider-native replay 的真实网络行为和私有 usage 验收。OpenAI Responses
codec 的离线 round-trip/篡改测试已完成。loopback 错误验收已覆盖
Chat 401/429/400/503 与 Responses 429，并确认 status/body 能映射到稳定 `LlmFailure`。默认 Desktop Profile
在空 settings 下保持不读取凭据、不创建 executor。File-backed Desktop Profile 的真实 Host 验收已覆盖单一
OpenAI Provider 的两种模型 API、文件凭据、
watcher generation reload、在途流与非法配置回退；当前 WSLg/niri 环境的 Compose 首帧仍是独立的 live gate。

外部 live acceptance 现在由 `OpenAiLiveAcceptanceTest` 显式 opt-in：未提供 live key 时整类跳过。用户提供的
OpenAI-compatible endpoint 已用其目录中的模型完成 Chat Completions 真实文本与 tool round trip；Chat
生命周期 gate 也已通过 downstream early-stop、caller cancellation、取消后恢复和限时 Host close。双模型
live gate 进一步证明 HTTP 503 映射为 `SERVER` Error Finish 后，同一 executor 可立即使用健康模型恢复。
`RetryPolicy` 当前只是数据约定，退避重试执行留给后续 `llm-retry` Plugin。Responses 请求已得到 HTTP 200，
但 Koog 1.1.1 将 OpenAI schema 允许的 string `response.instructions` 按数组反序列化，命中上游
JetBrains/koog#2211。这里不复制客户端或拦截 SSE；等待包含修复的正式 Koog 版本后重跑同一契约。私有
cache/reasoning usage 继续独立等待 JetBrains/koog#1275 及正式发布。

## 完成定义

模块“可编译”只代表骨架健康。某项能力只有在它的纯契约、主路径 integration contract、三平台模块检查
和整仓构建全部通过时才算实现；具体 Provider 还必须执行对应 Host 模块检查（当前 OpenAI 为
`./kotlin check -m jvm-app`）、无网络 fixture 测试与 live acceptance。未执行的
平台或网络验收应在交付说明中明确列为未验证，而不是从其他平台构建结果推断。
