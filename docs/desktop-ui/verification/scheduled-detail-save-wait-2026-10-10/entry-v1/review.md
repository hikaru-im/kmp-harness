# Entry V1 — scheduling save tests wait for the commit

Date: 2026-10-10. Batch V. Test-only change plus evidence. No production source was modified.

## Why this batch exists

Batch U surfaced two failures whenever a scheduling/plugins multi-class set ran in one JVM:

- `ScheduledPageRoutingInteractionTest.harnessRouteEditsBoundTaskAndPersistsPausedConfigurationWithoutExecution`
- `ScheduledRoutingInteractionTest.editingBoundTaskPersistsAcrossStoreInstancesWithoutExecutingSession`

Both failed with `scheduled-detail-prompt` not found at 1440x900, yet both passed when run alone, and
removing the class that Batch U changed from the set did not remove them. They were therefore
independent of Batch U and reproducible on demand: three consecutive 8-class runs in Batch U gave the
same two failures (`verification/compact-more-route-contract-2026-10-10/entry-u1/logs/`).

## Root cause

Both tests click `scheduled-save` and then assert `scheduled-detail-prompt` immediately. The click only
starts the save:

- `ScheduledManagementPage.kt` runs `onSave = { coroutineScope.launch { repository.save() } }`;
- `ScheduledRepository.save()` commits with `store.compareAndSet(...)`;
- `DesktopScheduledStore.compareAndSet` is `withContext(Dispatchers.IO)` plus a sidecar file lock.

The Compose test clock does not track `Dispatchers.IO`, so the assertion races the disk commit. The
isolated run is fast enough to win; the loaded multi-class run is not. While the save is in flight the
draft is still non-null, so the editor is on screen and there is no detail node at all. The commit
clears the draft and publishes the new effective snapshot in a single state update, so a detail
rendering a stale prompt is not possible — waiting for the node is enough.

## Change

Test-only. Both tests now wait for the committed detail before the original assertion:

```
waitUntil(timeoutMillis = 10_000) {
    onAllNodes(hasTestTag("scheduled-detail-prompt") and hasText(<expected prompt>))
        .fetchSemanticsNodes().isNotEmpty()
}
onNodeWithTag("scheduled-detail-prompt").assertTextEquals(<expected prompt>)
```

This cannot mask a real defect: a save that never commits still fails on the 10s timeout. The
`runOnIdle` block that follows still verifies the persisted store (revision 2, Paused status, the new
prompt) and that no execution was triggered.

Changed files:

- `apps/jvm-app/test/im/hikaru/harness/desktop/ScheduledPageRoutingInteractionTest.kt`
- `apps/jvm-app/test/im/hikaru/harness/desktop/ScheduledRoutingInteractionTest.kt`

## Verification

| Run | Before (Batch U) | After |
| --- | --- | --- |
| 8-class set | 41 / 43, same 2 failures | **43 / 43** |
| 8-class set re-run | 41 / 43 | **43 / 43** |
| 7-class set (no ScopeNavigation) | 38 / 40, same 2 failures | **40 / 40** |
| the 2 classes alone | 5 / 5 | 5 / 5 (unchanged) |

Three consecutive green runs on the sets that previously failed.

- `logs/harness-v1-jvmapp-8class.log`, `logs/harness-v1-jvmapp-8class-rerun.log`,
  `logs/harness-v1-jvmapp-7class.log`.
- Pre-fix failing runs and the 5/5 isolated run are Batch U's logs, referenced above.

## Boundaries

- Test-only. No production source changed, so no page behavior, geometry or capture changed.
- This removes two spurious failures from the shared verification command; it does not add new UI
  coverage.
- Other batches keep their own independent evidence.
