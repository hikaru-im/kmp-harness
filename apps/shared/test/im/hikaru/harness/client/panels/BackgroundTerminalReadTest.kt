package im.hikaru.harness.client.panels

import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.harness.client.account.BackendTenant
import im.hikaru.harness.client.account.MemberIdentity
import im.hikaru.harness.client.connection.*
import im.hikaru.harness.client.conversation.ConversationHostScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class BackgroundTerminalReadTest {
    @Test
    fun unavailableInventoryEmptyInventoryAndEmptyOutputAreDifferent() = runTest {
        val fixture = Fixture()
        val repository = ConnectionThreadPanelsRepository()
        val unavailable = repository.readTerminal(fixture.owner) as PanelLoad.Unavailable
        assertEquals("Producer missing", unavailable.serviceDescription)
        fixture.inventory = BackgroundProcessesResult.Available(fixture.parent, emptyList())
        assertTrue(repository.readTerminal(fixture.owner) is PanelLoad.Empty)
        assertTrue(fixture.outputRequests.isEmpty())
        fixture.inventory = BackgroundProcessesResult.Available(fixture.parent, listOf(fixture.process("actual", 1)))
        fixture.readOutput = { parent, process -> BackgroundOutputResult.Available(
            parent, process, parent, "", 0, false, BackgroundProcessLifecycle.Running,
        ) }
        val actual = repository.readTerminal(fixture.owner) as PanelLoad.Ready
        assertEquals("", actual.value.buffer)
        assertEquals(BackgroundProcessLifecycle.Running, actual.value.lifecycle)
        assertEquals("command-actual", actual.value.command)
        assertEquals("/actual/cwd", actual.value.cwd)
        assertEquals("producer", actual.value.key.producerIncarnation)
        assertEquals(listOf(fixture.parent to BackgroundProcessId("actual")), fixture.outputRequests)
    }

    @Test
    fun newestRunningWinsOverNewerExitedWithStableTieBreakThenExitedFallback() = runTest {
        val fixture = Fixture()
        val runningB = fixture.process("b", 10)
        val runningA = fixture.process("a", 10)
        val exited = fixture.process("exited", 20, BackgroundProcessLifecycle.Exited(7))
        val unknown = fixture.process("unknown", 30, BackgroundProcessLifecycle.Unknown)
        fixture.readOutput = { parent, process -> BackgroundOutputResult.Available(
            parent, process, parent, "actual output", 1, true,
            if (process == exited.process) BackgroundProcessLifecycle.Exited(7) else BackgroundProcessLifecycle.Running,
        ) }
        val repository = ConnectionThreadPanelsRepository()
        for (rows in listOf(listOf(runningB, exited, unknown, runningA), listOf(runningA, unknown, exited, runningB))) {
            fixture.inventory = BackgroundProcessesResult.Available(fixture.parent, rows)
            assertEquals("a", (repository.readTerminal(fixture.owner) as PanelLoad.Ready).value.key.processId)
        }
        fixture.inventory = BackgroundProcessesResult.Available(fixture.parent, listOf(unknown, exited))
        val result = repository.readTerminal(fixture.owner) as PanelLoad.Ready
        assertEquals("exited", result.value.key.processId)
        assertEquals(BackgroundProcessLifecycle.Exited(7), result.value.lifecycle)
        assertTrue(result.value.truncated)
    }

    @Test
    fun unknownIsReadableButNeverInventsAnExitAndGoneProcessIsNotEmpty() = runTest {
        val fixture = Fixture()
        val process = fixture.process("unknown", 1, BackgroundProcessLifecycle.Unknown)
        fixture.inventory = BackgroundProcessesResult.Available(fixture.parent, listOf(process))
        fixture.readOutput = { parent, id -> BackgroundOutputResult.Available(
            parent, id, parent, "last known output", 2, false, BackgroundProcessLifecycle.Unknown,
        ) }
        val repository = ConnectionThreadPanelsRepository()
        assertEquals(BackgroundProcessLifecycle.Unknown, (repository.readTerminal(fixture.owner) as PanelLoad.Ready).value.lifecycle)
        fixture.readOutput = { parent, id -> BackgroundOutputResult.NotFound(parent, id) }
        assertTrue(repository.readTerminal(fixture.owner) is PanelLoad.Error)
        fixture.readOutput = { parent, id -> BackgroundOutputResult.Unavailable(parent, id, "Buffer service missing") }
        assertEquals("Buffer service missing", (repository.readTerminal(fixture.owner) as PanelLoad.Unavailable).serviceDescription)
    }

    @Test
    fun typedReadsRejectForeignParentProcessAndOwnerBeforePublishing() = runTest {
        val fixture = Fixture()
        val repository = ConnectionThreadPanelsRepository()
        fixture.inventory = BackgroundProcessesResult.Unavailable(SessionId("foreign"), "missing")
        assertFailsWith<IllegalArgumentException> { repository.readTerminal(fixture.owner) }
        assertTrue(fixture.outputRequests.isEmpty())
        val process = fixture.process("actual", 1)
        fixture.inventory = BackgroundProcessesResult.Available(fixture.parent, listOf(process))
        fixture.readOutput = { parent, _ -> BackgroundOutputResult.NotFound(parent, BackgroundProcessId("foreign")) }
        assertFailsWith<IllegalArgumentException> { repository.readTerminal(fixture.owner) }
        fixture.readOutput = { _, id -> BackgroundOutputResult.Unavailable(SessionId("foreign"), id, "missing") }
        assertFailsWith<IllegalArgumentException> { repository.readTerminal(fixture.owner) }
        assertFailsWith<IllegalArgumentException> {
            BackgroundProcessesResult.Available(fixture.parent, listOf(process.copy(ownerSession = SessionId("foreign"))))
        }
        assertFailsWith<IllegalArgumentException> {
            BackgroundOutputResult.Available(fixture.parent, process.process, SessionId("foreign"), "secret", 1,
                false, BackgroundProcessLifecycle.Unknown)
        }
    }

    @Test
    fun everyOwnerDimensionAndDisconnectFenceNonCooperativeTerminalRead() = runTest {
        val fixture = Fixture()
        val original = fixture.owner
        val tenant = BackendTenant("https://backend.example", 1L)
        val replacements = listOf(
            original.copy(connection = Fixture().connection),
            original.copy(host = original.host.copy(generation = 2)),
            original.copy(host = original.host.copy(hostId = HostId("other"))),
            original.copy(host = original.host.copy(identity = MemberIdentity(tenant, 1))),
            original.copy(parentSessionId = SessionId("other-parent")),
            null,
        )
        for (replacement in replacements) {
            val gate = CompletableDeferred<PanelLoad<BackgroundTerminalOutput>>()
            var reads = 0
            val controller = ThreadPanelsController(backgroundScope, terminalRepository { captured ->
                reads++
                if (reads == 1) withContext(NonCancellable) { gate.await() } else PanelLoad.Empty(captured.scope)
            })
            controller.bind(original)
            assertNull(controller.refreshTerminal())
            open(controller, original)
            runCurrent()
            controller.bind(replacement)
            if (replacement != null) open(controller, replacement)
            runCurrent()
            gate.complete(output(original, "private old output", 10))
            runCurrent()
            if (replacement == null) assertNull(controller.state.value)
            else assertTrue(controller.state.value!!.terminal is PanelLoad.Empty)
            controller.close()
        }
    }

    @Test
    fun newRefreshAndCloseReopenFenceCancelledReplies() = runTest {
        for (reopen in listOf(false, true)) {
            val owner = Fixture().owner
            val gate = CompletableDeferred<PanelLoad<BackgroundTerminalOutput>>()
            var reads = 0
            val controller = ThreadPanelsController(backgroundScope, terminalRepository {
                reads++
                if (reads == 1) withContext(NonCancellable) { gate.await() } else output(it, "new", 2)
            })
            controller.bind(owner)
            open(controller, owner)
            runCurrent()
            if (reopen) {
                controller.dispatch(ThreadPanelAction.CloseTab(owner.scope, ThreadPanelTab.BackgroundOutput))
                assertNull(controller.refreshTerminal())
                open(controller, owner)
            } else controller.refreshTerminal()
            runCurrent()
            gate.complete(output(owner, "stale even with higher revision", 99))
            runCurrent()
            assertEquals("new", ready(controller).buffer)
            controller.close()
        }
    }

    @Test
    fun revisionWatermarkSurvivesReopenAndResetsOnlyForExplicitNewProducerOrOwner() = runTest {
        val fixture = Fixture()
        val owner = fixture.owner
        var response: PanelLoad<BackgroundTerminalOutput> = output(owner, "newest", 10)
        val controller = ThreadPanelsController(backgroundScope, terminalRepository { response })
        controller.bind(owner)
        open(controller, owner)
        runCurrent()
        response = output(owner, "older", 9)
        controller.refreshTerminal()
        assertTrue(controller.state.value!!.terminal is PanelLoad.Loading)
        runCurrent()
        assertTrue(controller.state.value!!.terminal is PanelLoad.Error)
        response = output(owner, "changed at equal revision", 10)
        controller.refreshTerminal()
        runCurrent()
        assertTrue(controller.state.value!!.terminal is PanelLoad.Error)
        response = output(owner, "newest", 10)
        controller.refreshTerminal()
        runCurrent()
        assertEquals("newest", ready(controller).buffer)
        controller.dispatch(ThreadPanelAction.CloseTab(owner.scope, ThreadPanelTab.BackgroundOutput))
        response = output(owner, "resurrected old version", 1)
        open(controller, owner)
        runCurrent()
        assertTrue(controller.state.value!!.terminal is PanelLoad.Error)
        response = output(owner, "new producer", 0, "next-producer")
        controller.refreshTerminal()
        runCurrent()
        assertTrue(controller.state.value!!.terminal is PanelLoad.Error)
        assertEquals("producer", controller.state.value!!.selectedTerminalKey!!.producerIncarnation)
        controller.dispatch(ThreadPanelAction.SelectTerminal(controller.state.value!!.terminalInventory.single().key))
        runCurrent()
        assertEquals("new producer", ready(controller).buffer)
        val next = owner.copy(host = owner.host.copy(generation = 2))
        response = output(next, "new owner", 0)
        controller.bind(next)
        open(controller, next)
        runCurrent()
        assertEquals("new owner", ready(controller).buffer)
        controller.close()
    }

    @Test
    fun terminalRefreshIsIndependentOfPendingOverviewAndErrorsCanRetry() = runTest {
        val owner = Fixture().owner
        val overviewGate = CompletableDeferred<PanelLoad<SubagentOverview>>()
        var fail = true
        val controller = ThreadPanelsController(backgroundScope, ReadOnlyThreadPanelsRepository(
            readOverview = { overviewGate.await() },
            readChildHistory = { _, _ -> error("No child selected") },
            readBackgroundOutput = { if (fail) error("private backend exception") else output(it, "real", 0) },
        ))
        controller.bind(owner)
        controller.dispatch(ThreadPanelAction.OpenTab(owner.scope, ThreadPanelTab.Subagents))
        open(controller, owner)
        runCurrent()
        assertTrue(controller.state.value!!.subagents is PanelLoad.Loading)
        val failure = controller.state.value!!.terminal as PanelLoad.Error
        assertFalse(failure.message.contains("private"))
        fail = false
        controller.refreshTerminal()
        runCurrent()
        assertEquals("real", ready(controller).buffer)
        assertTrue(controller.state.value!!.subagents is PanelLoad.Loading)
        overviewGate.complete(PanelLoad.Ready(owner.scope, SubagentOverview(owner.scope, emptyList())))
        runCurrent()
        assertTrue(controller.state.value!!.subagents is PanelLoad.Ready)
        controller.close()
    }

    @Test
    fun controllerCloseAndForeignLoadingCompletionsNeverPublish() = runTest {
        val owner = Fixture().owner
        var response: PanelLoad<BackgroundTerminalOutput> = PanelLoad.Loading(owner.scope)
        val controller = ThreadPanelsController(backgroundScope, terminalRepository { response })
        controller.bind(owner)
        open(controller, owner)
        runCurrent()
        assertTrue(controller.state.value!!.terminal is PanelLoad.Error)
        response = PanelLoad.Empty(owner.scope.copy(hostId = HostId("foreign")))
        controller.refreshTerminal()
        runCurrent()
        assertTrue(controller.state.value!!.terminal is PanelLoad.Error)
        controller.dispatch(ThreadPanelAction.ReceiveTerminal(owner.scope, controller.state.value!!.terminalGeneration,
            output(owner, "untrusted injected result", 1)))
        assertTrue(controller.state.value!!.terminal is PanelLoad.Error)
        controller.close()
        assertNull(controller.refreshTerminal())
        assertNull(controller.state.value)
    }

    @Test
    fun reducerFencesSuccessfulEmptyAndAcceptsEmptyBufferAsReady() {
        val scope = Fixture().owner.scope
        var state = ThreadPanelsState(scope).reduce(ThreadPanelAction.BeginTerminalLoad(scope))
        val generation = state.terminalGeneration
        state = state.reduce(ThreadPanelAction.ReceiveTerminal(scope, generation, PanelLoad.Empty(scope)))
        assertTrue(state.terminal is PanelLoad.Empty)
        state = state.reduce(ThreadPanelAction.BeginTerminalLoad(scope))
        val real = PanelLoad.Ready(scope, BackgroundTerminalOutput(BackgroundTerminalKey(scope, "real"), "", 0))
        state = state.reduce(ThreadPanelAction.ReceiveTerminal(scope, state.terminalGeneration, real))
        assertEquals(real, state.terminal)
        val closed = state.reduce(ThreadPanelAction.CloseTab(scope, ThreadPanelTab.BackgroundOutput))
            .reduce(ThreadPanelAction.OpenTab(scope, ThreadPanelTab.BackgroundOutput))
        assertEquals(closed, closed.reduce(ThreadPanelAction.ReceiveTerminal(scope, generation, PanelLoad.Empty(scope))))
    }

    @Test
    fun pendingInputSurvivesClosingViewAndBlocksDuplicateUntilConfirmed() = runTest {
        val owner = Fixture().owner
        val gate = CompletableDeferred<TerminalOperationResult>()
        var calls = 0
        val controller = ThreadPanelsController(backgroundScope, operationRepository(owner) { _, _ ->
            calls++
            gate.await()
        })
        controller.bind(owner)
        open(controller, owner)
        runCurrent()
        val key = ready(controller).key
        val operation = controller.operateTerminal(key, TerminalOperationKind.Input, "hello\n")!!
        assertTrue(controller.state.value!!.terminalControls!!.pending)
        assertNull(controller.operateTerminal(key, TerminalOperationKind.Input, "duplicate\n"))
        runCurrent()
        controller.dispatch(ThreadPanelAction.CloseTab(owner.scope, ThreadPanelTab.BackgroundOutput))
        assertTrue(operation.isActive, "Closing a view must not cancel an issued input")
        open(controller, owner)
        runCurrent()
        assertTrue(controller.state.value!!.terminalControls!!.pending)
        gate.complete(TerminalOperationResult(TerminalOperationOutcome.Applied, "sent"))
        runCurrent()
        assertEquals(1, calls)
        assertFalse(controller.state.value!!.terminalControls!!.pending)
        assertEquals(1L, controller.state.value!!.terminalControls!!.inputReceipt)
        controller.close()
    }

    @Test
    fun uncertainInputFenceSurvivesRefreshAndReopenAndRejectedCancel() = runTest {
        val owner = Fixture().owner
        val calls = mutableListOf<TerminalOperationKind>()
        val controller = ThreadPanelsController(backgroundScope, operationRepository(owner) { kind, _ ->
            calls += kind
            TerminalOperationResult(if (kind == TerminalOperationKind.Cancel) TerminalOperationOutcome.Rejected
                else TerminalOperationOutcome.Unknown, "result")
        })
        controller.bind(owner)
        open(controller, owner)
        runCurrent()
        val key = ready(controller).key
        controller.operateTerminal(key, TerminalOperationKind.Input, "hello\n")
        runCurrent()
        assertTrue(controller.state.value!!.terminalControls!!.inputBlocked)
        controller.dispatch(ThreadPanelAction.CloseTab(owner.scope, ThreadPanelTab.BackgroundOutput))
        open(controller, owner)
        runCurrent()
        assertNull(controller.operateTerminal(key, TerminalOperationKind.Input, "retry\n"))
        assertNull(controller.operateTerminal(key, TerminalOperationKind.Eof))
        controller.operateTerminal(key, TerminalOperationKind.Cancel)
        runCurrent()
        assertTrue(controller.state.value!!.terminalControls!!.inputBlocked, "Cancel rejection must preserve prior input uncertainty")
        assertEquals(listOf(TerminalOperationKind.Input, TerminalOperationKind.Cancel), calls)
        controller.close()
    }

    @Test
    fun appliedEofAndUnknownCancelBlockRepeatsButNeverInventExit() = runTest {
        for (kind in listOf(TerminalOperationKind.Eof, TerminalOperationKind.Cancel)) {
            val owner = Fixture().owner
            var calls = 0
            val controller = ThreadPanelsController(backgroundScope, operationRepository(owner) { _, _ ->
                calls++
                TerminalOperationResult(if (kind == TerminalOperationKind.Eof) TerminalOperationOutcome.Applied
                    else TerminalOperationOutcome.Unknown, "result")
            })
            controller.bind(owner)
            open(controller, owner)
            runCurrent()
            val key = ready(controller).key
            controller.operateTerminal(key, kind)
            runCurrent()
            controller.refreshTerminal()
            runCurrent()
            assertNull(controller.operateTerminal(key, kind))
            assertNull(controller.operateTerminal(key, TerminalOperationKind.Input, "retry"))
            assertEquals(BackgroundProcessLifecycle.Running, ready(controller).lifecycle)
            assertEquals(1, calls)
            controller.close()
        }
    }

    @Test
    fun staleKeysAndOldOwnerOperationRepliesCannotMutateNextOwner() = runTest {
        val owner = Fixture().owner
        val gate = CompletableDeferred<TerminalOperationResult>()
        val controller = ThreadPanelsController(backgroundScope, operationRepository(owner) { _, _ ->
            withContext(NonCancellable) { gate.await() }
        })
        controller.bind(owner)
        open(controller, owner)
        runCurrent()
        val key = ready(controller).key
        assertNull(controller.operateTerminal(key.copy(producerIncarnation = "other"), TerminalOperationKind.Cancel))
        controller.operateTerminal(key, TerminalOperationKind.Input, "old\n")
        runCurrent()
        val next = owner.copy(host = owner.host.copy(generation = 2))
        controller.bind(next)
        open(controller, next)
        runCurrent()
        gate.complete(TerminalOperationResult(TerminalOperationOutcome.Unknown, "old reply"))
        runCurrent()
        assertNull(controller.state.value!!.terminalControls)
        controller.close()
    }

    @Test
    fun transportExceptionAndRequestCancellationAreUnknownWithoutRetry() = runTest {
        for (cancel in listOf(false, true)) {
            val owner = Fixture().owner
            val gate = CompletableDeferred<TerminalOperationResult>()
            val controller = ThreadPanelsController(backgroundScope, operationRepository(owner) { _, _ ->
                if (cancel) gate.await() else error("Transport lost after dispatch")
            })
            controller.bind(owner)
            open(controller, owner)
            runCurrent()
            val key = ready(controller).key
            val job = controller.operateTerminal(key, TerminalOperationKind.Input, "uncertain\n")!!
            runCurrent()
            if (cancel) { job.cancel(); runCurrent() }
            assertEquals(TerminalOperationOutcome.Unknown, controller.state.value!!.terminalControls!!.lastOutcome)
            assertNull(controller.operateTerminal(key, TerminalOperationKind.Input, "retry\n"))
            controller.close()
        }
    }

    private fun operationRepository(
        original: ThreadPanelsOwner, operation: suspend (TerminalOperationKind, String) -> TerminalOperationResult,
    ) = object : ThreadPanelsRepository {
        override suspend fun readTerminal(owner: ThreadPanelsOwner) = PanelLoad.Ready(owner.scope, BackgroundTerminalOutput(
            BackgroundTerminalKey(owner.scope, "actual", "producer"), "", 1,
            lifecycle = BackgroundProcessLifecycle.Running, inputState = BackgroundInputState.Open,
        ))
        override suspend fun operateTerminal(owner: ThreadPanelsOwner, key: BackgroundTerminalKey, kind: TerminalOperationKind, input: String): TerminalOperationResult {
            assertTrue(owner.sameOwner(original))
            assertEquals(owner.scope, key.scope)
            return operation(kind, input)
        }
        override suspend fun readOverview(owner: ThreadPanelsOwner): PanelLoad<SubagentOverview> =
            PanelLoad.Unavailable(owner.scope, PanelUnavailableReason.ChildrenServiceMissing)
        override suspend fun readDetail(owner: ThreadPanelsOwner, child: ChildAgentKey): PanelLoad<ChildAgentDetail> =
            PanelLoad.Unavailable(owner.scope, PanelUnavailableReason.ChildHistoryUnavailable)
    }

    private fun open(controller: ThreadPanelsController, owner: ThreadPanelsOwner) =
        controller.dispatch(ThreadPanelAction.OpenTab(owner.scope, ThreadPanelTab.BackgroundOutput))

    private fun ready(controller: ThreadPanelsController): BackgroundTerminalOutput =
        (controller.state.value!!.terminal as PanelLoad.Ready).value

    private fun output(owner: ThreadPanelsOwner, buffer: String, revision: Long, producer: String = "producer") =
        PanelLoad.Ready(owner.scope, BackgroundTerminalOutput(
            BackgroundTerminalKey(owner.scope, "actual", producer), buffer, revision,
        ))

    private fun terminalRepository(read: suspend (ThreadPanelsOwner) -> PanelLoad<BackgroundTerminalOutput>) =
        ReadOnlyThreadPanelsRepository(
            readOverview = { PanelLoad.Unavailable(it.scope, PanelUnavailableReason.ChildrenServiceMissing) },
            readChildHistory = { owner, _ -> PanelLoad.Unavailable(owner.scope, PanelUnavailableReason.ChildHistoryUnavailable) },
            readBackgroundOutput = read,
        )

    /** Only the two background reads can be called; every unrelated or mutating API fails. */
    private class Fixture {
        val parent = SessionId("parent")
        var inventory: BackgroundProcessesResult = BackgroundProcessesResult.Unavailable(parent, "Producer missing")
        val outputRequests = mutableListOf<Pair<SessionId, BackgroundProcessId>>()
        var readOutput: suspend (SessionId, BackgroundProcessId) -> BackgroundOutputResult = { parent, id ->
            BackgroundOutputResult.NotFound(parent, id)
        }
        val connection = object : Connection {
            override val host: HostApi = object : HostApi {
                override suspend fun describe() = error("Already verified")
            }
            override val session: SessionApi = object : SessionApi {
                override suspend fun backgroundProcesses(parent: SessionId): BackgroundProcessesResult {
                    assertEquals(this@Fixture.parent, parent)
                    return inventory
                }
                override suspend fun readBackgroundOutput(parent: SessionId, process: BackgroundProcessId): BackgroundOutputResult {
                    outputRequests.add(parent to process)
                    return readOutput(parent, process)
                }
                override suspend fun list(): List<SessionSummary> = error("No session enumeration")
                override suspend fun create(id: SessionId?, options: CreateSessionOptions, agentOptions: AgentOptions): SessionSummary =
                    error("No creation")
                override suspend fun history(id: SessionId): List<Message> = error("No generic tool history")
                override suspend fun prompt(id: SessionId, message: Message): SessionPrompt = error("No command or prompt")
                override suspend fun cancel(id: SessionId, keepInbox: Boolean) = error("No process/session mutations")
            }
        }
        val owner = ThreadPanelsOwner(connection, ConversationHostScope(HostId("host"), 1, null), parent)
        fun process(id: String, startedAt: Long, lifecycle: BackgroundProcessLifecycle = BackgroundProcessLifecycle.Running) =
            BackgroundProcess(BackgroundProcessId(id), parent, "call-$id", "command-$id", "/actual/cwd",
                startedAt, "producer", lifecycle)
    }
}
