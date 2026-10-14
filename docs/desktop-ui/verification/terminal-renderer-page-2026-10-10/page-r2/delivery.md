# 交互终端真实页面 R2 最小修复交付

实施及静态检查完成；全部动态验收 pending，由父线程在本工作者退出后执行。

- Model: SHUAIAPI/gpt-6.1-sol
- Provider: opencodex
- contract_sha256: 26cc6eec457c1a51bd4a1a1e73f892070d1754ee75f26fa2650ec2b422cc20b9
- baseline_sha256: 8fdda6a105ce0e28d7e46a6e9fc957c27a26ae4e3e61933db11eaeb91ef397c7
- 本轮唯一源码写集为四个指定新测试文件；原 22 文件清单及全部 before_sha256 保持原 baseline，仅四条 after_sha256 更新。
- builds_run / tests_run / screenshots_run / native_execution_run / compiler_run / process_fixtures_started: false。
- write_set_released / source_writes_stopped: true。

## 修复原因与内容

- A: Parent R1 execution observed jediterm 3.76 converting ANSI 256-color index 196 to RGB red. Only the independent literal foreground expectation becomes Rgb(255, 0, 0); bold, background, offsets and history assertions remain.
- B: bind(next) resets visible=false; the previous test timed out waiting for the selected snapshot. Release the old gate, verify replacement inventory and absent old snapshot, then explicitly setVisible(true). Distinct new-owner text and full key assertions strengthen replacement evidence; foreign-incarnation error checks remain.
- C-waits: ComposeUiTest waitUntil first positional argument is String? in the parent dependency API. Exactly 13 existing waits now use timeoutMillis = 10_000, preserving every predicate and bound.
- C-session: SessionId is an inline value class and cannot be lateinit. Use private nullable initializedSession plus a checked non-null SessionId getter; initialize from the real agent session once during start. No fake session sentinel.
- D: Prior Meta+C could retain clipboard text from the copy button, and Exited copy had no chord evidence. Before each real Meta+C, pointer-select history, assert input focus, set a distinct clipboard sentinel, and verify fresh row- content with no NUL and unchanged nativeWrites. After Exited(0), scroll and select again, repeat the real chord, and retain unchanged closes plus zero-close assertion.

## 本轮静态核验

- 2,253 输入重新枚举完全一致，无新增或缺失；四文件之外 2,249 输入 SHA 不变。
- 原交付另外 18 条文件记录完全不变；合同、原 baseline、另列三个前端 YAML SHA 不变。
- 本轮写集外 SHA digest: ab39c90da02e71e660103809b71a3cd5771689bc0bc2205ba05391d4af1a4b96
- 当前全部输入 SHA digest: 7125eacf865480a545e18dbefc4790e9778929c0e9fc3b483b02f0fa8a5a973a
- 固定新增八类 36 个方法名字与数量逐项保持；四个修改文件方法数为 4 / 8 / 4 / 6，旧测试源码保持字节。
- 限定四路径 git diff --check 返回 0；untracked 四文件另外直接检查 UTF-8、LF、尾空白及文本词法。文本检查不代表 Kotlin 类型检查或编译成功。
- 13 个等待均保留 10,000 ms；旧身份、bytes、次数、清理断言保留，替换身份及复制断言增强。
- capture helper、截图写入及 geometry 断言逐字节不变；预期 10 张截图，本工作者未采集。可选 matrix 元数据扩展未实施。

## 原 22 文件 SHA

| 路径 | before_sha256（原 baseline） | after_sha256 |
| --- | --- | --- |
| modules/session-api/src/im/hikaru/harness/session/api/SessionTerminals.kt | 1144cc4ea3a90b1d9af40a34064e7d461d4af9a743f53ea1de5be1f7fc333455 | f1ca5feb1090a5d69876540e57e022f01787eafa387d89e26c5847368536f875 |
| apps/shared/src/im/hikaru/harness/client/connection/SessionTerminals.kt | 0934bde12cbb402d170e513fb388d23face0fd39760c4e6c7b246056a9c3c070 | 5baf137b076cc0f2de306a4b3eb59e50562ed2629c14d16020abcf6fa70a920f |
| modules/exec-jvm/src/im/hikaru/harness/exec/jvm/JvmPtyTerminalSessions.kt | 36eb35ec97ceee38e49296671ad1dc0ffc431643c781dd646fc8a5815aaf6092 | d016563e905c21934ab8bf42764959a205ffdde06867d4863284b29dc83c01d0 |
| apps/jvm-app/src/im/hikaru/harness/desktop/connection/LocalSessionTerminals.kt | 11025fce0795610f0ccca6e16223af733acc64d6f1d5d68aed2c3581dd283202 | e2e93b418f762cf681c8502700e8616a483a9a4b950bb8e4107674951bf3fa55 |
| apps/shared/src/im/hikaru/harness/client/panels/ThreadPanels.kt | d02e68e1c8bd4d2c9bc8d44375f2a4fc60ecfcbba3bac3eb36d5423244ccd5a4 | 2487b0b41651cb20d6f4f2f4394c6b620e19fd4534336626689ffb5a12952355 |
| apps/shared/src/im/hikaru/harness/client/app/HarnessApp.kt | 1a8c82adee7786dce3e254cd36969e95566de850753b4ef5f5781c0d1fe3ce5b | 6cb22a31e582f06654680414a64a104b492d252e37e0faef34c3ec43c957087d |
| apps/jvm-app/src/main.kt | d3ebfc8caf738d5ffa7047522d44d7db7766acfb862209c3389ab9601c9127a5 | d39aa62167cf3330e1bb0ce84db8aa281e0805087215575add0a3f2189e7e66d |
| apps/shared/src/im/hikaru/harness/client/panels/TerminalLaunchProvider.kt | null | 152cd3a62195095a242e5bdb7bdef558e6bf7969d47cb5e4990c05482129f393 |
| apps/shared/src/im/hikaru/harness/client/panels/PtyTerminalModels.kt | null | a6109c5b8e80fd8c916b8b434527264e3d29d6e958a059750ae3814985dc9c32 |
| apps/shared/src/im/hikaru/harness/client/panels/PtyTerminalController.kt | null | 50e9fb2ad0768f026df4142018931877e73876a0d97621d8c970074acfe1fc6e |
| apps/shared/src/im/hikaru/harness/client/panels/PtyTerminalGrid.kt | null | c84763293cb6dcc500fd8b1e2e9cb81a1ce102101bc21ed49ea8c75926eb4f78 |
| apps/shared/src/im/hikaru/harness/client/panels/TerminalTextInputBridge.kt | null | 4af7c2f64acd9241df3627eee1522f1709513ffd3074d2badfaf63f9f575991a |
| apps/shared/src/im/hikaru/harness/client/panels/PtyTerminalPanel.kt | null | f88cfe3bae428d26ab3b1a901c65485621243bfa7a58c9112304177dd01166a2 |
| apps/jvm-app/src/im/hikaru/harness/desktop/terminal/DesktopTerminalLaunchProvider.kt | null | 5ff837d6aff61465aed07860f88059c26031794a98520d81b14c335a5f6c0055 |
| modules/session-api/test/im/hikaru/harness/session/api/TerminalColumnMappingContractTest.kt | null | da19a709b7579a035666c71e8e99338cd5e04f744175a6be753090bd96f282ff |
| modules/exec-jvm/test/im/hikaru/harness/exec/jvm/JvmTerminalColumnMappingTest.kt | null | 4b9aabd604d0a72f90f9d0cd4639f1e9379ef338a6752193ca6e57acfaa86008 |
| apps/shared/test/im/hikaru/harness/client/panels/PtyTerminalControllerTest.kt | null | 1a1bf5e83f7cee2a01c92336792701d6891523e951df3cbc675181c76ed98556 |
| apps/shared/test/im/hikaru/harness/client/panels/PtyTerminalGridTest.kt | null | 20e2b1e2f8a7a7f05f8ff556ee2510f0b5e59a726515371e4891f2f6067ec796 |
| apps/shared/test/im/hikaru/harness/client/panels/TerminalTextInputBridgeTest.kt | null | 944890fae31e930babf3f5057d4b9fedde12f7471cebeb47fbacbd60ce4a8b29 |
| apps/jvm-app/test/im/hikaru/harness/desktop/terminal/DesktopTerminalLaunchProviderTest.kt | null | af59719ac2645454d5411a7aba998649a3a62a0cc43bb236f8787f27c60d18dc |
| apps/jvm-app/test/im/hikaru/harness/desktop/terminal/PtyTerminalConnectionIntegrationTest.kt | null | bc9715db5af275eae82f30536fc4d26ccbe967bf95a20dd876bb7336cfc6716c |
| apps/jvm-app/test/im/hikaru/harness/desktop/PtyTerminalPageInteractionTest.kt | null | 5aa8463c2b9499348a363f9c3db0fe6b6a0c262686de67ffc2502376df55ac62 |

## 固定新增 36 方法（本轮未运行）

### TerminalColumnMappingContractTest (3)

- mappingInvariantsAndLegacyJsonRemainCompatible
- wideContinuationAndSurrogateOffsetsRetainNativeColumns
- mappingCollectionsAreDetachedAcrossSnapshots

### JvmTerminalColumnMappingTest (4)

- realParserMapsAsciiCjkEmojiAndNulToNativeColumns
- realStylesHistoryAndLeadingContinuationKeepOffsets
- realResizeTruncationPreservesNativeSpansAndValidUnicode
- realMappedSnapshotsRemainDetachedAndCleanupIsOwned

### PtyTerminalControllerTest (8)

- openingRefreshingAndRebindingNeverCreateTerminal
- explicitCreateDispatchesOnceAndUnknownDoesNotRetry
- ownerConnectionAndFullKeyFenceLateResults
- snapshotRevisionHighWaterRejectsStaleOrConflictingData
- orderedBoundedInputRejectsOverflowWithoutReplayingEvents
- unknownFencesInputAndResizeButAllowsExplicitClose
- closingViewStopsObservationWithoutClosingProcess
- resizeDeduplicatesAndExitRetainsFinalScreen

### PtyTerminalGridTest (4)

- glyphSelectionAndCopyUseNativeOffsets
- legacyMappingFallbackIsAsciiOnly
- historyAlternateWrappedCopyAndCursorStayConsistent
- viewportMetricsClampAndDeduplicateActualSizes

### TerminalTextInputBridgeTest (4)

- committedUnicodeIsOneTextAndPreeditHasNoWrite
- cancelBlurAndOwnerResetDiscardComposition
- compositionKeysAndKeyUpCannotDoubleWrite
- pasteAndPlatformShortcutsHaveDistinctSemanticActions

### DesktopTerminalLaunchProviderTest (3)

- realLocalBindingAndExecutableShellPrepareWithoutSpawn
- shellFallbackAndEnvironmentAreCapturedWithoutDisclosure
- foreignConnectionRelayAndUnsupportedPlatformAreUnavailable

### PtyTerminalConnectionIntegrationTest (4)

- controllerCreateTextPasteAndKeysReachExactRealPty
- typedMappedScreenHistoryAndKernelResizeReachPageModel
- hiddenViewReadAndExitRetentionNeverSpawnOrKill
- lostAcknowledgementAndOwnerReplacementNeverReplayNativeInput

### PtyTerminalPageInteractionTest (6)

- fullHarnessAppOpensReadOnlyAndCreatesOnlyOnRealClick
- focusedTypingPasteKeysAndResizeUseRealController
- wideAndCompactLightDarkMappedGridRemainUsable
- historySelectionCopyAndExitedScreenRemainVisible
- unknownUnavailableAndViewCloseHaveHonestActions
- ownerSwitchHidesOldTerminalAndPreservesOtherPanels

## 动态验收与限制

完整 JVM app 编译/build、正式 runtime 图、36 方法、旧 Host R2 与受影响回归、真实页面 pointer/chord/clipboard、截图及 geometry 的动态验收全部 pending。本轮没有运行编译器、应用、测试、进程夹具或截图，也没有改生产、配置、合同、基线、父验收脚本或旧冻结/失败目录。

源码写入已停止，写集已释放；交付后立即退出。
