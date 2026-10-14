# Worktrees 聊天归档 A1：Session 准入门槛合同

日期：2026-10-10。实施路由：`SHUAIAPI/gpt-6.1-sol / opencodex`。本合同是父线程对只读提案 A 的第一次收敛；优先于提案中的广泛写集。

## 本批交付

将真正的 Session 写入和 SessionStore 生命周期入口接入同一个、属于精确 Store 实例的准入协调器。交付可独立验证的普通活动许可与独占 reservation，为后续 Agent/Inbox/driver、静止恢复与持久归档复用。当前仅证明 Session 层；reservation 本身不表示 Agent、Room、后台进程或 PTY 已静止，也不启用归档按钮。

本批选用忙碌立即拒绝语义。已有活动时独占请求返回 typed Busy；不等活动完成，不取消执行。reservation 关闭目标 Session 的新写入与全 Store 的 catalog/lifecycle 写入；已有其他 Session 的普通 append 仍可进行。历史和身份读取保持可用。

## 唯一源码写集（4 文件）

1. 修改 `modules/session/src/im/hikaru/harness/session/SessionStore.kt`。
2. 修改 `modules/session/src/im/hikaru/harness/session/Session.kt`。
3. 新增 `modules/session/src/im/hikaru/harness/session/SessionAdmission.kt`。
4. 新增 `modules/session/test/im/hikaru/harness/session/SessionAdmissionContractTest.kt`。

实现者可在新增 Admission 文件中定义必要的 typed result/exception/token；API 名称自定，职责以本合同为准。Store 向下一批暴露精确实例的协调器访问能力。现阶段不需要修改 Plugin/Definition、Runtime、Agent、Room、现有测试或模块依赖。若此写集不能满足合同，提交具体阻碍并停止，不能自行扩大。

## 准入与身份

- 每个 Store 只有一个协调器，所有经 prepare/load 构造的 Session 都在返回或发布前绑定不可伪造的实例身份。SessionId 不能代替 Session 引用和 Store 归属。相同 ID 的新实例获得新身份。
- 短状态 mutex 只检查与更新 owner 状态、精确 token、目标活动计数和 catalog 活动计数。不得持有它调用 owner、回调、IO 或等待其他活动结束。
- 普通 operation/catalog 许可在入口获取，覆盖真实工作及其回调和失败回滚，结束后只释放自己的许可。许可不能通过可继承 CoroutineContext 自动授权子协程；异步工作必须有独立的显式许可。
- 独占 reservation 在同一状态临界区检查目标活动及 catalog 活动为零，并关闭目标写入与 catalog 新准入。其他 reservation、活动或已停止 owner 返回明确的拒绝，不能伪装成功。
- reservation 绑定精确目标、Store incarnation 和自身 token。重复释放是幂等操作；过期 token 不能释放另一个 reservation 或打开新的 Store。释放只解除本次内存 reservation，不写任何归档元数据。
- 活动许可释放必须在取消状态下可靠完成。内部嵌套调用使用窄的显式 helper/token，或独立计数；没有提前释放和把父活动许可借给后续子任务的窗口。

## 真实入口覆盖

| 入口 | 许可生命周期 |
| --- | --- |
| Store prepare | ID 分配前取得 catalog 许可，构造与精确身份绑定完成后释放 |
| Store create | ID 分配前开始，跨 prepare/enter/announce、取消检查及所有 rollback；不能只分别检查几个子调用 |
| Store load | 构造前开始，跨 restore/enter/announce/rollback；覆盖本函数全部工作 |
| Store enter/announce/attach/detach | 各公开入口取得 catalog/精确目标许可；内部调用显式传递有效许可，不持 owner 锁重新获取协调器许可 |
| Store flush | 取得 catalog 许可后读取快照，覆盖全部 Flush listener 及错误聚合 |
| Session append | 在编码、mutex 等待和日志改动前取得目标许可，覆盖同步通知与错误报告，finally 释放 |
| Session restore | 在日志验证与恢复前取得目标许可，Closing 拒绝必须发生在日志改动之前 |
| 内部 enter/announce/close | 由已准入 Store 路径显式授权，不能留下无许可的低层旁路 |

Store 与 Session 原有 owner mutex 和同步 observer 语义保留。普通 callback 可以查询 reservation（得到 Busy）或修改另一个 Session；协调器状态锁不得因此死锁。本批不引入 ordered outbox，不承诺原本持 owner mutex 的同对象重入调用已经可用。后续若需要改变通知语义，应另行分析和验收。

已关闭旧 handle 的 dispose 保持幂等，不能误关同 ID replacement。Store 整体 dispose 使用专门的 owner 停止路径：使当前 reservation 失效、阻止新准入，然后完成原有精确 cleanup；不能因为调用者持内存 reservation 而泄露 Store。迟到 release 不得重新打开已停止 owner。正在进行的操作仍受原有 owner/lifecycle 检查约束。

## 固定新增测试（8 个）

类：`im.hikaru.harness.session.SessionAdmissionContractTest`。使用真实 Runtime/SessionPlugin/SessionStore，以 Deferred/明确事件回调控制时序，不依赖 sleep 或反射，不加生产测试钩子。

- inFlightAppendMakesReservationBusyThroughNotice
- reservedSessionRejectsAppendRestoreAndLifecycleWithoutMutation
- catalogPermitProtectsCreateLoadAndFlush
- callbackReservationAndIndependentMutationDoNotHoldStateLock
- cancellationAndFailuresReleaseOnlyOwnPermits
- releasedParentPermitCannotAuthorizeInheritedChild
- staleHandleOrReservationCannotAffectSameIdReplacement
- ownerDisposeInvalidatesReservationAndKeepsOldReferencesClosed

各方法分别证明：真实 append 通知尚未完成时为 Busy；Closing 拒绝 direct append/restore/enter/load/attach 等并且日志与 live 集合不变；create/load 全段与异步 Flush listener 没有零计数窗口；observer 中查询 reservation 和独立对象写入不占状态锁；取消与失败不泄露/误释放许可；继承协程上下文的迟到子任务仍被 Closing 拒绝；旧 handle/token 不影响 replacement；owner dispose 后旧引用与 reservation 不能重新写入。可在固定方法中使用有意义的表驱动覆盖其他生命周期入口。

旧回归固定：SessionContractTest 14、AgentContractTest 6、SessionPersistenceContractTest 7；共 4 类 **35 项**。默认 open-turn repair、普通 pending retry 跨 restart 的行为由旧 Persistence 方法验证。完整 JVM app build 为编译兼容门槛。当前不需要截图。

## 交付及验收

保留当前 dirty/untracked 工作。工作者只实施和静态检查，动态 test/build/截图由父线程在释放写集后独立冻结并串行运行。不得改旧测试、用户配置或 Git 状态，不启动其他 worker。

交付 `/tmp/harness-worktree-chat-archive-a1-delivery-20261010.json` 及同名 `.md`；JSON 包含 model=`SHUAIAPI/gpt-6.1-sol`、provider=`opencodex`（宿主请求路由）、baseline_sha256、contract_sha256、4 个 files 的 path/before_sha256/after_sha256、tests_run=false/builds_run=false/screenshots_run=false/native_execution_run=false、write_set_released=true/source_writes_stopped=true、实现 API 摘要与尚未覆盖范围。交付后停止写入并 exit。

父线程核对完整输入集合及逐 SHA：仅 4 文件允许变化，其他源文件/YAML 与 Pin R2 保持；按冻结的 8 新方法与 27 旧方法核验实际 XML，无 failure/error/skip；完整 JVM build exit0。真实失败另存，修复须有窄写集。通过后再确定 Agent/Inbox/driver 的下一份合同；本批通过不能宣称归档/恢复已完成。
