package im.hikaru.harness.client.summary

import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.harness.client.connection.BackgroundProcessLifecycle
import im.hikaru.harness.client.connection.SessionId
import im.hikaru.harness.client.panels.BackgroundTerminalKey
import im.hikaru.harness.client.panels.BackgroundTerminalSummary
import im.hikaru.harness.client.panels.ChildAgentKey
import im.hikaru.harness.client.panels.ChildAgentStatus
import im.hikaru.harness.client.panels.ChildAgentSummary
import im.hikaru.harness.client.panels.ChildHistoryAvailability
import im.hikaru.harness.client.panels.PanelLoad
import im.hikaru.harness.client.panels.PanelUnavailableReason
import im.hikaru.harness.client.panels.SubagentOverview
import im.hikaru.harness.client.panels.ThreadPanelScope
import kotlin.test.*

class ThreadSummaryActivityTest {
    private val scope = ThreadPanelScope(HostId("host"), SessionId("parent"))

    @Test
    fun unavailableLoadingEmptyAndErrorSubagentReadsProduceNoRows() {
        assertTrue(threadSummarySubagents(PanelLoad.Unavailable(scope, PanelUnavailableReason.ChildrenServiceMissing)).isEmpty())
        assertTrue(threadSummarySubagents(PanelLoad.Loading(scope)).isEmpty())
        assertTrue(threadSummarySubagents(PanelLoad.Empty(scope)).isEmpty())
        assertTrue(threadSummarySubagents(PanelLoad.Error(scope, "boom")).isEmpty())
        assertTrue(threadSummarySubagents(PanelLoad.Ready(scope, SubagentOverview(scope, emptyList()))).isEmpty())
    }

    @Test
    fun runningChildrenLeadThenRecencyAndCarryTheOriginalLabels() {
        val done = child("done", ChildAgentStatus.Done, recency = 900) // recent but finished
        val active = child("active", ChildAgentStatus.Active, recency = 100)
        val waiting = child("waiting", ChildAgentStatus.Waiting, recency = 200)
        val unknown = child("unknown", ChildAgentStatus.Unknown, recency = 950)
        val rows = threadSummarySubagents(PanelLoad.Ready(scope, SubagentOverview(scope, listOf(done, active, waiting, unknown))))
        assertEquals(listOf("waiting", "active", "unknown", "done"), rows.map { it.id })
        assertTrue(rows.all { it.kind == ThreadSummaryActivityKind.Subagent })
        assertEquals(ThreadSummaryRunningLabel, rows[0].meta)
        assertEquals(ThreadSummaryRunningLabel, rows[1].meta)
        assertNull(rows[2].meta)
        assertNull(rows[3].meta)
        assertTrue(rows[0].running && rows[1].running)
        assertFalse(rows[2].running || rows[3].running)
    }

    @Test
    fun subagentRowLabelFallsBackToSessionIdWhenNoDisplayNameIsReported() {
        val named = child("child-a", ChildAgentStatus.Active, displayName = "Reviewer")
        val unnamed = child("child-b", ChildAgentStatus.Active)
        val rows = threadSummarySubagents(PanelLoad.Ready(scope, SubagentOverview(scope, listOf(named, unnamed))))
        assertEquals(setOf("Reviewer", "child-b"), rows.map { it.label }.toSet())
    }

    @Test
    fun processRowsUseTheCommandFirstLineRunningStateAndTheOriginalFallback() {
        val rows = threadSummaryProcesses(
            listOf(
                terminal("p1", "  npm run dev  \n--watch", BackgroundProcessLifecycle.Running),
                terminal("p2", "   ", BackgroundProcessLifecycle.Exited(0)),
                terminal("p3", null, BackgroundProcessLifecycle.Unknown),
            ),
        )
        assertEquals(listOf("npm run dev", ThreadSummaryBackgroundTerminalLabel, ThreadSummaryBackgroundTerminalLabel), rows.map { it.label })
        assertEquals(listOf("p1", "p2", "p3"), rows.map { it.id })
        assertTrue(rows.all { it.kind == ThreadSummaryActivityKind.Process })
        assertTrue(rows[0].running)
        assertFalse(rows[1].running || rows[2].running)
        assertTrue(rows.all { it.meta == null })
    }

    @Test
    fun activityRejectsBlankIdentity() {
        assertFailsWith<IllegalArgumentException> { ThreadSummaryActivity(ThreadSummaryActivityKind.Subagent, " ", "label") }
        assertFailsWith<IllegalArgumentException> { ThreadSummaryActivity(ThreadSummaryActivityKind.Subagent, "id", " ") }
        assertFailsWith<IllegalArgumentException> { ThreadSummaryActivity(ThreadSummaryActivityKind.Subagent, "id", "label", meta = " ") }
    }

    private fun child(
        id: String,
        status: ChildAgentStatus,
        recency: Long? = null,
        displayName: String? = null,
    ): ChildAgentSummary = ChildAgentSummary(
        key = ChildAgentKey(scope, SessionId(id)),
        verifiedAncestorPath = listOf(scope.conversationId, SessionId(id)),
        status = status,
        canInteract = false,
        historyAvailability = ChildHistoryAvailability.Available,
        displayName = displayName,
        recencyAtEpochMillis = recency,
    )

    private fun terminal(id: String, command: String?, lifecycle: BackgroundProcessLifecycle): BackgroundTerminalSummary =
        BackgroundTerminalSummary(BackgroundTerminalKey(scope, id), command, lifecycle)
}
