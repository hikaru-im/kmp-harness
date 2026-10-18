package im.hikaru.harness.client.shell

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NavigatorResizeDragTest {
    @Test fun touchingMinimumDoesNotCollapseButContinuingInwardDoes() {
        val drag = NavigatorResizeDragState()
        drag.start(300.dp)
        assertEquals(NavigatorResizeResult(240.dp, false), drag.drag((-60).dp, NavigatorLayout.Pushed, 1440.dp))
        assertFalse(drag.drag((-23).dp, NavigatorLayout.Pushed, 1440.dp).collapse)
        assertTrue(drag.drag((-1).dp, NavigatorLayout.Pushed, 1440.dp).collapse)
        assertFalse(drag.drag((-50).dp, NavigatorLayout.Pushed, 1440.dp).collapse, "close only once")
    }

    @Test fun reversingDirectionAndStartingAnotherGestureResetTheOverdrag() {
        val drag = NavigatorResizeDragState()
        drag.start(240.dp)
        assertFalse(drag.drag((-20).dp, NavigatorLayout.Floating, 800.dp).collapse)
        assertFalse(drag.drag(1.dp, NavigatorLayout.Floating, 800.dp).collapse)
        assertEquals(NavigatorResizeResult(240.dp, false), drag.drag((-21).dp, NavigatorLayout.Floating, 800.dp))
        drag.start(240.dp)
        assertFalse(drag.drag((-20).dp, NavigatorLayout.Floating, 800.dp).collapse)
        drag.start(240.dp)
        assertFalse(drag.drag((-20).dp, NavigatorLayout.Floating, 800.dp).collapse)
        assertTrue(drag.drag((-4).dp, NavigatorLayout.Floating, 800.dp).collapse)
    }

    @Test fun oneFastDragCanCrossBothMinimumAndCollapseThreshold() {
        val drag = NavigatorResizeDragState()
        drag.start(400.dp)
        assertEquals(NavigatorResizeResult(240.dp, true), drag.drag((-184).dp, NavigatorLayout.Pushed, 1440.dp))
        drag.start(240.dp)
        assertFalse(drag.drag((-100).dp, NavigatorLayout.Hidden, 1440.dp).collapse)
        assertEquals(NavigatorResizeResult(270.dp, false), drag.drag(30.dp, NavigatorLayout.Pushed, 1440.dp))
    }
}
