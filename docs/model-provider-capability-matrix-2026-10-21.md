# 七家模型厂商：思考参数与模型发现矩阵

状态：厂商官方能力调研基线；实际已实现范围见 [统一协议与改造方案](model-provider-unification-plan.md#已落地范围)。核验日期：**2026-10-21**。调研代码基线：`fa0a9be1b3a67504230235d4292bd094ef2c0ed2`。

范围为 OpenAI、Anthropic/Claude、Google Gemini、DeepSeek、阿里云百炼/Qwen、Moonshot/Kimi、智谱/GLM 的直接 API。地区、Coding Plan、托管入口和 API 协议必须分别识别；同名模型不能跨入口套用参数。以下是官方文档核验，不是使用真实密钥完成的接口实测。型号举例用于覆盖参数差异，不构成永久有效的全型号清单。

配套实现设计见 [统一协议与改造方案](model-provider-unification-plan.md)。

## 1. 思考能力总矩阵

“未声明”表示本次查阅的公开契约未提供该控制能力，不等于断言服务永远不支持。档位集合是厂商内的并集，不能直接作为每个模型的可选项。`max_tokens` 等输出上限不能当作独立思考预算。

| 厂商 | 原生控制字段与协议差异 | 强度与预算 | 默认、关闭和主要限制 |
| --- | --- | --- | --- |
| OpenAI | Responses：`reasoning.effort`；Chat：`reasoning_effort`。部分新模型另有 `reasoning.mode`、`reasoning.context` | 档位并集 `none/minimal/low/medium/high/xhigh/max`，按模型取子集；独立思考 token 预算未声明 | `none` 是否可用、默认档位随模型变化；GPT-6.1 Sol 不支持 none/minimal，默认 medium。GPT-5.6/6 的 Responses 可有 standard/pro 执行模式。工具、采样参数还受模型和协议约束。[思考](https://developers.openai.com/api/docs/guides/reasoning)、[部署约束](https://developers.openai.com/api/docs/guides/deployment-checklist) |
| Anthropic / Claude | `thinking.type`；`output_config.effort`；旧家族手动思考用 `thinking.budget_tokens` | low/medium/high/xhigh/max 的型号子集；手动预算仅部分家族支持，通常至少 1024 | adaptive/enabled/disabled/between_tools 支持集合随型号变化；新旧模型省略字段时行为不同。effort 影响整体输出，可与手动预算同时存在，也可能在关闭思考时有效。[模式](https://platform.claude.com/docs/en/build-with-claude/thinking)、[effort](https://platform.claude.com/docs/en/build-with-claude/effort)、[手动预算](https://platform.claude.com/docs/en/build-with-claude/extended-thinking) |
| Gemini | GenerateContent：`generationConfig.thinkingConfig.{thinkingLevel,thinkingBudget}`；Interactions：`generation_config.thinking_level` | 新家族以档位为主；GenerateContent 2.5 使用预算，`-1` 为动态，部分型号 `0` 为关闭；Interactions 2.5 也有档位 | minimal 不等于关闭；2.5 Pro 不能关闭。两种 API 对同一型号的控制形式不同；不能同时发不兼容的 level/budget。[GenerateContent 思考](https://ai.google.dev/gemini-api/docs/generate-content/thinking)、[Interactions 思考](https://ai.google.dev/gemini-api/docs/thinking) |
| DeepSeek | Chat：`thinking.type`、`reasoning_effort`；Responses：`reasoning.effort`；Anthropic 兼容：`output_config.effort` | 当前直连规范档位 low/high/max，none 关闭；独立思考预算未声明 | 当前默认开启、high。已核验的 Chat 工具循环需回传 reasoning_content；Chat 思考时部分采样字段忽略，强制工具调用不支持。其他协议需独立规则。[思考](https://api-docs.deepseek.com/guides/thinking_mode/)、[Chat 参数](https://api-docs.deepseek.com/api/create-chat-completion/) |
| Qwen / 百炼 | Chat 顶层或 DashScope `parameters` 内的 `enable_thinking`、`thinking_budget`、`reasoning_effort`；Responses 用 `reasoning.effort` | Qwen3.8 规范档位 low/medium/xhigh；effort 与 budget 互斥。其他家族能力不同 | 混合与纯思考模型并存；默认开关随型号变化。Responses、Chat、DashScope 的输出上限口径不完全相同。[DashScope 参数](https://www.alibabacloud.com/help/en/model-studio/qwen-api-via-dashscope)、[Responses](https://www.alibabacloud.com/help/en/model-studio/qwen-api-via-openai-responses) |
| Kimi / Moonshot | K3：`reasoning_effort`；K2.6/2.7：`thinking.type`、`thinking.keep`，规则各异 | K3 low/high/max，默认 max；K2.6/2.7 无 effort；独立思考预算未声明 | K3、K2.7 Code 始终思考，K2.6 可关闭。K3 不应发送 thinking。历史保留和采样约束不能套通用 OpenAI 参数。[模型参数](https://platform.kimi.ai/docs/api/models-overview)、[思考](https://platform.kimi.ai/docs/guide/use-thinking-models)、[effort](https://platform.kimi.ai/docs/guide/use-reasoning-effort) |
| GLM / 智谱、Z.ai | `thinking.type`、`reasoning_effort`、`thinking.clear_thinking`；标准 API 与 Coding Plan 规则不同 | GLM5.3 low/high/max；5.2 high/max；独立思考预算未核实 | 5.3 系列不能关闭；Coding Plan 中 none 可映射 low，不能解释为 off。5.2 可关闭，跨轮历史默认随服务类型变化。[国内思考](https://docs.bigmodel.cn/cn/guide/capabilities/thinking)、[国际思考](https://docs.z.ai/guides/capabilities/thinking)、[历史](https://docs.z.ai/guides/capabilities/thinking-mode) |

## 2. 必须按模型、协议区分的规则

### 2.1 OpenAI

Responses 的 `reasoning.mode=standard|pro` 与 effort 是不同维度，不能把 pro 伪装成另一档 effort。`reasoning.context` 在支持的模型上影响历史思考使用范围，同样不是“是否显示思考”。省略字段保留服务默认，不自动补 high 或 medium。[官方 reasoning 指南](https://developers.openai.com/api/docs/guides/reasoning)

GPT-6 Astra、GPT-6.1 Sol 的工具调用需要 Responses；部分其他 GPT-6 模型的 Chat 工具支持又受 effort 限制。新模型开启推理时不一定接受 temperature/top_p 等字段。因此模型可出现在目录里，但当前适配器仍可能无法满足 Agent 工具调用要求。[部署检查](https://developers.openai.com/api/docs/guides/deployment-checklist)

无状态重放要保留原始 reasoning/output items 及加密内容，并校验来源模型兼容性；界面展示的思考摘要不能代替可重放状态。[reasoning 指南](https://developers.openai.com/api/docs/guides/reasoning)

### 2.2 Claude

| 家族示例 | 省略 thinking 时 | 可显式选择的关键模式 |
| --- | --- | --- |
| Opus 5.5；Fable/Mythos 5、5.1 | adaptive | 仅 adaptive；不能关闭或手动预算 |
| Sonnet 5.5 | adaptive | adaptive、between_tools；后者要求 effort 不高于 high |
| Opus 5、Haiku 5.5 | adaptive | 可 disabled，但 effort 不能高于 high |
| Sonnet 5 | adaptive | 可 disabled |
| Opus 4.7、4.8 | off | adaptive/disabled；不接受 enabled+budget |
| Opus/Sonnet 4.6 | off | adaptive；手动 enabled+budget 尚可用但弃用 |
| Opus/Sonnet/Haiku 4.5 | off | enabled+budget 或 disabled；无 adaptive |
| Mythos Preview | adaptive | adaptive 或 enabled+budget；不能关闭 |

来源：[Thinking 模式](https://platform.claude.com/docs/en/build-with-claude/thinking)。`between_tools` 是单独模式，不等于 disabled，也不允许随意附带其他 thinking 子字段。

effort 不能用连续序数推导：例如 Opus/Sonnet 4.6、Mythos Preview 支持 max，却不支持 xhigh；Opus 4.5 仅 low/medium/high。默认通常 high，Opus 5.5、Haiku 5.5 为 medium。[Effort](https://platform.claude.com/docs/en/build-with-claude/effort)

手动预算通常须小于 `max_tokens`；手动 interleaving 有跨整个 assistant turn 的例外及型号/header 限制。**Opus 4.5 的 effort 与 budget 可以同时设置**：前者控制整体响应投入，后者控制思考预算。不能在统一协议全局规定二者互斥。[Extended thinking](https://platform.claude.com/docs/en/build-with-claude/extended-thinking)

工具循环保留完整 assistant content、thinking/signature/redacted_thinking/tool_use；不同模式对 forced tool choice 有额外限制。保留历史还涉及模型、前缀、system/tools 的兼容性验证。[工具工作流](https://platform.claude.com/docs/en/build-with-claude/thinking-tool-workflows)、[Preserved thinking](https://platform.claude.com/docs/en/build-with-claude/preserved-thinking)

### 2.3 Gemini

| API / 家族示例 | 档位或预算 | 默认与边界 |
| --- | --- | --- |
| GenerateContent：4 Argon、3.8 Flash | low/medium/high | 默认 medium；minimal 不支持 |
| GenerateContent：3.6 Flash | minimal/low/medium/high | 默认 medium |
| GenerateContent：3.5、3.1 Flash-Lite | minimal/low/medium/high | 默认 minimal |
| GenerateContent：3.1 Flash-Lite Image | minimal/high | 默认 minimal |
| GenerateContent：3 Flash | minimal/low/medium/high | 默认 high |
| GenerateContent：2.5 Pro | budget 128–32768，另有 -1 | 默认 dynamic；不支持 0/off，不支持 thinkingLevel |
| GenerateContent：2.5 Flash | budget 0–24576，另有 -1 | 默认 dynamic；0 关闭 |
| GenerateContent：2.5 Flash-Lite | budget 512–24576，另有 0/-1 | 默认关闭 |
| Interactions：2.5 Pro/Flash/Flash-Lite | low/medium/high | 前两者默认 on，Flash-Lite 默认 off；不能据此推导固定 token 数 |

来源：[GenerateContent thinking](https://ai.google.dev/gemini-api/docs/generate-content/thinking)、[Interactions thinking](https://ai.google.dev/gemini-api/docs/thinking)。预算不保证精确消耗；minimal 不保证完全不思考。4 系列 budget 已弃用，与 level 同传报错。

GenerateContent REST schema 使用 `MINIMAL/LOW/MEDIUM/HIGH` 枚举；Interactions 使用小写 `thinking_level`，当前 schema 没有独立 thinking_budget。Interactions 还有 v1/v1beta，支持模型范围不可仅换 URL 推断。[GenerateContent schema](https://ai.google.dev/api/generate-content)、[Interactions v1beta](https://ai.google.dev/api/interactions-api)、[Interactions v1](https://ai.google.dev/api/interactions-api-v1)

GenerateContent 必须保留原始 parts 和 thoughtSignature，不能合并有签名的 part。Interactions 的有状态流程可用 previous_interaction_id，无状态流程仍需保存原始 thought steps；逐请求配置还要重新发送。[Thought signatures](https://ai.google.dev/gemini-api/docs/generate-content/thought-signatures)、[Interactions overview](https://ai.google.dev/gemini-api/docs/interactions-overview)

### 2.4 DeepSeek、Qwen、Kimi、GLM

| 对象 | 必须记录的规则 |
| --- | --- |
| DeepSeek 直连 Chat | 规范档位 low/high/max；兼容别名不必重复显示在 UI。带 tools 的历史需完整 reasoning_content；无 tools 时该历史可被忽略。Chat 思考模式不能强制 required/指定工具；部分采样参数被忽略。[思考规则](https://api-docs.deepseek.com/guides/thinking_mode/) |
| DeepSeek Responses | 使用 reasoning item 的 content/reasoning_text 回传历史；当前为无状态接口，不支持 previous_response_id/store/encrypted_content。兼容表列出 required/指定工具支持，但其与思考模式的组合仍需独立验证，不能直接继承 Chat 禁止规则。[Responses 契约](https://api-docs.deepseek.com/api/create-response/)、[兼容表](https://api-docs.deepseek.com/guides/responses_api/) |
| Qwen3.8 | Chat/DashScope 的 effort 与 budget 互斥；文档列 low/medium/xhigh 对应 4096/16384/262144，budget 的 0–4096 映射 low，因此 0 不能全局解释成关闭。字段均省略的默认预算另有说明，不能用档位映射代替；默认值冲突需按具体型号与协议确认。[原生参数](https://www.alibabacloud.com/help/en/model-studio/qwen-api-via-dashscope) |
| Qwen 其他家族 | 部分混合模型默认开启，旧 qwen-plus/qwen-flash 默认关闭；部分纯思考型号不支持 off。百炼托管 Kimi/DeepSeek 的默认和档位也可能不同于厂商直连。[支持模型](https://www.alibabacloud.com/help/en/model-studio/deep-thinking) |
| Qwen 输出上限与历史 | Responses 的 max_output_tokens、Chat 的 max_completion_tokens/max_tokens 在不同家族下是否包含思考有差别；preserve_thinking 的默认也随型号变化。[Responses 参数](https://www.alibabacloud.com/help/en/model-studio/qwen-api-via-openai-responses)、[DashScope 参数](https://www.alibabacloud.com/help/en/model-studio/qwen-api-via-dashscope) |
| Kimi K3 | 始终思考，low/high/max，默认 max；不发 thinking。完整保留 assistant 的 reasoning_content 和 tool_calls。[effort 指南](https://platform.kimi.ai/docs/guide/use-reasoning-effort) |
| Kimi K2.7 Code / highspeed | 始终思考，无 effort；仅接受 enabled/keep all 的显式思考配置。max_tokens 包含思考和回答。[模型参数](https://platform.kimi.ai/docs/api/models-overview) |
| Kimi K2.6 | enabled/disabled，默认 enabled，无 effort；keep 默认 null，可为 all。同一工具循环仍需保留完整思考，与跨轮 keep 区分。[思考指南](https://platform.kimi.ai/docs/guide/use-thinking-models) |
| GLM5.3 系列 | 标准档位 low/high/max、默认 max，不能关闭；Coding Plan 的 none/minimal/low 可都映射 low。绝不能全局把 none 当关闭。[国内思考](https://docs.bigmodel.cn/cn/guide/capabilities/thinking) |
| GLM5.2 | 原生 high/max、默认 max，允许关闭；标准 API 与 Coding Plan 的默认历史保留不同。`thinking.clear_thinking=false` 启用跨轮保留。[思考模式](https://docs.z.ai/guides/capabilities/thinking-mode) |
| 旧 GLM | enabled 可能表示模型自行判断是否思考；不能统一解释成“每次强制生成思考”。不能为没有文档保证的旧型号附加 effort。[国内思考](https://docs.bigmodel.cn/cn/guide/capabilities/thinking) |

## 3. 模型列表获取矩阵

“能获取列表”只证明完成一次目录请求，不自动证明所有模型都有推理权限、余额、工具能力或可用额度。

| 厂商 | 官方列表接口 | 认证、连接条件和分页 | 返回元数据；仍需补齐 |
| --- | --- | --- | --- |
| OpenAI | `GET https://api.openai.com/v1/models` | Bearer；当前契约未列分页参数 | id/created/owned_by、可选 shutdown_date；不能直接获得完整思考能力、上下文和输出限制。[Models](https://developers.openai.com/api/reference/resources/models/methods/list) |
| Claude | `GET https://api.anthropic.com/v1/models`；另有详情接口 | 当前指南推荐 Bearer，兼容 x-api-key；anthropic-version。部分 key 需 workspace；limit 默认 20、最大 1000，after_id/has_more | 能力包含 thinking modes、effort、模态等，以及输入/输出限制和生命周期；允许 null，仍缺默认值、组合约束、预算和回传规则。[Models](https://platform.claude.com/docs/en/api/models/list)、[认证](https://platform.claude.com/docs/en/manage-claude/authentication) |
| Gemini | `GET https://generativelanguage.googleapis.com/v1beta/models` | x-goog-api-key；pageSize 默认 50、最大 1000；pageToken/nextPageToken | inputTokenLimit/outputTokenLimit、supportedGenerationMethods、thinking、采样字段；缺完整档位、预算范围、关闭能力和 API surface 适用条件。[Models](https://ai.google.dev/api/models) |
| DeepSeek | `GET https://api.deepseek.com/models` | Bearer；当前契约未列分页参数 | context_window/max_output_tokens、模态、effort.supported_levels/default_level、部分协议能力；缺关闭语义、采样和工具历史约束。[Models](https://api-docs.deepseek.com/api/list-models/) |
| Qwen / 百炼 | 地区对应的原生 `GET /api/v1/models` | Bearer；地区、部分地区 WorkspaceId；page_no 从 1 开始，page_size 默认 20，output.total | provider/inference_provider、模态/features、上下文与输出/思考长度、快照和生命周期；缺完整 effort 与组合规则。[Models](https://www.alibabacloud.com/help/en/model-studio/list-models) |
| Kimi | 国际 `GET https://api.moonshot.ai/v1/models`；国内使用 moonshot.cn | Bearer；国内国际 key 不互通；当前契约未列分页参数 | context_length、supports_image_in/video_in/reasoning；不能推导 effort、关闭能力、预算、历史规则。[Models](https://platform.kimi.ai/docs/api/list-models)、[国内接入](https://platform.kimi.com/docs/get-api-key) |
| GLM / Z.ai | **公开稳定列表契约未确认** | 国内/国际、标准 API/Coding Plan 要分别选择；不猜测并声称支持 `/models` | 首版使用版本化内置目录与手工 ID，明确账户可用性未验证。已核对官方索引/OpenAPI，但不能据此断言服务不存在。[OpenAPI](https://docs.z.ai/openapi.json)、[国内索引](https://docs.bigmodel.cn/llms.txt)、[模型目录](https://docs.bigmodel.cn/cn/guide/start/model-overview) |

Claude Models 示例中的 capabilities 是 schema 示例，不能把示例里的全部 true 当作该示例型号的真实能力；实际响应中的 null 是 unknown。[Models schema](https://platform.claude.com/docs/en/api/models/list)

百炼发现地址需要单独配置预设，不能从 OpenAI-compatible 推理 base 机械拼接：新加坡为 `https://dashscope-intl.aliyuncs.com/api/v1/models`；北京使用 `https://{WorkspaceId}.cn-beijing.maas.aliyuncs.com/api/v1/models`；香港使用 `https://cn-hongkong.dashscope.aliyuncs.com/api/v1/models`。其他地区按官方表维护。[地区列表](https://www.alibabacloud.com/help/en/model-studio/list-models)

## 4. “填写 key 获取模型”的产品边界

| 普通流程 | 条件性补充信息 |
| --- | --- |
| 选择 OpenAI → 输入 key → 获取模型 | 自定义代理才展开 base URL、额外 headers 等高级配置 |
| 选择 Claude → 输入 key → 获取模型 | 非单 workspace 绑定的个人/服务 key 需要 workspace 标识；凭据范围内的 workspace 发现能力须另行实现或允许输入 |
| 选择 Gemini → 输入 key → 获取模型 | 官方 Developer API 不要求普通用户再填写 Vertex project/location；Vertex 属另一接入配置 |
| 选择 DeepSeek → 输入 key → 获取模型 | 直连与托管入口分开 |
| 选择百炼 → 选择地区 → 输入 key → 获取模型 | 仅在所选地区/入口需要时询问 WorkspaceId |
| 选择 Kimi → 选择国内/国际 → 输入 key → 获取模型 | 不通过 key 外观猜地区，也不向另一区域自动发送 key |
| 选择 GLM → 选择国内/国际及服务类型 → 输入 key → 展示预置目录 | 发现接口未确认时显示“预置目录，账户可用性未验证”；保留手工模型 ID |

Claude 条件认证依据 [Authentication](https://platform.claude.com/docs/en/manage-claude/authentication)；百炼地区依据 [Responses 接入](https://www.alibabacloud.com/help/en/model-studio/qwen-api-via-openai-responses)；Kimi 地区依据 [国际接入](https://platform.kimi.ai/docs/overview) 与 [国内接入](https://platform.kimi.com/docs/get-api-key)。GLM 自 2026-10-16 起新标准 API、个人/团队 Coding Plan key 分开管理；服务入口不是纯展示信息。[国内公告](https://docs.bigmodel.cn/cn/coding-plan/notice/api-key-management)、[Z.ai 接入](https://docs.z.ai/devpack/quick-start)

## 5. 转化为工程规则

1. 能力身份必须至少包含连接实例、服务入口、协议/API 版本、模型及可得的版本信息；托管服务还要区分模型研发方与推理服务方。
2. provider_default、off、adaptive、minimal 是不同语义；任何数值/档位转换必须由具体模型规则负责。
3. effort 和 budget 是独立可选维度。Claude 可以组合，部分 Qwen/Gemini 禁止组合，由能力约束校验。
4. 远端目录提供事实，版本化规则补齐缺口，运行时适配器决定哪些能力真正可执行。缺失值不补成 false 或虚构限额。
5. 模型列表、推理权限、运行时可调用、工具支持、最后一次实测状态分别记录。
6. 工具循环的原始思考状态、签名与顺序必须保留；支持参数但丢失 replay 的适配不能宣称 Agent 接入完成。

实施前仍需确认：GLM 发现契约；百炼个别默认值/纯思考型号与通用参数说明的冲突；当前 SDK 对所有请求扩展字段、流式原始块和签名的无损支持。这些不阻止先实现统一契约与连接隔离，但对应能力应保持 unknown/未启用，直到验证完成。
