# Codex 视觉复刻：公共组件当前状态（2026-10-08）

## 结论与边界

typed Button/Menu/Tabs/Row 和 anchored Popover 已有源码实现，且部分已接生产页面；问题主要是页面采用不一致、部分契约没有接入，以及缺少原版同尺寸同状态截图。交付前复读已确认项目/Git selector 在盘点期间变为 typed 接入（外部写入，非本审计修改）；建议下一批只迁移 **ScheduledPagePicker 到现有 typed anchored Menu**，保留长值和当前页面 fence。第二候选仅为共享 Tooltip 的 hover/focus 接入。

保留范围：核心壳、本地 Home/Thread/Projects/Recent、account、Host、基础 settings、定时任务、本地插件、Git Worktrees、终端和子 Agent。排除 ChatGPT 云端项目、Codex 云端环境、自动代码审查、团队、计费、家长控制、受信任联系人、语音、宠物、电脑操控、应用快照、浏览器操控、Cloud computer、图片库和插件公开市场。不能扩展服务或制造生产假数据来补 UI。

这是父线程的只读子盘点：不重新运行 Comet probe、不进入 workflow、不编译、不运行测试、不创建 agent/thread、不杀进程。唯一仓库写入是本新文档；交付元数据位于 `/tmp/harness-common-audit-20261008.*`。所有既有脏文件保留。历史 `common-component-analysis.md` 不代表当前实现状态。

### 证据口径

- 代码定位先使用 exact-file `codegraph explore <path>`；实现/接入判断来自读到的源码。负向搜索只声明检查范围，不能推导全仓库不存在。
- “已有测试源码”与“已有 XML 报告通过”分别记录；本轮 `tests_run=false`、`builds_run=false`。已有报告不证明当前脏树已重新测试，也不汇总历史总通过数。
- 原版 ASAR **外观声明**、**交互实现**、Harness 截图、原版截图是四类证据。代码中的尺寸不是原版像素测量。
- 本轮检查既有截图出处、manifest 和部分 PNG 原始尺寸，没有新抓图或逐像素视觉比对。无原版同 viewport、尺寸、主题、状态配对图，一律 **未验收**。
- 模型身份：`gpt-6.1-sol`。`SHUAIAPI` 是用户指定的路由名称，本地未独立核验 provider。

## 路径和证据索引

下列缩写是完整路径的前缀；后文每个文件都按此前缀解析，避免把同名测试或 legacy overload 混为一谈。

| 缩写 | 完整路径前缀 |
|---|---|
| F | `modules/ui-foundation/src/im/hikaru/harness/ui/foundation/` |
| FT | `modules/ui-foundation/test/im/hikaru/harness/ui/foundation/` |
| FJ | `modules/ui-foundation/test@jvm/im/hikaru/harness/ui/foundation/` |
| C | `apps/shared/src/im/hikaru/harness/client/` |
| DT | `apps/jvm-app/test/im/hikaru/harness/desktop/` |
| D | `docs/desktop-ui/` |

### 原版 ASAR：当前字节与历史证据分开

当前 `/Applications/ChatGPT.app/Contents/Resources/app.asar` 实查大小 `561986433`，payload base `5808772`，SHA-256 `797be85cd4c5b1be359c55c1d57970671cfba284a5eacf7612ee0ae9add1c360`。历史证据包含另一大小/散列的版本；不能套用旧绝对 offset。下表文件均位于 `webview/assets/`，offset 为本次当前包的绝对字节位置。

| 编号 | 文件、offset、完整 SHA-256 | 本次能确认的内容 |
|---|---|---|
| A1 | `app-shared-f2d570e85ceb.css`；76282285；`48caa3b5cef73fb568b697fe9aa0c6724916333b035b6c36d7958713ed759a38` | **外观声明**：overlay ring .5px、dropdown `--shadow-xl-spread`、popover `--shadow-lg`；不是渲染测量 |
| A2 | `app-shared-6c00c2afcf84.js`；68309201；`c85c0913e7ae7478e2756a2a34b172ddac37ae993d40443709a67da08ad4f611` | **交互**：Tooltip disabled/null bypass；wrapper delay 为传入/context/200 路径，底层 provider 的 700 默认另算；hover/open trigger；checkbox mixed/checked/disabled 及事件保护；Dialog modal/controlled open；Popper bottom/collision 默认 |
| A3 | `settings-host-dropdown-5fe57c5918fb.js`；451235627；`cdfbb2de54c0aa03ad7b0bf96b63a5c7770ade652194c7260f24b42f74a35251` | **外观/结构**：selected checkmark、max-h40/overflow-y、end align/menuWide；**交互**：disabled 返回 trigger、tooltip/aria-label、onSelect hostId |
| A4 | `settings-loading-row-12ddeb6e1903.js`；451239346；`14d6b20f95a084703b93eb5d27d555e8340fc599017c87275a1f773456214e4b` | **外观/语义**：small secondary icon、balanced text、SettingsRow control:null、role status |
| A5 | `settings-unsaved-changes-dialog-231441bfeb2a.js`；451318718；`0baa61313dfd06c6e6e30e0232700b1bc816bb77e503b2ce434182dd422f788f` | **交互**：pendingNavigation 控制 open，先 clear 再 destination；**外观结构**：secondary Keep、danger submit、compact/no close button |
| A6 | `settings-tabs-17f837176d94.js`；451317350；`da8c4bcf6b00d732621fcda2a68c8aeae7daace1944761132f7eacab267bb652` | **交互/结构**：selectedKey toolbar tabs、路由；仅引用保留的本地 Worktree settings 关联 |
| A7 | `settings-row-disclosure-ed4fa3e3d67e.js`；451316756；`13504271044ebb457d4c705e408fc9d06b5f3679ecacab775486789cdb4e917e` | **交互/结构**：expanded 条件内容和 contentId |
| A8 | `general-settings-5151ec63b051.js`；204609993；`897ac32b317d5d31d2b308297ddd8159e9fed8e6daf9fbfcf98d112f8c1b0dd5` | **交互**：confirm-before-close radio、async save/loading/error、controlled mac menu toggle；**外观结构**：end-align RadioGroup/RadioItem |
| A9 | `local-conversation-thread-turn-entries-5460cecd925b.js`；288052219；`de738426ee8f15f4da0589de296bc02e4094e0f6f5ba0d0de575af482f1bf0f1` | **交互/结构**：tool activity grouping、attached threads、assistant-message assembly；不能证明 File card 样式 |

既有、版本化源码证据仍可定位原版逻辑，但不声称等于当前 ASAR 版本：

- **H1**：`D/reverse-engineering/home-thread-evidence-2026-10-07.json`：保留的 `app-primary` V4e、`app-initial` PS/pFt/sLc、HomeComposerRoute/local dc；含输入组合/IME 线索。排除其中 cloud BMt、voice 等范围外内容。
- **T1**：`D/reverse-engineering/terminal-subagents-implementation-contract-2026-10-07.evidence.json`：F01 宽度、F05 terminal identity、F06–08 history/事件、F17 no-output/font-code、F19 active4/done10、F34–41 PTY onData/onResize。后者未等于当前 Harness 实现。
- **G1**：`D/reverse-engineering/git-worktrees-implementation-contract-2026-10-07.evidence.json`：G01–03 query/status/checkout/create、W14 行状态、W15 reduced-motion、D01–03 diff/toolbar/file renderer；D04 是 renderer 配置，不是像素测量。
- **S1**：`D/reverse-engineering/scheduled-implementation-contract-2026-10-07.evidence.json` 为定时页面来源，不能将自动 review 示例纳入本盘点。

`Tooltip-7a883f440ba4.js` 是 chart tooltip，`actions-menu-e897acebc66d.js` 是范围外 library artifacts；不能据此认定本地 hover tooltip/Menu。composer/comment/cloud 混合 bundle 的首个匹配也不能代替保留范围的来源。

### 截图索引：尺寸相同不等于原版配对

| 编号 | 已存在来源、尺寸和性质 | 配对结论 |
|---|---|---|
| V0 | 原版 `D/reference/captures/2026-10-06-user-pages/manifest.json`：`settings-general.png`、`scheduled-tasks.png` 均实查 2560×1440。General SHA `d89296acb02f7f319e6fbcc551592ec99a5970f4157d93b06e27e140287edc7b`；Scheduled SHA `33b6fe02c9ed53fe5bbb42b82e16cbb185248b1af2e8077efe5c9ce143eb11ae` | 可用原版截图；不是所有组件/状态证据 |
| V1 | Harness `D/captures/general-runtime/general-light-2560x1440.png` 实查同 canvas；另有历史 `D/captures/settings/general-light-screen-aligned-comparison.png` | 原图含 30px macOS 菜单栏，Harness 为 window-local，状态也不同；当前同 viewport/状态 **未验收** |
| V2 | `D/verification/thread-shell-2026-10-08/manifest.json` + `captures/`：1440×900、1024×768、600×800 light/dark，terminal/subagents/closed/resized；实查 `1024x768-dark-navigator-resized-both-tabs-terminal.png` 为 1024×768 | 无原版同尺寸同状态图，**未验收** |
| V3 | `D/verification/terminal-selection-2026-10-08/screenshots-manifest.json`（列表）：320×600/640×800 panel viewport 状态矩阵；实查 `detail-empty-panel-viewport-dark-320x600.png` 为 320×600 | 无原版 panel 配对，**未验收** |
| V4 | `D/captures/projects-main-path-2026-10-08/manifest.json`：1440×900/600×800；实查 1440 dark Home selected/detail/recent/thread-after-send。全 HarnessApp + temp disk/LocalConnection/Gateway/controlled endpoint fixture | 无原版 Projects/Recent 同状态图，**未验收**；不证明真实模型、远端或 native 渲染 |
| V5 | `D/captures/plugins-page-2026-10-08/manifest.json`：1440×900/600×800 页面与独立 register/remove popup；实查 detail/empty PNG | 无原版本地插件配对，**未验收**；公开市场原图被排除；page/popup fixture 不等于完整 native shell |
| V6 | `D/captures/git-worktrees-manifest-2026-10-08.json`：`captures/git-worktrees/` 下 1440×900/600×800 page/focused，另有 560×230/320 dialog；实查 large-diff 两种页面尺寸 | 无原版同状态 Git/Worktree 图，**未验收**；独立 dialog 尺寸不能当页面配对 |
| V7 | Harness `D/captures/scope/scheduled-light-1440x900.png` 和 `scheduled-dark-600x800.png` 实查对应尺寸；`D/captures/scheduled-page-2026-10-08/` 有 longfields/picker scroll PNG/geometry | 与 V0 的 2560×1440 原版不等尺寸，**未验收** |

`DT/ScreenshotTest.kt` 的 `renderReferenceScreens` 用 Harness `ReferenceThreadShell` 生成 `reference-thread-*`，不是原版。`D/reference/captures/2026-10-05-shell-refactor/` 也不能仅凭目录名认定原版。以上“实查”指出处/尺寸，不宣称逐像素通过。

### 实际读到的 XML 报告

| 编号 | 实际存在的报告 | 可用范围 |
|---|---|---|
| R1 | `D/verification/terminal-selection-2026-10-08/jvm-app.xml` | 下文指定的 ThreadPanelsInteractionTest 用例没有 failure/error/skipped；这是既有报告，不是本轮执行 |
| R2 | `D/verification/thread-shell-2026-10-08/reports/shell-final.xml` | `fullShell1440x900Light/Dark`、`fullShell600x800Light/Dark`、`fullShell1024x768Light/Dark`、`fullShell1024x768ResizedNavigatorLight/Dark` 实际存在且无 failure/error/skipped；另一个 pre-fix/sanitized-results.xml 有失败，不能混称全部通过 |
| R3 | `D/verification/main-path-and-plugins-2026-10-08/projects-main-path/TEST-junit-jupiter.xml` | 下文 ProjectsRecentInteractionTest 指定用例无 failure/error/skipped |
| R4 | `D/verification/main-path-and-plugins-2026-10-08/plugins-interaction/TEST-junit-jupiter.xml` | 下文 plugins 指定用例无 failure/error/skipped |

在已检查的 verification 目录未找到对应 foundation、LiveConversationInteractionTest、GeneralSettingsInteractionTest、GitPageInteractionTest XML，因此对这些只写“已有测试源码，结果未核验”。测试 fixture 可用于截图；报告不把 fixture 数据或假动作归入生产。

## 逐族当前盘点

### 1. Button / IconButton

- **源码**：`F/HarnessComponentModels.kt` typed button state/variant/size；`F/HarnessActionControls.kt` 的 `HarnessActionSurface`、`HarnessActionButton`、typed `HarnessIconButton`（148）、`HarnessProgress`。有 hover/pressed/focus/selected/error/loading/disabled，loading 挡重复 action，error 可 retry，progress 尊重 reduceMotion。`F/HarnessComponents.kt` 的兼容 Button/IconButton delegate 到 typed controls。
- **生产调用**：`C/panels/ThreadPanels.kt` 的 `TerminalProcessControls` 真实 LF/EOF/cancel 和 retry；`C/page/LocalProjectsPage.kt` 的注册/start/editor 实际 callbacks。General 不支持的按钮为 disabled，不能算功能接入。
- **测试**：`FT/HarnessComponentInteractionTest.kt`：`loadingControlsBlockActionsAndErrorsCanRetry`；`FT/HarnessComponentModelsTest.kt`：`loadingBlocksRepeatActionsWhileErrorsAllowRetry`、`selectedAndPressedGhostButtonsUseTheActivePalette`，结果未核验。`DT/ThreadPanelsInteractionTest.kt`：R1 的 `refreshAndErrorRetryReadThroughRealControllerAndDisablePendingActions`、`eofUsesExactProcessAndClosingViewDoesNotCancelIt` 有报告通过。
- **原版外观/交互**：A1 仅 overlay 外观声明，A2/A8 提供受控/disabled 交互线索；未确认所有按钮 variant 的原版尺寸和色值。**截图** V1/V2/V4，无对应原版按钮状态配对，**未验收**。
- **缺口**：缺按钮独立原版 hover/pressed/focus/loading 同尺寸证据；不能用当前 palette 测试认定像素一致。

### 2. Menu / Popover

- **源码**：交付前复读 `F/HarnessOverlays.kt::HarnessMenu`（164）已有 `popupModifier/popupWidth/popupMaxHeight/wrapLabels/enabled/selectionScope`；entries snapshot、lifetime invalidation、pendingLifetime、popupAlive guard，以及 close 后 current lifetime/model/enabled recheck。wrapped label 为 `Int.MAX_VALUE` + min row height；默认仍为单行。`HarnessPopover` 提供 bounded Popup/scroll、flip/clamp/RTL。`F/HarnessComponentModels.kt::HarnessMenuEntry` 有 testTag，`HarnessMenuSelection.readyKey(false,model)` 检查 ordered snapshot/selectable。
- **生产接入**：SettingsFormSelect、GeneralClosePolicyMenu、ComposerFooter model menu 已接 typed。交付前复读 `C/page/LocalProjectsPage.kt::LocalProjectPicker`（143）也已 typed，使用 selectionScope=currentBinding、popupMaxHeight=320、active/current/latest membership guard；`C/git/GitPageComponents.kt::GitPageSelect`（148）也已 typed，popupWidth≤560/popupMaxHeight320/wrapLabels=true，使用 optionsLifetime/active/latestEnabled guard。**这些是盘点期间外部改动后的当前源码，不再列为未迁移；既有 XML 不证明刚写入的迁移。** `C/scheduling/ScheduledPageLayout.kt::ScheduledPagePicker`（126）仍直接使用 Material DropdownMenu/DropdownMenuItem。`C/shell/AppShell.kt::AccountMenuOverlay` 为 deliberate in-composition scrim/rail anchor，ImageComposeScene 可捕获，不应顺手替换 Popup。`F/HarnessComponents.kt` 仍保留 legacy Menu compatibility API。
- **测试**：`FT/HarnessPopoverPositionTest.kt` 的 `sideAndAlignmentUseTheAnchorWithoutChangingItsLayout`、`startEndAlignmentAndSidesFollowRtl`、`insufficientSpaceFlipsToTheOppositeSide`、`everyEdgeAndOffscreenAnchorKeepsFittingContentInsideTheWindow`、`oversizedContentOrTinyWindowsDoNotProduceInvalidClampRanges`；`FT/HarnessComponentInteractionTest.kt::menuSkipsUnavailableActionsAndClosesBeforeDispatch`；`FT/HarnessComponentModelsTest.kt::menuSelectionIsDeliveredOnlyAfterClosingAndCannotBeReplacedByRepeatedActivation`，结果未核验。R3 已通过 `DT/ProjectsRecentInteractionTest.kt::pickerLongNameScrollSelectionDismissAndOldCallbackAreFenced` 和 `pickerFailedReadsAndRemovedOptionsRejectCapturedSelection`。`DT/GitPageInteractionTest.kt::longBranchMenuSelectionIsReadOnlyAndExplicitSwitchIsSingleFlight` 仅源码，结果未核验。
- **原版外观/交互**：A1 外观 shadow/ring；A2 collision；A3 checkmark/end-align/overflow/hostId。**截图** V4/V6/V7，无原版同尺寸 Menu 配对，**未验收**。
- **缺口**：项目/Git 已迁移，scheduled 仍未迁移。当前 scheduled picker 最大520×280、长值单行 horizontalScroll、立即 callback，必须保留完整值可达性。新增 typed lifetime/wrap 机制和项目/Git接入源码已读，但缺对最终脏树的本轮测试/原版同尺寸验收；旧报告只能证明旧产物。

### 3. Dialog / Confirm / unsaved

- **源码**：`F/HarnessComponents.kt::HarnessDialog`（228）；`F/HarnessConfirmDialog.kt::HarnessConfirmDialog`（47）支持 Ready/Submitting/Failed、destructive 初始 cancel focus、submit/cancel/Escape/outside loading guards、body scroll；`HarnessUnsavedNavigationState`（144）支持 request/keepEditing/discard。
- **生产调用**：`C/page/SettingsShortcutsPage.kt` 使用 shared Confirm 真实 repository.resetAll；`C/page/SettingsAppearancePage.kt` import、`C/page/SettingsConnectionsPage.kt` Host detail、`C/page/LocalProjectsPage.kt` editor、`C/git/GitPages.kt` commit 为 generic Dialog 真实回调。`C/plugins/InstalledPluginsPage.kt` removal 是 generic Dialog +真实 RemoveRegistration；`C/scheduling/ScheduledManagementPage.kt` delete 是 inline Surface +真实 delete，均未迁 shared Confirm。在 `apps/shared/src` 未找到 shared UnsavedNavigationState 调用。
- **测试**：`FT/HarnessComponentInteractionTest.kt` 的 `confirmDialogSubmitsAndKeepsFailureRetryable`、`confirmDialogLoadingBlocksRepeatedSubmitCancelAndEscape`、`confirmDialogEscapeDismissesAndRestoresTriggerFocus`、`unsavedNavigationConsumesOnlyDiscardedDestination`，结果未核验。R3 `editorBusyClosePreservesPendingThenExactRetryConfirmsOneDurableProject`；R4 `hostReplacementAndDisconnectHideDataAndBothDialogs` 有报告通过。`DT/AppearanceInteractionTest.kt::importStaysOpenOnFormatAndWriteFailureThenClosesAfterRetry` 仅源码。
- **原版外观/交互**：A2 modal/open；A5 Keep/danger/compact + clear-before-destination。**截图** V4/V5/V6 独立 popup 与 V1，无原版同状态 dialog pair，**未验收**。
- **缺口**：unsaved 仅契约/测试，生产未接；Confirm adoption 不一致。不要为没有真实 editable dirty state 的页添加假 unsaved。

### 4. Tooltip

- **源码**：`F/HarnessComponents.kt::HarnessTooltip`（250）是 inline Column 条件 Surface，没有 hover/focus/delay 生命周期，也不是 anchored popup。
- **生产调用**：`apps/shared/src` 搜索未见 `HarnessTooltip` 使用；不能把语义说明文字算 shared Tooltip。
- **测试**：未发现 dedicated Tooltip 文件/用例；本轮未验证 tooltip 测试通过。
- **原版外观/交互**：A2 能确认 disabled/null bypass、wrapper delay 路径和 trigger 行为；A3 能确认 retained Host tooltip/aria-label；具体外观未提取。**截图** V2 没有 Tooltip 原版同尺寸 pair，**未验收**。
- **缺口**：共享 hover/focus/delay/anchor ownership 与生产接入，尤其 disabled icon 的可解释性；优先限于 retained shell actions，不做全仓库重构。

### 5. Tabs

- **源码**：`F/HarnessCollections.kt::HarnessTabs`（50）支持 keyboard/RTL/close/horizontalScroll；`F/HarnessComponentModels.kt` 的 typed Tabs state/key 规则支持 enabled selection、close active 后 next/previous。
- **生产调用**：`C/panels/ThreadPanels.kt`（72）真实 `onAction` select/close；不是只在截图 demo 使用。
- **测试**：`FT/HarnessComponentInteractionTest.kt::tabsSupportKeyboardSelectionAndClosingTheActiveTab`；`FT/HarnessComponentModelsTest.kt::closingAnActiveTabChoosesTheNextThenPreviousAvailableNeighbour`、`closingAnInactiveTabPreservesSelectionAndProtectedTabsCannotClose` 等仅源码。R1 `DT/ThreadPanelsInteractionTest.kt::keyboardSwitchAndCloseTabsClearOnlyLocalPanelState` 有报告通过。
- **原版外观/交互**：A6 selectedKey/routing，T1 panel grouping/width；不能推导所有 tab 样式。**截图** V2/V3 有 Harness tab 状态，无原版配对，**未验收**。
- **缺口**：exact height/close icon/overflow presentation 原版证据；测试覆盖键盘不等于像素一致。

### 6. Input / Search / Select / Textarea

- **源码**：`F/HarnessComponents.kt::HarnessTextField`（125）用 BasicTextField，external label/support/error/readOnly/multiline；`F/HarnessSearchField.kt::HarnessSearchField`（30）受控输入/clear/focus/disabled。`C/page/SettingsFormComponents.kt::SettingsFormInput` local draft、Enter/blur 校验提交；`SettingsFormSelect` typed Menu。Select 尚无统一专用 primitive。
- **生产调用**：LocalProjects search/编辑、InstalledPlugins search/注册、Git commit multiline 与 Worktree inputs、Scheduled draft prompt textarea、SettingsAppearance import textarea 为真实参数/回调。model/process/project/Git selects 已采用 typed Menu；scheduled target/model/frequency/weekday 仍 Material legacy。
- **测试**：`FJ/HarnessTextFieldInteractionTest.kt`：`compactPathLabelAndSupportPaintWithoutClipping`、`typingSelectionReadOnlyDisabledAndErrorContractsRemainAvailable`、`multilineAndVisualTransformationKeepTheirTextLayouts`；`FJ/HarnessSearchFieldInteractionTest.kt`：`inputReportsChangesAndClearEmptiesValueWithoutLosingFocus`、`disabledFieldAndClearActionDoNotChangeValue`，仅源码、结果未核验；TextField 的 reference ink 是同 fixture 文本基线，不是原版。R3 `projectsLongNameRootSearchAndPointerWheelReachLastOfTwentyFour`、R4 `mouseSearchFilterDetailAndBackAtBothWidths` 已有报告通过。
- **原版外观/交互**：A3/A8 select，H1 local rich-editor/IME；本轮未确认通用 input 全部 pixel。**截图** V1/V4/V5/V6/V7，只有 V1 same canvas 而非 same viewport/state，其余无原版同尺寸，**未验收**。
- **缺口**：字体/line height/focus/validation、长 ref 完整值、textarea scroll。不能引入服务数据或 enabled empty handler 补控件。

### 7. Switch / Checkbox

- **源码**：`F/HarnessActionControls.kt::HarnessSwitch`（186）、`HarnessCheckbox`（227）、`HarnessTriStateCheckbox`（245）controlled + disabled/loading gating。switch 32×19dp、inset3/thumb13，checkbox16dp 是当前代码尺寸，未经原版测量。
- **生产调用**：`C/page/SettingsAppearancePage.kt` separate modes/pointer 有真实 settings save。smoothing 是 disabled empty handler；`C/page/SettingsGeneralPage.kt::Toggle` checked=false、empty handler 也是 disabled。在 `apps/shared/src` 未找到 shared Checkbox/TriStateCheckbox 使用，不能说已接入。
- **测试**：`FT/HarnessComponentInteractionTest.kt::togglesReportControlledValuesAndRespectDisabledOrLoadingState`；`DT/AppearanceInteractionTest.kt::resetPreservesVisualChoicesAndUnavailableSmoothingStaysDisabled` 仅源码，结果未核验。
- **原版外观/交互**：A2 checkbox mixed/checked/disabled 与事件保护；A8 controlled mac toggle；几何未确认。**截图** V1 同 canvas 但状态不同，无 toggle pair，**未验收**。
- **缺口**：Checkbox adoption 与真实可勾选数据；尺寸、轨道、thumb、disabled 色值原版证据。

### 8. Row / List / Section

- **源码**：`F/HarnessCollections.kt::HarnessRow`（132）depth/expansion/retry，`HarnessSection`（186）；`F/HarnessComponentModels.kt` typed Row state。`C/page/SettingsFormComponents.kt` responsive frame/section/card/row；`C/page/SettingsGeneralPage.kt::SettingsRow` 是另一个页面私有 layout。
- **生产调用**：`C/page/LocalProjectsPage.kt` 使用 shared row 与真实 register/start/bind/editor；General custom row 不算 shared adoption。GitPageList bounded scroll、Subagents custom rows、plugin rows仍各自布局。
- **测试**：`FT/HarnessComponentModelsTest.kt::rowDisclosureRequiresAnAvailableBranchAndKeepsOtherState` 仅源码。R3 `narrowDetailActionsWrapAndPointerStartUsesRealSelectedId`、`detailFullRootWrappedActionsAndUnavailableMembershipNeverRestore`；R4 `mouseWheelReachesListEndAndCompleteSkillAndAppValues` 有报告通过。
- **原版外观/交互**：A4 secondary loading row，A7 expanded/contentId，G1 W14 工作树行状态。**截图** V1/V4/V5/V6，没有共享 row 原版同尺寸 pair，**未验收**。
- **缺口**：adoption 部分完成；settings/general、terminal/subagent rows 仍 custom。无需先大规模统一所有 row；先复用既有 typed 状态而保留页面真实布局。

### 9. Loading / Error / Empty / Unavailable / Feedback

- **源码**：`F/HarnessFeedback.kt` 的 `HarnessLoadingState`（20）、`HarnessEmptyState`（36）、`HarnessErrorState`（44）、`HarnessStateContent`（62）；typed ContentState 只有 Ready/Loading/Empty/Error，缺 Unavailable。
- **生产调用**：`C/page/SettingsConnectionsPage.kt` Loading/Error/Empty +真实 refresh；InstalledPlugins shared feedback，但 `PluginReadFeedback` 把 Unavailable/AuthRequired/UnsupportedHost 映为 Empty，有语义混用。`C/app/LiveConversationSurface.kt` 分别展示 loading/error/disconnected/unavailable/empty。`apps/shared/src` 未见 HarnessStateContent 调用。未找到统一 toast/success primitive。
- **测试**：`FT/HarnessComponentInteractionTest.kt::disclosureAndFeedbackComposeOnlyTheVisibleContent`；`DT/LiveConversationInteractionTest.kt::emptyLoadingDisconnectedAndFailedThreadStatesDoNotInventMessages`、`typedHistorySurvivesLoadingAndFailureAndOnlyHistoryUsesGenericRetry` 仅源码。R4 `unavailableAndReadOnlyConnectionsDisableManagement`、`failedReadIsErrorAndExplicitRefreshRecovers`；R3 `distinctProjectPhasesDoNotSearchFailedSnapshots` 有报告通过。
- **原版外观/交互**：A4 role status/secondary row；A8 async save/error；T1 no-output。**截图** V3/V4/V5/V6 有 Harness 状态矩阵但无原版对应 pair，**未验收**。
- **缺口**：shared Unavailable/AuthRequired/Unsupported 不应伪装 empty；没有统一 feedback 视觉验收。现有真实 phase/error 区分应保留。

### 10. Scroll / Resize

- **源码**：`F/HarnessOverlays.kt` bounded popover verticalScroll；`F/HarnessCollections.kt` Tabs horizontalScroll；`F/HarnessComponents.kt::HarnessScrollbar`（280）只是 fraction + position×100dp 的装饰 bar，没有真实 adapter/drag。`C/shell/AppShell.kt` navigator horizontal drag/clamp 与右 panel side/overlay 几何；`C/app/HarnessApp.kt` onNavigatorResize 更新真实状态，未见 right-panel drag/persist primitive。
- **生产调用**：ConversationViewport LazyColumn、project/plugin list、GitPageList/Patch、terminal/subagent detail 都滚真实内容；无 shared HarnessScrollbar 生产调用（`apps/shared/src` 范围）。
- **测试**：R2 final shell exact-size/resized cases；R1 `compactLongTitleAndTranscriptRemainReachableByScrolling`；R3 长 list pointer wheel；R4 plugin list wheel 已有报告。`DT/LiveConversationInteractionTest.kt::longTypedTimelineScrollsInsideNarrowViewport` 仅源码。
- **原版外观/交互**：A2 collision、A3 bounded menu；T1 width/PTY resize，后者未实现。**截图** V2/V3/V4/V5/V6 多尺寸 Harness，不等于原版 resizing pair，**未验收**。
- **缺口**：装饰 scrollbar 不是真实滚动条；右 panel drag/持久化与原版 scroll geometry 未验证。

### 11. Composer

- **源码**：`C/app/LiveConversationSurface.kt::LiveConversationComposer`（122）TextFieldValue、Enter repeat/IME guard、Shift newline、owner/session keyed editor、40..160 或 viewport25% cap；`ComposerFooter`（234）compact<640、typed model Menu。
- **生产调用**：`C/app/HarnessApp.kt` conversation.updateDraft/submit/cancel 和真实 model catalog；current owner/session fences。unsupported attachment/permissions 为 disabled semantics，没有假 click。`C/page/ShellPages.kt::HomeComposer`（64）仍有 enabled empty Attach/Agent/Model handlers；`apps/shared/src` 对 `HomeComposer(` 搜索只见定义，不能把它当 live 接入，也应记录为遗留风险。
- **测试**：`DT/LiveConversationInteractionTest.kt` 的 `homeSlotUsesSuppliedHostAndDraftAndSubmitsWithoutSelectedSession`、`enterSubmitsShiftEnterEditsAndBusyStateBlocksDuplicateSubmission`、`blankDisconnectedAndUnavailableDisableSendWithoutDroppingDraft`、`modelMenuDispatchesTypedIdentityAndKeepsEnterAndEscapeBehavior`、`unsupportedControlsExplainDisabledStateAndHaveNoPretendClickAction`、`longEditorAndFooterFit4206001440LightAndDarkViewports`、`configuredEditorCapAndViewportQuarterAreBothRespected`；仅源码，结果未核验。
- **原版外观/交互**：H1 retained local slots/rich-editor/IME；混合 composer bundles 不用来认定原版全工具栏。**截图** V2/V4 有 Harness composer，无原版 same state pair，**未验收**。
- **缺口**：原版 toolbar geometry/attachments/focus、可用 model 菜单长值；禁用项不能用假服务补齐。

### 12. Message / Tool / File cards

- **源码**：`C/app/ConversationComponents.kt::ConversationViewport` LazyColumn；`MessageCard`（69）通用 bordered full-width Surface/You/Harness；`MessageContentView` Text/Reasoning/ToolCall/ToolResult，reasoning inline disclosure、`ToolCallBlock`（125）、`ToolResultBlock`（149）。
- **生产调用**：HarnessApp live conversation timeline 接真实消息/工具状态；在 `apps/shared/src` 未发现 FileCard，已读 renderer 未有 markdown/copy/diff 专用 card。不能把 Git patch renderer 当 message File card。
- **测试**：`DT/LiveConversationInteractionTest.kt::typedHistorySurvivesLoadingAndFailureAndOnlyHistoryUsesGenericRetry`、`emptyLoadingDisconnectedAndFailedThreadStatesDoNotInventMessages`、`longTypedTimelineScrollsInsideNarrowViewport`；仅源码，结果未核验。`DT/ScreenshotTest.kt::renderReferenceScreens` 是 Harness fixture，不是原版 card 测试。
- **原版外观/交互**：A9 tool grouping/attached threads，H1 retained turn entry；尚未确认 File card style。**截图** V2/V4 及 synthetic reference-thread，无原版 card 同尺寸 pair，**未验收**。
- **缺口**：通用 message surface 与原版具体 turn/tool/activity 外观仍有差距；file card 未实现于已查范围，markdown/copy/diff 需要具体原版与真实数据契约。

### 13. Terminal / subagent panel primitive

- **源码**：`C/panels/ThreadPanels.kt` Tabs、process `SettingsFormSelect`（90）、`TerminalProcessControls`（152）、`BackgroundOutputPanel`（196）、`SubagentsPanel`（230）；Active/Done/Unknown/show-more/detail/back/model/reasoning/transcript。output 是可 selection、vertical/horizontal scroll 的 plain read-only text，非 PTY/ANSI。
- **生产调用**：真实 process key/callback；LF/EOF/cancel 按 pending/lifecycle gating，draft 只在 receipt 后清；subagent consumer 需 state authorization 和真实 consumer 都允许，否则 read-only。关闭 panel 不取消进程。
- **测试**：`DT/ThreadPanelsInteractionTest.kt` 在 R1 已有报告：`compactProcessSelectRoutesInputToExactKeyAndDoesNotReuseAnotherDraft`、`sameKeyRefreshPreservesUnknownDraftAfterEarlierDeliveryReceipt`、`keyboardSwitchAndCloseTabsClearOnlyLocalPanelState`、`terminalInputConfirmsDeliveryAndUncertainReplyPreservesDraftInCompactPanel`、`compactLongTitleAndTranscriptRemainReachableByScrolling`、`selectBackAndShowMoreReduceActualPanelActions`、`eofUsesExactProcessAndClosingViewDoesNotCancelIt`、`refreshAndErrorRetryReadThroughRealControllerAndDisablePendingActions`、`readOnlyDetailNeverMountsConsumerAndBothAuthorizationsAreRequired`。这些 case 在该 XML 均无 failure/error/skipped；不是本轮执行。
- **原版外观/交互**：T1 F01/F17/F19 宽度/字体结构/list limits；F05–08 identity/history；F34–41 PTY 事件；A9 attached/tool assembly。**截图** V2/V3，无原版 panel 同尺寸 pair，**未验收**。
- **缺口**：PTY/ANSI/native onResize/onData 未实现，不能把 plain output 面板称完整终端；真实模型、远端链路、native 渲染没有本轮证明。

## 下一实施切片：只推荐一个主项

### 主项：ScheduledPagePicker 接入 typed anchored Menu

收益：`C/scheduling/ScheduledPageLayout.kt::ScheduledPagePicker` 是最终复读时仍明确未接 typed Menu 的 retained selector，供 target/model/frequency/weekday 四类实际字段共用。真实调用在 `C/scheduling/ScheduledManagementPage.kt`（286/291/297/309），均经 isCurrent() 更新 repository draft。复用 Menu 可统一 keyboard、close-before-dispatch、bounded popup 和 disabled 状态，无需新增服务。项目/Git 已在盘点期间接入，不重复安排。account overlay 不在此切片。

**建议允许写集（父线程下一实施提案，非本审计实际写集）：**

- `C/scheduling/ScheduledPageLayout.kt`：只 ScheduledPagePicker 和必要 callback/fence bridge。
- `FT/HarnessComponentInteractionTest.kt`；只有发现实际基础契约缺陷才允许改 `F/HarnessOverlays.kt`/`F/HarnessComponentModels.kt` 与直接相关 `FT/HarnessComponentModelsTest.kt`/`FT/HarnessPopoverPositionTest.kt`，默认复用已有参数，不新增统一抽象。
- `DT/ScheduledPageInteractionTest.kt`；截图需要时 `DT/ScheduledPageScreenshotTest.kt`/`DT/ScheduledManagementScreenshotTest.kt`。
- 新的 `D/verification/common-menu-integration-<实施日期>/`，保存实际 XML、原始截图、manifest；不覆写历史证据。

**可复用契约：**

1. typed entries stable/unique key、selected/enabled/loading、close 后单次派发、ordered snapshot/selectionScope lifetime、popupAlive guard、keyboard/RTL/flip/clamp；bounded width/height 和 wrapLabels 已存在。不得用 label 反推 identity。
2. ScheduledPagePicker 的 full page fence、`token=remember(fence,enabled,value,options)`、activeToken、key(token)/onDispose；原始 semantic callback 生命周期不能换成 stale option。保留 520×280 bounded menu 与长值完整可读语义（若从水平 scroll 改 wrap，需要明确对照验收）。
3. ScheduledManagementPage 的 repository/page current fence、真实 Host target/model catalog；无 catalog 必须 disabled。选项只修改可取消 draft，不能启用任务、执行任务或创造 model/target。保持静态 compile-time slots 与 cold-start 架构。

**真实验收步骤（本轮不执行）：**

1. 重新读最新源码与 dirty baseline，迁移 picker，review diff 保留 fence/activeToken 及同值 A→B→A、catalog reorder/remove 的失效约束。
2. 跑 targeted foundation Menu 与 ScheduledPageInteraction，保存实际 XML、检查 failure/error/skipped。沿用 `changedDraftAndContextClosePopupAndRejectOldOptionCallback`、`scopeAndDisconnectHideOldHistoryAndRejectSavedOpenCallback`、`replacingRepositoryClosesPickerAndRejectsOldDraftCallback`、`longPickersStayBoundedAndNavigatorHeaderSurvivesIndependentScroll`、`absentHostCatalogCannotBeReplacedWithInventedTargetOrModel`。这些用例源码已读；本审计未确认对应 XML，通过不能预填。
3. 验证 Up/Down/Home/End/Enter/Space/Escape/Tab、disabled/loading skip、close 后 dispatch 单次、outside dismiss/focus、edge/RTL；长 target/model 全值可达、header 和 independent scroll 不回归。
4. 用实际 ScheduledRepository 与 catalog/context/scope 操作 draft；draft/context 改变、repository replacement/disconnect/save busy/option removed 后触发捕获的旧 callback，确认无选择/写入；cancel 不写 durable store。截图 fixture 可造长名称，生产不得假数据/空 handler。
5. 保存600×800/1440×900 light/dark open menu/long values/disabled/edge 截图，区分 page 与 popup viewport。取得同尺寸同主题同状态原版图后才比较 font/geometry/colors。V0 scheduled 原图2560×1440不能替代这些配对图。

**仍不能验收：**原版 scheduled selector 同尺寸同状态 pair、原版 width/row height/shadow/ring/checkmark/hover/focus/长值处理、native popup placement。ASAR A1/A2/A3 只给共享声明/交互线索，S1 是历史版本页面证据；即使新测试通过仍标 **视觉未验收**。

### 第二候选（仅替补）：retained shell actions 的 shared Tooltip

若主项因原版长值约束暂不能实施，替补只做 `F/HarnessComponents.kt` Tooltip 或独立 foundation Tooltip 文件 + `C/shell/AppShell.kt` 已有真实动作上的 opt-in hover/focus/delay。测试真实 trigger focus/hover/disabled/null content/dismiss，取得原版 tooltip same-state pair 才验外观。依据 A2/A3，不采用 chart tooltip，不扫全仓库；不会扩展任何业务服务。此候选当前收益低于 scheduled 多字段 selector，因为 shared Tooltip 尚无生产依赖。

## 交付说明

本审计实际仓库写集只有 `docs/desktop-ui/reverse-engineering/common-component-status-2026-10-08.md`，before 不存在，after SHA 记入 `/tmp/harness-common-audit-20261008.json`。`write_set_released=true` 表示本子工作释放文档写集供父线程审核；不是批准执行上述实施提案。现有报告/截图不代替父线程独立复核，也不宣称本轮构建、测试或原版视觉验收通过。
