# Entry U1 — compact (600dp) More route contract

Date: 2026-10-10. Batch U. Test-only change plus evidence. No production source was modified.

## Why this batch exists

Four earlier batches (O, P, Q, T) each ran a desktop set and each recorded one failure as
"既有陈旧用例": `ScopeNavigationInteractionTest.compactMoreReachesPluginsAndScheduledTasks`.
It asserted that the compact (600dp) More route into Plugins mounts `plugins-navigator`. No batch
fixed it, and none of them checked whether the fault was the test or the shell.

At the same time `PluginsPageInteractionTest.compactMoreRouteDismissesDrawerAndExplicitOpenSurvivesRefresh`
asserts the opposite for the same scenario (600dp, More to Plugins): `plugins-navigator` must NOT
exist, and the shell affordances (`Toggle sidebar`, `bottom-nav-drawer`) open it on demand. The two
tests are mutually exclusive, so exactly one of them is wrong.

## What was measured

A throwaway probe (`probe/TmpCompactDrawerProbeTest.kt`, removed from the tree after it ran) mounted
the production `HarnessApp` at `requiredWidth(600.dp)` with `connection = null` and, for every route,
listed which navigator/overlay tags were mounted. Full output in
`logs/harness-u1-compact-probe.log`; the re-runnable check is
`measurement/measure-compact-more-route.py` (PROBLEMS: 0).

| Entry | Route | navigator mounted | overlay + scrim |
| --- | --- | --- | --- |
| initial | Harness (Workspace) | `workspace-navigator` | yes |
| initial | Settings | `settings-navigator` | yes |
| initial | ScheduledTasks | `scheduled-navigator` | yes |
| initial | PluginInventory | none | no |
| More | Plugins | none | no |
| More | Scheduled | `scheduled-navigator` | yes |
| More | Hosts | `hosts-navigator` | yes |
| More | Settings | `settings-navigator` | yes |

So at compact width the Workspace, Settings, Scheduled and Hosts routes open their floating drawer
above the content, and only the Plugins route exposes its content and keeps the page-owned navigator
closed. That asymmetry is not accidental: it comes from `dismissFloatingPluginsNavigator` plus the
`LaunchedEffect(page, shellWidthPx != null)` in `HarnessApp.kt`, whose comment says "Expose plugin
content on route entry ... an explicitly opened drawer stays open", and it is the exact contract that
`PluginsPageInteractionTest` covers in detail. `shell-width-classes.md` describes the same shape for
the compact shell: a drawer above the content, "opened from the bottom bar entry or the title-strip
toggle" rather than covering the content on entry.

## Decision

`PluginsPageInteractionTest` is the newer, more specific, deliberately authored contract (added in
`main-path-and-plugins-2026-10-08`, after `ScopeNavigationInteractionTest` was written in the
2026-10-07 scope batch). The Plugins production behavior is deliberate and covered by a dedicated
test, so the stale expectation belongs in `ScopeNavigationInteractionTest`.

`ScopeNavigationInteractionTest.compactMoreReachesPluginsAndScheduledTasks` was updated in place to
the measured contract and keeps its original intent (compact More reaches both destinations):

- More to Plugins: `plugins-search` displayed, `plugins-navigator` absent, then `bottom-nav-drawer`
  opens `plugins-navigator` and toggling it again closes it.
- More to Scheduled: `scheduled-navigator` displayed, `scheduled-toggle-search` reveals
  `scheduled-search`, `rail-overflow-menu` absent.

No production source changed, so no capture was regenerated and no other page's behavior moved.

## Changed files

- `apps/jvm-app/test/im/hikaru/harness/desktop/ScopeNavigationInteractionTest.kt` (one test body).

## Verification

- Targeted 8-class set (`ScopeNavigation`, `PluginsPage`, `ScheduledCronPageIntegration`,
  `ScheduledPageInteraction`, `ScheduledManagementInteraction`, `PluginRouting`,
  `ScheduledPageRoutingInteraction`, `ScheduledRoutingInteraction`):
  **43 found / 41 successful / 2 failed**. `compactMoreReachesPluginsAndScheduledTasks` passes.
  The 2 failures are `ScheduledPageRoutingInteractionTest.harnessRouteEditsBoundTaskAndPersistsPausedConfigurationWithoutExecution`
  and `ScheduledRoutingInteractionTest.editingBoundTaskPersistsAcrossStoreInstancesWithoutExecutingSession`,
  both `scheduled-detail-prompt` not found at 1440x900.
- Those 2 are pre-existing and order-dependent, not a regression:
  - same 8-class set re-run: same 2 failures (`logs/harness-u1-jvmapp-rerun.log`);
  - the 8-class set with the changed class removed (7 classes, 40 tests): **38 / 40**, the identical
    2 failures (`logs/harness-u1-jvmapp-no-scope.log`) — so the changed class is not the trigger;
  - the 2 classes alone: **5 / 5**, 0 failures (`logs/harness-u1-routing-isolated.log`).
- the changed class alone: **3 / 3**, 0 failures (`logs/harness-u1-scope-isolated.log`).
- Logs: `logs/harness-u1-jvmapp-8class.log`, `logs/harness-u1-jvmapp-rerun.log`,
  `logs/harness-u1-jvmapp-no-scope.log`, `logs/harness-u1-routing-isolated.log`,
  `logs/harness-u1-scope-isolated.log`, `logs/harness-u1-compact-probe.log`.

## Boundaries

- This batch resolves the contradiction between two tests; it does not change what any page renders.
- The compact shell is this project's own decision (no Codex reference capture exists below the
  desktop sizes), so the asymmetry between Plugins (content first) and the other four routes
  (drawer open on entry) is recorded as designed behavior, not asserted against a reference.
- The 2 `scheduled-detail-prompt` failures are pre-existing and left untouched; they are order
  dependent in multi-class runs and pass in isolation. They are unrelated to this batch and still
  need their own investigation.
