# OpenAI Koog Provider

`llm-koog-openai` 是 OpenAI Provider Plugin。它拥有 OpenAI Chat Completions 与 Responses 的 route、
默认模型目录、settings defaults、Koog client factory、请求/流/failure 语义和 Responses replay codec。
该模块只向装配层导出 `OpenAiKoogPluginDefinition`；Host 不得再次注册 OpenAI model 或 mapper。

工具参数通过 Provider 自有的 `OpenAiToolMapper` 与 SDK Schema generator 扩展点原样传递 JSON
Schema。Chat Completions 和 Responses 都保留数组、嵌套对象和参数约束，不受通用 Koog primitive
mapper 子集限制。默认 `bash`/`subagent` 工具由离线真实 Koog HTTP fixture 验证。

Chat 流的 choice/text 与 tool-call 索引分别管理，支持兼容端点的 reasoning → tool-call 和空文本
结束帧。助手 reasoning 历史保留为 `reasoning_content`，工具结果可继续进入下一轮请求。外部工具
验收可通过 `HARNESS_OPENAI_LIVE_TOOL_MAX_TOKENS`（1–4096）为推理模型增加测试输出预算，默认值不变。

模型目录来自 `resources/model-catalog/openai.json`。它是从固定 models.dev revision 生成并随模块离线打包的快照，应用启动不会访问 models.dev。维护者显式运行 `tools/update-model-catalog.py` 更新；同一 revision 的输出按模型 id 排序且字节稳定。

一个 provider profile 只使用一个 `api`。需要同时使用 Chat Completions 和 Responses 时，声明两个 provider profile，并分别设置 `api: openai-chat-completions` 与 `api: openai-responses`。模型条目不接受 `api`，`models` 非空时完整替换 bundled catalog，`modelOverrides` 只修改 bundled catalog 中的已知模型。

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
stable loader row `llm-openai`; user profile patches can disable that row without importing Provider code:

```yaml
- id: llm-openai
  name: llm-openai
  disabled: true
```

`OpenAiKoogSettingsSource.Dynamic` reads the shared `llm-koog` settings namespace and credential service.
`OpenAiKoogSettingsSource.Static` remains a test/embedded API, not a Desktop construction mode. API keys are resolved
only when the plugin activates and are never stored in settings, profile patches or replay state.
