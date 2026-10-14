# Scheduled Cron 生产启用审查

2026-10-09最新结论：只读证据修复readonly-r2已冻结归档并通过12类85/85，包括真实执行器五窗口跨JVM crash、Room、日期/DST、Owner/store/lease与旧持久化回归。main的 `scheduledExecutionVerified=false` 仍保持；下一批为共用正式启动接线、本机Cron启用与真实页面到持久终态/Thread导航验收。当前证据与失败历史见 `reverse-engineering/scheduled-execution-readiness-2026-10-09.md`。下方保留此前审查记录。

日期：2026-10-07。范围：当前 executor、Local session adapter、desktop evidence、leader lease、测试，以及 reverse-engineering 下的 scheduled implementation contract 第 7 节和 executor gap 第 13 节。旧 gap 文档的实施前缺口不能直接当成当前事实。

本文在最初只读审查后按当前实现更新。Room adapter/evidence 集成测试已有 6/6 通过记录；本批另在隔离快照串行验证完整 executor + JSON ledger + leader + Runtime/LocalConnection/Room，3/3 通过，以及两个真实 JVM 的 leader lease 测试，2/2 通过，均无跳过。组合测试覆盖正常关闭后的重开，不证明 executor 进程崩溃恢复。

## 结论

继续保持 main 的 `verifiedCronExecution=false`。真实 Cron 创建、投递、精确终态和持久 ledger 的基础链路，以及下列 R1/R2/R3/R4/R5 修复均已实现。R4 Owner/桌面恢复交互分别 13/13、5/5，R5 最终容量回归 16/16 通过；剩余生产启用门槛是完整真实组合生命周期、进程 crash 与故障边界。Heartbeat 继续 unavailable，不能用 idle 检查代替与人工发送共用的 atomic admission。

## 已实现的基础

| 区域 | 当前代码行为 | 边界 |
| --- | --- | --- |
| claim | owner/revision CAS，immutable task snapshot、稳定 request/input ID，先落盘再 create/prompt；同 request 去重 | 单 task blocking run 防重叠 |
| delivery | CreatingSession → SessionCreated → Dispatching → Accepted；真实 connection.session | receipt 不是成功证据 |
| terminal | 指定 UserMessage.id → 同 turn 唯一 TurnEnd；真实 persistence.flush 后写 ledger | 不用 assistant 文本或 awaitIdle 判成功 |
| restart | 不 create/prompt；精确 evidence 修复 terminal，否则 Unknown | 不自动重投、不补跑 missed occurrence |
| shutdown | 同步停止 admission，有限等待，重复关闭可重核 Unknown；Unsafe 保留 runtime/lease | 不取消方法来假装副作用撤销 |
| isolation | scope 包含 Host/member/generation；invalidate 清公开状态，保留旧 fence 仅写最终证据 | 失去验证后本实例不能恢复启动 |
| store/lease | v2 durable snapshot、tombstone、坏配置保护、稳定文件锁 | 文件 CAS 与 leader 是不同职责 |

## 已实现修复与原始问题

以下复现描述保留为回归依据，旧源码行号是原始审查定位，不是当前源码定位。当前实现及回归以具体文件和测试方法为准。

### R1：Dispatching commit 后、prompt 调用前的停止窗口（已修复）

当前 `ScheduledExecutor.kt` 的 `uncalledPrompt` 与 `settleUncalledPromptLocked` 保留本生命周期的未调用证明，将已落盘 Dispatching 安全转换为 `Withdrawn`，保留 delivery intent 审计，不伪造 TurnEnd。撤回 CAS 失败时保留证明与锁，后续 shutdown/tick 可重试；真实 crash 或已经调用 prompt 的 Unknown 不适用此路径。`ScheduledExecutorTest` 已加入停止/失效、撤回 CAS 失败、crash intent 与已调用 prompt 异常回归。

原始问题：

源码：`apps/shared/src/im/hikaru/harness/client/scheduling/ScheduledExecutor.kt:254-262`；ledger 规则：同包 `ScheduledLedgerRules.kt:57-78`。

可复现顺序：Dispatching/deliveryIntent=true 已写入，shutdown/invalidate 停止 accepting，随后 deliver 在第 261 行写 Unknown 并直接返回，真实 prompt 从未调用。intent 不能清除、Unknown 不能变 Rejected，且没有该 input 的 UserMessage/TurnEnd；shutdown 第 188-204 行将永久保留锁，窗口即使没有实际运行也无法正常关闭。

现有 suspended-prompt 测试已经进入 prompt，suspended-create 测试尚未写 Dispatching，均未覆盖这个窗口。Dispatching CAS 自身使用 requireAcceptance=false，因此停止发生在 CAS 挂起期间也能进入这个分支。

建议在限定 scheduling 写集中增加可信的“本生命周期确定未调用 prompt”终结路径：保留历史 intent，不伪造 TurnEnd，单独表示投递已安全撤回，并在规则中限制该转换。若通过改变 reservation 语义让已提交 Dispatching 必须投递一次，则须先修订“每次调用前检查 live fence”的契约，不能擅自改成停止后投递。必须保留真正 crash/已进入 prompt 的 Unknown 防重发行为。

回归门槛：控制 Dispatching 写入后的暂停点，停止后 prompt=0；持久 run 有明确非成功结果；重读、重复 shutdown 可 Drained；模糊调用仍 Unsafe。

### R2：长运行或临时 evidence 错误后的进程内重核（已修复）

当前 `reconcileUnknown` 在 tick 中只读重核，按 run 限制次数并退避，每 tick 最多重核一个到期 run。终态 CAS 失败导致 admission 关闭后仍可重核，绝不重新 create/prompt；次数耗尽保持 Unknown，shutdown 拥有最终证据检查。已有超时后迟到终态、临时错误、终态/Unknown CAS 失败、次数与退避、挂起重核时有限 shutdown 回归。

原始问题：

源码：`ScheduledExecutor.kt:270-288`；默认 `ScheduledExecutionModels.kt:46-50`。

默认观察 300000ms，或 inspect 一次 Unavailable 后，observer 结束并写 Unknown。后续 tick 只检查 blocking run/推进 cursor，不 inspect；仅 start 和 shutdown 再重核。真实任务第 6 分钟结束或短暂 flush 故障恢复后，应用继续运行期间日志仍 Unknown、任务继续阻塞。增加超时只是延迟触发。

建议 bounded backoff 的只读 reconciliation 或真实 reconcile action：Unknown 可根据精确已 flush 的证据转 terminal，不能重新 create/prompt。有限 shutdown 与后台重核生命周期要协调，不能引入无法停下的 observer。补超时后迟到 terminal、一次 Unavailable 后恢复、失败 terminal CAS 后恢复测试。

## 其他具体可修复项

### R3：adapter 副作用入口独立 scope 校验（已修复）

当前 `LocalScheduledSessionAccess` 在 create/prompt 前验证 scope、target、model 与 session；Room 集成回归直接覆盖非法 scope 的零副作用。generation fence 仍由绑定的 executor/connection 生命周期负责；desktop evidence 不能单独证明 generation。以下为原始审查记录：

`apps/shared/src/im/hikaru/harness/client/scheduling/ScheduledSessionAccess.kt:32-50`：create 先真实创建，之后 flushCreated 才检查 scope；prompt 不检查 scope。当前 executor 在 claim 前调用 unavailableReason，正常消费链受保护，未发现正常链路可直接越过 owner 的证据；但 adapter 的 public 方法直接传 foreign Host/member 时会先造成副作用。

建议在 create/prompt 之前调用真实 scope/target admission，foreign scope 副作用计数为零。generation fence 仍由绑定的 executor/connection 生命周期负责：DesktopScheduledSessionEvidence 当前只验证 Host/member，不能声称它单独验证 generation。现有 Room admission 测试检查 unavailableReason/flush/inspect，没有直接测试非法 scope 的 create/prompt。

### R4：启动失败后的安全恢复 Owner 与产品入口（已实现并验证）

`ScheduledExecutor.kt:50-82` acquire 后读账本/恢复失败会保留 lease；第 41 行拒绝直接再次 start，直到 shutdown。保留锁是 fail-closed，不能在未证明不存在未知 delivery 时自动释放。main 只 start 一次，关闭安全后才释放；用户留在当前窗口无法恢复执行能力。

`ScheduledExecutorOwner` 保留原实例与完整 binding，显式恢复先 shutdown，只有 Drained 才由同一 factory 构造新实例。Unsafe、坏账本、未收敛投递与超时保留原实例、锁与 runtime；并发恢复不排队。invalidate/shutdown 先永久停止准入，挂起恢复不能迟到发布替代实例，未发布候选仍受跟踪并关闭。取消等待不会取消底层副作用方法来伪造安全停止。

HarnessApp 的恢复入口要求同一 verified connection 实例与完整 scope；替换 executor 不改变恢复 binding，失去验证永久 invalidate Owner。main 由 Owner 统一 start/shutdown，并同时使用 false gate 控制执行与恢复入口。恢复不绕过 factory 的 verifiedCronExecution 配置。

指定模型新增 Owner 与 13 项真实 ScheduledExecutor 生命周期测试，主线程隔离验证 13/13；LocalServiceTransportInteractionTest 5/5，包含恢复成功、transport 切换永久 fence 与坏账本保留原实例。全部无跳过，恢复测试 create/prompt 计数为零。受控 store/access/lease 测试不证明进程 crash。

### R5：跨 task 并发预算（已实现并验证）

`ScheduledExecutorConfig.maxConcurrentRuns` 默认 2，配置必须大于零。准入读取持久账本，所有 `blocksTask` 运行占用额度，包括 Unknown、创建/投递中记录以及已删除任务的历史；同 request 去重先于任务查找和容量检查。手动满额明确拒绝且不新增运行；定时满额以 CAS 持久化 `SkipCapacity` 与严格晚于当前时间的未来 cursor，不排队、不补跑。账本 CAS 冲突保持停止准入，不覆盖竞争写入。终态或安全 Withdrawn 成功持久化后释放额度。

指定模型新增 `ScheduledExecutorCapacityTest`，主线程在隔离目录最终验证 16/16 通过，包括同 request 并发重试、终态 CAS 失败仍占预算、全部精确终态释放额度；原 `ScheduledExecutorTest`、`ScheduledLedgerRulesTest`、`ScheduledRepositoryTest` 共 50/50 通过，无跳过。测试使用验证规则与 JSON round trip 的 CAS store、受控时钟和 adapter，证明容量/恢复规则，不证明文件系统锁或进程 crash。

原始审查：同 task 防重叠已经存在，但所有同时到期任务可以启动，缺少 Host 总容量。该问题以默认预算和明确 skip 策略修复。

## 测试覆盖与缺口

| 测试类 | 已有源码覆盖 | 仍需验证 |
| --- | --- | --- |
| ScheduledExecutorTest | 一次投递、request 去重、CAS conflict、restart Unknown、迟到终态、重复 shutdown、validation/store 挂起 fencing、suspended create、due/late wakeup，以及 R1/R2 新增 deterministic 回归 | 完整故障边界的真实磁盘/runtime 组合 |
| ScheduledLedgerRulesTest | Unknown 不丢证据、不重开 delivery、terminal 精确性/不可变、请求/occurrence 去重、阻塞历史不能移除，以及 Withdrawn 转换 | 真实 restart 与撤回组合 |
| LocalScheduledSessionAccessIntegrationTest（6 方法，已有隔离验证通过） | 真 Runtime/Gateway/LocalConnection/AgentLoop/Room；header durable、真实 inbox receipt、精确 terminal、新 reader 重读、不 restore Agent、provider failure、flush failure、模型/cwd/owner 检查、非法副作用 scope 拒绝 | 进程 crash 恢复；完整 executor 的故障路径组合 |
| DesktopScheduledExecutorIntegrationTest（最新 7 方法，隔离验证通过） | 真实文件 ledger、OS lease、Runtime/Gateway/LocalConnection/AgentLoop/Room；基础 3 方法，加 Dispatching 提交后安全撤回、accepted 挂起期间保锁与迟到终态、claim/Dispatching 外部字节 CAS 冲突；有序 shutdown 后 fresh reader 与新 executor 不重投 | executor 进程 crash、跨进程 executor recovery、create/receipt/terminal CAS 更多边界、DST/missed wakeup 故障矩阵 |
| DesktopScheduledSessionEvidenceTest | typed input/turn、重复/坏序号/坏时间拒绝、非成功 outcome | pending retry/open-turn repair 的 scheduler 组合 |
| DesktopScheduledStoreTest（15 方法） | 真 CRUD/迁移/坏文件/CAS/临时 fsync fencing/外部编辑/ledger 重读与删除 | rename 后 durability warning 的 executor 组合、实际 crash 边界 |
| DesktopScheduledLeaderLeaseTest（3 方法） | 同 JVM contender、alias normalize、loser release、稳定 inode | executor 故障组合；不可删除替换锁文件 |
| DesktopScheduledLeaderLeaseProcessTest（2 方法，本批隔离验证通过） | 两个真实 JVM 调用生产 lease；持锁拒绝 contender，失败 contender release 不解锁赢家；正常 release/退出及 holder 被强制终止后接管；全程 stable fileKey | 仅 leader 行为；不证明 executor crash recovery，也不证明所有文件系统/平台行为 |

Room adapter 的历史验证为 6/6；executor 组合最新为 7/7（替代之前同类 3/3，不累加），跨进程 leader 的历史证据为 2/2。生产 Cron 仍关闭，Heartbeat unavailable。

## 建议串行启用门槛

1. R1/R2/R3 修复与对应 deterministic 回归已实现；继续保留相应门槛，不能以其替代下面的真实组合验证。
2. **基础组合已通过**：真实临时 home 下 executor + 文件 store + leader + LocalConnection/SessionApi + Room；runNow 与 activation/tick 各一次，精确 terminal 写 ledger，正常 shutdown 后换 reader/executor 不重投。provider 与时钟受控，只用于测试。
3. 停止与 claim/create/Dispatching/receipt/terminal CAS 各边界；Unsafe 时窗口/runtime/锁保留，迟到 evidence 后重复 close 可 Drained。
4. crash/restart：CreatingSession、Dispatching、Accepted、session terminal 已 flush 但 ledger 未终态；不能重投；无证据 Unknown，有证据只修复原 run。pending LLM retry 不被 evidence load 自动续跑或伪成功。
5. **两进程 leader 子项已通过**：同稳定锁文件竞争、正常释放及 holder 强制退出后接管。仍需 CAS 与未知/坏版本、atomic move/fsync warning 失败的 executor 组合真实重读，不能在模糊 commit 后继续副作用。
6. pause/delete/edit 与 due/manual 竞争，tombstone 不复活任务；墙钟回拨、DST、休眠与 missed wakeup，不补跑/不重复投递。当前 nominal 去重忽略 task revision 更保守；回拨重遇已消费 due 时 duplicate 分支不推进 cursor，会重复 wakeup 直到超出 lateness 后跳过，应补显式推进或验证该边界。
7. **R4/R5 已实现并定向验证**：同 binding 显式安全恢复、默认跨 task 预算 2、满额持久 skip。**AJ 批次已补 UI 侧**：页面常驻 `scheduled-execution-model` 明示「只在本应用运行期间执行、无系统 daemon」，详情页 `scheduled-blocked-runs` 按真实账本给出进行中/结果未知计数与「不自动重投」说明；离屏截图与全量 `jvm-app` 642/642 通过（`verification/scheduled-execution-model-2026-10-11/entry-aj1/`）。仍缺**原生窗口**下的同状态视觉验收，以及不可恢复 Unknown 的应用内确认/清除通道。
8. main 的 verified local HostId/协议验证、同一 connection 实例、full scope 匹配、失去验证 invalidate 和 close gate 保持；只有确认的 explicit Local Cron 开放。

## 不应当重新扩大为阻塞项的已接受限制

- 基础 SessionApi 没有执行前 durable inbox barrier；现行契约接受模糊窗口 Unknown 不重发。不要求改 SessionApi 才能验证 Cron，也不承诺外部副作用 exactly once。
- evidence load 读取/修复 typed log，不恢复 Agent；pending retry 保守 Unknown 是限制，不是自动重投的理由。
- pause 不 cancel 已执行任务；无 daemon 时退出后不继续运行；relay/团队/云/custom RRULE/retry/cancel 扩展不属于当前 gate。
- 已知真实运行没有精确 TurnEnd 时 Unsafe 合理。R1 的问题是本生命周期已知未调用 prompt，却制造同样不可恢复的状态。

## 交付

新增测试由指定的 `SHUAIAPI/gpt-6.1-sol` 实施，主线程修正测试 reader 在原 Runtime 关闭前重复打开同 Room location 的生命周期错误后，串行验证两类测试，3/3 与 2/2 通过。生产源码未因本批改变，`main` 的 `verifiedCronExecution=false` 保持。

验证命令（同一隔离源码 `/tmp/harness-native-verification-20261007` 与构建目录）：

```sh
./kotlin test --build-dir=/tmp/harness-native-verification-build-20261007 -p jvm -m jvm-app --include-classes=im.hikaru.harness.desktop.scheduling.DesktopScheduledExecutorIntegrationTest
./kotlin test --build-dir=/tmp/harness-native-verification-build-20261007 -p jvm -m jvm-app --include-classes=im.hikaru.harness.desktop.scheduling.DesktopScheduledLeaderLeaseProcessTest
```

分别保存 JUnit 报告于 `/tmp/harness-native-verification-evidence-20261007/cron-composition/jvm` 与 `/tmp/harness-native-verification-evidence-20261007/leader-process/jvm`，防止后续同模块测试覆盖报告。此批未跑全量 desktop/Android/iOS 或视觉截图，不扩展为产品/像素验收。完整门槛未通过前不启用 Cron。

最终源码同步核对发现 Owner 静态收尾包含 start binding CAS、候选清理 finally 解锁与错误保留调整；主线程复制最新源码后重新合跑 Owner/Capacity，29/29（13+16）通过，无跳过。最终报告保存于 `/tmp/harness-native-verification-evidence-20261007/r4-r5-final/jvm`；桌面恢复交互亦按最终源码重新验证，报告仍保存于 `r4-desktop-recovery/jvm`。源码快照与当前 R4/R5 落盘文件逐字节一致。

## 最新同步：2026-10-07 真实停止与文件 CAS 组合

指定 `SHUAIAPI/gpt-6.1-sol` 新增四项组合，主线程审查、复制最新源码到隔离目录后串行运行。同类最终 **7/7**，无失败、错误或跳过；与工作区逐字节核对一致。未改生产执行开关。

| 新边界 | 实际证据 |
| --- | --- |
| Dispatching CAS 已提交、prompt 尚未调用时停止 | 闸门在生产 CAS 成功并 fsync 后暂停；初次 Unsafe 保锁，恢复后 Withdrawn 保留 intent 且 prompt=0；重复 shutdown Drained；fresh reader 无 UserMessage/TurnEnd，新 executor 不重投 |
| accepted 后 provider 挂起、观察超时及迟到终态 | 初次 Unsafe 时 OS contender 无法获得锁，Runtime/Agent 保留；Unknown 后 provider 真正完成并 flush 精确 input→TurnEnd；重复 shutdown 修复原 ledger 才 Drained/释放；fresh reader 与 restart 去重 |
| claim CAS 前外部字节编辑 | 同 revision/snapshot 的合法 JSON whitespace 修改并 fsync；生产 store 检测字节冲突，零 create、停止准入、竞争字节不覆盖 |
| Dispatching CAS 前外部字节编辑 | SessionCreated 已真落盘后编辑；生产 store 拒绝冲突，零 prompt，保留 SessionCreated 和竞争字节；shutdown Drained，fresh reader 仅 header，无 UserMessage/TurnEnd |

组合使用真实 DesktopScheduledStore、OS lease、Runtime、Session/Llm/Agent/AgentLoop 插件、Room、SessionApi/Gateway/LocalConnection。Provider 与提交闸门受控且无网络；闸门原样返回 delegate 结果，不伪造 receipt、CAS 成功或 evidence。测试 finally 的强制资源收尾不是安全释放证据。同一 Room location 一次只由一个 Runtime 持有。

日志：`/tmp/harness-cron-fault-composition-final-20261007.log`。JUnit XML：`/tmp/harness-native-verification-evidence-20261007/cron-fault-composition-final/jvm`。命令为上文同一隔离目录的 DesktopScheduledExecutorIntegrationTest；`git diff --check` 通过。基础 3 方法包含在最终 7 方法内。

有序同进程关闭/换 reader/换 executor 不等于进程 crash recovery。真正 executor 崩溃与跨进程恢复、create/receipt/terminal CAS 更多边界、DST/回拨/missed wakeup 及完整用户/视觉验收仍需完成；`verifiedCronExecution=false` 与 Heartbeat unavailable 保持。
