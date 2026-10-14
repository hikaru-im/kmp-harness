# Worktrees「环境」小节三项设置：本批实施边界（2026-10-11）

## 1. 本批依据（ASAR 只读）

从本机安装包抽取的 webview bundle（`/tmp/asar-new/webview/assets`）只读分析，未改动任何原包。
原始片段见 `verification/worktree-env-settings-2026-10-11/entry-az1/logs/asar-evidence.txt`。

| 编号 | asset | 观察到的内容 |
| --- | --- | --- |
| N01 | `webview/assets/worktrees-settings-page-ad5cbb37d745.js` | 「环境」控件的状态与提交：上游刷新 `ke(L.upstreamRefreshMode)`（切换时 `Ze(i,L.upstreamRefreshMode, e)`，值域 `best-effort`/`never`）；自动删除 `d?.autoCleanupEnabled ?? L.autoCleanupEnabled.default`，提交 `Nt(i,n,L.autoCleanupEnabled,e)`；保留数量 `d?.keepCount ?? L.keepCount.default`，提交走 keepCount mutation（`Enter` 键触发保存）。三条提交各自带成功/失败 toast |
| N02 | 同上 | 关闭自动删除前先弹确认框：`autoCleanup.confirm.title/body/confirm/cancel`（确认=「禁用自动删除功能」，取消=「启用自动删除功能」） |
| N03 | `webview/assets/app-shared-8bd9d0399038.js` | 三个键与默认值/ schema：`upstreamRefreshMode`（key `worktree-upstream-refresh-mode`，enum `never`\|`best-effort`，默认 `never`）、`autoCleanupEnabled`（key `worktree-auto-cleanup-enabled`，boolean，默认 `true`）、`keepCount`（key `worktree-keep-count`，`int().min(1)`，默认 `15`） |
| N04 | 同上 | `upstreamRefreshMode` 默认值常量 `$pt=`never`` |
| N05 | 同上 | 对照 AY：`worktreeRoot` key `git-worktree-root`，默认空串 |
| N06 | `webview/assets/zh-CN-b05cfd55c534.js` | 小节标题「常规」「环境」；三行文案与「自动删除已停用」的替换描述、成功/失败 toast 文案 |

## 2. 已观察到与未观察到的语义

- **已观察**：三项都是**按 host 持久化**的设置；`autoCleanupEnabled` 是布尔开关（默认开），关闭前需二次确认；`keepCount` 是 `int().min(1)` 的数值（默认 15），自动删除关闭时描述改为「自动删除已停用…」；`upstreamRefreshMode` 是 `never`/`best-effort` 二值开关（默认 `never`，开启即「创建前始终获取上游更新」）；每条提交各有成功/失败 toast。
- **未观察到**：`cleanWorktrees`/自动删除的**后端实现**（裁剪时机、是否先建快照、并发语义）；创建前「获取上游更新」的**具体 git 命令与失败处理**（是 `git fetch` 还是其它、`best-effort` 的失败是否吞掉）；`L.keepCount.default = 15` 之外是否有服务端覆盖；原版同状态像素。

因此本批只实现**可观察的那一层**：三个真实持久化的偏好（含默认值与 `min(1)` 校验）与「关闭自动删除先确认」的交互；**破坏性的自动裁剪**与**创建前的上游获取执行**属后端行为，本批不接线、也不主张。

## 3. Harness 实施（本批生产改动）

| 层 | 改动 |
| --- | --- |
| `settings/WorktreeSettings.kt`（shared） | 文档从「只存 root」扩展为三项；新增 `WorktreeUpstreamRefreshMode`（`@SerialName("never"/"best-effort")` + `wire`）、常量 `WorktreeAutoCleanupDefault=true`/`WorktreeKeepCountDefault=15`，`WorktreeSettings` 增 `autoCleanupEnabled`/`keepCount`/`upstreamRefreshMode`（带默认值，旧文档仍可读）；纯函数 `keepCountValue`（trim 后整数且 ≥1）与 `keepCountError`（原版 `min(1)`） |
| `git/GitPages.kt`（shared） | WorktreesPage 参数 `onSaveWorktreeRoot: (String)->Unit` 改为 `onSaveWorktreeSettings: (WorktreeSettings)->Unit`；根目录小节改为 `settings.copy(root=…)`（不再整体替换，避免清空其它字段）；新增 `WorktreeEnvironmentSection`（「环境」小节）：自动删除开关（关闭前 `HarnessConfirmDialog` 确认）、保留数量输入（行内 `min(1)` 错误 + 保存按钮）、上游刷新开关，附加载/保存错误与「已保存工作树设置」提示 |
| `app/HarnessApp.kt`（shared） | 透传参数改名并改为把整份 `WorktreeSettings` 交给仓库 `save(next)` |

### 3.1 未实现（明确不主张）

- 自动删除旧工作树的实际裁剪、以及创建前「获取上游更新」的实际 git 执行（破坏性/网络行为，本批只持久化偏好）。
- 原版逐条 toast（本仓无全局 toast，改为小节内联提示）；逐条独立 mutation 的并发/失败重试语义。
- 原版 `CmdOrCtrl+S`/`Enter` 快捷键（本仓用显式保存按钮）。
- 原版同尺寸像素、原生窗口；本批只有生产模型/仓库/页面证据与 Harness 自渲染截图。

## 4. 验收

见 `verification/worktree-env-settings-2026-10-11/entry-az1/review.md`。各批证据独立，不累加为总体覆盖率。
