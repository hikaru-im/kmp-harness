# 本地新建分支（create and checkout）：本批实施边界（2026-10-11）

## 1. 本批依据（ASAR 只读）

既有 Git/Worktrees 契约已记录「create branch」这一动作，但此前一直没有实现：`git-branch-switch-implementation-2026-10-07.md` 明确把「新分支」列入「不混入本批」，`git-worktrees-implementation-contract-2026-10-07` 的实施要求表也写着 `git-create-branch → create-branch(branch,cwd,failIfExists,mode,startPoint)（G03/G07/G10）`。

本批直接复用同一份固定证据快照 `reverse-engineering/git-worktrees-implementation-contract-2026-10-07.evidence.json`，不重新抓包：

| 编号 | asset | 观察到的内容 |
| --- | --- | --- |
| G03 | `webview/assets/git-branch-switcher-5f293be111c5.js` | `git-create-branch` 请求包装：`params:{...n,cwd:e.cwd,hostId:e.hostId}`，与 `git-checkout-branch` 同形；失败时 `throw Error('Expected a repository target')` |
| G07 | `.vite/build/main-C7cfj__D.js` | main 分发 `git-create-branch` → `requestGitWorker({method:'create-branch', params:{...r, hostConfig, operationSource}})`，按 `hostId` 取 `RequestAppServerClient` |
| G10 | `.vite/build/worker.js` | worker `case 'create-branch'`：`let{branch:t,cwd:n,failIfExists:r,mode:a,startPoint:s}=e.params` 后调用 Git manager |
| G01 | `webview/assets/git-branch-switcher-state-ff8df93405e4.js` | 分支切换入口状态：`createAndCheckoutDisabled = hasNoCommits \|\| disabled`，即「新建并切换」在没有提交时禁用 |

证据快照：archive `546868793` bytes / SHA-256 `22466838293554644d2dcb52aa128b389c713bed734900cd926bed76de47aa71`（见 evidence `archive` 段；该快照与当前安装包可能不同版本，本文件只声明该快照可观察到的字段）。

## 2. 已观察到的行为与未观察到的语义

- **已观察**：存在一个真实 mutation `create-branch`，参数含 `branch`、`cwd`、`failIfExists`、`mode`、`startPoint`；它属于「新建并切换」入口（G01 的 `createAndCheckoutDisabled`），因此在没有提交时不可用；失败必须报错，不能静默成功。
- **未观察**：`mode` 的取值集合与各自语义（本批没有针对 `mode` 的独立片段）；`failIfExists=false`（允许覆盖/复用）的分支如何在 UI 暴露；返回体字段；是否附带 `set-upstream`。这些不做猜测，也不实现。

所以本批只实现**一种**有完整证据的形态：在真实 HEAD 或真实已有本地分支上，创建一个新的本地分支并切换过去；`failIfExists` 固定为真（同名分支存在即拒绝）。不做 force、不做 reset、不做 stash、不做远端上游、不做 `mode` 的其他取值。

## 3. Harness 实施（本批生产改动）

| 层 | 改动 |
| --- | --- |
| `GitModels` | 新增 `GitCreateBranchRequest/Receipt/Result`、`GitBranchCreation`、`GitBranchCreator` 能力接口，以及共享的名称规则 `validLocalBranchName`；`GitState` 增加 `branchCreate*` 状态与 `branchCreateDisabledReason` / `canCreateBranch` |
| `GitController` | `setNewBranchName` / `setNewBranchStart` 只读草稿；`createBranch()` 与切换/创建/提交共用同一 `mutationBusy` admission 槽；回执校验、未知结果与 rescan 门 |
| `DesktopGitBackend` | `createBranch`：本机 Host/仓库 scope 复验、名称与起点校验、`check-ref-format`、两次一致快照、`git switch --no-guess --no-overwrite-ignore --no-recurse-submodules --create <name> <start>`、提交后复读并校验 HEAD/状态/refs/worktrees |
| `GitPages` | 新增「新建分支并切换」小节：名称输入、起点 picker（HEAD + 真实本地分支）、提交按钮、禁用原因与结果展示 |

### 3.1 请求与结果

请求绑定 `binding`、`root`、来源 `expectedBranch`/`expectedHeadOid`、`branch`、`startPointRef`（`HEAD` 或 `refs/heads/…`）与 `startPointOid`。回执只有在重新读取并确认以下全部成立时才发布：当前分支等于新 ref、HEAD 等于起点 oid、工作区干净、refs 中该新 ref 为 current 且 oid 等于起点 oid、工作树列表中仓库根目录对该 ref/oid 一致。

### 3.2 安全顺序

1. 本机 Host + 仓库 scope 复验；名称规则 + `git check-ref-format`；起点必须是 `HEAD` 或**已读取的**本地分支；同名本地分支存在即拒绝（`InvalidRequest`），不调用 `git switch`。
2. 两次一致快照（含 `verifyCreationRepository`）作为前置；前置与外部 Git 之间不保证原子锁。
3. 调用 `git switch --create` 之后，即使失败/取消也不能当作「没发生」：`dispatched` 之后的失败或取消返回 `UnknownOutcome`，要求重新读取，不自动重试、不回滚。
4. 未跟踪文件、已暂存、未暂存、冲突一律阻止创建（与既有「已有分支切换」同一干净前提，属 Harness 安全决定，不冒充原版行为）；不含 ignored 文件覆盖（`--no-overwrite-ignore`）。

### 3.3 未实现（明确不主张）

- `mode` 的其他取值、`failIfExists=false`、允许非干净工作树、自动 stash/commit/上游设置、detached HEAD 的新建入口。
- 远端 Host 的新建分支（本批仅 `LocalReadOnly` + 已验证本机 Host）。
- 原版同尺寸像素、原生窗口；本批只有生产页面/控制器/真实 Git 对象的证据。

## 4. 验收

见 `verification/git-create-branch-2026-10-11/entry-ap1/review.md`：shared 控制器、真实临时仓库 backend、生产页面交互三层证据，另有全量 `jvm-app` 结果与退出码。各批证据独立，不累加为总体覆盖率。
