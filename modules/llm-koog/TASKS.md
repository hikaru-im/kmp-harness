# llm-koog 任务清单

这里记录 Koog Adapter 的基础建设与用户实现边界。核心转换逻辑不在脚手架阶段代写。

## 基础边界

- [x] K0：提取共享 `LLModel`、route 和 `PromptExecutor` 测试夹具。
- [x] K1：建立 `KoogStreamState`，管理 provider block、输出顺序和 finish 生命周期。
- [x] K2：实现 provider-neutral 文本流并启用契约；`TextDelta`/`TextComplete`/`End("stop")`、缺失
  terminal、取消和 context 接线已验证，其他 frame/finish 仍分派到各自显式边界。
- [x] K3：提取可注入、可感知已解析模型的 `KoogOptionMapper`；temperature/maxTokens 已支持，
  Koog 范围和 Int 溢出使用稳定错误，通用层对 stop/reasoning effort 使用稳定 unsupported 错误。
- [x] K4：提取 `KoogMessageMapper` 和 system message factory；provider-neutral 文本、assistant tool-call 与
  tool-result 历史已实现，Provider 私有 replay 仍由独立 restorer 负责。
- [x] K5：建立并实现 `KoogToolMapper`、顺序化消息上下文和 provider-neutral tool stream mapper；schema、
  tool history、identity-only/delta/complete 与主路径 integration 已验证。
- [x] K6：实现纯文本 reasoning 子集并接入主流；delta/complete、共享 block 生命周期、字段拒绝和
  integration contract 已验证，Provider 私有 reasoning 字段仍显式拒绝。
- [x] K7：实现标准 input/output usage 无损子集并接入 terminal；缺失 usage 返回 null，部分/负数/total
  不一致使用稳定 `INVALID_USAGE`，metadata 私有计数仍留给 Provider mapper。
- [x] K8：建立并接入 failure mapper 边界；通用实现保留稳定 `LlmException` 字段、归一化未知异常，
  保持协程取消和下游异常透明；可注入 Provider classifier 已进入 Runtime error-finish 路径，具体
  Provider/HTTP 分类仍未实现。
- [x] K9：提取并接入模型 metadata mapper。
- [x] K10：建立 credential 引用、Provider settings 和异步 executor factory 生命周期边界。
- [x] K11：Desktop Profile 提供可选 Koog 装配入口；没有具体 Provider 客户端。
- [x] K12：settings 构造器校验 route/settings 一一对应并按 route 顺序快照；Desktop 可传入
  Provider option/failure 语义组件，required credential 使用稳定错误失败。
- [x] K13：为流映射增加 Harness route/model/Koog provider 上下文；usage/finish 可从 Plugin 与 Desktop
  注入，并为 Adapter context、usage、reasoning、tool schema、tool history、tool stream 增加主路径
  integration contract。
- [x] K14：建立 `KoogProviderSemantics`；多 route 装配要求 option/usage/finish/message/tool/reasoning/
  replay/failure bundle 与 routes 精确匹配，Desktop 默认使用逐 route 的保守通用 bundle，低层独立 mapper
  构造器继续保留。
- [x] K15：按 DSH 源码复核 Adapter 边界；模型输出能力与请求默认值分离，空成功响应固定为
  `EMPTY_RESPONSE`，并建立 caller cancellation / consumer early-stop 集成契约和边界审计。
- [x] K16：建立 `KoogReplayRestorer` 与 source/target context；默认对本 Adapter replay fail-loud，semantics
  registry 按历史 source route 分派 codec，foreign Adapter state 回退为 provider-neutral durable content。
- [x] K17：建立 `KoogStreamTerminalMapper`；成功状态校验、usage/finish 调度与 terminal 顺序可独立测试，
  已报告 usage 在文本阶段不会被静默跳过；Adapter 冷流保证每次 collection 只发起一次请求尝试。
- [x] K18：把 tool-call id/name 生命周期放入 per-collection `KoogStreamBlockState`；支持分帧观察、冲突校验
  和 name 单次发出，避免后续实现把并发请求状态错误地保存在共享 mapper。
- [x] K19：移除公共 terminal 对 `"tool_calls"` 的硬编码；新增 route-bundled
  `KoogToolCallTerminalPolicy`，默认严格拒绝 open block，只有 OpenAI semantics 显式允许 delta-only
  tool-call 收尾。
- [x] K19：验证 executor 创建后若 Adapter route 注册冲突，Plugin apply rollback 只关闭新 executor，原有
  route/executor 保持生效；正常 uninstall 仍关闭原 executor。
- [x] K20：公开 message/tool schema/reasoning/tool-stream mapper 接口与 route context，并把它们接入
  `KoogProviderSemantics` registry；外部 Desktop 不再只能装配 option/usage/finish/failure。
- [x] K21：自定义 message mapper 由 Adapter 统一套一层 replay-aware wrapper；durable content 仍由 mapper
  生成，Provider 私有 state 恰好恢复一次，不能因工具历史扩展点而被静默跳过。
- [x] K22：新增 message/tool/reasoning/tool-stream 参数放在既有公开构造参数之后，保持原有位置参数
  装配源码兼容。
- [x] K23：为 OpenAI JVM/Desktop 增加真实 Koog `OpenAILLMClient` 的离线 scripted HTTP fixture，覆盖请求
  序列化、文本/tool SSE、取消传播、executor/client close 与 API key 不泄漏；不把 fixture 外推为 live 网络
  或 HTTP engine 验收。
- [x] K24：增加独立 `openai-responses` route，使用 Koog 原生 `OpenAIResponsesParams`/`v1/responses`，并以
  scripted HTTP fixture 验证 `store:false`、`max_output_tokens`、Responses text/tool SSE、terminal 顺序、
  consumer early-stop、caller cancellation 和 client close；Responses tool argument delta 暂不单独发出
  Harness delta，等待权威 `ToolCallComplete` 以避免虚构 call id。
- [x] K25：Desktop 建立 provider-neutral Registry；每个 registration 独立拥有 routes/settings/semantics/
  executor factory，Registry 严格校验 route 与 Koog model ownership，按 registration 隔离 settings，组合
  executor 按 Koog provider/model 分派，并在部分创建失败时反向关闭已创建 child executor。OpenAI 已改为
  Registry registration，`main.kt` 不再直接依赖 OpenAI 构造入口。
- [x] K26：把模糊的 OpenAI Chat route 从 `openai` 迁移为 `openai-chat-completions`；Responses 保持
  `openai-responses`。同步 Desktop 常量、测试、文档和两份用户 settings，Koog client provider 仍保持
  `LLMProvider.OpenAI`，因此协议命名与 client 路由身份不混用。
- [x] K27：增加 `OpenAiLocalHttpTransportTest`，用随机 loopback JDK `HttpServer` 驱动默认 Koog/Ktor HTTP
  factory，验证 Chat/Responses 两条真实 POST 路径、Bearer 鉴权、请求体密钥隔离和标准 SSE 解析；不访问
  外部网络，HTTP 取消/idle teardown 仍待后续 live gate。
- [x] K28：扩展 loopback HTTP 验收，验证真实 Ktor transport 在 Chat caller cancellation 和 Responses
  consumer early-stop 后关闭上游 SSE 连接；不访问外部网络，provider idle timeout 仍待后续 live gate。
- [x] K29：扩展 loopback HTTP 验收，验证真实 Koog/Ktor HTTP status 和结构化 error body 能到达 OpenAI
  failure classifier；覆盖 Chat 401/429/400/503 与 Responses 429，并断言稳定错误码、status、message 和密钥隔离。
- [x] K30：把 Koog request/connect/socket timeout 纳入 provider-neutral settings；OpenAI factory 将
  `socketTimeoutMillis` 作为 SSE read-idle 边界传给 Ktor，并用 loopback 空闲流验收 `TIMEOUT` failure
  与上游连接关闭。默认值仍由 Koog 提供，同一 Provider 的两个 API template 必须共享 transport timeout 配置。
- [x] K31：为 OpenAI Responses 实现 provider-native replay codec；成功 terminal writer、版本化 JSON state、
  reasoning `id/summary/encrypted` 恢复、source/target 与 block 对齐校验、foreign fallback、失败 terminal
  不写 state，以及消息正文替换时清除旧 replay state 均有活动测试。
- [x] K32：增加显式 opt-in 的外部 live acceptance，不带 `HARNESS_OPENAI_LIVE_API_KEY` 时整类跳过，避免
  日常 `check` 意外触网或产生费用。OpenAI-compatible endpoint 的 Chat Completions 文本流已真实通过；
  Responses 已到达 HTTP 200，但 Koog 1.1.1 会把 OpenAI 合法的 string `response.instructions` 按数组解析而
  失败，对应上游 issue #2211。测试专用模型目录可由环境变量覆盖，不污染产品目录。
- [x] K33：对齐 DSH 的 settings-driven 模型目录规则；省略/空 `models` 保留安装目录，非空 `models`
  替换目录，`modelOverrides` 局部修正且拒绝未知 id/冲突组合。每个有效 settings 快照会物化真实
  `LLModel`、metadata 与 executor generation；prepared/in-flight 调用保持旧 generation，新调用使用新目录。
  JVM 契约覆盖容量/输入/reasoning 继承、请求默认 maxTokens、未知旧模型和 YAML 到真实 OpenAI 请求。
- [x] K34：完整对齐 DSH 的 Provider Directory 与 dormant adapter：插件静态安装 API templates，空 settings
  保持零活动 route，provider map 动态增删并使用 generation 原子替换和失败回退。OpenAI 对外只声明
  `openai`，Chat Completions/Responses 下沉为模型 `api`；loopback 与 file-backed Host 已验证同一 Provider
  按模型选择两种 endpoint。
- [x] K35：把 OpenAI live acceptance 改为生产 `startDesktopProfile()` + 临时 file-backed Harness home；
  Chat 文本默认启用，tool round trip 与 Responses 分别由显式环境开关控制。测试验证 file credential 来源、
  Directory/route/model 激活、非凭据产物与异常不含密钥，并在 Host close 后删除临时目录；外部执行仍单列验收。
- [x] K36：用测试凭据执行生产 file-backed live harness；Responses-only 兼容端点的 `gpt-5.6-sol` raw 请求
  返回 HTTP 200，Harness 同样到达 HTTP 200 后精确复现 Koog #2211 的 `response.instructions` string/array
  反序列化错误。该连接没有 Chat Completions 可用渠道，故 Chat/tool 仍未验收；临时目录与密钥审计通过。
- [x] K37：用 ShuaiAPI 声明的 `gpt-5.6-luna` 执行生产 file-backed Chat tool live acceptance；第一轮生成
  唯一 `lookup({"query":"Kotlin"})` 并以 tool-calls 结束，回传 tool result 后第二轮返回 `TOOL_OK`。
  Host 关闭、临时 Harness Home 删除和密钥隔离审计均通过。
- [x] K38：补齐生产 file-backed Chat 的真实网络生命周期验收；`gpt-5.6-luna` 已通过 downstream
  `take(1)`、caller cancel、同一 executor 取消后恢复请求、限时 Host close、临时目录删除和密钥隔离。
  外部服务端 socket 不可观测，实际连接断开继续由 loopback transport 契约证明。
- [x] K39：补齐生产 file-backed Chat 的真实失败与恢复验收；同一 generation 中的 `gpt-4o-mini` HTTP
  503 已映射为唯一 `SERVER` Error Finish，随后同一 executor 使用 `gpt-5.6-luna` 恢复并返回
  `RECOVERY_OK`。当前 `RetryPolicy` 只提供数据约定，真正的退避重试属于后续 `llm-retry` Plugin。

这些勾选项表示“实现入口和失败边界已具备”，不表示对应 Provider 能力已经完成。

## 当前用户实现阶段

- [x] 移除 `KoogStreamMapperTest` 类上的 `@Ignore`，启用 12 条文本流与隐式 index 契约。
- [x] 实现 `TextDelta`、`TextComplete`，并让 `End` 调用现有 `KoogStreamTerminalMapper`。
- [x] 文本流通过后移除 usage/context 两个 integration contract 与
  `KoogCancellationIntegrationContractTest` 的 `@Ignore`，共验证四条主路径契约。
- [x] 实现 `KoogUsageMapperContractTest` 的通用 input/output 无损子集，并验证默认 End 主路径输出
  `UsageChunk → FinishChunk`。
- [x] 实现纯文本 reasoning mapper 及其主路径 integration contract。
- [x] 实现工具 schema mapper；顶层 object、primitive properties、required/optional 顺序和默认请求路径
  已验证，Koog 无法表达的 `additionalProperties` 等约束使用稳定 unsupported 错误拒绝。
- [x] 实现 assistant tool-call/tool-result 历史映射；raw arguments、message id 与多文本 part 顺序保持，
  result name 只从前序 assistant call 恢复。
- [x] 实现 tool-call stream；identity-only delta、分帧 identity、权威 complete、Provider finish 接线与
  per-collection 状态已验证。

完整阶段顺序见 [IMPLEMENTATION_PATH.md](IMPLEMENTATION_PATH.md)，当前文本流逐步说明见
[TEXT_STREAM_GUIDE.md](TEXT_STREAM_GUIDE.md)。

Provider 专属 stop/reasoning effort 请求参数见
[REQUEST_MAPPING_GUIDE.md](REQUEST_MAPPING_GUIDE.md)；应在选定真实 Provider 后实现，不能修改通用
mapper 来假装所有 Provider 都支持。

当前 usage 阶段见 [USAGE_MAPPING_GUIDE.md](USAGE_MAPPING_GUIDE.md)。
纯文本 reasoning 阶段见 [REASONING_MAPPING_GUIDE.md](REASONING_MAPPING_GUIDE.md)。
工具阶段见 [TOOL_MAPPING_GUIDE.md](TOOL_MAPPING_GUIDE.md)。
错误分类阶段见 [FAILURE_MAPPING_GUIDE.md](FAILURE_MAPPING_GUIDE.md)。
Provider 装配阶段见 [PROVIDER_ASSEMBLY_GUIDE.md](PROVIDER_ASSEMBLY_GUIDE.md)。
Provider-native replay 闭环见 [REPLAY_MAPPING_GUIDE.md](REPLAY_MAPPING_GUIDE.md)。

## 后续实现阶段

- [x] 动态 settings/credential/model catalog generation reload：`SettingsKey`/`CredentialsKey` watcher、
  Provider Directory、route 动态增删、空配置休眠、原子 generation 切换、旧 generation 空闲关闭与失败
  回退均已接入。
- [x] 多 block 与缺失 index：显式 index 支持交错 block；缺失 index 按同类型当前隐式 block 生命周期关联，
  顺序完成后可再次创建同类型 block；未完成 block 中途切换显式/隐式 index 使用稳定状态错误。
- [x] OpenAI Responses provider 私有 reasoning replay：`id/summary/encrypted` 已通过专属 codec 恢复；其他
  Provider 的私有 reasoning 仍待各自 codec。
- [ ] Provider 专属 usage、finish reason 与 metadata 语义（OpenAI Chat Completions/Responses 的标准 usage、
  `stop`/`length`/`tool_calls` 已在 JVM/Desktop mapper 中完成；私有 cache/reasoning usage 仍未映射）。
- [x] OpenAI Chat Completions JVM/Desktop 已实现并安装 `KoogProviderFailureClassifier`，fixture 覆盖
  rate limit、quota、context window、credential、server、timeout/transport 边界；生产 file-backed live
  已验证 503 分类和同一 executor 恢复。退避重试执行仍属于后续 `llm-retry` Plugin。
- [x] OpenAI Chat Completions JVM/Desktop Executor Factory 已实现；DeepSeek 等其他 Provider 仍未实现。
- [x] OpenAI Chat Completions JVM/Desktop 专属 `KoogOptionMapper` 已安装；其他 Provider 仍未实现。
- [x] Desktop File-backed Profile 已用临时 `settings.yaml` / `.credentials.yaml`、真实 `HarnessHost` 和
  scripted `KoogHttpClient.Factory` 验证一个 OpenAI Provider 的模型级 Chat Completions/Responses API、密钥边界、Host close，以及
  settings/credential watcher 的 generation 切换、在途请求保护、旧 client 空闲关闭和非法配置回退。
- [ ] Desktop Profile 外部 live 验收闭环（生产 file-backed Chat text/tool/lifecycle/failure recovery 已通过；
  Responses 已在 HTTP 200 后复现 Koog #2211；OpenAI 官方 endpoint、live replay 和 Compose 首帧仍未完成）。
- [x] OpenAI Responses stream replay writer、版本化 state、严格 restorer 和内容改写清除已完成；真实网络
  replay/live persistence 仍待验收。

### OpenAI JVM/Desktop 当前实现

- [x] Chat Completions API template、`gpt-4o-mini`/`o3-mini` 模型目录和 reasoning effort metadata 已装配；
  `o3-mini` mapper 与 metadata 均只支持 `low`/`medium`/`high`。
- [x] `temperature`、`maxTokens`、`stop`、reasoning effort、标准 input/output usage、finish reason 和
  结构化 HTTP failure mapper 已有 JVM fixture 测试。
- [x] OpenAI semantics 安装专属 `KoogToolCallTerminalPolicy`；delta-only tool-call 流在
  `End("tool_calls")` 时补出 `ToolCallBlock`，随后才输出 usage 和 finish，公共层不绑定该字符串。
- [x] OpenAI Responses JVM/Desktop API template、Koog 原生参数、标准 usage/finish、文本 terminal、tool complete
  和 scripted cancellation/close fixture 已实现；loopback 默认 Ktor transport 也已验收 read-idle timeout
  与连接断开；Provider-native reasoning/replay state 的离线 codec/round-trip 已实现，真实网络 text/tool/
  cancellation/HTTP teardown 和 live persistence 尚未验收。Responses live 文本已到达 HTTP 200，但当前由
  Koog #2211 的 `response.instructions` 反序列化缺陷阻塞。

## 每步验收

```bash
./kotlin check -m llm-koog
./kotlin check -m jvm-app
./kotlin build
```

provider-neutral 契约当前不再包含 `@Ignore`。后续 Provider 能力必须用真实客户端 fixture、对应 Host
模块测试和全项目构建一起验收。
