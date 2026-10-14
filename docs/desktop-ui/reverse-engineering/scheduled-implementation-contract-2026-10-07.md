# Scheduled：当前 ASAR 实施契约（2026-10-07）

## 1. 范围与交付边界

本文件分析当前安装包的组件、状态、动作、表单及本地数据/执行接口，给下一批生产实现提供契约；本次仅交付本文件及同名 `.evidence.json`，未修改生产代码，未运行 Amper、构建或行为测试。

- **保留**：本机任务、显式 Harness Host 范围的任务、创建/编辑、真实暂停/恢复、手动运行、运行历史、真实会话导航、本地持久化。原版 `cron` 为独立任务，`heartbeat` 为既有会话上的周期跟进，两者应使用不同 typed target。
- **排除**：团队共享、云执行环境、云 HTTP automation CRUD、站点自动化、团队权限、Webhook/Slack/GitHub 触发、Habitat 云迁移。原版的 cloud `executor=local` 也不能直接当作 Harness 本机服务。
- 本机/Host 是 Harness 的实施范围；不能据此声称原版本地 `automation-*` bridge 已支持任意远端 Host 调度。Host 必须有自己的能力声明与调度 owner。
- 无原版 Scheduled 页面截图；此处是代码契约，不声明像素一致、交互实测或运行稳定性。

文中 `S-*` 指 evidence 的 ASAR snippet，`R-*` 指本次仓库源码快照；“实施要求”是 Harness 设计约束，不冒充 ASAR 已保证的行为。

## 2. 当前 archive 与证据追溯

| 项目 | 当前值 |
| --- | --- |
| archive | `/Applications/ChatGPT.app/Contents/Resources/app.asar` |
| bytes | `546868793` |
| SHA-256 | `22466838293554644d2dcb52aa128b389c713bed734900cd926bed76de47aa71` |
| header uint32 LE | `[4, 5779180, 5779176, 5779170]` |
| payload base | `5779188` |

evidence 记录相关 asset 的完整 SHA-256、size、ASAR relative/absolute offset，每个摘录的 UTF-8 **byte** `[start,end)`、absolute offset、摘录 SHA-256 与原文。offset 不使用 minified JS 的字符索引，也不依赖旧 inventory。安装包更新后必须重新提取核准。

主要资产为 `automations-page-d52bf0983109.js`、`automation-dialog-a1fe1c940cf2.js`、`automation-detail-panel-1f14031f0700.js`、`automation-frequency-section-5fa30e5f7829.js`、destination/row/delete/submit-tooltip 组件，以及 `.vite/build/main-C7cfj__D.js`、`.vite/build/bootstrap-CXJAEjVI.js` 和 app-shared bundle；完整指纹在 evidence。

## 3. 原版组件、状态和动作

### 3.1 页面与列表

原版 Scheduled 有按名称搜索、任务状态筛选（all/active/paused/completed）、创建入口、任务行、详情/编辑表单、运行历史。行展示 displayName、scheduleLabel、nextRunAt、unread/running/paused/selected，并提供 Pause/Resume/Run/Delete。`completed` 同时出现在混合云列表里，不能无条件映射为本机“已执行成功”。本地数据来自能力 `automations.local`、`list-automations` 与 inbox 数据；云数据另取并混合展示。见 `S-page-status`、`S-page-local-row`、`S-page-sources`。

实施要求：保留本机/Host 列表及真实任务状态；无任务是真实 Empty，不灌示例任务。搜索/选择/展开是 UI 状态，不能改变 ACTIVE/PAUSED。Loading、LoadError、Disconnected/Unavailable、Empty、Ready、Saving、MutationError 均可见且有相应有效操作。Retry 必须重新读取 owner 数据；不能只把 Error 改 Ready。

### 3.2 创建/编辑与保存

本地原版创建和更新走 `automation-create`、`automation-update`；成功响应中的 `item` 替换缓存。更新存在乐观显示，但失败恢复 previousAutomations/previousDraftStatus 并显示错误。创建成功后使用返回的真实 ID 导航详情，不是先生成假记录。见 `S-create-cache`、`S-update-rollback`、`S-local-bridge`。

实施要求：UI draft 与 scheduler effective snapshot 分离。只有 durable save 成功才切换 effective revision 和 nextRun。failed save 保留旧有效任务及用户草稿；保存期间的进一步编辑不能被较早响应覆盖。每个任务串行 mutation，加 expectedRevision/draftRevision/request token；暂停、编辑、删除与 Run now 不能互相越过。禁用重复提交时仍应保留可编辑草稿，或显式锁定整个表单并给 Saving 状态。

### 3.3 暂停、恢复、删除、手动运行

原版 `automation-update` 使用 `status=PAUSED/ACTIVE`。storage 对非 ACTIVE 将 nextRunAt/nextRunNominalAt 清空，恢复或改 schedule 重新求未来 occurrence。删除 bridge 返回可区分失败；底层有 store_unavailable、invalid_id、state_cleanup_failed、remove_failed、not_found 等结果。`automation-run-now` 调用真实执行入口后返回 success，heartbeat 成功会打开真实 targetThreadId。见 `S-store-update`、`S-next-run-state`、`S-store-delete`、`S-run-now-ui`、`S-local-bridge`。

实施要求：Pause 成功后阻止新的 claim，恢复重新计算未来时间；**Pause 不等于取消已运行的 agent**。删除需持久化 tombstone/删除结果并禁止后续 claim，是否保留历史必须单独声明；本契约选择保留历史，除非用户显式清理。Run now 是实际 execution command；按钮 pending、重复点击、timeout/结果不明均不能伪造成功记录或自动重发。允许 PAUSED 手动运行的策略应固定并测试；建议保留原版手动入口，但不得因此恢复周期状态。

### 3.4 运行历史与真实导航

原版本地历史取 inbox-items（bridge 默认 limit=200），按 automationId 过滤、createdAt 降序；详情有最新结果、Previous runs、Loading/无结果、read/unread、归档/恢复。run 行只有非归档且存在 threadId 时才直接打开会话；没有 threadId 时禁用直接选择，归档项使用恢复动作。见 `S-list-and-inbox`、`S-history-query`、`S-history-link`。

实施要求：运行记录来自 durable run ledger 与实际执行/会话结果，显示真实状态、触发来源、起止时间、错误、Host、thread/session ref。没有 ref 显示明确原因并禁用 Open；禁止 `${taskId}:${runId}` fallback。主线程已修复：`openRun` 返回 nullable，只接受非空白 chatRef，ScheduledTasksPage 缺 ref 行无 click 且说明“此运行没有关联会话”；必须保留此约束。导航还需带 Host/identity + sessionId，不能在当前另一个 Host 上打开同字符串 ID。历史分页需实际 cursor/hasMore；原版摘录只证明本地 limit=200，未证明本地游标分页。Show older 若仅展开已加载数据，应明确其语义。

## 4. 原版表单与 Harness 表单契约

| 字段/控件 | 当前 ASAR 事实 | Harness 实施要求 |
| --- | --- | --- |
| Name | `automation-title-input`，placeholder Scheduled task title；新建空 name 可由 prompt 压缩前 80 字形成有效名称 | 可显示自动名称建议；保存前确定真实 name；编辑不能因空值悄悄覆盖旧名 |
| Prompt | 多行编辑器，file mentions；`enterBehavior=newline` | 多行文本，Enter 换行；Save 用显式按钮；没有文件/mention 服务就解释 unavailable，不能用空回调 |
| Kind/target | cron project/projectless；heartbeat targetThreadId | sealed target；cron 选真实项目或 projectless，heartbeat 选同 Host 真实既有会话；不造线程 ID |
| Location | local/cloud 选择，云分支有团队能力 | 本机/显式 Host；团队/云不进入本批表单 |
| Model/reasoning | local cron 要求 model 与 executionEnvironment；heartbeat 要求 thread（原版还允许新建专用会话流程） | 从该 Host 真实 model catalog 选 providerId/modelId，reasoning 仅展示模型真实支持值；heartbeat 新建目标需真实 create 服务，否则仅既有会话 |
| Project/environment | project roots/cwds、projectless `~`、local/worktree 环境与 sandbox 提示 | 传 typed projectId/roots 给 executor 校验；`~` 是原版 UI sentinel，不能作为未经解析的执行目录；worktree/setup 无真实服务则 disabled 并解释 |
| Frequency | 本地 hourly/daily/weekdays/weekly/custom；cloud 增 monthly preset；heartbeat 的 hourly label 是 Interval；分钟选项由调用方传入 | 明确支持的 schedule subset；保留未知/raw 规则；不能把云 monthly/date/timezone 校验直接等同本地能力 |
| Notification | cron 默认 All runs，heartbeat 默认 Important updates，支持 failed_runs_only | 只有真实通知 service 可用才允许修改；否则保留存量值并解释 unavailable |
| Save requirements | trimmed name/prompt、有效 schedule；local kind 对应 model/environment/thread；tooltip 列 missingRequirements | 字段错误及 disabled 原因可见；同一 validator 被表单/repository/executor 使用 |

证据：`S-form-name`、`S-form-prompt`、`S-form-target`、`S-form-notifications`、`S-form-requirements`、`S-form-tooltip`、`S-frequency-options`、`S-frequency-source`。

### 4.1 Schedule 范围

原版并非简单 interval：native RRULE next-occurrence 支持 wall-clock 与 heartbeat interval 区分；有 COUNT=1 判定、MINUTELY/HOURLY interval 识别，以及 nominalRunAt 和部分规则的确定性 jitter。shared form 遇 local monthly 切 custom。云 schedule-draft 的 once、until、timeZone、两年上界属于其转换路径，不能照抄成已验证的本地规则。见 `S-native-recurrence`、`S-native-interval`、`S-frequency-source`、`S-form-target`。

Harness 首批必须公布能力：hourly interval、daily、weekdays、weekly 与经真实 parser 验证的 custom。若选定引擎还没有支持 custom、one-shot、timezone/UNTIL，则相应编辑控件明确 unavailable，保留已有 raw 规则且不激活执行，不能悄悄降成 daily。未来无 occurrence 是明确校验结果，不能拿 now+24h 补值。保存 canonical rule/timezone 与原始文本的关系必须可解释。

wall-clock 规则需 ZoneId，interval 需 duration/anchor；本地时区变更、DST 重复/缺失时间、闰日、周界、COUNT/UNTIL、时钟回拨均需确定策略。UI nextRun 由 schedule engine 的同一函数产生；不能自行用字符串拼接模拟。原版 jitter 是已观察事实，本契约不要求首批仿制 jitter。

## 5. 本地接口与云接口边界

| 原版入口 | 已观察数据/动作 | Harness 对接方向 |
| --- | --- | --- |
| `list-automations` | canonical items；存在 Habitat owner/migration 分支 | scoped repository.load；排除云迁移 |
| `automation-create` | service.create，回 item | validated draft → durable store → effective snapshot |
| `automation-update` | service.update，回 canonical item | expectedRevision mutation；Pause/Resume 同一 owner |
| `automation-delete` | service.delete | durable deletion/tombstone + claim fence |
| `automation-run-now` | native Nl → cron/heartbeat 执行入口 | service.runNow，真实 receipt/runId，不能返回 UI 自造成功 |
| `inbox-items` | 实际本地历史与 unread counts | ledger history API；仅实现真实存在的 read/archive 能力 |
| `/automations/save`、`/automations/set_status`、`/automations/remove` | `safePost` 云 HTTP、account/team 校验 | **排除**，不能包装成 LocalStore |

证据 `S-local-bridge`、`S-list-and-inbox`、`S-cloud-http`。以上是逆向接口，不要求 Harness 使用原版字符串协议；Harness 使用静态 compile-time typed service/registry，禁止反射、动态命令扫描或伪响应。

## 6. 持久化事实与实施要求

### 6.1 原版事实

native bootstrap 有 `automations/<id>/automation.toml`（version=1）路径、读/解析/序列化、ID 路径校验、未知 version 返回 null；SQLite automations 保存调度投影和 owner，automation_runs 以真实 thread_id 为主键保存运行状态/read/title/summary/时间。旧本地文件与 account/user/installation 归属数据库记录存在迁移与合并路径；因此不能概括为“只有 TOML”或“只有 SQLite”。见 `S-store-paths`、`S-store-read`、`S-store-write`、`S-store-ownership`、`S-run-schema`。

写文件为同目录临时文件后 rename；原版 catch fallback 会 rm 目标再 rename。**这不证明所有失败路径都保旧，也不证明 fsync/双存储事务或跨进程 exactly-once。** Harness 不应照抄删除旧文件的 fallback。创建/更新同时触发文件写入与 SQL 投影，不据此声称两者原子提交。见 `S-store-atomic`、`S-store-create`、`S-store-update`。

### 6.2 Harness 实施要求（新能力）

1. 独立 JVM store；版本化 envelope 与 Host/identity scope，stable 随机 ID；生产空配置为空任务列表。任务配置、scheduler state、run ledger 均持久化，不能只保存表单。明确唯一 canonical source，避免文件/数据库各自成为 owner。可选择事务数据库，或在声明单进程 owner 的前提下使用一个原子 snapshot 包含三者；存量未知/坏格式只报告，禁止启动时覆盖。
2. 同目录 temp → 写完整 → flush/fsync → atomic replace；不支持原子替换、权限不足、磁盘满、序列化失败均返回 typed failure，旧文件和旧 effective snapshot 保留。不能 delete-old 再 rename。并发读写由 mutex/CAS revision 串行化；若支持多个进程必须有真实 lock/lease，不能仅内存 flag。
3. Load 返回 Missing/Ready/Invalid/UnsupportedVersion/IoFailure；Invalid/UnsupportedVersion 不能“修复”为 empty 并自动写回。显示原始诊断，不丢未知字段；不支持的规则/字段保留、禁止破坏性保存，待显式迁移。
4. Save 返回 canonical item/revision 与 committed scheduler state；失败保持 draft（含后续新编辑）及旧有效配置。连续 Save/Pause/Resume/Delete 响应必须按 mutation token 与 expectedRevision 应用，晚到响应不能复活删除任务。
5. credentials 不入任务文件；持久化 provider/model 身份引用，执行时向同 Host 真实配置解析。通知和目录访问不靠 fixture。

## 7. 真实调度与执行契约

### 7.1 原版已有的真实路径

`main` 的 scheduler 读取 due tasks，受 capability 控制，有 tick 重入防护、heartbeat automation/thread in-flight 集合、interval 生命周期、启动 interrupted-run settlement 与 dispose abort。cron 推进 next occurrence 后调用 Fl/真实 appServerConnection；heartbeat readThread，检查 renderer eligibility、collaboration mode、连接仍当前，缺目标/忙时推迟。手动 Run now 与 scheduled trigger 共用执行路径并区分 runTrigger。见 `S-scheduler-owner`、`S-scheduler-lifecycle`、`S-run-now-native`、`S-cron-execute`、`S-heartbeat-execute`。

原版 cron 执行会查询模型并可能 fallback；Harness 设计选择是缺失指定 provider/model 时 Blocked 并解释，不静默改模型。原版 restart settlement/in-flight 集合是代码事实，不能提升成已证明的断电恢复或跨进程 exactly-once。

### 7.2 Harness 必须补齐

- **ScheduleEngine**：纯 typed occurrence 计算与 validate/preview，不依赖 UI。TimerService 最多提供 wake-up；每次醒来重新读取 durable due state，不把 callback 注册当任务保存。
- **SchedulerOwner**：本机 application/service 生命周期的唯一 owner，独立于 page、composition、route 和选中会话。配置持久化成功后更新 owner；关闭应用若没有 OS daemon，则停止本机调度并在 UI 明示“应用运行期间执行”，不能承诺退出后照常运行。
- **Claim/ledger**：以 `(hostScope, taskId, scheduleRevision, nominalOccurrence)` 唯一 claim；先原子落盘 claim/run 与下一 occurrence，再执行。manual request 使用独立稳定 requestId。至少保证本 owner 对同 occurrence 不重复 dispatch；外部 agent 无 idempotency 时不得承诺 exactly-once。
- **Executor**：cron 真实 create session → prompt → 收到 receipt → 真实完成信号；heartbeat 使用真实既有 session，与手动发送共享执行互斥/忙状态。typed Host scope、connection epoch、模型/项目/权限校验必须进入执行 fence；切 Host/登录身份不能把旧结果写到新 scope。
- **完成语义**：本机真实 awaitIdle 可用于完成，但失败/取消仍需 typed terminal outcome；remote `DeliveryAcknowledgedOnly` 仅代表投递，receipt/awaitIdle 的立即返回绝不能标 Succeeded。无 Host scheduler/结果协议时 Host task 为 Unavailable；不能在本机偷偷替远端服务调度。
- **暂停/删除竞争**：claim 前检查最新 revision/status；暂停成功后无新 claim。已 claim/run 保留真实运行状态，取消只能走真实 cancel API。暂停不是 Cancelled，删除不是 Succeeded。
- **恢复与误点火**：应用重启读取未完成 ledger，标 Interrupted/Unknown 并向 Host 查真实结果；未确认的 prompt 不能自动 resend。首批选择离线期间不逐次补跑，按当前时间求下一未来 occurrence，记录 missed/skipped；需要 catch-up 必须后续显式能力。sleep/wake/时钟跳变同样重算。
- **错误/重试**：永久配置缺失为 Blocked；读取/连接失败可显式 Refresh。运输结果不明为 Unknown，可查 ledger/历史，不自动重发。Run now duplicate click、重入 tick 和 route 重建不得启动第二次任务。

建议 typed API（设计草案，未实现）：`ScheduledTask(scope,id,revision,name,prompt,target,schedule,modelRef,status,notificationPolicy)`；`ScheduledRun(runId,taskId,scope,trigger,occurrence,configRevision,status,receipt,sessionRef,startedAt,finishedAt,error)`；`ScheduledRepository.load/save/setPaused/remove/history`；`ScheduledExecutionService.capabilities/runNow/observe/reconcile`。状态至少区分任务 Active/Paused/Blocked 与运行 Claimed/Running/DeliveryAcknowledged/Succeeded/Failed/Cancelled/Interrupted/Unknown。不能复用 UI Monitoring 作为“正在后台执行”的证明。

## 8. 当前 Harness 的具体缺口

调查先使用 CodeGraph，当前源码指纹/行范围记录在 evidence。

| 当前源码 | 真实现状 | 本批之后必须修正 |
| --- | --- | --- |
| `ScheduledTasksRepository.kt` | 同步内存 state；createTask 默认标题、`local-${size+1}`；无 draft/store/executor | 完整 typed form/repository/store，stable ID，保存成功才激活 |
| `toggleTask` | 仅选择并展开/收起 | Pause/Resume 要新 mutation，不能复用展开 |
| `retry` / `showOlder` | retry 仅 Ready；showOlder 本地增计数 | 接真实加载/历史语义，不能伪恢复或伪分页 |
| `openRun` | 主线程已改 nullable，仅非空白 chatRef 可导航；page 缺 ref 无 click 并解释原因 | 保留已修复约束，后续补 scoped SessionRef；不能重新引入 fallback |
| `ShellPages.kt` ScheduledTasksPage | 明示“当前客户端内存”；列表/搜索/创建/历史，无配置编辑/真实暂停 | 表单和 mutation 状态/消费者接线 |
| `modules/timer/.../TimerService.kt` | Context 生命周期的 timeout/interval/debounce/throttle coroutine jobs | 可作为唤醒组件；缺 RRULE、durable claims、恢复、运行账本、Host scheduler |
| `ConversationController.kt` | 真实 session/create/prompt/history/cancel 与 Host fence；remote 禁止 LocalIdle | 可复用底层 session 能力；页面 controller 的 selection/send lifecycle 不能充当后台调度 owner |

证据：`R-repository`、`R-page`、`R-timer`、`R-conversation`。仓库另有 backend Quartz/基础设施 job service；本次 CodeGraph 未建立其与桌面 Scheduled 的配置/调度/执行链路，不把它计为已实现的 Host 定时任务服务。当前缺口是可见实现及消费链路缺失，不声称全仓库所有代码均不存在类似能力。

## 9. 下一批实施顺序与验收门槛

1. typed model/validator/engine、scope 与能力定义；明确首批支持规则及 daemon 限制。
2. canonical JVM store + repository draft/effective mutation fence；真实无配置启动、未知版本与坏文件保护。
3. scheduler owner/ledger/executor 与 Connection/session 服务接线；先完成真实执行和完成语义，再开放可用 Run now/Active UI。
4. 配置表单、列表管理、历史与 scoped thread 导航；HarnessApp/Desktop 的实际 owner 只有一份，route 切换不得重启 scheduler。
5. 主线程串行跑 targeted shared/JVM/desktop 测试；不靠生产 fake 数据。fixtures 仅测试文件。

必测：校验空输入/未知模型/项目/无未来规则；创建编辑重启持久化；写失败保旧/保草稿；保存中编辑及连续 Pause/Resume/Delete/Save 的晚到响应；真正暂停不发新 claim；PAUSED 手动运行策略；缺 ref 不导航；历史真实 cursor；同 occurrence 多 tick/多窗口不重复；应用退出/恢复、missed policy、DST/时钟跳变；Host disconnect/rebind/identity 变化；delivery-only 不成功、模糊 transport 不自动重发；heartbeat 与人工 prompt 竞争。需要截图时覆盖普通/紧凑浅深主题和 Loading/Error/Empty/Saving/Paused/Blocked，但无原版截图只能声明结构验证。

## 10. 本次验证与未验证项

本次完成本地 ASAR 静态解析、当前 archive/asset/snippet SHA 与 byte offset 追溯、仓库 CodeGraph 对照，以及 JSON/摘录静态一致性核对。未执行原版任务、未抓原版界面、未构建 Harness，未验证文件系统 atomic-move 兼容性、断电恢复、跨进程 claim、通知服务、RRULE/DST 引擎、Host scheduler 与完成结果协议。

**表单、持久化与真实调度均是下一批生产实现缺口；本契约和 evidence 不表示这些能力已经完成。** 本文件及 evidence 交付后释放写权限，生产文件和其他文档保持本次未编辑。
