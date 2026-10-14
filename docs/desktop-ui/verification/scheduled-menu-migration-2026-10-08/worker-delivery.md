# ScheduledPagePicker typed HarnessMenu migration — 2026-10-08

status=implemented; migration_completed=true; write_set_released=true; tests_run=false; builds_run=false

Model: SHUAIAPI/gpt-6.1-sol (engine gpt-6.1-sol); provider: opencodex. Session identity retained from prior verified handoff; no switch performed.

## Changes

- Typed mounted HarnessMenu anchor uses HarnessButton; tags remain unchanged.
- Ordered copied options and key(token) preserve index meaning and invalidate fence/enabled/value/options/disposal lifetimes.
- Latest enabled/options/onSelect are checked before dispatching the captured option; equal ordered value lists remain compatible.
- Uses shared popup removal-before-dispatch contract; parent page service guards remain untouched.
- Preserves min(parent,520dp) width, 280dp height, complete wrapped labels, selected value horizontal reader and copy control.

## File SHA256

| File | Before | After | Changed |
| --- | --- | --- | --- |
| apps/shared/src/im/hikaru/harness/client/scheduling/ScheduledPageLayout.kt | b308533e19e173d1f853e405a82d9b53b3b643de1f6a675db07861f598870c8e | 2df88ab45a611d14d168cb6a472b1174d000f90c5e40fea6cf07dd3c3e29b30f | true |
| apps/jvm-app/test/im/hikaru/harness/desktop/ScheduledPickerMigrationInteractionTest.kt | null (new file) | 3b809c8b25c93b391726ee28473360dedff04740b34914cc6f647aec12d3c46a | true |
| apps/jvm-app/test/im/hikaru/harness/desktop/ScheduledManagementInteractionTest.kt | 055ae9349ff8ac34a18d24a57a8a2b8752a579f551e5ff06be4a9c0576462cea | 055ae9349ff8ac34a18d24a57a8a2b8752a579f551e5ff06be4a9c0576462cea | false |
| apps/jvm-app/test/im/hikaru/harness/desktop/ScheduledPageTestSupport.kt | 2065de687ebe529b41468fb8b1ee5370304431509411da912eab2204c29a890d | 2065de687ebe529b41468fb8b1ee5370304431509411da912eab2204c29a890d | false |

## Added tests

Class: im.hikaru.harness.desktop.ScheduledPickerMigrationInteractionTest; exactly 14 new @Test methods.

- popup600Light
- popup600Dark
- popup1440Light
- popup1440Dark
- fourEditorPickersUpdateTheRealDraft
- queuedSelectionRejectsDisabled
- queuedSelectionRejectsRemovedValue
- queuedSelectionRejectsReorderedValues
- queuedSelectionRejectsChangedFence
- queuedSelectionRejectsDisposedOwner
- queuedSelectionRejectsChangedSelectedValue
- retainedIndexCannotSelectAReorderedDuplicateLabel
- equalOrderedValuesDispatchTheLatestCallbackAfterDisposal
- keyboardNavigationAndOutsideDismissalPreserveSelection

## Parent verification

Freeze the released sources and compile/test serially. No worker, build or test was launched by this implementation.

- im.hikaru.harness.desktop.ScheduledPickerMigrationInteractionTest
- im.hikaru.harness.desktop.ScheduledPageInteractionTest
- im.hikaru.harness.desktop.ScheduledManagementInteractionTest

Expected capture artifacts: 4 real independent-popup-root PNG files and 4 geometry JSON files; actual generated count: 0. Environment: HARNESS_SCHEDULED_PICKER_CAPTURE_DIR. Default directory: /tmp/harness-scheduled-picker-captures-20261008.

- scheduled-600x800-light.png
- scheduled-600x800-dark.png
- scheduled-1440x900-light.png
- scheduled-1440x900-dark.png

## Static checks and limitations

- Restricted git diff --check passed. Baseline no-index --check produced zero whitespace diagnostics (exit 1 denotes actual differences).
- Only picker body differs in production; existing ScheduledManagementInteractionTest and ScheduledPageTestSupport SHA256 remain unchanged.
- New normal mouse/wheel/key actions use measured-root real input, with 10-second deadline assertions; retained OnClick is deliberate stale injection only.
- Baseline diff: /tmp/harness-scheduled-menu-migration-20261008.diff

- No compilation, build, test, screenshot generation or pixel comparison performed.
- Parent must serially compile internal production access and SemanticsNode.layoutInfo.isAttached assertions.
- Parent must verify paused-frame pointer queue timing on its Skiko dispatcher; tests explicitly assert popup still attached and callback absent before invalidation.
- Real wheel helpers are reused unchanged; parent should validate page reveal and popup/reader scroll behavior across window sizes.

Source write set released before report publication. No source writes remain.
