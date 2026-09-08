# OpenAI Koog Provider

`llm-koog-openai` 是 OpenAI Provider Plugin。它拥有 OpenAI Chat Completions 与 Responses 的 route、
默认模型目录、settings defaults、Koog client factory、请求/流/failure 语义和 Responses replay codec。
该模块只向装配层导出 `OpenAiKoogPluginDefinition`；Host 不得再次注册 OpenAI model 或 mapper。

## Source Layout

```text
src/im/hikaru/harness/llm/koog/openai/
  OpenAiKoogPlugin.kt   Public plugin/configuration entry point
  catalog/              Installed routes and deployment defaults
  client/               Credential resolution and PromptExecutor creation
  semantics/            Route-to-semantics bundle assembly
  chat/                 Chat Completions request and terminal semantics
  responses/            Responses request, stream, history and replay semantics
  failure/              Structured OpenAI failure classification
```

Responses replay is intentionally split into state, strict JSON codec, writer, restorer and history message mapper.
Durable Harness content remains authoritative; Provider-private reasoning metadata is only restored after identity and
block-shape validation.

## Installation

Desktop production installs this module through the compiled catalog in `modules/bundle/desktop`. The bundle inserts the
stable loader row `llm-koog-openai`; user profile patches can disable that row without importing Provider code:

```yaml
- id: llm-koog-openai
  name: llm-koog-openai
  disabled: true
```

`OpenAiKoogSettingsSource.Dynamic` reads the shared `llm-koog` settings namespace and credential service.
`OpenAiKoogSettingsSource.Static` remains a test/embedded API, not a Desktop construction mode. API keys are resolved
only when the plugin activates and are never stored in settings, profile patches or replay state.
