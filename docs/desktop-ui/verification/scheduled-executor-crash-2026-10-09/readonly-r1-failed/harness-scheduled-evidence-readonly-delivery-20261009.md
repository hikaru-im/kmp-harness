# 定时任务持久证据只读修复交付

- status=implemented
- model=SHUAIAPI/gpt-6.1-sol；actual_model=gpt-6.1-sol；provider=opencodex
- tests_run=false；builds_run=false；write_set_released=true
- 五文件写集已释放；停止源码与交付写入。

## 基线

- candidate: `/tmp/harness-scheduled-executor-crash-parent-candidate-20261009-initial`
- baseline_manifest: `/tmp/harness-scheduled-evidence-readonly-baseline-20261009/source-manifest.json`
- manifest SHA256: `ad1c5bcfc1d1939e9ed07e1583bcff985e72519a0ffa96ee9659affb0fd255d5`
- inputs: 2211

## 实施

Room 新增默认 nullable 的 readEvents 能力，在现有 mutex 中验证持久快照并检查取消；不会恢复 Session、补写开放回合、flush 或发布生命周期。普通 load 恢复语义保持。

历史 evidence 直接只读持久事件，禁止 load fallback；live 无精确终态候选时保持 Pending，有候选才真实 flush 后重读持久证据。缺能力或失败返回 Unavailable；取消原样传播。精确终态提取保持。

CrashProcessTest 只将 Agent 计数与 created 对应并注明 create 语义，保留其他断言。未知事件 JSON fixture 已修正为合法 Kotlin 原始字符串。

## 五文件 SHA256

| 文件 | before SHA256 | after SHA256 |
| --- | --- | --- |
| `modules/session-persistence/src/im/hikaru/harness/session/persistence/RoomSessionPersistence.kt` | `e630a66a50ae27796ca2e422e863321729b4f5bc9bb0849d3a86a8ef28e5b737` | `d8e8b7aff7ee49e0da1253dc48a758065f846f661b409c2bee3f8c04c0b48bfd` |
| `apps/jvm-app/src/im/hikaru/harness/desktop/scheduling/DesktopScheduledSessionEvidence.kt` | `7d2153a9e13b91793d4222760c1fa0db7de1b64ad2746755700e655f619377aa` | `85bbfb57170f53b79732f2820fa11e4cf65b0f53d4abaf1df673d4247e004142` |
| `modules/session-persistence/test/im/hikaru/harness/session/persistence/SessionPersistenceReadOnlyEventsTest.kt` | `null (new file)` | `67256a97ade43f5213a144ff68c0a38b5dce5c51ddea14e3685e4cab00c1b904` |
| `apps/jvm-app/test/im/hikaru/harness/desktop/scheduling/DesktopScheduledSessionEvidenceReadOnlyTest.kt` | `null (new file)` | `57dc8f97e4dd72fc6ee53ee3a812fb1f7e2a9ed3ac948e33a54b3d1e143566a9` |
| `apps/jvm-app/test/im/hikaru/harness/desktop/scheduling/DesktopScheduledExecutorCrashProcessTest.kt` | `2479d86ec46dcbb155aa720be228634098c99e2d1e6c831988b38479468e5725` | `75bc4036b326b174bdc06584f4bd0c03e568be38b031349b44604d3658cc5261` |

## 新增测试源码

共新增 2 类、19 项；全部未运行。

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

## 静态核对与父线程验收

三个既有文件 before SHA 与唯一基线一致，完整 manifest SHA 已实际核对。限定 git diff --check 通过；untracked 文件使用 /dev/null 的 no-index --check，exit=1 且无任何空白诊断。生产基线 diff、两新类源码及实际测试名称数量已静态复核。

父线程保留的 initial crash 结果为 3/5、失败 2；本轮没有运行 build/test，不声明验收通过。冻结后请串行运行两新类 19 项、原 48 项（含 DesktopScheduledExecutorCrashProcessTest 五窗口），并回归普通 load 7、header 10、factory 1 项。

尚未验证：编译和运行行为，均由父线程冻结后验证。
