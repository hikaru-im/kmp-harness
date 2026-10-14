# 定时任务执行器跨进程崩溃恢复：实施合同

状态：父线程已运行首轮真实跨 JVM 五窗口：3/5通过、失败2、错误/跳过0，其余43项未运行。Accepted开放回合已复现恢复误判；Dispatching的Agent计数断言混淆create与prompt。失败证据保留于 verification/scheduled-executor-crash-2026-10-09/initial-failed/。同一指定模型进入五文件只读证据修复合同，生产Cron保持false。

当前唯一任务仍是本合同。首轮工作者在上下文压缩后误回已验收插件任务，两个新增源码和本合同交付报告均未生成；旧插件报告不算本切片交付。父线程已纠正并拆分实施顺序：先保存真实子进程 helper，再补五个父 JVM 用例，最终核对两个文件并释放。阶段交付不视作完整释放，不启动构建验收。

阶段 1 已实际保存 `DesktopScheduledExecutorCrashProcessHelper.kt`（595 行，SHA `fbe86fe7e5d04b51e5aac12e28c753004bed32857630b25992d45ee256e3cb67`），`write_set_released=false`。阶段 2 工作者在 contextCompaction 后再次引用过期插件上下文；父线程用最新直接消息重新明确本合同和两文件写集，工作者已确认继续五窗口父测试。父线程再次核对原有 2,209 个输入，无源码漂移。尚未编译或测试，不能记作五窗口已通过。

最终释放：父测试 655 行、5 个独立 `@Test`，SHA `2479d86ec46dcbb155aa720be228634098c99e2d1e6c831988b38479468e5725`；helper 保持上述 SHA。最终有限静态修正匹配真实 TurnStart 顺序，并核对恢复阶段持久协议及失败清理日志。报告 `/tmp/harness-scheduled-executor-crash-delivery-20261009.json` 与 `.md` 明确 `tests_run=false / builds_run=false / write_set_released=true`。阶段 2 第三次压缩跑偏后的旧插件输出同样无效；父线程重新纠正后取得本合同交付。冻结候选 `/tmp/harness-scheduled-executor-crash-parent-candidate-20261009-initial`，构建目录同名 `parent-build`；目标 48 项串行验收，crash 类失败时停止并保留证据。

## 本轮派发基线

- worker：`local/01a11a54-1694-7fb3-8007-37c434531314`，显式模型 override 为 `SHUAIAPI/gpt-6.1-sol`。
- 完整基线：`/tmp/harness-worktree-host-observation-parent-candidate-20261009-host-r5`，2,209 个输入；manifest SHA `c5c567897852b08496f529e396018acfdbbb8dc6b735cc41a4027a6ff0005de7`。
- 基线 build：`/tmp/harness-worktree-host-observation-parent-build-20261009-host-r5`，供父线程冻结后复用。
- before 清单及完整 manifest 副本：`/tmp/harness-scheduled-executor-crash-baseline-20261009/`。两个授权新增文件均不存在，before SHA 为 null；父线程已核对工作区全部 2,209 输入与基线一致。
- 当前唯一实施任务为本合同；已验收的 Worktrees 和插件交付不属于本轮写集。

## 已有能力与唯一目标

当前执行器、文件 CAS、精确持久 TurnEnd 观察、日期计算和 OS 领导锁均已有实现。`DesktopScheduledExecutorIntegrationTest` 的重启仍是 graceful 同进程；`DesktopScheduledLeaderLeaseProcessTest` 只验证租约，不验证执行器崩溃。现状详见 `scheduled-execution-readiness-2026-10-09.md`。

本切片通过真实子 JVM 的执行器，在实际提交窗口暂停后强制结束**测试创建的进程**，由另一个 JVM 读取同一临时 home、Room、JSON 账本和稳定锁文件，证明重启不重复创建或投递，且仅凭精确持久证据修复结果。不得用手工构造在途 run、写入假 TurnEnd 或同进程重新构造 fixture 代替崩溃链路。

## 精确写集

仅新增：

1. `apps/jvm-app/test/im/hikaru/harness/desktop/scheduling/DesktopScheduledExecutorCrashProcessTest.kt`：父 JVM 用例、进程管理和断言。
2. `apps/jvm-app/test/im/hikaru/harness/desktop/scheduling/DesktopScheduledExecutorCrashProcessHelper.kt`：子 JVM 入口、真实 Runtime/Room/Gateway/SessionApi/AgentLoop/受控 provider、阶段 gate 和测试协议。

已有生产文件、既有测试、module.yaml、main.kt 不改。若发现生产故障，先报告可复现的实际边界与必要修复写集，由父线程安排修复。文档由父线程维护。派发前父线程另存 before SHA 与当前完整构建基线；本合同不会自动授予旧基线。

## 必须覆盖的五个窗口

| 窗口 | 暂停点及父线程独立确认 | 接管结果 |
| --- | --- | --- |
| CreatingSession 提交后、创建结果未确认 | 真正 CreatingSession CAS 已完成；生产 create 尚未返回，可在调用前或真实创建后返回前挂起，并明确断言选定边界 | Unknown；零次新 create/prompt；原 requestId 不重建 |
| Dispatching 提交后、prompt 尚未调用 | 真正 Dispatching CAS 已完成，deliveryIntent=true，真实已创建的 Session 存在；prompt 计数为零 | 无法沿用已死进程的未调用证明，保守 Unknown；零次新 create/prompt |
| prompt 返回后、Accepted CAS 提交前 | 真实 SessionApi.prompt 已返回且 UserMessage.id 是 run.inputMessageId，候选 Accepted 写前暂停；磁盘仍 Dispatching、无 receiptId；provider 未结束 | Unknown；收据缺失不能成为重发理由；零次新 create/prompt |
| Accepted 提交后、provider 未结束 | 真正 Accepted CAS 已完成且真实 provider 仍挂起；通过真实 persistence.flush 保存已由 AgentLoop 生成的 UserMessage/TurnStart，Room 无对应精确 TurnEnd | Unknown；不恢复 Agent 执行；零次新 create/prompt；接管时修补的 Interrupted 不得充当前一进程已有终态 |
| 精确 TurnEnd 已持久化、终态账本 CAS 前 | 真实 provider 正常完成且 Room flush 后已有对应 UserMessage→TurnEnd；终态 CAS 写前暂停；磁盘仍在途 | 接管仅修复终态，保持 run/request/input/session/receipt 身份；零次新 create/prompt |

至少五个独立 `@Test`；用例名须对应窗口。正常链必须走真实生产服务；允许测试装饰器仅在生产 store/access 的真实调用前后暂停并记录，返回值和 transition 原样传递。不增加生产测试开关，不 monkey patch 内部 phase，不绕过真实 CAS。

## 派发前发现的具体恢复风险

2026-10-09 父线程静态复核：`DesktopScheduledSessionEvidence.inspect` 在 live store 无目标会话时调用 `context.persistence.load`；`RoomSessionPersistence.load` 调用 `repairOpenTurn` 并 flush，新补写的 `InterruptedTurnEndReason` 又可被 `terminalEvidence` 当作对应输入的终态。此发现是源码路径证据，尚无跨进程复现通过结论。

第四窗口必须保留真实已持久的开放回合，不得让 Room 只剩空 header 来绕过此边界。恢复前后独立读取原始 Room 事件，区分旧 JVM 已写入的 TurnEnd 和新 JVM 修补的事件。若现有生产路径把无终态运行改为 Interrupted，保存失败证据并报告最小修复写集；不把预期改为 Interrupted，不用替身 evidence 隐藏 production.load 的副作用，不在本两测试文件合同下修改生产代码。测试父进程自身的证据读取也不能调用会修补的 load。

## 进程与持久证据协议

- 两个 JVM 都用同一临时 home 和同一真实 Room DB、生产 DesktopScheduledStore、DesktopScheduledLeaderLease；记录 holder/contender PID，断言彼此及父 JVM 均不同。沿用已有 classpath 收集方式，不能假设仅 `java.class.path` 在 Amper 的 URLClassLoader 下完整。
- gate 通过结构化输出/文件标记报告 runId、阶段、原/候选 revision、真实提交确认、create/prompt/provider 计数。父 JVM读生产 JSON store核对磁盘阶段；Room 内容在安全的只读时刻或 holder 退出后由新 Runtime 独立核验，避免为断言同时建立冲突的 Runtime 归属。
- contender 在 holder 活着且持锁时真实启动并拒绝接管；失败启动不能改账本、创建 Session 或启动 provider，也不能释放 holder 的锁。确认 holder 到达窗口后，只结束本 fixture 注册的 holder，等待其退出，再让 contender 构造新的执行器生命周期接管。
- leader.lock 始终存在，支持 fileKey 的文件系统上 identity 不变；不得删除锁文件或靠重建文件取得锁。接管前后核对 owner/home，以及 run 的 id、requestId、inputMessageId、candidateSessionId、sessionRef、receiptId 和快照 task 身份。
- 首四窗口重启保持 Unknown、仍占用准入额度。同 requestId 读出 duplicate；新 requestId 拒绝同任务重叠；多次 tick/reconcile 不重新 create/prompt。receipt ID 不能代替 inputMessageId 作为精确终态依据。
- 终态窗口由新 Runtime/Room 读取真实事件，断言 USER_MESSAGE.id、turn 与 TURN_END.seq/turn/reason 对应；错误 input/receipt 的检查不得被误认可。只修复账本结果，不恢复 Agent，不调用 create/prompt/provider stream。重复接管或重复请求保持单次投递。
- counters 不只依赖父进程可丢失的内存；子 JVM 报告每次真实副作用，恢复 JVM单独报告零计数。任何不支持的 classpath/启动/超时均明确失败，不 silent skip。所有等待有有限 deadline（默认阶段 30 秒、清理 10 秒），无猜测 sleep。
- 所有子进程立即登记到 fixture；finally 先结束并等待所有测试子进程，再关闭 reader/executor/Runtime/线程。子进程未退出时保留临时 home 和诊断，不删除其锁。Unknown 的强制 fixture 清理不是生产安全关闭证据。不得操作用户应用进程或真实 home。

## 父线程验收与交付

工作者只写源码，不运行 build/test，不创建新线程，不 reset/stash/clean/commit；源码定位先 exact-file CodeGraph。完成限定 whitespace 检查，写 `/tmp/harness-scheduled-executor-crash-delivery-20261009.json` 和 `.md`，包含实际 model/provider、派发基线 before 与最终 after SHA、两个文件写集、实际 @Test 数、`tests_run=false`、`builds_run=false`、`write_set_released=true`、窗口/gate 映射及限制。释放后停止写入。

父线程冻结完整源码，串行运行新进程类及相关现有 executor 集成、lease process、calendar/owner/store 回归；使用 testcase.classname、精确数量、零失败/错误/跳过检查 XML，保存日志和同一源码 manifest。只有当前切片证明的新边界才算新增完成，不重复累计历史测试轮次。

生产 `scheduledExecutionVerified=false` 本切片不改。Heartbeat 准入、退出应用后的后台执行、真实远端 provider 及原版像素不属于本次证据。完成测试后再单列生产本机 Cron 启用与页面端到端合同。

## 本轮只读源码依据

- `ScheduledExecutor.start` 只读恢复映射、`deliver` 的 create/dispatch/receipt 顺序、`observe` 的精确终态提交和 `commit` 的 CAS：`/tmp/harness-cron-executor-full-contract-review-20261009.txt`。
- 真实 Runtime/Room/Gateway/AgentLoop fixture、ObservedAccess、CommitGateStore、持久事件核验：`/tmp/harness-cron-integration-full-contract-review-20261009.txt`。
- 既有跨进程 lease 与日历测试：`/tmp/harness-cron-process-calendar-analysis-20261009.txt`。
- 持久恢复副作用：`/tmp/harness-cron-access-evidence-crash-review-20261009.txt`、`/tmp/harness-cron-room-repair-review-20261009.txt`、`/tmp/harness-cron-repair-open-turn-review-20261009.txt`；真实 create/prompt 适配：`/tmp/harness-cron-session-access-full-review-20261009.txt`。

上述源码分析已完成；本合同实施授权就绪，定时任务新增测试尚未运行。父线程计划沿同一新 manifest 串行验收 crash 5、integration 7、lease 2、calendar 3、owner 13、store 15、evidence 3，共 48 项。
