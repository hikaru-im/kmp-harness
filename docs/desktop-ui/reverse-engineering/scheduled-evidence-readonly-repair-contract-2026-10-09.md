# 定时任务持久证据只读修复：实施合同

状态：readonly-r2已由父线程冻结并完成12类85/85验收。以下保留本批实施合同。模型 `SHUAIAPI/gpt-6.1-sol`，provider `opencodex`，工作者 `01a11a54-1694-7fb3-8007-37c434531314`。工作者只实施和静态检查，不 build/test；父线程冻结后串行验收。

## 实际失败与修复目标

首轮真实跨 JVM 五窗口为 3/5、失败 2、错误/跳过 0。Accepted 开放回合恢复前只有真实 TurnStart/UserMessage/chunks；恢复时 load→repairOpenTurn→flush 新增 seq8 StepEnd、seq9 Interrupted TurnEnd，账本误变 Interrupted，预期必须保持 Unknown。原始事件、XML和日志归档到 `verification/scheduled-executor-crash-2026-10-09/initial-failed/`。其余43项因串行门槛未执行。

第二失败在父测试 assertGate 的 `agentCount == prompted`：真实 `DefaultSessionApi.create` 调用 `context.agents.create`，Dispatching窗口已经创建1个Agent，但 prompt/providerStreams均0。仅将此处Agent数与created对应并注明真实语义；保留所有 prompt/provider零计数、恢复零Agent及身份/准入断言，不改五窗口预期。

## 精确写集：五文件

1. `modules/session-persistence/src/im/hikaru/harness/session/persistence/RoomSessionPersistence.kt`
2. `apps/jvm-app/src/im/hikaru/harness/desktop/scheduling/DesktopScheduledSessionEvidence.kt`
3. 新增 `modules/session-persistence/test/im/hikaru/harness/session/persistence/SessionPersistenceReadOnlyEventsTest.kt`
4. 新增 `apps/jvm-app/test/im/hikaru/harness/desktop/scheduling/DesktopScheduledSessionEvidenceReadOnlyTest.kt`
5. `apps/jvm-app/test/im/hikaru/harness/desktop/scheduling/DesktopScheduledExecutorCrashProcessTest.kt` 仅上面的Agent计数断言及说明。

helper、普通load的恢复语义、schema、其他既有测试、module.yaml、main.kt与生产Cron开关不改。保留全部dirty/untracked，不reset/stash/clean/commit，不操作真实home或用户进程。定位源码先CodeGraph。

## API与消费顺序

- `SessionPersistence.readEvents(id: SessionId): List<SessionEventEnvelope>?` 默认null，表示不支持能力；不把缺失或坏会话当作空成功。
- Room实现复用mutex、disposed、readSnapshot和validateSnapshot，核对header.id；读取前后检查取消。只返回已验证的持久事件快照，不store.load、repairOpenTurn、flush、发布lifecycle或恢复Agent。
- readSnapshot读取events时的CancellationException必须原样传播，其他失败沿用明确SessionException；不改错误为静默空列表。
- evidence历史分支直接readEvents，缺能力返回Unavailable，禁止fallback load。没有对应TurnEnd返回Pending。
- live分支先判断精确终态候选，无候选返回Pending；有候选才flush真实session，然后调用readEvents并从持久结果重新提取证据。flush/读取失败返回Unavailable，取消继续传播。禁止仅靠flush前内存证据宣布终态。
- exact input/turn/seq/time/reason提取保持；已由旧进程持久的Completed和Interrupted均认可，不屏蔽所有Interrupted。

## 必要新增测试

使用真实临时Room证明开放回合重复readEvents不补TurnEnd、不改DAO行、不发布live会话/lifecycle；持久Completed/Interrupted和未知ignorable事件仍可读取。缺失、坏事件、错误header、disposed、无能力与取消保留明确语义，必要异常seam可用DAO装饰器。

evidence覆盖历史开放回合Pending且store/Room不变、历史精确Completed/Interrupted、错误input、无能力不fallback load、live终态flush后重读持久证据、flush失败/读取失败及取消。不要重复纯提取测试；只测新增边界。所有runtime/database清理用finally。

旧普通load开放回合修补7项、header目录10项、factory1项由父线程回归。五窗口的真实进程、强杀、锁、Room、计数、身份和Unknown准入断言全部保留。

## 基线与交付

基线 `/tmp/harness-scheduled-executor-crash-parent-candidate-20261009-initial`，2211输入，完整manifest和五文件before副本见 `/tmp/harness-scheduled-evidence-readonly-baseline-20261009/`。不得覆盖父线程已保存的失败历史。

先实际保存两个生产修复和新测试，再完成静态核对；不要长篇重复分析。写 `/tmp/harness-scheduled-evidence-readonly-delivery-20261009.json` 与 `.md`，含model/provider、baseline_manifest、五文件before/after SHA、两新类的实际Test名称/数量、tests_run=false、builds_run=false、write_set_released=true。释放后停止写入。父线程负责相关48项、新测试与持久回归；全通过后另列生产Cron启用和页面端到端合同。

## 父线程最终验收

`readonly-r2`：2,213冻结输入，12类85项全部通过，失败/错误/跳过0；五文件交付SHA与工作树、冻结候选均核对。最终交付为 `/tmp/harness-scheduled-evidence-readonly-delivery-20261009-r2.json`，原交付及全部日志/XML/manifest已保存于 `../verification/scheduled-executor-crash-2026-10-09/readonly-r2-final/`。Room fixture仅改真实owner DAO生命周期接线，10项测试正文逐字保持。首轮和readonly-r1失败历史独立保留。

本合同完成；生产开关仍false。下一合同单独处理本机Cron启动接线与真实页面端到端。
