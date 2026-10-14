# 交互终端真实页面 R4 最小修复交付

仅实施与静态检查完成；R4 动态验收 pending，由父线程在工作者退出、冻结写集后独立串行执行。

- Model: SHUAIAPI/gpt-6.1-sol
- Provider: opencodex
- contract_sha256: 26cc6eec457c1a51bd4a1a1e73f892070d1754ee75f26fa2650ec2b422cc20b9
- baseline_sha256: 8fdda6a105ce0e28d7e46a6e9fc957c27a26ae4e3e61933db11eaeb91ef397c7
- 唯一源码写集：PtyTerminalGrid.kt、PtyTerminalPageInteractionTest.kt。
- 原 22 条 path/before_sha256 保持原 baseline（新增为 null）；仅本两条 after_sha256 更新，其它 20 条保持。
- builds_run / tests_run / screenshots_run / native_execution_run / compiler_run / process_fixtures_started: false。
- write_set_released / source_writes_stopped: true。

## R4 原因与修复

- A（生产）：原 onDragStart 将越过 touch slop 的位置作 anchor，父线程 isolated instrumentation 观察到 anchor=end=(line=0,column=10)、computed copy 为空、clipboard null。改用现有 orientationLock=null、三参数 onDragStart 重载，anchor 来自 down.position，初始 end 来自 slopTrigger.position；onDrag 更新 end、consume、真实 focus、tap 清选区、reading identity、scroll 均保持字节。没有自制鼠标协议、测试特例或诊断输出。
- B（页面测试）：leading history/page rows 为 shell source invocation，固定 y=9 不代表 row-00。新增方法内 helper，通过真实 grid Text 行级 trimEnd 与 10 秒有界等待找到独立 literal row-00 中😀；三次选择均重新获取可见行、行索引和实际 grid bounds，以 bounds.height/可见行数定位行中点。从行首拖到 grid 右边界内，保留 press → advance(16) → single move → release。Running 按钮、Running Meta+C、Exited Meta+C 均要求 clipboard.trimEnd()==expectedRows.first()，并保留 row- 与无 NUL 断言。

## 本轮静态核验

- 写入前 R4 before 全部 2,253 输入 SHA 匹配；写入后精确重新枚举 2,253 输入，无新增或缺失。
- 写集外 2,251 输入 SHA 全部保持；合同、原 baseline、其它源码/测试/YAML 与另列三个前端 YAML 保持。
- 原 22 条 path/before_sha256 全部核对原 baseline；本轮只更新两个 after_sha256，其它 20 条交付记录相同。
- 固定 8 类 36 个新增方法逐项匹配合同；页面固定 6 方法名字、数量保持。其它 5 个页面方法与全部既有 helper 字节一致。
- 生产仅既有 drag-start 两行替换；页面三次调用同一真实 pointer helper，每次 press 后恰好一次 move，没有额外 move、controller/model selection 绕过或向 clipboard 注入 expected。
- history 80 行顺序、history 非空、identity/bytes/次数/Unknown/no-retry/lifecycle/cleanup、Running/Exited 哨兵、focus、真实 chord、no nativeWrites/no closes 保持。
- command、input/paste/resize、截图与 geometry 既有代码字节保持；计划 10 图，本轮未运行或采集。
- 限定两路径 git diff --check 返回 0；untracked 直接检查 UTF-8、LF、尾空白、词法分隔符；断言函数数量保留或增加。
- foundation-desktop 1.11.1 本地 JAR 经 Python 只读解析方法描述与注解，Orientation + Function3 重载存在，未加入额外 OptIn；未执行 JVM/编译器或类型检查。
- 本轮写集外 SHA digest（before=after）：2d318009861ebd6b0c827505825830d39754e17f4971a50c309d2d3eaa0df04a。
- 静态核验不代表编译、测试或动态通过。

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
| apps/shared/src/im/hikaru/harness/client/panels/PtyTerminalGrid.kt | null | efc37ddac576eaf4e4acc32083c9ee2151463cc82c1be67308b548990ef3d54b |
| apps/shared/src/im/hikaru/harness/client/panels/TerminalTextInputBridge.kt | null | 4af7c2f64acd9241df3627eee1522f1709513ffd3074d2badfaf63f9f575991a |
| apps/shared/src/im/hikaru/harness/client/panels/PtyTerminalPanel.kt | null | f88cfe3bae428d26ab3b1a901c65485621243bfa7a58c9112304177dd01166a2 |
| apps/jvm-app/src/im/hikaru/harness/desktop/terminal/DesktopTerminalLaunchProvider.kt | null | 5ff837d6aff61465aed07860f88059c26031794a98520d81b14c335a5f6c0055 |
| modules/session-api/test/im/hikaru/harness/session/api/TerminalColumnMappingContractTest.kt | null | da19a709b7579a035666c71e8e99338cd5e04f744175a6be753090bd96f282ff |
| modules/exec-jvm/test/im/hikaru/harness/exec/jvm/JvmTerminalColumnMappingTest.kt | null | 4b9aabd604d0a72f90f9d0cd4639f1e9379ef338a6752193ca6e57acfaa86008 |
| apps/shared/test/im/hikaru/harness/client/panels/PtyTerminalControllerTest.kt | null | 1a1bf5e83f7cee2a01c92336792701d6891523e951df3cbc675181c76ed98556 |
| apps/shared/test/im/hikaru/harness/client/panels/PtyTerminalGridTest.kt | null | 20e2b1e2f8a7a7f05f8ff556ee2510f0b5e59a726515371e4891f2f6067ec796 |
| apps/shared/test/im/hikaru/harness/client/panels/TerminalTextInputBridgeTest.kt | null | 944890fae31e930babf3f5057d4b9fedde12f7471cebeb47fbacbd60ce4a8b29 |
| apps/jvm-app/test/im/hikaru/harness/desktop/terminal/DesktopTerminalLaunchProviderTest.kt | null | af59719ac2645454d5411a7aba998649a3a62a0cc43bb236f8787f27c60d18dc |
| apps/jvm-app/test/im/hikaru/harness/desktop/terminal/PtyTerminalConnectionIntegrationTest.kt | null | 254ff54d29eb47b1fff01d29f84b255b8288951774528b7f23d84de08c432cdc |
| apps/jvm-app/test/im/hikaru/harness/desktop/PtyTerminalPageInteractionTest.kt | null | 1cb6746f92750208e7656e8f216200a30bd06fb34bdec2371e78a182f473b1db |

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

## 动态验收与交接

父线程 R3 official 历史证据为 8 类 36 项、35 通过 1 failure；后 21 类及 build 未运行。diagnostic-copy-observe 独立于 official，不计入正式验收；本轮没有运行任何动态项目。R4 编译/build、固定 36 方法、既有回归、真实页面 pointer/chord/clipboard、内核 resize、10 图和 geometry 全部 pending。native IME 与原版像素对齐仍未验证。历史证据、合同、基线与父验证脚本未修改。

额外交付：/tmp/harness-terminal-renderer-page-repair-r4-delivery-20261010.json。
源码写入已停止，写集已释放；交付后立即退出，不实施下一轮。
