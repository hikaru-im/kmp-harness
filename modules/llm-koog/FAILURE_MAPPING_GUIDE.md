# Koog 错误映射实现指南

错误映射只负责把一次 Provider 调用已经发生的失败转换成 `LlmFailure` 事实。它不执行重试、不生成
第二次请求，也不把取消改写成普通错误；重试决策仍由 Harness 的 retry policy 和后续 retry Plugin
负责。

## 已经接好的通用顺序

`KoogLlmAdapter` 使用 Flow `catch` 只捕获上游请求、Provider 和映射错误。处理顺序固定为：

1. `CancellationException` 原样抛出，classifier 不会被调用；
2. 已有 `LlmException` 原样保留全部稳定字段，classifier 不会覆盖它；
3. 调用 `KoogProviderFailureClassifier`；识别成功时使用其 `LlmFailure`；
4. classifier 返回 `null` 时回退到 `UNKNOWN`，保留非空异常消息；
5. Adapter 抛出带 mapped failure 的 `LlmException`，`LlmRuntime` 再产生唯一 error finish。

这里使用 Flow `catch` 而不是包住 `emitAll` 的 `try/catch`，因此下游 collector 自己抛出的异常不会被
误判为 Provider 失败。

Classifier 同时收到只包含 `provider/model` 的 `KoogFailureContext`。它不包含消息正文、工具参数或
凭据，足以在共用一个 `PromptExecutor` 时选择正确 Provider 规则。

## Koog 1.1.1 能提供的证据

Koog 通用客户端有两层常见异常：

- `LLMClientException`：只有格式化消息和 cause，没有 HTTP status、header 或结构化错误体；
- `KoogHttpClientException`：公开 `clientName`、`statusCode`、`errorBody` 和 cause。

默认 Ktor Koog HTTP client 在非成功响应中保存 status/body，但不把响应 headers 放进
`KoogHttpClientException`。因此仅凭该异常不能可靠恢复 `Retry-After` 或 request id；字段必须保持
`null`。若产品要求它们，需要 Provider client/wrapper 在丢失 header 前捕获并抛出自己的结构化异常。

## 第一步：先确定 Provider 错误协议

针对一个已选 Provider，列出并保存测试 fixture：

- HTTP status；
- Provider JSON 错误 code/type；
- 可安全展示的 message；
- quota 与临时 rate limit 的区别；
- context-window 错误的结构化标记；
- request-id 和 retry-after 的真实 header 名称；
- timeout、DNS、连接失败等 transport cause 类型。

fixture 应去除 API key、用户 prompt、工具参数和个人数据。不要把完整生产错误 body 提交进仓库。

## 第二步：先写 classifier 纯测试

至少覆盖以下矩阵：

| 输入 | 期望 code | 关键字段 |
|---|---|---|
| 明确的临时 429 | `RATE_LIMIT` | status=429；有证据时才写 retry delay |
| 明确的额度耗尽 | `QUOTA` | 不应按临时 rate limit 重试 |
| 明确的上下文超限 | `CONTEXT_WINDOW_EXCEEDED` | 保留 status 和安全 message |
| 已证明的凭据错误 | `INVALID_CREDENTIAL` | 不把所有 403 一概归为凭据错误 |
| Provider 5xx | `SERVER` | status 保留 |
| 已证明的超时 | `TIMEOUT` | 没有 HTTP status 时保持 null |
| 已证明的网络失败 | `TRANSPORT` | 不解析本地化 message 猜类型 |
| 未识别异常/其他 Provider | `null` | 交给通用 UNKNOWN fallback |

另外验证：取消与 `LlmException` 不进入 classifier；畸形/空 error body 不会导致 classifier 自己崩溃；
message 不包含凭据和原始敏感 body。

`isQuotaExceededError()` 和 `isContextWindowExceededError()` 可作为兼容 OpenAI-like 文本的保守辅助，
但结构化 Provider code 应优先。没有命中时返回 `null` 或选择有证据的通用 HTTP 分类，不要靠模糊
字符串把永久错误标成可重试错误。

## 第三步：实现单 Provider classifier

```kotlin
class ExampleProviderFailureClassifier : KoogProviderFailureClassifier {
    override fun classify(
        error: Throwable,
        context: KoogFailureContext,
    ): LlmFailure? {
        if (context.provider != "example") return null

        // 1. 从已知异常/cause 中读取结构化 status 和 Provider body。
        // 2. 安全解析 Provider error code；解析失败不能抛异常。
        // 3. 按测试矩阵返回 LlmFailure。
        // 4. 不认识时返回 null，让通用层产生 UNKNOWN。
        TODO("由具体 Provider 错误协议决定")
    }
}
```

如果同时支持多个 Provider，使用显式 dispatcher 按 `context.provider` 选择各自 classifier。不要让一个
OpenAI-compatible 规则无条件处理所有 `KoogHttpClientException`。

## 第四步：与 retry policy 对齐

默认 normal policy 只重试：`EMPTY_RESPONSE`、`RATE_LIMIT`、`SERVER`、`TIMEOUT`、
`TRANSPORT`。Classifier 新增或修改 code 时必须同时检查 route 的 `retryPolicy`：

- 永久性认证、额度、请求格式、上下文超限不能误用上述可重试 code；
- `providerRetryAfterMs` 只有在 Provider 明确返回正数延迟时才设置；
- Adapter 内禁止 sleep 或再次调用 executor。

## 第五步：安装并做边界集成测试

```kotlin
KoogLlmPlugin(
    executorFactory = executorFactory,
    routes = routes,
    providerSemantics =
        listOf(
            KoogProviderSemantics(
                provider = "example",
                failureClassifier = ExampleProviderFailureClassifier(),
            )
        ),
)
```

settings/credential 构造器也接受同名 `providerSemantics` 参数。使用抛出确定性异常的测试 executor
验证最终
`ErrorFinishReason.failure` 完整保留 code、status、retry delay 和 request id；同时验证 executor 只调用
一次。默认不传 classifier 时，未知 Provider 异常仍应为 `UNKNOWN`。

## 验收

```bash
./kotlin check -m llm-koog
./kotlin build
```

只有具体异常 fixture、纯 classifier 测试和 Runtime error-finish 集成测试都通过后，才能声称该
Provider 的错误分类可用于重试。真实 header 当前拿不到时，应明确保留 null，而不是从 message 猜值。
