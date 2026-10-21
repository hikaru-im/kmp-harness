package im.hikaru.harness.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import im.hikaru.harness.client.app.HarnessApp
import im.hikaru.harness.client.page.NewTabPage
import im.hikaru.harness.client.page.NewTabTool
import im.hikaru.harness.client.settings.*
import im.hikaru.harness.client.shell.*
import im.hikaru.harness.desktop.window.HarnessTabDragState
import im.hikaru.harness.desktop.window.HarnessWindowNavigation
import im.hikaru.harness.desktop.window.rememberHarnessTabMainRegion
import im.hikaru.harness.ui.foundation.harnessColors
import im.hikaru.harness.ui.foundation.HarnessTheme
import im.hikaru.harness.ui.foundation.HarnessThemeMode
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Samples the rendered production shell, including both panes, across live appearance changes. */
@OptIn(ExperimentalTestApi::class)
class ThemeLayersInteractionTest {
    @Test
    fun titleBarNavigationLeavesItsParentBackgroundVisible() {
        listOf(HarnessThemeMode.Light, HarnessThemeMode.Dark).forEach { mode ->
            runSkikoComposeUiTest(size = Size(360f, 60f)) {
                val parentColor = Color(0xFF475569)
                val tabs = HarnessTabStripState(emptyList(), "")
                val drag = HarnessTabDragState()
                setContent {
                    Box(Modifier.fillMaxSize().background(parentColor)) {
                        HarnessTheme(mode, drawBackground = false) {
                            HarnessWindowNavigation(tabs, drag, null, null, null, null, clientRegion = { this })
                        }
                    }
                }
                val pixels = onRoot().captureToImage().toPixelMap()
                // Inside the navigation theme's surface, outside button artwork and state fills.
                assertEquals(parentColor.toArgb(), pixels[4, 40].toArgb(), "$mode navigation must be transparent")
            }
        }
    }

    @Test
    fun toolCardsSupportKeyboardActivationAndKeepUnavailableToolsDisabled() = runSkikoComposeUiTest {
        var opened: NewTabTool? = null
        setContent {
            HarnessTheme(HarnessThemeMode.Light) { NewTabPage(onOpenTool = { opened = it }) }
        }
        val action = onNode(hasClickAction() and hasAnyAncestor(hasTestTag("new-tab-tool-changes")), useUnmergedTree = true)
        action.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        action.assertIsFocused().performKeyInput { pressKey(Key.Enter) }
        runOnIdle { assertEquals(NewTabTool.Changes, opened) }
        listOf("terminal", "files", "sidechat").forEach {
            onNodeWithTag("new-tab-tool-$it").assertIsNotEnabled()
        }
    }

    @Test
    fun splitWorkspacesShareOneCanvasAfterThemeAndCustomBackgroundChanges() =
        runSkikoComposeUiTest(size = Size(1600f, 1000f)) {
            val initial = AppearanceSettings(mode = AppearanceMode.Dark)
            val repository = AppearanceRepository(MemoryAppearanceStore(initial), initial)
            runBlocking { repository.load() }
            val tabs = HarnessTabStripState(emptyList(), "").apply { addNewTab() }
            val drag = HarnessTabDragState()
            setContent {
                CompositionLocalProvider(
                    LocalHarnessTabStrip provides tabs,
                    LocalHarnessMainRegion provides rememberHarnessTabMainRegion(tabs, drag),
                ) {
                    HarnessApp(null, appearanceRepository = repository)
                }
            }

            fun verifyAndCapture(name: String, settings: AppearanceSettings) {
                waitForIdle()
                val dark = settings.mode == AppearanceMode.Dark
                val colors = harnessColors(dark, settings.themeCustomization())
                val bitmap = onRoot().captureToImage()
                val pixels = bitmap.toPixelMap()
                fun sample(tag: String, expected: Color, xFraction: Float = 0.5f) {
                    val bounds = onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                    val x = (bounds.left + bounds.width * xFraction).toInt()
                    val y = (bounds.top + bounds.height * 0.75f).toInt()
                    assertEquals(expected.toArgb(), pixels[x, y].toArgb(), "$name: $tag at $x,$y")
                }
                sample("app-rail", colors.ShellBackground)
                // The default light navigator is a 65% white tint over the shell; custom themes use an
                // opaque derived navigation layer, for which compositing is a no-op.
                sample("workspace-navigator", colors.NavigationBackground.compositeOver(colors.ShellBackground))
                // The conversation pane draws the home page on the shared workspace canvas, while the
                // side panel's new-tab page paints its own page surface over it.
                sample("harness-split-left", colors.WorkspaceBackground, 0.04f)
                sample("harness-split-right", colors.SurfaceCanvas, 0.04f)
                val directory = File("build/reports/theme-layers").apply { mkdirs() }
                Image.makeFromBitmap(bitmap.asSkiaBitmap()).use { image ->
                    File(directory, "$name.png").writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
                }
            }

            verifyAndCapture("dark", initial)
            val light = initial.copy(mode = AppearanceMode.Light)
            runOnIdle { runBlocking { assertTrue(repository.save(light)) } }
            verifyAndCapture("light", light)
            val custom = initial.copy(dark = AppearanceVariant(background = "#171C25", accent = "#A8C7FA"))
            runOnIdle { runBlocking { assertTrue(repository.save(custom)) } }
            verifyAndCapture("custom-dark", custom)
        }
}
