# Subagent producer gap — 2026-10-07

> 状态校正（同日后续实现）：本文正文是实施前分析，producer 缺口已由 `modules/subagent-tools` 的真实 handler 和 DesktopBundle 默认注册替代。`SubagentToolsContractTest` 15/15 通过，XML 位于 `/tmp/harness-native-verification-evidence-20261007/subagent-contract-final/jvm`；另新增真实默认 profile/父子 AgentLoop/Room/LocalConnection/panel repository 组合 `DesktopSubagentLoopIntegrationTest` 2/2，通过自动消费与关闭后 fresh reader 完整事件读取验证，无手工 append/restore，XML 位于 `/tmp/harness-native-verification-evidence-20261007/subagent-loop-final/jvm`。历史建议不是待实施清单；永久 Done、持久历史枚举、Compose/远端验证与 PTY 仍是独立边界。最新进度见 `../replication-plan-2026-10-07.md`。

## 范围与结论

本次只读分析当前共享工作区的 Kotlin 生产实现；先使用 CodeGraph 定位与读取，再用限定生产源码的文本搜索补足负证据。未修改产品源码，未运行构建或测试。这里描述 Harness 实现，不把 ASAR 或其他宿主的 spawn 工具当成本仓库已有能力。

**当前默认 Desktop Host 有真实 Agent/Session 创建与工具执行基础设施，但没有发现生产 subagent 创建工具、handler 注册或 `origin = SessionOrigin.SUBAGENT` 写入。** B descendants 服务已具备读取能力；默认生产路径不会自动产生可通过该服务验证的子 Agent。成功空列表是可能且符合契约的结果，不代表历史上从未存在子 Agent。

普通 branch 与 subagent 在 metadata 层能够区分：`parentSession` 表示父引用，`origin=SUBAGENT` 才表示委派谱系。仅有父引用不能成为 subagent 证据。当前也未发现完成的普通 fork 工具；共享客户端只是允许传递 branch 相关字段，不能据此声称已有历史复制功能。

## 当前真实创建链

```text
LocalConnection.session.create(id, clientOptions, clientAgentOptions)
  → SessionCreateEndpoint / SessionCreateRequest
  → DefaultSessionApi.create(id, coreOptions, coreAgentOptions)
  → context.agents.create(..., sessionOptions)
  → ownerContext.createSession(..., sessionOptions.copy(cwd, agentPreset))
  → SessionStore.create → prepare → CreateSessionOptions.toHeader
  → SessionHeader → Session 生命周期进入/发布
  → AgentFactory.create(实际 Session) → 同一 AgentRegistry 注册
```

| 证据 | 当前行为 |
| --- | --- |
| `apps/jvm-app/src/im/hikaru/harness/desktop/connection/LocalConnection.kt:89` | 将客户端 cwd、parentSession、seedLength、agentPreset 映射至 core options；provider/model 等映射至真实 AgentOptions。没有补 SUBAGENT。 |
| `modules/api/gateway/src/im/hikaru/harness/api/gateway/session/SessionApiGateway.kt:27`、`:67` | typed create 请求包含 core CreateSessionOptions；直接交给 SessionApi，未区分 spawn 与 branch。 |
| `modules/session-api/src/im/hikaru/harness/session/api/SessionApi.kt:78` | create 直接调用当前 Context 的 AgentRegistry，返回真实 SessionSummary。 |
| `modules/agent/src/im/hikaru/harness/agent/AgentRegistry.kt:45`、`:70` | 默认 sessionOptions 无谱系；仅 copy 填 cwd/preset。Context.child 是资源所有权关系，不是 Session 谱系。创建时检查 Agent 与 Session 身份，成功加入 entries。 |
| `modules/session/src/im/hikaru/harness/session/SessionStore.kt:31`、`:92` | 生成真实 ID/时间，直接由 options 构造 Header，再 enter/announce。 |
| `modules/session/src/im/hikaru/harness/session/SessionHeader.kt:9`、`:49` | core 已有 SUBAGENT、parentSession、origin、delegationDepth；toHeader 原样保留，未推导 origin。 |

生产调用搜索只发现 DefaultSessionApi 的 `context.agents.create` 调用。CodeGraph 的宽查询曾把通用 `create` 关联到 IncludePlugin；读取实际源码后确认 `modules/include/src/im/hikaru/harness/include/IncludePlugin.kt:25` 仅隔离 Context 并安装配置 child loader，**没有 Agent 创建调用**。不要据此给 include subtree 标 subagent。

持久化具有承载能力：`modules/session-persistence/src/im/hikaru/harness/session/persistence/RoomSessionPersistence.kt:354` 保存 parentSession/origin/delegationDepth，`:360` 恢复相应字段；这不是 producer，也不会把旧普通会话改成 subagent。

## 工具执行链与缺口

`modules/tools/src/im/hikaru/harness/tools/Tools.kt:35` 提供 `ToolHandler.execute(ToolCallRequest)`，`:39` 提供 `ToolsService.schemas/execute/register`。默认 service 的 entries 为空（`:100`）；ToolsPlugin 只安装 service（`:86`），并不注册内建 spawn/fork handler。

`modules/agent-loop/src/im/hikaru/harness/agent/loop/AgentLoopAgent.kt:387` 将实际已注册 schemas 交给模型；`:526` 执行返回的 tool calls，并检查名称确实已注册。`:553` 由 loop 构造：

```kotlin
ToolCallRequest(call.id, call.name, call.arguments, scope = session.id.value)
```

因此生产 loop 已携带真实父 Session ID，模型 arguments 不需要也不应决定父 ID。`scope` 当前仍是可空字符串与通用去重域，**不是独立的认证或父身份凭据**；若新增其他工具入口，必须限制其能否伪造该字段，或者增加服务端可信的 typed execution context。

`modules/bundle/desktop/src/im/hikaru/harness/bundle/desktop/DesktopBundle.kt:43`、`:74` 编译并安装 tools/agent-loop/session-api 等基础插件，没有 subagent producer 插件。当前生产 Kotlin 搜索 `spawn/fork/subagent`、`ToolHandler`、`SessionOrigin.SUBAGENT`、`parentSession` 的结果中，SUBAGENT 只见 enum 与 reader 使用；显式子 Agent metadata 构造存在于测试，不能作为生产能力。

## 为什么会真实返回空列表

`modules/session-api/src/im/hikaru/harness/session/api/LiveSessionDescendants.kt:16` 读取当前 registry；`:61` 验证 lineage。child 到请求 parent 之间每条边必须具有 SUBAGENT origin，整个父链必须在同一 live registry 中可验证，无 self/cycle/missing ancestor。

| 实际情况 | 结果与含义 |
| --- | --- |
| 当前 parent 在 registry，但其余会话 origin=null | Available(empty)，普通会话与 branches 被正确排除。当前默认创建链符合此情况。 |
| 子 Session 有 SUBAGENT，但 parent 未指向请求 parent，或中间边缺 origin | 排除该候选；不会将所有 session.list 行当子 Agent。 |
| child 或任一祖先不在当前 registry，或含 cycle/self | 该候选链不能成立；已移除的历史 Session 不会自动被恢复供枚举。 |
| 请求 parent 本身不存在，或已验证实例在读取中被替换/关闭 | 读失败，不应伪装成功空。childHistory 也会在 history suspend 前后复验身份。 |
| endpoint/service 未提供 | typed Unavailable，属于能力缺失，区别于成功空。 |

Available 的覆盖面为 LIVE_REGISTRY，`complete=false`；没有历史穷举和 backend revision。当前 AgentStatus.IDLE 映射 UNKNOWN，不是 Done；正在运行才映射 RUNNING。provider/model 来自真实 AgentOptions，createdAt 是 Session 创建时间，不冒充任务启动时间；objective 没有生产来源，仍为 null。

## 最小接线方案（建议，尚未实现）

1. **新增明确的服务端 subagent producer/工具插件**，在默认 Host 的同一 ToolsService 注册具体 schema+handler。普通 SessionApi.create、AgentRegistry.create 和 branch 默认值继续保持现有语义，不统一补 SUBAGENT。先只实现创建与首次真实 prompt，无须同时提供子 Agent 发送/取消入口。
2. handler 从生产 loop 注入的 request.scope 解析父 ID；拒绝缺失/无效 scope。通过该 Host 的同一 AgentRegistry 获取实际父 Agent/Session，验证完整 live ancestry，禁止 self/cycle/失效链。不能只检查父 ID 在任意 session.list 出现。
3. 接受明确 typed AgentOptions（真实 provider/model 或明确 preset 解析策略），任务映射为真实 Message。调用现有创建入口并显式提供：

   ```kotlin
   CreateSessionOptions(
       cwd = validatedCwd,
       parentSession = parent.session.id,
       origin = SessionOrigin.SUBAGENT,
       // delegationDepth：仅在定义并验证真实深度策略后填写；否则保持 null。
   )
   ```

   创建返回真实 child ID 后，再通过真实 SessionApi.prompt/Agent.followup 提交任务。Header 在创建时就有 metadata，避免事后修改不可变 Header。
4. **处理父创建竞争与生命周期**：创建前后复验父与祖先的精确实例；父在创建期间失效时拒绝并清理新建未交付 child。若采用 AgentRegistry.create，producer 必须持有返回 AgentHandle；成功 child 的 handle 应由明确 Host/producer 生命周期拥有，不能在 handler 返回时立即 dispose，否则 reader 看不到它。真正需要与父移除原子协调时须增加 registry 内部操作；现有分离的 get/create 调用不能承诺原子性。
5. create 成功、首次 prompt 失败要区分两个阶段。已交付/保留的 child ID 必须返回明确状态，不能报告“未创建”并促使无条件重建。具体保留或清理策略由 producer 契约确定；不通过只读 descendants reader 隐式执行 mutation。
6. 面板继续调用已接线的 descendants/readChildHistory 并显式 refresh；不造 revision、不注入假 rows、不依赖默认 Remote awaitIdle 的 Unit 推导任务完成。IDLE/awaitIdle 只说明循环空闲，永久 Done 需要额外真实任务生命周期协议。

**最小实施分工**：producer owner 负责上述插件、可信父上下文、真实创建/prompt 与 handle 生命周期；Host bundle owner 显式安装 producer；B reader/gateway/Connection 与 panel 可继续复用已有只读 API。没有必要为面板放宽 lineage 检查或把 ordinary client create 暴露为任意 origin 切换。

## 后续验收与确定限制

建议 producer 实现后的定向用例：真实工具调用产生 parent+SUBAGENT Header 并返回 child ID；真实 provider/model 传入；descendants 与 childHistory 随即可读；普通 branch 排除；两级真实谱系可读；错 registry/self/cycle/缺失 scope 拒绝；父在 create suspend 时替换拒绝；prompt 失败返回已创建 child 状态；handler 返回不移除成功 child；IDLE 不显示 Done。此轮未新增或运行这些测试。

未检查机器上运行中的 Host 数据库或外部运行时注册，因而不声称某个实际用户会话一定零后代，也不排除外部代码通过 core create 请求显式提供 origin。确认的是当前仓库默认生产 producer 缺口。普通 fork 的历史复制、delegationDepth 策略、objective 持久来源、历史后代枚举、永久完成状态和父子原子创建均未有本轮已确认实现，需独立契约，不能借此只读分析隐式增加。

本次写集仅本文档；交付后释放。
