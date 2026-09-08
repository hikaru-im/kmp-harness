# Tools

`ToolsService` owns schemas and exclusive call/result submission. Each call is
identified by a provider-neutral `CallId`; duplicate results and unknown tools
fail atomically. Session extension events retain the auditable call and result
facts while the AgentLoop remains the scheduler.
