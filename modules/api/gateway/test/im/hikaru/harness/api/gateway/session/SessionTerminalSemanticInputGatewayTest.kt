package im.hikaru.harness.api.gateway.session

import im.hikaru.contracts.common.ApiResult
import im.hikaru.harness.agent.AgentOptions
import im.hikaru.harness.agent.AgentPlugin
import im.hikaru.harness.api.gateway.ApiGateway
import im.hikaru.harness.api.gateway.GatewayResultCodes
import im.hikaru.harness.llm.Message
import im.hikaru.harness.runtime.Runtime
import im.hikaru.harness.session.CreateSessionOptions
import im.hikaru.harness.session.SessionId
import im.hikaru.harness.session.SessionPlugin
import im.hikaru.harness.session.api.*
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SessionTerminalSemanticInputGatewayTest {
    private val key = TerminalKey(TerminalOwner(TerminalScope("host"), SessionId("session"),
        TerminalIncarnation("instance")), TerminalSessionId("terminal"))
    private val request = SessionTerminalInputRequest(key, TerminalInput.Text("你好🙂"))

    @Test
    fun forwardsSemanticVariantsAndEveryResultExactlyOnce() = runBlocking<Unit> {
        val api = RecordingApi()
        fixture(api) { _, gateway ->
            assertEquals("session.terminal.input", SessionTerminalInputEndpoint.method)
            for (input in variants()) for (reply in results(key)) {
                val sent = SessionTerminalInputRequest(key, input)
                assertEquals(sent, Json.decodeFromString<SessionTerminalInputRequest>(Json.encodeToString(sent)))
                api.reply = reply
                val before = api.calls.size
                assertEquals(reply, data(gateway.invoke(SessionTerminalInputEndpoint, sent)))
                assertEquals(before + 1, api.calls.size)
                assertEquals(sent, api.calls.last())
                assertNotSame(sent.key, api.calls.last().key)
                if (input is TerminalInput.KeyPress) assertNotSame(input, api.calls.last().input)
            }
            assertEquals(variants().size * 5, api.calls.size)
            assertEquals(0, api.rawCalls)
        }
    }

    @Test
    fun missingLegacyAndDisposedInputEndpointAreUnavailable() = runBlocking<Unit> {
        fixture(null) { runtime, gateway ->
            assertTrue(SessionTerminalInputEndpoint.method in gateway.methods())
            assertEquals(key, assertIs<TerminalMutationResult.Unavailable>(data(gateway.invoke(SessionTerminalInputEndpoint, request))).key)
            val legacy = runtime.provide(SessionApiKey, LegacyApi())
            assertIs<TerminalMutationResult.Unavailable>(data(gateway.invoke(SessionTerminalInputEndpoint, request)))
            legacy.dispose()
            runtime.install(SessionPlugin())
            runtime.install(AgentPlugin())
            runtime.install(SessionApiPlugin())
            assertNull(runtime.context.get(SessionTerminalHostKey))
            assertEquals(key, assertIs<TerminalMutationResult.Unavailable>(data(gateway.invoke(SessionTerminalInputEndpoint, request))).key)
        }
        for (disposeOwner in listOf(false, true)) {
            val oldRuntime = Runtime()
            val nextRuntime = Runtime()
            val gateway = ApiGateway()
            try {
                val previous = RecordingApi()
                oldRuntime.provide(SessionApiKey, previous)
                val old = gateway.registerSessionApi(oldRuntime.context)
                val registeredMethods = gateway.methods()
                assertTrue(SessionTerminalInputEndpoint.method in registeredMethods)
                assertTrue(previous.calls.isEmpty())
                if (disposeOwner) oldRuntime.context.dispose() else old.dispose()
                assertFalse(SessionTerminalInputEndpoint.method in gateway.methods())
                assertTrue(gateway.methods().isEmpty())
                assertTrue(previous.calls.isEmpty())
                val missing = gateway.invoke(SessionTerminalInputEndpoint, request)
                assertEquals(GatewayResultCodes.NotFound, missing.code)
                assertEquals("Harness API endpoint 'session.terminal.input' 未注册", missing.msg)
                assertNull(missing.data)
                assertTrue(previous.calls.isEmpty())
                val replacement = RecordingApi()
                nextRuntime.provide(SessionApiKey, replacement)
                val next = gateway.registerSessionApi(nextRuntime.context)
                assertEquals(registeredMethods, gateway.methods())
                assertTrue(previous.calls.isEmpty())
                assertTrue(replacement.calls.isEmpty())
                old.dispose()
                oldRuntime.context.dispose()
                old.dispose()
                assertTrue(SessionTerminalInputEndpoint.method in gateway.methods())
                assertEquals(registeredMethods, gateway.methods())
                assertTrue(previous.calls.isEmpty())
                assertTrue(replacement.calls.isEmpty())
                assertEquals(replacement.reply, data(gateway.invoke(SessionTerminalInputEndpoint, request)))
                assertEquals(1, replacement.calls.size)
                assertEquals(request, replacement.calls.single())
                assertTrue(previous.calls.isEmpty())
                next.dispose()
                assertFalse(SessionTerminalInputEndpoint.method in gateway.methods())
                assertTrue(gateway.methods().isEmpty())
                assertEquals(1, replacement.calls.size)
                assertTrue(previous.calls.isEmpty())
                assertEquals(0, previous.rawCalls)
                assertEquals(0, replacement.rawCalls)
            } finally {
                withContext(NonCancellable) { withTimeout(10_000) {
                    oldRuntime.context.dispose()
                    nextRuntime.context.dispose()
                } }
            }
        }
        for (disposeOwner in listOf(false, true)) {
            val oldRuntime = Runtime()
            val nextRuntime = Runtime()
            val gateway = ApiGateway()
            try {
                val previous = RecordingApi()
                oldRuntime.provide(SessionApiKey, previous)
                val old = gateway.registerSessionApi(oldRuntime.context)
                val registeredMethods = gateway.methods()
                assertTrue(SessionTerminalInputEndpoint.method in registeredMethods)
                assertTrue(previous.calls.isEmpty())
                val replacement = RecordingApi()
                nextRuntime.provide(SessionApiKey, replacement)
                // session.list is registered first; its duplicate check precedes all registration effects.
                val duplicate = assertFailsWith<IllegalStateException> {
                    bounded { gateway.registerSessionApi(nextRuntime.context) }
                }
                assertEquals(IllegalStateException::class, duplicate::class)
                assertEquals("API endpoint '${SessionListEndpoint.method}' is already registered", duplicate.message)
                assertEquals(registeredMethods, gateway.methods())
                assertTrue(previous.calls.isEmpty())
                assertTrue(replacement.calls.isEmpty())
                assertEquals(previous.reply, data(gateway.invoke(SessionTerminalInputEndpoint, request)))
                assertEquals(1, previous.calls.size)
                assertEquals(request, previous.calls.single())
                assertTrue(replacement.calls.isEmpty())
                if (disposeOwner) bounded { oldRuntime.context.dispose() } else bounded { old.dispose() }
                assertTrue(gateway.methods().isEmpty())
                assertEquals(1, previous.calls.size)
                assertTrue(replacement.calls.isEmpty())
                val next = gateway.registerSessionApi(nextRuntime.context)
                assertEquals(registeredMethods, gateway.methods())
                assertEquals(1, previous.calls.size)
                assertTrue(replacement.calls.isEmpty())
                // Releasing the old handle and owner again must retain every replacement endpoint.
                bounded { old.dispose(); oldRuntime.context.dispose(); old.dispose() }
                assertTrue(SessionTerminalInputEndpoint.method in gateway.methods())
                assertEquals(registeredMethods, gateway.methods())
                assertEquals(1, previous.calls.size)
                assertTrue(replacement.calls.isEmpty())
                assertEquals(replacement.reply, data(gateway.invoke(SessionTerminalInputEndpoint, request)))
                assertEquals(1, replacement.calls.size)
                assertEquals(request, replacement.calls.single())
                assertEquals(1, previous.calls.size)
                assertEquals(request, previous.calls.single())
                bounded { next.dispose() }
                assertFalse(SessionTerminalInputEndpoint.method in gateway.methods())
                assertTrue(gateway.methods().isEmpty())
                assertEquals(1, replacement.calls.size)
                assertEquals(1, previous.calls.size)
                assertEquals(0, previous.rawCalls)
                assertEquals(0, replacement.rawCalls)
            } finally {
                withContext(NonCancellable) { bounded {
                    oldRuntime.context.dispose()
                    nextRuntime.context.dispose()
                } }
            }
        }
    }

    @Test
    fun foreignAcknowledgementsUseRequestedFullIdentity() = runBlocking<Unit> {
        val api = RecordingApi()
        fixture(api) { _, gateway ->
            for (foreign in foreignKeys()) for (reply in results(foreign)) {
                api.reply = reply
                val before = api.calls.size
                val unknown = assertIs<TerminalMutationResult.UnknownOutcome>(data(gateway.invoke(SessionTerminalInputEndpoint, request)))
                assertEquals(key, unknown.key)
                assertFalse(unknown.reason.contains("private"))
                assertEquals(before + 1, api.calls.size)
            }
            assertEquals(20, api.calls.size)
        }
    }

    @Test
    fun serviceReplacementAndLostAcknowledgementRemainUnknown() = runBlocking<Unit> {
        for (failure in listOf<Throwable?>(null, IllegalStateException("private failure"),
            CancellationException("private service cancellation"))) {
            val api = RecordingApi()
            fixture(api) { runtime, gateway ->
                val entered = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                api.beforeReply = { entered.complete(Unit); release.await(); failure?.let { throw it } }
                val replacementApi = RecordingApi()
                coroutineScope {
                    val pending = async(Dispatchers.Default) { data(gateway.invoke(SessionTerminalInputEndpoint, request)) }
                    var replacement: im.hikaru.harness.runtime.effect.Disposable? = null
                    try {
                        bounded { entered.await() }
                        assertEquals(1, api.calls.size)
                        assertEquals(request, api.calls.single())
                        assertTrue(replacementApi.calls.isEmpty())
                        if (failure == null) {
                            replacement = bounded { runtime.replace(SessionApiKey, replacementApi) }
                            assertSame(replacementApi, runtime.context.get(SessionApiKey))
                            assertEquals(1, api.calls.size)
                            assertTrue(replacementApi.calls.isEmpty())
                        }
                        release.complete(Unit)
                        val unknown = assertIs<TerminalMutationResult.UnknownOutcome>(bounded { pending.await() })
                        assertEquals(key, unknown.key)
                        assertFalse(unknown.reason.contains("private"))
                        assertEquals(1, api.calls.size)
                        assertEquals(request, api.calls.single())
                        assertTrue(replacementApi.calls.isEmpty())
                        // This controlled boundary proves lost acknowledgement only; it owns no native fence.
                        if (failure == null) {
                            val nextRequest = request.copy(input = TerminalInput.Text("replacement only"))
                            assertEquals(replacementApi.reply, data(gateway.invoke(SessionTerminalInputEndpoint, nextRequest)))
                            assertEquals(1, replacementApi.calls.size)
                            assertEquals(nextRequest, replacementApi.calls.single())
                            assertEquals(1, api.calls.size)
                            assertEquals(request, api.calls.single())
                        }
                        assertEquals(0, api.rawCalls)
                        assertEquals(0, replacementApi.rawCalls)
                    } finally {
                        release.complete(Unit)
                        withContext(NonCancellable) { bounded { pending.join(); replacement?.dispose() } }
                    }
                }
            }
        }
    }

    @Test
    fun semanticCancellationAndModifierCaptureHaveNoRetry() = runBlocking<Unit> {
        val api = RecordingApi()
        fixture(api) { _, gateway ->
            coroutineScope {
                val observed = CompletableDeferred<Result<TerminalMutationResult>>()
                val cancelled = launch(Dispatchers.Default) {
                    coroutineContext[Job]!!.cancel()
                    observed.complete(runCatching { data(gateway.invoke(SessionTerminalInputEndpoint, request)) })
                }
                assertIs<CancellationException>(bounded { observed.await() }.exceptionOrNull())
                bounded { cancelled.join() }
                assertTrue(api.calls.isEmpty())
            }
            for (reply in results(key)) coroutineScope {
                api.reply = reply
                val original = TerminalModifier.entries.toMutableSet()
                val input = TerminalInput.KeyPress(TerminalKeyName.Up, original)
                val expected = TerminalInput.KeyPress(TerminalKeyName.Up, TerminalModifier.entries.toSet())
                val sent = SessionTerminalInputRequest(key, input)
                val entered = CompletableDeferred<Unit>()
                val release = CompletableDeferred<Unit>()
                api.beforeReply = { entered.complete(Unit); release.await() }
                val actual = CompletableDeferred<Result<TerminalMutationResult>>()
                val before = api.calls.size
                val writer = launch(Dispatchers.Default) {
                    actual.complete(runCatching { data(gateway.invoke(SessionTerminalInputEndpoint, sent)) })
                }
                try {
                    bounded { entered.await() }
                    original.clear()
                    @Suppress("UNCHECKED_CAST")
                    (input.modifiers as MutableSet<TerminalModifier>).clear()
                    assertEquals(expected, api.calls.last().input)
                    assertNotSame(input.modifiers, (api.calls.last().input as TerminalInput.KeyPress).modifiers)
                    writer.cancel()
                    release.complete(Unit)
                    val returned = assertIs<TerminalMutationResult.UnknownOutcome>(bounded { actual.await() }.getOrThrow())
                    assertEquals(key, returned.key)
                    bounded { writer.join() }
                    assertTrue(writer.isCancelled)
                    assertEquals(before + 1, api.calls.size)
                } finally {
                    release.complete(Unit)
                    withContext(NonCancellable) { bounded { writer.join() } }
                    api.beforeReply = {}
                }
            }
            assertEquals(5, api.calls.size)
            assertEquals(0, api.rawCalls)
        }
    }

    private fun variants(): List<TerminalInput> = listOf(TerminalInput.Text("π中文🙂"), TerminalInput.Paste("a\r\nb\nc")) +
        TerminalKeyName.entries.flatMap { name ->
            (listOf(emptySet<TerminalModifier>(), TerminalModifier.entries.toSet()) + TerminalModifier.entries.map { setOf(it) }).map {
                TerminalInput.KeyPress(name, it, if (name == TerminalKeyName.Character) "~" else null)
            }
        }

    private fun results(key: TerminalKey): List<TerminalMutationResult> = listOf(TerminalMutationResult.Applied(key, 23, TerminalSize(30, 100)),
        TerminalMutationResult.Rejected(key, "private rejection"), TerminalMutationResult.NotFound(key),
        TerminalMutationResult.Unavailable(key, "private unavailable"), TerminalMutationResult.UnknownOutcome(key, "private unknown"))

    private fun foreignKeys() = listOf(key.copy(owner = key.owner.copy(scope = TerminalScope("foreign"))),
        key.copy(owner = key.owner.copy(session = SessionId("foreign"))),
        key.copy(owner = key.owner.copy(incarnation = TerminalIncarnation("foreign"))), key.copy(terminal = TerminalSessionId("foreign")))

    private suspend fun fixture(api: SessionApi?, body: suspend (Runtime, ApiGateway) -> Unit) {
        val runtime = Runtime()
        val gateway = ApiGateway()
        try {
            if (api != null) runtime.provide(SessionApiKey, api)
            gateway.registerSessionApi(runtime.context)
            body(runtime, gateway)
        } finally { withContext(NonCancellable) { bounded { runtime.context.dispose() } } }
    }

    private fun <T : Any> data(result: ApiResult<T>): T {
        assertEquals(ApiResult.SUCCESS_CODE, result.code)
        assertTrue(result.isSuccess)
        assertEquals("", result.msg)
        return assertNotNull(result.data)
    }
    private suspend fun <T> bounded(body: suspend () -> T): T = withTimeout(10_000) { body() }

    private inner class RecordingApi : SessionApi by LegacyApi() {
        val calls = CopyOnWriteArrayList<SessionTerminalInputRequest>()
        var reply: TerminalMutationResult = TerminalMutationResult.Applied(key, 23)
        var beforeReply: suspend () -> Unit = {}
        var rawCalls = 0
        override suspend fun inputTerminal(key: TerminalKey, input: TerminalInput): TerminalMutationResult {
            calls += SessionTerminalInputRequest(key, input)
            beforeReply()
            return reply
        }
        override suspend fun writeTerminal(key: TerminalKey, input: String): TerminalMutationResult {
            rawCalls++
            error("Unexpected raw write")
        }
    }

    private class LegacyApi : SessionApi {
        override suspend fun list(): List<SessionSummary> = error("Unexpected list")
        override suspend fun create(id: SessionId?, options: CreateSessionOptions, agentOptions: AgentOptions): SessionSummary = error("Unexpected create")
        override suspend fun history(id: SessionId): List<Message> = error("Unexpected history")
        override suspend fun prompt(id: SessionId, message: Message): SessionPrompt = error("Unexpected prompt")
        override suspend fun cancel(id: SessionId, keepInbox: Boolean) = error("Unexpected cancel")
    }
}
