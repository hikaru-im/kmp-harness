# 本机 PTY Host、Session API 与 LocalConnection 合同

日期：2026-10-09。实施者固定为现有 `local/01a11a54-1694-7fb3-8007-37c434531314`，模型 `SHUAIAPI/gpt-6.1-sol`，provider `opencodex`。基础正式验收通过后才派发本合同。

## 交付目标

接通真实 `Runtime / Agent / Session → JvmSessionTerminalHost → SessionApi → Gateway → LocalConnection → client DTO`。正式 DesktopBundle 已装入 ExecProcessPlugin，PTY Host 由该 plugin 的 root 生命周期登记和清理。显式 create 才启动真实 PTY；discovery/list/read 不启动 shell。本批交付本机能力，不把它记作 renderer、页面、remote 或原版像素验收。

继续保留 static、typed、cold-start 架构；禁止动态加载。保留全部既有 dirty/untracked。不得 reset/stash/clean/commit，不修改用户配置，不处理既有用户进程，不启动真实用户模型请求。测试只使用自己创建的临时 Runtime、Session、目录、进程。

## 已分析的接口决定

1. 基础 `SessionTerminals.kt` 与 `JvmPtyTerminalSessions.kt` 保持本批验收后的字节。其 `TerminalCreateResult.UnknownOutcome.key` 非空表示基础已预留实际资源。Gateway/Local 丢回包时未必知道 key，因此新增 Host 层 `HostTerminalCreateResult.UnknownOutcome(owner, key: TerminalKey?, reason)`，client 对应 create 结果也允许 nullable key；不得编造 terminal id，不得为丢回包重发 create。
2. 新 common Host service 与 ServiceKey 只暴露 discovery、create、list、read、write、resize、close。discovery 接受真实 SessionId，Available 含完整 owner、canonical creation cwd 和容量；Unavailable/Rejected 保留 requested SessionId，不编造 owner。SessionApi 添加默认 Unavailable 方法，DefaultSessionApi 获取可选 Host service。旧 Connection、测试 doubles 和 remote 自动继承 Unavailable。
3. core/client 保持各自 DTO；client 新文件明确映射所有 owner/key、尺寸、metadata、lifecycle/input、screen/history、run column/span、style/colors/attributes、cursor、alternate、revision、raw tail、truncated 以及每个结果分支。common 不暴露 JediTerm/AWT/Swing/Process/Path。collection 逐层复制。
4. 注册器绑定真实 Agent、Session、root Context、SessionStore、AgentRegistry 引用和 canonical Session cwd。入口与 dispatch 前复核 registry/store 的 exact live membership、agent.context.root 与依赖引用、context 未 dispose，读取发布前再验。仅同文本 id 或 TerminalOwner 值不授予 shell 权限。缺 cwd、非目录、越出 Session root/Host allowedRoots、错 root、旧 incarnation 均在启动前拒绝。canonical path 与基础构造在 IO。
5. root scope 由 Host 实例生成一次；每个实际 Session 绑定的 incarnation 由基础类生成。有限注册器可配置 maxBindings，基础每个 Session 的 capacity 使用既有 Limits。discovery 可建立不含 OS 进程的绑定；满容量返回 typed 拒绝，不无限创建对象。
6. 同文本 Session 被替换后旧绑定失效：旧请求拒绝，已拥有资源进入清理但必须保留注册器所有权直到基础 shutdown 明确 Applied。不能因创建新 binding、关闭 UI 或从 live registry 移除就遗失旧资源。Unknown 仍占容量；root dispose 对所有 active/retired binding 发起有界 shutdown，未确认清理必须报告而非静默清空。
7. mutation 在 dispatch 前可确定拒绝；dispatch 后异常、超时、调用者取消、身份变化或回包身份错误都返回 requested identity 的 Unknown。create 未拿到可信 key 时 key=null，已拿到的 key 必须匹配 owner。单次派发，零自动重试。Gateway/Local 新 helper 不照抄旧 background helper 的 dispatch 后 ensureActive；既有后台接口的取消语义保持现状。避免取消的 dispatcher 返程吞掉已经形成的 Unknown，可使用同 dispatcher 的外层 NonCancellable + IO 内层，并捕获原 caller Job 在前后检查。
8. read/list/discovery 的取消仍传播；缺 endpoint/service 与 dispatch 后失败分别为 Unavailable 和 Unknown。所有分支校验完整 identity。不得把业务返回 NotFound 当作 endpoint 缺失；沿 ApiGateway 实际未注册响应判断，不解析请求内容或泄漏私有 payload。
9. plugin 先 scope.add(Host Disposable)，再 provide service，使部分安装失败也被清理；与现有 producer cleanup 一同属于 root。无第二 main 入口；默认正式本机装配可发现能力，未支持平台 discovery 明确 Unavailable。只有已验 macOS x86_64/amd64 可宣称支持。
10. 自然 exit 保留最终 snapshot，显式 close 在确认 owned process/worker 停止后才移除。UI 关面板不在本批实现；不得附带 close 的副作用。

## 唯一写集（14 文件）

| 文件 | 允许改动 |
| --- | --- |
| `modules/session-api/src/im/hikaru/harness/session/api/SessionTerminalHost.kt` | 新 Host discovery/create DTO、service/key |
| `modules/session-api/src/im/hikaru/harness/session/api/SessionApi.kt` | 新可选 PTY facade/default service forwarding |
| `modules/exec-jvm/src/im/hikaru/harness/exec/jvm/JvmSessionTerminalHost.kt` | 新有限真实 owner 注册器与清理 |
| `modules/exec-jvm/src/im/hikaru/harness/exec/jvm/ExecProcessPlugin.kt` | 正式 root 装配；必要的尾部默认配置参数 |
| `modules/api/gateway/src/im/hikaru/harness/api/gateway/session/SessionTerminalEndpoints.kt` | 新 typed endpoint、request、注册与 identity/Unknown helper |
| `modules/api/gateway/src/im/hikaru/harness/api/gateway/session/SessionApiGateway.kt` | 将新注册 Disposable 纳入现有 root 注册生命周期 |
| `apps/shared/src/im/hikaru/harness/client/connection/SessionTerminals.kt` | 新纯 client DTO，legacy 默认能力保持可编译 |
| `apps/shared/src/im/hikaru/harness/client/connection/Connection.kt` | 新 PTY 默认方法 |
| `apps/jvm-app/src/im/hikaru/harness/desktop/connection/LocalSessionTerminals.kt` | 显式双向映射、新单次调用 helper |
| `apps/jvm-app/src/im/hikaru/harness/desktop/connection/LocalConnection.kt` | 新 facade delegation |
| `modules/session-api/test/im/hikaru/harness/session/api/SessionTerminalHostContractTest.kt` | 4 项新合同测试 |
| `modules/exec-jvm/test/im/hikaru/harness/exec/jvm/JvmSessionTerminalHostTest.kt` | 8 项真实 Host/native 测试 |
| `modules/api/gateway/test/im/hikaru/harness/api/gateway/session/SessionTerminalsGatewayTest.kt` | 6 项新 Gateway 测试 |
| `apps/jvm-app/test/im/hikaru/harness/desktop/connection/SessionTerminalsConnectionTest.kt` | 6 项新 Local/client 测试 |

本批不改 module.yaml、基础五文件、renderer/UI、Relay/remote、main、其它页面或旧测试。若确有编译依赖缺口，先交具体诊断，由父线程明确扩展写集；不能自行改 YAML。只实施/静态检查，禁止 worker build/test/截图/另开工作者。

## 固定测试方法与实际门槛

common 4：

- legacySessionApiReturnsTypedUnavailableWithoutInventingOwner
- hostCreateUnknownRetainsOptionalKeyAndSerializes
- capabilityAndCreateResultsRejectIdentityMismatch
- hostResultsPreserveFullTerminalSnapshotIdentity

Host 8：

- discoveryAndInventoryNeverStartShellAndCapacityIsBounded
- onlyExactLiveSessionCanDiscoverAndStart
- sameTextSessionReplacementRejectsOldOwnerAndRetainsCleanup
- foreignRootAndStaleIncarnationCannotMutate
- realOwnerChainCreateWriteResizeReadClose
- cancelBeforeDispatchStartsNothingAndAfterDispatchIsUnknown
- rootDisposeStopsOwnedProcessesAndWorkersAndFencesNewStarts
- naturalExitRetainsSnapshotUntilExplicitClose

Gateway 6：

- missingServiceAndLegacyDefaultsReturnTypedUnavailable
- forwardsDiscoveryReadsAndEveryMutationExactlyOnce
- mismatchedResponseIdentityBecomesRequestedUnknown
- lostCreateAcknowledgementHasNoInventedKeyAndNoRetry
- cancellationBeforeDispatchAndAfterDispatchRemainDistinct
- registrationAndOwnerDisposalRemoveAllTerminalEndpoints

Local/client 6：

- localProductionRegistrationOwnsRealPtyEndToEnd
- localCreateLostAcknowledgementStartsExactlyOnceAndInventoryFindsIt
- localMutationsRejectWrongResponseIdentityAndNeverRetry
- localMissingEndpointAndRemoteDefaultsRemainUnavailable
- localDtosAndStyledSnapshotRoundTripWithoutNativeObjects
- localCancellationAfterDeliveryPreservesUnknownAndOwnedCleanup

真实链路必须使用 Runtime 安装 SessionPlugin/AgentPlugin/ToolsPlugin/ExecProcessPlugin/SessionApiPlugin、受控无模型 Agent、Gateway.registerSessionApi、LocalConnection。实际创建 PTY、输入 UTF-8、kernel/emulator resize、读 styled snapshot、显式 close；不是直接基础构造替代链路。独立 pid 文件/真实 ProcessHandle 与 worker 状态用于启动次数和 cleanup 证明。故障 gate 只能在真实派发之后丢确认，不替代 native 写入/创建。finally 清理自己创建的资源。断言保留原10秒等合理期限，不用 sleep 隐藏竞争，不 skip native。

父线程释放后冻结全输入，串行验收 11 类 **70 项**：新增4+8+6+6=24，基础/既有5类38，既有 SessionBackgroundMutationsGatewayTest6 与 SessionBackgroundMutationsProcessTest2。要求0 failure/error/skipped，精确方法/数量；runtime 依赖图人工检查 JNA/stdlib 最终版本无重复，完整 JVM app build。任何新增测试/回归未实际运行不得写成通过。

数量更正：父线程在派发后按 CodeGraph 和已验收 R3 冻结源码核对，既有 SessionBackgroundMutationsProcessTest 实际只有2个方法，初版误记为3。门槛因此从71修正为70；没有删除、改名或削弱任何测试。初版合同与baseline保留在baseline目录history/pre-count-correction/。

## 交付报告

写 `/tmp/harness-terminal-host-local-delivery-20261009.json` 和同名 `.md`：model/provider、contract/baseline SHA、14 文件 before/after SHA、固定方法名/数量、设计决定、未解风险；`builds_run=false`、`tests_run=false`、`screenshots_run=false`、`write_set_released=true`、`source_writes_stopped=true`。明确本机链路尚待父线程验收；完成后停止写入。
