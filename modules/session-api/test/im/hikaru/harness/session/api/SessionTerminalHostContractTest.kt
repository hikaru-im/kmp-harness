package im.hikaru.harness.session.api

import im.hikaru.harness.agent.AgentOptions
import im.hikaru.harness.llm.Message
import im.hikaru.harness.session.CreateSessionOptions
import im.hikaru.harness.session.SessionId
import kotlin.test.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SessionTerminalHostContractTest {
    private val owner = TerminalOwner(TerminalScope("host"), SessionId("session"), TerminalIncarnation("instance"))
    private val key = TerminalKey(owner, TerminalSessionId("terminal"))
    private val metadata = TerminalMetadata(key, "/session", listOf("/bin/sh", "-c", "printf tail"),
        TerminalLifecycle.Exited(7), TerminalInputState.Closed)

    @Test
    fun legacySessionApiReturnsTypedUnavailableWithoutInventingOwner() = runTest {
        // Implements only the pre-PTY interface. Optional methods must remain source compatible.
        val legacy = object : SessionApi {
            override suspend fun list(): List<SessionSummary> = emptyList()
            override suspend fun create(id: SessionId?, options: CreateSessionOptions, agentOptions: AgentOptions): SessionSummary =
                error("Legacy create must not be called by terminal discovery")
            override suspend fun history(id: SessionId): List<Message> = emptyList()
            override suspend fun prompt(id: SessionId, message: Message): SessionPrompt = error("No model request")
            override suspend fun cancel(id: SessionId, keepInbox: Boolean) = Unit
        }
        val requested = SessionId("not-created")
        val capability = assertIs<HostTerminalDiscoveryResult.Unavailable>(legacy.terminalCapability(requested))
        assertEquals(requested, capability.session)
        assertEquals(capability, Json.decodeFromString<HostTerminalDiscoveryResult>(Json.encodeToString<HostTerminalDiscoveryResult>(capability)))
        val create = assertIs<HostTerminalCreateResult.Unavailable>(legacy.createTerminal(
            TerminalCreateRequest(owner, listOf("/bin/sh"), emptyMap())))
        assertEquals(owner, create.owner)
        assertEquals(owner, assertIs<TerminalListResult.Unavailable>(legacy.terminals(owner)).owner)
        assertEquals(key, assertIs<TerminalReadResult.Unavailable>(legacy.readTerminal(key)).key)
        assertEquals(key, assertIs<TerminalMutationResult.Unavailable>(legacy.writeTerminal(key, "hi\n")).key)
        assertEquals(key, assertIs<TerminalMutationResult.Unavailable>(legacy.resizeTerminal(key, TerminalSize(30, 100))).key)
        assertEquals(key, assertIs<TerminalMutationResult.Unavailable>(legacy.closeTerminal(key)).key)
        assertTrue(legacy.list().isEmpty())
    }

    @Test
    fun hostCreateUnknownRetainsOptionalKeyAndSerializes() {
        val results: List<HostTerminalCreateResult> = listOf(
            HostTerminalCreateResult.Created(owner, metadata),
            HostTerminalCreateResult.Rejected(owner, "before dispatch"),
            HostTerminalCreateResult.Unavailable(owner, "no service"),
            HostTerminalCreateResult.UnknownOutcome(owner, null, "lost create acknowledgement"),
            HostTerminalCreateResult.UnknownOutcome(owner, key, "known reservation"),
        )
        results.forEach { result ->
            assertEquals(owner, result.owner)
            assertEquals(result, Json.decodeFromString<HostTerminalCreateResult>(Json.encodeToString(result)))
        }
        assertNull((results[3] as HostTerminalCreateResult.UnknownOutcome).key)
        assertEquals(key, (results[4] as HostTerminalCreateResult.UnknownOutcome).key)
    }

    @Test
    fun capabilityAndCreateResultsRejectIdentityMismatch() {
        val changed = owner.copy(incarnation = TerminalIncarnation("replacement"))
        val foreignSession = owner.copy(session = SessionId("foreign"))
        assertFailsWith<IllegalArgumentException> { HostTerminalDiscoveryResult.Available(owner.session, foreignSession, "/session", 1) }
        assertFailsWith<IllegalArgumentException> { HostTerminalDiscoveryResult.Available(owner.session, owner, " ", 1) }
        assertFailsWith<IllegalArgumentException> { HostTerminalDiscoveryResult.Available(owner.session, owner, "/session", 0) }
        assertFailsWith<IllegalArgumentException> { HostTerminalCreateResult.Created(changed, metadata) }
        assertFailsWith<IllegalArgumentException> { HostTerminalCreateResult.UnknownOutcome(changed, key, "mismatched key") }
        val discovery: List<HostTerminalDiscoveryResult> = listOf(
            HostTerminalDiscoveryResult.Available(owner.session, owner, "/session", 8),
            HostTerminalDiscoveryResult.Rejected(foreignSession.session, "not live"),
            HostTerminalDiscoveryResult.Unavailable(foreignSession.session, "unsupported platform"),
        )
        discovery.forEach { assertEquals(it, Json.decodeFromString<HostTerminalDiscoveryResult>(Json.encodeToString(it))) }
        assertEquals(foreignSession.session, discovery[1].session)
        assertEquals(foreignSession.session, discovery[2].session)
    }

    @Test
    fun hostResultsPreserveFullTerminalSnapshotIdentity() {
        val style = TerminalTextStyle(TerminalColorValue.Indexed(9), TerminalColorValue.Rgb(10, 20, 30),
            setOf(TerminalTextAttribute.Bold, TerminalTextAttribute.Italic, TerminalTextAttribute.Underlined))
        val snapshot = TerminalSnapshot(metadata, TerminalSize(2, 12),
            listOf(TerminalLineSnapshot(listOf(TerminalTextRun(2, 4, "tail", style)), wrapped = true), TerminalLineSnapshot(emptyList())),
            listOf(TerminalLineSnapshot(listOf(TerminalTextRun(0, 3, "old", TerminalTextStyle())))),
            TerminalCursor(6, 0, false), true, 42, "\u001b[31mtail\r\n", true)
        val read: TerminalReadResult = TerminalReadResult.Available(key, snapshot)
        val decoded = assertIs<TerminalReadResult.Available>(Json.decodeFromString<TerminalReadResult>(Json.encodeToString(read)))
        assertEquals(read, decoded)
        assertEquals(owner, decoded.snapshot.metadata.key.owner)
        assertEquals(metadata.argv, decoded.snapshot.metadata.argv)
        assertEquals(TerminalLifecycle.Exited(7), decoded.snapshot.metadata.lifecycle)
        assertEquals(TerminalInputState.Closed, decoded.snapshot.metadata.input)
        assertEquals(style, decoded.snapshot.screen.first().runs.single().style)
        assertEquals(2, decoded.snapshot.screen.first().runs.single().column)
        assertEquals(4, decoded.snapshot.screen.first().runs.single().span)
        assertTrue(decoded.snapshot.screen.first().wrapped)
        assertEquals(snapshot.history, decoded.snapshot.history)
        assertEquals(snapshot.cursor, decoded.snapshot.cursor)
        assertTrue(decoded.snapshot.alternate)
        assertEquals(42L, decoded.snapshot.revision)
        assertEquals(snapshot.rawTail, decoded.snapshot.rawTail)
        assertTrue(decoded.snapshot.truncated)
        val list: TerminalListResult = TerminalListResult.Available(owner, listOf(metadata))
        assertEquals(list, Json.decodeFromString<TerminalListResult>(Json.encodeToString(list)))
        val create: HostTerminalCreateResult = HostTerminalCreateResult.Created(owner, metadata)
        assertEquals(create, Json.decodeFromString<HostTerminalCreateResult>(Json.encodeToString(create)))
    }
}
