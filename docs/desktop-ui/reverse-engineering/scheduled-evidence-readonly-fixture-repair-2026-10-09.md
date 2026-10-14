# 定时任务证据测试：Room fixture 修复

继续同一工作者 `01a11a54-1694-7fb3-8007-37c434531314`，模型 `SHUAIAPI/gpt-6.1-sol`、provider `opencodex`。本修复补充原五文件合同，仅允许改动 `apps/jvm-app/test/im/hikaru/harness/desktop/scheduling/DesktopScheduledSessionEvidenceReadOnlyTest.kt`；工作者不构建、不运行测试，父线程冻结后串行验收。

## 已运行证据

`readonly-r1`：跨 JVM crash 5/5、Room readEvents 9/9；evidence 10 项全部在 fixture 读取行时抛出错误，未到达完整业务断言。其余类未运行。失败日志、XML、manifest、原交付及测试 before 副本已保留于 `verification/scheduled-executor-crash-2026-10-09/readonly-r1-failed/`。

`Fixture.rows()` 每次用新 factory 打开相同路径，与 Runtime 持有的真实 Room 冲突。`SessionDatabaseRegistry.acquire` 正确拒绝重复所有权；禁止修改或绕过生产保护。

## 修复要求

- 在 fixture 的真实 factory 接线中保留当前生命周期的 `SessionDatabaseAccess`，Runtime 活跃时直接通过这一真实 owner 的 DAO 读取行；没有活跃 Runtime 时才独立打开读取并在 finally 中关闭。
- Runtime 关闭/重启后必须切换到新 owner，禁止缓存已 disposed 的 access。保持 plugin/runtime 的关闭顺序和取消清理。
- 保留全部 10 个测试、真实 Room、前后逐行对比、精确终态和顺序断言、生命周期与 live store 计数、失败和取消断言；不改生产文件或其他测试。
- 静态检查后输出新的五文件交付（before SHA 仍对应 initial 基线），并另记此单文件修复 before SHA。交付路径 `/tmp/harness-scheduled-evidence-readonly-delivery-20261009-r2.json` 及 `.md`；须含 `tests_run=false`、`builds_run=false`、`write_set_released=true`、`source_writes_stopped=true`。
- 保留所有 dirty/untracked；定位先 CodeGraph。释放后停止写入。

## 最终结果

指定工作者已按真实Room owner生命周期完成接线并释放。父线程核对五文件SHA、原四文件与R1保持一致、10项测试正文未改，冻结readonly-r2并串行通过12类85/85，错误/失败/跳过0。最终证据位于 `../verification/scheduled-executor-crash-2026-10-09/readonly-r2-final/`；本fixture修复完成。生产Cron尚未启用。
