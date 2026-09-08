# LLM retry

`llm-retry` is an event plugin. It observes `agent/request-error`, records a
durable retry decision before waiting, then records `retry-started` immediately
before returning `Retry`. Provider adapters and `LlmRuntime` still perform only
one attempt per prepared call.
