# Tooltip + RailNavItem delivery — 2026-10-08

status=implemented; write_set_released=true; tests_run=false; builds_run=false.
Actual model: SHUAIAPI/gpt-6.1-sol. Actual provider: opencodex. Verified from current rollout metadata.

## Implemented

- HarnessTooltipTrigger: nonfocusable anchored Popup, default hover 200ms, immediate keyboard focus, physical trigger Modifier
- Lifetime invalidation for text/enabled/scope/suppression/dispose; held hover/focus does not reopen after cancellation
- Legacy HarnessTooltip signature retained with explicit positional compatibility test and separate new API name
- RailNavItem keyed by item ID; page/workspace scope and account/More/overlay/panel suppression; original callbacks/icons/geometry retained
- Existing placement flip/clamp/RTL helper reused without editing overlays; complete wrapped text and bounded scroll

## Changed files and SHA256

| File | Before SHA256 | After SHA256 |
| --- | --- | --- |
| modules/ui-foundation/src/im/hikaru/harness/ui/foundation/HarnessTooltip.kt | None | 11f4e78267df56abc44594a53be27a2004f4944c36830d2966db50f27adfd915 |
| modules/ui-foundation/src/im/hikaru/harness/ui/foundation/HarnessComponents.kt | cf6f0088ed8bc1b7195fe035221907ea75ae6c2e44376868936e342a95994a5c | a5841c9d24d9870c98bd3d08139ffecd721947c915031fbcb38c9f3e403bdcfb |
| apps/shared/src/im/hikaru/harness/client/shell/AppShell.kt | 0e60b34f0b59ee15a1dad7bec824ed1bc853c748c067aaf270cb6b3dd130a963 | 8a5f70d179576950f6795d0cb0608af2c50abeb25552124cfe40f762ceb3cdd4 |
| apps/jvm-app/test/im/hikaru/harness/desktop/TooltipInteractionTest.kt | None | 040b3a6e5194a5b2ef8a42d673aca54ad2c49080905e9d57ef9a7e20a5c08578 |

Original immutable baseline: /tmp/harness-tooltip-before-20261008/hashes.json

## Static checks

- Limited git diff --check passed. Frozen candidate git diff --no-index --check returned 1 for differing bytes with no whitespace diagnostics.
- Static comparison confirmed exactly one four-line insertion in TooltipInteractionTest.kt. All three production delivery files match the frozen parent candidate. No helper/other source changes in this correction.

## Parent verification before this correction

- layout-api-fix candidate compiled successfully. 29 tests: 28 passed, 1 failed, 0 errors, 0 skipped.
- Failure: tinyRtlEndTooltipClampsAndRealWheelReadsFullText — Reader must expose its end at PluginsPageTestSupport.kt:322.
- Evidence directory: /tmp/harness-pages-verified-20261008/tooltip-rail/layout-api-fix/TooltipInteractionTest
- Parent generated four independent Popup PNGs and four geometry JSON files: 600x800 and 1440x900, light/dark. Capture directory: /tmp/harness-tooltip-captures-20261008-layout-api-fix

## Real-wheel clock correction

- Reader remained mounted in input root 551 (reader 556), bounds [8, 8, 152, 192], viewport 144 x 184, maximum scroll 4148px. Real wheel moved its position from 0px to 6px.
- Evidence supports a paused-clock animation issue: mainClock.autoAdvance=false and the shared helper advances only one frame before reading the end. No root/hit/geometry failure was observed. Diagnosis and correction remain pending parent runtime verification.
- Four inserted lines enable automatic clock advancement before the unchanged real-wheel helper and print TOOLTIP_READER_CLOCK autoAdvance=true. Full text, multiline, no-ellipsis, geometry, end and 10-second assertions remain intact. No semantic scroll/click, swallowed failure or skipped assertion.
- Before this fix: 16cc704575568790507141578903d6c49d353c5882ba37355b4b7ed8ffd27c1a
- After this fix: 040b3a6e5194a5b2ef8a42d673aca54ad2c49080905e9d57ef9a7e20a5c08578
- Production/helper files unchanged. Original baseline preserved. Added cases in this fix: 0; total new Tooltip cases: 29.

## Test compilation API correction history

- Earlier correction replaced two unavailable layout helpers with performSemanticsAction(SemanticsActions.GetTextLayoutResult) and the necessary import. All assertions were preserved. Parent compilation and 28 passing runtime cases verified this preceding candidate.

## Parent serial verification

- im.hikaru.harness.desktop.TooltipInteractionTest
- im.hikaru.harness.desktop.ScopeNavigationInteractionTest

29 test methods; rerun TooltipInteractionTest with the current correction first.
Captures use independent Popup root, PNG SHA256/dimensions, window/popup/anchor/root bounds and root IDs. HARNESS_TOOLTIP_CAPTURE_DIR overrides /tmp/harness-tooltip-captures-20261008.

### New cases

- hoverWaitsFor200MillisAndLeaveCloses
- earlyLeaveCancelsPendingHover
- unmountCancelsPendingAndOpenTooltip
- tabFocusOpensImmediatelyAndPopupAddsNoTabStop
- escapeDoesNotReopenUnderHeldFocusOrHover
- clickDismissesAndDispatchesBusinessIdentityExactlyOnce
- enterAndSpaceRetainTheFocusedTriggerBusinessAction
- contextPressDismissesWithoutDispatchingPrimaryAction
- tooltipDisabledNullAndBlankStillAllowTriggerBusinessInput
- pendingTextChangeCancelsOldIntent
- openTextChangeCancelsOldContent
- pendingDisabledCancelsOldIntent
- openDisabledCancelsOldContent
- pendingNullCancelsOldIntent
- openBlankCancelsOldContent
- pendingScopeAToBToACancelsOldIntent
- openScopeAToBToACancelsOldContent
- pendingOverlaySuppressionCancelsOldIntent
- openOverlaySuppressionCancelsOldContent
- focusedContentChangeRequiresBlurAndRefocus
- actualShellNavigationAndMoreKeepRailGeometryAndIdentity
- railKeyboardUsesActualItemAndDisabledItemStaysDisabled
- legacyVisibleSignatureUsesPopupWithoutLayoutShift
- popup600Light
- popup600Dark
- popup1440Light
- popup1440Dark
- rtlLogicalEndFlipsBesideLeftEdgeTrigger
- tinyRtlEndTooltipClampsAndRealWheelReadsFullText

## Limitations

- This worker ran no builds or tests. Parent compiled and ran the preceding layout-api-fix candidate: 29 tests, 28 passed, 1 failed. The current four-line wheel-clock correction requires parent serial verification.
- Parent produced four Popup PNGs and four geometry JSON files for the preceding candidate. This worker generated no captures and makes no pixel parity claim.

Source write set is released. No further source writes after this delivery.
