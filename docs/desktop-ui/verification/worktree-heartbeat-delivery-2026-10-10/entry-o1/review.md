# Batch O 证据：会话跟进（Heartbeat）真实投递

日期：2026-10-10
范围：定时任务 —— `ScheduledTarget.Heartbeat`（会话跟进）从“处处 Unavailable”改为向既有活动会话真实投递。
证据目录：`docs/desktop-ui/verification/worktree-heartbeat-delivery-2026-10-10/entry-o1/`

## 结论

会话跟进运行现在与手动发送共用同一条 `SessionApi.prompt` 路径（`agent.followup` → 同一 `SessionAdmission` 原子准入）。
一个 Heartbeat 运行不创建、不恢复、不指定模型：其持久 intent 直接指向任务绑定的那个既有会话。

- shared 定向 **83/83** 通过，含 2 个新增 Heartbeat 用例，以及 2 处既有用例改写为覆盖 Heartbeat。
- jvm-app 定向 **90 通过 / 2 失败**；2 个失败与本批无关（见“边界”）。
- 完整 JVM app build **Build successful, exit 0**。

## 语义

1. `ScheduledLedgerRules`：运行目标允许 Cron 或 Heartbeat；Heartbeat 运行的 `target.sessionId` 必须等于 `candidateSessionId`；游标同样接受两种目标。
2. `ScheduledExecutor.claim`：Heartbeat 的候选会话即任务目标会话；`deliver` 对 Heartbeat 直接从 `Claimed` 进入 `SessionCreated`（`sessionRef = target.sessionId`），跳过 `CreatingSession`，从不调用 `access.create`。
3. `ScheduledExecutor.settleUncalledPromptLocked`：Heartbeat 在 prompt 前停止且未提交投递意图时落 `Rejected`，因为没有创建副作用需要核对。
4. `ScheduledSessionAccess`：`create` 对 Heartbeat 明确报错；`prompt` 要求 `sessionRef` 恰为目标会话；`unavailableReason` 不再对 Heartbeat 短路。
5. `ScheduledRepository.taskExecutionReason`：目标必须在当前 Host 真实目录内；模型仅在非空时校验（Heartbeat 模型为 null）。
6. `ScheduledModels.canActivate`：Cron 或 Heartbeat 均可启用。
7. `ScheduledManagementPage`：移除 `scheduled-heartbeat-unavailable` 文案，改为仅在目标为 Heartbeat 时显示说明标签 `scheduled-heartbeat-target`。
8. `DesktopScheduledSessionEvidence.unavailableReason`：Heartbeat 要求模型为 null 且目标会话是本机**当前活动会话**，否则给出“不能创建或恢复”的中文原因；Cron 路径不变。
9. 执行器启用门槛 `verifiedCronExecution` 仍决定 Heartbeat 是否可被认领——与 Cron 相同，只有正式接线（`main.kt`）下才可执行。

## 本批修复的真实缺陷

- **编译错误（本批引入）**：`ScheduledExecutor.claim` 中新增的会话候选变量与既有快照变量同名 `candidate`，导致重复声明；已重命名为 `candidateSessionId`。
- **陈旧测试（页面迁移遗留，非本批引入）**：两个调度 UI 测试仍引用已迁移掉的 tag（`scheduled-page-task:*`、`scheduled-page-search`，以及在非 landing 状态下断言 `scheduled-management-title`）。已对齐到当前 `ScheduledManagementPage`：`LocalServiceTransportInteractionTest` 改用 `scheduled-task:local-only` / `scheduled-new-task`；`ScheduledManagementScreenshotTest` 按状态分阶段校验（landing 标签 → 进入 ledger → 历史标签），并对没有滚动祖先的状态区改为“能滚则滚，否则直接断言可见”。

## 测试与证据

| 命令 | 覆盖 | 结果 |
| --- | --- | --- |
| `kotlin test -m shared`（6 类） | ScheduleEngine、ExecutorCapacity、ExecutorOwner、Executor、LedgerRules、Repository | 83/83，0 失败（logs/harness-o1-shared-scheduling.log） |
| `kotlin test -m jvm-app`（16 类） | 调度页面交互/路由/截图、Cron 页面集成、Local 会话接入/evidence、ScopeNavigation、Shortcuts、LocalServiceTransport | 90 通过 / 2 失败（logs/harness-o1-jvmapp-scheduling.log） |
| `kotlin build -m jvm-app` | 生产与测试源码 | Build successful, exit 0（logs/harness-o1-jvmapp-build.log） |

新增/改写用例：

- `ScheduledRepositoryTest.heartbeatActivatesAndClaimsItsBoundSessionWithoutEverCreatingOne`：激活 + runNow → Accepted，candidate/sessionRef 等于目标会话，0 次 create，1 次 prompt。
- `ScheduledRepositoryTest.heartbeatIsRefusedWhenItsBoundSessionIsNoLongerAvailable`：目标会话消失后激活与 runNow 均拒绝，无副作用。
- `ScheduledExecutorTest.heartbeatDeliversIntoItsBoundSessionWithoutCreatingOne`：无 `CreatingSession` 提交，candidate 等于目标会话，0 create、1 prompt。
- `ScheduledExecutorTest.directAdapterRejectsUnsupportedOrInvalidTargetAndMismatchedSessionBeforeEffects`：Heartbeat 的 `create`/`prompt` 在副作用前失败。

## 边界

- 只做“投递到既有活动会话”；不自动创建、不自动恢复、不覆盖模型。
- 未纳入：应用退出后的后台执行、跨进程 Heartbeat 投递验证。
- jvm-app 定向运行中 2 个失败与本批无关，均为既有陈旧用例：
  - `ScopeNavigationInteractionTest.compactMoreReachesPluginsAndScheduledTasks`：600dp 紧凑宽度下 `plugins-navigator` 未挂载（shell 紧凑布局，非调度）。
  - `ShortcutsInteractionTest.ordinarySearchEditingAndOpenMenuKeepTheirOwnKeyboardBehavior`：`shortcuts-search` 把占位符暴露为 `Text`，`assertTextEquals("")` 不再成立（设置页快捷键搜索，非调度）。
- 无原生窗口、同尺寸像素或新截图验收。
- 各批证据独立，整体目标 active。

