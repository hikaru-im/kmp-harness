package im.hikaru.harness.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import im.hikaru.harness.client.app.HarnessApp
import im.hikaru.harness.client.app.HarnessDestination
import kotlin.test.Test

/**
 * M0 structure baseline for the Settings surface (SET-01/SET-02/SET-04).
 *
 * These assertions always run; the matching same-viewport captures live in
 * [SettingsPanelsScreenshotTest] and are written only when its capture directory is configured.
 */
@OptIn(ExperimentalTestApi::class)
class SettingsPanelsInteractionTest {
    @Test
    fun everySettingsRowExposesItsStablePanelTag() = runSkikoComposeUiTest(size = Size(1440f, 900f)) {
        setContent {
            Box(Modifier.fillMaxSize()) {
                HarnessApp(null, initialDestination = HarnessDestination.Settings)
            }
        }

        onNodeWithTag("settings-navigator").assertExists()
        onNodeWithTag("settings-row-general").assertIsSelected()
        onNodeWithTag("settings-panel-general-settings").assertExists()
        // The M0 tag is additive: current tests keep using the row-slug content tag.
        onNodeWithTag("settings-content-general").assertExists()

        val panels = listOf(
            "appearance" to "settings-panel-appearance",
            "models" to "settings-panel-models",
            "keyboard-shortcuts" to "settings-panel-keyboard-shortcuts",
            "connections" to "settings-panel-connections",
            "git" to "settings-panel-git-settings",
            "worktrees" to "settings-panel-worktrees",
        )
        panels.forEach { (row, panelTag) ->
            onNodeWithTag("settings-row-$row").performClick()
            onNodeWithTag("settings-row-$row").assertIsSelected()
            onNodeWithTag(panelTag).assertExists()
            onNodeWithTag("settings-content-$row").assertExists()
        }

        // The plugins row leaves Settings for its own page; that page carries the same panel
        // identity the Codex plugins-settings panel uses (SET-20).
        onNodeWithTag("settings-row-plugins").performClick()
        onNodeWithTag("settings-panel-plugins-settings").assertExists()
        onNodeWithTag("installed-plugins-page").assertExists()
    }

    @Test
    fun settingsSearchFiltersRowsAndKeepsTheHarnessPlaceholderUntilSet04() = runSkikoComposeUiTest(size = Size(1440f, 900f)) {
        setContent {
            Box(Modifier.fillMaxSize()) {
                HarnessApp(null, initialDestination = HarnessDestination.Settings)
            }
        }

        onNodeWithTag("settings-navigator-search").assertExists()
        // SET-04 / D-09: Harness still says 搜索设置; the aligned Codex placeholder is 搜索. This
        // assertion is the M0 baseline, not the M4 change.
        onNodeWithText("搜索设置").assertExists()
        onNodeWithTag("settings-navigator-search").assertContentDescriptionEquals("搜索设置")

        onNodeWithTag("settings-navigator-search").performTextInput("Git")
        onNodeWithTag("settings-row-git").assertExists()
        onNodeWithTag("settings-row-appearance").assertDoesNotExist()
        onNodeWithTag("settings-row-models").assertDoesNotExist()

        onNodeWithTag("settings-navigator-search").performTextClearance()
        onNodeWithTag("settings-row-appearance").assertExists()
        onNodeWithTag("settings-row-models").assertExists()
    }
}
