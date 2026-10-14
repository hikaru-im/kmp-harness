# Batch P 证据：定时任务导航器视觉对齐

日期：2026-10-10
范围：定时任务 —— 左侧导航器（`ScheduledManagementNavigator`）的结构与间距对齐原版截图。
证据目录：`docs/desktop-ui/verification/scheduled-navigator-parity-2026-10-10/entry-p1/`

## 结论

本批只改视觉，且只改 1 个生产源文件：`apps/shared/src/im/hikaru/harness/client/scheduling/ScheduledManagementNavigator.kt`（未改任何测试）。
修正来自对原版截图的重新测量，不是凭印象改动。

三处有实测依据的差异：

1. 任务行此前是"整行居中按钮 + 缩进状态"：原版是左对齐的两行（任务名在上、状态在下，二者左内缩相同）。实测我们任务名内缩 24 css、参考 17 css。改为 `HarnessRow`（左对齐标题 + 描述，描述用 `bodySmall` 弱化为状态）后内缩 16 css。
2. "新建任务"此前是整行填满的居中按钮：原版是左对齐的一行（+ 图标 + 文本）。改为 `NavigatorNewTaskRow`：`NavigatorIcon` 的 `Plus` + 文本，`Spacing.Two` 内缩。testTag `scheduled-new-task` 与 disabled 语义完全保持，8 处 `assertIsNotEnabled()` 与鼠标点击用例不受影响。
3. 小节标题此前是"任务"：原版为"即将执行"（Codex 对该任务列表的组名）。已对齐文案，样式 `labelLarge` + `TextMuted`。

另外两处对齐：

4. 去掉 header 与列表之间的 `HorizontalDivider`。原版导航器在该区间没有任何水平分割线：x=200 列在 y 74..300 恒为 `#FCFCFC`（导航器底色），全宽扫描 y 74..300 没有任何一行亮度低于 247。
5. 纵向间距：外层 10→14 dp、header 内 10→12 dp，使"新建任务""即将执行""任务名"的基线落到参考位置（见下表）。

## 参考图的密度判定（纠正上一批 handoff 的换算）

上一批 handoff 记的是"1 raw px = 0.5 css，rail 0..103 raw"。实测该截图密度为 1，即 1 raw px = 1 css px：

- 图标 rail 实测宽 52 px，等于 `HarnessTokens.Control.RailWidth = 52.dp`；导航器左边界 x=52、右边界 x=340，宽 288 px，正是 `workspace-navigator-measurement-2026-10-06.md` 已记录的"用户手改后的 288"。
- 若按密度 2 换算，rail 会变成 26 css、导航器 144 css，两者都与仓库既有 token 与记录矛盾。

因此参考图坐标可直接当 css 使用。归一化：参考图的导航器左边界 x=52、应用内容顶 y=74（30px 菜单栏 + 44px 标题栏之下）；我们的截图导航器 Box 在 root x 0..248、内容顶 y=0。

## 实测对照（measurement/navigator-measurements.txt）

| 元素 | 参考（相对导航器左 / 内容顶） | 我们（改后） |
| --- | --- | --- |
| 标题 定时任务 ink | x 17，y 17..33 | x 16，y 18..32 |
| 新建任务 + 图标 | x 18 | x 19 |
| 新建任务 文本 | x 41 | x 40 |
| 小节标题 ink | x 17，y 99..112 | x 17，y 97..108 |
| 任务名 ink | x 17，y 132..146 | x 16，y 133..143 |
| 任务状态 ink | x 17，y 153..163 | x 17，y 149..159 |

改前（Batch P 之前，`landing-light-1440x900` 几何 json，id-padding 12）：任务名内缩 24、状态内缩 25，且新建任务是整行按钮。改后任务行的语义节点为 x 8..240（列表不再额外内缩，内缩由 `HarnessRow` 自带的 `Spacing.Two` 提供），文本落在 16。

纵向剩余 3~4px 的差来自字形：参考是 CJK"跟踪 Koog PR #1703"，我们是拉丁"Task 1"，ink 高度 14 vs 10；基线位置已对齐。

## 测试与证据

| 命令 | 覆盖 | 结果 | 日志 |
| --- | --- | --- | --- |
| kotlin build -m shared | 生产源码 | Build successful | logs/harness-p1-shared-build.log |
| kotlin test -m shared（9 类） | PageShellSpec、PageNavigatorMatrixAudit、ScheduleEngine、Executor、ExecutorOwner、ExecutorCapacity、LedgerRules、Repository、ScheduledTasksRepository | 103/103，0 失败 | logs/harness-p1-shared-tests.log |
| kotlin test -m jvm-app（8 类） | 调度页面交互/路由/截图、Cron 页面集成、Picker 迁移、LocalServiceTransport | 47/47，0 失败 | logs/harness-p1-jvmapp-tests.log |
| kotlin build -m jvm-app | 生产与测试源码 | Build successful, exit 0 | logs/harness-p1-jvmapp-build.log |
| kotlin test -m jvm-app（仅 ScopeNavigationInteractionTest） | 壳层紧凑导航（非本批） | 2 通过 / 1 失败（既有无关，见边界） | logs/harness-p1-scope-known-failure.log |
| pillow 测量脚本 | 参考图 vs 我们的截图 | 见上表 | measurement/navigator-measurements.txt |

截图重新生成到新目录 `docs/desktop-ui/captures/scheduled-page-2026-10-10/`（144 PNG + 144 geometry json），未覆盖 2026-10-08 的旧产物。

## 有意保留的差距（未做）

- 参考图"即将执行"右侧有一个漏斗筛选图标。本机没有真实的筛选语义（列表恒为全部任务），加一个不改变任何结果的图标就是死控件，故不加。
- 参考图导航器右边界有一条竖线；`ScheduledPageTestSurface` 只渲染导航器 + 页面，不渲染窗口 chrome，所以我们的截图没有这条线。
- 本批是几何对齐 + 目视核对，不是像素级比对，也没有原生窗口组合。
- 模板卡片与页面主体的宽度/文案本批不动（页面契约 `scheduled-page-contract-2026-10-08.md` 明确要求示例卡片不得带入原用户内容）。

## 边界

- 本批唯一失败是既有无关用例：`ScopeNavigationInteractionTest.compactMoreReachesPluginsAndScheduledTasks`——600dp 紧凑宽度下 `plugins-navigator` 未挂载（shell 紧凑布局，非调度）。
- 另一个既有无关失败 `ShortcutsInteractionTest.ordinarySearchEditingAndOpenMenuKeepTheirOwnKeyboardBehavior`（设置页快捷键搜索）本批未运行。
- 过程中 `ScheduledRoutingInteractionTest.editingBoundTaskPersistsAcrossStoreInstancesWithoutExecutingSession` 出现过一次 `scheduled-detail-prompt` 找不到的偶发失败；单独重跑与整套重跑均通过，记录为 flake，非本批代码问题。
- 各批证据独立，不累加为整体覆盖率；整体目标 active。
