package im.hikaru.harness.desktop

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import im.hikaru.harness.client.app.HarnessApp
import im.hikaru.harness.client.app.HarnessDestination
import im.hikaru.harness.client.page.SettingsShortcutsPage
import im.hikaru.harness.client.settings.*
import im.hikaru.harness.desktop.window.*
import im.hikaru.harness.ui.foundation.HarnessTheme
import im.hikaru.harness.ui.foundation.HarnessThemeMode
import kotlinx.coroutines.runBlocking
import kotlin.test.*

/** Exercises the production event root and consumers; all stores are test-local. */
@OptIn(ExperimentalTestApi::class)
class ShortcutsInteractionTest {
    private val custom = ShortcutChord(ShortcutKey.K, meta = true, shift = true)

    @Test
    fun realSettingsBackSidebarAndScheduledConsumersShareTheEffectiveMapping() = runComposeUiTest {
        val repository = loaded(ShortcutsSettings().withBindings(ShortcutCommand.ManageTasks, listOf(custom)))
        val controller = DesktopWindowCloseController()
        val focus = FocusRequester()
        setContent { WindowApp(repository, controller, CloseConfirmationPolicy.KeyboardOnly, focus) {} }
        runOnIdle { focus.requestFocus() }
        onNodeWithTag("shortcuts-test-key-target").shortcut(Key.Slash)
        onNodeWithTag("settings-content-keyboard-shortcuts").assertExists()
        onNodeWithTag("settings-row-keyboard-shortcuts").assertIsSelected()
        onNodeWithTag("shortcuts-test-key-target").shortcut(Key.Comma)
        onNodeWithTag("settings-content-general").assertExists()
        onNodeWithTag("shortcuts-test-key-target").shortcut(Key.LeftBracket)
        onNodeWithTag("settings-content-keyboard-shortcuts").assertExists()
        onNodeWithTag("settings-navigator").assertExists()
        onNodeWithTag("shortcuts-test-key-target").shortcut(Key.S, shift = true)
        onNodeWithTag("settings-navigator").assertDoesNotExist()
        onNodeWithTag("shortcuts-test-key-target").shortcut(Key.S, shift = true)
        onNodeWithTag("settings-navigator").assertExists()
        onNodeWithTag("shortcuts-test-key-target").shortcut(Key.K, shift = true)
        onNodeWithTag("scheduled-new-task").assertExists()
        onNodeWithTag("scheduled-navigator").assertExists()
        onNodeWithTag("shortcuts-test-key-target").shortcut(Key.LeftBracket)
        onNodeWithTag("settings-content-keyboard-shortcuts").assertExists()
        runOnIdle { assertEquals(WindowClosePhase.Open, controller.phase.value) }
    }

    @Test
    fun customRemovedAndResetCloseKeysUseTheRealControllerAndNativePolicy() = runComposeUiTest {
        val repository = loaded()
        var controller by mutableStateOf(DesktopWindowCloseController())
        var policy by mutableStateOf(CloseConfirmationPolicy.KeyboardOnly)
        var closes = 0
        val focus = FocusRequester()
        val close = { closes++; Unit }
        setContent { WindowApp(repository, controller, policy, focus, close = close) }
        runOnIdle {
            assertTrue(runBlocking { repository.save(ShortcutsSettings().withBindings(ShortcutCommand.CloseWindow, listOf(custom))) })
            focus.requestFocus()
        }
        onNodeWithTag("shortcuts-test-key-target").shortcut(Key.W)
        onNodeWithTag("window-close-dialog").assertDoesNotExist()
        onNodeWithTag("shortcuts-test-key-target").shortcut(Key.K, shift = true)
        onNodeWithTag("window-close-dialog").assertIsDisplayed()
        onNodeWithTag("window-close-dialog:cancel").shortcut(Key.Slash)
        onNodeWithTag("settings-content-keyboard-shortcuts").assertDoesNotExist()
        onNodeWithTag("window-close-dialog:cancel").performKeyInput { pressKey(Key.Enter) }
        onNodeWithTag("window-close-dialog").assertDoesNotExist()
        runOnIdle {
            assertEquals(0, closes)
            assertTrue(runBlocking { repository.remove(ShortcutCommand.CloseWindow, 0) })
            focus.requestFocus()
        }
        onNodeWithTag("shortcuts-test-key-target").shortcut(Key.K, shift = true)
        onNodeWithTag("shortcuts-test-key-target").shortcut(Key.W)
        onNodeWithTag("window-close-dialog").assertDoesNotExist()
        CloseConfirmationPolicy.entries.forEach { candidate ->
            runOnIdle { controller = DesktopWindowCloseController(); policy = candidate; closes = 0 }
            runOnIdle { controller.request(candidate, WindowCloseOrigin.NativeControl, close) }
            if (candidate == CloseConfirmationPolicy.Always) {
                onNodeWithTag("window-close-dialog").assertIsDisplayed()
                onNodeWithTag("window-close-dialog:confirm").performClick()
            }
            runOnIdle { assertEquals(1, closes); assertEquals(WindowClosePhase.Closing, controller.phase.value) }
        }
        runOnIdle {
            controller = DesktopWindowCloseController(); policy = CloseConfirmationPolicy.KeyboardOnly; closes = 0
            assertTrue(runBlocking { repository.reset(ShortcutCommand.CloseWindow) })
        }
        runOnIdle { focus.requestFocus() }
        onNodeWithTag("shortcuts-test-key-target").shortcut(Key.K, shift = true)
        onNodeWithTag("window-close-dialog").assertDoesNotExist()
        onNodeWithTag("shortcuts-test-key-target").shortcut(Key.W)
        onNodeWithTag("window-close-dialog:cancel").performKeyInput { pressKey(Key.Escape) }
        runOnIdle { assertEquals(0, closes); assertEquals(WindowClosePhase.Open, controller.phase.value) }
    }

    @Test
    fun recordingAndKeySearchPrecedeBothAppAndWindowDispatch() = runComposeUiTest {
        val repository = loaded()
        val controller = DesktopWindowCloseController()
        val focus = FocusRequester()
        var closes = 0
        setContent { WindowApp(repository, controller, CloseConfirmationPolicy.Never, focus) { closes++ } }
        runOnIdle { focus.requestFocus() }
        onNodeWithTag("shortcuts-test-key-target").shortcut(Key.Slash)
        runOnIdle { repository.beginRecording(ShortcutCommand.CloseWindow, 0) }
        onNodeWithTag("shortcuts-key-target").assertIsFocused().shortcut(Key.W)
        runOnIdle {
            assertEquals(ShortcutChord(ShortcutKey.W, meta = true), repository.state.value.editor?.chord)
            assertEquals(0, closes)
        }
        onNodeWithTag("shortcut-save").assertIsFocused().performKeyInput { pressKey(Key.Escape) }
        runOnIdle { assertNull(repository.state.value.editor) }
        onNodeWithTag("shortcuts-key-search").performScrollTo().performClick()
        onNodeWithTag("shortcuts-key-target").assertIsFocused().shortcut(Key.W)
        onNodeWithTag("shortcut-binding-CloseWindow-0").assertExists()
        onNodeWithTag("shortcut-binding-Settings-0").assertDoesNotExist()
        onNodeWithTag("shortcuts-key-target").shortcut(Key.Comma)
        onNodeWithTag("settings-content-keyboard-shortcuts").assertExists()
        onNodeWithTag("shortcuts-key-target").performKeyInput { pressKey(Key.Escape) }
        onNodeWithTag("shortcuts-key-search").assertIsFocused()
        runOnIdle { assertEquals(0, closes); assertEquals(WindowClosePhase.Open, controller.phase.value) }
    }

    @Test
    fun pageRetainsFailedRecordingThenRetryRemoveAndResetCommit() = runComposeUiTest {
        var fail = true
        val repository = ShortcutsRepository(object : ShortcutsStore {
            override val durable = true
            override suspend fun read(): ShortcutsSettings? = null
            override suspend fun write(settings: ShortcutsSettings) { if (fail) error("private test storage failure") }
        }, ShortcutPlatform.Mac)
        runBlocking { repository.load() }
        setContent { HarnessTheme { SettingsShortcutsPage(repository) } }
        onNodeWithTag("shortcuts-search").performTextInput("管理定时任务")
        onNodeWithTag("shortcut-add-ManageTasks").performScrollTo().performClick()
        onNodeWithTag("shortcuts-key-target").assertIsFocused().shortcut(Key.K, shift = true)
        onNodeWithTag("shortcut-save").assertIsFocused().performClick()
        onNodeWithTag("shortcuts-error").performScrollTo().assertIsDisplayed()
        onNodeWithTag("shortcut-recording-ManageTasks").assertExists()
        runOnIdle { assertNull(repository.commandFor(custom)); assertNotNull(repository.state.value.draft); fail = false }
        onNodeWithTag("shortcuts-retry").performScrollTo().performClick()
        onNodeWithTag("shortcut-add-ManageTasks").assertIsFocused()
        onNodeWithTag("shortcut-binding-ManageTasks-0").assertExists()
        runOnIdle { assertEquals(ShortcutCommand.ManageTasks, repository.commandFor(custom)) }
        onNodeWithTag("shortcut-remove-ManageTasks-0").performScrollTo().performClick()
        onNodeWithTag("shortcut-empty-ManageTasks").assertExists()
        onNodeWithTag("shortcuts-reset-all").performScrollTo().performClick()
        onNodeWithTag("shortcuts-reset-dialog:confirm").performClick()
        runOnIdle { assertTrue(repository.state.value.settings.overrides.isEmpty()) }
        onNodeWithTag("shortcuts-search").performTextClearance()
        onNodeWithTag("shortcuts-search").performTextInput("does-not-match")
        onNodeWithTag("shortcuts-no-match").assertExists()
    }

    @Test
    fun narrowPageControlsStayWithinTheForm() = runComposeUiTest {
        val repository = loaded()
        var width by mutableStateOf(420.dp)
        setContent {
            HarnessTheme(HarnessThemeMode.Dark) {
                Box(Modifier.requiredSize(width, 800.dp).semantics { testTag = "shortcuts-width-root" }) {
                    SettingsShortcutsPage(repository)
                }
            }
        }
        listOf(420.dp, 600.dp, 720.dp).forEach { size ->
            runOnIdle { width = size }
            val root = onNodeWithTag("shortcuts-width-root").getUnclippedBoundsInRoot()
            listOf("settings-shortcuts-title", "shortcuts-search", "shortcut-binding-Settings-0", "shortcut-remove-Settings-0").forEach { tag ->
                val bounds = onNodeWithTag(tag).getUnclippedBoundsInRoot()
                assertTrue(bounds.left >= root.left && bounds.right <= root.right, "$tag at $size: $bounds vs $root")
            }
        }
    }

    @Test
    fun ordinarySearchEditingAndOpenMenuKeepTheirOwnKeyboardBehavior() = runComposeUiTest {
        val repository = loaded()
        val controller = DesktopWindowCloseController()
        val focus = FocusRequester()
        // The confirm-close control is editable only when the platform exposes the close-policy
        // capability, which production enables (main.kt sets closePolicyAvailable = true). Provide
        // the same wiring so the menu this test opens is actually reachable.
        val generalRepository = GeneralSettingsRepository(MemoryGeneralSettingsStore(), closePolicyAvailable = true)
        runBlocking { generalRepository.load() }
        setContent { WindowApp(repository, controller, CloseConfirmationPolicy.Never, focus,
            generalRepository = generalRepository) {} }
        runOnIdle { focus.requestFocus() }
        onNodeWithTag("shortcuts-test-key-target").shortcut(Key.Slash)
        onNodeWithTag("shortcuts-search").performTextInput("draft")
        onNodeWithTag("shortcuts-search").shortcut(Key.A)
        onNodeWithTag("shortcuts-search").performKeyInput { pressKey(Key.Backspace) }
        // The empty field still renders its placeholder, which legitimately appears as Text; the
        // input value itself is what must be empty, so assert EditableText the way the shared
        // HarnessSearchField test does.
        onNodeWithTag("shortcuts-search")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        onNodeWithTag("shortcuts-test-key-target").shortcut(Key.Comma)
        onNodeWithTag("settings-general-control-confirm-close").performScrollTo()
            .assertIsDisplayed().assertIsEnabled().performMouseInput { click() }
        onNodeWithTag("menu:" + CloseConfirmationPolicy.Always.name)
            .assertIsDisplayed().performMouseInput { click() }
        onNodeWithTag("menu:" + CloseConfirmationPolicy.Always.name).assertDoesNotExist()
        runOnIdle { assertEquals(WindowClosePhase.Open, controller.phase.value) }
    }

    private fun loaded(settings: ShortcutsSettings = ShortcutsSettings()): ShortcutsRepository =
        ShortcutsRepository(MemoryShortcutsStore(settings), ShortcutPlatform.Mac).also { runBlocking { it.load() } }

    @Composable
    private fun WindowApp(
        repository: ShortcutsRepository,
        controller: DesktopWindowCloseController,
        policy: CloseConfirmationPolicy,
        focus: FocusRequester,
        generalRepository: GeneralSettingsRepository? = null,
        close: () -> Unit,
    ) {
        DesktopWindowShortcutBinding(repository, controller, policy, close)
        DesktopWindowContent(shortcutsRepository = repository) {
            HarnessApp(null, initialDestination = HarnessDestination.Settings, shortcutsRepository = repository,
                generalSettingsRepository = generalRepository,
                modifier = Modifier.requiredSize(1000.dp, 800.dp))
            Box(Modifier.size(1.dp).focusRequester(focus).focusable().semantics { testTag = "shortcuts-test-key-target" })
            DesktopWindowCloseDialog(controller, close)
        }
    }

    private fun SemanticsNodeInteraction.shortcut(key: Key, shift: Boolean = false) {
        performKeyInput {
            keyDown(Key.MetaLeft)
            if (shift) keyDown(Key.ShiftLeft)
            pressKey(key)
            if (shift) keyUp(Key.ShiftLeft)
            keyUp(Key.MetaLeft)
        }
    }
}
