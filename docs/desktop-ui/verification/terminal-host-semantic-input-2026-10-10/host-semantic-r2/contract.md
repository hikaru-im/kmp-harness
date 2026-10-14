# 终端语义输入：Host、Gateway 与本地连接合同

日期：2026-10-10。实施指定 `SHUAIAPI/gpt-6.1-sol`，provider `opencodex`。父线程先分析、保存基线，再派发；工作者只实施与静态检查，交付并退出后父线程冻结及串行验收。

## 已分析的真实链路

`LocalConnection.session → LocalSessionTerminals → ApiGateway → DefaultSessionApi → JvmSessionTerminalHost → JvmPtyTerminalSessions.input`。本批补同一链的 typed Text/Paste/KeyPress，复用已有权限、完整身份和单次 mutation 路径。2026-10-09 standalone 语义输入六文件及全部旧测试保持已验收字节。

核心证据是当前 `SessionApi.kt` 的接口默认值及 DefaultSessionApi、`JvmSessionTerminalHost.kt` 的 mutate、`SessionTerminalEndpoints.kt` 的注册/mutate/snapshot、`LocalSessionTerminals.kt` 的 mutateKey/mutate，和 `LocalConnection.kt` 委派。代码已由 CodeGraph 阅读；全部源码/YAML 由父线程基线 SHA 固定。

## 唯一写集（12 文件）

1. 修改 `modules/session-api/src/im/hikaru/harness/session/api/SessionTerminalHost.kt`。
2. 修改 `modules/session-api/src/im/hikaru/harness/session/api/SessionApi.kt`。
3. 修改 `modules/exec-jvm/src/im/hikaru/harness/exec/jvm/JvmSessionTerminalHost.kt`。
4. 修改 `modules/api/gateway/src/im/hikaru/harness/api/gateway/session/SessionTerminalEndpoints.kt`。
5. 新增 `apps/shared/src/im/hikaru/harness/client/connection/TerminalInput.kt`。
6. 修改 `apps/shared/src/im/hikaru/harness/client/connection/Connection.kt`。
7. 修改 `apps/jvm-app/src/im/hikaru/harness/desktop/connection/LocalSessionTerminals.kt`。
8. 修改 `apps/jvm-app/src/im/hikaru/harness/desktop/connection/LocalConnection.kt`。
9. 新增 `modules/session-api/test/im/hikaru/harness/session/api/SessionTerminalSemanticHostContractTest.kt`。
10. 新增 `modules/exec-jvm/test/im/hikaru/harness/exec/jvm/JvmSessionTerminalSemanticInputTest.kt`。
11. 新增 `modules/api/gateway/test/im/hikaru/harness/api/gateway/session/SessionTerminalSemanticInputGatewayTest.kt`。
12. 新增 `apps/jvm-app/test/im/hikaru/harness/desktop/connection/SessionTerminalSemanticInputConnectionTest.kt`。

只改此写集；不改 YAML、旧 raw write、旧测试、standalone encoder/worker、页面或 Remote/Relay。保留 dirty/untracked，不 reset/stash/clean/checkout/commit，不处理既有用户进程和配置。

## 生产接线

- `SessionTerminalHostService` 增加 `suspend fun input(key: TerminalKey, input: TerminalInput): TerminalMutationResult`，默认请求 key 的 typed Unavailable，保持旧实现源兼容。
- core `SessionApi` 增加 `inputTerminal(key, input)` 默认 Unavailable。同文件 DefaultSessionApi 委派 `SessionTerminalHostKey.input`，缺服务返回完整请求 key 的 Unavailable。
- JVM Host 的 input 在第一次 suspension 前复制 KeyPress modifiers，随后复用 existing mutate：exact live Session 对象、root scope、incarnation 和 terminal 全部验证，不创建/发现额外进程。调用 underlying service 一次，不增加重试、第二 worker 或旁路锁。
- Gateway 在同一文件新增可序列化 `SessionTerminalInputRequest(key, input)` 和 `SessionTerminalInputEndpoint`，method 固定 `session.terminal.input`。纳入 `registerSessionTerminals` 已有 registrations/disposal；handler 第一次 suspension 前捕获独立 KeyPress，复用 existing mutate 与 full-key snapshot 验证。
- shared 新增独立 portable `TerminalInput`、`TerminalKeyName`、`TerminalModifier`，schema 与 core 完全一致：Text/Paste/KeyPress、text/paste/key_press、现有全部31键/4 modifier；Character 限单个 printable ASCII，其他键 character=null。KeyPress 构造与序列化按已验收 core 实现捕获集合，显式 equals/hashCode；不 typealias/import core、JediTerm、AWT、native types。
- shared `SessionApi` 增加同名 `inputTerminal` 默认 typed Unavailable。RemoteConnection/Relay 保持默认能力。
- LocalSessionTerminals 增加 inputTerminal；同步将 client DTO 映射并捕获至 core request，复用 mutateKey。新增 client/core 三分支、全部 key/modifier 双向显式 when 映射，不用 name/valueOf/ordinal 代替契约。LocalConnection.session 委派该 helper。
- Request/key/input 捕获使用已有 immutable String 和 owner/value DTO；所有可变 modifier 来源必须在排队/挂起前隔离。新增映射可 internal；保持边界上的类型独立。

## 结果、取消与确认边界

- Applied.bytes 是 native 实际 write+flush 数量；Rejected/NotFound/Unavailable/UnknownOutcome 保留完整请求 key。转发三种输入和每种结果，snapshot 验证 scope/session/incarnation/terminal；任何 foreign response 变请求 key 的 Unknown，不暴露 private exception，不重试。
- 外围 Host/Gateway/Local existing mutate 使用 NonCancellable，因此派发后外围调用者取消、服务替换或丢确认可以返回 Unknown，而 underlying native 写入已经完成。此外围 Unknown **不能宣称已设置 native fence**。测试必须区分真实 native afterNativeWrite 异常造成的 fence，与外围仅丢确认的 Unknown。
- standalone native 实际失败的 Unknown 必须贯穿整链；真实 file 字节只一份，后续 semantic/raw/resize 被 native fence。已知编码/最终预算拒绝保持 Open，可继续合法输入。
- 预派发取消保留各现有层语义：Host typed Rejected；Gateway/Local ensureActive 的 CancellationException。均零 underlying semantic dispatch。测试捕获实际返回而非只看 cancelled Deferred 的 await。
- Local 仅精确 NotFound code + `Harness API endpoint 'session.terminal.input' 未注册` 判定 Unavailable，已派发其它异常/失败/无数据归 Unknown。
- endpoint unregister、runtime/owner dispose 必须覆盖新增方法；旧 handle 不能移除替换 registration。

## 固定新测试（3+4+5+6=18）

所有 suspend 方法使用显式 `runBlocking<Unit>`。原有测试零修改、零 skip。可使用受控服务测试转发边界，native 用例必须使用真实临时 Runtime/Session/Agent/ExecProcessPlugin、生产 registrar/DefaultSessionApi 和 PTY。仅 fixture agent/model受控，不能 fake PTY/nativeWrite/transport 或预造 native output。

### SessionTerminalSemanticHostContractTest（3）

1. `legacyHostAndSessionApiSemanticInputAreTypedUnavailable`：旧 Host/SessionApi 实现无新 override，三分支保留完整 key，不调用旧 raw 或其它业务方法。
2. `defaultSessionApiForwardsSemanticInputExactlyOnce`：真实 Runtime/SessionApiPlugin，缺服务及受控 Host服务两种路径，三分支、每种结果只一次且 full-key。
3. `semanticEndpointPayloadAndCapturedModifiersRetainIdentity`：core Text/Paste/全部KeyPress的序列化、请求 key、Unicode，以及构造后修改原 mutable modifier 集合不会更改捕获值。此模块不依赖 Gateway，可序列化本模块 input 和 key，不引入跨模块依赖。

### JvmSessionTerminalSemanticInputTest（4）

1. `realHostSemanticInputUsesParserModesAndExactByteCounts`：真实 DefaultSessionApi/Host → PTY 收 Text Unicode、Paste CRLF且bracketed包裹、KeyPress mode；逐次 parser screen marker后再 input，expected literal bytes及 Applied.bytes，Rejected后Open、合法输入成功。
2. `staleOwnerAndSessionReplacementRejectSemanticDispatch`：scope/session/incarnation/terminal 四轴及同 textual SessionId 的 live对象替换；旧 key 零 nativeWrite，不额外spawn，新 owner仍可输入。
3. `hostSemanticCancellationAndNativeFailureHaveDistinctFences`：预派发取消零写；真实write后外围取消一次写/Unknown且native仍Open；现有 afterNativeWrite seam 真实失败一次写/Unknown且semantic/raw/resize被fence；不得混淆。
4. `hostCapturesModifiersAndDisposesOwnedPtyWorkers`：modifier捕获不受原集合改动；通过模式字节证明 dispatch 内容；root dispose 关闭已拥有进程/descendants/worker，后续 input Unavailable，重复dispose收敛。

### SessionTerminalSemanticInputGatewayTest（5）

1. `forwardsSemanticVariantsAndEveryResultExactlyOnce`：三分支、每个 key/modifier、所有结果及 Applied.bytes，request JSON roundtrip与单次调用，无 old raw调用。
2. `missingLegacyAndDisposedInputEndpointAreUnavailable`：缺服务、legacy默认、registration/owner dispose、新endpoint注册删除及旧handle不移除replacement。
3. `foreignAcknowledgementsUseRequestedFullIdentity`：所有结果 × 四轴 foreign key，仅 requested Unknown，次数=1，无private错误。
4. `serviceReplacementAndLostAcknowledgementRemainUnknown`：受控dispatch后服务替换/异常/丢确认，Unknown、一次输入，合法ack身份不能外推native fence。
5. `semanticCancellationAndModifierCaptureHaveNoRetry`：预派发零调用、派发后取消及modifier集合独立捕获，实际returned Unknown和single dispatch。

### SessionTerminalSemanticInputConnectionTest（6）

1. `portableSemanticDtosMapBothWaysWithoutPlatformObjects`：独立shared schema、全部31 keys/4 modifiers、三分支、双向映射与JSON roundtrip，Character不变量与capture；schema无平台类型。
2. `localSemanticInputOwnsRealPtyEndToEnd`：真实 LocalConnection→生产 registrar→DefaultSessionApi→Host→PTY，Text Unicode、Paste/bracketed/mode keys、精确native bytes与Applied.bytes，合法与known Rejected后可继续。
3. `localSemanticRejectsStaleFullIdentityBeforeWrite`：真实四轴stale/同id替换零副作用；另受控gateway全部结果foreign响应返回requested Unknown、单次调用。
4. `localSemanticNativeFailureFencesWithoutRetry`：真实afterNativeWrite异常从core经gateway至client，file字节只一份，semantic/raw/resize fence，owned cleanup。
5. `localSemanticCancellationAndLostAcknowledgementRemainUnknown`：预派发零write；真实delivery后外围取消/服务丢确认Unknown、一次写、no retry；明确native input可仍Open，捕获input不随mutable来源变化。
6. `missingEndpointAndRemoteSemanticDefaultsRemainUnavailable`：精确missing endpoint typed Unavailable，非精确错误/无data/失败Unknown；Remote/Relay默认无terminal新endpoint网络调用。使用现有受控remote fixture或独立符合Connection契约的remote实现，不能改Remote源码。

真实byte fixture只依赖 `/bin/sh`、`stty raw -echo`、`dd`/`od`。mode待parser唯一screen marker；expected literal bytes，不用本encoder作为oracle。poll/等待有界10秒；finally在NonCancellable清理且核对实际owned进程、descendants、worker Thread对象。不可自动skip缺依赖或平台；不能杀既有用户进程。

## 交付与父线程门槛

完整2,233输入作为基线，预计新增5文件→2,238输入。12文件before/after SHA、合同/基线SHA固定；旧六文件与所有写集外输入字节保持。交付 `/tmp/harness-terminal-host-semantic-input-delivery-20261010.json` 与同名.md；JSON的files路径为repo-relative，model/provider准确，builds_run/tests_run/screenshots_run=false，write_set_released/source_writes_stopped=true。

交付退出后父线程固定17类104项（本批18+已验收86），正式runtime图及完整JVM app build，完整XML/日志/源码manifest存档；不并发构建、不使用动态加载。失败独立attempt，指定模型窄修，不降低断言。

通过表示本机语义输入链完成；renderer、真实页面键盘/IME/粘贴、Remote、原版同状态像素与原生窗口仍独立待验收。整体复刻目标 active，用户必做和排除范围保持。
