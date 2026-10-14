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
| `apps/jvm-app/test/im/hikaru/harness/desktop/ScheduledCronPageIntegrationTest.kt` | `null` | `44b21ac2ccfaaf78c4c5815a69ea55ddcb438e5751a0dd190d8723d63eaebfb6` |
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

- 矩阵：600×800 / 1440×900 × Light / Dark × Paused / Accepted / Completed，共 12 PNG；本工作者生成 0 张。
- `HARNESS_SCHEDULED_CRON_CAPTURE_DIR` 指定归档目录，未设置时测试写入自身临时目录；不跳过矩阵。
- 源码断言实际尺寸、PNG SHA、fresh JSON 状态、精确终态、滚动终点与完整控制几何；打开真实 Thread 并检查完整 prompt/answer。
- 限四文件 `git diff --check` 无输出；三个新文件另以 `git diff --no-index --check /dev/null <file>` 检查，均无空白错误。后者 exit 1 表示新增差异。
- 已核对保存的 main 基线 SHA 和 manifest；相对该基线的改动限于装配/启用及同源 UI 接线，关闭顺序保持。

## 父线程验收及未决限制

Freeze the same full input manifest and run the two new classes serially, followed by the previously accepted 85 tests and existing Scheduled page routing/CRUD/screenshots and owner recovery tests named in the contract. Preserve per-class XML and logs and check exact JUnit names/counts and zero failure/error/skip.

- Compilation and runtime behavior have not been verified by this worker; the parent owns frozen serial validation.
- All 12 PNG captures and their geometry assertions are implemented as test source and have not been generated or visually reviewed.
- Headless Harness captures will provide offscreen layout evidence; they do not prove original-app pixel parity, a native window, relay execution or execution after application exit.

## 本次紧凑尺寸窄修复

- 本次源码写集仅 `apps/jvm-app/test/im/hikaru/harness/desktop/ScheduledCronPageIntegrationTest.kt`；其余三个文件 SHA 与原交付一致。四文件原始 before SHA 保留。
- 默认 fixture 为 600×800；截图尺寸为 `[[600,800],[1440,900]]`，浅深色及 paused/accepted/completed 阶段仍共 12 PNG。
- 600dp 下使用真实底栏 home/more；离页返回时以现有真实鼠标 helper 展开并收起任务抽屉。既有真实键盘/滚轮、完整可见几何、run/input 身份和精确终态断言保留。
- 本次新增测试 0 项；原 2 类 10 项名称及断言保留；未生成 PNG，未构建或执行测试。
- 本次限定该文件 `git diff --check` 返回 0；新增文件 `git diff --no-index --check /dev/null <file>` 返回 1，均无空白诊断。
- `tests_run=false`、`builds_run=false`、`write_set_released=true`、`source_writes_stopped=true`。源码与交付写入在本次报告完成后停止。

## Cron 页面 R2 测试编译窄修复

- 仅修改 `ScheduledCronPageIntegrationTest.kt`：11 处 `waitUntil(10_000)` 改为 `waitUntil(timeoutMillis = 10_000)`，补充 `DesktopClientEntries` 与 `DesktopClientPluginCatalog` 的正式 imports。所有原条件及 10 秒期限保持。
- 反向还原上述 13 处修改后的 SHA 与 R1 文件 SHA 完全一致；业务/几何断言、真实鼠标键盘滚轮、6+4 共 10 项测试、12 PNG 的 600×800 / 1440×900 浅深色矩阵均保留。
- 四文件原始 before SHA 与原基线保持；其余三个文件 after SHA 不变。顶部表格为本轮最新 after SHA；JSON repair_history 保留上一轮 SHA 与修复证据。
- 父线程 `cron-page-r1` 已完成生产编译，但测试编译因 13 条错误失败，实际运行 0 项、生成 0 PNG。本轮修复尚待父线程冻结 R2 验收；本工作者未运行构建或测试。
- 日志：`/tmp/harness-pages-verified-20261009/scheduled-cron-page-enable/cron-page-r1/ScheduledCronPageIntegrationTest/command.log`。
- 本轮限定文件 `git diff --check` 返回 0，`git diff --no-index --check /dev/null <file>` 返回 1；均无空白诊断。
- `tests_run=false`、`builds_run=false`、`write_set_released=true`、`source_writes_stopped=true`。源码和交付写入已在本报告完成后停止。


## Cron 页面 R3 同名页面定位窄修复

- 仅修改 ScheduledCronPageIntegrationTest.kt。生产外壳与内页均使用 scheduled-page 标签；专用等待用祖先关系分别定位唯一外壳和唯一内页。初次进入、恢复原连接、替换连接、创建任务及离页返回均使用相同 helper，期限仍为 10 秒。
- 普通 waitForTag 的原始数量 == 1 条件、离页原始空节点检查和 assertDoesNotExist 保留；真实鼠标/键盘/滚轮及全部业务、持久化、几何断言未变。
- 本轮修改前 SHA：b356caaa65706d34259aef865dc6136874127a681396444a87c5a29b5397f739；最新 after SHA：44b21ac2ccfaaf78c4c5815a69ea55ddcb438e5751a0dd190d8723d63eaebfb6。静态反向还原本轮修改后得到修改前精确 SHA。四文件原始 before SHA 与唯一 manifest 一致，其余三文件 after SHA 不变。
- 6 项 UI + 4 项装配测试名称保持；本轮新增 0 项。默认 600×800，600×800 / 1440×900 × Light / Dark × paused / accepted / completed 仍为 12 PNG，本工作者生成 0 张。
- 父线程 R2 已通过生产和测试编译，UI 实际执行 6 项，0 failures / 6 errors / 0 skipped，全部在初次页面等待超时；0 PNG，后续 19 类未执行。归档：/Volumes/Data/IdeaProjects/kmp-harness/docs/desktop-ui/verification/scheduled-cron-page-enable-2026-10-09/cron-page-r2-failed。本轮修复待父线程冻结 R3 验证，未宣称测试通过。
- 限该文件 git diff --check 返回 0；新文件 git diff --no-index --check 返回 1且无空白诊断，表示新增差异。
- model=SHUAIAPI/gpt-6.1-sol、actual_model=gpt-6.1-sol、provider=opencodex；tests_run=false、builds_run=false、write_set_released=true、source_writes_stopped=true。报告完成后停止写集。
