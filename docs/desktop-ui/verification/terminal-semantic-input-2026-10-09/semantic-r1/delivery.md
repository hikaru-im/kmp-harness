# 终端语义输入六文件交付

日期：2026-10-09。model：`SHUAIAPI/gpt-6.1-sol`；provider：`opencodex`。

本合同六文件实施已完成，写集已释放，源码写入已停止。仅做源码与静态检查；未运行编译、构建、测试、截图，未启动真实 PTY 或应用，未处理用户既有进程。运行验收由父线程串行执行；本记录不声称测试通过。

## 固定材料

- contract_sha256：`399ecac62f0d0feaeeefb14d4d9708ae83ef6daaa6e4cacb1005696e778cc25a`
- baseline_sha256：`d4c05293631d6d9a7c15b8da409439cb6d6f97454261b8128422200993e509ae`
- 两份材料保持不变。只读 Comet probe 返回 none，未进入旧任务。

## 写集与 SHA

| 完整路径 | before_sha256 | after_sha256 |
| --- | --- | --- |
| `/Volumes/Data/IdeaProjects/kmp-harness/modules/session-api/src/im/hikaru/harness/session/api/TerminalInput.kt` | null（新增） | bd877de55744ef00e467489dfb5fee76ded7a146d469066d26f535e99fe9ef43 |
| `/Volumes/Data/IdeaProjects/kmp-harness/modules/session-api/src/im/hikaru/harness/session/api/SessionTerminals.kt` | d2b2de4a764270fc5a53d6d5356ed2b963c002fc0b2fdc61e569c81c57377e9d | 1144cc4ea3a90b1d9af40a34064e7d461d4af9a743f53ea1de5be1f7fc333455 |
| `/Volumes/Data/IdeaProjects/kmp-harness/modules/exec-jvm/src/im/hikaru/harness/exec/jvm/JvmTerminalInputAdapter.kt` | null（新增） | 8ff934e12c001db69a95c83c793e0869dbf1cf76205a95f3c5d880d4bbaca54e |
| `/Volumes/Data/IdeaProjects/kmp-harness/modules/exec-jvm/src/im/hikaru/harness/exec/jvm/JvmPtyTerminalSessions.kt` | 81f9ded80b6fa8c4f985100166328cb5b8d130db9f22044fb8fc30c923ef306c | 36eb35ec97ceee38e49296671ad1dc0ffc431643c781dd646fc8a5815aaf6092 |
| `/Volumes/Data/IdeaProjects/kmp-harness/modules/session-api/test/im/hikaru/harness/session/api/TerminalInputContractTest.kt` | null（新增） | e4d0549e9ea5344a4b14eae492789e283937acca0fe4690ec81c001f6064736d |
| `/Volumes/Data/IdeaProjects/kmp-harness/modules/exec-jvm/test/im/hikaru/harness/exec/jvm/JvmTerminalSemanticInputTest.kt` | null（新增） | 799a43702fcd9a7fbbb418da7bfb490f7d5f379df7f035a918ea4f772fabbf41 |

## 实施说明

- Common：可序列化 Text/Paste/KeyPress、31 个 key、4 个 modifier；构造捕获独立 modifier 集合，Character 限制为一个可打印 ASCII。SessionTerminalsService 仅新增默认 input 方法，typed Unavailable 保留完整请求 key。
- 构造/reset：adapter 不覆盖 reset。JediTerminal 构造期 reset(true) 的 AutoNewLine setter 只访问 primitive 字段并调用 superclass，不访问未初始化 Kotlin 对象。每次按键创建 fresh encoder，应用真实 arrows/keypad/AutoNewLine/Alt；HeadlessDisplay 保存真实 bracketed callback。CSI ! p / ESC c fixture 固定 AutoNewLine=false、其余未收到 setter 的模式保留策略。
- 已知拒绝：空/NUL/malformed Text/Paste、最终 UTF-8 字节预算（包含 paste wrappers）、Meta/unsupported、Unhandled、预派发取消、已知队列溢出均 typed Rejected，不调用 nativeWrite，正常 Open 终端保持 Open。Ctrl 字符显式合成；CtrlSpace NUL 进入语义 binary 路径，raw 文本 NUL 规则保留。
- IO 不确定：实际写入失败、超时、交付后丢确认或取消返回 UnknownOutcome，设置 fence；后续 semantic/raw/resize 均受限，无重试。worker 在其检测到失败或取消后、取下一任务前设置 fence，排队输入写前复查 canInput。
- 单次 bounded worker：同一个容量 8 的 IO 队列中，gate 内编码/采样，然后释放 gate 直接调用既有 nativeWrite 与 flush，不向自身二次 submit/wait；保留 afterNativeWrite 真实交付 seam。Diagnostics 增加真实 io.queue.size。
- 真实字节 fixture：临时 owned root 中 /bin/sh + stty raw -echo + dd/od，先等待真实 parser screen 的独特 marker，随后输入；独立预期字节与实际 binary/hex、Applied.bytes、真实 write 次数对比，并以 unexpected.bin 观察多余输入。无用户 Python/node 依赖或 silent skip。
- 清理：每个 native 方法显式 runBlocking<Unit>，poll 有界 10 秒；finally 在 NonCancellable 中 shutdown，检查 owned process、descendants 与实际 worker Thread 清理，并删除临时 root。这些运行断言尚未执行。

## 固定 16 方法

### Common（4 项）

1. `semanticInputsRoundTripWithoutPlatformObjects`
2. `keyPressCharacterInvariantsAreEnforced`
3. `legacyServiceSemanticInputIsTypedUnavailable`
4. `semanticResultRetainsRequestedIdentityAndByteCount`

### Native（12 项）

1. `realParserArrowModesAndModifiedThenPlainAreIsolated`
2. `realFunctionAndKeypadModesUseIndependentEncoders`
3. `realEnterBackspaceTabAndEscapeUseCurrentModes`
4. `realControlAndAltCharactersReachPtyExactlyOnce`
5. `committedUnicodeIsWholeAndNotLocallyEchoed`
6. `realPasteNormalizesNewlinesAndHonorsBracketedMode`
7. `finalByteBudgetRejectionKeepsInputOpen`
8. `softAndHardResetPreservePinnedParserInputPolicy`
9. `semanticInputRejectsEveryStaleIdentityBeforeDelivery`
10. `lostSemanticAcknowledgementFencesWithoutRetry`
11. `semanticCancellationBeforeAndAfterDeliveryRemainDistinct`
12. `boundedWorkerRejectsOverflowAndParserResizeDoNotDeadlock`

## 最终静态核对

- 基线 2,229 份输入；仅允许修改的两份已有文件 SHA 改变，写集外 2,227 份输入 SHA 均一致，无删除。新增四份允许文件，预计冻结输入 2,233。
- 方法名称与合同逐项一致：common 4 / native 12；共 13 个 suspend 测试均显式 runBlocking<Unit>。
- git diff --check 返回 0；六文件分别对 frozen base 或 /dev/null 做 git diff --no-index --check，输出均为空，无 whitespace 错误。该 no-index 命令因存在文件差异返回 1，完整结果见 JSON。
- 六文件的源码分隔符、字符转义与 literal 长度静态核对通过，无非预期控制字节；此项不是编译或测试。
- 原始 git status 4,835 项全部保留，未出现移除。最终增加四份允许文件，以及另一个观察到的 `docs/desktop-ui/verification/terminal-semantic-input-2026-10-09/parent-verify.py`；本线程未创建、修改或处理后者。
- write_set_released=true；source_writes_stopped=true；builds_run/tests_run/screenshots_run=false。

完整机器可读记录：`/tmp/harness-terminal-semantic-input-delivery-20261009.json`。
