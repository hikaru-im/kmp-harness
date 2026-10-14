# Harness Menu Picker Migration — 2026-10-08

- status: implemented
- migration_completed: true
- write_set_released: true
- tests_run: false
- builds_run: false
- Actual model: SHUAIAPI/gpt-6.1-sol; identity: gpt-6.1-sol; provider: opencodex.
- Changed files: 9; new test methods: 23 (model 1, foundation interaction 10, desktop migration 12).

## Implementation

- LocalProjectPicker uses typed entries and mounted anchor; project ID keys support duplicate names; default and management entries retain existing services and owner/readiness guards.
- GitPageSelect uses captured ordered option values and lifetime fences; deferred callbacks cannot translate stale indices against later options; outer GitSessionDirectoryPicker guards retained.
- Typed HarnessMenu dispatch waits for actual popup content disposal and uses latest callbacks; disabled, removed, reordered, owner-changed, disposed and stale retained selections are rejected.
- Popover narrow parameters provide popup modifier/width/maxHeight and wrapped labels while retaining short-menu defaults, window margins, RTL and flip positioning.
- Git 560dp width cap, 320dp scrolling, full wrapped labels, long selected-path reader, content lifecycle and conditional Initial/Final pointer diagnostics retained.

## Changed files and SHA256

Before hashes refer to preserved task-start files, including pre-existing dirty/untracked work. A null before hash denotes the new test file.

| File | Before SHA256 | After SHA256 |
| --- | --- | --- |
| apps/shared/src/im/hikaru/harness/client/page/LocalProjectsPage.kt | 809fd3db405214640d05b03a5a557573191bc1683a029fe08ec61a4fced47237 | 8b84a09bb4958b2b04853370e5ad3ea1c04e4e0ae6316024c48d7e485c783a06 |
| apps/shared/src/im/hikaru/harness/client/git/GitPageComponents.kt | bde93e835c5e5015e2646da14f4811dc1f9c65b47c800d157dada7b36870bd9e | 7d2d4f1a4885725d68e08ca43bef18231a0349ceb8089e5b4b383bcbf9edf7d9 |
| modules/ui-foundation/src/im/hikaru/harness/ui/foundation/HarnessOverlays.kt | 4e7a56c90cc20252c5f3da2064cc8e23d9c76e20647802e1f00b72881133e14c | 9ba681a28fc7ea5e07f0b9914dd40b33abb733f9c06fd5ed8c04c088f91ac207 |
| modules/ui-foundation/src/im/hikaru/harness/ui/foundation/HarnessComponentModels.kt | b038444579ea4aaa332f87a90e4f8c2f0284632774483fb37a4f5010975ba721 | 59e49798358f68ffe0ef18f1873bb43c35c24f9f3db6342c9aa1003500386617 |
| modules/ui-foundation/test/im/hikaru/harness/ui/foundation/HarnessComponentModelsTest.kt | acce84dc01f8cecfd09f247978eab6843e630a3fa6133307329b6fbcaf4c0409 | 10666264f401b2bfb6a7890e936d349bc05b1c44d4ffa6300290e949088670f2 |
| apps/jvm-app/test/im/hikaru/harness/desktop/ProjectsRecentInteractionTest.kt | 0b5fa7c7b9c948b7c8af8621e58a882e123f2cb34c407f28c953cdc14206b74a | 28fb4b20a71f267a81a91ed85934e9911d080c15f713658c1f7d52742c15efbd |
| apps/jvm-app/test/im/hikaru/harness/desktop/MenuPickerMigrationInteractionTest.kt | null (new file) | f7fb80832f1c767b112d557e5bdb97e45011fbda993db83f0845a09ce10d1bd2 |
| modules/ui-foundation/test/im/hikaru/harness/ui/foundation/HarnessComponentInteractionTest.kt | efbe510e556dc1d90127f1eac967987e752d78f9b8e250e75f09f91ba03c6d1d | 7e16be355c74a059c6c55e63bb91b53261dc7a0c2868fb3a4379cae0005dd4f4 |
| apps/jvm-app/test/im/hikaru/harness/desktop/ProjectsRecentScreenshotTest.kt | 115c3ba38af603d3087e17d04b05dffa4e1fe258be7115f659ebb6f76d013192 | 624037bf58eaca5fd31fd5cf9cb18ea37b2c0c3d704b3dff2f4d38be7765798a |

## Added tests

### modules/ui-foundation/test/im/hikaru/harness/ui/foundation/HarnessComponentModelsTest.kt (1)

- queuedMenuSelectionRequiresTheSameOrderedSelectableSnapshot

### apps/jvm-app/test/im/hikaru/harness/desktop/MenuPickerMigrationInteractionTest.kt (12)

- popupPickers600Light
- popupPickers600Dark
- popupPickers1440Light
- popupPickers1440Dark
- gitQueuedSelectionRejectsDisabled
- gitQueuedSelectionRejectsRemovedOption
- gitQueuedSelectionRejectsReorderedOptions
- gitQueuedSelectionRejectsChangedOwner
- gitQueuedSelectionRejectsDisposedOwner
- retainedGitIndexCannotBeTranslatedIntoReorderedOptions
- valueEqualOrderedGitOptionsAcceptTheLatestBusinessCallback
- tinyWindowClampsWideWrappedGitPopupAndStillScrolls

### modules/ui-foundation/test/im/hikaru/harness/ui/foundation/HarnessComponentInteractionTest.kt (10)

- menuMouseDispatchObservesContentDisposalAndLatestCallback
- queuedMenuSelectionRejectsDisabledOwner
- queuedMenuSelectionRejectsDisabledEntry
- queuedMenuSelectionRejectsRemovedOption
- queuedMenuSelectionRejectsReorderedOptions
- queuedMenuSelectionRejectsChangedOwner
- queuedMenuSelectionRejectsDisposedOwner
- retainedMenuCallbackCannotSelectAfterPopupReopens
- menuRealKeyboardNavigatesSkipsAndActivatesOrDismisses
- menuOutsidePointerDismissesWithoutDispatch

## Verification and parent handoff

- Restricted git diff --check for the 9 changed files passed (exit 0).
- Baseline git diff --no-index --check checks emitted no whitespace diagnostics. Exit 1 reports file differences.
- Static guard preservation was checked against the captured baseline.
- No build/test ran and no screenshots were generated. Compilation, runtime input behavior and pixel output remain unverified.
- Source/test write set is released; only these handoff reports were written after release.

### Suggested parent test classes

- im.hikaru.harness.ui.foundation.HarnessComponentModelsTest
- im.hikaru.harness.ui.foundation.HarnessComponentInteractionTest
- im.hikaru.harness.desktop.MenuPickerMigrationInteractionTest
- im.hikaru.harness.desktop.ProjectsRecentInteractionTest
- im.hikaru.harness.desktop.GitPageInteractionTest
- im.hikaru.harness.desktop.GitSettingsRouteInteractionTest
- im.hikaru.harness.desktop.ProjectsRecentScreenshotTest
- im.hikaru.harness.desktop.GitWorktreesScreenshotTest

### Captures and validation details

- The new matrix covers 600x800 and 1440x900, light/dark, for both production pickers: expected 8 independent popup-root PNG captures plus geometry artifacts when executed.
- Capture directory: HARNESS_MENU_PICKER_CAPTURE_DIR, default /tmp/harness-menu-picker-captures-20261008.
- Freeze sources and run compilation/tests serially. This worker did not run any build or test.
- Validate new desktop test compilation: test-only INVISIBLE_MEMBER/INVISIBLE_REFERENCE suppression accesses existing internal GitPageSelect without changing production visibility.
- Run real pointer/wheel/keyboard regressions and inspect independent popup-root captures and window geometry; no runtime or pixel parity is claimed.
- Existing dirty files preserved; reports reflect changes relative to captured task-start files, not HEAD. No source/test writes after release.

## Parent-review correction — 2026-10-08

- LocalProjectPicker now explicitly passes wrapLabels = true to typed HarnessMenu.
- All four existing popup matrix methods use the same moderately long name for two distinct project IDs. Each item must provide full matching TextLayoutResult, multiple lines, no visual overflow or ellipsized line, and a final line ending at the complete name length.
- ID selection, default directory, management callback, independent popup-root screenshots and geometry are retained.
- Correction touches only the production picker parameter and its existing narrow matrix test. No extra test method was added: total new methods for this migration remain 23 (1 model + 10 foundation interaction + 12 desktop migration), with 4 matrix methods enhanced.
- Restricted git diff --check and baseline whitespace checks passed again. No build/test was run; screenshots remain ungenerated. Source/test write set is released.

### Correction hashes relative to previous release

| File | Before correction SHA256 | After correction SHA256 |
| --- | --- | --- |
| apps/shared/src/im/hikaru/harness/client/page/LocalProjectsPage.kt | 8298597af0f25d6304f5ab7f5340780e10bae7dcdc2723e3bcc3374e7c69f709 | 8b84a09bb4958b2b04853370e5ad3ea1c04e4e0ae6316024c48d7e485c783a06 |
| apps/jvm-app/test/im/hikaru/harness/desktop/MenuPickerMigrationInteractionTest.kt | b98bf86bbfb042802cbd9de46482fac04fec9a76aeb6f9ae1a5b0f4656ded40e | f7fb80832f1c767b112d557e5bdb97e45011fbda993db83f0845a09ce10d1bd2 |

## Parent-build compile correction — 2026-10-08

- Added the missing androidx.compose.ui.test.click import to the existing real-root performMouseInput helper in HarnessComponentInteractionTest.kt.
- No production file, test body, input method, root selection, or contract was changed. No new test method was added; total new methods remains 23.
- Restricted git diff --check passed. The parent build error was not rerun here; tests and builds remain false.
- Write set is released after this correction; no further source edits are planned.

| File | Before correction SHA256 | After correction SHA256 |
| --- | --- | --- |
| modules/ui-foundation/test/im/hikaru/harness/ui/foundation/HarnessComponentInteractionTest.kt | a55f85882e2879ec2e343278422fe24e2f51a2865ceb420cfe79cdeedfce0552 | 7e16be355c74a059c6c55e63bb91b53261dc7a0c2868fb3a4379cae0005dd4f4 |

## Git observer conditional-lambda correction — 2026-10-08

- Changed only onContentApplied to use an explicit zero-argument lambda: if (observer == null) null else { -> record("menu-content-applied") }.
- Observer gating, record body, diagnostics and event order are preserved. A reverse replacement reproduces the prior released file SHA exactly.
- Restricted git diff --check and baseline whitespace checks passed. No build or test was run by this worker; runtime compilation remains for parent validation.
- The 9-file final hash manifest is current; total new tests remains 23. Source write set released and no further source writes will follow.

| File | Before correction SHA256 | After correction SHA256 |
| --- | --- | --- |
| apps/shared/src/im/hikaru/harness/client/git/GitPageComponents.kt | 1562fc2feda0fca8cf63a159576abf140a7bc23da9107e236f5f1ae4018fd050 | 914f260c86973c3feef0bb55d343010e8907812526fe69f317339ac7d78e0da2 |

## Retained Git index after reorder — 2026-10-08

- Parent XML locates the failure at MenuPickerMigrationInteractionTest.kt:201: calls.isEmpty(). It is a real unexpected business callback; the test does not assert that the retained action returns true.
- Same labels and index keys allow a clickable modifier node to survive the options reorder. Its retained semantics action can invoke the updated click handler.
- The production GitPageSelect now encloses HarnessMenu in key(optionsLifetime). Option/value lifetime changes remove the old popup/clickable nodes; value-equal ordered options keep their lifetime. Existing popup disposal guards reject retained actions.
- Only this GitPageSelect wrapper changed. All foundation files, external owner guards, callbacks, observer code, and tests are unchanged; rejection assertions and subsequent real pointer/controller selection remain intact.
- Parent source-manifest hashes match the prior released source and unchanged test. Exact reverse transformation reproduces the previous GitPageComponents.kt hash.
- Restricted diff and baseline whitespace checks passed. No build/test run by this worker; parent needs to verify this correction. Total new test methods remains 23. Write set released.

| File | Before correction SHA256 | After correction SHA256 |
| --- | --- | --- |
| apps/shared/src/im/hikaru/harness/client/git/GitPageComponents.kt | 914f260c86973c3feef0bb55d343010e8907812526fe69f317339ac7d78e0da2 | 7d2d4f1a4885725d68e08ca43bef18231a0349ceb8089e5b4b383bcbf9edf7d9 |
