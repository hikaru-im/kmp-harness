# 定时任务写入时间戳验收 BE

日期：2026-10-12（Asia/Shanghai）。目标模型：deepSeek V4.1 Flash 直接实施，不委托子代理。证据批次名 `entry-be1`。

## 1. 本批目标与依据

BD 实现了「筛选和排序任务」菜单里的三种排序，同一菜单还有 `updated_at_desc`（最近更新）；详情页另有一行写入时间戳 `automations.scheduled.detailUpdateTimestampWithLabel`。两者都依赖任务的更新时间戳，本仓模型此前没有该字段，因此本批先补**可观察的那一层**：给任务增加持久化写入时间戳，再据此实现「最近更新」排序与详情「上次更新」行。完整边界见 `../../../reverse-engineering/scheduled-task-timestamp-contract-2026-10-12.md`。

ASAR 依据（只读抽取，见 `logs/asar-evidence.txt`）：

- BE01 `webview/assets/table-92b84c469d71.js`（函数 `_t`）：排序选项顺序 `[automatic, next_run_asc, updated_at_desc, title_asc, ...(搜索时 relevance)]`；比较器 `n.sort==='updated_at_desc' ? t.updatedAt - e.updatedAt : (n.sort==null && !搜索 ? t.createdAt - e.createdAt : 0)`，即最近更新降序。
- BE02 `webview/assets/zh-CN-b05cfd55c534.js`：`table.sort`「排序方式」、`table.sortOption`「{automatic 默认 / next_run_asc 下次运行 / updated_at_desc 最近更新 / title_asc 名称 / other 相关性}」。
- BE03 `webview/assets/zh-CN-b05cfd55c534.js`：`automations.scheduled.detailUpdateTimestampWithLabel` = `{showLastUpdateLabel, select, yes {上次更新 · } other {}}{recent, select, yes {{relativeTime}} other {{date}{includeAt, select, yes { 于 } other { }}{time}}}`。
- BE04 `webview/assets/update-content-bc3a4545935a.js`（函数 `j`）：`showLastUpdateLabel = a == null ? 'no' : 'yes'`，`a` 是相对时间标签——**前缀 `上次更新 · ` 只出现在约一天内的相对分支**；更早的瞬间渲染 `date`（`includeAt` 决定「 于 」或空格）与 `time`，无前缀。

## 2. 写入集

生产（8）：

- `apps/shared/src/im/hikaru/harness/client/scheduling/ScheduledModels.kt`（`ScheduledTask.updatedAt: Long? = null`；`ScheduledTaskSort.Updated`）
- `apps/shared/src/im/hikaru/harness/client/scheduling/ScheduledRepository.kt`（新增 `clock` 注入；保存与持久暂停写时间戳；`beginCreate` 锚点改用同一 clock）
- `apps/shared/src/im/hikaru/harness/client/scheduling/ScheduledExecutor.kt`（`activate` 变更状态时一并写时间戳）
- `apps/shared/src/im/hikaru/harness/client/scheduling/ScheduledValidator.kt`（`updatedAt < 0` 非法）
- `apps/shared/src/im/hikaru/harness/client/scheduling/ScheduledTaskSummary.kt`（`ScheduledLastUpdatedPrefix` + `scheduledLastUpdatedLabel`）
- `apps/shared/src/im/hikaru/harness/client/scheduling/ScheduledManagementNavigator.kt`（「最近更新」排序项 + 降序分支）
- `apps/shared/src/im/hikaru/harness/client/scheduling/ScheduledManagementPage.kt`（详情 `scheduled-detail-updated`）
- `apps/jvm-app/src/im/hikaru/harness/desktop/scheduling/DesktopScheduledStore.kt`（文档 `schemaVersion` 3、读取接受 1–3、提交统一写当前版本）

测试（4）：

- `apps/shared/test/im/hikaru/harness/client/scheduling/ScheduledTaskSummaryTest.kt`（新增 3 项）
- `apps/shared/test/im/hikaru/harness/client/scheduling/ScheduledRepositoryTest.kt`（新增 1 项）
- `apps/jvm-app/test/im/hikaru/harness/desktop/ScheduledManagementInteractionTest.kt`（新增 2 项）
- `apps/jvm-app/test/im/hikaru/harness/desktop/scheduling/DesktopScheduledStoreTest.kt`（新增 1 项、重命名 migrates 断言 2→3 并加 1 断言）

文档（2，新增）：

- `docs/desktop-ui/reverse-engineering/scheduled-task-timestamp-contract-2026-10-12.md`
- 本文件

## 3. 实现要点

- **时间戳语义**：`updatedAt` 可空。旧文档（v1/v2）与旧任务读为 null，详情行不渲染，绝不伪造时间。新写入（新建/编辑保存、无执行器的持久暂停、有执行器 `activate` 的启停）写 `clock.nowEpochMillis()`。`clock: ScheduledClock` 可由测试注入。
- **持久化版本**：文档 `schemaVersion` 3。读取接受 1–3；v1 仍按旧严格形状（无 ledger/cursor）解码并拒绝注入；v2/v3 共用一个形状——v3 只多一个可选 `updatedAt`，v2 缺该键解码为 null。**提交时统一写当前版本 3**，因此旧文档在下一次写入时被升级（原实现会保留读到的显式版本，故 v2 会一直停在 v2）。
- **排序**：「最近更新」按 `updatedAt` 降序、无时间戳的最后（显式 `Comparator` 处理 null）；与搜索、状态筛选组合时先过滤后排序。
- **详情行**：`scheduledLastUpdatedLabel` 只在相对分支加 `上次更新 · ` 前缀，较早的瞬间只返回日期标签，与 BE03/BE04 的 `showLastUpdateLabel` 语义一致。
- **校验**：`structuralError` 增加 `updatedAt != null && updatedAt < 0` 非法，挡住手工构造的负时间戳。

## 4. 问题

- 原版时间戳来自远端服务（`automation.updatedAt`）；本仓是本地写入时钟，二者来源不同（已在契约 §2 §3.1 声明）。
- 原版非相对分支把 `{date}` 与 `{time}` 分开、`includeAt` 决定「 于 」或空格；本仓复用上次运行的日期标签（`runDayLabel`），不逐字复刻该拼接。
- 原版 `relevance`（相关性）与 `createdAt` 默认序、owner 筛选属远端/团队范围，不实现。

## 5. 验收（实际结果）

- `shared` 定向 `ScheduledTaskSummaryTest` + `ScheduledRepositoryTest`：**34/34**（新增 4）。日志 `logs/shared-targeted.log`。
- `shared` 全模块：**542/542**（较上一批 538 增 4，全部为本批新增）。日志 `logs/shared-full.log`。
- `jvm-app` 定向 `ScheduledManagementInteractionTest` + `DesktopScheduledStoreTest`：**25/25**（新增 3、改 1）。日志 `logs/jvm-app-targeted.log`。
- `jvm-app` 全量：**692 found / 5 skipped / 687 started / 687 passed / 0 failed**（exit 0；较上一批 689 found 增 3，均为本批新增；本次未命中历史 PTY 抖动）。日志 `logs/jvm-app-full-r1.log`。
- 截图目录 `docs/desktop-ui/captures/scheduled-execution/`：全量跑后按先例从批次前字节还原，`diff -r` 一致，组合 sha `ac8ebb54…`（跑后观测到的重生成 sha `4f387960…` 与先例相同）。

## 6. 未验证边界

- 无原生窗口/像素证据：本机 `screencapture` 仍返回 `could not create image from display`。
- 详情「上次更新」行与「最近更新」排序项只有交互与纯函数验证，未做原版同尺寸像素对照；`paused-detail-*` 截图夹具的 `updatedAt` 为 null，故冻结截图不含该行（与 BB 的「上次运行」行一致）。
- 未验证 v2 文档在真实用户目录中的存在与升级路径（仅用合成 v2 fixture 覆盖读取与下一提交升级）。
- 未验证多进程并发写同一文档时的版本升级竞态（既有 CAS/锁测试仍覆盖同一路径）。
- 各批证据独立，整体目标 active；本批数量不与其它批次累加成总体覆盖率。
