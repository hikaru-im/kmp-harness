# 终端语义输入：common 与真实 PTY 合同

日期：2026-10-09。实施模型固定 `SHUAIAPI/gpt-6.1-sol`，provider `opencodex`。父线程分析、审阅及冻结后串行验收；工作者只实施与静态检查。

Host/Local 的 host-r3 已验收 11 类 70/70、完整 JVM app build 成功。此批只扩展 standalone service 与 native adapter，后续单独接 Host/Gateway/Local 和 renderer。现有 raw write、全部旧测试及用户 dirty/untracked 保留。

## 唯一写集（六文件）

1. 新增 `modules/session-api/src/im/hikaru/harness/session/api/TerminalInput.kt`。
2. 修改 `modules/session-api/src/im/hikaru/harness/session/api/SessionTerminals.kt`：仅新增 service 默认方法，不改既有 DTO 与 raw write 签名。
3. 新增 `modules/exec-jvm/src/im/hikaru/harness/exec/jvm/JvmTerminalInputAdapter.kt`。
4. 修改 `modules/exec-jvm/src/im/hikaru/harness/exec/jvm/JvmPtyTerminalSessions.kt`：接 adapter、真实 paste mode 与单次有界输入。
5. 新增 `modules/session-api/test/im/hikaru/harness/session/api/TerminalInputContractTest.kt`。
6. 新增 `modules/exec-jvm/test/im/hikaru/harness/exec/jvm/JvmTerminalSemanticInputTest.kt`。

禁止改 YAML、旧测试、Host/Gateway/client/页面或用户配置；禁止 build/test/截图、启动应用、提交、reset/stash/clean、处理用户既有进程。测试代码的真实 PTY 只能由父线程运行。

## 公共接口

- 新增 `@Serializable sealed interface TerminalInput`，分支与 SerialName：`Text(text: String)` / `text`、`Paste(text: String)` / `paste`、`KeyPress(key: TerminalKeyName, modifiers: Set<TerminalModifier> = emptySet(), character: String? = null)` / `key_press`。
- `TerminalModifier`：Shift、Control、Alt、Meta。
- `TerminalKeyName`：Enter、Escape、Tab、Backspace、Up、Down、Left、Right、Home、End、Insert、Delete、PageUp、PageDown、F1..F12、KeypadUp、KeypadDown、KeypadLeft、KeypadRight、Character。仅此四个 keypad 方向和 Home/End，不宣称完整数字键盘。
- KeyPress 的构造不变量：Character 必须携带恰好一个 U+0020..U+007E 的 ASCII 字符；其他键不得携带 character。不暴露 AWT、JediTerm、原生 keycode、Process 或 byte[]。
- `SessionTerminalsService` 新增 `suspend fun input(key: TerminalKey, input: TerminalInput): TerminalMutationResult`，默认返回请求完整 key 的 typed Unavailable，保持旧实现源兼容。
- 捕获 modifiers 的独立集合；返回结果完整 key 与请求一致。

## Text、Paste 和组合键

- Text 是完整已提交 UTF-8 字符串，不能按 Char 拆 surrogate；不修改其换行。拒绝空文本、NUL、malformed UTF-16。preedit 没有公共请求分支，后续 renderer 只发送 commit。此批不宣称 IME 控件已可用。
- Paste 拒绝空文本、NUL、malformed UTF-16；CRLF→LF→CR，原有独立 CR 保留。开启 bracketed paste 后添加 ESC[200~ 与 ESC[201~。最终实际 UTF-8 bytes 含包裹必须 <= limits.inputBytes；拒绝不调用 nativeWrite、不设置 Unknown fence，后续合法输入仍可成功。
- Meta 在此批全部 Rejected，由后续产品快捷键消费者处理，不向 shell 发送。
- 命名键允许 Shift/Control/Alt 的组合，复用固定 JediTerm processor/encoder 的真实行为。Shift+Enter 不启用额外 ESC+CR 策略；scroll hint 不新增 client 模式状态。
- Character 支持 Control（可带 Shift）或仅 Alt；无 modifier、Alt+Control、Alt+Shift 等组合 Rejected，Unicode 走 Text。
- Control 字母 A..Z/a..z 显式合成 1..26；Space/@→NUL、[→ESC、反斜线→FS、]→GS、^→RS、_→US、?→DEL。其他 Control 字符 Rejected。Ctrl+Space 的 NUL 使用语义二进制路径，不能复用 raw write 的 NUL 文本拒绝规则。
- 仅 Alt 的 Character 只接受 ASCII 字母。实时 AltSendsEscape=true 时使用 processor 的 pressed 行为（base key 为字母、无 Shift，产生 ESC+小写字母）；false 时 Unhandled→Rejected，后续 composed 文本走 Text。不猜测 native Option 字符。
- named Escape/Tab 的 keyChar 分别为 ESC/TAB；Enter/Backspace 为相应控制字符；方向/功能/keypad 为 CHAR_UNDEFINED。Control 字符的 keyChar 由 adapter 显式合成，不能假定平台事件已转换。

## 真实 parser 状态与 encoder 隔离

- internal JVM adapter 继承 JediTerminal；记录 setApplicationArrowKeys、setApplicationKeypad、setAutoNewLine、setAltSendsEscape 的真实 setter，并调用 superclass。
- HeadlessDisplay 保存 setBracketedPasteMode callback 的 boolean；与 parser/编码共用 Entry gate。
- 每次按键在模型 gate 内使用 fresh TerminalKeyEncoder，先应用当前 arrows/keypad/AutoNewLine/Alt，返回本次私有 bytes。不得只复制共享 encoder 输出、仅恢复 arrow 缓存或反射替换 private 字段。
- 复用 TerminalKeyEventProcessor 的 PRESSED 与 BytesResult/StringResult/Unhandled；StringResult 转 UTF-8，Unhandled 为已知拒绝。adapter 的 getCodeForKey 提供 fresh encoder。可用 JediTerm core input 常量，common 不暴露它们。
- 构造必须安全：JediTerminal 构造会调用可覆盖 reset(true)，不能访问尚未初始化的 Kotlin 对象属性。可避免 override reset，仅用 primitive mode 字段和实际 setter callbacks。
- 本批 reset 策略严格保留固定 JediTerm 3.76 行为：soft/hard reset 会将 AutoNewLine=false；arrows/keypad/Alt/bracketed 未收到 setter 时保留已有值。必须通过真实 parser 的 CSI ! p / ESC c 观测；不宣称 xterm reset 完整兼容。
- 初始 arrows/keypad/AutoNewLine=false、AltSendsEscape=true、bracketed=false。所有 setter 更新与 input 编码在同一 gate 内。输出必须确实解析后才承诺 mode 已生效。

## IO、结果和清理

- 先执行既有完整 owner/incarnation/platform/key/closed 检查；已取消 caller 在入队前 Rejected、零副作用。
- 使用同一个单线程 bounded IO worker/现有容量8，语义编码与 native write 作为一个任务。gate 内编码并采样 paste mode，释放 gate 后该 worker 直接 nativeWrite+flush。不得重新向自身提交并等待，不得持 gate 做 native IO。
- 可以为新路径增加 typed Future / typed worker result，保持旧 raw 路径字节与语义。编码/最终预算/已知 queue 拒绝必须返回 Rejected，不得由旧 await 的异常转换误记 Unknown。IO 队列满的 submit failure 为已知 Rejected、不污染终端 input state。
- Applied.bytes 为实际 native 写入并 flush 的字节数。实际写入失败、超时、派发后丢确认/调用者取消且不能证明未写入→UnknownOutcome、fence 后续输入、无自动重试。明确取得的编码拒绝可以保留 Rejected。
- inputUnknown 在 worker 取下个任务前设置；排队任务在写前复查 canInput。旧 raw write/resize 也受同一 fence。
- 不伪造本地回显。只读/编码不能创建进程。close/shutdown 确认该批真实 owned process、descendants 和 worker 清理；macOS x86_64 为当前唯一已验证 native 平台。
- 可为队列压力测试在已有 internal Diagnostics 增加真实 queue size 观察值；不得用 fake native write、替代 transport 或给生产加入输入历史。

## 固定测试（新16项，旧70项保留）

所有 suspend 测试显式 `runBlocking<Unit>`，使用真实临时 root，finally shutdown 与 owned diagnostics 断言，poll 有界10秒。真实字节 fixture 可使用 /bin/sh + stty raw -echo + dd/od，把 PTY 收到的 bytes 保存并比对；外部程序仅在临时 owned root，不能依赖用户 Python/node 安装。mode 测试等待 parser 处理完独特 marker 后再输入，不能仅等文件或 rawTail。

Common 四项方法名固定：

1. `semanticInputsRoundTripWithoutPlatformObjects`：三分支、Unicode、全部键/modifiers 序列化及 schema。
2. `keyPressCharacterInvariantsAreEnforced`：ASCII/命名键约束、空/多字符/surrogate/Unicode 拒绝。
3. `legacyServiceSemanticInputIsTypedUnavailable`：旧实现无需实现新方法，返回原完整 key。
4. `semanticResultRetainsRequestedIdentityAndByteCount`：完整 key 与 Applied byte count 的公共合同。

Native 十二项方法名固定：

1. `realParserArrowModesAndModifiedThenPlainAreIsolated`：真实程序先开启/关闭 DECCKM；四箭头 ANSI/application 的 plain→Ctrl→plain 字节，不能只看 adapter 返回。
2. `realFunctionAndKeypadModesUseIndependentEncoders`：F1..F4 的 Ctrl→plain SS3 恢复、F5..F12 普通码；真实 ESC=/ESC> keypad 四方向及 Home/End，arrows 独立。
3. `realEnterBackspaceTabAndEscapeUseCurrentModes`：AutoNewLine 开/关、普通/Control Backspace、Tab/ShiftTab、Escape 的实际字节。
4. `realControlAndAltCharactersReachPtyExactlyOnce`：Ctrl+C/NUL及所有控制字符映射；Alt+b，实时1039开/关与拒绝恢复；Meta/unsupported组合零写入。
5. `committedUnicodeIsWholeAndNotLocallyEchoed`：中文和 non-BMP 完整 bytes，Text换行不规范化，不伪造输出。无 preedit 分支不能视为原生 IME 验收。
6. `realPasteNormalizesNewlinesAndHonorsBracketedMode`：真实2004开/关、CRLF/LF/CR混合、实际开始/结束字节及次数。
7. `finalByteBudgetRejectionKeepsInputOpen`：Text UTF-8预算、Paste包裹后超额、malformed/NUL/空文本、known Unhandled拒绝；零 nativeWrite且后续合法输入成功，raw NUL仍拒绝。
8. `softAndHardResetPreservePinnedParserInputPolicy`：真实CSI ! p / ESC c，验证构造安全、AutoNewLine归零与其余模式按此合同保留。
9. `semanticInputRejectsEveryStaleIdentityBeforeDelivery`：scope/session/incarnation/terminal/closed、不创建额外进程、有效key仍能写。
10. `lostSemanticAcknowledgementFencesWithoutRetry`：现有 afterNativeWrite seam 在真实交付后抛错；真实文件只一份，输入/resize/raw写入被fence。
11. `semanticCancellationBeforeAndAfterDeliveryRemainDistinct`：预派发零写入 Rejected；真实交付后取消→Unknown、一次写入、可清理。
12. `boundedWorkerRejectsOverflowAndParserResizeDoNotDeadlock`：真实 blocked IO + 8 queued + 已知overflow Rejected，无误fence；释放后队列按序完成，parser/resize/读取有界成功、owned清理。

父线程冻结预计 2,233 输入；串行13类86项（新4+12与host-r3全部70）、正式runtime解析图及完整JVM app build。测试不削弱旧断言、不增加skip；失败保留独立attempt证据，指定模型做最小窄修。

## 官方证据与交付

官方 JediTerm commit `1b354a63f9845aee3474b0749af0a62e406f0076`；完整固定源码与发布JAR编码探针在 `terminal-semantic-input-evidence-2026-10-09/` 和 `terminal-foundation-evidence-2026-10-09/`。分析见 `terminal-semantic-input-analysis-2026-10-09.md`。

交付 `/tmp/harness-terminal-semantic-input-delivery-20261009.json` 与 `.md`，字段 model/provider、contract_sha256/baseline_sha256、六文件 before/after SHA、builds_run/tests_run/screenshots_run=false、write_set_released/source_writes_stopped=true。

整体复刻仍 active；Host语义输入接线、renderer/页面、Remote、原版像素与原生窗口分别待验收。本合同通过也不等于这些能力完成。
