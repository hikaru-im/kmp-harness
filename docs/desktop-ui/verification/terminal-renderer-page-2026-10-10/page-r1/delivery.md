# 交互终端真实页面 R1 实施交付

状态：实施完成，仅静态检查；等待父线程独立审查、冻结、串行构建与测试。

- Model: SHUAIAPI/gpt-6.1-sol
- Provider: opencodex
- contract_sha256: 26cc6eec457c1a51bd4a1a1e73f892070d1754ee75f26fa2650ec2b422cc20b9
- baseline_sha256: 8fdda6a105ce0e28d7e46a6e9fc957c27a26ae4e3e61933db11eaeb91ef397c7
- 唯一写集：22 文件，修改 7、新增 15；输入 2238 → 2253。
- 写集外 2231 个基线输入及另列 3 个前端 YAML 的 SHA 全部保持一致。
- builds_run / tests_run / screenshots_run / native_execution_run: false。
- compiler_run / process_fixtures_started: false。
- write_set_released / source_writes_stopped: true。

## 实施与静态审查

已审查 core/client 列映射、Local 深复制、Compose 实际依赖接口、单一顺序 dispatcher、owner/key/epoch/revision 校验、Unknown fence、生命周期和正式 main 的生产注入。既有终端 tab 默认后台输出，交互终端为内部子视图；只有显式新建才启动 shell，隐藏视图不会关闭进程。生产代码没有 fixture/mock 注入。

22 文件 UTF-8、嵌套注释/字符串插值、定界符及转义的文本词法核对通过；限定 22 路径的 git diff --check 返回 0。词法检查不代表 Kotlin 类型检查或编译通过。36 个固定 @Test 方法名逐项匹配，无 disabled/skip 标记。

Compose 1.11.1 接口依据父线程基线所用依赖 class metadata 和既有源码静态核对；没有执行编译器。真实测试夹具使用生产 registrar、DefaultSessionApi、Host 与 LocalConnection，Session/Host gateway 各注册一次；尚未执行。

## 22 文件 SHA

| 路径 | before_sha256 | after_sha256 |
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
| modules/exec-jvm/test/im/hikaru/harness/exec/jvm/JvmTerminalColumnMappingTest.kt | null | 7f699501753b3f3a3f093f27b36e24287fc13982e00692ac377a91ef032a113c |
| apps/shared/test/im/hikaru/harness/client/panels/PtyTerminalControllerTest.kt | null | 0789b8d18061953a2c1fb03a28300be6ce79cd5852aca40ec151b1dfc9860609 |
| apps/shared/test/im/hikaru/harness/client/panels/PtyTerminalGridTest.kt | null | 20e2b1e2f8a7a7f05f8ff556ee2510f0b5e59a726515371e4891f2f6067ec796 |
| apps/shared/test/im/hikaru/harness/client/panels/TerminalTextInputBridgeTest.kt | null | 944890fae31e930babf3f5057d4b9fedde12f7471cebeb47fbacbd60ce4a8b29 |
| apps/jvm-app/test/im/hikaru/harness/desktop/terminal/DesktopTerminalLaunchProviderTest.kt | null | af59719ac2645454d5411a7aba998649a3a62a0cc43bb236f8787f27c60d18dc |
| apps/jvm-app/test/im/hikaru/harness/desktop/terminal/PtyTerminalConnectionIntegrationTest.kt | null | 03dc40a9d3043b91658ce1a488ceb62047baee15558d8b9f34ae5eca4bc2f8cc |
| apps/jvm-app/test/im/hikaru/harness/desktop/PtyTerminalPageInteractionTest.kt | null | 721441a74a55473457952e7ec7e7b9a563ab90f616cf0ae9bf1d6298e25668c8 |

## 全部 36 个固定测试方法（未执行）

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

## 后续验证边界

父线程需独立冻结后串行验证完整 JVM app build、正式 runtime 图、固定 36 方法、既有 Host R2 与受影响回归；实际测试计数以冻结 XML 为准。测试源码准备了完整 HarnessApp 的 8 张 Running/Exited 矩阵截图及各 1 张 controlled Unknown/Unavailable 截图，当前未采集。

真实 Local/Host/PTY 功能、Compose 操作、原生窗口/IME 和原版像素一致性均未获得本批运行验证。DWC-only、leading continuation 和孤立 surrogate 的部分边界为明确标注的模型补充，不声称全部 native 覆盖。Blink 为静态可见；其它 OS、Remote 和终端鼠标协议不在本合同完成范围。

源码写入已停止，写集已释放；本工作者交付后立即退出。
