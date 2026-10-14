# Batch R 证据：基础设置导航器按原版截图实测对齐

日期：2026-10-10
范围：基础设置 —— 左侧导航器（SettingsNavigator）相对原版截图的几何与边界线。
证据目录：docs/desktop-ui/verification/settings-navigator-parity-2026-10-10/entry-r1/

## 结论

参考图 docs/desktop-ui/reference/captures/2026-10-06-user-pages/settings-general.png 已在本批实测（密度 1，导航器 x=52..340）。分组标题与带图标条目在此前批次已实现，本批实测后确认行几何本就一致，只修了 3 处有实测依据的差异：

1. 导航器右边界竖线缺失。参考图在 x=340 有一条 1px 竖线（lum 228），我们渲染为 249，看不见线。原因是页面导航器自己用不透明背景铺满整列，盖住了外壳画在内容之下的 hairline（插件页的导航器不加背景，所以那条线一直是可见的，掩盖了这个缺陷）。AppShell.kt 改为：推入式导航器的右边界线由外壳在内容之上绘制，并且只在 Pushed 布局绘制（浮动抽屉靠阴影分隔）。
2. 搜索框内边距 8 → 12。参考图的放大镜 ink 在导航器相对 x 22..33、占位文本在 45，我们改前分别是 18..29 / 41，整段内容偏左 4px；据此把共享 HarnessSearchField 的水平内边距从 Spacing.Two 改为 Spacing.Three（图标 16 + 间距 8 的结构不变，推导为 12+16+8=36，与参考文本 ink 45 一致）。
3. 设置行占位图标溢出单元格。`SettingsRowIconScale = 1.17` 的推导假设共享图标是 0.64 幅面，但实际用于占位的是满幅网格图标：实测 ink 达 20px，越过 16px 的图标槽（x rel 14..33，而已编目的设置图标是 17..30）。按实测 14px ink 回标到 0.82，占位行现在与其他行同宽同高。

生产源码写集 3 个文件：HarnessSearchField.kt、HarnessTokens.kt、AppShell.kt。测试写集 1 个文件：GeneralSettingsInteractionTest.kt（新增默认不写盘的 settingsShellCapture 与截图用例）。

## 实测对照（measurement/settings-navigator-measurements.txt）

我们的截图是生产 HarnessApp 在 2560x1440、浅色、titleBarInset = 44.dp 下渲染的整窗（无原生 chrome）。两边都归一到各自导航器原点：参考导航器顶 = 74，我们 = 44。

| 元素 | 参考（相对导航器） | 我们（相对导航器） |
| --- | --- | --- |
| 导航器宽度 | 289（x 52..340） | 289（x 52..341） |
| 右边界竖线 | x 340，lum 228 | x 340，lum 239 |
| 导航器表面 | 252 | 249 |
| 标题 ink | y 17..33，x 16..50 | y 18..34，x 16..51 |
| 搜索框 | y 52..87，x 8..279 | y 52..87，x 8..280 |
| 搜索框放大镜 ink | x 22..33 | x 22..33 |
| 搜索框占位文本 ink | x 45..70 | x 45..99 |
| 分组标题 ink | y 109..121，x 17..42 | y 107..119，x 16..43 |
| 首行（选中）ink | y 142..156，图标 17..30，标签 41..67 | y 141..154，图标 17..30，标签 41..67 |
| 次行 ink | y 173..186 | y 172..185 |
| 行距 | 31 | 31 |
| 选中行底色 | y 134..163，x 8..279（宽 272） | y 133..162，x 8..280（宽 273） |

结构锚点全部一致：导航器宽 289、搜索框 36 高与 8 内缩、行高 30 / 行距 31、图标 17..30、标签 41、选中行 8..280、右边界竖线 x 340。剩余差是字形量级（分组标题纵向 2px）与参考图整体亮度偏移（参考表面 252、我们 249；这条在本批的搜索框/选中行检测里用"相对表面的偏移"处理，不改 token）。

## 本批修正的一处旧结论

2026-10-06 的 settings-navigator-measurement 文档写"仓库内没有同状态的 Codex 设置截图，所有几何都只是结构推断"。该截图后来已归档到 reference/captures/2026-10-06-user-pages/settings-general.png，本批据此做了实测核对；文档中"导航器宽度 275、行高 36/行距 40"是当时的实现值，现实现已是 289 宽、30 行高/31 行距，与参考一致。

## 跨批次影响（Q 批截图）

本批改了两个共享行为，因此 Q 批归档的插件导航器截图与当前代码不再逐像素一致。用同一测试在 /tmp 重新渲染后比较：

- 插件导航器区域（x < 342）只有 x 340..341、y 0..44 不同：Q 时那条竖线由外壳画在内容之下、覆盖到标题带；现在画在内容之上、只覆盖内容区（y >= 44）。参考图在标题带没有竖线，所以新行为更接近参考。
- 内容区域（x >= 342）在 x 798..923、y 133..147 不同：插件页自己的"搜索已安装插件"字段因本批的内边距修正右移 4px。

Q 的证据目录与其 files.sha256 保持原样（批次快照）；这里记录精确差异，避免以后误判为回归。

## 测试与证据

| 命令 | 覆盖 | 结果 | 日志 |
| --- | --- | --- | --- |
| kotlin test -m shared（5 类） | NavigatorLayout、PageShellSpec、PageNavigatorMatrixAudit、AppShellState、SettingsNavigatorState | **33/33**，0 失败 | logs/harness-r1-shared-tests.log |
| kotlin test -m jvm-app（8 类） | GeneralSettings、Appearance、ModelSettings、Connections、GitSettingsRoute、PluginRouting、PluginManagement、PluginsPage 交互 | **56/56**，0 失败 | logs/harness-r1-jvmapp-tests.log |
| kotlin build -m jvm-app | 生产与测试源码 | **Build successful, exit 0** | logs/harness-r1-jvmapp-build.log |
| 截图用例（HARNESS_SETTINGS_NAVIGATOR_CAPTURE_DIR 已设置） | 生产 Shell 渲染 | 5/5 通过 | logs/harness-r1-capture.log |
| kotlin test -m jvm-app（2 类） | PtyTerminalPageInteractionTest、HarnessAppThreadPanelsShellTest（都拖动 navigator-resize-handle） | **15/15**，0 失败 | logs/harness-r1-navigator-drag-tests.log |
| PIL 测量脚本 | 参考图 vs 我们的截图 | 见上表 | measurement/settings-navigator-measurements.txt |

截图落在新目录 docs/desktop-ui/captures/settings-navigator-2026-10-10/（8 PNG + 8 几何 JSON：1440x900 与 2560x1440 × 浅深）。

## 有意保留的差距（未做）

- 参考图的分组里包含本机已排除的条目（通知、导入、个人资料、家长控制、受信任联系人、语音、配置、个性化、Mini 与虚拟宠物、使用情况和计费、账户、电脑操控、应用快照、浏览器、Cloud computer、Codex 云端、代码审查、已归档的聊天）。我们的导航器只列范围内条目：个人（常规、外观、模型与 Provider、键盘快捷键）、集成（插件）、编码（连接、Git、Worktrees）。行几何与分组结构对齐，内容按已确认范围裁剪。
- "模型与 Provider"是本机自己的行，参考图没有对应条目，其图标仍是共享占位图形（已修到与编目图标同宽同高，但没有 Codex 原图）。
- 参考图导航器顶部标题是"设置"，我们的标题样式已对齐，但窗口标题带的表面色（参考 245 附近、我们 249）不在本批范围。
- 这是几何对齐 + 目视核对，不是像素级比对，也没有原生窗口组合。

## 边界

- 参考图 rail 与导航器边界的过渡是软的（x 44..51 从 243 渐变到 235），我们渲染为 x=51 单像素 234；两者接近但不是同一画法，本批未改。
- 共享搜索框内边距改为 12 的依据只来自设置导航器这一处。参考图里页面级搜索框是描边白底样式、与导航器的填充药丸不是同一控件，因此页面级用法没有同源实测，本批不动其结构。
- 本批未运行 ScopeNavigationInteractionTest 与 ShortcutsInteractionTest（已知无关失败，见前批）。
- 各批证据独立，不累加为整体覆盖率；整体目标 active。
