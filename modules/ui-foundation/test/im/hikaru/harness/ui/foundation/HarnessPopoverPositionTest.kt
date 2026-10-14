package im.hikaru.harness.ui.foundation

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class HarnessPopoverPositionTest {
    private val window = IntSize(800, 600)
    private val popup = IntSize(180, 120)
    private val anchor = IntRect(300, 200, 380, 240)

    @Test
    fun sideAndAlignmentUseTheAnchorWithoutChangingItsLayout() {
        assertEquals(IntOffset(300, 246), harnessPopoverPosition(anchor, window, popup))
        assertEquals(IntOffset(250, 246), harnessPopoverPosition(anchor, window, popup, align = HarnessPopoverAlign.Center))
        assertEquals(IntOffset(200, 246), harnessPopoverPosition(anchor, window, popup, align = HarnessPopoverAlign.End))
        assertEquals(IntOffset(300, 74), harnessPopoverPosition(anchor, window, popup, side = HarnessPopoverSide.Top))
        assertEquals(IntOffset(114, 200), harnessPopoverPosition(anchor, window, popup, side = HarnessPopoverSide.Start))
        assertEquals(IntOffset(386, 200), harnessPopoverPosition(anchor, window, popup, side = HarnessPopoverSide.End))
    }

    @Test
    fun startEndAlignmentAndSidesFollowRtl() {
        assertEquals(IntOffset(200, 246), harnessPopoverPosition(anchor, window, popup, layoutDirection = LayoutDirection.Rtl))
        assertEquals(IntOffset(300, 246), harnessPopoverPosition(anchor, window, popup, align = HarnessPopoverAlign.End, layoutDirection = LayoutDirection.Rtl))
        assertEquals(IntOffset(386, 200), harnessPopoverPosition(anchor, window, popup, side = HarnessPopoverSide.Start, layoutDirection = LayoutDirection.Rtl))
        assertEquals(IntOffset(114, 200), harnessPopoverPosition(anchor, window, popup, side = HarnessPopoverSide.End, layoutDirection = LayoutDirection.Rtl))
    }

    @Test
    fun insufficientSpaceFlipsToTheOppositeSide() {
        assertEquals(IntOffset(300, 424), harnessPopoverPosition(IntRect(300, 550, 380, 590), window, popup))
        assertEquals(IntOffset(300, 56), harnessPopoverPosition(IntRect(300, 10, 380, 50), window, popup, side = HarnessPopoverSide.Top))
        assertEquals(IntOffset(564, 200), harnessPopoverPosition(IntRect(750, 200, 790, 240), window, popup, side = HarnessPopoverSide.End))
        assertEquals(IntOffset(56, 200), harnessPopoverPosition(IntRect(10, 200, 50, 240), window, popup, side = HarnessPopoverSide.Start))
    }

    @Test
    fun everyEdgeAndOffscreenAnchorKeepsFittingContentInsideTheWindow() {
        val anchors = listOf(IntRect(0, 0, 20, 20), IntRect(790, 590, 800, 600), IntRect(-80, -50, -20, -10), IntRect(900, 700, 940, 740))
        anchors.forEach { a ->
            HarnessPopoverSide.entries.forEach { side ->
                HarnessPopoverAlign.entries.forEach { align ->
                    LayoutDirection.entries.forEach { direction ->
                        val position = harnessPopoverPosition(a, window, popup, side, align, layoutDirection = direction)
                        assertTrue(position.x >= 8 && position.x + popup.width <= window.width - 8)
                        assertTrue(position.y >= 8 && position.y + popup.height <= window.height - 8)
                    }
                }
            }
        }
    }

    @Test
    fun oversizedContentOrTinyWindowsDoNotProduceInvalidClampRanges() {
        assertEquals(IntOffset.Zero, harnessPopoverPosition(anchor, IntSize(10, 10), popup))
        assertEquals(IntOffset.Zero, harnessPopoverPosition(anchor, IntSize.Zero, popup))
        assertFailsWith<IllegalArgumentException> { harnessPopoverPosition(anchor, window, popup, margin = -1) }
        assertFailsWith<IllegalArgumentException> { harnessPopoverPosition(anchor, window, popup, sideOffset = -1) }
    }
}
