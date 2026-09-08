# LLM Koog

`modules/llm-koog` 是 Harness `LlmAdapter` 到 Koog `PromptExecutor` 的转换层，对应 DSH 中
`packages/llm/llm-pi-ai` 的定位，但不引入 Koog Agent Runtime。

## 当前状态

本模块现在是可编译、可安装、生命周期完整的 provider-neutral Adapter：

- 依赖 Koog 1.1.1 的 `prompt-executor-model`，不依赖 `koog-agents` 聚合包。
- `KoogProviderRoute` 和 `KoogModelRoute` 提供代码安装的 Provider route、协议能力模板与默认模型目录；
  动态模式会为每份 settings 快照重新物化实际 `LLModel` 列表。
- `KoogLlmAdapter` 已实现 provider、model、context window、max output tokens 和 retry policy 元数据。
- `KoogLlmPlugin` 声明对 `LlmKey` 的 required inject，注册 Adapter，并在 dispose 时先撤销 route、再关闭
  `PromptExecutor`。
- `KoogRequestMapper` 已支持纯文本 system/user/assistant、消息 ID、temperature、maxTokens 和安全的
  provider-neutral 工具 schema 子集。
- `KoogOptionMapper` 是可注入的 Provider 扩展点；默认通用实现继续显式拒绝 stop 和
  reasoning effort，具体 Provider 参数不会被猜测。
- option、message、tool、reasoning、usage、finish、replay、failure 与 metadata 已拆成独立映射边界；未支持能力会
  显式失败，不会静默丢字段。
- 通用 failure mapper 不会把协程取消伪装成 Provider 错误；`KoogProviderFailureClassifier` 已接入
  Adapter 和 Runtime error-finish 路径，默认 classifier 不猜测任何 Provider/HTTP 异常语义。
- 共享 Koog 测试夹具和 `KoogStreamState` 已建立；显式 index 支持交错 block，空 index 按同类型隐式
  生命周期关联，无法无歧义合并的 index 模式切换使用稳定状态错误。
- tool-call id/name 保存在每次 collection 的 block state，不会由共享 mapper 在并发请求间串线；
  identity-only、arguments delta 与权威 complete 已实现。
- `KoogStreamTerminalMapper` 已独立保证 tool-call 收尾、成功状态校验、usage/finish 调度和 terminal 顺序；
  默认 `KoogToolCallTerminalPolicy` 不猜测任何 Provider finish 字符串，具体 Provider 必须显式注入决策；
  标准 input/output usage 已由默认 mapper 无损转换。
- `KoogStreamContext` 把 Harness provider/model 与 Koog client provider 分开传给 usage/finish mapper；
  两个 mapper 由 Provider Plugin 显式注入，不需要猜测全局字符串或 metadata。
- `KoogProviderSemantics` 把同一协议 API 的 option、usage、finish、message、tool schema、reasoning、
  tool stream、replay、failure 规则组成一个 bundle；外部 Provider route 可任意命名，registry 始终按模型
  解析出的 `api` 分派全部规则。
- `KoogMessageMapper`、`KoogToolMapper`、`KoogReasoningMapper` 和 `KoogToolStreamMapper` 是公开扩展点；
  它们收到目标 route/context，由 Provider semantics bundle 装配。默认实现覆盖已证明的 provider-neutral 子集，
  未证明的 Provider 私有语义继续显式拒绝。
- 没有专属 codec 的 route 对带 `ModelMessageSource.replayState` 的历史默认使用
  `KOOG_REPLAY_MAPPING_NOT_IMPLEMENTED` 失败；OpenAI Responses route 已安装版本化 replay writer/restorer，
  只恢复 reasoning 私有 metadata，不替换 durable content。
- 自定义 message mapper 也由 Adapter 统一经过 replay-aware wrapper；扩展工具历史不会绕过 replay 检查，
  每条历史最多恢复一次。
- `LLModel.maxOutputTokens` 保持模型能力含义，只有显式 `KoogModelRoute.defaultMaxTokens` 才成为调用默认值。
- 空成功响应使用核心 `EMPTY_RESPONSE`，caller cancellation/consumer early-stop 已有活动契约。
- 标准 usage、context、取消 integration、纯文本 reasoning、tool schema、tool history、tool stream 和多
  block/缺失 index 均已通过三平台契约，当前没有 ignored provider-neutral 测试。
- `KoogProviderSettings` 只持有 credential 引用，异步 `KoogProviderExecutorFactory` 在 Plugin 激活时
  解析凭据并创建 executor。
- 动态构造器会从 `SettingsKey` 的 `llm-koog` namespace 读取 provider map，并监听 `SettingsScope` 与
  `CredentialsKey`；新 settings/credential 先创建并校验完整 executor generation，成功后才切换到新请求。
  进行中的流继续持有旧 generation，空闲后关闭；无效配置或凭据不会破坏当前可用 generation。
- 动态插件默认使用空 settings：Provider Directory 保留已安装插件可配置的入口，活动 route 可以为零；
  添加、删除或清空 provider map 会原子替换活动 routes，清空时不创建 executor，后续配置可再次唤醒。
- 动态模型目录遵循 DSH 的目录合并规则：省略 `models` 或使用空列表会保留代码安装的目录；非空
  `models` 会替换该 route 的目录，并为新 id 创建真实 Koog `LLModel`；`modelOverrides` 只修改命中的安装
  模型并保留其余模型。非空 `models` 与 `modelOverrides` 不能同时出现，未知 override 会拒绝整份快照。
- `contextWindow` 和 `maxTokens` 会改变模型能力；模型条目显式写出的 `maxTokens` 同时成为请求默认值，
  `defaultMaxTokens` 只是在目录没有答案时补足输出能力。模型条目的 `input: []` 与省略相同，会继续继承
  安装目录、route 默认值，最后回退为 text-only。
- Plugin apply 在 route 注册失败时会回滚并关闭刚创建的 executor，不会移除或关闭已有冲突 route。
- `KoogStreamMapper` 已支持 provider-neutral 的 text、纯文本 reasoning、tool-call 与 stop terminal；
  provider-neutral reasoning 的 id/summary/encrypted、Provider 私有 usage 和非 `stop` finish 仍按稳定错误显式
  失败；OpenAI Responses 的专属 mapper 会把 reasoning 私有字段写入 replay state。标准 input/output usage
  已支持。对 OpenAI Chat Completions，只有 OpenAI semantics 安装的
  `OpenAiChatToolCallTerminalPolicy` 会让 terminal seam 在 `tool_calls` 时补齐 delta-only tool block。

- Desktop File-backed Host 的跨层 scripted 验收已完成：临时 settings/credentials 文件会在真实
  `HarnessHost` 中创建一个 `openai` route，其不同模型分别请求 Chat Completions/Responses 路径；文件
  watcher 会原子切换 generation，在途流继续使用旧 client，空闲旧 client 才关闭，非法 settings 保留最近
  可用 generation。该验收不发送真实网络请求，也不读取真实 API key。

Desktop bundle 静态安装 LLM 与 OpenAI Provider Plugin，但空配置下活动 route 为零，也不会创建 executor
或读取凭据。OpenAI 插件的 Chat Completions/Responses 只作为代码安装的 API 模板；`settings.yaml` 创建
`openai` profile，并在模型上选择 API。两种 API 分别使用 OpenAI
option/usage/finish/tool-terminal/failure semantics。`o3-mini` 在 Chat API 下只公开并接受
`low`/`medium`/`high` reasoning effort。配置构造与 Profile 构造仍不会读取 API key，只有
Plugin 激活时 factory 才解析 `OPENAI_API_KEY` 并创建 client。新增的离线 scripted
`KoogHttpClient.Factory` 已驱动真实 Koog `OpenAILLMClient`，覆盖两种 API 的请求路径/JSON、文本和 tool
SSE、usage/finish 顺序、consumer early-stop、caller cancellation、executor/client close 以及密钥不进入
body/path/request override headers。Responses 的 `response.completed` 负责终止，文本 terminal policy 会
补齐没有 `TextComplete` 的开放文本 block，函数工具以权威 `ToolCallComplete` 关闭 block。它仍不是网络验收：
生产 file-backed live harness 已覆盖 Chat 文本入口、可选 tool round trip、独立 Responses gate、临时凭据与
非凭据文件 secret audit。测试凭据已在 Responses-only 兼容端点执行：`gpt-5.6-sol` 到达 HTTP 200 后受
Koog #2211 的合法 string `response.instructions` 反序列化缺陷阻塞；该连接没有 Chat Completions 渠道，
所以 Chat/tool、外部 HTTP engine teardown 和 replay persistence 仍未验收，不能标记为生产可用。当前
WSLg/niri 环境的 Compose 首帧还受到 Skiko Linux GL context 创建限制；软件渲染可
保持进程运行，但尚未证明 niri 窗口映射。

## 源码层次

```text
src/
  plugin/               Public constructors, executor factory, and activation runtime
  adapter/              Harness LlmAdapter implementation
  config/               Persisted settings and model profiles
  catalog/              Installed routes and model materialization
  lifecycle/            Immutable generation reload and leasing
  semantics/
    request/             History, options, tools and request mapping
    stream/              Block state, stream frames and terminal mapping
    replay/              Provider-native history replay seam
  error/                Stable errors and failure classification
```

目录表示实现职责，公开 Kotlin package 暂时保持 `im.hikaru.harness.llm.koog`，避免把内部整理变成无意义的
全仓 API 迁移。`KoogLlmPlugin` 只保留公开构造入口，`KoogLlmPluginRuntime` 负责 activation、reload 和
adapter registration；request message mapping 又拆成接口、纯文本、tool history、replay protection 和
system factory 文件。Provider-specific 实现不得回流到这些目录。

## 依赖方向

```text
Harness GenerateOptions / StreamChunk
                 ↓
          KoogLlmAdapter
          ↙             ↘
KoogRequestMapper    KoogStreamMapper
          ↓             ↑
     PromptExecutor -> StreamFrame
```

客户端、Session、AgentLoop 和 wire contracts 只能看到 `modules/llm` 类型，不能依赖 Koog 类型。
`PromptExecutor` 的具体创建由独立 Provider Plugin 负责，凭据不进入 `KoogProviderRoute` 或
`KoogProviderSettings`。Host 只安装 Provider Plugin；route、catalog、factory 和完整
`KoogProviderSemantics` 必须在同一个 Provider 模块内组装。未显式提供 semantics 时，
`KoogLlmPlugin` 只支持通用安全子集，不会自动开启 Provider 专属能力。

## 动态模型配置

Desktop 的 file-backed Profile 读取 `settings.yaml` 顶层的 `llm-koog` namespace。例如，一个 Provider
profile 可以让不同模型使用不同 OpenAI API：

```yaml
llm-koog:
  providers:
    openai:
      displayName: OpenAI
      api: openai-chat-completions
      baseUrl: https://provider.example/v1
      credential:
        name: OPENAI_API_KEY
      models:
        - id: provider-chat-model-id
          api: openai-chat-completions
          name: Provider Chat Model
          contextWindow: 65536
          maxTokens: 4096
          input: [text]
          reasoningEfforts:
            low: low
            medium: medium
            high: high
          defaultReasoningEffort: medium
        - id: provider-responses-model-id
          api: openai-responses
          name: Provider Responses Model
          input: [text]
```

局部修正安装目录时，不写非空 `models`，改用：

```yaml
modelOverrides:
  o3-mini:
    contextWindow: 131072
    maxTokens: 8192
```

`reasoningEfforts: false` 会移除继承的 reasoning 能力。当前 Koog Provider semantics 仍由代码安装，配置只
能声明它已经会映射的 reasoning id，因此对象值必须与 key 相同；wire alias 要等对应协议 mapper 提供显式
支持，不能被配置静默伪装成已支持。

保存有效文件后，新目录、新 executor 和新凭据会作为一个 generation 原子生效；新请求立即看到新目录，
已准备或正在流式执行的请求继续使用旧 generation。替换后不再存在的模型会以 `UNKNOWN_MODEL` 失败。文件
语法错误、未知 override、缺失凭据或 executor 创建失败都会保留最近一次成功 generation。

settings 可以新增、删除或清空 Provider route，也可以让模型选择代码已安装的 API；它不能定义新的协议
实现。Provider Directory 与活动 routes 独立，因此空配置仍能告诉配置界面 OpenAI 插件可用。

## 后续实现顺序

1. 已完成：纯文本请求、Provider-neutral 生命周期、failure 边界、流状态和所有后续契约入口。
2. 已完成：最小 text stream、terminal/context 主路径和两种取消路径。
3. 已完成：通用 usage、纯文本 reasoning、tool schema、工具历史、tool-call stream、交错显式 block 和
   顺序隐式 block。
4. 已完成：为 OpenAI Responses 定义最小版本化 replay state、stream writer 和严格 restorer；其他 Provider
   仍按选定协议分别实现。
5. 只针对该 Provider 实现并注入 option、usage、finish、message、tool schema、reasoning、tool stream、
   replay 和 failure 的精确语义；OpenAI JVM/Desktop 已完成 Chat Completions/Responses 的已证明子集，
   Responses provider-native replay 已通过离线 codec 契约。
6. 增加该 Provider 的 executor factory；OpenAI JVM/Desktop factory 已完成，API key 只由 Host credentials
   在激活时解析。
7. 已完成 settings/credential/model catalog 的动态 generation reload、Provider Directory、route 动态增删与
   空配置休眠。
8. 先用真实 Koog client + scripted HTTP seam 做无网络 fixture 验收，再用测试凭据做 live text/tool、取消和
   资源关闭验收。

每一步都先写 mapper 的纯 KMP 测试，再接入一次 Runtime 流式测试。不要在 Adapter 内实现 retry、Session
持久化或 AgentLoop；这些仍由各自模块负责。

统一的逐阶段解锁顺序见 [IMPLEMENTATION_PATH.md](IMPLEMENTATION_PATH.md)。
DSH 源码级职责对照与有意差异见 [DSH_BOUNDARY_AUDIT.md](DSH_BOUNDARY_AUDIT.md)。
更细的执行清单见 [TASKS.md](TASKS.md)。
请求通用字段与 Provider options 见 [REQUEST_MAPPING_GUIDE.md](REQUEST_MAPPING_GUIDE.md)。
当前文本流练习见 [TEXT_STREAM_GUIDE.md](TEXT_STREAM_GUIDE.md)。
Provider 专属 usage 映射见 [USAGE_MAPPING_GUIDE.md](USAGE_MAPPING_GUIDE.md)。
纯文本 reasoning 映射见 [REASONING_MAPPING_GUIDE.md](REASONING_MAPPING_GUIDE.md)。
Provider-native replay 闭环见 [REPLAY_MAPPING_GUIDE.md](REPLAY_MAPPING_GUIDE.md)。
工具 schema、历史消息和 tool-call 流见 [TOOL_MAPPING_GUIDE.md](TOOL_MAPPING_GUIDE.md)。
Provider/HTTP 错误分类见 [FAILURE_MAPPING_GUIDE.md](FAILURE_MAPPING_GUIDE.md)。
Host 凭据、client、executor 与 Desktop 装配见
[PROVIDER_ASSEMBLY_GUIDE.md](PROVIDER_ASSEMBLY_GUIDE.md)。
