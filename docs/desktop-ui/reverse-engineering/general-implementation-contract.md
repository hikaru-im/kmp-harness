# General 行为分析与实施契约（2026-10-07）

## 原版证据

本地已提取 `app-shared-9d148924be0b.js`，SHA256 `eb2500c5398279d4fbf5c71d7ce982d54631660907761740199f6c22591d71ab`。以下为 UTF-8 解码后的字符偏移，不能当作 ASAR byte offset。

| descriptor | 字符位置 | schema / default |
| --- | ---: | --- |
| confirmBeforeClose | 1987257 | never / keyboardOnly / always，default keyboardOnly；key confirm-before-close |
| projectlessWorkspaceRoot | 1993398 | absolute string 或 null，default null |
| preventSleepWhileRunning | 1993666 | boolean，default false |
| defaultTerminalLocation | 1994262 | bottom / right，default bottom |
| defaultWorkspaceFullView | 1988065 | boolean，default false |

位置 2406260 的 confirm() 动态加载 WindowCloseConfirmationDialog（dialog-f6ba808b9f32.js），校验 runtime scope 与 abort signal，再消费确认结果。该片段证明有实际窗口确认流程；本客户端用受控 Compose dialog 和自身窗口关闭回调实现。截图中的开关值属于参考场景，不是 Harness 默认值或能力证明。

## 页面组件与几何

现有 SettingsGeneralModel 保留为参考 evidence model。主列728，顶部73，card12radius，section/card15，权限行77/93；preferences heights 61/59/60/59/60/60/59/60/59/60/59/60/61。现有大窗口几何保留；低于600的内容列使用上下布局、自动高度和完整说明，不能固定行高裁切文案。

结构：scroll frame → title → permissions section/card/rows → general section/card/rows。复用 HarnessSwitch/Button/Menu；不新增 runtime JS。

## 能力与动作

| 行 | 本批 runtime 行为 |
| --- | --- |
| confirm-close | Desktop 真正存储策略；Never、KeyboardOnly、Always；点击保存成功后才发布。读取/写入失败不切换有效策略 |
| plugins | 改为“管理插件”按钮，导航真实 PageId.Plugins；不能做虚假的全局插件开关，目标页管理能力仍待插件批次 |
| default/full permission | 禁用，false；未接入 Host 权限配置 |
| task-folder/file-app/language/menu-bar | 禁用；不显示硬编码用户路径或可工作的语言/文件偏好 |
| full-view/bottom-panel/terminal-location | 禁用；随 Thread 面板批次接入 consumer |
| prevent-sleep/compress-history/prompt-suggestions | 禁用；底层服务尚未接入 |
| licenses | 禁用；尚无本客户端 bundled third-party notices，不能用 backend LICENSE 冒充完整声明 |

每个 unavailable 行由 GeneralUnavailableReasons.forRow 提供简短原因，在行 description 中可见。不可用项的 visual control 可以保留，但禁用、不持有可变假状态；不得在 runtime 使用 initiallyChecked。

## 页面委托 API 与独立写集

仅编辑 `apps/shared/src/im/hikaru/harness/client/page/SettingsGeneralPage.kt`。保留 SettingsGeneralModel 的参考内容/默认值/排序/几何常量；可调整其下方 runtime 布局/helper。不写 repository、main、窗口、测试、截图、模块或其他页面；不运行 Amper。

    @Composable public fun SettingsGeneralPage(
      state: GeneralSettingsState = GeneralSettingsState(),
      onClosePolicyChange: (CloseConfirmationPolicy) -> Unit = {},
      onRetry: () -> Unit = {},
      onOpenPlugins: (() -> Unit)? = null,
    )

state 唯一数据源；关闭选项使用 enum，不用文案作配置键；state.canEdit 控制菜单/按钮；state.closePolicyAvailable=false 显示本平台未接入。loading/loadError/saveError/saving 与重试必须可見；未持久化状态明确显示“本次运行”。插件按钮仅在 onOpenPlugins != null 时可用。

稳定 tag：既有 settings-general-title/row-{id}/control-{id} 保留；新增 general-loading/general-saving/general-load-error/general-save-error/general-retry；关闭菜单项 key 用 enum.name；显示 label 为 enum.label。

## 主线程集成与验收

GeneralSettingsRepository + DesktopGeneralSettingsStore 写 client-general.json，本机窗口消费已保存 closeConfirmation。native close 与 Cmd-W（macOS）/Ctrl-W（其他桌面）分别标注 origin；KeyboardOnly 仅快捷键确认，Always 两者确认，Never 不确认。取消/重复请求/重复确认必须只触发一次实际 close；主程序原有 finally 资源释放保留。Cmd-Q/系统注销不属于本窗口策略。

验证实际文件重读、坏配置保留、失败重试、取消传播、策略矩阵、窗口 handler/key wiring、页面路线切换后的已保存值、不可用控件、插件导航及600×800浅深色可读性。原版截图只提供参考布局；禁用原因与真实动作使 runtime 内容变化，不能宣称整页像素一致。
