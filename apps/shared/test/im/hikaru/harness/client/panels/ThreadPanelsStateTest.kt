package im.hikaru.harness.client.panels

import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.harness.client.connection.SessionId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ThreadPanelsStateTest {
    private val scope = ThreadPanelScope(HostId("host-a"), SessionId("parent"))

    @Test
    fun defaultStateIsExplicitlyUnavailable() {
        val state = ThreadPanelsState(scope)

        assertTrue(state.terminal is PanelLoad.Unavailable)
        assertEquals(PanelUnavailableReason.TerminalServiceMissing, (state.terminal as PanelLoad.Unavailable).reason)
        assertTrue(state.subagents is PanelLoad.Unavailable)
        assertEquals(PanelUnavailableReason.ChildrenServiceMissing, (state.subagents as PanelLoad.Unavailable).reason)
        assertFalse(state.interactiveDetail != null)
    }

    @Test
    fun scopedLoadsAndLateResponsesAreIgnored() {
        val started = ThreadPanelsState(scope).reduce(ThreadPanelAction.BeginOverviewLoad(scope))
        assertTrue(started.subagents is PanelLoad.Loading)
        assertEquals(1L, started.overviewGeneration)

        val child = child("child-1", canInteract = true, history = ChildHistoryAvailability.Available)
        val ready = PanelLoad.Ready(scope, SubagentOverview(scope, listOf(child)))
        val accepted = started.reduce(ThreadPanelAction.ReceiveOverview(scope, started.overviewGeneration, ready))
        assertTrue(accepted.subagents is PanelLoad.Ready)

        val selected = accepted.reduce(ThreadPanelAction.SelectChild(child.key))
        assertTrue(selected.detail is PanelLoad.Loading)
        val switched = selected.reduce(ThreadPanelAction.Back(scope))
        assertNull(switched.selectedChild)
        assertEquals(selected.detailGeneration + 1, switched.detailGeneration)

        val lateDetail = PanelLoad.Ready(scope, ChildAgentDetail(child.key, emptyList(), canInteract = true))
        val ignored = switched.reduce(ThreadPanelAction.ReceiveDetail(child.key, selected.detailGeneration, lateDetail))
        assertNull(ignored.selectedChild)
        assertTrue(ignored.detail is PanelLoad.Unavailable)
    }

    @Test
    fun activeAndDoneLimitsAndUnknownStatusStayTruthful() {
        val children = (1..6).map { child("active-$it", recency = it.toLong()) } +
            (1..12).map { child("done-$it", status = ChildAgentStatus.Done, recency = it.toLong()) } +
            child("unknown", status = ChildAgentStatus.Unknown)
        val state = ThreadPanelsState(scope, subagents = PanelLoad.Ready(scope, SubagentOverview(scope, children)))

        assertEquals(4, state.visibleChildren(SubagentSection.Active).size)
        assertEquals(10, state.visibleChildren(SubagentSection.Done).size)
        assertEquals(1, (state.subagents as PanelLoad.Ready).value.unknown.size)

        val expanded = state.reduce(ThreadPanelAction.ShowMore(scope, SubagentSection.Active))
            .reduce(ThreadPanelAction.ShowMore(scope, SubagentSection.Done))
        assertEquals(6, expanded.visibleChildren(SubagentSection.Active).size)
        assertEquals(12, expanded.visibleChildren(SubagentSection.Done).size)
    }

    @Test
    fun sectionHeadingsMatchCodexCountRules() {
        // Codex `subagents.panel.active` carries no count; `.done` is 已完成 · {count}.
        assertEquals("进行中", SubagentSection.Active.heading(7))
        assertEquals("已完成 · 0", SubagentSection.Done.heading(0))
        assertEquals("已完成 · 12", SubagentSection.Done.heading(12))
        assertFailsWith<IllegalArgumentException> { SubagentSection.Done.heading(-1) }
    }

    @Test
    fun terminalAcceptsOnlyNewerExternalRevision() {
        val first = terminal(1, "one")
        val started = ThreadPanelsState(scope, terminal = PanelLoad.Ready(scope, first))
            .reduce(ThreadPanelAction.BeginTerminalLoad(scope))
        val newer = terminal(2, "two")
        val accepted = started.reduce(ThreadPanelAction.ReceiveTerminal(scope, started.terminalGeneration, PanelLoad.Ready(scope, newer)))
        assertEquals("two", (accepted.terminal as PanelLoad.Ready).value.buffer)

        val old = terminal(1, "old")
        val ignored = accepted.reduce(ThreadPanelAction.ReceiveTerminal(scope, accepted.terminalGeneration, PanelLoad.Ready(scope, old)))
        assertEquals("two", (ignored.terminal as PanelLoad.Ready).value.buffer)
    }

    @Test
    fun widthPolicyKeepsReferenceMinimumWithinCompactHost() {
        assertEquals(700f, ThreadPanelLayout.preferredWidth(1200f, 800f))
        assertEquals(640f, ThreadPanelLayout.preferredWidth(1000f, 600f))
        assertEquals(200f, ThreadPanelLayout.resolveWidth(200f, 600f, 200f))
        assertEquals(320f, ThreadPanelLayout.resolveWidth(1000f, 600f, 320f))
        assertEquals(648f, ThreadPanelLayout.resolveWidth(1000f, 600f, 1000f, requestedWidth = 900f))
        assertEquals(680f, ThreadPanelLayout.resolveWidth(1000f, 600f, 1000f, ThreadPanelWidthMode.Unified, 900f))
        assertEquals(900f, ThreadPanelLayout.resolveWidth(1000f, 600f, 1000f, ThreadPanelWidthMode.Full, 900f))
        assertEquals(600f, ThreadPanelLayout.resolveWidth(1000f, 600f, 1000f, requestedWidth = Float.NaN))
        assertEquals(0f, ThreadPanelLayout.resolveWidth(1000f, 600f, 0f))
    }

    @Test
    fun loadingErrorAndSuccessfulEmptyRemainDistinct() {
        val initial = ThreadPanelsState(scope)
        val loading = initial.reduce(ThreadPanelAction.BeginOverviewLoad(scope))
        assertTrue(loading.subagents is PanelLoad.Loading)
        val failed = loading.reduce(ThreadPanelAction.ReceiveOverview(scope, loading.overviewGeneration,
            PanelLoad.Error(scope, "Host disconnected")))
        assertTrue(failed.subagents is PanelLoad.Error)
        val retry = failed.reduce(ThreadPanelAction.BeginOverviewLoad(scope))
        assertEquals(retry, retry.reduce(ThreadPanelAction.ReceiveOverview(scope, loading.overviewGeneration,
            PanelLoad.Ready(scope, SubagentOverview(scope, emptyList())))))
        val empty = retry.reduce(ThreadPanelAction.ReceiveOverview(scope, retry.overviewGeneration,
            PanelLoad.Ready(scope, SubagentOverview(scope, emptyList()))))
        assertTrue(empty.subagents is PanelLoad.Ready)
        assertTrue((empty.subagents as PanelLoad.Ready).value.children.isEmpty())

        val emptyOutput = initial.reduce(ThreadPanelAction.ReceiveTerminal(scope, 0,
            PanelLoad.Ready(scope, terminal(0, ""))))
        assertTrue(emptyOutput.terminal is PanelLoad.Ready)
        assertEquals("", (emptyOutput.terminal as PanelLoad.Ready).value.buffer)
    }

    @Test
    fun foreignActionsAndNestedPayloadScopesCannotPublish() {
        val state = ThreadPanelsState(scope)
        val otherHost = scope.copy(hostId = HostId("host-b"))
        val otherParent = scope.copy(conversationId = SessionId("other-parent"))
        for (foreign in listOf(otherHost, otherParent)) {
            assertEquals(state, state.reduce(ThreadPanelAction.CloseTab(foreign, ThreadPanelTab.BackgroundOutput)))
            assertEquals(state, state.reduce(ThreadPanelAction.ReceiveOverview(scope, 0,
                PanelLoad.Ready(foreign, SubagentOverview(foreign, emptyList())))))
            assertEquals(state, state.reduce(ThreadPanelAction.ReceiveOverview(scope, 0,
                PanelLoad.Ready(scope, SubagentOverview(foreign, emptyList())))))
            val output = BackgroundTerminalOutput(BackgroundTerminalKey(foreign, "process"), "foreign", 0)
            assertEquals(state, state.reduce(ThreadPanelAction.ReceiveTerminal(scope, 0, PanelLoad.Ready(scope, output))))
        }
    }

    @Test
    fun closeSelectsNeighbourAndFencesResponsesAcrossReopen() {
        val initial = ThreadPanelsState(scope).reduce(ThreadPanelAction.BeginTerminalLoad(scope))
        val closed = initial.reduce(ThreadPanelAction.CloseTab(scope, ThreadPanelTab.BackgroundOutput))
        assertEquals(ThreadPanelTab.Subagents, closed.selectedTab)
        val reopened = closed.reduce(ThreadPanelAction.OpenTab(scope, ThreadPanelTab.BackgroundOutput))
        assertEquals(ThreadPanelTab.BackgroundOutput, reopened.selectedTab)
        assertEquals(reopened, reopened.reduce(ThreadPanelAction.ReceiveTerminal(scope, initial.terminalGeneration,
            PanelLoad.Ready(scope, terminal(1, "late")))))
        val previous = reopened.reduce(ThreadPanelAction.CloseTab(scope, ThreadPanelTab.BackgroundOutput))
        assertEquals(ThreadPanelTab.Subagents, previous.selectedTab)
        val final = previous.reduce(ThreadPanelAction.CloseTab(scope, ThreadPanelTab.Subagents))
        assertNull(final.selectedTab)
        assertTrue(final.openTabs.isEmpty())
        assertEquals(final, final.reduce(ThreadPanelAction.SelectTab(scope, ThreadPanelTab.Subagents)))
    }

    @Test
    fun selectingWithoutReadableHistoryStaysUnavailable() {
        val row = child("child")
        val state = overview(listOf(row))
        val selected = state.reduce(ThreadPanelAction.SelectChild(row.key))
        assertEquals(row.key, selected.selectedChild)
        assertTrue(selected.detail is PanelLoad.Unavailable)
        assertEquals(state, state.reduce(ThreadPanelAction.SelectChild(child("not-in-list").key)))
    }

    @Test
    fun rapidSelectionAndRemovalFenceDetailResults() {
        val first = child("first", history = ChildHistoryAvailability.Available)
        val second = child("second", history = ChildHistoryAvailability.Available)
        val selectedFirst = overview(listOf(first, second)).reduce(ThreadPanelAction.SelectChild(first.key))
        val selectedSecond = selectedFirst.reduce(ThreadPanelAction.SelectChild(second.key))
        assertEquals(selectedSecond, selectedSecond.reduce(ThreadPanelAction.ReceiveDetail(first.key,
            selectedFirst.detailGeneration, PanelLoad.Ready(scope, ChildAgentDetail(first.key, emptyList(), false)))))
        assertEquals(selectedSecond, selectedSecond.reduce(ThreadPanelAction.ReceiveDetail(second.key,
            selectedSecond.detailGeneration, PanelLoad.Ready(scope, ChildAgentDetail(first.key, emptyList(), false)))))
        val removed = selectedSecond.reduce(ThreadPanelAction.ReceiveOverview(scope, selectedSecond.overviewGeneration,
            PanelLoad.Ready(scope, SubagentOverview(scope, listOf(first)))))
        assertNull(removed.selectedChild)
        assertEquals(removed, removed.reduce(ThreadPanelAction.ReceiveDetail(second.key,
            selectedSecond.detailGeneration, PanelLoad.Ready(scope, ChildAgentDetail(second.key, emptyList(), false)))))
    }

    @Test
    fun interactionRequiresBothRealAuthorizationsAndRevocationTakesEffect() {
        val row = child("child", canInteract = true, history = ChildHistoryAvailability.Available)
        val selected = overview(listOf(row)).reduce(ThreadPanelAction.SelectChild(row.key))
        val readOnly = selected.reduce(ThreadPanelAction.ReceiveDetail(row.key, selected.detailGeneration,
            PanelLoad.Ready(scope, ChildAgentDetail(row.key, emptyList(), false))))
        assertNull(readOnly.interactiveDetail)
        val interactive = readOnly.reduce(ThreadPanelAction.ReceiveDetail(row.key, readOnly.detailGeneration,
            PanelLoad.Ready(scope, ChildAgentDetail(row.key, emptyList(), true))))
        assertEquals(row.key, interactive.interactiveDetail?.key)
        val revoked = interactive.reduce(ThreadPanelAction.ReceiveOverview(scope, interactive.overviewGeneration,
            PanelLoad.Ready(scope, SubagentOverview(scope, listOf(row.copy(canInteract = false))))))
        assertNull(revoked.interactiveDetail)
        val unreadable = interactive.reduce(ThreadPanelAction.ReceiveOverview(scope, interactive.overviewGeneration,
            PanelLoad.Ready(scope, SubagentOverview(scope, listOf(row.copy(historyAvailability = ChildHistoryAvailability.Unavailable))))))
        assertTrue(unreadable.detail is PanelLoad.Unavailable)
        assertNull(unreadable.interactiveDetail)
        assertEquals(unreadable, unreadable.reduce(ThreadPanelAction.ReceiveDetail(row.key, interactive.detailGeneration,
            PanelLoad.Ready(scope, ChildAgentDetail(row.key, emptyList(), true)))))
    }

    @Test
    fun invalidAncestryAndMismatchedSnapshotsAreRejected() {
        assertFailsWith<IllegalArgumentException> { ChildAgentKey(scope, scope.conversationId) }
        val row = child("child")
        assertFailsWith<IllegalArgumentException> { row.copy(verifiedAncestorPath = listOf(row.key.sessionId, scope.conversationId)) }
        assertFailsWith<IllegalArgumentException> { row.copy(verifiedAncestorPath = listOf(scope.conversationId, row.key.sessionId, scope.conversationId, row.key.sessionId)) }
        assertFailsWith<IllegalArgumentException> { SubagentOverview(scope, listOf(row, row)) }
        assertFailsWith<IllegalArgumentException> { ThreadPanelsState(scope, detail = PanelLoad.Ready(scope, ChildAgentDetail(row.key, emptyList(), false))) }
        assertEquals("some objective", row.copy(objective = "  some   objective  ").preview)
    }

    private fun overview(rows: List<ChildAgentSummary>): ThreadPanelsState =
        ThreadPanelsState(scope, subagents = PanelLoad.Ready(scope, SubagentOverview(scope, rows)))

    private fun child(
        id: String,
        status: ChildAgentStatus = ChildAgentStatus.Active,
        canInteract: Boolean = false,
        history: ChildHistoryAvailability = ChildHistoryAvailability.Unavailable,
        recency: Long? = null,
    ): ChildAgentSummary = ChildAgentSummary(
        key = ChildAgentKey(scope, SessionId(id)),
        verifiedAncestorPath = listOf(scope.conversationId, SessionId(id)),
        status = status,
        canInteract = canInteract,
        historyAvailability = history,
        objective = "Objective $id",
        recencyAtEpochMillis = recency,
    )

    private fun terminal(revision: Long, buffer: String): BackgroundTerminalOutput =
        BackgroundTerminalOutput(BackgroundTerminalKey(scope, "process-1"), buffer, revision)
}
