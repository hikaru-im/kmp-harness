# Desktop Agent Plugin Composition 完整规格

## Plugin definition contract

### Scenario: Stable compiled definitions

WHEN Host 编译 Desktop catalog，THEN每个 Agent 链模块公开一个稳定 `PluginDefinition` 或平台绑定的 definition factory，其 name 分别为 `session`、`agent`、`tools`、`agent-loop`、`session-api`、`session-persistence`、`llm-retry`，且注册和撤销保持 Loader 的原子语义。

### Scenario: Strict empty configuration

WHEN无配置 Plugin 的 row 省略 config，THEN decoder 接受并安装；WHEN config 是空对象或含任意字段，THEN按现有 `SimplePlugin` strict boundary 拒绝，避免误以为配置已经生效。

## Typed configuration contract

### Scenario: Explicit Agent model selection

WHEN Session API 或其他入口创建 Agent，THEN `AgentOptions` 接受非空 `provider/model` 及可选 `reasoningEffort/maxTokens`；WHEN缺少任一必填模型字段，THEN以稳定“模型未配置”错误失败，不从 Provider 模型目录选取默认值。

### Scenario: AgentLoop configuration

WHEN默认 `agent-loop` row 无模型配置，THEN它注册 AgentFactory 且不创建 Agent；WHEN profile 提供支持的调度配置，THEN应用严格默认值；WHEN出现 provider/model、声明式 `agents` 或未知字段，THEN明确拒绝，避免配置看似生效。

### Scenario: Tools configuration

WHEN `tools` config 省略 `maxConcurrentCalls`，THEN使用 1；WHEN值为正整数，THEN按该上限创建 service；WHEN值小于 1、不是整数或含未知字段，THEN在 apply 前失败。

### Scenario: Platform-bound persistence configuration

WHEN Desktop 构造 catalog，THEN它用已解析的 `HarnessHome.resolve("harness-sessions.db")` 创建 JVM Room factory，并注册 `session-persistence` definition；WHEN row 包含任何 config 字段，THEN拒绝该字段，数据库位置不能从 profile 覆盖。

## Default model catalog contract

### Scenario: Model entries are data, not Kotlin constants

WHEN OpenAI adapter 安装协议目录，THEN具体模型条目从 bundled JSON snapshot 解码，而不是在 `OpenAiKoogCatalog.kt` 中逐项构造；snapshot 必须包含自身 schema version、`models.dev` source repository/revision 和许可证 notice。WHEN snapshot schema 不受支持、字段非法、模型 id 重复或 route 未安装，THEN Provider 激活前稳定失败。

### Scenario: Upstream capabilities are normalized conservatively

WHEN生成 snapshot，THEN只映射 Harness 能明确表达且上游存在的字段，包括名称、描述、context/output limit、text/image 输入和 reasoning 标志；tool calling、structured output 等字段可以保留在 snapshot schema 中供后续能力面使用，但不得伪装成当前 Harness 已公开的 contract。WHEN `models.dev` 没有 endpoint compatibility 或 reasoning effort 枚举，THEN生成器不得猜测，缺失能力保持未声明；endpoint 由 provider settings `api` 选择，并应用到该 route 的全部模型。

### Scenario: Settings override the bundled snapshot

WHEN provider settings 省略 `models` 或设置空列表，THEN沿用 bundled snapshot，并把 provider `api` 作为目录模型的协议；WHEN `models` 非空，THEN以显式目录完整替换 snapshot；WHEN `modelOverrides` 非空，THEN只对 snapshot 的已知模型做局部覆盖。非空 `models` 与 `modelOverrides` 同时出现、override 指向未知 id、provider 引用未知 API，或模型/override 自己声明 `api` 时必须拒绝。

### Scenario: One provider route uses one protocol

WHEN provider settings 声明 `api`，THEN该协议应用到此 provider route 的所有 bundled 或显式模型；WHEN部署同时需要 Chat Completions 与 Responses，THEN使用两个 provider route 分别配置。`KoogModelProfile`、`KoogModelOverride` 和对应 JSON schema 不公开单模型 `api`。

### Scenario: Runtime discovery does not invent capabilities

WHEN未来显式启用 OpenAI `/v1/models` discovery，THEN其结果只可用于过滤或补充当前凭据可见的模型 id；`id`、`created`、`owned_by` 与 availability 信息不能推导 context、模态、reasoning、tool calling 或 Chat Completions/Responses compatibility。默认 Desktop 启动与列表查询不得依赖 `models.dev` 或其他第三方网络可用性。

### Scenario: Catalog updates are explicit and reviewable

WHEN维护者对同一个 `models.dev` source revision 重跑更新脚本，THEN生成 JSON 必须字节稳定；WHEN切换到新 revision，THEN模型新增、删除和能力变化必须表现为普通、可审查的 Git diff。构建和应用启动不得隐式更新 snapshot。

## Dependency contract

### Scenario: Required services

WHEN Loader 激活完整 bundle，THEN `agent` 等待 `session`，`agent-loop` 等待 `agent` 与 `llm`，`session-api` 等待 `agent`，`session-persistence` 等待 `session`，`llm-retry` 等待 `agent` 与 `session`。任何 required service 缺失时依赖 Plugin 不进入 active Fiber。

### Scenario: Optional Tools service

WHEN Tools service 缺失，THEN AgentLoop 仍可处理文本请求并对 tool call 保持现有稳定失败；WHEN Tools service随后出现、消失或替换，THEN optional injection 触发 AgentLoop 重载并更新能力。

## Desktop bundle contract

### Scenario: Catalog and rows remain bijective

WHEN读取 `DesktopPluginCatalog` 和 `DesktopProfileBundle`，THEN两者 name 集合相等、每个 name 只出现一次，并包含原有五行与新增七行。

### Scenario: Default composition remains bootable

WHEN OpenAI settings 为空，THEN Desktop profile 与 AgentLoop 仍完成安装，Agent 数量为零且 provider catalog 保持 dormant；WHEN调用入口未提供完整模型选择，THEN返回稳定配置错误；`gpt-4o-mini` 模型目录条目不得自动成为默认值。

### Scenario: Patch disable and recovery

WHEN patch 禁用任一新增 row，THEN该 row 无 Fiber且依赖项按 injection 停止；WHEN patch 恢复该 row 并提供合法配置，THEN Loader 重建链条，不泄漏旧 service 或数据库句柄。

## Verification contract

### Scenario: Automated verification

WHEN实现候选提交给 Verify，THEN Definition/config contracts、Desktop bundle/Loader fixture、JVM App tests、整仓 JVM build、diff check 与依赖审计均通过；当前环境不可运行的目标平台检查以未验证风险记录，不能表述为通过。

## 明确不支持

- 声明式启动 Agent 与 DSH `agents` 数组；本阶段只对齐“默认启用但空启动”的结果。
- Tools `native/ptc/both`、代码运行时和并发安全分类。
- Session Remote/Gateway、冷查询和 UI contract。
- profile 配置数据库位置、Room builder 或凭据。
- 运行时自动下载模型目录、自动采用上游新增模型，或从 OpenAI `/v1/models` 猜测能力。
