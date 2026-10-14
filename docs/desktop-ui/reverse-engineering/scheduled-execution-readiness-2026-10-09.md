# 定时任务执行：当前能力与剩余验收

日期：2026-10-09。最新状态：readonly-r2只读证据修复已通过父线程12类85/85串行验收，生产执行开关仍false。以下初始只读分析与失败历史保留，最终结论见末节。本文替代旧 `scheduled-executor-gap-2026-10-07.md` 的“当前缺口”描述；旧文保留为历史设计依据。

## 当前已经存在的能力

| 能力 | 当前实现和证据 | 结论边界 |
| --- | --- | --- |
| 真实本机执行链 | `ScheduledExecutor.kt`；`DesktopScheduledExecutorIntegrationTest.runNowPersistsDeliveryAndExactTerminalThenRestartDeduplicatesWithoutResending` | 通过真实 Session/Agent/Room、文件账本与受控 provider 的集成用例已有源码；本次未重新执行 |
| 到期执行与不补跑 | `activationAndDueTickDeliverOneOccurrenceAndGracefulRestartDoesNotBackfillIt` | graceful 同进程重启，不能代替崩溃恢复 |
| 精确终态、Unknown 后只读复核 | `ScheduledExecutor.start/reconcileUnknown/observe/shutdown` | receipt 不等于输入消息 ID；结果只依据对应输入的持久 TurnEnd |
| 停止与写竞争 | 集成用例覆盖真实 Dispatching 提交后停止、claim/dispatch 外部字节修改，以及挂起 provider 的重复 shutdown | 不得重复开发已有 fence/CAS 行为；剩余 create/receipt/terminal 边界须按实际缺口补足 |
| OS 领导锁 | `DesktopScheduledLeaderLeaseProcessTest` 两个子 JVM 用例 | 已有正常释放与强制终止持锁 JVM 的源码；该类明确不测试执行器崩溃恢复 |
| 日期及夏令时 | `DesktopScheduleEngineTest` 三个用例与 `DesktopScheduleCalendar` | 跨年、闰日、工作日、weekly、gap 前移、overlap 只选较早 offset、hourly 固定时长、无效时区拒绝均已有断言 |
| 默认执行门槛 | `main.kt` 的 `scheduledExecutionVerified=false`；配置默认 false | 页面 CRUD 和已有执行器不等于生产已开启定时执行 |

## 下一项最小有效工作

优先补**执行器真正跨进程崩溃恢复**，复用已有 Runtime/Room/JSON store/OS lease，不新增另一套调度器。只对测试拥有的临时 home 与子 JVM 操作；父线程必须看到阶段已真实提交再结束子进程，禁止用手工构造 ledger 代替执行链崩溃证据。

关键窗口：

1. create intent 已提交而创建结果未确认。新进程保持保守结果，禁止自动再次创建/投递。
2. Dispatching 已提交、prompt 尚未调用。已死进程的未调用证明不能沿用，接管保持 Unknown，不能重发。
3. 真实 prompt 已返回、Accepted CAS 尚未提交。磁盘缺少 receipt 不能作为重发理由，接管保持 Unknown。
4. Accepted 已提交、provider 仍未结束。真实 UserMessage/TurnStart 已 flush 且无精确 TurnEnd；新进程只观察持久证据，无证据保持 Unknown。
5. 精确 TurnEnd 已持久化、terminal ledger CAS 尚未提交。新进程只修复结果，不发 create/prompt。

每个用例需证明不同 PID、真实文件/Room 阶段、稳定 lock 文件、先竞争失败后确认旧 JVM 退出才可接管，以及重启后的零次新 create/prompt。只验证 lease 强制释放的既有用例不能抵扣这五个窗口。

若复核发现 create/receipt/terminal CAS 的独立真实竞争尚无覆盖，使用既有 `CommitGateStore` 等窄测试 seam 暂停在生产 store 前后；不改变 production transition 或削弱已有断言。生产发现具体故障后再单列最小修复写集。

执行开关最后处理：完成上述故障验收、相关已存在时间与 owner/store 回归，以及 main 的同 Runtime 接线检查后，才安排本机 Cron 启用和真实页面端到端验收。Heartbeat 仍需独立 session 准入协议；不能用新会话替代目标会话。退出应用后的后台执行不在当前本机进程契约内。

## 同日追加：开放回合恢复的来源风险

静态路径已确认：`DesktopScheduledSessionEvidence.inspect` 的历史会话分支调用 `RoomSessionPersistence.load`，后者会为开放回合追加并持久化 Interrupted TurnEnd。执行器 `start` 随后可能将该新事件作为恢复终态。这不能单凭“精确输入消息匹配”证明崩溃前已经有终态。下一合同已要求真实 Accepted 开放回合窗口在崩溃前持久化真实 UserMessage/TurnStart，使用原始 Room 事件区分接管前后证据；若复现违背 Unknown 语义，单列最小生产修复。此项目前是源码发现，未运行复现，不将已有同进程测试视作覆盖。

## 本轮只读来源

CodeGraph 当前源码输出保存在：

- `/tmp/harness-cron-executor-current-analysis-20261009.txt`
- `/tmp/harness-cron-integration-current-analysis-20261009.txt`
- `/tmp/harness-cron-unit-current-analysis-20261009.txt`
- `/tmp/harness-cron-calendar-current-analysis-20261009.txt`
- `/tmp/harness-cron-process-calendar-analysis-20261009.txt`
- `/tmp/harness-cron-executor-boundaries-analysis-20261009.txt`
- `/tmp/harness-cron-integration-boundaries-analysis-20261009.txt`

本轮不声称定时任务新增用例已通过，不把受控 provider 的执行链证据称为真实远端或原版像素验收。Worktrees 会话观察层 host-r5 已通过父线程 38/38 验收并归档。定时任务五窗口合同已派给同一 SHUAIAPI/gpt-6.1-sol / opencodex 工作者，仅新增两个测试文件；父线程将在释放后冻结同一源码并串行验收 48 项。具体基线与写集见 `scheduled-executor-crash-contract-2026-10-09.md`，生产 Cron 保持关闭。

## 首轮实际验收与修复（2026-10-09）

父线程已运行首轮真实跨 JVM 五窗口：3/5通过、失败2、错误/跳过0，其余43项未运行。Accepted开放回合已复现恢复误判；Dispatching的Agent计数断言混淆create与prompt。失败证据保留于 verification/scheduled-executor-crash-2026-10-09/initial-failed/。同一指定模型进入五文件只读证据修复合同，生产Cron保持false。

## 只读证据修复首轮验收

`readonly-r1` 冻结2,213输入后串行运行：真实跨JVM五窗口5/5、Room只读事件9/9；证据消费10项因测试fixture另开活跃Runtime持有的同路径Room而全部错误。其余旧回归未运行，不能宣称85项通过。生产数据库重复所有权保护正确拒绝了第二次打开。失败证据及交付/SHA保留于 `verification/scheduled-executor-crash-2026-10-09/readonly-r1-failed/`。

同一指定模型按 `scheduled-evidence-readonly-fixture-repair-2026-10-09.md` 只修测试接线，使行快照复用Runtime真正持有的DAO；生产保护与全部业务断言不变。修复释放后重新冻结，完整串行运行12类85项。Cron仍关闭。

## readonly-r2最终验收与下一门槛

父线程完整冻结2,213输入并串行通过12类85/85：crash5、readEvents9、只读evidence10、组合7、lease2、calendar3、owner13、store15、旧evidence3、持久contract7、header10、factory1；失败/错误/跳过0。archive脚本核对XML、交付SHA及完整工作树/候选SHA，最终证据保存于 `../verification/scheduled-executor-crash-2026-10-09/readonly-r2-final/`。

五个真实跨JVM窗口和相关持久/时间/owner/store回归已验收。剩余门槛是正式main启动接线共用、本机Cron生产启用、真实页面创建暂停任务/运行/启停到Room精确终态、运行历史和真实Thread导航。现有页面测试中的禁止执行Connection和内存store不能抵扣此门槛。

main仍为 `scheduledExecutionVerified=false`。下一批允许在合同内开启经过HostId/协议和Local绑定验证的本机Cron；配置默认false、Heartbeat不可用、Unknown不重发、关闭Unsafe保留Runtime/lease等既有边界保留。页面截图只作Harness布局证据，不能替代原版同状态或原生窗口验收。
