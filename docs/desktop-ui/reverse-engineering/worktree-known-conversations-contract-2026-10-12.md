# Worktrees「已知对话」：本批实施边界（2026-10-12）

## 1. 本批依据（ASAR 只读）

从本机安装包解包出的 webview bundle（`/tmp/asar-new/webview/assets/`）只读分析，未改动任何原包、未联网。抄录见
`../verification/worktree-known-conversations-2026-10-12/entry-bx1/logs/asar-worktree-conversations-evidence.txt`。

| 编号 | asset | 观察到的内容 |
| --- | --- | --- |
| E01 | `zh-CN-b05cfd55c534.js` | 文案：`settings.worktrees.row.conversations` =「已知对话」、`.conversations.empty` =「未找到关联到此工作树的对话」、`.conversations.loading` =「正在加载对话…」、`settings.worktrees.conversation.untitled` =「无标题对话」、`.row.title` =「工作树」、`.row.newChat` =「在此工作树中新建聊天」、`.row.newChatDescription` =「使用相同文件和分支开始全新聊天」、`.row.pinned` =「请先取消置顶此工作树中的聊天，再删除此工作树。」 |
| E02 | `worktrees-settings-page-ad5cbb37d745.js` | `settings.worktrees.row.conversations` 的自述语义：`Known conversations` / description「Label for conversations linked by execution directory or already loaded attachments; other attachment owners may be missing」——已知对话 = 由**执行目录**或**已加载附件**关联的对话，其他附件归属方可能缺失。 |
| E03 | 同上 | 「已知对话」区块是 `div.flex.flex-col.gap-1`，依次为：`div.text-xs.text-secondary` 标签；加载态 `div.flex.items-center.gap-2.text-xs.text-secondary{spinner, 正在加载对话…}`；空态 `div.text-xs.text-secondary{未找到关联到此工作树的对话}`；列表 `div.flex.flex-col.gap-1`，每项为 `button.flex.w-full.items-center.justify-between.gap-2.rounded-lg.px-row-x.py-row-y.text-start.text-sm.text-default.hover:bg-primary-ghost-hover`，内部为 `span.truncate{ e.title 或 「无标题对话」 }`，`onClick` 用工作树的 `hostId` 打开该对话。 |
| E04 | 同上 | 行组件 `En({worktree, nativeConversations, visibleConversations, hostId, isConversationsLoading})` 同时持有原生与可见两组对话，**渲染 `visibleConversations`**；辅助函数 `Dn(e)=e.isPinned`、`On(e)=e.isPinned||!e.isSubagent`、`An(e,t)=t.length===0?e:e.filter(e=>!t.some(t=>mn(t,e.dir)))`、`kn(e)` 按 git dir 分组。行的「新建聊天」动作取 `nativeConversations.find(On)`，删除禁用原因取 `nativeConversations.some(Dn)`。 |

## 2. 已观察到与未观察到的语义

- **已观察**：工作树行内有一个「已知对话」块，标签 + 加载/空/列表三态；列表项显示对话**标题**（缺失时用「无标题对话」），点击后以该工作树的 `hostId` 打开该对话；「已知对话」的口径是**执行目录或已加载附件**的关联，而不是「该工作树创建出来的对话」；原版还会把 `isSubagent` 的对话排除在可操作集合外，并用 `isPinned` 决定删除按钮的禁用原因。
- **未观察到**：`visibleConversations` 的实际来源与去重规则（`An` 的第二个参数从何而来）、`isConversationsLoading` 的触发时机、列表项是否另有图标/二次行、以及原版同状态的像素值。

因此本批只实现**可观察且与本仓数据相称的那一层**：一个只读的「已知对话」块，列出当前 Host 已加载会话中 cwd 等于该工作树目录的条目，点击打开既有历史。

## 3. Harness 实施（本批生产改动）

| 层 | 改动 |
| --- | --- |
| `GitModels`（shared） | 新增纯函数 `worktreeConversations(root, sessions)`：把根路径与每条 `SessionSummary.cwd` 都做 `trimEnd('/')` 后比较，只保留 cwd **恰好等于**工作树目录的会话，按 `createdAt` 降序、再按会话 id 升序；空根（含 `"/"`）返回空列表 —— 不把无关会话归到工作树名下。 |
| `GitPages`（shared） | `WorktreesPage` 新增 `sessions` 与 `onOpenSession` 参数；新增私有 `WorktreeConversations(root, sessions, onOpenSession)`，渲染「已知对话」标签（`testTag=worktree-conversations-<dir>`）、空态（`worktree-conversations-empty-<dir>`）与逐条 `HarnessRow`（`worktree-conversation-<id>`，点击调用 `onOpenSession`）；该块同时出现在 Git 工作树卡片与 Managed 登记卡片内。 |
| `HarnessApp`（shared） | 把当前 Host 的 `liveSessions` 与既有 `onOpenSession`（`selectPage(PageId.Thread(id))`）传进 `WorktreesPage`；不新增读取、不创建会话、不写仓库。 |

### 3.1 未实现（明确不主张）

- **对话标题**：本仓 `SessionSummary` 无 title 字段，列表项以会话 id 起头并显示 cwd（与 Recent 页一致）；原版的 `e.title` 与「无标题对话」回退未复刻。
- **加载态**：会话列表在本仓是**已加载**数据，没有独立的对话加载过程，故「正在加载对话…」不实现。
- **`isPinned` / `isSubagent` 过滤**：`SessionSummary` 无这两个字段，本批不做「隐藏子代理对话」「用置顶对话作为新建聊天源」的过滤；列表是「执行目录关联」这一半口径，不主张覆盖「已加载附件关联」。
- **同行的其它动作**：「在此工作树中新建聊天」、删除按钮与 `settings.worktrees.row.pinned` 的禁用原因不在本批（本仓对应行是「读取此工作树」）。
- 原版同尺寸像素与原生窗口证据。

## 4. 验收

见 `../verification/worktree-known-conversations-2026-10-12/entry-bx1/review.md`。各批证据独立，不累加为总体覆盖率。
