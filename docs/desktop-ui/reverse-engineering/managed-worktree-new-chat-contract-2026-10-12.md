# Managed 工作树「在此工作树中新建聊天」：本批实施边界（2026-10-12）

## 1. 本批依据（ASAR 只读）

沿用 CB 批的 ASAR 只读证据（同一行组件覆盖所有工作树行），抄录见
`../verification/worktree-new-chat-2026-10-12/entry-cb1/logs/asar-newchat-strings.txt` 与
`.../asar-newchat-component.txt`；本批抄录见
`../verification/managed-worktree-new-chat-2026-10-12/entry-cc1/logs/asar-evidence-reused.txt`。

| 编号 | asset | 观察到的内容 |
| --- | --- | --- |
| E01 | `zh-CN-b05cfd55c534.js` | `settings.worktrees.row.newChat` =「在此工作树中新建聊天」、`.row.newChatDescription` =「使用相同文件和分支开始全新聊天」。 |
| E02 | `worktrees-settings-page-ad5cbb37d745.js` | 行组件 `En({worktree, nativeConversations, visibleConversations, hostId, isConversationsLoading})` 为**每个工作树行**渲染该动作：`g = nativeConversations.find(On)`、`On(e)=e.isPinned||!e.isSubagent`；按钮可见性 `w = (host.kind === 'local' && …)`，`disabled = f`（忙碌/加载态），`onClick = () => ht(s, {sourceConversationId: g?.id, workspaceRoot: n.dir})`。原版行不区分「本地 Git 工作树」与「Managed 登记」——该拆分是本仓自己的呈现。 |

## 2. 已观察到与未观察到的语义

- **已观察**：原版按工作树行统一渲染「在此工作树中新建聊天」，语义是**绑定工作目录后新建会话**（同文件同分支、不携带会话历史）。
- **未观察到**：原版是否对「登记/Managed」工作树有额外门控；`ht(...)` 之后的创建协议；原版同状态像素。

因此本批把 CB 的动作扩展到本仓的 **Managed 工作树卡片**，并沿用 CB 的「绑定目录 → 回 Home 新建」路径。

## 3. Harness 实施（本批生产改动）

| 层 | 改动 |
| --- | --- |
| `GitPages`（shared） | `ManagedWorktreeSection` 新增 `onNewChatInWorktree: (String) -> Unit` 参数，`WorktreesPage` 把自身的 `onNewChatInWorktree` 传入；在 Managed 卡片的「已知对话」块之后新增 `HarnessButton("在此工作树中新建聊天", { onNewChatInWorktree(entry.root) }, enabled = verified && pageReady && entry.worktree?.let { !it.bare && !it.prunable } == true, testTag = "managed-worktree-new-chat")`。 |

`HarnessApp` 无需再改：`WorktreesPage` 的 `onNewChatInWorktree`（CB 已接为 `pendingWorktreeChat = path; selectPage(PageId.Home)`）对 Managed 卡片同样生效。

### 3.1 未实现（明确不主张）

- **归属门控**：本批要求 `verified`（当前核验为 Ready 且非过期）且该行有可用 Git 条目（非 bare/prunable）；`Missing` 归属（无 Git 条目）或未核验/过期的行按钮禁用。原版是否有等价门控未观察到。
- **`sourceConversationId`**：与 CB 相同，本仓 `SessionSummary` 无 `isPinned`/`isSubagent`/`projectKind`，未携带源对话 id。
- **图标与说明文本**：未复刻按钮图标与行内 `newChatDescription` 说明行。
- 原版同尺寸像素与原生窗口证据。

## 4. 验收

见 `../verification/managed-worktree-new-chat-2026-10-12/entry-cc1/review.md`。各批证据独立，不累加为总体覆盖率。
