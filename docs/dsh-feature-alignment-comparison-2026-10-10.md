# DSH 对口功能对照分析（修订 r2，基于 DSH 0.2.1）

日期：2026-10-10。
口径：DSH 本地只读副本 `references/deepseek-harness`，固定提交 `d743267388641bc76f17c45ce8b4c231aed1d32c`（`dsh-v0.2.1-alpha.2`，2026-10-09）；cordis 参考副本 `91708e7befacbd7990f8dacf90c330c09103e809`（2026-10-10）。
本仓库：工作区提交 `7e7d520`（2026-09-29，改动均未提交）。
范围：定时任务、子 Agent、插件、工作树；附录为终端。
方法：只读。读 DSH 副本源码与官方文档、本仓库源码、两端既有契约文档。未运行构建或测试，未修改任何产品代码。

> **修订说明**：本文件 r1 基于 `0a53fb55`（`dsh-v0.1.2-alpha.2`，2026-08-30）。r1 的"DSH 明确不支持 Cron"、"DSH 没有 worktree 子系统"两条核心论断在 0.2.1 上已经不成立，第三节的插件结论也不完整。r2 按 0.2.1 重做全部四项。r1 与 r2 之间 DSH 相隔 6945 个提交、21 个发布 tag。

## 判据

"做过 DSH 对齐"指三件可复核的事：能指出 DSH 侧对应 package；有字段级或语义级的映射记录；该记录是本仓库的正式文档，不是委派 worker 的提示词。

按此判据，本仓库的 DSH 对齐文档仍集中在少数文件：`docs/cordis-alignment.md`（Phase 3-8 + DSH Profile Boot）、`docs/dsh-slot-contract-comparison.md`（Slot 字段级）、`docs/harness-host-api.md`、`docs/session-s1-plan.md`、`docs/session-agent-loop-plan.md`、`docs/reference-sync-and-ui-slots-plan.md`、`docs/ui-slot-integration-plan.md`、`docs/advanced-context.md`、`docs/desktop-ui-cross-platform-codex-plan.md`、`modules/llm-koog/DSH_BOUNDARY_AUDIT.md`。

`docs/desktop-ui/`（含 `reverse-engineering/` 下 50 多个文档）对 DSH 仍为零命中；目录里出现的 `deepseek-harness` 全在 `verification/**/worker-jsonl`，内容是"不要在 references 上直接操作"这类安全规则。

## 结论摘要

1. 0.1.2 到 0.2.1 之间，DSH 补上了 r1 判定为"上游没有"的三项中的两项：schedule 扩到六种规则并支持 cron，worktree 出现了实验包，插件管理从"只有只读清单"变成了完整的 plugin-manager。**"上游没有"是一个会过期的事实。**
2. 因此缺口形态变了。不再是"本仓库自造、上游空白"，而是**两边在同一片区域各有实现、语义模型不同、且没有任何一次字段级对照**。这种缺口比空白更危险。
3. 对齐强度仍然与"有没有正式对照文档"高度重合：runtime / loader / slot 有文档，最稳；其余四项没有文档，所以上游 40 天里做的事本仓库完全不知道。
4. 四项里唯一稳定的形态是子 Agent：本仓库 331 行、上游 13214 行，差距 40 倍，且接口形状（`spawn_agent` 三字段扁平 schema）已固化。
5. 最根本的机制问题：仓库里没有"上游变更 → 对齐复查"的触发点。`references/` 可以 40 天不更新，其间 DSH 发了 21 个 tag。

## 量级总表

| 功能 | DSH 0.2.1 侧 | 本仓库侧 | 有字段级对照 | 量级比 |
| --- | --- | --- | --- | --- |
| 插件运行时基建 | `vendor/loader`、`vendor/cordis`、`packages/core` | `modules/loader` 775 + `modules/runtime` | 有（`cordis-alignment.md`） | 相当 |
| 插件管理 | `boot/plugin-manager` 2487 + `client/ui-plugin-manager` 2346 + `host/plugin-inventory` 189 + `client/ui-settings-plugins` 125 + `client/ui-settings-plugin-inventory` 169 | `client/plugins` 963 | 无 | 约 1 : 5.5 |
| 子 Agent | `packages/subagent` 9 个包 13214 | `modules/subagent-tools` 331 | 无 | 约 1 : 40 |
| 定时任务 | `schedule/schedule` 3346 + `schedule/tool-schedule` 582 + `client/ui-schedule` 3082 | `scheduling` 2616 | 无 | 约 1 : 2.7 |
| 工作树 | `experimental/worktree` 351 + `experimental/tool-worktree` 43 | `client/git` 2088 | 无 | 约 5 : 1 |
| 终端（附录） | `terminal` 653 + `terminal-bash` 1362 + `experimental/tool-terminal` | `modules/exec-jvm` 1659 | 无 | 相当 |

行数只统计生产源文件，不含 `.spec.ts`/`tests/`。量级比只用于说明规模差异，不代表质量。

## 一、定时任务 / Cron

### DSH 0.2.1 是什么

`packages/schedule`：`schedule` 3346 行（宿主侧持久化、调度、检查、删除）、`tool-schedule` 582 行（面向模型的四个工具）。客户端 `client/ui-schedule` 3082 行。契约文档 `docs/subsystems/schedule.md`。

- **六种创建选择器**：`after_seconds`、`at`、`every_seconds`（≥60 秒，按经过时间）、`daily`、`weekly`、`cron`。
- **cron**：严格的五字段 Vixie 方言 `minute hour day-of-month month day-of-week` 加显式 IANA 时区，例 `cron: { expression: "*/15 9-17 * * 1-5", time_zone: "Asia/Shanghai" }`。拒绝 `L`/`W`/`#`、月份或星期名称、`@daily` 宏、六字段、越界值、反向范围、零步长、空字段，错误码 `invalid_rule` 并指名违规字段。最小间隔一分钟。创建时存规范化表达式（合并重复与相邻、均匀步长写成 `a-b/n`、`1` 步长丢弃、周日统一写 `0`），持久化解码拒绝非规范表达式。
- **daily / weekly**：本地时间加显式时区；DST 缺口跳过、重叠取较早时点，每天只在该日期上取一次。
- **投递模型**：`deliveryMode: 'host'`。到期提醒作为**原会话的普通 follow-up 消息**投递，"不是邮件、短信或推送"。重复任务只补**最近一次**错过的发生时点，错过的区间不累积；同一会话到期的多条合并成一条消息。Host 在到期时恢复冷 Session。
- **记录**：必需 `title`（≤120 字符，缺失/空/带空白/超长都导致持久化解码失败 `ScheduleLogError`）、`status: active | inactive`、可选 `lastDelivery`。只有显式删除才移除任务行，删除会一并移除已保存的投递记录。
- **并发**：`ScheduleService` 拥有一个版本 1 domain、全局唯一 id、**一个 Host 定时器**；管理写入与投递写入**共用一个 FIFO**；更新在队列内比较完整 `expected` 记录并采样时间；写入一旦开始，取消不回滚；超过平台定时器上限的延迟分段处理。
- **历史兼容**：旧 `schedule/change` 事件只在 `LegacyScheduleRecord`（after/at/every）里可解码；daily/weekly/cron 只属于当前 Host 记录。历史事件不填充任务表，不隐式迁移。
- **模型接口**：`schedule_create`、`schedule_list`、`schedule_update`（可改 name/instruction/timing）、`schedule_delete`，由 `standard`、`cordis`、`ptc` 三个 shipped Web preset 挂载，`minimal` 不挂载。

### 本仓库是什么

`apps/shared/src/im/hikaru/harness/client/scheduling/` 2183 行 + `apps/jvm-app/src/im/hikaru/harness/desktop/scheduling/` 433 行，合计 2616 行。核心：`ScheduledExecutor.kt` 500、`ScheduledRepository.kt` 381、`ScheduledManagementPage.kt` 348、`ScheduledExecutorOwner.kt` 187、`DesktopScheduledStore.kt` 156、`ScheduledPageLayout.kt` 152、`DesktopScheduledSessionEvidence.kt` 136、`ScheduledModels.kt` 130、`ScheduledLedgerRules.kt` 97。

- **四种规则**：`Hourly(intervalHours, anchorEpochMillis)`、`Daily(hour, minute, zoneId)`、`Weekdays(hour, minute, zoneId)`、`Weekly(day, hour, minute, zoneId)`。没有 `after`/`every`，也没有 cron 表达式。
- **两种目标**：`Cron(destination = projectless | cwd)` 和 `Heartbeat(sessionId)`。
- **记录**：`ScheduledTask(id, revision, name, prompt, target, schedule, model, status = Paused | Active)`；`ScheduledSnapshot(owner, revision, tasks, deletedTaskIds, runs, cursors)`，任务、发生时点与运行证据共用一个 durable revision。
- **并发与崩溃语义**：executor 注释写明 "Durable intents precede side effects; ambiguous delivery is never replayed"；有本机 leader lease、`unresolvedDelivery`/`uncalledPrompt` 集合、崩溃后不重放 prompt、`config.verifiedCronExecution` 生产闸门（当前为 false）。
- **操作**：`ScheduledRepository` 提供 create / updateDraft / delete；管理页 348 行加页面布局 152 行。

### 逐条对照

| 维度 | DSH 0.2.1 | 本仓库 | 判定 |
| --- | --- | --- | --- |
| 规则集 | after / at / every / daily / weekly / cron | Hourly / Daily / Weekdays / Weekly | 只有 Daily、Weekly 概念重叠 |
| cron 表达式 | 五字段 Vixie，带规范化与解码校验 | 无（"Cron" 是目标类型名，不是表达式） | 本仓库少做 |
| 一次性延迟 | after_seconds | 无 | 本仓库少做 |
| 固定间隔 | every_seconds ≥60s | Hourly 间隔 | 语义相近，边界不同 |
| 投递目标 | 原会话 | projectless / cwd / 指定 session | 本仓库多做 |
| 投递身份 | `deliveryMode: 'host'`，普通 follow-up 消息，不 steer 当前工作 | 未见对应公开契约 | 需对齐 |
| 错过处理 | 只补最近一次；同会话合并成一条 | 文档未给出等价契约 | 需对齐 |
| 任务命名 | 必需 `title` ≤120，不可由指令推导 | 有 `name`，约束未对照 | 部分对应 |
| 状态 | active / inactive | Paused / Active | 概念对应 |
| 删除 | 移除任务行与其投递记录 | delete + `deletedTaskIds` | 概念对应 |
| 并发 | 一个 Host 定时器 + 全程 FIFO，无租约 | leader lease + 单飞 + 生命周期 fence | 模型不同 |
| 崩溃恢复 | 不以崩溃恢复为契约 | 不重放 + 跨 JVM crash 验证 | 本仓库多做 |
| 跨重启 | 任务保留，冷 Session 到期恢复 | 有持久账本 | 概念对应 |
| 客户端 | Automation tasks page + 侧栏 tab + 会话头时钟 + transcript card（3082 行） | 管理页 + 页面布局（500 行） | 本仓库少做 |
| 模型工具 | 四个工具，按 preset 挂载 | 未发现面向模型的工具 | 本仓库少做 |

结论：0.1.2 时这一项是"上游明确不做"，现在是**两侧都在做、方向一致、模型不同**。DSH 选择以 Session 为中心、单一 Host 定时器、全局 FIFO 串行；本仓库选择以 owner/账本为中心、租约加崩溃恢复、绝不重放。规则集基本不相交。真正值得先对齐的是投递语义这一组（普通 follow-up 消息、不 steer、错过只补一次、同会话合并），因为 DSH 对此有明确公开契约，而本仓库没有对应的公开约定。

## 二、子 Agent

### DSH 0.2.1 是什么

`packages/subagent`：9 个包，13214 行。相对 0.1.2 有两处结构性变化：`subagent-in-process-driver` 并入 `subagent`，`tool-subagent-report`（子到父上报通道）**被移除**。

| 包 | 行数 | 职责 |
| --- | --- | --- |
| `subagent` | 5373 | `ctx.subagents` 服务、provider 注册表、一次性 run、可续聊子级、发现 |
| `subagent-codex` | 2162 | 通过官方 app-server 协议跑真实 Codex 子级 |
| `subagent-claude-code` | 1472 | 通过官方 Agent SDK 跑真实 Claude Code 子级 |
| `subagent-acp` | 1345 | 通过 ACP 跑进程外子级 |
| `tool-subagent` | 1212 | 面向模型的委派工具 |
| `subagent-dsh-sdk` | 1080 | 通过 TS SDK 跑进程外 Harness 子级 |
| `tool-subagent-control` | 326 | 相邻 Agent 消息、中断、列举 |
| `subagent-spawn-in-process` | 159 | 全新进程内子级 |
| `subagent-fork-in-process` | 85 | 从父级已完成历史 fork 的子级 |

关键设计未变：多 provider 并存、按名字注册；能力用静态描述符声明并在请求前校验，缺失即报 `UNSUPPORTED_CAPABILITY`，不允许"接受后忽略"；可续聊子级以 `prepareContinuable` 方法是否存在作为能力开关；每个子级对父级可见，无论运行中还是已存储。

### 本仓库是什么

`modules/subagent-tools`：3 个源文件 331 行。一个工具 `spawn_agent`，扁平三字段 schema `prompt / provider / model`。配套只有会话侧：`SessionHeader` 的 `parentSession` / `origin = SUBAGENT`、`LiveSessionDescendants`、`SessionApi.readChildHistory`，以及依据 Codex ASAR 复刻的 `ThreadPanels` 子 Agent 历史面板。

### 逐条对照

| 维度 | DSH 0.2.1 | 本仓库 | 判定 |
| --- | --- | --- | --- |
| provider 并存 | 6 种，按名注册 | 单一进程内路径 | 少做 |
| 从父级历史 fork | 有 | 无 | 少做 |
| 可续聊子级 | 有 | 无 | 少做 |
| 相邻 Agent 消息 / 中断 / 列举 | 有 | 无 | 少做 |
| 子到父上报通道 | 0.1.2 有，0.2.1 已移除 | 无 | 两边都没有 |
| capability 声明与 fail-loud | 有 | 无 | 少做 |
| 进程外后端 | 4 种（acp / codex / claude-code / sdk） | 无 | 少做 |
| 子级发现 | 服务 + session store | `LiveSessionDescendants` + `readChildHistory` | 对应 |
| 历史面板复用对话组件 | 无 Web 侧对位记录 | `ThreadPanels` + `ChildAgentDetail` | 本仓库特有 |

结论：四项里唯一形态稳定的缺口，且量级 1 : 40。本仓库只实现了"创建一个子级"和"读子级历史"，委派生态的其余部分没有。接口形状与 DSH 的 provider + capability 模型不兼容，将来补齐需要重做协议与面板。

## 三、插件

必须分成两层，否则会误判。

### (a) 插件运行时基建 —— 对齐最好的一层

本仓库：`modules/loader` 775 行（`Loader / Entry / Registry / PluginCatalog / LoaderFactory / ConfigAdapter`）+ `modules/runtime`（`Context / Fiber / EffectScope`）+ `modules/bundle/desktop` 编译期 `PluginCatalog`。

DSH 对位：`vendor/loader`、`vendor/cordis`、`packages/core`、`packages/bundle`（`base / web-app / headless / sdk-app` 等 profile）。

`docs/cordis-alignment.md` 记录 Phase 3-8 的逐项对应，含平台差异声明（DSH 在 Node 里动态 import，KMP 改为编译期 Catalog）与有意排除的能力（`!!js`、classpath 扫描、`ServiceLoader`、动态下载、源码 HMR）。**这是全项目唯一有字段级对照且有边界声明的部分。**

### (b) 插件管理 —— 上游 0.2.1 从"只读"补成了完整实现

本仓库：`apps/shared/src/im/hikaru/harness/client/plugins/` 963 行（`InstalledPluginsPage.kt` 462、`InstalledPluginsController.kt` 294、`PluginModels.kt` 155、`PluginManagement.kt` 34、`PluginRepository.kt` 18）。管理能力由 `LocalPluginManagementRepository` 定义：`registerLocal(directory, expectedRevision)`、`setEnabled(id, enabled, expectedRevision)`、`removeRegistration(...)`、`updateLocal(...)`（另有 `canUpdateLocal`），全部带 revision fence。契约文档 `docs/desktop-ui/plugins-page-contract-2026-10-08.md` 明确：登记的是本机目录，**完整包安装/升级/卸载未实现，页面不能把"移除登记"写成卸载**。

DSH 0.2.1 现在有五块：

| 包 | 行数 | 职责 |
| --- | --- | --- |
| `boot/plugin-manager` | 2487 | 启用/停用 profile 插件条目、选择已安装 bundle、**安装或移除外部 bundle**（走包管理器），`plugin_manager` 工具 |
| `client/ui-plugin-manager` | 2346 | Web 侧栏 **Plugins** 页：开关 bundle 与条目、安装（Host 先读 spec，可看 pnpm 输出、可停止运行）、卸载需确认；注册了配置页的插件在此编辑 |
| `host/plugin-inventory` | 189 | `pluginInventory/list`：**只读**点快照，含条目 id、模块说明符、生效启用状态、实时 phase |
| `client/ui-settings-plugin-inventory` + `client/ui-settings-plugins` | 169 + 125 | 设置里的只读清单 tab，以及只拥有导航与 tab 行的壳 |
| `extensions` | 约 21000 | 运行时 API 只读发现、进程内 Host/Client runner、历史生成插件卡片；Creator mode 通过 Plugin Manager 安装持久插件 |

`boot/plugin-manager` 的权限模型很明确：每个工具动作需要 `danger-full-access` 或该次调用的单独审批；低沙箱模式下 `ask` 会请求审批，`never`、拒绝、取消或审批通道不可用都会阻止执行；审批不改变会话的权限模式；安装的 Host 代码在 workspace 沙箱之外进程内执行；profile 变更跨会话持久。

### 逐条对照

| 维度 | DSH 0.2.1 | 本仓库 | 判定 |
| --- | --- | --- | --- |
| 列出插件 | 只读快照（含实时 phase、失败项） | 管理页自身即列表 | 对应但形态不同 |
| 启用 / 停用 | 有 | 有（`setEnabled`） | 对应 |
| 移除 | 有（卸载 bundle） | 有（`removeRegistration`，仅移除登记） | 弱对应 |
| 安装外部包 | 有（包管理器 + spec 读取 + 输出流） | 无（只能登记本机已存在目录） | 本仓库少做 |
| 本地更新 | 无对位 | 有（`updateLocal`，从源目录） | 本仓库特有 |
| 审批 / 权限门 | 有明确门控与审批语义 | 未见对应定义 | 本仓库少做 |
| 插件自身配置页 | 有（各官方插件同包注册） | 无 | 本仓库少做 |
| 只读诊断清单 | 独立 inventory（189 行） | 无（列表即管理面） | 本仓库少做 |
| Skills | `packages/skill`（868 + 433 + 1056 + …） | 仅在页面展示行 | 未对照 |
| 运行时动态插件 | `extensions`（约 21000 行） | 明确不做（`cordis-alignment.md` 排除） | 有意的对位 |

结论：r1 写"DSH 明确不做 enable/disable/add/remove"只对 0.1.2 成立。0.2.1 的 `plugin-manager` 正好覆盖本仓库这片区域，而且**更完整**：真安装、有输出流、有明确审批门。所以这一项的对齐缺口方向变了——不是本仓库多做，而是**本仓库少了安装与权限门，同时上游多了只读诊断层与插件配置页**。

## 四、工作树

### DSH 0.2.1 是什么

新增，仍在 `experimental`：`experimental/worktree` 351 行（`index.ts` 178、`process.ts` 84、`directory.ts` 25、`types.ts` 21）+ `experimental/tool-worktree` 43 行。**所有 shipped profile 默认关闭**，在 GUI 的 Plugins → Official → Git Worktrees 打开；关闭只移除 `create_worktree` 工具，不删除已存在的 checkout 与分支。

- `ctx.worktrees.create(agent, { name?, from? }, signal?)`：解析源版本、创建新分支与 checkout，然后通过 `ctx.workingDirectory.set` **把调用方 Session 的当前目录切进去**。
- `from` 在创建前解析为本地 commit，默认 `HEAD`；**不复制**源目录的暂存、未暂存、未跟踪与已忽略文件。
- 默认路径 `<当前 checkout root>/.agents/worktrees/<name>`；省略名字时用 `worktree-<uuid>`。仅在分配创建池子时写一个内容为 `*` 的 `.gitignore`，已有文件与忽略规则保留。
- 创建期间**禁用**仓库配置的 Git clean、smudge 与 process filter，且不修改仓库配置。已存在的分支或 checkout 路径报错。
- 返回 `path`、`branch`、`baseCommit`、`repositoryRoot`。
- 设计立场明确：**本包不保留任何自己的投影**——"Git owns checkout registration and the working-directory service owns the Session directory; this package retains no independent projection"，也没有 runtime invariant companion。
- 全部 Git 与目录分配都走已挂载的 subprocess 与 sandbox provider，沿用调用 Session 的文件策略；创建不会扩大对目标目录或共享 Git 管理目录的写权限。

### 本仓库是什么

`apps/shared/src/im/hikaru/harness/client/git/` 2088 行：`GitPages.kt` 539、`GitController.kt` 492、`GitModels.kt` 342、`ManagedWorktreeModels.kt` 306、`GitPageComponents.kt` 212、`ManagedWorktreeController.kt` 197。另有 `apps/jvm-app` 侧实现与 9 个 worktree 相关交互测试。

能力：Git 读取 / 创建 / 已有分支切换 / 已暂存提交；managed inventory（`ManagedWorktreeIntent` / `Evidence` / `Snapshot` / `ManagedRegistrationPhase`）；pin 与 header 指纹（`PinKey(id, headerFingerprint)`，"an id reused by a different header never inherits a pin"）；一整套会话观测状态（`ManagedSessionObservationStatus` / `ManagedSessionIssueKind` / `ManagedObservedSession`）；worktree chat archive 与恢复。

### 逐条对照

| 维度 | DSH 0.2.1 | 本仓库 | 判定 |
| --- | --- | --- | --- |
| 创建隔离 checkout | 有 | 有 | 对应 |
| 源目录不动 | 有（不复制任何未提交状态） | 有（同一约束） | 对应 |
| 切换调用方会话目录 | 有（`workingDirectory.set`） | 桌面页面形态，是否切换未核实 | 待核实 |
| 默认关闭 / 显式 opt-in | 有（每个 shipped profile 都关） | 桌面页面直接可见 | 形态不同 |
| 禁用 checkout filter | 有 | 未见对应 | 待核实 |
| managed inventory / 投影 | **刻意不做**，不留独立投影 | 有（intent / evidence / snapshot） | 本仓库多做 |
| pin 与指纹保护 | 无 | 有 | 本仓库特有 |
| 会话观测 | 无 | 有（完整状态机） | 本仓库特有 |
| 归档 / 恢复 | 无 | 有 | 本仓库特有 |
| 模型工具 | `create_worktree` | 无对位工具记录 | 上游特有 |

结论：这一项从 r1 的"本仓库特有"变成"**上游有一个极简版，本仓库大幅超出**"。上游用 351 行实现"创建并进入"，并且**明确拒绝**保留任何独立投影（理由写在 README 里：checkout 注册归 Git，Session 目录归 working directory 服务）；本仓库则围绕同一动作建了 intent / evidence / snapshot / pin / 会话观测 / 归档一整套。这是一个可以直接拿来质疑范围的问题：那套投影换来了什么，上游明确认为不需要。

## 五、附录：终端

DSH：`packages/terminal` 653 + `packages/terminal-bash` 1362 + `experimental/terminal-bundle` + `experimental/tool-terminal`（含 `render.ts` 177）。语义是 owner-scoped 的 `ctx.terminals` 常驻 PTY 会话，shell 与 REPL 状态跨工具调用保留，Session 是进程内对象、不跨 Host 重启。

本仓库：`modules/exec-jvm` 1659 行（`JvmSessionTerminalHost.kt` 265、`JvmTerminalInputAdapter.kt` 158 等），依赖 JetBrains 官方 `pty4j:0.14.0` 与 `jediterm-core:3.76`（选型见 `docs/desktop-ui/reverse-engineering/terminal-dependency-analysis-2026-10-09.md`）。自研的是外层：进程生命周期（`ExecProcessProducer`）、会话归属与绑定上限、屏幕快照与增量复制协议。

| 维度 | DSH 0.2.1 | 本仓库 | 判定 |
| --- | --- | --- | --- |
| PTY 与终端仿真 | 自研 TS | JetBrains pty4j + jediterm-core | 技术路线不同，非对齐关系 |
| 会话归属 | owner-scoped `ctx.terminals` | `JvmSessionTerminalHost` | 概念对应，无字段对照 |
| 快照 / 增量 | 包内实现 | 自建 common DTO 快照协议 | 未对照 |
| 模型工具 | `tool-terminal` | 未发现 | 上游特有 |

## 六、跨项规律

1. **"上游没有"会过期。** r1 依 0.1.2 判定三项无对位；40 天后两项有了实现，第三项（插件管理）从只读补成完整。任何"DSH 没做 X"的结论都必须带 pin commit 与复查日期，否则会像 r1 一样在两个月内失效。
2. **缺口形态从"空白"变成"并行分叉"。** 现在四项里三项是"两边都有、模型不同、没有过一次字段级对照"。空白会让人犹豫；并行分叉会让人各自认为自己对。
3. **对齐强度与"有没有正式对照文档"完全重合。** 有文档的地方（Cordis / loader / slot）即使经历 6945 个上游提交也没有明显漂移；没有文档的地方，上游做了什么本仓库无从知晓。
4. **本仓库的范围扩张有明确诱因**：桌面端页面需要一个动作，host 侧就得补一整套语义。当上游在同一区域有更小的实现时（worktree 351 行、plugin-inventory 189 行），这个诱因就能被外部证据检验；当上游没有时（r1 时的 cron），只能自证。
5. **唯一稳定的缺口是子 Agent**：1 : 40 的量级差，且接口形状已固化。

## 七、风险

1. **上游事实过期**：任何"上游没做"的判断都需要刷新证据。本文件 r1 即是反例。
2. **规则集分叉**：schedule 两侧规则不相交（after/every/cron 对 Hourly/Heartbeat），将来若要与上游对齐，需要一次迁移设计而非增量修补。
3. **投递语义无公开契约**：上游明确写了"普通 follow-up 消息、不 steer、错过只补一次、同会话合并"，本仓库文档没有等价约定，行为正确性只能靠自测。
4. **worktree 投影的收益未被检验**：上游刻意不做，本仓库做了 intent/evidence/snapshot/pin/观测/归档；继续加码前应先确认这套投影解决了什么真实问题。
5. **插件的权限模型缺口**：上游的安装动作有明确门控与审批语义；本仓库只登记本机目录。若将来支持真安装，权限模型需要先定义。
6. **子 Agent 接口已固化**：`spawn_agent` 三字段扁平 schema 与 provider + capability 模型不兼容，补齐需要重做协议。
7. **对齐欠账仍在**：`docs/reference-sync-and-ui-slots-plan.md` 第 157 行要求对 DSH 的 Slot、Plugin metadata、Session/Tool projection 做字段级对照。Slot 做了（`dsh-slot-contract-comparison.md`），后两项没做。
8. **没有上游变更的触发点**：`references/` 可以 40 天不更新，其间 21 个 tag、6945 个提交无人察觉。

## 八、可选动作（均不改产品代码）

1. 把本文件作为四项基线，固定 DSH pin 与复查日期；后续每份"上游没做 X"的判断都带同样的头字段。
2. 在 `reference-sync-and-ui-slots-plan.md` 里把欠账从两条扩到六条（schedule 投递语义、subagent provider/capability、plugin-manager 与 plugin-inventory、worktree 投影取舍、terminal 归属、slot 已完成）。
3. 给 `references/` 加一个定期复查触发（automation 或 comet），避免再次出现 40 天漂移；复查时只跑 `./scripts/update-references.sh --check` 与一次 `git log` 差异扫描。
4. 若要收敛 cron，先只对齐投递语义那一组（普通 follow-up、不 steer、错过只补一次、同会话合并）——上游对它有明确公开契约，本仓库没有。
5. 若要收敛 worktree，先回答"intent / evidence / snapshot / pin / 会话观测"各自解决了什么上游认为不需要解决的问题。
