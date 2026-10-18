package im.hikaru.harness.ui.foundation

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HarnessComponentModelsTest {
    @Test
    fun loadingBlocksRepeatActionsWhileErrorsAllowRetry() {
        HarnessControlState.entries.forEach { control ->
            assertFalse(control.canInteract(enabled = false))
            assertEquals(control != HarnessControlState.Loading, HarnessButtonState(control = control).interactive)
        }
        assertTrue(HarnessControlState.Error.canInteract())
    }

    @Test
    fun selectedHoveredAndPressedGhostButtonsRemainDistinct() {
        listOf(HarnessLightColors, HarnessDarkColors).forEach { colors ->
            val ordinary = harnessActionColors(colors, HarnessButtonState(), HarnessButtonVariant.Ghost, false)
            assertEquals(Color.Transparent, ordinary.background)
            val selected = harnessActionColors(colors, HarnessButtonState(selected = true), HarnessButtonVariant.Ghost, false)
            val hovered = harnessActionColors(colors, HarnessButtonState(), HarnessButtonVariant.Ghost, true)
            val pressed = harnessActionColors(colors, HarnessButtonState(), HarnessButtonVariant.Ghost, true, pressed = true)
            assertEquals(colors.SurfaceSelected, selected.background)
            assertEquals(colors.HoverBackground, hovered.background)
            assertEquals(colors.PressedBackground, pressed.background)
            assertTrue(setOf(selected.background, hovered.background, pressed.background).size == 3)
            assertEquals(colors.Danger, harnessActionColors(colors, HarnessButtonState(), HarnessButtonVariant.Destructive, false).background)
            val failed = harnessActionColors(colors, HarnessButtonState(control = HarnessControlState.Error), HarnessButtonVariant.Secondary, false)
            assertEquals(colors.Danger, failed.border)
            assertEquals(colors.Danger, failed.foreground)
            val disabled = harnessActionColors(colors, HarnessButtonState(enabled = false, selected = true), HarnessButtonVariant.Destructive, true)
            assertEquals(colors.TextDisabled, disabled.foreground)
            assertEquals(colors.SurfaceSecondary, disabled.background)
        }
    }

    private val menu = HarnessMenuModel(listOf(
        HarnessMenuEntry.Item("open", "Open"),
        HarnessMenuEntry.Separator,
        HarnessMenuEntry.Item("disabled", "Unavailable", enabled = false),
        HarnessMenuEntry.Item("loading", "Saving", state = HarnessControlState.Loading),
        HarnessMenuEntry.Item("retry", "Retry", state = HarnessControlState.Error),
        HarnessMenuEntry.Item("delete", "Delete", destructive = true),
    ))

    @Test
    fun menuNavigationSkipsSeparatorsAndBusyOrDisabledActionsAndWraps() {
        assertEquals("retry", menu.navigate("open", HarnessNavigation.Next))
        assertEquals("open", menu.navigate("delete", HarnessNavigation.Next))
        assertEquals("delete", menu.navigate("open", HarnessNavigation.Previous))
        assertEquals("open", menu.navigate("stale-key", HarnessNavigation.First))
        assertEquals("delete", menu.navigate(null, HarnessNavigation.Last))
        assertNull(menu.selectable("disabled"))
        assertNull(menu.selectable("loading"))
        assertNull(menu.selectable("missing"))
    }

    @Test
    fun unavailableMenusHaveNoKeyboardOrActivationTarget() {
        val unavailable = HarnessMenuModel(listOf(HarnessMenuEntry.Separator, HarnessMenuEntry.Item("a", "A", enabled = false)))
        HarnessNavigation.entries.forEach { assertNull(unavailable.navigate(null, it)) }
        assertNull(HarnessMenuModel(emptyList()).navigate("missing", HarnessNavigation.Next))
    }

    @Test
    fun menuSelectionIsDeliveredOnlyAfterClosingAndCannotBeReplacedByRepeatedActivation() {
        val idle = HarnessMenuSelection()
        assertEquals(idle, idle.request(menu, "disabled"))
        assertEquals(idle, idle.request(menu, "loading"))
        val pending = idle.request(menu, "open")
        assertNull(pending.readyKey(expanded = true))
        assertEquals("open", pending.readyKey(expanded = false))
        assertEquals(pending, pending.request(menu, "delete"))
        assertEquals(pending, pending.request(menu, "open"))
    }

    @Test
    fun queuedMenuSelectionRequiresTheSameOrderedSelectableSnapshot() {
        val entries = listOf(HarnessMenuEntry.Item("a", "Duplicate"), HarnessMenuEntry.Item("b", "Duplicate"))
        val original = HarnessMenuModel(entries)
        val queued = HarnessMenuSelection().request(original, "b")
        assertNull(queued.readyKey(true, original))
        assertEquals("b", queued.readyKey(false, HarnessMenuModel(entries.map { (it as HarnessMenuEntry.Item).copy() })))
        assertNull(queued.readyKey(false, HarnessMenuModel(entries.reversed())))
        assertNull(queued.readyKey(false, HarnessMenuModel(entries.take(1))))
        assertNull(queued.readyKey(false, HarnessMenuModel(entries.map {
            (it as HarnessMenuEntry.Item).copy(enabled = it.key != "b")
        })))
        assertEquals("b", queued.pendingKey, "Validation does not mutate the immutable queued selection")
    }

    @Test
    fun ambiguousOrBlankKeysAreRejectedBeforeRendering() {
        assertFailsWith<IllegalArgumentException> { HarnessMenuModel(listOf(HarnessMenuEntry.Item("a", "A"), HarnessMenuEntry.Item("a", "B"))) }
        assertFailsWith<IllegalArgumentException> { HarnessMenuModel(listOf(HarnessMenuEntry.Item(" ", "A"))) }
        assertFailsWith<IllegalArgumentException> { HarnessTabsState(listOf(HarnessTab("a", "A"), HarnessTab("a", "B"))) }
        assertFailsWith<IllegalArgumentException> { HarnessTabsState(listOf(HarnessTab("", "A"))) }
    }

    private val tabs = listOf(
        HarnessTab("first", "First", closable = true),
        HarnessTab("disabled", "Disabled", enabled = false, closable = true),
        HarnessTab("middle", "Middle", closable = true),
        HarnessTab("last", "Last", closable = true),
    )

    @Test
    fun tabsNavigateOnlyThroughEnabledKeysAndRejectInvalidSelections() {
        val state = HarnessTabsState(tabs, "first")
        assertEquals("middle", state.navigate(HarnessNavigation.Next).selectedKey)
        assertEquals("last", state.navigate(HarnessNavigation.Previous).selectedKey)
        assertEquals("first", state.navigate(HarnessNavigation.First).selectedKey)
        assertEquals("last", state.navigate(HarnessNavigation.Last).selectedKey)
        assertEquals(state, state.select("disabled"))
        assertEquals(state, state.select("missing"))
        assertEquals("last", state.select("last").effectiveSelectedKey)
        assertEquals("first", state.selectedKey, "the original state must remain immutable")
    }

    @Test
    fun staleOrDisabledTabSelectionsFallBackAndAllDisabledListsStayUnselected() {
        assertEquals("first", HarnessTabsState(tabs, "missing").effectiveSelectedKey)
        assertEquals("first", HarnessTabsState(tabs, "disabled").effectiveSelectedKey)
        assertNull(HarnessTabsState(tabs.map { it.copy(enabled = false) }, "first").effectiveSelectedKey)
        assertNull(HarnessTabsState(emptyList()).navigate(HarnessNavigation.Next).selectedKey)
    }

    @Test
    fun closingAnActiveTabChoosesTheNextThenPreviousAvailableNeighbour() {
        assertEquals("middle", HarnessTabsState(tabs, "first").close("first").selectedKey)
        assertEquals("last", HarnessTabsState(tabs, "middle").close("middle").selectedKey)
        assertEquals("middle", HarnessTabsState(tabs, "last").close("last").selectedKey)
        assertNull(HarnessTabsState(listOf(tabs.first()), "first").close("first").selectedKey)
        val remaining = HarnessTabsState(tabs, "middle").close("middle")
        assertFalse(remaining.tabs.any { it.key == "middle" })
    }

    @Test
    fun closingAnInactiveTabPreservesSelectionAndProtectedTabsCannotClose() {
        val state = HarnessTabsState(tabs, "last")
        assertEquals("last", state.close("first").selectedKey)
        assertEquals(state, state.close("missing"))
        assertEquals(state, state.close("disabled"))
        val protected = HarnessTabsState(listOf(HarnessTab("fixed", "Fixed")), "fixed")
        assertEquals(protected, protected.close("fixed"))
    }

    @Test
    fun rowDisclosureRequiresAnAvailableBranchAndKeepsOtherState() {
        val branch = HarnessRowState(expanded = false, depth = 2, selected = true)
        assertEquals(branch.copy(expanded = true), branch.toggleExpanded())
        assertEquals(branch, branch.toggleExpanded().toggleExpanded())
        val leaf = HarnessRowState()
        assertEquals(leaf, leaf.toggleExpanded())
        listOf(branch.copy(enabled = false), branch.copy(control = HarnessControlState.Loading)).forEach {
            assertEquals(it, it.toggleExpanded())
        }
        assertTrue(branch.copy(control = HarnessControlState.Error).toggleExpanded().expanded == true)
        assertFailsWith<IllegalArgumentException> { HarnessRowState(depth = -1) }
    }
}
