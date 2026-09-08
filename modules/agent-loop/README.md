# Agent loop

`agent-loop` installs the default `AgentFactory` and drives one agent at a time.
Without a `ToolsPlugin` it runs the text-only loop; when the optional Tools
service is present it records tool calls/results and continues the same turn
through additional steps. Every turn, step, request and assistant chunk is
recorded in the owning Session; retry remains a request-error extension point
and is not implemented inside the provider runtime.
