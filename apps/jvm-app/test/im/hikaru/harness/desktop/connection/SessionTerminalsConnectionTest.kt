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
import im.hikaru.harness.session.SessionId as CoreSessionId
import im.hikaru.harness.session.CreateSessionOptions as CoreCreateSessionOptions
import im.hikaru.harness.session.api.SessionApi as CoreSessionApi
import im.hikaru.harness.session.api.SessionApiKey
import im.hikaru.harness.session.api.SessionApiPlugin
import im.hikaru.harness.session.api.SessionSummary as CoreSessionSummary
import im.hikaru.harness.session.api.SessionPrompt as CoreSessionPrompt
import im.hikaru.harness.session.api.HostTerminalCreateResult
import im.hikaru.harness.session.api.TerminalCreateRequest as CoreCreateRequest
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
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlin.coroutines.CoroutineContext
import kotlin.test.*

/** Native cases use the installed Host through DefaultSessionApi and the production registrar. */
class SessionTerminalsConnectionTest {
    private val json = Json { encodeDefaults = true }
    private val owner = TerminalOwner(TerminalScope("scope"), SessionId("session"), TerminalIncarnation("incarnation"))
    private val key = TerminalKey(owner, TerminalSessionId("terminal"))
    private val input = "π你好🙂\n"
    private val resized = TerminalSize(30, 100)
    private val request = TerminalCreateRequest(owner, listOf("/bin/sh", "-c", "controlled"),
        mapOf("LANG" to "en_US.UTF-8"), TerminalSize(2, 16), "/canonical/cwd")

    @Test
    fun localProductionRegistrationOwnsRealPtyEndToEnd() = runBlocking<Unit> {
        withNativeFixture { f ->
            val capability = f.discover()
            assertEquals(f.session, capability.session)
            assertEquals(f.cwd.toRealPath().toString(), capability.canonicalCreationCwd)
            assertTrue(capability.capacity > 0)
            assertTrue(f.inventory(capability.owner).isEmpty())
            assertFalse(Files.exists(f.started))
            val created = assertIs<TerminalCreateResult.Created>(f.connection.session.createTerminal(f.request(capability.owner)))
            val actualKey = created.metadata.key.also(f::track)
            val first = f.untilMarker(actualKey, "SIZE_READY")
            val owned = f.captureOwned(actualKey)
            assertEquals(capability.owner, created.owner)
            assertEquals(capability.owner, actualKey.owner)
            assertEquals(f.session, actualKey.owner.session)
            assertEquals(capability.canonicalCreationCwd, created.metadata.canonicalCwd)
            assertEquals(created.metadata, first.metadata)
            assertEquals(f.request(capability.owner).argv, created.metadata.argv)
            assertEquals(TerminalLifecycle.Running, first.metadata.lifecycle)
            assertEquals(TerminalInputState.Open, first.metadata.input)
            assertTrue(first.rawTail.contains("TTY_OK"), first.rawTail)
            assertTrue(first.rawTail.contains("24 80"), first.rawTail)
            assertEquals(TerminalSize(), first.size)
            assertEquals(24, first.screen.size)
            assertTrue(first.screen.flatMap { it.runs }.any {
                it.text.contains("RED") && it.style.foreground == TerminalColorValue.Indexed(1) &&
                    TerminalTextAttribute.Bold in it.style.attributes
            })
            val detached = json.encodeToString(first)
            val write = assertIs<TerminalMutationResult.Applied>(f.connection.session.writeTerminal(actualKey, input))
            assertEquals(actualKey, write.key)
            assertEquals(input.toByteArray(Charsets.UTF_8).size, write.bytes)
            f.untilMarker(actualKey, "DELIVERED_READY:π你好🙂")
            awaitCondition("independent UTF-8 delivery") { f.deliveredText() == input }
            val resize = assertIs<TerminalMutationResult.Applied>(f.connection.session.resizeTerminal(actualKey, resized))
            assertEquals(actualKey, resize.key)
            assertEquals(resized, resize.size)
            // A second, distinct shell command observes the kernel size after resize.
            val sizeProbe = "size-check\n"
            val probe = assertIs<TerminalMutationResult.Applied>(f.connection.session.writeTerminal(actualKey, sizeProbe))
            assertEquals(actualKey, probe.key)
            assertEquals(sizeProbe.toByteArray(Charsets.UTF_8).size, probe.bytes)
            val last = f.untilMarker(actualKey, "DELIVERED_READY:size-check")
            awaitCondition("both distinct shell inputs persisted") { f.deliveredText() == input + sizeProbe }
            assertTrue(last.rawTail.contains("RECEIVED:π你好🙂"), last.rawTail)
            assertTrue(last.screen.any { it.text.contains("RECEIVED:π你好🙂") })
            assertFalse(last.rawTail.contains('\uFFFD'))
            assertTrue(last.rawTail.contains("30 100"), last.rawTail)
            assertEquals(resized, last.size)
            assertEquals(30, last.screen.size)
            assertEquals(100, last.size.columns)
            assertTrue(last.revision > first.revision)
            assertEquals(detached, json.encodeToString(first), "Earlier nested snapshots must stay detached")
            assertEquals(listOf(actualKey), f.inventory(capability.owner).map { it.key })
            assertEquals(1, f.pids().size)
            assertEquals(actualKey, assertIs<TerminalMutationResult.Applied>(f.connection.session.closeTerminal(actualKey)).key)
            f.assertStopped(owned)
            assertEquals(TerminalReadResult.NotFound(actualKey), f.connection.session.readTerminal(actualKey))
            assertTrue(f.inventory(capability.owner).isEmpty())
        }
    }

    @Test
    fun localCreateLostAcknowledgementStartsExactlyOnceAndInventoryFindsIt() = runBlocking<Unit> {
        val creates = AtomicInteger()
        val actual = AtomicReference<CoreKey>()
        val fixture = AtomicReference<NativeFixture>()
        withNativeFixture(decorate = { real ->
            object : CoreSessionApi by real {
                override suspend fun createTerminal(request: CoreCreateRequest): HostTerminalCreateResult {
                    creates.incrementAndGet()
                    // Lose only the acknowledgement of a successful production create.
                    actual.set(assertIs<HostTerminalCreateResult.Created>(real.createTerminal(request)).metadata.key)
                    assertNotNull(fixture.get()).track(assertNotNull(actual.get()).toClient())
                    throw IllegalStateException("private lost create acknowledgement")
                }
            }
        }) { f ->
            fixture.set(f)
            val capability = f.discover()
            val result = assertIs<TerminalCreateResult.UnknownOutcome>(
                f.connection.session.createTerminal(f.request(capability.owner)))
            assertEquals(capability.owner, result.owner)
            assertNull(result.key, "No trusted acknowledgement means no invented key")
            assertFalse(result.reason.contains("private"))
            assertEquals(1, creates.get())
            val recovered = f.inventory(capability.owner).single()
            assertEquals(assertNotNull(actual.get()).toClient(), recovered.key)
            f.untilMarker(recovered.key, "SIZE_READY")
            val owned = f.captureOwned(recovered.key)
            assertEquals(1, f.pids().size, "The actual shell must start exactly once")
            assertIs<TerminalMutationResult.Applied>(f.connection.session.closeTerminal(recovered.key))
            f.assertStopped(owned)
            assertTrue(f.inventory(capability.owner).isEmpty())
            assertEquals(1, creates.get())
            assertEquals(1, f.pids().size)
        }
    }

    @Test
    fun localMutationsRejectWrongResponseIdentityAndNeverRetry() = runBlocking<Unit> {
        withTransport { t ->
            val wrongOwners = listOf(
                owner.copy(scope = TerminalScope("foreign-scope")),
                owner.copy(session = SessionId("foreign-session")),
                owner.copy(incarnation = TerminalIncarnation("stale-incarnation")),
            )
            for (wrong in wrongOwners) {
                for (response in creates(wrong)) {
                    t.create = response
                    val result = assertIs<TerminalCreateResult.UnknownOutcome>(t.once("create") { t.local.session.createTerminal(request) })
                    assertEquals(owner, result.owner)
                    assertNull(result.key)
                }
                for (response in lists(wrong)) {
                    t.list = response
                    assertEquals(owner, assertIs<TerminalListResult.Unavailable>(
                        t.once("list") { t.local.session.terminals(owner) }).owner)
                }
            }
            // Discovery knows only the requested Session; scope/incarnation are first learned here.
            for (response in discoveries(wrongOwners[1])) {
                t.discovery = response
                assertEquals(owner.session, assertIs<TerminalDiscoveryResult.Unavailable>(
                    t.once("capability") { t.local.session.terminalCapability(owner.session) }).session)
            }
            val wrongKeys = wrongOwners.map { key.copy(owner = it) } + key.copy(terminal = TerminalSessionId("other-terminal"))
            for (wrong in wrongKeys) {
                for (response in reads(wrong)) {
                    t.read = response
                    assertEquals(key, assertIs<TerminalReadResult.Unavailable>(
                        t.once("read") { t.local.session.readTerminal(key) }).key)
                }
                for (response in mutations(wrong)) {
                    t.mutation = response
                    for (method in listOf("write", "resize", "close")) {
                        assertUnknown(t.once(method) { t.mutate(method) })
                    }
                }
            }
            // Correct business results remain typed, including NotFound from registered endpoints.
            for (response in discoveries(owner)) {
                t.discovery = response
                assertEquals(response, t.once("capability") { t.local.session.terminalCapability(owner.session) })
            }
            for (response in creates(owner)) {
                t.create = response
                assertEquals(response, t.once("create") { t.local.session.createTerminal(request) })
            }
            for (response in lists(owner)) {
                t.list = response
                assertEquals(response, t.once("list") { t.local.session.terminals(owner) })
            }
            for (response in reads(key)) {
                t.read = response
                assertEquals(response, t.once("read") { t.local.session.readTerminal(key) })
            }
            for (response in mutations(key)) {
                t.mutation = response
                for (method in listOf("write", "resize", "close")) assertEquals(response, t.once(method) { t.mutate(method) })
            }
            for (code in listOf(403, 404, 422, 500, ApiResult.SUCCESS_CODE)) {
                t.errorCode = code
                t.errorMessage = "private registered business error"
                assertNull(assertIs<TerminalCreateResult.UnknownOutcome>(t.once("create") { t.local.session.createTerminal(request) }).key)
                for (method in listOf("write", "resize", "close")) assertUnknown(t.once(method) { t.mutate(method) })
                for (method in listOf("capability", "list", "read")) {
                    assertReadUnavailable(method, t.once(method) { t.readOperation(method) })
                }
            }
            t.errorCode = null
            for (failure in listOf(IllegalStateException("private lost reply"), CancellationException("private service cancellation"))) {
                t.failure = failure
                assertNull(assertIs<TerminalCreateResult.UnknownOutcome>(t.once("create") { t.local.session.createTerminal(request) }).key)
                for (method in listOf("write", "resize", "close")) assertUnknown(t.once(method) { t.mutate(method) })
                for (method in listOf("capability", "list", "read")) {
                    if (failure is CancellationException) {
                        assertFailsWith<CancellationException> { t.once(method) { t.readOperation(method) } }
                    } else assertReadUnavailable(method, t.once(method) { t.readOperation(method) })
                }
            }
            t.failure = CancellationException("private read cancellation")
            for (method in listOf("capability", "list", "read")) {
                assertFailsWith<CancellationException> { t.once(method) { t.readOperation(method) } }
            }
            t.failure = null
            for (method in listOf("capability", "list", "read")) {
                coroutineScope {
                    val entered = CompletableDeferred<Unit>()
                    t.beforeReply = { called ->
                        assertEquals(method, called); entered.complete(Unit)
                        withTimeout(10_000) { awaitCancellation() }
                    }
                    val pending = async { t.once(method) { t.readOperation(method) } }
                    try {
                        withTimeout(10_000) { entered.await() }
                        pending.cancel()
                        assertFailsWith<CancellationException> { withTimeout(10_000) { pending.await() } }
                        withTimeout(10_000) { pending.join() }
                    } finally {
                        withContext(NonCancellable) { withTimeout(10_000) { pending.cancelAndJoin() } }
                        t.beforeReply = {}
                    }
                }
            }
            assertTrue(t.calls.values.all { it.get() > 0 }, "Exercise all seven methods")
        }
    }

    @Test
    fun localMissingEndpointAndRemoteDefaultsRemainUnavailable() = runBlocking<Unit> {
        val context = Context()
        val gateway = ApiGateway()
        val local = LocalConnection(gateway)
        try {
            val missing = gateway.invoke(SessionTerminalCreateEndpoint, request.toCore())
            assertEquals(GatewayResultCodes.NotFound, missing.code)
            assertEquals("Harness API endpoint '${SessionTerminalCreateEndpoint.method}' 未注册", missing.msg)
            assertNull(missing.data)
            assertTrue(gateway.methods().isEmpty())
            assertUnavailable(local.session)
            val registration = gateway.registerSessionApi(context)
            assertUnavailable(local.session) // Registered endpoints, absent optional service.
            context.provide(SessionApiKey, LegacyCoreApi())
            assertUnavailable(local.session)
            assertUnavailable(LegacyClientApi())
            assertRemoteDefaultsWithoutNetwork()
            registration.dispose()
            assertUnavailable(local.session)
            assertFalse(gateway.methods().any { it.startsWith("session.terminal.") })
        } finally { withContext(NonCancellable) { withTimeout(10_000) { context.dispose() } } }
    }

    @Test
    fun localDtosAndStyledSnapshotRoundTripWithoutNativeObjects() = runBlocking<Unit> {
        assertEquals(request, request.toCore().toClient())
        assertWire(request)
        assertWire(request.toCore())
        assertWire(request.copy(cwd = null, environment = emptyMap()))
        assertPortable(request)
        assertEquals(owner, owner.toCore().toClient())
        assertEquals(key, key.toCore().toClient())
        val styled = snapshot(key)
        assertEquals(styled, styled.toCore().toClient())
        assertWire(styled)
        assertWire(styled.toCore())
        assertPortable(styled)
        assertEquals(styled, json.decodeFromString<TerminalSnapshot>(json.encodeToString(styled.toCore())))
        val wire = json.parseToJsonElement(json.encodeToString(styled)).jsonObject
        assertEquals(setOf("metadata", "size", "screen", "history", "cursor", "alternate", "revision", "rawTail", "truncated"), wire.keys)
        assertEquals(TerminalSize(2, 16), styled.size)
        assertEquals(2, styled.screen[0].runs[0].column)
        assertEquals(4, styled.screen[0].runs[0].span)
        assertEquals("界🙂", styled.screen[0].runs[0].text)
        assertEquals("  界🙂", styled.screen[0].text)
        assertTrue(styled.screen[0].wrapped)
        assertTrue(styled.history.single().wrapped)
        assertEquals(TerminalColorValue.Indexed(201), styled.screen[0].runs[0].style.foreground)
        assertEquals(TerminalColorValue.Rgb(17, 99, 203), styled.screen[0].runs[0].style.background)
        assertEquals(TerminalTextAttribute.entries.toSet(), styled.screen[0].runs[0].style.attributes)
        assertEquals(TerminalCursor(16, 1, false), styled.cursor)
        assertTrue(styled.alternate)
        assertEquals(9876543210L, styled.revision)
        assertEquals("\u001b[31m原始🙂\u001b[0m", styled.rawTail)
        assertTrue(styled.truncated)
        for (lifecycle in listOf(TerminalLifecycle.Running, TerminalLifecycle.Exited(37), TerminalLifecycle.Unknown)) {
            for (state in TerminalInputState.entries) {
                val metadata = styled.metadata.copy(lifecycle = lifecycle, input = state)
                assertEquals(metadata, metadata.toCore().toClient())
                assertWire(metadata)
            }
        }
        for (color in listOf(TerminalColorValue.Default, TerminalColorValue.Indexed(0), TerminalColorValue.Indexed(255), TerminalColorValue.Rgb(0, 128, 255))) {
            val style = TerminalTextStyle(color, color, TerminalTextAttribute.entries.toSet())
            assertEquals(style, style.toCore().toClient())
            assertWire(style)
        }
        withTransport { t ->
            for (response in discoveries(owner)) {
                assertPortable(response)
                assertEquals(response, response.toCore().toClient()); assertWire(response); assertWire(response.toCore())
                t.discovery = response
                assertEquals(response, t.once("capability") { t.local.session.terminalCapability(owner.session) })
            }
            for (response in creates(owner)) {
                assertPortable(response)
                assertEquals(response, response.toCore().toClient()); assertWire(response); assertWire(response.toCore())
                t.create = response
                assertEquals(response, t.once("create") { t.local.session.createTerminal(request) })
            }
            for (response in lists(owner)) {
                assertPortable(response)
                assertEquals(response, response.toCore().toClient()); assertWire(response); assertWire(response.toCore())
                t.list = response
                assertEquals(response, t.once("list") { t.local.session.terminals(owner) })
            }
            for (response in reads(key)) {
                assertPortable(response)
                assertEquals(response, response.toCore().toClient()); assertWire(response); assertWire(response.toCore())
                t.read = response
                assertEquals(response, t.once("read") { t.local.session.readTerminal(key) })
            }
            for (response in mutations(key)) {
                assertPortable(response)
                assertEquals(response, response.toCore().toClient()); assertWire(response); assertWire(response.toCore())
                t.mutation = response
                for (method in listOf("write", "resize", "close")) assertEquals(response, t.once(method) { t.mutate(method) })
            }
            coroutineScope {
                val argv = request.argv.toMutableList()
                val env = request.environment.toMutableMap()
                val mutableRequest = request.copy(argv = argv, environment = env)
                val frozen = mutableRequest.toCore()
                val entered = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                t.beforeReply = { method ->
                    assertEquals("create", method); entered.complete(Unit)
                    withTimeout(10_000) { release.await() }
                    assertEquals(frozen, t.receivedCreate, "Caller changes cannot alter an in-flight request")
                }
                val pending = async { t.once("create") { t.local.session.createTerminal(mutableRequest) } }
                try {
                    withTimeout(10_000) { entered.await() }
                    argv.clear(); env.clear()
                    assertEquals(frozen, t.receivedCreate)
                    release.complete(Unit)
                    assertEquals(t.create, withTimeout(10_000) { pending.await() })
                } finally {
                    release.complete(Unit)
                    withContext(NonCancellable) { withTimeout(10_000) { pending.cancelAndJoin() } }
                    t.beforeReply = {}
                }
            }
        }
        assertCollectionCopies()
        val wrongOwner = owner.copy(incarnation = TerminalIncarnation("foreign"))
        val wrongKey = key.copy(owner = wrongOwner)
        assertFailsWith<IllegalArgumentException> { TerminalCreateResult.Created(owner, metadata(wrongKey)) }
        assertFailsWith<IllegalArgumentException> { TerminalCreateResult.UnknownOutcome(owner, wrongKey, "wrong") }
        assertFailsWith<IllegalArgumentException> { TerminalListResult.Available(owner, listOf(metadata(wrongKey))) }
        assertFailsWith<IllegalArgumentException> { TerminalReadResult.Available(key, snapshot(wrongKey)) }
        assertFailsWith<IllegalArgumentException> {
            TerminalDiscoveryResult.Available(owner.session, wrongOwner.copy(session = SessionId("foreign")), "/cwd", 3)
        }
    }

    @Test
    fun localCancellationAfterDeliveryPreservesUnknownAndOwnedCleanup() = runBlocking<Unit> {
        for (cleanupByRoot in listOf(false, true)) {
            val gate = CompletableDeferred<Unit>()
            val entered = CompletableDeferred<Unit>()
            val writes = AtomicInteger()
            val originalCaller = AtomicReference<Job>()
            var delivered: Path? = null
            withNativeFixture(decorate = { real ->
                object : CoreSessionApi by real {
                    override suspend fun writeTerminal(key: CoreKey, input: String): CoreMutation {
                        writes.incrementAndGet()
                        val response = assertIs<CoreMutation.Applied>(real.writeTerminal(key, input))
                        awaitCondition("real shell persisted input before acknowledgement gate") {
                            val path = assertNotNull(delivered)
                            Files.exists(path) && Files.readString(path) == input
                        }
                        entered.complete(Unit)
                        withTimeout(10_000) { gate.await() }
                        return response
                    }
                }
            }) { f ->
                delivered = f.delivered
                val capability = f.discover()
                val actualKey = assertIs<TerminalCreateResult.Created>(
                    f.connection.session.createTerminal(f.request(capability.owner))).metadata.key.also(f::track)
                f.untilMarker(actualKey, "SIZE_READY")
                val owned = f.captureOwned(actualKey)
                val executor = Executors.newSingleThreadExecutor { Thread(it, "harness-local-pty-test-caller") }
                val dispatcher = executor.asCoroutineDispatcher()
                val callerThread = AtomicReference<Thread>()
                val acknowledgement = CompletableDeferred<TerminalMutationResult>()
                val scope = CoroutineScope(SupervisorJob() + dispatcher)
                val writer = scope.launch {
                    originalCaller.set(currentCoroutineContext()[Job])
                    callerThread.set(Thread.currentThread())
                    try { acknowledgement.complete(f.connection.session.writeTerminal(actualKey, input)) }
                    catch (failure: Throwable) { acknowledgement.completeExceptionally(failure) }
                }
                try {
                    withTimeout(10_000) { entered.await() }
                    assertSame(writer, assertNotNull(originalCaller.get()), "Capture the original caller, not a NonCancellable inner Job")
                    assertSame(callerThread.get(), withTimeout(10_000) { withContext(dispatcher) { Thread.currentThread() } },
                        "The actual single caller thread remains responsive during IO")
                    assertEquals(input, f.deliveredText())
                    assertFalse(acknowledgement.isCompleted)
                    assertNotNull(originalCaller.get()).cancel(CancellationException("cancel after proven delivery"))
                    gate.complete(Unit)
                    val result = assertIs<TerminalMutationResult.UnknownOutcome>(withTimeout(10_000) { acknowledgement.await() })
                    assertEquals(actualKey, result.key)
                    withTimeout(10_000) { writer.join() }
                    assertTrue(writer.isCancelled)
                    f.untilMarker(actualKey, "DELIVERED_READY")
                    assertEquals(input, f.deliveredText(), "Input appears exactly once")
                    assertEquals(1, writes.get())
                    assertEquals(1, f.pids().size)
                    if (cleanupByRoot) withTimeout(10_000) { f.runtime.context.dispose() }
                    else assertIs<TerminalMutationResult.Applied>(f.connection.session.closeTerminal(actualKey))
                    f.assertStopped(owned)
                    if (!cleanupByRoot) {
                        assertTrue(f.inventory(capability.owner).isEmpty())
                        withTimeout(10_000) { f.runtime.context.dispose() }
                    }
                    assertTrue(f.runtime.context.isDisposed)
                    val fenced = assertIs<TerminalCreateResult.Unavailable>(
                        f.connection.session.createTerminal(f.request(capability.owner)))
                    assertEquals(capability.owner, fenced.owner)
                    assertEquals(1, f.pids().size, "Root disposal fences a second PID")
                    assertEquals(1, writes.get())
                } finally {
                    gate.complete(Unit)
                    try {
                        withContext(NonCancellable) {
                            withTimeout(10_000) { writer.cancelAndJoin(); scope.coroutineContext[Job]!!.cancelAndJoin() }
                        }
                    } finally {
                        dispatcher.close()
                        if (!executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS)) {
                            executor.shutdownNow()
                            assertTrue(executor.awaitTermination(10, java.util.concurrent.TimeUnit.SECONDS))
                        }
                    }
                }
            }
        }
    }

    private suspend fun assertUnavailable(api: SessionApi) {
        assertEquals(owner.session, assertIs<TerminalDiscoveryResult.Unavailable>(api.terminalCapability(owner.session)).session)
        assertEquals(owner, assertIs<TerminalCreateResult.Unavailable>(api.createTerminal(request)).owner)
        assertEquals(owner, assertIs<TerminalListResult.Unavailable>(api.terminals(owner)).owner)
        assertEquals(key, assertIs<TerminalReadResult.Unavailable>(api.readTerminal(key)).key)
        for (result in listOf(api.writeTerminal(key, input), api.resizeTerminal(key, resized), api.closeTerminal(key))) {
            assertEquals(key, assertIs<TerminalMutationResult.Unavailable>(result).key)
        }
    }

    private fun assertUnknown(result: TerminalMutationResult) {
        assertEquals(key, assertIs<TerminalMutationResult.UnknownOutcome>(result).key)
        assertFalse(result.reason.contains("private"))
    }

    private fun assertReadUnavailable(method: String, result: Any) {
        val reason = when (method) {
            "capability" -> assertIs<TerminalDiscoveryResult.Unavailable>(result).also { assertEquals(owner.session, it.session) }.reason
            "list" -> assertIs<TerminalListResult.Unavailable>(result).also { assertEquals(owner, it.owner) }.reason
            "read" -> assertIs<TerminalReadResult.Unavailable>(result).also { assertEquals(key, it.key) }.reason
            else -> error("Unexpected read $method")
        }
        assertFalse(reason.contains("private"))
    }

    private inline fun <reified T> assertWire(value: T) {
        assertEquals(value, json.decodeFromString<T>(json.encodeToString(value)))
    }

    /** Walk actual public DTO values; any core/native object is an unsupported leaf. */
    private fun assertPortable(value: Any?) {
        val children: List<Any?> = when (value) {
            null, is String, is Int, is Long, is Boolean -> return
            is List<*> -> value
            is Set<*> -> value.toList()
            is Map<*, *> -> value.entries.flatMap { listOf(it.key, it.value) }
            is SessionId -> listOf(value.value)
            is TerminalScope -> listOf(value.value)
            is TerminalIncarnation -> listOf(value.value)
            is TerminalSessionId -> listOf(value.value)
            is TerminalOwner -> listOf(value.scope, value.session, value.incarnation)
            is TerminalKey -> listOf(value.owner, value.terminal)
            is TerminalSize -> listOf(value.rows, value.columns)
            is TerminalCreateRequest -> listOf(value.owner, value.argv, value.environment, value.size, value.cwd)
            is TerminalMetadata -> listOf(value.key, value.canonicalCwd, value.argv, value.lifecycle, value.input)
            TerminalLifecycle.Running, TerminalLifecycle.Unknown, TerminalColorValue.Default -> emptyList()
            is TerminalLifecycle.Exited -> listOf(value.code)
            is TerminalInputState, is TerminalTextAttribute -> emptyList()
            is TerminalColorValue.Indexed -> listOf(value.index)
            is TerminalColorValue.Rgb -> listOf(value.red, value.green, value.blue)
            is TerminalTextStyle -> listOf(value.foreground, value.background, value.attributes)
            is TerminalTextRun -> listOf(value.column, value.span, value.text, value.style)
            is TerminalLineSnapshot -> listOf(value.runs, value.wrapped)
            is TerminalCursor -> listOf(value.column, value.row, value.visible)
            is TerminalSnapshot -> listOf(value.metadata, value.size, value.screen, value.history, value.cursor,
                value.alternate, value.revision, value.rawTail, value.truncated)
            is TerminalDiscoveryResult.Available -> listOf(value.session, value.owner, value.canonicalCreationCwd, value.capacity)
            is TerminalDiscoveryResult.Rejected -> listOf(value.session, value.reason)
            is TerminalDiscoveryResult.Unavailable -> listOf(value.session, value.reason)
            is TerminalCreateResult.Created -> listOf(value.owner, value.metadata)
            is TerminalCreateResult.Rejected -> listOf(value.owner, value.reason)
            is TerminalCreateResult.Unavailable -> listOf(value.owner, value.reason)
            is TerminalCreateResult.UnknownOutcome -> listOf(value.owner, value.key, value.reason)
            is TerminalListResult.Available -> listOf(value.owner, value.terminals)
            is TerminalListResult.Rejected -> listOf(value.owner, value.reason)
            is TerminalListResult.Unavailable -> listOf(value.owner, value.reason)
            is TerminalReadResult.Available -> listOf(value.key, value.snapshot)
            is TerminalReadResult.NotFound -> listOf(value.key)
            is TerminalReadResult.Rejected -> listOf(value.key, value.reason)
            is TerminalReadResult.Unavailable -> listOf(value.key, value.reason)
            is TerminalMutationResult.Applied -> listOf(value.key, value.bytes, value.size)
            is TerminalMutationResult.NotFound -> listOf(value.key)
            is TerminalMutationResult.Rejected -> listOf(value.key, value.reason)
            is TerminalMutationResult.Unavailable -> listOf(value.key, value.reason)
            is TerminalMutationResult.UnknownOutcome -> listOf(value.key, value.reason)
            else -> fail("Non-portable DTO leaf: ${value.javaClass.name}")
        }
        if (value !is Collection<*> && value !is Map<*, *>) {
            val type = assertNotNull(value).javaClass.name
            assertTrue(type.startsWith("im.hikaru.harness.client.connection."), type)
        }
        children.forEach(::assertPortable)
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
            assertUnavailable(connected.session)
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

    private fun metadata(key: TerminalKey) = TerminalMetadata(key, "/canonical/cwd", listOf("/bin/sh", "-c", "controlled"),
        TerminalLifecycle.Running, TerminalInputState.Open)

    private fun snapshot(key: TerminalKey): TerminalSnapshot {
        val attributes = TerminalTextAttribute.entries.toMutableSet()
        val runs = mutableListOf(TerminalTextRun(2, 4, "界🙂", TerminalTextStyle(
            TerminalColorValue.Indexed(201), TerminalColorValue.Rgb(17, 99, 203), attributes)))
        return TerminalSnapshot(metadata(key).copy(argv = mutableListOf("/bin/sh", "-c", "controlled")), TerminalSize(2, 16),
            mutableListOf(TerminalLineSnapshot(runs, true), TerminalLineSnapshot(mutableListOf(
                TerminalTextRun(0, 5, "plain", TerminalTextStyle())))),
            mutableListOf(TerminalLineSnapshot(mutableListOf(TerminalTextRun(1, 3, "old",
                TerminalTextStyle(TerminalColorValue.Rgb(9, 8, 7), TerminalColorValue.Indexed(4), attributes.toMutableSet()))), true)),
            TerminalCursor(16, 1, false), true, 9876543210L, "\u001b[31m原始🙂\u001b[0m", true)
    }

    private fun discoveries(owner: TerminalOwner): List<TerminalDiscoveryResult> = listOf(
        TerminalDiscoveryResult.Available(owner.session, owner, "/canonical/cwd", 3),
        TerminalDiscoveryResult.Rejected(owner.session, "rejected"), TerminalDiscoveryResult.Unavailable(owner.session, "unavailable"))

    private fun creates(owner: TerminalOwner): List<TerminalCreateResult> {
        val ownedKey = key.copy(owner = owner)
        return listOf(TerminalCreateResult.Created(owner, metadata(ownedKey)), TerminalCreateResult.Rejected(owner, "rejected"),
            TerminalCreateResult.Unavailable(owner, "unavailable"), TerminalCreateResult.UnknownOutcome(owner, null, "lost"),
            TerminalCreateResult.UnknownOutcome(owner, ownedKey, "reserved"))
    }

    private fun lists(owner: TerminalOwner): List<TerminalListResult> = listOf(
        TerminalListResult.Available(owner, listOf(metadata(key.copy(owner = owner)))), TerminalListResult.Rejected(owner, "rejected"),
        TerminalListResult.Unavailable(owner, "unavailable"))

    private fun reads(key: TerminalKey): List<TerminalReadResult> = listOf(TerminalReadResult.Available(key, snapshot(key)),
        TerminalReadResult.NotFound(key), TerminalReadResult.Rejected(key, "rejected"), TerminalReadResult.Unavailable(key, "unavailable"))

    private fun mutations(key: TerminalKey): List<TerminalMutationResult> = listOf(
        TerminalMutationResult.Applied(key, input.toByteArray(Charsets.UTF_8).size, resized),
        TerminalMutationResult.Applied(key), TerminalMutationResult.NotFound(key),
        TerminalMutationResult.Rejected(key, "rejected"), TerminalMutationResult.Unavailable(key, "unavailable"),
        TerminalMutationResult.UnknownOutcome(key, "lost"))

    private fun assertCollectionCopies() {
        val argv = mutableListOf("/bin/sh", "-c", "original")
        val env = mutableMapOf("COPY" to "original")
        val original = request.copy(argv = argv, environment = env)
        val core = original.toCore()
        val saved = json.encodeToString(core)
        argv[2] = "changed"; env["COPY"] = "changed"
        assertEquals(saved, json.encodeToString(core))
        val coreArgv = mutableListOf("/bin/sh", "-c", "core-original")
        val coreEnv = mutableMapOf("COPY" to "core-original")
        val reverse = core.copy(argv = coreArgv, environment = coreEnv).toClient()
        val reverseSaved = json.encodeToString(reverse)
        coreArgv.clear(); coreEnv.clear()
        assertEquals(reverseSaved, json.encodeToString(reverse))
        val source = snapshot(key)
        val copied = source.toCore()
        val copiedSaved = json.encodeToString(copied)
        (source.metadata.argv as MutableList<String>).clear()
        (source.screen[0].runs[0].style.attributes as MutableSet<TerminalTextAttribute>).clear()
        (source.screen[0].runs as MutableList<TerminalTextRun>).clear()
        (source.history[0].runs[0].style.attributes as MutableSet<TerminalTextAttribute>).clear()
        (source.history[0].runs as MutableList<TerminalTextRun>).clear()
        (source.screen as MutableList<TerminalLineSnapshot>).clear()
        (source.history as MutableList<TerminalLineSnapshot>).clear()
        assertEquals(copiedSaved, json.encodeToString(copied))
        val coreAttributes = copied.screen[0].runs[0].style.attributes.toMutableSet()
        val coreRuns = copied.screen[0].runs.map { it.copy(style = it.style.copy(attributes = coreAttributes)) }.toMutableList()
        val coreHistoryRuns = copied.history[0].runs.toMutableList()
        val coreHistoryAttributes = copied.history[0].runs[0].style.attributes.toMutableSet()
        coreHistoryRuns[0] = coreHistoryRuns[0].copy(style = coreHistoryRuns[0].style.copy(attributes = coreHistoryAttributes))
        val coreScreen = copied.screen.toMutableList().apply { this[0] = this[0].copy(runs = coreRuns) }
        val coreHistory = copied.history.toMutableList().apply { this[0] = this[0].copy(runs = coreHistoryRuns) }
        val mutableCoreArgv = copied.metadata.argv.toMutableList()
        val reverseSnapshot = copied.copy(metadata = copied.metadata.copy(argv = mutableCoreArgv), screen = coreScreen, history = coreHistory).toClient()
        val reverseSnapshotSaved = json.encodeToString(reverseSnapshot)
        coreAttributes.clear(); coreHistoryAttributes.clear(); coreRuns.clear(); coreHistoryRuns.clear()
        coreScreen.clear(); coreHistory.clear(); mutableCoreArgv.clear()
        assertEquals(reverseSnapshotSaved, json.encodeToString(reverseSnapshot))
        val clientInventory = mutableListOf(metadata(key))
        val coreInventory = TerminalListResult.Available(owner, clientInventory).toCore()
        clientInventory.clear()
        assertEquals(listOf(key), assertIs<TerminalListResult.Available>(coreInventory.toClient()).terminals.map { it.key })
        val reverseInventorySource = assertIs<im.hikaru.harness.session.api.TerminalListResult.Available>(coreInventory)
            .terminals.toMutableList()
        val reverseInventory = im.hikaru.harness.session.api.TerminalListResult.Available(owner.toCore(), reverseInventorySource).toClient()
        reverseInventorySource.clear()
        assertEquals(listOf(key), assertIs<TerminalListResult.Available>(reverseInventory).terminals.map { it.key })
    }

    private suspend fun withTransport(body: suspend (Transport) -> Unit) {
        val t = Transport()
        try { t.install(); body(t) } finally { withContext(NonCancellable) { withTimeout(10_000) { t.context.dispose() } } }
    }

    /** Deliberately unfiltered typed endpoints make LocalConnection do its own identity checks. */
    private inner class Transport {
        val context = Context()
        private val gateway = ApiGateway()
        val local = LocalConnection(gateway)
        val calls = listOf("capability", "create", "list", "read", "write", "resize", "close").associateWith { AtomicInteger() }
        var discovery: TerminalDiscoveryResult = discoveries(owner).first()
        var create: TerminalCreateResult = creates(owner).first()
        var list: TerminalListResult = lists(owner).first()
        var read: TerminalReadResult = reads(key).first()
        var mutation: TerminalMutationResult = mutations(key).first()
        var errorCode: Int? = null
        var errorMessage = ""
        var failure: Throwable? = null
        var beforeReply: suspend (String) -> Unit = {}
        var receivedCreate: CoreCreateRequest? = null

        private suspend fun <T : Any> respond(method: String, value: T): ApiResult<T> {
            calls.getValue(method).incrementAndGet()
            beforeReply(method)
            failure?.let { throw it }
            return errorCode?.let { ApiResult<T>(it, errorMessage) } ?: ApiResult(ApiResult.SUCCESS_CODE, "", value)
        }

        suspend fun install() {
            gateway.register(context, SessionTerminalCapabilityEndpoint) {
                assertEquals(CoreSessionId(owner.session.value), it); respond("capability", discovery.toCore())
            }
            gateway.register(context, SessionTerminalCreateEndpoint) {
                assertEquals(request.toCore(), it); receivedCreate = it; respond("create", create.toCore())
            }
            gateway.register(context, SessionTerminalsEndpoint) {
                assertEquals(owner.toCore(), it); respond("list", list.toCore())
            }
            gateway.register(context, SessionTerminalReadEndpoint) {
                assertEquals(key.toCore(), it); respond("read", read.toCore())
            }
            gateway.register(context, SessionTerminalWriteEndpoint) {
                assertEquals(SessionTerminalWriteRequest(key.toCore(), input), it); respond("write", mutation.toCore())
            }
            gateway.register(context, SessionTerminalResizeEndpoint) {
                assertEquals(SessionTerminalResizeRequest(key.toCore(), resized.toCore()), it); respond("resize", mutation.toCore())
            }
            gateway.register(context, SessionTerminalCloseEndpoint) {
                assertEquals(key.toCore(), it); respond("close", mutation.toCore())
            }
        }

        suspend fun <T> once(method: String, operation: suspend () -> T): T {
            val before = calls.mapValues { it.value.get() }
            try { return operation() } finally {
                for ((name, counter) in calls) assertEquals(before.getValue(name) + if (name == method) 1 else 0, counter.get(),
                    "$method must dispatch once and never retry or invoke another endpoint")
            }
        }

        suspend fun readOperation(method: String): Any = when (method) {
            "capability" -> local.session.terminalCapability(owner.session)
            "list" -> local.session.terminals(owner)
            "read" -> local.session.readTerminal(key)
            else -> error("Unexpected read $method")
        }

        suspend fun mutate(method: String): TerminalMutationResult = when (method) {
            "write" -> local.session.writeTerminal(key, input)
            "resize" -> local.session.resizeTerminal(key, resized)
            "close" -> local.session.closeTerminal(key)
            else -> error("Unexpected mutation $method")
        }
    }

    private suspend fun withNativeFixture(
        decorate: (CoreSessionApi) -> CoreSessionApi = { it },
        body: suspend (NativeFixture) -> Unit,
    ) {
        val cwd = Files.createTempDirectory("harness-local-pty-chain-").toRealPath()
        val runtime = Runtime()
        val f = NativeFixture(runtime, cwd)
        try {
            runtime.install(SessionPlugin())
            runtime.install(AgentPlugin())
            runtime.install(ToolsPlugin())
            runtime.context.agents.registerFactory(AgentFactory { ControlledAgent(it) })
            runtime.install(SubprocessPlugin())
            runtime.install(SessionApiPlugin())
            f.handle = runtime.context.agents.create(AgentId("owner"), CoreAgentOptions(cwd = cwd.toString()))
            val real = runtime.context.require(SessionApiKey)
            val service = decorate(real)
            val registrationOwner = if (service === real) runtime.context else runtime.context.isolate(SessionApiKey).also {
                it.provide(SessionApiKey, service)
            }
            f.gateway.registerSessionApi(registrationOwner)
            body(f)
        } finally {
            withContext(NonCancellable) {
                try { withTimeout(10_000) { runtime.context.dispose() } }
                finally {
                    try { f.assertAllStopped() }
                    finally { Files.walk(cwd).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } } }
                }
            }
        }
    }

    private class Owned(val process: ProcessHandle, val workers: Set<Thread>, val key: TerminalKey)

    private inner class NativeFixture(val runtime: Runtime, val cwd: Path) {
        lateinit var handle: AgentHandle
        val gateway = ApiGateway()
        val connection = LocalConnection(gateway)
        val started = cwd.resolve("started-pids")
        val delivered = cwd.resolve("delivered")
        private val observed = mutableListOf<Owned>()
        private val tracked = mutableMapOf<TerminalKey, Set<Thread>>()
        fun track(key: TerminalKey) { tracked[key] = workers(key) }
        val session: SessionId get() = SessionId(handle.agent.session.id.value)
        suspend fun discover() = assertIs<TerminalDiscoveryResult.Available>(connection.session.terminalCapability(session))
        suspend fun inventory(owner: TerminalOwner) = assertIs<TerminalListResult.Available>(connection.session.terminals(owner)).terminals
        fun pids(): List<Long> = if (Files.exists(started)) Files.readAllLines(started).filter { it.isNotBlank() }.map { it.toLong() } else emptyList()
        fun deliveredText(): String? = if (Files.exists(delivered)) Files.readString(delivered) else null

        fun request(owner: TerminalOwner): TerminalCreateRequest {
            val dollar = 36.toChar()
            val script = "printf '%s\n' \"$dollar$dollar\" >> started-pids; " +
                "test -t 0 && test -t 1 && test -t 2 || exit 71; stty -echo; " +
                "printf 'TTY_OK\n'; stty size; printf '\\033[1;31mRED\\033[0m\nSIZE_READY\n'; " +
                "while IFS= read -r message; do printf '%s\n' \"${dollar}message\" >> delivered; " +
                "printf 'RECEIVED:%s\n' \"${dollar}message\"; stty size; printf 'DELIVERED_READY:%s\n' \"${dollar}message\"; done"
            return TerminalCreateRequest(owner, listOf("/bin/sh", "-c", script),
                mapOf("PATH" to "/usr/bin:/bin", "LANG" to "en_US.UTF-8", "LC_ALL" to "en_US.UTF-8"), cwd = cwd.toString())
        }

        suspend fun untilMarker(key: TerminalKey, marker: String): TerminalSnapshot {
            var found: TerminalSnapshot? = null
            awaitCondition("raw and actual screen contain $marker") {
                found = assertIs<TerminalReadResult.Available>(connection.session.readTerminal(key)).snapshot
                assertNotNull(found).let { it.rawTail.contains(marker) && it.screen.any { line -> line.text.contains(marker) } }
            }
            return assertNotNull(found)
        }

        suspend fun captureOwned(key: TerminalKey): Owned {
            track(key)
            awaitCondition("independent shell PID and real owned workers") {
                pids().size == 1 && workers(key).map { it.name.substringAfterLast('-') }.containsAll(listOf("io", "parser", "waiter"))
            }
            val process = ProcessHandle.of(pids().single()).orElseThrow()
            assertTrue(process.isAlive)
            return Owned(process, workers(key), key).also { observed += it }
        }

        suspend fun assertStopped(owned: Owned) {
            awaitCondition("owned PID and workers stopped") {
                !owned.process.isAlive && owned.workers.none { it.isAlive } && workers(owned.key).none { it.isAlive }
            }
            assertFalse(owned.process.isAlive)
            assertFalse(ProcessHandle.of(owned.process.pid()).map { it.isAlive }.orElse(false))
            assertTrue(owned.workers.isNotEmpty())
            assertTrue(owned.workers.all { it.state == Thread.State.TERMINATED })
        }

        suspend fun assertAllStopped() {
            observed.forEach { assertStopped(it) }
            for ((key, initialWorkers) in tracked) {
                awaitCondition("all workers for tracked real key stopped") {
                    initialWorkers.none { it.isAlive } && workers(key).none { it.isAlive }
                }
            }
            awaitCondition("all independently recorded shell PIDs stopped") {
                pids().none { pid -> ProcessHandle.of(pid).map { it.isAlive }.orElse(false) }
            }
        }
    }

    private class ControlledAgent(request: AgentFactoryRequest) : Agent {
        override val id = request.id
        override val options = request.options
        override val session = request.session
        override val inbox = request.inbox
        override val context = request.context
        override val status = AgentStatus.IDLE
        override suspend fun awaitIdle() = Unit
        override suspend fun cancel(cause: Throwable?, keepInbox: Boolean) = Unit
        override suspend fun send(message: CoreMessage, target: AgentSendTarget, wakeup: AgentWakeup): String = inbox.insert(message, target, wakeup)
    }

    private class LegacyCoreApi : CoreSessionApi {
        override suspend fun list(): List<CoreSessionSummary> = error("Unexpected list")
        override suspend fun create(id: CoreSessionId?, options: CoreCreateSessionOptions, agentOptions: CoreAgentOptions): CoreSessionSummary = error("Unexpected create")
        override suspend fun history(id: CoreSessionId): List<CoreMessage> = error("Unexpected history")
        override suspend fun prompt(id: CoreSessionId, message: CoreMessage): CoreSessionPrompt = error("Unexpected prompt")
        override suspend fun cancel(id: CoreSessionId, keepInbox: Boolean) = error("Unexpected cancel")
    }

    private class LegacyClientApi : SessionApi {
        override suspend fun list(): List<SessionSummary> = error("Unexpected list")
        override suspend fun create(id: SessionId?, options: CreateSessionOptions, agentOptions: AgentOptions): SessionSummary = error("Unexpected create")
        override suspend fun history(id: SessionId): List<Message> = error("Unexpected history")
        override suspend fun prompt(id: SessionId, message: Message): SessionPrompt = error("Unexpected prompt")
        override suspend fun cancel(id: SessionId, keepInbox: Boolean) = error("Unexpected cancel")
    }

    companion object {
        private fun workers(key: TerminalKey): Set<Thread> {
            val prefix = "harness-pty-${key.terminal.value}-"
            return Thread.getAllStackTraces().keys.filter { it.name.startsWith(prefix) }.toSet()
        }

        private suspend fun awaitCondition(description: String, predicate: suspend () -> Boolean) {
            val deadline = System.nanoTime() + 10_000_000_000L
            withTimeout(10_000) {
                while (!predicate()) {
                    assertTrue(System.nanoTime() < deadline, "Timed out waiting for $description")
                    yield()
                }
            }
        }
    }
}
