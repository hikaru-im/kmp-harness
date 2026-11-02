# Built-in model provider contributions

这个模块安装 OpenAI、Claude、Gemini、DeepSeek、Qwen、Kimi、GLM 的厂商描述与模型发现贡献。
历史 loader 名称 `llm-openai` 和 `llm-koog` 设置 namespace 保留以迁移现有安装；实际发现、能力解析和调用创建均通过 `LlmRuntime` 的贡献注册表，Host 不再直接调用某个厂商的目录 helper。

## 当前可执行范围

| 厂商 | 推理协议 | 目录 |
| --- | --- | --- |
| OpenAI | Chat Completions、Responses | 远端列表，bundled catalog 回退 |
| DeepSeek | Chat Completions、Responses | 远端列表 |
| Qwen / 百炼 | OpenAI-compatible Chat、Responses | DashScope 原生分页目录 |
| Kimi / Moonshot | Chat Completions | 远端列表 |
| GLM / Z.ai | Chat Completions | 当前使用明确标记的 bundled 回退 |
| Claude | 尚未接入推理 adapter | 原生分页 Models API |
| Gemini | 尚未接入推理 adapter | Developer API 分页 Models API |

目录贡献与推理 factory 分开；Claude/Gemini 支持草稿发现，设置页暂不允许保存为推理连接；外部配置里的这两家只保留目录状态。Vertex、Bedrock 不在本批范围。

每个连接有独立 client、凭据与地址。一个 profile 只选择一个协议，需两个协议时创建两个连接。
兼容厂商必须明确 vendor 与 base URL；版本路径（例如 `/compatible-mode/v1` 或 `/api/paas/v4`）保留，不套用 OpenAI 的默认地址。

## 思考与历史

`OpenAiCompatibleCapabilityRules` 只给已核实的具体型号或旧显式配置提供可执行控制，不按型号前缀猜测未知后缀。
`provider-default`、开关、effort 与 budget 分开。Qwen effort/budget 互斥；Qwen3.8 budget=0 是低档预算而不是关闭；Kimi K3、GLM 5.3 不提供关闭选项。
未知控制在调用前返回能力未知/不支持错误，不静默降档。规则未确认的 execution mode 保持不可用。
GPT-6.1 Sol / GPT-6 Astra 当前适配器保守要求使用 Responses，尚未开放其 Chat 入口。

Chat 使用厂商的原生 `thinking`、`enable_thinking`、`reasoning_effort`、`thinking_budget` 字段。Responses 使用 `reasoning.effort`；OpenAI 无状态请求包含 encrypted reasoning，并通过版本化 replay codec 恢复 ID、summary、encrypted 数据。
工具历史保留 `reasoning_content`、tool-call ID 和结果关联。`ProviderWireContractTest` 使用回环 HTTP 捕获 SDK 最终 JSON，验证扩展参数不是仅留在内存的 additionalProperties。
回环 fixtures 验证请求/流转换，不能替代真实厂商端点的在线兼容验收。

## 安装与源码

Desktop 使用 `OpenAiKoogPluginDefinition`。`OpenAiKoogSettingsSource.Static` 是嵌入式/测试配置入口，同样经注册表创建 adapter；Dynamic 监听 settings/credentials，完整候选失败时保留当前 generation。

```text
providers/   厂商 descriptors、发现传输/解析、能力规则、注册用 adapter factory
catalog/     OpenAI bundled snapshot 与协议模板
client/      插件内部的 Koog HTTP executor 构建
chat/        Chat 参数、usage 与 terminal 映射
responses/   Responses 参数、流、历史与 replay codec
semantics/   协议映射组合与工具 schema
failure/     结构化 HTTP 错误分类
```

API key 只由 host 解析到短期 `LlmProviderAccess`，不进入设置、目录快照或 replay state。
