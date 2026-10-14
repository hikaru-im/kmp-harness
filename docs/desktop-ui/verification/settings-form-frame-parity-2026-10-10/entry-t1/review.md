# Batch T 证据：设置表单页共用「常规」页内容列

日期：2026-10-10
范围：走 `SettingsFormFrame` 的设置页——外观、模型与 Provider、键盘快捷键、连接、Git、Worktrees——的页面框架几何与卡片样式。
证据目录：docs/desktop-ui/verification/settings-form-frame-parity-2026-10-10/entry-t1/

## 结论

参考图只有 `settings-general.png` 一张设置页截图（2560×1440，密度 1），S 批已据此把「常规」页正文对齐到：内容列 728 宽、卡片 x 1079..1806、-8 偏移、标题 28px。其余设置页当时仍走旧的 `SettingsFormFrame`：外壳把内容列上限设成 728，页面自己又减了左右各 16，因此实际列宽只有 696，并且卡片圆角 16、填充 `Surface`、描边 `Border`、标题 24px，与「常规」页不是同一套页面框架。

本批把这 6 个页面统一到「常规」页已实测的几何与卡片样式。它们没有自己的参考图，所以这是 Harness 内部一致性，不是这些页面的像素级比对。

## 生产源码写集

1. `modules/ui-foundation/src/im/hikaru/harness/ui/foundation/HarnessTokens.kt`：在既有的 `Control.SettingsContentMaxWidth = 728.dp` 旁新增 `SettingsPageTopPadding = 73.dp`、`SettingsCardRadius = 12.dp`、`SettingsRowInsetX = 17.dp`，作为设置页几何的唯一来源。
2. `apps/shared/src/im/hikaru/harness/client/page/SettingsGeneralPage.kt`：`SettingsGeneralModel` 的 `ContentWidth` / `CardRadius` / `PageTopPadding` / `RowInsetX` 改为引用上述 token（`const val` 变 `val`，取值不变）。`SettingsGeneralModelTest` 钉住的 728 / 12 / 73 保持有效。
3. `apps/shared/src/im/hikaru/harness/client/page/SettingsFormComponents.kt`：
   - `SettingsFormFrame` 改为 `BoxWithConstraints`，用满 728px 内容列并按「常规」页同样 -8px 左移（不足 728px 时退回 16px 内缩）；上内边距取 token，下内边距 `Spacing.Seven`；页标题 24sp → 28sp 并固定 34px 行盒；内容列挂 `<testTag>-column` 作为几何锚点。
   - `SettingsFormCard`：圆角 16 → 12（token），填充 `Surface` → `SurfaceElevated`，描边 `Border` → `BorderSubtle`，横向内边距 16 → 17（token）。

测试写集 2 个文件：

4. 新增 `apps/jvm-app/test/im/hikaru/harness/desktop/SettingsFormFrameInteractionTest.kt`：跨 6 个页面 × 2 种窗口 × 2 种主题的几何契约、600dp 窄屏回退，以及默认不写盘的截图用例（`HARNESS_SETTINGS_FORM_CAPTURE_DIR`）。
5. `apps/jvm-app/test/im/hikaru/harness/desktop/ConnectionsInteractionTest.kt`：外壳截图里把页面内容的 `assertFits` 锚点从 728px 页面盒换成更宽的 `main-surface`。框架现在有意落在页面盒左外侧 8px，用页面盒做锚点会误报裁切。

## 实测对照（measurement/settings-form-page-measurements.txt）

### 语义几何：每个页面的 `<tag>-column` 相对外壳 `main-content-column`

读取 JSON 的 `layoutBounds`（positionInRoot + size）。注意同文件的 `boundsInRoot` 被祖先盒裁掉，会隐藏这 8px 位移，不能用来量这一项。

| 页面 | 窗口/主题 | 列宽 | 列左 − 主列左 | 主列右 − 列右 |
| --- | --- | --- | --- | --- |
| appearance / models / keyboard-shortcuts / connections / git / worktrees | 1440×900 与 2560×1440，浅/深（共 14 张） | 728 | −8 | 8 |

14 张截图无一例外：列宽 728，列左 = 主列左 − 8，列右 = 主列右 − 8。

### 像素：卡片左右边界

两边都是 2560×1440、密度 1，左侧同样是 52 rail + 289 导航器，x 可直接比较。取卡片范围内「持续竖直的深色列」作为左右边界。

| 来源 | 卡片带 | 宽度 |
| --- | --- | --- |
| 参考 settings-general | 1079..1806 | 728 |
| 我们 appearance-2560x1440-light | 1079..1806 | 728 |

### 像素：页标题 ink 高度

两边都归一到各自内容顶（参考 74，我们 45）。

| 来源 | 标题 ink（相对内容顶） | ink 高 |
| --- | --- | --- |
| 参考 settings-general「常规」 | y 79..104 | 26 |
| 我们 appearance「外观」 | y 80..105 | 26 |

同为两个 CJK 字形，ink 高度与位置都在 1px 内一致，与 S 批把标题定为 28px 的结论吻合。

### 窄屏回退

契约用例在 600dp 下直接渲染外观页：列左 = 页左 + 16，列右 = 页右 − 16，不回退到 -8 偏移，也不会溢出。

## 边界（本批不声称的部分）

- 参考图只覆盖「常规」页。外观/模型/快捷键/连接/Git/Worktrees 没有同尺寸参考图，本批是 Harness 内部一致性（与已实测的「常规」页对齐），不是这 6 个页面的像素级比对。
- 行内部仍是两套实现：表单行 `SettingsFormRow` 最小高 40 加上下各 8 内边距，「常规」页行是 61/77/93 的固定最小高。卡片自身的 8px 纵向内边距也让表单卡首行距卡片顶 16px，而「常规」页首行距顶 8px。本批不改行样式。
- 圆角 12、填充 `SurfaceElevated`、描边 `BorderSubtle` 是 token 级对齐（与「常规」页同值），不是从像素量出来的。
- rail 与导航器的软过渡渐变、插件页标题带等既有未决项未动。

## 既有失败（非本批回归）

- `ShortcutsInteractionTest.ordinarySearchEditingAndOpenMenuKeepTheirOwnKeyboardBehavior`：`shortcuts-search` 把占位符暴露为 Text，既有陈旧用例。
- `ScopeNavigationInteractionTest.compactMoreReachesPluginsAndScheduledTasks`：600dp 下 plugins-navigator 未挂载，shell 紧凑布局的既有缺口。
- `GitBranchCheckoutInteractionTest` 的 2 个用例（`menu:1` 找不到）。为确认是既有失败而非本批回归，把 `SettingsFormComponents.kt` 临时整体还原为改动前实现后单独重跑，仍是同样 2 个失败；随后恢复本批实现。日志见 logs/harness-t1-preexisting-gitbranch-original-source.log。

## 跨批次影响

- 既有的 `ConnectionsInteractionTest.captureLocalSettingsAndHostsInLightAndDarkAt420600And1440` 直接写 `docs/desktop-ui/captures/connections/`（不受环境变量控制）。重跑回归时这组 PNG 被刷新，现在反映对齐后的框架；该目录没有 sha256 清单，本批不改其结构。
- 本批新增截图一律落在新目录 `docs/desktop-ui/captures/settings-form-pages-2026-10-10/`（14 PNG + 14 几何 JSON），未覆盖 `settings-navigator-2026-10-10` 与 `settings-page-2026-10-10`。

- 将截图用例在最终源代码上重跑一次，28 个截图文件（PNG 加 JSON）逐字节相同，说明归档截图对应的正是当前源代码。日志见 logs/harness-t1-form-capture-determinism.log。

## 测试与证据

| 命令 | 覆盖 | 结果 | 日志 |
| --- | --- | --- | --- |
| kotlin build -m shared | shared 全量加 shared 测试编译 | Build successful，exit 0 | logs/harness-t1-shared-build.log |
| kotlin build -m jvm-app | jvm-app 全量 | Build successful，exit 0 | logs/harness-t1-jvmapp-build.log |
| kotlin test -m shared（6 类） | SettingsGeneralModelTest、NavigatorLayoutTest、PageShellSpecTest、PageNavigatorMatrixAuditTest、AppShellStateTest、SettingsNavigatorStateTest | 36/36，0 失败 | logs/harness-t1-shared-tests.log |
| kotlin test -m jvm-app（9 类） | SettingsFormFrameInteractionTest、GeneralSettings、Appearance、ModelSettings、Connections、Shortcuts、GitSettingsRoute、PluginsPage、HarnessAppThreadPanelsShell | 64/65，唯一失败是既有 ShortcutsInteractionTest | logs/harness-t1-jvmapp-tests.log |
| kotlin test -m jvm-app（10 类，含 Worktrees/Git 回归） | WorktreePage、WorktreePin、WorktreeSessionObservation、WorktreeCreation、ManagedWorktree、WorktreeArchiveRecentSync、GitBranchCheckout、GitCommit、ScopeNavigation、ScreenshotTest | 40/43，3 个失败均为既有（ScopeNavigation 1 加 GitBranchCheckout 2） | 见本 review 的既有失败段 |
| 截图生成（HARNESS_SETTINGS_FORM_CAPTURE_DIR） | 6 个页面乘尺寸/主题 | 14 组 PNG 加 JSON | logs/harness-t1-form-capture.log |
| kotlin test -m jvm-app（GitWorktreesScreenshotTest） | Git/Worktrees 两阶段几何与截图（写入临时目录 HARNESS_GIT_WORKTREES_CAPTURE_DIR） | **6/6**，0 失败 | logs/harness-t1-gitworktrees-screenshots.log |

`measure-settings-form-pages.py` 可重跑，输出见 measurement/settings-form-page-measurements.txt（PROBLEMS: 0）。

