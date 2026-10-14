package im.hikaru.harness.client.summary

import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.harness.client.connection.*
import im.hikaru.harness.client.conversation.ConversationHostScope
import im.hikaru.harness.client.panels.BackgroundTerminalKey
import im.hikaru.harness.client.panels.BackgroundTerminalOutput
import im.hikaru.harness.client.panels.BackgroundTerminalSelection
import im.hikaru.harness.client.panels.BackgroundTerminalSummary
import im.hikaru.harness.client.panels.ChildAgentKey
import im.hikaru.harness.client.panels.ChildAgentDetail
import im.hikaru.harness.client.panels.ChildAgentStatus
import im.hikaru.harness.client.panels.ChildAgentSummary
import im.hikaru.harness.client.panels.ChildHistoryAvailability
import im.hikaru.harness.client.panels.PanelLoad
import im.hikaru.harness.client.panels.PanelUnavailableReason
import im.hikaru.harness.client.panels.ReadOnlyThreadPanelsRepository
import im.hikaru.harness.client.panels.SubagentOverview
import im.hikaru.harness.client.panels.TerminalOperationKind
import im.hikaru.harness.client.panels.TerminalOperationOutcome
import im.hikaru.harness.client.panels.TerminalOperationResult
import im.hikaru.harness.client.panels.ThreadPanelScope
import im.hikaru.harness.client.panels.ThreadPanelsOwner
import im.hikaru.harness.client.panels.ThreadPanelsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ThreadSummaryActivityControllerTest {
    @Test
    fun unboundOrClosedRefreshIsANoOp() = runTest {
        val controller = ThreadSummaryActivityController(backgroundScope)
        assertNull(controller.refresh())
        assertEquals(ThreadSummaryActivityPhase.Idle, controller.state.value.phase)
        controller.close()
        assertNull(controller.refresh())
        assertFalse(controller.isBoundTo(null))
    }

    @Test
    fun readPublishesSubagentAndProcessRows() = runTest {
        val owner = owner()
        val row = child(owner, "child", ChildAgentStatus.Active)
        val controller = ThreadSummaryActivityController(backgroundScope, repository(
            overview = { PanelLoad.Ready(owner.scope, SubagentOverview(owner.scope, listOf(row))) },
            output = { readyOutput(owner, "npm run dev", BackgroundProcessLifecycle.Running) },
        ))
        controller.bind(owner)
        controller.refresh()
        runCurrent()
        val state = controller.state.value
        assertEquals(ThreadSummaryActivityPhase.Read, state.phase)
        assertEquals(listOf("child"), state.subagents.map { it.id })
        assertEquals(ThreadSummaryActivityKind.Subagent, state.subagents.single().kind)
        assertEquals(ThreadSummaryRunningLabel, state.subagents.single().meta)
        assertEquals(listOf("proc"), state.processes.map { it.id })
        assertEquals("npm run dev", state.processes.single().label)
        assertTrue(state.hasRows)
        controller.close()
    }

    @Test
    fun failingOverviewKeepsProcessRowsAndNeverInventsAgents() = runTest {
        val owner = owner()
        val controller = ThreadSummaryActivityController(backgroundScope, repository(
            overview = { error("transport down") },
            output = { readyOutput(owner, "watch", BackgroundProcessLifecycle.Running) },
        ))
        controller.bind(owner)
        controller.refresh()
        runCurrent()
        assertTrue(controller.state.value.subagents.isEmpty())
        assertEquals(listOf("proc"), controller.state.value.processes.map { it.id })
        controller.close()
    }

    @Test
    fun rebindFencesANonCooperativeLateOverview() = runTest {
        val owner = owner()
        val gate = CompletableDeferred<PanelLoad<SubagentOverview>>()
        val controller = ThreadSummaryActivityController(backgroundScope, repository(
            overview = { withContext(NonCancellable) { gate.await() } },
            output = { PanelLoad.Unavailable(owner.scope, PanelUnavailableReason.TerminalServiceMissing) },
        ))
        controller.bind(owner)
        controller.refresh()
        runCurrent()
        val replacement = owner.copy(host = owner.host.copy(generation = 2))
        controller.bind(replacement)
        assertEquals(ThreadSummaryActivityPhase.Idle, controller.state.value.phase)
        gate.complete(PanelLoad.Ready(owner.scope, SubagentOverview(owner.scope, listOf(child(owner, "secret", ChildAgentStatus.Active)))))
        runCurrent()
        assertTrue(controller.state.value.subagents.isEmpty())
        assertTrue(controller.isBoundTo(replacement))
        controller.close()
    }

    @Test
    fun bindingTheSameOwnerIsANoOpButANewIncarnationResetsRows() = runTest {
        val owner = owner()
        val controller = ThreadSummaryActivityController(backgroundScope, repository(
            overview = { PanelLoad.Ready(owner.scope, SubagentOverview(owner.scope, listOf(child(owner, "a", ChildAgentStatus.Active)))) },
            output = { PanelLoad.Unavailable(owner.scope, PanelUnavailableReason.TerminalServiceMissing) },
        ))
        controller.bind(owner)
        controller.refresh()
        runCurrent()
        assertEquals(listOf("a"), controller.state.value.subagents.map { it.id })
        controller.bind(owner.copy())
        assertEquals(listOf("a"), controller.state.value.subagents.map { it.id })
        controller.bind(owner.copy(host = owner.host.copy(generation = 3)))
        assertTrue(controller.state.value.subagents.isEmpty())
        assertEquals(ThreadSummaryActivityPhase.Idle, controller.state.value.phase)
        controller.close()
    }

    @Test
    fun stopAllProcessesCancelsOnlyRunningProcessesThenRereads() = runTest {
        val owner = owner()
        val repository = stoppingRepository(
            owner,
            running = "proc",
            exited = "proc2",
        )
        val controller = ThreadSummaryActivityController(backgroundScope, repository)
        controller.bind(owner)
        controller.refresh()
        runCurrent()
        assertEquals(listOf("proc", "proc2"), controller.state.value.processes.map { it.id })

        controller.stopAllProcesses()
        runCurrent()

        assertEquals(1, repository.operations.size)
        assertEquals("proc", repository.operations.single().first.processId)
        assertEquals(TerminalOperationKind.Cancel, repository.operations.single().second)
        assertFalse(controller.state.value.stoppingProcesses)
        assertNull(controller.state.value.stopNotice)
        assertEquals(listOf("proc", "proc2"), controller.state.value.processes.map { it.id })
        controller.close()
    }

    @Test
    fun aRejectedStopSurfacesTheServiceMessageAndStaysRetryable() = runTest {
        val owner = owner()
        val repository = stoppingRepository(owner, running = "proc", exited = null) {
            TerminalOperationResult(TerminalOperationOutcome.Rejected, "终端控制服务不可用。")
        }
        val controller = ThreadSummaryActivityController(backgroundScope, repository)
        controller.bind(owner)
        controller.refresh()
        runCurrent()

        controller.stopAllProcesses()
        runCurrent()
        assertEquals("终端控制服务不可用。", controller.state.value.stopNotice)
        assertFalse(controller.state.value.stoppingProcesses)

        controller.stopAllProcesses()
        runCurrent()
        assertEquals(2, repository.operations.size)
        controller.close()
    }

    @Test
    fun anUnknownStopIsNeverReissuedForTheSameIncarnation() = runTest {
        val owner = owner()
        val repository = stoppingRepository(owner, running = "proc", exited = null) {
            TerminalOperationResult(TerminalOperationOutcome.Unknown, "操作结果未知，已禁止重复操作。")
        }
        val controller = ThreadSummaryActivityController(backgroundScope, repository)
        controller.bind(owner)
        controller.refresh()
        runCurrent()

        controller.stopAllProcesses()
        runCurrent()
        assertEquals("操作结果未知，已禁止重复操作。", controller.state.value.stopNotice)
        val afterFirst = repository.operations.size

        assertNull(controller.stopAllProcesses())
        runCurrent()
        assertEquals(afterFirst, repository.operations.size)
        controller.close()
    }

    @Test
    fun stopAllProcessesIsANoOpWhenUnboundClosedOrWithoutRunningProcesses() = runTest {
        val owner = owner()
        val controller = ThreadSummaryActivityController(backgroundScope, stoppingRepository(owner, running = null, exited = "proc2"))
        assertNull(controller.stopAllProcesses())
        controller.bind(owner)
        controller.refresh()
        runCurrent()
        assertNull(controller.stopAllProcesses())
        controller.close()
        assertNull(controller.stopAllProcesses())
    }

    private class StoppingRepository(
        private val scope: ThreadPanelScope,
        private val running: String?,
        private val exited: String?,
        private val operate: (BackgroundTerminalKey) -> TerminalOperationResult,
    ) : ThreadPanelsRepository {
        public val operations: MutableList<Pair<BackgroundTerminalKey, TerminalOperationKind>> = mutableListOf()

        private val inventory: List<BackgroundTerminalSummary> = buildList {
            running?.let { add(summary(it, BackgroundProcessLifecycle.Running)) }
            exited?.let { add(summary(it, BackgroundProcessLifecycle.Exited(0))) }
        }

        private fun summary(id: String, lifecycle: BackgroundProcessLifecycle) =
            BackgroundTerminalSummary(BackgroundTerminalKey(scope, id), "run $id", lifecycle)

        override suspend fun readOverview(owner: ThreadPanelsOwner): PanelLoad<SubagentOverview> =
            PanelLoad.Ready(owner.scope, SubagentOverview(owner.scope, emptyList()))

        override suspend fun readDetail(owner: ThreadPanelsOwner, child: ChildAgentKey): PanelLoad<ChildAgentDetail> =
            error("not selected")

        override suspend fun readTerminalSelection(
            owner: ThreadPanelsOwner,
            selectedKey: BackgroundTerminalKey?,
        ): BackgroundTerminalSelection {
            val first = inventory.firstOrNull()
            val output = if (first == null) PanelLoad.Empty(owner.scope) else PanelLoad.Ready(
                owner.scope,
                BackgroundTerminalOutput(first.key, "", 0, first.command, lifecycle = first.lifecycle),
            )
            return BackgroundTerminalSelection(owner.scope, inventory, first?.key, output)
        }

        override suspend fun operateTerminal(
            owner: ThreadPanelsOwner,
            key: BackgroundTerminalKey,
            kind: TerminalOperationKind,
            input: String,
        ): TerminalOperationResult {
            operations += key to kind
            return operate(key)
        }

    }

    private fun stoppingRepository(
        owner: ThreadPanelsOwner,
        running: String?,
        exited: String?,
        operate: (BackgroundTerminalKey) -> TerminalOperationResult = {
            TerminalOperationResult(TerminalOperationOutcome.Applied, "已请求取消；刷新可查看退出状态。")
        },
    ): StoppingRepository = StoppingRepository(owner.scope, running, exited, operate)

    private fun repository(
        overview: suspend (ThreadPanelsOwner) -> PanelLoad<SubagentOverview>,
        output: suspend (ThreadPanelsOwner) -> PanelLoad<BackgroundTerminalOutput>,
    ) = ReadOnlyThreadPanelsRepository(
        readOverview = overview,
        readChildHistory = { _, _ -> error("not selected") },
        readBackgroundOutput = output,
    )

    private fun readyOutput(
        owner: ThreadPanelsOwner,
        command: String,
        lifecycle: BackgroundProcessLifecycle,
    ): PanelLoad<BackgroundTerminalOutput> = PanelLoad.Ready(
        owner.scope,
        BackgroundTerminalOutput(
            key = BackgroundTerminalKey(owner.scope, "proc"),
            buffer = "",
            revision = 0,
            command = command,
            lifecycle = lifecycle,
            inputState = BackgroundInputState.Open,
        ),
    )

    private fun owner(): ThreadPanelsOwner =
        ThreadPanelsOwner(
            StubConnection, ConversationHostScope(HostId("host"), 1, null), SessionId("parent"),
        )

    private fun child(
        owner: ThreadPanelsOwner,
        id: String,
        status: ChildAgentStatus,
    ): ChildAgentSummary = ChildAgentSummary(
        ChildAgentKey(owner.scope, SessionId(id)), listOf(owner.parentSessionId, SessionId(id)),
        status, canInteract = false, historyAvailability = ChildHistoryAvailability.Available,
    )

    /** A presentation-only controller must never call any SessionApi or HostApi operation. */
    private object StubConnection : Connection {
        override val host: HostApi = object : HostApi {
            override suspend fun describe() = error("Must not describe")
        }
        override val session: SessionApi = object : SessionApi {
            override suspend fun list(): List<SessionSummary> = error("Must not list")
            override suspend fun create(id: SessionId?, options: CreateSessionOptions, agentOptions: AgentOptions): SessionSummary =
                error("Must not create")
            override suspend fun history(id: SessionId): List<Message> = error("Must not read history")
            override suspend fun prompt(id: SessionId, message: Message): SessionPrompt = error("Must not prompt")
            override suspend fun cancel(id: SessionId, keepInbox: Boolean) = error("Must not cancel")
        }
    }
}
