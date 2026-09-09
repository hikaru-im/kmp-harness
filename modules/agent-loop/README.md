# Agent loop

`agent-loop` installs the default `AgentFactory` but does not create an Agent. Callers must provide `provider` and `model` in `AgentOptions`; `reasoningEffort` and `maxTokens` are optional. Plugin configuration accepts only an optional `system` prompt and rejects default-model or declarative-agent fields.

Without a `ToolsPlugin` it runs the text-only loop; when the optional Tools service is present it records tool calls/results and continues the same turn through additional steps. Every turn, step, request and assistant chunk is recorded in the owning Session; retry remains a request-error extension point.
