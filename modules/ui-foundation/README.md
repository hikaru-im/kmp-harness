# 桌面端主题规范

桌面端统一使用 `modules/ui-foundation` 的 `HarnessColors`、`HarnessTheme` 和 `HarnessTokens`。
页面通过 `HarnessTokens.Colors` 读取当前主题，不在组件中另建浅深色基础配色。

## 背景层级

| 语义角色 | 区域 | 浅色 | 深色 |
| --- | --- | --- | --- |
| `ShellBackground` | 窗口外壳、图标栏 | `#ECEDEF` | `#26282C` |
| `NavigationBackground` | 项目、会话、设置导航 | `#F4F5F6` | `#202124` |
| `WorkspaceBackground` | 聊天、工具页、上下文面板 | `#FFFFFF` | `#191A1C` |
| `OverlayBackground` | 菜单、弹层 | `#FFFFFF` | `#2B2D31` |

深色主题从左到右逐渐加深。聊天和工具页使用相同的不透明工作面；新标签页的工具栏、页面背景保持一致。
输入框融入工作面，以边框区分。浮层用独立表面与阴影表达层级。
客户端窗格标题随工作面取色；系统窗口标题栏保留外壳色。
标题栏中的导航框架保持透明，使用 `HarnessTheme(drawBackground = false)` 仅提供主题配置，
由父级标题栏绘制背景，按钮在交互时叠加状态色。

`AppBackground`、`NavigatorSurface`、`Canvas`、`SurfaceElevated` 是上述角色的兼容存储字段，
新增区域引用应使用语义名称。`SurfaceCanvas` 与 `Canvas` 保持一致，避免工具页再出现一档背景。

自定义背景以工作面为基础，向前景色混合派生外壳与导航：

- 浅色：外壳 8%，导航 4%。
- 深色：外壳 6.5%，导航 3%。

默认主题使用表中的精确色值，自定义主题使用派生规则。弹层、代码背景、边框和聊天表面同步变化。

## 交互与强调色

- 强调色 `Accent`：浅色 `#2563EB`，深色 `#8AB4F8`。
- 主要按钮文字和图标使用 `OnAccent`，跟随自定义主色调整。
- 悬停 `HoverBackground`：浅色前景叠加 4%，深色叠加 6%。
- 按下 `PressedBackground`：浅色前景叠加 8%，深色叠加 10%。
- 选中使用 `SurfaceSelected`；自定义主色时，选中色同步派生。
- 键盘焦点使用 `BorderFocused`，不与悬停状态合并。
- 用户消息背景由工作面和强调色混合得到，正文继续使用主题文字色。

## 字体与尺寸

界面、正文字体通过现有外观设置分别配置，代码使用独立字体和字号。
聊天正文读取 `LocalHarnessContentTextStyle`，代码读取 `LocalHarnessCodeTextStyle`。
正文默认 14sp / 22sp，辅助标签 13sp / 18sp。统一字号缩放由 `HarnessTheme` 提供。

布局继续使用 `HarnessTokens.Spacing`、`Corners`、`Control` 和 `Composer`。
本次保留已有布局尺寸，优先统一背景、聊天配色和主要交互状态。

## 验证

```powershell
.\kotlin.bat test -p jvm -m ui-foundation
.\kotlin.bat test -p jvm -m jvm-app --include-classes=im.hikaru.harness.desktop.ThemeLayersInteractionTest --include-classes=im.hikaru.harness.desktop.AppearanceInteractionTest
```

`ThemeLayersInteractionTest` 渲染真实分栏首页，检查图标栏、导航、聊天和工具区的背景，
覆盖深色、切换浅色、再切换自定义深色。截图保存到测试进程工作目录下的 `build/reports/theme-layers`。

2026-10-18 验证：主题基础 54 项、主题分层及键盘操作 2 项、外观设置 7 项通过，
已查看浅色、深色、自定义深色截图。原生窗口装饰未包含在截图检查中。

补充运行 `LiveConversationInteractionTest`：11 项通过、3 项失败。
失败断言仍查找旧提示文案（“描述你想构建的内容…”）、旧思考展开文案（“Reasoning · 展开”），
以及在外层容器上调用 `ScrollToIndex`。当前 HEAD 已使用“随心输入”“已完成思考”，
滚动语义由容器内的 `LazyColumn` 提供；本次未修改这些文案及节点结构，也未改写这组旧测试。
