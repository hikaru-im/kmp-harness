# BX：Worktrees「已知对话」只读列表（2026-10-12）

## §1 目标与依据

目标：Worktrees 页向 Codex 的 `settings.worktrees.row.conversations`（「已知对话」）收敛一处可见结构——
每个工作树行内列出**与该工作树目录关联的既有对话**，点击打开既有历史。只做只读投影，不新建会话、不写仓库、
不新增读取。

依据（`logs/asar-worktree-conversations-evidence.txt`，来源 `/tmp/asar-new/webview/assets/` 的
`worktrees-settings-page-ad5cbb37d745.js` 与 `zh-CN-b05cfd55c534.js`，只读、未联网）：

- 文案：`settings.worktrees.row.conversations` =「已知对话」、`.conversations.empty` =「未找到关联到此工作树的对话」、
  `.conversations.loading` =「正在加载对话…」、`settings.worktrees.conversation.untitled` =「无标题对话」。
- 语义：该 key 的英文自述为「Label for conversations linked by execution directory or already loaded
  attachments; other attachment owners may be missing」——口径是**执行目录或已加载附件**的关联，而不是「该工作树
  创建出来的对话」。
- 结构：`div.flex.flex-col.gap-1` = 标签 + 三态（加载 spinnner / 空态文本 / 列表）；列表项是整宽 `button`
  （`rounded-lg px-row-x py-row-y text-start text-sm`，hover 背景），内部 `span.truncate{ title 或 无标题对话 }`，
  点击以该工作树的 `hostId` 打开对话。

## §2 写入集合

生产（3 个文件）：

- `apps/shared/src/im/hikaru/harness/client/git/GitModels.kt`：新增纯函数 `worktreeConversations(root, sessions)`。
- `apps/shared/src/im/hikaru/harness/client/git/GitPages.kt`：`WorktreesPage` 新增 `sessions` / `onOpenSession`
  参数；新增私有 `WorktreeConversations(root, sessions, onOpenSession)`，并同时接进 Git 工作树卡片与 Managed 登记卡片。
- `apps/shared/src/im/hikaru/harness/client/app/HarnessApp.kt`：把当前 Host 的 `liveSessions` 与既有
  `onOpenSession`（选择 `PageId.Thread`）传入 `WorktreesPage`。

测试（2 个文件）：

- `apps/shared/test/im/hikaru/harness/client/git/WorktreeConversationsTest.kt`（新增）：精确匹配、尾斜杠/空根、
  排序三条纯函数用例。
- `apps/jvm-app/test/im/hikaru/harness/desktop/WorktreePageInteractionTest.kt`（新增 1 例 `knownConversationsListOnlyThatWorktreeAndOpenItsHistory`）：
  只列出 cwd 等于该工作树目录的会话、无匹配时保留空态文案、点击打开该会话。

文档：`docs/desktop-ui/reverse-engineering/worktree-known-conversations-contract-2026-10-12.md`；本目录
`review.md`、`logs/`、`files.sha256`。

## §3 实现

- **只读匹配**：`worktreeConversations` 把根路径与每条 `SessionSummary.cwd` 都做 `trimEnd('/')` 后精确比较，
  按 `createdAt` 降序再按会话 id 升序；空根（含 `"/"`）返回空列表。因此嵌套目录（`<root>/subdir`）与无关目录的会话
  都不会被归到工作树名下。
- **呈现**：`WorktreeConversations` 渲染「已知对话」标签（`testTag=worktree-conversations-<dir>`）、空态
  （`worktree-conversations-empty-<dir>`）与逐条 `HarnessRow`（`worktree-conversation-<id>`）；列表项用会话 id 起头、
  cwd 作为副标题，点击调用 `onOpenSession`。
- **接线**：`HarnessApp` 传入既有 `liveSessions` 与 `selectPage(PageId.Thread(id))`，与 Recent 页打开会话的路径一致；
  没有新增服务、没有新增仓库写操作。

## §4 验证

| 命令 | 结果 |
| --- | --- |
| `./kotlin --build-output=/tmp/bx-jvm-full test -m shared` | 561 found / 561 passed（上批 558 → +3，新增的 `WorktreeConversationsTest` 三条） |
| `./kotlin --build-output=/tmp/bx-targeted test -m jvm-app --include-classes=…WorktreePageInteractionTest` | 8 / 8（+1 新增用例） |
| `./kotlin … test -m jvm-app --include-classes=…WorktreePageInteractionTest,…HarnessAppThreadPanelsShellTest,…SettingsFormFrameInteractionTest,…GitWorktreesScreenshotTest` 等 13 个类 | 41 / 41 |
| `./kotlin --build-output=/tmp/bx-jvm-full test -m jvm-app`（全量） | 699 found / 5 skipped / 694 started / 693 passed / **1 failed**（仅 `PtyTerminalPageInteractionTest.focusedTypingPasteKeysAndResizeUseRealController`，30s `ComposeTimeoutException`，历史 PTY 抖动类） |
| 隔离重跑 `--include-classes=…PtyTerminalPageInteractionTest` | 7 / 7 passed（确认上一条为抖动，不是本批回归） |

全量 699 found 比上批（BW 698 found）多 1，正是本批新增的交互用例。日志见 `logs/`。

### 截图产物

- `docs/desktop-ui/captures/git-worktrees/`：全量运行按既有机制重写 416 个文件；这是**实质性**变化——
  几何 JSON 里新增了 `worktree-conversations-<dir>` 与 `worktree-conversations-empty-<dir>` 两个标记节点
  （例如 `creation-busy-dark-1440x900-focused-viewport.json` 中该标记从无到有，layoutBounds 为
  `left=365,top=1236,right=1059,bottom=1277`）。保留重生成版本。
- `docs/desktop-ui/captures/scheduled-execution/`（20 个）与 `docs/desktop-ui/captures/plugins-page-2026-10-08/`
  （64 个）：全量运行重写，已按先例从批次前字节还原，并逐文件 sha256 校验为**字节一致**。后者经 diff 确认差异只有
  `captureRootId`/`id`/`rootId` 这类每次运行都会变的 Compose 节点 id。

## §5 边界（明确不主张）

- **对话标题**：本仓 `SessionSummary` 无 title 字段，列表项以会话 id 起头、cwd 作副标题；原版显示 `e.title`
  并在缺失时回退「无标题对话」，本批未复刻。
- **加载态**：会话列表在本仓是已加载数据，没有独立加载过程，「正在加载对话…」不实现。
- **`isPinned` / `isSubagent` 过滤**：`SessionSummary` 无这两个字段，本批不做「隐藏子代理对话」「用置顶对话作为
  新建聊天源」的过滤；只覆盖「执行目录关联」这一半口径，不主张覆盖「已加载附件关联」。
- **同行其它动作**：「在此工作树中新建聊天」、删除按钮与 `settings.worktrees.row.pinned` 的禁用原因不在本批。
- 原版同尺寸像素与原生窗口证据不在本批（原生截图见 BY 批次）。

## §6 问题

- 全量 `jvm-app` 命中一次历史 PTY 抖动（`PtyTerminalPageInteractionTest`），隔离重跑 7/7 通过；本批未命中
  `DesktopGitCommitTest` 抖动。
- 本批生产改动让 Worktrees 页每个工作树卡片变高，因此 `captures/git-worktrees/` 的几何与 PNG 全部重写属预期；
  后续批次若再改该页，应按同样方式核对标记节点而不是直接接受字节变化。
