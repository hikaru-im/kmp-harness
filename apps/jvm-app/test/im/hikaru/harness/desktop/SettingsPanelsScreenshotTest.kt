package im.hikaru.harness.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import im.hikaru.harness.client.app.HarnessApp
import im.hikaru.harness.client.app.HarnessDestination
import im.hikaru.harness.client.shell.SettingsPanelCatalog
import im.hikaru.harness.ui.foundation.HarnessThemeMode
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.*
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image

/**
 * M0 visual baseline for the Settings surface.
 *
 * Codex's R13 window was 2560x1320 physical pixels, i.e. a 1280x660 CSS viewport at 2x. Each
 * implemented panel is rendered at that same viewport and saved as a 2560x1320 PNG, together with
 * `baseline.json` (matrix ID, Codex slug, harness row, test tag, reference screenshot). Captures
 * are written only when `HARNESS_SETTINGS_PANELS_CAPTURE_DIR` is set; ordinary test runs stay
 * side-effect free. No pixel-parity claim is made: these are the same-viewport structural
 * evidence D-02 requires before an `aligned` conclusion.
 */
@OptIn(ExperimentalTestApi::class)
class SettingsPanelsScreenshotTest {
    @Test
    fun capturesEveryImplementedPanelAtTheCodexViewport() {
        val root = System.getenv("HARNESS_SETTINGS_PANELS_CAPTURE_DIR")
            ?.takeIf { it.isNotBlank() }?.let(::File) ?: return
        root.mkdirs()

        val entries = mutableListOf<CaptureEntry>()
        listOf(HarnessThemeMode.Light, HarnessThemeMode.Dark).forEach { theme ->
            runSkikoComposeUiTest(size = Size(2560f, 1320f)) {
                setContent {
                    CompositionLocalProvider(LocalDensity provides Density(2f)) {
                        Box(Modifier.fillMaxSize()) {
                            HarnessApp(null, themeMode = theme, initialDestination = HarnessDestination.Settings)
                        }
                    }
                }
                onNodeWithTag("settings-navigator-search").assertExists()
                // SET-04: the search box itself, in its filtered state, at the same viewport.
                onNodeWithTag("settings-navigator-search").performTextInput("Git")
                onNodeWithTag("settings-row-git").assertExists()
                onNodeWithTag("settings-row-appearance").assertDoesNotExist()
                waitForIdle()
                val searchFile = File(root, "search-filtered-${theme.name.lowercase()}-2560x1320.png")
                capture(searchFile)
                val searchDecoded = requireNotNull(ImageIO.read(searchFile)) { "Unreadable PNG: ${searchFile.absolutePath}" }
                assertEquals(2560, searchDecoded.width, searchFile.name)
                assertEquals(1320, searchDecoded.height, searchFile.name)
                entries += CaptureEntry(
                    matrixId = "SET-04",
                    harnessSlug = "search",
                    codexSlug = null,
                    testTag = "settings-navigator-search",
                    theme = theme.name.lowercase(),
                    viewport = "1280x660 dp @ 2x = 2560x1320 px",
                    codexReference = "13-runtime/screenshots/general.png",
                    file = searchFile.name,
                )
                onNodeWithTag("settings-navigator-search").performTextClearance()

                panelSteps().forEach { (row, panelTag) ->
                    if (row != "general") onNodeWithTag("settings-row-$row").performClick()
                    onNodeWithTag(panelTag).assertExists()
                    waitForIdle()
                    val file = File(root, "panel-$row-${theme.name.lowercase()}-2560x1320.png")
                    capture(file)
                    val decoded = requireNotNull(ImageIO.read(file)) { "Unreadable PNG: ${file.absolutePath}" }
                    assertEquals(2560, decoded.width, file.name)
                    assertEquals(1320, decoded.height, file.name)
                    assertTrue(file.length() > 0, "Empty PNG: ${file.absolutePath}")
                    entries += captureEntry(row, panelTag, theme, file.name)
                }
            }
        }
        File(root, "baseline.json").writeText(baselineJson(entries))
        assertTrue(File(root, "baseline.json").isFile)
        println("SETTINGS PANEL BASELINE: " + root.absolutePath)
    }

    private fun panelSteps(): List<Pair<String, String>> = listOf(
        "general" to "settings-panel-general-settings",
        "appearance" to "settings-panel-appearance",
        "models" to "settings-panel-models",
        "keyboard-shortcuts" to "settings-panel-keyboard-shortcuts",
        "connections" to "settings-panel-connections",
        "git" to "settings-panel-git-settings",
        "worktrees" to "settings-panel-worktrees",
        "plugins" to "settings-panel-plugins-settings",
    )

    private fun captureEntry(
        row: String,
        panelTag: String,
        theme: HarnessThemeMode,
        fileName: String,
    ): CaptureEntry {
        val mapping = requireNotNull(SettingsPanelCatalog.forHarnessSlug(row)) { "Unmapped settings row: $row" }
        return CaptureEntry(
            matrixId = mapping.matrixId,
            harnessSlug = row,
            codexSlug = mapping.codexSlug,
            testTag = panelTag,
            theme = theme.name.lowercase(),
            viewport = "1280x660 dp @ 2x = 2560x1320 px",
            codexReference = codexReference(mapping.codexSlug),
            file = fileName,
        )
    }

    private fun codexReference(codexSlug: String?): String? = when (codexSlug) {
        "general-settings" -> "13-runtime/screenshots/general.png"
        "appearance" -> "13-runtime/screenshots/appearance.png"
        "keyboard-shortcuts" -> "13-runtime/screenshots/keyboard-shortcuts.png"
        "plugins-settings" -> "13-runtime/screenshots/plugins.png"
        "connections" -> "13-runtime/screenshots/connections.png"
        "git-settings" -> "13-runtime/screenshots/git.png"
        "worktrees" -> "13-runtime/screenshots/worktrees.png"
        else -> null // models is the D-05 Harness extension; no Codex runtime panel exists.
    }

    private fun baselineJson(entries: List<CaptureEntry>): String = buildString {
        appendLine("{")
        appendLine("  \"codexBaseline\": \"26.1027.61615/24141\",")
        appendLine("  \"codexWindow\": \"2560x1320 (1280x660 CSS @ 2x)\",")
        appendLine("  \"claim\": \"same-viewport structural evidence; no pixel-parity comparison\",")
        appendLine("  \"panels\": [")
        entries.forEachIndexed { index, entry ->
            appendLine("    {")
            appendLine("      \"matrixId\": \"${entry.matrixId}\",")
            appendLine("      \"harnessSlug\": \"${entry.harnessSlug}\",")
            appendLine("      \"codexSlug\": ${entry.codexSlug?.let { "\"$it\"" } ?: "null"},")
            appendLine("      \"testTag\": \"${entry.testTag}\",")
            appendLine("      \"theme\": \"${entry.theme}\",")
            appendLine("      \"viewport\": \"${entry.viewport}\",")
            appendLine("      \"codexReference\": ${entry.codexReference?.let { "\"$it\"" } ?: "null"},")
            appendLine("      \"file\": \"${entry.file}\"")
            append(if (index == entries.lastIndex) "    }\n" else "    },\n")
        }
        appendLine("  ]")
        appendLine("}")
    }

    private data class CaptureEntry(
        val matrixId: String,
        val harnessSlug: String,
        val codexSlug: String?,
        val testTag: String,
        val theme: String,
        val viewport: String,
        val codexReference: String?,
        val file: String,
    )
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.capture(file: File) {
    val bitmap = onRoot().captureToImage().asSkiaBitmap()
    val data = requireNotNull(Image.makeFromBitmap(bitmap).encodeToData(EncodedImageFormat.PNG)) {
        "PNG encoding failed: ${file.absolutePath}"
    }
    file.writeBytes(data.bytes)
}
