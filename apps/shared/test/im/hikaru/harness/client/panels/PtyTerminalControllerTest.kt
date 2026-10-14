package im.hikaru.harness.client.panels

import im.hikaru.contracts.common.ApiResult
import im.hikaru.contracts.harness.identity.HostDescription
import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.contracts.harness.protocol.ProtocolVersion
import im.hikaru.harness.client.connection.*
import im.hikaru.harness.client.conversation.ConversationHostScope
import kotlinx.coroutines.*
import kotlin.test.*

class PtyTerminalControllerTest {
    @Test fun openingRefreshingAndRebindingNeverCreateTerminal() = runBlocking<Unit> {
        fixture(this) { f ->
            f.open(); f.controller.refresh(); f.until { f.controller.state.value.discovery is PtyLoad.Ready }
            f.controller.setVisible(false); f.controller.setVisible(true)
            f.controller.bind(f.owner.copy(host = f.owner.host.copy(generation = 2)))
            f.until { f.controller.state.value.inventory is PtyLoad.Ready }
            assertEquals(0, f.api.creates); assertEquals(0, f.api.inputs.size); assertEquals(0, f.api.closes)
        }
    }
    @Test fun explicitCreateDispatchesOnceAndUnknownDoesNotRetry() = runBlocking<Unit> {
        fixture(this) { f ->
            val gate = CompletableDeferred<Unit>()
            f.api.creation = { r -> gate.await(); TerminalCreateResult.UnknownOutcome(r.owner, null, "lost acknowledgement") }
            f.open(); assertTrue(f.controller.createTerminal()); assertFalse(f.controller.createTerminal())
            f.until { f.api.creates == 1 }; gate.complete(Unit)
            f.until { f.controller.state.value.createUncertain }
            assertNull(f.controller.state.value.selectedKey)
            f.controller.refresh(); f.until { f.controller.state.value.discovery is PtyLoad.Ready }
            f.controller.setVisible(false); f.controller.setVisible(true)
            assertFalse(f.controller.createTerminal()); assertEquals(1, f.api.creates)
            f.controller.select(f.api.key); f.until { f.controller.state.value.snapshot != null }
            assertTrue(f.controller.state.value.createUncertain)
        }
    }
    @Test fun ownerConnectionAndFullKeyFenceLateResults() = runBlocking<Unit> {
        fixture(this) { f ->
            f.open()
            val gate = CompletableDeferred<Unit>()
            f.api.reading = { key -> withContext(NonCancellable) { gate.await() }; TerminalReadResult.Available(key, f.api.snapshot()) }
            f.controller.select(f.api.key); f.until { f.api.reads > 0 }
            val other = ControlledApi().also { it.text = "new-owner" }
            val otherConnection = ControlledConnection(other)
            val next = f.owner.copy(connection = otherConnection, host = f.owner.host.copy(generation = 1))
            f.controller.bind(next)
            assertFalse(f.controller.isBoundTo(f.owner)); assertTrue(f.controller.isBoundTo(next))
            gate.complete(Unit); f.until { f.controller.state.value.inventory is PtyLoad.Ready }
            assertEquals(listOf(other.metadata()), assertIs<PtyLoad.Ready<*>>(f.controller.state.value.inventory).value)
            assertNull(f.controller.state.value.snapshot)
            f.controller.setVisible(true)
            f.controller.select(other.key); f.until { f.controller.state.value.snapshot != null }
            assertEquals(other.key, f.controller.state.value.snapshot?.metadata?.key)
            assertEquals("new-owner", f.controller.state.value.snapshot?.screen?.first()?.text)
            other.reading = { TerminalReadResult.NotFound(it.copy(owner = it.owner.copy(incarnation = TerminalIncarnation("foreign")))) }
            f.controller.refresh(); f.until { f.controller.state.value.screen is PtyLoad.Error }
            assertEquals(other.key, f.controller.state.value.snapshot?.metadata?.key)
            assertEquals(0, f.api.inputs.size)
        }
    }
    @Test fun snapshotRevisionHighWaterRejectsStaleOrConflictingData() = runBlocking<Unit> {
        fixture(this) { f ->
            f.api.revision = 8; f.openSelected()
            f.api.revision = 3
            val before = f.api.reads; f.until { f.api.reads > before + 1 }
            assertEquals(8L, f.controller.state.value.snapshot?.revision)
            f.api.revision = 8; f.api.text = "different"
            f.until { f.controller.state.value.screen is PtyLoad.Error }
            assertEquals("ready", f.controller.state.value.snapshot?.screen?.first()?.text)
            f.api.revision = 9; f.controller.refresh()
            f.until { f.controller.state.value.snapshot?.revision == 9L }
        }
    }
    @Test fun orderedBoundedInputRejectsOverflowWithoutReplayingEvents() = runBlocking<Unit> {
        fixture(this) { f ->
            f.openSelected()
            val gate = CompletableDeferred<Unit>()
            f.api.input = { key, _ -> gate.await(); TerminalMutationResult.Applied(key) }
            assertTrue(f.controller.input(TerminalInput.Text("first"))); f.until { f.api.inputs.size == 1 }
            repeat(32) { assertTrue(f.controller.input(TerminalInput.Text("q" + it))) }
            assertFalse(f.controller.input(TerminalInput.Text("overflow")))
            gate.complete(Unit); f.until { f.api.inputs.size == 33 }
            assertEquals(listOf("first") + (0..31).map { "q" + it }, f.api.inputs.map { (it.second as TerminalInput.Text).text })
            assertTrue(f.controller.input(TerminalInput.Text("normal"))); f.until { f.api.inputs.size == 34 }
            val secondGate = CompletableDeferred<Unit>()
            f.api.input = { key, _ -> secondGate.await(); TerminalMutationResult.Applied(key) }
            f.controller.input(TerminalInput.Text("hold")); f.until { f.api.inputs.size == 35 }
            assertTrue(f.controller.input(TerminalInput.Text("x".repeat(65_536))))
            assertFalse(f.controller.input(TerminalInput.Text("中")))
            assertTrue(f.controller.input(TerminalInput.KeyPress(TerminalKeyName.Enter)))
            secondGate.complete(Unit); f.until { f.api.inputs.size == 37 }
            assertEquals(2, f.controller.state.value.receipts.count { it.outcome == PtyOutcome.Rejected })
        }
    }
    @Test fun unknownFencesInputAndResizeButAllowsExplicitClose() = runBlocking<Unit> {
        fixture(this) { f ->
            f.openSelected()
            val gate = CompletableDeferred<Unit>()
            f.api.input = { key, _ -> gate.await(); TerminalMutationResult.UnknownOutcome(key, "lost") }
            f.controller.input(TerminalInput.Text("first")); f.until { f.api.inputs.size == 1 }
            f.controller.input(TerminalInput.Paste("queued"))
            gate.complete(Unit); f.until { f.api.key in f.controller.state.value.fencedKeys }
            assertFalse(f.controller.input(TerminalInput.Text("later")))
            assertFalse(f.controller.viewport(TerminalSize(10, 40)))
            f.controller.refresh(); f.until { f.controller.state.value.screen is PtyLoad.Ready }
            assertFalse(f.controller.input(TerminalInput.Text("reopen")))
            assertTrue(f.controller.closeTerminal()); assertFalse(f.controller.closeTerminal())
            f.until { f.api.closes == 1 }
            assertEquals(1, f.api.inputs.size); assertEquals(0, f.api.resizes.size)
            assertTrue(f.controller.state.value.receipts.any { it.outcome == PtyOutcome.Unsent })
        }
    }
    @Test fun closingViewStopsObservationWithoutClosingProcess() = runBlocking<Unit> {
        fixture(this) { f ->
            f.openSelected(); f.controller.setVisible(false)
            val count = f.api.reads; delay(30); assertEquals(count, f.api.reads)
            assertEquals(0, f.api.closes)
            f.controller.setVisible(true); f.until { f.api.reads > count }
            assertEquals(f.api.key, f.controller.state.value.selectedKey)
            assertEquals(0, f.api.creates)
            f.controller.close(); val stopped = f.api.reads; delay(30); assertEquals(stopped, f.api.reads)
        }
    }
    @Test fun resizeDeduplicatesAndExitRetainsFinalScreen() = runBlocking<Unit> {
        fixture(this) { f ->
            f.openSelected(); val size = TerminalSize(10, 40)
            assertTrue(f.controller.viewport(size)); assertFalse(f.controller.viewport(size))
            f.until { f.controller.state.value.confirmedSizes[f.api.key] == size }
            assertFalse(f.controller.viewport(size)); assertEquals(listOf(size), f.api.resizes)
            val readsBeforeExit = f.api.reads
            f.api.lifecycle = TerminalLifecycle.Exited(7); f.api.revision++
            f.until { f.controller.state.value.snapshot?.metadata?.lifecycle == TerminalLifecycle.Exited(7) }
            f.until { f.api.reads >= readsBeforeExit + 2 }
            val finalReads = f.api.reads; delay(30); assertEquals(finalReads, f.api.reads)
            assertFalse(f.controller.state.value.canInput); assertNotNull(f.controller.state.value.snapshot)
            assertEquals(0, f.api.closes)
        }
    }

    private suspend fun fixture(scope: CoroutineScope, body: suspend (Fixture) -> Unit) {
        val f = Fixture(scope)
        try { body(f) } finally { f.controller.close() }
    }
    private class Fixture(scope: CoroutineScope) {
        val api = ControlledApi()
        val connection = ControlledConnection(api)
        val owner = ThreadPanelsOwner(connection, ConversationHostScope(HostId("test-host"), 0, null), SessionId("parent"))
        val provider = object : TerminalLaunchProvider {
            override fun availability(owner: ThreadPanelsOwner): String? = null
            override fun prepare(owner: ThreadPanelsOwner, discovery: TerminalDiscoveryResult.Available) =
                TerminalLaunchPreparation.Ready(TerminalLaunchPlan(discovery.owner, listOf("shell"), emptyMap(), discovery.canonicalCreationCwd))
        }
        val controller = PtyTerminalController(scope, provider, 5)
        suspend fun open() { controller.bind(owner); controller.setVisible(true); until { controller.state.value.inventory is PtyLoad.Ready } }
        suspend fun openSelected() { open(); controller.select(api.key); until { controller.state.value.canInput } }
        suspend fun until(predicate: () -> Boolean) { withTimeout(10_000) { while (!predicate()) delay(1) } }
    }
    private class ControlledConnection(override val session: ControlledApi) : Connection {
        override val host = object : HostApi {
            override suspend fun describe() = ApiResult(ApiResult.SUCCESS_CODE, "", HostDescription(ProtocolVersion.Current, HostId("test-host"), "Test"))
        }
    }
    private class ControlledApi : SessionApi {
        val owner = TerminalOwner(TerminalScope("scope"), SessionId("parent"), TerminalIncarnation("incarnation"))
        val key = TerminalKey(owner, TerminalSessionId("real-result-key"))
        var creates = 0; var reads = 0; var closes = 0; var revision = 1L; var text = "ready"
        var lifecycle: TerminalLifecycle = TerminalLifecycle.Running
        val inputs = mutableListOf<Pair<TerminalKey, TerminalInput>>()
        val resizes = mutableListOf<TerminalSize>()
        fun metadata() = TerminalMetadata(key, "/test", listOf("shell"), lifecycle, TerminalInputState.Open)
        fun snapshot() = TerminalSnapshot(metadata(), TerminalSize(),
            listOf(TerminalLineSnapshot(listOf(TerminalTextRun(0, text.length, text, TerminalTextStyle(), (0..text.length).toList())))) +
                List(23) { TerminalLineSnapshot(emptyList()) }, emptyList(), TerminalCursor(0, 0, true), false, revision, "", false)
        var creation: suspend (TerminalCreateRequest) -> TerminalCreateResult = { TerminalCreateResult.Created(it.owner, metadata()) }
        var reading: suspend (TerminalKey) -> TerminalReadResult = { TerminalReadResult.Available(it, snapshot()) }
        var input: suspend (TerminalKey, TerminalInput) -> TerminalMutationResult = { key, _ -> TerminalMutationResult.Applied(key) }
        override suspend fun terminalCapability(session: SessionId) = TerminalDiscoveryResult.Available(session, owner, "/test", 8)
        override suspend fun terminals(owner: TerminalOwner) = TerminalListResult.Available(owner, listOf(metadata()))
        override suspend fun createTerminal(request: TerminalCreateRequest): TerminalCreateResult { creates++; return creation(request) }
        override suspend fun readTerminal(key: TerminalKey): TerminalReadResult { reads++; return reading(key) }
        override suspend fun inputTerminal(key: TerminalKey, input: TerminalInput): TerminalMutationResult { inputs += key to input; return this.input(key, input) }
        override suspend fun resizeTerminal(key: TerminalKey, size: TerminalSize): TerminalMutationResult { resizes += size; return TerminalMutationResult.Applied(key, size = size) }
        override suspend fun closeTerminal(key: TerminalKey): TerminalMutationResult { closes++; return TerminalMutationResult.Applied(key) }
        override suspend fun list() = listOf(SessionSummary(owner.session, 1))
        override suspend fun history(id: SessionId) = emptyList<Message>()
        override suspend fun create(id: SessionId?, options: CreateSessionOptions, agentOptions: AgentOptions): SessionSummary = error("not a terminal action")
        override suspend fun prompt(id: SessionId, message: Message): SessionPrompt = error("not a terminal action")
        override suspend fun cancel(id: SessionId, keepInbox: Boolean) = Unit
    }
}
