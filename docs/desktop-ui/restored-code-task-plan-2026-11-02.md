# 基于 decode-codex 还原代码的任务规划

状态：Proposed  
规划日期：2026-11-02  
参考副本：`references/decode-codex`（HEAD `a3cd34ee`，ChatGPT app `26.930.61225`）

## 一、为什么现在重排任务

此前的复刻依据是 minified bundle 的 chunk 名、字符串计数和截图推断，很多行为只能“从现象反推”。
`references/decode-codex/restored/` 现在提供了 4,504 个已语义化命名的 TS/TSX 文件，可以直接读到：

- 组件边界与 props 契约（例如 `ThreadSummaryPanelSection` 的 `autoCollapse` 与三种 mode）；
- 精确常量与状态机（例如摘要小节 30 秒自动折叠、diff 预览支持 `image | markdown | pdf | pdb`）；
- 完整交互链路（例如行内评论位置、apply patch、MCP app 沙箱桥）；
- 官方 zh-CN 文案（`restored/locales/zh-cn.ts`）。

因此本轮任务的目标不是“再猜一遍”，而是**以 restored 为契约源，关闭 `replication-plan-2026-10-07.md` 中仍列在「尚缺」的条目**，并把已实现页面的偏差一次性审计出来。

范围仍以 [replication-plan-2026-10-07.md](replication-plan-2026-10-07.md) 的「已确认范围」为准。云端项目、云端环境、自动代码审查、浏览器操控、电脑操控、图片库、团队/计费/家长控制等排除项不进入本计划。

## 二、参考代码索引

路径均相对 `references/decode-codex/restored/`。

| 区域 | restored 目录/文件 | 对应的 Kotlin 区域 |
| --- | --- | --- |
| 会话时间线 | `conversations/local-conversation-thread-parts/` | `apps/shared/.../client/conversation/` |
| 摘要面板 | `conversations/local-conversation-thread-parts/thread-summary-*.tsx`、`summary-panel-*.tsx`、`plan-summary-card*` | `apps/shared/.../client/summary/` |
| 子 Agent / 后台任务 | `conversations/background-*.ts`、`subagent-activity-*.tsx` | `apps/shared/.../client/panels/ThreadPanels*.kt` |
| Composer | `composer/` | `apps/shared/.../client/app/`（Composer 组件） |
| Git / Diff / Review | `review/`、`git/`、`worktree/` | `apps/shared/.../client/git/` |
| 插件 / MCP / Skills | `plugins/`、`runtime/app-main-host-runtime/hooks-settings/` | `apps/shared/.../client/plugins/`、`modules/bundle/desktop/` |
| 定时任务 | `automations/`、`automation/` | `apps/shared/.../client/scheduled/`、`scheduling/` |
| 终端 | `terminal/` | `apps/shared/.../client/panels/PtyTerminal*` |
| 壳层 / 导航 | `app-shell/`、`sidebar/`、`command-menu/`、`routes/` | `apps/shared/.../client/shell/`、`app/` |
| 设置 | `settings/` | `apps/shared/.../client/settings/` |
| 主题 / 图标 / 文案 | `themes/`、`icons/`、`locales/zh-cn.ts` | `modules/ui-foundation/`、`apps/shared/.../client/settings/` |

## 三、任务清单

优先级：P0 = 直接关闭已确认缺口且参考契约完整；P1 = 缺口明确但工作量较大；P2 = 依赖数据源或后续验收。

### R0 — 建立 restored 参考索引与偏差审计（P0）

**现状**：参考代码已入库，但没有“restored 文件 ↔ Kotlin 实现”的索引；已实现页面是否存在契约偏差只能靠记忆。

**交付**：
- `docs/desktop-ui/restored-reference-index.md`：按上表 12 个区域逐项列出 restored 文件、Kotlin 文件、当前状态（已对齐 / 部分 / 未实现）。
- 对已实现页面（Shell、Home、Thread、Composer、Git、Plugins、Scheduled、Terminal、Settings）逐页记录与 restored 的偏差，附 restored 行号或文件路径。

**验收**：索引覆盖全部已确认范围；每条偏差都有 restored 证据和 Kotlin 落点，不写“看起来不对”。

---

### R1 — 线程摘要面板剩余小节与交互原语（P0）

**现状**：`ThreadSummaryPanel.kt` 已实现 environment / subagents / tasks / scheduled 四类小节；其余小节缺失，且没有 `autoCollapse`、持久展开状态和 `accordion | dropdown | headerless` 三种模式。

**restored 契约**：
- `conversations/local-conversation-thread-parts/thread-summary-panel-sections.tsx` — 小节装配顺序
- `.../thread-summary-source-rows.tsx`、`thread-summary-web-sources.ts` — Sources
- `.../summary-panel-artifacts-list.tsx`、`local-conversation-output-artifacts.ts` — Artifacts / Outputs
- `.../plan-summary-card.tsx`、`plan-summary-card/` — Plan
- `.../thread-summary-side-chat-rows.tsx` — Side chats
- `.../thread-summary-automation-row.tsx` — Automations / triggers
- `.../pull-request-summary-row.tsx`、`pull-request-git-summary-runtime.ts` — Pull requests
- `.../thread-summary-panel-section.tsx` — `autoCollapse` 30,000ms、`ThreadSummaryPanelSectionMode`、持久化 key 前缀 `thread-summary-panel-section-expanded-`
- `.../thread-summary-panel-chrome-primitives.tsx` — 标题、计数、展开/折叠按钮

**交付**：补齐上述小节；实现 30 秒自动折叠、展开状态持久化、三种 mode；补 diffStats 徽标。

**验收**：每个小节有 interaction test；与 restored 的小节顺序、标题、计数、空态逐项对照；自动折叠用可注入时钟测试，不 sleep 30 秒。

**R1 执行记录（2026-11-02）**

- 新增 `ThreadSummarySection.kt`：`Accordion | Dropdown | Headerless` 三种 mode、30,000ms `autoCollapse`（可注入延迟）、点击取消自动折叠、`thread-summary-panel-section-expanded-` 持久化 key 前缀、`ThreadSummarySectionState` 保存展开选择。
- `ThreadSummaryPanel.kt` 小节顺序改为 restored 的 automation → git summary → plan → outputs → side chats → background agents → background tasks → sources；既有 environment/subagents/processes/scheduled 改走统一原语，原 testTag 与计数 tag 保持不变。
- 新增 Plan、Outputs（无 Git 摘要时渲染）、Side chats、Sources（始终渲染）小节；Sources/Outputs 空态使用官方 zh-CN 文案「暂无来源」「暂无产物」。
- `HarnessApp` 提升 `ThreadSummarySectionState`，面板关闭再打开仍保留展开状态；落盘持久化待设置存储接入。
- 数据源说明：harness 目前没有 plan 文档、artifact 采集、side-chat 或 MCP/网页搜索工具，因此这些小节只在调用方传入真实数据时渲染；未伪造任何行。
- 验证：`ThreadSummarySectionStateTest` 4 项、`ThreadSummarySectionInteractionTest` 7 项、既有 `ThreadSummaryPanelInteractionTest` 5 项与 `ThreadSummaryActivityPanelInteractionTest` 5 项全部通过（jvm）。

---

### R2 — 子 Agent 面板最终回复与 diffStats（P1）

**现状**：子 Agent typed 历史已复用 MessageCard；缺「最终回复」节、diffStats 徽标和 autoCollapse。

**restored 契约**：
- `conversations/background-subagent-signals.ts`、`background-subagent-linked-threads.ts`
- `conversations/background-summary.ts`、`background-thread-*.ts`
- `conversations/subagent-activity-inline-group.tsx`、`subagent-activity-row.tsx`
- `conversations/local-conversation-thread-parts/local-conversation-background-agent-*.ts*`

**交付**：最终回复节（数据源不可用时显式 unavailable，不伪造内容）；diffStats 徽标；折叠行为与 R1 共用原语。

**验收**：有真实 typed 历史和无数据两种路径的 interaction test；徽标数字来自真实 diff stats。

**R2 执行记录（2026-11-02）**
- **最终回复节**：`ChildDetailPanel` 在「委派的任务」后新增「最终回复」节；数据来自子会话 typed 历史中最新的 assistant 文本（`ChildAgentDetail.finalReply`）。typed 历史缺失时显示「最终回复不可用」，有历史但无 assistant 文本时显示「尚无最终回复」，兼容 transcript 不会被当成最终回复。
- **列表预览**：`SessionDescendant` 增加 `lastAssistantMessage` / `lastAssistantAtEpochMillis`，Host 从子会话消息中提取最新 assistant 文本（跳过 reasoning-only），`ChildAgentSummary.preview` 改为最终回复优先，其次 objective / statusSummary；读取失败不隐藏子会话，取消仍传播。
- **diffStats**：`ChildDiffStats` → `ThreadSummaryActivity.diffStats` → 摘要面板行 `+N −N` 徽标；协议两侧增加可选 `diffStats`，只接受 Host 提供的真实非零 stats。当前 Host 没有 turn-diff 生产方，因此生产环境不显示徽标，不做本地估算。
- **autoCollapse**：摘要面板子智能体小节沿用 R1 已接线的原语。
- **验证**：`ThreadPanelsStateTest`、`ConnectionThreadPanelsRepositoryTest`、`ThreadSummaryActivityTest` 所在的 shared 全模块 **629/629**；`SessionDescendantsContractTest` **7/7**（含读取失败与取消路径）；`SessionDescendantsGatewayTest` **2/2**；`SubagentHistoryInteractionTest` + `ThreadSummaryActivityPanelInteractionTest` **16/16**；`ThreadPanelsInteractionTest` + `ThreadSummaryPanelInteractionTest` **30/30**。

---

### R3 — Git 富预览 image / SVG / PDF（P0）

**现状**：`GitRichPreview.kt` 只把 markdown 渲染成预览，源码注释明确写着 raster image、SVG、PDF 仍显示 diff 文本（“no preview source is wired”）。

**restored 契约**：
- `review/file-source-rich-preview.tsx` — `RichPreviewKind = "image" | "markdown" | "pdf" | "pdb"`
- `review/file-source-side-panel.tsx`、`file-source-tab.tsx` — 预览容器与 tab
- `review/file-source-header-actions.tsx`、`file-source-options-menu.tsx` — 打开/复制/下载动作
- `conversations/local-conversation-thread-parts/end-resource-open-actions/` — 资源打开动作

**交付**：图片（含 SVG）与 PDF 预览；不支持的格式回落到 diff；删除文件不预览；预览源的读取走现有 Host 文件读取能力，不新增越权路径。

**验收**：image / svg / pdf / markdown / 未知格式 / 已删除文件六类 fixture 的 interaction test；大文件有明确上限与错误态。

**R3 执行记录（2026-11-02）**

- `GitRichPreview.kt`：`GitDiffPreviewKind` 从 `Diff | Markdown` 扩展为 `Diff | Markdown | Image | Svg | Pdf`；新增 `GitImageExtensions`、`GitSvgExtensions`、`GitPdfExtensions`、`gitPreviewBinaryMimeType`；SVG 与位图分开是因为前者走 vector painter 而不是位图解码器；`pdb` 保持 diff 回落（本仓库没有分子渲染器）。
- 读取上限：文本 `GitPreviewTextLimit` 256 KiB，二进制 `GitPreviewBinaryLimit` 8 MiB；超限仍按 `Unavailable` 回落 diff。
- `GitPreviewFile.Binary(bytes, mimeType)`：Host 读取在 UTF-8 解码前按 MIME 判定返回原始字节，删除文件与不安全路径仍在同一处拒绝；`GitController` 的缓存终止判定包含 `Binary`，同一文件不会重复读取。
- 解码：`GitPreviewDecoding.kt` 用 expect/actual。位图走 `components-resources` 的 `decodeToImageBitmap`；SVG 走 `decodeToSvgPainter`（JVM/iOS），Android 暂无 Skia SVG 模块返回 null；PDF 只有 JVM 用 PDFBox 3.0.3 渲染首页（96 DPI），其他平台返回 null。解码失败一律显示「无法解码该文件的预览，先显示差异文本。」并回落到 diff，不画空框。
- `GitPageComponents.kt`：`previewPaths` 收集所有非 Diff 类型，删除文件被排除；卡片新增 `GitBinaryPreview` 与 `GitDiffFallbackBody`，未知格式保持 diff 文本。
- 验证：`GitRichPreviewTest` 8 项、`GitPreviewDecodingTest` 6 项（desktop runtime，Skia 原生库只在 Compose 测试进程可用）、`GitDiffBinaryPreviewInteractionTest` 2 项（六类 fixture 同一 diff）、既有 `GitDiffRichPreviewInteractionTest` 4 项与 shared git 包 134 项全部通过（jvm）。
- 未覆盖：iOS/Android 的 SVG 与 PDF 实际实现只做了编译级对齐，本次 jvm 构建无法验证这两个 target（它们的编译被既有的无关错误挡住）。

---

### R4 — Git Diff 阅读能力（P1）

**现状**：`GitDiffLayout.kt` 已有 `Unified | Split | Auto` 三种布局和选择器；没有 sticky 文件头、上下文展开、大 diff 横幅和 diff 内搜索。（R0 审计已确认：布局选择不再是缺口。）

**restored 契约**：
- `review/file-diff.tsx` — `stickyHeader`
- `review/large-diff-banner.tsx` — 大 diff 阈值与提示
- `review/diff-search-adapter.ts`、`diff-search-controller-runtime.ts`、`diff-find-highlight.ts` — 搜索与高亮
- `review/diff-virtualization-metrics.ts` — 虚拟化指标
- `review/file-change-gutter.ts`、`file-blame-gutter.tsx` — gutter
- `review/editor-diff-page.tsx` — 页面级装配

**交付**：sticky 文件头；按 hunk 展开上下文；大 diff 横幅与“加载完整文件”；diff 内搜索与命中跳转。布局选择已存在，不重复实现。

**验收**：每种能力独立 interaction test；大 diff 用固定阈值 fixture，记录首帧与滚动性能指标。

**R4 执行记录（2026-11-02）**

- **阈值与单文件模式**：`GitDiffReading.kt` 的 `GitDiffAutoOpenMaxFiles = 25`、`GitDiffAutoOpenMaxChangedLines = 2000` 取自 restored `editor-diff-page.tsx`（边界含等号），`GitDiffView` 接收可注入参数。超过任一阈值显示 `GitDiffLargeBannerCopy` 横幅，并改为同一时间只展开一个文件；`large-diff-banner.tsx` 本身没有阈值常量，复用自动展开阈值的选择写在源码注释里。
- **sticky 文件头**：不走 `LazyColumn`，保持每个文件正文仍是**一整段可选中的文本**。`ScrollState.value` + `derivedStateOf` 计算吸顶位移，`graphicsLayer { translationY }` 平移标题、`zIndex` 盖住正文，下一个文件头到达时把它顶出。
- **上下文展开**：`parseGitDiffHunks` / `gitDiffContextGaps` / `gitDiffExpandedBody` / `gitDiffExpandedLineIndex` 全部是纯函数；按 hunk 读取工作树文本，每次展开 20 行，重算 `@@` 起止与计数。暂存区 diff 不展开（工作树可能不同于索引），删除文件不展开；读取失败显示 controller 的 unavailable 原因而非伪造上下文。
- **diff 内搜索**：`findGitDiffMatches` 做不区分大小写的字面匹配（含 `@@` 头），500 条上限并标记截断。查找栏显示 `当前/总数`，上/下一个循环；命中文件自动展开，unified 布局用 `TextLayoutResult.getLineTop` 精确滚动到行，split/换行布局回退到卡片顶部；正文用 token 背景色高亮命中与当前命中，文件头显示命中数。
- **验证**：`GitDiffReadingTest` **7/7**；shared git 包 **141/141**；`GitDiffReadingInteractionTest` **8/8**（阈值与横幅、单文件切换、吸顶、搜索计数/跳转/自动展开、上下文展开、工作树不可读、暂存区禁用、性能指标）；`GitDiffViewInteractionTest` + 富预览 + 二进制预览 + `GitPageInteractionTest` + `WorktreePageInteractionTest` **39/39**。
- **性能指标**：25 文件 / 2000 变更行的边界 fixture（全部默认展开）在多轮运行中首帧 **216–471ms**、连续 10 次滚轮 **650–818ms**（约 65–82ms/次），数字随同进程已跑过的 UI 测试数变化；测试只保留宽松上限（首帧 15s、10 次滚动 20s）防止灾难性回归，不把本机数字当基准。
- **未做 / 移出**：blame gutter 需要 `git blame` 解析与 gutter 渲染，明确移出 R4；行内评论、apply/revert 留给 R5；工具栏状态持久化归 G1b；diff 虚拟化不引入（正文要保持可选中的单段文本），因此 restored 的 `diff-virtualization-metrics.ts` 只作为阈值参考，未照搬。

---

### R5 — 行内评论与 apply patch（P2）

**现状**：Kotlin 侧没有行内评论模型或 apply patch 链路。

**restored 契约**：
- `review/review-comment-utils.ts` — `InlineCommentPosition`（path / side / startSide / startLine / line）
- `review/diff-selection-summary.ts` — 选区摘要
- `review/apply-review-patch.ts` — patch 应用
- `review/action-popover-primitives/` — 行内操作浮层

**交付**：行选择 → 评论草稿 → 生成 patch → 应用/拒绝；与现有 `GitDiffApply.kt` 的 apply 能力合并，不重复实现。

**验收**：patch 生成与应用的单测；拒绝/冲突路径；评论草稿不写入 Git 工作树。

---

### R6 — 插件 MCP 真实连接与授权（P0）

**现状**：`PluginModels.kt` 只有 `McpServer / Hook / Script` 能力名和 Apps 状态；MCP 真实连接、授权、工具调用尚未接入。

**restored 契约**：
- `plugins/active-mcp-app-frame.tsx`、`mcp-app-frame*.ts*` — MCP App 宿主与 frame
- `plugins/codex-apps-server.ts` — Apps server
- `plugins/mcp-app-sandbox-*.ts` — 沙箱桥与 schema
- `plugins/mcp-app-tool-proxy.ts`、`mcp-app-tool-resolver.ts`、`mcp-tool-activity-runtime.tsx` — 工具代理与活动
- `plugins/mcp-app-errors.ts`、`mcp-capability-signals/` — 错误态与能力信号

**交付**：MCP server 连接与授权流程、工具列表/调用、错误与超时态；连接状态进入插件详情页；不把凭据写入日志或 UI。

**验收**：受控本地 MCP server 的集成测试（连接、授权、调用、断开、错误）；无授权时动作显式不可用。

---

### R7 — 插件 hooks 配置与 scripts 生命周期（P1）

**现状**：插件页已能展示能力清单；hooks 配置写入/启停、scripts 生命周期、目录型 apps 仍缺。

**restored 契约**：
- `runtime/app-main-host-runtime/hooks-settings/` — hooks 分组、常量、路由
- `plugins/plugin-capabilities.ts`、`plugin-config-edits.ts` — 能力与配置编辑
- `plugins/format-skill-scope.tsx`、`skill-utils.ts`、`use-skills.ts` — Skills 作用域
- `plugins/plugin-detail-page-current.tsx`、`plugin-detail-page-runtime/` — 详情页

**交付**：hooks 配置读写与启停；scripts 执行生命周期（启动/停止/退出码）；目录型 apps 的登记与展示。

**验收**：配置写入落盘可回读；启停有状态机测试；scripts 失败不吞错。

---

### R8 — 终端链接、原生窗口与远端（P1）

**现状**：PTY 阅读与紧凑视觉已验收；`TerminalTextInputBridge.kt` 已实现 IME 组合输入、Escape 取消和提交，不再列为缺口。剩余：链接识别/打开、原生窗口组合和远端链路。

**restored 契约**：
- `terminal/terminal-key-event-handler.ts` — 按键与组合键
- `terminal/terminal-link-handler.ts` — 链接识别
- `terminal/terminal-panel.tsx`、`terminal-session-tab.tsx`、`terminal-tab-title.ts` — 面板与 tab
- `terminal/use-conversation-terminal-snapshot.ts` — 快照

**交付**：链接识别与打开动作对齐 `terminal-link-handler.ts`；原生窗口下终端与面板组合验收；远端链路另列依赖真实 Host 的验收项。

**验收**：链接点击/修饰键行为 interaction test；原生窗口截图；远端链路依赖真实 Host。IME 已有 bridge 测试，回归即可，不重复实现。

---

### R9 — 同状态像素对照与 token 偏差（P1，跨任务）

**现状**：原生截图只覆盖空/不可用状态，缺同状态原版对照；token 偏差没有清单。

**restored 契约**：
- `themes/` — 主题变量
- `ui/`、`icons/` — 组件与图标
- `locales/zh-cn.ts` — 官方中文文案
- `app-shell/` — 壳层尺寸与布局

**交付**：每个已实现页面至少一组“同状态”原版/本机截图对照；token 偏差清单（颜色、间距、字号、圆角、阴影）；文案偏差清单。

**验收**：对照图归档到 `docs/desktop-ui/captures/`；偏差项逐条有 restored 证据或明确接受理由。

## 四、建议执行顺序

| 波次 | 任务 | 理由 |
| --- | --- | --- |
| 1 | R0 → R1 → R3 | R0 建立索引；R1、R3 是参考契约最完整、缺口最明确的页面能力 |
| 2 | R6 → R4 → R7 | MCP 是插件页的核心服务缺口；R4 依赖 R3 的预览容器 |
| 3 | R2 → R8 → R9 | 子 Agent、终端 IME 和像素对照作为收口 |
| 4 | R5 | 行内评论依赖 R4 的 diff 选区与 R3 的预览容器 |

依赖关系：R4 依赖 R3；R5 依赖 R3 + R4；R1 的折叠原语可被 R2 复用；R0 是所有任务的输入。

## 五、明确不做

- 浏览器操控、电脑操控、云电脑、应用快照、图片库、语音、宠物；
- ChatGPT 云端项目、云端环境、自动代码审查、团队/计费/家长控制/受信任联系人；
- 应用退出后的定时任务后台执行（已在计划中明确不做）；
- 不把 restored 中的商业应用代码复制进本仓库；只提取行为契约、常量和文案事实。

## 六、跟踪

对应 Kaneo `kmp-harness` 项目的 R0–R9 任务；每项完成后在本文件追加“实际交付/验证结果”，并以 `docs/desktop-ui/replication-plan-2026-10-07.md` 文末「最新同步」为权威状态。

逐页面核对后的缺口逐项计划见 [gap-implementation-plan-2026-11-02.md](gap-implementation-plan-2026-11-02.md)，含 G1–G15 与 Kaneo #41–#53；R2、R4–R9 的增量以该文件第三节为准。
