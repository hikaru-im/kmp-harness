# Codex 26.1027.61615 → Harness 迁移差距摘要

日期：2026-11-06。状态：**只读分析；未修改 `apps/**`，未启动 Electron，未重新解码。**

- 完整矩阵（95 行、逐条证据、55 路由附录）：[`ui-migration-gap-matrix.md`](ui-migration-gap-matrix.md)。本文只摘 Top 20、迁移顺序、Phase 2 backlog 与 unknown，所有断言都能在矩阵中按 ID 找到出处。
- Codex 基线：`26.1027.61615`（build `24141`），解码产物在 [`references/codex-26.1027.61615-24141/full-restore/`](../references/codex-26.1027.61615-24141/full-restore/README.md)。
- Harness 基线：`feat-SuiYI` @ `e4296aad` + 当前工作区已有 S-01/S-02 改动。本文不回退、不覆盖这些改动。

## 0. 决策记录（2026-11-06）

| 编号 | 决定 |
|---|---|
| D-01 | `billing`、`teams`、`parental-controls`、`cloud-computer` 一期不做，列入 Phase 2 backlog（3.4） |
| D-02 | `aligned` 采用「结构+视觉」双层判定；视觉层需同视口截图或交互基线。SET-01、RESP-02、TERM-06 由 `aligned` 降为 `partial`（缺视觉基线） |
| D-03 | 迁移顺序保持 M0–M6；M0 增加视觉基线验收，不重排阶段 |
| D-04 | Harness 自有扩展逐项决定，未决定前不删除；完整清单见矩阵 3.10，摘要见 3.5 |
| D-05 | 模型管理（SET-29/30）保留并标注为 Harness 扩展，不纳入 Codex 对齐验收 |
| D-06 | 账号 rail（NAV-01）：移除；账号只保留在个人资料菜单/设置，向 Codex 对齐 |
| D-07 | 资料库占位页（SIDE-08）保留占位，不实现文件库；继续标 `divergent` |
| D-08 | 主题编码（SET-12）移除导入导出；其余外观控件继续按 Codex 对齐 |
| D-09 | 设置侧栏搜索（SET-04）选择对齐 Codex：按占位符「搜索」与视觉细节对齐，补同视口基线，不移除 |

**修正记录（非用户决定）**：C-01（2026-11-06）——SET-04 曾误标为 Harness 扩展；`settings-rail-inventory.json` 只统计 button，`screenshots/general.png` 证明 Codex 设置页有搜索框。SET-04 改为 `partial`，并从 3.5/矩阵 3.10 的扩展清单移除；处理结论见 D-09。

## 1. 总量

| 区域 | 条目 | aligned | partial | divergent | missing | unknown |
|---|---:|---:|---:|---:|---:|---:|
| settings（含外壳与 19 面板） | 26 | 0 | 7 | 5 | 12 | 2 |
| shell / nav | 8 | 0 | 5 | 1 | 0 | 2 |
| sidebar / landing | 8 | 0 | 7 | 1 | 0 | 0 |
| conversation / composer | 16 | 0 | 10 | 2 | 4 | 0 |
| Git / worktree | 10 | 0 | 5 | 1 | 4 | 0 |
| scheduled | 8 | 0 | 5 | 0 | 3 | 0 |
| plugins / account | 8 | 0 | 2 | 4 | 2 | 0 |
| terminal / file panel | 6 | 0 | 4 | 0 | 2 | 0 |
| responsive | 5 | 0 | 2 | 1 | 0 | 2 |
| **合计** | **95** | **0** | **47** | **15** | **27** | **6** |

> 统计口径与矩阵 0.1 节一致（95 行）。核心事实：**0 条达到 aligned**；原 3 条（SET-01 设置语义身份、RESP-02 侧栏宽度 clamp、TERM-06 文件搜索）因缺同视口视觉基线降为 `partial`。其余要么部分实现，要么结构不同，要么完全缺失。
>
> scope 统计：`deferred_phase2: 4`（`billing`、`teams`、`parental-controls`、`cloud-computer`）。这 4 项是 static-only，见 3.4，不进入上表状态列。

## 2. Top 20 高优先级差距

排序原则：先核心编码回路（P0），再运行态已观测的主壳层/主流程（P1），最后是依赖产品范围决定的项。

| # | ID | 差距 | 状态 | 优先级 |
|---:|---|---|---|---|
| 1 | GIT-01 | 线程侧栏没有 Changes/Review 标签页；Git 只在设置页 | missing | P0 |
| 2 | CONV-12 | Composer 附件按钮是禁用占位 | missing | P0 |
| 3 | PLG-03 / PLG-04 | 插件管理缺 apps/mcps/skills/marketplace；MCP 完全无 UI | missing | P0 |
| 4 | SET-02 | 设置侧栏 8 项 vs Codex 19 个运行态面板；12 个面板缺失 | divergent | P1 |
| 5 | SET-13 | 已归档聊天没有管理面（搜索/恢复/删除/全量删除确认） | missing | P1 |
| 6 | CONV-02 | 对话没有 turn 级虚拟化/滚动锚定 | divergent | P1 |
| 7 | CONV-11 | Composer 缺 plan 模式、Escape 停止、动态 submit shortcut | divergent | P1 |
| 8 | CONV-13 | Follow-up 队列无 UI | missing | P1 |
| 9 | CONV-01 / CONV-09 | 标题栏缺 Git actions 与 turn diff 摘要 | partial / missing | P1 |
| 10 | TERM-03 | 没有文件浏览器/文件树 | missing | P1 |
| 11 | GIT-08 / SET-26 | Git 设置页是仓库操作台，不是 Codex 的 9 项 Git 偏好 | divergent | P1 |
| 12 | GIT-03 | 分支切换器只在设置页，不在 composer | partial | P1 |
| 13 | GIT-10 | 没有 stage/unstage、来源/stage 过滤 | missing | P1 |
| 14 | GIT-05 | 没有 commit & push / Create PR | missing | P1 |
| 15 | SET-25 / PLG-05 | Connections 页是 Host 连接，不是 Codex 远程控制 | divergent | P1 |
| 16 | SET-28 / GIT-09 | Worktrees 缺分组计数、pinned chat 删除阻止等细节 | partial | P1 |
| 17 | SET-16 | 没有 Agent configuration（scope/diagnostics/runtime 安装） | missing | P1 |
| 18 | SET-24 / PLG-05 | 没有 Hooks 设置页 | missing | P1 |
| 19 | SCH-04 / SCH-05 | 定时任务缺 ChatGPT/Codex 创建入口，编辑器缺月日/结束重复/通知策略 | missing / partial | P1 |
| 20 | PLG-01 | 插件目录是本地登记，不是 Codex marketplace | divergent | P1 |

次高但未入 Top 20 的 P1：SIDE-01～SIDE-07（侧栏/landing 细节）、CONV-05/06（工具/文件变更卡片）、TERM-01（终端多 tab/重连）、SET-10/12/19（General/Appearance/Shortcuts 差异）。它们应在对应阶段与 Top 20 一起排期，而不是单独开线。

## 3. 建议迁移顺序

依赖是硬约束：没有 Git 后端 stage/unstage 就不能做 Changes 面板；没有协议侧附件/队列字段就不能做 composer 附件/队列；没有 MCP 客户端就不能做 MCP 管理面。因此按"先能力、后 UI"排。

| 阶段 | 内容 | 估算 | 验收 |
|---|---|---|---|
| **M0 基线与护栏** | 固化矩阵与 55 路由附录；为设置页补 `settings-panel-<slug>` testTag 与截图/交互基线；按 D-02 建立结构+视觉双层基线；不改变任何已确认行为（P-01～P-03、S-01、S-02） | 0.5–1 周 | 矩阵行可追溯到 decode artifact 与 Harness 行号；每个 `aligned` 结论都有同视口截图或交互基线；现有测试全绿 |
| **M1 对话核心回路** | CONV-12 附件（先 Host 能力）；CONV-11 Escape 停止/submit shortcut（plan 模式按产品决定）；CONV-13 队列（先确认协议）；CONV-02 turn 分组→虚拟化；CONV-01/09 标题栏 diff 摘要（依赖 M2 数据） | 2–3 周 | 长对话滚动稳定；运行中可停止/排队；附件可发送；标题栏有 turn diff 摘要 |
| **M2 Changes / Git 闭环** | GIT-10 stage/unstage/hunk；GIT-01 Changes 侧栏 tab；GIT-03 composer 分支切换；GIT-04 提交表单迁入；GIT-05 push；GIT-02 diff 渲染复用；GIT-08/SET-26 拆分 Git 偏好 | 2–3 周 | 对话内可查看/暂存/提交/推送；Git 设置页只留偏好；现有入口不回归 |
| **M3 集成管理** | PLG-04 MCP 列表/详情/认证/添加；PLG-03 manage tabs；SET-24 Hooks；SET-16 Agent config（先只读）；PLG-01/02 按产品范围收敛或扩展 | 2–3 周 | MCP 可列出/查看/认证；Hooks 可查看/启停；Agent config 可看 scope 与诊断 |
| **M4 设置面补齐** | SET-10/12/19 现有页差异；SET-28 Worktrees；SET-25 拆分 Host 连接与远程控制；仅覆盖 12 个运行态缺失面板与仍未决定的本地 static-only route；明确排除 Phase 2 的 `billing`/`teams`/`parental-controls`/`cloud-computer` | 2–4 周 | 侧栏项与产品范围表一致；每个纳入面板有 aligned/partial 结论与测试 |
| **M5 文件/终端** | TERM-03 文件树（只读→打开→重命名/删除）；TERM-04 只读预览；TERM-01 多终端/重连/主题字体；TERM-05 底栏高度持久化 | 2–3 周 | 侧栏可浏览文件并预览；终端多 tab/重连；关视图不杀进程 |
| **M6 次级面与响应式** | SCH-04/05 创建菜单与编辑器字段；SCH-02 Completed；SIDE-01～07；RESP-01/03/04 断点/sheet/reduced motion；RESP-05 真机验证 | 1–2 周 | 定时任务三类创建与复杂频率；窄屏 dialog/sheet 有测试 |

### 3.1 可直接开工、不依赖产品决定

SET-10（General 行集合）、SET-12（Appearance 控件）、SET-19（Shortcuts 差异）、SET-26/GIT-08（Git 页拆分）、SET-28/GIT-09（Worktrees 细节）、GIT-10（Git 后端）、TERM-03（文件树）、CONV-11（Escape 停止/submit shortcut；plan 模式仍需产品决定）。

### 3.2 必须先做产品范围决定

SET-11/13/14/15/17/18/21/22/23/27、SCH-07（团队所有权）、PLG-01（本地登记 vs marketplace）、PLG-06/07（ChatGPT 账户体系）、除 Phase 2 backlog 4 项外的 ChatGPT/云 static-only 路由。这些不是"实现难度"问题，而是"是否纳入 Harness 产品"问题；其中 SET-13/17/27 建议优先纳入。SET-24 Hooks 已排在 M3，不在此列。

### 3.3 不能照搬

- **SET-25 Connections**：Harness 的同名页是核心 Host 选择面，Codex 的是远程控制；只能拆，不能替换。
- **SET-30 模型管理**：Harness 自有扩展，已有用户确认交互（P-01～P-03），不按 Codex `gateways` 回退。
- **NAV-01/02 rail 标签**：NAV-01 已补静态证据——Codex 有 8 个目的地 key（`home`/`customize`/`scheduled`/`space`/`code-review`/`orbit`/`meetings`/`defense-factory`），无 account/profile；账号入口在 profile footer，不是 rail 目的地。Harness 的 `账号` rail 项已决定移除（D-06），账号只保留在 profile footer 与设置。NAV-02 的 `exploreDestinations` 只有静态构造线索，R13 仍 blocked，保持 `unknown`。
- **RESP-05 平台差异**：不追求复制 Electron，建立 KMP 各端等价交互矩阵。

### 3.4 Phase 2 backlog（一期不做）

用户已决定以下 4 个 static-only route 一期不做、以后再实现。它们**不在 12 个运行态缺失面板内**，因此不重复计入 `missing`；scope 统计单列为 `deferred_phase2: 4`。M4 不覆盖这 4 项，只覆盖 12 个运行态缺失面板与仍未决定的本地 static-only route。

| route key | 真实页面 / export | 为什么延后 | 二期重新评估条件 |
|---|---|---|---|
| `billing` | `ConsumerBillingSettings` | ChatGPT 消费级计费（账单/支付/钱包/交易/取消订阅）；本构建硬关闭；Harness 无计费后端 | Harness 引入 ChatGPT 消费级账号与计费/支付后端 |
| `teams` | `TeamSettingsPage` | 团队工作区（成员/管理员/团队活动）；需要 ChatGPT workspace | Harness 引入 workspace/团队身份模型 |
| `parental-controls` | `ChatGptParentalControlsSettingsPage` | 家庭/信任联系人/U18 解绑；需要家庭账号状态 | Harness 支持家庭账号与监护人关系 |
| `cloud-computer` | `ChatGptCloudBrowserSettings` | 不是云电脑，是 ChatGPT Cloud Browser 的站点权限/cookie/Orbit cookie 设置 | Harness 提供 ChatGPT Cloud Browser 或等价站点权限管理 |

### 3.5 Harness 扩展逐项决定

按 D-05～D-08，以下 4 项（D-09 是 SET-04 的处理结论，不在本表）；完整表（file:line、Codex 对应面、可选项、影响）见矩阵 3.10。

| 条目 | 决定/建议 | 状态 |
|---|---|---|
| SET-29 / SET-30 模型管理 | 保留并标注为 Harness 扩展，不纳入 Codex 对齐验收 | 已决定（D-05） |
| NAV-01 账号 rail | 移除账号 rail；账号只保留在个人资料菜单/设置，向 Codex 对齐（D-06） | 已决定：移除账号 rail（D-06） |
| SIDE-08 资料库占位页 | 保留占位，不实现文件库 | 已决定（D-07） |
| SET-12 主题编码（`AppearanceThemeCodec`） | 主题导入/导出按 won't-do 移除；其余外观控件继续按 Codex 对齐 | 已决定：移除导入导出（D-08） |

## 4. unknown 列表（补齐证据前不得写进验收）

| # | unknown | 需要什么证据 |
|---:|---|---|
| U-01 | 36 个 static-only route key 的真实交互/保存/错误行为 | 换构建/flag 的运行态，或产品决定不纳入 |
| U-02 | 所有 `api-endpoint` 的真实请求/响应契约（R13 零网络往返） | 受控只读网络观测或后端契约 |
| U-03 | message id 的完整文案 wiring（严格配对 14.3%） | 更完整的跨 chunk 解析或运行态 DOM 核对 |
| U-04 | 逐路由 CSS 的真实归属（28 个 route stylesheet 仅 24 个应用） | 运行态样式快照 + 路由切换对照 |
| U-05 | 调用图三层以外的间接依赖（172 slice + 618 chunk，非传递闭包） | 扩展扫描集或按需补边 |
| U-06 | `gateways`（Model Providers）的运行态行为 | codexLocal 上下文运行态，或以静态契约为准 |
| U-07 | `codex-micro` 是否在别的构建/flag 下可达（`pets` 是另一个面板） | 换构建/flag 的运行态 |
| U-08 | `family` / `environments` route key 的 catalog 身份 | catalog 源码补证或运行态 |
| U-09 | Harness 在 Android/iOS 的响应式/返回/IME/safe-area 行为 | 真机交互测试 |
| U-10 | Harness 设置页语义身份是否等价于 `data-settings-panel-slug` | 统一 testTag + 交互测试断言 |
| U-11 | Codex 保存/删除/连接测试等写操作的精确状态机 | 受控写操作观测（需用户授权） |
| U-12 | 旧审计（`26.930.61225`）与当前 full-restore 的逐条对应 | 以本矩阵 full-restore 证据为准，逐条重核 |
| U-13 | Codex rail 的逐项按钮标签 | full-restore rail DOM/截图或静态切片补证 |
| U-14 | 探索菜单的完整目的地集合（R13 blocked，仅 `项目`） | 换构建/flag 的运行态，或静态切片补证 |

## 5. 证据分级（全文统一）

| 前缀 | 含义 | 能否单独支撑结论 |
|---|---|---|
| R13 | Phase 13 受控只读运行态观测 | 能（运行态事实） |
| S03–S14 | Phase 3–12 静态语义 / Phase 14 调用图 | 能（静态事实，须标注非运行态） |
| H | Harness 源码 `file:line` | 能（Harness 侧实现） |
| 旧审计 | `26.930.61225` 旧报告 | 不能作为 Codex 证据；只作 Harness 侧线索 |

状态定义：`aligned` 结构层+视觉层都通过（视觉层需同视口截图或交互基线）；`partial` 有对应面但结构层或视觉层的一部分缺失（含结构一致但缺视觉基线）；`divergent` 同名但语义不同；`missing` 无对应实现；`unknown` 证据不足，不得写成"无差异"。

优先级：P0 核心编码回路断裂；P1 运行态已观测的主面缺失或语义冲突；P2 次级面/依赖较大后端；P3 视觉/平台差异或已有替代。
