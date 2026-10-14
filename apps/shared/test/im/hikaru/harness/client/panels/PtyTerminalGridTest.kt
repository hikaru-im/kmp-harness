package im.hikaru.harness.client.panels

import im.hikaru.harness.client.connection.*
import kotlin.test.*

class PtyTerminalGridTest {
    private val style = TerminalTextStyle()
    private val key = TerminalKey(TerminalOwner(TerminalScope("s"), SessionId("p"), TerminalIncarnation("i")), TerminalSessionId("t"))
    private fun snapshot(screen: List<TerminalLineSnapshot>, history: List<TerminalLineSnapshot> = emptyList(), alternate: Boolean = false) =
        TerminalSnapshot(TerminalMetadata(key, "/test", listOf("shell"), TerminalLifecycle.Running, TerminalInputState.Open),
            TerminalSize(2, 5), screen, history, TerminalCursor(2, 0, true), alternate, 1, "RAW_MUST_NOT_APPEAR", false)
    private fun ascii(text: String, wrapped: Boolean = false) = TerminalLineSnapshot(listOf(TerminalTextRun(0, text.length, text, style)), wrapped)
    @Test fun glyphSelectionAndCopyUseNativeOffsets() {
        val line = TerminalLineSnapshot(listOf(TerminalTextRun(0, 3, "中x", style, listOf(0, 2, 3)),
            TerminalTextRun(3, 2, "😀", style, listOf(0, 1, 2))))
        assertEquals(listOf(0, 2, 3), PtyGridModel.glyphs(line).map { it.column })
        assertEquals(listOf(2, 3, 5), PtyGridModel.glyphs(line).map { it.endColumn })
        val s = snapshot(listOf(line, TerminalLineSnapshot(emptyList())))
        assertEquals("中x😀", PtyGridModel.copy(s, PtySelection(PtyCell(0, 0), PtyCell(0, 5))))
        assertEquals("中", PtyGridModel.copy(s, PtySelection(PtyCell(0, 1), PtyCell(0, 2))))
        assertEquals("😀", PtyGridModel.copy(s, PtySelection(PtyCell(0, 4), PtyCell(0, 5))))
        val leading = TerminalLineSnapshot(listOf(TerminalTextRun(0, 2, "x", style, listOf(1, 2))))
        assertEquals(" x ", PtyGridModel.copy(snapshot(listOf(leading, leading)), PtySelection(PtyCell(0, 0), PtyCell(0, 3))))
    }
    @Test fun legacyMappingFallbackIsAsciiOnly() {
        assertEquals(listOf(0, 1, 2), PtyGridModel.offsets(TerminalTextRun(0, 2, "ab", style)))
        listOf(TerminalTextRun(0, 2, "中", style), TerminalTextRun(0, 2, "😀", style),
            TerminalTextRun(0, 1, "\t", style), TerminalTextRun(0, 2, "a", style)).forEach {
            assertFailsWith<IncompatibleTerminalMapping> { PtyGridModel.offsets(it) }
        }
        assertEquals(listOf(2), PtyGridModel.offsets(TerminalTextRun(0, 2, "", style, listOf(2))))
    }
    @Test fun historyAlternateWrappedCopyAndCursorStayConsistent() {
        val s = snapshot(listOf(ascii("xy"), ascii("z")), listOf(ascii("abcde", true)))
        assertEquals("abcdexy   \nz", PtyGridModel.copy(s, PtySelection(PtyCell(0, 0), PtyCell(2, 1))))
        assertEquals(3, PtyGridModel.lines(s).size)
        assertEquals(2, PtyGridModel.lines(s.copy(alternate = true)).size)
        assertNull(PtyGridModel.cursor(s, 0, 2, true, true))
        assertEquals(PtyCell(1, 2), PtyGridModel.cursor(s, 1, 2, true, true))
        assertNull(PtyGridModel.cursor(s, 1, 2, false, true))
        val reading = PtyGridReading()
        reading.update(s, 2); reading.scrollLines(-1)
        assertFalse(reading.follow); assertEquals(0, reading.top)
        reading.selection = PtySelection(PtyCell(0, 0), PtyCell(0, 2))
        reading.update(s.copy(history = s.history + ascii("next"), revision = 2), 2)
        assertEquals(0, reading.top)
        reading.update(s.copy(alternate = true), 2)
        assertNull(reading.selection); assertTrue(reading.follow)
    }
    @Test fun viewportMetricsClampAndDeduplicateActualSizes() {
        assertEquals(TerminalSize(2, 5), PtyGridModel.viewport(1f, 1f, 8f, 18f))
        assertEquals(TerminalSize(200, 500), PtyGridModel.viewport(9000f, 9000f, 8f, 18f))
        assertEquals(TerminalSize(10, 10), PtyGridModel.viewport(80f, 180f, 8f, 18f))
        assertEquals(PtyGridModel.viewport(80f, 180f, 8f, 18f), PtyGridModel.viewport(87f, 197f, 8f, 18f))
        assertFailsWith<IllegalArgumentException> { PtyGridModel.viewport(1f, 1f, 0f, 18f) }
    }
}
