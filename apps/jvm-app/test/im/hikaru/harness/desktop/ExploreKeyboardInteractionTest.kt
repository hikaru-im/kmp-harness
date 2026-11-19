@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package im.hikaru.harness.desktop

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import im.hikaru.harness.client.shell.*
import kotlin.test.*

class ExploreKeyboardInteractionTest {
    private class Fixture {
        var state by mutableStateOf(AppShellState())
        val destinations = mutableListOf<PageId>()
        val pins = mutableListOf<RailDestination>()
        @Composable fun Content() {
            AppShell(state = state, navItems = harnessRailItems(state.navigation.currentPage, pinned = state.pinnedRailDestinations),
                navigator = WorkspaceNavigatorState(), projectNodes = emptyList(), recentTasks = emptyList(),
                onNavSelect = {}, onToggleSidebar = {}, onNewTask = {}, onNavigatorSearch = {},
                onToggleProject = {}, onSelectNode = {}, onSelectRecent = {},
                onOpenExploreMenu = { state = state.copy(exploreMenu = state.exploreMenu.open()) },
                onDismissExploreMenu = { state = state.copy(exploreMenu = state.exploreMenu.close()) },
                onOpenExploreDestination = {
                    destinations += it
                    state = state.copy(exploreMenu = state.exploreMenu.close(), navigation = state.navigation.navigate(it))
                },
                onTogglePinRailDestination = {
                    pins += it
                    state = state.copy(pinnedRailDestinations = if (it in state.pinnedRailDestinations)
                        state.pinnedRailDestinations - it else state.pinnedRailDestinations + it)
                }, content = {})
        }
    }

    @Test fun keyboardEntryNavigationPinningAndEscapeRestoreTrigger() = runSkikoComposeUiTest(size = Size(1440f, 900f)) {
        val f = Fixture()
        setContent { f.Content() }
        onNodeWithTag("rail-explore").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        key("rail-explore", Key.DirectionDown)
        onNodeWithTag("explore-destination-projects").assertIsFocused()
        key("explore-menu", Key.DirectionRight)
        onNodeWithTag("explore-pin-projects").assertIsFocused()
        key("explore-menu", Key.DirectionDown)
        onNodeWithTag("explore-pin-projects").assertIsFocused()
        key("explore-menu", Key.Spacebar)
        runOnIdle { assertEquals(listOf(RailDestination.Projects), f.pins); assertTrue(f.destinations.isEmpty()) }
        onNodeWithTag("explore-menu").assertExists()
        key("explore-menu", Key.MoveEnd)
        onNodeWithTag("explore-destination-projects").assertIsFocused()
        key("explore-menu", Key.DirectionRight)
        key("explore-menu", Key.MoveHome)
        onNodeWithTag("explore-destination-projects").assertIsFocused()
        key("explore-menu", Key.DirectionUp)
        onNodeWithTag("explore-destination-projects").assertIsFocused()
        key("explore-menu", Key.Escape)
        onNodeWithTag("explore-menu").assertDoesNotExist()
        onNodeWithTag("rail-explore").assertIsFocused()
        key("rail-explore", Key.DirectionUp)
        onNodeWithTag("explore-destination-projects").assertIsFocused()
        key("explore-menu", Key.Enter)
        onNodeWithTag("explore-menu").assertDoesNotExist()
        runOnIdle { assertEquals(listOf<PageId>(PageId.Projects()), f.destinations) }
    }

    @Test fun hoverPinVisibilityKeepsGeometryAndKeyboardFocusSurvivesPointerLeave() = runSkikoComposeUiTest(size = Size(1440f, 900f)) {
        val f = Fixture()
        mainClock.autoAdvance = false
        setContent { f.Content() }
        hover("rail-explore")
        mainClock.advanceTimeBy(250); waitForIdle()
        val pin = onNodeWithTag("explore-pin-projects", true)
        pin.assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.HideFromAccessibility))
        val bounds = pin.fetchSemanticsNode().boundsInWindow
        hover("explore-destination-projects")
        pin.assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.HideFromAccessibility))
        assertEquals(bounds, pin.fetchSemanticsNode().boundsInWindow)
        onNodeWithTag("rail-explore").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        key("rail-explore", Key.DirectionDown)
        hover("rail-home")
        mainClock.advanceTimeBy(500); waitForIdle()
        onNodeWithTag("explore-menu").assertExists()
        onNodeWithTag("explore-destination-projects").assertIsFocused()
        pin.assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.HideFromAccessibility))
        key("explore-menu", Key.DirectionRight)
        key("explore-menu", Key.Enter)
        key("explore-menu", Key.Escape)
        hover("rail-home"); hover("rail-explore")
        mainClock.advanceTimeBy(250); waitForIdle()
        pin.assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.HideFromAccessibility))
    }

    @Test fun hoverMenuAcceptsKeyboardFromMainRoot() = runSkikoComposeUiTest(size = Size(1440f, 900f)) {
        val f = Fixture()
        mainClock.autoAdvance = false
        setContent { f.Content() }
        onNodeWithTag("rail-home").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        for (entryKey in listOf(Key.DirectionDown, Key.DirectionUp, Key.Tab)) {
            hover("rail-explore")
            mainClock.advanceTimeBy(250); waitForIdle()
            onNodeWithTag("explore-menu").assertExists()
            key("rail-home", entryKey)
            onNodeWithTag("explore-destination-projects").assertIsFocused()
            hover("rail-home")
            mainClock.advanceTimeBy(500); waitForIdle()
            onNodeWithTag("explore-menu").assertExists()
            key("explore-menu", Key.Escape)
            onNodeWithTag("explore-menu").assertDoesNotExist()
            onNodeWithTag("rail-explore").assertIsFocused()
        }
        runOnIdle { assertTrue(f.destinations.isEmpty()); assertTrue(f.pins.isEmpty()) }
    }

    @Test fun rtlArrowsSwitchColumnsWithoutWrapping() = runSkikoComposeUiTest(size = Size(1440f, 900f)) {
        val f = Fixture()
        setContent { CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl) { f.Content() } }
        onNodeWithTag("rail-explore").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        key("rail-explore", Key.Enter)
        onNodeWithTag("explore-destination-projects").assertIsFocused()
        key("explore-menu", Key.DirectionLeft)
        onNodeWithTag("explore-pin-projects").assertIsFocused()
        key("explore-menu", Key.DirectionLeft)
        onNodeWithTag("explore-pin-projects").assertIsFocused()
        key("explore-menu", Key.DirectionRight)
        onNodeWithTag("explore-destination-projects").assertIsFocused()
    }

    private fun ComposeUiTest.root(tag: String): SemanticsNodeInteraction {
        val node = onNodeWithTag(tag, true).fetchSemanticsNode()
        val root = generateSequence(node) { it.parent }.last()
        return onNode(SemanticsMatcher("Explore input root") { it.id == root.id }, true)
    }
    private fun ComposeUiTest.key(tag: String, key: Key) {
        root(tag).performKeyInput { keyDown(key); keyUp(key) }
        mainClock.advanceTimeByFrame(); waitForIdle()
    }
    private fun ComposeUiTest.hover(tag: String) {
        val node = onNodeWithTag(tag, true).fetchSemanticsNode()
        val root = generateSequence(node) { it.parent }.last()
        root(tag).performMouseInput { moveTo(node.boundsInRoot.center - root.boundsInRoot.topLeft) }
        mainClock.advanceTimeByFrame(); waitForIdle()
    }
}
