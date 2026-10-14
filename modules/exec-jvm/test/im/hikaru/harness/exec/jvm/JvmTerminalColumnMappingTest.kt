package im.hikaru.harness.exec.jvm

import im.hikaru.harness.session.SessionId
import im.hikaru.harness.session.api.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import kotlinx.coroutines.*
import kotlin.test.*

/** Real parser/PTY coverage; impossible native lone-surrogate cases are explicitly model-only. */
class JvmTerminalColumnMappingTest {
    @Test fun realParserMapsAsciiCjkEmojiAndNulToNativeColumns() = runBlocking<Unit> {
        fixture { f ->
            val key = f.start("\u001b[2J\u001b[HA中x😀\u001b[2;10HZ", TerminalSize(4, 40))
            val s = f.ready(key)
            val run = s.screen[0].runs.first { it.text.startsWith("A中x😀") }
            assertEquals("A中x😀", run.text.take(5))
            assertEquals(listOf(0, 1, 3, 4, 5, 6), run.columnOffsets?.take(6))
            val blank = s.screen[1].runs.first { it.column == 0 }
            assertEquals("         ", blank.text.take(9))
            assertEquals((0..9).toList(), blank.columnOffsets?.take(10))
            assertTrue(s.screen.all { line -> line.runs.all { '\u0000' !in it.text && it.columnOffsets != null } })
        }
    }
    @Test fun realStylesHistoryAndLeadingContinuationKeepOffsets() = runBlocking<Unit> {
        fixture { f ->
            val output = buildString {
                repeat(10) { append("\u001b[1;38;5;196m中x\u001b[0m\r\n") }
                append("\u001b[2;1H\u001b[44m中x\u001b[0m")
            }
            val s = f.ready(f.start(output, TerminalSize(4, 40)))
            assertTrue(s.history.isNotEmpty())
            val historical = s.history.flatMap { it.runs }.first { it.text.startsWith("中x") }
            assertEquals(listOf(0, 2, 3), historical.columnOffsets?.take(3))
            assertEquals(TerminalColorValue.Rgb(255, 0, 0), historical.style.foreground)
            assertTrue(TerminalTextAttribute.Bold in historical.style.attributes)
            val styled = s.screen[1].runs.first { it.text.startsWith("中x") }
            assertEquals(TerminalColorValue.Indexed(4), styled.style.background)
            assertEquals(listOf(0, 2, 3), styled.columnOffsets?.take(3))
            // Model boundary, not a claim that native ANSI reliably produces a DWC-only entry.
            assertEquals(listOf(1, 2), TerminalTextRun(0, 2, "x", styled.style, listOf(1, 2)).columnOffsets)
            assertEquals(listOf(2), TerminalTextRun(0, 2, "", styled.style, listOf(2)).columnOffsets)
        }
    }
    @Test fun realResizeTruncationPreservesNativeSpansAndValidUnicode() = runBlocking<Unit> {
        fixture { f ->
            val key = f.start("\u001b[2J\u001b[Habcd😀中x", TerminalSize(4, 40))
            f.ready(key)
            assertIs<TerminalMutationResult.Applied>(f.service.resize(key, TerminalSize(4, 5)))
            f.until { f.snapshot(key).size == TerminalSize(4, 5) }
            val s = f.snapshot(key)
            assertEquals(5, s.size.columns)
            for (run in (s.screen + s.history).flatMap { it.runs }) {
                assertTrue(run.column + run.span <= 5)
                val offsets = assertNotNull(run.columnOffsets)
                assertEquals(run.span, offsets.last())
                assertEquals(run.text.length + 1, offsets.size)
                assertFalse(Regex("[\\uD800-\\uDBFF](?![\\uDC00-\\uDFFF])|(?<![\\uD800-\\uDBFF])[\\uDC00-\\uDFFF]").containsMatchIn(run.text))
            }
            // Supplemental literal replacement/cut invariants; these are not native surrogate fixtures.
            assertEquals(listOf(1), TerminalTextRun(0, 1, "", TerminalTextStyle(), listOf(1)).columnOffsets)
            assertEquals("�", TerminalTextRun(0, 1, "�", TerminalTextStyle(), listOf(0, 1)).text)
        }
    }
    @Test fun realMappedSnapshotsRemainDetachedAndCleanupIsOwned() = runBlocking<Unit> {
        fixture { f ->
            val key = f.start("\u001b[2J\u001b[H中x😀", TerminalSize(4, 40))
            val first = f.ready(key); val second = f.snapshot(key)
            val a = first.screen[0].runs.first { it.text.startsWith("中x") }
            val b = second.screen[0].runs.first { it.text.startsWith("中x") }
            assertEquals(a, b); assertNotSame(a.columnOffsets, b.columnOffsets)
            assertNotSame(first.screen, second.screen); assertNotSame(first.history, second.history)
            assertEquals(listOf(0, 2, 3, 4, 5), a.columnOffsets?.take(5))
            val owned = assertNotNull(f.service.diagnostics(key))
            assertTrue(owned.process?.isAlive == true)
            assertTrue(owned.workers.map { it.name.substringAfterLast('-') }.containsAll(listOf("io", "parser", "waiter")))
        }
    }
    private suspend fun fixture(body: suspend (Fixture) -> Unit) {
        val root = Files.createTempDirectory("harness-column-mapping-")
        val f = Fixture(root, JvmPtyTerminalSessions(TerminalScope("mapping"), SessionId("mapping"), root,
            JvmPtyTerminalSessions.Limits(ioWaitMillis = 10_000)))
        try { body(f) } finally {
            withContext(NonCancellable) {
                try {
                    f.keys.forEach { f.remember(it) }
                    withTimeout(10_000) { assertIs<TerminalShutdownResult.Applied>(f.service.shutdown(f.service.owner)) }
                    f.until { f.owned.all { it.process?.isAlive != true && it.descendants.none { p -> p.isAlive } && it.workers.none { t -> t.isAlive } } }
                    assertTrue(f.owned.isNotEmpty())
                    f.owned.forEach { assertTrue(it.workers.all { thread -> thread.state == Thread.State.TERMINATED }) }
                } finally { Files.walk(root).use { it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) } }
            }
        }
    }
    private class Fixture(val root: Path, val service: JvmPtyTerminalSessions) {
        val keys = mutableListOf<TerminalKey>()
        val owned = mutableListOf<JvmPtyTerminalSessions.Diagnostics>()
        private fun quote(value: String) = "'" + value.replace("'", "'\"'\"'") + "'"
        suspend fun start(output: String, size: TerminalSize): TerminalKey {
            val script = "stty raw -echo || exit 71; printf '%s' " + quote(output + "\u001b[4;1HMAPPING_READY") +
                "; dd bs=1 of=unexpected.bin 2>/dev/null"
            val result = assertIs<TerminalCreateResult.Created>(service.create(TerminalCreateRequest(service.owner,
                listOf("/bin/sh", "-c", script), mapOf("PATH" to "/usr/bin:/bin", "TERM" to "xterm-256color", "LANG" to "en_US.UTF-8"), size, root.toString())))
            keys += result.metadata.key; remember(result.metadata.key); return result.metadata.key
        }
        fun remember(key: TerminalKey) { service.diagnostics(key)?.let { owned += it } }
        suspend fun snapshot(key: TerminalKey) = assertIs<TerminalReadResult.Available>(service.read(key)).snapshot
        suspend fun ready(key: TerminalKey): TerminalSnapshot {
            until { snapshot(key).screen.any { it.text.contains("MAPPING_READY") } && Files.exists(root.resolve("unexpected.bin")) }
            remember(key); return snapshot(key)
        }
        suspend fun until(predicate: suspend () -> Boolean) { withTimeout(10_000) { while (!predicate()) delay(5) } }
    }
}
