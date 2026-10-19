package im.hikaru.harness.desktop.connection

import im.hikaru.contracts.common.ApiResult
import im.hikaru.contracts.harness.handshake.HandshakeRequest
import im.hikaru.contracts.harness.handshake.HandshakeResponse
import im.hikaru.contracts.harness.identity.HostDescription
import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.contracts.harness.protocol.ProtocolVersion
import im.hikaru.contracts.harness.relay.HarnessWebSocketMessageTypes
import im.hikaru.contracts.harness.relay.RelayRequest
import im.hikaru.contracts.harness.relay.RelayResponse
import im.hikaru.contracts.websocket.WebSocketMessage
import im.hikaru.harness.agent.Agent
import im.hikaru.harness.agent.AgentFactory
import im.hikaru.harness.agent.AgentFactoryRequest
import im.hikaru.harness.agent.AgentHandle
import im.hikaru.harness.agent.AgentId
import im.hikaru.harness.agent.AgentPlugin
import im.hikaru.harness.agent.AgentSendTarget
import im.hikaru.harness.agent.AgentStatus
import im.hikaru.harness.agent.AgentWakeup
import im.hikaru.harness.agent.agents
import im.hikaru.harness.agent.AgentOptions as CoreAgentOptions
import im.hikaru.harness.api.gateway.ApiGateway
import im.hikaru.harness.api.gateway.GatewayResultCodes
import im.hikaru.harness.api.gateway.session.*
import im.hikaru.harness.client.connection.*
import im.hikaru.harness.exec.jvm.SubprocessPlugin
import im.hikaru.harness.llm.Message as CoreMessage
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.Runtime
import im.hikaru.harness.session.SessionPlugin
import im.hikaru.harness.session.CreateSessionOptions as CoreCreateSessionOptions
import im.hikaru.harness.session.api.SessionApi as CoreSessionApi
import im.hikaru.harness.session.api.SessionApiKey
import im.hikaru.harness.session.api.SessionApiPlugin
import im.hikaru.harness.session.api.TerminalKey as CoreKey
import im.hikaru.harness.session.api.TerminalMutationResult as CoreMutation
import im.hikaru.harness.tools.ToolsPlugin
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngineBase
import io.ktor.client.engine.HttpClientEngineCapability
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.callContext
import io.ktor.client.plugins.websocket.WebSocketCapability
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.Headers
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.utils.io.InternalAPI
import io.ktor.util.date.GMTDate
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketExtension
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.readText
import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.coroutines.CoroutineContext
import kotlin.test.*

import im.hikaru.harness.exec.jvm.SubprocessConfig
import im.hikaru.harness.exec.jvm.JvmPtyTerminalSessions
import im.hikaru.harness.exec.jvm.JvmSessionTerminalHost
import im.hikaru.harness.session.sessions
import im.hikaru.harness.session.api.SessionTerminalHostKey
import im.hikaru.harness.session.api.TerminalInput as CoreInput
import im.hikaru.harness.session.api.TerminalKeyName as CoreKeyName
import im.hikaru.harness.session.api.TerminalModifier as CoreModifier
import java.io.IOException
import java.nio.charset.StandardCharsets.UTF_8
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real cases use production registration, DefaultSessionApi, Host and native PTY delivery. */
class SessionTerminalSemanticInputConnectionTest {
    private val json = Json { encodeDefaults = true }
    private val key = TerminalKey(TerminalOwner(TerminalScope("scope"), SessionId("session"),
        TerminalIncarnation("incarnation")), TerminalSessionId("terminal"))

    @Test
    fun portableSemanticDtosMapBothWaysWithoutPlatformObjects() = runBlocking<Unit> {
        val names = listOf(
            TerminalKeyName.Enter to CoreKeyName.Enter, TerminalKeyName.Escape to CoreKeyName.Escape,
            TerminalKeyName.Tab to CoreKeyName.Tab, TerminalKeyName.Backspace to CoreKeyName.Backspace,
            TerminalKeyName.Up to CoreKeyName.Up, TerminalKeyName.Down to CoreKeyName.Down,
            TerminalKeyName.Left to CoreKeyName.Left, TerminalKeyName.Right to CoreKeyName.Right,
            TerminalKeyName.Home to CoreKeyName.Home, TerminalKeyName.End to CoreKeyName.End,
            TerminalKeyName.Insert to CoreKeyName.Insert, TerminalKeyName.Delete to CoreKeyName.Delete,
            TerminalKeyName.PageUp to CoreKeyName.PageUp, TerminalKeyName.PageDown to CoreKeyName.PageDown,
            TerminalKeyName.F1 to CoreKeyName.F1, TerminalKeyName.F2 to CoreKeyName.F2,
            TerminalKeyName.F3 to CoreKeyName.F3, TerminalKeyName.F4 to CoreKeyName.F4,
            TerminalKeyName.F5 to CoreKeyName.F5, TerminalKeyName.F6 to CoreKeyName.F6,
            TerminalKeyName.F7 to CoreKeyName.F7, TerminalKeyName.F8 to CoreKeyName.F8,
            TerminalKeyName.F9 to CoreKeyName.F9, TerminalKeyName.F10 to CoreKeyName.F10,
            TerminalKeyName.F11 to CoreKeyName.F11, TerminalKeyName.F12 to CoreKeyName.F12,
            TerminalKeyName.KeypadUp to CoreKeyName.KeypadUp, TerminalKeyName.KeypadDown to CoreKeyName.KeypadDown,
            TerminalKeyName.KeypadLeft to CoreKeyName.KeypadLeft, TerminalKeyName.KeypadRight to CoreKeyName.KeypadRight,
            TerminalKeyName.Character to CoreKeyName.Character,
        )
        val modifiers = listOf(TerminalModifier.Shift to CoreModifier.Shift, TerminalModifier.Alt to CoreModifier.Alt,
            TerminalModifier.Control to CoreModifier.Control, TerminalModifier.Meta to CoreModifier.Meta)
        assertEquals(31, names.size)
        assertEquals(TerminalKeyName.entries.toSet(), names.map { it.first }.toSet())
        assertEquals(CoreKeyName.entries.toSet(), names.map { it.second }.toSet())
        assertEquals(4, modifiers.size)
        assertEquals(TerminalModifier.entries.toSet(), modifiers.map { it.first }.toSet())
        assertEquals(CoreModifier.entries.toSet(), modifiers.map { it.second }.toSet())
        for ((client, core) in names) { assertEquals(core, client.toCore()); assertEquals(client, core.toClient()) }
        for ((client, core) in modifiers) { assertEquals(core, client.toCore()); assertEquals(client, core.toClient()) }
        val inputs = mutableListOf<TerminalInput>(TerminalInput.Text("π中文🙂"), TerminalInput.Paste("a\r\nb\nc"))
        for ((client, core) in names) {
            val sets = listOf(emptyList<Pair<TerminalModifier, CoreModifier>>(), modifiers) + modifiers.map { listOf(it) }
            for (set in sets) {
                val character = if (client == TerminalKeyName.Character) "~" else null
                val input = TerminalInput.KeyPress(client, set.map { it.first }.toSet(), character)
                val expected = CoreInput.KeyPress(core, set.map { it.second }.toSet(), character)
                assertEquals(expected, input.toCore())
                assertEquals(input, expected.toClient())
                inputs += input
            }
        }
        for (input in inputs) {
            val core = input.toCore()
            assertEquals(input, core.toClient())
            assertNotEquals(input.javaClass.name, core.javaClass.name)
            assertEquals(input, json.decodeFromString<TerminalInput>(json.encodeToString(input)))
            assertEquals(core, json.decodeFromString<CoreInput>(json.encodeToString(core)))
            assertEquals(json.encodeToString(core), json.encodeToString(input))
            val request = SessionTerminalInputRequest(key.toCore(), core)
            assertEquals(request, json.decodeFromString<SessionTerminalInputRequest>(json.encodeToString(request)))
            assertEquals(key, request.key.toClient())
            assertPortable(input)
        }
        assertTrue(json.encodeToString<TerminalInput>(TerminalInput.Text("π")).contains("\"type\":\"text\""))
        assertTrue(json.encodeToString<TerminalInput>(TerminalInput.Paste("π")).contains("\"type\":\"paste\""))
        assertTrue(json.encodeToString<TerminalInput>(TerminalInput.KeyPress(TerminalKeyName.Enter)).contains("\"type\":\"key_press\""))
        for (bad in listOf<String?>(null, "", "ab", "π", "🙂", "\u001f", "\u007f"))
            assertFailsWith<IllegalArgumentException> { TerminalInput.KeyPress(TerminalKeyName.Character, character = bad) }
        for (name in TerminalKeyName.entries.filter { it != TerminalKeyName.Character })
            assertFailsWith<IllegalArgumentException> { TerminalInput.KeyPress(name, character = "A") }
        for (character in listOf(" ", "~")) assertEquals(character,
            TerminalInput.KeyPress(TerminalKeyName.Character, character = character).character)
        val source = modifiers.map { it.first }.toMutableSet()
        val captured = TerminalInput.KeyPress(TerminalKeyName.Up, source)
        source.clear()
        assertEquals(TerminalModifier.entries.toSet(), captured.modifiers)
        val frozenCore = assertIs<CoreInput.KeyPress>(captured.toCore())
        val frozenClient = frozenCore.toClient()
        @Suppress("UNCHECKED_CAST")
        (captured.modifiers as MutableSet<TerminalModifier>).clear()
        assertEquals(CoreModifier.entries.toSet(), frozenCore.modifiers)
        @Suppress("UNCHECKED_CAST")
        (frozenCore.modifiers as MutableSet<CoreModifier>).clear()
        assertEquals(TerminalModifier.entries.toSet(), assertIs<TerminalInput.KeyPress>(frozenClient).modifiers)
        assertPortable(key)
    }

    @Test
    fun localSemanticInputOwnsRealPtyEndToEnd() = runBlocking<Unit> {
        withFixture(inputBytes = 16) { f ->
            val phases = listOf(Phase("\u001b[?1l", TerminalInput.Text("π中🙂"), "π中🙂"),
                Phase("\u001b[?2004h", TerminalInput.Paste("a\r\nb"), "\u001b[200~a\rb\u001b[201~"),
                Phase("\u001b[?1h", TerminalInput.KeyPress(TerminalKeyName.Up), "\u001bOA"),
                Phase("\u001b[?1h", TerminalInput.KeyPress(TerminalKeyName.Up, setOf(TerminalModifier.Control)), "\u001b[1;5A"),
                Phase("\u001b[?1l", TerminalInput.KeyPress(TerminalKeyName.Up), "\u001b[A"),
                Phase("", TerminalInput.Text("OK"), "OK"))
            val capability = f.discover()
            assertEquals(f.cwd.toRealPath().toString(), capability.canonicalCreationCwd)
            assertTrue(assertIs<TerminalListResult.Available>(f.api.terminals(capability.owner)).terminals.isEmpty())
            assertTrue(f.pids().isEmpty())
            val capture = f.capture(phases, capability.owner)
            for (index in phases.indices) {
                f.ready(capture, index)
                if (index == 1) for (denied in listOf<TerminalInput>(TerminalInput.Text(""), TerminalInput.Text("\u0000"),
                    TerminalInput.Text("x".repeat(17)), TerminalInput.Paste("abcde"),
                    TerminalInput.KeyPress(TerminalKeyName.Up, setOf(TerminalModifier.Meta)))) {
                    val before = f.writes.get()
                    assertEquals(capture.key, assertIs<TerminalMutationResult.Rejected>(f.api.inputTerminal(capture.key, denied)).key)
                    assertEquals(before, f.writes.get())
                    assertEquals(TerminalInputState.Open, f.snapshot(capture.key).metadata.input)
                }
                f.deliver(capture, index)
            }
            f.finish(capture)
            assertEquals(6, f.writes.get())
            assertEquals(1, f.pids().size)
            assertEquals(TerminalInputState.Open, f.snapshot(capture.key).metadata.input)
        }
    }

    @Test
    fun localSemanticRejectsStaleFullIdentityBeforeWrite() = runBlocking<Unit> {
        withFixture { f ->
            val phase = Phase("", TerminalInput.Text("VALID"), "VALID")
            val capture = f.capture(listOf(phase))
            f.ready(capture, 0)
            for (requested in foreignKeys(capture.key)) for (input in variants()) {
                val result = f.api.inputTerminal(requested, input)
                if (requested.owner == capture.key.owner) assertIs<TerminalMutationResult.NotFound>(result)
                else assertIs<TerminalMutationResult.Rejected>(result)
                assertEquals(requested, result.key)
            }
            assertEquals(0, f.writes.get())
            assertEquals(1, f.pids().size)
            assertEquals(0L, Files.size(f.binary(capture, 0)))
            assertEquals(listOf(capture.key), assertIs<TerminalListResult.Available>(f.api.terminals(capture.key.owner)).terminals.map { it.key })
            val old = f.handle.agent.session
            bounded { f.runtime.context.sessions.attach(old).dispose() }
            val replacement = f.runtime.context.sessions.create(id = old.id, options = CoreCreateSessionOptions(cwd = f.cwd.toString()))
            assertEquals(old.id, replacement.session.id)
            assertNotSame(old, replacement.session)
            assertSame(f.handle.agent, f.runtime.context.agents.get(f.handle.agent.id))
            assertEquals(capture.key, assertIs<TerminalMutationResult.Rejected>(f.api.inputTerminal(capture.key, phase.input)).key)
            assertEquals(0, f.writes.get())
            assertEquals(1, f.pids().size)
            bounded { f.handle.dispose() }
            assertSame(replacement.session, f.runtime.context.sessions.get(old.id))
            val fresh = f.runtime.context.agents.restore(replacement.session, CoreAgentOptions(cwd = f.cwd.toString()))
            assertSame(replacement.session, fresh.agent.session)
            val owner = f.discover().owner
            assertEquals(capture.key.owner.scope, owner.scope)
            assertEquals(capture.key.owner.session, owner.session)
            assertNotEquals(capture.key.owner.incarnation, owner.incarnation)
            assertEquals(capture.key, assertIs<TerminalMutationResult.Rejected>(f.api.inputTerminal(capture.key, phase.input)).key)
            val next = f.capture(listOf(phase), owner)
            f.deliver(next, 0); f.finish(next)
            assertEquals(1, f.writes.get())
            assertEquals(2, f.pids().size)
        }
        withGateway { runtime, gateway, connection ->
            var calls = 0
            var reply: CoreMutation = CoreMutation.Applied(key.toCore(), 23)
            gateway.register(runtime.context, SessionTerminalInputEndpoint) { request ->
                calls++
                assertEquals(key.toCore(), request.key)
                assertEquals(CoreInput.Text("ONCE"), request.input)
                ApiResult(ApiResult.SUCCESS_CODE, "", reply)
            }
            for (foreign in foreignKeys(key)) for (result in mutations(foreign)) {
                reply = result.toCore()
                val before = calls
                val actual = assertIs<TerminalMutationResult.UnknownOutcome>(connection.session.inputTerminal(key, TerminalInput.Text("ONCE")))
                assertEquals(key, actual.key)
                assertFalse(actual.reason.contains("private"))
                assertEquals(before + 1, calls)
            }
            assertEquals(20, calls)
        }
    }

    @Test
    fun localSemanticNativeFailureFencesWithoutRetry() = runBlocking<Unit> {
        withFixture { f ->
            val capture = f.capture(listOf(Phase("", TerminalInput.Text("ONCE🙂"), "ONCE🙂")))
            f.ready(capture, 0)
            f.afterWrite { written ->
                assertEquals(capture.key.toCore(), written)
                f.writes.incrementAndGet()
                throw IOException("Native acknowledgement failed after real delivery")
            }
            val result = assertIs<TerminalMutationResult.UnknownOutcome>(f.api.inputTerminal(capture.key, capture.phases[0].input))
            assertEquals(capture.key, result.key)
            assertFalse(result.reason.contains("private"))
            f.expect(capture, 0); f.finish(capture)
            assertEquals(TerminalInputState.Unknown, f.snapshot(capture.key).metadata.input)
            assertEquals(capture.key, assertIs<TerminalMutationResult.Rejected>(f.api.inputTerminal(capture.key, TerminalInput.Text("RETRY"))).key)
            assertEquals(capture.key, assertIs<TerminalMutationResult.Rejected>(f.api.writeTerminal(capture.key, "RAW_RETRY")).key)
            assertEquals(capture.key, assertIs<TerminalMutationResult.Rejected>(f.api.resizeTerminal(capture.key, TerminalSize(30, 100))).key)
            assertEquals(1, f.writes.get())
            assertContentEquals("ONCE🙂".toByteArray(UTF_8), Files.readAllBytes(f.binary(capture, 0)))
            assertEquals(0L, Files.size(f.extra(capture)))
        }
    }

    @Test
    fun localSemanticCancellationAndLostAcknowledgementRemainUnknown() = runBlocking<Unit> {
        withFixture { f ->
            val capture = f.capture(listOf(Phase("", TerminalInput.Text("ONCE"), "ONCE"), Phase("", TerminalInput.Text("OPEN"), "OPEN")))
            f.ready(capture, 0)
            coroutineScope {
                val observed = CompletableDeferred<Result<TerminalMutationResult>>()
                val cancelled = launch(Dispatchers.Default) {
                    coroutineContext[Job]!!.cancel()
                    observed.complete(runCatching { f.api.inputTerminal(capture.key, TerminalInput.Text("BEFORE")) })
                }
                assertIs<CancellationException>(bounded { observed.await() }.exceptionOrNull())
                bounded { cancelled.join() }
                assertEquals(0, f.writes.get())
            }
            val entered = CountDownLatch(1)
            val release = CountDownLatch(1)
            f.afterWrite { written ->
                assertEquals(capture.key.toCore(), written)
                f.writes.incrementAndGet()
                entered.countDown()
                assertTrue(release.await(10, TimeUnit.SECONDS))
            }
            coroutineScope {
                val observed = CompletableDeferred<Result<TerminalMutationResult>>()
                val writer = launch(Dispatchers.Default) {
                    observed.complete(runCatching { f.api.inputTerminal(capture.key, capture.phases[0].input) })
                }
                try {
                    awaitLatch(entered)
                    writer.cancel()
                    release.countDown()
                    assertEquals(capture.key, assertIs<TerminalMutationResult.UnknownOutcome>(bounded { observed.await() }.getOrThrow()).key)
                    bounded { writer.join() }
                    assertTrue(writer.isCancelled)
                    f.expect(capture, 0)
                } finally {
                    release.countDown()
                    withContext(NonCancellable) { bounded { writer.join() } }
                    f.countWrites()
                }
            }
            // Outer Local/Gateway acknowledgement loss under NonCancellable leaves native input Open.
            assertEquals(TerminalInputState.Open, f.snapshot(capture.key).metadata.input)
            f.deliver(capture, 1); f.finish(capture)
            assertEquals(2, f.writes.get())
        }
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val received = CopyOnWriteArrayList<CoreInput>()
        withFixture(decorate = { real ->
            object : CoreSessionApi by real {
                override suspend fun inputTerminal(key: CoreKey, input: CoreInput): CoreMutation {
                    received += input
                    if (received.size == 1) {
                        entered.complete(Unit)
                        release.await()
                        val applied = assertIs<CoreMutation.Applied>(real.inputTerminal(key, input))
                        assertEquals(6, applied.bytes)
                        throw IOException("private service acknowledgement lost after delivery")
                    }
                    return real.inputTerminal(key, input)
                }
            }
        }) { f ->
            val original = mutableSetOf(TerminalModifier.Control, TerminalModifier.Shift)
            val input = TerminalInput.KeyPress(TerminalKeyName.Up, original)
            val expected = CoreInput.KeyPress(CoreKeyName.Up, setOf(CoreModifier.Control, CoreModifier.Shift))
            val capture = f.capture(listOf(Phase("\u001b[?1h", input, "\u001b[1;6A"), Phase("", TerminalInput.Text("OPEN"), "OPEN")))
            f.ready(capture, 0)
            coroutineScope {
                val writer = async(Dispatchers.Default) { f.api.inputTerminal(capture.key, input) }
                try {
                    bounded { entered.await() }
                    original.clear()
                    @Suppress("UNCHECKED_CAST")
                    (input.modifiers as MutableSet<TerminalModifier>).clear()
                    assertEquals(listOf(expected), received.toList())
                    release.complete(Unit)
                    val result = assertIs<TerminalMutationResult.UnknownOutcome>(bounded { writer.await() })
                    assertEquals(capture.key, result.key)
                    assertFalse(result.reason.contains("private"))
                    f.expect(capture, 0)
                } finally {
                    release.complete(Unit)
                    withContext(NonCancellable) { bounded { writer.join() } }
                }
            }
            assertEquals(1, f.writes.get())
            assertEquals(1, received.size)
            assertEquals(TerminalInputState.Open, f.snapshot(capture.key).metadata.input)
            f.deliver(capture, 1); f.finish(capture)
            assertEquals(2, f.writes.get())
            assertEquals(2, received.size)
        }
    }

    @Test
    fun missingEndpointAndRemoteSemanticDefaultsRemainUnavailable() = runBlocking<Unit> {
        withGateway { runtime, gateway, connection ->
            for (input in variants()) assertEquals(key,
                assertIs<TerminalMutationResult.Unavailable>(connection.session.inputTerminal(key, input)).key)
            val replies = listOf<ApiResult<CoreMutation>>(
                ApiResult(GatewayResultCodes.NotFound, "Harness API endpoint 'session.terminal.input' 未注册"),
                ApiResult(GatewayResultCodes.NotFound, "Harness API endpoint 'session.terminal.input' 未注册 "),
                ApiResult(GatewayResultCodes.InternalError, "Harness API endpoint 'session.terminal.input' 未注册"),
                ApiResult(ApiResult.SUCCESS_CODE, "", null),
                ApiResult(GatewayResultCodes.InternalError, "private failure"),
            )
            for ((index, reply) in replies.withIndex()) {
                var calls = 0
                val registration = gateway.register(runtime.context, SessionTerminalInputEndpoint) { calls++; reply }
                try {
                    val actual = connection.session.inputTerminal(key, TerminalInput.Text("ONCE"))
                    if (index == 0) assertIs<TerminalMutationResult.Unavailable>(actual)
                    else assertIs<TerminalMutationResult.UnknownOutcome>(actual).also { assertFalse(it.reason.contains("private")) }
                    assertEquals(key, actual.key)
                    assertEquals(1, calls)
                } finally { registration.dispose() }
            }
            var calls = 0
            gateway.register(runtime.context, SessionTerminalInputEndpoint) {
                calls++
                throw IOException("private endpoint exception")
            }
            val actual = assertIs<TerminalMutationResult.UnknownOutcome>(connection.session.inputTerminal(key, TerminalInput.Text("ONCE")))
            assertEquals(key, actual.key)
            assertFalse(actual.reason.contains("private"))
            assertEquals(1, calls)
        }
        assertRemoteDefaultsWithoutNetwork()
    }

    private fun variants(): List<TerminalInput> = listOf(TerminalInput.Text("π中文🙂"), TerminalInput.Paste("a\r\nb"),
        TerminalInput.KeyPress(TerminalKeyName.Enter))
    private fun mutations(key: TerminalKey): List<TerminalMutationResult> = listOf(TerminalMutationResult.Applied(key, 23, TerminalSize(30, 100)),
        TerminalMutationResult.Rejected(key, "private rejection"), TerminalMutationResult.NotFound(key),
        TerminalMutationResult.Unavailable(key, "private unavailable"), TerminalMutationResult.UnknownOutcome(key, "private unknown"))
    private fun foreignKeys(key: TerminalKey) = listOf(key.copy(owner = key.owner.copy(scope = TerminalScope("foreign"))),
        key.copy(owner = key.owner.copy(session = SessionId("foreign"))),
        key.copy(owner = key.owner.copy(incarnation = TerminalIncarnation("foreign"))), key.copy(terminal = TerminalSessionId("foreign")))

    private fun assertPortable(value: Any?) {
        val children: List<Any?> = when (value) {
            null, is String -> return
            is Set<*> -> value.toList()
            is SessionId -> listOf(value.value)
            is TerminalScope -> listOf(value.value)
            is TerminalIncarnation -> listOf(value.value)
            is TerminalSessionId -> listOf(value.value)
            is TerminalOwner -> listOf(value.scope, value.session, value.incarnation)
            is TerminalKey -> listOf(value.owner, value.terminal)
            is TerminalKeyName, is TerminalModifier -> emptyList()
            is TerminalInput.Text -> listOf(value.text)
            is TerminalInput.Paste -> listOf(value.text)
            is TerminalInput.KeyPress -> listOf(value.key, value.modifiers, value.character)
            else -> fail("Unsupported portable leaf: " + value.javaClass.name)
        }
        if (value !is Set<*>) assertTrue(assertNotNull(value).javaClass.name.startsWith("im.hikaru.harness.client.connection."))
        children.forEach(::assertPortable)
    }

    private suspend fun withGateway(body: suspend (Runtime, ApiGateway, LocalConnection) -> Unit) {
        val runtime = Runtime()
        val gateway = ApiGateway()
        try { body(runtime, gateway, LocalConnection(gateway)) }
        finally { withContext(NonCancellable) { bounded { runtime.context.dispose() } } }
    }

    /** Public RemoteConnection.connect with a channel-only engine: no socket, server or production API change. */
    @OptIn(InternalAPI::class)
    private suspend fun assertRemoteDefaultsWithoutNetwork() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val host = HostDescription(ProtocolVersion.Current, HostId("local-test-memory-relay"), "Memory relay")
        val methods = CopyOnWriteArrayList<String>()
        val connections = AtomicInteger()
        val raw = AtomicReference<MemoryWebSocket>()
        val engine = object : HttpClientEngineBase("local-terminal-defaults-memory") {
            override val config = HttpClientEngineConfig()
            override val supportedCapabilities: Set<HttpClientEngineCapability<*>> = setOf(WebSocketCapability)
            override suspend fun execute(data: HttpRequestData): HttpResponseData {
                assertEquals(1, connections.incrementAndGet())
                assertEquals("memory-relay.invalid", data.url.host)
                assertEquals("/ws", data.url.encodedPath)
                val context = callContext()
                val socket = MemoryWebSocket(context).also(raw::set)
                scope.launch {
                    withTimeout(10_000) {
                        for (frame in socket.outgoing) {
                            if (frame is Frame.Close) { socket.incoming.send(frame); break }
                            if (frame !is Frame.Text) continue
                            val outer = json.decodeFromString<WebSocketMessage>(frame.readText())
                            assertEquals(HarnessWebSocketMessageTypes.RelayRequest, outer.type)
                            val request = json.decodeFromString<RelayRequest>(outer.content)
                            assertEquals(host.hostId, request.hostId)
                            methods += request.method
                            val payload = when (request.method) {
                                "host.describe" -> json.encodeToJsonElement(host)
                                "host.handshake" -> {
                                    val handshake = json.decodeFromJsonElement(HandshakeRequest.serializer(), assertNotNull(request.payload))
                                    assertEquals("terminal-default-test-client", handshake.client.clientId.value)
                                    json.encodeToJsonElement(HandshakeResponse(host, ProtocolVersion.Current))
                                }
                                else -> fail("Terminal defaults must never dispatch ${request.method}")
                            }
                            val response = RelayResponse(request.requestId, host.hostId, result = ApiResult(ApiResult.SUCCESS_CODE, "", payload))
                            socket.incoming.send(Frame.Text(json.encodeToString(WebSocketMessage(
                                HarnessWebSocketMessageTypes.RelayResponse, json.encodeToString(response)))))
                        }
                    }
                }
                return HttpResponseData(HttpStatusCode.SwitchingProtocols, GMTDate(), Headers.Empty,
                    HttpProtocolVersion.HTTP_1_1, socket, context)
            }
        }
        val client = HttpClient(engine) { install(WebSockets) }
        var remote: RemoteConnection? = null
        try {
            val connected = withTimeout(10_000) {
                RemoteConnection.connect(client, "ws://memory-relay.invalid", "controlled-token", 1, host,
                    "terminal-default-test-client", timeoutMillis = 10_000, scope = scope)
            }
            remote = connected
            assertEquals(listOf("host.describe", "host.handshake"), methods.toList())
            for (input in variants()) {
                val result = assertIs<TerminalMutationResult.Unavailable>(connected.session.inputTerminal(key, input))
                assertEquals(key, result.key)
                assertFalse(result.reason.contains("private"))
            }
            assertEquals(listOf("host.describe", "host.handshake"), methods.toList(), "No terminal relay request")
            assertEquals(1, connections.get())
        } finally {
            withContext(NonCancellable) {
                withTimeout(10_000) {
                    try {
                        remote?.close()
                        raw.get()?.dispose()
                        scope.coroutineContext[Job]!!.cancelAndJoin()
                    } finally {
                        try {
                            client.close()
                            client.coroutineContext[Job]!!.cancelAndJoin()
                        } finally {
                            engine.close()
                            engine.coroutineContext[Job]!!.cancelAndJoin()
                        }
                    }
                }
            }
        }
    }

    private class MemoryWebSocket(override val coroutineContext: CoroutineContext) : WebSocketSession {
        override var masking = false
        override var maxFrameSize = 262_144L
        override val incoming = Channel<Frame>(8)
        override val outgoing = Channel<Frame>(8)
        override val extensions: List<WebSocketExtension<*>> = emptyList()
        override suspend fun flush() = Unit
        @Suppress("OVERRIDE_DEPRECATION")
        override fun terminate() = dispose()
        fun dispose() {
            incoming.cancel(); outgoing.cancel(); coroutineContext[Job]?.cancel()
        }
    }

    private data class Phase(val modes: String, val input: TerminalInput, val expected: String)
    private data class Capture(val key: TerminalKey, val token: String, val phases: List<Phase>)
    private class Owned(val process: ProcessHandle, val key: TerminalKey) {
        val workers = mutableSetOf<Thread>()
        val descendants = mutableSetOf<ProcessHandle>()
    }

    private suspend fun withFixture(inputBytes: Int = 65_536,
        decorate: (CoreSessionApi) -> CoreSessionApi = { it }, body: suspend (Fixture) -> Unit) {
        val cwd = Files.createTempDirectory("harness-semantic-local-chain-").toRealPath()
        val runtime = Runtime()
        var fixture: Fixture? = null
        try {
            runtime.install(SessionPlugin())
            runtime.install(AgentPlugin())
            runtime.install(ToolsPlugin())
            runtime.context.agents.registerFactory(AgentFactory { ControlledAgent(it) })
            runtime.install(SubprocessPlugin(SubprocessConfig(terminalLimits = JvmPtyTerminalSessions.Limits(
                inputBytes = inputBytes, ioWaitMillis = 10_000))))
            runtime.install(SessionApiPlugin())
            val handle = runtime.context.agents.create(AgentId("owner"), CoreAgentOptions(cwd = cwd.toString()))
            val gateway = ApiGateway()
            val real = runtime.context.require(SessionApiKey)
            val service = decorate(real)
            val owner = if (service === real) runtime.context else runtime.context.isolate(SessionApiKey).also {
                it.provide(SessionApiKey, service)
            }
            gateway.registerSessionApi(owner)
            val f = Fixture(runtime, cwd, handle, LocalConnection(gateway))
            fixture = f
            body(f)
        } finally {
            withContext(NonCancellable) {
                try {
                    fixture?.recordOwned()
                } finally {
                    try {
                        bounded { runtime.context.dispose() }
                    } finally {
                        try { fixture?.assertAllStopped() }
                        finally { Files.walk(cwd).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } } }
                    }
                }
            }
        }
    }

    private class Fixture(val runtime: Runtime, val cwd: Path, val handle: AgentHandle, val connection: LocalConnection) {
        val api = connection.session
        private val host = runtime.context.require(SessionTerminalHostKey) as JvmSessionTerminalHost
        val writes = AtomicInteger()
        private val observed = linkedMapOf<TerminalKey, Owned>()
        private val tracked = linkedMapOf<TerminalKey, String>()
        private val home = Files.createDirectory(cwd.resolve("home"))
        init { countWrites() }
        // The existing Host seam runs after the real native write. It never substitutes a transport.
        fun afterWrite(hook: (CoreKey) -> Unit) {
            val field = JvmSessionTerminalHost::class.java.getDeclaredField("afterNativeWrite")
            field.isAccessible = true
            field.set(host, hook)
        }
        fun countWrites() { afterWrite { writes.incrementAndGet() } }
        private fun quote(text: String) = "'" + text.replace("'", "'\"'\"'") + "'"
        private fun display(text: String) = "printf '%s' " + quote("\u001b[2J\u001b[H" + text)
        fun binary(capture: Capture, index: Int): Path = cwd.resolve(capture.token + "_" + index + ".bin")
        fun extra(capture: Capture): Path = cwd.resolve(capture.token + "_extra.bin")
        fun pids(): List<Long> = if (Files.exists(cwd.resolve("started-pids")))
            Files.readAllLines(cwd.resolve("started-pids")).filter { it.isNotBlank() }.map { it.toLong() } else emptyList()
        suspend fun discover() = assertIs<TerminalDiscoveryResult.Available>(api.terminalCapability(SessionId(handle.agent.session.id.value)))

        suspend fun capture(phases: List<Phase>, owner: TerminalOwner? = null): Capture {
            val requested = owner ?: discover().owner
            val token = "LS_" + UUID.randomUUID().toString().replace("-", "")
            val dollar = 36.toChar()
            val script = buildString {
                append("test -t 0 && test -t 1 && test -t 2 || exit 70; stty raw -echo || exit 71; ")
                append("printf '%s\n' \"").append(dollar).append(dollar).append("\" >> started-pids; ")
                append("printf '%s\n' \"").append(dollar).append(dollar).append("\" > ").append(token).append(".pid; ")
                for ((index, phase) in phases.withIndex()) {
                    append("printf '%s' ").append(quote(phase.modes)).append("; ")
                    append(display(token + "_READY_" + index)).append("; ")
                    val file = token + "_" + index + ".bin"
                    append("dd bs=1 count=").append(phase.expected.toByteArray(UTF_8).size)
                        .append(" of=").append(file).append(" 2>/dev/null || exit 72; ")
                    append("od -An -v -tx1 ").append(file).append(" > ").append(file).append(".hex || exit 73; ")
                }
                append(display(token + "_DRAIN")).append("; dd bs=1 of=").append(token).append("_extra.bin 2>/dev/null; exit 0")
            }
            val result = assertIs<TerminalCreateResult.Created>(api.createTerminal(TerminalCreateRequest(requested,
                listOf("/bin/sh", "-c", script), mapOf("HOME" to home.toString(), "PATH" to "/usr/bin:/bin",
                    "TERM" to "xterm-256color", "LC_ALL" to "C"), cwd = cwd.toString())))
            assertEquals(requested, result.owner)
            assertEquals(requested, result.metadata.key.owner)
            val key = result.metadata.key
            tracked[key] = token
            recordOwned()
            return Capture(key, token, phases)
        }

        suspend fun ready(capture: Capture, index: Int) {
            until("parser screen marker") { snapshot(capture.key).screen.any { it.text.contains(capture.token + "_READY_" + index) } }
            until("owned PID, io/parser/waiter Threads and native dd descendant") {
                recordOwned()
                observed[capture.key]?.let { owned -> Files.exists(binary(capture, index)) && owned.process.isAlive &&
                    owned.workers.map { it.name.substringAfterLast('-') }.containsAll(listOf("io", "parser", "waiter")) &&
                    owned.descendants.any { it.isAlive } } == true
            }
            val owned = assertNotNull(observed[capture.key])
            assertTrue(owned.process.isAlive)
            assertTrue(owned.workers.isNotEmpty())
            assertTrue(owned.descendants.any { it.isAlive })
        }
        suspend fun deliver(capture: Capture, index: Int) {
            ready(capture, index)
            val phase = capture.phases[index]
            val before = writes.get()
            val applied = assertIs<TerminalMutationResult.Applied>(api.inputTerminal(capture.key, phase.input))
            assertEquals(capture.key, applied.key)
            assertEquals(phase.expected.toByteArray(UTF_8).size, applied.bytes)
            assertEquals(before + 1, writes.get())
            expect(capture, index)
        }
        suspend fun expect(capture: Capture, index: Int) {
            val expected = capture.phases[index].expected.toByteArray(UTF_8)
            val file = binary(capture, index)
            val hex = Path.of(file.toString() + ".hex")
            val expectedHex = expected.joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
            until("native dd/od capture") { Files.exists(file) && Files.size(file) == expected.size.toLong() &&
                Files.exists(hex) && Files.readString(hex).filterNot { it.isWhitespace() } == expectedHex }
            assertContentEquals(expected, Files.readAllBytes(file))
            assertEquals(expectedHex, Files.readString(hex).filterNot { it.isWhitespace() })
        }
        suspend fun finish(capture: Capture) {
            until("parser drain and extra collector") { snapshot(capture.key).screen.any { it.text.contains(capture.token + "_DRAIN") } &&
                Files.exists(extra(capture)) }
            assertEquals(0L, Files.size(extra(capture)))
            recordOwned()
        }
        suspend fun snapshot(key: TerminalKey) = assertIs<TerminalReadResult.Available>(api.readTerminal(key)).snapshot
        fun recordOwned() {
            for ((key, token) in tracked) {
                val pidFile = cwd.resolve(token + ".pid")
                if (!Files.exists(pidFile)) continue
                val text = Files.readString(pidFile).trim()
                if (text.isEmpty()) continue
                val process = ProcessHandle.of(text.toLong()).orElse(null) ?: continue
                val owned = observed.getOrPut(key) { Owned(process, key) }
                owned.workers += workers(key)
                process.descendants().use { handles -> handles.forEach { owned.descendants += it } }
            }
        }
        suspend fun assertAllStopped() {
            for (owned in observed.values) {
                until("actual owned PID, descendants and Thread objects stopped") {
                    !owned.process.isAlive && owned.descendants.none { it.isAlive } &&
                        owned.workers.none { it.isAlive } && workers(owned.key).none { it.isAlive }
                }
                assertFalse(owned.process.isAlive)
                assertTrue(owned.descendants.none { it.isAlive })
                assertTrue(owned.workers.isNotEmpty())
                assertTrue(owned.workers.all { it.state == Thread.State.TERMINATED })
            }
            for (key in tracked.keys) until("all tracked native workers stopped") { workers(key).none { it.isAlive } }
            until("all independently recorded PID exits") { pids().none { ProcessHandle.of(it).map { process -> process.isAlive }.orElse(false) } }
        }
        private fun workers(key: TerminalKey): Set<Thread> {
            val prefix = "harness-pty-" + key.terminal.value + "-"
            return Thread.getAllStackTraces().keys.filter { it.name.startsWith(prefix) }.toSet()
        }
        private suspend fun until(label: String, predicate: suspend () -> Boolean) {
            val deadline = System.nanoTime() + 10_000_000_000L
            withTimeout(10_000) {
                while (!predicate()) {
                    assertTrue(System.nanoTime() < deadline, "Timed out waiting for " + label)
                    delay(5)
                }
            }
        }
    }

    private class ControlledAgent(request: AgentFactoryRequest) : Agent {
        override val id = request.id
        override val options = request.options
        override val session = request.session
        override val inbox = request.inbox
        override val context: Context = request.context
        override val status = AgentStatus.IDLE
        override suspend fun awaitIdle() = Unit
        override suspend fun cancel(cause: Throwable?, keepInbox: Boolean) = Unit
        override suspend fun send(message: CoreMessage, target: AgentSendTarget, wakeup: AgentWakeup): String = inbox.insert(message, target, wakeup)
    }

    private suspend fun awaitLatch(latch: CountDownLatch) {
        assertTrue(withContext(Dispatchers.IO) { latch.await(10, TimeUnit.SECONDS) })
    }
    private suspend fun <T> bounded(body: suspend () -> T): T = withTimeout(10_000) { body() }
}
