package im.hikaru.harness.desktop.window

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import im.hikaru.harness.client.shell.HarnessMainRegion
import im.hikaru.harness.client.shell.HarnessPaneSide
import im.hikaru.harness.client.shell.HarnessTabLayout
import im.hikaru.harness.client.shell.HarnessTabStripState
import im.hikaru.harness.client.shell.HarnessWorkspaceTab
import im.hikaru.harness.client.shell.LocalHarnessPaneTab
import im.hikaru.harness.ui.foundation.HarnessTokens

/**
 * The window's main region for the side panel's tabs.
 *
 * The two panes are **not** two tabs. One is the *conversation* — which is not a tab at all, and is drawn
 * through [LocalHarnessPaneTab] as `null` so the shell renders its own page in it — and the other is the
 * *side panel*, which is the thing that holds tabs. A split therefore always shows the conversation
 * beside the side panel's selected tab, never the same page twice, and the side panel's tab is handed to
 * exactly one pane.
 *
 * While the panel is closed ([HarnessTabStripState.tabsHidden]) the conversation is the only pane, which
 * is what the live shell shows with its `aside` unmounted; in 完整视图 the side panel is the only pane and
 * the conversation is pushed off screen.
 *
 * This wraps only the region right of the navigator ([HarnessMainRegion]); the App Rail and the
 * navigator are rendered once by the shell and are never duplicated by a split.
 *
 * Split rendering follows the measured seam: the separator is a 16px `cursor-col-resize` band with a
 * transparent fill, the divider an idle split shows is the 1px hairline at its centre (ink at 7% —
 * measured 239 in the boundary column on every row of the live seam), and hovering or dragging the band
 * thickens that line into the resizer's own mark, a full-height *vertical* gradient
 * (`transparent → text/25% → transparent`). The seam is draggable, because Codex's panes are
 * user-sized rather than equal halves.
 */
@Composable
public fun HarnessSplitMainRegion(
    state: HarnessTabStripState,
    drag: HarnessTabDragState,
    modifier: Modifier = Modifier,
    surface: @Composable (Modifier) -> Unit,
) {
    var regionWidth by remember { mutableStateOf(0f) }
    Box(
        modifier.fillMaxSize()
            // One page surface across the whole main region, as measured on the live shell (the main
            // content surface is opaque white): both panes and the 16px seam between them sit on it, so
            // the divider reads as the page surface instead of a band of whatever is composed behind it.
            .background(HarnessTokens.Colors.Canvas)
            .testTag("harness-tab-main-region").onGloballyPositioned {
            regionWidth = it.size.width.toFloat()
            // Drop targeting reads the window rect of this region, so a tab released over the rail or
            // the navigator is outside it and cannot move the panel into the other half.
            drag.regionBounds = Rect(
                it.positionInWindow(),
                androidx.compose.ui.geometry.Size(
                    it.size.width.toFloat(),
                    it.size.height.toFloat(),
                ),
            )
        },
    ) {
        val panelTab = state.sidePanelTab
        when {
            // Off a chat page the live shell mounts no side panel at all, so the page the conversation
            // is showing takes the whole region — navigator to window edge.
            !state.onChatPage -> ConversationPane { surface(Modifier.fillMaxSize()) }
            // The panel is closed: the conversation is the window.
            state.tabsHidden -> ConversationPane { surface(Modifier.fillMaxSize()) }
            // 完整视图: the panel has the whole main region and the conversation is off screen.
            state.layout == HarnessTabLayout.Full ->
                PaneTab(panelTab) { surface(Modifier.fillMaxSize()) }
            else -> Row(Modifier.fillMaxSize()) {
                val fraction = state.splitFraction
                // The left half keeps `splitFraction` of the workspace whichever pane it is, so the
                // seam's own drag stays a plain left-pane resize after a side swap.
                Pane(
                    weight = fraction,
                    tag = "harness-split-left",
                    conversation = state.sidePanelSide == HarnessPaneSide.Right,
                    tab = panelTab,
                    surface = surface,
                )
                SplitSeam(state, regionWidth)
                Pane(
                    weight = 1f - fraction,
                    tag = "harness-split-right",
                    conversation = state.sidePanelSide == HarnessPaneSide.Left,
                    tab = panelTab,
                    surface = surface,
                )
            }
        }
        // The drop halves cover the main region during a tab drag, which is the area the tab would
        // take over — the shell's navigation chrome is not a drop target, and a page with no panel has
        // nothing to drop a tab into.
        if (drag.active && state.onChatPage) Row(Modifier.fillMaxSize()) {
            DropHalf("harness-drop-left", drag.targetRight == false)
            DropHalf("harness-drop-right", drag.targetRight == true)
        }
    }
}

/**
 * One half of the split. A **conversation** pane holds no tab — it is handed `null`, which is how a pane
 * tells the shell to draw its own page — while the side panel's pane is handed the tab it shows.
 */
@Composable
private fun RowScope.Pane(
    weight: Float,
    tag: String,
    conversation: Boolean,
    tab: HarnessWorkspaceTab?,
    surface: @Composable (Modifier) -> Unit,
) {
    Box(Modifier.weight(weight).fillMaxHeight().clipToBounds().testTag(tag)) {
        PaneTab(if (conversation) null else tab) { surface(Modifier.fillMaxSize()) }
    }
}

/** The conversation on its own — the closed panel's only pane, and never a tab. */
@Composable
private fun ConversationPane(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().testTag("harness-tab-pane-main")) {
        PaneTab(null) { content() }
    }
}

/**
 * The pane separator. Measured: a 16px band (`cursor-col-resize`) whose fill is transparent, and the
 * boundary the two panes meet at is drawn *in* the band as a 1px line — ink at 7%, the value the live
 * seam's boundary column holds (239) on every row from 60 to 1300 while the pane beside it reads 255.
 * One pixel, not a band: that line is the divider an idle split shows, and a band that painted nothing
 * left the two panes reading as one surface.
 *
 * Pointing at, dragging or focusing the band thickens the same line into the resizer's own mark: a
 * full-height vertical gradient — `transparent → text/25% → transparent` — over the idle hairline.
 *
 * The seam reports a pixel delta and the state applies it to the live fraction, so a drag cannot act
 * on a fraction captured before the gesture started.
 */
@Composable
private fun SplitSeam(state: HarnessTabStripState, regionWidth: Float) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    var dragging by remember { mutableStateOf(false) }
    // Read the inks in the composition: the draw lambdas below are not composable scopes.
    val seamInk = HarnessTokens.Colors.ChromeSeam
    val seamEdgeInk = HarnessTokens.Colors.ChromeSeamEdge
    Box(
        Modifier.width(HarnessTokens.Control.SplitSeamWidth).fillMaxHeight()
            .hoverable(source)
            .pointerInput(state) {
                detectDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                ) { change, dragAmount ->
                    change.consume()
                    state.moveSplit(dragAmount.x, regionWidth)
                }
            }
            .semantics { contentDescription = "调整窗格大小" }
            .testTag("harness-split-seam"),
        contentAlignment = Alignment.Center,
    ) {
        // The idle divider: the boundary hairline, one pixel at the band's centre — where the two panes
        // divide.
        Box(
            Modifier.width(HarnessTokens.Control.SplitSeamLine).fillMaxHeight()
                .drawBehind { drawRect(seamEdgeInk) },
        )
        // The resizer's own mark, revealed with the pointer.
        Box(
            Modifier.width(HarnessTokens.Control.SplitSeamLine).fillMaxHeight()
                .alpha(if (hovered || dragging) 1f else 0f)
                .drawBehind {
                    drawRect(
                        Brush.verticalGradient(listOf(Color.Transparent, seamInk, Color.Transparent)),
                    )
                },
        )
    }
}

/**
 * Tells the surface which tab it is drawing. Passing `null` is meaningful: it is the conversation pane
 * saying "I am not a tab", and the shell then draws its own current page there.
 */
@Composable
private fun PaneTab(tab: HarnessWorkspaceTab?, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalHarnessPaneTab provides tab) { content() }
}

@Composable
private fun RowScope.DropHalf(tag: String, targeted: Boolean) {
    val shape = RoundedCornerShape(HarnessTokens.Corners.Large)
    Box(Modifier.weight(1f).fillMaxHeight().padding(HarnessTokens.Spacing.Two).clip(shape)
        .border(1.dp, HarnessTokens.Colors.BorderFocused, shape)
        .background(HarnessTokens.Colors.SurfaceSelected.copy(alpha = if (targeted) 0.55f else 0.15f))
        .testTag(tag))
}

/** The window's [HarnessMainRegion]: the side panel's strip owns the layout, the shell owns the chrome. */
private class TabMainRegion(
    private val state: HarnessTabStripState,
    private val drag: HarnessTabDragState,
) : HarnessMainRegion {
    @Composable
    override fun Panes(modifier: Modifier, surface: @Composable (Modifier) -> Unit) {
        HarnessSplitMainRegion(state, drag, modifier, surface)
    }
}

@Composable
public fun rememberHarnessTabMainRegion(
    state: HarnessTabStripState,
    drag: HarnessTabDragState,
): HarnessMainRegion = remember(state, drag) { TabMainRegion(state, drag) }
