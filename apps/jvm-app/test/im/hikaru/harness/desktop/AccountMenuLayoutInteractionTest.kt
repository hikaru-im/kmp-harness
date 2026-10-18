package im.hikaru.harness.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.dp
import im.hikaru.harness.client.app.HarnessApp
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class AccountMenuLayoutInteractionTest {
    @Test
    fun menuTracksItsButtonInsideAnOffsetShellAndDismissesWithoutNavigating() {
        for (size in listOf(Size(420f, 700f), Size(1440f, 900f))) {
            runSkikoComposeUiTest(size = size) {
                setContent {
                    Box(Modifier.padding(20.dp)) {
                        HarnessApp(null, modifier = Modifier.semantics { testTag = "offset-shell" })
                    }
                }
                val shell = onNodeWithTag("offset-shell").getUnclippedBoundsInRoot()
                val button = onNodeWithTag("rail-account").getUnclippedBoundsInRoot()
                assertTrue(button.right.value - button.left.value >= 36f, "account hit target")
                assertTrue(shell.bottom.value - button.bottom.value >= 16f, "bottom breathing room")
                onNodeWithTag("rail-account").performMouseInput { click() }
                val menu = onNodeWithTag("account-menu").getUnclippedBoundsInRoot()
                assertEquals(button.right.value + 8f, menu.left.value, 0.1f)
                assertEquals(button.bottom.value, menu.bottom.value, 0.1f)
                assertTrue(menu.left >= shell.left && menu.right <= shell.right)
                assertTrue(menu.top >= shell.top && menu.bottom <= shell.bottom)
                onRoot().performKeyInput { pressKey(Key.Escape) }
                onNodeWithTag("account-menu").assertDoesNotExist()
                onNodeWithTag("rail-account").performClick()
                onNodeWithTag("account-menu-settings").performMouseInput { click() }
                onNodeWithTag("settings-general-title").assertIsDisplayed()
            }
        }
    }
}
