# 交互终端 renderer 与真实页面合同 R1

日期：2026-10-10。唯一实施模型 `SHUAIAPI/gpt-6.1-sol`，provider `opencodex`。父线程分析、审查、冻结并串行运行；工作者只实施与静态检查，交付后退出。Comet先前probe为none，这是持续任务，不重新probe。

## 已验收基线与本批交付

Host语义输入R2已通过17类104/104与完整JVM app build；110份归档SHA、XML和runtime图已独立复核。证据 `verification/terminal-host-semantic-input-2026-10-10/host-semantic-r2/`。真实parser来自jediterm-core3.76；native依赖pty4j0.14.0，只在macOS x86_64验收。所有旧测试与写集外输入保持字节。

本批一次交付可操作页面：真实终端inventory、显式新建交互shell、typed grid、输入/粘贴、history滚动/选择/复制、kernel resize、退出保留与显式close。后台输出及子Agent已有功能继续可用。不得只交空renderer或生产fixture。

## 唯一源码写集（22文件）

修改7文件：

1. `modules/session-api/src/im/hikaru/harness/session/api/SessionTerminals.kt`：只增加run列映射及其不变量。
2. `apps/shared/src/im/hikaru/harness/client/connection/SessionTerminals.kt`：同一portable列映射。
3. `modules/exec-jvm/src/im/hikaru/harness/exec/jvm/JvmPtyTerminalSessions.kt`：只修改copyLine生成映射与截断处理；不改encoder、write/cleanup/权限策略。
4. `apps/jvm-app/src/im/hikaru/harness/desktop/connection/LocalSessionTerminals.kt`：双向深复制列映射。
5. `apps/shared/src/im/hikaru/harness/client/panels/ThreadPanels.kt`：在既有终端tab中加后台输出/交互终端选择，挂接新页面；现有tab enum、state/reducer/controller与旧标签保持。
6. `apps/shared/src/im/hikaru/harness/client/app/HarnessApp.kt`：新controller绑定同一verified panelOwner，注入launch provider，发布前同步isBoundTo；原root快捷键顺序保持。
7. `apps/jvm-app/src/main.kt`：给实际LocalConnection注入DesktopTerminalLaunchProvider。

新增7个生产文件：

8. `apps/shared/src/im/hikaru/harness/client/panels/TerminalLaunchProvider.kt`
9. `apps/shared/src/im/hikaru/harness/client/panels/PtyTerminalModels.kt`
10. `apps/shared/src/im/hikaru/harness/client/panels/PtyTerminalController.kt`
11. `apps/shared/src/im/hikaru/harness/client/panels/PtyTerminalGrid.kt`
12. `apps/shared/src/im/hikaru/harness/client/panels/TerminalTextInputBridge.kt`
13. `apps/shared/src/im/hikaru/harness/client/panels/PtyTerminalPanel.kt`
14. `apps/jvm-app/src/im/hikaru/harness/desktop/terminal/DesktopTerminalLaunchProvider.kt`

新增8个测试文件（helper放各文件内部）：

15. `modules/session-api/test/im/hikaru/harness/session/api/TerminalColumnMappingContractTest.kt`
16. `modules/exec-jvm/test/im/hikaru/harness/exec/jvm/JvmTerminalColumnMappingTest.kt`
17. `apps/shared/test/im/hikaru/harness/client/panels/PtyTerminalControllerTest.kt`
18. `apps/shared/test/im/hikaru/harness/client/panels/PtyTerminalGridTest.kt`
19. `apps/shared/test/im/hikaru/harness/client/panels/TerminalTextInputBridgeTest.kt`
20. `apps/jvm-app/test/im/hikaru/harness/desktop/terminal/DesktopTerminalLaunchProviderTest.kt`
21. `apps/jvm-app/test/im/hikaru/harness/desktop/terminal/PtyTerminalConnectionIntegrationTest.kt`
22. `apps/jvm-app/test/im/hikaru/harness/desktop/PtyTerminalPageInteractionTest.kt`

禁止修改其它源码/YAML/旧测试/用户配置/合同/基线/父线程脚本。实现不需要新增动态plugin/slot。保留全部dirty/untracked，不reset/stash/clean/checkout/commit，不处理用户既有进程。发现写集不足时给父线程具体证据，不能自行扩写。

## A. 字符列映射（已定案）

两套TerminalTextRun最后新增 `columnOffsets: List<Int>? = null`，保留原四个参数、旧JSON解码及旧line.text语义。非null长度=text.length+1，最后一项=span，所有项0..span严格递增（只有一项时无需相邻比较）。每项是该可见UTF-16 code unit在原native entry中的相对列，末项是native span边界；第一项可大于0。空text有效映射为 `[span]`。

producer按真实entry位置生成：DWC删去，NUL转空格；保留native count作为span，不因过滤移动其它run起始列。例：native `中 DWC x` → text=`中x`、span3、offsets `[0,2,3]`；native surrogate pair → 两UTF-16单位、span2、`[0,1,2]`；leading DWC+x → `[1,2]`；DWC-only span2 → `[2]`。

若viewport截断恰好切开完整surrogate pair，省略高surrogate但保留其native列背景；真正孤立surrogate用U+FFFD占原来的一个native列。不同entry/style边界不擅自合并surrogate，各孤立单位按上述规则处理。style背景覆盖完整span，省略continuation或截断字符不能暴露其它内容。history使用同一规则，不另运行wcwidth。

core/client不变量一致，Local双向映射复制offsets及已有attributes/runs。旧null映射仅可对严格可打印ASCII且text.length==span安全推导连续列；其它缺映射的run须显示明确快照不兼容状态，不能猜宽度或重新解析rawTail。当前生产每个run都给精确映射。

## B. Launch provider与身份

common `TerminalLaunchProvider` 准备portable argv/environment/cwd，不启动进程；默认实现返回Unavailable。计划集合在提交前冻结，环境值不得进入toString、日志、错误或交付。Desktop实现绑定expectedHostId及**同一LocalConnection对象**，只对当前macOS x86_64提供准备能力；同Host文本但不同connection、Relay及缺注入继续不可用。

controller与provider均核验discovery.session、discovery.owner.session==panelOwner.parentSessionId；cwd使用discovery.canonicalCreationCwd，Host继续最终验证。只在明确点击“新建终端”后准备并派发一次create。Desktop读取实际环境；SHELL须绝对路径、regular/executable，失败按/bin/zsh、/bin/sh次序验证；argv为shell和-i。给TERM=xterm-256color、COLORTERM=truecolor，缺PATH时给标准系统PATH；不改变用户配置。可注入只读环境/文件检查用于测试，但正式main使用真实提供者。

## C. Controller、可见性与真实操作

复用ThreadPanelsOwner(connection身份、host generation/identity、parentSessionId)；独立PtyTerminalController有typed state与isBoundTo，不改既有后台控制。HarnessApp在异步bind前隐藏旧owner state；新owner、断线、离开Thread、切会话、关闭controller均使迟到回调失效。读取绑定、tab/交互视图打开、刷新、切主题和renderer重建均只读，create计数必须0。

ThreadPanels既有“终端”入口和BackgroundOutput/Subagents enum保留；终端内提供“后台输出”“交互终端”两种视图，默认后台输出以保留已验收流程。新增view选择按owner/tab打开周期隔离；关闭整体面板/该tab停止PTY观察和未派发输入，不能调用closeTerminal或关shell。用户显式“关闭终端”才派发真实close。

状态明确区分discovery、inventory、screen的加载/不可用/错误/空/Ready；mutation的Pending/Applied/Rejected/Unavailable/Unknown有真实原因，不暴露内部异常或环境。完整owner/key/request epoch/revision独立保留；每次回包核验身份。保留每个完整key的revision high-water：低revision不覆盖，等revision但内容不同给冲突状态，不伪造新revision。inventory不得制造key或将消失终端改为虚构空screen。

新建pending期间阻止重复点击；Created选择真实返回key并读取。Unknown不重试、不编造key；刷新inventory恢复已有终端供用户选择，但不能将nokey回复声称为已创建成功。已有key的Unknown与create uncertainty按绑定保留，刷新/切主题/关闭重开不能擦掉fence。不得通过自动新建或自动close解决Unknown。

输入、resize和close通过同一有界顺序dispatcher，未派发总数最多32、文本UTF-8总量最多64KiB；超限明确拒绝且不丢后续正常事件。承认快照不可变、modifiers同步捕获；每事件一次SessionApi.inputTerminal，不本地echo，不raw重试。单次Rejected保持可用；Unknown停止该key后续input/resize并清掉未派发队列（这些事件显示未发送），close仍可显式申请一次。外层Unknown不声称native已fence。旧owner的在途效果不能取消后自动重放给新owner。

观察可见且选中终端时单一串行poll，建议200ms，不叠加read；退出取得最终snapshot后停止poll，保留history/screen直到显式close。隐藏/离开取消观察，重开读同key，不启动shell；read失败给明确状态及显式刷新。runtime所有job归controller scope，close不泄漏后台轮询。

## D. Grid、输入桥与快捷键

common Compose renderer只消费typed screen/history。用同一实际monospace字体测量cell宽/行高，固定列定位每个codepoint（完整surrogate pair一起绘制，用offsets求覆盖列），背景覆盖run span；支持256 indexed/RGB/default、Bold/Italic/Dim/Inverse/Underlined/Hidden，Blink最少静态可见且说明未做动画。cursor按typed row/column显示，仅live screen底部且focused可输入时绘制。rawTail不参与绘制和copy。

history+screen形成统一行地址，alternate不混入primary history。滚动后保持阅读位置并可回底部；底部才跟随新输出。鼠标选择与copy使用同一offsets，宽字符continuation不重复copy，wrapped行不加硬换行，非wrapped行换行，空隙按terminal列保留。owner/key/屏幕buffer切换使无效selection清空。显示viewport行列从实际可用空间和相同字体指标推导，限制rows2..200、columns5..500；创建前记录size，已有key只在尺寸变化时真实resize并去重。错误/Unknown不能假装resize已确认。

输入桥用Compose BasicTextField/TextFieldValue处理已提交文字，grid为实际正文，不用只读log代替交互。组合输入期间composition文本只作为preedit显示；composition结束且仍focus/同epoch才提交完整文本一次并清空bridge。取消、空文本、失焦、切key/owner、关闭视图或programmatic reset不能提交旧preedit。组合期间Enter/Backspace等交给文本编辑器，不能同时派发KeyPress；IME确认只提交文本，后续独立Enter才发送终端Enter。BasicTextField焦点与composition确认必须用实际Compose API，不能用字符串变化推测每个preedit字符都是提交。native IME实际验证仍独立记录，不因bridge单元测试称原生输入法已通过。

focused terminal内支持现有TerminalKeyName及Control/Alt ASCII，用显式键映射；key-down含auto-repeat每次只派发一次，key-up只释放不write。平台Meta+C复制选区、Meta+V或显式“粘贴”从clipboard一次派发Paste，Ctrl+C保留终端中断。没有支持的app command向已有root bubble；不得新增全局root或preview提前执行app command。快捷键录制/capture已有preview优先。剪贴板在common Compose使用现有portable API，不自行调用OS shell。

正文start16/top8/bottom12，scrollbar10为原ASAR证据初始值；页头允许窄宽换行，所有控件可点击。新增loading/空/Unavailable/Unknown不依赖fixture。关闭视图与关闭终端按钮命名清楚。

## E. 固定新增测试（36项，必须全部实际执行）

### TerminalColumnMappingContractTest：3

- `mappingInvariantsAndLegacyJsonRemainCompatible`：有效/非法边界，旧null JSON和新roundtrip。
- `wideContinuationAndSurrogateOffsetsRetainNativeColumns`：独立literal中文、emoji、leading/DWC-only预期及style。
- `mappingCollectionsAreDetachedAcrossSnapshots`：集合复制与历史不共享。

### JvmTerminalColumnMappingTest：4（真实PTY/parser）

- `realParserMapsAsciiCjkEmojiAndNulToNativeColumns`
- `realStylesHistoryAndLeadingContinuationKeepOffsets`
- `realResizeTruncationPreservesNativeSpansAndValidUnicode`
- `realMappedSnapshotsRemainDetachedAndCleanupIsOwned`

用真实parser与独立literal预期，禁止使用被测算法生成expected。可以利用真实ANSI overwrite/style/resize形成边界，finally有界NonCancellable清理owned PID/Thread；DWC-only/孤立surrogate不可实际制造的边界可补纯模型断言，但不得称这些边界均native覆盖。

### PtyTerminalControllerTest：8

- `openingRefreshingAndRebindingNeverCreateTerminal`
- `explicitCreateDispatchesOnceAndUnknownDoesNotRetry`
- `ownerConnectionAndFullKeyFenceLateResults`
- `snapshotRevisionHighWaterRejectsStaleOrConflictingData`
- `orderedBoundedInputRejectsOverflowWithoutReplayingEvents`
- `unknownFencesInputAndResizeButAllowsExplicitClose`
- `closingViewStopsObservationWithoutClosingProcess`
- `resizeDeduplicatesAndExitRetainsFinalScreen`

用controlled真实接口响应验证controller次数/有界队列/完整身份，不能fixture绕过生产controller。

### PtyTerminalGridTest：4

- `glyphSelectionAndCopyUseNativeOffsets`
- `legacyMappingFallbackIsAsciiOnly`
- `historyAlternateWrappedCopyAndCursorStayConsistent`
- `viewportMetricsClampAndDeduplicateActualSizes`

### TerminalTextInputBridgeTest：4

- `committedUnicodeIsOneTextAndPreeditHasNoWrite`
- `cancelBlurAndOwnerResetDiscardComposition`
- `compositionKeysAndKeyUpCannotDoubleWrite`
- `pasteAndPlatformShortcutsHaveDistinctSemanticActions`

### DesktopTerminalLaunchProviderTest：3

- `realLocalBindingAndExecutableShellPrepareWithoutSpawn`
- `shellFallbackAndEnvironmentAreCapturedWithoutDisclosure`
- `foreignConnectionRelayAndUnsupportedPlatformAreUnavailable`

### PtyTerminalConnectionIntegrationTest：4（真实生产Local/Host/PTY）

- `controllerCreateTextPasteAndKeysReachExactRealPty`
- `typedMappedScreenHistoryAndKernelResizeReachPageModel`
- `hiddenViewReadAndExitRetentionNeverSpawnOrKill`
- `lostAcknowledgementAndOwnerReplacementNeverReplayNativeInput`

fixture复用生产registrar/DefaultSessionApi/JvmSessionTerminalHost/LocalConnection；独立bytes、marker和stty size证据，精确PID及owned workers清理。禁止只mock Connection来声称真实链路通过。

### PtyTerminalPageInteractionTest：6

- `fullHarnessAppOpensReadOnlyAndCreatesOnlyOnRealClick`
- `focusedTypingPasteKeysAndResizeUseRealController`
- `wideAndCompactLightDarkMappedGridRemainUsable`
- `historySelectionCopyAndExitedScreenRemainVisible`
- `unknownUnavailableAndViewCloseHaveHonestActions`
- `ownerSwitchHidesOldTerminalAndPreservesOtherPanels`

真实pointer/焦点/key event/clipboard操作完整HarnessApp；至少首两项用真实生产Local/Host/PTY加Desktop launch provider，临时profile/Session/no-network model fixture可用。截图只在测试中fixture，无生产假数据。10秒有界等待，不增skip、不降低identity/bytes/次数/assertion；避免重复registration的旧夹具错误。所有suspend测试显式runBlocking<Unit>。

截图最小8张：1440×900与600×800 ×浅深×Running/Exited；从完整HarnessApp捕获，保存PNG、geometry/matrix，唯一key可脱敏但state必须真实。另Unavailable/Unknown各至少一个截图（可controlled响应）并独立标明。坐标以window-local统一，不混入菜单栏偏移。原版同状态参考未采集时不能宣称像素一致。

## F. 父线程验收与交付

基线2,238个输入与host-semantic-r2 SHA一致，预计新增15源码文件→2,253输入。保留旧17类104项，新8类36项，总25类140项。另运行既有ThreadPanelsInteractionTest、TerminalReadingInteractionTest、HarnessAppThreadPanelsShellTest和ThreadPanelsStateTest受影响回归，实际计数由冻结XML独立记录，不在未运行前猜总数。完整JVM app build与正式runtime图必须通过；冻结候选内串行运行，worker存活/写集未释放时不编译。

交付 `/tmp/harness-terminal-renderer-page-delivery-20261010.json` 和同名.md：model/provider、contract_sha256/baseline_sha256、22条files（repo-relative path/before_sha256或null/after_sha256）、builds_run/tests_run/screenshots_run/native_execution_run均false、write_set_released/source_writes_stopped均true；固定新增36方法清单及静态检查。写集外完整输入SHA核验，git diff --check限定写集，文档可由父线程继续写但worker不得修改。

工作者写完交付立即退出；不要自行推进其它页面。父线程分别报告build、controller/真实PTY功能、离屏完整页面、原版像素、原生窗口/IME和Remote状态。当前合同不宣称native IME、鼠标终端协议、Blink动画、其它OS或Remote已经完成。整体复刻goal保持active。
