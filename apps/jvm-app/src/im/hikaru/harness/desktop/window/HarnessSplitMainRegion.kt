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
import im.hikaru.harness.client.shell.HarnessTabLayout
import im.hikaru.harness.client.shell.HarnessTabStripState
import im.hikaru.harness.ui.foundation.HarnessTokens

/**
 * The window's main region for the title-bar tabs: one page surface, or two divided by a draggable
 * seam while the tab strip is split.
 *
 * This wraps only the region right of the navigator ([HarnessMainRegion]); the App Rail and the
 * navigator are rendered once by the shell and are never duplicated by a split.
 *
 * Split rendering follows the measured seam: the panes paint nothing themselves, the separator is a
 * 16px `cursor-col-resize` band, and the mark inside it is a 1px full-height *vertical* gradient
 * (`transparent → text/25% → transparent`) that is invisible until the band is hovered or dragged.
 * The seam is draggable, because Codex's panes are user-sized rather than equal halves.
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
            // the navigator is outside it and cannot split the panes.
            drag.regionBounds = Rect(
                it.positionInWindow(),
                androidx.compose.ui.geometry.Size(
                    it.size.width.toFloat(),
                    it.size.height.toFloat(),
                ),
            )
        },
    ) {
        when (val layout = state.layout) {
            HarnessTabLayout.Single -> surface(Modifier.fillMaxSize())
            is HarnessTabLayout.Split -> Row(Modifier.fillMaxSize()) {
                val fraction = state.splitFraction
                Box(Modifier.weight(fraction).fillMaxHeight().clipToBounds().testTag("harness-split-left")) {
                    surface(Modifier.fillMaxSize())
                }
                SplitSeam(state, regionWidth)
                Box(Modifier.weight(1f - fraction).fillMaxHeight().clipToBounds().testTag("harness-split-right")) {
                    surface(Modifier.fillMaxSize())
                }
            }
        }
        // The drop halves cover the main region during a tab drag, which is the area the tab would
        // take over — the shell's navigation chrome is not a drop target.
        if (drag.active) Row(Modifier.fillMaxSize()) {
            DropHalf("harness-drop-left", drag.targetRight == false)
            DropHalf("harness-drop-right", drag.targetRight == true)
        }
    }
}

/**
 * The pane separator. Measured: a 16px band (`cursor-col-resize`), a 1px line centred in it, and the
 * line's ink is a vertical gradient — `transparent → text/25% → transparent` — at `opacity: 0` until
 * the band is hovered, dragged or focused. Nothing at all is painted while the seam is idle, so the
 * panes read as one surface with a hairline inset between them.
 *
 * The seam reports a pixel delta and the state applies it to the live fraction, so a drag cannot act
 * on a fraction captured before the gesture started.
 */
@Composable
private fun SplitSeam(state: HarnessTabStripState, regionWidth: Float) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    var dragging by remember { mutableStateOf(false) }
    // Read the ink in the composition: the draw lambda below is not a composable scope.
    val seamInk = HarnessTokens.Colors.ChromeSeam
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

@Composable
private fun RowScope.DropHalf(tag: String, targeted: Boolean) {
    val shape = RoundedCornerShape(HarnessTokens.Corners.Large)
    Box(Modifier.weight(1f).fillMaxHeight().padding(HarnessTokens.Spacing.Two).clip(shape)
        .border(1.dp, HarnessTokens.Colors.BorderFocused, shape)
        .background(HarnessTokens.Colors.SurfaceSelected.copy(alpha = if (targeted) 0.55f else 0.15f))
        .testTag(tag))
}

/** The window's [HarnessMainRegion]: the tab strip owns the split, the shell owns the chrome. */
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
