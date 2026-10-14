# Worktrees 聊天归档 C：driver 独立活动合同

## 状态与依据

本合同由父线程审查只读报告第 3 节及生产 AgentLoopAgent send/cancel/resumeAttempt/shutdown/runDriver/ensureInitialized/updateStatus 后制定。指定实施模型 **SHUAIAPI/gpt-6.1-sol / opencodex**；本合同先准备，Registry B 独立验收后才下发。

现状：三个公共操作无全段 Session 活动；launch 在 Agent mutex 内，driver 初始化和 RUNNING 在 try 外；initialized 先于历史读取置位；finally 与 cancel 收尾持 Agent mutex 读取 Inbox，未独立保护 driver 接力与取消。父 Context disposal 的 Runtime lane 等待风险仍待独立设计。

## 写集与 API

仅修改 `modules/agent-loop/src/im/hikaru/harness/agent/loop/AgentLoopAgent.kt`。仅新增 `modules/agent-loop/test/im/hikaru/harness/agent/loop/AgentDriverAdmissionContractTest.kt`。

不新增公共 API；使用已验收的 `Session.acquireOperationPermit()`，直接 try/finally dispose。候选、启动屏障、初始化协调、停止记录和 wakeup generation 都是 Agent 私有状态。其它生产文件、所有既有测试和 fixture 字节保持。

## 公共操作与初始化

- send、resumeAttempt、普通 cancel 在初始化、Agent mutex、Inbox mutation、状态改变前取得外层 operation permit，覆盖全部操作和收尾。Closing 拒绝无变更。普通 cancel 不接收 owner token 或继承授权。
- 并发 cancel 用串行协调或精确停止记录；一个 finally 不得解除另一个未结束的停止请求。保留 keepInbox=true 不自动重启，keepInbox=false 不遗漏清理后到达的真实 wakeup。
- 初始化只在历史读取成功、nextTurn 提交后置 initialized；并发调用等待同一个完成结果。失败或取消后可重试，从持久最大 turn 继续。历史读取与等待在 Agent mutex 外。

## driver 候选与全段活动

1. 每个候选独立取得 Session operation permit，不能借 caller permit。scope Job 已取消或 Agent disposed 时不创建、不重启。
2. 推荐在 Agent mutex 外 launch(UNDISPATCHED)，候选最外层 try 先等待采用屏障，随后初始化和 RUNNING。锁内仅复核 scope/候选 Job 活性、stopping、driver identity 并安装记录；解锁后打开屏障。
3. 未采用候选只释放自己活动，不发 IDLE、不清其它 driver。取消及等待在 Agent mutex 外。同步 launch 失败、启动前取消、采用前 caller 取消均有确定的许可释放路径；不另 launch cleanup，不使用没有未启动取消释放协议的 LAZY Job。
4. 已采用 driver 的同一个 permit 覆盖初始化、RUNNING、body、IDLE、finally 和接力。最外层 finally 使用 NonCancellable，最内层 finally 始终释放 permit，并按精确 identity 整理 driver；通知、snapshot 或接力失败不能跳过释放。
5. 重启判断使用进入 finally 前保存的真实 driver Job 与 owned scope Job，不读取 NonCancellable 活性。仅正常退出且真实队列待处理才接力；取消、初始化失败、基础设施异常、disposed 或 scope cancellation 不自动循环重投。
6. 接力在旧 permit 释放前登记新候选；未采用候选自行释放。不产生可 reserve 的零活动窗口。IDLE 通知期间仍登记当前 driver，防止 cancel 丢失它；RUNNING 通知前已安装采用记录，允许同步 listener 检查 already-running 而不自锁。

## 队列与通知

- 彻底消除 Agent mutex 内获取 Inbox mutex：锁外读取 snapshot，用私有 wakeup generation 在锁内复核；generation 变动时重读。send 插入后尚未取 Agent mutex 的情况由 send 观察最终 driver 状态完成启动。cancel 清理后的复核采用同一协议。
- 所有同步用户/事件回调均在 Agent mutex 外；不以 snapshot 移出锁而丢失原 IDLE followup 接力保证。
- contained listener 错误被 EventsService 包含；Context 开始关闭后 checkActive 的通知错误仍可能抛出。按实际差异验证，任一情况均释放 driver permit。

## 固定新增测试

AgentDriverAdmissionContractTest 固定九个 @Test 方法：

1. closingRejectsSendCancelAndResumeBeforeStateMutation
2. cancelledInitializationCanRetryFromPersistedTurnNumber
3. driverPermitCoversRunningBodyAndIdleNotification
4. idleFollowupHandoffKeepsContinuousSessionActivity
5. runningNoticeCanCheckAlreadyRunningWithoutMutexDeadlock
6. closedContextNotificationFailureStillReleasesDriverPermit
7. cancelledDriverAndDisposedScopeNeverRestartQueuedTurns
8. concurrentCancelsCannotReleaseEachOthersStoppingState
9. restoredAttemptOwnsIndependentActivityUntilItsFinallyCompletes

使用真实 Runtime/SessionPlugin/AgentPlugin/AgentLoop、最小本机假 LLM provider、Deferred 和真实 dispatcher 的有界同步。无网络或模型请求、sleep、反射、生产测试钩子。同步 launch 抛错分支以源码验收，当前正常公共 API 不可可靠制造；不得使用 OOM 或虚构覆盖。Context 通知失败用实际生命周期 effect 构造，不据此宣称 owner disposal 整体安全。

## 交付与验收

实施工作者只写两文件并做静态检查，不 test/build/screenshot/native app/PTY；交付后停止写入并 exit。交付 JSON/MD 记录 model/provider、baseline_sha256、contract_sha256、两项 before/after SHA、各运行字段 false、write_set_released/source_writes_stopped true 和边界。

父线程收取退出、复核写集、冻结源码，串行运行新 driver 9 项及 Registry6、Inbox8、SessionAdmission8、Session14、Agent6、AgentLoop10、LlmRetry1、Persistence7，共 9 类 69 个固定方法，失败/错误/跳过 0；然后完整 JVM app build。保留 open-turn repair、pending retry 重启和原 keepInbox/visible-prefix/IDLE-followup 语义，不修改旧测试以适配实现。

## 后续边界

本批只证明正常 owner 下操作、driver 活动与取消一致性。有限 owner 清理须等 Runtime 生命周期等待边界独立验收；不新增 Context guard、Store owner-stop/bypass 或 CoroutineContext 权限。之后才做显式静止恢复、Room/process/PTY 静止、归档持久化和页面接线。归档按钮未启用；不增加归档页、删除或清理，不证明原生窗口或原版像素一致。
