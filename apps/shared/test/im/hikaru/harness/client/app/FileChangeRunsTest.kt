package im.hikaru.harness.client.app

import im.hikaru.harness.client.connection.FileChangeContent
import im.hikaru.harness.client.connection.FileChangeKind
import im.hikaru.harness.client.connection.MessageContent
import im.hikaru.harness.client.connection.TextContent
import kotlinx.serialization.json.Json
import kotlin.test.*

class FileChangeRunsTest {
    @Test
    fun consecutiveChangesFormOneRunKeyedByItsFirstIndex() {
        val contents = listOf<MessageContent>(
            TextContent("完成"),
            change("a.kt"),
            change("b.kt", FileChangeKind.DELETED),
            TextContent("说明"),
            change("c.kt", FileChangeKind.RENAMED),
        )
        val runs = fileChangeRuns(contents)
        assertEquals(setOf(1, 4), runs.keys)
        assertEquals(listOf("a.kt", "b.kt"), runs.getValue(1).map { it.path })
        assertEquals(listOf("c.kt"), runs.getValue(4).map { it.path })
    }

    @Test
    fun aMessageWithNoFileChangesHasNoRuns() {
        assertTrue(fileChangeRuns(listOf(TextContent("仅文本"))).isEmpty())
    }

    @Test
    fun everyKindCarriesItsCountsAndPatchState() {
        val files = listOf(
            FileChangeContent("added.kt", FileChangeKind.ADDED, added = 12, removed = 0, patch = "+a"),
            FileChangeContent("modified.kt", FileChangeKind.MODIFIED, added = 2, removed = 3, patch = "-b\n+c"),
            FileChangeContent("deleted.kt", FileChangeKind.DELETED, added = 0, removed = 9, patch = "-d"),
            FileChangeContent("new.kt", FileChangeKind.RENAMED, added = 0, removed = 0,
                previousPath = "old.kt", truncated = true),
        )
        val runs = fileChangeRuns(files)
        assertEquals(4, runs.getValue(0).size)
        assertEquals(FileChangeKind.RENAMED, runs.getValue(0)[3].kind)
        assertEquals("old.kt", runs.getValue(0)[3].previousPath)
        assertTrue(runs.getValue(0)[3].truncated)
    }

    @Test
    fun fileChangeContentSurvivesTheWireJson() {
        val files = listOf(
            FileChangeContent("src/a.kt", FileChangeKind.MODIFIED, 2, 1, "--- a\n+++ b\n@@ -1 +1 @@\n-x\n+y"),
            FileChangeContent("src/new name.kt", FileChangeKind.RENAMED, 0, 0, previousPath = "src/old.kt"),
        )
        for (file in files) {
            val json = Json.encodeToString(FileChangeContent.serializer(), file)
            assertEquals(file, Json.decodeFromString(FileChangeContent.serializer(), json))
        }
        // The transcript carries content through the sealed interface, so the wire name must be there.
        val polymorphic = Json.encodeToString(MessageContent.serializer(), files[0])
        assertTrue(polymorphic.contains("\"file-change\""))
        assertEquals(files[0], Json.decodeFromString(MessageContent.serializer(), polymorphic))
    }

    @Test
    fun invalidFileChangesAreRejected() {
        assertFailsWith<IllegalArgumentException> { FileChangeContent("", FileChangeKind.ADDED, 1, 0) }
        assertFailsWith<IllegalArgumentException> { FileChangeContent("a.kt", FileChangeKind.ADDED, -1, 0) }
        assertFailsWith<IllegalArgumentException> { FileChangeContent("a.kt", FileChangeKind.RENAMED, 0, 0, previousPath = "") }
    }

    private fun change(path: String, kind: FileChangeKind = FileChangeKind.MODIFIED) =
        FileChangeContent(path, kind, added = 1, removed = 1, patch = "+x\n-y")
}
