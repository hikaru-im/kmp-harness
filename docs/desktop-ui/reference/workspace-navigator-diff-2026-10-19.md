# Workspace Navigator：与 Codex 桌面端（v155）差异清单

测量对象：`/Applications/ChatGPT.app`（Codex v155，zh-CN，light），窗口 1630×882，CDP 只读 DOM +
原生窗口几何。Harness 侧对照 `apps/shared/src/im/hikaru/harness/client/shell/AppShell.kt` 的
`WorkspaceNavigator`。数值单位均为 1px = 1dp。

## 1. 展开布局

| 项 | Codex v155 | Harness 现状 | 处理 |
| --- | --- | --- | --- |
| 宽度 | `--spacing-token-sidebar: clamp(240px, 340px, min(520px, 100vw - 320px))`；本实例存 288px | `NavigatorPreferredWidth = 275dp`，可拖拽 | 保留现有可拖拽布局（用户已确认 `NavigatorLayout` 就是按该 token 实现），只记录默认值差异 |
| Header 高度 | 52px | `NavigatorHeader = 50dp` | 新增 Workspace 专用 52dp |
| Header 内容 | 工作区名 18sp/24、weight 600、85% ink；右侧 4px 间隔的铃铛 + 搜索 28×28（上下居中，右内边距 12） | 只有工作区名 14sp + chevron，无右侧动作 | 补铃铛/搜索动作，名称改 18sp/600/85%；chevron 与切换菜单按 Harness 固定差异移除 |
| 工作区名 x | 16（sidebar 内），y 居中于 52px header | 16 | 保持 |
| New chat 行 | 30px 高、圆角 12.5、5.5% hover 填充、padding 5/8、图标 16、文字 14sp/430、85% ink，图标 x=16、文字 x=40、块下方 8px margin | 36px 高、圆角 10、`SurfaceSelected` 填充、14sp/400、全 ink | 改为 30 / 12.5 / 5.5% / 85% |
| 搜索入口 | Header 图标按钮，点击打开居中的命令菜单；没有常驻“搜索会话”行，按钮在菜单打开时也不变蓝 | 常驻“搜索会话”行 + 内联搜索框 | 删除常驻行与内联框，改为居中命令菜单（见第 7 节） |
| 分组标题 | “项目” / “最近”；30px 行高、标签按钮 25px 高（4px 左缩进 + 4px 内缩，文字落在 x=16）、14sp/500、49.4% ink、标签后 4px 间距的 14px chevron，可折叠 | “项目” / “最近会话”；12sp Medium、无 chevron、不可折叠、上下 20/8 padding | 全部对齐，并新增折叠 |
| 分组间距 | 分组之间 16px；滚动区顶部 4px、底部 8px；分组标题到首行 4px | 标题上 20 / 下 8，无分组间距 | 改为 4 / 16 / 8 |
| 分组 hover 动作 | 24×24：项目 = “…” + 新建项目，最近 = “…” + 新建会话 | 无 | 补 24×24 hover 动作 |
| 项目行 | 30px、圆角 12.5、5.5% hover/选中、padding 8/8、文件夹图标 16（x=16）、文字 x=40；每个项目块下方 8px margin | 30px、圆角 10、`SurfaceSelected`、chevron、hover 只有右侧面板按钮 | 换文件夹图标、圆角/填充、hover 改 “…” + 新会话 20×20 |
| 项目子项 | 30px（相邻行 pitch 31）、圆角 12.5、5.5% hover/选中、文字 x=40、无 trailing status | 30px、圆角 10、`SurfaceSelected`、可带 status | 对齐；status 无数据时不显示 |
| 展开更多 | 5 条后显示“展开显示”，32px 高、padding 4/32、14sp/400、49.4% ink、文字 x=40 | Ghost 按钮“查看全部 N 条会话”/“收起会话” | 改为“展开显示”/“收起”，32px 行 |
| 最近行 | 30px、圆角 12.5、5.5% hover/选中、padding 8、文字 x=16、hover 显示置顶/归档 20×20 | 30px、圆角 10、`SurfaceSelected`、无 hover 动作 | 对齐并补 hover 动作 |
| 滚动条 | 11px gutter（内容右缘因此比无滚动条时少 11） | Compose 默认滚动，无固定 gutter | 滚动列右侧预留 11px 内边距对齐内容宽度，不自绘滚动条 |
| 项目 hover 预览卡 | 悬停项目行出现任务数/仓库/路径/编辑 | 无（缺项目计数、仓库、路径数据） | 不实现，等数据模型补齐 |
| 右键菜单 | 原生 macOS 菜单；含“项目▸”“分享”“在新窗口中打开”等 | Compose 菜单：重命名/固定/标为未读/分组/分叉/复制/打开方式/归档/删除会话 | 保留 Compose 实现；本轮不伪造原生菜单项 |

## 2. 临时悬浮（peek）

| 项 | Codex v155 | Harness 现状 | 处理 |
| --- | --- | --- | --- |
| 卡片尺寸 | 284×826（shell 高 - 8），x=56、y=48（窗口坐标）；圆角 8 | 宽度 `peekWidth - 4`；高度 `shellHeight - 12`；x=56、y=4（shell 内） | 高度改为 shell - 8 |
| 表面 | 不透明白色 + 1px 8% ink ring + `--shadow-card` | 已一致 | 保持 |
| 内容 | 与展开布局同一套 header/New chat/分组/行 | 同一套 | 随展开布局一起生效 |
| 悬浮行为 | rail 项 hover 出现，离开消失，不导航 | 已一致 | 保持 |

## 3. 折叠布局

| 项 | Codex v155 | Harness 现状 | 处理 |
| --- | --- | --- | --- |
| 折叠宽度 | 0（只剩 52px rail） | 0 | 保持 |
| Rail 悬浮触发 | rail 项 hover 显示 peek | 已一致 | 保持 |
| 拖拽边缘 | peek 右缘可拖拽改宽 | `NavigatorResizeDragState` 已实现 | 保持 |
| 折叠时的行/字号 | 不渲染，全部由 peek 承载 | 同 | 保持 |

## 4. Token 变更

新增 Workspace 专用 token，避免影响 Settings/Plugins/Scheduled 仍在使用的
`NavigatorHeader` / `NavigatorRowRadius`：

- `WorkspaceNavigatorHeader = 52dp`
- `WorkspaceNavigatorRowRadius = 12.5dp`
- `WorkspaceNavigatorHeaderAction = 28dp`
- `WorkspaceNavigatorSectionAction = 24dp`
- `WorkspaceNavigatorRowAction = 20dp`
- `WorkspaceNavigatorActionRadius = 9.375dp`
- `HarnessColors.NavigatorRowText` = `ChromeText` @ 85%
- `HarnessColors.NavigatorRowFill` = `ChromeText` @ 5.5%

## 5. 验证

- `./kotlin build -m jvm-app --build-dir=/tmp/kmp-harness-nav-20261019-build`：Build successful。
- `ScreenshotTest.renderWorkspaceNavigatorStates`：light/dark 的 expanded、collapsed、search-open（完整命令菜单）、
  search-filter（输入 `DSH`）全部写出并通过；组件裁图见
  `docs/desktop-ui/captures/rail/workspace-navigator-expanded-light-1440x900-crop.png`。
- `ScreenshotTest.renderWorkspaceNavigatorActivityStates`：点击铃铛后 light/dark 的活动视图全部写出并通过；
  裁图见 `docs/desktop-ui/captures/rail/workspace-navigator-activity-light-1440x900-crop.png`。
- `WorkspaceNavigatorActionsInteractionTest` 9 项全部通过：打开菜单、输入过滤并选择、Esc 关闭清空、↑/Enter 激活、
  点击外部关闭、铃铛往返切换（活动视图保留 New chat）、“…”菜单开关/恢复默认、隐藏置顶、header 按钮提示。
- `shared` 模块 595 个测试全部通过；`ui-foundation` 模块 61 个测试全部通过（新增的 Workspace token 断言已加入
  `HarnessTokensTest.workspaceNavigatorRowPitchKeepsTheCodexSidebarGap`）。
- `LocalProjectsInteractionTest.registerExistingDirectorySelectHomeProjectAndPersistConfirmedMembershipBeforePrompt`
  在“新建项目”保存后仍停留在 `PageId.Projects()`（该页按 `PageShellSpec` 不挂载 Workspace navigator），因此找不到
  `new-task`；这与本轮 navigator 渲染改动无关。
- `HarnessAppThreadPanelsShellTest.fullShell1440x900Light` 在选中会话后等待“终端”文本超时，失败点为面板内容，
  不是 navigator 标签或几何。

## 6. 搜索与活动行为（本轮补充）

### 搜索按钮

- 点击 header 搜索打开居中的命令菜单，不再展开侧栏内联搜索框；菜单打开期间搜索按钮保持 49.4% ink，不变蓝。
- 外框 520×487、圆角 20、`0 16px 32px -8px rgba(0,0,0,.19)`，水平居中；shell 内 top=145（窗口坐标 189，
  含 44px titlebar）。菜单下方有整窗遮罩：Codex 的 `.codex-dialog-overlay fixed inset-0 z-50` 使用
  `--color-dialog-overlay: #0002`（黑色 13.3%），rail、navigator 与页面会整体变暗，不是纯透明点击层。
- 内边距 5；输入区高 33（上下 5/4 + 5 内缩 33 高）、14sp、文字内缩 10；无边框/底色。内容从 y+43 开始。
- 分组标签 27px 高、padding 8/8/0、13sp/500、49.4% ink，标签后 4px 接首行。
- “聊天”列出最近会话：标题、右对齐项目名、`⌃1…⌃9`；行高 31、行距 0、圆角 12.5、padding 8，选中/悬停为 5.5% ink；文字 x=card+13，右缘内缩 13。
- “快捷操作”：新聊天 `⌘N`、打开文件夹 `⌘O`、搜索文件 `⌘P`，带 16px 前置图标。
- 键盘：Esc 关闭并清空查询；↑/↓ 移动高亮；Enter 打开高亮项；点击空白处关闭。
- Harness 的“打开文件夹”接到项目编辑器；“搜索文件”暂无对应表面，只保留动作外观。

### 铃铛按钮

- 点击切到侧栏内的活动视图（不是 popover），再次点击回到 新聊天/项目/最近；打开时图标为 `rgb(58,131,247)`，
  底色为同一蓝色 13%，圆角 9.375、28×28，aria 描述切换为“关闭活动视图”。
- 活动视图**保留可见的 New chat 行**（272×30，x=60、y=96，与项目列表里同一组件）；它占据 38px 块并留 8px
  margin，首个小标题 y=138（52 + 38 + 4 的顶部留白另计）。此前误判为隐藏占位，已按实时 DOM 修正。
- “优先级”标题 30px 高、padding 0 6 0 8、常显“…”（24×24）→ 无未读时是 37px 高的占位（8 + 21 + 8）。
  随后 16px 接“最近”分组。
- 活动视图的“…”菜单（Codex 实测 172px 宽、padding 4、圆角 20、白 90%、`0 8px 16px -4px rgba(0,0,0,.12)`
  + 0.5px ring，触发器下方 2px）：
  - “显示”标题 27px 高、13sp/49.4%，右侧 16×16“恢复默认设置”；
  - 开关 29px 高、padding 水平 8、圆角 15、13sp/18.57，右侧勾选 16×16 全 ink：优先事项部分 / 置顶 / 定时任务；
  - 分隔线总高 9px（上下 4px + 1px 线，左右内缩 8，8% ink）；
  - 动作 29px 高、padding 水平 8、圆角 15：全部标为已读 / 归档聊天。
  - 行为：每次切换后菜单关闭并立即刷新列表；关闭“优先事项部分”时“优先级”标题消失，“…”触发器移到下一个
    仍可见的分组标题（“最近”）。无未读/无可归档项或宿主未提供批量动作时，两个动作显示为禁用。
  - Harness 的数据模型暂无“定时任务”活动行，该开关保留状态但不控制任何分组。
- 活动项：卡片宽 272（不预留滚动条 gutter）、padding 6/5/8/8、圆角 12.5；标题与右对齐项目名同一行
  14/20（85% / 49.4%），摘要第二行 12/16、49.4%、最多 2 行，两行之间 2px。Harness 数据模型没有摘要，
  因此状态存在时以状态文本作第二行。
- 铃铛图形改为 Codex 的 16×16 even-odd 填充路径（外轮廓 + 内挖空 + 铃舌），不再使用 20 网格描边，墨迹尺寸和
  垂直居中与搜索图标一致。

### 按钮提示

- 铃铛、搜索、活动视图“…”以及 header/分组里的所有图标按钮都经 `HarnessTooltipTrigger` 显示驻留提示
  （200ms 延迟，离开、点击或键盘激活立即消失）；搜索提示为“搜索会话”，铃铛为“查看活动 / 关闭活动视图”。
- 命令菜单的空白点击层同时是遮罩：使用 `DialogScrim`（黑色 13.3%）压暗整窗并负责点击外部关闭，
  与 Codex 的 `codex-dialog-overlay` 一致。

### 稳定标记

`workspace-search-toggle`、`workspace-activity-toggle`、`workspace-command-menu`、`workspace-command-input`、
`workspace-command-scrim`、`command-chat-<id>`、`command-action-<index>`、`workspace-activity-view`、
`activity-<id>`、`workspace-activity-menu`、`activity-menu-priority|pinned|scheduled|reset|mark-read|archive`、
`navigator-tooltip-<触发标记>`。`WorkspaceNavigatorActionsInteractionTest` 覆盖打开/过滤/选择/Esc/铃铛切换、
活动视图保留 New chat、菜单开关/恢复默认/隐藏置顶，以及 header 按钮提示。

## 7. 不纳入本轮

- 默认宽度 275 → 340：现有 `NavigatorLayout` 与大量测试按 275 固定，用户已确认按 token 实现；
  改动会同时影响所有页面的默认列宽。
- 项目 hover 预览卡（缺数据）。
- 原生右键菜单与“项目▸/分享/在新窗口中打开”（Compose 菜单无法原生复刻，且部分动作未接线）。
- 工作区切换：Harness 没有多工作区，Header 的工作区名是固定标签；Codex 的 chevron 与下拉菜单属于固定差异，不实现。
  Header 名称直接取宿主 `HostDescription.displayName`（Desktop 宿主现为 “KMP Harness”，不再带 “Desktop” 后缀），
  回退为“工作区”。
- 命令菜单的“搜索文件”动作只保留外观：Harness 没有文件搜索面。
