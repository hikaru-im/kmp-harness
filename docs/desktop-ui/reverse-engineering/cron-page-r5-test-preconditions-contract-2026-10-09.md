# Cron 页面 R5：测试等待与详情导航前提

日期：2026-10-09。实施者：同一 `local/01a11a54-1694-7fb3-8007-37c434531314`，模型 `SHUAIAPI/gpt-6.1-sol`，provider `opencodex`。

## 已确认的父线程证据

- R4 冻结 2,216 输入、计划 20 类 134 项。生产及测试编译通过；实际首类 6 项中 3 通过、3 失败，无错误或跳过；其余 19 类未执行。
- 创建暂停任务、手动运行、离开页面保留 owner、真实历史导航及 12 张截图场景通过。字符输入已有效，不再修改输入机制。
- 完整 R4 证据：`docs/desktop-ui/verification/scheduled-cron-page-enable-2026-10-09/cron-page-r4-failed/`。PNG SHA 和几何矩阵已检查；目视检查待最终候选。
- R5 修复前副本：`/tmp/harness-scheduled-cron-page-r5-baseline-20261009/`。

## 写入范围

仅修改 `apps/jvm-app/test/im/hikaru/harness/desktop/ScheduledCronPageIntegrationTest.kt`，修复前 SHA `ff7e7df010d96eb5d5a425e8fda83506e86830ee8f7cbbd4c8d08bc0194fa0cd`。允许更新原 `/tmp/harness-scheduled-cron-page-enable-delivery-20261009.json` 和同名 `.md` 的修复历史及本文件 after SHA。原始 before SHA 保留；其他三份交付源码 SHA 必须不变。

不运行 build、test、截图。不新增测试，不改生产接线或页面、不重构现有输入、不改超时、持久化或次数断言、不操作真实用户配置和进程。保留所有既有 dirty/untracked 文件。

## 修复 A：到期 Accepted 与流启动是不同观察点

`realPageActivationDueOccurrenceAndPausePersistWithoutFutureDelivery` 在 Accepted 后立即检查 `streams=1`，R4 实测 actual=0。生产先保存 prompt receipt / Accepted，模型流随后调度。已有 `runFromPage` 通过有界 `started.await()` 等待真实 adapter。

在到期用例 Accepted 后，按现有成功链路使用 `runBlocking { withTimeout(10_000) { f.adapter.started.await() } }`；断言 provider=`scripted`、model=`test`、`streams=1`，且 finish 未完成、Accepted 尚无终态。保留 nominalAt/requestId、重复 tick 无重复运行、精确终态和暂停后不投递的全部断言。不得 sleep、重试投递或放宽计数。

## 修复 B：连接改变后通过真实鼠标重新选择任务

`HarnessApp` 按连接身份 remember `ScheduledRepository`；失去/恢复连接或同 Host 替换连接后新仓库没有选中任务。`scheduled-execution-unavailable` 仅在已选任务详情中渲染。`waitForScheduledPage` 只证明外壳和内页存在。R4 两项所有旧 owner 拒绝操作、零流及零会话断言已通过，失败点只是未选详情时查询该标签。

两个失效用例在连接恢复/替换和页面就绪后，通过真实鼠标重新选择原 `task.id`：

1. 任务节点不存在时使用既有 `toggleNavigator()`；等到 `scheduled-task:${task.id}` 后鼠标点击。
2. 关闭存在的 `navigator-overlay`，等待 `scheduled-detail-name`。可抽一个小型公共测试 helper 并供既有离开页面用例复用。
3. 使用既有真实 wheel `reveal` 显示失效提示，断言 `scheduled-execution-unavailable` 已显示。
4. 显示并断言 `scheduled-run-now`、`scheduled-pause` 禁用；按当前源码的真实标签断言永久失效的恢复按钮不存在。
5. 保留 old executor runNow/activate/start/recover 拒绝、账本不变、streams=0、无会话的全部既有断言。

不得调用 repository.selectTask、Semantics SetText/Click 或生产回调、反射控制页面；不能跳过失效 UI 断言。若重新选择后仍无法证明失效 UI，停止源码写入并报告实际状态，交父线程继续定位。

## 交付与父线程验收

更新原 delivery 的 `repair_history`：R5 前后 SHA、两类实际修复、R4 真实统计、未执行后 19 类、未运行 build/test/截图，明确释放写集。原始四文件 before SHA 保留；三个未改 after SHA 保留。10 个测试方法总数和 12 PNG 状态/主题/尺寸矩阵保持。

父线程检查相对 R5 baseline 的精确 diff、其余三份源码 SHA 与全部输入无漂移，再运行唯一候选 `cron-page-r5` 的 freeze/run。20 类 134 项全部实际通过、12 图目视与几何检查后才能归档并登记验收。Heartbeat 仍 Unavailable；应用退出后继续执行不在本批范围。
