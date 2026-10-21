package im.hikaru.harness.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.unit.dp
import im.hikaru.harness.client.app.HarnessTitleStripState
import im.hikaru.harness.client.app.LocalHarnessTitleStrip
import im.hikaru.harness.client.settings.ShortcutPlatform
import im.hikaru.harness.client.settings.ShortcutsRepository
import im.hikaru.harness.ui.foundation.HarnessButton
import kotlinx.coroutines.runBlocking

/**
 * Shortcuts for bare-app fixtures whose toggle is driven by the ⇧⌘S chord.
 *
 * `HarnessApp` falls back to `ShortcutPlatform.Unavailable` when no repository is passed — the shared
 * layer cannot detect the host OS — and that platform binds no chords at all. Desktop supplies the
 * real platform in `main.kt`; a test that types macOS chords has to supply the matching repository.
 */
internal fun loadedMacShortcuts(): ShortcutsRepository =
    ShortcutsRepository(platform = ShortcutPlatform.Mac).also { runBlocking { it.load() } }

/**
 * The navigator toggle is window chrome: the desktop window draws it in its title strip from the
 * page's `navigatorToggle`, and a bare `HarnessApp` has no strip. Tests that mount the app alone
 * therefore drive the same production action through its default ⇧⌘S chord, which `HarnessApp`
 * handles whenever it owns the shortcut root.
 */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.toggleSidebarByShortcut() {
    onRoot().performKeyInput {
        keyDown(Key.MetaLeft)
        keyDown(Key.ShiftLeft)
        pressKey(Key.S)
        keyUp(Key.ShiftLeft)
        keyUp(Key.MetaLeft)
    }
    waitForIdle()
}

/**
 * Mounts the window's navigator toggle above [content].
 *
 * A bare `HarnessApp` has no title strip, and the ⇧⌘S chord cannot stand in for the toggle once a
 * text field owns desktop character input: the platform routes those keys to the field, so the
 * root chord handler never sees them. This draws the window's own control — the page publishes it
 * into `navigatorToggle` — so tests can flip the navigator by pointer at any point in a flow.
 */
@Composable
internal fun WithWindowNavigatorToggle(content: @Composable () -> Unit) {
    val strip = remember { HarnessTitleStripState() }
    CompositionLocalProvider(LocalHarnessTitleStrip provides strip) {
        Column(Modifier.fillMaxSize()) {
            val toggle = strip.navigatorToggle
            Box(Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.Center) {
                HarnessButton(
                    text = "切换导航",
                    onClick = { toggle?.invoke() },
                    enabled = toggle != null,
                    testTag = "fixture-navigator-toggle",
                )
            }
            Box(Modifier.fillMaxWidth().weight(1f)) { content() }
        }
    }
}
