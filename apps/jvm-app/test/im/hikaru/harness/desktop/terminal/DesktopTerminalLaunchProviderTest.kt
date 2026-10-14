package im.hikaru.harness.desktop.terminal

import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.harness.api.gateway.ApiGateway
import im.hikaru.harness.client.connection.*
import im.hikaru.harness.client.conversation.ConversationHostScope
import im.hikaru.harness.client.panels.*
import im.hikaru.harness.desktop.connection.LocalConnection
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import java.nio.file.Path
import im.hikaru.contracts.common.ApiResult
import im.hikaru.contracts.harness.handshake.HandshakeRequest
import im.hikaru.contracts.harness.handshake.HandshakeResponse
import im.hikaru.contracts.harness.identity.HostDescription
import im.hikaru.contracts.harness.protocol.ProtocolVersion
import im.hikaru.contracts.harness.relay.HarnessWebSocketMessageTypes
import im.hikaru.contracts.harness.relay.RelayRequest
import im.hikaru.contracts.harness.relay.RelayResponse
import im.hikaru.contracts.websocket.WebSocketMessage
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

class DesktopTerminalLaunchProviderTest {
    private val host = HostId("launch-provider-test")
    private val local = LocalConnection(ApiGateway())
    private val session = SessionId("launch-session")
    private val owner = ThreadPanelsOwner(local, ConversationHostScope(host, 1, null), session)
    private val discovery = TerminalDiscoveryResult.Available(session,
        TerminalOwner(TerminalScope("launch-scope"), session, TerminalIncarnation("launch-incarnation")),
        Path.of("/tmp").toRealPath().toString(), 8)

    @Test fun realLocalBindingAndExecutableShellPrepareWithoutSpawn() {
        val before = ProcessHandle.current().children().use { it.map { p -> p.pid() }.toList().toSet() }
        val provider = DesktopTerminalLaunchProvider(host, local)
        assertNull(provider.availability(owner))
        val ready = assertIs<TerminalLaunchPreparation.Ready>(provider.prepare(owner, discovery))
        val request = ready.plan.request(TerminalSize(20, 90))
        assertEquals(discovery.owner, request.owner)
        assertEquals(discovery.canonicalCreationCwd, request.cwd)
        assertEquals(2, request.argv.size)
        assertEquals("-i", request.argv[1])
        val shell = Path.of(request.argv[0])
        assertTrue(shell.isAbsolute && Files.isRegularFile(shell) && Files.isExecutable(shell))
        assertEquals(TerminalSize(20, 90), request.size)
        assertEquals(before, ProcessHandle.current().children().use { it.map { p -> p.pid() }.toList().toSet() })
        assertEquals("TerminalLaunchPlan(redacted)", ready.plan.toString())
    }

    @Test fun shellFallbackAndEnvironmentAreCapturedWithoutDisclosure() {
        val secret = "synthetic-secret-only"
        val environment = mutableMapOf("SHELL" to "relative-shell", "PRIVATE" to secret)
        val checked = mutableListOf<String>()
        val provider = DesktopTerminalLaunchProvider(host, local, { environment }, { path ->
            checked += path; path == "/bin/sh"
        }, "Mac OS X", "x86_64")
        val ready = assertIs<TerminalLaunchPreparation.Ready>(provider.prepare(owner, discovery))
        assertEquals(listOf("/bin/zsh", "/bin/sh"), checked)
        environment["PRIVATE"] = "changed-after-preparation"
        val first = ready.plan.request(TerminalSize())
        assertTrue(first.environment["PRIVATE"] == secret, "Prepared environment is frozen")
        assertEquals(listOf("/bin/sh", "-i"), first.argv)
        assertTrue(first.environment["PATH"] == "/usr/bin:/bin:/usr/sbin:/sbin")
        assertTrue(first.environment["TERM"] == "xterm-256color")
        assertTrue(first.environment["COLORTERM"] == "truecolor")
        (first.environment as? MutableMap)?.clear()
        assertTrue(ready.plan.request(TerminalSize()).environment["PRIVATE"] == secret)
        assertFalse(ready.toString().contains(secret))
        assertFalse(ready.plan.toString().contains(secret))
        val invalidShell = DesktopTerminalLaunchProvider(host, local, { mapOf("SHELL" to "/invalid-shell") },
            { path -> if (path == "/invalid-shell") error(secret) else path == "/bin/sh" }, "Mac OS X", "x86_64")
        val fallback = assertIs<TerminalLaunchPreparation.Ready>(invalidShell.prepare(owner, discovery))
        assertEquals(listOf("/bin/sh", "-i"), fallback.plan.request(TerminalSize()).argv)
        val broken = DesktopTerminalLaunchProvider(host, local, { error(secret) }, osName = "Mac OS X", osArch = "amd64")
        assertFalse(assertIs<TerminalLaunchPreparation.Unavailable>(broken.prepare(owner, discovery)).reason.contains(secret))
    }

    @Test fun foreignConnectionRelayAndUnsupportedPlatformAreUnavailable() = runBlocking<Unit> {
        val provider = DesktopTerminalLaunchProvider(host, local, osName = "Mac OS X", osArch = "x86_64")
        assertIs<TerminalLaunchPreparation.Unavailable>(provider.prepare(owner.copy(connection = LocalConnection(ApiGateway())), discovery))
        assertIs<TerminalLaunchPreparation.Unavailable>(provider.prepare(owner.copy(host = ConversationHostScope(HostId("foreign"), 1, null)), discovery))
        for ((os, arch) in listOf("Linux" to "x86_64", "Mac OS X" to "aarch64")) {
            val unsupported = DesktopTerminalLaunchProvider(host, local, osName = os, osArch = arch)
            assertIs<TerminalLaunchPreparation.Unavailable>(unsupported.prepare(owner, discovery))
        }
        assertMemoryRelayRejected { remote ->
            assertIs<TerminalLaunchPreparation.Unavailable>(provider.prepare(owner.copy(connection = remote), discovery))
        }
    }
}

/** Public RemoteConnection.connect with a channel-only engine: no socket, server or production API change. */
@OptIn(InternalAPI::class)
private suspend fun assertMemoryRelayRejected(body: (RemoteConnection) -> Unit) {
    val json = Json { encodeDefaults = true }
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
        body(connected)
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
