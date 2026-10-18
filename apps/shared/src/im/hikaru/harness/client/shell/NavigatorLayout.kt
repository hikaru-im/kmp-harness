package im.hikaru.harness.client.shell

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import im.hikaru.harness.ui.foundation.HarnessTokens

/**
 * How the Workspace Navigator is presented at one available window width.
 *
 * The App Rail is fixed at every Desktop width, so the navigator is the region that changes shape:
 * - [Hidden]: the user collapsed it.
 * - [Pushed]: expanded beside the rail; the main surface moves right by [navigatorWidth].
 * - [Floating]: expanded above the main surface so the content keeps [MainSurfaceMinWidth].
 *
 * Codex derives the same split from `--spacing-token-sidebar`
 * `clamp(240px, 275px, min(520px, calc(100vw - 320px)))`: the navigator never drops below 240px
 * while pushed, and the window always reserves 320px for the main surface. When even the pushed
 * minimum would eat into that reserve, the reference state matrix calls the navigator "temporarily
 * expanded" and the drawer floats instead of squeezing the content.
 */
public enum class NavigatorLayout {
    Hidden,
    Pushed,
    Floating,
}

/** Codex `--spacing-token-sidebar` lower clamp bound, in dp. */
public val NavigatorMinWidth: Dp = 240.dp

/**
 * Codex `--spacing-token-sidebar` upper clamp bound, in dp: `min(520px, calc(100vw - 320px))`. The
 * navigator is user-sized, so the ceiling is a real limit rather than the preferred width.
 */
public val NavigatorMaxWidth: Dp = 520.dp

/**
 * Width Codex opens the navigator at (`--codex-sidebar-preferred-width`, 275px by default). The
 * reference window capture measures 288px, which is a stored user width inside
 * [NavigatorMinWidth]..[NavigatorMaxWidth], not the Codex default; see
 * docs/desktop-ui/reference/rail-measurement-2026-10-05.md.
 */
public val NavigatorPreferredWidth: Dp = HarnessTokens.Control.SidebarExpanded

/** All mounted navigators share the user-selected width, independent of page content. */
public fun pageNavigatorWidth(
    kind: NavigatorKind,
    workspacePreferredWidth: Dp = NavigatorPreferredWidth,
): Dp =
    if (kind == NavigatorKind.None) 0.dp else workspacePreferredWidth

/** Codex keeps `calc(100vw - 320px)` for the window; the main surface never goes below this. */
public val MainSurfaceMinWidth: Dp = 320.dp

/**
 * Width below which an expanded navigator floats instead of pushing. The rail is mounted at every
 * width, so below the compact-shell size there is no room to push a column beside it; the drawer
 * goes over the content instead, which keeps the main surface at its own width and keeps the
 * context/summary panels from switching to their overlay modes too early.
 */
public val NavigatorFloatBelow: Dp = 640.dp

/** Layout of the navigator for [mode] at [availableWidth]. */
public fun navigatorLayout(
    mode: SidebarMode,
    availableWidth: Dp,
): NavigatorLayout =
    when (mode) {
        SidebarMode.Collapsed -> NavigatorLayout.Hidden
        SidebarMode.TemporarilyExpanded -> NavigatorLayout.Floating
        SidebarMode.Expanded ->
            if (availableWidth < NavigatorFloatBelow ||
                availableWidth - HarnessTokens.Control.RailWidth - NavigatorMinWidth < MainSurfaceMinWidth
            ) {
                NavigatorLayout.Floating
            } else {
                NavigatorLayout.Pushed
            }
    }

/**
 * Width of the navigator for [layout] at [availableWidth]. A pushed navigator shrinks so the main
 * surface keeps [MainSurfaceMinWidth]; a floating one only shrinks when the window is narrower than
 * its preferred width.
 */
public fun navigatorWidth(
    layout: NavigatorLayout,
    availableWidth: Dp,
    preferredWidth: Dp = NavigatorPreferredWidth,
): Dp {
    // The fixed rail is a side column, so the floating drawer starts past it.
    val room = availableWidth - HarnessTokens.Control.RailWidth
    val wanted = preferredWidth.coerceIn(NavigatorMinWidth, NavigatorMaxWidth)
    return when (layout) {
        NavigatorLayout.Hidden -> 0.dp
        // Pushed never squeezes the main surface below its reserve; the layout rule guarantees the
        // result stays at or above [NavigatorMinWidth] whenever it chooses Pushed.
        NavigatorLayout.Pushed -> minOf(wanted, room - MainSurfaceMinWidth)
        // A floating drawer keeps its minimum even when the window is narrower than the drawer.
        NavigatorLayout.Floating -> minOf(wanted, maxOf(room, NavigatorMinWidth))
    }
}

/**
 * Width after the user drags the navigator's right edge by [dragDelta]. [currentWidth] must be the
 * width the shell actually laid out (see [navigatorWidth]), not the stored preference: when the
 * layout clamped a larger preference, a preference-based start would leave a dead zone before the
 * edge moved. The result stays within the Codex --spacing-token-sidebar range
 * [NavigatorMinWidth]..[NavigatorMaxWidth] and, while [layout] is [NavigatorLayout.Pushed], it also
 * stops at the room left once the main surface keeps [MainSurfaceMinWidth]. A floating drawer keeps
 * the same 240..520 clamp. A hidden navigator has no edge to drag, so the current width is returned
 * unchanged.
 */
public fun navigatorResizeWidth(
    currentWidth: Dp,
    dragDelta: Dp,
    layout: NavigatorLayout,
    availableWidth: Dp,
): Dp {
    val room = availableWidth - HarnessTokens.Control.RailWidth
    val ceiling =
        when (layout) {
            NavigatorLayout.Hidden -> return currentWidth
            NavigatorLayout.Pushed -> minOf(NavigatorMaxWidth, room - MainSurfaceMinWidth)
            NavigatorLayout.Floating -> NavigatorMaxWidth
        }
    // A window too narrow to hold the minimum still gets an ordered range instead of an exception.
    val floor = minOf(NavigatorMinWidth, ceiling)
    val upper = maxOf(ceiling, floor)
    return (currentWidth + dragDelta).coerceIn(floor, upper)
}

/** Extra inward travel after reaching the minimum; keeps merely touching the edge from closing it. */
public val NavigatorCollapseDragDistance: Dp = 24.dp

/** One pointer gesture, independent of recomposition. Collapsing is emitted only once per gesture. */
public class NavigatorResizeDragState {
    private var width: Dp = NavigatorMinWidth
    private var inwardTravel: Dp = 0.dp
    private var collapsed: Boolean = false

    public fun start(currentWidth: Dp) {
        width = currentWidth
        inwardTravel = 0.dp
        collapsed = false
    }

    public fun drag(delta: Dp, layout: NavigatorLayout, availableWidth: Dp): NavigatorResizeResult {
        if (collapsed || layout == NavigatorLayout.Hidden) return NavigatorResizeResult(width, false)
        val requested = width + delta
        inwardTravel = if (delta > 0.dp) 0.dp else inwardTravel + maxOf(0.dp, NavigatorMinWidth - requested)
        width = navigatorResizeWidth(width, delta, layout, availableWidth)
        collapsed = inwardTravel >= NavigatorCollapseDragDistance
        return NavigatorResizeResult(width, collapsed)
    }
}

public data class NavigatorResizeResult(val width: Dp, val collapse: Boolean)
