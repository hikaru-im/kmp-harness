package im.hikaru.harness.session.api

import im.hikaru.harness.session.SessionId
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlin.test.*

class SessionTerminalsContractTest {
    private val owner = TerminalOwner(TerminalScope("scope"), SessionId("thread"), TerminalIncarnation("instance"))
    private val key = TerminalKey(owner, TerminalSessionId("terminal"))
    private val metadata = TerminalMetadata(key, "/session", listOf("/bin/sh"), TerminalLifecycle.Exited(7), TerminalInputState.Closed)
    private fun snapshot() = TerminalSnapshot(metadata, TerminalSize(2, 5), listOf(
        TerminalLineSnapshot(listOf(TerminalTextRun(0, 2, "hi", TerminalTextStyle(
            TerminalColorValue.Indexed(3), TerminalColorValue.Rgb(1, 2, 3), setOf(TerminalTextAttribute.Bold))))),
        TerminalLineSnapshot(emptyList()),
    ), emptyList(), TerminalCursor(2, 0, false), false, 9, "\u001b[31mhi", true)

    @Test
    fun blankIdentityComponentsAreRejected() {
        for (text in listOf("", " ", "\n")) {
            assertFailsWith<IllegalArgumentException> { TerminalScope(text) }
            assertFailsWith<IllegalArgumentException> { TerminalIncarnation(text) }
            assertFailsWith<IllegalArgumentException> { TerminalSessionId(text) }
            assertFailsWith<IllegalArgumentException> { SessionId(text) }
        }
    }

    @Test
    fun terminalDtosRoundTripWithCompleteIdentity() {
        val original = snapshot()
        assertEquals(original, Json.decodeFromString<TerminalSnapshot>(Json.encodeToString(original)))
        val request = TerminalCreateRequest(owner, listOf("/bin/sh", "-c", "printf hi"), mapOf("TERM" to "xterm"))
        assertEquals(request, Json.decodeFromString<TerminalCreateRequest>(Json.encodeToString(request)))
        val result: TerminalReadResult = TerminalReadResult.Available(key, original)
        assertEquals(result, Json.decodeFromString<TerminalReadResult>(Json.encodeToString(result)))
        assertEquals("hi", original.screen.first().text)
        assertEquals(TerminalColorValue.Default, TerminalTextStyle().foreground)
        assertNotEquals(owner, owner.copy(incarnation = TerminalIncarnation("replacement")))
    }

    @Test
    fun snapshotAndSizeInvariantsAreEnforced() {
        for (size in listOf(1 to 80, 24 to 4, 201 to 80, 24 to 501)) {
            assertFailsWith<IllegalArgumentException> { TerminalSize(size.first, size.second) }
        }
        val original = snapshot()
        assertFailsWith<IllegalArgumentException> { original.copy(screen = emptyList()) }
        assertFailsWith<IllegalArgumentException> { original.copy(revision = -1) }
        assertFailsWith<IllegalArgumentException> { original.copy(history = List(2_001) { TerminalLineSnapshot(emptyList()) }) }
        assertFailsWith<IllegalArgumentException> { original.copy(cursor = TerminalCursor(6, 0, true)) }
        assertFailsWith<IllegalArgumentException> { original.copy(cursor = TerminalCursor(0, 2, true)) }
        assertFailsWith<IllegalArgumentException> { original.copy(rawTail = "x".repeat(262_145)) }
        assertFailsWith<IllegalArgumentException> { original.copy(screen = listOf(
            TerminalLineSnapshot(listOf(TerminalTextRun(0, 6, "abcdef", TerminalTextStyle()))), TerminalLineSnapshot(emptyList()))) }
        assertFailsWith<IllegalArgumentException> { original.copy(rawTail = "\uDC00") }
        assertFailsWith<IllegalArgumentException> { original.copy(rawTail = "\uD800") }
        assertFailsWith<IllegalArgumentException> { TerminalColorValue.Indexed(256) }
        assertFailsWith<IllegalArgumentException> { TerminalColorValue.Rgb(-1, 0, 0) }
        assertFailsWith<IllegalArgumentException> { TerminalTextRun(-1, 1, "a", TerminalTextStyle()) }
        assertFailsWith<IllegalArgumentException> { TerminalLineSnapshot(listOf(
            TerminalTextRun(0, 3, "abc", TerminalTextStyle()), TerminalTextRun(2, 1, "x", TerminalTextStyle()))) }
        assertEquals(5, original.copy(cursor = TerminalCursor(5, 0, true)).cursor.column)
    }

    @Test
    fun resultsRetainRequestedOwnerAndTerminalIdentity() {
        val foreign = owner.copy(scope = TerminalScope("foreign"))
        val requested = key.copy(owner = foreign)
        val creates: List<TerminalCreateResult> = listOf(TerminalCreateResult.Rejected(foreign, "stale"),
            TerminalCreateResult.Unavailable(foreign, "platform"), TerminalCreateResult.UnknownOutcome(foreign, requested, "cleanup"))
        creates.forEach { assertEquals(it, Json.decodeFromString<TerminalCreateResult>(Json.encodeToString(it))); assertEquals(foreign, it.owner) }
        val created: TerminalCreateResult = TerminalCreateResult.Created(owner, metadata)
        assertEquals(created, Json.decodeFromString<TerminalCreateResult>(Json.encodeToString(created)))
        val listed: TerminalListResult = TerminalListResult.Available(owner, listOf(metadata))
        assertEquals(listed, Json.decodeFromString<TerminalListResult>(Json.encodeToString(listed)))
        val reads: List<TerminalReadResult> = listOf(TerminalReadResult.NotFound(requested),
            TerminalReadResult.Rejected(requested, "stale"), TerminalReadResult.Unavailable(requested, "platform"))
        reads.forEach { assertEquals(requested, it.key); assertEquals(it, Json.decodeFromString<TerminalReadResult>(Json.encodeToString(it))) }
        val mutations: List<TerminalMutationResult> = listOf(TerminalMutationResult.Applied(requested, 3),
            TerminalMutationResult.NotFound(requested), TerminalMutationResult.Rejected(requested, "stale"),
            TerminalMutationResult.Unavailable(requested, "platform"), TerminalMutationResult.UnknownOutcome(requested, "delivery"))
        mutations.forEach { assertEquals(requested, it.key); assertEquals(it, Json.decodeFromString<TerminalMutationResult>(Json.encodeToString(it))) }
        assertEquals(foreign, TerminalListResult.Rejected(foreign, "stale").owner)
        assertEquals(foreign, TerminalShutdownResult.Rejected(foreign, "stale").owner)
        val shutdowns: List<TerminalShutdownResult> = listOf(TerminalShutdownResult.Applied(foreign),
            TerminalShutdownResult.Rejected(foreign, "stale"), TerminalShutdownResult.UnknownOutcome(foreign, listOf(requested)))
        shutdowns.forEach { assertEquals(it, Json.decodeFromString<TerminalShutdownResult>(Json.encodeToString(it))) }
        assertFailsWith<IllegalArgumentException> { TerminalCreateResult.Created(foreign, metadata) }
        assertFailsWith<IllegalArgumentException> { TerminalReadResult.Available(requested, snapshot()) }
        assertFailsWith<IllegalArgumentException> { TerminalListResult.Available(foreign, listOf(metadata)) }
    }
}
