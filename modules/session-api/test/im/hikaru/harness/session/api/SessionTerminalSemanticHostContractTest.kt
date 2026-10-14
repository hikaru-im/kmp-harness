package im.hikaru.harness.session.api

import im.hikaru.harness.agent.AgentOptions
import im.hikaru.harness.agent.AgentPlugin
import im.hikaru.harness.llm.Message
import im.hikaru.harness.runtime.Runtime
import im.hikaru.harness.session.CreateSessionOptions
import im.hikaru.harness.session.SessionId
import im.hikaru.harness.session.SessionPlugin
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SessionTerminalSemanticHostContractTest {
    private val key = TerminalKey(TerminalOwner(TerminalScope("semantic-host"), SessionId("session"),
        TerminalIncarnation("instance")), TerminalSessionId("terminal"))

    @Test
    fun legacyHostAndSessionApiSemanticInputAreTypedUnavailable() = runBlocking<Unit> {
        val host = LegacyHost()
        val api = LegacyApi()
        for (input in variants()) {
            assertEquals(key, assertIs<TerminalMutationResult.Unavailable>(host.input(key, input)).key)
            assertEquals(key, assertIs<TerminalMutationResult.Unavailable>(api.inputTerminal(key, input)).key)
        }
    }

    @Test
    fun defaultSessionApiForwardsSemanticInputExactlyOnce() = runBlocking<Unit> {
        val runtime = Runtime()
        try {
            runtime.install(SessionPlugin())
            runtime.install(AgentPlugin())
            runtime.install(SessionApiPlugin())
            val api = runtime.context.sessionApi
            for (input in variants()) {
                assertEquals(key, assertIs<TerminalMutationResult.Unavailable>(api.inputTerminal(key, input)).key)
            }
            val calls = mutableListOf<Pair<TerminalKey, TerminalInput>>()
            var reply: TerminalMutationResult = TerminalMutationResult.Applied(key, 19)
            val host = object : SessionTerminalHostService by LegacyHost() {
                override suspend fun input(key: TerminalKey, input: TerminalInput): TerminalMutationResult {
                    calls += key to input
                    return reply
                }
            }
            val registration = runtime.provide(SessionTerminalHostKey, host)
            for (input in variants()) for (result in results()) {
                reply = result
                val before = calls.size
                assertEquals(result, api.inputTerminal(key, input))
                assertEquals(before + 1, calls.size)
                assertEquals(key to input, calls.last())
                assertEquals(key, result.key)
            }
            assertEquals(15, calls.size)
            registration.dispose()
            assertEquals(key, assertIs<TerminalMutationResult.Unavailable>(api.inputTerminal(key, variants().first())).key)
            assertEquals(15, calls.size)
        } finally { withContext(NonCancellable) { withTimeout(10_000) { runtime.context.dispose() } } }
    }

    @Test
    fun semanticEndpointPayloadAndCapturedModifiersRetainIdentity() = runBlocking<Unit> {
        assertEquals(key, Json.decodeFromString<TerminalKey>(Json.encodeToString(key)))
        assertEquals(31, TerminalKeyName.entries.size)
        assertEquals(4, TerminalModifier.entries.size)
        val source = TerminalModifier.entries.toMutableSet()
        val captured = TerminalInput.KeyPress(TerminalKeyName.Up, source)
        val wire = Json.encodeToString<TerminalInput>(captured)
        source.clear()
        assertEquals(TerminalModifier.entries.toSet(), captured.modifiers)
        assertEquals(wire, Json.encodeToString<TerminalInput>(captured))
        for (input in variants() + TerminalKeyName.entries.map {
            TerminalInput.KeyPress(it, TerminalModifier.entries.toSet(), if (it == TerminalKeyName.Character) "~" else null)
        }) {
            val decoded = Json.decodeFromString<TerminalInput>(Json.encodeToString(input))
            assertEquals(input, decoded)
            assertEquals(input.hashCode(), decoded.hashCode())
        }
        assertTrue(Json.encodeToString<TerminalInput>(TerminalInput.Text("中文🙂")).contains("text"))
        assertTrue(Json.encodeToString<TerminalInput>(TerminalInput.Paste("π\r\n🙂")).contains("paste"))
        assertTrue(wire.contains("key_press"))
        assertFailsWith<IllegalArgumentException> { TerminalInput.KeyPress(TerminalKeyName.Character) }
        for (text in listOf("", "ab", "\n", "中", "\u007f")) {
            assertFailsWith<IllegalArgumentException> { TerminalInput.KeyPress(TerminalKeyName.Character, character = text) }
        }
        assertFailsWith<IllegalArgumentException> { TerminalInput.KeyPress(TerminalKeyName.Enter, character = "a") }
    }

    private fun variants(): List<TerminalInput> = listOf(TerminalInput.Text("你好🙂"),
        TerminalInput.Paste("π\r\n🙂"), TerminalInput.KeyPress(TerminalKeyName.Up, setOf(TerminalModifier.Control)))

    private fun results(): List<TerminalMutationResult> = listOf(TerminalMutationResult.Applied(key, 19),
        TerminalMutationResult.Rejected(key, "known"), TerminalMutationResult.NotFound(key),
        TerminalMutationResult.Unavailable(key, "missing"), TerminalMutationResult.UnknownOutcome(key, "lost"))

    private class LegacyHost : SessionTerminalHostService {
        override suspend fun discover(session: SessionId): HostTerminalDiscoveryResult = error("Unexpected discover")
        override suspend fun create(request: TerminalCreateRequest): HostTerminalCreateResult = error("Unexpected create")
        override suspend fun list(owner: TerminalOwner): TerminalListResult = error("Unexpected list")
        override suspend fun read(key: TerminalKey): TerminalReadResult = error("Unexpected read")
        override suspend fun write(key: TerminalKey, input: String): TerminalMutationResult = error("Unexpected raw write")
        override suspend fun resize(key: TerminalKey, size: TerminalSize): TerminalMutationResult = error("Unexpected resize")
        override suspend fun close(key: TerminalKey): TerminalMutationResult = error("Unexpected close")
    }

    private class LegacyApi : SessionApi {
        override suspend fun list(): List<SessionSummary> = error("Unexpected list")
        override suspend fun create(id: SessionId?, options: CreateSessionOptions, agentOptions: AgentOptions): SessionSummary = error("Unexpected create")
        override suspend fun history(id: SessionId): List<Message> = error("Unexpected history")
        override suspend fun prompt(id: SessionId, message: Message): SessionPrompt = error("Unexpected prompt")
        override suspend fun cancel(id: SessionId, keepInbox: Boolean) = error("Unexpected cancel")
    }
}
