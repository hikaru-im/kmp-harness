# 本机 Cron 启用交付

状态：implemented；四文件写集已释放，源码写入已停止。

- model: `SHUAIAPI/gpt-6.1-sol`；actual_model: `gpt-6.1-sol`；provider: `opencodex`。
- `tests_run=false`、`builds_run=false`、`write_set_released=true`、`source_writes_stopped=true`。
- 基线：`/tmp/harness-scheduled-executor-crash-parent-candidate-20261009-readonly-r2`。
- manifest：`/tmp/harness-scheduled-cron-page-enable-baseline-20261009/source-manifest.json`，2,213 输入。

## 四文件 SHA256

| 文件 | before | after |
| --- | --- | --- |
| `apps/jvm-app/src/main.kt` | `bdf34f9df06cd92b8197de24a6169d129a1db3d70874aa2d6d5acc8f9015125f` | `d3ebfc8caf738d5ffa7047522d44d7db7766acfb862209c3389ab9601c9127a5` |
| `apps/jvm-app/src/im/hikaru/harness/desktop/scheduling/DesktopScheduledExecution.kt` | `null` | `a834443b75675e95cc96d185fcf53b2a20fe2a58c655d0cdca60b27d16825357` |
| `apps/jvm-app/test/im/hikaru/harness/desktop/ScheduledCronPageIntegrationTest.kt` | `null` | `425b7d1e84641954ffa7cb02176ef32f0cee261a805a842bad3136ba30ca71cb` |
| `apps/jvm-app/test/im/hikaru/harness/desktop/scheduling/DesktopScheduledExecutionTest.kt` | `null` | `e7e9c5e10914703cc85356d2e0dc8eb3e70aed94aeeb9c31a0bda2593d706b3c` |

## 实现与静态修正

- DesktopScheduledExecution is shared by main and both suites; it keeps the exact LocalConnection, real Context/store/calendar/evidence and OS leader lease.
- The start entry checks successful host.describe data, expected HostId and protocol major; invalid descriptions permanently invalidate the owner, and cancellation propagates.
- main explicitly enables local verified Cron and passes the same execution instance components and recovery capability to HarnessApp. The config default remains false, and existing Unsafe/Drained shutdown resource ordering is retained.
- The UI fixture uses temporary JSON/Room, Runtime, Session/Agent/AgentLoop, real gateway Host and Session services, LocalConnection and the real desktop model catalog. Only model text/completion gate and clock are controlled.
- Normal creation, model/rule choice, run, activation, pause, navigation and history access use measured-root mouse/keyboard/wheel input. Thread verification checks persisted message IDs and full text, then physically exposes the answer with wheel input.
- Assembly coverage includes HostId/protocol refusal, cancellation, default-disabled admission/recovery, and an open provider shutdown retaining Runtime/lease until exact persisted terminal evidence allows Drained and a new owner.
- The completed row assertion matches the actual Chinese title in the tagged run subtree.
- Client history Message.id is a String, and the exact input and answer content are asserted.
- Compact navigation uses the measured bottom-nav-drawer control, avoiding the title toggle covered by the floating drawer.
- Thread lazy content is exposed through real wheel input with measured viewport/control geometry and a ten-second deadline.

## 新增测试源码：2 类、10 项

### im.hikaru.harness.desktop.ScheduledCronPageIntegrationTest（6）

- `realPageCreatesPausedRunsOnceAndOpensPersistedThread`
- `realPageActivationDueOccurrenceAndPausePersistWithoutFutureDelivery`
- `leavingPageRetainsApplicationOwnerAndOpenTurnWithoutDuplicateDelivery`
- `losingVerifiedBindingPermanentlyFencesOldOwnerEvenAfterConnectionReturns`
- `sameHostReplacementConnectionCannotBorrowPreviousOwnerExecutionCapability`
- `capturesTwelveRealPausedAcceptedCompletedPagesAndReachableThreadHistory`

### im.hikaru.harness.desktop.scheduling.DesktopScheduledExecutionTest（4）

- `realGatewayHostAndProtocolMismatchRefuseAdmissionAndNeverAcquireLease`
- `cancellationFromRealGatewayHostServicePropagatesWithoutStartingOwner`
- `defaultDisabledConfigurationRefusesRunActivationAndRecoveryWithRealStore`
- `openProviderShutdownRetainsRuntimeAndLeaseUntilExactTerminalThenNewOwnerStarts`

## 截图源码与静态检查

- 矩阵：320×600 / 1440×900 × Light / Dark × Paused / Accepted / Completed，共 12 PNG；本工作者生成 0 张。
- `HARNESS_SCHEDULED_CRON_CAPTURE_DIR` 指定归档目录，未设置时测试写入自身临时目录；不跳过矩阵。
- 源码断言实际尺寸、PNG SHA、fresh JSON 状态、精确终态、滚动终点与完整控制几何；打开真实 Thread 并检查完整 prompt/answer。
- 限四文件 `git diff --check` 无输出；三个新文件另以 `git diff --no-index --check /dev/null <file>` 检查，均无空白错误。后者 exit 1 表示新增差异。
- 已核对保存的 main 基线 SHA 和 manifest；相对该基线的改动限于装配/启用及同源 UI 接线，关闭顺序保持。

## 父线程验收及未决限制

Freeze the same full input manifest and run the two new classes serially, followed by the previously accepted 85 tests and existing Scheduled page routing/CRUD/screenshots and owner recovery tests named in the contract. Preserve per-class XML and logs and check exact JUnit names/counts and zero failure/error/skip.

- Compilation and runtime behavior have not been verified by this worker; the parent owns frozen serial validation.
- All 12 PNG captures and their geometry assertions are implemented as test source and have not been generated or visually reviewed.
- Headless Harness captures will provide offscreen layout evidence; they do not prove original-app pixel parity, a native window, relay execution or execution after application exit.
