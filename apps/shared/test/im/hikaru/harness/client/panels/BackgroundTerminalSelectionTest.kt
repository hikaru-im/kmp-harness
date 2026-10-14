package im.hikaru.harness.client.panels

import im.hikaru.contracts.harness.identity.HostId
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
class BackgroundTerminalSelectionTest {
    @Test
    fun productionInventoryIsSortedAndInitialSelectionUsesRealLifecycle() = runTest {
        val fixture = SelectionConnectionFixture()
        val repository = ConnectionThreadPanelsRepository()
        val a = fixture.process("a", 10)
        val b = fixture.process("b", 10)
        val exited = fixture.process("exited", 20, BackgroundProcessLifecycle.Exited(9))
        val unknown = fixture.process("unknown", 30, BackgroundProcessLifecycle.Unknown)
        fixture.rows = listOf(b, unknown, a, exited)
        val first = repository.readTerminalSelection(fixture.owner)
        assertEquals(listOf("unknown", "exited", "a", "b"), first.inventory.map { it.key.processId })
        assertEquals(fixture.key(a), first.selectedKey)
        assertEquals("command-a", first.inventory.first { it.key == first.selectedKey }.command)
        assertEquals(10L, first.inventory.first { it.key == first.selectedKey }.startedAtEpochMillis)
        fixture.rows = listOf(unknown, exited)
        assertEquals(fixture.key(exited), repository.readTerminalSelection(fixture.owner).selectedKey)
        fixture.rows = listOf(unknown)
        val last = repository.readTerminalSelection(fixture.owner)
        assertEquals(fixture.key(unknown), last.selectedKey)
        assertEquals(BackgroundProcessLifecycle.Unknown, (last.output as PanelLoad.Ready).value.lifecycle)
        fixture.rows = emptyList()
        val empty = repository.readTerminalSelection(fixture.owner)
        assertTrue(empty.output is PanelLoad.Empty)
        assertNull(empty.selectedKey)
        assertTrue(empty.inventory.isEmpty())
        assertEquals(listOf("a", "exited", "unknown"), fixture.outputRequests.map { it.second.value })
        assertEquals(0, fixture.mutations)
    }

    @Test
    fun exactSelectionSurvivesNewerProcessRefreshAndCloseReopen() = runTest {
        val fixture = SelectionConnectionFixture()
        val a = fixture.process("a", 1)
        val b = fixture.process("b", 2)
        fixture.rows = listOf(a)
        val controller = ThreadPanelsController(backgroundScope, ConnectionThreadPanelsRepository())
        controller.bind(fixture.owner)
        open(controller, fixture.owner)
        runCurrent()
        fixture.rows = listOf(a, b)
        controller.refreshTerminal()
        runCurrent()
        assertEquals(fixture.key(a), controller.state.value!!.selectedTerminalKey)
        assertEquals(listOf("b", "a"), controller.state.value!!.terminalInventory.map { it.key.processId })
        controller.dispatch(ThreadPanelAction.CloseTab(fixture.owner.scope, ThreadPanelTab.BackgroundOutput))
        assertEquals(fixture.key(a), controller.state.value!!.selectedTerminalKey)
        open(controller, fixture.owner)
        runCurrent()
        assertEquals(fixture.key(a), ready(controller).key)
        controller.dispatch(ThreadPanelAction.SelectTerminal(fixture.key(b)))
        runCurrent()
        assertEquals(fixture.key(b), ready(controller).key)
        assertEquals(listOf("a", "a", "a", "b"), fixture.outputRequests.map { it.second.value })
        assertEquals(0, fixture.mutations)
        controller.close()
    }

    @Test
    fun missingSelectionAndReplacementIncarnationAreErrorsWithoutAlternativeOutputRead() = runTest {
        val fixture = SelectionConnectionFixture()
        val a = fixture.process("a", 1)
        val b = fixture.process("b", 2)
        val key = fixture.key(a)
        val repository = ConnectionThreadPanelsRepository()
        for (rows in listOf(emptyList(), listOf(b), listOf(a.copy(producerIncarnation = "replacement"), b))) {
            fixture.rows = rows
            val snapshot = repository.readTerminalSelection(fixture.owner, key)
            assertEquals(key, snapshot.selectedKey)
            assertTrue(snapshot.output is PanelLoad.Error)
            assertTrue((snapshot.output as PanelLoad.Error).message.contains("身份"))
            assertEquals(rows.size, snapshot.inventory.size)
            assertTrue(fixture.outputRequests.isEmpty())
        }
        fixture.rows = listOf(a.copy(producerIncarnation = "replacement"), b)
        for (kind in TerminalOperationKind.entries) {
            assertEquals(TerminalOperationOutcome.Rejected,
                repository.operateTerminal(fixture.owner, key, kind, "old draft").outcome)
        }
        assertEquals(0, fixture.mutations)
    }

    @Test
    fun replacementDuringOutputReadFailsIdentityRecheckAndRetainsActualInventory() = runTest {
        val fixture = SelectionConnectionFixture()
        val a = fixture.process("a", 1)
        fixture.rows = listOf(a)
        fixture.afterOutput = { fixture.rows = listOf(a.copy(producerIncarnation = "new"), fixture.process("b", 2)) }
        val result = ConnectionThreadPanelsRepository().readTerminalSelection(fixture.owner, fixture.key(a))
        assertTrue(result.output is PanelLoad.Error)
        assertEquals(fixture.key(a), result.selectedKey)
        assertEquals("new", result.inventory.first { it.key.processId == "a" }.key.producerIncarnation)
        assertEquals(listOf(fixture.parent to a.process), fixture.outputRequests)
    }

    @Test
    fun unavailableOrFailedOutputKeepsInventoryAndRequestedIdentity() = runTest {
        val fixture = SelectionConnectionFixture()
        val a = fixture.process("a", 1)
        fixture.rows = listOf(a)
        val repository = ConnectionThreadPanelsRepository()
        fixture.readOutput = { parent, id -> BackgroundOutputResult.Unavailable(parent, id, "missing output") }
        val unavailable = repository.readTerminalSelection(fixture.owner, fixture.key(a))
        assertTrue(unavailable.output is PanelLoad.Unavailable)
        assertEquals(listOf(fixture.key(a)), unavailable.inventory.map { it.key })
        fixture.readOutput = { _, _ -> error("private failure") }
        val failure = repository.readTerminalSelection(fixture.owner, fixture.key(a))
        assertTrue(failure.output is PanelLoad.Error)
        assertEquals(unavailable.inventory, failure.inventory)
        assertEquals(fixture.key(a), failure.selectedKey)
    }

    @Test
    fun snapshotRejectsForeignScopeDuplicateIdsAndNonExactOutputKey() {
        val fixture = SelectionConnectionFixture()
        val key = fixture.key(fixture.process("a", 1))
        val row = summary(key)
        val output = output(key)
        val foreign = key.copy(scope = key.scope.copy(hostId = HostId("foreign")))
        assertFailsWith<IllegalArgumentException> {
            BackgroundTerminalSelection(key.scope, listOf(summary(foreign)), key, PanelLoad.Error(key.scope, "error"))
        }
        assertFailsWith<IllegalArgumentException> {
            BackgroundTerminalSelection(key.scope, listOf(row, row.copy(key = key.copy(producerIncarnation = "new"))),
                key, PanelLoad.Ready(key.scope, output))
        }
        assertFailsWith<IllegalArgumentException> {
            BackgroundTerminalSelection(key.scope, listOf(row), key.copy(producerIncarnation = "new"), PanelLoad.Ready(key.scope, output))
        }
        assertFailsWith<IllegalArgumentException> {
            BackgroundTerminalSelection(key.scope, listOf(row), key, PanelLoad.Ready(foreign.scope, output))
        }
        assertFailsWith<IllegalArgumentException> {
            BackgroundTerminalSelection(key.scope, listOf(row), key, PanelLoad.Empty(key.scope))
        }
        assertFailsWith<IllegalArgumentException> { ThreadPanelsState(key.scope, terminalInventory = listOf(row, row)) }
    }

    @Test
    fun productionRejectsWrongParentOwnerDuplicateIdAndOutputIdentity() = runTest {
        val fixture = SelectionConnectionFixture()
        val a = fixture.process("a", 1)
        val repository = ConnectionThreadPanelsRepository()
        fixture.inventoryParent = SessionId("foreign")
        assertFailsWith<IllegalArgumentException> { repository.readTerminalSelection(fixture.owner) }
        fixture.inventoryParent = fixture.parent
        fixture.rows = listOf(a, a.copy(producerIncarnation = "new"))
        assertFailsWith<IllegalArgumentException> { repository.readTerminalSelection(fixture.owner) }
        fixture.rows = listOf(a.copy(ownerSession = SessionId("foreign")))
        assertFailsWith<IllegalArgumentException> { repository.readTerminalSelection(fixture.owner) }
        fixture.rows = listOf(a)
        assertTrue(fixture.outputRequests.isEmpty())
        val foreignKey = fixture.key(a).copy(scope = fixture.owner.scope.copy(conversationId = SessionId("foreign")))
        assertFailsWith<IllegalArgumentException> { repository.readTerminalSelection(fixture.owner, foreignKey) }
        fixture.readOutput = { parent, _ -> BackgroundOutputResult.NotFound(parent, BackgroundProcessId("b")) }
        assertFailsWith<IllegalArgumentException> { repository.readTerminalSelection(fixture.owner, fixture.key(a)) }
        fixture.readOutput = { _, id -> BackgroundOutputResult.NotFound(SessionId("foreign"), id) }
        assertFailsWith<IllegalArgumentException> { repository.readTerminalSelection(fixture.owner, fixture.key(a)) }
        fixture.readOutput = { parent, id -> BackgroundOutputResult.Available(parent, id, SessionId("foreign"), "secret", 0,
            false, BackgroundProcessLifecycle.Unknown) }
        // DTO validation fails during the read; the repository exposes only a sanitized error.
        val rejectedOwner = repository.readTerminalSelection(fixture.owner, fixture.key(a))
        assertTrue(rejectedOwner.output is PanelLoad.Error)
        assertEquals(fixture.key(a), rejectedOwner.selectedKey)
        assertFalse(rejectedOwner.output.toString().contains("secret"))
    }

    @Test
    fun legacyAdapterExposesOnlyRealSingleOutputAndNeverSilentlyReplacesSelection() = runTest {
        val fixture = SelectionConnectionFixture()
        val a = fixture.key(fixture.process("a", 1))
        val b = fixture.key(fixture.process("b", 2))
        var actual: PanelLoad<BackgroundTerminalOutput> = PanelLoad.Ready(a.scope, output(a))
        val repository = ReadOnlyThreadPanelsRepository(
            { PanelLoad.Unavailable(it.scope, PanelUnavailableReason.ChildrenServiceMissing) },
            { owner, _ -> PanelLoad.Unavailable(owner.scope, PanelUnavailableReason.ChildHistoryUnavailable) },
            { actual },
        )
        val first = repository.readTerminalSelection(fixture.owner)
        assertEquals(listOf(a), first.inventory.map { it.key })
        assertNull(first.inventory.single().startedAtEpochMillis)
        actual = PanelLoad.Ready(b.scope, output(b))
        val replaced = repository.readTerminalSelection(fixture.owner, a)
        assertEquals(a, replaced.selectedKey)
        assertEquals(listOf(b), replaced.inventory.map { it.key })
        assertTrue(replaced.output is PanelLoad.Error)
        actual = PanelLoad.Empty(a.scope)
        assertTrue(repository.readTerminalSelection(fixture.owner, a).output is PanelLoad.Error)
        assertTrue(repository.readTerminalSelection(fixture.owner).output is PanelLoad.Empty)
        val unavailable = UnavailableThreadPanelsRepository().readTerminalSelection(fixture.owner)
        assertTrue(unavailable.output is PanelLoad.Unavailable)
        assertTrue(unavailable.inventory.isEmpty())
    }

    @Test
    fun selectionAcceptsOnlyKnownExactKeysAndFencesOldGenerations() {
        val fixture = SelectionConnectionFixture()
        val a = fixture.key(fixture.process("a", 1))
        val b = fixture.key(fixture.process("b", 2))
        val state = ThreadPanelsState(a.scope, terminal = PanelLoad.Ready(a.scope, output(a)),
            terminalInventory = listOf(summary(a), summary(b)))
        for (invalid in listOf(b.copy(processId = "unread"), b.copy(producerIncarnation = "forged"),
            b.copy(scope = b.scope.copy(hostId = HostId("foreign"))), a)) {
            assertEquals(state, state.reduce(ThreadPanelAction.SelectTerminal(invalid)))
        }
        val next = state.reduce(ThreadPanelAction.SelectTerminal(b))
        assertEquals(b, next.selectedTerminalKey)
        assertTrue(next.terminal is PanelLoad.Loading)
        val late = BackgroundTerminalSelection(a.scope, state.terminalInventory, a, PanelLoad.Ready(a.scope, output(a)))
        assertEquals(next, next.reduce(ThreadPanelAction.ReceiveTerminalSelection(late, state.terminalGeneration)))
        assertEquals(next, next.reduce(ThreadPanelAction.ReceiveTerminalSelection(late, next.terminalGeneration)))
        val closed = next.reduce(ThreadPanelAction.CloseTab(a.scope, ThreadPanelTab.BackgroundOutput))
        assertEquals(b, closed.selectedTerminalKey)
        assertEquals(closed, closed.reduce(ThreadPanelAction.SelectTerminal(a)))
    }

    @Test
    fun controllerCopiesReadInventoryAndRejectsDifferentRequestedIdentity() = runTest {
        val owner = SelectionConnectionFixture().owner
        val repository = SelectableRepository(owner.scope)
        val externalRows = mutableListOf(summary(repository.a), summary(repository.b))
        repository.read = { key -> BackgroundTerminalSelection(owner.scope, externalRows, key ?: repository.a,
            PanelLoad.Ready(owner.scope, output(key ?: repository.a))) }
        val controller = ThreadPanelsController(backgroundScope, repository)
        controller.bind(owner)
        open(controller, owner)
        runCurrent()
        externalRows.clear()
        assertEquals(listOf(repository.a, repository.b), controller.state.value!!.terminalInventory.map { it.key })
        repository.read = { repository.snapshot(repository.b) }
        controller.refreshTerminal()
        runCurrent()
        assertTrue(controller.state.value!!.terminal is PanelLoad.Error)
        assertEquals(repository.a, controller.state.value!!.selectedTerminalKey)
        assertEquals(listOf(repository.a, repository.b), controller.state.value!!.terminalInventory.map { it.key })
        controller.close()
    }

    @Test
    fun nonCooperativeReadCannotOverwriteNextSelectionOrRefreshedSelection() = runTest {
        val owner = SelectionConnectionFixture().owner
        val repository = SelectableRepository(owner.scope)
        val controller = ThreadPanelsController(backgroundScope, repository)
        controller.bind(owner)
        open(controller, owner)
        runCurrent()
        val gate = CompletableDeferred<BackgroundTerminalSelection>()
        val old = repository.snapshot(repository.a)
        repository.read = { withContext(NonCancellable) { gate.await() } }
        controller.refreshTerminal()
        runCurrent()
        repository.read = { key -> repository.snapshot(key ?: repository.a) }
        controller.dispatch(ThreadPanelAction.SelectTerminal(repository.b))
        runCurrent()
        assertEquals(repository.b, ready(controller).key)
        controller.refreshTerminal()
        runCurrent()
        val current = controller.state.value
        gate.complete(old)
        runCurrent()
        assertEquals(current, controller.state.value)
        assertEquals(listOf(null, repository.a, repository.b, repository.b), repository.requests)
        assertTrue(repository.operations.isEmpty())
        controller.close()
    }

    @Test
    fun nonCooperativeSelectionReadCannotPublishAfterSameScopeOwnerChange() = runTest {
        val owner = SelectionConnectionFixture().owner
        val repository = SelectableRepository(owner.scope)
        val gate = CompletableDeferred<BackgroundTerminalSelection>()
        repository.read = { withContext(NonCancellable) { gate.await() } }
        val controller = ThreadPanelsController(backgroundScope, repository)
        controller.bind(owner)
        open(controller, owner)
        runCurrent()
        val oldGeneration = controller.state.value!!.ownerGeneration
        controller.bind(owner.copy(host = owner.host.copy(generation = 2)))
        repository.read = { repository.snapshot(repository.b) }
        open(controller, owner)
        runCurrent()
        val next = controller.state.value
        assertTrue(next!!.ownerGeneration > oldGeneration)
        gate.complete(repository.snapshot(repository.a))
        runCurrent()
        assertEquals(next, controller.state.value)
        assertEquals(repository.b, ready(controller).key)
        controller.close()
    }

    @Test
    fun allOperationKindsKeepPendingAndUnknownGatesOnAThroughBAndBack() = runTest {
        for (kind in TerminalOperationKind.entries) {
            val owner = SelectionConnectionFixture().owner
            val repository = SelectableRepository(owner.scope)
            val gate = CompletableDeferred<TerminalOperationResult>()
            repository.operation = { _, _, _ -> gate.await() }
            val controller = ThreadPanelsController(backgroundScope, repository)
            controller.bind(owner)
            open(controller, owner)
            runCurrent()
            controller.operateTerminal(repository.a, kind, if (kind == TerminalOperationKind.Input) "A\n" else "")
            runCurrent()
            controller.dispatch(ThreadPanelAction.SelectTerminal(repository.b))
            runCurrent()
            assertNull(controller.state.value!!.terminalControls)
            controller.dispatch(ThreadPanelAction.SelectTerminal(repository.a))
            runCurrent()
            assertTrue(controller.state.value!!.terminalControls!!.pending)
            assertNull(controller.operateTerminal(repository.a, kind, "repeat"))
            controller.dispatch(ThreadPanelAction.SelectTerminal(repository.b))
            runCurrent()
            val beforeReply = controller.state.value
            gate.complete(TerminalOperationResult(TerminalOperationOutcome.Unknown, "uncertain A"))
            runCurrent()
            assertEquals(beforeReply, controller.state.value, "A reply cannot change B")
            controller.dispatch(ThreadPanelAction.SelectTerminal(repository.a))
            runCurrent()
            assertFalse(controller.state.value!!.terminalControls!!.pending)
            assertEquals(TerminalOperationOutcome.Unknown, controller.state.value!!.terminalControls!!.lastOutcome)
            assertTrue(controller.state.value!!.terminalControls!!.inputBlocked)
            assertEquals(kind == TerminalOperationKind.Cancel, controller.state.value!!.terminalControls!!.cancelBlocked)
            controller.refreshTerminal()
            runCurrent()
            assertNull(controller.operateTerminal(repository.a, kind, "repeat"))
            assertNull(controller.operateTerminal(repository.a, TerminalOperationKind.Input, "repeat"))
            assertEquals(listOf(repository.a to kind), repository.operations)
            controller.close()
        }
    }

    private fun open(controller: ThreadPanelsController, owner: ThreadPanelsOwner) =
        controller.dispatch(ThreadPanelAction.OpenTab(owner.scope, ThreadPanelTab.BackgroundOutput))

    private fun ready(controller: ThreadPanelsController): BackgroundTerminalOutput =
        (controller.state.value!!.terminal as PanelLoad.Ready).value

    private fun summary(key: BackgroundTerminalKey) = BackgroundTerminalSummary(key, "command-" + key.processId,
        BackgroundProcessLifecycle.Running, inputState = BackgroundInputState.Open)

    private fun output(key: BackgroundTerminalKey) = BackgroundTerminalOutput(key, "output-" + key.processId, 1,
        command = "command-" + key.processId, lifecycle = BackgroundProcessLifecycle.Running, inputState = BackgroundInputState.Open)

    private inner class SelectableRepository(val scope: ThreadPanelScope) : ThreadPanelsRepository {
        val a = BackgroundTerminalKey(scope, "a", "producer-a")
        val b = BackgroundTerminalKey(scope, "b", "producer-b")
        val requests = mutableListOf<BackgroundTerminalKey?>()
        val operations = mutableListOf<Pair<BackgroundTerminalKey, TerminalOperationKind>>()
        var read: suspend (BackgroundTerminalKey?) -> BackgroundTerminalSelection = { snapshot(it ?: a) }
        var operation: suspend (BackgroundTerminalKey, TerminalOperationKind, String) -> TerminalOperationResult =
            { _, _, _ -> error("No view mutation") }
        fun snapshot(key: BackgroundTerminalKey) = BackgroundTerminalSelection(scope, listOf(summary(a), summary(b)),
            key, PanelLoad.Ready(scope, output(key)))
        override suspend fun readTerminalSelection(owner: ThreadPanelsOwner, selectedKey: BackgroundTerminalKey?): BackgroundTerminalSelection {
            assertEquals(scope, owner.scope)
            requests += selectedKey
            return read(selectedKey)
        }
        override suspend fun operateTerminal(owner: ThreadPanelsOwner, key: BackgroundTerminalKey,
            kind: TerminalOperationKind, input: String): TerminalOperationResult {
            operations += key to kind
            return operation(key, kind, input)
        }
        override suspend fun readOverview(owner: ThreadPanelsOwner): PanelLoad<SubagentOverview> =
            PanelLoad.Unavailable(scope, PanelUnavailableReason.ChildrenServiceMissing)
        override suspend fun readDetail(owner: ThreadPanelsOwner, child: ChildAgentKey): PanelLoad<ChildAgentDetail> =
            PanelLoad.Unavailable(scope, PanelUnavailableReason.ChildHistoryUnavailable)
    }
}

private class SelectionConnectionFixture {
    val parent = SessionId("parent")
    var inventoryParent = parent
    var rows: List<BackgroundProcess> = emptyList()
    val outputRequests = mutableListOf<Pair<SessionId, BackgroundProcessId>>()
    var mutations = 0
    var afterOutput: () -> Unit = {}
    var readOutput: suspend (SessionId, BackgroundProcessId) -> BackgroundOutputResult = { parent, process ->
        val row = rows.single { it.process == process }
        val result = BackgroundOutputResult.Available(parent, process, parent, "output-" + process.value, 1,
            false, row.lifecycle, row.inputState)
        afterOutput()
        result
    }
    val connection = object : Connection {
        override val host: HostApi = object : HostApi {
            override suspend fun describe() = error("Already verified")
        }
        override val session: SessionApi = object : SessionApi {
            override suspend fun backgroundProcesses(parent: SessionId): BackgroundProcessesResult {
                assertEquals(this@SelectionConnectionFixture.parent, parent)
                return BackgroundProcessesResult.Available(inventoryParent, rows)
            }
            override suspend fun readBackgroundOutput(parent: SessionId, process: BackgroundProcessId): BackgroundOutputResult {
                outputRequests += parent to process
                return readOutput(parent, process)
            }
            override suspend fun writeBackgroundInput(parent: SessionId, process: BackgroundProcessId, input: String): BackgroundInputResult {
                mutations++
                return BackgroundInputResult.Accepted(parent, process, input.encodeToByteArray().size)
            }
            override suspend fun closeBackgroundInput(parent: SessionId, process: BackgroundProcessId): BackgroundControlResult {
                mutations++
                return BackgroundControlResult.Applied(parent, process)
            }
            override suspend fun cancelBackgroundProcess(parent: SessionId, process: BackgroundProcessId): BackgroundControlResult {
                mutations++
                return BackgroundControlResult.Applied(parent, process)
            }
            override suspend fun list(): List<SessionSummary> = error("No session enumeration")
            override suspend fun create(id: SessionId?, options: CreateSessionOptions, agentOptions: AgentOptions): SessionSummary =
                error("No process/session creation")
            override suspend fun history(id: SessionId): List<Message> = error("No tool history")
            override suspend fun prompt(id: SessionId, message: Message): SessionPrompt = error("No prompt")
            override suspend fun cancel(id: SessionId, keepInbox: Boolean) = error("No session cancel")
        }
    }
    val owner = ThreadPanelsOwner(connection, ConversationHostScope(HostId("host"), 1, null), parent)
    fun process(id: String, started: Long, lifecycle: BackgroundProcessLifecycle = BackgroundProcessLifecycle.Running) =
        BackgroundProcess(BackgroundProcessId(id), parent, "call-" + id, "command-" + id, "/cwd", started,
            "producer-" + id, lifecycle, BackgroundInputState.Open)
    fun key(process: BackgroundProcess) = BackgroundTerminalKey(owner.scope, process.process.value, process.producerIncarnation)
}
