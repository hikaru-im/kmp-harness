# Session capability

## 模块边界

`modules/session` 提供一个进程内、仅追加、可重放的会话事件日志。它依赖 Runtime、Loader 与 LLM
共享数据契约，但不运行 Agent、不调用 Provider，也不执行 retry、文件 I/O 或网络 I/O。

Session 日志是会话事实和模型历史的唯一真源。任何消息历史、请求头或请求上下文缓存都只是可丢弃的
派生状态，必须能从完整日志重新计算出同一结果。

## 标识与头部

- `SessionId` 是可序列化 value class，不与 `MessageId`、`CallId` 或 `LlmSessionId` 混用。
- `SessionHeader` 包含格式 version、SessionId、createdAt、可选 cwd、lineage、subagent delegation 和
  agentPreset 字段。
- S1 只创建新 Session，正常入口是 `Context.createSession(...)`；构造不依赖 thread-local 或对调用方
  Fiber 的隐式推断。
- clock 与 id factory 可注入，以便确定性测试。

## 事件键与信封

`SessionEventKey<T>` 由稳定事件名和 `KSerializer<T>` 组成。Session 核心和外部 Plugin 都可以声明事件
键；增加业务事件不需要修改 Session 的封闭类型层级。

每个已提交事件使用固定 `SessionEventEnvelope`：

- `type`：事件键的稳定名称；
- `seq`：Session 内从 0 开始连续递增的序号；
- `time`：提交时的 epoch milliseconds；
- `data`：由事件键 serializer 立即生成的 detached JSON snapshot；
- `ignorable`：可选的前向兼容标记；
- `sourceEventSeqs`：可选 provenance，值必须唯一并且只引用 candidate 之前已提交的事件；
- `surfaceOp`：模型可见事件必须为 `append`，log-only 事件不设置该字段。

序列化结果必须递归验证为可无损保存的 JSON；NaN、正负 Infinity 等非有限数字在提交前拒绝。
append 的输入、返回 envelope、events snapshot 和派生对象都不得持有可改写内部日志的共享可变引用。

## 核心事件词汇

S1 定义并提供 serializer 的核心事件为：

- `turn/start` 与 `turn/end`；
- `step/start` 与 `step/end`；
- `user/message`；
- `assistant/chunk` 与 `assistant/message`；
- `request/header` 与 `request/context`。

`turn/end` 原因至少覆盖 completed、max-tokens、aborted、blocked、error 和 interrupted。
`assistant/message` 保存 turn、step、组装后的 Message、可选 TokenUsage 和 interrupted 状态。
`request/header` 保存实际发送的 `LlmCallConfig`、adapter defaults、system 和有序 tools schema；
`request/context` 保存已解析 route metadata。

## Append 事务

单个 Session 的 append 在 suspend 串行区中按以下顺序完成：

1. 使用事件键 serializer 生成 detached `JsonElement`。
2. 验证 JSON 可无损表示，并 snapshot/验证 surface metadata。
3. 在串行区分配 candidate 的 `seq` 与 `time`。
4. 验证事件专属 shape、surface eligibility 和 provenance。
5. 将 candidate 追加到日志并失效相关派生缓存；此处是唯一 commit point。
6. 如果 Session 已 enter，按提交顺序发布 `session/event`。
7. 隔离每个 post-commit observer failure，返回已经提交的 envelope。

任何序列化、shape、surface 或 provenance 失败都不写日志、不消耗 seq、不发布事件。commit 后不再执行
可以否决 append 的验证；观察者失败不能回滚日志或改变 append 的成功返回值。

事件专属 shape 至少要求：

- `user/message` 只接受 user/plugin source；
- `assistant/message` 只接受 assistant/model source；
- turn 与 step 编号为正整数；
- surface 事件显式声明 `surfaceOp = append`；
- S1 收到任何 replace/range replacement surface 操作时返回稳定的 unsupported 错误。

并发 append 必须被线性化；最终日志中的 seq 从 0 连续递增，事件发布顺序与提交顺序一致。

## 模型历史投影

`deriveMessages()` 每次返回 detached、不可影响日志的 fresh List，并按 seq 顺序执行以下投影：

- `surfaceOp = append` 的 `user/message` 解码为原 user message；
- `surfaceOp = append` 且正文非空的 `assistant/message` 解码为 assistant history；
- 正文为空、只承载 usage 的 `assistant/message` 不进入模型历史；
- chunk、turn/step、request metadata、自定义事件和所有其他 log-only 事件不产生消息。

相同日志必须始终产生相同历史。实现可以缓存 surface seq 或 JSON 投影，但不能暴露可变 typed projection
作为第二真源。

## 请求折叠

模块提供纯函数和对应 live Session API：

- `canonicalHeader(header)`；
- `headerEquals(a, b)`，其中 tool schema 按顺序比较；
- `foldRequestHeader(events)`，选择最新的完整 request snapshot；
- `foldRequestContext(events)`，选择最新的 route metadata。

任何 live 增量缓存都必须与从完整日志执行纯 fold 的结果相同。header equality 不得忽略 system、tools
顺序、call config 或 adapter defaults。

## Store 与生命周期事务

`SessionStore` 管理 live Session，并保持创建顺序。创建严格拆分为：

1. `prepare`：构造尚未公开的 Session；
2. `enter`：以 SessionId 原子注册 live entry；
3. `announce`：同步发布 `session/created`。

`session/created` 是可否决事件。其 listener 失败时，Store 必须 identity-bound 回滚对应 live entry，并为
已经开始的生命周期发布恰好一次配对 `session/disposed`。失败创建不得向调用方返回半个 Session。

重复 SessionId 原子失败且不覆盖原对象。`get` 返回查询结果，`list` 按成功创建顺序返回。只有创建
handle、owner Context effect 或 Plugin lifecycle 拥有 detach/dispose 权限；detach 必须比较对象身份，旧
handle 不能删除同 id 的其他实例。

owner Context dispose 会 detach 自己创建的 Session。Session Plugin dispose 会按确定顺序清理全部 live
Session，并确保 Store 不遗留 entry。dispose 必须幂等，且每个成功开始的生命周期最多发布一次
`session/disposed`。

## Runtime 事件语义

Session Runtime 事件 payload 显式携带目标 Session：

- `session/created`：普通 `EventKey<SessionNotice>`，同步 listener 失败会 veto 创建并触发回滚；
- `session/event`：`EventKey<SessionEventNotice>`，通过 `Context.emitContained` 逐 listener 隔离失败；
- `session/disposed`：`EventKey<SessionNotice>`，通过 `Context.emitContained` 逐 listener 隔离失败；
- `session/flush`：`ParallelEventKey<SessionNotice>`，等待全部 listener settle 后返回，并向调用方传播失败。

Runtime `Context.emitContained` 必须代理已有 `EventsService.emitContained`，同时保持 listener 注册顺序、
once、发布时 snapshot 与 Context dispose 的现有规则。Session 不绕过 Context 直接访问 RuntimeCore。

在通用 scoped event filtering 落地前，事件从仍存活的 Session Plugin Context 发布；owner Context 只持有
detach effect。这样 owner 正在 dispose 时仍能发布配对 disposed。

## 错误与并发契约

模块提供稳定的 `SessionErrorCode` 与 `SessionException`，至少区分 invalid header/event、duplicate id、
not live、reentrant/closed lifecycle 和 unsupported surface replace。测试和调用方依据 code 判断错误类别，
不依赖异常消息文本。

Session 主 mutation、Store 注册与 lifecycle transition 在 Kotlin Multiplatform common code 中串行化。
实现不得依赖 JVM 专属 monitor 语义。重入若会破坏原子性或死锁，必须以稳定错误立即失败。

## Plugin 暴露

模块提供 `SessionPlugin`、`SessionPluginDefinition`、`SessionKey` 和面向 Context 的创建/查找入口。
Loader 可以按现有模块规范安装该 Plugin。README 说明最小创建、append、derive、flush 和 dispose 用法，
但不承诺持久化或 Agent 执行能力。

## 明确不支持

S1 不支持磁盘加载、persistence codec registry、格式迁移、未知 required event 恢复、fork、restore、
compaction、surface replacement、chunk row compression、Agent/AgentLoop、Provider route、retry 执行、
tools、Gateway、Repository、ViewModel 或 UI。

未来 persistence Plugin 只能通过 `session/event` 和 `session/flush` 接入；未来 Agent、retry 与 tools
模块通过自有 `SessionEventKey<T>` 扩展日志，不得在 Session 内增加反向业务依赖或旁路 message store。
