# Projects / Recent 列表头带与行高对齐（2026-10-13）

> **后续变更（2026-10-13 晚）：**Rail 重构中 **Recent 页面已删除**，`LocalRecentSessionsPage.kt` 与
> `RecentReady` / `RecentNoResults` 两类截图不再对应生产代码；本目录保留为当时批次的记录。
> 同一批次新增的 `ProjectsRecentHeader`（列表页头部带）与 `HarnessRow` 单行带改动仍在生产代码中。

批次内容：把本机 Projects 与 Recent 页面从「标题 / 说明 / 页面动作」三段整宽纵排，改为一条
头部带——宽窗口动作与标题同排，窄窗口动作落到独立一行；并让列表行在长名称、长路径下保持
单行带，而不是被折行撑高。

## 证据来源

| 数值或规则 | 来源 |
| --- | --- |
| `项目` / `最近` 小节行是单个 30px 行盒，标题 ink 14px/21px，尾部动作与标题同排、相距 8px，行内动作 24×24 | Codex Desktop CDP 实测（`nav[aria="首页"]` 内的小节行） |
| 长文本按单行省略，不折行撑高行 | Codex 导航器列表行均为 31px 单行（同上实测） |
| 动作行与标题的间距取 `Spacing.Three`，标题到说明取 `Spacing.One` | `modules/ui-foundation` 的 `HarnessTokens` |
| 窄窗口（< 640dp）动作独占一行 | 参照导航器无此状态；取项目自身 640dp 紧凑阈值（`reference/shell-width-classes.md`） |
| 行最小高度 36、控件高度 36、搜索控件规格 | `HarnessTokens.Control`、`HarnessButton`、`HarnessSearchField` |

## 实测几何（density = 1，离屏页面）

头部带：

| 状态 | 头部带 | 动作行 | 搜索框 |
| --- | --- | --- | --- |
| ProjectsReady 1440×900 | x=352 y=16 1072×36 | x=658 y=16 176×36 | x=388 y=72 1024×20 |
| ProjectsReady 600×800 | x=352 y=16 232×76 | x=352 y=56 176×36 | x=388 y=112 184×20 |
| RecentReady 600×800 | x=352 y=16 232×76 | x=352 y=56 58×36 | x=388 y=112 184×20 |

列表行：

| 状态 | 普通行高 | 最长名称/最长路径行高 |
| --- | --- | --- |
| ProjectsReady 1440×900 | 38 | 42 |
| ProjectsReady 600×800 | 38 | 42 |

宽窗口动作右缘 768 落在头部带右缘 1424 之内；窄窗口动作左缘与头部带左缘一致（352），并落在
标题与说明下方。列表行高由 54–78（折行）收敛到 38–42（单行带）。

## 代码改动

- `ProjectsRecentPageComponents.kt`：新增页面私有 `ProjectsRecentHeader`（`ActionsBesideFrom = 640.dp` 阈值）。
- `LocalProjectsPage.kt` / `LocalRecentSessionsPage.kt`：改用头部带；列表行传
  `titleMaxLines = 1`、`descriptionMaxLines = 1`。
- `modules/ui-foundation/.../HarnessCollections.kt`：`HarnessRow` 新增可选
  `titleMaxLines` / `descriptionMaxLines`（默认 null，保持既有折行行为）。
- `ProjectsRecentInteractionTest.kt`：新增头部带合成断言与列表行单行带断言。

## 截图

`ProjectsReady`、`RecentReady`、`ProjectsNoResults`、`RecentNoResults` 各尺寸/主题的 PNG 与
同名 `.geometry.json`（含每个 tag 的 bounds、滚动量与 stateDescription）。

## 边界与残留差异

- 以上为离屏 Compose 页面截图与几何比对，属结构/几何对齐；仓库没有原版 Projects/Recent
  内容页同状态导出图，不声明像素级一致。
- 头部带标题仍用 20sp（`headlineSmall`）；参照仅提供导航器小节行的 14px/21px，内容页标题
  字号没有实测，未按 28sp 的设置页字号猜值。
- 最长名称行仍比普通行高 4dp（单行字形舍入），已在断言中作为有界差异记录。
- 未接入任何真实 Git、Worktree、Cron、插件、MCP、Hooks、Scripts、PTY、Subagent、Remote 或
  网络能力；页面数据来自测试 fixture。
