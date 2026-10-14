package im.hikaru.harness.client.panels

import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.harness.client.account.BackendTenant
import im.hikaru.harness.client.account.MemberIdentity
import im.hikaru.harness.client.connection.*
import im.hikaru.harness.client.conversation.ConversationHostScope
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ThreadPanelsControllerTest {
    @Test
    fun defaultRepositoryIsUnavailableAndOpeningNeverCallsSessionMutation() = runTest {
        val controller = ThreadPanelsController(backgroundScope)
        val owner = owner()
        controller.bind(owner)
        assertTrue(controller.state.value!!.openTabs.isEmpty())
        assertNull(controller.state.value!!.selectedTab)
        assertNull(controller.refreshOverview())
        bindAndOpen(controller, owner)
        runCurrent()
        val state = assertNotNull(controller.state.value)
        assertEquals(PanelUnavailableReason.ChildrenServiceMissing, (state.subagents as PanelLoad.Unavailable).reason)
        assertEquals(PanelUnavailableReason.TerminalServiceMissing, (state.terminal as PanelLoad.Unavailable).reason)
        controller.dispatch(ThreadPanelAction.CloseTab(owner.scope, ThreadPanelTab.BackgroundOutput))
        controller.dispatch(ThreadPanelAction.OpenTab(owner.scope, ThreadPanelTab.BackgroundOutput))
        runCurrent()
        assertTrue(controller.state.value!!.terminal is PanelLoad.Unavailable)
        assertNull(controller.refreshDetail())
        controller.close()
        assertNull(controller.state.value)
        assertFalse(controller.isBoundTo(null))
    }

    @Test
    fun everyOwnerDimensionAndDisconnectFenceNonCooperativeOverview() = runTest {
        val original = owner()
        // EqualConnections deliberately compare equal: referential identity must still fence.
        val replacements = listOf<ThreadPanelsOwner?>(
            original.copy(connection = EqualConnection()),
            original.copy(host = original.host.copy(generation = 2)),
            original.copy(host = original.host.copy(hostId = HostId("other-host"))),
            original.copy(host = original.host.copy(identity = identity(1))),
            original.copy(parentSessionId = SessionId("other-parent")),
            null,
        )
        for (replacement in replacements) {
            val gate = CompletableDeferred<PanelLoad<SubagentOverview>>()
            var reads = 0
            val repository = ReadOnlyThreadPanelsRepository(
                readOverview = { captured ->
                    reads++
                    if (reads == 1) withContext(NonCancellable) { gate.await() } else emptyOverview(captured)
                },
                readChildHistory = { _, _ -> error("not selected") },
            )
            val controller = ThreadPanelsController(backgroundScope, repository)
            bindAndOpen(controller, original)
            runCurrent()
            if (replacement == null) controller.bind(null) else bindAndOpen(controller, replacement)
            if (replacement == null) assertNull(controller.state.value)
            else assertEquals(replacement.scope, controller.state.value!!.scope)
            runCurrent()
            gate.complete(overview(original, child(original, "secret")))
            runCurrent()
            if (replacement == null) assertNull(controller.state.value)
            else {
                assertTrue(controller.isBoundTo(replacement))
                assertTrue(readyOverview(controller).children.isEmpty())
            }
            controller.close()
        }
    }

    @Test
    fun fullTenantIdentityAndSameOwnerNoOpAreRespected() = runTest {
        var reads = 0
        val controller = ThreadPanelsController(backgroundScope, ReadOnlyThreadPanelsRepository(
            { reads++; emptyOverview(it) }, { _, _ -> error("not selected") },
        ))
        val first = owner().copy(host = owner().host.copy(identity = identity(1)))
        bindAndOpen(controller, first)
        runCurrent()
        controller.bind(first.copy())
        runCurrent()
        assertEquals(1, reads)
        val second = first.copy(host = first.host.copy(identity = identity(2)))
        bindAndOpen(controller, second)
        assertTrue(controller.state.value!!.subagents is PanelLoad.Loading)
        runCurrent()
        assertEquals(2, reads)
        assertFalse(controller.isBoundTo(first))
        controller.close()
    }

    @Test
    fun notificationRefreshPreservesSelectionAndIndependentPendingHistory() = runTest {
        val owner = owner()
        val row = child(owner, "child")
        val overviewGate = CompletableDeferred<PanelLoad<SubagentOverview>>()
        val historyGate = CompletableDeferred<PanelLoad<ChildAgentDetail>>()
        var reads = 0
        val controller = ThreadPanelsController(backgroundScope, ReadOnlyThreadPanelsRepository(
            { reads++; if (reads == 1) overview(owner, row) else overviewGate.await() },
            { captured, selected ->
                assertSame(owner.connection, captured.connection)
                assertEquals(row.key, selected)
                historyGate.await()
            },
        ))
        bindAndOpen(controller, owner)
        runCurrent()
        controller.dispatch(ThreadPanelAction.SelectChild(row.key))
        runCurrent()
        val detailGeneration = controller.state.value!!.detailGeneration
        controller.refreshOverview()
        runCurrent()
        assertEquals(row.key, controller.state.value!!.selectedChild)
        assertEquals(detailGeneration, controller.state.value!!.detailGeneration)
        assertTrue(controller.state.value!!.overviewRefreshing)
        historyGate.complete(detail(row, "real history"))
        runCurrent()
        assertEquals("real history", readyDetail(controller).entries.single().text)
        overviewGate.complete(overview(owner, row))
        runCurrent()
        assertFalse(controller.state.value!!.overviewRefreshing)
        assertEquals(row.key, controller.state.value!!.selectedChild)
        assertEquals("real history", readyDetail(controller).entries.single().text)
        assertNull(controller.state.value!!.interactiveDetail)
        controller.close()
    }

    @Test
    fun newerOverviewRequestWinsEvenIfCancelledReadReturnsLast() = runTest {
        val owner = owner()
        val old = CompletableDeferred<PanelLoad<SubagentOverview>>()
        var reads = 0
        val controller = ThreadPanelsController(backgroundScope, ReadOnlyThreadPanelsRepository(
            { reads++; if (reads == 1) withContext(NonCancellable) { old.await() }
                else overview(owner, child(owner, "latest")) },
            { _, _ -> error("not selected") },
        ))
        bindAndOpen(controller, owner)
        runCurrent()
        controller.refreshOverview()
        runCurrent()
        old.complete(overview(owner, child(owner, "stale")))
        runCurrent()
        assertEquals("latest", readyOverview(controller).children.single().key.sessionId.value)
        controller.close()
    }

    @Test
    fun selectBackCloseAndReopenFenceLateHistory() = runTest {
        for (operation in 0..2) {
            val owner = owner()
            val first = child(owner, "first")
            val second = child(owner, "second")
            val gate = CompletableDeferred<PanelLoad<ChildAgentDetail>>()
            val controller = ThreadPanelsController(backgroundScope, ReadOnlyThreadPanelsRepository(
                { overview(owner, first, second) },
                { _, key -> if (key == first.key) withContext(NonCancellable) { gate.await() }
                    else detail(second, "second transcript") },
            ))
            bindAndOpen(controller, owner)
            runCurrent()
            controller.dispatch(ThreadPanelAction.SelectChild(first.key))
            runCurrent()
            when (operation) {
                0 -> controller.dispatch(ThreadPanelAction.SelectChild(second.key))
                1 -> controller.dispatch(ThreadPanelAction.Back(owner.scope))
                else -> {
                    controller.dispatch(ThreadPanelAction.CloseTab(owner.scope, ThreadPanelTab.Subagents))
                    controller.dispatch(ThreadPanelAction.OpenTab(owner.scope, ThreadPanelTab.Subagents))
                }
            }
            runCurrent()
            gate.complete(detail(first, "private old history"))
            runCurrent()
            if (operation == 0) {
                assertEquals(second.key, controller.state.value!!.selectedChild)
                assertEquals("second transcript", readyDetail(controller).entries.single().text)
            } else {
                assertNull(controller.state.value!!.selectedChild)
                assertTrue(controller.state.value!!.detail is PanelLoad.Unavailable)
            }
            controller.close()
        }
    }

    @Test
    fun bindDisconnectAndControllerCloseFenceLateDetail() = runTest {
        for (operation in 0..2) {
            val owner = owner()
            val row = child(owner, "child")
            val gate = CompletableDeferred<PanelLoad<ChildAgentDetail>>()
            val controller = ThreadPanelsController(backgroundScope, ReadOnlyThreadPanelsRepository(
                { captured -> overview(captured, child(captured, "child")) },
                { _, _ -> withContext(NonCancellable) { gate.await() } },
            ))
            bindAndOpen(controller, owner)
            runCurrent()
            controller.dispatch(ThreadPanelAction.SelectChild(row.key))
            runCurrent()
            when (operation) {
                0 -> bindAndOpen(controller, owner.copy(host = owner.host.copy(generation = 9)))
                1 -> controller.bind(null)
                else -> controller.close()
            }
            runCurrent()
            gate.complete(detail(row, "stale"))
            runCurrent()
            if (operation == 0) {
                assertNull(controller.state.value!!.selectedChild)
                assertTrue(controller.state.value!!.detail is PanelLoad.Unavailable)
            } else assertNull(controller.state.value)
            controller.close()
        }
    }

    @Test
    fun removalAndHistoryRevocationInvalidatePendingDetail() = runTest {
        for (remove in listOf(true, false)) {
            val owner = owner()
            val row = child(owner, "child")
            val gate = CompletableDeferred<PanelLoad<ChildAgentDetail>>()
            var reads = 0
            val controller = ThreadPanelsController(backgroundScope, ReadOnlyThreadPanelsRepository(
                { reads++; if (reads == 1) overview(owner, row) else if (remove) emptyOverview(owner)
                    else overview(owner, row.copy(historyAvailability = ChildHistoryAvailability.Unavailable)) },
                { _, _ -> withContext(NonCancellable) { gate.await() } },
            ))
            bindAndOpen(controller, owner)
            runCurrent()
            controller.dispatch(ThreadPanelAction.SelectChild(row.key))
            runCurrent()
            controller.refreshOverview()
            runCurrent()
            gate.complete(detail(row, "revoked"))
            runCurrent()
            if (remove) assertNull(controller.state.value!!.selectedChild)
            else assertEquals(row.key, controller.state.value!!.selectedChild)
            assertTrue(controller.state.value!!.detail is PanelLoad.Unavailable)
            assertNull(controller.refreshDetail())
            controller.close()
        }
    }

    @Test
    fun detailRefreshOnSameChildUsesNewRequestAndGeneration() = runTest {
        val owner = owner()
        val row = child(owner, "child")
        val old = CompletableDeferred<PanelLoad<ChildAgentDetail>>()
        var reads = 0
        val controller = ThreadPanelsController(backgroundScope, ReadOnlyThreadPanelsRepository(
            { overview(owner, row) },
            { _, _ -> reads++; if (reads == 1) withContext(NonCancellable) { old.await() } else detail(row, "new") },
        ))
        bindAndOpen(controller, owner)
        runCurrent()
        controller.dispatch(ThreadPanelAction.SelectChild(row.key))
        runCurrent()
        controller.refreshDetail()
        runCurrent()
        old.complete(detail(row, "old"))
        runCurrent()
        assertEquals("new", readyDetail(controller).entries.single().text)
        controller.close()
    }

    @Test
    fun emptyIncompleteUnavailableAndErrorAreDistinctAndErrorsAreSanitized() = runTest {
        val owner = owner()
        var reads = 0
        val controller = ThreadPanelsController(backgroundScope, ReadOnlyThreadPanelsRepository(
            { reads++; when (reads) {
                1 -> emptyOverview(owner)
                2 -> PanelLoad.Ready(owner.scope, SubagentOverview(owner.scope, emptyList(),
                    SubagentOverviewCompleteness.LiveOnlyIncomplete))
                3 -> PanelLoad.Unavailable(owner.scope, PanelUnavailableReason.ChildrenServiceMissing)
                else -> error("secret transport response")
            } },
            { _, _ -> error("not selected") },
        ))
        bindAndOpen(controller, owner)
        runCurrent()
        assertEquals(SubagentOverviewCompleteness.Complete, readyOverview(controller).completeness)
        controller.refreshOverview()
        runCurrent()
        assertEquals(SubagentOverviewCompleteness.LiveOnlyIncomplete, readyOverview(controller).completeness)
        controller.refreshOverview()
        runCurrent()
        assertTrue(controller.state.value!!.subagents is PanelLoad.Unavailable)
        controller.refreshOverview()
        runCurrent()
        val error = controller.state.value!!.subagents as PanelLoad.Error
        assertFalse(error.message.contains("secret"))
        controller.close()
    }

    @Test
    fun foreignSnapshotsLoadingCompletionsAndExternalReceiveActionsCannotPublish() = runTest {
        val owner = owner()
        val foreign = owner.copy(parentSessionId = SessionId("foreign"))
        var reads = 0
        val controller = ThreadPanelsController(backgroundScope, ReadOnlyThreadPanelsRepository(
            { reads++; if (reads == 1) emptyOverview(foreign) else PanelLoad.Loading(owner.scope) },
            { _, _ -> error("not selected") },
        ))
        bindAndOpen(controller, owner)
        runCurrent()
        assertTrue(controller.state.value!!.subagents is PanelLoad.Error)
        val before = controller.state.value
        controller.dispatch(ThreadPanelAction.ReceiveOverview(owner.scope, before!!.overviewGeneration, emptyOverview(owner)))
        assertEquals(before, controller.state.value)
        controller.refreshOverview()
        runCurrent()
        assertTrue(controller.state.value!!.subagents is PanelLoad.Error)
        controller.close()
    }

    @Test
    fun cancellationPropagatesAndUnknownStatusNeverBecomesDone() = runTest {
        val owner = owner()
        val row = child(owner, "unknown").copy(status = ChildAgentStatus.Unknown)
        val controller = ThreadPanelsController(backgroundScope, ReadOnlyThreadPanelsRepository(
            { overview(owner, row) }, { _, _ -> throw CancellationException("cancelled") },
        ))
        bindAndOpen(controller, owner)
        runCurrent()
        assertEquals(1, readyOverview(controller).unknown.size)
        assertTrue(readyOverview(controller).children(SubagentSection.Done).isEmpty())
        controller.dispatch(ThreadPanelAction.SelectChild(row.key))
        runCurrent()
        val job = assertNotNull(controller.refreshDetail())
        job.join()
        assertTrue(job.isCancelled)
        assertTrue(controller.state.value!!.detail is PanelLoad.Error)
        controller.close()
    }

    private fun bindAndOpen(controller: ThreadPanelsController, owner: ThreadPanelsOwner) {
        controller.bind(owner)
        controller.dispatch(ThreadPanelAction.OpenTab(owner.scope, ThreadPanelTab.Subagents))
    }

    private fun owner(): ThreadPanelsOwner = ThreadPanelsOwner(
        EqualConnection(), ConversationHostScope(HostId("host"), 1, null), SessionId("parent"),
    )
    private fun identity(tenant: Long): MemberIdentity = MemberIdentity(BackendTenant("https://example.test", tenant), 7)
    private fun child(owner: ThreadPanelsOwner, id: String): ChildAgentSummary = ChildAgentSummary(
        ChildAgentKey(owner.scope, SessionId(id)), listOf(owner.parentSessionId, SessionId(id)),
        ChildAgentStatus.Active, canInteract = true, historyAvailability = ChildHistoryAvailability.Available,
    )
    private fun overview(owner: ThreadPanelsOwner, vararg rows: ChildAgentSummary): PanelLoad<SubagentOverview> =
        PanelLoad.Ready(owner.scope, SubagentOverview(owner.scope, rows.toList()))
    private fun emptyOverview(owner: ThreadPanelsOwner): PanelLoad<SubagentOverview> = overview(owner)
    private fun detail(row: ChildAgentSummary, text: String): PanelLoad<ChildAgentDetail> =
        PanelLoad.Ready(row.key.scope, ChildAgentDetail(row.key,
            listOf(ChildTranscriptEntry("entry", ChildTranscriptRole.Assistant, text)), canInteract = true))
    private fun readyOverview(controller: ThreadPanelsController): SubagentOverview =
        (controller.state.value!!.subagents as PanelLoad.Ready).value
    private fun readyDetail(controller: ThreadPanelsController): ChildAgentDetail =
        (controller.state.value!!.detail as PanelLoad.Ready).value

    /** A read-only panel must never touch any existing unrelated SessionApi operation. */
    private class EqualConnection : Connection {
        override val host: HostApi = object : HostApi {
            override suspend fun describe() = error("Host already verified by parent")
        }
        override val session: SessionApi = object : SessionApi {
            override suspend fun list(): List<SessionSummary> = error("No list inference")
            override suspend fun create(id: SessionId?, options: CreateSessionOptions, agentOptions: AgentOptions): SessionSummary =
                error("No creation")
            override suspend fun history(id: SessionId): List<Message> = error("Must use verified child history")
            override suspend fun prompt(id: SessionId, message: Message): SessionPrompt = error("Read only")
            override suspend fun cancel(id: SessionId, keepInbox: Boolean) = error("Read only")
        }
        override fun equals(other: Any?): Boolean = other is EqualConnection
        override fun hashCode(): Int = 0
    }
}
