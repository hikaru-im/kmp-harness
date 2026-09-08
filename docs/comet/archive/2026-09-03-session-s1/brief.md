# 目标

建立 `modules/session`，为后续 `agent` 与 `agent-loop` 提供一个可扩展、仅追加、可重放的会话事件日志。
Session 日志是模型历史的唯一真源；消息历史、请求头和请求上下文都必须由已提交事件确定性派生。

# 范围

- 给 Runtime `Context` 补齐 `emitContained` 代理，使 Session 通过公开 Context API 隔离观察者失败。
- 创建 `modules/session` 及 `session` Plugin，提供 `Session`、`SessionStore`、`SessionKey` 和
  `Context.createSession(...)`。
- 定义可扩展的 `SessionEventKey<T>`、固定 JSON 信封、surface append 元数据和九个首版核心事件。
- 实现并发安全的 append-only 日志、连续序号、提交前完整验证、提交后 contained 事件通知。
- 实现 `deriveMessages()`、request header fold 和 request context fold，并保证 live 结果与完整 replay 一致。
- 实现 `prepare -> enter -> announce` 创建事务、回滚、identity-bound detach、flush barrier 和 Plugin 清理。
- 在 JVM、Android Debug、Android Release 及可用 Apple target 上验证模块契约，并提供 Runtime/Loader
  组合 fixture。

## Source coverage

| 来源定位 | 读取状态 | 保留语义 | Spec 位置 | 验收 ID | 覆盖状态 | 理由 |
| --- | --- | --- | --- | --- | --- | --- |
| `docs/session-s1-plan.md` | complete | S1 数据模型、append 原子性、surface、Store 事务、事件语义和分阶段验收 | `specs/session/spec.md` 全文 | A1-A12 | covered | 作为 Session 的详细对齐规划完整收敛到目标规格 |
| `docs/session-agent-loop-plan.md` 的“阶段 S1：Session” | complete | 三模块边界、首版事件词汇、后续 Agent/AgentLoop 消费约束 | `specs/session/spec.md` 的“模块边界”“核心事件词汇”“明确不支持” | A2、A5、A10、A12 | covered | 仅吸收 S1 当前有效语义，A1/L1 内容留在后续 change |
| DSH `0a53fb55bea101816fa226bb964ae2bed71c343b` (`dsh-v0.1.2-alpha.2`) | complete | Session 日志真源、信封、surface、生命周期和扩展事件设计 | `specs/session/spec.md` 全文 | A2-A10 | covered | 保留架构语义；按 Kotlin Multiplatform 与当前 Runtime API 调整构造和并发机制 |

# 非目标

- 不实现 `agent`、`agent-loop`、Inbox、重试策略或 Provider 调用编排。
- 不实现文件/数据库持久化、加载、迁移、崩溃修复、fork、restore 或 compaction。
- 不实现 surface replace/range replacement；S1 只接受显式 `surfaceOp = append`。
- 不实现 tool 执行、MCP、Skill、Subagent、Goal 或多 Agent 协调。
- 不实现 Gateway、wire contract、Repository、ViewModel、Compose UI 或 Desktop 自动启动 Agent。
- 不在 Session 内加入文件 I/O、网络 I/O、Provider route 或 retry 执行逻辑。

# 验收示例

- A1：`Context.emitContained` 的一个 listener 抛错时，后续 listener 仍执行；once、listener snapshot 和
  Context dispose 行为保持 Runtime 现有规则。
- A2：核心事件与自定义 `SessionEventKey<T>` 都能编码为稳定信封；append 后修改输入对象、返回 envelope
  或派生消息都不能改变日志快照，非有限数字等不能无损表示的值在提交前被拒绝。
- A3：单线程与并发 append 得到从 0 开始、无重复无空洞且与日志顺序一致的 `seq`；任何验证或序列化失败
  都不消耗序号、不写日志、不发 `session/event`。
- A4：`user/message` 与 `assistant/message` 的角色、turn/step、`surfaceOp = append` 和
  `sourceEventSeqs` 都在提交前校验；引用重复、未来或不存在 seq 时原子失败。
- A5：对同一日志执行 `deriveMessages()` 总是得到相同历史；log-only 事件和空正文 assistant usage 事件
  不进入历史，不存在第二份可独立修改的 message store。
- A6：request header/context 的 live fold 与从完整事件日志重新 fold 的结果相同；header canonical equality
  保留 system、tools 顺序、call config 和 adapter defaults 的实际发送值。
- A7：成功创建严格遵循 `prepare -> enter -> announce`；`session/created` listener 失败会回滚 live entry，
  并为已开始的生命周期发布一次配对 `session/disposed`。
- A8：重复 SessionId 不覆盖 live entry；旧 handle/detach 不能删除同 id 的其他实例；owner Context 和
  Session Plugin dispose 后 Store 不残留 live Session，list 保持创建顺序。
- A9：已 enter Session 的 `session/event` 与 `session/disposed` 观察者失败逐个隔离且不回滚状态；
  `session/flush` 等待所有并行 listener settle 后才返回，并向调用方传播失败。
- A10：外部模块可声明并追加自定义事件 key，而无需修改 Session 核心枚举；未知业务事件保持 log-only，
  不会进入模型历史。
- A11：最小 fixture 能安装 Runtime 与 Session Plugin，由 child Context 创建 Session、追加 user message、
  派生相同消息，并在 child dispose 后确认 Store 已移除该 Session。
- A12：Runtime 与 Session 的目标平台测试、整仓 build 和 `git diff --check` 通过；依赖审计确认 Session
  不依赖 apps、backend、agent、Koog 或具体 Provider，无法在本机运行的平台必须明确记录为未验收风险。

# 约束与不变量

- Session 日志是唯一真源，缓存只能加速，不能成为第二份权威状态。
- 日志保存 detached JSON snapshot；所有公开读取返回不可影响内部日志的快照。
- 所有可能否决 append 的工作在 commit 前完成；commit 后观察者失败不得回滚。
- 单个 Session 的 mutation 必须串行化，并在 Kotlin Multiplatform 支持的平台保持相同语义。
- surface 只有显式 append 的 `user/message` 和非空 `assistant/message`；其他事件默认 log-only。
- `sourceEventSeqs` 唯一且只能指向 candidate 之前已提交的事件。
- Runtime 事件从仍存活的 Session Plugin Context 发布，payload 显式携带目标 Session。
- 首版创建入口为 `Context.createSession(...)`，不依赖 JVM thread-local 或调用方 Fiber 推断。

# 决策

- 对齐 DSH 的架构语义而非逐行移植；保留 version、ignorable、provenance 和完整 request schema。
- 使用 `SessionEventKey<T>` 加 serializer 扩展事件词汇，不使用封闭 sealed event 枚举。
- 核心事件为 `turn/start`、`turn/end`、`step/start`、`step/end`、`user/message`、
  `assistant/chunk`、`assistant/message`、`request/header`、`request/context`。
- `session/created` 使用普通 `emit` 形成可否决事务；`session/event` 与 `session/disposed` 使用
  `emitContained`；`session/flush` 使用 parallel barrier。
- Session 主操作使用 suspend/串行区保证 KMP 并发安全，不把 JVM 锁语义带入 common code。
- persistence 是以后独立 Plugin，通过 `session/event` 与 `session/flush` 接入。

# 待解决问题

- 无；Session S1 的目标、范围、验收项和非目标已经确认。

# 验证预期

- Runtime 单元测试覆盖 `Context.emitContained` 的异常隔离、顺序、once、snapshot 与 dispose。
- Session serializer/append/projection/store/plugin contract 测试覆盖 A2-A10 的成功与失败边界。
- JVM、Android Debug、Android Release 运行 Session 与 Runtime 测试；Apple target 至少完成可用的编译/测试检查。
- 运行最小 Runtime/Loader/Session fixture、整仓 build、依赖边界检查和 `git diff --check`。
- 最终报告逐项映射 A1-A12，未运行的平台或外部环境限制不得表述为通过。
