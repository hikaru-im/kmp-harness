# Driver 两文件实施交付

- model: `SHUAIAPI/gpt-6.1-sol`
- provider: `opencodex`
- baseline_sha256: `445b272a7771c78968efe0e5a455f99e1a197cdf2970576fe5404cbdfa5ab226`
- contract_sha256: `b6646786d29d63159c7d72ce25a09804e2cd5f8409775e1f10bafe5e8602eb60`
- tests_run=false / builds_run=false / screenshots_run=false / native_execution_run=false
- write_set_released=true / source_writes_stopped=true

## 精确写集

| 仓库相对路径 | before_sha256 | after_sha256 |
| --- | --- | --- |
| `modules/agent-loop/src/im/hikaru/harness/agent/loop/AgentLoopAgent.kt` | `a5f26a914771032ef07636b2cfb3a49a753f08959f6a5ad8e705afd9592e71f9` | `cebbfc695e65cccb8e7cac15823b2a2a460e0592741fc47f98bbb5509f1e4f59` |
| `modules/agent-loop/test/im/hikaru/harness/agent/loop/AgentDriverAdmissionContractTest.kt` | null（新增文件） | `7f14b787269984be16abbd8a1c92811fe2de811f358c77aa07f279b62ae7a10d` |

## 实施内容

- send/resumeAttempt/cancel 各有完整公共操作许可；每个 driver 候选另取独立许可。
- 历史初始化在锁外读取、共享结果，仅成功提交 nextTurn 后完成；失败和取消后可重试。
- 候选在 Agent mutex 外 UNDISPATCHED 启动，屏障在最外层 try 内；采用前取消、未采用及同步 launch 失败具有释放路径。
- driver 的 RUNNING、body、IDLE、finally 和接力由同一许可覆盖；最内层 finally 释放，按同 identity 清理。
- 接力检查真实 Job 与 scope，generation 复核锁外 Inbox snapshot；旧许可释放前登记新活动。
- 并发 cancel 仅移除自己的 stopping；保留 keepInbox 语义。awaitIdle 等待 owned driver children 完成最内层释放。
- runTurn 起至文件结束与原基线逐字节一致，包含恢复 turn、retry、visible-prefix、工具和状态实现。

## 静态证据

- 基线及合同 SHA 校验通过；2,262 项非写集输入 SHA 均未变。既有 dirty/untracked 内容按基线保留。
- 既有八类 60 个固定测试方法保持；新增九个 @Test 的名称与顺序精确匹配，总计九类 69 个。
- 18 个 Agent mutex 块未发现 Inbox、launch/join、通知、Deferred completion、历史读取或 beforeAttempt 调用；无跨模块 internal Context.isDisposing。
- 公共 override 名称保持；候选唯一 launch 为锁外 UNDISPATCHED；去除字符串和注释后两文件分隔符平衡。
- 生产 git diff --check 返回 0，无诊断；新文件 git diff --no-index --check 返回 1（与 /dev/null 有差异），无空白诊断；两文件无行末空白。
- 以上属于源码与静态检查，未进行编译或运行验收。

## 固定新增测试

1. `closingRejectsSendCancelAndResumeBeforeStateMutation`
2. `cancelledInitializationCanRetryFromPersistedTurnNumber`
3. `driverPermitCoversRunningBodyAndIdleNotification`
4. `idleFollowupHandoffKeepsContinuousSessionActivity`
5. `runningNoticeCanCheckAlreadyRunningWithoutMutexDeadlock`
6. `closedContextNotificationFailureStillReleasesDriverPermit`
7. `cancelledDriverAndDisposedScopeNeverRestartQueuedTurns`
8. `concurrentCancelsCannotReleaseEachOthersStoppingState`
9. `restoredAttemptOwnsIndependentActivityUntilItsFinallyCompletes`

## 边界与独立验收

- 未运行 test/build/screenshot/native app/PTY/资源；新增测试仅编写，运行行为未验收。
- 同步 launch 抛错分支仅做源码检查；未采用生产钩子、反射、OOM 或虚构覆盖。
- 通知失败测试使用真实 public Context.effect 生命周期与 emitContained；不证明 Runtime owner disposal 或 mutation lane 整体安全。
- 未新增公共 API、Context guard、owner token/bypass 或 CoroutineContext 授权；未重跑 Comet probe。
- 未修改配置、执行 reset/stash/clean/checkout/commit/push、派其他工作者或终止既有进程。
- 无归档按钮启用、归档页、删除、清理、持久化页面接线、原生窗口或像素一致声明。
- 写集已释放、源码写入已停止。父线程收取退出后按本交付 SHA 冻结源码，串行执行九类 69 个固定方法（失败/错误/跳过均为 0），再执行完整 JVM app build。
