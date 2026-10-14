# Entry X1 — In-scope interaction tests that were red or load-flaky

Date: 2026-10-10. Batch X. Test files only; no production change (one production regression is
reported, not changed).

## Why this batch

The plan and the U/V/W records carried two tests as "pre-existing red, not touched". Keyboard
shortcuts, Git branch checkout, Local Projects and Worktree observation are all inside the confirmed
scope, so a red suite there is a real gap. This batch found the actual cause of each instead of
repeating the first symptom, using temporary probes that were deleted from the tree.

## What the full module run showed

After the Shortcuts/Git fixes in this batch, the whole `jvm-app` module ran 647 tests: 5 skipped,
639 successful, 3 failed:

- `LocalProjectsInteractionTest.registerExistingDirectorySelectHomeProjectAndPersistConfirmedMembershipBeforePrompt`
- `WorktreeSessionObservationInteractionTest.capturesFourRealCatalogLayoutsWithWheelReachableTailAndCompleteEndMarker`
- `DesktopProductionBoundaryTest.productionSourceShouldDependOnProfileBundleInsteadOfProviderInternals`

Running just those three classes reproduced LocalProjects and DesktopProductionBoundary and cleared
WorktreeSessionObservation, so that one is a load-only timeout.

## Fixes

### 1. Git branch checkout: the picker was disabled, not mistagged

A probe reproduced the failing setup (real temp repo via `BranchCheckoutFixture`, a real
`DesktopGitBackend`, size 1000x1100) and printed the state at the click:

    checkoutAvailable=true canSwitchBranch=false reason=请选择已有本地分支。
    refs=[refs/heads/feature, refs/heads/main, refs/heads/sandbox, refs/remotes/origin/feature]
    repository=Ready(binding matches) status=Ready(changes=[]) commitSnapshot=Loading
    pickerDisabled=true   menuTags=[]   observer enabled=false on every event

`BranchCheckoutForm` enables the picker with `state.pageReady && ...` (`GitPages.kt`), and
`pageReady` (`GitPageComponents.kt`) needs the whole read model — repository, status, branches,
worktrees, diff, and now `commitSnapshot` — out of `Loading`. The test waited only for `branches`
and `status`, so the picker was still disabled; the click toggled nothing and no `menu:*` was ever
composed. It is not a tag problem: `GitPageInteractionTest` uses the same `menu:<index>` keys.

The same defect then hit one step later: the post-rejection `git-refresh` click was a no-op because
the button was still disabled (`commitSnapshot=Loading`), so the tree stayed dirty
(`changes=[tracked.txt M]`) and `canSwitchBranch` never became true.

### 2. Shortcuts confirm-close menu: the control was disabled, not missing

A probe printed the anchor just before the click:

    anchorDisabled=true   anchorBounds=Rect.fromLTRB(842.0, 756.0, 967.0, 768.0)   menuNodes=[]

The control is editable only when `GeneralSettingsState.canEdit` holds, i.e. `closePolicyAvailable`
(`SettingsGeneralPage.kt`, `GeneralSettingsRepository.kt`). The default repository has it false;
production enables it (`main.kt`) and `GeneralSettingsInteractionTest` passes a repository with it
enabled. This test built `HarnessApp` without a general settings repository, so the menu was
unreachable. A second defect in the same test: `assertTextEquals("")` after clearing the search
treated the placeholder as field text; the empty field renders its placeholder `HarnessText` inside
the decoration box (`HarnessSearchField.kt`) and merges it into `Text`, while `EditableText` is
correctly empty.

### 3. Local Projects picker: entries are tagged by id, not by name

`LocalProjectsInteractionTest` clicked `onNodeWithTag(project.name)` ("Interaction project"). The
picker builds entries with `testTag = "local-project-picker-item-<id>"` (`LocalProjectsPage.kt:142`);
only the default entry keeps a name tag. The name tag no longer exists, so the click found no node.

### 4. Worktree observation readiness: load-only timeout

The test passed alone and failed only in the full run, where its 10 s `readyObservation` wait expired
with `gitReady=false` and `commitSnapshot=Loading`. The gate includes the commit snapshot, whose real
`git` read is slow under full-suite load. That one bounded wait was raised to 30 s; the assertion is
unchanged.

## Reported, not changed: the Desktop production boundary

`DesktopProductionBoundaryTest.productionSourceShouldDependOnProfileBundleInsteadOfProviderInternals`
fails because `DesktopScheduledSessionEvidence.kt:6` imports `im.hikaru.harness.llm.llm` and uses it
at lines 39-41 to validate a task model through `resolveModelInfo`. It is the only offender under
`apps/jvm-app/src`, and the invariant predates the violation (boundary test 2026-09-12; evidence file
2026-10-10 15:55).

Not changed here because it is a design decision, not a stale-test bug:

- The shared layer already validates the model against the Host real catalog
  (`ScheduledRepository.kt:344`, `ScheduledManagementPage.kt:173`; the context is built in
  `HarnessApp.kt:636` from `modelChoices`), so this Desktop call is a second, provider-coupled check
  at execution admission.
- Deleting it would change execution-admission behaviour for the verified cron/heartbeat batches;
  relocating it needs a provider-neutral resolver supplied from a module that may import `llm`
  (`modules/bundle/desktop`), which is a cross-module change. Both need the owner of the invariant.

Recommendation for the follow-up: inject a neutral resolver fun-interface from the bundle layer, or
drop the redundant Desktop check and rely on the shared catalog, then re-run the scheduled-execution
batches.

## Changes (test files only)

- `GitBranchCheckoutInteractionTest.kt`: new `ComposeUiTest.awaitEnabled(tag)` helper and calls
  before the branch picker (both tests), `git-refresh` (both tests) and `git-checkout-submit`.
  Those readiness waits are bounded at 30 s: a first version used 10 s and still expired for this
  pair under a full-module run even though the classes pass alone and in the eight-class set.
- `ShortcutsInteractionTest.kt`: assert empty `EditableText`; `WindowApp` takes an optional
  `generalSettingsRepository` (declared before `close` so trailing-lambda call sites keep working),
  and the test passes a `GeneralSettingsRepository(MemoryGeneralSettingsStore(), closePolicyAvailable = true)`
  loaded before `setContent`.
- `LocalProjectsInteractionTest.kt`: picker entry tag changed to `local-project-picker-item-<id>`.
- `WorktreeSessionObservationInteractionTest.kt`: `readyObservation` bound 10 s -> 30 s.

## Verification

- Two fixed classes: **8 / 8** — `logs/harness-x1-jvmapp-shortcuts-git.log`.
- Eight related classes: **46 / 46** — `logs/harness-x1-jvmapp-regression-8class.log`.
- Three affected classes in isolation after the fixes: **9 / 10**, only the boundary test still red —
  `logs/harness-x1-jvmapp-three-class-after.log` (`...-before.log` shows 8 / 10 before).
- Full module before the LocalProjects/Worktree fixes: 639 / 647 —
  `logs/harness-x1-jvmapp-full-module.log`.
- Final full-module result after the readiness bounds were widened is recorded in entry Y1
  (`desktop-boundary-fix-2026-10-10/entry-y1`).

## Boundaries

- Test-only. No page render, geometry or product behaviour changed; no screenshot regenerated, so no
  visual claim is made here.
- The probes were temporary and are deleted; their observations are quoted above and asserted by no
  committed test.
- The readiness waits stay bounded at 30 s (a 10 s bound still expired under a full-module run for
  the Git checkout gates), so a genuine regression still fails rather than passing silently.
- The provider coupling at scheduled-execution admission and whether the boundary allowlist changes
  are left to the owner of that invariant; this entry only records the finding.
