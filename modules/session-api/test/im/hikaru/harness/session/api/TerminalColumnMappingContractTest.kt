package im.hikaru.harness.session.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlin.test.*

class TerminalColumnMappingContractTest {
    private val style = TerminalTextStyle()
    @Test fun mappingInvariantsAndLegacyJsonRemainCompatible() {
        val legacy = Json.decodeFromString<TerminalTextRun>("""{"column":0,"span":1,"text":"x","style":{}}""")
        assertNull(legacy.columnOffsets)
        val run = TerminalTextRun(0, 3, "中x", style, listOf(0, 2, 3))
        assertEquals(run, Json.decodeFromString<TerminalTextRun>(Json.encodeToString(run)))
        listOf(listOf(0, 3), listOf(0, 0, 3), listOf(-1, 2, 3), listOf(0, 2, 4), listOf(0, 3, 2)).forEach {
            assertFailsWith<IllegalArgumentException> { TerminalTextRun(0, 3, "中x", style, it) }
        }
        assertEquals(listOf(2), TerminalTextRun(0, 2, "", style, listOf(2)).columnOffsets)
    }
    @Test fun wideContinuationAndSurrogateOffsetsRetainNativeColumns() {
        val bold = TerminalTextStyle(attributes = setOf(TerminalTextAttribute.Bold))
        val runs = listOf(TerminalTextRun(0, 3, "中x", bold, listOf(0, 2, 3)),
            TerminalTextRun(3, 2, "😀", style, listOf(0, 1, 2)),
            TerminalTextRun(5, 2, "x", style, listOf(1, 2)),
            TerminalTextRun(7, 2, "", style, listOf(2)))
        assertEquals("中x😀x", TerminalLineSnapshot(runs).text)
        assertEquals(listOf(0, 2, 3), runs[0].columnOffsets)
        assertEquals(2, runs[1].text.length)
        assertEquals(listOf(0, 1, 2), runs[1].columnOffsets)
        assertEquals(listOf(1, 2), runs[2].columnOffsets)
        assertEquals(setOf(TerminalTextAttribute.Bold), runs[0].style.attributes)
        assertEquals(listOf(1), TerminalTextRun(0, 1, "", style, listOf(1)).columnOffsets)
        assertEquals(listOf(0, 1), TerminalTextRun(0, 1, "�", style, listOf(0, 1)).columnOffsets)
    }
    @Test fun mappingCollectionsAreDetachedAcrossSnapshots() {
        val offsets = mutableListOf(0, 2, 3)
        val original = TerminalLineSnapshot(listOf(TerminalTextRun(0, 3, "中x", style, offsets)))
        val decoded = Json.decodeFromString<TerminalLineSnapshot>(Json.encodeToString(original))
        offsets[1] = 1
        assertEquals(listOf(0, 2, 3), decoded.runs.single().columnOffsets)
        assertNotSame(original.runs, decoded.runs)
        assertNotSame(original.runs.single().columnOffsets, decoded.runs.single().columnOffsets)
        val history = Json.decodeFromString<List<TerminalLineSnapshot>>(Json.encodeToString(listOf(decoded, decoded)))
        assertNotSame(history[0].runs[0].columnOffsets, history[1].runs[0].columnOffsets)
    }
}
