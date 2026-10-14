# Batch Q 证据：插件导航器视觉对齐与真实已登记列表

日期：2026-10-10
范围：插件管理 —— 左侧导航器（PluginsManagementNavigator）的结构与间距对齐原版截图，并把 Host 真实已登记插件接进导航器。
证据目录：docs/desktop-ui/verification/plugins-navigator-parity-2026-10-10/entry-q1/

## 结论

本批解决两个真实缺口：

1. 插件页导航器此前是"只有两个筛选按钮 + 无具体内容"的壳，视觉上与原版差距很大。现在按原版截图的实测几何重建：标题条（搜索动作右对齐）、两条 30px 主导图标行（1px 行距）、弱化的小节标题、30px 条目行（16px 图标，标签内缩 +40）。
2. 导航器此前从不列出已注册插件，尽管 `InstalledPluginsState.list` / `visiblePlugins` 早已存在。现在导航器读取同一份 Host 库存，与页面共用同一个查询；不渲染 Host 没报过的条目，空/错误/不可用状态照实显示。

生产源码写集 5 个文件，其中新增 1 个：

- 新增 apps/shared/src/im/hikaru/harness/client/plugins/PluginsManagementNavigator.kt
- apps/shared/src/im/hikaru/harness/client/shell/AppShell.kt（新增 `pluginsNavigatorContent` 参数；`NavigatorKind.Plugins` 优先用它，旧 PluginsNavigator 退化为回退）
- apps/shared/src/im/hikaru/harness/client/app/HarnessApp.kt（构造并注入 PluginsManagementNavigator；筛选回调同时收起紧凑抽屉）
- modules/ui-foundation/src/im/hikaru/harness/ui/foundation/HarnessTokens.kt（新增 `Control.PageNavigatorWidth = 289.dp`，`SettingsNavigatorWidth` 改为其别名）
- apps/shared/src/im/hikaru/harness/client/shell/NavigatorLayout.kt（Settings/Plugins/Hosts/Scheduled 的 pageNavigatorWidth 统一为 289，替换原先的 275）

测试写集 3 个文件：

- PluginRoutingInteractionTest.kt（新增 3 个用例：真实已登记列表 + 共用查询；289/31/+40 几何断言；生产 Shell 截图）
- PluginManagementInteractionTest.kt（`已安装插件` 计数 2→1：该文案只在页面标题出现，导航器小节标题是"已安装"）
- PluginsPageTestSupport.kt（新增 `pluginsShellCapture`：只在设置了环境变量时落盘 PNG + 几何 JSON）

## 参考图的密度判定

沿用 P 批次已确认的事实：docs/desktop-ui/reference/captures/2026-10-06-user-pages/ 下三张原版截图（settings-general、plugins-marketplace、scheduled-tasks）都是密度 1（1 raw px = 1 css px）。本批独立复核：plugins-marketplace.png 的导航器右边界竖线落在 x=340，rail 宽 52，导航器 x=52..340 = 289 px。归一化：参考导航器左 52 / 内容顶 74（30px 菜单栏 + 44px 标题栏之下）。

## 实测对照（measurement/navigator-measurements.txt）

我们的截图是生产 HarnessApp 在 2560x1440、浅色、`titleBarInset = 44.dp` 下渲染的整窗（无原生 chrome）。两边都归一到各自导航器左边界 / 导航器顶：参考导航器顶 = 74，我们 = 44。

| 元素 | 参考（相对导航器） | 我们（相对导航器） |
| --- | --- | --- |
| 导航器宽度 | 289（x 52..340） | 289（x 52..341） |
| 标题 ink y | 17..33 | 18..32 |
| 标题 ink x | 19..68 | 16..43 |
| 搜索图标 x | 256..267 | 251..265 |
| 主导行 1 y | 60..74 | 60..73 |
| 主导行 1 图标 x | 17..30 | 18..29 |
| 主导行 1 标签 x | 40..67 | 40..95 |
| 主导行 2 y | 91..105 | 91..104 |
| 主导行行距 | 31 | 31 |
| 小节标题 y | 138..150 | 136..147 |
| 小节标题 x | 17..56 | 17..53 |
| 条目行 1 y | 173..186 | 174..188 |
| 条目行 2 y | 204..214 | 205..219 |
| 条目行行距 | 31 | 31 |
| 条目标签 x | 41 | 41 |
| 选中高亮 y | 52..81 | 52..81 |
| 选中高亮 x（宽） | 8..279（272） | 8..280（273） |
| 右边界竖线 | x 340（lum 228） | x 340（lum 239） |

结论：结构性锚点（导航器宽 289、行距 31、标签内缩 40、选中高亮 8..280、右边界竖线 x 340）全部一致。剩余差是字形量级：参考标题是 CJK"自定义"，我们是"插件"，ink 起点 19 vs 16、ink 高度 16 vs 14；小节标题纵向 2 px。这与 P 批次记录的同类差一致。

注意本批推翻了上一批 handoff 的一个数据：handoff 写"参考选中高亮 x 66..323（相对 14..271）"，本次直接测量为 x 60..331（相对 8..279）。我们的实现（相对 8..280）本来就正确，无需改动。

## 一个被澄清的"缺口"

P 批次边界里写过"参考图导航器右边界竖线未复刻，因为测试载体只渲染导航器 + 页面，不含窗口 chrome"。本批用生产 Shell 复核后确认：真正原因是截图时 `titleBarInset = 0`，而 `TitleBarInsetColumn` 只在 inset > 0 时才画右边缘 hairline。真实窗口有 44dp 标题带，因此这条竖线在生产中是存在的。本批截图改用 44dp inset，竖线即出现在 x=340，与参考一致；不需要改任何源码。

## 测试与证据

| 命令 | 覆盖 | 结果 | 日志 |
| --- | --- | --- | --- |
| kotlin test -m shared | NavigatorLayoutTest、PageShellSpecTest、PageNavigatorMatrixAuditTest、AppShellStateTest | **28/28**，0 失败 | logs/harness-q1-shared-tests.log |
| kotlin test -m jvm-app（4 类） | PluginManagementInteractionTest、PluginRoutingInteractionTest、PluginLocalUpdateInteractionTest、PluginsPageInteractionTest | **28/28**，0 失败 | logs/harness-q1-jvmapp-tests.log |
| kotlin build -m jvm-app | 生产与测试源码 | **Build successful, exit 0** | logs/harness-q1-jvmapp-build.log |
| kotlin test -m jvm-app（仅 ScopeNavigationInteractionTest） | 壳层紧凑导航（非本批） | 2 通过 / 1 失败（既有无关） | logs/harness-q1-scope-known-failure.log |
| 截图用例（HARNESS_PLUGINS_NAVIGATOR_CAPTURE_DIR 已设置） | 生产 Shell 渲染 | 6/6 通过 | logs/harness-q1-navigator-capture.log |
| PIL 测量脚本 | 参考图 vs 我们的截图 | 见上表 | measurement/navigator-measurements.txt |

截图落在新目录 docs/desktop-ui/captures/plugins-navigator-2026-10-10/（8 PNG + 8 几何 JSON：1440x900 与 2560x1440 × 浅深）。未覆盖 plugins-page-2026-10-08 与 plugins-visual-2026-10-07。

## 有意保留的差距（未做）

- 参考导航器的三个分区（自定义 / 已安装 / 技能）与右侧市场网格属于用户已排除的云端/市场范围。我们的导航器只分组本机真实已登记库存，不引入市场发现。
- 参考导航器顶部标题是"自定义"、两个主导行是"插件 / 技能"；我们把同样的几何用在"插件 / 全部插件 / 仅启用的插件 / 已安装"上。语义对得上（都是主导图标行 + 分组列表），文案按本机真实语义。
- 参考图 rail 与导航器表面亮度是 245 / 252，我们渲染为 243 / 249（token 为 #F3F3F3 / #F9F9F9）。差 ≤4/255，且参考图取色是整机截图（有色彩配置与抗锯齿差异），不据此改 token。
- 这是几何对齐 + 目视核对，不是像素级比对，也没有原生窗口组合。

## 边界

- 唯一失败是既有无关用例：ScopeNavigationInteractionTest.compactMoreReachesPluginsAndScheduledTasks —— 600dp 紧凑宽度下 plugins-navigator 未挂载（shell 紧凑布局，本批未改该路径）。
- 另一个既有无关失败 ShortcutsInteractionTest.ordinarySearchEditingAndOpenMenuKeepTheirOwnKeyboardBehavior 本批未运行。
- 本批新增的截图用例默认不写盘（未设置环境变量时直接 return），因此对常规测试无副作用。
- 导航器只展示 Host 报告的库存；没有市场、安装来源扩展或云端插件，也不伪造任何条目。
- AppShell.kt 中的 ScheduledTasksNavigator 分支自 P 批次起已是死代码（生产走 ScheduledManagementNavigator），PluginsNavigator 退化为回退分支；本批不改动它们，留待清理批次。
- 各批证据独立，不累加为整体覆盖率；整体目标 active。
