# Worktrees 聊天归档 M：Recent（live 会话列表）归档后失效接线（entry-m1）

日期 2026-10-10。承接 L（取消归档把聊天恢复成可运行会话）。L 之后取消归档确实会让聊天在"下一次 Recent 刷新"时重新出现，但 App 没有任何东西触发那次刷新：archiving/unarchiving 只写了 Worktrees 页的托管回执，ConversationController 持有的 live 会话列表保持上一次成功读取的内容，直到用户手动点"刷新"。本批把归档回执接到会话列表失效，让归档的聊天立即从 Recent 消失、取消归档的聊天立即回到 Recent。

## 写集（4 个文件）

| # | 文件 | 变更 |
| --- | --- | --- |
| 1 | apps/shared/src/im/hikaru/harness/client/conversation/ConversationController.kt | 新增公开 `invalidateSessions(retired: SessionId? = null)`：重读 live 列表；`retired` 仍等于当前选中会话时先取消选择 |
| 2 | apps/shared/src/im/hikaru/harness/client/app/HarnessApp.kt | 观察托管控制器 state 的 `archiveReceipt`，收到 `Applied` 时调用 `invalidateSessions`（归档传 retired=该聊天 id，取消归档不传） |
| 3 | apps/shared/test/im/hikaru/harness/client/conversation/ConversationControllerTest.kt | 新增 2 个契约用例（合计 33） |
| 4 | apps/jvm-app/test/im/hikaru/harness/desktop/WorktreeArchiveRecentSyncInteractionTest.kt（新增） | 真实 HarnessApp：从 Worktrees 页归档/取消归档，断言 Recent 无需手动刷新地移除/恢复该聊天 |

档案 SHA 见同目录 `files.sha256`。

## 行为

- **归档回执驱动列表失效。** HarnessApp 只订阅 `ManagedWorktreeController.state` 的 `archiveReceipt` 字段，收到 `Applied`（且属于当前 owner/仓库/root，控制器已按会话完整 header 指纹核验过）时调用 `conversation.invalidateSessions`。`refreshSessions` 本来只在连接绑定、显式刷新和创建会话时发生，因此此前归档不会被 Recent 看到。
- **不取消也不重投执行。** `invalidateSessions` 与既有 `refresh` 一样只读列表，不触碰发送/取消/重试路径；批次 I/K/L 的静止与恢复结论不受影响。
- **被归档的当前会话会被取消选择。** 传入 `retired` 时若它仍是 ConversationController 的当前选择，先 `select(null)` 再重读列表，避免任何界面继续呈现一个已经停止的会话。现有唯一归档入口在 Settings → Worktrees，进入设置时路由已经把选择清空，所以该分支在今天的界面上不会被命中；它由契约测试覆盖，并在将来出现"从 Recent 直接归档当前聊天"入口时直接生效。
- **未新增任何 Recent 过滤字段。** Recent 仍然列 live Agent；归档 chat 之所以消失，是因为触发了重读，而不是伪造了 archived 标志。

## 验收

| 命令 | 类 | 结果 |
| --- | --- | --- |
| kotlin test -m shared | ConversationControllerTest（含 externalArchiveInvalidationRelistsWithoutTouchingAnUnrelatedSelection、retiredArchiveDeselectsItsSelectionAndFencesStaleHistory） | 33/33 |
| kotlin test -m jvm-app | WorktreeArchiveRecentSyncInteractionTest | 1/1 |
| kotlin test -m jvm-app（9 类定向回归） | WorktreeArchiveRecentSync、GitSettingsRoute、WorktreePin、WorktreeSessionObservation、ManagedWorktree、ProjectsRecent、LiveHarnessConversation、DesktopWorktreeChatArchiveIntegration、GitWorktreesScreenshot | 59/59，0 失败 |
| kotlin test -m shared（4 类） | ConversationController、ManagedWorktreeChatArchiveController、ManagedWorktreePinController、ManagedWorktreeController | 47/47，0 失败 |
| kotlin build -m jvm-app -p jvm | 完整 jvm-app JVM 构建 | Build successful，exit 0 |

关键证据：真实 HarnessApp 在 Settings → Worktrees 触发归档后，`SessionSummary` 列表被重读（受控连接记录的 list 调用次数 +1），导航回 Recent 时 `local-recent-session-*` 卡片不存在、页面进入 `local-recent-empty`；取消归档后同一卡片恢复。固定装置里 worktree 观察始终保留该聊天的历史 header（与生产一致：归档只停止 Agent，不删持久历史），所以归档回执不会被随后的刷新丢弃，App 能稳定观察到 `Applied`。

**反向对照（证伪）**：临时把 HarnessApp 里的 `invalidateSessions` 调用换成空表达式，同一用例失败于 `waitUntil { listCalls > beforeArchive }`（列表从未被重读），随后恢复接线并重跑通过。证据见 `logs/harness-m1-negative-control.log`。

## 边界

- 本批只处理"归档/取消归档后 live 列表失效"。**归档后回退栈仍可回到该聊天的 Thread**（Route 选择对不在 live 列表中的 id 是权威的，这是既有契约），本批不改；Thread 面对已归档聊天应该显示什么（返回 Home、只读历史还是归档提示）需要单独决定。
- 若某次归档的静止未完成（durabilityWarning / busy），该聊天仍在 live 列表，Recent 仍会显示它；本批不伪造 archived 字段、不做 Recent 侧归档过滤。
- 独立归档管理页、删除与清理保护、跨进程与 Room/process/PTY 静止、原版同尺寸像素与原生窗口仍待。本批无截图与像素验收。
- 本批修改了 `apps/shared/src/im/hikaru/harness/client/app/HarnessApp.kt`，使 I 批次为该文件记录的 SHA 失效；I 的 quiesce 结论与其余文件不变。
- 各批证据独立保留，不累加作覆盖率；整体目标 active。

