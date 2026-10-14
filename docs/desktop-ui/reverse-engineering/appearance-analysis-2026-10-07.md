# 外观设置：当前归档分析

来源：/Applications/ChatGPT.app/Contents/Resources/app.asar；manifest 所记录 SHA256 为 22252f9b76d73a04d9e23710e358201cc4c29b6f003cfbbe109eeb0784dcf848。本轮按 ASAR header offset 直接读取下面两个文件，未使用动态 UI 加载。

- appearance-settings-42610e757930.js（807 bytes）：AppearanceSettings 页面用 slug=appearance 的标题；读取目标定位状态。targetId / targetText 有值时 defaultAdvancedExpanded=true；navigationKey 改变重建子内容。
- general-settings-4f1402fc1fbd.js（152954 bytes）：Ms 是外观内容组件；高级展开和 separateModes 分开保存于 React local state。

## 已确认组件树

1. Visual style 卡片：Header；Content gap-4；nested Mode row（Ys）；Ia(section=visual,separateModes)。
2. Advanced 卡片：Header 的 ghostTertiary / inline 展开按钮，aria-controls / aria-expanded 与上下 Chevron；No 提供 reset action。
3. 展开后 Content gap-4：输入/字体相关分组；Separate light and dark modes 开关；Ia(section=advanced,separateModes)；其余外观选项。

已找到主题 Light / Dark / System、Accent / Background / Text / Contrast、UI / Content / Code 字体及样式、主题导入/导出、指针光标、Diff 颜色/符号、减少动画、UI/代码字号、字体平滑的 message ID。

separateModes 的描述明确：只展示独立的浅深主题控件，不更改其现有值。Reset 使用 Promise.allSettled 并记录失败；不能用按钮点击立即伪造全部保存成功。

## 首轮尚未达到可委托实施的部分（历史状态）

- Ys / Ia / No 与具体配置 schema 的连接尚未逐项追踪；字体边界与重置的真实配置键未确定。
- 未提取本页参考截图/精确几何；不能只从上述 message IDs 拼出最终页面。
- Dock icon 属于平台能力，需要单独确认目标客户端适用性。
- aeon-appearance-picker 是虚拟助手/宠物外观，不是此设置页面；已辨别并排除。

下一步先解析 Ys、Ia、No 与设置描述符，确定可复用表单行及客户端配置存储接口，再委托页面实现。

## 第二轮组件追踪

已提取的 verbatim functions 位于 inventory-2026-10-07/appearance-functions.json，包含 source offset 与 bundle SHA。

- Ys：V.theme 读取值，空值用 system；system/light/dark 三列 radio group，grid w-68 max-w-full grid-cols-3 gap-4；onSelect 调用 S(...,{optimistic:false})，不是先假设保存成功。
- ac / oc：隐藏 radio input 提供键盘/选择语义，外层 label 配 Tooltip。主题缩略图 aspect-4/3、rounded-lg，选中 border-ring、hover border-strong、focus-visible outline。System 预览将浅深图并排。
- Ia：separateModes=false 显示当前解析的主题变体；true 显示 light/dark 两个 La，变体标题只在多主题时显示；system backdrop 支持状态控制透明侧栏选项。
- La：主题颜色/字体/代码主题数据来自 Nr(variant)。visual 使用 font family，advanced 使用 font style。Accent 修改同时置 accentSource=custom。导入有本地 draft/dialog，复制或导入反馈由实际操作结果决定。
- No：只在有高级更改或 separateModes 时显示重置；操作中禁用；同时重置两种主题和全局高级首选项，某项失败显示错误 Toast。

尚需追踪导入自 app-initial 的 Nr 和实际设置 schema，以及本客户端可用的配置写入边界。上述新增证据已确定主题选择与高级展开行为，但不代表外观页面已实现。

## 第三轮：schema 与保存边界已完成追踪

本节取代上方历史状态中的未解析 schema / 保存边界。原页参考截图仍缺失。

- oF（app-shared，导出 yZt）提供真实 descriptor：appearanceTheme、两种 ChromeTheme、字号边界、指针、字体平滑、差异标记和减少动画。逐项源片段保存在 inventory-2026-10-07/appearance-settings-evidence.json。
- dxn（导出 EEt）说明 optimistic=false 时在原生写入成功后发布；kRs（app-initial，导出 nw）串行写入并在失败时恢复。客户端采用受控 repository 和成功后发布，避免磁盘失败时主题假成功。
- HOi 的 nested 行 min-height40 / paddingY8 / gap12 与 compact 堆叠、nki 的 rounded16 控件组、aUs 的 section header / gap16 已解析；页面主列上限728与顶部73沿用现有 General 框架测量，不能作为本页像素一致的证据。
- 静态 Kotlin model / repository / DesktopAppearanceStore 已提供；页面实施契约见 appearance-implementation-contract.md。保存于本客户端 home 的 client-appearance.json，不读取或写入 Codex 用户配置。
- 原版任意字体目录和 ChromeTheme 分享 schema 尚未迁移；客户端目前使用有限字体族及独立 harness-theme-v1 分享格式。窗口透明、Dock 图标、代码预设和字体平滑没有可用 adapter，页面显示不可用。

当前实际交付状态和验证结果以 ../replication-plan-2026-10-07.md 的 P2 推进记录为准。
