package im.hikaru.harness.desktop

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import im.hikaru.harness.client.app.HarnessApp
import im.hikaru.harness.client.app.HarnessDestination
import im.hikaru.harness.client.page.SettingsAppearancePage
import im.hikaru.harness.client.settings.*
import im.hikaru.harness.ui.foundation.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class AppearanceInteractionTest {
    @Test
    fun compactPageCanScrollToAdvancedAndReturnToImport() = runComposeUiTest {
        val initial = AppearanceSettings(mode = AppearanceMode.Light)
        val repository = AppearanceRepository(MemoryAppearanceStore(initial), initial)
        runBlocking { repository.load() }
        setContent { Box(Modifier.requiredWidth(600.dp)) { OwnedAppearance(repository) } }
        onNodeWithTag("appearance-advanced-toggle").performScrollTo().performClick()
        onNodeWithTag("appearance-code-size").performScrollTo().assertIsDisplayed()
        onNodeWithTag("appearance-pointer").performScrollTo().assertIsDisplayed()
        onNodeWithTag("appearance-import").performScrollTo().performClick()
        onNodeWithTag("appearance-import-input").assertIsDisplayed()
    }

    @Test
    fun repeatedArrowKeysAdvanceModesAndLoadErrorsBlockEdits() = runComposeUiTest {
        val initial = AppearanceSettings(mode = AppearanceMode.System)
        var state by mutableStateOf(AppearanceState(initial, loading = false, loadError = "bad settings"))
        var changes = 0
        setContent {
            HarnessTheme(HarnessThemeMode.Light) {
                SettingsAppearancePage(state, { changes++; state = state.copy(settings = it) }, { state = state.copy(loadError = null) })
            }
        }
        onNodeWithTag("appearance-mode-system").assertIsNotEnabled()
        onNodeWithTag("appearance-import").assertIsNotEnabled()
        runOnIdle { assertEquals(0, changes) }
        onNodeWithTag("appearance-retry").performClick()
        onNodeWithTag("appearance-mode-system").performClick()
        onNodeWithTag("appearance-mode-system").performKeyInput { pressKey(Key.DirectionRight) }
        onNodeWithTag("appearance-mode-light").assertIsSelected()
        onNodeWithTag("appearance-mode-system").performKeyInput { pressKey(Key.DirectionRight) }
        onNodeWithTag("appearance-mode-dark").assertIsSelected()
    }

    @Test
    fun settingsRouteChangesTheSavedModeAndActualShellPalette() = runComposeUiTest {
        val initial = AppearanceSettings(mode = AppearanceMode.Light)
        val repository = AppearanceRepository(MemoryAppearanceStore(initial), initial)
        runBlocking { repository.load() }
        setContent { HarnessApp(null, initialDestination = HarnessDestination.Settings, appearanceRepository = repository) }
        onNodeWithTag("settings-row-appearance").performClick()
        onNodeWithTag("settings-appearance-title").assertIsDisplayed()
        val light = onRoot().captureToImage().toPixelMap().let { it[it.width - 4, it.height / 2] }
        onNodeWithTag("appearance-mode-dark").performClick()
        onNodeWithTag("appearance-mode-dark").assertIsSelected()
        runOnIdle { assertEquals(AppearanceMode.Dark, repository.state.value.settings.mode) }
        val dark = onRoot().captureToImage().toPixelMap().let { it[it.width - 4, it.height / 2] }
        assertNotEquals(light, dark)
        onNodeWithTag("settings-row-general").performClick()
        onNodeWithTag("settings-row-appearance").performClick()
        onNodeWithTag("appearance-mode-dark").assertIsSelected()
    }

    @Test
    fun numberValidationAndSeparateModesDoNotPublishInvalidSettings() = runComposeUiTest {
        val initial = AppearanceSettings(mode = AppearanceMode.Light)
        val repository = AppearanceRepository(MemoryAppearanceStore(initial), initial)
        runBlocking { repository.load() }
        setContent { OwnedAppearance(repository, advanced = true) }
        onNodeWithTag("appearance-ui-size").performScrollTo().performTextReplacement("999")
        onNodeWithTag("appearance-ui-size").performKeyInput { pressKey(Key.Enter) }
        onNodeWithTag("appearance-ui-size").assert(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.Error))
        runOnIdle { assertEquals(initial, repository.state.value.settings) }
        onNodeWithTag("appearance-ui-size").performTextReplacement("16")
        onNodeWithTag("appearance-ui-size").performKeyInput { pressKey(Key.Enter) }
        runOnIdle { assertEquals(16, repository.state.value.settings.uiFontSize) }
        onNodeWithTag("appearance-separate-modes").performScrollTo().performClick()
        onNodeWithTag("appearance-contrast-light").assertExists()
        onNodeWithTag("appearance-contrast-dark").assertExists()
        runOnIdle { assertEquals(initial.copy(uiFontSize = 16), repository.state.value.settings) }
        onNodeWithTag("appearance-advanced-toggle").performScrollTo().performClick()
        onNodeWithTag("appearance-code-size").assertDoesNotExist()
        onNodeWithTag("appearance-advanced-toggle").performClick()
        onNodeWithTag("appearance-ui-size").performScrollTo().assertTextEquals("16")
    }

    @Test
    fun invalidColorStaysLocalAndValidColorCommitsOnBlur() = runComposeUiTest {
        val initial = AppearanceSettings(mode = AppearanceMode.Light)
        val repository = AppearanceRepository(MemoryAppearanceStore(initial), initial)
        runBlocking { repository.load() }
        setContent { OwnedAppearance(repository) }
        onNodeWithTag("appearance-color-accent-light").performTextReplacement("red")
        onNodeWithTag("appearance-color-background-light").performClick()
        runOnIdle { assertNull(repository.state.value.settings.light.accent) }
        onNodeWithTag("appearance-color-accent-light").performTextReplacement("#123456")
        onNodeWithTag("appearance-color-background-light").performClick()
        runOnIdle { assertEquals("#123456", repository.state.value.settings.light.accent) }
    }

    @Test
    fun importStaysOpenOnFormatAndWriteFailureThenClosesAfterRetry() = runComposeUiTest {
        val initial = AppearanceSettings(mode = AppearanceMode.Light)
        var fail = true
        val repository = AppearanceRepository(object : AppearanceStore {
            override val durable = true
            override suspend fun read() = initial
            override suspend fun write(settings: AppearanceSettings) { if (fail) error("disk unavailable") }
        }, initial)
        runBlocking { repository.load() }
        setContent { OwnedAppearance(repository) }
        onNodeWithTag("appearance-import").performClick()
        onNodeWithTag("appearance-import-input").performTextReplacement("invalid")
        onNodeWithTag("appearance-import-confirm").performClick()
        onNodeWithTag("appearance-import-input").assertIsDisplayed()
        runOnIdle { assertEquals(0, repository.state.value.saveRevision) }
        val variant = AppearanceVariant(accent = "#456789", codeFontStyle = AppearanceFontStyle.Italic)
        onNodeWithTag("appearance-import-input").performTextReplacement(AppearanceThemeCodec.encode(AppearanceMode.Light, variant))
        onNodeWithTag("appearance-import-confirm").performClick()
        onNodeWithTag("appearance-import-input").assertIsDisplayed()
        runOnIdle { assertEquals(initial, repository.state.value.settings); fail = false }
        onNodeWithTag("appearance-import-retry").performClick()
        onNodeWithTag("appearance-import-input").assertDoesNotExist()
        onNodeWithTag("appearance-feedback").assertTextEquals("主题已导入并保存")
        runOnIdle { assertEquals(variant, repository.state.value.settings.light) }
    }

    @Test
    fun resetPreservesVisualChoicesAndUnavailableSmoothingStaysDisabled() = runComposeUiTest {
        val initial = AppearanceSettings(mode = AppearanceMode.Light, uiFontSize = 16,
            light = AppearanceVariant(accent = "#123456", uiFont = AppearanceFont.Serif, contrast = 70))
        val repository = AppearanceRepository(MemoryAppearanceStore(initial), initial)
        runBlocking { repository.load() }
        setContent { OwnedAppearance(repository, advanced = true) }
        onNodeWithTag("appearance-reset").performScrollTo().performClick()
        runOnIdle { assertEquals(initial.resetAdvanced(), repository.state.value.settings) }
        onNodeWithTag("appearance-smoothing").performScrollTo().assertIsNotEnabled()
    }

    @Composable
    private fun OwnedAppearance(repository: AppearanceRepository, advanced: Boolean = false) {
        val state by repository.state.collectAsState()
        val scope = rememberCoroutineScope()
        HarnessTheme(state.settings.mode.themeMode(), state.settings.themeCustomization()) {
            SettingsAppearancePage(state, { scope.launch { repository.save(it) } }, { scope.launch { repository.retry() } }, advanced)
        }
    }
}
