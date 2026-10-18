# 桌面端主题规范

桌面端统一使用 `modules/ui-foundation` 的 `HarnessColors`、`HarnessTheme` 和 `HarnessTokens`。
页面通过 `HarnessTokens.Colors` 读取当前主题，不在组件中另建浅深色基础配色。

## 背景层级

| 语义角色 | 区域 | 浅色 | 深色 |
| --- | --- | --- | --- |
| `ShellBackground` | 窗口外壳、图标栏、标题栏、窗格标题 | `#F6F6F6` | `#0F0F0F` |
| `NavigationBackground` | 项目、会话、设置导航 | 65% 白（`A6FFFFFF`） | `#202020` |
| `WorkspaceBackground` | 聊天、工具页、客户端窗格 | `#FFFFFF` | `#171717` |
| `OverlayBackground` | 菜单、弹层 | `#FFFFFF` | `#282828` |

浅色外壳比工作面暗一档，导航以半透明白色叠在页面卡上；深色外壳比工作面更深，导航为不透明
`#202020`。新标签页工具栏使用工作面白（深色为 `#171717`），内容区使用 `SurfaceCanvas`
（`#FAFAFA` / `#1A1A1A`）保留一档层次。上下文与摘要面板使用 `Surface`（`#F9F9F9` / `#202020`）。
浮层用独立表面与阴影表达层级。
标题栏中的导航框架保持透明，使用 `HarnessTheme(drawBackground = false)` 仅提供主题配置，
由父级标题栏绘制背景，按钮在交互时叠加状态色。

`AppBackground`、`NavigatorSurface`、`Canvas`、`SurfaceElevated` 是上述角色的兼容存储字段，
新增区域引用应使用语义名称。`SurfaceCanvas` 保留为新标签页内容区自己的槽位。

聊天页不直接套用工作面色板：`ConversationAppearance` 保留 Codex 会话实测配色，
浅色工作区为白色、深色为 `#181818`，深色输入框为独立的 `#363636`，用户消息为
`#E8F3FE` / `#173E76`。它只根据主题明暗切换，不参与自定义背景派生。

自定义背景以工作面为基础，向前景色混合派生外壳与导航：

- 浅色：外壳 8%，导航 4%。
- 深色：外壳 6.5%，导航 3%。

默认主题使用表中的精确色值，自定义主题使用派生规则。弹层、代码背景和边框同步变化。

## 交互与强调色

- 界面强调色 `Accent`：浅色 `#0169CC`，深色 `#8AB4F8`。
- 主要按钮文字和图标使用 `OnAccent`，跟随自定义主色调整。会话输入区的发送按钮使用
  `ConversationAppearance.onPrimary`（白色图标），不随界面强调色变化。
- 悬停 `HoverBackground`：浅色前景叠加 4%，深色叠加 6%；`HarnessActionControls` 使用。
- 按下 `PressedBackground`：浅色前景叠加 8%，深色叠加 10%。
- 导航项和工具栏卡片沿用本地实测效果：选中与悬停使用 `SurfaceSelected`，工具卡片悬停使用
  `ChromeFill`。
- 键盘焦点使用 `BorderFocused`，不与悬停状态合并。
- 会话强调色 `ConversationAppearance.primary`：浅色 `#3A83F7`，深色 `#2C67C5`；思考强度读数
  两种主题都使用 `#3A83F7`。用户消息背景使用固定实测色，正文使用各自的会话文字色。

## 字体与尺寸

界面、正文字体通过现有外观设置分别配置，代码使用独立字体和字号。
聊天正文读取 `LocalHarnessContentTextStyle`，代码读取 `LocalHarnessCodeTextStyle`。
正文默认 14sp / 22sp，辅助标签 13sp / 18sp。统一字号缩放由 `HarnessTheme` 提供。

布局继续使用 `HarnessTokens.Spacing`、`Corners`、`Control` 和 `Composer`。
本次保留已有布局尺寸，只统一外壳、导航、工作面和交互状态的角色命名与默认色值；
聊天页继续使用独立的会话实测配色。

## 验证

```powershell
.\kotlin.bat test -p jvm -m ui-foundation
.\kotlin.bat test -p jvm -m jvm-app --include-classes=im.hikaru.harness.desktop.ThemeLayersInteractionTest --include-classes=im.hikaru.harness.desktop.AppearanceInteractionTest
```

`ThemeLayersInteractionTest` 渲染真实分栏首页，检查图标栏、导航、聊天和工具区的背景，
覆盖深色、切换浅色、再切换自定义深色。截图保存到测试进程工作目录下的 `build/reports/theme-layers`。

恢复本地默认配色后尚未执行上述测试，只完成合并版本的编译验证；新的像素期望需在运行测试时
一并核对。原生窗口装饰不包含在截图检查中。
