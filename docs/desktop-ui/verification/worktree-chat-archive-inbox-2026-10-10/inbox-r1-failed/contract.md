# Worktrees 聊天归档 A2a：Inbox 全段准入合同

日期：2026-10-10。实施模型固定 `SHUAIAPI/gpt-6.1-sol`，provider `opencodex`。先分析后实施；工作者只实施及静态检查，退出并释放写集后由父线程独立冻结、串行测试与 JVM 构建。

## 已核对的基线与决定

A1 的 gate-r3 已归档并逐文件核验：SessionAdmission8、Session14、Agent6、Persistence7 共35/35及完整JVM build通过，2,261编译输入。该批只提供 Session 准入，归档按钮尚未启用。

同一指定模型的 A2 只读分析 exit0；建议先交付 Inbox，Registry/driver 必须另行处理有限 owner 清理。父线程核对当前生产 Inbox.kt、Session.kt、SessionAdmission.kt、AgentContractTest.kt 与 emitContained 实现后选定本合同。只读提案中 Context guard、bindOwner、Registry 和 driver 的广泛写集没有在本批获准。

Inbox 当前四个写入口在自己的 mutex 下提交 Session event，再更新 live projection、version 和通知；insert 在 commit 返回之后才推进 nextItemId。append 许可仅覆盖 append，不能覆盖后续 projection/通知；调用者取消或 onChanged 抛错还可能漏增消息ID。本批关闭这两个具体窗口。

## 唯一写集

1. 修改 `modules/session/src/im/hikaru/harness/session/Session.kt`。
2. 修改 `modules/agent/src/im/hikaru/harness/agent/Inbox.kt`。
3. 新增 `modules/agent/test/im/hikaru/harness/agent/InboxAdmissionContractTest.kt`。

保留所有 dirty/untracked；现有测试、fixture、SessionAdmission、SessionStore、AgentRegistry、AgentLoopAgent、Runtime/Context、配置、持久化和页面字节不得修改。禁止 reset/stash/clean/checkout/commit/push、修改用户配置、杀既有进程、启动其它工作者。无法在此写集内满足时，报告具体生产事实并退出；不得自行扩大。

## 生产契约

### Session 公共入口

仅新增 `suspend fun acquireOperationPermit(): SessionAdmissionPermit`，委托当前精确 `ownerStore.admission.acquireOperation(this)`。保留 ownerStore、useAdmission 和已准入 append/restore 的 internal/private 可见性及现有语义。返回许可必须由调用方在 finally 中 dispose；它只登记活动，不提供跳过 Closing、停止 Store、绕过 closed identity 或继承到子协程的授权。

### Inbox 四个写入口

- insert、claim、replace、clear 在取得 Inbox mutex、复制 Message/items、计算 id/version、检查空队列之前取得同一精确 Session 的操作许可。每个入口的完整 try/finally 覆盖获得 mutex、提交、projection、onChanged 和同步 Agent notice。空 claim/clear 也须通过准入。
- reservation 下四入口均以原有 SessionAdmissionException(CLOSING) 拒绝；不复制、不写 Session log、不更新 queue/version/id、不通知。其它既有 Session 的操作保持可用。closed/stopped 继续使用 A1 现有异常；不按 SessionId 查找 replacement。
- acquire 与等待 Inbox mutex 保持可取消；锁内在提交前检查当前协程取消。预先完成必要的 detached copies，避免持久日志提交后因普通数据复制失败而留下半更新 projection。
- 使用最小 NonCancellable 提交段，覆盖 Session.append、live queue、version、insert 的 nextItemId 推进、onChanged 及同步 notice。持有外层许可期间，嵌套 Session.append 可继续获取普通许可；不传入显式绕过令牌。
- nextItemId 必须在任何 post-commit callback 前推进。onChanged 抛错时保留已经提交的 queue/version/id，原错误传播且活动许可释放；后续 insert 不复用这个已提交ID。提交前失败不消费 version/id。
- 调用者在提交段取消后，完成该次已接管提交并释放许可；live snapshot 必须等于相同 Session 原始事件的 Inbox.replay。取消完成不得自动重投消息。
- 原有 INSERT/CLAIM/REPLACE/CLEAR、NEXT_TURN/NEXT_STEP、返回值、snapshot/replay与同步 contained notice语义保留。无需 ordered outbox、后台cleanup launch、CoroutineContext授权、反射或生产测试钩子。

本批取消一致性以正常存活的 owner 提交链为对象。Context/Registry owner disposal、Agent初始化/driver活动、静止恢复和资源静止由后续生命周期合同覆盖，本批不宣称解决这些问题。

## 固定8个新增方法

类：`im.hikaru.harness.agent.InboxAdmissionContractTest`。使用真实 Runtime、SessionPlugin、AgentPlugin/真实 Session/Inbox；可使用最小 TestAgent 绑定真实通知。Deferred 控制并发；不用 sleep、反射、生产测试钩子或网络。

1. `reservedInboxRejectsEveryWriteBeforeMutation`：四入口、两个队列及空 claim/clear在Closing下拒绝，原events/projection/通知不变；释放reservation后的insert无id/version缺口；另一Session可写。
2. `noticeKeepsReservationBusyThroughProjectionAndNotification`：onChanged及真实Inbox notice期间reserve均Busy，通知可观察已提交事件；退出后可reserve。不能在持有Inbox mutex的回调里重入snapshot制造死锁。
3. `concurrentMutationsKeepContiguousVersionsAndUniqueIds`：以Deferred控制相邻写入和互斥等待，原日志version连续、返回消息ID唯一、live/replay相同，期间Busy，完成后许可释放。
4. `cancelledMutexWaiterDoesNotCommitAndReleasesPermit`：第一写入在onChanged受控暂停，第二写入确实等待mutex后取消；第二项无日志/通知/ID消费，第一项完成后reserve成功。
5. `cancellationDuringCommitKeepsReplayProjectionAndIdsConsistent`：已进入提交后的onChanged用Deferred阻塞，取消调用者不会中断提交，期间Busy；释放屏障并join后live/replay/版本/后续ID一致、permit已释放。
6. `callbackFailureCannotReuseCommittedItemId`：onChanged在已提交insert后抛错，仍可重放该项；下一insert为下一个ID且事件version连续；错误与许可释放均核对。
7. `preCommitFailureReleasesPermitWithoutConsumingIdentity`：真实SessionClock/有效公共配置制造提交前失败，事件/projection不变且可reserve；恢复正常clock后从原ID/version写入成功。
8. `staleInboxCannotMutateSameIdReplacement`：旧真实handle关闭后，同文本ID新实例与reservation不受旧Inbox四入口影响；release旧对象不能影响replacement，replacement最后正常写入。

同步通知回调里的suspend协调应使用既有runBlocking/Deferred方式；跨Dispatchers.Default屏障等待必须放在真实dispatcher上使用10秒withTimeout，不能让runTest的虚拟时间提前超时。屏障与runtime/handle释放都放finally，测试失败也可退出。

父线程固定旧回归：SessionAdmissionContractTest、SessionContractTest、AgentContractTest、AgentLoopContractTest、LlmRetryContractTest、SessionPersistenceContractTest。从当前字节冻结方法集合，不修改旧方法；验证真实XML方法、零失败/错误/跳过后再完整JVM app build。保留默认open-turn repair、Restore retry自动续跑与keepInbox取消语义。底层批次不生成截图。

## 交付与后续

基线 `/tmp/harness-worktree-chat-archive-inbox-baseline-20261010/baseline.json`；父线程核对全部编译输入和三文件before SHA。交付 `/tmp/harness-worktree-chat-archive-inbox-delivery-20261010.json` 与同名.md；记录model/provider、baseline_sha256、contract_sha256，三项files(path/before_sha256/after_sha256)，tests_run/builds_run/screenshots_run/native_execution_run均false，write_set_released/source_writes_stopped均true。交付后退出。

后续先完成有限owner清理、Registry create/restore/detach和driver独立许可；再做显式静止恢复、Room/process/PTY静止、持久归档与正式bootstrap，最后Worktrees按钮及Recent/当前聊天同步。不加入独立归档页、删除或清理，不启用归档按钮。整体目标active，用户确认的排除/必做范围不变。
