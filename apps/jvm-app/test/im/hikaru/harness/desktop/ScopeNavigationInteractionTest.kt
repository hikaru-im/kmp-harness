package im.hikaru.harness.desktop

import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import im.hikaru.harness.client.app.HarnessApp
import im.hikaru.harness.client.app.HarnessDestination
import im.hikaru.harness.ui.foundation.HarnessThemeMode
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class ScopeNavigationInteractionTest {
    @Test
    fun disconnectedScheduledPageCannotCreateSyntheticTasks() = runComposeUiTest {
        setContent {
            HarnessApp(connection = null, initialDestination = HarnessDestination.ScheduledTasks, themeMode = HarnessThemeMode.Light)
        }
        onNodeWithTag("scheduled-new-task").assertIsNotEnabled()
        onNodeWithTag("scheduled-task:local-1").assertDoesNotExist()
        onNodeWithTag("scheduled-unbound-content").assertIsDisplayed()
    }

    @Test
    fun settingsRowsSwitchTheContentAndSelection() = runComposeUiTest {
        setContent {
            HarnessApp(connection = null, initialDestination = HarnessDestination.Settings, themeMode = HarnessThemeMode.Light)
        }
        onNodeWithTag("settings-content-general").assertIsDisplayed()
        listOf("appearance", "models", "keyboard-shortcuts", "connections", "git", "worktrees", "general").forEach { slug ->
            onNodeWithTag("settings-row-" + slug).performClick()
            onNodeWithTag("settings-row-" + slug).assertIsSelected()
            onNodeWithTag("settings-content-" + slug).assertIsDisplayed()
        }
        onNodeWithTag("settings-row-plugins").performClick()
        onNodeWithTag("plugins-navigator").assertIsDisplayed()
        onNodeWithTag("settings-navigator").assertDoesNotExist()
    }

    @Test
    fun compactRailStaysMountedAndReachesPluginsAndScheduledTasks() = runComposeUiTest {
        setContent {
            HarnessApp(
                connection = null,
                initialDestination = HarnessDestination.Account,
                modifier = Modifier.requiredWidth(600.dp),
                themeMode = HarnessThemeMode.Light,
            )
        }
        // The rail replaces the old compact bottom bar, so it is the navigation at 600dp.
        onNodeWithTag("app-rail").assertIsDisplayed()
        onNodeWithTag("rail-plugins").assertIsDisplayed().performClick()
        // The Plugins route exposes its content; a compact window floats the page-owned navigator
        // rather than pushing it. PluginsPageInteractionTest covers that contract in detail.
        onNodeWithTag("plugins-search").assertIsDisplayed()
        onNodeWithTag("rail-scheduled").assertIsDisplayed().performClick()
        onNodeWithTag("scheduled-navigator").assertIsDisplayed()
        onNodeWithTag("scheduled-toggle-search").performClick()
        onNodeWithTag("scheduled-search").assertIsDisplayed()
        onNodeWithTag("explore-menu").assertDoesNotExist()
    }
}
