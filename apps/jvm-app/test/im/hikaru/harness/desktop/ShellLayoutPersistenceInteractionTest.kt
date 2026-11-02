package im.hikaru.harness.desktop

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import im.hikaru.harness.client.app.HarnessApp
import im.hikaru.harness.client.shell.MemoryShellLayoutStore
import im.hikaru.harness.client.shell.ShellLayoutRepository
import im.hikaru.harness.client.shell.ShellLayoutSettings
import im.hikaru.harness.client.shell.SidebarMode
import im.hikaru.harness.ui.foundation.HarnessTheme
import kotlin.test.*

/** The shell writes a settled drag once and restores it on the next mount. */
@OptIn(ExperimentalTestApi::class)
class ShellLayoutPersistenceInteractionTest {
    @Test
    fun resizingTheNavigatorPersistsOneWidthAfterTheDragSettles() = runSkikoComposeUiTest(size = Size(1200f, 800f)) {
        val store = MemoryShellLayoutStore()
        setContent {
            HarnessTheme {
                HarnessApp(
                    connection = null,
                    shellLayoutRepository = ShellLayoutRepository(store),
                )
            }
        }
        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag("navigator-resize-handle").fetchSemanticsNodes().isNotEmpty()
        }
        fun bounds(tag: String): Rect =
            onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val before = bounds("workspace-navigator").width
        val edge = bounds("navigator-resize-handle").center
        onRoot().performMouseInput {
            moveTo(edge)
            press()
            advanceEventTime(16)
            moveTo(edge + Offset(120f, 0f))
            advanceEventTime(16)
            release()
        }
        mainClock.advanceTimeBy(500)
        waitForIdle()

        val persisted = assertNotNull(store.current)
        assertTrue(
            persisted.navigatorWidth > before,
            "persisted=${persisted.navigatorWidth} before=$before",
        )
    }

    @Test
    fun aStoredCollapsedNavigatorIsRestoredOnTheNextMount() = runSkikoComposeUiTest(size = Size(1200f, 800f)) {
        val repository = ShellLayoutRepository(
            MemoryShellLayoutStore(ShellLayoutSettings(navigatorMode = SidebarMode.Collapsed)),
        )
        setContent {
            HarnessTheme {
                HarnessApp(connection = null, shellLayoutRepository = repository)
            }
        }

        waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag("workspace-navigator").fetchSemanticsNodes().isEmpty()
        }
        onNodeWithTag("navigator-overlay").assertDoesNotExist()
    }
}
