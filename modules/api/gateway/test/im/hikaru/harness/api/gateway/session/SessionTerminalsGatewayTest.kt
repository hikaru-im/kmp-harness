package im.hikaru.harness.api.gateway.session

import im.hikaru.contracts.common.ApiResult
import im.hikaru.harness.agent.AgentOptions
import im.hikaru.harness.agent.AgentPlugin
import im.hikaru.harness.api.gateway.ApiGateway
import im.hikaru.harness.llm.Message
import im.hikaru.harness.runtime.Runtime
import im.hikaru.harness.session.CreateSessionOptions
import im.hikaru.harness.session.SessionId
import im.hikaru.harness.session.SessionPlugin
import im.hikaru.harness.session.api.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.CoroutineContext
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class SessionTerminalsGatewayTest {
    private val owner = TerminalOwner(TerminalScope("host"), SessionId("session"), TerminalIncarnation("instance"))
    private val key = TerminalKey(owner, TerminalSessionId("terminal"))
    private val size = TerminalSize(2, 12)
    private val input = "你好\n"
    private val request = TerminalCreateRequest(owner, listOf("/bin/sh", "-c", "printf tail"),
        mapOf("TERM" to "xterm-256color"), size, "/session")

    @Test
    fun missingServiceAndLegacyDefaultsReturnTypedUnavailable() = terminalTest {
        fixture { runtime, gateway ->
            assertEquals(7, gateway.methods().intersect(terminalMethods).size)
            assertUnavailable(gateway)
            val legacy = runtime.provide(SessionApiKey, LegacyApi())
            assertUnavailable(gateway)
            legacy.dispose()

            // Exercise the real default facade with an absent optional Host service.
            installDefaultApi(runtime)
            assertNotNull(runtime.context.get(SessionApiKey))
            assertNull(runtime.context.get(SessionTerminalHostKey))
            assertUnavailable(gateway)
        }
    }

    @Test
    fun forwardsDiscoveryReadsAndEveryMutationExactlyOnce() = terminalTest {
        val api = RecordingApi()
        val source = MutableSnapshot(key)
        val expected = MutableSnapshot(key).snapshot
        val inventory = mutableListOf(source.snapshot.metadata)
        api.createResult = HostTerminalCreateResult.Created(owner, source.snapshot.metadata)
        api.listResult = TerminalListResult.Available(owner, inventory)
        api.readResult = TerminalReadResult.Available(key, source.snapshot)
        fixture(api, hostFacade = true) { _, gateway ->
            val capability = data(gateway.invoke(SessionTerminalCapabilityEndpoint, owner.session))
            assertEquals(api.discoveryResult, capability)
            assertNotSame(owner, assertIs<HostTerminalDiscoveryResult.Available>(capability).owner)

            val argv = request.argv.toMutableList()
            val environment = request.environment.toMutableMap()
            val mutableRequest = request.copy(argv = argv, environment = environment)
            val gate = ReplyGate()
            api.beforeReply = { method -> if (method == CREATE) gate.await() }
            val created = coroutineScope {
                val pending = async { data(gateway.invoke(SessionTerminalCreateEndpoint, mutableRequest)) }
                try {
                    bounded { gate.entered.await() }
                    val forwarded = assertIs<TerminalCreateRequest>(api.calls().single { it.method == CREATE }.request)
                    assertEquals(request, forwarded)
                    assertNotSame(mutableRequest, forwarded)
                    assertNotSame(argv, forwarded.argv)
                    assertNotSame(environment, forwarded.environment)
                    assertNotSame(mutableRequest.owner, forwarded.owner)
                    assertNotSame(mutableRequest.size, forwarded.size)
                    argv.clear()
                    argv += "changed after dispatch"
                    environment.clear()
                    environment["PRIVATE"] = "changed after dispatch"
                    assertEquals(request, forwarded)
                    gate.release.complete(Unit)
                    assertIs<HostTerminalCreateResult.Created>(bounded { pending.await() })
                } finally { gate.release.complete(Unit) }
            }
            api.beforeReply = {}
            val listed = assertIs<TerminalListResult.Available>(data(gateway.invoke(SessionTerminalsEndpoint, owner)))
            val read = assertIs<TerminalReadResult.Available>(data(gateway.invoke(SessionTerminalReadEndpoint, key)))
            val written = data(gateway.invoke(SessionTerminalWriteEndpoint, SessionTerminalWriteRequest(key, input)))
            val resized = data(gateway.invoke(SessionTerminalResizeEndpoint, SessionTerminalResizeRequest(key, size)))
            val closed = data(gateway.invoke(SessionTerminalCloseEndpoint, key))
            assertEquals(api.writeResult, written)
            assertEquals(api.resizeResult, resized)
            assertEquals(api.closeResult, closed)
            assertEquals(listOf(
                Call(DISCOVERY, owner.session), Call(CREATE, request), Call(LIST, owner), Call(READ, key),
                Call(WRITE, SessionTerminalWriteRequest(key, input)),
                Call(RESIZE, SessionTerminalResizeRequest(key, size)), Call(CLOSE, key),
            ), api.calls())

            assertEquals(HostTerminalCreateResult.Created(owner, expected.metadata), created)
            assertEquals(TerminalListResult.Available(owner, listOf(expected.metadata)), listed)
            assertEquals(TerminalReadResult.Available(key, expected), read)
            assertNotSame(inventory, listed.terminals)
            assertMetadataCopied(source.snapshot.metadata, created.metadata)
            assertMetadataCopied(source.snapshot.metadata, listed.terminals.single())
            assertSnapshotCopied(source.snapshot, read.snapshot)
            assertNotSame(size, assertIs<TerminalMutationResult.Applied>(resized).size)
            source.clearCollections()
            inventory.clear()
            assertEquals(expected.metadata, created.metadata)
            assertEquals(listOf(expected.metadata), listed.terminals)
            assertEquals(expected, read.snapshot)
        }

        // Every valid business branch, including NotFound, remains successful typed data.
        val branches = RecordingApi()
        fixture(branches) { _, gateway ->
            for (result in discoveryResults(owner)) {
                branches.discoveryResult = result
                assertEquals(result, data(gateway.invoke(SessionTerminalCapabilityEndpoint, owner.session)))
            }
            for (result in createResults(owner)) {
                branches.createResult = result
                assertEquals(result, data(gateway.invoke(SessionTerminalCreateEndpoint, request)))
            }
            for (result in listResults(owner)) {
                branches.listResult = result
                assertEquals(result, data(gateway.invoke(SessionTerminalsEndpoint, owner)))
            }
            for (result in readResults(key)) {
                branches.readResult = result
                assertEquals(result, data(gateway.invoke(SessionTerminalReadEndpoint, key)))
            }
            for (result in mutationResults(key)) {
                branches.writeResult = result
                branches.resizeResult = result
                branches.closeResult = result
                for ((_, action) in mutationActions(gateway).drop(1)) assertEquals(result, action())
            }
            assertCallCounts(branches, mapOf(DISCOVERY to 3, CREATE to 5, LIST to 3, READ to 4,
                WRITE to 5, RESIZE to 5, CLOSE to 5))
        }
    }

    @Test
    fun mismatchedResponseIdentityBecomesRequestedUnknown() = terminalTest {
        val api = RecordingApi()
        fixture(api) { _, gateway ->
            val foreignOwners = listOf(
                owner.copy(scope = TerminalScope("foreign-host")),
                owner.copy(session = SessionId("foreign-session")),
                owner.copy(incarnation = TerminalIncarnation("replacement")),
            )
            for (result in discoveryResults(foreignOwners[1])) {
                api.discoveryResult = result
                val unavailable = assertIs<HostTerminalDiscoveryResult.Unavailable>(
                    data(gateway.invoke(SessionTerminalCapabilityEndpoint, owner.session)))
                assertEquals(owner.session, unavailable.session)
            }
            for (foreign in foreignOwners) {
                for (result in createResults(foreign)) {
                    api.createResult = result
                    val unknown = assertIs<HostTerminalCreateResult.UnknownOutcome>(
                        data(gateway.invoke(SessionTerminalCreateEndpoint, request)))
                    assertEquals(owner, unknown.owner)
                    assertNull(unknown.key, "Foreign create acknowledgements must not supply a key")
                }
                for (result in listResults(foreign)) {
                    api.listResult = result
                    val unavailable = assertIs<TerminalListResult.Unavailable>(data(gateway.invoke(SessionTerminalsEndpoint, owner)))
                    assertEquals(owner, unavailable.owner)
                }
            }
            val foreignKeys = foreignOwners.map { key.copy(owner = it) } + key.copy(terminal = TerminalSessionId("other-terminal"))
            for (foreign in foreignKeys) {
                for (result in readResults(foreign)) {
                    api.readResult = result
                    val unavailable = assertIs<TerminalReadResult.Unavailable>(data(gateway.invoke(SessionTerminalReadEndpoint, key)))
                    assertEquals(key, unavailable.key)
                }
                for (result in mutationResults(foreign)) {
                    api.writeResult = result
                    api.resizeResult = result
                    api.closeResult = result
                    for ((_, action) in mutationActions(gateway).drop(1)) {
                        assertEquals(key, assertIs<TerminalMutationResult.UnknownOutcome>(action()).key)
                    }
                }
            }

            // Mutable backing rows can invalidate a DTO after its constructor checked it.
            for (corrupt in listOf(foreignKeys.first(), key)) {
                val rows = mutableListOf(metadata(key))
                api.listResult = TerminalListResult.Available(owner, rows)
                rows += metadata(corrupt)
                val unavailable = assertIs<TerminalListResult.Unavailable>(data(gateway.invoke(SessionTerminalsEndpoint, owner)))
                assertEquals(owner, unavailable.owner)
            }
            assertCallCounts(api, mapOf(DISCOVERY to 3, CREATE to 15, LIST to 11, READ to 16,
                WRITE to 20, RESIZE to 20, CLOSE to 20))
        }
    }

    @Test
    fun lostCreateAcknowledgementHasNoInventedKeyAndNoRetry() = terminalTest {
        val failures = listOf<Throwable>(
            IllegalArgumentException("private argv/environment"),
            IllegalStateException("private argv/environment"),
            CancellationException("private service cancellation"),
        )
        for (failure in failures) {
            val api = RecordingApi()
            val gate = ReplyGate()
            val owned = mutableListOf<TerminalMetadata>()
            api.beforeReply = { method ->
                if (method == CREATE) {
                    // Record the accepted resource before losing its acknowledgement.
                    owned += metadata(key)
                    gate.await()
                    throw failure
                }
            }
            fixture(api) { _, gateway ->
                coroutineScope {
                    val pending = async { data(gateway.invoke(SessionTerminalCreateEndpoint, request)) }
                    try {
                        bounded { gate.entered.await() }
                        assertEquals(listOf(metadata(key)), owned)
                        assertEquals(1, api.calls().count { it.method == CREATE })
                        gate.release.complete(Unit)
                        val unknown = assertIs<HostTerminalCreateResult.UnknownOutcome>(bounded { pending.await() })
                        assertEquals(owner, unknown.owner)
                        assertNull(unknown.key)
                        assertFalse(unknown.reason.contains("private"))
                        api.listResult = TerminalListResult.Available(owner, owned.toList())
                        val recovered = assertIs<TerminalListResult.Available>(data(gateway.invoke(SessionTerminalsEndpoint, owner)))
                        assertEquals(listOf(key), recovered.terminals.map { it.key })
                        assertCallCounts(api, mapOf(CREATE to 1, LIST to 1))
                    } finally { gate.release.complete(Unit) }
                }
            }
        }

        val api = RecordingApi()
        fixture(api) { _, gateway ->
            for (known in listOf(null, key)) {
                val response = HostTerminalCreateResult.UnknownOutcome(owner, known, "host reservation")
                api.createResult = response
                assertEquals(response, data(gateway.invoke(SessionTerminalCreateEndpoint, request)))
            }
            // A foreign but internally consistent acknowledgement is still untrusted.
            val foreign = owner.copy(incarnation = TerminalIncarnation("untrusted"))
            api.createResult = HostTerminalCreateResult.Created(foreign, metadata(key.copy(owner = foreign)))
            val unknown = assertIs<HostTerminalCreateResult.UnknownOutcome>(data(gateway.invoke(SessionTerminalCreateEndpoint, request)))
            assertEquals(owner, unknown.owner)
            assertNull(unknown.key)
            assertCallCounts(api, mapOf(CREATE to 3))
        }
    }

    @Test
    fun cancellationBeforeDispatchAndAfterDispatchRemainDistinct() = terminalTest {
        val api = RecordingApi()
        fixture(api) { _, gateway ->
            for ((method, action) in allActions(gateway)) {
                val boundaryEntered = CompletableDeferred<Unit>()
                scheduledCall(action = { boundaryEntered.complete(Unit); action() }) { call, dispatcher ->
                    val firstStep = dispatcher.next()
                    call.job.cancel()
                    firstStep.run()
                    bounded { boundaryEntered.await() }
                    val outcome = bounded { call.observed.await() }
                    assertIs<CancellationException>(outcome.exceptionOrNull(), method)
                    bounded { call.job.join() }
                    assertTrue(api.calls().isEmpty(), "$method must not reach the service before dispatch")
                }
            }

            // A cancelled Deferred would hide a returned Unknown. Observe the value inside
            // the actual caller coroutine through a separate, parentless deferred instead.
            for ((method, action) in mutationActions(gateway)) {
                val replies: List<Any> = if (method == CREATE) createResults(owner) else mutationResults(key)
                for (reply in replies) {
                    api.setReply(method, reply)
                    val before = api.calls().size
                    val gate = ReplyGate()
                    api.beforeReply = { assertEquals(method, it); gate.await() }
                    scheduledCall(action, gate) { call, dispatcher ->
                        dispatcher.next().run()
                        bounded { gate.entered.await() }
                        assertEquals(before + 1, api.calls().size)
                        call.job.cancel()
                        gate.release.complete(Unit)
                        // This step is the real IO-to-caller-dispatcher return boundary.
                        val returnStep = dispatcher.next()
                        assertFalse(call.observed.isCompleted, "$method has not returned across the scheduler gate")
                        returnStep.run()
                        val returned = bounded { call.observed.await() }.getOrThrow()
                        if (method == CREATE) {
                            val unknown = assertIs<HostTerminalCreateResult.UnknownOutcome>(returned)
                            assertEquals(owner, unknown.owner)
                            val trustedKey = when (reply) {
                                is HostTerminalCreateResult.Created -> reply.metadata.key
                                is HostTerminalCreateResult.UnknownOutcome -> reply.key
                                else -> null
                            }
                            assertEquals(trustedKey, unknown.key)
                        } else {
                            assertEquals(key, assertIs<TerminalMutationResult.UnknownOutcome>(returned).key)
                        }
                        bounded { call.job.join() }
                        assertTrue(call.job.isCancelled)
                        assertEquals(before + 1, api.calls().size, "$method must never retry")
                    }
                }
            }

            for ((method, action) in readActions(gateway)) {
                for (cancelWhileSuspended in listOf(true, false)) {
                    val before = api.calls().size
                    val gate = ReplyGate()
                    api.beforeReply = { assertEquals(method, it); gate.await() }
                    scheduledCall(action, gate) { call, dispatcher ->
                        dispatcher.next().run()
                        bounded { gate.entered.await() }
                        val publishStep = if (cancelWhileSuspended) {
                            call.job.cancel()
                            dispatcher.next()
                        } else {
                            gate.release.complete(Unit)
                            dispatcher.next().also { call.job.cancel() }
                        }
                        assertFalse(call.observed.isCompleted)
                        publishStep.run()
                        val outcome = bounded { call.observed.await() }
                        assertIs<CancellationException>(outcome.exceptionOrNull(), method)
                        assertEquals(before + 1, api.calls().size)
                        bounded { call.job.join() }
                    }
                }
            }
            assertCallCounts(api, mapOf(DISCOVERY to 2, CREATE to 5, LIST to 2, READ to 2,
                WRITE to 5, RESIZE to 5, CLOSE to 5))
        }
    }

    @Test
    fun registrationAndOwnerDisposalRemoveAllTerminalEndpoints() = terminalTest {
        for (disposeOwner in listOf(false, true)) {
            val runtime = Runtime()
            val replacementRuntime = Runtime()
            val gateway = ApiGateway()
            val api = RecordingApi()
            val gates = terminalMethods.associateWith { ReplyGate() }
            api.beforeReply = { method -> gates.getValue(method).await() }
            try {
                runtime.provide(SessionApiKey, api)
                val oldRegistration = gateway.registerSessionApi(runtime.context)
                assertTrue(gateway.methods().containsAll(terminalMethods))
                coroutineScope {
                    val pending = allActions(gateway).associate { (method, action) -> method to async { action() } }
                    try {
                        for (gate in gates.values) bounded { gate.entered.await() }
                        assertCallCounts(api, terminalMethods.associateWith { 1 })
                        if (disposeOwner) runtime.context.dispose() else oldRegistration.dispose()
                        assertTrue(gateway.methods().intersect(terminalMethods).isEmpty())
                        assertMissingEndpoints(gateway)
                        for (gate in gates.values) gate.release.complete(Unit)
                        val discovery = assertIs<HostTerminalDiscoveryResult.Unavailable>(bounded { pending.getValue(DISCOVERY).await() })
                        assertEquals(owner.session, discovery.session)
                        val listed = assertIs<TerminalListResult.Unavailable>(bounded { pending.getValue(LIST).await() })
                        assertEquals(owner, listed.owner)
                        val read = assertIs<TerminalReadResult.Unavailable>(bounded { pending.getValue(READ).await() })
                        assertEquals(key, read.key)
                        val created = assertIs<HostTerminalCreateResult.UnknownOutcome>(bounded { pending.getValue(CREATE).await() })
                        assertEquals(owner, created.owner)
                        assertEquals(key, created.key, "A validated acknowledgement may retain the actual key")
                        for (method in listOf(WRITE, RESIZE, CLOSE)) {
                            assertEquals(key, assertIs<TerminalMutationResult.UnknownOutcome>(bounded { pending.getValue(method).await() }).key)
                        }
                        assertCallCounts(api, terminalMethods.associateWith { 1 })
                    } finally { for (gate in gates.values) gate.release.complete(Unit) }
                }

                val replacement = RecordingApi()
                replacementRuntime.provide(SessionApiKey, replacement)
                val newRegistration = gateway.registerSessionApi(replacementRuntime.context)
                oldRegistration.dispose()
                runtime.context.dispose()
                oldRegistration.dispose()
                assertTrue(gateway.methods().containsAll(terminalMethods), "Old handles must not remove replacement registrations")
                for ((method, action) in allActions(gateway)) {
                    val response = action()
                    assertEquals(replacement.reply(method), response, method)
                }
                assertCallCounts(replacement, terminalMethods.associateWith { 1 })
                newRegistration.dispose()
                assertTrue(gateway.methods().intersect(terminalMethods).isEmpty())
                assertMissingEndpoints(gateway)
            } finally {
                for (gate in gates.values) gate.release.complete(Unit)
                withContext(NonCancellable) {
                    bounded { replacementRuntime.context.dispose() }
                    bounded { runtime.context.dispose() }
                }
            }
        }
    }

    private fun terminalTest(block: suspend CoroutineScope.() -> Unit) = runTest(timeout = 10.seconds) {
        // IO must share a real-time bound, rather than racing a virtual timeout.
        withContext(Dispatchers.Default) { withTimeout(BOUND) { block() } }
    }

    private suspend fun <T> bounded(block: suspend CoroutineScope.() -> T): T = withTimeout(BOUND) { block() }

    private suspend fun fixture(
        api: SessionApi? = null,
        hostFacade: Boolean = false,
        block: suspend (Runtime, ApiGateway) -> Unit,
    ) {
        val runtime = Runtime()
        val gateway = ApiGateway()
        try {
            if (hostFacade) {
                installDefaultApi(runtime)
                runtime.provide(SessionTerminalHostKey, HostAdapter(assertNotNull(api)))
            } else if (api != null) runtime.provide(SessionApiKey, api)
            gateway.registerSessionApi(runtime.context)
            block(runtime, gateway)
        } finally { withContext(NonCancellable) { bounded { runtime.context.dispose() } } }
    }

    private suspend fun installDefaultApi(runtime: Runtime) {
        runtime.install(SessionPlugin())
        runtime.install(AgentPlugin())
        runtime.install(SessionApiPlugin())
    }

    private fun <T : Any> data(result: ApiResult<T>): T {
        assertEquals(ApiResult.SUCCESS_CODE, result.code)
        assertTrue(result.isSuccess)
        assertEquals("", result.msg)
        return assertNotNull(result.data)
    }

    private suspend fun assertUnavailable(gateway: ApiGateway) {
        assertEquals(owner.session, assertIs<HostTerminalDiscoveryResult.Unavailable>(
            data(gateway.invoke(SessionTerminalCapabilityEndpoint, owner.session))).session)
        assertEquals(owner, assertIs<HostTerminalCreateResult.Unavailable>(
            data(gateway.invoke(SessionTerminalCreateEndpoint, request))).owner)
        assertEquals(owner, assertIs<TerminalListResult.Unavailable>(data(gateway.invoke(SessionTerminalsEndpoint, owner))).owner)
        assertEquals(key, assertIs<TerminalReadResult.Unavailable>(data(gateway.invoke(SessionTerminalReadEndpoint, key))).key)
        for ((_, action) in mutationActions(gateway).drop(1)) assertEquals(key, assertIs<TerminalMutationResult.Unavailable>(action()).key)
    }

    private suspend fun assertMissingEndpoints(gateway: ApiGateway) {
        val results = listOf(
            DISCOVERY to gateway.invoke(SessionTerminalCapabilityEndpoint, owner.session),
            CREATE to gateway.invoke(SessionTerminalCreateEndpoint, request),
            LIST to gateway.invoke(SessionTerminalsEndpoint, owner),
            READ to gateway.invoke(SessionTerminalReadEndpoint, key),
            WRITE to gateway.invoke(SessionTerminalWriteEndpoint, SessionTerminalWriteRequest(key, input)),
            RESIZE to gateway.invoke(SessionTerminalResizeEndpoint, SessionTerminalResizeRequest(key, size)),
            CLOSE to gateway.invoke(SessionTerminalCloseEndpoint, key),
        )
        for ((method, result) in results) {
            assertEquals(404, result.code, method)
            assertFalse(result.isSuccess, method)
            assertNull(result.data, method)
            assertEquals("Harness API endpoint '$method' 未注册", result.msg)
        }
    }

    private fun readActions(gateway: ApiGateway): List<Pair<String, suspend () -> Any>> = listOf(
        action(DISCOVERY) { data(gateway.invoke(SessionTerminalCapabilityEndpoint, owner.session)) },
        action(LIST) { data(gateway.invoke(SessionTerminalsEndpoint, owner)) },
        action(READ) { data(gateway.invoke(SessionTerminalReadEndpoint, key)) },
    )

    private fun mutationActions(gateway: ApiGateway): List<Pair<String, suspend () -> Any>> = listOf(
        action(CREATE) { data(gateway.invoke(SessionTerminalCreateEndpoint, request)) },
        action(WRITE) { data(gateway.invoke(SessionTerminalWriteEndpoint, SessionTerminalWriteRequest(key, input))) },
        action(RESIZE) { data(gateway.invoke(SessionTerminalResizeEndpoint, SessionTerminalResizeRequest(key, size))) },
        action(CLOSE) { data(gateway.invoke(SessionTerminalCloseEndpoint, key)) },
    )

    private fun action(method: String, operation: suspend () -> Any): Pair<String, suspend () -> Any> = method to operation

    private fun allActions(gateway: ApiGateway): List<Pair<String, suspend () -> Any>> = readActions(gateway) + mutationActions(gateway)

    private fun metadata(requested: TerminalKey): TerminalMetadata = MutableSnapshot(requested).snapshot.metadata

    private fun discoveryResults(requested: TerminalOwner): List<HostTerminalDiscoveryResult> = listOf(
        HostTerminalDiscoveryResult.Available(requested.session, requested, "/session", 8),
        HostTerminalDiscoveryResult.Rejected(requested.session, "not live"),
        HostTerminalDiscoveryResult.Unavailable(requested.session, "unsupported"),
    )

    private fun createResults(requested: TerminalOwner): List<HostTerminalCreateResult> {
        val actual = key.copy(owner = requested)
        return listOf(
            HostTerminalCreateResult.Created(requested, metadata(actual)),
            HostTerminalCreateResult.Rejected(requested, "before dispatch"),
            HostTerminalCreateResult.Unavailable(requested, "unsupported"),
            HostTerminalCreateResult.UnknownOutcome(requested, null, "no acknowledgement"),
            HostTerminalCreateResult.UnknownOutcome(requested, actual, "known reservation"),
        )
    }

    private fun listResults(requested: TerminalOwner): List<TerminalListResult> = listOf(
        TerminalListResult.Available(requested, listOf(metadata(key.copy(owner = requested)))),
        TerminalListResult.Rejected(requested, "not live"),
        TerminalListResult.Unavailable(requested, "unsupported"),
    )

    private fun readResults(requested: TerminalKey): List<TerminalReadResult> = listOf(
        TerminalReadResult.Available(requested, MutableSnapshot(requested).snapshot),
        TerminalReadResult.NotFound(requested),
        TerminalReadResult.Rejected(requested, "stale"),
        TerminalReadResult.Unavailable(requested, "unsupported"),
    )

    private fun mutationResults(requested: TerminalKey): List<TerminalMutationResult> = listOf(
        TerminalMutationResult.Applied(requested, 7, size),
        TerminalMutationResult.NotFound(requested),
        TerminalMutationResult.Rejected(requested, "closed"),
        TerminalMutationResult.Unavailable(requested, "unsupported"),
        TerminalMutationResult.UnknownOutcome(requested, "host uncertain"),
    )

    private fun assertMetadataCopied(source: TerminalMetadata, copy: TerminalMetadata) {
        assertEquals(source, copy)
        assertNotSame(source, copy)
        assertNotSame(source.key, copy.key)
        assertNotSame(source.key.owner, copy.key.owner)
        assertNotSame(source.argv, copy.argv)
    }

    private fun assertSnapshotCopied(source: TerminalSnapshot, copy: TerminalSnapshot) {
        assertEquals(source, copy)
        assertNotSame(source, copy)
        assertMetadataCopied(source.metadata, copy.metadata)
        assertNotSame(source.size, copy.size)
        assertNotSame(source.cursor, copy.cursor)
        for ((originalLines, copiedLines) in listOf(source.screen to copy.screen, source.history to copy.history)) {
            assertNotSame(originalLines, copiedLines)
            for ((originalLine, copiedLine) in originalLines.zip(copiedLines)) {
                assertNotSame(originalLine, copiedLine)
                assertNotSame(originalLine.runs, copiedLine.runs)
                for ((originalRun, copiedRun) in originalLine.runs.zip(copiedLine.runs)) {
                    assertNotSame(originalRun, copiedRun)
                    assertNotSame(originalRun.style, copiedRun.style)
                    assertNotSame(originalRun.style.attributes, copiedRun.style.attributes)
                    assertNotSame(originalRun.style.foreground, copiedRun.style.foreground)
                    assertNotSame(originalRun.style.background, copiedRun.style.background)
                }
            }
        }
    }

    private fun assertCallCounts(api: RecordingApi, expected: Map<String, Int>) {
        val calls = api.calls()
        assertEquals(expected, calls.groupingBy { it.method }.eachCount())
        assertEquals(expected.values.sum(), calls.size)
    }

    private data class Call(val method: String, val request: Any)

    private inner class RecordingApi : LegacyApi() {
        private val events = Channel<Call>(Channel.UNLIMITED)
        private val received = mutableListOf<Call>()
        var beforeReply: suspend (String) -> Unit = {}
        var discoveryResult: HostTerminalDiscoveryResult = HostTerminalDiscoveryResult.Available(owner.session, owner, "/session", 8)
        var createResult: HostTerminalCreateResult = HostTerminalCreateResult.Created(owner, metadata(key))
        var listResult: TerminalListResult = TerminalListResult.Available(owner, listOf(metadata(key)))
        var readResult: TerminalReadResult = TerminalReadResult.Available(key, MutableSnapshot(key).snapshot)
        var writeResult: TerminalMutationResult = TerminalMutationResult.Applied(key, input.encodeToByteArray().size)
        var resizeResult: TerminalMutationResult = TerminalMutationResult.Applied(key, size = size)
        var closeResult: TerminalMutationResult = TerminalMutationResult.Applied(key)

        // Only the test caller drains this channel; concurrent service dispatches only send.
        fun calls(): List<Call> {
            while (true) received += events.tryReceive().getOrNull() ?: break
            return received.toList()
        }

        private suspend fun record(method: String, request: Any) {
            events.trySend(Call(method, request)).getOrThrow()
            beforeReply(method)
        }

        fun reply(method: String): Any = when (method) {
            DISCOVERY -> discoveryResult
            CREATE -> createResult
            LIST -> listResult
            READ -> readResult
            WRITE -> writeResult
            RESIZE -> resizeResult
            CLOSE -> closeResult
            else -> error("Unexpected terminal method: $method")
        }

        fun setReply(method: String, response: Any) {
            when (method) {
                CREATE -> createResult = assertIs<HostTerminalCreateResult>(response)
                WRITE -> writeResult = assertIs<TerminalMutationResult>(response)
                RESIZE -> resizeResult = assertIs<TerminalMutationResult>(response)
                CLOSE -> closeResult = assertIs<TerminalMutationResult>(response)
                else -> error("Unexpected mutation: $method")
            }
        }

        override suspend fun terminalCapability(session: SessionId): HostTerminalDiscoveryResult {
            record(DISCOVERY, session); return discoveryResult
        }
        override suspend fun createTerminal(request: TerminalCreateRequest): HostTerminalCreateResult {
            record(CREATE, request); return createResult
        }
        override suspend fun terminals(owner: TerminalOwner): TerminalListResult {
            record(LIST, owner); return listResult
        }
        override suspend fun readTerminal(key: TerminalKey): TerminalReadResult {
            record(READ, key); return readResult
        }
        override suspend fun writeTerminal(key: TerminalKey, input: String): TerminalMutationResult {
            record(WRITE, SessionTerminalWriteRequest(key, input)); return writeResult
        }
        override suspend fun resizeTerminal(key: TerminalKey, size: TerminalSize): TerminalMutationResult {
            record(RESIZE, SessionTerminalResizeRequest(key, size)); return resizeResult
        }
        override suspend fun closeTerminal(key: TerminalKey): TerminalMutationResult {
            record(CLOSE, key); return closeResult
        }
    }

    private class HostAdapter(private val api: SessionApi) : SessionTerminalHostService {
        override suspend fun discover(session: SessionId) = api.terminalCapability(session)
        override suspend fun create(request: TerminalCreateRequest) = api.createTerminal(request)
        override suspend fun list(owner: TerminalOwner) = api.terminals(owner)
        override suspend fun read(key: TerminalKey) = api.readTerminal(key)
        override suspend fun write(key: TerminalKey, input: String) = api.writeTerminal(key, input)
        override suspend fun resize(key: TerminalKey, size: TerminalSize) = api.resizeTerminal(key, size)
        override suspend fun close(key: TerminalKey) = api.closeTerminal(key)
    }

    private class MutableSnapshot(key: TerminalKey) {
        val argv = mutableListOf("/bin/sh", "-c", "printf tail")
        val screenAttributes = mutableSetOf(TerminalTextAttribute.Bold, TerminalTextAttribute.Italic, TerminalTextAttribute.Underlined)
        val historyAttributes = mutableSetOf(TerminalTextAttribute.Dim, TerminalTextAttribute.Inverse)
        val screenRuns = mutableListOf(TerminalTextRun(2, 4, "tail", TerminalTextStyle(
            TerminalColorValue.Indexed(9), TerminalColorValue.Rgb(10, 20, 30), screenAttributes)))
        val emptyRuns = mutableListOf<TerminalTextRun>()
        val historyRuns = mutableListOf(TerminalTextRun(0, 3, "old", TerminalTextStyle(
            TerminalColorValue.Rgb(40, 50, 60), TerminalColorValue.Indexed(7), historyAttributes)))
        val screen = mutableListOf(TerminalLineSnapshot(screenRuns, wrapped = true), TerminalLineSnapshot(emptyRuns))
        val history = mutableListOf(TerminalLineSnapshot(historyRuns, wrapped = false))
        val snapshot = TerminalSnapshot(
            TerminalMetadata(key, "/session", argv, TerminalLifecycle.Exited(7), TerminalInputState.Closed),
            TerminalSize(2, 12), screen, history, TerminalCursor(6, 0, false), true, 42, "\u001b[31mtail\r\n", true,
        )

        fun clearCollections() {
            argv.clear()
            screenAttributes.clear()
            historyAttributes.clear()
            screenRuns.clear()
            emptyRuns += TerminalTextRun(0, 1, "x", TerminalTextStyle())
            historyRuns.clear()
            screen.clear()
            history.clear()
        }
    }

    private class ReplyGate {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        suspend fun await() {
            entered.complete(Unit)
            withTimeout(BOUND) { release.await() }
        }
    }

    /** Queues real continuations without changing their Job or dispatch context. */
    private class GateDispatcher : CoroutineDispatcher() {
        private val steps = Channel<Runnable>(Channel.UNLIMITED)
        override fun dispatch(context: CoroutineContext, block: Runnable) {
            if (steps.trySend(block).isFailure) Dispatchers.Default.dispatch(context, block)
        }
        suspend fun next(): Runnable = withTimeout(BOUND) { steps.receive() }
        fun releaseAll() {
            // Closing first makes racing dispatches fall back to Default instead of
            // leaving a NonCancellable IO return stranded during failed-test cleanup.
            steps.close()
            while (true) (steps.tryReceive().getOrNull() ?: break).run()
        }
    }

    private data class ScheduledCall<T>(val job: Job, val observed: CompletableDeferred<Result<T>>)

    private suspend fun <T> scheduledCall(
        action: suspend () -> T,
        gate: ReplyGate? = null,
        block: suspend (ScheduledCall<T>, GateDispatcher) -> Unit,
    ) {
        val dispatcher = GateDispatcher()
        val parent = Job()
        val observed = CompletableDeferred<Result<T>>()
        val job = CoroutineScope(parent + dispatcher).launch(start = CoroutineStart.ATOMIC) {
            // ATOMIC reaches the gateway boundary even if cancelled while queued.
            observed.complete(runCatching { action() })
        }
        try { block(ScheduledCall(job, observed), dispatcher) } finally {
            gate?.release?.complete(Unit)
            parent.cancel()
            dispatcher.releaseAll()
            withContext(NonCancellable) { withTimeout(BOUND) { job.join() } }
        }
    }

    private open class LegacyApi : SessionApi {
        override suspend fun list(): List<SessionSummary> = error("Unexpected nonterminal list")
        override suspend fun create(id: SessionId?, options: CreateSessionOptions, agentOptions: AgentOptions): SessionSummary =
            error("Unexpected session/model creation")
        override suspend fun history(id: SessionId): List<Message> = error("Unexpected history")
        override suspend fun prompt(id: SessionId, message: Message): SessionPrompt = error("Unexpected model request")
        override suspend fun cancel(id: SessionId, keepInbox: Boolean) = error("Unexpected session cancellation")
    }

    private companion object {
        const val BOUND = 10_000L
        const val DISCOVERY = "session.terminal.capability"
        const val CREATE = "session.terminal.create"
        const val LIST = "session.terminal.list"
        const val READ = "session.terminal.read"
        const val WRITE = "session.terminal.write"
        const val RESIZE = "session.terminal.resize"
        const val CLOSE = "session.terminal.close"
        val terminalMethods = setOf(DISCOVERY, CREATE, LIST, READ, WRITE, RESIZE, CLOSE)
    }
}
