# 会话终端 / 子 Agent 面板实施契约

日期：2026-10-07。状态：只读分析完成；本次仅交付本文及同名 `.evidence.json`，未实施、未构建、未启动 GUI。

> 同日后续状态校正：正文保留当时的反向分析。当前已有真实 ExecProcessProducer、默认 subagent-tools、live descendants/谱系验证/child history、ThreadPanelsController 与生产面板；子 Agent 真实默认父子 loop/Room/LocalConnection/panel repository 组合已隔离通过2/2，合约15/15另记。本文“无后台进程或children服务”与A/B待实施建议不再代表当前缺口。交互PTY/stdin/resize/ANSI、完整历史枚举、远端与面板视觉/点击验收仍分别处理；本轮正补面板验收，详见 `../replication-plan-2026-10-07.md`。

## 1. 范围、证据与结论

范围限会话内交互终端、后台命令输出 tab、子 Agent 概览及详情。本文只分析承载它们所必需的 thread panel 接缝，不接管 Home/Thread、Connections 测试或 Shortcuts。排除语音、宠物、电脑/浏览器操控、快照、cloud computer、云端项目环境、自动审查、团队计费、图片库。父代理 Comet probe 已为 none，本子任务没有重复 probe。

已先使用仓库 `.codegraph` 的 CodeGraph 探索终端、exec/session/background、Agent/children、连接网关和 UI，再检查源码及当前 ASAR；补充负向搜索仅用于核对服务缺口。根目录旧 bundle 不作为原版证据。

**当前 Harness 没有可调用的会话 PTY、后台进程服务或 `agent.children` 服务。** 现有 `session.list/create/history/prompt/cancel` 可用于真实会话读写，但不足以声称这两个面板已经接通。原版后台输出是只读 xterm；“Stop all background terminals” 调用的是整个 thread 的清理 RPC，不是单进程 kill。

证据 JSON 保存：当前 archive 路径/大小/mtime/SHA256，18 个相关 asset 的路径/大小/SHA/ASAR offset，50 个关键符号及片段偏移，仓库源文件 SHA 与带行号源码，以及负向搜索输出。偏移统一为 **从 0 开始的 UTF-8 字节，end exclusive**；absoluteStart 可直接定位 archive，snippet 另有自身起止及 SHA。尺寸分为源码声明和有条件推导，不声称运行时像素一致。

### 当前参考安装指纹

- 路径：`/Applications/ChatGPT.app/Contents/Resources/app.asar`。
- 大小：546868793 bytes。mtime UTC：2026-10-06T16:00:27.362314+00:00。
- SHA256：`22466838293554644d2dcb52aa128b389c713bed734900cd926bed76de47aa71`。
- ASAR header words：`[4, 5779180, 5779176, 5779170]`；payload base：5779188。直接只读解析 header/index，再按 entry offset/size 读取原始 bytes；交付时重新计算 archive/asset SHA。

下表路径均相对 ASAR；其余消费者的完整路径和 SHA 在 JSON `assets` 内。

| 当前 asset | bytes | SHA256 | archive absolute offset |
| --- | ---: | --- | ---: |
| `webview/assets/local-conversation-background-terminal-tab-79ac30138cc0.js` | 2576 | `3bb7a46515472ec89f21f0142ffcf5e27af81ee59104807e6b2a438117c47d73` | 278165049 |
| `webview/assets/local-conversation-subagents-panel-tab-1b63b2705858.js` | 7668 | `31122f79f0cbd474b217007d9f9e825b72727091995717ef1b1848a7e474ae9e` | 278268247 |
| `webview/assets/subagent-panel-d27c2c58ea7d.js` | 6033 | `72fa2a3cc026d1a74166318d9b78f0fe0c2127cb1a6525a708bbd5709b7494ca` | 466546027 |
| `webview/assets/terminal-panel-a9f4a9caf6f8.js` | 16093 | `7d5e000d496a76868c13b6c8ab13df9a0d7d79e8c00a5d5b7a4d4c31db68eafe` | 485216140 |
| `webview/assets/terminal-panel-d0f289883c52.css` | 1424 | `7dbba3bed0635d658a0af672b1dd0574edf0e3bcd39bcc41688941bf1e8cc049` | 485232391 |
| `webview/assets/xterm-output-panel-261b3212daa2.js` | 4363 | `f834f33b88e1dd4a8fa298020e9e5abd0c48e3df30a16a4f12f43babd062b0e5` | 533166085 |

## 2. 原版组件及行为

### 2.1 后台命令输出 tab

`LocalConversationBackgroundTerminalTab` 接收 `conversationId/hostId/terminalId/renderSlots/setTabState`。倒序寻找会话 turns 中 `type===commandExecution && id===terminalId`，使用 `aggregatedOutput ?? terminalSnapshot.buffer ?? 空字符串`。非空挂 `XtermOutputPanel`；空内容显示 “No output yet”。没有内部 headerRows。

`XtermOutputPanel` 使用 xterm、FitAddon、WebLinksAddon、ResizeObserver；`disableStdin:true`、`cursorStyle:bar`、`letterSpacing:0`、`lineHeight:1.2`。字体及字号来自当前 code theme。新输出以旧输出为前缀时追加 delta，否则 reset 后重写；仅当原来接近底部时随输出滚动。输出读取快照回写 `setTabState({readOutput:...})`。组件卸载释放 renderer/observer，不因此停止后台命令。

`open-local-conversation-background-terminal-tab-d01c9ec06d9d.js` 负责打开：稳定 ID 为 `background-terminal:<conversationId>:<terminalId>`，标题优先实际 command，持久化 `terminalId`、payloadVersion=1。当前恢复逻辑要求匹配 stable ID、local thread route 和 right target。不要把它还原成可输入的 shell，也不要添加本 tab 没有的 Restart/单进程 Stop。

### 2.2 交互式本地会话终端

`terminal-tab-16cdb3f968db.js` 的本地分支要求 `sessionIds.includes(sessionId)`，使用 host/conversation/cwd/sessionId/workspaceRoot 绑定。会话有至少两个 workspace roots、且首次用户交互前，才显示目录选择器。选择目录先查真实 shell/cwd，再写入 shell 对应的 cd 指令；Option/Alt+1…9 绑定目录选择。超出会话 workspaces 的警告可 Dismiss 或调用真实 `onNewTerminalTab`。

`TerminalPanel` 输入经 terminal manager.write，区分 user 与 terminal-generated；create 带 cwd/cols/rows/host/conversation，fit 后 resize；Cmd+T 调用新终端 consumer。Copy/Paste 使用 clipboard bridge。错误态 Reload 调 `resetError` 重挂渲染器，**不代表重执行命令**。

目录命令必须保持原版 shell 语义：POSIX `cd --` 并转义单引号；PowerShell LiteralPath 并转义单引号；cmd `cd /d` 且拒绝 `%`/`!` 的危险插值。将路径写到 UI 状态并不能改变实际 cwd，只有 host 执行确认或查询结果可更新它。

生命周期有所有权差异：传入已有 sessionId 的 renderer 卸载保存 alternate-screen/释放 renderer；组件自己创建 session 的分支卸载会 close。普通 panel tab close 经过 `tabType.dispose/onClose`，不能统一理解为 kill。

### 2.3 子 Agent 概览 / 详情

`LocalConversationSubagentsPanelTab` 按 host+parent scope 读取列表，存在功能 gate。列表来源 `app-primary-1a9a7a89414a.js` 的 `yJ`（导出别名 Ql）：合并 parent/source membership、缓存 conversations、descendant discovery、summary/runtime、`subAgentActivity`/`collabAgentToolCall`。`discoverSubagentDescendantSnapshot(parent,{reconcile:true})` 及 thread/started、archive/delete/unarchive、conversation-state 更新参与重算；不能将纯 `session.list` 冒充该投影。

- 项目字段：conversationId、displayName、objective、status、statusSummary、startedAtMs、lastAssistantMessageAtMs、recencyAtMs、canInteract。
- `status!==done` 归 Active，done 归 Done；等待为明确 waiting 状态。按 recencyAtMs 降序，缺值排序为 0。
- Active 初始最多 4，Done 最多 10；Show more 展示已有列表余项，非服务端分页；无 Active 显示 “No active subagents”，Done 空时整段不显示。
- objective 标准化后截到 60，次选 statusSummary，再选 Working；preview 单行。avatar seed 是真实 conversationId。
- Active elapsed 取 startedAtMs；Done 的相对时间取 lastAssistantMessageAtMs，否则 recencyAtMs。没有时间时不伪造。
- 点行选择详情；Back 清空 selection；详情 header 是返回按钮/avatar/name/model/reasoning，正文复用该子会话 transcript，loading 为 “Subagent transcript”。只有 `canInteract===true` 才允许交互。

`open-local-conversation-subagents-panel-5e96735cfbf7.js` 先 verify-descendant：拒绝自身、祖先循环、错误 host、根会话或不成立的 ancestry；必要时 hydrate 缺失 ancestors。按 parent 建稳定 tab，持久化 selectedConversationId/payloadVersion=1。加载父 includeTurns 和子 history 后再检查 selection 是否仍相同，避免旧异步结果覆盖新选择。打开详情不 spawn Agent。原版一处 history rejection 被吞掉；Harness 建议显式显示加载错误。

`spawnAgent/sendInput/wait/resumeAgent/closeAgent` 是协作事件中被投影的方法名，**不是已证实的概览按钮集**。不据此添加创建、暂停、恢复、删除等操作。

## 3. 尺寸与视觉证据

来源：subagent-panel、两种 terminal renderer 的 className/CSS，以及 app-shared CSS 和 app-initial 的 panel width 函数。`--spacing:.25rem`；下表 px 数字是在根字号 16px 时的推导值，未通过 GUI 测量。Electron 覆盖 `text-sm=13px`、`text-xs=12px`、medium=500、UI weight=430；不能沿用全局默认的 12/11px。

| 部位 | 原版声明 | 有条件推导 / 限制 |
| --- | --- | --- |
| 子 Agent overview | `px-3 py-5`、overflow-y-auto、max thread-content width | 横 12 / 纵 20；body thread-content token 为 48rem，约 768，不是面板固定宽度 |
| 列表 row | `min-h-10 gap-3 rounded-lg px-2 py-2` | 最小高 40，gap 12，pad 8；圆角取主题 radius-lg |
| row 主行 | `min-h-6 gap-2 text-sm` | 最小高 24、gap 8、13px |
| preview | `text-sm leading-5`、单行 truncate | 行高 20；有一行 preview 的 row 约 60 高，是推导而非 fixed height |
| row avatar / 详情 Back | `size-6` | 24×24 |
| section 标题 | `mb-2 gap-3 px-2 text-sm` | 下间距 8、gap 12、横 pad 8 |
| row 列表 / Done 段 | `gap-1` / `mt-6` | 4 / 24 |
| Show more | `ms-11 mt-2` | start 44、top 8 |
| 详情 header | `h-full gap-2 px-4` | gap 8、横 pad 16；高度由宿主 headerRows 决定，无固定测量值 |
| 详情 model/reasoning | `max-w-1/2 text-xs truncate` | 最多 header 50%、12px |
| 后台 xterm | `py-3 ps-3` | 纵 12、start 12 |
| 后台无输出 | `p-4 font-code text-size-code-sm` | pad 16；code-sm=主题 chat-code-font-size−1 |
| 交互终端 | `ps-4 pt-2 pb-3` | start 16、top 8、bottom 12 |
| 终端 scrollbar | terminal-panel CSS 的 width/height 10px | 10px；fg/bg 和字体来自主题，xterm 背景透明处理 |

右侧 panel 宽度 key 为 `app-shell:right-panel-width:v3`。defaultWidth=600 只是算法输入；当前算法：

```text
preferred(default, main, height) =
  if default == 600 then max(320, min(height*1.6, main-500), min(640, main-352))
  else default
maximum(main, mode) =
  if mode == full then max(320, main)
  else max(320, main-(if mode == unified then 320 else 352))
clamp(width, main, mode) = max(min(320, maximum), min(finite(width)?width:600, maximum))
```

持久化的是规范化 0…1 比例；恢复按模式和可用宽度转换。tab opener 只在 tab 类型支持 bottom target 时使用 bottom，否则 right。上述后台 durable restore 限 right。不要把其他 summary-layout 的 300px 套给终端或子 Agent；titlebar toggle 有 aria-label/aria-pressed、disabled 和 tooltip，但源码 `size:toolbar` 不是固定按钮像素测量。

## 4. 动作到真实消费者映射

| 动作 | 原版消费者 / 服务 | Harness 当前可用性与实施规则 |
| --- | --- | --- |
| 打开后台输出 | open-background → durable descriptor → scoped tab opener → BackgroundTerminalTab → commandExecution 或 terminal snapshot | 无 commandExecution/PTY snapshot 服务；真实输出未提供时显示 unavailable，不显示伪命令或模拟 buffer |
| 停止所有后台终端 | thread 终端入口 `cleanBackgroundTerminals(conversationId)` → `thread/backgroundTerminals/clean {threadId}`；terminalId 仅用于行 spinner，失败 toast | endpoint 未注册；session.cancel 不是它的替代，当前按钮必须不可用 |
| 打开/新建交互终端、Cmd+T | TerminalTab/onNewTerminalTab → TerminalPanel → FRt.create → bound terminal hostService.create | 无 terminal hostService/create；静态注册能力缺失原因，禁止空 onClick |
| 键盘输入、Paste | TerminalPanel/onData 或 clipboard → FRt.write → hostService.write | 无 stdin/PTY 服务；后台输出 tab 原版本身禁止 stdin |
| 调整尺寸 | FitAddon → manager.resize → hostService.resize（去重） | 无 resize 服务；仅改变文本组件尺寸不能算成功 |
| 选择 cwd | getShellCwd → shell 对应 cd → write(user) | 无 shell/cwd 查询；不能用 session.cwd 当实时 shell cwd |
| Copy 输出 | clipboard bridge 消费选中文本 / 渲染后输出 | 可由平台 clipboard adapter 本地实现，需真实可读输出与非空选区；不是远程 endpoint |
| Reload renderer | error boundary.resetError | 可作为本地 renderer 恢复；不自动执行命令，不写无服务回调 |
| close 终端 tab | tab.dispose/onClose；TerminalPanel 依 session 所有权决定释放或 close | 生命周期分开建模，关闭 UI 不等于取消 Agent 或任意 kill |
| 选子 Agent | onSelect → verify-descendant → hydrate → load child history → recheck selected ID | 有已知 live session 的 history；缺 children/ancestry/read metadata，当前无法可信枚举并验证子 Agent |
| Back / Show more | 清空 selection / 客户端已有列表展示 | 本地状态动作可实现，但先有真实 Ready 数据；不发 spawn/cancel |
| 子会话发送 / 停止生成 | 子 transcript 的真实会话交互，且 canInteract true | 对确定 live SessionId 可用 session.prompt/cancel；缺服务明确授权的 canInteract 时保持只读 |

原版终端服务链已追到 bundle 内真实消费者：app-shared 的 `IRt as gJt` 为 `FRt` manager，runtime gate `i6.terminal!=null&&kRt(i6.terminal)` 绑定 host service；未绑定会抛 “Terminal host service is unavailable”。manager 订阅 input-status/data/exit/error/init-log/attached，data 追加，init-log 替换 snapshot，attached 提供 cwd/shell。`.vite/build/main-C7cfj__D.js` 的 `IGe` facade 接 create/attach/close/resize/write/getShellCwd/getThreadSnapshot/transferSessionOwnership/subscribe；注册 `terminal:new IGe(...)`，`VGe` manager 进入本地 `node-pty` spawn/onData/onExit/write/resize/kill。这是静态消费者证据，未启动任何 PTY。

## 5. Harness 现有字段、事件、限制

证据 JSON `repositoryFiles` 对应本次读取的源码版本，记录 SHA 和 sourceLines；以下路径相对仓库。

| 来源 | 已有事实 | 面板限制 |
| --- | --- | --- |
| `apps/shared/src/im/hikaru/harness/client/connection/Connection.kt:16` | Connection 仅 host/session；158 起 list/create/history/prompt/cancel | 无 terminal、exec、children API |
| 同文件 :28/:36/:140 | CreateSessionOptions 有 cwd/parentSession/seedLength/agentPreset；AgentOptions 有 provider/model/reasoningEffort；summary 仅 id/createdAt/cwd/agentPreset | 无 displayName/objective、父谱系输出、生命周期、elapsed、exitCode、PID、terminalId |
| `modules/session/src/im/hikaru/harness/session/SessionHeader.kt:8` | origin 有 SUBAGENT；header 含 parentSession/seedLength/origin/delegationDepth | Core 可存谱系，但 summary/API 未输出；parentSession 单独不证明是子 Agent |
| `modules/session-api/src/im/hikaru/harness/session/api/SessionApi.kt:61` | list 来自 live agent registry；history 由 live session.deriveMessages；prompt followup/awaitIdle；cancel agent.cancel | 已落盘不等于可枚举/恢复历史；未 live 会报 Session is not live；cancel 不杀 PTY |
| `modules/api/gateway/src/im/hikaru/harness/api/gateway/session/SessionApiGateway.kt` | 注册五个 session endpoint；缺模型配置返回 404 / 无效 Agent 422 | 不存在 exec/children endpoint，不能仅增加客户端调用名 |
| `apps/jvm-app/src/im/hikaru/harness/desktop/connection/HostRelayConnection.kt:84` | 路由 host.describe/handshake、五个 session 方法、session.events.subscribe/unsubscribe；未知 endpoint 404 | 无会话终端或 children，错误 host/forbidden 等访问受限 |
| `modules/agent/src/im/hikaru/harness/agent/AgentTypes.kt:54` | AgentStatus 只有 IDLE/RUNNING；runtime 有 created/disposed/status/inbox 等事件 | IDLE 不能映射 Done/Waiting；runtime 事件未自动向 UI relay |
| `modules/agent/src/im/hikaru/harness/agent/Agent.kt` / `AgentRegistry.kt` | Agent API 无 children；registry 可 live create/restore/get/list | Context.child 是生命周期所有权，不是 agent.children 业务关系 |
| `modules/session/src/im/hikaru/harness/session/SessionEventKey.kt:47` | envelope type/seq/time/data/surfaceOp/sourceEventSeqs/ignorable；APPEND/REPLACE | 时间属于 envelope，不能从消息正文或 seq 捏造 |
| `SessionEventKeys.kt` / `SessionEventTypes.kt`（同目录） | turn/step start/end、user/message、assistant/chunk/message、request/header/context；turn-end 区分 completed/aborted/blocked/error/max-tokens/interrupted | 轮次完成不等于整个子 Agent 永久结束；waiting 需独立语义 |
| `SessionProjection.kt`（同目录） | deriveMessages 投影 APPEND 用户/助手消息；foldRequestHeader/Context 有请求配置 | history 丢失事件时间和其他事件，无法还原完整运行状态 |
| `apps/jvm-app/src/im/hikaru/harness/desktop/connection/HostEventStream.kt:25` | capacity=256，监听 SessionEvents.Appended，trySend；RelayEvent 带 eventId/hostId/streamId/sequence/event/payload=data | 丢 envelope time/surfaceOp/sourceEventSeqs/ignorable 和 header lineage；缓冲可丢事件；不转发 Agent runtime status |
| `contracts/harness-protocol/src/im/hikaru/contracts/harness/relay/RelayEvent.kt` | relay 只有上述六字段 | 注释所称 sequence gap + history 只能恢复消息，不能恢复终端/children 元数据 |
| `modules/tools/src/im/hikaru/harness/tools/Tools.kt`、Connection tool-content | 泛化 tool-call/result 文本 | 不具备 stdout/stderr stream、PTY、PID、exit、kill 的进程契约 |
| `ClientUiSlots.kt:39` / `AppShellState.kt:38` | titlebar actions / summary 静态 slots；PanelKind 尚无 terminal/subagents | 未有面板专用 typed outlet；shell 接缝由父代理协调，不能使用运行时动态 slot |

补充负向搜索匹配 `agent.children`、exec start/write/resize/kill/list、Exec API/Service、pty/backgroundSession/ProcessBuilder，生产 Kotlin 中仅发现 `DesktopAppSessionStore.kt` 使用 `/usr/bin/secret-tool` 的凭据子进程。它不是会话终端服务。缺口结论同时依据上述正向 API/网关的穷尽注册，非仅凭无搜索命中。

## 6. 静态 typed DTO / repository 建议（均为提案）

采用编译期 Kotlin DTO、冷启动 immutable slot/schema 注册、Flow 状态投影。禁止动态 JS/React renderer、HMR、ClassLoader、运行时声明 slots；generic JsonElement 留在协议解码边界，组件消费封闭 typed 状态。下面名称不代表仓库已有服务。

### 6.1 面板 DTO

```text
ThreadPanelKey(hostId: HostId, parent: SessionId)
TerminalKey(owner: ThreadPanelKey, processId: ProcessId)
TerminalMode = BackgroundOutput | Interactive
LoadState<T> = Unavailable(reason, missingCapabilities) | Loading
             | Ready(value, revision, completeness) | Failed(typedError, retryability)
TerminalSnapshot(key, mode, command?, cwd?, shell?, buffer, outputVersion,
                 processState, exitCode?, ownerKind, capabilities)
TerminalEvent = Attached(snapshot) | Output(delta, sequence, outputVersion)
              | ReplaceOutput(snapshot) | Exited(code?) | Failed(error) | Gap(cursor)
ChildAgentSnapshot(hostId, parent, id, origin, verifiedAncestorPath,
                   displayName?, objective?, statusSummary?, lifecycle,
                   startedAt?, lastAssistantAt?, recencyAt?, provider?, model?,
                   reasoningEffort?, canInteract, revision)
ChildLifecycle = Running | Waiting | Completed | Failed | Cancelled | Unknown
PanelSelection = Overview | Child(id, loadGeneration)
```

枚举扩展要配序列化未知值策略；使用已有 HostId/SessionId，新 ProcessId 与普通工具 call ID 分离。所有时间要有明确 wire 单位/clock 来源，不能盲将 current createdAt 视作毫秒。nullable 表示真实缺失：不填示例命令、虚构 objective/名字/时间/模型。label 可由真实 ID 降级，不伪造人物身份。

Ready(empty) 只能来自成功、完整的查询；服务未接入是 Unavailable，失败是 Failed，尚未查完是 Loading/不完整结果。若只有 Agent RUNNING/IDLE，则无法满足原版 active/done 分组，应保留 Unknown 并显示明确状态，不能把 IDLE 自动标 Done 或 Waiting。只有服务确认 SUBAGENT origin + 同 host + 有效 parent ancestry 才进入列表。

### 6.2 Repository 与能力边界

`ConversationTerminalRepository` 建议将 snapshot/events 与 commands 分开：observe(owner)、readSnapshot(key)、可用时 createInteractive/write/resize/queryCwd/close、按 owner 的 cleanAllBackground。后台输出 DTO 不暴露 write 能力。不可用返回显式类型，不提供一组什么都不做的 lambda。Clipboard/Back/Show more 属客户端真实本地动作，不伪装成 host commands。

`SubagentPanelRepository` 建议提供 observeVerifiedDescendants(owner)、verifySelection、loadChildTranscript、observeChildState。优先补宿主元数据读服务，内部复用 live `session.history`；non-live 返回已知限制，后续有持久日志读接口才能恢复。prompt/cancel 复用真实 SessionApi，执行前校验当前 host、live、canInteract，不能根据是否有按钮自行推导授权。

能力从实际注册的 Host service/API 静态暴露，远端从握手 capability/version 验证。协议名称（例如 terminal 或 children 新 endpoints）必须先定义、实现和注册再消费；原版 `thread/backgroundTerminals/clean` 不是 Harness 自动拥有的 endpoint。客户端不得把 404 转成成功。

流事件契约需包含 event time、谱系及生命周期语义，snapshot revision/cursor、APPEND/REPLACE 和缺口恢复入口；重连先 snapshot 再按 cursor 去重补流，gap 时请求可恢复完整数据的 snapshot/event range。现有 history 仅能用于消息回补，不能用于补造丢失的过程状态。stdout 的 ANSI/CR/alternate screen、UTF-8 分块与 buffer 限额应在服务/renderer 边界明确；buffer 截断必须有标记。

选择子会话使用 loadGeneration，旧加载完成不能覆盖新选择；验证 ancestry 要防循环、host 切换和中途删除。关闭 panel 停止订阅及 renderer；进程是否继续由 ownerKind 和 host 约定决定。stop-all 仅在真实清理服务存在时开放，并等待确认/exit 状态，不能只清空列表。

用户要求的 `SHUAIAPI/gpt-6.1-sol` 作为待实施时验证的 provider/model 目标：用真实 provider catalog、AgentOptions 与 request/model source 确认 provider=SHUAIAPI、model=gpt-6.1-sol 的实际标识对应；缺配置按现有模型配置错误呈现，不自动改选或假称已路由。本文没有改模型配置或调用服务，面板显示只能取真实请求/会话元数据。

## 7. 后续文件写集与可实施批次

以下是后续建议写集，**本次没有修改这些文件**。提交前与父代理协调 shell/Connection/relay 共享接缝，保留其现有脏改动。

| 批次 | 建议路径 / 写集 | 可交付边界 |
| --- | --- | --- |
| A：typed panel 状态和不可用 UI | 新增 `apps/shared/src/im/hikaru/harness/client/threadpanel/{ThreadPanelModels,ConversationTerminalRepository,SubagentPanelRepository,ThreadPanelsState}.kt`，以及 `TerminalOutputPanel.kt` / `SubagentsPanel.kt` | 编译期 DTO、已注册能力检查、真实 Unavailable/loading/error 状态；禁止生产 fixture 数据。先不暴露没有消费者的可点击操作 |
| B：真实子 Agent metadata | 新增 session-api 的子会话元数据/谱系读取及 typed response；扩展 session gateway、client SessionApi/summary mapping、relay envelope/snapshot | 由 SessionHeader.origin/parent + live registry + event log 建投影；提供可靠 children/ancestry、timestamp、lifecycle；先实现只读概览和详情 |
| C：会话本地终端 | 新增 JVM host 的终端 service/manager、process ownership/buffer snapshot；新增正式协议 DTO/注册网关与 client adapter | 用真实 PTY provider 验证 create/write/resize/exit/close；后台 exec 另有关联 process ID、snapshot 和 clean-all。没有 PTY provider 时保持 unavailable |
| D：可验证 renderer | 新增上述组件对应的状态/投影/生命周期测试，平台 renderer adapter 与有限 UI 验证 | ANSI 输出、尾随滚动、copy、错误 retry、selection race、关闭所有权；未来独立运行构建及 UI 对照 |

Shell 接缝仅作为交接：`apps/shared/src/im/hikaru/harness/client/app/ClientUiSlots.kt` 加 panel 专用 typed props/key 并在启动 schema 注册；`.../shell/AppShellState.kt` 的 tab/width/selection 承载由父代理决定。Home/Thread、`main.kt`、`HarnessApp` 及 Connections 测试不由本子任务改写。新增协议可放 `contracts/harness-protocol/src/im/hikaru/contracts/harness/threadpanel/`；宿主终端 adapter 可放 `apps/jvm-app/src/im/hikaru/harness/desktop/terminal/`，平台能力不可搬进 common UI。

**实用下一批：先做 A 的 typed 契约及不可用状态，再做 B 的只读子 Agent metadata 垂直切片。** B 能复用已有会话 history，仍须补 origin/ancestry/status/time 读出；禁止先用全体 session.list 做假子 Agent 列表。交互终端 C 需要实际 PTY 服务，适合作为独立后续批次。模型 provider 配置验证与 UI 状态分开验收。

## 8. 验收清单

### 本次分析交付

- [x] 当前安装 ASAR 直接只读解析；相关 asset path/size/SHA/字节偏移与片段固化。
- [x] CodeGraph 先行；真实 Harness Connection/gateway、Agent、session events、relay、现有 UI 接缝检查完成。
- [x] 原版组件/尺寸声明与推导分别标注；未宣称截图或像素一致。
- [x] 按钮映射到真实消费者；stop-all、renderer reload、关闭所有权、打开子 Agent 不 spawn 的语义明确。
- [x] 缺失 terminal/exec/children 服务明确 unavailable；无虚构数据/空回调建议。
- [x] 仅写两份约定 docs，保留原有脏文件；未实施、未构建、未运行 GUI。

### 后续实施必须通过

- [ ] 启动时 typed registration 固定；slot 不存在动态声明。Unavailable/Loading/Ready-empty/Failed 四者可区分。
- [ ] 每个可点击命令都有实际平台/host consumer，能力缺失或 endpoint 404 不呈现成功。
- [ ] 同 host、SUBAGENT origin、谱系完整且无循环；不存在将所有 session 或 Context.children 当子 Agent 的行为。
- [ ] Active/Done/Waiting 使用确认生命周期；时间/模型/目标缺失不造值；历史 non-live 限制和请求错误可见。
- [ ] 子 Agent Back/Show more 无额外 spawn；详情加载 race 不覆盖新选择；canInteract 仅接受真实服务确认。
- [ ] PTY create/write/resize/cwd/exit/close 使用真实进程；background output 只读；clean-all 作用范围为所选 thread，不能误杀其他会话。
- [ ] ANSI/CR/多字节 chunk、输出替换、滚动不抢阅读位置、截断标记、buffer gap recovery 可验证。
- [ ] UI tab close 和进程结束分别验证；重连去重、APPEND/REPLACE、256 缓冲丢事件恢复有完整数据服务支持。
- [ ] 按 Electron 13/12px token 与条件推导尺寸实现；未来截图验收另行提供，不能以源码一致代替像素验收。
- [ ] 实际 provider/model 元数据确认 SHUAIAPI/gpt-6.1-sol；没有配置时显式错误，不做假接通。

证据伴随文件：`thread-panels-implementation-contract-2026-10-07.evidence.json`。本次核对只涉及文档/JSON/源证据及格式，不构成编译、测试或运行时服务验证。
