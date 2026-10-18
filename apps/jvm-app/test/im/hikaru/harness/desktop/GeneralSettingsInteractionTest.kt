package im.hikaru.harness.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.dp
import im.hikaru.harness.client.app.HarnessApp
import im.hikaru.harness.client.app.HarnessDestination
import im.hikaru.harness.client.page.SettingsGeneralPage
import im.hikaru.harness.client.settings.*
import im.hikaru.harness.ui.foundation.*
import java.io.File
import java.security.MessageDigest
import javax.imageio.ImageIO
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class GeneralSettingsInteractionTest {
    @Test
    fun everyPolicySavesAndRouteReturnShowsTheSavedChoice() = runComposeUiTest {
        val repository = GeneralSettingsRepository(MemoryGeneralSettingsStore(), true)
        runBlocking { repository.load() }
        setContent {
            // Stay within the Compose test host's viewport so mouse coordinates can hit the controls.
            Box(Modifier.fillMaxSize()) {
                HarnessApp(null, initialDestination = HarnessDestination.Settings, generalSettingsRepository = repository)
            }
        }
        CloseConfirmationPolicy.entries.forEach { policy ->
            onNodeWithTag("settings-general-control-confirm-close").performScrollTo().clickWithMouse()
            onNodeWithTag("menu:" + policy.name).clickWithMouse()
            onNodeWithTag("menu:" + policy.name).assertDoesNotExist()
            runOnIdle {
                assertEquals(policy, repository.state.value.settings.closeConfirmation)
                assertEquals(policy.ordinal + 1L, repository.state.value.saveRevision)
            }
            onNodeWithTag("settings-row-appearance").clickWithMouse()
            onNodeWithTag("settings-row-general").clickWithMouse()
            onNodeWithTag("settings-general-control-confirm-close").performScrollTo()
                .assertTextContains(policy.label, substring = true)
        }
        onNodeWithTag("settings-general-control-plugins").performScrollTo().clickWithMouse()
        onNodeWithTag("plugins-navigator").assertExists()
    }

    @Test
    fun compactPageDisablesUnavailableControlsAndExposesPluginAction() = runComposeUiTest {
        var opens = 0
        var width by mutableStateOf(420.dp)
        val state = GeneralSettingsState(closePolicyAvailable = true, durable = true, loading = false, loaded = true)
        setContent {
            HarnessTheme(HarnessThemeMode.Dark) {
                Box(Modifier.requiredSize(width, 700.dp).semantics { testTag = "general-test-root" }) {
                    SettingsGeneralPage(state, onOpenPlugins = { opens++ })
                }
            }
        }
        // Include the compact threshold and the intermediate width that cannot fit 728dp plus margins.
        listOf(420.dp, 600.dp, 720.dp).forEach { viewportWidth ->
            runOnIdle { width = viewportWidth }
            val rootBounds = onNodeWithTag("general-test-root").getUnclippedBoundsInRoot()
            listOf("settings-general-title", "settings-permissions-card", "settings-preferences-card").forEach { tag ->
                val bounds = onNodeWithTag(tag).getUnclippedBoundsInRoot()
                assertTrue(bounds.left >= rootBounds.left, "$tag left edge at $viewportWidth: $bounds vs $rootBounds")
                assertTrue(bounds.right <= rootBounds.right, "$tag right edge at $viewportWidth: $bounds vs $rootBounds")
            }
        }
        runOnIdle { width = 420.dp }
        onNodeWithTag("settings-general-control-default-permission").assertIsNotEnabled().assertIsOff()
        onNodeWithTag("settings-general-unavailable-default-permission").assertIsDisplayed().assertTextEquals("暂不可用")
        onNodeWithText("尚未接入 Host 权限配置").assertDoesNotExist()
        onNodeWithTag("settings-general-control-task-folder").performScrollTo().assertIsNotEnabled()
        onNodeWithTag("settings-general-control-terminal-location-bottom").performScrollTo().assertIsNotEnabled()
        onNodeWithTag("settings-general-control-plugins").performScrollTo().performClick()
        runOnIdle { assertEquals(1, opens) }
    }

    @Test
    fun failedSaveKeepsMenuValueAndRetryCommitsTheCandidate() = runComposeUiTest {
        var fail = true
        val repository = GeneralSettingsRepository(object : GeneralSettingsStore {
            override val durable = true
            override suspend fun read(): GeneralSettings? = null
            override suspend fun write(settings: GeneralSettings) { if (fail) error("storage unavailable") }
        }, true)
        runBlocking { repository.load() }
        setContent { OwnedGeneral(repository) }
        onNodeWithTag("settings-general-control-confirm-close").performScrollTo().performClick()
        onNodeWithText(CloseConfirmationPolicy.Always.label).performClick()
        onNodeWithTag("general-save-error").performScrollTo().assertIsDisplayed()
        onNodeWithTag("settings-general-control-confirm-close").performScrollTo()
            .assertTextContains(CloseConfirmationPolicy.KeyboardOnly.label, substring = true)
        runOnIdle { assertEquals(0L, repository.state.value.saveRevision); fail = false }
        onNodeWithTag("general-retry").performScrollTo().performClick()
        onNodeWithTag("general-save-error").assertDoesNotExist()
        runOnIdle { assertEquals(CloseConfirmationPolicy.Always, repository.state.value.settings.closeConfirmation) }
    }

    @Test
    fun loadingErrorsAndUnsupportedPlatformNeverEnableThePolicyMenu() = runComposeUiTest {
        var state by mutableStateOf(GeneralSettingsState(closePolicyAvailable = true, durable = true))
        setContent {
            HarnessTheme { SettingsGeneralPage(state, onRetry = {
                state = state.copy(loaded = true, loading = false, loadError = null)
            }) }
        }
        onNodeWithTag("general-loading").assertIsDisplayed()
        onNodeWithTag("settings-general-control-confirm-close").performScrollTo().assertIsNotEnabled()
        runOnIdle { state = state.copy(loading = false, loadError = "无法读取常规设置") }
        onNodeWithTag("general-load-error").performScrollTo().assertIsDisplayed()
        onNodeWithTag("general-retry").performClick()
        onNodeWithTag("settings-general-control-confirm-close").performScrollTo().assertIsEnabled()
        runOnIdle { state = state.copy(closePolicyAvailable = false) }
        onNodeWithTag("settings-general-control-confirm-close").assertIsNotEnabled()
        onNodeWithTag("settings-general-unavailable-confirm-close").assertExists()
    }

    @Composable
    private fun OwnedGeneral(repository: GeneralSettingsRepository) {
        val state by repository.state.collectAsState()
        val scope = rememberCoroutineScope()
        HarnessTheme {
            SettingsGeneralPage(state,
                { scope.launch { repository.save(state.settings.copy(closeConfirmation = it)) } },
                { scope.launch { repository.retry() } })
        }
    }

    private fun SemanticsNodeInteraction.clickWithMouse() {
        assertIsDisplayed().assertIsEnabled().performMouseInput { click() }
    }

    /**
     * Renders the production Shell on the Settings route so the Settings navigator can be measured
     * against the 2026-10-06 Codex capture. Writes PNG + geometry only when
     * HARNESS_SETTINGS_NAVIGATOR_CAPTURE_DIR is set, so ordinary runs stay side-effect free.
     */
    @Test
    fun capturesRenderedSettingsNavigatorForParityReview() {
        val directory = System.getenv("HARNESS_SETTINGS_NAVIGATOR_CAPTURE_DIR")
            ?.takeIf { it.isNotBlank() }?.let(::File) ?: return
        for (size in listOf(Size(1440f, 900f), Size(2560f, 1440f)))
            for (theme in listOf(HarnessThemeMode.Light, HarnessThemeMode.Dark))
                runSkikoComposeUiTest(size = size) {
                    setContent { HarnessApp(null, themeMode = theme,
                        initialDestination = HarnessDestination.Settings) }
                    onNodeWithTag("settings-navigator").assertExists()
                    onNodeWithTag("settings-navigator-search").assertExists()
                    onNodeWithTag("settings-row-general").assertIsSelected()
                    settingsShellCapture(directory,
                        "navigator-" + size.width.toInt() + "x" + size.height.toInt() + "-" + theme.name.lowercase(),
                        size.width.toInt(), size.height.toInt())
                }
    }

    /**
     * Renders the production Shell on the Settings route with a durable repository, so the page shows
     * only the content the 2026-10-06 capture shows (no "session-only" status line). Writes PNG +
     * geometry only when HARNESS_SETTINGS_PAGE_CAPTURE_DIR is set.
     */
    @Test
    fun capturesRenderedSettingsPageForParityReview() {
        val directory = System.getenv("HARNESS_SETTINGS_PAGE_CAPTURE_DIR")
            ?.takeIf { it.isNotBlank() }?.let(::File) ?: return
        for (size in listOf(Size(1440f, 900f), Size(2560f, 1440f)))
            for (theme in listOf(HarnessThemeMode.Light, HarnessThemeMode.Dark))
                runSkikoComposeUiTest(size = size) {
                    // A durable store keeps the honest "session-only" notice off the page, which is the
                    // state the 2026-10-06 capture shows.
                    // A path that does not exist yet: an existing empty file would read as a JSON error.
                    val file = java.nio.file.Files.createTempDirectory("settings-page-capture-")
                        .resolve("general.json")
                    val repository = GeneralSettingsRepository(DesktopGeneralSettingsStore(file), true)
                    runBlocking { repository.load() }
                    setContent { HarnessApp(null, themeMode = theme,
                        initialDestination = HarnessDestination.Settings, generalSettingsRepository = repository) }
                    onNodeWithTag("settings-general-title").assertExists()
                    onNodeWithTag("settings-permissions-card").assertExists()
                    onNodeWithTag("general-session-only").assertDoesNotExist()
                    settingsShellCapture(directory,
                        "page-" + size.width.toInt() + "x" + size.height.toInt() + "-" + theme.name.lowercase(),
                        size.width.toInt(), size.height.toInt())
                }
    }
}

/**
 * Captures the whole production Shell and writes the tagged geometry beside the PNG. The schema
 * matches the plugins-navigator capture so the same measurement method reads both.
 */
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.settingsShellCapture(directory: File, name: String, width: Int, height: Int) {
    directory.mkdirs()
    val bitmap = onRoot().captureToImage()
    assertEquals(width, bitmap.width); assertEquals(height, bitmap.height)
    val bytes = Image.makeFromBitmap(bitmap.asSkiaBitmap()).use { image ->
        image.encodeToData(EncodedImageFormat.PNG)?.use { it.bytes } ?: error("PNG encoding failed")
    }
    val png = File(directory, name + ".png").also { it.writeBytes(bytes) }
    val decoded = assertNotNull(ImageIO.read(png))
    assertEquals(width, decoded.width); assertEquals(height, decoded.height)
    val nodes = onAllNodes(SemanticsMatcher("tagged nodes") { it.config.contains(SemanticsProperties.TestTag) },
        useUnmergedTree = true).fetchSemanticsNodes()
    val geometry = buildJsonObject {
        put("schema", "harness.settings-navigator.shell.v1"); put("image", png.name)
        put("width", width); put("height", height); put("density", 1f); put("units", "pixels")
        put("note", "Whole production Shell capture. boundsInRoot is window-local and excludes native chrome.")
        put("sha256", MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
        putJsonArray("tags") { nodes.forEach { node -> add(buildJsonObject {
            put("tag", node.config[SemanticsProperties.TestTag]); put("id", node.id)
            put("rootId", generateSequence(node) { it.parent }.last().id)
            putJsonObject("layoutBounds") {
                put("x", node.positionInRoot.x); put("y", node.positionInRoot.y)
                put("width", node.size.width); put("height", node.size.height)
            }
            putJsonObject("boundsInRoot") {
                put("left", node.boundsInRoot.left); put("top", node.boundsInRoot.top)
                put("right", node.boundsInRoot.right); put("bottom", node.boundsInRoot.bottom)
            }
            put("disabled", node.config.contains(SemanticsProperties.Disabled))
            put("selected", node.config.contains(SemanticsProperties.Selected))
        }) } }
    }
    File(directory, name + ".json").writeText(Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), geometry))
}
