# Inbox 独立批次交付 — 2026-10-10

状态：三文件实施完成，静态检查完成；源码写入已停止，写集已释放。父线程独立冻结和串行验收待执行。

## 必需交付字段

```json
{
  "model": "SHUAIAPI/gpt-6.1-sol",
  "provider": "opencodex",
  "baseline_sha256": "58cc581edc9b2fe18e98d7b771b7b7470b0c5da4ceba9c60e0f56432fcc0a6da",
  "contract_sha256": "817de26117cb4bf6d995404f9830036928ec4be4521809bad581f268a5517925",
  "files": [
    {
      "path": "modules/session/src/im/hikaru/harness/session/Session.kt",
      "before_sha256": "7d57a17ff730445dfd96fb9e4050424bc4f6ab6ca058c38b200a8a7d8b9ba4cc",
      "after_sha256": "fa30f4724722bd07cd7ca4649c5f6eff319e41d37f231df7a59e471ea6f74d8e"
    },
    {
      "path": "modules/agent/src/im/hikaru/harness/agent/Inbox.kt",
      "before_sha256": "e826ec08013a5ea45a0c1ea009ab36910f35a005cb051acedd7b109c2ff04e5f",
      "after_sha256": "01210800fbf92ff92cfd51cc336e9c1932074c7a96eeff7065f4847aa501b3ba"
    },
    {
      "path": "modules/agent/test/im/hikaru/harness/agent/InboxAdmissionContractTest.kt",
      "before_sha256": null,
      "after_sha256": "ddf3790c8d44a68634d267011984bc2857756daeefa29437ba40e043864781c4"
    }
  ],
  "tests_run": false,
  "builds_run": false,
  "screenshots_run": false,
  "native_execution_run": false,
  "write_set_released": true,
  "source_writes_stopped": true
}
```

新增测试文件的 before_sha256 为 null，表示基线中不存在该文件。model/provider 是用户及合同指定的实施元数据。

## 实施内容

- Session only adds public suspend acquireOperationPermit delegating to this exact ownerStore.admission.acquireOperation(this); baseline append, restore, admission and lifecycle bytes remain unchanged.
- Inbox insert, claim, replace and clear acquire and finally dispose the exact Session permit before mutex, copies, identity/version calculation and empty-queue checks.
- Copies and claim return values are prepared before append; cancellation is checked after mutex acquisition and immediately before the NonCancellable commit.
- NonCancellable commit retains ordinary Session.append and includes projection, version, INSERT identity increment, onChanged and synchronous contained Agent notice; the identity advances before callbacks.
- Added the eight fixed InboxAdmissionContractTest methods with real Runtime, SessionPlugin, AgentPlugin, SessionHandle and bound Inbox/TestAgent; Deferred barriers use real dispatcher 10-second timeouts and finally cleanup.

## 静态检查证据

- git diff --check 退出码 0；三文件 LF、末尾换行、无尾随空白及异常控制字节。
- 基线 2,261 个编译输入中，写集以外 2,259 个全部 SHA256 一致；合同和基线自身 SHA256 一致。
- 写集外 dirty/untracked 指纹：count=6860，SHA256=ecc228222aa3c9c803e3ba3f50560bbab5973b3a88157e4cbcff7e463654afa7，与实施前一致。
- Session 删除唯一新增 acquireOperationPermit 片段后精确恢复基线 SHA256，证明没有改变既有 append/restore/admission/lifecycle 字节。
- 固定 Inbox 8 方法精确匹配；旧 SessionAdmission8、Session14、Agent6、AgentLoop10、LlmRetry1、Persistence7 方法集合精确匹配。
- 已静态检查四入口准入顺序、取消检查、提交前复制、消息 ID 更新顺序、同步通知、普通嵌套 append、Deferred 屏障及 finally 释放；未执行任何 Kotlin 代码。

## 固定 8 个新方法

1. `reservedInboxRejectsEveryWriteBeforeMutation`
2. `noticeKeepsReservationBusyThroughProjectionAndNotification`
3. `concurrentMutationsKeepContiguousVersionsAndUniqueIds`
4. `cancelledMutexWaiterDoesNotCommitAndReleasesPermit`
5. `cancellationDuringCommitKeepsReplayProjectionAndIdsConsistent`
6. `callbackFailureCannotReuseCommittedItemId`
7. `preCommitFailureReleasesPermitWithoutConsumingIdentity`
8. `staleInboxCannotMutateSameIdReplacement`

## 限制与父线程验收

- The new test source and production edits have not been compiled or executed. Static checks do not establish behavioral acceptance.
- Parent must independently freeze source after worker exit, run the fixed eight Inbox methods plus the six existing regression classes serially, verify actual XML methods with zero failures/errors/skips, then run the full JVM app build.
- Cancellation consistency is scoped to the normal live owner commit chain. Owner Context/Registry disposal, Agent initialization/driver activity and resource quiescence remain for later lifecycle work.
- SessionAdmission, SessionStore, AgentRegistry, AgentLoopAgent, Runtime/Context, configuration, persistence and UI behavior remain outside this implementation write set; archive buttons are not enabled by this batch.
- No UI or screenshot acceptance is claimed.

## 遵守的执行限制

- Only the three contract source paths were written; the two specifically requested delivery artifacts are outside the repository.
- All existing dirty/untracked paths outside the three-file set and all protected baseline inputs retain their initial bytes/status fingerprint.
- No existing tests or fixtures were edited. No reset, stash, clean, checkout, commit, push, configuration changes, process killing or delegation.
- No Comet resume probe was rerun. CodeGraph preceded code understanding; only exact production paths informed implementation.
- No test, build, screenshot, Kotlin compiler, PTY, native application or resource execution was performed.
- No source writes follow this handoff; write ownership is released.

本交付结束实施工作者的写入职责；交付后退出，不再写入。
