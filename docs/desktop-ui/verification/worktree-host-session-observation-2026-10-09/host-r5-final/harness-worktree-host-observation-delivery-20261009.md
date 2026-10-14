# Worktrees Host observation delivery

Revision: host-r4-creation-readiness

Model route: SHUAIAPI/gpt-6.1-sol; actual model: gpt-6.1-sol; provider: opencodex.

tests_run=false; builds_run=false; write_set_released=true.

## Correction

Both existing creation tests wait for the exact fixture repository root and enabled path, branch and start-ref semantics before input.
Readiness timeout remains 10000ms; creation success timeout remains 15000ms. Submit enablement is not required before fields are populated.
Only the two readiness predicates changed. HarnessApp, actual Git/files chain, source/index preservation and Relay assertions and all existing interactions are byte-for-byte preserved.
No controller manipulation, sleeps, retries, production changes, builds or tests. The other eight delivered files exactly match host-r4.

## Baselines

Original 2206-input before hashes: /tmp/harness-session-header-catalog-parent-candidate-20261009-catalog-r1-manifest.json

Creation before copy and manifest: /tmp/harness-worktree-creation-readiness-baseline-20261009

Eight preserved after hashes: /tmp/harness-worktree-host-observation-parent-candidate-20261009-host-r4-manifest.json

## Nine delivered files

| File | Before SHA256 | After SHA256 |
| --- | --- | --- |
| apps/shared/src/im/hikaru/harness/client/git/ManagedWorktreeModels.kt | 349db0b30eaf7f5fa1610ed7c86c3928d3d561b71d5e7da1d4b9aa1a1c7b3a00 | 3654455a0d0c381465f745508415f8e30e3dc6bf66c2e0f81960fb5eb38be1d4 |
| apps/jvm-app/src/im/hikaru/harness/desktop/git/DesktopWorktreeSessionObserver.kt | None | 651673d4940403650b1004055ca40b7fad5439dae8f81c2c0de05f7312feab43 |
| apps/jvm-app/src/im/hikaru/harness/desktop/git/DesktopManagedWorktreeRepository.kt | 4d0430fe8bfb83e23a3e220099c267ba2c2273acef8bf4a62628f86f996b1d7e | 11a66b28a37a3bf44005ff288715835d94a9778f35f3a9af2bd8f195ab3c04f9 |
| apps/jvm-app/src/main.kt | 1ace77c5401d22f8376e68c4993821f1a727aff0a18cb03dca00de4c2b4f771b | bdf34f9df06cd92b8197de24a6169d129a1db3d70874aa2d6d5acc8f9015125f |
| apps/shared/src/im/hikaru/harness/client/git/GitPages.kt | 5abc956a11d7f8ff8d9f8c23285db950b5cf792ce9f1d42a30d8eb29a097981b | ea4f017e59fa9c6d42f1703131d13a9a1031c7d2b9823a99b82e0b24fb147746 |
| apps/jvm-app/test/im/hikaru/harness/desktop/git/DesktopWorktreeSessionObserverTest.kt | None | a02b8f2129c598d6dc72dcf386fb916097e6bbb0dab9ec95b3a16a14cb81c6da |
| apps/jvm-app/test/im/hikaru/harness/desktop/WorktreeSessionObservationInteractionTest.kt | None | 15f3590ea77453ae7d941194269fb4d13d55db83559627e05b3e2c3918a1cae0 |
| apps/jvm-app/module.yaml | e2a9ab2274d3c6f0bdcffcea29e77474e0180f95a0736c7951a5d25c9f38fc30 | 4c61cc5f68bb29e54c3e977c553471655a773318c684bc693ccbfdee31963c46 |
| apps/jvm-app/test/im/hikaru/harness/desktop/WorktreeCreationInteractionTest.kt | d44c737c661a5f2051a6aba21c7bb35def9e23af899987ccb43e625636fef725 | 261efc95f6c82aa7ae08e75a3e90036183ad0987bdde332c60c4b85b6ce58be2 |

This revision wrote one source file: apps/jvm-app/test/im/hikaru/harness/desktop/WorktreeCreationInteractionTest.kt. Other eight delivered files match host-r4.

## Parent evidence

host-r4: 32 executed, 30 passed, 2 creation failures. Six screenshot tests were not run.

| Class | Passed / executed |
| --- | --- |
| im.hikaru.harness.desktop.git.DesktopManagedWorktreeIntegrationTest | 8 / 8 |
| im.hikaru.harness.desktop.git.DesktopWorktreeSessionObserverTest | 11 / 11 |
| im.hikaru.harness.client.git.ManagedWorktreeControllerTest | 4 / 4 |
| im.hikaru.harness.desktop.ManagedWorktreeInteractionTest | 3 / 3 |
| im.hikaru.harness.desktop.WorktreeCreationInteractionTest | 0 / 2 |
| im.hikaru.harness.desktop.WorktreeSessionObservationInteractionTest | 4 / 4 |

Evidence: /tmp/harness-pages-verified-20261009/worktree-host-observation/host-r4. Prior host-r1/r2/r3 evidence remains in JSON. This correction is not runtime verified.

Eight observation PNGs and eight geometry files were generated and visually inspected by the parent; archive pending. Worker generated no captures.

## Test inventory and next validation

New classes remain observer 11 + observation UI 4 = 15 cases. This revision adds zero test methods; creation retains two cases.

- settingsCreateWorktreeReadsActualDirectoryAndPreservesSourceChanges
- relayDisablesLocalWorktreeCreationControlsWithoutCreatingDirectories

Parent host-r5: freeze a new manifest; run creation 2 first. If passing, run remaining 36 on the same manifest and archive eight observation captures.

| Order | Class | Cases |
| --- | --- | --- |
| 1 | im.hikaru.harness.desktop.WorktreeCreationInteractionTest | 2 |
| 2 | im.hikaru.harness.desktop.WorktreeSessionObservationInteractionTest | 4 |
| 3 | im.hikaru.harness.desktop.git.DesktopWorktreeSessionObserverTest | 11 |
| 4 | im.hikaru.harness.desktop.git.DesktopManagedWorktreeIntegrationTest | 8 |
| 5 | im.hikaru.harness.client.git.ManagedWorktreeControllerTest | 4 |
| 6 | im.hikaru.harness.desktop.ManagedWorktreeInteractionTest | 3 |
| 7 | im.hikaru.harness.desktop.GitWorktreesScreenshotTest | 6 |

## Static checks and limitations

Scoped git diff --check passed. Baseline comparison proves only two readiness predicates changed; all existing interactions, deadlines and assertion chains remain.

Parent host-r4 executed 32 tests: 30 passed and both old creation tests failed. Six GitWorktreesScreenshotTest cases were not run. Historical failures remain; there is no 38/38 result.
This correction has only been statically reviewed. Worker ran no build/test; parent must freeze host-r5 and run creation 2 before the remaining 36 on the same manifest.
Eight observation PNGs and eight geometry files were generated and visually inspected by the parent in host-r4; archive remains pending.
End and lineage are separate measured stages. Independent live/durable reads are observations, not linearizable snapshots or deletion authorization.

Write set released; no further edits.
