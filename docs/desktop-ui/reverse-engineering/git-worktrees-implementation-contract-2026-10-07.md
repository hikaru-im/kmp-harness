# Git / Worktrees / 本地环境实施契约（2026-10-07）

> 2026-10-08 状态校正：正文保留实施前契约。当前 DesktopGitBackend 已实现真实本地 discover/status/branches/worktrees/diff；GitWorktreeCreator、GitBranchSwitcher 与 GitCommitter 已接入创建、已有分支切换和仅提交已有暂存变更。M1 已实现独立 managed 登记、owner/Host/home 隔离与真实 Git 归属核验。历史批次分别为创建52/52、分支切换72/72、managed M1 92/92、提交C1 15/15，详见 `../replication-plan-2026-10-07.md`，这些数量不累计为本次页面验收结果。持久会话目录、pins/archive、完整移除保护与本地环境脚本仍缺，remove capability 尚未开放。正文的“无Git服务/无commit/无managed inventory”均不是当前状态。用户排除的应用快照不进入后续实现范围，也不作为保留功能的必做依赖。

## 1. 范围与当前证据基线

只覆盖本地 Git 分支、变更/diff、managed worktree 创建管理和本地环境。排除云环境、PR/远程发布、自动审查。本次仅文档分析，没有生产实现、构建或测试。

当前 `/Applications/ChatGPT.app/Contents/Resources/app.asar`：**546868793 bytes**，SHA256 **22466838293554644d2dcb52aa128b389c713bed734900cd926bed76de47aa71**。Header words `[4,5779180,5779176,5779170]`，payload base **5779188**。证据直接读取当前archive，不使用工作区旧解包文件。

对应 evidence JSON 保留archive/asset SHA、asset payload/absolute offset、每个anchor/snippet的UTF-8 byte range、snippet SHA、原文及当前Kotlin源码hash/行片段/补充搜索输出。所有range从0开始、end exclusive；G/W/P/E/D编号对应findings。区分原版已观察行为、当前缺口与拟实施接口。布局来自源码声明，未实测GUI。

## 2. 当前Harness缺口

已先使用CodeGraph查 `SettingsNavigatorModel / SettingsSection / Connection / LocalConnection / SessionApi / GitRepository / WorktreeService / LocalEnvironmentRepository`，再限定apps/modules/contracts的Kotlin/Java/KTS核查，证据JSON收录摘要与实际搜索输出。

| 当前来源 | 已有 | 缺口 |
| --- | --- | --- |
| SettingsNavigatorModel / NavigationState | git/worktrees导航及SettingsSection | 真实Git/worktree服务与功能接线 |
| Connection | 仅host/session；session list/create/history/prompt/cancel | Git/worktree/environment协议 |
| LocalConnection / SessionApiGateway | 真实host/session Gateway | status/diff/branch/worktree backend |
| settings基础设施 | 可复用偏好存储模式 | managed inventory、环境文件CAS、脚本runner |

在所查当前范围没有实现上述服务。导航/图标/空按钮不是功能完成；普通Git worktree list不证明managed owner/pins/snapshot。缺capability显示Unavailable/Unsupported，不能假空列表、fake branch/diff、空success或成功toast。

## 3. 组件树、路由与动作

```text
AppScope(identity + verifiedHost + connectionGeneration)
├─ Settings/Git → diff source, branch prefix, commit instructions/save state
├─ Settings/Worktrees → host, managed root, refresh, loading/error/empty
│  └─ repository groups → worktree path/branch/chats, open/new chat, guarded delete
├─ Home/Thread utilities → current/base/recent/search branch picker
│  └─ create branch/worktree, environment selector, creation/attachment progress
├─ Changes/Diff → actual summary/files, unified/split, expand/collapse
└─ Local environments → project/inherited entries, default/OS view
   └─ editor → setup/overrides/cleanup/actions, read/parse/save/select status
```

原版prefix跳转 `/settings/git-settings`（G04），本地环境 `/settings/local-environments`（E06）；Worktrees `tab=environments`有feature gate并以host+location key重建（W13）。原版diff确切URL未知，不补造。Harness接现有 `PageId.Settings(SettingsSection.Git/Worktrees)`；typed Diff target含scope/repo/worktree/source/snapshot/文件。选择路径不隐式checkout。静态编译期接线，无JS/HMR/dynamic slots；不移植云重定向。

| 动作 | 原版实际数据链 | 实施要求 |
| --- | --- | --- |
| status | status-summary(cwd,includeUntrackedFiles)，query/live-query策略不同（G01/G11/G15） | staged/unstaged/untracked独立；失败不显示0伪装干净 |
| branches | 真实current/base/recent/search；recent默认limit100（G05） | 去重、loading/error/retry、detached/unborn明确 |
| checkout | git-checkout-branch → main → worker checkout-branch，真实cwd/host/signal（G02/G08/G09） | 有mutation capability才开放，复读refs/status；无隐式stash/reset/commit |
| create branch | git-create-branch → create-branch(branch,cwd,failIfExists,mode,startPoint)（G03/G07/G10） | 验证名/起点，只支持已实现本地模式 |
| init | guarded git-init-repo（G06） | 单独显式mutation，不自动初始化 |
| changes/diff | branch-diff-stats、review-summary、review-diff（G12–G14） | 真实patch，source/snapshot绑定，stale/error独立 |
| managed list | codex-worktrees(hostConfig,worktreesRoot)（W01/W08） | 与普通git worktree list分开 |
| managed create | worktree-create-managed → local service → create-worktree（W02/W04/W07） | actual roots/id/阶段，setup/attach与创建分开 |
| delete | pins/chats检查 → archive → local worker delete（W03/W05/W09–W12） | typed receipt+重扫；Unit不能推断成功 |
| environment save/select | localEnvironments.saveConfig(expectedRevision)后select（E01–E05/E08） | conflict与saved-but-selection-failed独立；save不等于run |

review-summary/review-diff只是本地Git数据方法名，不引入自动审查。原版计数 `max(stagedCount,unstagedCount)+(untrackedCount??0)`（G01）不是两类总和；Harness保留路径级overlap，error不转0。无commit时create-and-checkout禁用；不自动commit解除禁用。

## 4. 实际接口与已观察边界

- status真实执行含 `git status --renames --porcelain=v1 -z --untracked-files=no`，另有untracked读取策略（G15）；NUL、rename双路径、Unicode、空格/换行安全解析。找不到repository是repository_unavailable（G11）。
- diff stats字段cwd/baseBranch/hideWhitespace/includeUntrackedFiles；review-diff有snapshotGeneration/source/files/baseBranch/commitSha/hideWhitespace及stale-snapshot（G12–G14）。部分异常fallback仍形如success，Harness不复制错误遮蔽。
- create bridge传hostId/cwd/startingState/localEnvironmentConfigPath/streamId；service补name/upstreamRefreshMode/worktreesRoot/useWorkspaceDir，订阅真实progress并保护newborn root（W02/W04）。完整startingState/branch mode enum未知，不猜默认远程分支。
- create成功setup失败保留worktreeGitRoot/worktreeWorkspaceRoot并标failed/needsAttention（W06），不能退回未创建或隐式删除。
- delete worker receipt区分success/worktreeId和deletion-failed/unsupported-nested-repository；service返回archived/failed/unsupported，main丢弃receipt，settings可能错误toast成功（W03/W05/W09/W12）。实施须修正。
- linked chat真实thread/list分页+pins，重复cursor/缺pin service是error，不猜no pins。targeted delete重查pins、archive(cleanupWorktree:false)再delete，可部分成功（W10–W12）。新chat共享文件/branch，不复制历史。
- 环境TOML：version/name、setup默认及darwin/linux/win32 override、cleanup、actions(name/icon/command/platform)；draft action UUID非runtime数据。空platform override使用默认（E01/E07）；managed选择走Git config `codex.localEnvironmentConfigPath`（E03/E08）。

## 5. 拟实施类型化服务与app入口

以下是未来新增契约，不是当前endpoint；首轮只读discover/status/branch/diff/worktree list，mutation默认unavailable。

```kotlin
// 草拟接口，DTO需后续静态定义。
interface ReadOnlyGit {
    suspend fun discover(cwd: String): RepositoryDiscovery
    suspend fun status(scope: GitScope): GitStatusSnapshot
    suspend fun branches(scope: GitScope, query: String?): BranchSnapshot
    suspend fun diff(scope: GitScope, request: DiffRequest): DiffSnapshot
    suspend fun listWorktrees(scope: GitScope): List<GitWorktree>
}
interface ManagedWorktrees {
    suspend fun list(host: VerifiedLocalHost, root: String): ManagedInventory
    suspend fun create(request: CreateWorktreeRequest): CreateWorktreeReceipt
    fun progress(operationId: String): Flow<WorktreeProgress>
    suspend fun deletionPlan(id: ManagedWorktreeId): WorktreeDeletionPlan
    suspend fun delete(plan: WorktreeDeletionPlan): DeleteWorktreeReceipt
}
interface LocalEnvironments {
    suspend fun list(scope: GitScope): EnvironmentInventory
    suspend fun read(scope: GitScope, path: String): EnvironmentDocument
    suspend fun save(request: SaveEnvironmentRequest): EnvironmentSaveReceipt
    suspend fun select(scope: GitScope, path: String): EnvironmentSelectionReceipt
}
```

| 类型 | 必须保留 |
| --- | --- |
| GitScope | identity/hostId/generation/repoRoot/commonDir/worktreeRoot |
| Discovery | GitUnavailable/NotRepository/Unavailable/Found；branch/detached/unborn |
| Status | revision/head/index/working tree关系；path/oldPath/status/overlap/conflicts；error |
| BranchSnapshot | 实际ref/name/current/base/recent/remote/oid；unknown不伪填 |
| Diff request/snapshot | source/base/head/files/whitespace/revision；真实patch、binary/submodule/truncated/stale/error |
| GitWorktree vs ManagedInventory | actual path/head/branch/lock/prunable；owner/protection/chats另存 |
| Create receipt | Created/CreatedSetupFailed/CreatedAttachmentFailed/FailedBeforeCreation；actual roots/id |
| Delete plan/receipt | fingerprint/pins/owner/running/shared/primary/nested/snapshot；Deleted/Blocked/Unsupported/Failed/UnknownOutcome |
| Environment doc/save | path/raw/parsed/version/revision/继承/平台/read/parse错误；expectedRevision含初建竞争；Saved(newRevision)/Conflict/Failed |
| Capabilities | ReadOnlyGit、Git mutation、managed/archive/restore、environment read/save/run分别声明 |

Desktop adapter用真实process/file API，argv+cwd执行Git，禁止不受控shell拼接。保留exit/stderr/timeout/cancel，NUL解析路径。平台/WSL映射在adapter，scope/result携host，不能用本机cwd执行别host请求；不增加未实现后端行为。

Compose-free不可变 `StateFlow<GitWorktreesState>` controller注入operations/scope。app facade首轮入口 `bind(identity,verifiedHost,connectionGeneration,capabilities) / selectRepository(cwd) / selectWorktree(id/path) / refresh() / searchBranches(query) / selectDiff(request)`。后续按capability增加checkout/createBranch/createWorktree/deleteWorktree(plan)、selectEnvironment/updateEnvironmentDraft/saveEnvironment/runEnvironmentAction/cancel(operationId)。

状态分scope/selection、各query LoadState、mutation receipt、draft revision、capabilities；Loading/Ready(empty)/Error/Unsupported/Disconnected不同。并发必须：

1. 发布前校验identity+host+generation+repo/commonDir/worktree selection epoch+读取requestId，旧scope不泄露数据。
2. list/status/branch/diff独立Job/latest token；watcher refresh合并读取，不能取消在途create/checkout/delete/setup/save；同repo冲突mutation单飞。
3. select只选择和读。scope切换fence旧结果；cancel coroutine不代表已接受process回滚，unknown outcome重扫，不重发mutation。
4. summary/patch同revision，stale重取，最新失败不能被旧成功覆盖。
5. create保留actual id/roots；setup/attach失败独立重试，不隐式cleanup。
6. draft revision防晚到read/save覆盖；saved-but-selection-failed保留文件/newRevision，仅重试activation。
7. cancel失败保留error/活动状态；stream unsubscribe/finally不证明完成，progress capability与completion receipt分开。
8. 同identity+verified host断线可保留selection/draft，清旧实时status/diff禁mutation；新generation重读，换host/identity清数据，remote无能力明确unsupported。
9. git-repo-changed按host/root/commonDir失效相关读取（G16）；refs/topology/index/config/worktree分别处理。

## 6. 本地环境及删除服务边界

Caller确实传expectedRevision，save后select失败与conflict不同（E02）。**host原子CAS未证实**；legacy main bridge只是mkdir/writeFile且无expectedRevision（E05），不能混为同一保证。Harness开放save前须实现真实revision compare/save（含初建竞争），否则unavailable。外部修改/删除、权限、TOML错误独立；name必填，action name/command均空可省略，仅一项有值阻止save。

Save不执行setup。Setup属于创建后的project root执行；cleanup属于生命周期清理前；action是显式动作。完整terminal/process runner ABI、注入env及权限未确定，示例CODEX_WORKTREE_PATH不证明完整ABI。run开放前定义cwd/platform fallback/argv或shell/env/output/exit/cancel/operationId；无runner不可显示成功Run。

普通Git worktree list不证明managed owner/pins/primary/shared/运行会话/恢复快照。当前SessionApi没有pins/archive/lifecycle，先只读，不开放危险mutation。正式delete必须可信plan、复读保护和fingerprint、receipt+重扫；unknown pins/owner、primary/shared/locked/in-use/nested拒绝。archive部分成功/delete失败分开；原版force不是获准开启force的依据。snapshot/archive/restore和pin/owner/lifecycle齐备前auto-cleanup不可实际生效。

## 7. 布局、偏好

仅静态源码声明，未测GUI（W13–W16、D01–D04、P01–P03）：

- Worktrees按repo分组，行真实path/branch/chats，text-xs路径截断，busy/delete独立；loading/unknown/error/retry/empty区分，host变化重建绑定，列表独立滚动。
- 创建行status/reduced-motion；registering/completed显示Attaching worktree，其他创建阶段Creating worktree。这不是setup最终成功receipt。
- Home环境label：min-width0/flex，max-width spacing*56、gap spacing*1.5、padding spacing*2、flex-shrink0/overflow hidden。若spacing=.25rem且root16px为224/6/8px，是条件换算非实测。
- Diff满高、toolbar `py-2 ps-6 pe-2`、文件列overflow-y/panel padding；sticky header、line-info hunk separator、wrap false、deleted初始折叠；unified/split、expand/collapse。renderer行高非实测像素。
- Git偏好：git-branch-prefix默认codex/；git-review-mode full/last-turn-only，Disable Git-based diffs切后者；worktree root/commit instructions默认空。无真实last-turn snapshot就unavailable，不伪造diff。
- Worktree upstream refresh never/best-effort、auto-cleanup、keep-count原版默认15最低1。只读首轮不自动fetch，缺保护不cleanup。保存commit instructions不等于执行commit，PR/push/自动审查排除。

## 8. 实施顺序与验收

首轮Desktop真实discover/status/branch/diff/git worktree list → shared fenced controller → 静态surface。没有mutation capability保持禁用。之后才补managed inventory、环境CAS与lifecycle，真实receipt完整后开放mutation。后续构建/测试由父线程串行验证，本轮未运行。

| 必测场景 | 验收 |
| --- | --- |
| Git缺失/非仓库/权限/unborn/detached | 明确状态，无fake/空success |
| overlap/rename/Unicode/空格换行/untracked/conflict | NUL安全真实分类 |
| working/index/base diff、binary/submodule/大diff | actual patch/source，错误/截断明确 |
| A延迟后选B、最新失败后旧成功 | fencing，无覆盖/泄露 |
| generation/reconnect/host/identity/logout | 清旧实时数据，draft/selection遵循scope |
| 高频event refresh与在途action | 不取消mutation、不重复发、不卡action |
| stale snapshot | 重取，不混旧summary新patch |
| ordinary worktree vs managed | 来源明确，不猜owner/pins/可删 |
| create成功setup/attach失败、cancel失败 | actual roots/id保留，部分成功/unknown，无隐式delete |
| delete unsupported/failed/未知pins/archive部分成功 | 无假toast，实际receipt+guards |
| 外部环境修改/初建竞争/select失败/晚到read | CAS conflict、文件保留、防draft覆盖、分阶段重试 |
| 无runner/remote | disabled，无fake log/completed |
| loading/error/empty/长路径/键盘/layout | 可访问状态、截断可读、独立滚动 |

Fixture只允许测试adapter，生产均真实数据。

## 9. 未解决与交付

diff确切URL、完整branch mode/startingState及DTO，环境后端CAS、完整runner/env ABI，Harness managed snapshot/archive/restore/pins/ownership/lifecycle、GUI/pixel/runtime验证未解决。不得用猜测或空实现补齐。原版delete receipt丢失/diff异常success fallback需要修正。

**本文与evidence交付后立即释放写集；父线程可按真实只读Git服务开始实现。**
