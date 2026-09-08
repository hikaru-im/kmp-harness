# Tools 扩展完整规格

Tools 作为独立扩展点提供 system prompt、tool schema、tool call/result 事件和调度服务。AgentLoop 通过注入的 Tools service 获取 schema，不把工具注册表内嵌到 loop。

每个 tool call 必须有稳定 `CallId`、名称、参数 JSON 和唯一 result；result 的 `ToolMessageSource.callId` 必须匹配。并发调用遵守配置的并行上限，单个 call 的结果只能提交一次；需要审批或不可执行时使用稳定错误并关闭当前 step，不伪造完成的 tool result。

工具调用产生多 step turn：assistant tool-call surface → tool result user message → 下一次 prepared call。工具事件通过 `SessionEventKey` 扩展，未知业务事件仍保持 log-only。

### Scenario: 多 step 工具闭环

WHEN adapter 输出一个权威 tool call 且 Tools service 返回结果，THEN 同一 turn 追加配对 tool result 并进入下一 step，最终 assistant message 可从日志 replay；重复 result 或缺失 call id 原子失败。
