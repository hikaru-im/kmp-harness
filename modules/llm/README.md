# LLM

`modules/llm` 是提供方无关的模型调用 seam，对应 DeepSeek Harness 的
`packages/llm/llm`（`@deepseek-ai/dsh-llm`）。它定义 Session、AgentLoop、工具系统和提供方
Adapter 共同依赖的稳定词汇，但不连接任何真实模型。

## 职责

```text
AgentLoop / Session / Tools
            ↓
        LlmRuntime
            ↓ provider route
        LlmAdapter
            ↓
 llm-koog 等提供方实现
```

本模块拥有：

- `MessageId`、`CallId`、`ProviderRequestId` 和 `ReasoningEffortId`。
- `Message`、消息来源以及 text、reasoning、tool-call、tool-result 内容块。
- `LlmCallConfig`、`GenerateOptions`、`ToolSchema` 和精确模型元数据。
- `StreamChunk`、`FinishReason`、`TokenUsage` 和 `BlockAssembler`。
- `LlmFailure`、稳定错误码及适配器异常归一化。
- `LlmAdapter`、`LlmRuntime`、`LlmKey`、`LlmPlugin` 和 `llm/stream` waterfall。
- provider route 的原子注册、替换、释放和一次性 `PreparedLlmCall`。
- 厂商贡献 registry、未激活连接的模型发现和按连接创建 Adapter 的公共入口。
- 模式、强度、预算、执行模式独立表达的 `ReasoningOptions` 和三态能力校验。
- 提供方重试策略的数据约定；真正执行策略的 `llm-retry` Plugin 不在本模块。

## Runtime Service

安装 `LlmPlugin` 后，当前 Context 可以通过 `context.llm` 取得 `LlmRuntime`：

```kotlin
val fiber = runtime.install(LlmPlugin())
val llm = runtime.context.llm
```

提供方 Plugin 将 Adapter 注册句柄加入自己的 `EffectScope`：

```kotlin
scope.add(
    context.llm.registerAdapter(
        providers = listOf("koog"),
        adapter = koogAdapter,
    )
)
```

Plugin dispose 后，句柄只移除自己当前拥有的 routes；旧句柄不会删除后来注册的同名 route。
`replace()` 会先验证整组候选，再一次性提交，因此冲突不会留下半注册状态，也不会产生可观察的空档。

## 厂商贡献与连接实例

生产接入使用 `LlmProviderContribution` 聚合 descriptor、discovery、capabilityRules 和
adapterFactory。厂商 Plugin 把 `registerProviderContribution()` 返回的句柄加入已有
`EffectScope`；它不是新的插件加载系统。一份贡献可创建多个独立连接实例。

`discoverModels(vendorId, access)` 允许查询尚未激活的连接，按 descriptor 校验 vendor/protocol。
返回的 `LlmCatalogSnapshot` 区分完整、部分和未知目录；目录事实不等于 Adapter 的可执行能力。
缺少 discovery 或 adapterFactory 时，分别返回稳定的“不支持发现”或“尚无可执行适配器”错误，
不会退回某个默认厂商。发现中贡献被替换或卸载时，旧结果不会发布。

`createProviderAdapter(vendorId, access)` 是厂商工厂调用入口。它捕获当前贡献的能力规则，
对精确模型解析和 prepared call 使用同一规则版本。Host 将返回实例和 route 句柄加入自己的
scope；配置重载先创建候选，再提交 routes，最后释放旧实例。`registerAdapter()` 仍是底层
route 注册操作，也用于不连接远端的测试 Adapter。

贡献注销/替换会使它创建的实例退休：新调用被拒绝，已准备调用持有的租约继续有效；最后一个
租约释放后才关闭底层资源。注销只影响该句柄拥有的贡献，旧句柄不会删除后来安装的同名插件。
贡献替换只原子更新 registry；调用路由切换仍由 Host 的重载协调器负责。

`LlmProviderConnection` 可以持久化 endpoint、协议、服务变体、region、workspace、API version
和凭据引用。真实 key 只出现在不可序列化、诊断输出脱敏的 `LlmProviderAccess` 中；工厂
收到的配置模型和 timeout 是该连接的快照。发现插件应只把公开目录事实放入 metadata。

## 思考意图与兼容

新调用使用 `LlmCallConfig.reasoning` / `GenerateOptions.reasoning`。`ReasoningOptions()`
明确要求全部跟随厂商，阻止模型旧默认值回填；`reasoning = null` 为旧配置和历史日志保留
原来的 `reasoningEffort` 默认解析。新旧字段同时存在时必须表达相同 effort，否则拒绝调用。
上层应在生成 `ReasoningOptions` 前完成应用、模型和会话默认值的继承。

mode、effort、budgetTokens、executionModeId 是独立控制。`provider-default` 和 null 表示
省略，不代表关闭、自动或某个默认档位。预算 0 只有该模型声明允许时才合法；core 不把它
统一翻译成关闭，也不全局禁止 effort 与 budget、disabled 与 effort 的组合。

`ReasoningCapabilities` 对每个维度区分 supported、unsupported、unknown，并通过有限类型化
约束表达型号特有组合。显式请求未知能力返回 `CAPABILITY_UNKNOWN`；省略参数不需要猜测
能力。旧 reasoning 目录可提供 effort 兼容能力，但新 reasoningCapabilities 优先决定是否
可执行。`maxOutputTokens` 是输出容量，与请求默认值 `defaultMaxTokens` 分开保存。

## 调用语义

`LlmRuntime.stream()` 返回冷 `Flow<StreamChunk>`。Runtime 在进入 waterfall 前复制请求集合，按
`provider` 解析 Adapter，并把 Adapter 抛出的非取消异常转换成终止 `FinishChunk`。Kotlin
协程的 `CancellationException` 继续传播，以保持结构化并发；提供方主动中止可以使用
`LlmException(code = ABORTED)` 表达为 `AbortedFinishReason`。

`LlmStreamEvent` 是同步 onion middleware，返回的值仍是冷 Flow：监听器可以包装 `next()` 返回的
Flow，或直接返回自己的 Flow 短路 Adapter。监听器和下游 Consumer 的错误不会被当作提供方失败。

`prepareCall()` 在一次解析中捕获精确 Adapter registration、模型容量、适配器默认值和重试策略。
返回的 `PreparedLlmCall` 只能使用一次；route 后续替换不会改变已经准备的调用。
它也持有脱离原集合的 reasoningCapabilities/规则版本；尚未组装的消息不属于此时的快照。
`stream()` 直接入口同样校验显式思考能力；prepared call 的配置不能在 middleware 中再次改写。

## 与 DSH 的 KMP 映射

- DSH `AsyncIterable<StreamChunk>` 映射为 `Flow<StreamChunk>`。
- TypeScript branded string 映射为可序列化 Kotlin value class。
- TypeScript declaration merging 映射为当前核心 sealed vocabulary；出现真实生产方后再增加新变体。
- Adapter registry 使用 `Mutex` 保护多协程并发；DSH 依赖 JavaScript 单线程执行模型。
- Session 标识使用低依赖的 `LlmSessionId`，避免未来形成 `llm <-> session` 循环依赖。

## 明确不包含

- Koog、DeepSeek 或其他真实 Provider SDK。
- Agent、AgentLoop 或 Session 日志。
- 重试执行、token-meter、compaction 或持久化。
- 凭据存储、Settings、模型配置界面和远程协议。
- 图像内容块；它需要先建立 Attachment 所有权和跨平台持久引用。

下一层 `modules/llm-koog` 是可选的 SDK 复用层，负责将 Harness `GenerateOptions` 转成 Koog
请求，并将 Koog 流映射为 `StreamChunk`。厂商插件也可提供原生 `LlmAdapter`；公共契约与
客户端 wire contracts 均不依赖 Koog。
