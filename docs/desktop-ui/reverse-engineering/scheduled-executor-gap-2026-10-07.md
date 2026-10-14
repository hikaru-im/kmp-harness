# Scheduled 真实执行服务：最小架构与缺口

> 历史设计文档。2026-10-09 当前实现与剩余验收请见 [定时任务执行就绪复核](scheduled-execution-readiness-2026-10-09.md)；本文中的“尚未实现”不代表当前仓库状态。

日期：2026-10-07。范围：本机 Host 的 Cron 新会话与 Heartbeat 既有会话执行。本文是基于当前仓库源码的分析与下一批实施建议，**没有实现 executor，没有运行构建、测试或故障注入**。

前置约束沿用 [Scheduled 实现契约](scheduled-implementation-contract-2026-10-07.md)：保留本机/Host 任务；排除团队、云环境；真实 session 引用才能导航；暂停阻止后续调度，取消是独立动作；未知执行不得自动重发。本次没有重新提取或校验 app.asar，原版证据追溯以该契约和对应 evidence 为准。

## 1. 当前可用能力与结论

当前 scheduling 批次提供 typed 任务、规则预览、draft/effective state、暂停任务 CRUD 和 versioned JVM 文件存储。它还没有持久化 occurrence、运行账本、Host 执行 owner、会话准入和重启 reconciliation。现有 `canActivate`、`canRunNow` 为 false；这些限制必须保持到真实执行链路完成。保存暂停任务不能表示定时服务已上线。

首个可实施 executor 的范围应限定为：

- 经验证的本机 Host，使用当前 harness home 内的任务与会话存储。
- 桌面进程和对应 Host runtime 存活期间执行；退出应用后不承诺后台运行。
- Cron 每次实际运行创建一个真实新会话，支持 projectless 或真实绝对 cwd。
- Heartbeat 只使用任务指定的真实 SessionId，不创建替代会话。
- 单进程领导者、任务默认不重叠、Heartbeat 忙时跳过、重启不补跑错过的 occurrence。
- 支持 hourly/daily/weekdays/weekly；不据此宣称任意 RRULE 已支持。
- Remote/Relay 保持 Unavailable，直至 Host 端有 durable dispatch、恢复和终态协议。

### 源码证据索引

下列路径相对仓库根目录；方法名是定位锚点。结论来自本轮 CodeGraph 返回的当前源码，行号可能随父线程后续修改移动。

| 当前文件/锚点 | 真实能力 | executor 缺口 |
| --- | --- | --- |
| `modules/timer/src/im/hikaru/harness/timer/TimerService.kt`，`timeout`、`interval`、`schedule`、`dispose` | coroutine delay；interval 在 callback 后等待；owner.effect 绑定生命周期；默认失败停止 | 不是 wall-clock cron；无 occurrence、claim、存储或重启恢复 |
| `modules/runtime/src/im/hikaru/harness/runtime/Context.kt`，`child`、`dispose` | 子 Context 继承服务并承担生命周期 | 需专门 Host owner；不能跟随页面切换销毁 |
| `modules/session-api/src/im/hikaru/harness/session/api/SessionApi.kt`，`DefaultSessionApi.create/prompt/history` | 新建 live agent、投递 prompt、读取 live history；receipt + awaitIdle | 无 typed turn result、durable dispatch、恢复入口、准入 lease |
| `apps/jvm-app/src/im/hikaru/harness/desktop/connection/LocalConnection.kt`，`createSession`、`LocalSessionPrompt` | Gateway options 映射；awaitIdle 委托真实 session prompt | Connection 层没有 busy 原子保留、restore、幂等 dispatch 或 turn outcome |
| `apps/shared/src/im/hikaru/harness/client/connection/RemoteConnection.kt`，`prompt`、`invoke` | socket request/response，timeout，Host/request 校验 | Remote prompt 的 awaitIdle 为 Unit；timeout 不证明未执行；requestId 不是 durable 去重键 |
| `modules/agent/src/im/hikaru/harness/agent/Agent.kt`，`followup` | NEXT_TURN/IF_IDLE 投递 | 忙时入队，不拒绝；不能拿它直接实现 Heartbeat 忙时跳过 |
| `modules/agent-loop/src/im/hikaru/harness/agent/loop/AgentLoopAgent.kt`，`send`、`awaitIdle`、`cancel`、`runTurn` | inbox + driver；记录 UserMessage/TurnEnd；取消整个 session driver/队列 | idle 检查和提交有竞争；awaitIdle 不代表成功；取消不按 Scheduled run 隔离 |
| `modules/agent/src/im/hikaru/harness/agent/Inbox.kt`，`insert`、`commit`、`replay` | InboxSpliced 事件和内存投影；恢复队列 | append 后即能启动 driver；无持久化屏障；receipt id 不是输入 Message.id |
| `modules/session/src/im/hikaru/harness/session/Session.kt`，`append`、`events` | mutex 内追加内存 log，发送 contained Appended 事件 | append 不等于持久化事务或 fsync；观察者失败不使 append 自动回滚 |
| `modules/session/src/im/hikaru/harness/session/SessionEventTypes.kt`，`UserMessageEvent`、`TurnEndReason` | typed turn、消息、completed/aborted/blocked/error/max-tokens/interrupted 终态 | 需把精确 run 输入关联到对应 turn，不能从会话整体 idle 推断 |
| `modules/session-persistence/src/im/hikaru/harness/session/persistence/RoomSessionPersistence.kt`，`flush`、`load`、`repairOpenTurn` | SessionPersistence；事务写 header/events；读取并修复中断 turn | plugin 注册 Flush，未自动监听每次 Appended；不与 Scheduled JSON 组成同一事务 |
| `modules/session/src/im/hikaru/harness/session/SessionStore.kt`，`load`、`attach`、`flush` | 加载 live session、生命周期绑定、聚合 flush 失败 | load 不创建 Agent；dispose 不能当成已确认 flush |
| `modules/agent/src/im/hikaru/harness/agent/AgentRegistry.kt`，`restore` | 从 session 重建 agent/inbox，发出 Restore | 不由 SessionApi 自动调用；需真实模型/options 恢复与验证 |
| `modules/llm-retry/src/im/hikaru/harness/llm/retry/LlmRetryPlugin.kt`，`apply`、`resume`、`decide` | Restore 可续跑 pending LLM attempt；同一 turn 内退避重试 | 恢复可能立即产生模型请求；不能同时重发 Scheduled prompt；其事件持久化仍需明确屏障 |
| `modules/bundle/desktop/src/im/hikaru/harness/bundle/desktop/DesktopBundle.kt`，`catalog`、profile | 静态 session/agent/persistence/llm-retry 注册；`harness-sessions.db` | 所读 catalog 没有 timer/executor；profile 可覆盖，需运行时能力检查 |
| `apps/jvm-app/src/main.kt`，desktop store/calendar 注入 | home 下 `client-scheduled-tasks.json`；LocalConnection；Host 生命周期 | 当前入口未建立 scheduler owner、恢复或 shutdown flush 链路 |
| `apps/shared/src/im/hikaru/harness/client/scheduling/ScheduledModels.kt`，`ScheduledSnapshot` | owner/revision/tasks/deletedTaskIds；scope 含 generation | 没有 next occurrence 和 run ledger |
| 同目录 `ScheduledStore.kt`、`ScheduledRepository.kt` | compareAndSet；Unavailable 默认值；clear/unbind + scope fencing；paused CRUD | UI fence 不替代 Host 后台执行 owner；runNow/activate 当前不可用 |
| 同目录 `ScheduleEngine.kt` | strictly future 规则计算，calendar DST 策略 | 只计算 preview，不能 claim 或记住某次到期 |
| `apps/jvm-app/src/im/hikaru/harness/desktop/scheduling/DesktopScheduledStore.kt` | version=1 严格 schema；文件 CAS、短期协作锁、atomic replace、文件/目录同步 | 无执行账本；短期写锁不是 scheduler 生命周期 leader lease |

## 2. 最小静态架构

以下组件是**建议新增的职责，不是已存在 API**。采用静态 typed 注册和显式构造依赖。executor 不应通过 UI route、反射查找或临时 timer callback 拼装。

| 建议组件 | 最小职责 |
| --- | --- |
| Host Scheduled owner | 持有生命周期、本机身份、runtime generation、进程领导锁；恢复完成后才接受 claim |
| Execution store | 同一原子快照保存 task、next nominal、claim/run 状态、去重记录和 tombstone；严格版本与 CAS |
| Session admission coordinator | 对人工提交、Scheduled 提交、restore、cancel 共用 session 准入与顺序规则；处理忙状态 |
| Local session execution adapter | 创建/恢复真实 session；持久化提交屏障；输入关联；精确 turn 终态；受控 cancel |
| Schedule driver | 读取 durable next nominal、到期 claim、推进下一时间、记录 skip；timer 仅负责唤醒 |
| Run observer/reconciler | 从持久化消息/turn 证据恢复状态；监听只用于唤醒；更新 ledger，不制造成功 |
| UI repository projection | 展示 effective 任务与真实历史，调用 Host typed actions；无需打开页面也能执行 |

owner 放在 application/Host 根 Context 下的独立 child。页面关闭、搜索、选中任务和 sidebar 导航不影响它。Cron 创建的会话也不能被临时 run child 的 dispose 无意销毁，应由真实 session/agent 生命周期管理。

当前 DesktopBundle 未注册 timer；下一批需显式安装 TimerService 或提供 typed clock/wakeup 实现。TimerService.interval 是 fixed-delay，不能以“每隔一小时 callback”代替 durable hourly occurrence。唤醒后必须比较存储的 nominalDue 与当前时间。

## 3. 身份、连接与领导者

### durable owner 与 live fence 分离

- durable owner 继续使用 HostId + MemberIdentity；它必须绑定当前解析的 harness home/installation。本机验证不能只依赖字符串 `desktop-local`，该默认值不是全局 installation ID。
- `generation` 属于 live scope，不放入 occurrence 去重身份。重连不应把同一 occurrence 变成新任务运行。
- 每个异步阶段带 owner、binding token、connection/runtime generation、leader epoch；晚到返回只能进入其原 owner/run 的受控 reconciliation，不能修改当前 UI 或另一 Host。
- UI `clear()/unbind()` 清理投影；Host runtime 是否仍允许执行由其独立 owner 生命周期决定。Member 切换、local Host 验证失效、profile 被替换时停止新 claim。

### 最小领导机制

在受支持的本机文件系统上，对规范化 harness home 持有 executor 生命周期排他锁；同时保留 store CAS 锁处理短事务。第二个进程拿不到领导锁时只读/Unavailable，不调度。当前 DesktopScheduledStore sidecar tryLock 只保护一次写入，不能直接宣称已保证单 scheduler。

领导者 epoch 用于本进程异步 fencing；它不能阻止已发出的模型或工具副作用。不要仅凭“lease 超时”抢占仍活跃的本机执行进程。锁失败、文件系统不支持、身份不明时 fail closed。退出时先停新 claim、收敛/记录在途 run，再释放领导锁。

## 4. durable 执行数据

首批建议把 v1 文件显式迁移到 v2，将任务和执行投影放入**同一个 atomic snapshot**，使用既有 CAS/atomic replace/fsync 思路。另一个可行路线是迁到本机事务数据库，但必须先明确唯一 canonical store 和迁移协议；不能让 JSON 与数据库分别充当有效配置源。本文不要求复用 session 表，也不把两种存储包装成虚构的跨库事务。

v2 最少需要：

| 数据 | 最小字段/语义 |
| --- | --- |
| Task execution projection | taskId、scheduleRevision、nextNominalEpoch、最后一次 claim、active runId、skip/missed 摘要 |
| Scheduled claim key | `(owner, taskId, scheduleRevision, nominalOccurrence)`；唯一/不可再次提交 |
| Manual claim key | owner/taskId + 稳定 requestId；重复点击/重试传输仍指向同一次请求 |
| Run | 稳定 runId、trigger、claimKey、retryOf、创建时配置快照和 revision、阶段、timestamps、typed error |
| Input binding | 独立 dispatch token、稳定输入 Message.id、receipt inbox id、turn number、持久化 event seq |
| Session binding | 已确认的真实 SessionId/cwd/model context；创建确认前 ref 为 null；不得导航预留 ID |
| Recovery | accepted/terminal durability barrier、pending retry 证据、最后 reconciliation、Unknown/Interrupted 原因 |
| Delete/dedup | tombstone、已消费 claim 的必要去重信息；删除任务不删除历史或在途证据 |

运行配置是不可变快照：target、prompt、provider/model、cwd、权限/执行上下文和 schedule revision。任务后续编辑不能修改旧 run 的解释；revision 更新后 next occurrence 从编辑生效后的未来时间计算，不回放旧 nominal。

迁移/坏文件规则：未知版本、坏 JSON、坏 required field、owner 不匹配都保留原文件并报告；不能自动重写为空，不能遇到错误改成内存成功。v1 Paused 任务迁移后仍 Paused。历史裁剪需要保留去重证明和 Unknown run；简单删除 ledger 可能让墙钟回拨时重复执行。

### rename 后 durability warning

当前 store 在 atomic rename 后目录同步失败时返回 `Committed(durabilityWarning)`，不是 Failed rollback。executor 不能把它当“未 claim”再试，也不能在持久化保证不明时直接发 prompt。应保留已提交状态、停止新 dispatch，待 durability/reconciliation 得到确认后再恢复。文件更新和 session Room 提交之间永远存在 crash window，下一节用 ledger 和输入证据处理，不宣称 exactly-once。

## 5. 会话提交和终态的三个硬缺口

### 5.1 receipt 不是输入消息身份

DefaultSessionApi.prompt 使用 `agent.followup(message)` 的返回值填写 receipt.messageId。Inbox.insert 创建的是 inbox item ID；它不是 `Message.id`。Inbox 恢复时 next item counter 从当前 pending items 重建，已全部消费后该形式的 ID 不能作为会话全生命周期唯一去重依据。

ledger 需要自身 runId/dispatch token 与稳定输入 Message.id。通过 `UserMessageEvent.message.id` 找到真实 turn，再找该 turn 的 TurnEnd。receipt 只保留作投递证据。若 Message ID 不具备强制唯一约束，adapter 必须额外验证/建立 durable dispatch 去重，不能仅生成一个 ID 就假定 prompt 已幂等。

### 5.2 append/prompt 返回不等于 durable acceptance

Session.append 在 mutex 内更新内存 log，并发送 contained Appended；SessionPersistence plugin 注册的是 Flush，没有每次 Appended 自动写库。AgentLoop.send 在 inbox insert 后能立即启动 driver。因此“先 prompt，返回后 flush”仍可能在 flush 前已经产生模型/工具副作用。

若要承诺执行前 durable acceptance，需要 Host dispatch adapter 建立：**准入保留 → durable Inbox/输入关联 → 明确提交成功 → 才唤醒 driver**。既有 SessionApi.prompt 无法提供这个保证。首批可以保持该基础 API 不变，采用第 13 节的 durable delivery intent + 单次调用 + 模糊结果 Unknown 方案，提供实际本地执行和持久日志；不能把 prompt 返回后 flush 宣称成执行前 durable acceptance。LLM retry 的恢复/重试事件也要进入可等待的持久化协议。

不要在 Session.append 的同步 Appended listener 中直接调用 `flush(session)`：append 仍持有 session mutex，flush 读取 events 可能回入同一锁。可以在 append 返回后的提交路径设置屏障，或使用有顺序的异步 writer + 可 await 的 commit barrier，并与 driver 启动协议配合。监听失败不能默默当作 durable success。

### 5.3 awaitIdle 不等于成功

AgentLoop.awaitIdle 等待 driver 结束；runTurn 会把 Throwable 转为 ErrorTurnEndReason，把 cancellation 写成 Aborted，然后回到 IDLE。它也可能继续处理后续人工 inbox，导致 awaitIdle 等到另一个 turn。RemoteConnection 的 awaitIdle 则直接返回 Unit。

Scheduled outcome 必须来自**精确绑定的 turn**：

| TurnEnd 证据 | 建议 run outcome |
| --- | --- |
| CompletedTurnEndReason | Succeeded：仅表示该 turn 正常结束，不证明外部副作用只发生一次 |
| ErrorTurnEndReason(failure) | Failed，保存真实 typed failure 摘要 |
| BlockedTurnEndReason | Blocked，不能冒充完成 |
| MaxTokensTurnEndReason | Incomplete/MaxTokens，不能冒充正常成功 |
| AbortedTurnEndReason | Aborted；仅在有明确取消请求绑定时展示 Cancelled；保留 cause |
| InterruptedTurnEndReason | Interrupted，区分 load 修复、shutdown 和异常中断 |
| 没有可信终态/关联 | Unknown 或仍在原 turn 内恢复；不能只因 idle 写 Succeeded |

AgentLoop 的通用 coroutine cancellation 可写 UserTurnAbortCause，因此该 cause 本身不能证明用户点击了 Stop。取消请求需要独立 intent/requestId 证据。终态事件应先完成 session durable barrier，再 CAS 写 ledger terminal；若后者失败，重启从已持久化的精确 turn 重建。

## 6. Cron：新会话一次运行

### 推荐执行顺序

1. 校验本机 owner、scope、领导锁、任务 Active、执行服务能力、真实 provider/model 和权限。cwd 必须是真实允许访问的绝对目录；projectless 保持明确的无项目配置，不虚构路径。
2. 原子检查任务 revision、due、无该 claim、无重叠 run；写 Claimed、不可变 run 配置与 next nominal。manual 使用稳定 requestId，跳过 schedule due 条件。提交未确认时不产生副作用。
3. 写并确认 CreatingSession/Dispatching 边界后，使用真实 SessionApi/Host adapter 创建新 Session。可以预留稳定 SessionId 以便恢复定位，但只有实际 create 确认后才能设置可导航 ref；当前 create 没有幂等恢复保证。
4. 持久化 session header/options，并把真实 sessionRef 写入 ledger。模型/cwd 不可悄悄回退到默认值。观察者预先注册；后续用 snapshot reconciliation 补漏，避免极快完成丢事件。
5. 在 session admission 内确认 durable prompt dispatch 意图和稳定输入 Message.id，再通过注入的真实 connection.session.prompt 调用一次。保存 receipt/turn 关联并请求 session flush。该调用当前能在 flush 前执行；第 13 节以 Unknown 和禁止自动重发处理窗口，不声称已实现 durable inbox 启动屏障。
6. 监听/读取精确 turn，区分 running、LLM retry、真实 terminal。完成 session flush 后更新 ledger terminal，释放 active-run 位置。

默认同一 task 不重叠：有 Running、恢复中的 run 或 Unknown 未解决时，不开启下一个 Cron 会话，记录 Busy/RecoveryBlocked skip。任务并行上限另由 Host 控制，不能通过无限创建 Context 达成。

### 创建与投递的模糊边界

create 返回丢失、ledger sessionRef 提交失败、prompt 返回丢失都不能马上再 create/prompt。先按预留 ID、输入 Message.id、session header/inbox/events 寻找实际证据。当前公开 SessionApi 只列 live agents，不足以查找所有 persisted sessions；需本机 persistence adapter 提供受 owner 限制的查找/恢复能力。无证据只能 Unknown，不制造 ref 或成功。

## 7. Heartbeat：既有会话一次运行

1. target 必须包含该 owner 下真实 SessionId。live list 中没有它不等于不存在；先查询 durable session，验证归属、cwd、模型/agent preset 与可恢复性。
2. 如需 restore，通过真实 SessionPersistence.load + AgentRegistry.restore。SessionStore.load 只加载 Session，不恢复 Agent。恢复 options 必须来自可信持久化配置；默认 restore options 不足以保证 provider/model/reasoning 全部还原。缺配置时 Blocked，不替换默认模型。
3. restore 之前先绑定 ledger 和 admission：LlmRetryPlugin 的 Restore listener 可能立即继续 pending attempt。恢复原 run 时不能同时投递新的 Heartbeat，也不能将 pending retry 先修成最终 Interrupted。
4. 通过所有提交者共享的 Host session admission 判断忙状态。首批忙时 SkipBusy，并推进该 nominal；不进入 inbox 等待，不制造大量排队 Heartbeat。
5. idle 且恢复完成时 claim、持久化输入、投递一次、观察该输入对应 turn，流程沿用 Cron 的 acceptance 和 terminal 屏障。

`agent.status == IDLE` 后调用 followup 存在 TOCTOU；只有 scheduler 内部 mutex 也挡不住人工 submit。需要把人工 prompt、定时 prompt、restore、cancel 接入同一准入协议。首批可保守地把 scheduled turn 执行期间对同一 session 的后续人工提交设为 Busy 并说明；若要支持排队，必须新增明确队列和取消语义，不能暗中依赖现有 followup 自动入队。

Heartbeat 不新建 fallback thread，不改既有会话模型，也不把 task 保存时的 provider/model 覆盖到 session 上。真实会话不可恢复、配置缺失、权限失效时 Blocked，保留任务/历史。

## 8. 单次执行与重启恢复

### 最小 run 阶段

`Claimed → CreatingSession(Cron) → SessionBound → Dispatching → Accepted → Running/RetryWaiting → Terminal`

任何有副作用可能发生但证据不足的阶段进入 Unknown。Heartbeat 已有 session 可跳过 CreatingSession。阶段是建议 ledger schema；不要混用当前 UI save 状态。

| 崩溃位置 | 恢复要求 |
| --- | --- |
| durable claim 前 | 无运行；按未来规则继续，不补跑旧 due |
| 已 Claimed，且证明尚未进入任何副作用阶段 | 可恢复同一个 runId，或记 Interrupted；不能生成第二 claim |
| CreatingSession/Dispatching 已 durable，但调用结果不明 | 查真实 session/input；无法证明时 Unknown，不自动重发 |
| session 已创建，ledger ref 未保存 | 使用真实 create identity 查找并绑定；不能假设不存在后再次创建 |
| inbox/input 已持久化，Accepted ledger 尚未写入 | 从持久化 inbox/events 重建原 run；不要重复 prompt |
| 输入仅在内存、模型/工具可能已经执行 | Unknown；当前接口的硬缺口，不能通过 idle 或空 history 猜测 |
| pending LLM retry | 恢复原 turn/attempt，由 LLM retry 继续；不创建新 Scheduled run |
| open turn 没有可恢复 retry | Room load 可修成 Interrupted；读取修复后证据并持久化 ledger |
| session terminal 已 durable，ledger terminal 丢失 | 按 input→turn→TurnEnd 重建 terminal，不再执行 |
| 坏 ledger/未知版本/owner 不匹配 | executor Unavailable，保留文件，不能清空后重新 claim |

一次 claim/CAS 可以避免同一 scheduler 重复认领，不能保证模型、网络、工具副作用 exactly-once。真正消除“已执行但没持久化”窗口需要 Host dispatch 的 durable inbox 去重和执行前屏障；外部工具自身仍需幂等或补偿。

### 启动顺序

1. 解析真实 home/本机身份，取得领导锁，检查 required runtime services/profile generation。
2. 严格加载/迁移 store；此阶段 UI 可读，但 activate/runNow 不可用。
3. 按原 run 的 session/input 证据 reconciliation；恢复前准备 admission/outcome observation，避免 Restore 自动续跑脱离账本。
4. 未解决 Unknown 阻止对应 task/session 的新运行；向用户暴露原因。
5. 对没有阻塞的 Active 任务重新计算未来 next nominal，记录 missed/skipped 摘要，不 catch up。
6. 完成 durable 更新后开始 timer wakeup 与新 claim。

### 墙钟、休眠和 DST

ScheduleEngine.nextOccurrence 是 strictly future，只用于设置/推进 next nominal；每次 tick 调 next(now) 会把正在到期的一次跳过。driver 应读取 durable nominal，判断是否 due，再 claim 和推进。

允许正常 timer 延迟的最大 lateness 必须作为明确配置/策略固定；超过窗口或重启/长休眠后的历史 occurrence 记 missed，不集中补跑。可保存 missed 时间范围和计数，避免为长期离线写成千上万行。墙钟回拨通过 nominal 去重；forward jump 按 lateness 策略处理。daily/weekdays/weekly 遵循现有 calendar gap 位移、overlap 较早 offset 且每日期一次；hourly 为 anchored duration，不把它解释成每天当地整点 cron。

## 9. 编辑、暂停、删除与取消竞争

- task 写入、due claim、activation 和 manual claim 通过同一 canonical store CAS 排序；claim 前重新检查有效 revision/status/tombstone。
- 暂停提交成功后不接受新的 claim；已认领且准入的 run 可继续，单独显示在途状态。暂停不调用 agent.cancel。
- 删除留下 tombstone 和历史；晚到 terminal 只更新对应 run，不复活 task，不替换当前草稿。
- 编辑只影响未来运行；在途 run 使用 immutable snapshot。保存失败保留 effective 任务与草稿，不推进 schedule revision。
- Stop 是真实 cancel intent。当前 `cancel(keepInbox=false)` 影响整个 session 并清理 NEXT_TURN/NEXT_STEP；不能给 Heartbeat 展示为“仅取消本任务”。首批需要 session 准入阻止混入其他提交，并明确会话级取消范围。
- cancel 的 timeout/断线不是 Cancelled；仍需精确终态。若 terminal 与 Stop 竞争，已有 durable terminal 优先，取消请求可以记为已无在途 turn。
- profile/runtime 替换与离线触发 live fence；原连接晚到返回不能驱动新 owner。已发出的外部副作用无法由 fence 撤回，必须保留旧 run 证据。

## 10. 日志、历史与重试

### 历史与诊断

真实历史至少展示 trigger/nominal、runId、实际开始/结束时间、阶段/终态、skip 原因、真实 sessionRef 和错误摘要。没有 chatRef/sessionRef 的行禁止点击，沿用现有 nullable openRun 约束，不用 taskId/runId 伪造 threadID。

run ledger 保存 immutable input 配置以支持恢复；普通诊断日志只记录 owner/task/run/turn/revision、阶段、屏障与错误 code，不把 prompt、credentials、provider secret 或完整工具输出灌入日志。正文和输出由真实 session history 展示；ledger 和 session 都要遵循本机数据权限，不能跨 Member 读取。

不能用人工填充的 succeeded fixture 形成生产运行历史。Skipped、Blocked、Unknown 是真实服务结果，应独立于“执行成功”。

### 重试边界

| 场景 | 首批方案 |
| --- | --- |
| store/read/验证等确定尚未产生副作用的暂态失败 | bounded backoff；每次重检 scope/revision，不制造新 run |
| commit durability 不明 | 停 dispatch，reconcile；不能当失败重新 claim |
| create/prompt timeout，可能已经执行 | Unknown，不自动重发；要求恢复证据或显式用户处理 |
| LLM retry policy 内部 retry | 同一 run/turn/attempt 链，使用现有恢复；不能叠加 Scheduled 自动 prompt retry |
| 明确 terminal Failed/Blocked 后用户选择 Retry | 新 runId + retryOf + 新 manual requestId；明确使用原配置还是当前配置，记录选项 |
| Unknown 后用户要求重新执行 | 明示前次可能已执行；显式创建新 run，保留 Unknown，不能把旧 run 改成未执行 |

下一期周期触发是新 occurrence，不是前一次 run retry。第一批没有通用“网络错误自动再 prompt”策略。恢复 adapter 必须理解 Room 的 open-turn repair 和 LlmRetryPlugin 的 pending recovery，不能各自重复执行。

## 11. 退出与 runtime 重新加载

shutdown 顺序建议：停止新 claim/RunNow → 标记服务 draining → 按明确策略等待在途或请求真实 session cancel → await session durability barrier → 写最终 ledger/Unknown → 释放 executor/timer owner 和领导锁 → dispose Host。发生 flush failure 时保留可恢复状态并报告，不写 Succeeded。

不能用进程 finally 的“关闭资源”推导持久化已完成；当前 main 所读退出路径没有该 scheduler 协议。profile reload 也需要 generation fence、drain/rebind 和能力重检，不能让旧 observer 使用新 SessionPersistence。用户关闭窗口而进程仍存活时是否继续由真实 General/desktop 生命周期决定；scheduler 不另造隐式后台常驻服务。

## 12. 下一批最小实现与验收门槛

按依赖顺序实施，能力完成后再开放 Active/RunNow：

1. v2 execution store/migration、claim/ledger/next nominal/tombstone，保持坏配置保护和暂停 CRUD。
2. Host application owner、领导锁、clock/timer 注册、profile/scope fencing。
3. session delivery/evidence adapter + 全提交者共用 admission；保持基础 create/prompt 不变，确认 provider/model/options 恢复，增加 precise turn outcome。
4. Cron 执行、Heartbeat 忙时 skip/真实 restore、reconciler、cancel、drain。
5. UI capability/history/retry 接入真实服务；Remote 仍不可用。

必须串行验证的具体风险：

- claim、create、session bind、inbox commit、driver start、terminal flush、ledger terminal 各边界 crash injection；证明不自动重复 prompt。
- Heartbeat 与人工 submit/cancel/restore 竞争；尤其 idle 检查到投递之间的窗口和 keepInbox=false 的队列影响。
- restore 的 pending LLM retry 自动续跑与 ledger reconciliation，不重复创建或投递；open turn repair 后 outcome 一致。
- two-process 同 home 领导锁、CAS、scope 换绑、profile reload、晚到返回隔离。
- pause/delete/edit 和 due/manual claim 并发；保存失败保 effective/草稿；tombstone 不被终态复活。
- unsupported filesystem、atomic move/fsync 失败、rename 后 warning、损坏/未知版本及迁移中断；以实际磁盘重读验证，不只测内存。
- restart/休眠/墙钟回拨/DST/长延迟不补跑，任务不重叠和 Busy/Unknown 阻塞策略。
- 精确 Message.id→turn→TurnEnd 关联；receipt inbox id 重用；极快完成先于订阅；awaitIdle 正常返回但 turn Error/Aborted。
- 原始没有 sessionRef 的 run 不可导航；没有真实服务时 activation/runNow 持续禁用并说明原因。

本次仅完成静态源码分析与方案文档。未验证真实磁盘持久性、进程互斥、恢复顺序、调度时钟行为或 executor runtime；不能据此声明真实调度已经完成。

## 13. 交给主线程审核的首批执行 API 与写集

### 新的实施边界

主线程反馈：前批 shared 调度 10/10、desktop 存储/时间 10/10、真实 CRUD 路由 2/2 已实际通过，所有构建结束。这是主线程提供的验证结果，本分析没有重复运行。下一批目标明确为**可执行、持久运行日志的本地调度**，不继续停在 paused CRUD。

基础 shared/core SessionApi、Gateway、LocalConnection 的 create/prompt/cancel 均保持不变。执行器消费注入的真实 `connection.session`；额外 session event/persistence/restore 证据由 desktop scheduling 内的 adapter 读取当前 Host runtime，不能把 Connection.history 或 awaitIdle 包装成虚假的 typed success。新 child 读接口由 Wegener 管理，本执行批次不修改它。

### Claim、delivery、重启、shutdown 语义

- **Claim**：同一 v2 store 原子检查 owner/task revision/status、唯一 occurrence/manual requestId、无重叠；同时提交 run 配置、next nominal。只有确认 durable commit 后才进入副作用阶段。冲突要重读，不能覆盖 UI 草稿或 executor 账本。
- **Delivery**：先 durable CreatingSession（Cron）/Dispatching intent，再调用真实 create/prompt 一次。每次调用前检查 live fence；方法已经开始后不能假设取消 coroutine 撤销了副作用。receipt 表示 Accepted，不是 Succeeded，也不是执行前 durable acceptance。
- **Restart**：加载真实任务和历史，恢复未来调度；仅从未进入副作用阶段且有可信证据的 Claimed 恢复同一个 run。CreatingSession/Dispatching/Accepted 的模糊状态先 reconcile；不能证明时 Unknown，不自动重发。匹配 durable terminal 可修复日志；pending LLM retry 保持原 run/turn。Unknown 阻止该任务/会话重复执行，提供明确的人工处理入口。
- **Heartbeat**：共用人工提交的 admission 时才开放；目标 live idle 且 owner 验证通过才投递。首批持久会话无法正确 restore/options 不全时 Blocked，不 fallback create。共享准入还未接线时仅 Heartbeat 不可执行，Cron 可真实运行，说明具体缺失能力。
- **Shutdown**：先停止新 claim，默认 Drain(timeout)，不默认 cancel 用户会话；超时记录 Interrupted/Unknown 并保留 delivery 证据，等待方法/observer 收敛，关闭唤醒后才释放领导锁。若仍有可能执行的不可取消方法，结果必须报告 UnsafeToRelease，父层不可在同进程另启 executor 冒充已停止。进程被强制终止后的 reconciliation 仍按 Unknown 规则。显式 CancelOwnedRuns 仅对确认拥有准入的在途 run 调用真实 session.cancel，终态靠证据判定。
- **终态**：有匹配 TurnEnd 才给 Succeeded/Failed/Blocked/Incomplete/Aborted/Interrupted；只有 awaitIdle 则记 IdleUnverified（日志明确“会话空闲，执行结果未核实”），不写成功。无法读取 evidence 时必须暴露降级能力，不能制造终态。
- **重试**：前 dispatch 的安全暂态错误可 bounded retry；进入副作用阶段后不自动再 prompt。用户显式 Retry 创建新 runId/requestId + retryOf 并保留前次风险；周期的下一 occurrence 与 retry 分开。

### 建议稳定构造与动作 API（审核后实现）

下列是提案签名，尚未写入产品源码。使用已有 ScheduledScope、ScheduledOwner、ScheduledTask、ScheduleEngine 与客户端 SessionApi。新类型名固定如下，具体 sealed result 字段在实施时按上面的语义展开。

```kotlin
class ScheduledExecutor(
    store: ScheduledExecutionStore,
    engine: ScheduleEngine,
    access: ScheduledSessionAccess,
    clock: ScheduledClock,
    wakeup: ScheduledWakeup,
    leader: ScheduledLeaderLease,
    coroutineScope: CoroutineScope,
) {
    val state: StateFlow<ScheduledExecutorState>
    suspend fun start(scope: ScheduledScope): ScheduledStartResult
    suspend fun runNow(taskId: String, requestId: String): ScheduledClaimResult
    suspend fun retry(runId: String, requestId: String): ScheduledClaimResult
    suspend fun cancelRun(runId: String, requestId: String): ScheduledCancelResult
    suspend fun shutdown(policy: ScheduledShutdownPolicy): ScheduledShutdownResult
    fun invalidate() // 同步停新准入并 fence 晚到响应；随后 await shutdown
}

interface ScheduledExecutionStore : ScheduledStore {
    // 对 task CRUD 与 executor 更新共用一个版本和原子文件，不另设配置源。
    suspend fun claim(scope: ScheduledScope, request: ScheduledClaimRequest): ScheduledClaimResult
    suspend fun updateRun(scope: ScheduledScope, update: ScheduledRunUpdate): ScheduledRunWriteResult
    suspend fun readExecution(owner: ScheduledOwner): ScheduledExecutionReadResult
}

class LocalScheduledSessionAccess(
    sessions: SessionApi, // 注入真实 connection.session，不实现假 SessionApi
    evidence: ScheduledSessionEvidence,
    admission: ScheduledSessionAdmission,
) : ScheduledSessionAccess

class DesktopScheduledSessionEvidence(
    context: Context, // 当前本机 Host runtime；profile 替换时重新构造并 fence
) : ScheduledSessionEvidence

class DesktopScheduledLeaderLease(home: Path) : ScheduledLeaderLease
```

`ScheduledSessionAccess` 负责目标校验、准入、一次 create/prompt/cancel 与 evidence reconciliation；`ScheduledSessionEvidence` 提供 snapshot/精确 turn 观察/flush/受控 restore。不能把 restore 接入伪造成功的默认实现。API result 区分 unavailable、busy、conflict、stale scope、durability warning、accepted、unknown；`shutdown` 区分 drained、证据已保留但结果未确认、unsafe to release。

既有 `DesktopScheduledStore(path: Path)` 构造保持，升级实现 ScheduledExecutionStore/v2；既有 `ScheduledRepository` 构造保留默认执行服务 Unavailable，可追加有默认值的 executor/actions 依赖，父层将**同一 store 实例**注入 repository 和 executor。Task CRUD 保留返回 snapshot 内的 ledger/projection；不能沿用仅 copy tasks 的逻辑抹掉运行日志。Page/Navigator 现有参数和 tags 保持，新增能力与真实历史 action；不能放空 callback。

### 请求审核的限定写集

只修改/新增下面包内文件，既有已通过测试继续保留：

- `apps/shared/src/im/hikaru/harness/client/scheduling/*.kt`：执行模型/ledger/typed API/engine driver/admission、repository capability/history、Page 真实历史。
- `apps/shared/test/im/hikaru/harness/client/scheduling/*.kt`：claim、delivery、recovery、shutdown、晚到响应、heartbeat busy/cancel 等（fake 仅测试）。
- `apps/jvm-app/src/im/hikaru/harness/desktop/scheduling/*.kt`：v2 durable store、leader lease、clock/wakeup、真实 session evidence/flush/restore adapter。
- `apps/jvm-app/test/im/hikaru/harness/desktop/scheduling/*.kt`：实际磁盘重读、迁移/坏文件、锁/故障窗口、本机真实会话执行与日志。
- 本文 `docs/desktop-ui/reverse-engineering/scheduled-executor-gap-2026-10-07.md`：实现后补实际边界/验证记录。

父线程负责 main/HarnessApp 生命周期、verified local Host/runtime/context 注入、人工 prompt/cancel 与共享 admission 接线，以及串行构建测试。本执行批次不抢写这些文件，不修改 SessionApi/Gateway/AgentLoop/Inbox/persistence 核心模块，也不改旧 scheduled 包或 ShellPages/AppShell。若 evidence 无法在这个范围内取得真实结果，交付明确降级状态/缺口，由主线程决定新增范围，不能偷偷改底层协议。
