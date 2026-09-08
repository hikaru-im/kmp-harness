# Koog Provider 装配实现指南

Provider 装配负责把 Host 配置和凭据解析成 Koog client/`PromptExecutor`，再交给
`KoogLlmPlugin` 管理生命周期。它不拥有请求转换、流转换、重试、Session 持久化或 AgentLoop。

## 当前已经建立的生命周期

```text
持久化 KoogProviderSettings（只有 credential ref）
                         ↓ Plugin 激活
Host KoogCredentialResolver ──→ KoogProviderExecutorFactory
                                      ↓
                              PromptExecutor / clients
                                      ↓
                     KoogLlmPlugin 替换活动 routes
```

- Desktop 静态安装 Provider Plugin；没有 Koog 配置时活动 route 为零，不解析凭据、不创建 executor；
- 静态 settings 构造器要求已解析 route 与 settings 的 Provider id 集合完全一致且均无重复；动态构造器
  接受空 provider map，并从已安装 API 模板物化任意外部 route 名；
- Provider semantics 与代码安装的 API 集合完全一致，而不是与外部 Provider 名一致；
- settings 会按 route 顺序形成不可变列表快照，再传给 factory；
- 凭据只在 Plugin 激活期间由 factory 主动解析，构造 Profile/Plugin 不读取凭据；
- Plugin 激活或更新时先创建完整 generation，再替换 Directory 与 routes；失败会恢复旧快照；
- EffectScope 逆序释放：先撤销 Adapter 注册，再关闭 executor；
- 同一 route 的 option、usage、finish、message、tool schema、reasoning、tool stream、replay 和 failure
  组件由一个 `KoogProviderSemantics` bundle 提供；默认 bundle 不猜测具体 Provider 能力。

若 Provider 不需要持久化 settings，可使用接收 `KoogPromptExecutorFactory` 的低层构造器；不要传一份
与 routes 不匹配的空 settings 来绕过校验。

## 第一步：选择实现所在的平台模块

`modules/llm-koog` 只直接依赖 Koog 的 `prompt-executor-model`，保持 Provider-neutral。真实 factory
应放在需要它的平台 Host 或独立 Provider 模块，并显式声明所使用的 Koog client 依赖：

- Desktop/JVM factory 放在 JVM 模块；
- Android/iOS 若也需要该 Provider，分别确认该 client artifact 和 HTTP engine 真正支持目标平台；
- 不能用 JVM 构建通过来声称 iOS Provider 已可用。

不要依赖传递依赖碰巧暴露的 client 类型；源码直接 import 的 artifact 必须直接声明。

## 第二步：定义 route 与 settings

每个 Harness Provider route 对应一条 settings：

```kotlin
val routes = listOf(
    KoogProviderRoute(
        id = "example",
        name = "Example",
        models = listOf(
            KoogModelRoute(model = exampleKoogModel),
        ),
    )
)

val settings = listOf(
    KoogProviderSettings(
        provider = "example",
        baseUrl = "https://provider.example",
        credential = KoogCredentialRef("example-api-key"),
    )
)
```

约束：

- `provider` 是 Harness route id，不是密钥名；
- `LLModel.provider` 必须能被最终 Koog client 路由，通常与 client 的 `llmProvider()` 相同；
- `LLModel.maxOutputTokens` 是模型能力上限，不会自动成为请求 cap；只有部署明确需要默认 cap 时才设置
  `KoogModelRoute.defaultMaxTokens`，且不得超过能力上限；
- `baseUrl` 不得包含 user-info、API key 或敏感 query；scheme、host、路径规则由 factory 按 Provider
  协议验证，不能自动猜补；
- 模型目录只声明已经验证的模型及 metadata，不能把 client “可能支持”的模型全部注册。

Harness route id 与 `LLModel.provider.id` 若故意不同，必须写集成测试证明 option/error dispatcher 和
Koog executor 都选择了正确分支。

## 第三步：先写 factory 生命周期测试

在创建真实 client 前，使用记录型替身覆盖：

1. Profile 和 Plugin 构造时 factory/credential resolver 调用次数均为零；
2. Plugin 每次激活只调用一次 factory；
3. factory 收到的 settings 顺序与 routes 一致，且不受调用方之后修改原列表影响；
4. 只解析当前 Provider 明确需要的 credential ref；
5. `resolveRequired()` 对 null/blank 使用 `INVALID_CREDENTIAL`，不会注册 Adapter；
6. uninstall 时 Adapter 已不可见且 executor 恰好关闭一次；
7. factory 抛错时不留下 route、client 或后台协程。

如果 factory 在返回 executor 之前已经创建了多个 client，随后某一步失败，factory 自己必须关闭已创建
资源；此时 Plugin 尚未取得 executor，无法代为清理。

## 第四步：实现单 Provider factory

```kotlin
class ExampleProviderExecutorFactory : KoogProviderExecutorFactory {
    override suspend fun create(
        settings: List<KoogProviderSettings>,
        credentials: KoogCredentialResolver,
    ): PromptExecutor {
        val setting = settings.single { it.provider == "example" }
        val reference = requireNotNull(setting.credential) {
            "Example credential reference is not configured"
        }
        val apiKey = credentials.resolveRequired(setting.provider, reference)

        // 1. 严格验证 baseUrl。
        // 2. 用 apiKey 创建具体 Koog LLMClient。
        // 3. 把 client 交给 PromptExecutor；返回后由 Plugin 拥有并关闭。
        // 4. 不记录 apiKey，不把它写回 setting，也不缓存 resolver。
        TODO("由选定 Koog Provider client 决定")
    }
}
```

Desktop/JVM 可以使用 Koog `PromptExecutor.builder().addClient(...).build()`。Builder 会按
`LLModel.provider` 选择 client；多个 client 具有相同 Koog Provider 时会切换成路由/负载均衡 executor。
当前 Harness settings 没有表达端点身份、粘性或 replay 规则，因此不要意外注册同 Provider 的多个
client。

不要配置 Koog fallback model。Fallback 会让 Harness 记录的 requested provider/model 与实际执行模型
不一致；真正的模型切换必须在拥有持久步骤和审计信息的上层明确实现。

同样必须把 Koog client/SDK 的内部 retry 设为零。一次 `LlmAdapter.stream()` 只能产生一次 Provider
attempt；重试、backoff 和第二次调用由 retry Plugin 在持久步骤边界负责。若所选 client 无法禁用内部
retry，该 client 在证明 attempt 可审计前不能标为可用。

## 第五步：同时安装 Provider 语义 mapper

真实 client 只是传输层，同一 Provider Plugin 还必须组装完整语义 bundle：

```kotlin
KoogLlmPlugin(
    routes = routes,
    providerSettings = settings,
    credentials = hostCredentialResolver,
    providerExecutorFactory = ExampleProviderExecutorFactory(),
    providerSemantics =
        listOf(
            KoogProviderSemantics(
                provider = "example",
                optionMapper = ExampleProviderOptionMapper(),
                usageMapper = ExampleProviderUsageMapper(),
                finishReasonMapper = ExampleProviderFinishReasonMapper(),
                messageMapper = ExampleProviderMessageMapper(),
                toolMapper = ExampleProviderToolMapper(),
                reasoningMapper = ExampleProviderReasoningMapper(),
                toolStreamMapper = ExampleProviderToolStreamMapper(),
                replayRestorer = ExampleProviderReplayRestorer(),
                failureClassifier = ExampleProviderFailureClassifier(),
            )
        ),
)
```

Provider 模块应再用自己的 `SimplePlugin` 包装上述 `KoogLlmPlugin`，并在该模块内持有 route、
默认 catalog、settings defaults、client factory 和 mapper。Desktop/Mobile composition root 只安装这个
Provider Plugin，不复制任何 registration 或 mapper 表。

四个 request/stream 扩展点是公开接口，均收到目标 route 身份：`KoogMessageMapper` 通过
`KoogMessageMappingContext.targetContext` 看到目标 route/model，`KoogToolMapper`、`KoogReasoningMapper`
和 `KoogToolStreamMapper` 通过 `KoogStreamContext` 看到同一目标 route。message mapper 是完整消息契约；
registry/adapter 会自动给自定义 message mapper 加 replay-aware wrapper；它应负责生成 Koog parts，不要自己
把 replay state 当作正文替换，也不要绕过 wrapper 直接在请求外持久化 Provider metadata。

请求字段按 [REQUEST_MAPPING_GUIDE.md](REQUEST_MAPPING_GUIDE.md) 实现；错误按
[FAILURE_MAPPING_GUIDE.md](FAILURE_MAPPING_GUIDE.md) 分类；usage 按
[USAGE_MAPPING_GUIDE.md](USAGE_MAPPING_GUIDE.md) 实现。流 mapper 会把
`KoogStreamContext(provider, model, koogProvider, api)` 同时交给 usage 与 finish mapper，避免 Harness
route、协议 API 和 Koog client provider 被混为一谈。

Koog 客户端不会统一 finish 字符串。Provider mapper 必须根据该客户端的已验证枚举/响应 fixture 映射
stop、max-token 和 tool-call 终止，未知值继续失败。没有这些组件时，默认实现仍会拒绝 Provider 专属
options、Provider 私有 usage 和非 `stop` finish，并把未知 client 异常归为 `UNKNOWN`；标准 input/output
usage 已由通用 mapper 支持。

`providerSemantics` 必须与代码安装的 API 模板精确一一对应；bundle 的 `provider` 字段当前承载 API id，
不是 Harness route id，也不是 `LLModel.provider.id`。多个 Harness routes 可以引用同一个 API bundle，并通过
`KoogStreamContext.provider`、`api`、`koogProvider` 分别看到外部 route、协议和 Koog client 身份。

## 第六步：分层验收

先做无网络验收：

```bash
./kotlin check -m llm-koog
./kotlin check -m jvm-app
./kotlin build
```

再做真实 Provider 验收，且必须使用测试凭据：

1. 启动 Host，确认只在 Plugin 激活时读取一次凭据；
2. 发出一个最小文本流请求，验证 provider/model、请求参数、chunk 顺序和 finish；
3. 验证一次已知 Provider 错误的结构化分类；
4. 取消请求，确认取消未变成 error finish；
5. 提前停止消费，确认 Provider 网络读取立即结束；分别验证 request timeout 与 stream read-idle timeout；
6. 停止 Plugin/Host，确认 executor/client 和 HTTP engine 关闭；
7. 审计日志、序列化 settings、异常和测试报告中没有 API key。

构建和替身测试不是 live acceptance。最小文本流虽已完成，Provider 仍不能在 reasoning、tool、usage、
错误分类、取消 teardown 和真实 client 证据齐全前标记为可用。

Plugin 已有活动 rollback 契约：若 executor 创建成功后 route 注册冲突，新 executor 会被关闭，原有
route/executor 不受影响。真实 factory 仍必须让 `PromptExecutor.close()` 完整关闭其 HTTP engine、连接池和
后台协程；替身的 `closed` 标志不能外推为真实 client teardown 已验证。

动态 Profile 已使用不可变 generation：settings 或 credential 更新先创建新 executor，成功后新请求切换；
已准备或在途请求继续租用旧 generation，归还最后一个 lease 后才关闭旧 client。

## 当前 OpenAI Provider Plugin

`modules/llm-koog-openai` 在一个 Provider Plugin 中安装两个明确隔离的 API 模板：Chat Completions 的
`openai-chat-completions` 与 Responses API 的 `openai-responses`。Provider Directory 只公开 `openai`，
`settings.yaml` 决定活动 route，并由模型 `api` 选择模板。两种 API 共用
`OpenAiPromptExecutorFactory`/Koog `OpenAILLMClient`、credential ref 和 base URL，但拥有独立 semantics。

- `OpenAiPromptExecutorFactory` 显式使用 `prompt-executor-openai-client`、`http-client-core` 和
  `http-client-ktor`，创建 `OpenAILLMClient` 后交给 `PromptExecutor`；不配置 fallback model，也不增加
  SDK 内部 retry。
- `OpenAiClientFixtureTest` 通过自定义 `KoogHttpClient.Factory` 驱动上述真实 `OpenAILLMClient`，验证两种 API
  的请求 JSON、`v1/chat/completions`/`v1/responses` 路径、文本/tool SSE、usage/finish、consumer early-stop、
  caller cancellation、`PromptExecutor.close()` 到 HTTP client close 的生命周期，以及 API key 不进入
  body/path/request override headers。fixture 完全离线，不代表 OpenAI 网络或 HTTP engine live teardown 已验收。
- `OpenAiKoogPlugin` 一次组装两个 API templates/semantics；Desktop 只安装插件，空 settings 下不读取 API
  key、不创建 executor，也不持有 OpenAI mapper。
- 静态 Profile 可用 `EnvironmentKoogCredentialResolver` 在 Plugin 激活时把 `openai-api-key` 映射到
  `OPENAI_API_KEY`；动态 Profile 使用共享 credentials service。
- base URL 只接受无 user-info/query/fragment 的 `http(s)` URI；API key 不写入 settings、日志、wire 或
  replay。
- 已有 JVM fixture 覆盖 Chat 参数、`o3-mini` 的 `low`/`medium`/`high` reasoning 约束、标准 usage、
  `stop`/`length`/`tool_calls` finish、结构化 HTTP failure 和 credential 生命周期；OpenAI semantics
  显式安装 `OpenAiChatToolCallTerminalPolicy`，再由共享 terminal seam 完成 `End("tool_calls")` 的
  delta-only tool block 收尾，公共层不硬编码该 finish 字符串。

这不是完整 live readiness 证明。生产 file-backed Responses 已使用测试凭据到达外部 HTTP 200，并精确复现
Koog #2211；当前测试连接没有 Chat Completions 渠道，所以 Chat text/tool、外部 HTTP engine/live teardown、
生产 retry header，以及 replay 的真实网络/live persistence 仍未验证。OpenAI Responses 已有离线
provider-native replay codec
和严格 round-trip/mismatch 测试。Responses tool argument delta 目前由 Koog 原生 client 解码但不直接
发出 Harness delta，使用权威 complete frame 形成无损最终 block。
没有测试凭据时不要把 JVM fixture/build 结果描述成 OpenAI 网络验收。
