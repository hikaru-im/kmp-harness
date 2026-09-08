# Session S1 对齐 DSH 实施规划

本文把 KMP Harness 的 `modules/session` S1 拆成可直接实施和验收的任务。设计依据为 DeepSeek
Harness `0a53fb55bea101816fa226bb964ae2bed71c343b`（`dsh-v0.1.2-alpha.2`）中的
`packages/core/session`，重点核对了：

- `src/types.ts`：Header、事件信封、事件词汇与 surface metadata；
- `src/index.ts`：append 提交边界、SessionStore 生命周期、flush 与派生缓存；
- `src/surface.ts`：模型可见 surface 与消息投影；
- `src/request-header.ts`：请求信封规范化与折叠；
- `src/invariant.ts`：turn / step / tool 关系约束；
- `tests/session.spec.ts`、`derived-cache.spec.ts`、`properties.spec.ts`：不可变性、重放与生命周期契约。

S1 的目标不是复制 DSH 当前 Session 包的全部功能，而是先建立不会妨碍后续持久化、AgentLoop、
compaction 和 fork 的正确内核。

## 一、S1 必须保持的 DSH 原则

### 1. 日志是唯一真源

`Session` 只保存仅追加事件日志。模型历史由 `deriveMessages()` 从日志派生，不再维护第二份可独立变化的
message 列表。

```text
append event
    -> JSON snapshot
    -> validate candidate
    -> commit to append-only log
    -> publish session/event
    -> deriveMessages() reads committed surface events
```

`assistant/chunk`、turn / step 边界、request metadata 和失败诊断都留在日志中，但不会自动进入模型历史。

### 2. 候选先完整验证，再原子提交

一次 `append()` 必须先完成序列化、无损 JSON 校验、事件信封校验和 surface metadata 校验。任何一步失败：

- 日志不增长；
- `seq` 不消耗；
- 不发布 `session/event`；
- 已缓存的派生状态不变化。

事件进入日志后即视为提交成功。`session/event` 观察者失败必须被逐个隔离，不能让已经提交的 append
对调用方表现为失败，也不能阻止后面的观察者看到同一事件。

### 3. 模型可见必须显式声明

DSH 使用 surface 区分“写进日志”和“进入下一次模型请求”。S1 只支持尾部追加，但从第一版就保留：

- `surfaceOp: "append"`；
- 可选 `sourceEventSeqs`，例如 assistant message 引用生成它的 chunk seq；
- S1 的 surface metadata 只允许出现在 `user/message` 与 `assistant/message`；未来 `tool/result` 加入时
  必须复用同一套 surface contract；
- surface 事件必须携带 `surfaceOp`，非 surface 事件禁止携带它。

位置替换 `{ op: "replace", start, end }` 留到 compaction 阶段；届时必须连同完整 range/provenance
校验、重放和格式版本决策一起实现，不能只增加一个数据类。

### 4. 生命周期是可回滚事务

正常创建入口不能只做 `map[id] = session`。它必须保留 DSH 的三段式事务：

```text
prepare        构造但不发布 Session
    -> enter   精确注册到 Store，取得 identity-bound detach capability
    -> announce 发布 session/created
```

`session/created` 同步失败会撤销 enter；如果部分 listener 已经收到 created，则撤销时必须配对发布
`session/disposed`。旧 detach capability 只能删除它创建的精确 entry，不能按 id 删除后来创建的对象。

### 5. 持久化是独立 Plugin

`session` 不读写文件。未来 backend 订阅提交后的 `session/event` 并缓冲写入；需要 durability barrier
的调用方使用 `SessionStore.flush(session)`，它通过 `ParallelEventKey` 等待所有 `session/flush` listener。

## 二、DSH 全量能力与 S1 边界

| DSH 能力 | S1 决策 | 原因 |
| --- | --- | --- |
| append-only event log | 实现 | Session 的核心真源 |
| typed extensible event vocabulary | 实现 | `agent/inbox/spliced`、`llm/retry` 不能反向修改 session 核心 |
| lossless JSON snapshot | 实现 | 写入时隔离可变调用方对象，并为持久化保留标准边界 |
| `session/created` / `event` / `disposed` | 实现 | 生命周期和持久化观察入口 |
| `session/flush` | 实现 | 先固定持久化屏障语义，S1 不提供 backend |
| append-only surface 与消息派生 | 实现 | 保证后续请求历史只有一个真源 |
| request header / context fold | 实现 | AgentLoop 第一版就需要比较和重建实际请求信封 |
| surface range replacement | 延后 | 必须与 compaction、来源覆盖验证和格式版本一起验收 |
| seed / restore / `session/end-seed` | 延后 | S1 没有 persistence reader |
| fork 与 lineage 行为 | 延后 | 必须以稳定 turn boundary 和 seed 重放为前提 |
| crash repair | 延后 | 依赖 load、tool call/result 和 invariant replay |
| chunk-row compression | 延后 | 属于 persistence/storage codec，不属于内存 Session |
| generated known-event catalog | 延后 | 读取未知 required event 时才需要；S1 仍写出 `ignorable` 标记 |
| tool call/result surface | 延后 | 等 Tools 阶段连同配对 invariant 一起实现 |
| Typert/Gateway lookup | 延后 | Session API 阶段再建立 wire contract |

## 三、KMP 数据模型

### 1. SessionId 与 Header

`SessionId` 是独立的 `@Serializable @JvmInline value class`，不能与 `MessageId`、`CallId` 或
`LlmSessionId` 混用。默认 id 由可注入的 `SessionIdFactory` 生成，测试不依赖随机 UUID。

`SESSION_FORMAT_VERSION` 首版为 `0`。`SessionHeader` 对齐 DSH 的持久元数据形状：

```text
version: Long
id: SessionId
createdAt: Long
cwd?: String
parentSession?: SessionId
seedLength?: Long
origin?: SUBAGENT
delegationDepth?: Long
agentPreset?: String
```

S1 正常创建只使用 `cwd`；其他可选字段先固定 schema 和验证规则，为 fork、resume 与 subagent 保留位置。
`createdAt`、`seedLength` 和 `delegationDepth` 必须是非负数。`cwd` 使用可移植的绝对路径语法检查
（POSIX、Windows drive、UNC），实际规范化仍由创建 Session 的 Host 负责。

### 2. 可扩展的 SessionEventKey

Kotlin 没有 TypeScript declaration merging，因此使用显式 codec key：

```kotlin
open class SessionEventKey<T : Any>(
    val name: String,
    val serializer: KSerializer<T>,
    val ignorable: Boolean = false,
)

class SurfaceSessionEventKey<T : Any>(...) : SessionEventKey<T>(...)
```

`append(key, value)` 立即通过 key 的 serializer 转成 `JsonElement`，然后递归重建
`JsonObject` / `JsonArray` / `JsonPrimitive`，确保嵌套的 `JsonElement`（例如
`Message.source.replayState`）也不会保留调用方的 backing map/list 引用。日志只保存这个 JSON snapshot，
不保存调用方传入的对象引用。Plugin 可以声明自己的 key；`session` 不维护封闭 sealed event 枚举。

`ignorable` 默认是 required。只有丢失后绝不会改变重建语义的纯诊断事件才可把它固定为 `true`；它被写成
信封中的 `ignorable: true`，不能写 `false` 来代替缺失。

### 3. 固定事件信封

日志公开形态使用非泛型、可直接持久化的 `SessionEventEnvelope`：

```text
type: String
seq: Long
time: Long
data: JsonElement
surfaceOp?: "append"
sourceEventSeqs?: List<Long>
ignorable?: true
```

`seq` 从 `0` 连续增长。`time` 由可注入 `SessionClock` 在真正提交候选时产生。公开的 events API 返回新的
List snapshot；`JsonElement` 是日志权威数据，typed decode 只能产生脱离日志的副本。

### 4. S1 核心事件词汇

首版实现以下 key 与 `@Serializable` payload：

```text
turn/start
turn/end
step/start
step/end
user/message
assistant/chunk
assistant/message
request/header
request/context
```

`TurnEndReason` 对齐 DSH 已有变体：

- `completed`；
- `aborted`，携带 `user | parent | hook(reason) | disposed | legacy` cause；
- `blocked`；
- `error(LlmFailure)`；
- `max-tokens`；
- `interrupted`，只供未来 crash repair 写入，AgentLoop 不主动产生。

`AssistantMessageEvent` 保存 `turn`、`step`、完整 `Message`、可选 `TokenUsage` 和可选
`interrupted = true`。`RequestHeaderEvent` 保存完整、规范化后的 `EpochHeader`：

```text
config: LlmCallConfig
adapterDefaults?: LlmCallConfigAdapterDefaults
system?: String
tools?: List<ToolSchema>
reason: initial | resume | change | series
startsSeries?: true
```

空 system 和空 tools 规范化为缺失。`request/context` 单独保存注册 route 的 provider、Provider-owned
model id 和可选 context window，不混入 header equality。

## 四、KMP API 与并发语义

### 1. 为什么主要 API 是 suspend

DSH 依靠 JavaScript 单线程和同步 append 维持顺序；KMP 会跨 JVM、Android 和 Native。S1 不暴露一个
在并发调用下可能损坏 `MutableList` 的假同步 API。以下一致性操作统一为 suspend，并在 Session / Store
内部串行化：

```text
Session.append(...)
Session.events()
Session.deriveMessages()
Session.requestHeader()
Session.requestContext()
SessionStore.create / enter / announce / get / list / flush
```

append 的事件提交与 `session/event` 同步通知保持同一顺序。listener 不能执行挂起 I/O；持久化 listener
只负责把 envelope 放入自己的缓冲队列，真正刷盘由 `session/flush` 完成。

### 2. 正常创建入口

当前 KMP Service 方法无法自动得知“哪个 Context 调用了它”，因此不复制 DSH 隐式 caller-fiber 行为。
公开入口使用 Context 扩展显式传入 owner：

```kotlin
suspend fun Context.createSession(
    id: SessionId? = null,
    options: CreateSessionOptions = CreateSessionOptions(),
): Session
```

它要求当前 Context 能看到同一个 `SessionStore`，并把 enter 返回的 detach 注册为当前 Context effect。
Context dispose 时 Session 自动从 Store 移除。

底层 `SessionStore.prepare()`、`enter()`、`announce()` 保留为模块 API。未来 Agent 工厂把 Session、driver
和 Agent registration 放入同一个 ordered effect transaction，确保 driver 的最终日志先提交，随后才撤销
Session publication hooks。

### 3. Store 与 Plugin

模块提供：

- `SessionKey : ServiceKey<SessionStore>`；
- Plugin 名称 `session`；
- `SessionPlugin` 创建并提供唯一 Store；
- `SessionPluginDefinition` 供 Loader 按配置装配；
- Store dispose 按创建顺序的反方向 detach 所有 live Session。

物理依赖为 `runtime + loader + llm`，不依赖 apps、backend、Koog、具体 Provider、文件系统或数据库。

### 4. Runtime 生命周期事件

由于 Runtime event key 只接收一个 payload，使用明确 notice 类型：

```text
SessionEvents.Created   : EventKey<SessionNotice>
SessionEvents.Appended  : EventKey<SessionEventNotice>
SessionEvents.Disposed  : EventKey<SessionNotice>
SessionEvents.Flush     : ParallelEventKey<SessionNotice>
```

Created 使用普通 `emit`：同步 listener 失败会 veto 并触发 rollback。Appended 与 Disposed 使用
`emitContained`：逐 listener 隔离。Flush 使用 `parallel`：全部 listener settle 后返回，失败向调用方传播。

当前 `EventsService` 已有 `emitContained`，但 `Context` 尚未公开同等入口。S1.0 先增加
`Context.emitContained(...)` 代理及测试；Session 不绕过 Context 访问 `RuntimeCore`。

在通用 scoped event filtering 落地前，事件从仍存活的 Session Plugin Context 发布，payload 显式携带
Session。owner Context 只拥有 detach effect；这样 owner 正在 dispose 时仍能可靠发布配对的
`session/disposed`。

## 五、消息 surface 与请求折叠

### 1. deriveMessages

S1 surface 是所有已提交、`surfaceOp = append` 的 `user/message` 与 `assistant/message` seq，顺序与日志一致。

- `user/message` 解码为原消息；
- 非空 `assistant/message.message.content` 解码为 assistant history；
- 空 content 的 assistant message 只承载 usage，不进入模型历史；
- 其他事件全部不产生消息。

每次返回 fresh List 和从日志 JSON 解码出的 detached Message；调用方即使通过不安全 cast 修改内部 List，
也不能改写日志。可以缓存 surface seq / JSON 投影，但不能把可变 typed projection 作为第二真源暴露。

### 2. requestHeader 与 requestContext

实现纯函数与 live Session API：

- `canonicalHeader(header)`；
- `headerEquals(a, b)`，tool schema 按顺序比较；
- `foldRequestHeader(events)`，选择最新完整 snapshot；
- `foldRequestContext(events)`，选择最新 route metadata；
- Session 上的增量缓存必须与从头 fold 结果一致。

## 六、原子提交顺序

一次 append 的实现顺序固定为：

1. 使用 event key serializer 生成 detached `JsonElement`；
2. 递归拒绝非有限数字等非无损 JSON 值；
3. snapshot 并验证 surface metadata；
4. 在 Session 串行区内分配 `seq` / `time` 并构造 candidate；
5. 验证 surface eligibility、source seq 唯一且全部早于 candidate；
6. 把 candidate 加入日志并失效相关缓存；
7. 若 Session 已 enter，则按提交顺序发布 `session/event`；
8. 逐个包含 observer failure，返回已经提交的 envelope。

不得在步骤 6 后执行还能否决 append 的验证。不得让 observer failure 回滚日志或修改返回值。

## 七、实施任务拆分

### S1.0：Runtime contained emission

- 给 `Context` 增加 `emitContained`；
- 测试一个 listener 失败不阻止后续 listener；
- 测试 once、snapshot 与 Context dispose 仍遵守现有事件规则。

### S1.1：模块骨架与 durable vocabulary

- 创建 `modules/session/module.yaml`；
- 建立 `SessionId`、Header、clock/id factory；
- 建立 EventKey、surface intent、固定 envelope；
- 建立九个核心事件 payload 与 serializer；
- 建立稳定 `SessionErrorCode` / `SessionException`，至少区分 invalid header/event、duplicate id、not live、
  reentrant/closed lifecycle 和 unsupported surface replace。

先写 serializer contract，固定 discriminator、字段名、缺失字段和 `surfaceOp: "append"` 的 JSON 形状。
append 还要在 codec 成功后执行事件专属 shape 校验：`user/message` 只能是 user/plugin source，
`assistant/message` 必须是 assistant/model source，turn/step 必须为正整数；不能只依赖通用
`Message` 构造器允许的 role 校验。

### S1.2：Session append-only log

- 实现串行 append、events snapshot 与 next seq；
- 所有验证在 commit 前完成；
- 实现 surface append/provenance 验证；
- 实现 post-commit contained publication；
- 测试 caller mutation、返回值 mutation、失败不耗 seq、并发 append 顺序和 observer failure。

### S1.3：surface projection 与 request fold

- 实现 `deriveMessages()`；
- 实现 header canonicalize/equality/fold；
- 实现 request context fold；
- 用 from-scratch oracle 测试 live 增量结果；
- 测试任意 log-only 事件穿插都不改变 derived history。

### S1.4：SessionStore 生命周期事务

- 实现 `prepare / enter / announce / create`；
- 实现 `Context.createSession` owner effect；
- 实现 identity-bound detach、get/list 创建顺序和 Plugin dispose；
- 实现 created rollback、paired disposed 与 Appended/Disposed failure containment；
- 实现 `flush()` 的 awaited parallel barrier。

### S1.5：Plugin 与三平台验收

- 提供 `SessionPlugin`、`SessionPluginDefinition` 和 README；
- Runtime + Session 模块测试覆盖 JVM、Android Debug、Android Release；
- KMP 编译覆盖 iOS Simulator / iOS device target；
- 运行整仓 build 与 `git diff --check`；
- 增加最小 fixture：Runtime 安装 Session Plugin、child Context 创建 Session、append user message、派生、
  dispose 后 Store 不再返回该 Session。

## 八、S1 验收清单

- 同一份日志永远产生相同模型历史；不存在第二份权威 message store。
- seq 从 0 连续递增；失败候选不消耗 seq。
- append 保存的是 JSON snapshot，调用方对象和派生对象都不能改写历史。
- surface 事件必须显式 append；log-only 事件不能进入 history。
- Appended observer 失败不改变已提交结果，并且后续 observer 仍执行。
- Created 同步失败会回滚，并为已经开始的 lifecycle 发布配对 Disposed。
- duplicate id 不覆盖 live entry；旧 detach 不删除其他对象。
- owner Context dispose 和 Session Plugin dispose 都不会遗留 live entry。
- request header/context 的 live fold 与纯 replay fold 相同。
- flush 等待全部 listener，并在全部 settle 后传播失败。
- Session 模块没有文件 I/O、网络 I/O、Provider route 或 retry 执行逻辑。

## 九、S1 完成后再做什么

S1 完成后进入 A1 `modules/agent`。在没有 Agent/AgentLoop Consumer 前，不提前实现 persistence、fork、
compaction 或 tool events；但后续任何这些能力都必须继续沿用本规划确定的 JSON 信封、原子提交、surface
真源和 Store transaction，而不能另建旁路状态。
