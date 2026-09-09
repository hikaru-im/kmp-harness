---
generated_from_state_version: 7
---

# 验证

## 当前结果

- 结果: **已归档**
- 验证情况: **已完成检查，验证结果已确认**
- 目标周期: 1
- 迭代: 1
- 验证器尝试次数: 1
- 完成时间: 2026-09-08T14:39:45.407Z
- 摘要: 独立只读 Verifier 判定候选 f3416059-1956-422f-87c3-122684ca86c6 满足 A1-A18；live 与 iOS 限制已明确记录。

## 验收

| 编号 | 结果 | 来源 | 验收项 | 原因 |
| --- | --- | --- | --- | --- |
| A1 | passed | specs/desktop-agent-plugin-composition/spec.md | Stable compiled definitions WHEN Host 编译 Desktop catalog，THEN每个 Agent 链模块公开一个稳定 `PluginDefinition` 或平台绑定的 definition factory，其 name 分别为 `session`、`agent`、`tools`、`agent-loop`、`session-api`、`session-persistence`、`llm-retry`，且注册和撤销保持 Loader 的原子语义。 | 七个 Agent 链模块公开稳定 definition/factory，名称正确并保留 Loader 原子注册撤销。 |
| A2 | passed | specs/desktop-agent-plugin-composition/spec.md | Strict empty configuration WHEN无配置 Plugin 的 row 省略 config，THEN decoder 接受并安装；WHEN config 是空对象或含任意字段，THEN按现有 `SimplePlugin` strict boundary 拒绝，避免误以为配置已经生效。 | 无配置 definition 接受省略 config，空对象或字段经 ConfigAdapter.unit 在 apply 前拒绝。 |
| A3 | passed | specs/desktop-agent-plugin-composition/spec.md | Explicit Agent model selection WHEN Session API 或其他入口创建 Agent，THEN `AgentOptions` 接受非空 `provider/model` 及可选 `reasoningEffort/maxTokens`；WHEN缺少任一必填模型字段，THEN以稳定“模型未配置”错误失败，不从 Provider 模型目录选取默认值。 | AgentOptions 显式接收 provider/model/reasoningEffort/maxTokens，缺模型稳定返回 MODEL_NOT_CONFIGURED。 |
| A4 | passed | specs/desktop-agent-plugin-composition/spec.md | AgentLoop configuration WHEN默认 `agent-loop` row 无模型配置，THEN它注册 AgentFactory 且不创建 Agent；WHEN profile 提供支持的调度配置，THEN应用严格默认值；WHEN出现 provider/model、声明式 `agents` 或未知字段，THEN明确拒绝，避免配置看似生效。 | AgentLoop 只接受可选 system，拒绝 provider/model/agents/未知字段，安装只注册 factory 且零 Agent。 |
| A5 | passed | specs/desktop-agent-plugin-composition/spec.md | Tools configuration WHEN `tools` config 省略 `maxConcurrentCalls`，THEN使用 1；WHEN值为正整数，THEN按该上限创建 service；WHEN值小于 1、不是整数或含未知字段，THEN在 apply 前失败。 | Tools 默认并严格解析正整数 maxConcurrentCalls，字符串、非正数和未知字段均失败。 |
| A6 | passed | specs/desktop-agent-plugin-composition/spec.md | Platform-bound persistence configuration WHEN Desktop 构造 catalog，THEN它用已解析的 `HarnessHome.resolve("harness-sessions.db")` 创建 JVM Room factory，并注册 `session-persistence` definition；WHEN row 包含任何 config 字段，THEN拒绝该字段，数据库位置不能从 profile 覆盖。 | Desktop 固定使用 resolved HarnessHome/harness-sessions.db，profile 不能覆盖平台路径。 |
| A7 | passed | specs/desktop-agent-plugin-composition/spec.md | Model entries are data, not Kotlin constants WHEN OpenAI adapter 安装协议目录，THEN具体模型条目从 bundled JSON snapshot 解码，而不是在 `OpenAiKoogCatalog.kt` 中逐项构造；snapshot 必须包含自身 schema version、`models.dev` source repository/revision 和许可证 notice。WHEN snapshot schema 不受支持、字段非法、模型 id 重复或 route 未安装，THEN Provider 激活前稳定失败。 | 模型从 bundled JSON 加载，schema/source/revision/license/字段/正容量/重复 ID 均严格校验。 |
| A8 | passed | specs/desktop-agent-plugin-composition/spec.md | Upstream capabilities are normalized conservatively WHEN生成 snapshot，THEN只映射 Harness 能明确表达且上游存在的字段，包括名称、描述、context/output limit、text/image 输入和 reasoning 标志；tool calling、structured output 等字段可以保留在 snapshot schema 中供后续能力面使用，但不得伪装成当前 Harness 已公开的 contract。WHEN `models.dev` 没有 endpoint compatibility 或 reasoning effort 枚举，THEN生成器不得猜测，缺失能力保持未声明；endpoint 由 provider settings `api` 选择，并应用到该 route 的全部模型。 | 只映射明确上游能力；endpoint 不猜测，toolCall/structuredOutput 仅保留校验而不伪装为 runtime contract。 |
| A9 | passed | specs/desktop-agent-plugin-composition/spec.md | Settings override the bundled snapshot WHEN provider settings 省略 `models` 或设置空列表，THEN沿用 bundled snapshot，并把 provider `api` 作为目录模型的协议；WHEN `models` 非空，THEN以显式目录完整替换 snapshot；WHEN `modelOverrides` 非空，THEN只对 snapshot 的已知模型做局部覆盖。非空 `models` 与 `modelOverrides` 同时出现、override 指向未知 id、provider 引用未知 API，或模型/override 自己声明 `api` 时必须拒绝。 | models 的继承、完整替换和已知 modelOverrides 均符合约束，冲突、未知项和模型级 api 被拒绝。 |
| A10 | passed | specs/desktop-agent-plugin-composition/spec.md | One provider route uses one protocol WHEN provider settings 声明 `api`，THEN该协议应用到此 provider route 的所有 bundled 或显式模型；WHEN部署同时需要 Chat Completions 与 Responses，THEN使用两个 provider route 分别配置。`KoogModelProfile`、`KoogModelOverride` 和对应 JSON schema 不公开单模型 `api`。 | provider api 统一应用整条 route；profile/override 不公开单模型 api，双协议由两个 provider profile 表达。 |
| A11 | passed | specs/desktop-agent-plugin-composition/spec.md | Runtime discovery does not invent capabilities WHEN未来显式启用 OpenAI `/v1/models` discovery，THEN其结果只可用于过滤或补充当前凭据可见的模型 id；`id`、`created`、`owned_by` 与 availability 信息不能推导 context、模态、reasoning、tool calling 或 Chat Completions/Responses compatibility。默认 Desktop 启动与列表查询不得依赖 `models.dev` 或其他第三方网络可用性。 | 默认目录完全离线，启动和查询不依赖 models.dev 或 /v1/models，也不从可见 ID 推导能力。 |
| A12 | passed | specs/desktop-agent-plugin-composition/spec.md | Catalog updates are explicit and reviewable WHEN维护者对同一个 `models.dev` source revision 重跑更新脚本，THEN生成 JSON 必须字节稳定；WHEN切换到新 revision，THEN模型新增、删除和能力变化必须表现为普通、可审查的 Git diff。构建和应用启动不得隐式更新 snapshot。 | 更新脚本固定 revision、排序且显式执行；同 revision 两次输出字节一致。 |
| A13 | passed | specs/desktop-agent-plugin-composition/spec.md | Required services WHEN Loader 激活完整 bundle，THEN `agent` 等待 `session`，`agent-loop` 等待 `agent` 与 `llm`，`session-api` 等待 `agent`，`session-persistence` 等待 `session`，`llm-retry` 等待 `agent` 与 `session`。任何 required service 缺失时依赖 Plugin 不进入 active Fiber。 | required injection 正确，真实 reload 测试证明 session 缺失时依赖 Fiber 停止。 |
| A14 | passed | specs/desktop-agent-plugin-composition/spec.md | Optional Tools service WHEN Tools service 缺失，THEN AgentLoop 仍可处理文本请求并对 tool call 保持现有稳定失败；WHEN Tools service随后出现、消失或替换，THEN optional injection 触发 AgentLoop 重载并更新能力。 | ToolsKey 为 optional，缺失仍支持文本并稳定拒绝 tool call，服务变化由 optional injection 触发重载。 |
| A15 | passed | specs/desktop-agent-plugin-composition/spec.md | Catalog and rows remain bijective WHEN读取 `DesktopPluginCatalog` 和 `DesktopProfileBundle`，THEN两者 name 集合相等、每个 name 只出现一次，并包含原有五行与新增七行。 | Desktop catalog 与 bundle 是同一组 12 个唯一名称，含原五项与新增七项。 |
| A16 | passed | specs/desktop-agent-plugin-composition/spec.md | Default composition remains bootable WHEN OpenAI settings 为空，THEN Desktop profile 与 AgentLoop 仍完成安装，Agent 数量为零且 provider catalog 保持 dormant；WHEN调用入口未提供完整模型选择，THEN返回稳定配置错误；`gpt-4o-mini` 模型目录条目不得自动成为默认值。 | 默认 12 Fiber active、零 Agent、OpenAI dormant，且没有 gpt-4o-mini 自动默认。 |
| A17 | passed | specs/desktop-agent-plugin-composition/spec.md | Patch disable and recovery WHEN patch 禁用任一新增 row，THEN该 row 无 Fiber且依赖项按 injection 停止；WHEN patch 恢复该 row 并提供合法配置，THEN Loader 重建链条，不泄漏旧 service 或数据库句柄。 | 禁用 session 后自身 Fiber 消失且依赖链 Pending；恢复后全部 active 并创建新的 persistence service。 |
| A18 | passed | specs/desktop-agent-plugin-composition/spec.md | Automated verification WHEN实现候选提交给 Verify，THEN Definition/config contracts、Desktop bundle/Loader fixture、JVM App tests、整仓 JVM build、diff check 与依赖审计均通过；当前环境不可运行的目标平台检查以未验证风险记录，不能表述为通过。 | Runtime 的目标 JVM 测试、JVM App、整仓 JVM build、Android build、diff check 全部通过，限制如实记录。 |

## 检查

| 检查 | 命令 | 工作目录 | 状态 | 退出码 | 耗时 |
| --- | --- | --- | --- | ---: | ---: |
| Affected JVM module tests | test --include-module=agent --include-module=agent-loop --include-module=tools --include-module=session-api --include-module=session-persistence --include-module=llm-retry --include-module=llm-koog --include-module=llm-koog-openai --include-module=desktop --platform=jvm | . | passed | 0 | 9626 ms |
| JVM App integration tests | test --include-module=jvm-app --platform=jvm | . | passed | 0 | 7711 ms |
| Whole repository JVM build | build --platform=jvm | . | passed | 0 | 10030 ms |
| Affected Android module build | build -m agent -m agent-loop -m tools -m session-api -m session-persistence -m llm-retry -m llm-koog --platform=android | . | passed | 0 | 5823 ms |
| Git diff whitespace check | diff --check | . | passed | 0 | 14 ms |

## 阻塞项

_无。_

## 风险与跳过的工作

- 5 个 OpenAI live tests 因缺少 HARNESS_OPENAI_LIVE_API_KEY 跳过。
- iOS Simulator 构建在既存 credentials/settings unresolved JvmInline 处阻塞。
- 整仓 JVM test 有无关 server PermissionCommonApi 既存失败；目标模块、JVM App 和整仓 JVM build 已分别通过。
- Bundled models.dev 目录不代表每个模型已由实时服务验证支持所选 endpoint。

## 之前的迭代

| 目标周期 | 迭代 | 尝试 | 结果 | 未解决项 | 摘要 | 完成时间 |
| ---: | ---: | ---: | --- | --- | --- | --- |
| 1 | 1 | 1 | pass | — | 独立只读 Verifier 判定候选 f3416059-1956-422f-87c3-122684ca86c6 满足 A1-A18；live 与 iOS 限制已明确记录。 | 2026-09-08T14:39:45.407Z |



## 结论

独立只读 Verifier 判定候选 f3416059-1956-422f-87c3-122684ca86c6 满足 A1-A18；live 与 iOS 限制已明确记录。
