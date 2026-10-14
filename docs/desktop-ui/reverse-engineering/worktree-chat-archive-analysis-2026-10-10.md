# Worktrees 关联聊天归档与恢复：实施前分析

日期：2026-10-10。后续实施模型固定 `SHUAIAPI/gpt-6.1-sol / opencodex`。本文是下一批分析，尚未实施或验收归档功能。仅覆盖已确认范围内 Worktrees 的关联聊天操作；独立归档管理页仍待定。

> **后续变更（2026-10-13）：**Rail 重构中 **Recent 页面与其消费方 `LocalRecentSessionsPage.kt` 已删除**。
> 本文中涉及「Recent 列表 / Recent 同步」（第 23、25、34、55 行等）的部分已不再对应生产 UI；
> 归档的持久写入、恢复与 Worktrees 页面操作仍与代码一致。本文件保留为当时批次的记录。

## 当前 ASAR 证据

已重新校验本机 app.asar 与 app-shared asset 的完整 SHA256。来源、字节偏移和精确函数见 `worktree-chat-archive-evidence-2026-10-10/manifest.json`；相关工作树删除前固定核验见 `worktree-chat-pin-evidence-2026-10-10/manifest.json`。

| 函数 | 当前可确认的前端行为 | 不能由此推断的事实 |
| --- | --- | --- |
| `_nn` | 区分 ephemeral、缓存中聊天与未载入聊天，调用归档前处理，确定 cwd 与是否清理工作树 | App Server 内部如何停止 Agent、写数据库或移动 rollout |
| `ynn` | 记录 pending archive；调用 archiveThread，特定缺 rollout 情形走 inactive archive；成功后尝试取消固定、驱逐聊天缓存、广播归档 | 缓存隐藏等于持久归档；取消固定失败等于归档失败；工作树可以安全删除 |
| `vnn` | 准备已有 owner stream、移除 pending archive、调用 unarchiveThread；重新 hydrate 并确认已载入；可选恢复短期 undo 的 pin placement | 恢复必然重新发送 prompt；任意旧 pin 都应自动恢复 |

原版明确分开持久请求、缓存投影、固定副作用与恢复载入。工作树定向删除还会先在目标锁内重读固定集合，再归档关联聊天，最后调用工作树删除。这个顺序只是前端可见契约，不证明 Harness 已拥有完整删除保护。

## Harness 当前可复用内容与真实缺口

代码先经 CodeGraph 查询，再核对生产路径：

- `modules/session/src/im/hikaru/harness/session/SessionHeader.kt` 没有归档字段；本轮 PinKey 的完整 header 摘要不应因新增展示状态改变。
- `modules/session-persistence/src/im/hikaru/harness/session/persistence/RoomSessionPersistence.kt` 的 `SessionPersistence` 只有 durable headers/events 读取、flush/load/inbox；没有 archive/unarchive。`load` 会恢复 live Session，必要时修复 open turn 并 flush，不能拿来做只读归档目录。
- `modules/session-api/src/im/hikaru/harness/session/api/SessionApi.kt` 的 `list` 目前只列 live agents；history/prompt/cancel 也依赖 live agent。仅给 Worktrees 卡片加 archived 标签，无法改变会话可发送性、Recent 列表或重启后的状态。
- `modules/session/src/im/hikaru/harness/session/SessionStore.kt` 的 handle dispose 会 detach/close 精确 Session；`modules/agent/src/im/hikaru/harness/agent/AgentRegistry.kt` 的 Agent handle dispose 会 cancel Agent 并释放 owner context。两者都不是现成的持久归档 API，不能在页面里直接冒充归档。
- `apps/shared/src/im/hikaru/harness/client/page/LocalRecentSessionsPage.kt` 消费当前 Host 已载入的 SessionSummary，无持久归档过滤或恢复入口。
- 正式 bootstrap 已捕获真实 Runtime/Room，Worktrees observer 已提供不加载历史的 header 目录；本批独立聊天固定存储可以复用身份核验与读取，但不能承担 archive 状态。

## 下一批拆分与必要决策

1. **先定义 Host 生命周期能力**：typed archive/unarchive 请求、精确聊天身份、scope、revision、结果与 capability。本机服务负责真实持久状态；不把桌面页面回调当服务。Remote 无实现时明确 unavailable。
2. **定义独立归档元数据与提交顺序**：避免把展示状态塞进 SessionHeader 破坏 PinKey。记录归档操作阶段和最终持久结果；处理写入成功但 cache/detach/取消固定失败的情况，禁止报未提交后自动重投。归档不能删除事件历史或 Git 文件。
3. **核对 Agent/Session 所有权和活动执行门槛**：确定归档是否只允许已停止的聊天，以及怎样在服务层防止 prompt/create/restore 与归档竞争。当前没有共享 lifecycle gate，不能仅先读 idle 再写归档。不得顺带停止用户既有进程。
4. **实现恢复消费链**：从 durable 元数据与真实历史恢复精确会话/Agent；保证归档状态重启可读、恢复不发送新消息、不复制 Session、不丢事件。错误和未知结果应可刷新核验。
5. **最后接 Worktrees 页面**：每个关联聊天展示真实当前归档状态与操作结果；复用共享按钮、菜单与确认组件。Recent/主会话只增加必要的归档过滤与当前会话失效处理，独立管理页不加入本批。
6. **删除保护另行验收**：即使 Pin 和 archive 都完成，也还缺共享 gate、主/共享工作树与内容保护、完整关联目录和对并发变化的处理。此分析不开放删除、清理或自动回收。

实施前需把第 1–4 项收敛成明确写集与运行合同；目前只完成原版前端行为和现有缺口核对。验收应以真实临时 Runtime/Room/Git、正式 bootstrap 重开、旧历史保留、无自动 prompt、错误提交阶段、导航/关闭及宽窄浅深页面为证据。功能通过、离屏布局、原生窗口与原版同状态像素分别记录。

## 生命周期入口补查（2026-10-10，尚未实施）

本轮经 CodeGraph 核对生产模块，下一份合同需覆盖以下具体入口，不能只在 Worktrees 或 SessionApi 增加检查：

- `AgentRegistry.kt:250` 的 `detach` 是私有的，并绑定登记 identity；`get/list` 只返回 Agent，不返回可释放的原始 handle。归档服务需要明确的 registry 生命周期能力，不能伪造 handle 或只移除页面缓存。
- `AgentLoopAgent.kt:125` 的 `cancel` 默认清空 NEXT_TURN/NEXT_STEP 持久 inbox。直接拿 handle.dispose/cancel 实现归档可能改变历史队列；首批应拒绝仍运行、仍有待执行队列或未确认活动状态的聊天，并在同一生命周期门槛下核验。如何处理子 Agent、后台进程和终端还需纳入合同。
- `AgentLoopAgent.kt:155` 的 `send` 先执行 inbox.insert，再取 Agent mutex；`resumeAttempt` 也可独立启动 driver。`runDriver` 在清除 driver 之前就发布 IDLE。因此读到 IDLE 不足以证明可归档，共享门槛必须覆盖消息接收、恢复启动和执行接管，定义锁顺序及回调重入规则。
- `SessionStore.kt:49` 的 enter、`:108` 的 load 和 `AgentRegistry.kt:45/131` 的 create/restore 是独立入口；仅在 Host API 外包一层锁不能防止直接模块调用重新接入已归档身份。持久归档检查与精确实例身份检查需由真正拥有这些入口的层执行。
- `LlmRetryPlugin.kt:189` 监听 AgentEvents.Restore，并在 `:263` 检测 pending retry 后调用 resumeAttempt。因此“恢复没有发送新 prompt”仍不足以证明不会自动执行；合同还需定义恢复为静止可读状态的方式，并验证模型请求数为0及未新增 retry/turn 事件。

以上是当前代码行为与待收敛要求，不代表已经选定公共 gate 的实现，也不扩大到独立归档管理页或工作树删除。先完成当前 Pin 验收，再形成有明确写集、锁顺序和失败阶段的归档合同。

## Pin 验收后的父线程设计约束

Pin R2 已完成 56/56 与完整 JVM build 验收。父线程再次核对工作树与该批冻结的 2,259 个输入逐字节一致；归档仍未实施。只读设计工作者继续使用 `SHUAIAPI/gpt-6.1-sol / opencodex`，其提案须经以下约束审查后才能派实施。

1. 分三批推进：共享生命周期门槛与静止恢复；持久归档/恢复及正式启动接线；Worktrees 操作与必要的 Recent/当前聊天同步。每批单独冻结与验收，不要求底层批次生成页面截图。
2. 首批使用忙碌拒绝语义。归档独占资格必须在同一原子决策中关闭新的准入并确认没有在途操作；仍有执行或生命周期操作时返回 Busy，不排队等待执行结束，不代替用户 cancel、清 inbox、关闭 PTY 或停止进程。
3. 门槛的短状态锁不得跨工厂调用、事件通知、Room/文件 IO 或 join 持有。普通嵌套调用须有确定的许可生命周期；不得让继承协程上下文的子任务误用已释放许可。执行 driver 的许可必须延续到实际完成，不能在发布 IDLE 时提前释放。
4. 旧 Session/Agent/Inbox 的引用与同 ID 替换实例必须能区分。已归档或过期实例不得借助低层入口产生新的 durable 事件；只在页面或 Host API 检查不满足此要求。
5. 静止恢复为显式路径，已有默认 load/restore、开放回合修复和 retry 续跑语义保留。新路径需拒绝不完整历史、待执行 inbox、开放 turn 和 pending retry；不能通过修复历史或清队列把不满足条件的会话变成可恢复。
6. 恢复验收检查实际模型请求数为 0，同时检查 turn/attempt/retry 事件和原始 durable 历史均未增加。仅检查没有调用 prompt 不充分。未声明支持静止初始化的工厂或恢复参与者应拒绝该路径。
7. 门槛首批只证明同一 Runtime 中的互斥与身份有效性。独立 JSON 的 CAS/文件锁不自动证明另一个进程的 live Agent 已静止；生产可宣称的跨进程边界及启动所有权保护须在第二批明确并验证。
8. 持久提交与 detach、pin cleanup、恢复载入分别报告。提交结果未知时只允许显式核验；不得自动重投，也不得把后续投影失败改写成持久提交失败。

以上约束是父线程选定的实施方向，不代表只读提案已交付、具体写集已批准或任何归档测试已通过。首批精确合同仍须核对生产接线后落盘。
