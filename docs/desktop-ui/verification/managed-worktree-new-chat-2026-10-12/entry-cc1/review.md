# Batch CC：Managed 工作树「在此工作树中新建聊天」（2026-10-12）

## §1 本批目标

CB 批把 `settings.worktrees.row.newChat`（「在此工作树中新建聊天」）接到**本地 Git 工作树**卡片，并明确把
Managed 卡片列为范围收窄。原版行组件 `En({worktree, nativeConversations, visibleConversations, hostId,
isConversationsLoading})` 为**每个工作树行**渲染该动作，不区分「本地 Git 工作树 / Managed 登记」——该拆分是本仓
自己的呈现。本批把同一动作扩展到本仓的 **Managed 工作树卡片**，沿用以工作树目录绑定、回 Home 新建会话的 CB 路径。

ASAR 证据沿用 CB（`logs/asar-evidence-reused.txt`；原始抄录见
`../worktree-new-chat-2026-10-12/entry-cb1/logs/asar-newchat-strings.txt` 与 `.../asar-newchat-component.txt`）。

## §2 生产改动

（1 个文件，`apps/shared`）

| 文件 | 改动 |
| --- | --- |
| `src/im/hikaru/harness/client/git/GitPages.kt` | `ManagedWorktreeSection` 新增 `onNewChatInWorktree: (String) -> Unit` 参数，`WorktreesPage` 把自身的 `onNewChatInWorktree` 传入；在 Managed 卡片的「已知对话」块（`WorktreeConversations`）之后新增 `HarnessButton("在此工作树中新建聊天", { onNewChatInWorktree(entry.root) }, enabled = verified && pageReady && entry.worktree?.let { !it.bare && !it.prunable } == true, testTag = "managed-worktree-new-chat")`。 |

`HarnessApp` 无需再改：`WorktreesPage` 的 `onNewChatInWorktree`（CB 已接为 `pendingWorktreeChat = path;
selectPage(PageId.Home)`）对 Managed 卡片同样生效。

## §3 测试改动

（1 个文件，`apps/jvm-app/test`）

- `im/hikaru/harness/desktop/WorktreePageInteractionTest.kt`：新增
  `managedNewChatBindsVerifiedRootAndIsUnavailableForMissingOwnership` —— 归属为 `Managed`（有可用 Git 条目）的行
  点击 `managed-worktree-new-chat` 以 `entry.root` 触发回调；`Missing` 归属（无 Git 条目）的行按钮 `assertIsNotEnabled`；
  断言不产生 `creations`/`switches` 副作用。

## §4 验收（各批独立，不累加为总体覆盖率）

| 命令 | 结果 |
| --- | --- |
| `--build-dir=/tmp/harness-cb-targeted -p jvm -m shared`（全量） | 563 found / 563 passed（`logs/shared-full.txt`） |
| 同 `-m jvm-app --include-classes=…WorktreePageInteractionTest` | 10 / 10（+1 新增用例）（`logs/targeted-worktree-page.txt`） |
| 同 `-m jvm-app --include-classes=…ManagedWorktreeInteractionTest` | 3 / 3（`logs/targeted-managed.txt`） |
| 同 `-m jvm-app`（全量） | 704 found / 5 skipped / 699 started / 699 passed / **0 failed**（exit 0）（`logs/full-jvm-app.txt`） |

全量 704 found 比 CB（703）多 1，正是本批新增的 Managed 用例；本次全量无失败（CB 那条历史 PTY 字体抖动本次未复现）。

## §5 截图产物核对与还原

- `docs/desktop-ui/captures/git-worktrees/`：全量运行按既有机制重写 **504** 个文件；这是**实质性**变化——几何 JSON 中
  新增了 `managed-worktree-new-chat` 标记节点（全量运行后含该标签的文件从 0 → 188）。保留重生成版本。
- `docs/desktop-ui/captures/plugins-page-2026-10-08/`（64）与 `docs/desktop-ui/captures/scheduled-execution/`（20）：
  按先例从批次前快照（`/tmp/cc-captures-before-YC6B6h`）还原并逐文件校验。前者 diff 差异 43 个叶全部为 id 命名
  （`id`/`rootId`/`captureRootId`），后者还原后组合 sha `ac8ebb54…`，与既有冻结基线一致。详见 `logs/captures-restore.txt`。

## §6 边界（明确不主张）

- **归属门控**：本批要求 `verified`（当前核验 Ready 且非过期）且该行有可用 Git 条目（非 bare/prunable）；`Missing`
  归属或未核验/过期的行按钮禁用。原版是否有等价门控未观察到。
- **`sourceConversationId`**：与 CB 相同，本仓 `SessionSummary` 无 `isPinned`/`isSubagent`/`projectKind`，未携带源对话 id。
- **图标与说明文本**：未复刻按钮图标资源与行内 `newChatDescription` 说明行。
- 原版同尺寸像素与原生窗口证据（未做）。

## §7 复现

```
./kotlin test --build-dir=/tmp/harness-cb-targeted -p jvm -m shared
./kotlin test --build-dir=/tmp/harness-cb-targeted -p jvm -m jvm-app --include-classes='im.hikaru.harness.desktop.WorktreePageInteractionTest'
./kotlin test --build-dir=/tmp/harness-cb-targeted -p jvm -m jvm-app --include-classes='im.hikaru.harness.desktop.ManagedWorktreeInteractionTest'
./kotlin test --build-dir=/tmp/harness-cb-targeted -p jvm -m jvm-app
```
