# Session API

The provider-neutral API is a thin facade over `AgentRegistry` and `Session`.
It exposes identifiers and serializable summaries only; history always comes
from the Session event log and prompt/cancel operate on the owning Agent.

`SessionPrompt.awaitIdle()` remains synchronization only. Local callers can then read
`failure()` for the original message's recorded turn, including its stable error code
and optional HTTP status. Provider response bodies are not exposed. The Desktop
conversation controller displays this failure instead of silently treating idle as
success; Relay still exposes delivery acknowledgement only.
