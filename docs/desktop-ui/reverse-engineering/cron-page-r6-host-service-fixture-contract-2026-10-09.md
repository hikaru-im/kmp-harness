# Cron 页面 R6：Host 测试服务的生命周期

日期：2026-10-09。复用工作者 `local/01a11a54-1694-7fb3-8007-37c434531314`，模型 `SHUAIAPI/gpt-6.1-sol`，provider `opencodex`。

当前结果：R6父线程已冻结2,216输入，串行完成20类134/134，失败/错误/跳过均0。页面6/6、装配4/4及后18类回归全部通过；12张PNG的SHA与geometry矩阵已复核，并逐张目视检查。完整归档：`../verification/scheduled-cron-page-enable-2026-10-09/cron-page-r6-final/`。下方R5结果及实施合同保留为历史事实。

## 父线程证据

R5 冻结 2,216 输入，计划 20 类 134 项。生产及测试编译通过，页面 6 项全部通过；装配 4 项中 2 项通过、2 项 error，均为重复注册 `api.host`。实际共 10 项、8 通过、0 failure、2 error、0 skipped；后 18 类未执行。12 PNG 的 SHA 与几何矩阵通过，目视验收待最终候选。

完整失败证据已保存在 `docs/desktop-ui/verification/scheduled-cron-page-enable-2026-10-09/cron-page-r5-failed/`，37 个归档文件的 SHA 已核验。修复前四源码及完整输入清单：`/tmp/harness-scheduled-cron-page-r6-baseline-20261009/`。

## 已确认根因及接口

`CronHarness.create` 安装 `HostApiPlugin`，该插件通过 effect scope 注册 `HostApiKey`。两个装配用例再次 `context.provide` 同一 key，被 ServiceRegistry 正确拒绝。`Context.replace` 是 internal；公开的 `Runtime.replace` 禁止替换 plugin-owned service，均不应拿来绕过所有权。

修复只在两个用例中，通过公开的 `Runtime.uninstall` 卸载本夹具唯一的 `HostApiPlugin` Fiber，确认 `runtime.context.has(HostApiKey)` 为 false，再按既有方式 `provide` 测试服务。用精确 plugin identity 查找唯一 Fiber，不卸载其他插件，不使用反射或直接 registry。测试服务的 registration 仍在 finally dispose，夹具仍在外层 finally close。

## 唯一源码写集

`apps/jvm-app/test/im/hikaru/harness/desktop/scheduling/DesktopScheduledExecutionTest.kt`，修复前 SHA `e7e9c5e10914703cc85356d2e0dc8eb3e70aed94aeeb9c31a0bda2593d706b3c`。允许更新原 delivery JSON/MD 和本文 after SHA。其余三文件，包含已通过的 6 个页面用例和夹具，必须字节不变。

1. `realGatewayHostAndProtocolMismatchRefuseAdmissionAndNeverAcquireLease`：在修正描述前卸载夹具的 Host 插件。注入后增加真实 `connection.host.describe()` 返回 corrected description 的断言，再证明这个已失效 owner 的 start 仍 Unavailable。保留初始 mismatch、lease available、零会话/零流/零账本的所有断言。
2. `cancellationFromRealGatewayHostServicePropagatesWithoutStartingOwner`：注入 throwing service 前同样卸载夹具 Host 插件。保留 exact same CancellationException、owner 未启动、无投递、lease available 的所有断言。
3. 其余两个装配用例及 helper 字节不变，测试方法总数保持 4；页面 6 项与 12 图矩阵不变。

不运行 build/test/截图，不改生产源码、Runtime 所有权、gateway、夹具或既有输入机制。保留所有 dirty/untracked，不操作真实用户配置或现有用户进程，不 reset/stash/clean/commit。

## 交付与验收

更新 `/tmp/harness-scheduled-cron-page-enable-delivery-20261009.json` 及同名 MD：保留原始 before SHA，更新本文件 after SHA；在 repair_history 新增 R6，记录 R5 的真实 8/10 及 2 errors、18 类未执行、12 PNG 已核验，标明 R5 failed、R6 pending。worker builds/tests/screenshots 均 false；完成后停止全部写入并释放写集。

父线程对比 R6 baseline、验证其他三源码及全部输入无漂移，冻结唯一候选 `cron-page-r6` 并串行运行全部 20 类 134 项。全部实际通过后检查 12 图并归档。仍不把 headless 截图视为原版像素或 native 验收；Heartbeat 仍 Unavailable。
