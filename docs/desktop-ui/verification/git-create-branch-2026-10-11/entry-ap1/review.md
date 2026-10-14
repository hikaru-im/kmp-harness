# Git 新建分支并切换（create and checkout）验收 AP

日期：2026-10-11（Asia/Shanghai）。目标模型：deepSeek V4.1 Flash 直接实施，不委托子代理。证据批次名 `entry-ap1`。

## 1. 本批目标与依据

既有 Git/Worktrees 契约一直把「新分支」列为未做（`git-branch-switch-implementation-2026-10-07.md`：「新分支、提交、初始化、工作树删除、远端切换不混入本批」），而 ASAR 证据 G03/G07/G10 明确存在真实 mutation `git-create-branch → create-branch{branch,cwd,failIfExists,mode,startPoint}`，G01 的 `createAndCheckoutDisabled = hasNoCommits || disabled` 说明它就是「新建并切换」入口。

本批只在有完整证据的形态上接线：在真实 HEAD 或真实已有本地分支上创建并切换一个新本地分支，`failIfExists` 固定为真；不做 force/reset/stash/上游/mode 其他取值。完整边界见 `../../../reverse-engineering/git-create-branch-contract-2026-10-11.md`。

## 2. 写入集

生产（4）：

- `apps/shared/src/im/hikaru/harness/client/git/GitModels.kt`
- `apps/shared/src/im/hikaru/harness/client/git/GitController.kt`
- `apps/shared/src/im/hikaru/harness/client/git/GitPages.kt`
- `apps/jvm-app/src/im/hikaru/harness/desktop/git/DesktopGitBackend.kt`

测试（3，新增）：

- `apps/shared/test/im/hikaru/harness/client/git/GitBranchCreateControllerTest.kt`
- `apps/jvm-app/test/im/hikaru/harness/desktop/git/DesktopGitBranchCreateTest.kt`
- `apps/jvm-app/test/im/hikaru/harness/desktop/GitBranchCreateInteractionTest.kt`

文档（2，新增）：

- `docs/desktop-ui/reverse-engineering/git-create-branch-contract-2026-10-11.md`
- 本文件

未触碰既有的 `BranchCheckoutFixture`（`apps/jvm-app/test/.../git/DesktopGitBranchCheckoutTest.kt` 内的共享夹具）与页面 fixture adapter；新建分支相关断言使用它现成的真实临时仓库。

## 3. 实现要点

- `GitModels`：新增 `GitCreateBranchRequest/Receipt/Result`、`GitBranchCreation`、`GitBranchCreator`、共享名称规则 `validLocalBranchName`；`GitState` 增加 `branchCreateAvailable/newBranchName/newBranchStartRef/branchCreateBusy/branchCreation/branchCreateNeedsRescan` 与 `branchCreateDisabledReason`/`canCreateBranch`；切换、创建、提交、新建分支的禁用原因现在互相包含彼此的 busy/rescan 门。
- `GitController`：名称/起点只读草稿；`createBranch()` 与切换/创建/提交共用同一 `mutationBusy` admission 槽；回执校验要求新 ref 为 current 且 HEAD/状态/工作树一致；未知结果进入独立 `uncertainBranchCreates` rescan 门，需状态+分支+工作树三类新读取一致才解除（`worktrees` 单独成功不解除），不重放。
- `DesktopGitBackend`：本机 Host/仓库 scope 复验、名称+起点校验、`check-ref-format`、同名分支存在即 `InvalidRequest`、两次一致快照；`git switch --no-guess --no-overwrite-ignore --no-recurse-submodules --create <name> <start>`；调用后复读并校验 HEAD/状态/refs/worktrees，前后任一不一致即 `StaleSnapshot`；`dispatched` 之后的失败/取消一律 `UnknownOutcome`。
- `GitPages`：新增「新建分支并切换」小节（名称输入、HEAD+真实本地分支起点 picker、提交按钮、禁用原因与结果），并让已有分支 picker 在 `branchCreateNeedsRescan` 时禁用。

## 4. 问题

- 无生产/测试失败。全量 `jvm-app` 一次通过，未命中历史上偶发的 `PtyTerminalFontInteractionTest.savedFontChangeRefitsSameRunningPtyAtFixedBounds` 真实 PTY 就绪抖动。
- 全量运行重新生成了未跟踪的 `docs/desktop-ui/captures/scheduled-execution/`；按 AE/AJ 先例从批次前备份还原，`diff -r` 一致，组合 sha `ac8ebb5463e8ccf2f7306dabee0b044b8e4018c5`。
- 全量运行同时重新生成了未跟踪的 Git 页面截图（新小节出现属预期变化），未做像素对照。

## 5. 验收

所有命令使用 `--build-dir=/tmp/ap-build`。

| 套件 | 类/范围 | 结果 | 日志 |
| --- | --- | --- | --- |
| shared 控制器 | `im.hikaru.harness.client.git.*`（含新增 `GitBranchCreateControllerTest` 6 项） | **63/63**，0 失败/错误/跳过，exit 0 | `logs/shared-git-branch-create.log` |
| 真实临时仓库 backend + 生产页面 | `DesktopGitBranchCreateTest` + `GitBranchCreateInteractionTest` | **7/7**，exit 0 | `logs/desktop-branch-create.log` |
| Git 页面与截图回归（本会话中间轮） | `GitPageInteractionTest` + `GitWorktreesScreenshotTest` + `GitSettingsRouteInteractionTest` | **19/19**，exit 0 | 由下方全量覆盖 |
| desktop git 包（本会话中间轮，约 15 分钟） | `im.hikaru.harness.desktop.git.*` | **120/120**，exit 0 | 由下方全量覆盖 |
| 全量 jvm-app（本批门槛） | 全部 | **661 found / 5 skipped / 656 started / 656 passed / 0 failed**，exit 0 | `logs/full-jvm-app.txt` |

各套件是独立证据，不累加为总体覆盖率。

### 5.1 新增断言（关键）

- 控制器：草稿与起点只选择不执行；成功回执携带准确 `branch/startPointRef/startPointOid/expectedBranch/root/binding`；从已有本地分支起点创建；同名/非法名/脏/无提交/远端/无能力一律不派发；与切换/创建/提交共享 admission；错配回执与取消都是 Unknown 而非 Verified；跨导航的 Unknown 必须状态+分支+工作树三类新读取一致才 Rescanned 且不重放。
- backend：真实创建并切换（`symbolic-ref`/`rev-parse`/文件内容），源仓库证据逐字节不变；从 `refs/heads/feature` 起点创建；同名与过期起点在 `git switch` **之前** 拒绝（switch 调用计数 0）；脏工作树与缺失起点同样在 switch 之前拒绝；非法名/异 scope/远端绑定拒绝且未创建任何分支。
- 生产页面：输入名称→提交→`git-branch-create-success` 文本与 `git-current-branch` 显示新 ref，真实 Git HEAD 变化；同名导致按钮禁用并显示原因，且 `git branch` 未新增 `codex`。

## 6. 未验证边界

- 不主张原版同尺寸像素或原生窗口；本批无截图对照、无原生捕获（本机 Screen Recording 权限当前不可用，`screencapture` 返回 `could not create image from display`）。
- 未实现/未验证：`mode` 的其他取值与 `failIfExists=false`、允许非干净工作树、自动 stash/commit/上游、detached HEAD 入口、远端 Host 新建分支。
- 「干净工作区」前置是沿用既有「已有分支切换」的 Harness 安全决定，不冒充原版行为；回执与外部 Git 并发之间不保证原子锁。
- 各批证据独立，整体目标仍 active。
