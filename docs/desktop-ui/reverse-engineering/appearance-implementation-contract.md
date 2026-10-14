# 外观设置实施契约：P2

日期：2026-10-07。先读 appearance-analysis-2026-10-07.md、inventory-2026-10-07/appearance-functions.json 与 appearance-settings-evidence.json。当前 ASAR 已完整解析 Ms / Ys / Ia / La / No / ac / oc、实际 descriptor oF 及 kRs 的保存队列；不是仅凭消息 ID 拼界面。

## 已确认键与结构

- appearanceTheme: system/light/dark，默认 system；Ys 为 w-68（三列、gap-4）的 radio group，缩略图 4:3、8px 圆角，选中与焦点各有边框。
- appearanceLightChromeTheme / appearanceDarkChromeTheme：独立主题含 accent/surface/ink/contrast/fonts；contrast 0..100。UI 字号 sansFontSize 默认 14，范围 11..16；codeFontSize 默认 12，范围 8..24。
- usePointerCursors=false；useFontSmoothing=true；appearanceDiffMarkerStyle=color/symbols；reduced-motion-preference=system/on/off。
- 上方 Visual style section：Mode nested row；当前主题的颜色 / UI 字体分组。下方 Advanced section：ghost 展开与 Reset；字号、字体/样式、separateModes 本地开关、对比度、Diff、减少动画、平滑、指针。
- separateModes 只控制呈现 light/dark 两组，不保存为配置，不修改主题值。Reset Advanced 保留 visual 的颜色及 UI font family；重置高级字段与两种主题的高级值。
- H 为 section + Header + Content；Header 无副标题时 min-h-toolbar / pb-1.5。qe 内层控件组 rounded-2xl，nested row min-h-10 / py-2 / gap-3，max-sm 堆叠。Content gap-4。

## Kotlin 契约（已经写入）

apps/shared/src/.../settings/AppearanceSettings.kt：AppearanceSettings、AppearanceVariant、AppearanceMode / Font / FontStyle / DiffStyle / Motion；validate、variant、withVariant、resetAdvanced；AppearanceThemeCodec（客户端自己的版本化分享文本）。

AppearanceRepository.kt：AppearanceState(settings,loading,saving,loadError,saveError,durable)，canEdit；owner 只在持久写入成功后发布新值，失败保留主题且可以 retry；读取坏配置不会偷偷覆盖。UI 只发出 candidate。

主线程接入后的补充：state.loaded 区分首次读取完成与被取消，未读完时 canEdit=false，retry 重新读取；saveRevision 只在写入成功后递增，导入弹窗用 revision 和 candidate 匹配确认保存，再关闭与显示成功。

页面接口：

    SettingsAppearancePage(
      state: AppearanceState,
      onChange: (AppearanceSettings) -> Unit,
      onRetry: () -> Unit,
      defaultAdvancedExpanded: Boolean = false
    )

## 子 Agent 写集与交付要求

只新增 SettingsAppearancePage.kt 和可复用 SettingsFormComponents.kt，位于 apps/shared/src/im/hikaru/harness/client/page/。主线程负责 settings model/repository、desktop store、主题生效、HarnessApp / main.kt 路由、测试和截图。不要修改主线程写集；不运行 Amper。

1. 复用共享 Button / Switch / Menu / Dialog / Feedback 和 Token；抽出能用于后续设置页的 Frame / Card / Row / Select / number/color input，别复制整套 General 私有代码。页面主列上限 728dp（现有测量），padding top 73dp，主题控件采用上述 ASAR 几何；无本页原图，不声称像素通过。
2. Mode：System/Light/Dark 缩略预览单选，明确 selected、role、标签和键盘操作。testTag appearance-mode-system/light/dark。mode 变化发 onChange(copy(...))，不能自己保存或假反馈。
3. Visual：当前 light/dark 的三种颜色输入（空=默认，#RRGGBB 校验、失焦/Enter 提交）；UI 字体选择（当前客户端实际支持 System/Sans/Serif/Monospace）。高级区域有本地 separateModes，仅控制显示。
4. Advanced：字号含整数/range 校验；内容/代码字体和 UI/内容/代码样式；contrast；Diff color/symbols；reduced-motion system/on/off；fontSmoothing/usePointerCursors。全部受控并服从 canEdit。
5. Copy / Import theme：采用已提供 codec。复制只在剪贴板操作成功后反馈；导入对话框含 draft、格式错误、可修正/重试、取消。所有可编辑内容使用 BasicTextField 并留足真实高度，不复用裁切 Material 的固定小高度。导入回调只发 candidate，关闭/成功以 owner 保存结果为准；若此闭环过于复杂先用明确受控提交弹层状态，不伪造成功。
6. Reset Advanced 是即时受控变更；loading/saving 禁用；失败保留错误和 Retry。折叠不丢当前配置。
7. 可复用 Row 在紧凑列宽堆叠 label/control；本页长内容可滚动，错误与折叠动作可达；浅深主题都可用。
8. code theme presets / translucent native window / macOS Dock icon 没有对应可用客户端 adapter：按 source 核对后的相关位置显示明确 unavailable 状态；不要创建假的成功回调。原版任意字体目录、原版 codex-theme-v1 分享互通、native blur/dock 仍是后续缺口，不能报告外观全量完工。

推荐稳定 tag：settings-appearance-title、appearance-advanced-toggle、appearance-reset、appearance-ui-size、appearance-code-size、appearance-separate-modes、appearance-load-error、appearance-save-error、appearance-retry、appearance-import、appearance-import-input、appearance-import-confirm；每个 variant 字段附 light/dark。

## 主线程验收

- 读取/保存/重启复原；坏配置和磁盘写入失败不可发布成功状态；取消异常穿透；提交中不重复写入。
- route 可达；mode 改变实际壳层 palette 与 native title strip；两个主题独立，separateModes 不改配置；字体/颜色/对比度使用真实配置。
- 高级展开、校验、Reset、导入修正失败、保存 Retry；普通/紧凑、浅深截图。未接入的平台能力逐项保留缺口。
