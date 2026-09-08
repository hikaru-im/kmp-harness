# Agent

`modules/agent` 是 Agent 的公开生命周期与扩展边界。它只依赖 Runtime、LLM 和 Session：

- `AgentRegistry` 管理可替换 `AgentFactory` 与 live Agent；
- `AgentHandle` 绑定 owner identity，旧 handle 不能拆除同 id 的新实例；
- `Inbox` 将 follow-up 与 steer/inject 分成 `NEXT_TURN`、`NEXT_STEP`，每次 splice 先写 Session 事件；
- `AgentEvents` 提供 created/status/disposed、pre-step、request、request-error、turn-stopping typed keys；
- 具体文本循环由 `agent-loop` 提供，重试执行由后续 `llm-retry` Plugin 提供。
