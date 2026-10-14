# 颜色 token 对照（2026-10-13）

范围：本项目的颜色 token（`HarnessTokens.Colors` / `HarnessLightColors` / `HarnessDarkColors`）与
Codex 的对应关系，以及新增 token 的规则。**颜色必须主题感知**：Codex 每个颜色都是明/暗双值
（`app-shared-*.css` 用 `var(--lightningcss-light,X) var(--lightningcss-dark,Y)` 定义），本项目
同样在 `HarnessLightColors` / `HarnessDarkColors` 各给一份。组件里不得写死十六进制色。

## 现行 token 对照

| 本项目 token | Light | Dark | Codex 来源 |
| --- | --- | --- | --- |
| `Canvas` | `#FFFFFF` | `#171717` | `--gray-fixed-0` / 深色画布 |
| `Surface` | `#F9F9F9` | `#202020` | `--gray-fixed-50` |
| `SurfaceSecondary` | `#F3F3F3` | `#1C1C1C` | `--gray-fixed-75`（Rail/导航器底） |
| `SurfaceElevated` | `#FFFFFF` | `#282828` | `--color-surface-elevated` = light `--gray-0` / dark `--gray-300` |
| `SurfaceSelected` | `#EDEDED` | `#303030` | `--gray-fixed-100` / `--gray-100` |
| `Border` | `#7F7F7F` α10 | `#3A3A3A` | `--color-border` = `--alpha-10` |
| `BorderSubtle` | `#7F7F7F` α~7 | `#2E2E2E` | `--color-border-subtle` = light `--alpha-05` / dark `--alpha-06` |
| `BorderFocused` | `#0169CC` | `#8AB4F8` | `focusBorder` / `--blue-500` |
| `Text` | `#0D0D0D` | `#F2F2F2` | `--gray-fixed-1000` / `--alpha-base` |
| `TextMuted` | `#5D5D5D` | `#A8A8A8` | 本项目取值（参考 tertiary 档） |
| `TextDisabled` | `#8F8F8F` | `#6F6F6F` | `--gray-fixed-400` |
| `Accent` | `#0169CC` | `#8AB4F8` | `--blue-500` |
| `AccentStrong` | `#004F99` | `#B8D1FF` | 本项目取值 |
| `Danger` / `Success` / `Warning` | `#E02E2A` / `#00A240` / `#E0AC00` | 见源码 | `--red-500` / `--green-500` / `--yellow-500` |
| `OnAccent` | `#FFFFFF` | `#10213A` | 本项目取值 |
| `InverseSurface` | `#1A1C1F` | `#FCFCFC` | `--color-background-primary-solid` = light `--gray-1000` / dark `--gray-975` |
| `OnInverse` | `#FFFFFF` | `#0D0D0D` | `--color-text-primary-solid` |
| `CodeBackground` | `#F9F9F9` | `#1A1A1A` | 编辑器/代码块底色 |
| `DiffAdded` / `DiffRemoved` (+Background) | 见源码 | 见源码 | diff 语义色 |

`InverseSurface` / `OnInverse` 是本轮新增：Rail 的 hover tip 是**反色胶囊**——浅色主题下深底白字，
深色主题下浅底深字（实测浅色下为 `rgb(26,28,31)` + 白字，圆角 20、内边距 5×12）。

## 新增 token 的规则

1. 先在参照实测（CDP 或 `app-shared-*.css`）拿到**明暗两个值**，不要只量当前主题。
2. 用语义名加入 `HarnessColors` 并在两个面板各给一份；不要按页面/组件命名。
3. 组件通过 `HarnessTokens.Colors.X` 读取；**禁止**在业务代码里写 `Color(0x…)`。
   当前仅存的硬编码是合理例外：终端 ANSI 调色板（`PtyTerminalGrid`）、外观页预览色块
   （`SettingsAppearancePage`）、用户自定义色解析（`AppearanceThemeAdapter`）、
   `HarnessThemeCustomization` 的 accent 亮度判断。

## 未断言

- `TextMuted` / `AccentStrong` / `OnAccent` 是本项目取值，未与 Codex 某个具名 token 一一对应。
- 深色主题下多数值是本项目按「与浅色对应关系」给出，未逐项在本机深色 Codex 里实测；
  需要严格对齐时应切到深色再测一轮。
