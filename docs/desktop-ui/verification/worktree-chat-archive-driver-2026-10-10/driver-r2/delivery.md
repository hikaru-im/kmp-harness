# Driver 新增测试修复交付（静态）

仅修复授权测试；其余八方法字节一致。所有运行字段均为 false；不宣称 owner drain 完成。

## 修复说明

- 仅修改新增测试的 imports、Fixture.dispose 和 cancelledDriverAndDisposedScopeNeverRestartQueuedTurns；其余八个 @Test 方法逐块字节一致，固定九方法的名称和顺序保持。
- 完整关闭 Agent Context 后，使用公开 context.isDisposed 断言关闭状态；resumeAttempt 精确断言 SessionException.code == SessionErrorCode.CLOSED_LIFECYCLE，reserve 精确断言 Rejected(CLOSED_SESSION)。保留 driver 调用次数为 1，新增 beforeAttempt 调用次数为 0 和完整保留队列快照不变断言。
- Fixture.dispose 区分已完整关闭的 Agent Context 和普通清理路径；前者明确断言公开 isDisposed 及 CLOSED_LIFECYCLE，只接受没有附带 suppressed 错误的该精确异常。错误 code、其他异常、意外成功或附带错误均不被吞掉。
- handle 清理或断言失败后仍在 NonCancellable 中尝试 runtime.context.dispose；保留首个非预期错误并把额外 Runtime 错误作为 suppressed 传播。此处只描述测试夹具的清理控制流，不宣称 Runtime owner drain 已完成。
- 使用 CodeGraph 定位，并直接核对 modules 下的实际生产路径：AgentLoopAgent.resumeAttempt/cancel、Session.acquireOperationPermit、SessionAdmission.acquire/reserve/retire、SessionStore.detach、Session.close、AgentRegistry.detach 和公开 Context.isDisposed/dispose。verification 副本未作为修复依据。

## 完整交付字段

```json
{
  "model": "SHUAIAPI/gpt-6.1-sol",
  "provider": "opencodex",
  "baseline_sha256": "445b272a7771c78968efe0e5a455f99e1a197cdf2970576fe5404cbdfa5ab226",
  "contract_sha256": "b6646786d29d63159c7d72ce25a09804e2cd5f8409775e1f10bafe5e8602eb60",
  "files": [
    {
      "path": "modules/agent-loop/src/im/hikaru/harness/agent/loop/AgentLoopAgent.kt",
      "before_sha256": "a5f26a914771032ef07636b2cfb3a49a753f08959f6a5ad8e705afd9592e71f9",
      "after_sha256": "cebbfc695e65cccb8e7cac15823b2a2a460e0592741fc47f98bbb5509f1e4f59"
    },
    {
      "path": "modules/agent-loop/test/im/hikaru/harness/agent/loop/AgentDriverAdmissionContractTest.kt",
      "before_sha256": null,
      "after_sha256": "ede5d9cc77ada06650420d44d8a5df33e4069a9185f5c329294b971bab4e5bf4"
    }
  ],
  "tests_run": false,
  "builds_run": false,
  "screenshots_run": false,
  "native_execution_run": false,
  "write_set_released": true,
  "source_writes_stopped": true,
  "static_checks": {
    "baseline_and_contract_sha256_match": true,
    "repair_baseline_test_before_sha256_match": true,
    "unchanged_non_write_repair_baseline_inputs": 2263,
    "production_unchanged_from_repair_baseline": true,
    "unchanged_other_new_test_methods": 8,
    "new_fixed_test_methods_match": 9,
    "permitted_test_regions_only": true,
    "test_git_diff_no_index_check": {
      "exit_code": 1,
      "diagnostics": [],
      "interpretation": "test differs from repair baseline; no whitespace diagnostics"
    },
    "no_trailing_whitespace": true,
    "no_sleep_reflection_production_hook_or_new_test_method_added": true,
    "compile_or_runtime_acceptance_claimed": false,
    "owner_drain_completion_claimed": false
  },
  "implementation": [
    "仅修改新增测试的 imports、Fixture.dispose 和 cancelledDriverAndDisposedScopeNeverRestartQueuedTurns；其余八个 @Test 方法逐块字节一致，固定九方法的名称和顺序保持。",
    "完整关闭 Agent Context 后，使用公开 context.isDisposed 断言关闭状态；resumeAttempt 精确断言 SessionException.code == SessionErrorCode.CLOSED_LIFECYCLE，reserve 精确断言 Rejected(CLOSED_SESSION)。保留 driver 调用次数为 1，新增 beforeAttempt 调用次数为 0 和完整保留队列快照不变断言。",
    "Fixture.dispose 区分已完整关闭的 Agent Context 和普通清理路径；前者明确断言公开 isDisposed 及 CLOSED_LIFECYCLE，只接受没有附带 suppressed 错误的该精确异常。错误 code、其他异常、意外成功或附带错误均不被吞掉。",
    "handle 清理或断言失败后仍在 NonCancellable 中尝试 runtime.context.dispose；保留首个非预期错误并把额外 Runtime 错误作为 suppressed 传播。此处只描述测试夹具的清理控制流，不宣称 Runtime owner drain 已完成。"
  ],
  "new_test_methods": [
    "closingRejectsSendCancelAndResumeBeforeStateMutation",
    "cancelledInitializationCanRetryFromPersistedTurnNumber",
    "driverPermitCoversRunningBodyAndIdleNotification",
    "idleFollowupHandoffKeepsContinuousSessionActivity",
    "runningNoticeCanCheckAlreadyRunningWithoutMutexDeadlock",
    "closedContextNotificationFailureStillReleasesDriverPermit",
    "cancelledDriverAndDisposedScopeNeverRestartQueuedTurns",
    "concurrentCancelsCannotReleaseEachOthersStoppingState",
    "restoredAttemptOwnsIndependentActivityUntilItsFinallyCompletes"
  ],
  "parent_acceptance": {
    "run_by_worker": false,
    "fixed_classes": 9,
    "fixed_methods": 69,
    "required": "After worker exit, freeze source and run the independent serial fixed-method acceptance with zero failures, errors or skips, then the complete JVM app build."
  },
  "boundaries": [
    "修复唯一仓库写集为新增 AgentDriverAdmissionContractTest.kt；生产文件、旧测试和配置与修复基线一致。files 的两项仍按原交付列出，before_sha256 保留原合同 baseline 的值，新测试为 null。",
    "仅静态核对；未运行 test/build/screenshot/native app/PTY。编译、运行行为和独立父线程验收仍未执行。",
    "未重跑已返回 none 的持续任务 probe；未重做架构分析、派工作者、修改生产 gate 或新增生产 hook。",
    "未执行 reset/stash/clean/checkout/commit/push；未终止既有进程。",
    "Fixture 的异常保留及 Runtime 清理路径修正不构成 owner drain 或生命周期等待安全的验收结论。",
    "生成本交付后停止写入并释放写集；父线程应冻结当前 SHA 后执行独立验收。"
  ],
  "repair_baseline_sha256": "fbff734002a1b420003868bbc02a09bca6f794bba1c97f4a5ee1443db78215e5",
  "repair_before_test_sha256": "7f14b787269984be16abbd8a1c92811fe2de811f358c77aa07f279b62ae7a10d",
  "repair_after_test_sha256": "ede5d9cc77ada06650420d44d8a5df33e4069a9185f5c329294b971bab4e5bf4",
  "repair_notes": [
    "仅修改新增测试的 imports、Fixture.dispose 和 cancelledDriverAndDisposedScopeNeverRestartQueuedTurns；其余八个 @Test 方法逐块字节一致，固定九方法的名称和顺序保持。",
    "完整关闭 Agent Context 后，使用公开 context.isDisposed 断言关闭状态；resumeAttempt 精确断言 SessionException.code == SessionErrorCode.CLOSED_LIFECYCLE，reserve 精确断言 Rejected(CLOSED_SESSION)。保留 driver 调用次数为 1，新增 beforeAttempt 调用次数为 0 和完整保留队列快照不变断言。",
    "Fixture.dispose 区分已完整关闭的 Agent Context 和普通清理路径；前者明确断言公开 isDisposed 及 CLOSED_LIFECYCLE，只接受没有附带 suppressed 错误的该精确异常。错误 code、其他异常、意外成功或附带错误均不被吞掉。",
    "handle 清理或断言失败后仍在 NonCancellable 中尝试 runtime.context.dispose；保留首个非预期错误并把额外 Runtime 错误作为 suppressed 传播。此处只描述测试夹具的清理控制流，不宣称 Runtime owner drain 已完成。",
    "使用 CodeGraph 定位，并直接核对 modules 下的实际生产路径：AgentLoopAgent.resumeAttempt/cancel、Session.acquireOperationPermit、SessionAdmission.acquire/reserve/retire、SessionStore.detach、Session.close、AgentRegistry.detach 和公开 Context.isDisposed/dispose。verification 副本未作为修复依据。"
  ]
}
```
