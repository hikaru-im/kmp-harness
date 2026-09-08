# Session API

The provider-neutral API is a thin facade over `AgentRegistry` and `Session`.
It exposes identifiers and serializable summaries only; history always comes
from the Session event log and prompt/cancel operate on the owning Agent.
