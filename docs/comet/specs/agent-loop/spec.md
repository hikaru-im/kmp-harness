# AgentLoop 模块完整规格

## 模块边界

`modules/agent-loop` 只向 `AgentRegistry` 注册默认 `AgentFactory`，不向调用方暴露具体 loop 实现类型。它依赖 `runtime`、`llm`、`session`、`agent`；不依赖 Provider/Koog 内部类型。

## 文本闭环

每个 follow-up 独占一个 turn；首版无工具，一个 turn 正常只有一个 step。固定事件顺序：

```text
turn/start
  agent/pre-step
  step/start
    user/message*
    agent/request
    request/header
    request/context?
    assistant/chunk*
    assistant/message
  step/end
  agent/turn-stopping
turn/end
```

请求必须由 `LlmRuntime.prepareCall()` 产生；`request/header` 提交前不能发生 Provider I/O。一次 prepared call 只允许一次 attempt，route replacement 不影响已绑定请求。

## 失败、取消与 retry 插槽

- `ErrorFinishReason`/`AbortedFinishReason` 不生成正常 assistant surface message，改为 `agent/request-error`。
- `Retry` 结果在同一个 turn/step 内重新 prepare 并发起下一次 attempt；失败 attempt 的 chunks 留在日志但不进入 `deriveMessages()`。
- `Unhandled` 不重试；`step/end` 在 finally 执行，`turn/end` 保存结构化 `LlmFailure`。
- Caller cancellation 不进入 request-error retry listener。已有可见正文时保存 `interrupted=true` 的 assistant message，然后以 aborted 结束 turn。
- L1 的 retry listener 只是执行插槽，不负责退避；`llm-retry` 在后续模块实现真正策略。
- L1 发送 `tools = null`；任何 ToolCall/ToolCalls finish 使用稳定 `TOOLS_NOT_AVAILABLE` 失败。

## 场景

### Scenario: scripted 文本响应

WHEN Agent 收到一条 follow-up 且 adapter 输出文本流，THEN 事件严格符合 turn/step 顺序，Session 可派生唯一 assistant history，Agent 最终回到 IDLE。

### Scenario: 两条 follow-up 串行

WHEN 同一 Agent 连续收到两条 follow-up，THEN 产生两个顺序 turn，不并行驱动同一 Agent，第二条在第一条 step/turn 完成后领取。

### Scenario: request-error Retry

WHEN 首次 attempt 返回 `SERVER` 且 request-error listener 返回 Retry，THEN 同一 turn/step 只有第二次成功 attempt 产生 assistant surface message；失败 chunks 仍可审计。

### Scenario: 未处理错误

WHEN request-error 没有 listener 或 listener 返回 Unhandled，THEN 不发生隐式重试，step/end 必须写入，turn/end 包含原始结构化 failure。

### Scenario: 流中取消

WHEN Provider Flow 在已有可见前缀后被 caller 取消，THEN 上游 Flow 被取消，写入 interrupted assistant message，turn 以 aborted 关闭，retry listener 不被调用。
