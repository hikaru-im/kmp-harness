# 本机 Git Commit 最小实施契约

契约批次：2026-10-07。本文件先记录 commit 逆向分析，再记录 C1 的实际实施与验证结果。测试只在测试创建的临时仓库中执行，没有操作用户工作区、M1 数据或用户 Git 配置。

## 1. 结论与首批范围

实施前本仓库具有真实 Git 读取、创建 worktree、切换已有本地分支，**没有 commit/stage capability、controller action 或提交表单**。原版确有 commit 弹窗和 worker 处理器，不应以 Settings 的 commit instructions 设置代替提交能力；C1 已补上受限的本地 commit capability、controller action 与提交表单。

建议 C1：只在 verified 本机 Host 的已存在本地分支上，提交用户已经暂存的非空变更。用户填写非空消息，查看 Index diff，明确点击“提交暂存变更”。未暂存、未跟踪、ignored 文件允许保留；不能因为允许提交就自动处理这些文件。

首批拒绝 unborn、detached、冲突、merge/rebase/cherry-pick/revert/sequencer 等进行中操作。无 amend、empty commit、stage/unstage、新分支、push、remote、Agent 消息生成、自动归属 trailer 或快照服务。已有分支/worktree 功能继续使用原契约。

## 2. 原版真实证据与差异

沿用 `git-worktrees-implementation-contract-2026-10-07.evidence.json` 的 archive 基线：

- `/Applications/ChatGPT.app/Contents/Resources/app.asar`，546868793 bytes。
- 既有基线 SHA-256：`22466838293554644d2dcb52aa128b389c713bed734900cd926bed76de47aa71`。本次没有重新计算整个 archive 的 hash；实际从 archive 读取下列 assets，各自 hash 与既有 evidence 匹配。
- 下面范围为 asset 内 UTF-8 **byte** 半开区间；绝对位置为 archiveOffset 加该范围。不是 minified 文件行号或 GUI 测量。

| Asset | archiveOffset | 本次核对的 SHA-256 |
| --- | ---: | --- |
| `webview/assets/local-conversation-git-actions-5c9d336fcf82.js` | 278167625 | `9e34ed5b84466756fd0813e5a44d54ce23406b7b00665f435546265c94a6cb79` |
| `webview/assets/git-branch-switcher-5f293be111c5.js` | 198420023 | `dcb20d26f344057b960e4aa41139415670b9a55fdc33d4f51d59313788d4059d` |
| `webview/assets/git-action-new-branch-field-8a28eae34c56.js` | 198362757 | `5a02ff29bddad7b6ae3a53700ab559f7ca81b733822726203a160d01938fc9c7` |
| `.vite/build/worker.js` | 13250815 | `46b153e94466da15a8245dba0aecea0cc775b0b1688fe3b4cc9dd74d7805d7ad` |

| 锚点 | Asset / bytes | 实际证据 | C1 对应 |
| --- | --- | --- | --- |
| C01 | local-conversation-git-actions / [9901,10787)、[23355,24241) | `Commit` 菜单设置 action=`commit`；该状态挂载独立 modal，`enablePushActions:!0` | SettingsGitPage 内独立提交区/弹窗；不复制 Thread 壳层 |
| C02 | git-branch-switcher / [8100,11800) | textarea `rows:3`、`h-20`；消息可留空生成；Include unstaged changes checkbox；Commit / Commit and push / Push 三个 action；dialog `w-[420px] max-w-[92vw]` | 首批消息必填、仅 Commit；宽度可参考 420dp 并限制 viewport，不能声称像素一致 |
| C03 | git-action-new-branch-field / [17068,17684)、[28000,35000) | `git-action-include-unstaged-changes` 默认 `!0`；mutation 发送 `method:commit`、message、includeUnstaged、commitAttribution；另有 generate-commit-message 请求 | 原版包括暂存动作，C1 明确缩小为已有 index；生成/归属均后续独立能力 |
| C04 | worker / [2023885,2025168) | includeUnstaged 为真时先 f4 stage helper，fallback `add -A`；随后 `commit -m`；失败可分类 nothing-to-commit；成功再 `rev-parse HEAD` 返回 commitSha | 真实 commit 链确实存在；C1 不能复用自动 stage，成功核验须更强 |
| C05 | worker / [1752222,1752916) | Git runner argv 注入 `core.hooksPath=`，Windows `NUL`，Unix `/dev/null` | hooks 禁用与原版策略一致；此片段没有证明原版禁用签名 |
| C06 | git-action-new-branch-field / [39000,44000) | commit step 解析/trim 消息、await commit mutation、显示 error/execOutput；signal aborted 中断流程 | 可复用状态组织；本仓库须新增 dispatch 后 unknown 证据状态 |

原版 target 行含仓库/分支截断显示和新分支选择，消息区含变更摘要/loading，action 区有分隔线和禁用原因。C1 只展示当前分支，不提供“提交到其他分支”。原版 commitSha 读取本身不证明 parent/tree/message 与点击时选择一致；不能照搬成 verified receipt。

已有 G01/G02/G08/G09/G11/G15/G16 提供 status、binding、checkout 与 stale-read 背景，P 设置锚点提供 commit instructions；这里不重复通用 worktree 契约。

## 3. 实施前源码事实与 C1 补齐点

已 CodeGraph 优先核对以下链路：

| 当前文件/符号 | 已有能力 | commit 需要补充 |
| --- | --- | --- |
| `apps/shared/src/im/hikaru/harness/client/git/GitModels.kt` | GitBackend 四类读取与 discover；GitWorktreeCreator / GitBranchSwitcher 可选接口；status 的 staged/unstaged/untracked 与 HEAD | 独立可选 GitCommitter；真实 staged 内容身份、请求、结果与 receipt |
| 同目录 `GitController.kt` | mutationBusy 共享准入；bind/navigation 只取消 reads；epoch fencing；checkout unknown/rescan | commit 进入同一 admission；索引证据读取与 gate；不得绕开 creator/switch 的未知状态 |
| 同目录 `GitPages.kt` / SettingsGitPage | 状态计数/变更、本地分支切换、Index diff | 消息输入与明确提交按钮；pending/rejected/unknown/verified/rescanned；首帧 scope filter |
| `apps/jvm-app/src/im/hikaru/harness/desktop/git/DesktopGitBackend.kt` | canonical root/commonDir/gitDir 复验、local Host 限制、argv runner、HEAD 前后读取 | commit 前操作状态/index 核验，真实命令和对象核验 |
| 同目录 `DesktopManagedGitBackend.kt` | 委托读取并显式转发 creator/switcher | 新 capability 必须显式转发；不然父接线中的 decorator 会遮蔽真实 committer |

当前 status count、展示 patch 都不提供完整 index 内容身份。仅记录 stagedCount 或 HEAD oid 不足以防止用户点击后另一进程改 index。当前 diff 的 HEAD 前后检查不保证 index 原子快照。

当前 DesktopGitCommandRunner：ProcessBuilder argv 无 shell；前缀 `git --no-optional-locks -c core.fsmonitor=false`；设置 GIT_OPTIONAL_LOCKS=0、GIT_PAGER=cat、GIT_TERMINAL_PROMPT=0，清除仓库/索引/object directory 重定向环境；限时默认 15 秒、stdout 默认 8 MiB、stderr 有界，并发 drain，取消销毁其拥有的 process。没有可写 stdin 接口，不能直接提出 `-F -` 而假称现成可用。它未统一禁用 hooks/signing，不可从 terminal prompt 禁止推导出二者被禁止。

## 4. 最小公共契约与只读 staged 证据

建议 GitCommitter 包含 sync `canCommit(binding)`、suspend `readCommitSnapshot(repository)` 与 `commit(repository, request)`；只读 GitBackend 实现无须增加 mutation。snapshot 不能由生产 fixture 构造。

请求至少携带完整 GitBinding、canonical root/commonDir/gitDir、expected HEAD branch/OID、staged entries 内容摘要、accepted message、固定 hooks/signing policy。receipt 携带同一 repository/request、verified 新 OID/parent/tree、当前 status/branches/worktrees；controller 逐项验证回执与接受请求一致。只凭非空 commitSha 不入 Verified。

### staged proof 建议

1. HEAD 前后包围读取；`ls-files --stage -z` 获取全部 index entries（mode/object OID/stage/path），不是仅变更路径。stage 必须全为 0。
2. `ls-tree -r -z --full-tree <expectedHeadOid>` 获取基准 tree；`diff --cached --raw -z --no-renames --no-abbrev --no-ext-diff --no-textconv <expectedHeadOid> --` 获取 staged delta。用基准+delta核对完整 stage-zero entries，证明确有待提交内容，并拒绝无法证明的 intent-to-add 等状态。
3. 对 canonical entries（含 mode、OID、NUL 分隔路径）计算强摘要；公开 snapshot 只传摘要/摘要计数，不把路径当 argv。提交前重新读取相同证据与 HEAD，两次一致再 dispatch。
4. 首批对无法解析/过大输出、非 UTF-8 可保真路径、unsupported sparse directory entries 等 fail closed；现有 String runner 不能虚称任意原始路径 bytes 均可无损处理。支持 split/sparse index 应以真实 Git 展开输出测试证明，否则标为 unavailable。

此方案不需要 `git write-tree` 作为“只读”读取。write-tree 会写 objects，也可能更新 index cache-tree，不能隐瞒副作用。commit 后用真实 commit 的递归 tree entries 与 accepted staged entries 对比，再确认 tree OID，不以显示 diff 代替。完整 tree 比较保留 executable/symlink/gitlink mode；首批可拒绝 staged gitlink 变化，避免未验证 submodule 扩展。

读取不 stage；单击 commit 也不调用 add/update-index/reset/stash。未暂存/untracked 内容不参与 accepted tree，不要求工作树整体 clean。外部进程仍可能在最后检查与 Git 自己锁 index 之间写入：这是前置检测加事后核验，不是对外部 Git 的原子锁；事后不一致只能 UnknownOutcome，不能回滚。

## 5. 命令、hooks/signing 与确认规则

本次仅执行 `git --version` / `git commit -h`，本机为 `git version 2.50.1 (Apple Git-155)`；未执行以下 mutation。help 明确 `--no-verify` 仅 bypass pre-commit/commit-msg；不能靠它禁止全部 hooks。

推荐首批固定 policy：**不运行 Git hooks，不签名**。UI 在提交动作附近明确显示此策略。需要 hooks 或签名的仓库应改用外部 Git；首批不提供假 signing toggle。原版 hooksPath 证据支持禁用 hooks；签名禁用是为当前 bounded runner 选择的显式产品收敛，不宣称与原版完全相同。

macOS 实际 argv（runner 前缀保留，下面是传给 runner 的 arguments；每项独立 String）：

```text
-c core.hooksPath=/dev/null
-c commit.gpgSign=false
-c i18n.commitEncoding=UTF-8
-c maintenance.auto=false
-c gc.auto=0
commit --no-verify --no-gpg-sign --no-edit --cleanup=verbatim -m <acceptedMessage>
```

无 `-a`、`--include`、`--only`、pathspec、`--amend`、`--allow-empty`、shell 字符串、自动 add。hooksPath 与 no-gpg-sign 双重显式策略；maintenance/gc 禁用限于该调用，避免自动维护扩大后台执行。其他平台需验证其空 hooksPath 目标，不能直接把 macOS 路径照搬。

消息规范在模型固定：拒绝 NUL、全空白与过长输入；明确 LF 规范和提交对象末尾换行规则，accepted message 与对象 body 按该规则核对。`-m` 后消息是一个参数，前导 `--`、引号、反引号和多行都不成为选项/shell。消息会出现在本地 process argv；stdout/stderr/诊断不能无界复制用户消息。未来需要 stdin 时另扩 runner，不作为 C1 的隐藏要求。

作者/committer 使用本机 Git 自身配置/身份规则；不设置假 identity，不扫描用户全局/敏感配置，不自动保存 user.name/email。配置不足由实际 Git 拒绝。固定 policy overrides 不等于隔离所有仓库 Git 配置；同进程仍可能继承 author 等环境，不承诺全新身份环境。

### preflight 与结果确认

- 重验 verified local binding、Host、canonical root/commonDir/gitDir 和 request scope；在 accepted mutation 中保留该 scope，不用当前页面字符串替换。
- 只接受当前真实 refs/heads branch+已存在 commit；核对 branches 中该 ref 的 OID 与 expectedHeadOid。
- status 和 stage entries均无冲突；按 Git 解析的 `--git-path` 定位进行中操作 marker/目录，只检查必要存在性，不读取敏感配置。进行中操作、index lock 或证据失败拒绝，不删除 lock。
- dispatch 前再次比对 HEAD、staged proof、操作状态。Git 自己执行后必须 exit 0，并重新确认同一 repository。
- 新 HEAD OID 必须不同于旧 OID；`cat-file commit <newOid>` 读取真实对象，parent **恰一个且等于 expectedHeadOid**，tree entries 等于 accepted stage entries，message 匹配，固定 no-sign policy 下不含签名 header。
- 重新读取 status、branches、worktrees 与当前 index，确认 branch 未变、当前 HEAD/ref/worktree 指向该新 OID；索引与新 tree 一致。未暂存/未跟踪依然可以存在，不为了形成 clean receipt 删除或覆盖文件。
- 新旧 bytes/对象核验不足、外部 ref/index 竞争、confirmation 中断 => UnknownOutcome；stdout 中的“成功”、exit 0 或新 HEAD 单读均不充分。

## 6. Controller、取消、unknown 与 UI 状态

commit/create/switch 共用 mutationBusy 和已有准入，双击只启动一次。开始接受/成功后 fence 旧 reads；navigation/refresh 不取消 accepted mutation。读 snapshot 的 job 与 mutation 的 owner 分离，表单选择/打开弹窗只读。

unknown 证据键至少包含完整 identity 分区（允许当前 Local anonymous）、Host、canonical commonDir/root/gitDir；generation 不应使导航后同一 repo 的未知状态自动消失。mutation 互斥/阻断投影可按 commonDir 阻止同库 create/switch；未知 index 证据仍属于具体 worktree，不能用 sibling worktree 的读取清除。旧 owner 的消息、receipt、路径不得进入新页面。

| 状态 | 成立证据 / 可操作性 |
| --- | --- |
| Unavailable | 没有 committer、remote/disconnected/foreign Host 或不支持的 index/操作状态；说明原因 |
| Loading / Ready / Empty | 真实 coherent commit snapshot；Ready 且非空消息才可提交；Empty 指没有 staged delta |
| Submitting | 接受的 scope/request 已固定，统一 mutationBusy；输入/提交禁用，显示“正在提交暂存变更” |
| Rejected | 只有 dispatch 前能证明未调用 commit；保留消息，显示 stale/index/操作状态等原因 |
| Verified | backend 和 controller 都确认真实 receipt；显示短 OID、已验证，刷新数据 |
| UnknownOutcome | runner invocation 后 timeout/cancel/nonzero/无法确认；needsRescan，禁止直接重发及同库其他 mutation |
| Rescanned | 实际 HEAD/status/branches/worktrees/**该 worktree index proof与操作状态**重新读取成功；展示“已重新读取当前状态，可重新选择”，不是 Committed |

现有 runner 缺精确 process-start receipt；C1 在调用 runner 前标 dispatched，之后连 spawn failure 也保守 unknown，不能伪造“未启动”。dispatch 前取消不修改 Git；调用后取消只清理拥有 process，不等于 Git 没有提交。controller close 取消 owner 时必须保存 uncertainty，不让 generic catch 降为 preflight failure；没有后台补偿/reset/retry。

rescan 只解除未知门槛，不能用一次 worktrees 或 status 读取解锁。coherent index/HEAD/ref 读取失败继续阻断；冲突/进行中操作可以作为已读到的不可提交状态展示，但仍不能放行 commit。若服务实例销毁，则新 controller 必须先读真实 snapshot 才出现 Ready，不能恢复旧 clean state。是否需要跨应用重启持久 intent 是后续范围，C1 不宣称 durable exactly-once。

成功结果被导航 fence：保存 accepted operation 的结果，不把旧 scope receipt 显示到新页面；释放 mutation 后，对当前同 commonDir 页面安排真实 refresh并再次 fence旧reads，避免导航前读到的旧 HEAD 长期显示。首帧只消费 repository/binding 相符的 commit state。create 的旧 rescan 分支不得提前清除 commit 的更强 uncertainty gate。

UI 复用公共 inputs/buttons/dialogs。展示仓库/当前分支、staged count/Index diff入口、多行消息、明确策略说明、提交按钮与短状态/错误。普通弹窗参考 420dp、最大 viewport 92%，紧凑 320px 允许纵向滚动与长标题截断/可访问完整文本。键盘输入保留；Enter 不隐式提交多行消息，快捷键只有 Ready 时可与按钮同一 action。没有 Include unstaged checkbox/Push/New branch/Generate 的空 consumer。

## 7. C1 实施范围

C1 已按上述边界落地，生产实现集中在以下 Git 模型、控制器、页面、Desktop backend 与 managed decorator 文件：

1. `apps/shared/src/im/hikaru/harness/client/git/GitModels.kt`：可选 capability、snapshot/request/typed result/receipt。
2. `apps/shared/src/im/hikaru/harness/client/git/GitController.kt`：snapshot读取、共享 admission、scope fencing/unknown/rescan。
3. `apps/shared/src/im/hikaru/harness/client/git/GitPages.kt`：真实表单、scope filter 与状态 consumer。
4. `apps/jvm-app/src/im/hikaru/harness/desktop/git/DesktopGitBackend.kt`：现有 runner 复用，staged读取/preflight/commit/confirmation。
5. `apps/jvm-app/src/im/hikaru/harness/desktop/git/DesktopManagedGitBackend.kt`：可选 commit capability 透明转发，不改 managed 登记。

配套测试文件为：

- `apps/shared/test/im/hikaru/harness/client/git/GitCommitControllerTest.kt`。
- `apps/jvm-app/test/im/hikaru/harness/desktop/git/DesktopGitCommitTest.kt`。
- `apps/jvm-app/test/im/hikaru/harness/desktop/git/DesktopGitCommitProcessTest.kt`。
- `apps/jvm-app/test/im/hikaru/harness/desktop/GitCommitInteractionTest.kt`。

无需新增 main/HarnessApp 接线；`DesktopManagedGitBackend` 显式转发可选 `GitCommitter` capability，保持 managed 登记行为不变。

## 8. 有意义验证矩阵

全部 mutation 只在测试自己创建的 tmp repo；不操作原工作区、不用用户 home/config。真实测试用局部隔离身份/config，controlled runner 只暂停或注入明确失败，不伪造 commitSha/成功对象。父线程串行构建与运行。

| 组 | 必须验证的实际边界 |
| --- | --- |
| 真 commit | 部分暂存文件：commit tree=stage版本、工作文件仍是unstaged版本；新增untracked/ignored字节保留；HEAD branch/唯一parent/message/tree均真读 |
| 拒绝 | 空 index、unborn/detached、conflict、merge/rebase/cherry-pick/revert 状态、foreign/remote/scope错配；零 commit invocation，不删lock/文件 |
| stale/竞争 | 读取后真实修改index（计数不变但blob变化）、HEAD/ref/目录重绑定；dispatch前拒绝；dispatch后竞争/对象不匹配 unknown，不回滚/重发 |
| policy与argv | 精确argv无add/force/amend/pathspec；安装 pre-commit/prepare-commit-msg/commit-msg/post-commit及reference-transaction marker hooks验证不执行；配置 signing=true和marker签名程序验证不调用，最终对象无签名 |
| 取消/结果 | invocation前取消0次；真实 commit 已完成但confirmation失败/超时/取消=>unknown；nonzero不能假Verified；rescan不会再commit，且不是成功receipt |
| controller | commit与creator/switch互斥、stale read fenced、跨导航unknown保留；同commonDir sibling不能清index gate；错误receipt/mismatched request拒绝，navigation后的同库真实refresh |
| decorator | 实际wrapped DesktopGitBackend仍可commit；只读/无能力delegate保持Unavailable，不改managed记录 |
| UI | 按钮经真实controller seam，消息多行/选项样文本安全，Empty/Unavailable禁用、loading/失败/unknown/Rescanned；320px长标题与滚动；readonly没有互动mutation consumer |

**交付边界：**C1 只提交已有 index 的本地分支变更，固定禁用 hooks/signing，不提供 stage/unstage、消息生成、push、amend、empty commit 或自动重试。

## 9. C1 实施与验证结果

### 已实现

- `GitCommitter`、commit snapshot/request/receipt 与 `GitCommit` 状态已接入 shared controller/page。
- Desktop backend 已执行 staged index proof、HEAD/ref/操作状态复验、真实 `git commit` argv 与 commit/tree/message/branch/worktree 结果确认。
- commit 已使用统一 mutation admission；提交后的确认失败、取消或超时进入 `UnknownOutcome`，显示“结果未知”，禁止直接重试，必须先刷新真实状态。
- `operationReason()` 直接检查已解析的 `gitDir` marker，并使用 `NOFOLLOW_LINKS`，避免每个 marker 启动一次 Git 子进程；这修复了确认阶段的超时风险。

### 验证

使用 `./kotlin test --build-dir=/tmp/harness-c1-fixed-20261007 -p jvm -m jvm-app` 串行运行三个 C1 测试类，最终 **15/15** 通过：

- `DesktopGitCommitTest`：7/7。
- `DesktopGitCommitProcessTest`：6/6。
- `GitCommitInteractionTest`：2/2。

验证覆盖部分暂存提交保留未暂存/未跟踪/ignored 内容、hooks/signing 禁用、冲突与进行中操作拒绝、提交后确认失败进入未知、320px 交互视口、刷新前禁止重放，以及无私有 runner 错误泄露。

`GitCommitInteractionTest` 的未知结果断言使用 `substring = true`，因为语义节点显示的是带完整操作建议的中文句子，而不是单独的短标签。

### 尚未覆盖

C1 不证明原版同尺寸像素一致性、跨平台 Git 行为、远端提交/push、stage/unstage、签名或 hooks 可配置，以及完整 Desktop 测试套件。
