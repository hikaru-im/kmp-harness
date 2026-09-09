# 目标

补齐 Desktop Agent 运行链各模块的 `PluginDefinition`、严格配置解析、显式模型选择、真实服务依赖声明和 bundle rows，使默认 Desktop profile 能通过 Loader 组装 Session、Agent、Tools、AgentLoop、Session API、Room Persistence 与 `llm-retry`。

本 change 对齐 DSH/Cordis 的组合边界：编译期 catalog 决定宿主可加载哪些 Plugin，profile rows 决定启用和配置哪些实例，Loader 根据 `inject` 的服务可用性驱动启动与重载。KMP 不复制 Node 动态包加载，也不把平台路径或凭据放入 profile 配置。

# 范围

- 为 `agent`、`agent-loop`、`session-api`、`tools`、`llm-retry` 增加公开、稳定命名的 `PluginDefinition`。
- 让 `AgentOptions` 承载显式 `provider`、`model`、可选 `reasoningEffort` 与 `maxTokens`；AgentLoop 不再在自身配置里绑定默认模型。
- 将 `OpenAiKoogCatalog` 中的具体模型条目迁移为随应用发布的版本化 JSON snapshot；snapshot 由固定 revision 的 `models.dev` 数据生成。Kotlin 代码只保留 schema、协议模板、校验和加载逻辑，Chat Completions/Responses 只由用户在 provider settings 的 `api` 字段中选择。
- 对齐 DSH，移除 `KoogModelProfile.api` 与 `KoogModelOverride.api`：一个 provider route 使用一套 wire protocol，模型条目只描述 id、名称、容量、模态和推理能力。
- 复用现有 settings 模型目录语义：省略或空 `models` 使用 bundled snapshot，非空 `models` 完整替换，`modelOverrides` 只局部覆盖 snapshot 中已知条目；任何 settings 都不产生默认 provider/model。
- 公开现有 `SessionPluginDefinition`；整理 Room persistence definition factory，使 Desktop 用 launcher 已解析的 `HarnessHome` 绑定 `harness-sessions.db`。
- 为有配置的 Plugin 增加 `JsonObject` 严格 decoder：拒绝未知字段、错误类型、空字符串和越界数字；允许缺省配置的模块使用明确默认值。
- 补齐 `agent -> session`、`session-persistence -> session` 等真实 required injection，保留 `agent-loop -> tools` 的 optional injection。
- 将七个 Agent 链条 Plugin 加入 `DesktopPluginCatalog` 与 `DesktopProfileBundle`，保证 catalog name 集合和 bundle row name 集合一致。
- 更新模块依赖、README、Loader/Desktop 组合测试和各 config decoder contract tests。

## DSH 对照结论

| 能力 | DSH | KMP 方案 |
| --- | --- | --- |
| `session` / `agent` | Service class，无业务配置 | 严格无配置 definition；`agent` 显式依赖 `session` |
| `tools` | `mode`、`maxParallelSubCalls`，默认并行 10 | 当前执行器没有 PTC/独占分类，只暴露 `maxConcurrentCalls`，默认保持安全串行 1 |
| `agent-default-model` | 独立 service，base row 提供 `provider/model` | 本阶段不引入默认模型 service；Desktop 不硬编码模型，调用入口显式提供选择 |
| Provider 模型目录 | Provider 包维护默认目录，settings 可替换/覆盖 | 以 `models.dev` 的 MIT 数据生成 bundled snapshot；用户通过 settings 选择 API，并可替换或局部覆盖目录 |
| API 协议归属 | provider profile 可用 `api` 重定向整条 route；`models`/`modelOverrides` 没有单模型 `api` | 只保留 provider settings `api`，删除单模型 `api`，避免同一 provider 内混用两套协议 |
| `agent-loop` | 默认启用，`agents: []` 只注册 factory，不自动创建 Agent | 默认启用且不自动创建 Agent；模型来自 AgentOptions，不再是 AgentLoop 配置 |
| Session API | DSH `session-controller` 还承担 Remote/冷读/打开路径 | KMP `session-api` 只暴露已有 provider-neutral `list/create/history/prompt/cancel`，不引入 Gateway/Remote/UI |
| persistence | DSH JSONL row 配置 `root`，启动器计算 `$DSH_HOME/sessions` | KMP Room factory 由 Desktop launcher 绑定 `$HARNESS_HOME/harness-sessions.db`，row 不接受平台路径字段 |
| `llm-retry` | 空配置；策略属于 provider | 严格无配置 definition；继续读取请求开始时绑定的 `RetryPolicy` |
| profile boot | patch 层按 id 整行替换配置，Loader 由服务可用性决定激活 | 复用现有 EntryPatch/Profile Loader；不赋予 row 顺序依赖语义 |

# 非目标

- 不新增声明式 `agents` 数组，不在启动时自动创建 Agent。
- 不实现 DSH Tools 的 `native/ptc/both` 展示模式、代码运行时或工具并发分类。
- 不把 `session-api` 扩展为 Gateway、Typert Remote、冷 Session 查询、文件打开或 UI contract。
- 不改 Session、Tools、Retry 的运行算法和事件语义；AgentLoop 只调整模型选择的配置归属。
- 不允许 profile 覆盖 Harness home、数据库路径、Room driver、dispatcher、凭据或 Provider 私密值。
- 不在 Desktop 启动或模型列表请求时联网刷新第三方目录；不把 OpenAI `/v1/models` 返回的模型 ID 推断成未经验证的能力或 endpoint 支持。
- 不把 `models.dev`、LiteLLM 或 OpenRouter 当作运行时真源，也不因上游出现条目就自动改变用户已经配置的 provider route。
- 不引入 JSONL persistence，也不修改 Room schema。

# 验收示例

### Scenario: Every Desktop Agent module is profile-loadable

WHEN Desktop catalog is registered, THEN `session`、`agent`、`tools`、`agent-loop`、`session-api`、`session-persistence`、`llm-retry` 均可由稳定 row name 解析，且 catalog 与 bundle rows 集合完全一致、无重复 name。

### Scenario: Configuration is strict and typed

WHEN profile 提供合法 `agent-loop` 或 `tools` 配置，THEN decoder 生成强类型配置并应用默认值；WHEN 出现未知字段、错误类型或非正并发数，THEN Loader 在安装前明确失败且不留下 Fiber。

### Scenario: Empty-config plugins reject accidental configuration

WHEN `session`、`agent`、`session-api` 或 `llm-retry` row 带有任意配置对象，THEN definition 明确拒绝；WHEN row 不含 config，THEN 正常安装。

### Scenario: Service dependencies drive activation and reload

WHEN rows 按任意顺序加载，THEN Loader 依据 required/optional service injection 收敛到完整运行链；WHEN必需服务消失，THEN 依赖 Fiber 停止；WHEN optional Tools 出现或消失，THEN AgentLoop 安全重载。

### Scenario: Desktop owns the Room database location

WHEN `startDesktopProfile()` 使用已解析的 Harness home，THEN persistence definition 打开该 home 下独立的 `harness-sessions.db`；WHEN profile 尝试设置 path、factory 或 driver，THEN 配置被拒绝。

### Scenario: Desktop AgentLoop is active without auto-starting Agents

WHEN用户未覆盖默认 bundle，THEN `agent-loop` row 正常激活并注册 AgentFactory，但不会创建任何 Agent；WHEN Session API 使用显式 `provider/model` 创建 Agent 且对应 settings 与凭据可用，THEN可完成 prompt、Session events 与 Room flush；WHEN未提供完整模型选择，THEN以稳定“模型未配置”错误失败，不选择目录中的任意模型。

### Scenario: Bundled model catalog is reproducible and overridable

WHEN构建 OpenAI 默认目录，THEN具体模型条目来自仓库内 JSON snapshot，且 snapshot 记录 `models.dev` source revision、schema version 与许可证归属；WHEN settings 省略或提供空 `models`，THEN使用该 snapshot；WHEN提供非空 `models`，THEN完整替换默认目录；WHEN提供 `modelOverrides`，THEN只覆盖已知模型且拒绝未知 id、与非空 `models` 并用或不合法能力。用户必须在 provider `api` 中选择已安装的 Chat Completions/Responses 协议；模型或 override 中出现 `api` 必须被严格拒绝。

### Scenario: Existing disable and reload behavior is preserved

WHEN用户 patch 禁用 Agent 链中的任一 row，THEN该 row 不产生 Fiber，依赖行按 injection 状态收敛；WHEN重新启用并提供合法配置，THEN链条恢复，且 host-owned home 不被覆盖。

### Scenario: Verification covers module and assembled runtime boundaries

WHEN change 完成，THEN相关 JVM 模块测试、Desktop bundle/Loader fixture、JVM app tests、整仓 JVM build、`git diff --check` 和依赖边界审计通过；无法在当前主机运行的 Android/Apple 检查明确记录为风险。

# 约束与不变量

- Plugin 常量、definition name 和 Desktop row name 必须一一相等。
- 每个 decoder 必须在 Plugin apply 前完成完整校验；未知字段不得静默丢弃。
- 平台对象通过 definition factory/launcher 闭包注入，不进入可序列化 profile config。
- 默认 bundle 启用 AgentLoop 但不自动创建 Agent，也不设置默认模型；模型缺失或 provider route 未声明时 fail loud。
- Bundled model snapshot 是供选择和校验的目录，不是默认模型选择；加载失败、schema 不兼容、重复 id、未知 route 或无效容量必须在 Provider 激活前 fail loud。
- `llm-retry` 继续保持空配置，RetryPolicy 仍由 provider/request 绑定。
- 本 change 只补组合边界，不改变既有持久化格式和 Agent 事件时序。

# 决策

- 使用单个 Native change 顺序实现；改动集中在 Loader definitions、七个模块和 Desktop bundle，拆分会增加同一 catalog/fixture 的协调成本。
- 保留 KMP 已确认的显式 `AgentRegistry.create()` / Session API 创建方式，不照搬 DSH `agents: []` 的声明式启动能力。
- 对齐 DSH 的模型归属边界：AgentLoop 本身不拥有全局 provider/model；与 DSH 不同，Desktop 本阶段不提供默认模型 service，调用入口必须显式选择。
- Tools 首版配置只含 `maxConcurrentCalls`，默认 1；DSH PTC 与并发分类另立 change。
- Session Persistence row 不接受路径配置；Desktop 固定使用 `HarnessHome.resolve("harness-sessions.db")`。
- Bundle row 书写顺序按阅读依赖排列，但正确性只依赖 `inject`。
- Desktop 的 `agent-loop` row 默认启用且不自动创建 Agent；`gpt-4o-mini` 只保留为 OpenAI 模型目录条目，不成为默认选择。
- 不再在 Kotlin 源码中逐个构造 OpenAI 模型条目。首选上游为 MIT 许可的 `anomalyco/models.dev`；生成器固定 source revision 并映射名称、描述、context/output limit、输入模态与 reasoning/tool/structured-output 等可表达能力。LiteLLM 仅用于交叉核对，OpenRouter 仅代表其自身路由，二者不作为 bundled snapshot 主源。
- `models.dev` 不提供足以证明 OpenAI Chat Completions/Responses 兼容性的完整机器可读信息，因此 bundled catalog 只陈述通用模型能力，不承诺 endpoint 兼容性。用户选择 `api`，Harness 只校验该 API 已安装、名称合法，不假装知道上游没有提供的兼容关系。
- 保留既有 settings 规则：`models` 省略或为空表示沿用 bundled snapshot，非空表示完整替换；`modelOverrides` 与非空 `models` 互斥，只能覆盖 snapshot 已知 id。Provider 的启用/禁用继续由 settings provider entry 决定。
- 对齐 DSH 的单协议 route：provider `api` 对该 route 的全部模型生效；模型配置不再允许覆盖 `api`。需要同时使用 Chat Completions 和 Responses 时声明两个不同的 provider route，而不是在一个 route 中逐模型混用。

# 实施规划（大白话）

1. **把模型清单从 Kotlin 文件里搬出去。** `OpenAiKoogCatalog.kt` 不再手写 `gpt-4o-mini`、`o3-mini` 等具体条目，只负责读取目录、校验目录和建立 Koog route。
2. **用 `models.dev` 当原料，不在用户启动应用时访问它。** 仓库提供一个更新脚本；维护者需要更新目录时才运行脚本。脚本读取固定的 `models.dev` revision，避免今天构建和明天构建得到不同结果。
3. **把处理后的结果跟应用一起打包。** 生成的 JSON 放进 `llm-koog-openai` 模块 resources，包含模型 id、显示名、说明、context/output 上限、输入模态、推理能力，以及来源仓库、revision、schema version 和 MIT notice。Desktop 离线也能正常列模型。
4. **由用户在 provider 上决定接口。** `api` 对这个 provider route 下的全部模型生效。一个 route 走 Chat Completions，另一个 route 走 Responses；如果两套接口都要用，就配置两个 route。模型条目本身不再出现 `api`。
5. **settings 继续拥有最终决定权。** 用户不写 `models` 或写空列表，就使用随应用发布的目录；写非空 `models`，就完全采用用户目录；只想改名字、容量、模态或推理配置时使用 `modelOverrides`。错误 id、错误 route、重复模型和冲突配置直接报错。
6. **目录只是可选项，不替用户做选择。** 即使 bundled JSON 中排在第一位的是 `gpt-4o-mini`，系统也不会把它当默认模型。创建 Agent 时仍必须明确给出 provider/model。
7. **OpenAI `/v1/models` 以后只能做账号可见性检查。** 它可以告诉我们“这个 key 看得到哪些 id”，但不能告诉我们完整能力。本 change 不自动调用它，也不会因为它返回一个新 id 就宣称该模型支持工具、图片、推理或某个 endpoint。
8. **更新目录必须可审查。** 更新脚本输出稳定排序；同一个 source revision 重跑必须得到完全相同的 JSON。目录更新作为普通 Git diff 审查，不允许上游新增模型在无人检查时自动进入发布包。

计划新增或调整的边界：

| 位置 | 作用 |
| --- | --- |
| `modules/llm-koog-openai/resources/...json` | 随应用发布的 OpenAI 默认模型目录 |
| `modules/llm-koog-openai/.../catalog` | snapshot schema、严格 decoder、能力映射和 route 装配 |
| 仓库内 catalog 更新脚本 | 从固定 `models.dev` revision 生成稳定 JSON |
| `settings.yaml` | 替换整个目录或局部覆盖，保持现有语义 |

# 验证预期

- Definition/config 单测逐个覆盖成功、缺省、未知字段和类型/范围错误。
- Desktop 组合测试验证完整 row 顺序、catalog 集合、随机 row 顺序的依赖收敛、disable/re-enable、Room 文件位置和 provider 缺失失败边界。
- Catalog 测试验证 bundled JSON 能加载、同一 source revision 生成结果稳定、模型 id/route 唯一、未知字段与非法容量失败、settings 的保留/替换/局部覆盖语义不回退。
- 离线测试禁止访问 `models.dev` 和 OpenAI `/v1/models`，仍能列出 bundled catalog；上游目录更新只通过显式脚本发生。
- 运行相关 JVM tests、JVM App tests、JVM build、`git diff --check` 与模块依赖审计。
