# Worktrees 本机 Host 会话观察层：实施合同

状态：host-r5 父线程定向验收通过并归档；本合同的实施写集已释放。下一切片为 scheduled-executor-crash-contract-2026-10-09.md。

## 实施身份与基线

- 沿用 worker `local/01a11a54-1694-7fb3-8007-37c434531314`，指定模型 `SHUAIAPI/gpt-6.1-sol`、provider `opencodex`。
- 基线为 `/tmp/harness-session-header-catalog-parent-candidate-20261009-catalog-r1`，其 manifest 包含 2,206 个输入。
- 本切片派发前 SHA 和文件副本在 `/tmp/harness-worktree-host-observation-baseline-20261009/`。先核对基线，再实施；旧插件合同和交付报告均不是本次任务。
- 不运行 build/test，不创建工作者或新线程。父线程在写集释放后冻结、审阅、串行构建验收。保留所有现有 dirty/untracked 内容，不 reset/stash/clean/commit。

## 唯一目标

将同一个本机 Runtime 的 `SessionStore.list()` 与 `SessionPersistence.listHeaders()` 接到 managed Worktrees 的只读刷新。用户能看到本次观察的关联会话及来源，且能区分真实空观察、未知、不支持。不是假数字、静态 fixture，也不是修改 `SessionApi.list` 的语义。

## 精确写集

生产文件：

1. `apps/shared/src/im/hikaru/harness/client/git/ManagedWorktreeModels.kt`：增加独立 typed 会话观察、关联项、来源、未知原因及观察时间模型，兼容现有 protection 默认值。
2. `apps/jvm-app/src/im/hikaru/harness/desktop/git/DesktopWorktreeSessionObserver.kt`（新增）：绑定本机 Context/home/Host 的只读观察消费者。
3. `apps/jvm-app/src/im/hikaru/harness/desktop/git/DesktopManagedWorktreeRepository.kt`：可选 observer，默认关闭；一次 read 只观察一次目录，再分配到 entries，返回前 owner 重验保留。
4. `apps/jvm-app/src/main.kt`：用当前同一 harnessHost.runtime.context 和已解析 managedHome 绑定 observer，仅限相关 imports 和 managed 接线。
5. `apps/shared/src/im/hikaru/harness/client/git/GitPages.kt`：现有 managed card 显示 typed 状态、来源/父子和本次观察时间；保持既有 stale、pin/archive/other 和 destructive-unavailable 行为。

测试文件（新增）：

6. `apps/jvm-app/test/im/hikaru/harness/desktop/git/DesktopWorktreeSessionObserverTest.kt`。
7. `apps/jvm-app/test/im/hikaru/harness/desktop/WorktreeSessionObservationInteractionTest.kt`。

其他文件不写。若确有编译所需的新写集，先报告具体原因，父线程决定扩展。既有 Room 目录、插件和终端/子 Agent 源码不改。文档由父线程维护。

## 观察协议

1. 生产 observer 捕获当前同一 Runtime/Context 的确切 SessionStore、SessionPersistence 实例，以及本机 Host/home 来源。创建绑定使用已有 DesktopBundle 的 home→Room factory 链；不能仅用相同 HostId 就认可任意 provider。读取前后都检查 context 活跃、两服务引用身份仍一致及 owner 与匿名 LocalReadOnly binding 一致。profile reconcile 替换任一服务时旧 observer 失效，不能继续使用旧目录。生产 factory 不可用时以 typed Unavailable/Unknown 降级，不令 app 启动失败。
2. 构造兼容：已有 `(backend, store) { ownerValidator }` trailing lambda 和既有第三参数调用都须仍表达 ownerValidator。可以加明确兼容构造重载，不能把旧 lambda 默默绑定到 observer。
3. observer 为可选依赖，未提供时保留旧 fixture 的 Unknown 文案和行为；main 为验证通过的本机 Host 接入真实 observer。远端、member、Host/home 不符拒绝观察，不返回其它 owner 会话。
4. 一次目录读取合并 live headers 与 durable headers。只读取 header，不 load、repair、inbox 或解析事件。`listHeaders()==null` 是不支持，空集合是真实可用空目录；live 为空不能替代 durable 能力。
5. 按 session id 去重，保留 Live/Durable 两个来源和 parentSession。匹配 id 的 header 字段冲突（至少 cwd/parent，其他身份元数据差异也须明确处理）给 typed Unknown/冲突，不静默覆盖为完整成功。若 header 版本差异仅是正常事件追加，应明确比较可影响归属的字段，不因版本计数变化制造假冲突。
6. cwd 必须是本机 native Path 可解析的绝对路径；normalize、toRealPath 并确认真实目录。null/非法/不存在/不可读或无法验证的 cwd 记 typed 未判定原因，不能给成功零会话。roots 同样必须真实验证。POSIX 主机不能认可 Windows/UNC 字符串为本机路径。
7. 使用 Path 分量边界匹配根及子目录；`tree-2` 不属于 `tree`。真实 symlink 应以解析后路径归属，临时目录或 symlink 检查失败为未知。观察中无法归属的会话使受影响结果为 Unknown，即使已观察到部分匹配也保留匹配及原因，不能输出完整零会话。
8. 每次记录开始/完成时间和 owner。两个服务没有共同事务，状态命名为“本次只读观察”；无关联匹配且无未判定项只显示“本次未观察到关联会话”，不得称完整无会话证明、安全删除或线性化快照。
9. disposed、来源异常、服务替换或 owner 变化不能成功；CancellationException 原样继续抛出，不能吞成空集合。已取消 coroutine 也须 ensureActive。repository 返回前原有 owner 重验保留；失败时 UI 不显示当前成功状态。
10. 观察消费者无写操作，不写 registry/Room、不删除/pin/archive。现有 Git 指纹核验和安全边界不削弱；生产 `scheduledExecutionVerified=false` 保持。

## 定向验收要求

- 用真实 Runtime、临时 Room、真实 Git 和临时 home 证明：历史 durable 尚未 load、live 尚未 flush、同 id 双来源去重、父子关系、真实空目录。读取前后 SessionStore 不因历史枚举增加、Room/registry 内容和事件不变。
- Path 边界、子目录、symlink、缺 cwd、非法 native 路径、缺目录、非目录的结果；冲突 header 不能完整成功。
- null capability、source 异常、disposed、服务替换（包括同 HostId）、owner/home/member/remote 变化；取消继续抛出。可在错误用例用窄 seam 注入 provider，正常成功链不能用假目录替代真实 Room。
- repository 完整 read 集成：真实 Git 列表和登记只读，观察一次供所有 entries 使用；读取中 owner 变化拒绝结果。旧默认关闭/构造调用保留。
- UI 接生产 WorktreesPage/controllers + 真实 repository/Runtime/Room/Git。刷新用实际 `performMouseInput` 点击测得的按钮 bounds，不用 `performClick`、`performScrollTo` 作为新增通过证据；必要滚轮也用真实鼠标。assert typed 页面状态与 controller 数据，不仅查一段固定文案。
- UI 包含真实空→live/历史关联→未知、错误或 owner 变化的刷新状态；浅/深色 640×800 与 320×600 各留代表图，末尾真实滚动可达，不声称原版像素验收。
- 不弱化已有测试。报告建议回归类及每个新增类的实际用例数。父线程沿相同 frozen manifest 串行测试。

## 交付

### 2026-10-09 首轮编译修正授权

父线程 `host-r1` 已编译生产源码，但新增测试源码编译失败，测试未运行。错误证据在 `/tmp/harness-pages-verified-20261009/worktree-host-observation/host-r1/DesktopWorktreeSessionObserverTest/command.log`。本轮仅修改两个新增测试及 `apps/jvm-app/module.yaml` 的 test-dependencies；新增 Room 3.0.0 和当前持久化模块一致的 JVM bundled SQLite 2.6.2 直接测试依赖，不升级生产依赖。修正 Compose `waitUntil(timeoutMillis = 10_000)` 参数、用公开 GitState 字段表达 readiness，保持所有真实链路与原有断言。五个生产文件保持首轮交付 SHA。

最终交付扩为八文件，before SHA 仍以原始 2,206 输入基线为准，包含 module.yaml 原始 SHA；报告 `tests_run=false` 指工作者未运行测试，并说明父线程首轮编译失败。其余合同与 11/4 测试数量保持。

写入 `/tmp/harness-worktree-host-observation-delivery-20261009.json` 及 `.md`，包含实际 model/provider、完整 changed files 的派发基线 before/最终 after SHA256、`tests_run=false`、`builds_run=false`、`write_set_released=true`、新增用例数、建议父线程测试类及限制。限定写集 `git diff --check`（新文件另查 whitespace）。释放后停止写入。

### 2026-10-09 host-r2 UI 修正授权

父线程 host-r2 实际运行观察层 11/11，通过；UI 0/4，三个首次 readyObservation 超时、一个内层滚动末尾父会话行不可见。日志在 `/tmp/harness-pages-verified-20261009/worktree-host-observation/host-r2/WorktreeSessionObservationInteractionTest/command.log`。仅授权修改新增 `WorktreeSessionObservationInteractionTest.kt` 与本切片交付 JSON/MD。其他七文件保持 host-r2 SHA，尤其五个生产文件；若证明确有生产缺陷，先报告证据和最小写集，不擅自改动。

核查页面 LaunchedEffect 绑定及 Compose 就绪推进，先执行正常页面帧/idle，再等待真实 Git 与 managed 状态；失败必须包含具体状态诊断，不能通过删减 readiness 条件或延长等待掩盖错误。不得直接 bind managed 代替生产页面接线。检查内层列表和外层 SettingsFormFrame 的裁剪、目标及末尾 marker 布局，使用现有 measured mouse/wheel helpers 实际揭示节点。保留 full text、无 ellipsis、实际水平/垂直末尾和完整 marker/boundary 可见断言。不得把节点存在视为可见；若一屏确实容纳不下两项，应分别留真实滚动证据并报告，不能伪造同帧。

保持 11/4 用例数量及真实 Runtime/Room/Git/registry 链，不运行 build/test。更新交付报告完整八文件最终 SHA、原始 before SHA、修正原因、tests_run=false、builds_run=false、write_set_released=true，释放后停止写入。父线程使用唯一 host-r3 冻结并串行验收，保留 host-r2。

host-r3 候选仅修改上述 UI 测试。固定 320dp 内层视口的 lineage 与底部保护文案在窄宽度下不同时可见，因此四个尺寸/主题组合各保存 lineage 与 end 两阶段，共八 PNG 及八份 geometry；lineage 保留水平末尾 marker 和边界全文可见断言，end 保留实际垂直末尾和最后保护文案全文可见断言。父线程已审阅改动并更新归档脚本核对完整八槽矩阵，2,209 输入已冻结；当前开始运行 38 项串行验收，尚无本轮通过结论。

### 2026-10-09 host-r3 调度修正授权

host-r3 已完成观察层 11/11；UI 0/4，全部失败于首次 readiness，managed 已为 Ready 且观察状态为 Observed，但 Git 的 diff 或 commitSnapshot 仍为 Loading。实际 XML/log 保留于 `/tmp/harness-pages-verified-20261009/worktree-host-observation/host-r3/`；本轮未运行后续五个回归类。

仅授权修改 `WorktreeSessionObservationInteractionTest.kt` 与本切片交付 JSON/MD，其他七交付源码保持 host-r3 SHA。优先核查 ObservationPage 的 Dispatchers.Unconfined：真实 backend 经 Dispatchers.IO 挂起恢复，多个 Git read 的 state.copy 更新可能在不同线程并行，从而丢失完成状态；生产页面由 Compose scope 协调。应先用实际源码说明调度原因，再让测试使用与生产一致的串行 controller 调度和 UI 操作。保留真实 Git、Runtime、Room 和页面 LaunchedEffect 绑定，不直接 bind managed，不删除 readiness 条件、不延长 10 秒 deadline、不添加无依据重试，也不修改生产文件。若确认生产问题，先报告最小写集。

保留 11/4 用例及四组尺寸/主题的 lineage/end 八图、八份 geometry。不运行 build/test。交付报告保持原始 2,206 输入 before SHA，更新最终 after SHA、调度说明与释放标志，修订号为 `host-r3-scheduling-corrections`。父线程将先在 host-r4 冻结候选上运行具体失败的 UI 类；通过后再运行其余 34 项，避免先重复已通过的观察层。

### 2026-10-09 host-r4 创建测试就绪修正授权

host-r4 的新增 UI 4/4、observer 11/11、managed integration 8/8、controller 4/4、managed UI 3/3 通过。旧 WorktreeCreationInteractionTest 两项在输入路径前失败：根目录已出现，但创建路径仍 Disabled。共执行32项，30通过、2失败；GitWorktreesScreenshotTest六项未运行。八张观察图和八份 geometry 已生成并目视检查，尚未归档。原始 XML/log/manifest 留在 `/tmp/harness-pages-verified-20261009/worktree-host-observation/host-r4/`。

父线程 exact-file CodeGraph 确认两测试只等待 `git-repository-root` 存在；生产 `GitState.pageReady` 还要求 status/branches/worktrees/diff/commitSnapshot 等异步读取均不处于 Loading。该等待不保证输入可用，生产禁用条件有依据。

本轮仅新增授权修改旧 `apps/jvm-app/test/im/hikaru/harness/desktop/WorktreeCreationInteractionTest.kt` 及本切片交付 JSON/MD。其余八个交付源码保持 host-r4 SHA。两测试等待真实页面字段 enabled 后才输入，保留现有10秒就绪期限、15秒创建期限、真实 HarnessApp/Git/文件链与全部创建、源文件/index保护及Relay断言；不增加 sleep/retry，不直接操纵 controller，不修改生产 enabled 条件。可抽取同文件就绪 helper，不能把 submit 的初始禁用误当失败（尚未填入路径/分支）。

父线程已核对工作树全部2,209输入与host-r4一致，并将旧测试 before SHA/副本和前次delivery保存到 `/tmp/harness-worktree-creation-readiness-baseline-20261009/`。该测试 before SHA 为 `d44c737c661a5f2051a6aba21c7bb35def9e23af899987ccb43e625636fef725`，同时与原始catalog-r1基线一致。最终delivery扩为九文件，保留原八文件的catalog-r1 before及host-r4 after，仅补旧测试记录；修订号 `host-r4-creation-readiness`，标明worker未build/test并释放写集。

父线程冻结host-r5时精确检查九文件合同；先运行两项创建回归，成功后再按同一manifest完成余下36项及八图归档。host-r4失败历史保留，不能累计成38/38通过。

### 2026-10-09 host-r5 最终定向验收

同一指定工作者完成旧创建测试两处就绪谓词修正，等待预期仓库路径及 path/branch/start 三字段 enabled。父线程核对九文件交付、原八 host-r4 SHA 和原始 before SHA，冻结 2,209 个输入；保留原有期限和全部真实创建/源目录保护/Relay 断言。

同一 manifest 串行通过 **38/38**：创建 2、观察 UI 4、observer 11、managed integration 8、controller 4、managed UI 3、Git/Worktrees 截图 6，零失败、错误或跳过。父线程逐张查看 320×600/640×800 浅深色 lineage/end 八 PNG，归档脚本核验八份 geometry、完整矩阵和复制前后 SHA。lineage/end 为分别验证的滚动阶段。

证据位于 `verification/worktree-host-session-observation-2026-10-09/host-r5-final/`；图片与 geometry 位于 `captures/worktree-host-session-observation-2026-10-09/host-r5/`。冻结 manifest SHA 为 `c5c567897852b08496f529e396018acfdbbb8dc6b735cc41a4027a6ff0005de7`。host-r1 至 host-r4 的失败历史保留，不与本轮数量累加。

本批是临时真实本机 Runtime/Room/Git 与离屏生产 UI 验证。两个目录独立读取，不提供安全删除授权；pin/archive/移除保护、原版同状态像素、原生窗口及远端仍待。生产 Cron=false。定时任务下一切片以本轮 2,209 输入为基线。

整体复刻范围仍是用户已确认的本地页面及真实功能。本切片完成不等于整体目标完成。
