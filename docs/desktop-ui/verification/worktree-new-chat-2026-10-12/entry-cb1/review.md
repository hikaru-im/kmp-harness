# Batch CB：Worktrees「在此工作树中新建聊天」（2026-10-12）

## §1 本批目标

承接 BX 批（同一 Worktrees 行的 `settings.worktrees.row.conversations`「已知对话」）遗留的同族动作
`settings.worktrees.row.newChat`（「在此工作树中新建聊天」）。ASAR 只读证据（`logs/asar-newchat-strings.txt`
与 `logs/asar-newchat-component.txt`，来源 `/tmp/asar-new/webview/assets/` 的 `zh-CN-b05cfd55c534.js` 与
`worktrees-settings-page-ad5cbb37d745.js`）：

- 文案：`settings.worktrees.row.newChat` =「在此工作树中新建聊天」、`.row.newChatDescription` =
  「使用相同文件和分支开始全新聊天」、`.row.title` =「工作树」、`.row.delete` =「删除」、
  `.row.pinned` =「请先取消置顶此工作树中的聊天，再删除此工作树。」。
- 组件：行组件 `En({worktree, nativeConversations, visibleConversations, hostId, isConversationsLoading})`；
  `g = nativeConversations.find(On)`、`v = nativeConversations.some(Dn)`，其中 `On(e)=e.isPinned||!e.isSubagent`、
  `Dn(e)=e.isPinned`。
- 行为：按钮可见性 `w = (host.kind === 'local' && !(record?.projectKind === 'local' && mt(record.projectId)))`
  （仅本机 Host 且源对话非本机项目会话时可见），`disabled = f`（忙碌/加载态）；`onClick =
  () => ht(s, {sourceConversationId: g?.id, workspaceRoot: n.dir})` —— 以**同文件同分支**开启全新聊天，
  源对话取 `find(isPinned || !isSubagent)` 的 id，工作目录取该工作树目录，不携带会话历史。

本批落地「绑定工作树目录 → 在 Home 新建会话」这一条可观察、与本仓数据相称的路径。

## §2 生产改动

（2 个文件，均在 `apps/shared`）

| 文件 | 改动 |
| --- | --- |
| `src/im/hikaru/harness/client/git/GitPages.kt` | `WorktreesPage` 新增 `onNewChatInWorktree: (String) -> Unit` 参数；在**本地 Git 工作树卡片**内（`WorktreeConversations(...)` 之后、`HarnessButton("读取此工作树", ...)` 之前）新增 `HarnessButton("在此工作树中新建聊天", { onNewChatInWorktree(worktree.path) }, enabled = state.pageReady && !worktree.bare && !worktree.prunable, testTag = "git-worktree-new-chat")`。 |
| `src/im/hikaru/harness/client/app/HarnessApp.kt` | 新增 `pendingWorktreeChat`（`remember(conversationHost?.hostId, conversationHost?.identity, transport)`）；`WorktreesPage` 的 `onNewChatInWorktree` 接为 `{ path -> pendingWorktreeChat = path; selectPage(PageId.Home) }`；`LocalProjectPicker.onSelect` 与「取消」按钮清除 `pendingWorktreeChat`；Home 组合器在 `page == PageId.Home` 时渲染待定提示（`testTag=local-worktree-chat-pending`，「将在该工作树目录新建聊天：<dir>」）+「取消」（`testTag=local-worktree-chat-cancel`）；`LiveConversationComposer` 提交时若 `pendingWorktreeChat != null` 则 `prepareCreation = { CreateSessionOptions(cwd = worktreeDirectory) }`、`onSessionCreated = { pendingWorktreeChat = null; true }`，不走项目关联分支。 |

未新增读取、未新增仓库写操作：点击按钮只登记一个待用目录并导航 Home，真正创建会话发生在用户在 Home 发送第一条消息时。

## §3 测试改动

（2 个文件，均在 `apps/jvm-app/test`）

- `im/hikaru/harness/desktop/WorktreePageInteractionTest.kt`：新增 `newChatBindsThisWorktreeDirectoryAndIsUnavailableForBareOrPrunable` —— 点击非 bare/prunable 卡片的 `git-worktree-new-chat` 以 `worktree.path` 触发回调；bare/prunable 卡片按钮 `assertIsNotEnabled`；断言不产生 `creations`/`switches` 副作用。
- `im/hikaru/harness/desktop/GitSettingsRouteInteractionTest.kt`：新增 `worktreeNewChatBindsDirectoryReturnsHomeWithoutCreatingASession` —— 在真实 `HarnessApp`（`HarnessDestination.Settings`）里进入 Git→Worktrees，点击按钮后回到 Home 并出现 `local-worktree-chat-pending`（含工作树目录），「取消」后消失；断言 `creations`/`switches` 为空。

## §4 验收（各批独立，不累加为总体覆盖率）

| 命令 | 结果 |
| --- | --- |
| `--build-dir=/tmp/harness-cb-targeted -p jvm -m shared`（全量） | 563 found / 563 passed（与 CA 持平；本批未改 shared 测试）（`logs/shared-full.txt`） |
| 同 `-m jvm-app --include-classes=…WorktreePageInteractionTest` | 9 / 9（+1 新增用例）（`logs/targeted-worktree-page.txt`） |
| 同 `-m jvm-app --include-classes=…GitSettingsRouteInteractionTest` | 8 / 8（+1 新增用例）（`logs/targeted-git-route.txt`） |
| 同 `-m jvm-app`（全量） | 703 found / 5 skipped / 698 started / 697 passed / **1 failed**（exit 1）（`logs/full-jvm-app.txt`） |
| 隔离重跑 `--include-classes=…PtyTerminalFontInteractionTest` | 3 / 3 passed（确认上一条为环境抖动，不是本批回归）（`logs/pty-isolate-rerun.txt`） |

全量 703 found 比 CA（701）多 2，正是本批新增的两条交互用例。唯一失败为历史 PTY 字体抖动
`PtyTerminalFontInteractionTest.savedFontChangeRefitsSameRunningPtyAtFixedBounds`（30s `ComposeTimeoutException`，
与 CA/BZ 同一条），隔离重跑通过。

## §5 截图产物核对与还原

- `docs/desktop-ui/captures/git-worktrees/`：全量运行按既有机制重写 **432** 个文件；这是**实质性**变化——
  几何 JSON 中新增了 `git-worktree-new-chat` 标记节点（全量运行后含该标签的文件从 0 → 220）。保留重生成版本。
- `docs/desktop-ui/captures/plugins-page-2026-10-08/`（64）与 `docs/desktop-ui/captures/scheduled-execution/`（20）：
  全量运行重写，已按先例从批次前快照（`/tmp/cb-captures-before-VJToHj`）还原并逐文件校验。前者经 diff 确认差异只有
  `id`/`rootId`/`captureRootId` 这类每次运行都会变的 Compose 节点 id（43 个差异叶全部为 id 命名，图片 `sha256`
  字段不变）；后者还原后组合 sha `ac8ebb54…`，与既有冻结基线一致。详见 `logs/captures-restore.txt`。

## §6 边界（明确不主张）

- **Managed 登记卡片**：本批只在本地 Git 工作树卡片上加动作，Managed 卡片未加（范围收窄）。
- **可见性门控**：原版要求本机 Host 且源对话非本机项目会话；本仓以 `pageReady && !bare && !prunable` 为可用性
  门控，未复刻 `projectKind` 判断。
- **`sourceConversationId`**：本仓 `SessionSummary` 无 `isPinned`/`isSubagent`/`projectKind` 字段，未从既有对话挑选
  「置顶或首个非子代理」作为源，也不携带源对话 id；「同文件同分支」由工作树目录本身表达。
- **图标与说明文本**：未复刻按钮图标资源与行内 `newChatDescription` 说明行，只保留可操作按钮文案。
- 原版同尺寸像素与原生窗口证据（未做）。
- 定时任务「应用退出后后台执行」仍属未做（沿用既有边界）。

## §7 复现

```
./kotlin test --build-dir=/tmp/harness-cb-targeted -p jvm -m shared
./kotlin test --build-dir=/tmp/harness-cb-targeted -p jvm -m jvm-app --include-classes='im.hikaru.harness.desktop.WorktreePageInteractionTest'
./kotlin test --build-dir=/tmp/harness-cb-targeted -p jvm -m jvm-app --include-classes='im.hikaru.harness.desktop.GitSettingsRouteInteractionTest'
./kotlin test --build-dir=/tmp/harness-cb-targeted -p jvm -m jvm-app
```

注意：`--include-classes` 只接受**单个**全限定名或含包路径的 glob；用逗号并列多个类会「no tests were discovered」。
