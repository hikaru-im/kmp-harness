# Worktrees 聊天归档 J：页面归档按钮与回执（entry-j1）

日期 2026-10-10。承接 I（提交后 quiesce）。F–I 已经把归档链贯通到 controller/repository/bootstrap 并具备真实 Agent 门禁，但 Worktrees 页面仍只有只读的 `managed-archive` 保护标签，没有可点击入口。本批在**已观察会话**卡片上加归档状态与归档/取消归档按钮，并在 Managed 区渲染归档回执；归档按钮由此启用。

## 写集（3 个文件）

| # | 文件 | 变更 |
| --- | --- | --- |
| 1 | apps/shared/src/im/hikaru/harness/client/git/GitPages.kt | 每个已观察会话新增 `managed-session-archive-status` 与 `managed-session-archive-toggle`；归档与固定共用一行按钮以保持卡片高度；Managed 区新增 `managed-archive-receipt`；页脚边界文案由「删除、归档与清理不可用」改为「删除与清理不可用」 |
| 2 | apps/jvm-app/test/im/hikaru/harness/desktop/git/DesktopWorktreePinIntegrationTest.kt | WorktreePinFixture.repository 增加可选 `archiveStore`，并新增 `chatArchiveRequest` 辅助 |
| 3 | apps/jvm-app/test/im/hikaru/harness/desktop/WorktreePinInteractionTest.kt | 新增 `realMouseArchiveAndUnarchivePersistToFreshReader`（合计 4）；新增 `awaitArchive` 与 `PinPage.archiveSnapshot` |

档案 SHA 见同目录 `files.sha256`。

## 行为

- **每会话归档控件。** 归档可用性与固定同构：仅当会话观察为 Observed、观察 owner 与 inventory owner 一致、会话 `pinKey` 指纹有效、且 `archiveRead` 为 Available 时才展示归档状态并允许点击。状态文案三态：已归档 / 未归档 / 服务不可用或状态未知。按钮启用条件走既有 `state.canArchive(root, ChatArchiveKey)`，与 `canPin` 一样要求非 stale、owner/binding 无 identity、Git 只读且 header 指纹合法。
- **归档与固定共享一行按钮。** 原因是观察批次冻结的几何证据：`WorktreeSessionObservationInteractionTest` 要求「父会话标记」与「观察边界」在同一视口内同时可见，卡片每多一行都会把该跨度撑出列表视口（实测卡片高度 +128 时 640×800 边界被裁掉）。把两个按钮并到一行后每会话只多一行状态文本，跨度仍在可对齐范围内，测试恢复通过。
- **归档回执。** 仅当回执 owner/repository 与当前 inventory 一致、root 匹配、会话观察为 Observed 且归档键仍能对应到该会话时，才渲染 `managed-archive-receipt`。Applied 且带 durability warning 时优先显示 warning；Rejected/Unknown 显示原因。归档成功后的显示状态来自磁盘刷新，不本地翻转。

## 验收

| 类 | 方法 | 结果 |
| --- | --- | --- |
| WorktreePinInteractionTest | 含 realMouseArchiveAndUnarchivePersistToFreshReader：真实鼠标点击归档后 fresh reader 读到的 archived 含该键、状态变为「聊天已归档」、按钮变为「取消归档聊天」，再点击取消归档 revision=2 且 archived 清空、状态回到「此聊天未设置归档」，刷新后仍为空；且 registry/Room/Git/ live session 逐字节不变 | 4/4 |
| WorktreeSessionObservationInteractionTest | capturesFourRealCatalogLayoutsWithWheelReachableTailAndCompleteEndMarker 等 4 类（4 种尺寸/主题两阶段几何） | 4/4 |
| ManagedWorktreeInteractionTest | 3 类（含 managed-archive 保护标签仍为「归档服务不可用。」） | 3/3 |
| DesktopWorktreeChatArchiveIntegrationTest | 10 类（含 Agent 支撑归档与在途 Busy） | 10/10 |
| DesktopWorktreePinIntegrationTest | 6 类 | 6/6 |
| DesktopManagedWorktreeIntegrationTest | 8 类 | 8/8 |

完整 jvm-app JVM build：exit 0（Build successful）。日志见 `logs/`。

## 边界

本批只启用 Worktrees 页面的归档按钮与回执，Recent/当前聊天同步未接线。归档只提交元数据并在同一 Runtime 内 quiesce Agent；独立 JSON 的 CAS 不证明另一进程 live Agent 已静止，Room/process/PTY 静止与显式静止恢复（0 模型请求）仍待。按钮位置变动使观察批次（pin-r2/observation-captures）与固定批次捕获的几何工件对当前页面而言已过期，本批未重新生成这些图片，仅以交互与几何断言通过为准；无原生窗口或像素验收；不移除 `managed-archive` 只读保护标签，不增加删除或清理。各批证据独立，整体目标 active。
