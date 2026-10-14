# Terminal reading delivery — R2 review corrections

Released locally: 2026-10-09. Original baseline/report identifier: 2026-10-08.

status=implemented; migration_completed=true; write_set_released=true
model=SHUAIAPI/gpt-6.1-sol; provider=opencodex
builds_run=false; tests_run=false

Model/provider are the parent-designated route; no independent transport probe.

## Exact released hashes

| File | Original before SHA256 | Released after SHA256 |
| --- | --- | --- |
| apps/shared/src/im/hikaru/harness/client/panels/ThreadPanels.kt | a7cc50a9a3ff41f22b4d32b2207109769d2294b5dcdb4007a37c956c3ae21c83 | bc4d243d0f7af1b10ec4835b9ccb1c3776a64f84bf3a235821f2231817f1652e |
| apps/jvm-app/test/im/hikaru/harness/desktop/TerminalReadingInteractionTest.kt | absent (new file) | 24671461e3cc5d59a5ad3844d8cca3ece513f3a2ca56beed31aa35897e37b7fd |

Only TerminalReadingInteractionTest.kt changed this round. Its round-entry SHA was acbd45c5312da54fb9fefa5c35d6a0260d1ec2287a5edcaa9216f4d429b85dab. Production unchanged; all existing dirty work retained.

## R2 corrections

- Screenshot conversion now uses measured horizontalScroll viewport positionInRoot minus actual horizontal axis value exactly once. Ancestor vertical translation is already included. Character rectangles are translated with that inner text origin and intersected with real clipped reader/capture bounds. Full marker visibility assertion retained.
- Geometry schema v3 records horizontalViewportOriginInRoot, horizontalOffsetSubtracted, resulting textOriginInRoot and explicit coordinate contract along with marker/clipped bounds. The conversion agrees with the passed horizontal-wheel test visible window [scroll value, scroll value + viewport width].
- Lifecycle fixture width reduced from 180dp to 120dp; existing controller and wheel interactions retained. Shared measured text-layout helper asserts Running lineCount=1, Exited(Int.MIN_VALUE) lineCount>1, text-layout and semantics height growth, and actual range growth while retaining same-key upward offset. Returning to Running asserts one line, restored height/range and bottom preservation.
- Production source unchanged in this round; SHA bc4d243d0f7af1b10ec4835b9ccb1c3776a64f84bf3a235821f2231817f1652e preserved.

## Verification boundary

Parent reviewed-r2 compiled and ran 9 tests: 7 passed, 2 failed (capturesTerminalReadingMatrix and sameIdentityResizeClampsTheUpReaderAndKeepsTheBottomReaderAtTheTail). Those failures remain retained evidence; this release does not claim they passed.

Log: /tmp/harness-pages-verified-20261008/terminal-reading/reviewed-r2/TerminalReadingInteractionTest/command.log

Worker ran only limited whitespace checks: git diff --check passed with exit 0; both untracked paths passed git diff --no-index --check /dev/null FILE with exit 1 and empty output. No builds/tests or screenshot captures executed.

## Parent verification

Actual new test count: 9.

Classes:

- im.hikaru.harness.desktop.TerminalReadingInteractionTest
- im.hikaru.harness.desktop.ThreadPanelsInteractionTest
- im.hikaru.harness.desktop.HarnessAppThreadPanelsShellTest

Test methods:

- firstLongOutputShowsTheTailThenUpwardScrollSurvivesAppendAndBottomFollowsAgain
- sameKeyLoadingErrorRetryPreservesBothAxesAndIdenticalBufferRevisionDoesNotSteal
- realHorizontalWheelReachesTheEndMarkerAndFullTextIsNotOmitted
- nonPrefixReplacementClampsAndReadyEmptyDiffersFromUnavailable
- tabSwitchPreservesWhileSelectionIdentityChangeAndOwnerChangeReset
- realUpwardScrollDuringPendingFollowCancelsFollowingForLaterAppendsToo
- pendingReadIsFencedByOwnerChangeAndTabClose
- sameIdentityResizeClampsTheUpReaderAndKeepsTheBottomReaderAtTheTail
- capturesTerminalReadingMatrix

Screenshot matrix: 12 planned captures, 320x600 and 640x800 × light/dark × tail/up/horizontal-end. Schema v3 preserves actual marker clipping assertions and records the measured viewport origin and exactly one horizontal subtraction.

## Unresolved verification risks

- Parent must rerun compilation and the nine tests to verify the current test-only changes; no current pass claim is made.
- Parent must confirm the 120dp real lifecycle fixture provides the asserted multiline layout while all retained wheel interactions remain reachable.
- Parent must confirm corrected measured-origin-minus-scroll geometry passes marker visibility/clipping assertions across all 12 planned captures. Production and the seven passing R2 behavioral tests were not modified.

Write set released. Source edits stopped.
