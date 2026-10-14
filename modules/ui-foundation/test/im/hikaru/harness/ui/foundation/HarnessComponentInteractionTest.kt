package im.hikaru.harness.ui.foundation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.TimeSource

@OptIn(ExperimentalTestApi::class)
class HarnessComponentInteractionTest {
    @Test
    fun menuMouseDispatchObservesContentDisposalAndLatestCallback() = runComposeUiTest {
        var expanded by mutableStateOf(false)
        var callbackVersion by mutableStateOf(0)
        var mounted = false
        val dispatched = mutableListOf<Pair<String, Int>>()
        setContent { HarnessTheme {
            val version = callbackVersion
            HarnessMenu(expanded, listOf(HarnessMenuEntry.Item("id-b", "Duplicate", selected = true)),
                onExpandedChange = { expanded = it; if (!it) callbackVersion++ },
                onSelect = { assertFalse(mounted, "Actual popup content must be disposed before business dispatch"); dispatched += it to version },
                onContentMountChange = { mounted = it },
                popupModifier = Modifier.testTag("migration-popup"),
                anchor = { toggle -> HarnessButton("Open", toggle, testTag = "migration-anchor") })
        } }
        menuPointerClick("migration-anchor")
        runOnIdle { assertTrue(mounted, "Actual popup content must have mounted") }
        onNodeWithTag("menu:id-b").assertIsSelected()
        val popupRoot = generateSequence(onNodeWithTag("migration-popup", true).fetchSemanticsNode()) { it.parent }.last().id
        val anchorRoot = generateSequence(onNodeWithTag("migration-anchor").fetchSemanticsNode()) { it.parent }.last().id
        assertTrue(popupRoot != anchorRoot, "Popup must own a separate root")
        menuPointerClick("menu:id-b")
        waitUntil(timeoutMillis = 10_000) { dispatched.size == 1 }
        onNodeWithTag("migration-popup").assertDoesNotExist()
        runOnIdle { assertEquals(listOf("id-b" to 1), dispatched); assertFalse(mounted) }
    }

    @Test fun queuedMenuSelectionRejectsDisabledOwner() = invalidatedMenuSelection(MenuInvalidation.Disabled)
    @Test fun queuedMenuSelectionRejectsDisabledEntry() = invalidatedMenuSelection(MenuInvalidation.EntryDisabled)
    @Test fun queuedMenuSelectionRejectsRemovedOption() = invalidatedMenuSelection(MenuInvalidation.Removed)
    @Test fun queuedMenuSelectionRejectsReorderedOptions() = invalidatedMenuSelection(MenuInvalidation.Reordered)
    @Test fun queuedMenuSelectionRejectsChangedOwner() = invalidatedMenuSelection(MenuInvalidation.Owner)
    @Test fun queuedMenuSelectionRejectsDisposedOwner() = invalidatedMenuSelection(MenuInvalidation.Disposed)

    private enum class MenuInvalidation { Disabled, EntryDisabled, Removed, Reordered, Owner, Disposed }

    private fun invalidatedMenuSelection(invalidation: MenuInvalidation) = runComposeUiTest {
        var expanded by mutableStateOf(false)
        var enabled by mutableStateOf(true)
        var present by mutableStateOf(true)
        var owner by mutableStateOf(0)
        var entries by mutableStateOf(listOf(HarnessMenuEntry.Item("a", "Same"), HarnessMenuEntry.Item("b", "Same")))
        var mounted = false
        val dispatched = mutableListOf<String>()
        fun invalidate() {
            when (invalidation) {
                MenuInvalidation.Disabled -> enabled = false
                MenuInvalidation.EntryDisabled -> entries = entries.map { it.copy(enabled = it.key != "b") }
                MenuInvalidation.Removed -> entries = entries.filter { it.key != "b" }
                MenuInvalidation.Reordered -> entries = entries.reversed()
                MenuInvalidation.Owner -> owner++
                MenuInvalidation.Disposed -> present = false
            }
        }
        setContent { HarnessTheme { if (present) {
            HarnessMenu(expanded, entries, enabled = enabled, selectionScope = owner,
                onExpandedChange = { expanded = it; if (!it) invalidate() },
                onSelect = { assertFalse(mounted); dispatched += it },
                onContentMountChange = { mounted = it },
                popupModifier = Modifier.testTag("migration-popup"),
                anchor = { toggle -> HarnessButton("Open", toggle, testTag = "migration-anchor") })
        } } }
        menuPointerClick("migration-anchor")
        runOnIdle { assertTrue(mounted) }
        val retained = onNodeWithTag("menu:b").fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
        // Normal selection is real input. Invalidation occurs synchronously in the close callback.
        menuPointerClick("menu:b")
        waitUntil(timeoutMillis = 10_000) { !mounted }
        onNodeWithTag("migration-popup").assertDoesNotExist()
        runOnIdle {
            assertTrue(dispatched.isEmpty(), "Queued callback must be fenced: $invalidation")
            retained() // Deliberate stale platform callback injection; never a normal interaction.
        }
        waitForIdle()
        runOnIdle { assertTrue(dispatched.isEmpty(), "Retained callback must be fenced: $invalidation") }
    }

    @Test
    fun retainedMenuCallbackCannotSelectAfterPopupReopens() = runComposeUiTest {
        var expanded by mutableStateOf(false)
        var calls = 0
        setContent { HarnessTheme {
            HarnessMenu(expanded, listOf(HarnessMenuEntry.Item("a", "A")),
                onExpandedChange = { expanded = it }, onSelect = { calls++ },
                anchor = { toggle -> HarnessButton("Open", toggle, testTag = "migration-anchor") })
        } }
        menuPointerClick("migration-anchor")
        val retained = onNodeWithTag("menu:a").fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
        onNodeWithTag("menu:a").performKeyInput { keyDown(Key.Escape); keyUp(Key.Escape) }
        onNodeWithTag("menu:a").assertDoesNotExist()
        menuPointerClick("migration-anchor")
        runOnIdle { retained() }
        waitForIdle()
        runOnIdle { assertEquals(0, calls); assertTrue(expanded) }
        menuPointerClick("menu:a")
        waitUntil(timeoutMillis = 10_000) { calls == 1 }
    }

    @Test
    fun menuRealKeyboardNavigatesSkipsAndActivatesOrDismisses() = runComposeUiTest {
        var expanded by mutableStateOf(false)
        val calls = mutableListOf<String>()
        setContent { HarnessTheme {
            HarnessMenu(expanded, listOf(HarnessMenuEntry.Item("a", "A", selected = true),
                HarnessMenuEntry.Item("disabled", "Disabled", enabled = false),
                HarnessMenuEntry.Item("busy", "Busy", state = HarnessControlState.Loading),
                HarnessMenuEntry.Item("b", "B"), HarnessMenuEntry.Item("c", "C")),
                onExpandedChange = { expanded = it }, onSelect = { calls += it },
                anchor = { toggle -> HarnessButton("Open", toggle, testTag = "migration-anchor") })
        } }
        fun press(tag: String, key: Key) {
            onNodeWithTag(tag).assertIsFocused().performKeyInput { keyDown(key); keyUp(key) }
        }
        menuPointerClick("migration-anchor")
        onNodeWithTag("menu:a").assertIsSelected()
        onNodeWithTag("menu:disabled").assertIsNotEnabled()
        menuPointerClick("menu:disabled")
        runOnIdle { assertTrue(calls.isEmpty()); assertTrue(expanded) }
        press("menu:a", Key.DirectionDown)
        press("menu:b", Key.DirectionUp)
        press("menu:a", Key.MoveEnd)
        press("menu:c", Key.MoveHome)
        press("menu:a", Key.Enter)
        waitUntil(timeoutMillis = 10_000) { calls == listOf("a") }
        menuPointerClick("migration-anchor")
        press("menu:a", Key.MoveEnd)
        press("menu:c", Key.Spacebar)
        waitUntil(timeoutMillis = 10_000) { calls == listOf("a", "c") }
        for (dismiss in listOf(Key.Escape, Key.Tab)) {
            menuPointerClick("migration-anchor")
            // Active item is retained between openings.
            press("menu:c", dismiss)
            waitUntil(timeoutMillis = 10_000) { !expanded }
            onNodeWithTag("menu:c").assertDoesNotExist()
        }
        runOnIdle { assertEquals(listOf("a", "c"), calls) }
    }

    @Test
    fun menuOutsidePointerDismissesWithoutDispatch() = runComposeUiTest {
        var expanded by mutableStateOf(false)
        var calls = 0
        setContent { HarnessTheme {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                HarnessMenu(expanded, listOf(HarnessMenuEntry.Item("a", "A")),
                    onExpandedChange = { expanded = it }, onSelect = { calls++ },
                    popupModifier = Modifier.testTag("migration-popup"),
                    anchor = { toggle -> HarnessButton("Open", toggle, testTag = "migration-anchor") })
                HarnessButton("Outside", {}, testTag = "migration-outside")
            }
        } }
        menuPointerClick("migration-anchor")
        val popup = onNodeWithTag("migration-popup", true).fetchSemanticsNode().boundsInWindow
        val outside = onNodeWithTag("migration-outside").fetchSemanticsNode().boundsInWindow.center
        assertFalse(popup.contains(outside), "Outside input must be measured outside popup")
        menuPointerClick("migration-outside")
        waitUntil(timeoutMillis = 10_000) { !expanded }
        onNodeWithTag("migration-popup").assertDoesNotExist()
        runOnIdle { assertEquals(0, calls) }
    }

    private fun ComposeUiTest.menuPointerClick(tag: String) {
        val started = TimeSource.Monotonic.markNow()
        fun settle() { mainClock.advanceTimeByFrame(); waitForIdle(); assertTrue(started.elapsedNow().inWholeMilliseconds < 10_000, "Pointer deadline: $tag") }
        settle()
        val target = onNodeWithTag(tag).assertIsDisplayed()
        val node = target.fetchSemanticsNode()
        val rootNode = generateSequence(node) { it.parent }.last()
        val root = onNode(SemanticsMatcher("Menu pointer root ${rootNode.id}") { it.id == rootNode.id }, true)
        root.performMouseInput { moveTo(node.boundsInRoot.center - rootNode.boundsInRoot.topLeft) }
        settle()
        val pressed = target.assertIsDisplayed().fetchSemanticsNode()
        assertTrue(pressed.boundsInRoot.width > 0f && pressed.boundsInRoot.height > 0f)
        val point = pressed.boundsInRoot.center - root.fetchSemanticsNode().boundsInRoot.topLeft
        root.performMouseInput { moveTo(point); advanceEventTime(16); click(point) }
        settle()
    }

    @Test
    fun confirmDialogSubmitsAndKeepsFailureRetryable() = runComposeUiTest {
        var visible by mutableStateOf(true)
        var submit by mutableStateOf<HarnessConfirmSubmitState>(HarnessConfirmSubmitState.Ready)
        var attempts = 0
        setContent {
            HarnessTheme {
                HarnessConfirmDialog(
                    visible = visible,
                    onDismissRequest = { visible = false },
                    title = "Delete project?",
                    description = List(120) { "This cannot be undone. The details can be reviewed before confirming." }.joinToString("\n"),
                    cancelLabel = "Cancel",
                    confirmLabel = "Delete",
                    submitState = submit,
                    onCancel = { visible = false },
                    onConfirm = {
                        attempts++
                        submit = HarnessConfirmSubmitState.Failed("Delete failed")
                    },
                    intent = HarnessConfirmIntent.Destructive,
                )
            }
        }
        onNodeWithTag("confirm-dialog:cancel").assertIsFocused()
        onNodeWithTag("confirm-dialog:cancel").assertIsDisplayed()
        onNodeWithTag("confirm-dialog:confirm").assertIsDisplayed()
        onNodeWithTag("confirm-dialog:confirm").performClick()
        onNodeWithTag("confirm-dialog:error").assertExists()
        onNodeWithTag("confirm-dialog:confirm").performClick()
        runOnIdle { assertEquals(2, attempts); assertTrue(visible) }
    }

    @Test
    fun confirmDialogLoadingBlocksRepeatedSubmitCancelAndEscape() = runComposeUiTest {
        var visible by mutableStateOf(true)
        var submit by mutableStateOf<HarnessConfirmSubmitState>(HarnessConfirmSubmitState.Submitting)
        var submits = 0
        var cancels = 0
        setContent {
            HarnessTheme {
                HarnessConfirmDialog(
                    visible = visible,
                    onDismissRequest = { visible = false },
                    title = "Save changes?",
                    description = "Saving is in progress.",
                    cancelLabel = "Cancel",
                    confirmLabel = "Save",
                    submitState = submit,
                    onCancel = { cancels++ },
                    onConfirm = { submits++ },
                )
            }
        }
        onNodeWithTag("confirm-dialog:confirm").assertIsNotEnabled().performClick()
        onNodeWithTag("confirm-dialog:cancel").assertIsNotEnabled().performClick()
        onNodeWithTag("confirm-dialog:cancel").performKeyInput { keyDown(Key.Escape); keyUp(Key.Escape) }
        runOnIdle { assertEquals(0, submits); assertEquals(0, cancels); assertTrue(visible) }
    }

    @Test
    fun confirmDialogEscapeDismissesAndRestoresTriggerFocus() = runComposeUiTest {
        var visible by mutableStateOf(false)
        setContent {
            HarnessTheme {
                Column {
                    HarnessButton("Open dialog", { visible = true }, testTag = "confirm-trigger")
                    HarnessConfirmDialog(
                        visible = visible,
                        onDismissRequest = { visible = false },
                        title = "Leave page?",
                        description = "Unsaved changes will be lost.",
                        cancelLabel = "Keep editing",
                        confirmLabel = "Discard",
                        submitState = HarnessConfirmSubmitState.Ready,
                        onCancel = { visible = false },
                        onConfirm = { visible = false },
                        intent = HarnessConfirmIntent.Destructive,
                    )
                }
            }
        }
        onNodeWithTag("confirm-trigger").performClick()
        onNodeWithTag("confirm-dialog:cancel").assertIsFocused()
        onNodeWithTag("confirm-dialog:cancel").performKeyInput { keyDown(Key.Escape); keyUp(Key.Escape) }
        onNodeWithTag("confirm-dialog").assertDoesNotExist()
        onNodeWithTag("confirm-trigger").assertIsFocused()
    }

    @Test
    fun unsavedNavigationConsumesOnlyDiscardedDestination() {
        val first = HarnessUnsavedNavigationState<String>().request("settings/profile")
        val kept = first.keepEditing()
        assertNull(kept.pendingDestination)
        val second = kept.request("projects/42")
        val discarded = second.discard()
        assertEquals("projects/42", discarded.destination)
        assertNull(discarded.state.pendingDestination)
        val repeatedDiscard = discarded.state.discard()
        assertNull(repeatedDiscard.destination)
        assertEquals(discarded.state, repeatedDiscard.state)
    }

    @Test
    fun loadingControlsBlockActionsAndErrorsCanRetry() = runComposeUiTest {
        var control by mutableStateOf(HarnessControlState.Loading)
        var clicks = 0
        setContent {
            HarnessTheme {
                Column {
                    HarnessButton("Save", { clicks++ }, state = control)
                    HarnessIconButton("Send", { clicks++ }, state = HarnessButtonState(control = control)) { HarnessText("↑") }
                }
            }
        }
        onNodeWithTag("Save").assertIsNotEnabled().performClick()
        onNodeWithTag("Send").assertIsNotEnabled().performClick()
        runOnIdle { assertEquals(0, clicks); control = HarnessControlState.Error }
        onNodeWithTag("Save").performClick()
        onNodeWithTag("Send").performClick()
        runOnIdle { assertEquals(2, clicks) }
    }

    @Test
    fun togglesReportControlledValuesAndRespectDisabledOrLoadingState() = runComposeUiTest {
        var checked by mutableStateOf(false)
        var enabled by mutableStateOf(true)
        var control by mutableStateOf(HarnessControlState.Normal)
        var updates = 0
        setContent {
            HarnessTheme {
                Column {
                    HarnessSwitch(checked, { checked = it; updates++ }, "Notifications", enabled = enabled, state = control)
                    HarnessCheckbox(checked, { checked = it; updates++ }, "Pin", enabled = enabled, state = control)
                    HarnessTriStateCheckbox(ToggleableState.Indeterminate, { updates++ }, "All", enabled = enabled, state = control)
                }
            }
        }
        onNodeWithTag("Notifications").assertIsOff().performClick()
        onNodeWithTag("Pin").assertIsOn().performClick()
        onNodeWithTag("Notifications").assertIsOff()
        onNodeWithTag("All").performClick()
        runOnIdle { assertEquals(3, updates); enabled = false }
        onNodeWithTag("Pin").assertIsNotEnabled().performClick()
        runOnIdle { assertEquals(3, updates); enabled = true; control = HarnessControlState.Loading }
        onNodeWithTag("Notifications").assertIsNotEnabled().performClick()
        onNodeWithTag("All").assertIsNotEnabled().performClick()
        runOnIdle { assertEquals(3, updates) }
    }

    @Test
    fun menuSkipsUnavailableActionsAndClosesBeforeDispatch() = runComposeUiTest {
        var expanded by mutableStateOf(false)
        val events = mutableListOf<String>()
        val entries = listOf(
            HarnessMenuEntry.Item("open", "Open"),
            HarnessMenuEntry.Separator,
            HarnessMenuEntry.Item("busy", "Busy", state = HarnessControlState.Loading),
            HarnessMenuEntry.Item("disabled", "Disabled", enabled = false),
            HarnessMenuEntry.Item("delete", "Delete", destructive = true),
        )
        setContent {
            HarnessTheme {
                HarnessMenu(
                    expanded, entries,
                    onExpandedChange = { expanded = it; events += "expanded:$it" },
                    onSelect = { assertFalse(expanded); events += "select:$it" },
                    anchor = { toggle -> HarnessButton("Actions", toggle) },
                )
            }
        }
        onNodeWithTag("Actions").performClick()
        onNodeWithTag("menu:open").assertIsFocused().performKeyInput {
            keyDown(Key.DirectionDown); keyUp(Key.DirectionDown)
        }
        onNodeWithTag("menu:delete").performKeyInput { keyDown(Key.Enter); keyUp(Key.Enter) }
        onNodeWithTag("menu:delete").assertDoesNotExist()
        runOnIdle { assertEquals(listOf("expanded:true", "expanded:false", "select:delete"), events) }
        onNodeWithTag("Actions").performClick()
        onNodeWithTag("menu:open").performKeyInput { keyDown(Key.Escape); keyUp(Key.Escape) }
        runOnIdle { assertFalse(expanded); assertEquals(1, events.count { it.startsWith("select:") }) }
    }

    @Test
    fun tabsSupportKeyboardSelectionAndClosingTheActiveTab() = runComposeUiTest {
        var state by mutableStateOf(HarnessTabsState(listOf(
            HarnessTab("first", "First", closable = true),
            HarnessTab("disabled", "Disabled", enabled = false),
            HarnessTab("last", "Last", closable = true),
        ), "first"))
        setContent { HarnessTheme { HarnessTabs(state, { state = it }) } }
        onNodeWithTag("tab:first").performClick()
        onNodeWithTag("tab:first").performKeyInput { keyDown(Key.DirectionRight); keyUp(Key.DirectionRight) }
        onNodeWithTag("tab:last").assertIsSelected().assertIsFocused()
        onNodeWithTag("Close Last").performClick()
        onNodeWithTag("tab:last").assertDoesNotExist()
        onNodeWithTag("tab:first").assertIsSelected().assertIsFocused()
        runOnIdle { assertEquals("first", state.effectiveSelectedKey) }
    }

    @Test
    fun disclosureAndFeedbackComposeOnlyTheVisibleContent() = runComposeUiTest {
        var row by mutableStateOf(HarnessRowState(expanded = false))
        var feedback by mutableStateOf<HarnessContentState>(HarnessContentState.Loading())
        var retries = 0
        setContent {
            HarnessTheme {
                Column {
                    HarnessRow("Project", state = row, onExpandedChange = { row = row.copy(expanded = it) }) {
                        HarnessText("Child")
                    }
                    HarnessStateContent(feedback, onRetry = { retries++ }) { HarnessButton("Ready", {}) }
                }
            }
        }
        onNodeWithText("Child").assertDoesNotExist()
        onNodeWithTag("Expand Project").performClick()
        onNodeWithText("Child").assertExists()
        onNodeWithTag("Collapse Project").performClick()
        onNodeWithText("Child").assertDoesNotExist()
        onNodeWithTag("Expand Project").performClick()
        runOnIdle { assertTrue(row.expanded == true); feedback = HarnessContentState.Error("Request failed") }
        onNodeWithTag("loading-state").assertDoesNotExist()
        onNodeWithTag("Ready").assertDoesNotExist()
        onNodeWithTag("Retry").performClick()
        runOnIdle { assertEquals(1, retries); feedback = HarnessContentState.Empty("No projects") }
        onNodeWithTag("error-state").assertDoesNotExist()
        onNodeWithTag("empty-state").assertExists()
        runOnIdle { feedback = HarnessContentState.Ready }
        onNodeWithTag("empty-state").assertDoesNotExist()
        onNodeWithTag("Ready").assertExists()
    }
}
