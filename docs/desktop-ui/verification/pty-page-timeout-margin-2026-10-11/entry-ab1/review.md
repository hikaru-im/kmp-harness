# Entry AB1 — PTY page interaction test: real-IO gates widened to 30 s (test-only)

Date: 2026-10-11. Batch AB. One test file; no production change.

## Problem

The batch AA full `jvm-app` run failed exactly one case,
`PtyTerminalPageInteractionTest.focusedTypingPasteKeysAndResizeUseRealController`, with
`ComposeTimeoutException: Condition still not satisfied after 10000 ms` at line 122 — a `waitUntil`
that waits for a real PTY to write back the `size-trigger.hex` / `kernel-size` files, the same gate
batch Z widened in the sibling `PtyTerminalFontInteractionTest`. The class passes alone, so the 10 s
ceiling is marginal under full-suite load rather than a behavioural failure, and that test renders no
settings page, so batch AA's production change is not implicated.

## Change

Widen every marginal real-IO readiness gate in the file from `timeoutMillis = 10_000` to
`timeoutMillis = 30_000` — 23 sites, matching the batch Z precedent. Only the ceiling changes; every
assertion after each gate is untouched, so a real regression still fails at 30 s instead of passing
silently. `measurement/verify-timeout-margin.py` re-checks 23 widened gates and 0 remaining 10 s gates.

## Verification

- Class alone before the change (isolation of the AA failure): **7 / 7** —
  `../settings-control-geometry-2026-10-11/entry-aa1/logs/harness-aa-pty-page-isolation.log`.
- Class alone after the change: **7 / 7**, 0 failed — `logs/harness-ab-pty-page-class.log`.
- `measurement/verify-timeout-margin.py`: **PROBLEMS: 0**.

No full-module re-run was taken for this entry: the batch AA full-module log predates it and already
isolates this exact gate, and the change is a pure wait ceiling with unchanged assertions.

## Boundaries

- Test-only change; no production source touched, no screenshot regenerated, no pixel parity claimed.
- The bounds stay finite (30 s), so a genuinely broken PTY path still fails.
- This widens an existing environment-marginal gate; it does not add coverage.

