package im.hikaru.harness.exec.jvm

import im.hikaru.harness.session.SessionId
import im.hikaru.harness.session.api.*
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class JvmPtyTerminalSessionsTest {
    private val dollar = 36.toChar()

    @Test
    fun readingDoesNotCreatePtyAndCapacityIsBounded() = runBlocking {
        withPty(JvmPtyTerminalSessions.Limits(capacity = 1)) { f ->
            val unknown = TerminalKey(f.service.owner, TerminalSessionId("not-created"))
            repeat(3) {
                assertTrue(f.inventory().isEmpty())
                assertIs<TerminalReadResult.NotFound>(f.service.read(unknown))
                assertNull(f.service.diagnostics(unknown))
            }
            val key = f.create("printf 'CAPACITY_READY\n'; IFS= read -r stop")
            f.untilSnapshot(key) { it.rawTail.contains("CAPACITY_READY") }
            val rejected = f.service.create(f.request("touch forbidden-start; IFS= read -r stop"))
            assertIs<TerminalCreateResult.Rejected>(rejected)
            assertEquals(listOf(key), f.inventory().map { it.key })
            assertFalse(Files.exists(f.root.resolve("forbidden-start")))
        }
    }

    @Test
    fun realPtyAndResizeUpdateKernelAndEmulator() = runBlocking {
        withPty { f ->
            val key = f.create("test -t 0 && test -t 1 && test -t 2 || exit 71; " +
                "printf 'TTY_OK\n'; stty size; printf 'SIZE_READY\n'; IFS= read -r gate; " +
                "stty size; printf 'RESIZED_READY\n'; IFS= read -r stop")
            val first = f.untilSnapshot(key) { it.rawTail.contains("SIZE_READY") }
            assertTrue(first.rawTail.contains("TTY_OK"))
            assertTrue(first.rawTail.contains("24 80"), first.rawTail)
            assertEquals(TerminalSize(), first.size)
            val resized = TerminalSize(30, 100)
            assertEquals(resized, assertIs<TerminalMutationResult.Applied>(f.service.resize(key, resized)).size)
            assertIs<TerminalMutationResult.Applied>(f.service.write(key, "measure\n"))
            val last = f.untilSnapshot(key) { it.rawTail.contains("RESIZED_READY") }
            assertTrue(last.rawTail.contains("30 100"), last.rawTail)
            assertEquals(resized, last.size)
            assertEquals(30, last.screen.size)
        }
    }

    @Test
    fun realInputAndSplitUtf8RetainDecoderState() = runBlocking {
        withPty { f ->
            val key = f.create("printf 'BEFORE_UTF8'; printf '\\342\\202'; printf ready > utf8-phase; " +
                "IFS= read -r gate; printf '\\254\\360\\237\\231\\202\nUTF8_READY\n'; " +
                "IFS= read -r message; printf 'RECEIVED:%s\n' \"" + dollar + "message\"; IFS= read -r stop")
            f.until("child delivered only the first UTF-8 bytes") { Files.exists(f.root.resolve("utf8-phase")) }
            val before = f.snapshot(key)
            assertFalse(before.rawTail.contains("€"))
            assertIs<TerminalMutationResult.Applied>(f.service.write(key, "release\n"))
            val decoded = f.untilSnapshot(key) { it.rawTail.contains("UTF8_READY") }
            assertTrue(decoded.rawTail.contains("€🙂"), decoded.rawTail)
            val input = "π🙂\n"
            assertEquals(input.toByteArray(Charsets.UTF_8).size,
                assertIs<TerminalMutationResult.Applied>(f.service.write(key, input)).bytes)
            val echoed = f.untilSnapshot(key) { it.rawTail.contains("RECEIVED:π🙂") }
            assertFalse(echoed.rawTail.contains('\uFFFD'))
            assertWellFormed(echoed.rawTail)
        }
    }

    @Test
    fun splitAnsiKeepsParserAndSnapshotsDetached() = runBlocking {
        withPty { f ->
            val key = f.create("printf 'PLAIN\n\\033['; printf ready > ansi-phase; IFS= read -r gate; " +
                "printf '31mRED\\033[0m\nANSI_READY\n'; IFS= read -r stop")
            f.until("native half-escape delivered") { Files.exists(f.root.resolve("ansi-phase")) }
            val saved = f.untilSnapshot(key) { it.rawTail.endsWith("\u001b[") }
            val savedJson = Json.encodeToString(saved)
            val began = System.nanoTime()
            repeat(3) { assertEquals(savedJson, Json.encodeToString(f.snapshot(key))) }
            assertTrue(System.nanoTime() - began < TimeUnit.SECONDS.toNanos(1), "Half an ANSI sequence must not lock out snapshots")
            assertIs<TerminalMutationResult.Applied>(f.service.write(key, "continue\n"))
            val colored = f.untilSnapshot(key) { it.screen.any { line -> line.text.contains("ANSI_READY") } }
            assertTrue(colored.screen.flatMap { it.runs }.any { it.text.contains("RED") && it.style.foreground == TerminalColorValue.Indexed(1) })
            assertIs<TerminalMutationResult.Applied>(f.service.resize(key, TerminalSize(12, 40)))
            assertEquals(savedJson, Json.encodeToString(saved), "All nested snapshot values must be detached from JediTerm")
            assertFalse(saved.screen.any { it.text.contains("RED") })
        }
    }

    @Test
    fun carriageReturnCursorAndEraseUpdateScreen() = runBlocking {
        withPty { f ->
            val key = f.create("printf 'abcdef\rXY\\033[K\\033[2;3HZ\\033[?25l'; printf ready > cursor-phase; IFS= read -r stop")
            f.until("cursor script emitted") { Files.exists(f.root.resolve("cursor-phase")) }
            val snapshot = f.untilSnapshot(key) { !it.cursor.visible && it.screen[1].text.contains('Z') }
            assertEquals("XY", snapshot.screen[0].text.trimEnd())
            assertEquals("  Z", snapshot.screen[1].text.trimEnd())
            assertEquals(TerminalCursor(3, 1, false), snapshot.cursor)
            assertTrue(snapshot.rawTail.contains("abcdef"))
            assertFalse(snapshot.screen.any { it.text.contains("abcdef") })
        }
    }

    @Test
    fun alternateScreenRestoresPrimary() = runBlocking {
        withPty { f ->
            val key = f.create("printf 'PRIMARY'; IFS= read -r gate; printf '\\033[?1049hALT_SCREEN'; " +
                "IFS= read -r gate; printf '\\033[?1049l_RESTORED'; IFS= read -r stop")
            val primary = f.untilSnapshot(key) { it.screen.any { line -> line.text.contains("PRIMARY") } }
            assertFalse(primary.alternate)
            assertIs<TerminalMutationResult.Applied>(f.service.write(key, "alternate\n"))
            val alternate = f.untilSnapshot(key) { it.alternate && it.screen.any { line -> line.text.contains("ALT_SCREEN") } }
            assertFalse(alternate.screen.any { it.text.contains("PRIMARY") })
            assertIs<TerminalMutationResult.Applied>(f.service.write(key, "restore\n"))
            val restored = f.untilSnapshot(key) { !it.alternate && it.screen.any { line -> line.text.contains("PRIMARY_RESTORED") } }
            assertFalse(restored.screen.any { it.text.contains("ALT_SCREEN") })
            assertFalse(primary.alternate)
            assertTrue(alternate.alternate)
        }
    }

    @Test
    fun staleScopeSessionIncarnationAndTerminalHaveNoSideEffects() = runBlocking {
        withPty { f ->
            val key = f.create("printf 'IDENTITY_READY\n'; while IFS= read -r token; do printf '%s\n' \"" + dollar +
                "token\" >> received; printf 'INPUT_SEEN\n'; done")
            val before = f.untilSnapshot(key) { it.rawTail.contains("IDENTITY_READY") }
            val owner = f.service.owner
            val staleOwners = listOf(owner.copy(scope = TerminalScope("foreign-scope")),
                owner.copy(session = SessionId("foreign-session")), owner.copy(incarnation = TerminalIncarnation("foreign-incarnation")))
            for (stale in staleOwners) {
                val foreign = key.copy(owner = stale)
                assertEquals(stale, assertIs<TerminalCreateResult.Rejected>(f.service.create(f.request("touch forbidden-owner").copy(owner = stale))).owner)
                assertEquals(stale, assertIs<TerminalListResult.Rejected>(f.service.list(stale)).owner)
                assertEquals(foreign, assertIs<TerminalReadResult.Rejected>(f.service.read(foreign)).key)
                assertEquals(foreign, assertIs<TerminalMutationResult.Rejected>(f.service.write(foreign, "BAD\n")).key)
                assertEquals(foreign, assertIs<TerminalMutationResult.Rejected>(f.service.resize(foreign, TerminalSize(30, 100))).key)
                assertEquals(foreign, assertIs<TerminalMutationResult.Rejected>(f.service.close(foreign)).key)
                assertIs<TerminalShutdownResult.Rejected>(f.service.shutdown(stale))
                assertTrue(assertNotNull(f.service.diagnostics(key)?.process).isAlive)
                assertEquals(before.size, f.snapshot(key).size)
            }
            val missing = key.copy(terminal = TerminalSessionId("unknown-terminal"))
            assertIs<TerminalReadResult.NotFound>(f.service.read(missing))
            assertIs<TerminalMutationResult.NotFound>(f.service.write(missing, "BAD\n"))
            assertIs<TerminalMutationResult.NotFound>(f.service.resize(missing, TerminalSize(30, 100)))
            assertIs<TerminalMutationResult.NotFound>(f.service.close(missing))
            assertEquals(listOf(key), f.inventory().map { it.key })
            assertFalse(Files.exists(f.root.resolve("received")))
            assertFalse(Files.exists(f.root.resolve("forbidden-owner")))
            assertEquals(before.rawTail, f.snapshot(key).rawTail)
            assertIs<TerminalMutationResult.Applied>(f.service.write(key, "VALID\n"))
            f.untilSnapshot(key) { it.rawTail.contains("INPUT_SEEN") }
            assertEquals("VALID\n", Files.readString(f.root.resolve("received")))
        }
    }

    @Test
    fun writeFailureAfterDeliveryIsUnknownAndNeverResent() = runBlocking {
        withPty { f ->
            val key = f.create("printf 'WRITE_READY\n'; while IFS= read -r token; do printf '%s\n' \"" + dollar +
                "token\" >> delivered; printf 'DELIVERED\n'; done")
            f.untilSnapshot(key) { it.rawTail.contains("WRITE_READY") }
            var nativeWrites = 0
            f.service.afterNativeWrite = { nativeWrites++; throw IOException("Injected after the real native delivery") }
            assertIs<TerminalMutationResult.UnknownOutcome>(f.service.write(key, "ONCE\n"))
            f.untilSnapshot(key) { it.rawTail.contains("DELIVERED") }
            assertEquals("ONCE\n", Files.readString(f.root.resolve("delivered")))
            assertEquals(1, nativeWrites)
            assertIs<TerminalMutationResult.Rejected>(f.service.write(key, "ONCE\n"))
            assertIs<TerminalMutationResult.Rejected>(f.service.resize(key, TerminalSize(30, 100)))
            assertEquals(TerminalInputState.Unknown, f.snapshot(key).metadata.input)
            assertEquals(1, nativeWrites)
            assertEquals("ONCE\n", Files.readString(f.root.resolve("delivered")))
        }
    }

    @Test
    fun blockingReaderClosesAndOwnedWorkersFinish() = runBlocking {
        withPty { f ->
            val key = f.create("mkfifo owned-fifo; /bin/cat owned-fifo >/dev/null & printf 'IDLE_READY\n'; " +
                "IFS= read -r ping; printf 'RESPONSIVE_ACK\n'; IFS= read -r stop")
            f.untilSnapshot(key) { it.rawTail.contains("IDLE_READY") }
            f.until("owned blocked descendant observed") { f.service.diagnostics(key)?.descendants?.any { it.isAlive } == true }
            val owned = assertNotNull(f.service.diagnostics(key))
            assertTrue(owned.workers.any { it.name.endsWith("-parser") && it.isAlive })
            val began = System.nanoTime()
            f.snapshot(key)
            assertTrue(System.nanoTime() - began < TimeUnit.SECONDS.toNanos(1), "Idle PTY read must release the model gate")
            val callerThread = AtomicReference<Thread>()
            val callerExecutor = Executors.newSingleThreadExecutor { task ->
                Thread(task, "harness-test-pty-caller").also { callerThread.set(it) }
            }
            val callerDispatcher = callerExecutor.asCoroutineDispatcher()
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            // Hold only the return of an actual native write, never substitute its delivery.
            f.service.afterNativeWrite = { written ->
                if (written == key) {
                    entered.countDown()
                    check(release.await(10, TimeUnit.SECONDS)) { "Native write return was not released" }
                }
            }
            val delivered = CompletableDeferred<TerminalMutationResult>()
            val writer = launch(callerDispatcher) {
                try {
                    delivered.complete(f.service.write(key, "RESPOND\n"))
                } catch (error: Throwable) {
                    delivered.completeExceptionally(error)
                }
            }
            try {
                assertTrue(withContext(Dispatchers.IO) { entered.await(1, TimeUnit.SECONDS) }, "Real native write must begin")
                val probeThread = withTimeout(1_000) {
                    withContext(callerDispatcher) { Thread.currentThread() }
                }
                assertSame(callerThread.get(), probeThread, "The same single caller thread must run while native IO is pending")
                assertFalse(delivered.isCompleted, "The native completion latch still holds the write result")
                writer.cancel()
                release.countDown()
                val result = assertIs<TerminalMutationResult.UnknownOutcome>(withTimeout(1_000) { delivered.await() },
                    "Cancelling a caller after real delivery must still return the explicit unknown result")
                assertEquals(key, result.key)
                withTimeout(1_000) { writer.join() }
                f.untilSnapshot(key) { it.rawTail.contains("RESPONSIVE_ACK") }
                assertIs<TerminalMutationResult.Rejected>(f.service.write(key, "NO_RETRY\n"))
                assertIs<TerminalMutationResult.Applied>(withContext(callerDispatcher) { f.service.close(key) })
            } finally {
                release.countDown()
                f.service.afterNativeWrite = null
                writer.cancel()
                try {
                    withTimeout(10_000) { writer.join() }
                } finally {
                    callerDispatcher.close()
                    assertTrue(withContext(Dispatchers.IO) { callerExecutor.awaitTermination(10, TimeUnit.SECONDS) },
                        "The test-owned single caller executor must terminate")
                    assertFalse(callerThread.get()?.isAlive == true)
                }
            }
            assertFalse(assertNotNull(owned.process).isAlive)
            assertTrue(owned.descendants.none { it.isAlive })
            assertTrue(owned.workers.none { it.isAlive }, owned.workers.joinToString { it.name + ":" + it.state })
            assertIs<TerminalReadResult.NotFound>(f.service.read(key))
            assertTrue(f.inventory().isEmpty())
        }
    }

    @Test
    fun naturalExitKeepsFinalSnapshotUntilExplicitClose() = runBlocking {
        withPty(JvmPtyTerminalSessions.Limits(capacity = 1)) { f ->
            val key = f.create("printf 'FINAL_BEFORE_EXIT\n'; exit 7")
            val final = f.untilSnapshot(key) { it.metadata.lifecycle == TerminalLifecycle.Exited(7) }
            assertTrue(final.screen.any { it.text.contains("FINAL_BEFORE_EXIT") })
            assertEquals(TerminalInputState.Closed, final.metadata.input)
            assertEquals(final, f.snapshot(key))
            assertEquals(key, f.inventory().single().key)
            assertIs<TerminalCreateResult.Rejected>(f.service.create(f.request("touch forbidden-after-exit")))
            assertFalse(Files.exists(f.root.resolve("forbidden-after-exit")))
            assertIs<TerminalMutationResult.Rejected>(f.service.write(key, "late\n"))
            assertIs<TerminalMutationResult.Applied>(f.service.close(key))
            assertIs<TerminalReadResult.NotFound>(f.service.read(key))
            assertTrue(f.inventory().isEmpty())
        }
    }

    @Test
    fun scrollbackAndRawTailAreBounded() = runBlocking {
        withPty(JvmPtyTerminalSessions.Limits(historyLines = 6, rawTailChars = 80)) { f ->
            val lines = (1..160).joinToString("; ") { "printf 'LINE-" + it + "\n'" }
            val key = f.create("printf '\\033[31m'; " + lines +
                "; printf 'FINAL_\\342\\202\\254_\\360\\237\\231\\202'; IFS= read -r stop", TerminalSize(3, 40))
            val snapshot = f.untilSnapshot(key) { it.screen.any { line -> line.text.contains("FINAL_€_🙂") } }
            assertEquals(6, snapshot.history.size)
            assertEquals(3, snapshot.screen.size)
            assertTrue(snapshot.truncated)
            assertTrue(snapshot.rawTail.length <= 80)
            assertFalse(snapshot.rawTail.contains("\u001b[31m"), "Original ANSI prefix must already be outside the retained raw tail")
            assertTrue(snapshot.screen.flatMap { it.runs }.any {
                it.text.contains("FINAL_") && it.style.foreground == TerminalColorValue.Indexed(1)
            }, "Color persists from the complete original stream, without replaying the truncated tail")
            assertWellFormed(snapshot.rawTail)
            (snapshot.screen + snapshot.history).flatMap { it.runs }.forEach { assertWellFormed(it.text) }
        }
    }

    @Test
    fun invalidDimensionsInputAndCwdAreRejectedBeforeIo() = runBlocking {
        withPty(JvmPtyTerminalSessions.Limits(inputBytes = 4)) { f ->
            for ((rows, columns) in listOf(1 to 80, 201 to 80, 24 to 4, 24 to 501)) {
                assertFailsWith<IllegalArgumentException> { TerminalSize(rows, columns) }
            }
            val outside = Files.createDirectory(f.temp.resolve("outside"))
            val link = Files.createSymbolicLink(f.root.resolve("escape"), outside)
            val valid = f.request("touch forbidden-invalid; IFS= read -r stop")
            val invalid = listOf(valid.copy(argv = emptyList()), valid.copy(argv = listOf("/bin/sh\u0000")),
                valid.copy(environment = mapOf("BAD\u0000" to "x")), valid.copy(environment = mapOf("BAD=NAME" to "x")),
                valid.copy(environment = mapOf("VALUE" to "x\u0000")), valid.copy(cwd = "bad\u0000"),
                valid.copy(argv = listOf("/bin/sh", "x".repeat(65_537))), valid.copy(cwd = "../outside"),
                valid.copy(cwd = outside.toString()), valid.copy(cwd = link.toString()), valid.copy(cwd = "missing"))
            for (request in invalid) {
                assertIs<TerminalCreateResult.Rejected>(f.service.create(request))
                assertTrue(f.inventory().isEmpty())
            }
            assertFalse(Files.exists(f.root.resolve("forbidden-invalid")))
            val key = f.create("printf 'INPUT_LIMIT_READY\n'; IFS= read -r gate; touch accepted-input; IFS= read -r stop")
            f.untilSnapshot(key) { it.rawTail.contains("INPUT_LIMIT_READY") }
            for (input in listOf("\u0000", "12345", "\uD800")) {
                assertIs<TerminalMutationResult.Rejected>(f.service.write(key, input))
                assertFalse(Files.exists(f.root.resolve("accepted-input")))
                assertTrue(assertNotNull(f.service.diagnostics(key)?.process).isAlive)
            }
            assertIs<TerminalMutationResult.Applied>(f.service.write(key, "OK\n"))
            f.until("valid input reached the real child") { Files.exists(f.root.resolve("accepted-input")) }
        }
    }

    private suspend fun withPty(
        limits: JvmPtyTerminalSessions.Limits = JvmPtyTerminalSessions.Limits(),
        block: suspend (Fixture) -> Unit,
    ) {
        val temp = Files.createTempDirectory("harness-owned-pty-")
        val root = Files.createDirectory(temp.resolve("session"))
        val fixture = Fixture(temp, root, JvmPtyTerminalSessions(TerminalScope("test-scope"), SessionId("test-session"), root, limits))
        try { block(fixture) } finally {
            val diagnostics = fixture.inventory().mapNotNull { fixture.service.diagnostics(it.key) }
            assertIs<TerminalShutdownResult.Applied>(fixture.service.shutdown(fixture.service.owner), "Every test must confirm cleanup of its real PTYs")
            diagnostics.forEach { owned ->
                assertTrue(owned.workers.none { it.isAlive })
                assertFalse(owned.process?.isAlive == true)
                assertTrue(owned.descendants.none { it.isAlive })
            }
            Files.walk(temp).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.delete(it) } }
        }
    }

    private class Fixture(val temp: Path, val root: Path, val service: JvmPtyTerminalSessions) {
        private val home = Files.createDirectory(root.resolve("home"))
        fun request(script: String, size: TerminalSize = TerminalSize()) = TerminalCreateRequest(service.owner,
            listOf("/bin/sh", "-c", "stty -echo; " + script),
            mapOf("HOME" to home.toString(), "PATH" to "/usr/bin:/bin", "TERM" to "xterm-256color", "LC_ALL" to "C"), size, root.toString())
        suspend fun create(script: String, size: TerminalSize = TerminalSize()): TerminalKey =
            assertIs<TerminalCreateResult.Created>(service.create(request(script, size))).metadata.key
        suspend fun inventory(): List<TerminalMetadata> = assertIs<TerminalListResult.Available>(service.list(service.owner)).terminals
        suspend fun snapshot(key: TerminalKey): TerminalSnapshot = assertIs<TerminalReadResult.Available>(service.read(key)).snapshot
        suspend fun until(label: String, predicate: suspend () -> Boolean) {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            while (!predicate()) {
                assertTrue(System.nanoTime() < deadline, "Deadline waiting for " + label)
                delay(5)
            }
        }
        suspend fun untilSnapshot(key: TerminalKey, predicate: (TerminalSnapshot) -> Boolean): TerminalSnapshot {
            var latest = snapshot(key)
            until("PTY snapshot predicate; latest=" + latest.rawTail) {
                latest = snapshot(key)
                predicate(latest)
            }
            return latest
        }
    }

    private fun assertWellFormed(text: String) {
        var index = 0
        while (index < text.length) {
            val char = text[index++]
            assertFalse(char.isLowSurrogate(), "Unpaired low surrogate")
            if (char.isHighSurrogate()) {
                assertTrue(index < text.length && text[index++].isLowSurrogate(), "Unpaired high surrogate")
            }
        }
    }
}
