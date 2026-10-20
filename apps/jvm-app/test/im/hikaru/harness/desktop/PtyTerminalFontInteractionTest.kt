@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package im.hikaru.harness.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import im.hikaru.harness.client.connection.*
import im.hikaru.harness.client.panels.*
import im.hikaru.harness.client.settings.*
import im.hikaru.harness.desktop.terminal.RealPtyPageFixture
import im.hikaru.harness.ui.foundation.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.test.*

/** Real PTY tests for the parent verifier; these captures do not certify native IME or pixel parity. */
class PtyTerminalFontInteractionTest {
    @Test
    fun emptyViewportUsesSavedFontBeforeFirstRealCreate() = runBlocking<Unit> {
        RealPtyPageFixture.use { f ->
            val repository = loadedAppearance(AppearanceSettings(mode = AppearanceMode.Light))
            val script = f.pidPrefix() +
                "stty size > initial-kernel-size; printf 'FIRST_READY\\r\\n'; dd bs=1 of=extra.bin 2>/dev/null"
            val createRequests = CopyOnWriteArrayList<TerminalCreateRequest>()
            val observedConnection = object : Connection by f.connection {
                override val session = object : SessionApi by f.connection.session {
                    override suspend fun createTerminal(request: TerminalCreateRequest): TerminalCreateResult {
                        createRequests += request
                        val result = f.connection.session.createTerminal(request)
                        if (result is TerminalCreateResult.Created) {
                            // Read the real initial kernel size before exposing the new grid and its refit.
                            f.until { Files.exists(f.cwd.resolve("initial-kernel-size")) &&
                                Files.readString(f.cwd.resolve("initial-kernel-size")).trim().isNotEmpty() }
                        }
                        return result
                    }
                }
            }
            runSkikoComposeUiTest(size = Size(900f, 600f)) {
                var controller: PtyTerminalController? = null
                var reference: FontReference? = null
                try {
                    setContent {
                        val settings by repository.state.collectAsState()
                        val scope = rememberCoroutineScope()
                        val page = remember { f.controller(scope, script).also {
                            it.bind(f.owner.copy(connection = observedConnection)); it.setVisible(true)
                        } }.also { controller = it }
                        val state by page.state.collectAsState()
                        CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                            IndependentFontReference(settings.settings) { reference = it }
                            HarnessTheme(settings.settings.mode.themeMode(), settings.settings.themeCustomization()) {
                                Box(Modifier.requiredSize(640.dp, 420.dp)) { PtyTerminalPanel(page, state) }
                            }
                        }
                    }
                    awaitTag("pty-terminal-empty-viewport")
                    waitUntil(timeoutMillis = 30_000) {
                        controller?.state?.value?.let { it.canCreate && it.inventory is PtyLoad.Empty } == true && reference != null
                    }
                    val page = assertNotNull(controller)
                    val bodyBefore = bounds("pty-terminal-empty-viewport")
                    val expectedBefore = assertNotNull(reference).viewport(bodyBefore)
                    waitUntil(timeoutMillis = 30_000) { page.state.value.viewport == expectedBefore }
                    assertTrue(expectedBefore.columns > 5 && expectedBefore.rows > 2)
                    assertEquals(0, f.creates.get()); assertEquals(0, f.closes.get()); assertTrue(f.inputs.isEmpty())
                    runOnUiThread { assertTrue(runBlocking { repository.save(repository.state.value.settings.copy(codeFontSize = 20)) }) }
                    waitUntil(timeoutMillis = 30_000) { reference?.size == 20 && page.state.value.viewport == reference?.viewport(bodyBefore) }
                    assertEquals(bodyBefore, bounds("pty-terminal-empty-viewport"))
                    val expectedAfter = assertNotNull(reference).viewport(bodyBefore)
                    assertTrue(expectedAfter.columns < expectedBefore.columns && expectedAfter.rows < expectedBefore.rows)
                    assertEquals(expectedAfter, page.state.value.viewport)
                    assertEquals(0, f.creates.get()); assertEquals(0, f.closes.get()); assertEquals(0, f.nativeWrites.get())
                    assertTrue(f.inputs.isEmpty()); assertTrue(f.keys.isEmpty())
                    assertTrue(createRequests.isEmpty())
                    click("pty-terminal-create")
                    awaitTag("pty-terminal-grid")
                    waitUntil(timeoutMillis = 30_000) {
                        Files.exists(f.cwd.resolve("initial-kernel-size")) && f.keys.size == 1 &&
                            Files.readString(f.cwd.resolve("initial-kernel-size")).trim() == expectedAfter.rows.toString() + " " + expectedAfter.columns
                    }
                    assertEquals("${expectedAfter.rows} ${expectedAfter.columns}", Files.readString(f.cwd.resolve("initial-kernel-size")).trim())
                    assertEquals(expectedAfter, createRequests.single().size)
                    val snapshot = runBlocking { f.snapshot(f.keys.single()) }
                    assertEquals(f.keys.single(), snapshot.metadata.key)
                    assertEquals(f.session, snapshot.metadata.key.owner.session)
                    assertEquals(TerminalLifecycle.Running, snapshot.metadata.lifecycle)
                    assertEquals(1, f.creates.get()); assertEquals(0, f.closes.get()); assertTrue(f.inputs.isEmpty())
                    f.recordOwned(); assertTrue(f.ownedProcesses().single().isAlive)
                } finally { runOnUiThread { controller?.close() } }
            }
        }
    }

    @Test
    fun codeFontFamilyAndStyleRenderWithoutAdditionalUiScale() = runBlocking<Unit> {
        RealPtyPageFixture.use { f ->
            val script = f.pidPrefix() + "stty raw -echo; printf '\\033[2J\\033[H\\033[?25li\\r\\n\\033[1mi\\033[0m\\r\\n\\033[3mi\\033[0m\\r\\nFONT_SAMPLE_READY\\r\\n'; dd bs=1 of=extra.bin 2>/dev/null"
            val controller = f.controller(this, script)
            try {
                f.awaitReady(controller); controller.viewport(TerminalSize(6, 80)); assertTrue(controller.createTerminal())
                f.awaitScreen(controller, "FONT_SAMPLE_READY")
                val snapshot = f.snapshot(f.keys.single())
                assertEquals(TerminalLifecycle.Running, snapshot.metadata.lifecycle)
                assertEquals(listOf("i", "i", "i"), snapshot.screen.take(3).map { it.text.trimEnd() })
                assertTrue(snapshot.screen[0].runs.first().style.attributes.isEmpty())
                assertTrue(TerminalTextAttribute.Bold in snapshot.screen[1].runs.first().style.attributes)
                assertTrue(TerminalTextAttribute.Italic in snapshot.screen[2].runs.first().style.attributes)
                val repository = loadedAppearance(AppearanceSettings(mode = AppearanceMode.Light, codeFontSize = 20))
                runSkikoComposeUiTest(size = Size(1300f, 360f)) {
                    var reference: FontReference? = null
                    var viewport: TerminalSize? = null
                    setContent {
                        val appearance by repository.state.collectAsState()
                        CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                            IndependentFontReference(appearance.settings) { reference = it }
                            HarnessTheme(appearance.settings.mode.themeMode(), appearance.settings.themeCustomization()) {
                                Row {
                                    PtyTerminalGrid(snapshot, remember { PtyGridReading() }, false, false,
                                        { viewport = it }, {}, Modifier.requiredSize(640.dp, 320.dp))
                                    ReferenceGlyphs(appearance.settings, Modifier.requiredSize(630.dp, 320.dp))
                                }
                            }
                        }
                    }
                    awaitTag("pty-terminal-grid"); awaitTag("font-reference-glyphs")
                    val fixedBounds = bounds("pty-terminal-grid")
                    fun verify(): List<IntArray> {
                        waitForIdle()
                        val oracle = assertNotNull(reference)
                        assertEquals(oracle.viewport(fixedBounds), viewport)
                        assertEquals(fixedBounds, bounds("pty-terminal-grid"))
                        val actual = onNodeWithTag("pty-terminal-grid", true).captureToImage()
                        val expected = onNodeWithTag("font-reference-glyphs", true).captureToImage()
                        assertEquals(expected.width, actual.width); assertEquals(expected.height, actual.height)
                        assertTrue(oracle.rowHeight * 3 <= actual.height)
                        return (0..2).map { row ->
                            val actualPixels = glyphPixels(actual, oracle, row)
                            val referencePixels = glyphPixels(expected, oracle, row)
                            assertContentEquals(referencePixels, actualPixels, "Saved ${repository.state.value.settings} / ANSI row $row")
                            val background = palette(repository.state.value.settings.mode).Surface.toArgb()
                            assertTrue(actualPixels.any { it != background }, "The real glyph contains visible ink")
                            actualPixels
                        }
                    }
                    val regular = verify()
                    assertFalse(regular[0].contentEquals(regular[1]), "Real ANSI Bold differs from regular")
                    assertFalse(regular[0].contentEquals(regular[2]), "Real ANSI Italic differs from regular")
                    val saved = repository.state.value.settings.copy(
                        light = AppearanceVariant(codeFont = AppearanceFont.Serif, codeFontStyle = AppearanceFontStyle.Bold),
                        dark = AppearanceVariant(codeFont = AppearanceFont.Sans, codeFontStyle = AppearanceFontStyle.Italic))
                    for (mode in listOf(AppearanceMode.Light, AppearanceMode.Dark)) {
                        save(repository, saved.copy(mode = mode))
                        val styled = verify()
                        assertFalse(regular[0].contentEquals(styled[0]), "Saved family/style changes actual ordinary glyph pixels")
                        if (mode == AppearanceMode.Light) assertContentEquals(styled[0], styled[1], "Ordinary glyph inherits saved Bold")
                        else assertContentEquals(styled[0], styled[2], "Ordinary glyph inherits saved Italic")
                        for (size in listOf(8, 24)) {
                            save(repository, saved.copy(mode = mode, codeFontSize = size)); verify()
                        }
                        save(repository, saved.copy(mode = mode, uiFontSize = 11))
                        val smallUi = verify(); val sizeAt11 = viewport; val metricsAt11 = assertNotNull(reference)
                        save(repository, saved.copy(mode = mode, uiFontSize = 16))
                        val largeUi = verify()
                        assertEquals(sizeAt11, viewport); assertEquals(metricsAt11, reference)
                        smallUi.zip(largeUi).forEach { (small, large) -> assertContentEquals(small, large, "UI font size cannot rescale code pixels") }
                    }
                    assertEquals(snapshot.metadata.key, f.keys.single())
                    assertEquals(TerminalLifecycle.Running, runBlocking { f.snapshot(f.keys.single()) }.metadata.lifecycle)
                    assertEquals(1, f.creates.get()); assertEquals(0, f.closes.get()); assertTrue(f.inputs.isEmpty())
                    f.recordOwned(); assertTrue(f.ownedProcesses().single().isAlive)
                }
            } finally { controller.close() }
        }
    }

    private fun loadedAppearance(settings: AppearanceSettings): AppearanceRepository =
        AppearanceRepository(MemoryAppearanceStore(settings), settings).also { runBlocking { it.load() } }

    private fun ComposeUiTest.save(repository: AppearanceRepository, settings: AppearanceSettings) {
        runOnUiThread { assertTrue(runBlocking { repository.save(settings) }) }; waitForIdle()
    }

    /** Oracle uses raw saved sizes and a base density, independently of theme compensation and production helpers. */
    private data class FontReference(val size: Int, val cellWidth: Float, val rowHeight: Float) {
        fun viewport(bounds: Rect) = TerminalSize(floor(bounds.height / rowHeight).toInt().coerceIn(2, 200),
            floor(bounds.width / cellWidth).toInt().coerceIn(5, 500))
    }

    private fun referenceStyle(settings: AppearanceSettings): TextStyle {
        val variant = settings.variant(settings.mode)
        return TextStyle(fontFamily = when (variant.codeFont) {
            AppearanceFont.System, AppearanceFont.Sans -> FontFamily.SansSerif
            AppearanceFont.Serif -> FontFamily.Serif
            AppearanceFont.Monospace -> FontFamily.Monospace
        }, fontSize = settings.codeFontSize.sp, lineHeight = (settings.codeFontSize * 1.5f).sp,
            fontWeight = when (variant.codeFontStyle) {
                AppearanceFontStyle.Bold -> FontWeight.Bold
                AppearanceFontStyle.Medium -> FontWeight.Medium
                else -> FontWeight.Normal
            }, fontStyle = if (variant.codeFontStyle == AppearanceFontStyle.Italic) FontStyle.Italic else FontStyle.Normal)
    }

    @Composable
    private fun IndependentFontReference(settings: AppearanceSettings, publish: (FontReference) -> Unit) {
        val measurer = rememberTextMeasurer()
        val measured = measurer.measure(AnnotatedString("MMMM"), referenceStyle(settings), softWrap = false)
        val reference = FontReference(settings.codeFontSize, measured.size.width / 4f,
            maxOf(measured.size.height.toFloat(), settings.codeFontSize * 1.5f))
        SideEffect { publish(reference) }
    }

    @Composable
    private fun ReferenceGlyphs(settings: AppearanceSettings, modifier: Modifier) {
        CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
            val measurer = rememberTextMeasurer()
            val style = referenceStyle(settings).copy(color = palette(settings.mode).Text)
            val measured = measurer.measure(AnnotatedString("MMMM"), style, softWrap = false)
            val cellWidth = measured.size.width / 4f
            val rowHeight = maxOf(measured.size.height.toFloat(), settings.codeFontSize * 1.5f)
            val background = palette(settings.mode).Surface
            val emphasized = listOf(style, style.copy(fontWeight = FontWeight.Bold), style.copy(fontStyle = FontStyle.Italic))
            val glyphSizes = emphasized.map { measurer.measure(AnnotatedString("i"), it, softWrap = false).size }
            SideEffect { glyphSizes.forEach {
                assertTrue(it.width <= ceil(cellWidth).toInt(), "Independent glyph fits its terminal column")
                assertTrue(it.height <= rowHeight, "Independent glyph fits the declared row height")
            } }
            Canvas(modifier.semantics { testTag = "font-reference-glyphs" }) {
                drawRect(background)
                for (row in 0..2) {
                    drawText(measurer, "i", topLeft = Offset(0f, row * rowHeight), style = emphasized[row], softWrap = false)
                }
            }
        }
    }

    private fun palette(mode: AppearanceMode) = if (mode == AppearanceMode.Dark) HarnessDarkColors else HarnessLightColors

    private fun glyphPixels(bitmap: ImageBitmap, reference: FontReference, row: Int): IntArray {
        val pixels = bitmap.toPixelMap()
        val top = floor(row * reference.rowHeight).toInt()
        val bottom = ceil((row + 1) * reference.rowHeight).toInt().coerceAtMost(bitmap.height)
        // Include the next blank cell so clipping away any reference ink also fails the comparison.
        val width = ceil(reference.cellWidth * 2).toInt().coerceAtMost(bitmap.width)
        return IntArray(width * (bottom - top)) { index -> pixels[index % width, top + index / width].toArgb() }
    }

    private fun ComposeUiTest.exists(tag: String) = onAllNodesWithTag(tag, true).fetchSemanticsNodes().isNotEmpty()
    private fun ComposeUiTest.bounds(tag: String) = onNodeWithTag(tag, true).fetchSemanticsNode().boundsInWindow
    private fun ComposeUiTest.awaitTag(tag: String) { waitUntil(timeoutMillis = 30_000) { exists(tag) }; onNodeWithTag(tag, true).assertIsDisplayed() }
    private fun ComposeUiTest.click(tag: String) { awaitTag(tag); onNodeWithTag(tag, true).assertIsEnabled().performMouseInput { click() }; waitForIdle() }
}
