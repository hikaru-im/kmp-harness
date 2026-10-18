package im.hikaru.harness.desktop.window

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.dp
import im.hikaru.harness.client.shell.*
import im.hikaru.harness.ui.foundation.*
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class NavigatorCollapseInteractionTest {
    @Test fun inwardDragClosesOnceAndCanBeReopened() = runSkikoComposeUiTest(size = Size(1000f, 500f)) {
        var width by mutableStateOf(300.dp)
        var visible by mutableStateOf(true)
        var closes = 0
        val drag = NavigatorResizeDragState()
        setContent {
            HarnessTheme {
                val density = LocalDensity.current
                Box(Modifier.fillMaxSize().testTag("root")) {
                    if (visible) Box(Modifier.width(width).fillMaxHeight()) {
                        HarnessResizeHandle(
                            modifier = Modifier.align(Alignment.CenterEnd).testTag("edge"),
                            onResizeStart = { drag.start(width) },
                            onResize = {
                                val result = drag.drag(with(density) { it.toDp() }, NavigatorLayout.Pushed, 1000.dp)
                                width = result.width
                                if (result.collapse) { visible = false; closes++ }
                            },
                        )
                    }
                }
            }
        }
        val start = onNodeWithTag("edge").fetchSemanticsNode().boundsInRoot.center
        onNodeWithTag("root").performMouseInput {
            moveTo(start); press(); moveTo(start - Offset(60f, 0f), delayMillis = 40)
        }
        onNodeWithTag("edge").assertExists()
        onNodeWithTag("root").performMouseInput {
            moveTo(start - Offset(180f, 0f), delayMillis = 40); release()
        }
        onNodeWithTag("edge").assertDoesNotExist()
        runOnIdle {
            assertEquals(1, closes)
            assertEquals(NavigatorMinWidth, width)
            visible = true
        }
        onNodeWithTag("edge").assertIsDisplayed()
        onNodeWithTag("edge").performMouseInput {
            moveTo(center); press(); moveTo(center + Offset(80f, 0f), delayMillis = 40); release()
        }
        runOnIdle { assertTrue(width > NavigatorMinWidth); assertEquals(1, closes) }
    }
}
