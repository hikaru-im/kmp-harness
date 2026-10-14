package im.hikaru.harness.client.summary

import im.hikaru.harness.client.panels.ConnectionThreadPanelsRepository
import im.hikaru.harness.client.panels.BackgroundTerminalKey
import im.hikaru.harness.client.panels.BackgroundTerminalSummary
import im.hikaru.harness.client.panels.PanelLoad
import im.hikaru.harness.client.panels.SubagentOverview
import im.hikaru.harness.client.panels.ThreadPanelsOwner
import im.hikaru.harness.client.panels.ThreadPanelsRepository
import im.hikaru.harness.client.panels.TerminalOperationKind
import im.hikaru.harness.client.panels.TerminalOperationOutcome
import im.hikaru.harness.client.panels.TerminalOperationResult
import im.hikaru.harness.client.connection.BackgroundProcessLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Idle before the first read, Loading while it is in flight, Read after a completed snapshot. */
public enum class ThreadSummaryActivityPhase { Idle, Loading, Read }

/** Background activity for the summary island. Empty lists mean "hide this section". */
public data class ThreadSummaryActivityState(
    val subagents: List<ThreadSummaryActivity> = emptyList(),
    val processes: List<ThreadSummaryActivity> = emptyList(),
    val phase: ThreadSummaryActivityPhase = ThreadSummaryActivityPhase.Idle,
    /** Changes for every owner incarnation, even when the visible scope is equal. */
    val ownerGeneration: Long = 0,
    /** True while a stop-all request is in flight; the island disables its stop actions. */
    val stoppingProcesses: Boolean = false,
    /** Last stop-all outcome that was not `Applied`, exactly as the service reported it. */
    val stopNotice: String? = null,
) {
    public val hasRows: Boolean get() = subagents.isNotEmpty() || processes.isNotEmpty()
}

/**
 * Projection of the bound conversation's background subagents and processes for the summary
 * island, plus the original island's single `停止所有后台终端` action. It reuses the panels
 * repository reads but owns no tab state: opening the island never opens the tabbed context
 * panel, starts a process, or spawns an agent, and the stop action only cancels processes that a
 * prior read listed as running. Every published snapshot is fenced by owner identity and request
 * generation.
 */
public class ThreadSummaryActivityController(
    private val coroutineScope: CoroutineScope,
    private val repository: ThreadPanelsRepository = ConnectionThreadPanelsRepository(),
) {
    private val mutableState = MutableStateFlow(ThreadSummaryActivityState())
    public val state: StateFlow<ThreadSummaryActivityState> = mutableState.asStateFlow()
    private var owner: ThreadPanelsOwner? = null
    private var epoch: Long = 0
    private var request: Long = 0
    private var job: Job? = null
    private var stopJob: Job? = null
    /** The inventory of the last successful read; the only processes the stop action may target. */
    private var inventory: List<BackgroundTerminalSummary> = emptyList()
    /** Keys whose stop outcome was applied or unknown; never re-issued within one incarnation. */
    private val stoppedKeys = mutableSetOf<BackgroundTerminalKey>()
    private var closed: Boolean = false

    public fun isBoundTo(owner: ThreadPanelsOwner?): Boolean = !closed && when {
        owner == null -> this.owner == null
        else -> this.owner?.sameOwner(owner) == true
    }

    public fun bind(owner: ThreadPanelsOwner?) {
        check(!closed) { "Summary activity controller is closed" }
        if (isBoundTo(owner)) return
        epoch++
        invalidate()
        stopJob?.cancel()
        stopJob = null
        inventory = emptyList()
        stoppedKeys.clear()
        this.owner = owner
        mutableState.value = ThreadSummaryActivityState(ownerGeneration = epoch)
    }

    /**
     * Read the conversation's children and background process inventory. Safe to call when the
     * island is (re)opened; an unbound or closed controller is a no-op.
     */
    public fun refresh(): Job? {
        if (closed) return null
        val capturedOwner = owner ?: return null
        invalidate()
        val capturedEpoch = epoch
        val capturedRequest = request
        mutableState.value = mutableState.value.copy(phase = ThreadSummaryActivityPhase.Loading)
        val started = coroutineScope.launch(start = CoroutineStart.LAZY) {
            val overview = try {
                verifyOverview(capturedOwner, repository.readOverview(capturedOwner))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                PanelLoad.Error(capturedOwner.scope, "无法读取后台子 Agent，请重试。")
            }
            val readInventory = try {
                repository.readTerminalSelection(capturedOwner, null).inventory
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                emptyList()
            }
            if (!valid(capturedOwner, capturedEpoch) || capturedRequest != request) return@launch
            inventory = readInventory
            mutableState.value = mutableState.value.copy(
                subagents = threadSummarySubagents(overview),
                processes = threadSummaryProcesses(readInventory),
                phase = ThreadSummaryActivityPhase.Read,
                ownerGeneration = capturedEpoch,
            )
        }
        job = started
        started.start()
        return started
    }

    /**
     * Cancels every background process the last read listed as running, mirroring
     * `codex.localConversation.backgroundTerminals.stop`. Rejected outcomes may be retried;
     * applied or unknown outcomes are never re-issued for the same key and incarnation. An
     * unbound, closed, empty, or already-in-flight request is a no-op.
     */
    public fun stopAllProcesses(): Job? {
        if (closed) return null
        val capturedOwner = owner ?: return null
        val previous = mutableState.value
        if (previous.stoppingProcesses) return null
        val targets = inventory
            .filter { it.lifecycle is BackgroundProcessLifecycle.Running }
            .map { it.key }
            .filter { it !in stoppedKeys }
        if (targets.isEmpty()) return null
        val capturedEpoch = epoch
        mutableState.value = previous.copy(stoppingProcesses = true, stopNotice = null)
        val started = coroutineScope.launch(start = CoroutineStart.LAZY) {
            val failures = mutableListOf<String>()
            for (key in targets) {
                if (!valid(capturedOwner, capturedEpoch)) return@launch
                val result = try {
                    repository.operateTerminal(capturedOwner, key, TerminalOperationKind.Cancel)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    TerminalOperationResult(TerminalOperationOutcome.Unknown, "操作结果未知，已禁止重复操作。")
                }
                if (result.outcome != TerminalOperationOutcome.Rejected) stoppedKeys += key
                if (result.outcome != TerminalOperationOutcome.Applied) failures += result.message
            }
            if (!valid(capturedOwner, capturedEpoch)) return@launch
            mutableState.value = mutableState.value.copy(
                stoppingProcesses = false,
                stopNotice = failures.distinct().takeIf { it.isNotEmpty() }?.joinToString("；"),
            )
            refresh()
        }
        stopJob = started
        started.start()
        return started
    }

    public fun close() {
        if (closed) return
        closed = true
        epoch++
        invalidate()
        stopJob?.cancel()
        stopJob = null
        inventory = emptyList()
        stoppedKeys.clear()
        owner = null
        mutableState.value = ThreadSummaryActivityState()
    }

    private fun verifyOverview(owner: ThreadPanelsOwner, load: PanelLoad<SubagentOverview>): PanelLoad<SubagentOverview> {
        require(load.scope == owner.scope) { "Overview scope mismatch" }
        if (load is PanelLoad.Ready) require(load.value.scope == owner.scope) { "Overview payload scope mismatch" }
        return load
    }

    private fun valid(captured: ThreadPanelsOwner, capturedEpoch: Long): Boolean =
        !closed && epoch == capturedEpoch && owner?.sameOwner(captured) == true

    private fun invalidate() {
        request++
        job?.cancel()
        job = null
    }
}
