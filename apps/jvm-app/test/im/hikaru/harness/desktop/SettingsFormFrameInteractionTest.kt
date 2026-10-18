package im.hikaru.harness.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.dp
import im.hikaru.harness.client.app.HarnessApp
import im.hikaru.harness.client.app.HarnessDestination
import im.hikaru.harness.client.page.SettingsAppearancePage
import im.hikaru.harness.client.settings.*
import im.hikaru.harness.ui.foundation.*
import java.io.File
import java.security.MessageDigest
import javax.imageio.ImageIO
import kotlinx.serialization.json.*
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import kotlin.test.*

/**
 * The Settings pages that render through [im.hikaru.harness.client.page.SettingsFormFrame] must share one
 * content column. Centering belongs to the Shell, including on General, so pages cannot introduce
 * independent offsets that make titles and cards drift when navigating between settings pages.
 */
@OptIn(ExperimentalTestApi::class)
class SettingsFormFrameInteractionTest {
    @Test
    fun everySettingsFormPageUsesTheFullSettingsContentColumn() {
        for (size in listOf(Size(1440f, 900f), Size(2560f, 1440f))) {
            for ((slug, tag) in Pages) {
                runSkikoComposeUiTest(size = size) {
                    setContent { HarnessApp(null, initialDestination = HarnessDestination.Settings) }
                    openPage(slug, tag)
                    val where = slug + " at " + size.width.toInt() + "x" + size.height.toInt()
                    val main = onNodeWithTag("main-content-column").getUnclippedBoundsInRoot()
                    val column = onNodeWithTag("$tag-column").getUnclippedBoundsInRoot()
                    assertClose(728f, column.right.value - column.left.value, where + " column width")
                    assertClose(main.left.value, column.left.value, where + " column left")
                    assertClose(main.right.value, column.right.value, where + " column right")
                    val title = onNodeWithTag(tag).getUnclippedBoundsInRoot()
                    assertClose(column.left.value, title.left.value, where + " title left")
                    assertClose(column.top.value, title.top.value, where + " title top")
                }
            }
        }
    }

    @Test
    fun aNarrowPageKeepsTheColumnInsideItsOwnWidth() {
        runSkikoComposeUiTest(size = Size(600f, 800f)) {
            setContent {
                HarnessTheme(HarnessThemeMode.Light) {
                    Box(Modifier.requiredSize(600.dp, 800.dp).semantics { testTag = "narrow-form-root" }) {
                        SettingsAppearancePage(AppearanceState(loading = false), {}, {})
                    }
                }
            }
            val root = onNodeWithTag("narrow-form-root").getUnclippedBoundsInRoot()
            val column = onNodeWithTag("settings-appearance-title-column").getUnclippedBoundsInRoot()
            assertClose(root.left.value + 16f, column.left.value, "narrow column left")
            assertClose(root.right.value - 16f, column.right.value, "narrow column right")
        }
    }

    private fun assertClose(expected: Float, actual: Float, where: String) =
        assertEquals(expected.toDouble(), actual.toDouble(), 0.01, where)

    /**
     * Writes PNG + tagged geometry for the form pages only when HARNESS_SETTINGS_FORM_CAPTURE_DIR is set,
     * so ordinary runs stay side-effect free. Appearance is rendered in both themes and at both window
     * sizes; the other pages get the 1440x900 light frame.
     */
    @Test
    fun capturesRenderedSettingsFormPagesForReview() {
        val directory = System.getenv("HARNESS_SETTINGS_FORM_CAPTURE_DIR")
            ?.takeIf { it.isNotBlank() }?.let(::File) ?: return
        val wide = listOf(Size(1440f, 900f), Size(2560f, 1440f))
        val narrow = listOf(Size(1440f, 900f))
        val plans = listOf(
            Triple("appearance", "settings-appearance-title", wide),
            Triple("models", "settings-models-title", narrow),
            Triple("keyboard-shortcuts", "settings-shortcuts-title", narrow),
            Triple("connections", "settings-connections-title", narrow),
            Triple("git", "git-page", narrow),
            Triple("worktrees", "worktrees-page", narrow),
        )
        for ((slug, tag, sizes) in plans) {
            for (size in sizes) for (theme in listOf(HarnessThemeMode.Light, HarnessThemeMode.Dark)) {
                runSkikoComposeUiTest(size = size) {
                    setContent {
                        HarnessApp(null, themeMode = theme,
                            initialDestination = HarnessDestination.Settings)
                    }
                    openPage(slug, tag)
                    onNodeWithTag("$tag-column").assertExists()
                    formShellCapture(directory,
                        slug + "-" + size.width.toInt() + "x" + size.height.toInt() + "-" + theme.name.lowercase(),
                        size.width.toInt(), size.height.toInt())
                }
            }
        }
    }

    private fun ComposeUiTest.openPage(slug: String, tag: String) {
        if (slug == "worktrees") onNodeWithTag("settings-row-git").performClick()
        onNodeWithTag("settings-row-" + slug).assertIsDisplayed().performClick()
        onNodeWithTag("$tag-column").assertExists()
    }

    private companion object {
        /** Settings navigator slug to the page title tag that the shared frame carries. */
        val Pages = listOf(
            "general" to "settings-general-title",
            "appearance" to "settings-appearance-title",
            "models" to "settings-models-title",
            "keyboard-shortcuts" to "settings-shortcuts-title",
            "connections" to "settings-connections-title",
            "git" to "git-page",
            "worktrees" to "worktrees-page",
        )
    }
}

/**
 * Captures the whole production Shell and writes the tagged geometry beside the PNG. The schema matches
 * the settings-navigator capture so the same measurement method reads both.
 */
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.formShellCapture(directory: File, name: String, width: Int, height: Int) {
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
        put("schema", "harness.settings-form-page.shell.v1"); put("image", png.name)
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
