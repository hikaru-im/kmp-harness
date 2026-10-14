# 交互终端真实页面 R3 最小修复交付

仅实施与静态检查完成；动态验收 pending，由父线程在工作者退出、冻结写集后串行执行。

- Model: SHUAIAPI/gpt-6.1-sol
- Provider: opencodex
- contract_sha256: 26cc6eec457c1a51bd4a1a1e73f892070d1754ee75f26fa2650ec2b422cc20b9
- baseline_sha256: 8fdda6a105ce0e28d7e46a6e9fc957c27a26ae4e3e61933db11eaeb91ef397c7
- 唯一源码写集：PtyTerminalConnectionIntegrationTest.kt、PtyTerminalPageInteractionTest.kt。
- 原 22 条 path/before_sha256 保持原 baseline（新增为 null）；仅本两条 after_sha256 更新，其它 20 条保持。
- builds_run / tests_run / screenshots_run / native_execution_run / compiler_run / process_fixtures_started: false。
- write_set_released / source_writes_stopped: true。

## R3 原因与修复

- A: Official connection failures read Files.size(extra.bin) before dd created that file; DRAIN was printed first. Test-only captureScript creates extra.bin before DRAIN, retains real dd/od and all exact-zero/nativeWrites/typed inputs/owner/rebind assertions. The Unknown rebind branch also waits at most 10 seconds for file existence.
- B: First-command echo and shell startup/readline redisplay matched contains markers; LC_ALL=C could edit direct UTF-8 command text. Test-only Desktop provider sets independent ASCII PS1. Every page command waits for the actual prompt in page semantics and native snapshot, writes a unique UTF-8 script and invocation record under fixture.cwd, then submits only the ASCII absolute-path source invocation through focused real page Text plus Enter. The separate typed inputs are asserted and existing fixture shutdown removes the files.
  These observations do not establish a production input bridge defect.
- C: HISTORY_DONE/RUNNING/exit markers could match command echo before actual script output; history was empty. Markers occupy independent CRLF lines and waits compare trimEnd lines, with native snapshot confirmation. History confirms all 80 exact row lines in order and row-00 in native history before original real scrollbar/selection/copy operations. Running and Exited clipboard sentinels/chords/no-writes/no-closes, lifecycle and exit assertions remain.
- D: A 70px navigator drag moved the grid but left 74 columns/38 rows and resize count unchanged because the panel stayed 640px wide. Real pointer drag moves 300px toward the navigator 520px clamp, requiring narrower grid bounds, changed native typed snapshot columns/size and a native resize. Optional captureScript phase after DRAIN reads real page-typed r via dd, records independent od hex 72 and kernel stty size, then continues real dd into extra.bin. Assertions require literal single-byte r, exact new kernel rows/columns, same full key, one create and zero extra bytes; z/Unicode bracketed paste/Up/Ctrl+C literals and typed inputs remain.
- E: Capture metadata lacked actual snapshot identity, revision, size and native lifecycle. The unchanged 8 Running/Exited plus 2 controlled Unknown/Unavailable capture matrix records actual native revision, full scope/session/incarnation/terminal key, expectedHostId, lifecycle and rows/columns. Running/Exited lifecycle assertions precede capture; Unknown explicitly marks ack_controlled and keeps actual native lifecycle; Unavailable snapshot fields are null. Existing matrix fields and entire image/geometry write-and-assert region remain compatible and byte preserved.

## 本轮静态核验

- R3 before 全部 2,253 输入 SHA 在写入前匹配；最终输入重新枚举完全一致。
- 两文件以外 2,251 输入 SHA 全部不变；所有生产文件、其它测试、配置、合同、原 baseline、另列三个前端 YAML 保持。
- 固定新增 36 方法逐项匹配；本两文件方法名字保持，数量为 4 + 6。
- 限定两路径 git diff --check 返回 0；untracked 源码直接检查 UTF-8、LF、尾空白、文本词法和等待上限。
- 既有等待保留 10,000 ms；断言函数数量保留或增加；Running/Exited 复制 chord 与哨兵、shutdown 清理代码字节保持。
- 截图 bitmap 写入与 geometry 完整 bounds、正尺寸、不越界断言区域字节保持。预期 10 图，本工作者未采集。
- 文本检查不代表 Kotlin 编译、类型检查或动态通过。
- 本轮写集外 SHA digest: e1aab92df64b6f42bcf752523d847d959a140fc5ddd2b369c0dbe4a1c6deb508

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
| apps/jvm-app/test/im/hikaru/harness/desktop/terminal/PtyTerminalConnectionIntegrationTest.kt | null | 254ff54d29eb47b1fff01d29f84b255b8288951774528b7f23d84de08c432cdc |
| apps/jvm-app/test/im/hikaru/harness/desktop/PtyTerminalPageInteractionTest.kt | null | 117a2ae7753efd1825352654a4aa4ac43629ad6ca596261a59a3835e9f4cccab |

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

全部正式编译/build、固定 36 方法、旧 Host R2 与受影响回归、真实页面 pointer/chord/clipboard、内核 resize、10 图与 geometry 验收均 pending。native IME 与像素对齐仍未验证。历史证据和冻结材料未修改。
额外交付：/tmp/harness-terminal-renderer-page-repair-r3-delivery-20261010.json。
源码写入已停止，写集已释放；交付后立即退出，不实施下一轮。
