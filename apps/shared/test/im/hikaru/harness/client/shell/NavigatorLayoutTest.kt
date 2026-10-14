package im.hikaru.harness.client.shell

import androidx.compose.ui.unit.dp
import im.hikaru.harness.ui.foundation.HarnessTokens
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The rail stays mounted at every width, so the navigator is the only region that changes shape.
 * These cases encode the Codex `--spacing-token-sidebar` rule `clamp(240px, 275px, min(520px,
 * calc(100vw - 320px)))`: push while the window can spare 240dp of navigator and still leave 320dp
 * of main surface, otherwise float above the content so a narrow window is not squeezed. Below the
 * compact-shell width the drawer always floats, because the rail is still mounted there.
 */
class NavigatorLayoutTest {
    @Test
    fun collapsedHidesTheNavigatorAtEveryWidth() {
        assertEquals(NavigatorLayout.Hidden, navigatorLayout(SidebarMode.Collapsed, 1440.dp))
        assertEquals(NavigatorLayout.Hidden, navigatorLayout(SidebarMode.Collapsed, 600.dp))
        assertEquals(0.dp, navigatorWidth(NavigatorLayout.Hidden, 1440.dp))
    }

    @Test
    fun wideWindowsPushTheNavigatorAtItsPreferredWidth() {
        // 1440 - 52 rail - 275 navigator leaves 1113dp of main surface.
        assertEquals(NavigatorLayout.Pushed, navigatorLayout(SidebarMode.Expanded, 1440.dp))
        assertEquals(HarnessTokens.Control.SidebarExpanded, navigatorWidth(NavigatorLayout.Pushed, 1440.dp))
        // 1024x768 must still push: 1024 - 52 rail - 275 navigator leaves 697dp of content.
        assertEquals(NavigatorLayout.Pushed, navigatorLayout(SidebarMode.Expanded, 1024.dp))
        assertEquals(HarnessTokens.Control.SidebarExpanded, navigatorWidth(NavigatorLayout.Pushed, 1024.dp))
    }

    @Test
    fun pushedNavigatorShrinksBeforeItSqueezesTheMainSurface() {
        // 800 - 52 - 320 = 428, above the 275dp preferred width, so the preferred width wins.
        assertEquals(NavigatorLayout.Pushed, navigatorLayout(SidebarMode.Expanded, 800.dp))
        assertEquals(275.dp, navigatorWidth(NavigatorLayout.Pushed, 800.dp))

        // 700 - 52 - 320 = 328 -> still the preferred 275dp.
        assertEquals(275.dp, navigatorWidth(NavigatorLayout.Pushed, 700.dp))

        // 660 - 52 - 320 = 288, between the 240dp minimum and the preferred width.
        assertEquals(NavigatorLayout.Pushed, navigatorLayout(SidebarMode.Expanded, 660.dp))
        assertEquals(275.dp, navigatorWidth(NavigatorLayout.Pushed, 660.dp))
    }

    @Test
    fun narrowWindowsFloatTheDrawerSoTheMainSurfaceKeepsItsWidth() {
        // 660 still holds a pushed 240dp navigator next to 320dp of content.
        assertEquals(NavigatorLayout.Pushed, navigatorLayout(SidebarMode.Expanded, 660.dp))
        assertEquals(275.dp, navigatorWidth(NavigatorLayout.Pushed, 660.dp))

        // Below the compact-shell width (640dp) the rail is still mounted, so the drawer floats over
        // the content instead of pushing and the main surface keeps its own width.
        assertEquals(NavigatorLayout.Floating, navigatorLayout(SidebarMode.Expanded, 639.dp))
        assertEquals(275.dp, navigatorWidth(NavigatorLayout.Floating, 639.dp))
    }

    @Test
    fun temporaryExpansionAlwaysFloats() {
        assertEquals(NavigatorLayout.Floating, navigatorLayout(SidebarMode.TemporarilyExpanded, 1440.dp))
        assertEquals(275.dp, navigatorWidth(NavigatorLayout.Floating, 1440.dp))
        // A window narrower than the preferred drawer caps the floating drawer to the room left.
        assertEquals(248.dp, navigatorWidth(NavigatorLayout.Floating, 300.dp))
        assertEquals(240.dp, navigatorWidth(NavigatorLayout.Floating, 200.dp))
    }

    @Test
    fun compactWindowFloatsTheNavigatorBecauseTheRailIsAlwaysMounted() {
        // The rail is fixed at every width, so below the compact-shell size there is no room to push
        // a column beside it and the navigator goes over the content instead.
        assertEquals(NavigatorLayout.Floating, navigatorLayout(SidebarMode.Expanded, 600.dp))
        assertEquals(NavigatorLayout.Pushed, navigatorLayout(SidebarMode.Expanded, 800.dp))
        assertEquals(NavigatorLayout.Pushed, navigatorLayout(SidebarMode.Expanded, 1440.dp))
        // The drawer keeps its preferred width down to the window edge.
        assertEquals(275.dp, navigatorWidth(NavigatorLayout.Floating, 1440.dp))
        assertEquals(275.dp, navigatorWidth(NavigatorLayout.Floating, 400.dp))
    }

    @Test
    fun userPreferredWidthIsClampedToTheCodexRangeInsteadOfPinningOneScreenshot() {
        // 288dp is the width measured in the reference window capture, so a stored user width in the
        // Codex range has to survive rather than being forced back to the 275dp default.
        assertEquals(288.dp, navigatorWidth(NavigatorLayout.Pushed, 1440.dp, preferredWidth = 288.dp))
        assertEquals(288.dp, navigatorWidth(NavigatorLayout.Floating, 1440.dp, preferredWidth = 288.dp))
        // Codex clamps the navigator to 240..520, so narrower and wider requests are bounded.
        assertEquals(240.dp, navigatorWidth(NavigatorLayout.Pushed, 1440.dp, preferredWidth = 120.dp))
        assertEquals(520.dp, navigatorWidth(NavigatorLayout.Pushed, 1440.dp, preferredWidth = 900.dp))
        // A wide preference still yields to the 320dp main-surface reserve before squeezing it.
        assertEquals(428.dp, navigatorWidth(NavigatorLayout.Pushed, 800.dp, preferredWidth = 520.dp))
        // The default preference is the bundle value, not the resized value seen in one capture.
        assertEquals(275.dp, navigatorWidth(NavigatorLayout.Pushed, 1440.dp, preferredWidth = NavigatorPreferredWidth))
    }

    @Test
    fun draggingTheNavigatorEdgeMovesItsWidthWithinTheCodexBounds() {
        // A drag grows or shrinks the preference by exactly the pointer delta at a wide window.
        assertEquals(300.dp, navigatorResizeWidth(275.dp, 25.dp, NavigatorLayout.Pushed, 1440.dp))
        assertEquals(250.dp, navigatorResizeWidth(275.dp, (-25).dp, NavigatorLayout.Pushed, 1440.dp))
        // Codex clamps the navigator to 240..520, so the drag stops at each bound.
        assertEquals(NavigatorMaxWidth, navigatorResizeWidth(510.dp, 40.dp, NavigatorLayout.Floating, 1440.dp))
        assertEquals(NavigatorMinWidth, navigatorResizeWidth(245.dp, (-40).dp, NavigatorLayout.Floating, 1440.dp))
        // A floating drawer keeps the same clamp at a wide window under the bottom bar.
        assertEquals(
            NavigatorMaxWidth,
            navigatorResizeWidth(510.dp, 40.dp, NavigatorLayout.Floating, 1440.dp),
        )
    }

    @Test
    fun pushedResizeNeverEatsIntoTheMainSurfaceReserve() {
        // 630 - 52 rail = 578 room, and 578 - 320 reserve = 258, the widest a pushed edge may reach.
        assertEquals(258.dp, navigatorResizeWidth(200.dp, 400.dp, NavigatorLayout.Pushed, 630.dp))
        // The same drag floats to the full 520 ceiling, so the drawer never pushes the content.
        assertEquals(NavigatorMaxWidth, navigatorResizeWidth(200.dp, 400.dp, NavigatorLayout.Floating, 630.dp))
        // A hidden navigator has no edge, so the preference is left untouched.
        assertEquals(275.dp, navigatorResizeWidth(275.dp, 50.dp, NavigatorLayout.Hidden, 1440.dp))
    }

    @Test
    fun resizeStartsFromTheLaidOutWidthSoAClampedDrawerDragsContinuously() {
        // A 520dp preference renders 258dp at a 630dp pushed window, so the first leftward drag has
        // to move the visible edge instead of spending the 520 - 258 gap as a dead zone.
        assertEquals(258.dp, navigatorWidth(NavigatorLayout.Pushed, 630.dp, preferredWidth = NavigatorMaxWidth))
        assertEquals(248.dp, navigatorResizeWidth(258.dp, (-10).dp, NavigatorLayout.Pushed, 630.dp))
        // The floating drawer is clamped to the room at a narrow window too; the drag starts from
        // that actual width and still stops at the 240dp floor.
        assertEquals(248.dp, navigatorWidth(NavigatorLayout.Floating, 300.dp, preferredWidth = NavigatorMaxWidth))
        assertEquals(240.dp, navigatorResizeWidth(248.dp, (-10).dp, NavigatorLayout.Floating, 300.dp))
    }
}
