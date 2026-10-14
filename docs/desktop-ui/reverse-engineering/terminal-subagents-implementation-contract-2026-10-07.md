# Terminal / 子 Agent 面板实施契约

日期：2026-10-07。状态：分析交付；仅本文与同名 `.evidence.json`，没有产品改动、构建、测试执行或 GUI 验证。

> 当前状态（2026-10-08）：本文记录原始 ASAR 分析时的仓库现状。之后已实现真实 Exec producer、descendants/history、默认 subagent-tools、typed Gateway/Local/Relay、panel owner/controller及显式管道输入/EOF/取消。stdin批次114/114通过，随后真实进程选择批次106/106通过（shared71、desktop30、截图5），108PNG已保存；本文中“服务缺失”等历史描述不能作为当前缺口。PTY/resize/ANSI、完整持久子会话枚举、原版同尺寸视觉仍未完成；当前结果见 `terminal-input-and-selection-2026-10-08.md` 及页面总计划。

## 1. 交付范围与证据基线

范围：Thread 后台命令输出、交互终端、子 Agent 概览/详情及其面板布局、状态、真实服务边界。排除云环境、自动审查和 Git/Worktrees 实施。先 CodeGraph 检查现有 `panels/`、HarnessApp/AppShell、Connection、session gateway 与 Relay，再复用已提取 ASAR 文件及先前 Thread Panels 证据，逐项重验原始 archive。

- 当前 archive：`/Applications/ChatGPT.app/Contents/Resources/app.asar`，546868793 bytes。
- SHA256：`22466838293554644d2dcb52aa128b389c713bed734900cd926bed76de47aa71`。
- header words：`[4,5779180,5779176,5779170]`；payload base：5779188。
- 本次复核 18 个 asset、50 个 finding 的索引 entry、大小、hash、anchor、snippet hash 与绝对偏移；均匹配。已有提取目录 `/private/tmp/harness-asar-conversation-20261007/` 的四个相关文件也与 archive hash 匹配。其余片段直接按已有索引从当前 archive 读取核验，无新增扩大扫描。
- evidence 偏移是零起点 UTF-8 字节，end exclusive；`absoluteStart = asset.asar_absolute_offset + byteStart`。`snippetByteStart/End` 与 `snippetSha256` 可独立复核。

本文更新 `thread-panels-implementation-contract-2026-10-07.md` 的仓库现状；保留其 ASAR finding ID F01–F50 便于追溯。旧文档关于“尚无面板 UI/仅凭据子进程”的描述不用于当前结论。仓库指纹及行号见本次 evidence `repositoryFiles`，多 agent 继续编辑后应按新指纹核对。

**可立即实施的下一步是给已有页面增加真实能力绑定与异步 owner，再补只读子 Agent 元数据服务。现有 Session API 可以复用；终端与可信 children 服务目前均缺失，不能用模拟输出、全体 sessions 或空操作按钮代替。**

## 2. 当前 Harness 页面与接线

### 已存在的产品接口

路径均相对仓库；本次没有修改这些文件。

```kotlin
// apps/shared/src/im/hikaru/harness/client/panels/ThreadPanels.kt
@Composable fun ThreadPanels(
    state: ThreadPanelsState, onAction: (ThreadPanelAction) -> Unit,
    modifier: Modifier = Modifier,
    childInteraction: (@Composable (ChildAgentDetail) -> Unit)? = null,
)
@Composable fun BackgroundOutputPanel(load: PanelLoad<BackgroundTerminalOutput>, modifier: Modifier = Modifier)
@Composable fun SubagentsPanel(
    state: ThreadPanelsState, onAction: (ThreadPanelAction) -> Unit,
    modifier: Modifier = Modifier,
    childInteraction: (@Composable (ChildAgentDetail) -> Unit)? = null,
)
```

| 现有文件 | 已实现 | 下一步边界 |
| --- | --- | --- |
| `panels/ThreadPanelModels.kt` | scope=HostId+SessionId；BackgroundOutput/Subagents 两类 tab；Unavailable/Loading/Error/Ready；真实 processId+buffer+revision；子会话 ancestry/status/time/canInteract DTO | 构造器校验 ancestry 不等于服务证明。没有查询、订阅、PTY 或 children adapter |
| `panels/ThreadPanelsState.kt` | 纯 reducer，terminal/overview/detail generation，选择/Back/ShowMore/关闭，scope 和 revision 校验 | 无协程/连接 owner；generation 不替代 identity+connection generation fencing |
| `panels/ThreadPanels.kt` | 可关闭 tab；后台可选择等宽文本、空/截断/不可用提示；子 Agent 列表与 transcript 详情；交互插槽 | 文本组件不是 ANSI/PTY renderer；当前无真实内容来源，也未提供 childInteraction consumer |
| `panels/ThreadPanelLayout.kt` | 原版 preferred/max 宽度算法及 compact 可用空间 clamp | 没有原版比例持久化/拖拽完整实现证据 |
| `app/HarnessApp.kt:659` | 仅 Thread 建 panelScope；按 scope、conversationHost.generation/identity remember；初始 openTabs 空；titlebar 打开/关闭，onAction 直接 reduce | 目前不执行 BeginLoad/Receive 的服务 IO；不是已接通终端/子 Agent |
| `shell/AppShell.kt:190` | mainAreaWidth<672dp 时 thread panel overlay；其余并排并保留主区；titlebar inset、无额外 panel 内边距、scrim/关闭回调接缝 | 当前布局源码已存在；本次未验证截图或 600dp 交互 |

`ThreadPanelsState.visibleChildren` 初始 Active 4、Done 10，Unknown 独立展示；`interactiveDetail` 同时要求最新 overview row 与 detail 的 canInteract。这比只检查一次选中项更适合权限变化。现有 `BackgroundOutput` tab 表达的是后台只读输出，不能直接加输入框宣布成为交互终端。

## 3. 原版页面组成、路由与动作

### 3.1 后台命令输出

```text
Thread titlebar / background activity entry
  → scoped durable tab opener
    → LocalConversationBackgroundTerminalTab
      → actual commandExecution.aggregatedOutput / terminal snapshot.buffer
        → XtermOutputPanel (read only) / No output yet
```

F05、F16–F18、F25–F26、F44–F46：稳定 tab ID 为 `background-terminal:<conversationId>:<terminalId>`；durableRoute 保存 terminalId、payloadVersion=1，props 带 hostId/conversationId，标题优先真实 command。既有恢复路径限定匹配 local thread 与 right target。后台正文没有自己的 headerRows；倒序匹配 `commandExecution` item 的真实 ID，聚合输出优先，否则使用 terminal snapshot。

非空输出用 xterm/FitAddon/WebLinksAddon，`disableStdin:true`，lineHeight=1.2。若新完整 buffer 以前一个 buffer 为前缀则 append delta，否则 reset+rewrite。仅接近底部时随新输出滚动；卸载释放 renderer，不等于停止进程。`No output yet` 只用于已确认终端/命令的空输出；没有服务应显示 Unavailable。

Thread 的“Stop all background terminals”（F11、F23–F24）调用 `thread/backgroundTerminals/clean {threadId}`。它清理该 Thread，不是单个 terminalId；行级 spinner 不改变命令范围，失败应显示错误。Harness 没有注册此 RPC。`session.cancel` 不能替代它。

### 3.2 交互终端

```text
TerminalTab (thread / host / terminal session / workspace binding)
  → TerminalPanel (render/input/fit/clipboard/error boundary)
    → FRt terminal manager
      → bound terminal host service
        → main IGe facade → VGe process manager → local node-pty
```

F09–F13、F34–F41、F47–F50：manager 只有绑定真实 host service 才可操作；未绑定明确抛 `Terminal host service is unavailable`。main 将 `terminal:new IGe(...)` 注册到窗口服务，IGe 消费 create/attach/close/resize/write/getShellCwd/getThreadSnapshot/transferSessionOwnership/subscribe；VGe 的本地进程分支调用 node-pty spawn/onData/onExit/write/resize/kill。静态服务链已证实，本次没有启动进程。

| 页面部件/动作 | 实际行为 | Harness 落地条件 |
| --- | --- | --- |
| terminal tab / terminal body | 绑定 host、thread、sessionId、cwd、workspaceRoot；本地分支检查 sessionIds.includes | 真实 TerminalSessionId 与 owner，不拿 Agent SessionId 冒充 PTY ID |
| 新终端 / Cmd+T | 调 onNewTerminalTab，再由 host create 产生真实 session | 单独能力和实际 consumer；不在打开面板时无意创建进程 |
| 输入 / Paste | onData/write；user 与 terminal-generated 输入来源区分 | PTY stdin 服务；后台输出永远无 write 权限 |
| Resize | fit 后传 cols/rows，去重 | 真实 resize ack；只改 Compose 宽度不算 PTY resize |
| cwd/workspace picker | 至少两 root、首次交互前选择；getShellCwd 后按 shell 写 cd；Alt+1…9 | 真实 shell/cwd 与 workspace 授权，不把 session.cwd 当当前 shell cwd |
| Copy | bridge 写选区文本 | 本地 clipboard adapter 和真实选区 |
| Reload | error boundary.resetError 重建 renderer | 不重发执行命令；进程恢复是另一项能力 |
| 关闭 | renderer 卸载/解绑；自己创建的 session 分支 close，已有 session 分支保留 | 明确 renderer owner、process owner 与 transfer；不能统一 kill |

服务事件类型包括 input-status/data/exit/error/init-log/attached；data 追加，init-log 替换快照，attached 带 cwd/shell。具体 wire DTO、持久重连语义和各 shell 支持范围仍须 Harness 自己定义。目录改变属于写操作，不能作为先行只读切片；若后续实现，须保留 POSIX/PowerShell/cmd 各自 escaping 与平台验证。

### 3.3 子 Agent 概览和详情

```text
Thread 子 Agent entry → parent-scoped durable tab
  ├─ overview: Active / Done sections → row → Show more
  └─ selected child: Back + avatar/name + model/reasoning
       → transcript loading/error/history
       → authorized child composer, otherwise read only
```

F06–F08、F19–F22、F27–F33、F42–F43：列表源是 host+parent 的 descendant snapshot、缓存 thread/source membership 和 summary/runtime/collaboration 投影；`discoverSubagentDescendantSnapshot(parent,{reconcile:true})` 加 thread/started 等更新触发重算。不能等价成全部 `session.list()`。

- 字段：真实 conversationId/displayName/objective/status/statusSummary/startedAtMs/lastAssistantMessageAtMs/recencyAtMs/canInteract。按 recency 排序；Active 初始 4、Done 初始 10。Show more 是本地展开已有列表，不是网络分页。
- 原版 status!=done 都进入 Active，其中 waiting 有独立语义；Harness 缺确定生命周期时应继续用现有 Unknown 分组，不从 IDLE 推断 Done/Waiting。
- objective 归一化后截到 60，次选 statusSummary；原版 active fallback Working。Harness 只有真实 Active 才可用这种 fallback，未知状态不要伪称 Working。avatar seed 为真实 ID；名字缺失可显示 ID。
- 点击详情先 verify-descendant：拒绝父自身、循环、错误 host/根会话和不成立谱系，必要时 hydrate ancestors；durable payload 保存 selectedConversationId/version=1。
- 父/子 history await 后再次检查 requestedConversationId；旧选择、关闭或 scope 变化不能发布旧 transcript。Back 清 selection；打开、Back、Show more 都不 spawn Agent。
- 当前 row 的 `canInteract===true` 才显示 composer；权限缺失为只读。原版一处 history rejection 被吞掉，Harness 应显式 Error，不伪装空历史。

ASAR 中出现 spawnAgent/sendInput/wait/resumeAgent/closeAgent 是协作投影名称，不能据此添加一套面板按钮。本次不复制这些业务 mutation。

## 4. 布局与尺寸

F01–F04、F14–F15、F30–F33、F37–F38、F45。原版 `--spacing:.25rem`；以下数值以根字号 16px 推导，未经 GUI 测量。Electron text-sm 为 13px、text-xs 为 12px；主题、字体渲染和缩放不保证与 dp 像素一致。

| 区域 | 原版声明 / 推导 | 当前 Harness 可复用 |
| --- | --- | --- |
| overview | px-3 py-5 → 横12/纵20；滚动 | SubagentsPanel 同方向 padding |
| row | min-h-10/gap-3/p-2 → 最小40、gap12、pad8；avatar24 | 已有 typed row；preview 有值时扩高 |
| row 文本 | 13px、preview leading-5 → 20；单行 truncate | 真实字段与状态文本；无固定60高度约束 |
| 行间/Done 间隔 | gap-1/mt-6 → 4/24 | 已有列表分组 |
| Show more | ms-11/mt-2 → start44/top8 | 已有本地展开 |
| detail header | 横16、gap8；model/reasoning 12px/max50% | Back、名字、元数据与详情状态已有 |
| 后台 output | py-3 ps-3 → 纵12/start12；空态pad16 | 当前纯文本面板pad12，是能力子集 |
| 交互 terminal | ps-4 pt-2 pb-3 → start16/top8/bottom12；scrollbar10px | 尚无 renderer/PTY，不宣称还原 |

原版 width key 为 `app-shell:right-panel-width:v3`，存规范化比例；default 600 只是算法输入：

```text
preferred = default==600 ? max(320,min(height*1.6,main-500),min(640,main-352)) : default
maximum = max(320,main-(mode==full ? 0 : mode==unified ? 320 : 352))
clamp = max(min(320,maximum),min(finite(width)?width:600,maximum))
```

现有 ThreadPanelLayout 实现上述公式并限制 availableWidth。AppShell 在 mainAreaWidth<672dp 用 Full mode overlay；并排时 availableWidth=mainAreaWidth−353dp（主内容至少352dp加分隔线）。600dp 窗口应保留 main surface 布局，面板作为覆盖层且可关闭。此行为是 Harness 已有适配，不把 600 说成原版固定宽度，也不声称本次完成视觉验证。

## 5. 真实服务复用与缺口

| 数据/操作 | 当前真实来源 | 可接线结论 |
| --- | --- | --- |
| 已知子 ID 的 transcript | Connection.session.history(id) → session gateway → DefaultSessionApi.requireAgent(id).session.deriveMessages() | 可以复用，但需先由可信服务验证该 ID 是同 scope 后代；只支持 live session，落盘不等于可恢复 |
| 已授权子会话发送/取消 | Connection.session.prompt(id,message)/cancel(id,keepInbox) | 实际 API 已有；还缺 canInteract/归属证明，不开放无授权 composer |
| provider/model | 明确 AgentOptions(provider/model/reasoningEffort)；消息 ModelMessageSource | create 可传真实选择；详情元数据应来自该子请求/消息或新增元数据服务，不显示父 UI 当前选择 |
| lineage | core SessionHeader.origin=SUBAGENT、parentSession、delegationDepth | core 可存；客户端 SessionSummary 只有 id/createdAt/cwd/agentPreset，未输出 lineage。client CreateSessionOptions 的 parentSession 也不是归属证明 |
| 子 Agent 生命周期 | core AgentStatus=IDLE/RUNNING，session turn-end 有不同 reason | 缺 Waiting/Done/失败/注销的统一业务 snapshot；轮次 completed 不等于整个 Agent 完成 |
| 背景命令输出/PID/exit | 工具 call/result 是泛化消息文本 | 无 typed process/output snapshot/event 服务，不能从工具文本解析成可信终端对象 |
| 交互 PTY/stdin/resize/cwd/close | 当前 Connection 仅 host/session | 无 terminal service/manager/网关/平台 PTY adapter |
| children discovery/verify | registry 可列 live agents，core header 可存谱系 | 无当前 client/gateway/Relay children 接口；Context.children 是 runtime 生命周期关系，不是子 Agent 列表 |
| Relay 消息回补 | RemoteConnection.events/sequenceGaps/historyRecoveries；SessionStreamDriver 可复用消息交互接缝 | 仅消息恢复；不恢复遗漏的终端输出/谱系/生命周期快照 |

`SessionApiGateway` 注册五个 session endpoint；`HostRelayConnection.invoke` 路由 host.describe/handshake、五个 session 方法和 events.subscribe/unsubscribe，其他方法 404。不存在自动拥有原版 clean RPC 或 terminal host service 的情况。新服务必须在 Host、typed gateway、Relay 和 client 各层实现后才启用能力。DesktopGitBackend 的只读 ProcessBuilder 不是终端服务，不能用当前机器 shell/仓库冒充 remote Host。

### Completion capability 是独立边界

现有 `conversation/ConversationController.kt` 已定义：

```kotlin
data class ConversationHostScope(val hostId: HostId, val generation: Long, val identity: MemberIdentity?)
enum class CompletionCapability { LocalIdle, DeliveryAcknowledgedOnly }
```

Local SessionPrompt.awaitIdle 委托真实 agent.awaitIdle；RemoteConnection 的 handle 在收到 receipt 后 `awaitIdle() = Unit`，HostRelay 只序列化 receipt。**Remote 成功只代表 accepted/delivery acknowledged，应保留 RunningUnknown，不能变 Idle/Done。** 即使 LocalIdle 返回，也只证明该 live agent 当前 idle，不证明子 Agent 永久 Done。child status 必须来自专门 lifecycle snapshot/event；取消 ack 不证明 PTY exited。

当前 HostEventStream capacity=256、trySend，将 event 的 type/seq/data 投到 RelayEvent；未带原 envelope 的 time/surfaceOp/sourceEventSeqs/ignorable，也不发送 Agent runtime 状态。history 回补可修复消息视图，不能补造已丢过程状态。Elapsed、Done 相对时间必须有服务提供的 epochMillis 来源，不能用 seq 或本地接收时间伪造。

## 6. Owner、状态与协议实施规则

以下是后续设计要求，**不是仓库已有接口/已注册 endpoint**。继续复用现有 panel DTO/reducer，避免另建第二套页面状态。

1. 绑定 owner 包含认证 MemberIdentity（server/tenant/member）、HostId、connection generation、parent SessionId、实际 Connection 实例；本地 identity=null 仍须 verified local Host。`ThreadPanelScope(hostId,conversationId)` 只作为页面键。服务发布必须检查完整 owner token，不依赖 remember 清 UI 代替 IO fencing。
2. 分离 overview/terminal/detail 的 Job 和最新 request token；Capture reducer **更新后** generation。refresh 只替换同类只读任务，不取消在途 prompt/cancel；UI 关闭终止订阅/详情工作并 fence late callback。
3. terminal 每 process 保存 revision high-water；同 revision/旧 revision 忽略。当前 reducer 仅在旧值仍 Ready 时比较 revision，BeginTerminalLoad 会丢 Ready；adapter 要避免 loading 重置后接受旧 snapshot。init-log/replace 与 data append 分开，不凭重复 buffer 猜 stream 顺序。
4. overview 有 complete/revision/cursor；Unavailable、Error、Loading、不完整列表和成功 Ready(empty) 分开。现有 DTO 尚无 completeness/cursor 字段，补协议时明确扩展。流更新可用 ReceiveOverview 保留 selection；BeginOverviewLoad 会清 selectedChild，不用于每条 notification。已选 child 消失/权限撤销/谱系不再成立时立即清 detail/交互。
5. detail 校验完整 owner+selection+request+detailGeneration；Back、关闭、换 Host/identity、断线时旧返回无权发布。history 失败有 Retry，不自动 spawn/resend。
6. 断线清服务数据/禁用写操作；可保留同身份同 Host 的路由/草稿意图，但下一代连接先重新验证 children 和权限，再读取 snapshot/history。换 Host/identity、logout 清 panel 内容、订阅、child 草稿，防止旧消息泄露。
7. 接受 prompt 后 history 失败保留 receipt/accepted 状态，不显示“未发送”；草稿 revision 防异步清掉用户新编辑；重复 submit 有独立保护。子交互可复用 ConversationController 的真实 operations 与 completion capability，不能给 detail 另造一次隐式发送流程。
8. snapshot/events/read 与命令能力分开：后台输出无 stdin；交互终端显式 create/write/resize/queryCwd/close；cleanAllBackground 独立 scope/ack。停止订阅、关闭 UI、终止进程及 session.cancel 是四种不同动作。所有命令需要实际注册消费者和可见失败状态。
9. 新 wire 契约建议带 typed process/child ID、Host/parent、revision/cursor、complete、epochMillis、lifecycle、origin/verifiedAncestorPath、canInteract/historyAvailability、buffer truncation、error/retryability。提供完整 snapshot 及 gap recovery；未知枚举保留 Unknown，不凭未知推断授权/完成。
10. shared 用不可变 StateFlow/controller 和注入 CoroutineScope/operation；平台 adapter 消费 typed DTO。冷启动静态注册，无运行时假数据、JS/HMR/ClassLoader 或动态 slots。

## 7. 可独立实施的切分

| 批次/owner | 建议写集与交接 | 可交付验收 |
| --- | --- | --- |
| A：客户端 panel owner | `apps/shared/.../client/panels/` 的 controller/repository capability adapter 与测试；父任务接 HarnessApp bind/onAction | 保持现有三个页面签名；完整认证 scope/request fencing；真实 unavailable；延迟/权限撤销/事件 refresh 不打断 send |
| B：只读子 Agent 服务 | core/session-api 的 verified descendants/metadata 读投影；typed contracts+gateway；Local/Relay/client adapter | SessionHeader origin/parent+registry/log 是唯一谱系来源；真实 timestamp/lifecycle/权限和 complete；已验证 live child 复用 history |
| C：只读后台 process snapshot | JVM Host process/output ownership 服务和正式协议/adapter；复用 BackgroundOutputPanel | 真实 process ID、buffer/revision/exit/truncation；gap/reconnect snapshot；能力缺失仍 unavailable，先不开放 kill/write |
| D：交互 PTY与平台 renderer | 独立 JVM terminal manager/provider、typed gateway/Relay、平台 ANSI renderer/clipboard；父接 static surface | 真实 create/input/resize/cwd/exit/close；明确 process 所有权。平台缺能力不降级成伪成功 |

优先 A+B，只读垂直切片可复用现有会话 API；C 必须先有真正 process producer，D 另行授权和平台验证。服务实现 owner 与 HarnessApp/shell integration owner 分开交接，避免多个 agent 同改 Connection/网关/main。以上路径只是未来协作切分，本次写集只有两份 docs。

## 8. 验收与已知未知

### 后续验收

- [ ] 打开两类 panel 不 create session/PTY、不 spawn；关闭/Back/Show more 是真实本地动作，停进程独立。
- [ ] 无服务显示 Unavailable；成功空列表才显示无子 Agent；失败不显示空或成功。
- [ ] parent/self/cycle/wrong-host/unknown-origin 被拒；新 scope、旧 generation、旧 selection 的延迟返回和事件不能写入。
- [ ] 子权限撤销后即时去 composer；history non-live/失败有真实错误；Remote receipt/awaitIdle Unit 不显示 Done。
- [ ] 高频 event refresh 不取消 prompt/cancel；accepted 后 history 失败保留发送结果，draft revision 和 duplicate submit 正确。
- [ ] terminal append/replace、旧 revision、截断、UTF-8 分块/ANSI/CR/alternate screen、退出错误及断线重连有确定消费者。
- [ ] 600dp overlay 不压缩 main 布局，可关闭；大屏并排保留至少352dp主区；titlebar inset/scroll/选区可验证。
- [ ] 本地服务只消费 verified local Host；remote 无正式服务明确不可用，不执行父机 shell。

### 未解决边界

- 没有 terminal/children Host API 的可用版本、capability 标识或真实 provider；本文不把建议 endpoint 当现有服务。
- 子 Agent 业务 Done/Waiting/失败/取消与持久恢复策略尚未定义；core header 能存谱系不说明当前所有 spawn 路径完整填写它。
- 现有 history 仅 live Agent；持久 session 日志读服务是否可供 UI恢复需独立服务工作，不能由本文保证。
- 现有 panel scope 无 identity/generation，overview 缺 completeness/cursor，background 只表达一个已选输出；多 process tab/terminal manager 生命周期是后续扩展。
- 原版后端的完整 descendant/terminal wire schema、跨窗口 transfer、重连进程存活及全部 shell 行为未做运行验证。
- 原版字体、根字号和 header 实际高度没有 GUI 测量；源码尺寸推导与现有 Compose 适配不能视为像素一致。

本次只完成静态证据与 JSON 完整性复核；没有运行构建、测试、PTY、Harness 服务或参考 App。两份文档交付后释放写集。
