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

## 调用语义

`LlmRuntime.stream()` 返回冷 `Flow<StreamChunk>`。Runtime 在进入 waterfall 前复制请求集合，按
`provider` 解析 Adapter，并把 Adapter 抛出的非取消异常转换成终止 `FinishChunk`。Kotlin
协程的 `CancellationException` 继续传播，以保持结构化并发；提供方主动中止可以使用
`LlmException(code = ABORTED)` 表达为 `AbortedFinishReason`。

`LlmStreamEvent` 是同步 onion middleware，返回的值仍是冷 Flow：监听器可以包装 `next()` 返回的
Flow，或直接返回自己的 Flow 短路 Adapter。监听器和下游 Consumer 的错误不会被当作提供方失败。

`prepareCall()` 在一次解析中捕获精确 Adapter registration、模型容量、适配器默认值和重试策略。
返回的 `PreparedLlmCall` 只能使用一次；route 后续替换不会改变已经准备的调用。

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

下一层 `modules/llm-koog` 已建立可编译的 Adapter 与 Plugin 骨架。它将把 Harness
`GenerateOptions` 转成 Koog 请求，并把 Koog 流映射回这一模块的 `StreamChunk`；客户端和 wire
contracts 不直接依赖 Koog 类型。
