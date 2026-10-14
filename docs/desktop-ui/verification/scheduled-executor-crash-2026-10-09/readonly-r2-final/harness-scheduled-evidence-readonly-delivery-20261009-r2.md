# 定时任务证据只读修复 R2 交付

- status=implemented；revision=r2
- model=SHUAIAPI/gpt-6.1-sol；actual_model=gpt-6.1-sol；provider=opencodex
- tests_run=false；builds_run=false；write_set_released=true；source_writes_stopped=true

## 本轮 fixture 修复

仅修改 DesktopScheduledSessionEvidenceReadOnlyTest.kt 的 Fixture。真实 SessionPersistencePlugin 的 factory 使用原 JVM Room factory，捕获当前 SessionDatabaseAccess；rows() 复用活跃 owner 的真实 DAO。没有活跃 owner 时才临时打开，并在 finally 关闭。Runtime dispose 先关闭真实数据库，再按身份清除引用；重启后使用新 owner。生产 registry 及其重复打开保护保持。

- 本轮单文件 before SHA256: `57dc8f97e4dd72fc6ee53ee3a812fb1f7e2a9ed3ac948e33a54b3d1e143566a9`
- 本轮单文件 after SHA256: `e732b1b15e68cf7f64356399faaf70ce758222ed49cd79ea7b06f602761d23ef`
- 10 个测试正文和所有断言、清理保持；本轮新增测试 0 项，原新增测试总数仍为 2 类、19 项。

## Initial 唯一基线

- candidate: `/tmp/harness-scheduled-executor-crash-parent-candidate-20261009-initial`
- baseline_manifest: `/tmp/harness-scheduled-evidence-readonly-baseline-20261009/source-manifest.json`
- manifest SHA256: `ad1c5bcfc1d1939e9ed07e1583bcff985e72519a0ffa96ee9659affb0fd255d5`
- inputs: 2211

## 五文件完整 SHA256（before 均对 initial）

| 文件 | before SHA256 | after SHA256 |
| --- | --- | --- |
| `modules/session-persistence/src/im/hikaru/harness/session/persistence/RoomSessionPersistence.kt` | `e630a66a50ae27796ca2e422e863321729b4f5bc9bb0849d3a86a8ef28e5b737` | `d8e8b7aff7ee49e0da1253dc48a758065f846f661b409c2bee3f8c04c0b48bfd` |
| `apps/jvm-app/src/im/hikaru/harness/desktop/scheduling/DesktopScheduledSessionEvidence.kt` | `7d2153a9e13b91793d4222760c1fa0db7de1b64ad2746755700e655f619377aa` | `85bbfb57170f53b79732f2820fa11e4cf65b0f53d4abaf1df673d4247e004142` |
| `modules/session-persistence/test/im/hikaru/harness/session/persistence/SessionPersistenceReadOnlyEventsTest.kt` | `null (new file)` | `67256a97ade43f5213a144ff68c0a38b5dce5c51ddea14e3685e4cab00c1b904` |
| `apps/jvm-app/test/im/hikaru/harness/desktop/scheduling/DesktopScheduledSessionEvidenceReadOnlyTest.kt` | `null (new file)` | `e732b1b15e68cf7f64356399faaf70ce758222ed49cd79ea7b06f602761d23ef` |
| `apps/jvm-app/test/im/hikaru/harness/desktop/scheduling/DesktopScheduledExecutorCrashProcessTest.kt` | `2479d86ec46dcbb155aa720be228634098c99e2d1e6c831988b38479468e5725` | `75bc4036b326b174bdc06584f4bd0c03e568be38b031349b44604d3658cc5261` |

## 两新类实际测试名称与数量


### SessionPersistenceReadOnlyEventsTest (9)

- `repeatedOpenTurnReadsDoNotRepairRowsLoadSessionsOrPublishLifecycle`
- `durableCompletedInterruptedAndIgnorableEventsRemainReadableSnapshots`
- `defaultCapabilityMissingSessionEmptyLogAndDisposedAreDistinct`
- `malformedNoncontiguousAndIllegalEnvelopesFailWithoutChangingRoom`
- `mismatchedHeaderIdIsRejectedWithoutLoadingOrWriting`
- `daoCancellationPropagatesAndOtherReadFailuresKeepTheirCause`
- `cancellationWhileWaitingForDaoDoesNotPublishASnapshot`
- `cancelledReadWaitingForRealFlushCannotEscapeItsMutexOrCommit`
- `cancellationAfterDaoReturnsCannotPublishValidatedEvents`

### DesktopScheduledSessionEvidenceReadOnlyTest (10)

- `repeatedHistoricalOpenTurnInspectionIsPendingAndDoesNotRepairRoomOrLoad`
- `historicalCompletedAndOldInterruptedRequireTheExactDurableInput`
- `unsupportedReadCapabilityIsUnavailableAndNeverFallsBackToLoad`
- `liveOpenOrWrongInputHasNoFlushAndNoDurableRead`
- `liveExactTerminalIsReallyFlushedBeforeDurableEvidenceIsRead`
- `inMemoryTerminalCannotReplaceMissingDurableTerminalAfterFlushReturns`
- `flushAndHistoricalOrLiveReadFailuresAreUnavailableWithoutLoadFallback`
- `flushAndReadCancellationArePropagatedInsteadOfUnavailable`
- `cancellingSuspendedHistoricalReadCannotReturnAResultOrLoadHistory`
- `cancellationAfterReadAndBeforePendingInspectionCannotReturnSuccess`

## 静态核对与验收状态

单文件本轮 before 与 readonly-r1-failed 保存副本一致；静态逐字比较证明 Fixture 外全文未改，其余四文件 SHA 与 R1 交付一致。五文件 initial before 和 manifest SHA 已核对。限定 git diff --check 通过；untracked 文件 no-index --check 返回 1 且无任何空白诊断。

父线程报告 readonly-r1 crash 5/5、readEvents 9/9；evidence 10 项均因旧 fixture 的重复 owner 打开失败，未达到完整业务断言，其余类未运行。失败历史保留。本工作者未运行任何构建或测试，不声明 R2 验收通过。

父线程冻结后串行验证 evidence 10、readEvents 9、crash 5，完成原 48 项及普通 load 7、header 10、factory 1 回归。R2 编译和运行行为仍待验证。

五文件写集已释放；停止全部源码与交付写入。
