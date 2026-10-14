# Worktrees「在此工作树中新建聊天」：本批实施边界（2026-10-12）

## 1. 本批依据（ASAR 只读）

从本机安装包解包出的 webview bundle（`/tmp/asar-new/webview/assets/`）只读分析，未改动任何原包、未联网。抄录见
`../verification/worktree-new-chat-2026-10-12/entry-cb1/logs/asar-newchat-strings.txt` 与
`../verification/worktree-new-chat-2026-10-12/entry-cb1/logs/asar-newchat-component.txt`。

| 编号 | asset | 观察到的内容 |
| --- | --- | --- |
| E01 | `zh-CN-b05cfd55c534.js` | 文案：`settings.worktrees.row.newChat` =「在此工作树中新建聊天」、`.row.newChatDescription` =「使用相同文件和分支开始全新聊天」、`.row.title` =「工作树」、`.row.delete` =「删除」、`.row.pinned` =「请先取消置顶此工作树中的聊天，再删除此工作树。」 |
| E02 | `worktrees-settings-page-ad5cbb37d745.js` | 行组件 `En({worktree, nativeConversations, visibleConversations, hostId, isConversationsLoading})`。`g = nativeConversations.find(On)`、`v = nativeConversations.some(Dn)`，其中 `On(e)=e.isPinned||!e.isSubagent`、`Dn(e)=e.isPinned`。 |
| E03 | 同上 | 按钮可见性 `w = (host.kind === 'local' && !(record?.projectKind === 'local' && mt(record.projectId)))`，其中 `record = conversations[g?.id]`；按钮 `disabled = f`（`f = re(vn, {hostId, worktree: n.dir})` 的忙碌/加载态）。按钮为工具栏尺寸、`color: secondary`、图标 + 「在此工作树中新建聊天」标签；`onClick = () => ht(s, {sourceConversationId: g?.id, workspaceRoot: n.dir})`。 |
| E04 | 同上 | 当 `w` 为真时，行内同时渲染 `settings.worktrees.row.newChatDescription`（「使用相同文件和分支开始全新聊天」）作为说明文本；行头为「工作树」标题 + 工作树目录。`Dn`/`On` 同族辅助函数也决定「删除」的禁用原因（`v`）与删除动作 `gn(s, {hostId, worktree})`。 |

## 2. 已观察到与未观察到的语义

- **已观察**：工作树行有一个「在此工作树中新建聊天」动作，**仅本机 Host** 且源对话不是本机项目会话时可见；忙碌/加载时禁用；点击时以**同文件同分支**（不携带会话历史）开启全新聊天，源对话取 `nativeConversations.find(e => e.isPinned || !e.isSubagent)` 的 id，工作目录取该工作树目录。原版语义是**绑定工作目录后新建会话**，不是复用既有会话。
- **未观察到**：`ht(s, {sourceConversationId, workspaceRoot})` 之后的实际会话创建协议与去重规则、`isConversationsLoading` 的触发时机、按钮图标 `Ke` 的具体资源、以及原版同状态的像素值。

因此本批只实现**可观察且与本仓数据相称的那一层**：在本地 Git 工作树卡片上提供该动作，点击后把该工作树目录登记为「下一次 Home 创建的 cwd」并导航 Home，用户在 Home 发送第一条消息时才真正创建会话。

## 3. Harness 实施（本批生产改动）

| 层 | 改动 |
| --- | --- |
| `GitPages`（shared） | `WorktreesPage` 新增 `onNewChatInWorktree: (String) -> Unit` 参数；在**本地 Git 工作树卡片**内（「已知对话」块之后、「读取此工作树」之前）新增按钮 `HarnessButton("在此工作树中新建聊天", …, enabled = state.pageReady && !bare && !prunable, testTag = "git-worktree-new-chat")`，点击时以 `worktree.path` 调用回调。 |
| `HarnessApp`（shared） | 新增 `pendingWorktreeChat` 状态；`WorktreesPage` 的 `onNewChatInWorktree` 接为 `{ path -> pendingWorktreeChat = path; selectPage(PageId.Home) }`；Home 组合器在 `page == PageId.Home` 时渲染待定提示（`testTag=local-worktree-chat-pending`）+「取消」（`testTag=local-worktree-chat-cancel`）；提交时若存在 `pendingWorktreeChat` 则用 `CreateSessionOptions(cwd = 该目录)` 创建会话（`onSessionCreated` 清除待定），不走项目关联分支；切换项目或取消会清除待定。 |

### 3.1 未实现（明确不主张）

- **Managed 登记卡片**：本批只在**本地 Git 工作树**卡片上加动作，Managed 卡片未加（范围收窄）。
- **可见性门控**：原版要求本机 Host 且源对话非本机项目会话；本仓用 `pageReady && !bare && !prunable` 作为可用性门控（`bare`/`prunable` 不是可用工作目录），未复刻 `projectKind` 判断。
- **`sourceConversationId`**：本仓 `SessionSummary` 无 `isPinned`/`isSubagent`/`projectKind` 字段，未从既有对话里挑「置顶或首个非子代理」作为源，也不携带源对话 id。
- **图标与说明文本**：未复刻按钮图标资源与行内 `newChatDescription` 说明行，只保留可操作按钮文案。
- **原版同尺寸像素与原生窗口证据**。

## 4. 验收

见 `../verification/worktree-new-chat-2026-10-12/entry-cb1/review.md`。各批证据独立，不累加为总体覆盖率。
