# Entry Y1 — Desktop production boundary restored: model resolution moved to the bundle layer

Date: 2026-10-10. Batch Y. Two production files; resolves the finding recorded in entry X1.

## Problem

Batch X found the only remaining red test in the full `jvm-app` module:
`DesktopProductionBoundaryTest.productionSourceShouldDependOnProfileBundleInsteadOfProviderInternals`.
It failed because `DesktopScheduledSessionEvidence.kt` imported `im.hikaru.harness.llm.llm` and used
it at execution admission to validate a task model. The invariant predates the violation (boundary
test 2026-09-12; the evidence file 2026-10-10 15:55), so this is a real regression rather than a stale
test. Entry X1 recorded it and left the decision here.

## Fix

The app module already depends on `modules/bundle/desktop`, and that module is allowed to touch
provider internals (`DesktopProviderSettings` already calls `context.llm.resolveModelInfo`). So the
single-model lookup moved down into the bundle instead of relaxing the boundary:

- `modules/bundle/desktop/.../DesktopProviderSettings.kt`: new `DesktopModelCatalog` handle with
  `suspend fun requireResolvable(providerId, modelId)` (throws when the pair is unknown), plus
  `requireDesktopModelCatalog(context)`, which requires the local LLM runtime and returns the handle.
- `apps/jvm-app/src/.../DesktopScheduledSessionEvidence.kt`: drops `import im.hikaru.harness.llm.llm`;
  `val llm = context.llm` becomes `val modelCatalog = requireDesktopModelCatalog(context)` and the lookup
  becomes `modelCatalog.requireResolvable(...)`.

Behaviour is preserved deliberately:

- The runtime is still required at the same point in `unavailableReason` (before the model-null check),
  so a context without the LLM service still reports the same generic unavailable message.
- The resolve still throws for an unknown provider/model, and the surrounding `catch` still maps that
  to the same message.
- `resolveModelInfo` is suspending, so the facade method is suspending and the call site is unchanged in
  shape.

## Verification

- Boundary test plus the scheduled-access/evidence consumers:
  `DesktopProductionBoundaryTest`, `LocalScheduledSessionAccessIntegrationTest`,
  `DesktopScheduledSessionEvidenceReadOnlyTest`, `ScheduledCronPageIntegrationTest`:
  **24 / 24**, 0 failed, and `productionSourceShouldDependOnProfileBundleInsteadOfProviderInternals`
  now passes — `logs/harness-y1-jvmapp-boundary-and-scheduled.log`.
- Scheduled-execution consumers: `DesktopScheduledExecutorIntegrationTest` +
  `DesktopScheduledExecutionTest`: **11 / 11**, 0 failed —
  `logs/harness-y2-jvmapp-scheduled-execution.log`.
- Bundle module (`-m desktop`): **29 / 29**, 0 failed — `logs/harness-y3-desktop-bundle-module.log`.
- Full `jvm-app` module: **647 found, 5 skipped, 641 passed, 1 failed** (`104 containers successful`,
  `0 failed`, exit 1). The single non-passing case is
  `PtyTerminalFontInteractionTest.savedFontChangeRefitsSameRunningPtyAtFixedBounds`, which timed out at a
  `waitUntil(timeoutMillis = 10_000)` guarding a real-PTY file round-trip (line 177) under full-suite load
  — an environment-marginal gate, not a behavioural regression, and unrelated to this batch's change. It is
  addressed separately in `verification/pty-terminal-timeout-margin-2026-10-10/entry-z1/`. Every
  boundary/scheduled-execution consumer in this batch's scope is green (24/24, 11/11, 29/29 above) —
  `logs/harness-y1-jvmapp-full-module.log`.

## Boundaries

- The boundary allowlist was not relaxed and no provider import was added to app sources; the coupling
  now sits where the boundary test says it belongs.
- The change is behaviour-preserving by construction, but it is a production change: the verification
  above re-runs the real scheduled-execution consumers rather than only the boundary test.
- Entry X1's own review keeps its "reported, not changed" wording as the record of what that batch did
  at the time; this entry supersedes it.
