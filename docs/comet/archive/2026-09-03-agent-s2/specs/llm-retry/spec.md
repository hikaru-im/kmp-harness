# llm-retry Plugin 完整规格

`llm-retry` 是独立 Plugin，监听 `agent/request-error`，不修改 `LlmAdapter` 或 `LlmRuntime.stream()`。它读取 request 开始时绑定的 `RetryPolicy`，按稳定错误 code、最大次数、provider retry-after、指数退避、jitter、取消和 dispose 执行。

每次决定重试必须先 append `llm/retry`（包含 Agent、Session、attempt、failure、policy snapshot 及固定的 `notBeforeEpochMilliseconds`），等待结束且准备下一次 attempt 前 append `llm/retry-started`。成功、不可重试、达到上限、取消和 dispose 都写入可恢复的终态事件。Retry state 由 Session Persistence 保存，重启时不会重复已提交 attempt 或跳过已提交 retry-started；AgentLoop 通过 provider-neutral resume seam 继续尚未写入 `request/header` 的 attempt。

策略默认值沿用当前 `NormalRetryPolicy`；是否采用 DSH 的 maxRetries=5 由该阶段单独确认并测试。

### Scenario: 持久化重试

WHEN 第一次 attempt 返回可重试 `SERVER`，THEN 写入 retry、执行退避、写入 retry-started、重新准备并执行下一次 attempt；重启后从 retry 事件恢复剩余次数，成功后不再产生额外 attempt。
