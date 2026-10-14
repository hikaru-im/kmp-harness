package im.hikaru.harness.exec.jvm

import im.hikaru.harness.session.SessionId
import im.hikaru.harness.session.api.*
import java.io.IOException
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class JvmTerminalSemanticInputTest {
    private val esc = "\u001b"

    @Test
    fun realParserArrowModesAndModifiedThenPlainAreIsolated() = runBlocking<Unit> {
        val directions = listOf(TerminalKeyName.Up to "A", TerminalKeyName.Down to "B",
            TerminalKeyName.Left to "D", TerminalKeyName.Right to "C")
        val stages = listOf(false, true, false).map { application ->
            val prefix = esc + if (application) "O" else "["
            Stage(esc + if (application) "[?1h" else "[?1l", directions.flatMap { (key, suffix) -> listOf(
                named(key, prefix + suffix), named(key, esc + "[1;5" + suffix, TerminalModifier.Control),
                named(key, prefix + suffix),
            ) } + listOf(
                named(TerminalKeyName.Up, esc + "[1;2A", TerminalModifier.Shift),
                named(TerminalKeyName.Up, esc + "[1;6A", TerminalModifier.Shift, TerminalModifier.Control),
                named(TerminalKeyName.Up, esc + prefix + "A", TerminalModifier.Alt),
                named(TerminalKeyName.Up, esc + prefix + "A", TerminalModifier.Alt, TerminalModifier.Control),
                named(TerminalKeyName.Up, esc + prefix + "A", TerminalModifier.Alt, TerminalModifier.Shift),
                named(TerminalKeyName.Up, esc + prefix + "A", TerminalModifier.Alt, TerminalModifier.Shift, TerminalModifier.Control),
                named(TerminalKeyName.Up, prefix + "A"),
            ))
        }
        withFixture { it.run(stages) }
    }

    @Test
    fun realFunctionAndKeypadModesUseIndependentEncoders() = runBlocking<Unit> {
        val functions = listOf(TerminalKeyName.F1 to "P", TerminalKeyName.F2 to "Q",
            TerminalKeyName.F3 to "R", TerminalKeyName.F4 to "S").flatMap { (key, suffix) -> listOf(
            named(key, esc + "[1;5" + suffix, TerminalModifier.Control), named(key, esc + "O" + suffix),
        ) } + listOf(TerminalKeyName.F5 to 15, TerminalKeyName.F6 to 17, TerminalKeyName.F7 to 18,
            TerminalKeyName.F8 to 19, TerminalKeyName.F9 to 20, TerminalKeyName.F10 to 21,
            TerminalKeyName.F11 to 23, TerminalKeyName.F12 to 24).map { (key, code) -> named(key, esc + "[" + code + "~") }
        val directions = listOf(TerminalKeyName.KeypadUp to "A", TerminalKeyName.KeypadDown to "B",
            TerminalKeyName.KeypadLeft to "D", TerminalKeyName.KeypadRight to "C")
        val navigation = listOf(TerminalKeyName.Insert to 2, TerminalKeyName.Delete to 3,
            TerminalKeyName.PageUp to 5, TerminalKeyName.PageDown to 6).flatMap { (key, code) -> listOf(
            named(key, esc + "[" + code + "~"), named(key, esc + "[" + code + ";5~", TerminalModifier.Control),
        ) }
        val stages = listOf(true to false, false to true, true to true, false to false).map { (arrows, keypad) ->
            val prefix = esc + if (keypad) "O" else "["
            Stage(esc + (if (arrows) "[?1h" else "[?1l") + esc + (if (keypad) "=" else ">"),
                functions + navigation + directions.flatMap { (key, suffix) -> listOf(
                    named(key, prefix + suffix), named(key, prefix + suffix, TerminalModifier.Control), named(key, prefix + suffix),
                ) } + listOf(TerminalKeyName.Home to "H", TerminalKeyName.End to "F").flatMap { (key, suffix) -> listOf(
                    named(key, prefix + suffix), named(key, esc + "[1;5" + suffix, TerminalModifier.Control), named(key, prefix + suffix),
                ) } + named(TerminalKeyName.Up, esc + (if (arrows) "OA" else "[A")))
        }
        withFixture { it.run(stages) }
    }

    @Test
    fun realEnterBackspaceTabAndEscapeUseCurrentModes() = runBlocking<Unit> {
        val stages = listOf(false, true, false).map { autoNewLine ->
            val enter = if (autoNewLine) "\r\n" else "\r"
            Stage(esc + if (autoNewLine) "[20h" else "[20l", listOf(
                named(TerminalKeyName.Enter, enter), named(TerminalKeyName.Enter, enter, TerminalModifier.Shift),
                named(TerminalKeyName.Enter, enter, TerminalModifier.Control),
                named(TerminalKeyName.Backspace, "\u007f"), named(TerminalKeyName.Backspace, "\b", TerminalModifier.Control),
                named(TerminalKeyName.Backspace, esc + "\u007f", TerminalModifier.Alt),
                named(TerminalKeyName.Tab, "\t"), named(TerminalKeyName.Tab, esc + "[Z", TerminalModifier.Shift),
                named(TerminalKeyName.Escape, esc), named(TerminalKeyName.Escape, esc, TerminalModifier.Control),
            ))
        }
        withFixture { it.run(stages) }
    }

    @Test
    fun realControlAndAltCharactersReachPtyExactlyOnce() = runBlocking<Unit> {
        val controlLetters = ('a'..'z').flatMap { letter ->
            val control = (letter.code - 'a'.code + 1).toChar().toString()
            listOf(character(letter.toString(), control, TerminalModifier.Control),
                character(letter.uppercaseChar().toString(), control, TerminalModifier.Control, TerminalModifier.Shift))
        }
        val controls = listOf(" " to "\u0000", "@" to "\u0000", "[" to esc, "\\" to "\u001c",
            "]" to "\u001d", "^" to "\u001e", "_" to "\u001f", "?" to "\u007f").flatMap { (text, bytes) -> listOf(
            character(text, bytes, TerminalModifier.Control), character(text, bytes, TerminalModifier.Control, TerminalModifier.Shift),
        ) }
        val unsupported = listOf(
            character("a", null), character("a", null, TerminalModifier.Shift),
            character("a", null, TerminalModifier.Alt, TerminalModifier.Control),
            character("a", null, TerminalModifier.Alt, TerminalModifier.Shift),
            character("a", null, TerminalModifier.Alt, TerminalModifier.Control, TerminalModifier.Shift),
            character("1", null, TerminalModifier.Control), character("!", null, TerminalModifier.Control, TerminalModifier.Shift),
            character("@", null, TerminalModifier.Alt), character("a", null, TerminalModifier.Meta),
        ) + TerminalKeyName.entries.filter { it != TerminalKeyName.Character }.map { named(it, null, TerminalModifier.Meta) }
        val stages = listOf(
            Stage(samples = controlLetters + controls + unsupported + listOf(
                character("b", esc + "b", TerminalModifier.Alt), character("B", esc + "b", TerminalModifier.Alt))),
            Stage(esc + "[?1039l", listOf(character("b", null, TerminalModifier.Alt),
                Sample(TerminalInput.Text("恢复🙂"), "恢复🙂"))),
            Stage(esc + "[?1039h", listOf(character("b", esc + "b", TerminalModifier.Alt))),
        )
        withFixture { it.run(stages) }
    }

    @Test
    fun committedUnicodeIsWholeAndNotLocallyEchoed() = runBlocking<Unit> {
        val committed = "中文🙂𠮷\r\nLINE\n"
        withFixture { f ->
            val key = f.run(listOf(Stage(samples = listOf(Sample(TerminalInput.Text(committed), committed)))))
            val snapshot = f.snapshot(key)
            assertFalse(snapshot.rawTail.contains("中文"))
            assertFalse(snapshot.rawTail.contains("🙂"))
            assertFalse((snapshot.screen + snapshot.history).any { it.text.contains("中文") || it.text.contains("🙂") })
            assertFalse(snapshot.rawTail.contains('\ufffd'))
            assertEquals(1, f.nativeWrites.get())
        }
    }

    @Test
    fun realPasteNormalizesNewlinesAndHonorsBracketedMode() = runBlocking<Unit> {
        val source = "a\r\nb\nc\rd"
        val normalized = "a\rb\rc\rd"
        val start = esc + "[200~"
        val end = esc + "[201~"
        val stages = listOf(
            Stage(esc + "[?2004l", listOf(Sample(TerminalInput.Paste(source), normalized))),
            Stage(esc + "[?2004h", listOf(Sample(TerminalInput.Paste(source), start + normalized + end),
                Sample(TerminalInput.Paste("中🙂\r\n\n\r"), start + "中🙂\r\r\r" + end))),
            Stage(esc + "[?2004l", listOf(Sample(TerminalInput.Paste(source), normalized))),
        )
        withFixture { f ->
            f.run(stages)
            val actual = Files.readString(f.root.resolve("phase-1.bin"), UTF_8)
            assertEquals(2, actual.windowed(start.length).count { it == start })
            assertEquals(2, actual.windowed(end.length).count { it == end })
            assertFalse(Files.readString(f.root.resolve("phase-0.bin"), UTF_8).contains(start))
            assertFalse(Files.readString(f.root.resolve("phase-2.bin"), UTF_8).contains(end))
        }
    }

    @Test
    fun finalByteBudgetRejectionKeepsInputOpen() = runBlocking<Unit> {
        val invalid = listOf("", "\u0000", "a\u0000b", "\uD800", "\uDC00", "\uD800x", "x\uDC00")
        val denied = invalid.flatMap { listOf(Sample(TerminalInput.Text(it), null), Sample(TerminalInput.Paste(it), null)) } + listOf(
            Sample(TerminalInput.Text("🙂".repeat(5)), null), Sample(TerminalInput.Text("x".repeat(17)), null),
            Sample(TerminalInput.Paste("abcde"), null), character("b", null, TerminalModifier.Alt),
        )
        val stage = Stage(esc + "[?2004h" + esc + "[?1039l", denied + listOf(
            Sample(TerminalInput.Text("🙂".repeat(4)), "🙂".repeat(4)),
            Sample(TerminalInput.Paste("abcd"), esc + "[200~abcd" + esc + "[201~"), Sample(TerminalInput.Text("OK"), "OK"),
        ))
        withFixture(JvmPtyTerminalSessions.Limits(inputBytes = 16)) { f ->
            val key = f.capture(listOf(stage))
            f.waitStage(key, 0)
            val raw = assertIs<TerminalMutationResult.Rejected>(f.service.write(key, "\u0000"))
            assertEquals(key, raw.key)
            assertEquals(0, f.nativeWrites.get())
            assertEquals(TerminalInputState.Open, f.snapshot(key).metadata.input)
            f.deliver(key, 0, stage)
            f.finish(key)
            assertEquals(3, f.nativeWrites.get())
            assertEquals(TerminalInputState.Open, f.snapshot(key).metadata.input)
        }
    }

    @Test
    fun softAndHardResetPreservePinnedParserInputPolicy() = runBlocking<Unit> {
        val initial = Stage(samples = listOf(named(TerminalKeyName.Up, esc + "[A"),
            named(TerminalKeyName.KeypadUp, esc + "[A"), named(TerminalKeyName.Home, esc + "[H"),
            named(TerminalKeyName.Enter, "\r"), character("b", esc + "b", TerminalModifier.Alt),
            Sample(TerminalInput.Paste("P"), "P")))
        fun retained(enter: String): List<Sample> = listOf(
            named(TerminalKeyName.Up, esc + "OA"), named(TerminalKeyName.Up, esc + "[1;5A", TerminalModifier.Control),
            named(TerminalKeyName.Up, esc + "OA"), named(TerminalKeyName.KeypadUp, esc + "OA"),
            named(TerminalKeyName.Home, esc + "OH"), named(TerminalKeyName.Enter, enter),
            character("b", null, TerminalModifier.Alt), Sample(TerminalInput.Paste("P"), esc + "[200~P" + esc + "[201~"),
        )
        val stages = listOf(initial,
            Stage(esc + "[?1h" + esc + "=" + esc + "[20h" + esc + "[?1039l" + esc + "[?2004h", retained("\r\n")),
            Stage(esc + "[!p", retained("\r")), Stage(esc + "[20h" + esc + "c", retained("\r")))
        withFixture { it.run(stages) }
    }

    @Test
    fun semanticInputRejectsEveryStaleIdentityBeforeDelivery() = runBlocking<Unit> {
        val stage = Stage(samples = listOf(Sample(TerminalInput.Text("VALID"), "VALID")))
        withFixture { f ->
            val key = f.capture(listOf(stage), exitAfterCapture = true)
            f.waitStage(key, 0)
            val pid = assertNotNull(f.service.diagnostics(key)?.process).pid()
            val stale = listOf(key.copy(owner = key.owner.copy(scope = TerminalScope("other-scope"))),
                key.copy(owner = key.owner.copy(session = SessionId("other-session"))),
                key.copy(owner = key.owner.copy(incarnation = TerminalIncarnation("other-incarnation"))))
            for (requested in stale) {
                for (input in listOf(TerminalInput.Text("WRONG"), TerminalInput.Paste("WRONG"), TerminalInput.KeyPress(TerminalKeyName.Enter))) {
                    assertEquals(requested, assertIs<TerminalMutationResult.Rejected>(f.service.input(requested, input)).key)
                }
            }
            val missing = key.copy(terminal = TerminalSessionId("not-created"))
            repeat(3) {
                assertEquals(missing, assertIs<TerminalMutationResult.NotFound>(f.service.input(missing, TerminalInput.Text("WRONG"))).key)
                assertNull(f.service.diagnostics(missing))
            }
            assertEquals(listOf(key), f.inventory().map { it.key })
            assertEquals(pid, assertNotNull(f.service.diagnostics(key)?.process).pid())
            assertEquals(0, f.nativeWrites.get())
            f.deliver(key, 0, stage)
            f.until("owned process naturally exited") { f.snapshot(key).metadata.lifecycle == TerminalLifecycle.Exited(0) }
            assertEquals(TerminalInputState.Closed, f.snapshot(key).metadata.input)
            assertEquals(key, assertIs<TerminalMutationResult.Rejected>(f.service.input(key, TerminalInput.Text("EXITED"))).key)
            f.remember(key)
            assertIs<TerminalMutationResult.Applied>(f.service.close(key))
            assertEquals(key, assertIs<TerminalMutationResult.NotFound>(f.service.input(key, TerminalInput.Text("CLOSED"))).key)
            assertTrue(f.inventory().isEmpty())
            assertEquals(1, f.nativeWrites.get())
            assertContentEquals("VALID".toByteArray(UTF_8), Files.readAllBytes(f.root.resolve("phase-0.bin")))
        }
    }

    @Test
    fun lostSemanticAcknowledgementFencesWithoutRetry() = runBlocking<Unit> {
        val stage = Stage(samples = listOf(Sample(TerminalInput.Text("ONCE🙂"), "ONCE🙂")))
        withFixture { f ->
            val key = f.capture(listOf(stage))
            f.waitStage(key, 0)
            f.service.afterNativeWrite = { written ->
                assertEquals(key, written)
                f.nativeWrites.incrementAndGet()
                throw IOException("Acknowledgement lost after actual native delivery")
            }
            assertEquals(key, assertIs<TerminalMutationResult.UnknownOutcome>(f.service.input(key, stage.samples.single().input)).key)
            f.expectCapture(0, stage)
            f.finish(key)
            assertEquals(1, f.nativeWrites.get())
            assertEquals(TerminalInputState.Unknown, f.snapshot(key).metadata.input)
            assertIs<TerminalMutationResult.Rejected>(f.service.input(key, TerminalInput.Text("RETRY")))
            assertIs<TerminalMutationResult.Rejected>(f.service.write(key, "RAW_RETRY"))
            assertIs<TerminalMutationResult.Rejected>(f.service.resize(key, TerminalSize(30, 100)))
            assertEquals(1, f.nativeWrites.get())
            assertContentEquals(stage.expectedBytes, Files.readAllBytes(f.root.resolve("phase-0.bin")))
        }
    }

    @Test
    fun semanticCancellationBeforeAndAfterDeliveryRemainDistinct() = runBlocking<Unit> {
        val stage = Stage(samples = listOf(Sample(TerminalInput.Text("ONCE"), "ONCE")))
        withFixture { f ->
            val key = f.capture(listOf(stage))
            f.waitStage(key, 0)
            val before = CompletableDeferred<TerminalMutationResult>()
            val cancelled = launch(start = CoroutineStart.UNDISPATCHED) {
                coroutineContext[Job]!!.cancel()
                before.complete(f.service.input(key, TerminalInput.Text("BEFORE")))
            }
            assertEquals(key, assertIs<TerminalMutationResult.Rejected>(withTimeout(10_000) { before.await() }).key)
            cancelled.join()
            assertEquals(0, f.nativeWrites.get())
            assertEquals(TerminalInputState.Open, f.snapshot(key).metadata.input)
            f.until("owned dd has opened the capture") { Files.exists(f.root.resolve("phase-0.bin")) }
            assertEquals(0L, Files.size(f.root.resolve("phase-0.bin")))
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            f.service.afterNativeWrite = {
                f.nativeWrites.incrementAndGet()
                entered.countDown()
                check(release.await(10, TimeUnit.SECONDS)) { "Owned native acknowledgement was not released" }
            }
            val after = CompletableDeferred<TerminalMutationResult>()
            val writer = launch(Dispatchers.Default) {
                try { after.complete(f.service.input(key, stage.samples.single().input)) }
                catch (error: Throwable) { after.completeExceptionally(error) }
            }
            try {
                assertTrue(withContext(Dispatchers.IO) { entered.await(10, TimeUnit.SECONDS) })
                f.expectCapture(0, stage) // bytes are observed before cancelling the real caller
                writer.cancel()
                release.countDown()
                assertEquals(key, assertIs<TerminalMutationResult.UnknownOutcome>(withTimeout(10_000) { after.await() }).key)
                withTimeout(10_000) { writer.join() }
                f.finish(key)
                assertEquals(TerminalInputState.Unknown, f.snapshot(key).metadata.input)
                assertIs<TerminalMutationResult.Rejected>(f.service.input(key, TerminalInput.Text("RETRY")))
                assertIs<TerminalMutationResult.Rejected>(f.service.write(key, "RETRY"))
                assertIs<TerminalMutationResult.Rejected>(f.service.resize(key, TerminalSize(30, 100)))
                assertEquals(1, f.nativeWrites.get())
            } finally {
                release.countDown()
                f.service.afterNativeWrite = null
                writer.cancel()
                withContext(NonCancellable) { withTimeout(10_000) { writer.join() } }
            }
        }
    }

    @Test
    fun boundedWorkerRejectsOverflowAndParserResizeDoNotDeadlock() = runBlocking<Unit> {
        val first = Stage(samples = listOf(Sample(TerminalInput.Text("0"), "0")))
        val queuedStage = Stage(esc + "[?1h", (1..8).map { Sample(TerminalInput.Text(it.toString()), it.toString()) })
        withFixture { f ->
            val key = f.capture(listOf(first, queuedStage))
            f.waitStage(key, 0)
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            f.service.afterNativeWrite = {
                if (f.nativeWrites.incrementAndGet() == 1) {
                    entered.countDown()
                    check(release.await(10, TimeUnit.SECONDS)) { "Owned blocked IO was not released" }
                }
            }
            val firstWriter = async(Dispatchers.Default) { f.service.input(key, first.samples.single().input) }
            val queued = mutableListOf<kotlinx.coroutines.Deferred<TerminalMutationResult>>()
            try {
                assertTrue(withContext(Dispatchers.IO) { entered.await(10, TimeUnit.SECONDS) })
                f.waitStage(key, 1) // parser must process output while native IO is blocked outside its gate
                for ((index, sample) in queuedStage.samples.withIndex()) {
                    queued.add(async(Dispatchers.Default) { f.service.input(key, sample.input) })
                    f.until("exactly ${index + 1} queued IO tasks") { f.service.diagnostics(key)?.queuedIoTasks == index + 1 }
                }
                assertEquals(8, assertNotNull(f.service.diagnostics(key)).queuedIoTasks)
                val overflow = withTimeout(1_000) { f.service.input(key, TerminalInput.Text("OVERFLOW")) }
                assertEquals(key, assertIs<TerminalMutationResult.Rejected>(overflow).key)
                assertEquals(1, f.nativeWrites.get())
                assertEquals(8, assertNotNull(f.service.diagnostics(key)).queuedIoTasks)
                assertEquals(TerminalInputState.Open, withTimeout(1_000) { f.snapshot(key) }.metadata.input)
                release.countDown()
                f.until("worker released a queue slot") { assertNotNull(f.service.diagnostics(key)).queuedIoTasks < 8 }
                val resize = async(Dispatchers.Default) { f.service.resize(key, TerminalSize(30, 100)) }
                assertEquals(1, assertIs<TerminalMutationResult.Applied>(withTimeout(10_000) { firstWriter.await() }).bytes)
                for (writer in queued) assertEquals(1, assertIs<TerminalMutationResult.Applied>(withTimeout(10_000) { writer.await() }).bytes)
                assertEquals(TerminalSize(30, 100), assertIs<TerminalMutationResult.Applied>(withTimeout(10_000) { resize.await() }).size)
                f.expectCapture(0, first)
                f.expectCapture(1, queuedStage)
                f.finish(key)
                val snapshot = withTimeout(1_000) { f.snapshot(key) }
                assertEquals(TerminalSize(30, 100), snapshot.size)
                assertEquals(TerminalInputState.Open, snapshot.metadata.input)
                assertEquals(0, assertNotNull(f.service.diagnostics(key)).queuedIoTasks)
                assertEquals(9, f.nativeWrites.get())
                assertTrue(assertNotNull(f.service.diagnostics(key)).workers.any { it.name.endsWith("-parser") && it.isAlive })
            } finally {
                release.countDown()
                f.service.afterNativeWrite = null
                firstWriter.cancel()
                queued.forEach { it.cancel() }
                withContext(NonCancellable) {
                    withTimeout(10_000) { firstWriter.join(); queued.forEach { it.join() } }
                }
            }
        }
    }

    private data class Sample(val input: TerminalInput, val expected: String?)
    private data class Stage(val modes: String = "", val samples: List<Sample>) {
        val expectedBytes: ByteArray get() = samples.mapNotNull { it.expected }.joinToString("").toByteArray(UTF_8)
    }

    private fun named(key: TerminalKeyName, expected: String?, vararg modifiers: TerminalModifier): Sample =
        Sample(TerminalInput.KeyPress(key, modifiers.toSet()), expected)
    private fun character(text: String, expected: String?, vararg modifiers: TerminalModifier): Sample =
        Sample(TerminalInput.KeyPress(TerminalKeyName.Character, modifiers.toSet(), text), expected)

    private suspend fun withFixture(
        limits: JvmPtyTerminalSessions.Limits = JvmPtyTerminalSessions.Limits(ioWaitMillis = 10_000),
        block: suspend (Fixture) -> Unit,
    ) {
        val temp = Files.createTempDirectory("harness-owned-semantic-input-")
        val root = Files.createDirectory(temp.resolve("session"))
        val f = Fixture(root, JvmPtyTerminalSessions(TerminalScope("semantic-scope"), SessionId("semantic-session"), root, limits))
        try { block(f) } finally {
            withContext(NonCancellable) {
                try {
                    f.inventory().forEach { f.remember(it.key) }
                } finally {
                    assertIs<TerminalShutdownResult.Applied>(f.service.shutdown(f.service.owner), "Owned real PTYs must be confirmed stopped")
                    for (owned in f.owned) {
                        assertFalse(owned.process?.isAlive == true, "Owned native process remains alive")
                        assertTrue(owned.descendants.none { it.isAlive }, "Owned descendant remains alive")
                        assertTrue(owned.workers.none { it.isAlive }, owned.workers.joinToString { it.name + ":" + it.state })
                    }
                    Files.walk(temp).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.delete(it) } }
                }
                assertTrue(f.inventory().isEmpty())
            }
        }
    }

    private class Fixture(val root: Path, val service: JvmPtyTerminalSessions) {
        val nativeWrites = AtomicInteger()
        val owned = mutableListOf<JvmPtyTerminalSessions.Diagnostics>()
        private val token = "SEM_" + UUID.randomUUID().toString().replace("-", "")
        private val home = Files.createDirectory(root.resolve("home"))

        init { service.afterNativeWrite = { nativeWrites.incrementAndGet() } }

        private fun quote(text: String): String = "'" + text.replace("'", "'\"'\"'") + "'"
        private fun marker(index: Int): String = token + "_READY_" + index
        private fun display(text: String): String = "printf '%s' " + quote("\u001b[2J\u001b[H" + text)

        suspend fun capture(stages: List<Stage>, exitAfterCapture: Boolean = false): TerminalKey {
            require(stages.isNotEmpty() && stages.all { it.expectedBytes.isNotEmpty() })
            val script = buildString {
                append("test -t 0 && test -t 1 && test -t 2 || exit 70; stty raw -echo || exit 71; ")
                for ((index, stage) in stages.withIndex()) {
                    append("printf '%s' ").append(quote(stage.modes)).append("; ")
                    append(display(marker(index))).append("; ")
                    // dd/od observe native stdin; expected bytes are used only as an exact count.
                    append("dd bs=1 count=").append(stage.expectedBytes.size).append(" of=phase-").append(index).append(".bin 2>/dev/null || exit 72; ")
                    append("od -An -v -tx1 phase-").append(index).append(".bin > phase-").append(index).append(".hex || exit 73; ")
                }
                append(display(token + "_DRAIN"))
                append(if (exitAfterCapture) "; exit 0" else "; dd bs=1 of=unexpected.bin 2>/dev/null")
            }
            val request = TerminalCreateRequest(service.owner, listOf("/bin/sh", "-c", script),
                mapOf("HOME" to home.toString(), "PATH" to "/usr/bin:/bin", "TERM" to "xterm-256color", "LC_ALL" to "C"),
                TerminalSize(), root.toString())
            val key = assertIs<TerminalCreateResult.Created>(service.create(request), "Native fixture has no platform/program skip").metadata.key
            remember(key)
            return key
        }

        suspend fun run(stages: List<Stage>): TerminalKey {
            val key = capture(stages)
            for ((index, stage) in stages.withIndex()) deliver(key, index, stage)
            finish(key)
            assertEquals(stages.sumOf { stage -> stage.samples.count { it.expected != null } }, nativeWrites.get())
            return key
        }

        suspend fun deliver(key: TerminalKey, index: Int, stage: Stage) {
            waitStage(key, index)
            for (sample in stage.samples) {
                val before = nativeWrites.get()
                val result = service.input(key, sample.input)
                assertEquals(key, result.key)
                if (sample.expected == null) {
                    assertIs<TerminalMutationResult.Rejected>(result, sample.input.toString())
                    assertEquals(before, nativeWrites.get(), "Known rejection must not call nativeWrite")
                    assertEquals(TerminalInputState.Open, snapshot(key).metadata.input)
                } else {
                    assertEquals(sample.expected.toByteArray(UTF_8).size, assertIs<TerminalMutationResult.Applied>(result, sample.input.toString()).bytes)
                    assertEquals(before + 1, nativeWrites.get(), "One committed request makes one real native write")
                }
            }
            expectCapture(index, stage)
        }

        suspend fun expectCapture(index: Int, stage: Stage) {
            val binary = root.resolve("phase-" + index + ".bin")
            val hexFile = root.resolve("phase-" + index + ".hex")
            val expectedHex = stage.expectedBytes.joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
            until("real dd/od capture for stage " + index) {
                Files.exists(binary) && Files.size(binary) == stage.expectedBytes.size.toLong() &&
                    Files.exists(hexFile) && Files.readString(hexFile).filterNot { it.isWhitespace() } == expectedHex
            }
            assertContentEquals(stage.expectedBytes, Files.readAllBytes(binary))
            assertEquals(expectedHex, Files.readString(hexFile).filterNot { it.isWhitespace() })
        }

        suspend fun waitStage(key: TerminalKey, index: Int) {
            until("parser screen marker " + marker(index)) { snapshot(key).screen.any { it.text.contains(marker(index)) } }
            remember(key)
        }

        suspend fun finish(key: TerminalKey) {
            until("parser drain marker") { snapshot(key).screen.any { it.text.contains(token + "_DRAIN") } }
            until("owned extra-byte collector") { Files.exists(root.resolve("unexpected.bin")) }
            assertEquals(0L, Files.size(root.resolve("unexpected.bin")), "No duplicate or rejected bytes may follow the expected capture")
            remember(key)
        }

        fun remember(key: TerminalKey) { owned.add(assertNotNull(service.diagnostics(key))) }
        suspend fun inventory(): List<TerminalMetadata> = assertIs<TerminalListResult.Available>(service.list(service.owner)).terminals
        suspend fun snapshot(key: TerminalKey): TerminalSnapshot = assertIs<TerminalReadResult.Available>(service.read(key)).snapshot
        suspend fun until(label: String, predicate: suspend () -> Boolean) {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            while (!predicate()) {
                assertTrue(System.nanoTime() < deadline, "Deadline waiting for " + label)
                delay(5)
            }
        }
    }
}
