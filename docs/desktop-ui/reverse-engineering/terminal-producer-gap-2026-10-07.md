# 终端生产者最小闭环与缺口（2026-10-07）

> 当前状态（2026-10-08）：下文为实施前分析，保留历史证据。`modules/exec-jvm` 已提供真实 ProcessBuilder producer、输出 revision/退出/截断、超时与 Host dispose；DesktopBundle 默认安装 `exec-process`，typed Gateway/Local/Relay/panel 链已接通。默认命令关闭 stdin，显式 `background=true, stdin=true` 可通过真实管道接收输入、EOF和取消；本批 producer16、shared58、gateway12、desktop28，共114/114通过。正文仍是 plain text，不含 PTY/resize/ANSI renderer、重启 attach 或 Stop all；真实进程选择已完成，批次106/106通过（shared71、desktop30、截图5），108PNG仅作为面板viewport证据。结果与精确命令见 `terminal-input-and-selection-2026-10-08.md`，旧 producer 缺口不再作为待实施任务。

## 1. 结论与范围

当前 Harness 没有生产 shell/exec tool handler，也没有按会话登记进程、保存持续输出的 producer/registry。打开 BackgroundOutputPanel 仍应明确 `TerminalServiceMissing`。通用 ToolsService、tool/call 与 tool/result 事件不能提供后台进程能力。

可以复用工具注册、Host 生命周期、typed gateway、LocalConnection、现有 panel DTO/reducer/正文；必须新增真正创建进程并收集输出的 JVM producer。只补 read adapter 不能形成闭环。第一批建议只支持 verified Local Host、只读完整快照和用户刷新；不提供 PTY、stdin、resize、kill、Stop all、云端或 Relay 执行。面板打开/关闭都不启动命令。

本次仅静态调查并新增本文；未启动服务、进程、GUI，未运行构建/测试。复用 `terminal-subagents-implementation-contract-2026-10-07.md` 的后台输出及 owner 规则，没有重新扫描 ASAR。该旧契约中的“所有会话端点尚缺 children”等描述已被当前 descendants 实现超越，本文以当前磁盘源码为准。

## 2. 当前源码证据

以下路径均相对 `/Volumes/Data/IdeaProjects/kmp-harness`，先经 CodeGraph 定位/读取，再做针对性源码搜索。未发现的结论限定仓库当前生产 `apps/`、`modules/`，不把外部 Codex 工具当作 Harness 服务。

| 路径与符号 | 当前真实行为 | 对后台输出的意义 |
| --- | --- | --- |
| `modules/tools/src/im/hikaru/harness/tools/Tools.kt:18` `ToolCallRequest` | callId/name/arguments；`scope: String?` | scope 是调用关联字符串，不能单独证明 Session/Host 授权 |
| 同文件 `ToolExecutionResult:30`、`ToolHandler:35`、`ToolsService:39` | 结果仅 content/isError；注册 schema+handler | 没有 process ID、stdout stream、exit、后台句柄 |
| 同文件 `ToolSessionEvents:67` | `tool/call`、`tool/result` | 记录调用与 handler 返回；不表示 OS 进程创建/退出 |
| 同文件 `ToolsPlugin.apply:86`、`DefaultToolsService:97` | 提供空 entries registry；register 后才有工具；execute 维护 inFlight/completed 并等待 handler | 并发许可/inFlight 是工具生命周期，不是进程管理 |
| `modules/tools/src/im/hikaru/harness/tools/ToolsPluginDefinition.kt:15` | 仅配置 maxConcurrentCalls 的 ToolsPluginDefinition | 配置安装 tools 不会自动注册 shell/exec |
| `modules/bundle/desktop/src/im/hikaru/harness/bundle/desktop/DesktopBundle.kt:43` `desktopPluginCatalog`、`DesktopProfileBundle:66` | catalog/默认 entries 包含 tools、agent-loop、session-api；没有 shell/exec producer definition | 默认桌面拥有工具框架，缺生产命令实现 |
| `modules/agent-loop/src/im/hikaru/harness/agent/loop/AgentLoopAgent.kt:526` `executeTools` | 校验 schemas；553 行把 session.id.value 填入 request.scope；先 append Call，等待 execute，再 append Result 和工具消息 | session 是可复用关联来源；Result 即使成功也不证明后台命令已结束 |
| `modules/session/src/im/hikaru/harness/session/SessionRuntimeEvents.kt:15` `SessionEvents` | Appended/Flush/Disposed 等会话事件 | 可作失效通知或生命周期入口，当前不是 process output 事件 |
| `modules/session-persistence/src/im/hikaru/harness/session/persistence/RoomSessionPersistence.kt` `SessionPersistence`、`validateSnapshot` | 存储/验证会话及 tool call/result 日志 | 不保存可重新 attach 的 OS process，不可从重放日志制造 Running |
| `apps/jvm-app/src/im/hikaru/harness/desktop/git/DesktopGitBackend.kt:120` `DesktopGitCommandRunner` | 128 行 ProcessBuilder 启动真实 git；关闭 stdin；并行 drain 两个 pipe；等待退出后返回；超时/取消清理所属进程 | 真实短命进程，但无 Session/call 归属、输出 registry、增量快照或后台 handle；不可接成会话终端 |
| `apps/shared/src@jvm/im/hikaru/harness/client/account/DesktopAppSessionStore.kt:74` `SecretToolBlobStore` | Linux 分支启动 secret-tool 读写凭据；macOS 使用 Keychain native API | 也是 ProcessBuilder，但属于敏感账号存储；绝不能导入 terminal buffer |
| `modules/boot/src/im/hikaru/harness/boot/HarnessHost.kt:74` `start` | 81–82 行注册 host/session gateway；catalog+loader 启动；close 释放 loader/context | producer 可静态安装并跟随 Host 生命周期释放，不跟随面板生命周期 |
| `modules/api/gateway/src/im/hikaru/harness/api/gateway/session/SessionApiGateway.kt:24` 等 endpoint、`registerSessionApi:61` | 当前有 list/create/history/descendants/child.history/prompt/cancel | 没有 terminal/process endpoint；需正式新增消费者链路 |
| `apps/jvm-app/src/im/hikaru/harness/desktop/connection/LocalConnection.kt:76` session adapter；`apps/shared/src/im/hikaru/harness/client/connection/Connection.kt:158` SessionApi | 当前 descendants/history 等 typed 操作 | 没有 process read 方法 |
| `apps/jvm-app/src/im/hikaru/harness/desktop/connection/HostRelayConnection.kt:84` `invoke` | 对已列方法分派，150 行其他方法返回 404 | Relay 不会自动代理新进程接口；首批继续 unavailable |
| `apps/shared/src/im/hikaru/harness/client/panels/ThreadPanelModels.kt:38` `BackgroundTerminalKey`、`:43` `BackgroundTerminalOutput` | scope+processId；完整 buffer/revision/command/truncated | 已可接真实单进程输出；缺进程列表、生命周期和“没有进程”状态 |
| `apps/shared/src/im/hikaru/harness/client/panels/ThreadPanels.kt` `BackgroundOutputPanel` | 只读 plain text/SelectionContainer/滚动；空真实 buffer 显示 No output yet | 不是 ANSI/PTY emulator；不创建任何 shell |
| `apps/shared/src/im/hikaru/harness/client/panels/ThreadPanelsState.kt` `ReceiveTerminal` | generation、key.scope、同 key revision 去旧；close 增代 | 可复用但 Loading 重置会丢旧 Ready revision，controller 需额外记 last revision |

生产调用点搜索：`ToolHandler` / `ToolsService.register` 只有通用定义及服务内部实现；实际 echo handler 注册出现在 `modules/tools/test/` 和 `modules/agent-loop/test/`。生产 ProcessBuilder 命中 Git 与 Linux 凭据辅助进程，未找到 shell/exec 注册。构建脚本、测试 fixture、LLM stream 的 terminal finish、Compose shell 容器均不是会话后台命令。此结论是当前静态实现能力，未声称已检查运行中 Host。

## 3. 必须区分的四种事实

| 事实 | 权威来源 | UI 可表达 |
| --- | --- | --- |
| 通用工具正在执行 | ToolsService.inFlight / Call 尚无 Result | 仅工具调用处理中；不能标为已创建命令进程 |
| 真实命令仍在运行 | producer 持有成功 start 的 Process、实时输出及未完成退出记录 | 运行中命令输出；允许真实空 buffer |
| 后台 handle 已返回，命令继续运行 | producer Host Job 保持存活，tool 返回 processId 后仍收集输出 | detached/background；当前不存在，需专门实现 |
| 已返回的通用工具结果 | ToolResultEvent / ToolExecutionResult | 会话工具结果；无 producer 关联时不得新建 terminal key 或猜 exit |

`ToolExecutionResult.isError=false` 不是 exitCode=0；Result 表示 handler 返回，不一定是进程结束。Agent.awaitIdle/session.cancel 也不是后台进程退出/终止协议。completed 工具消息文本不许拼成假 buffer。

## 4. 最小生产方案（以下全部为新增提案）

### 4.1 Producer：先让输出有真实来源

新增 JVM 模块 `modules/exec-jvm/`，静态 plugin `ExecProcessPluginDefinition`（建议配置名 `exec-process`），依赖 runtime/loader/tools/session/session-api。由 DesktopBundle 注册/安装，注入 ToolsKey、SessionKey 和 Host 描述，在 plugin apply 中 `ToolsService.register` 一个明确命名的 `exec` schema+handler，返回 Disposable。不动态载入插件代码。

- typed 内部入口建议 `ExecProcessProducer.start(owner: ExecProcessOwner, request: ExecCommand): ExecProcessHandle`；owner 包含 server HostId、经当前 Context SessionStore 验证的 SessionId、CallId 和 producer incarnation。command 的 argv/cwd/timeout 从正式 schema 解析。
- 优先直接 argv 的 exec；若明确提供 shell command，必须由正式 shell handler 使用平台 executable/escaping。原版 shell 语义不能从工具名推定。cwd 来自真实会话 options/允许的路径，不默认取 UI 所在主机目录；未验证 scope/cwd 时不 spawn。
- `ToolCallRequest.scope` 由现有 AgentLoop 设置，但 producer 仍须解析并验证 session 属于安装它的 Host/SessionStore。缺失/未知 session 拒绝；processId 仅服务生成，关联 session+call，不能用任意 OS pid attach。初批只读 exact session，子会话输出若另行开放需验证祖先链。
- 成功 ProcessBuilder.start 后登记稳定 processId（producer incarnation+唯一序号/UUID），真实 pid 可作可选诊断字段。start 失败返回真实执行错误，不能出现 Running 记录。进程退出和 pipe EOF 完成后才发布 Exited(exitCode)；不能在工具返回时自动 Done。
- Host/plugin Job 负责 drain stdout/stderr、增量 UTF-8 decoder、有限 buffer 和超时/清理；输出版本由 producer 在 buffer/exit 变更时单调增加，不由 UI 的刷新次数代替。建议 merged pipe，保留实际捕获顺序；若双 pipe 则序号描述采集顺序，不声称全局 OS 写入顺序。
- 第一垂直切片可让 exec handler 等待进程结束，同时 panel 从 registry 读取 in-flight 命令；这提供真实正在运行的命令输出，但不是 detached 后台执行。若本批要求真正后台命令，schema 必须显式增加 background mode：成功登记后返回实际 handle，Host Job 接管进程/pipe，tool permit 可释放。两种模式共用 registry，返回 handle 后的 tool/result 不标记进程完成。
- buffer 截断仍持续 drain，避免 pipe 阻塞；revision 在截断后也推进。关闭面板只取消读取；Host/plugin dispose 则清理其拥有的进程/Job/reader。plugin 重装产生新 incarnation，旧 processId 不复用。不 attach 系统无关进程。
- 不持久恢复进程：Host 重启清 registry，旧命令不可重新标 Running。退出记录采用明确有限 TTL/数量 retention；expiry 返回 NotFound，成功空列表仍是服务正常的空结果。

### 4.2 只读契约：先 full snapshot，后订阅

为缩小模块改动，将 provider-neutral DTO/读 service 放在现有 `modules/session-api/src/im/hikaru/harness/session/api/SessionBackgroundProcesses.kt`，JVM producer 实现读投影，gateway 查可选 `BackgroundProcessesKey`。client 在 connection 同名新文件定义 typed DTO，按现有 descendants 模式明确映射，不使用 Map<String, Any> 或反射。建议稳定接口：

```kotlin
// 新增到 core/client SessionApi；默认返回 Unavailable，兼容旧实现。
suspend fun backgroundProcesses(parent: SessionId): BackgroundProcessesResult
suspend fun readBackgroundOutput(
    parent: SessionId, process: BackgroundProcessId,
): BackgroundOutputResult

// 新增读服务，由 Host producer 提供；具体 serializable DTO 使用各层现有 SessionId。
interface BackgroundProcessesService {
    suspend fun list(parent: SessionId): BackgroundProcessesResult
    suspend fun read(parent: SessionId, process: BackgroundProcessId): BackgroundOutputResult
}
```

建议结果和字段：

- `BackgroundProcessesResult.Unavailable(parent, reason)` / `Available(parent, processes)`；Available(emptyList) 仅安装并成功读取 registry 后可返回。条目包含 processId、ownerSession、callId、command display、cwd、startedAtEpochMillis、真实 lifecycle。HostId 在 Host绑定/读取边界校验。
- lifecycle 为 Running、Exited(exitCode)、Unknown；不存在权威状态时保留 Unknown。spawn failure 是执行错误，不造 Running。
- `BackgroundOutputResult.Unavailable(parent, process, reason)` / `NotFound(parent, process)` / `Available(parent, process, ownerSession, buffer, revision, truncated, lifecycle)`。Unavailable 与 NotFound、调用失败分开。完整快照直接恢复丢失更新，初批无 cursor/append wire，无订阅能力声明。
- endpoint 提案：`SessionBackgroundProcessesEndpoint` → `session.background-processes.list`；`SessionBackgroundOutputEndpoint` → `session.background-processes.read`，请求为 typed parent/parent+process。这些名字目前不存在，需 gateway 注册与 LocalConnection 映射完成才可用。
- 所有 read 再次验证 parent 存在、process 归属 exact session、Host/incarnation；客户端传 id 不授予 attach 权限。未知/外部 session 不返回 buffer；scope mismatch/读取失败不能转成成功空列表。Relay 初批不新增路由；其默认 SessionApi 返回 Unavailable，绝不执行本机 producer。

### 4.3 接到现有 BackgroundOutputPanel

为 ThreadPanelsRepository/controller 新增独立 terminal 读流程（建议 `refreshTerminal(): Job?`），按完整 ThreadPanelsOwner 的 connection 引用身份、host identity/HostId/generation、parentSession 及 request generation fencing。保持 bind 空 tabs、打开输出面板只 list/read；切换 Host/断线/close/reopen 使迟到返回无效，不影响子 Agent overview/detail Job。

已选真实 processId 调用 read；初次无选择可确定性选择最新真实 Running，若无 Running 则选最新保留 Exited，仅展示服务已有数据。切换过程 id 增代；同 key 的 last revision 在 controller 保留，Loading 不清它；旧版本不能替换新 buffer。映射 Available 为现有 `BackgroundTerminalOutput(key, buffer, revision, command, truncated)`。现有 DTO 不表达 lifecycle，第一批若展示状态则单独加 typed metadata，不能把 exit 状态伪造到输出正文。

缺少 producer → TerminalServiceMissing；安装正常但没有进程 → 新增明确的 terminal Empty 状态与正文（现有 PanelLoad 尚无 Empty，不能用虚构 processId+空 buffer 表达）；真实进程无输出 → Ready(buffer=empty) 的 No output yet；NotFound → 提示已过期/重读列表；读取失败 → Error+真实 retry。新增可选 `onRefreshTerminal` 回调接 refreshTerminal，初批手动刷新，不假装已支持实时订阅。多 process picker 可后续接真实列表；不增加没有消费者的 stdin/stop 控件。

## 5. 后续写集与依赖交接

| owner/批次 | 拟写集 | 依赖/交付 |
| --- | --- | --- |
| producer | 新 `modules/exec-jvm/module.yaml`、`src/im/hikaru/harness/exec/jvm/` 和对应 JVM tests | 新模块依赖 runtime/loader/tools/session/session-api；仅 java.lang.ProcessBuilder + 现有 coroutines/serialization，无 PTY/native library |
| core typed 读契约 | `modules/session-api/.../SessionBackgroundProcesses.kt`、`SessionApi.kt`、相关 tests | DTO+BackgroundProcessesKey/Service；DefaultSessionApi 委托可选 service；无 producer默认 unavailable |
| Host/gateway | `modules/api/gateway/.../session/SessionBackgroundProcessesGateway.kt`、`SessionApiGateway.kt`；`modules/bundle/desktop/module.yaml`、`DesktopBundle.kt` | gateway 可复用既有 registerSessionApi root 生命周期；Desktop bundle 依赖 exec-jvm 并安装 definition/entry；不让 boot 依赖 JVM 模块 |
| transport | `apps/shared/.../connection/Connection.kt`、新 typed DTO文件；`apps/jvm-app/.../connection/LocalConnection.kt`、映射文件、tests | 新默认方法兼容 RemoteConnection；首批 Relay unavailable，不改 HostRelayConnection |
| panels | `apps/shared/.../panels/` 与对应 tests | terminal 独立 Job/request/revision、Empty、刷新及真实 metadata；复用 BackgroundOutputPanel |
| 父线程 | `apps/shared/.../app/HarnessApp.kt`；必要配置/注册由 Host owner负责 | verified Local owner 绑定和刷新；不在 main/打开面板时自行创建 shell |

依赖顺序：DTO/service → producer+注册 → gateway/Local typed consumer → panels/父接线。仅注册 DTO/gateway 或仅安装 ToolsPlugin 都不足以宣布终端可用。exec 是新增执行能力，需独立实施批次明确 schema、可执行程序、cwd、超时、后台模式与取消所有权；本次没有实现或默认启动它。现有 Git runner 保持原用途，不作为终端生产者复用。

## 6. 下批验收门槛

- 真实 JVM 测试启动明确的短命测试命令，验证启动后 Running、退出码、实际输出；fixtures 只在 tests。命令 start 失败不发布 Running。
- producer 缺失/空 registry/真实空 stdout/异常/过期分别呈现；成功工具 Result 与后台 Process 生命周期独立。
- 大量输出持续 drain、buffer限额/truncated、UTF-8跨块、非零退出、超时、取消及 Host dispose 有真实验证；不凭 sleep 或文本猜完成。
- 双 Host/错误 parent/外部 processId/旧 incarnation 均拒；切连接/identity/代际/断线及面板关闭后旧回复不能发布。
- panel open/close/refresh 不增加进程数量；UI无 stdin/kill/cleanAll 无消费者按钮；Relay 不触发当前机器 exec。
- 完整 snapshot 和真实 producer revision 可去旧、恢复输出；初批刷新不会声称实时、PTY/ANSI或重启 attach 能力。

本文交付后释放该单文件写集。所有产品源码保持本次调查前状态。
