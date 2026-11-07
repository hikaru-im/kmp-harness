# UI 交互逐项确认

本记录只收录用户逐项确认的决定。旧方案、现有代码和测试结果不自动成为交互标准。

参考对象：整体 UI 参考 Codex；模型供应商配置交互参考 DSH。Claude/Gemini 原生推理接入暂缓。

## 1. 添加厂商的模式切换

状态：用户已确认，已实现，交互测试通过。

用户选择：**保留两边输入**。

在添加卡片的“厂商预设”和“自定义连接”之间切换时，两种模式分别保留各自未保存的字段，回到原模式可继续编辑。切换本身不保存配置；尚未通过校验的输入也原样保留。

参考证据：DSH `ModelsSection.tsx` 的添加面板访问后保留挂载，通过 `hidden` 切换可见模式。本地参考版本为 `d743267388`。本项采用其行为，不复制实现。

实现位置：`apps/shared/src/im/hikaru/harness/client/page/SettingsModelsPage.kt`。

验收：`ModelSettingsInteractionTest.addModesRetainIndependentUnsavedInputsAcrossRepeatedSwitches` 验证两边不同的名称、端点、模型列表和超时文本在反复切换后保持独立，切换不触发写入，最后保存使用自定义模式自己的临时 API key。`returningToPresetRetainsItsOwnApiKeyForSave` 反向验证返回预设后保存使用预设的临时 API key。补充反向用例后的 `ModelSettingsInteractionTest` 最终 18/18 通过。

边界：本项没有决定关闭添加卡片或离开设置页的行为；这些差异需另行确认。获取模型期间的模式切换见第 2 项。

## 2. 获取模型期间的模式切换

状态：用户已确认，已实现，交互测试通过。

用户选择：**获取期间禁用切换**。

任一添加模式正在获取模型列表时，“厂商预设”和“自定义连接”两个切换按钮均禁用。请求成功或失败后恢复切换，避免切换模式中断获取。

参考证据：DSH `ModelsSection.tsx` 使用 `catalogBusy || customBusy` 锁定模式切换控件；本地参考版本 `d743267388`。

验收：`fetchingModelsLocksModeSwitchUntilSuccess` 与 `fetchingModelsUnlocksModeSwitchAfterFailure` 使用可控等待请求，覆盖两种模式中等待期间禁用切换、点击不切走，以及成功或失败后恢复切换。`ModelSettingsInteractionTest` 完整类 20/20 通过。

边界：关闭卡片、离开页面尚未在本项决定；获取结果的模式切换保留见第 3 项。

## 3. 切换模式后保留获取结果

状态：用户已确认，已实现，交互测试通过。

用户选择：**保留获取结果**。

“厂商预设”和“自定义连接”分别保留各自已获取的模型列表及目录来源；切换后返回仍可查看，不自动重复请求，也不保存配置。修改连接字段或 API key 时，继续按原有行为清除该模式的获取结果。

参考证据：DSH `ModelsSection.tsx` 在模式首次访问后保留面板挂载，通过 `hidden` 切换可见性，保留面板中的获取结果；本地参考版本 `d743267388`。本项采用其行为，不复制实现。

验收：`addModesRetainIndependentDiscoveryResultsAcrossSwitches` 为两种模式获取不同目录，反复切换验证列表与来源各自保留、仅请求两次、没有配置或凭据写入，并验证修改连接后只清除当前模式结果。完整 `ModelSettingsInteractionTest` 最终 21/21 通过，0 失败、0 跳过；本项新增用例通过，`git diff --check` 通过。

边界：关闭卡片、离开页面的保留行为仍需分别确认。

## 4. S-01 接通文件搜索

状态：用户已确认“接通文件搜索”；已实现，桌面自动化交互及访问边界测试通过。尚未进行独立桌面窗口的人工视觉验收。

用户选择：**接通文件搜索**。

命令菜单入口执行当前项目内文件路径搜索。无可用项目时明确提示先选择项目；支持输入筛选、上下键选择、Enter 预览、Esc 关闭、搜索/读取失败重试。菜单展示当前快捷键配置的首个有效绑定；默认桌面映射为 macOS ⌘P、Windows/Linux Ctrl+P。预览为应用内只读文本视图。

实现边界（实现方案，不扩展为用户额外指定的产品决定）：仅使用已验证的本机 Local Host 适配器；Relay 或未接入适配器的平台不读取本机路径。过滤 `.git`、跳过符号链接，预览限制为项目内 UTF-8 文本且不超过 512 KiB；搜索结果最多 200 条，并提示可能截断。

实现位置：`apps/shared/src/im/hikaru/harness/client/projects/ProjectFileSearch.kt`、`ProjectFileSearchDialog.kt`、`apps/jvm-app/src/im/hikaru/harness/desktop/projects/DesktopProjectFileSearch.kt`，以及 HarnessApp、AppShell、快捷键注册表和 JVM 入口接线。

验收：最终 JVM 定向套件 32/32 通过，0 失败、0 跳过：`ProjectFileSearchInteractionTest` 3 项、`WorkspaceNavigatorActionsInteractionTest` 10 项、`ShortcutsInteractionTest` 7 项、`DesktopProjectFileSearchTest` 11 项，以及 `LocalProjectsInteractionTest.commandMenuSearchReadsOnlyTheCurrentRegisteredProject` 1 项。覆盖菜单回调与关闭、真实快捷键入口、无项目提示、加载/空结果/失败重试、上下键与 Enter 预览、返回及 Esc、延迟搜索的旧结果隔离、真实项目注册后的搜索/读取接线，以及路径、Host、编码和大小边界。测试发现并修复预览态缺少焦点承接导致 Esc 无法关闭的问题。shared `ShortcutsRepositoryTest` 另行执行 7/7 通过；`git diff --check` 通过。以上为自动化测试结果，不代表全应用视觉验收完成。

边界：S-01 本身不代表用户确认其他外壳/导航差异；S-02 后续确认见第 5 项。移动端和 Relay 文件搜索适配器未实现。

## 5. S-02 Explore 键盘导航与固定按钮显示

状态：用户已确认，已实现，JVM 自动化交互与回归测试通过。尚未进行独立桌面窗口人工视觉验收。

用户选择：**按参考补齐**；追加核对固定按钮显示条件后，选择 **采用参考规则**。

探索入口支持 ↓/↑ 打开并定位首/末行；菜单内 ↑/↓ 循环且保留目的地/固定按钮列，←/→ 在同一行切换并适配 RTL，Home/End 定位首/末行目的地。Enter/Space 激活当前控件；固定操作不导航且不关闭菜单。Esc 关闭并返回探索入口。键盘焦点位于菜单内时，不因指针移出而触发悬停关闭。

固定按钮：未固定时默认隐藏，在行悬停或行内键盘焦点时显示；已固定时始终显示。按钮隐藏时保留占位。触屏平台默认显示固定按钮；平台能力声明尚不等同于运行时检测所有混合输入设备。

参考证据：Codex bundle 的方向键/Home/End 规则与固定按钮 `hover`、`focus-visible`、`hover:none` 显示条件。本项提取行为，不复制参考实现。

验收：最终 JVM 定向套件 33/33 通过，0 失败、0 跳过：`ExploreKeyboardInteractionTest` 4 项、`TooltipInteractionTest` 29 项。覆盖入口 ↓/↑/Enter、悬停打开后从主窗口用 ↓/↑/Tab 进入、菜单方向键与 Home/End、RTL 列切换及边界、Space/Enter 固定不导航且不关闭、Enter 导航关闭、Esc 关闭并返回入口、菜单键盘焦点在指针移出后保留，以及未固定按钮隐藏/行悬停/键盘焦点显示、隐藏占位不变和已固定按钮始终显示。当前 Explore 只有“项目”一行，运行测试覆盖单行循环，未验证多行间移动。`git diff --check` 通过。以上为自动化测试结果，不代表全应用视觉验收完成。

边界：不增加 Explore 目的地，不实施 S-03 固定项拖动重排；Android/iOS 的真机交互尚未验证。
