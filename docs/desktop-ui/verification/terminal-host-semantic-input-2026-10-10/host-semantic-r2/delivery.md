# typed 终端语义输入交付

日期：2026-10-10。model：SHUAIAPI/gpt-6.1-sol；provider：opencodex。

本批作者工作完成，全部 12 文件释放，源码写入已停止。未运行编译、build/test、native、PTY/App 或截图；不能将静态检查称为测试通过。

## 实现

- SessionTerminalHostService 与 core/client SessionApi 增加 typed 默认 Unavailable；DefaultSessionApi 和 LocalConnection 委派新 inputTerminal。
- JvmSessionTerminalHost 在首次挂起前复制输入与 modifiers，复用现有完整身份验证及单次 mutate/native dispatch。
- Gateway 新增可序列化 SessionTerminalInputRequest 和 session.terminal.input endpoint，复用 registration/disposal、单次 mutate 与 full-key snapshot。
- shared DTO 独立于 core/platform；三种输入、31 keys、4 modifiers 使用双向显式 when；Local 在排队前构造独立 core request。
- 固定新增 18 个方法覆盖转发结果、四轴身份、同 SessionId 对象替换、注册替换释放、集合捕获、取消、丢 ack 和真实 native fence。
- 真实 fixture 通过生产 registrar/DefaultSessionApi/Host/PTY，采用独立 literal expected bytes、parser 唯一 marker 与 dd/od；finally 使用 NonCancellable、有界 owned cleanup，核对真实 PID/descendants/Thread 对象。

## 结果与取消边界

外围 NonCancellable 的 Unknown 可仅表示 ack 丢失；不声称 native fence。真实 afterNativeWrite 异常才验证 native Unknown 及 semantic/raw/resize fence，known Rejected 保持 Open。

预派发取消保持已有各层语义：Host typed Rejected，Gateway/Local CancellationException；断言零 dispatch。派发后取消用独立 observer 捕获实际返回结果。外层丢 ack 用例断言 native 仍 Open 并继续合法输入；真实写后失败用例核对一份字节、零 retry 和后续 semantic/raw/resize 拒绝。

## 静态核验

- 合同 SHA256：70fdea79c3837932fd9e81dcdbc8cbe7d8ff02a77fb06ca3d3296da8ec05be98
- 基线 SHA256：c6b101b782a9b7afa9c5d154d7248203306250578a27db214c7ecbbb130766f6
- 完整基线 2,233 输入全部核验：仅允许的 7 个既有写集输入改变；写集外 2,226 输入零变化；新增恰好 5 个文件，总计 2,238 输入。
- 340 个旧测试文件、50 个基线 YAML、写集外已验收 standalone encoder/worker 字节均未变。
- 7 个 before SHA 与精确基线及保存的源副本一致；5 个新增文件 before 为 null；12 个 after SHA 见下表。
- 固定 3+4+5+6=18 方法名称与合同一致，全部显式 runBlocking<Unit>，未增加 skip。
- 双向 key 映射各 31 个、modifier 映射各 4 个显式 when 分支；无 name/valueOf/ordinal 替代；独立 shared DTO 去除 package 差异后与已验收 core schema 源码一致。
- 限定 12 文件 git diff --check 返回 0；另对全部写集文件（包含 untracked 新文件）检查行尾空白和最终换行，零问题。
- 输入扫描遵从基线 Kotlin/YAML 范围，不纳入 docs 或 backend/ui 的既有前端配置；额外只读核对 3 个前端 YAML 与 git index 一致。父线程 docs 验收脚本未修改。

## 12 文件 SHA256

| repo-relative path | before_sha256 | after_sha256 |
| --- | --- | --- |
| modules/session-api/src/im/hikaru/harness/session/api/SessionTerminalHost.kt | 9593f27365a763d58078d5e3db2cf04a48bb527c0b5ba3b0a834a9f636477e18 | 39d3c01ea3413ece2fe07c062bbb2d432fff2f37365f79b30559d737b8d155c1 |
| modules/session-api/src/im/hikaru/harness/session/api/SessionApi.kt | 20e7ebad2cff9d4b2d6018c03248a134db4ab898e2c57af25a9566b37b6e479d | 964c63cc2f1a36a9477f8b1ce0f4323827f1483bbdb62406295969a6cb58306d |
| modules/exec-jvm/src/im/hikaru/harness/exec/jvm/JvmSessionTerminalHost.kt | 8d12bb4671897dc411783e23d31ebce82428f83b219dfa138d85f272d6dd9554 | e1f5729a71eda51257e63a2a96f18419de5d8986a6f915f30d29f778f7b2d887 |
| modules/api/gateway/src/im/hikaru/harness/api/gateway/session/SessionTerminalEndpoints.kt | d5115f8e67798927c9970623708cc8f992eaef662c306d5a9b2a33cb05cedab0 | f2fc8cf6a0a1c889d8586f69d7cb66ef37f53e963285ef6ea24b768aa2f6f9df |
| apps/shared/src/im/hikaru/harness/client/connection/TerminalInput.kt | null | 6dceec11431c73b185b8d4af0b59ea4a322aa3cdad69baf914b59fd996be44db |
| apps/shared/src/im/hikaru/harness/client/connection/Connection.kt | 8052f7f1750b98d1cfa8d48b4727bdd27e2761b7314120d5743cea4fb5933957 | 600e249e724fcbacc2d2ed9b8d338e7f32e8eb9666851c5e5b90be3d03f4ec7c |
| apps/jvm-app/src/im/hikaru/harness/desktop/connection/LocalSessionTerminals.kt | 771bd678d62f0c60f45293eb22aa8cb2eda50476a55109c045c50dea79256cba | 11025fce0795610f0ccca6e16223af733acc64d6f1d5d68aed2c3581dd283202 |
| apps/jvm-app/src/im/hikaru/harness/desktop/connection/LocalConnection.kt | 625d7e49887e23d0d768797ac0b5e80a3412c6290e66e8cd9075c692d874c654 | b126e89a29d75a691f26a8efd5033fda2971cb0dbdf6713e8bf812a6ffca4753 |
| modules/session-api/test/im/hikaru/harness/session/api/SessionTerminalSemanticHostContractTest.kt | null | bfdc6f9788ae30e7c3f1f3b4a4026fec55114c7b8e6a22d0b31c2eb7650be821 |
| modules/exec-jvm/test/im/hikaru/harness/exec/jvm/JvmSessionTerminalSemanticInputTest.kt | null | c5ad5150322a92b18985e8d2d9695065019bdd2b63a26b9bcf72953dda57e6d4 |
| modules/api/gateway/test/im/hikaru/harness/api/gateway/session/SessionTerminalSemanticInputGatewayTest.kt | null | e24525a092e048b22252901b7068d42dbcf9c43af42e66ab2fd3c5bb266986c8 |
| apps/jvm-app/test/im/hikaru/harness/desktop/connection/SessionTerminalSemanticInputConnectionTest.kt | null | 485186426b87804dbf9381aaf05c9feb1097aeeb8462eb8cf84f014156bd3470 |

## 固定新增方法（未执行）

### SessionTerminalSemanticHostContractTest（3）

- legacyHostAndSessionApiSemanticInputAreTypedUnavailable
- defaultSessionApiForwardsSemanticInputExactlyOnce
- semanticEndpointPayloadAndCapturedModifiersRetainIdentity

### JvmSessionTerminalSemanticInputTest（4）

- realHostSemanticInputUsesParserModesAndExactByteCounts
- staleOwnerAndSessionReplacementRejectSemanticDispatch
- hostSemanticCancellationAndNativeFailureHaveDistinctFences
- hostCapturesModifiersAndDisposesOwnedPtyWorkers

### SessionTerminalSemanticInputGatewayTest（5）

- forwardsSemanticVariantsAndEveryResultExactlyOnce
- missingLegacyAndDisposedInputEndpointAreUnavailable
- foreignAcknowledgementsUseRequestedFullIdentity
- serviceReplacementAndLostAcknowledgementRemainUnknown
- semanticCancellationAndModifierCaptureHaveNoRetry

### SessionTerminalSemanticInputConnectionTest（6）

- portableSemanticDtosMapBothWaysWithoutPlatformObjects
- localSemanticInputOwnsRealPtyEndToEnd
- localSemanticRejectsStaleFullIdentityBeforeWrite
- localSemanticNativeFailureFencesWithoutRetry
- localSemanticCancellationAndLostAcknowledgementRemainUnknown
- missingEndpointAndRemoteSemanticDefaultsRemainUnavailable

## 父线程未验收点

- r1 父线程 Gateway 验收 5 项，errors=2；本轮 r2 未编译或运行，Kotlin 类型及运行结果由父线程重新串行验收。
- 父线程负责串行执行固定 17 类 104 项（已验收旧 86 + 本批新 18）、正式 runtime 图、真实 native/PTY 与 owned cleanup 验证以及完整 JVM app build，并归档 XML、日志和源码 manifest。
- renderer、页面键盘/IME/粘贴、Remote 实际终端能力、像素和原生窗口属于独立后续验收范围。

## 交接状态

builds_run=false；tests_run=false；screenshots_run=false；native_execution_run=false；write_set_released=true；source_writes_stopped=true。

交付 JSON：/tmp/harness-terminal-host-semantic-input-delivery-20261010.json。本次交付后退出；后续窄修等待新 prompt。

## r2 测试夹具窄修（2026-10-10）

唯一源码写集：`modules/api/gateway/test/im/hikaru/harness/api/gateway/session/SessionTerminalSemanticInputGatewayTest.kt`。r1 父线程 XML 为 5 tests / 2 errors / 0 failures / 0 skipped；本轮没有运行编译、测试、PTY、native 或截图。

- serviceReplacementAndLostAcknowledgementRemainUnknown 使用真实 runtime.replace(SessionApiKey, replacementApi)，确认 pending 已进入旧 service；替换后仍断言请求 full-key Unknown、无 private 泄漏、旧 service 一次调用、replacement 零重试；随后独立新请求仅调用 replacement 一次。
- missingLegacyAndDisposedInputEndpointAreUnavailable 在活跃注册上精确断言 IllegalStateException 及 session.list 同名错误；重复注册先在首个 endpoint 的写入/effect 前失败。失败后完整方法集合不变，原 endpoint 可正常单次派发，replacement 零调用。
- 保留两种 dispose 路径：先释放旧 handle 或旧 owner，再合法注册 replacement；重复释放旧 handle/owner 后全部 replacement endpoint 保留，input 只进入 replacement，新 handle 删除全部 endpoint。注册、释放及每次输入派发后核对 calls，rawCalls 为零。
- 固定五个 Gateway 方法和所有原断言保持；其余三个方法及 helpers 字节未变。所有 suspend 测试仍显式 runBlocking<Unit>，owned cleanup 为 NonCancellable 且有界 10 秒。外围 Unknown 仅证明确认丢失，不声称 native fence。

### r2 静态核验

- 窄修基线 SHA256：`206f462e622a0e8aa11c1f6e0c774d94a4fe25ccdc1b394c2ffc3e3256340302`。当前完整输入集合与窄修基线一致，共 2,238；唯一文件改变，其余 2,237 字节未变。
- 340 个旧测试、50 个 YAML 均对原 baseline 核验未变；原合同、原 baseline、窄修 baseline、delivery-r1 JSON/MD 保持 SHA256。
- 原 12 文件路径与 before_sha256 完整保留；after_sha256 已按当前文件重新核对。Gateway before（r2 基线）：`1351b6745984b72a7a080a2ff2a98147ac4dfdbfde2ff6a0347f92910f90fd16`；after：`e24525a092e048b22252901b7068d42dbcf9c43af42e66ab2fd3c5bb266986c8`。
- 固定 3+4+5+6=18 方法及显式 runBlocking<Unit> 保留；Gateway 仅两个指定方法体变化，其余方法与 helpers 保持。原 46 条断言行全部保留，当前 105 条；零 skip，限定 git diff --check=0，空白/最终换行检查无问题。
- CodeGraph 已确认真实注册顺序、逐 endpoint 写入/owner effect 顺序、registration 身份保护、runtime.replace 及 Gateway 单次 mutate 的确认丢失边界。上述为静态检查，不能视为运行通过。

源码写入已停止，唯一写集已释放；父线程负责全部后续编译/测试/PTY/native。完成说明：`/tmp/harness-terminal-host-semantic-input-repair-r2-result-20261010.md`。
