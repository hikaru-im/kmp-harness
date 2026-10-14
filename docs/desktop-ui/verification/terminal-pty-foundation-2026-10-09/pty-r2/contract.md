# 终端 PTY / ANSI 基础实施合同

日期：2026-10-09。实现者限定 `SHUAIAPI/gpt-6.1-sol`，provider `opencodex`；复用 local/01a11a54-1694-7fb3-8007-37c434531314。工作者实施及静态检查，释放写集后父线程冻结并串行验证。当前 Comet probe 已是 none，继续任务不重新探针。

## 本批目标与证据

为显式创建的交互终端提供真实 JVM PTY、持续 ANSI 仿真和纯 common 快照。前置探针已经证明固定依赖在当前 Mac OS X/x86_64 上编译、启动 TTY 和调整 kernel 尺寸；不是正式模块验收。既有后台 pipe producer 和 26/26 阅读基线保持。

先读 terminal-dependency-analysis、terminal-pty-ansi-gap、15 份固定 terminal-foundation-evidence、22 份 terminal-native-probe-evidence；理解现有源码先精确路径 CodeGraph。不再全量扫描 app.asar。固定候选 pty4j 0.14.0 + JediTerm core 3.76，repository 使用已验证的 intellij-dependencies YAML。不引入 Swing UI 或手写 ANSI parser。

## 唯一写集

1. `modules/exec-jvm/module.yaml`：仅增加上述两个依赖及 repository。
2. 新 `modules/session-api/src/im/hikaru/harness/session/api/SessionTerminals.kt`。
3. 新 `modules/session-api/test/im/hikaru/harness/session/api/SessionTerminalsContractTest.kt`。
4. 新 `modules/exec-jvm/src/im/hikaru/harness/exec/jvm/JvmPtyTerminalSessions.kt`。
5. 新 `modules/exec-jvm/test/im/hikaru/harness/exec/jvm/JvmPtyTerminalSessionsTest.kt`。

禁止 reset/stash/clean/commit、修改其他 dirty/untracked、操作真实用户配置或既有用户进程。本批不修改 Host wire、Gateway、Local/Relay、Bundle、main 或 Compose。后续在本批通过后接线，不能把基础交付报告为页面已完成。若五文件无法完整满足合同，说明具体原因并停止扩写。

## common 合同

- Serializable 强类型 scope、TerminalSessionId、producer incarnation；完整 owner = opaque scope + SessionId + 当前实例生成的 incarnation，完整 terminal key 再含 TerminalSessionId。JVM service 实例绑定可信 owner/canonical Session cwd，文本相同不代表获得 Host 权限，Host adapter 后续负责真实对象绑定。
- 独立 terminal service 提供显式 create、list、read、write、resize、close，以及有界 service shutdown。所有请求携带完整 owner/key；旧 scope/session/incarnation/id 为真实 Rejected/NotFound/Unavailable，不能启动、写入、resize 或关闭任何进程。list/read/snapshot 构造从不创建 shell。
- create 使用显式 argv、environment、rows/columns、可选 cwd；cwd 解析后必须位于绑定的 canonical 根内。拒绝 NUL、无效 dimensions、超限输入和容量后才启动/IO。argv 不隐式附加 login 参数或 source 用户配置。
- metadata 包含完整 key、canonical cwd、初始命令、生命周期；Running / Exited(code) / Unknown 真实区分。Input Open / Closed / Unknown；write 只有真实 write+flush 成功才 Applied，成功入队不算。
- screen/history DTO 包含 rows/columns、文字、列位置和样式、cursor/visible、alternate 状态、revision、原始输出尾与 truncated。样式支持默认/索引/RGB 颜色和必要文本属性。锁内逐值深复制成 common DTO，不能带 JediTerm/AWT、TextStyle、CharBuffer、TerminalLine/TextEntry 引用。旧 snapshot 不随新输出/resize 改变。
- bounded session capacity、尺寸、scrollback、raw tail、input bytes 与 shutdown wait。raw tail 保留原始 ANSI 字符且不切坏 surrogate；不能把任意 tail 重新喂给 emulator。

## JVM 解析、IO 与生命周期

- pty4j 真实 PTY；持续 InputStreamReader UTF-8 decoder + TtyConnector/TtyBasedArrayDataStream + JediEmulator/JediTerminal。真正流结束才 EOF；不能每 chunk 重建 stream/parser/decoder。
- 模型 mutation、cursor/display 状态、snapshot 和 resize 必须协调同一模型锁；snapshot 内再按 TerminalTextBuffer 契约取锁和深复制。阻塞 PTY read 不占 snapshot 锁。可用模型 gate 在 connector 的真实阻塞 read 前释放、finally 重取，让连续 emulator 保留半个 escape，同时 snapshot/resize 可达；不要围绕可能阻塞的 emulator.next 持锁而不释放。明确锁顺序。
- parser 的设备应答通过同一受控 connector 写入；UI write 不走吞异常的 TerminalStarter send 队列。write 在 IO worker 真正完成后返回；已写后 flush/后续 IO 异常、取消或超时为 UnknownOutcome，零自动重发；不能返回 Applied。允许 narrow internal after-write IO seam 验证真实已发送后失败，不使用 fake PTY 代替 native 场景。
- resize 同时更新 JediTerm 模型与 kernel PTY，Applied 时两者为请求尺寸；任何一半失败按真实结果报告 Unknown，不能仅改变 DTO。
- 真实 connector/input/output/error streams 关闭解除阻塞，等待 owned parser/IO worker 和 process 结束后才能 close Applied / 释放 entry。仅 requestEmulatorStop/cancel Job 不足。无法证实清理时 Unknown 并保留所有权/容量；不要移除记录或伪造已退出。只处理本实例明确拥有的进程/后代。
- natural exit 必须 waitFor 与完整 EOF/解析完成后 Exited；保留最终 screen/history/raw 供 read，显式 close 后才移除。shutdown 不把未清理记录变成已释放。
- 对尚未提供真实能力的平台返回 Unavailable；本次只验证当前 macOS x86_64，不宣称 Windows/Linux/arm64 或远端已通过。

## 固定验收矩阵

新增 common 类恰好 4 个 `@Test`：

1. `blankIdentityComponentsAreRejected`
2. `terminalDtosRoundTripWithCompleteIdentity`
3. `snapshotAndSizeInvariantsAreEnforced`
4. `resultsRetainRequestedOwnerAndTerminalIdentity`

新增 JVM 类恰好 12 个 `@Test`，均使用测试拥有的真实 PTY、临时 cwd/HOME、finally cleanup；关键输出用 marker/有界等待，不用固定长 sleep：

1. `readingDoesNotCreatePtyAndCapacityIsBounded`
2. `realPtyAndResizeUpdateKernelAndEmulator`：三个标准流为 TTY，stty 初始24×80、resize30×100，DTO尺寸对应。
3. `realInputAndSplitUtf8RetainDecoderState`：真实输入回显与跨 write/read 的多字节字符完整。
4. `splitAnsiKeepsParserAndSnapshotsDetached`：escape分块、颜色、旧快照不变。
5. `carriageReturnCursorAndEraseUpdateScreen`
6. `alternateScreenRestoresPrimary`
7. `staleScopeSessionIncarnationAndTerminalHaveNoSideEffects`：逐一验证 create/list/read/write/resize/close，无 marker、存活/size变更或额外启动。
8. `writeFailureAfterDeliveryIsUnknownAndNeverResent`：真实写入后 seam 抛 IO；Unknown，单次发送，之后不静默重发。
9. `blockingReaderClosesAndOwnedWorkersFinish`：idle reader 时 snapshot 及时返回；close 后实际worker/process结束。
10. `naturalExitKeepsFinalSnapshotUntilExplicitClose`
11. `scrollbackAndRawTailAreBounded`：完整stream从起点解析、tail truncated、尾部Unicode不坏。
12. `invalidDimensionsInputAndCwdAreRejectedBeforeIo`

回归既有 `ExecProcessContractTest` 16 项、`SessionApiContractTest` 1 项、`SessionDescendantsContractTest` 5 项。父线程总计 5 类 **38 项**，每类实际 XML 0 failures/errors/skipped；不由前置探针推导通过。正式 exec-jvm 与 jvm-app runtime dependency graph 必须保存，确认 JNA/stdlib 最终解析与无重复版本。本阶段不需要重跑无关 Cron 134 项或截图；Host/renderer 批次另有页面和真实装配验收。

## 交付

父线程先保存全部2,216输入及五文件 before SHA（新增null）。工作者报告到 `/tmp/harness-terminal-pty-foundation-delivery-20261009.json` 与同名 `.md`：完整 before/after、固定16个新增测试名、实际模型/provider、设计/锁顺序/清理证据、未解决限制。明确 `builds_run=false`、`tests_run=false`、`screenshots_run=false`、`write_set_released=true`、`source_writes_stopped=true`，之后停止全部写入。父线程检查其余输入无漂移，再冻结、串行运行38项和依赖图、归档真实结果。
