# Batch S 证据：设置页正文按原版截图实测对齐

日期：2026-10-10
范围：基础设置页正文（标题、分节标题、卡片、行、分隔线）相对原版截图的几何。
证据目录：docs/desktop-ui/verification/settings-page-parity-2026-10-10/entry-s1/

## 结论

参考图 docs/desktop-ui/reference/captures/2026-10-06-user-pages/settings-general.png 已实测（密度 1）。正文三处有实测依据的差异已修正：

1. 内容列宽 696 → 728。页面自身把 728 上限又减了左右各 16 内边距，而外壳（MainSurface）已经把设置内容列的上限设成 728，于是 `narrowContent` 分支在恰好 728 时误判为窄屏，走了"内缩 16"的分支而不是既有的 -8 偏移。改为 `maxWidth < ContentWidth` 才判窄屏后，卡片落在 x 1079..1806（宽 728），与参考完全一致。
2. 行分隔线内缩 17。参考图卡片 x 1079..1806、分隔线 x 1096..1789，即分隔线与行内容同内缩 17；我们此前是整卡满宽。现在分隔线也内缩 17（同行内容的 `RowInsetX`）。
3. 页标题字号 24 → 28，行盒 34。参考图标题"常规"ink 是 53×26；同一套字体下 24px 渲染成 45×23（参照：同一批参考图里插件页标题"插件"实测 46×23，正好是 24px）。改到 28px 后 ink 为 53×27、位置完全对上。标题的自然行盒是 39px，会让下方所有内容整体下移 5px，因此固定为 34px 行盒，使分节标题与首卡位置与参考一致。

生产源码写集 1 个文件：`SettingsGeneralPage.kt`。测试写集 1 个文件：`GeneralSettingsInteractionTest.kt`（新增 `capturesRenderedSettingsPageForParityReview`，用持久化 store 渲染，默认不写盘）。

## 实测对照（measurement/settings-page-measurements.txt）

两边都归一到各自内容顶：参考 = 74，我们 = 44。

| 元素 | 参考（相对内容顶） | 我们（相对内容顶） |
| --- | --- | --- |
| 页标题 ink | y 79..104，x 1081..1133 | y 80..106，x 1081..1133 |
| 分节标题「权限」ink | y 157..170，x 1079..1106 | y 157..170，x 1079..1106 |
| 首卡上/下边 | y 189 / 359 | y 189 / 359 |
| 首卡内分隔线 | y 266 | y 266 |
| 第二卡上边 | y 446 | y 446 |
| 卡片横向范围 | 1090..1795（边线）／1096..1789（分隔线） | 1089..1796／1096..1789 |
| 卡片宽度（布局） | 728（x 1079..1806） | 728（x 1079..1806） |
| 行内容内缩 | 17（ink x 1096/1097） | 17（ink x 1096/1097） |
| 第二行标签 ink | y 282..294，x 1097..1173 | y 282..294，x 1097..1173 |
| 分节标题「常规」ink | y 414..427，x 1080..1106 | y 414..427，x 1080..1106 |

结构锚点全部逐像素一致。

## 仅剩的差异（内容驱动，非几何错误）

第二张卡片内部分隔线：参考 y 507 / 567，我们 y 518 / 591。原因是参考的每个偏好行是"标题 + 说明"两行（行高 61），我们为未接入的服务额外显示第三行「尚未接入…」状态（行高 73）。这是既有批次的诚实性要求（未接的服务必须写明未接入，而不是显示一个可用的开关），不为对齐而隐藏。首卡不受影响，因为它的两行与参考一一对应，高度 77/93 与参考完全相同。

另一处小差：一行的标题与说明之间的 ink 间距，参考约 4px、我们约 7px（来自 2dp 行内间距与字体行盒）。行高由 minHeight 决定，不影响卡片与分节的对齐。

## 跨批次影响（R 批截图）

本批改了设置页正文，因此在同一测试下重渲染 R 批的设置导航器截图后：导航器区域（x < 341）逐像素**完全相同**；只有页面区域（x >= 341）不同（2560x1440 下 bbox 738,122..1466,1440）。R 的证据目录与其 files.sha256 保持批次快照不变。

## 测试与证据

| 命令 | 覆盖 | 结果 | 日志 |
| --- | --- | --- | --- |
| kotlin test -m shared（4 类） | GeneralSettingsRepositoryTest、DesktopGeneralSettingsStoreTest、NavigatorLayoutTest、SettingsNavigatorStateTest | **21/21**，0 失败 | logs/harness-s1-shared-tests.log |
| kotlin test -m jvm-app（5 类） | GeneralSettings、Appearance、ModelSettings、Connections、GitSettingsRoute 交互 | **39/39**，0 失败 | logs/harness-s1-jvmapp-tests.log |
| kotlin build -m jvm-app | 生产与测试源码 | **Build successful, exit 0** | logs/harness-s1-jvmapp-build.log |
| 截图用例（HARNESS_SETTINGS_PAGE_CAPTURE_DIR 已设置） | 生产 Shell 渲染（持久化 store） | 6/6 通过 | logs/harness-s1-capture.log |
| PIL 测量脚本 | 参考图 vs 我们的截图 | 见上表 | measurement/settings-page-measurements.txt |

截图落在新目录 docs/desktop-ui/captures/settings-page-2026-10-10/（8 PNG + 8 几何 JSON：1440x900 与 2560x1440 × 浅深）。

## 边界

- 参考图的偏好行都是可用控件；我们的多数行是"未接入"状态并额外显示一行说明，因此第二张卡片更高（参考 789、我们 925）。这是如实反映本机能力，不是布局偏差。
- 参考图正文右侧控件（开关、下拉、按钮）的颜色/形状本批未逐项测量，只对齐了位置与整页几何。
- 本批是几何对齐 + 目视核对，不是像素级比对，也无原生窗口组合。
- 各批证据独立，不累加为整体覆盖率；整体目标 active。
