# Terminal reading slice delivery — 2026-10-08

status=implemented; migration_completed=true; write_set_released=true
tests_run=false; builds_run=false

Actual model: SHUAIAPI/gpt-6.1-sol. Provider: opencodex (parent-supplied route; no independent transport probe).

## Write set and hashes

| Path | before_sha256 | after_sha256 |
| --- | --- | --- |
| apps/shared/src/im/hikaru/harness/client/panels/ThreadPanels.kt | a7cc50a9a3ff41f22b4d32b2207109769d2294b5dcdb4007a37c956c3ae21c83 | e951b88bc6c5234da0044c5c09faeacfef8ec4eca80925adceccd73d001243aa |
| apps/jvm-app/test/im/hikaru/harness/desktop/TerminalReadingInteractionTest.kt | null (absent) | 69f3fd597b0e7fc59aab10db4e76d0ca8c1950d2fd48e9346a2fcb1ad019e651 |

The ThreadPanels.kt before value is the parent baseline from /tmp/harness-terminal-reading-baseline-20261008.json; it equals the frozen base input hash for that path. No other file was written.

## Production changes (ThreadPanels.kt, BackgroundOutputPanel and its display state only)

1. First display. ScrollState.maxValue starts at Int.MAX_VALUE, so "value >= max - 1" was false on the first Ready and the tail was never shown. TerminalReadingState now carries an explicit measured flag that is set only after a real content placement; a first display is treated as at-the-bottom.
2. Real layout signal. onGloballyPositioned moved inside verticalScroll, so a content remeasure (append, replacement, reflow) or a viewport resize bumps the layout version the follow effect waits on. The two-frame budget remains only as a guard.
3. Whole pending window. The effect performs one suspend wait and then one decision, so nothing scrolls the axis before the decision; an upward wheel anywhere in that window is observed from the live value. A smaller range is the system clamp on shrink/resize and is excluded from the cancel signal. A later append re-evaluates from its own pre-change baseline and stays put while the reader is above the bottom. terminalGeneration/revision remain excluded from reading identity.
4. State shape. Public BackgroundOutputPanel(load, modifier) is unchanged and delegates to an internal overload; layoutVersion/measured are plain fields read only by the effect, so a placement cannot trigger recomposition. No new public TerminalReadingState API.

Preserved: scope + ownerGeneration + full BackgroundTerminalKey identity, active dispose, loading/error axis retention, non-prefix clamp, same-key Loading/Error retention, Subagents tab behaviour, long-line horizontal scrolling, SelectionContainer, truncated notice, "No output yet" only for a Ready empty buffer.

## New suite (9 tests, class im.hikaru.harness.desktop.TerminalReadingInteractionTest)

- firstLongOutputShowsTheTailThenUpwardScrollSurvivesAppendAndBottomFollowsAgain
- sameKeyLoadingErrorRetryPreservesBothAxesAndIdenticalBufferRevisionDoesNotSteal
- realHorizontalWheelReachesTheEndMarkerAndFullTextIsNotOmitted
- nonPrefixReplacementClampsAndReadyEmptyDiffersFromUnavailable
- tabSwitchPreservesWhileSelectionIdentityChangeAndOwnerChangeReset
- realUpwardScrollDuringPendingFollowCancelsFollowingForLaterAppendsToo
- pendingReadIsFencedByOwnerChangeAndTabClose
- sameIdentityResizeClampsTheUpReaderAndKeepsTheBottomReaderAtTheTail
- capturesTerminalReadingMatrix

Corrections applied after parent review. The wide fixture now ends with WIDE-END-MARKER and the horizontal-end assertion uses the laid-out text result (full buffer in one node, no ellipsis, final character laid out) plus the marker's right edge relative to the visible window before and after the wheel. Appends are true suffix appends so the range strictly grows, including the pending-follow case. The harness opens the Subagents tab and readOverview returns a real Empty state; closing BackgroundOutput keeps Subagents open, so "Panels closed" is asserted only when every tab is closed. Process id and producer incarnation each switch through the real dropdown (inventory refreshed first; the retained stale selection reports an Error with the new inventory, and the new key is then selected from the menu). Retry is a real "Retry output" click. awaitTerminalSettled requires at least 8 real frames plus three equal samples instead of accepting two samples immediately. The pending-follow window is exercised with a paused frame clock so no frame separates publication from the wheel.

All movement uses real performMouseInput (wheel and click) and real node geometry with 10-second deadlines; no performClick/performScrollTo/scrollTo substitutes, no silent skips, no swallowed exceptions.

## Checks

git diff --check restricted to the write set: exit 0, no output. Per-file git diff --no-index --check /dev/null FILE: no whitespace errors (exit 1 only reflects differences). No builds or tests were run.

## Parent verification request

Run, in order: im.hikaru.harness.desktop.TerminalReadingInteractionTest (9 new tests), im.hikaru.harness.desktop.ThreadPanelsInteractionTest, im.hikaru.harness.desktop.HarnessAppThreadPanelsShellTest.

## Limits

No build or test was executed, so the candidate is uncompiled and unexecuted and no screenshot was captured (12 planned through HARNESS_TERMINAL_READING_CAPTURE_DIR, default /tmp/harness-terminal-reading-captures-20261008). Marker visibility is proven from layout and scroll-range geometry, not from raster ink or original pixel parity. Producer/consumer contract, PTY resize, ANSI, live subscription and subagent history remain out of this slice.

Write set released; no further writes.
