package im.hikaru.harness.client.panels

import im.hikaru.harness.client.connection.BackgroundInputState
import im.hikaru.harness.client.connection.BackgroundProcessLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * UI-owner controller: call bind/dispatch/refresh/close on the supplied scope's UI dispatcher.
 * Terminal, overview and detail are independent reads with owner/request fences.
 * Opening views only reads existing data; it never creates an Agent, shell, or session.
 */
public class ThreadPanelsController(
    private val coroutineScope: CoroutineScope,
    private val repository: ThreadPanelsRepository = ConnectionThreadPanelsRepository(),
) {
    private val mutableState = MutableStateFlow<ThreadPanelsState?>(null)
    public val state: StateFlow<ThreadPanelsState?> = mutableState.asStateFlow()
    private var owner: ThreadPanelsOwner? = null
    private var epoch: Long = 0
    private var terminalRequest: Long = 0
    private var terminalJob: Job? = null
    // Kept across close/reopen and Loading, reset for every owner incarnation.
    private val acceptedOutputs = mutableMapOf<BackgroundTerminalKey, BackgroundTerminalOutput>()
    private val terminalOperations = mutableMapOf<BackgroundTerminalKey, TerminalControls>()
    private var overviewRequest: Long = 0
    private var detailRequest: Long = 0
    private var overviewJob: Job? = null
    private var detailJob: Job? = null
    private var closed: Boolean = false

    public fun isBoundTo(owner: ThreadPanelsOwner?): Boolean = !closed && when {
        owner == null -> this.owner == null
        else -> this.owner?.sameOwner(owner) == true
    }

    public fun bind(owner: ThreadPanelsOwner?) {
        check(!closed) { "Panel controller is closed" }
        if (isBoundTo(owner)) return
        epoch++
        invalidateTerminal()
        acceptedOutputs.clear()
        terminalOperations.clear()
        invalidateOverview()
        invalidateDetail()
        this.owner = owner
        mutableState.value = owner?.let { ThreadPanelsState(it.scope, openTabs = emptyList(), ownerGeneration = epoch) }
    }

    /** Only local view actions enter here; service responses belong to fenced reads below. */
    public fun dispatch(action: ThreadPanelAction) {
        if (closed) return
        val previous = state.value ?: return
        if (action.scope != previous.scope) return
        when (action) {
            is ThreadPanelAction.SelectTab, is ThreadPanelAction.OpenTab, is ThreadPanelAction.CloseTab,
            is ThreadPanelAction.ShowMore, is ThreadPanelAction.SelectChild, is ThreadPanelAction.SelectTerminal,
            is ThreadPanelAction.Back -> Unit
            else -> return
        }
        val next = previous.reduce(action)
        mutableState.value = next
        if (next.terminalGeneration != previous.terminalGeneration) invalidateTerminal()
        if (next.detailGeneration != previous.detailGeneration) invalidateDetail()
        if (next.overviewGeneration != previous.overviewGeneration) invalidateOverview()
        when (action) {
            is ThreadPanelAction.SelectTerminal -> if (next.terminalGeneration != previous.terminalGeneration) refreshTerminal()
            is ThreadPanelAction.SelectChild -> if (next.detailGeneration != previous.detailGeneration &&
                next.detail is PanelLoad.Loading) startDetail()
            is ThreadPanelAction.OpenTab -> if (action.tab !in previous.openTabs) when (action.tab) {
                ThreadPanelTab.BackgroundOutput -> refreshTerminal()
                ThreadPanelTab.Subagents -> refreshOverview()
            }
            else -> Unit
        }
    }

    /** Finite inventory + output read. No producer is created and no command is restarted. */
    public fun refreshTerminal(): Job? {
        if (closed) return null
        val capturedOwner = owner ?: return null
        val previous = state.value ?: return null
        if (ThreadPanelTab.BackgroundOutput !in previous.openTabs) return null
        invalidateTerminal()
        val request = terminalRequest
        val capturedEpoch = epoch
        val loading = previous.reduce(ThreadPanelAction.BeginTerminalLoad(previous.scope))
        mutableState.value = loading
        val generation = loading.terminalGeneration
        val selectedKey = loading.selectedTerminalKey
        return launchRead({ terminalJob = it }) {
            try {
                val result = repository.readTerminalSelection(capturedOwner, selectedKey)
                    .selectionSnapshot(capturedOwner.scope, selectedKey)
                currentCoroutineContext().ensureActive()
                if (valid(capturedOwner, capturedEpoch) && request == terminalRequest) {
                    publishTerminalSelection(generation, result)
                }
            } catch (cancelled: CancellationException) {
                if (valid(capturedOwner, capturedEpoch) && request == terminalRequest) {
                    publishTerminal(generation, PanelLoad.Error(capturedOwner.scope, "后台输出读取已取消，请重试。"))
                }
                throw cancelled
            } catch (_: Exception) {
                if (valid(capturedOwner, capturedEpoch) && request == terminalRequest) {
                    publishTerminal(generation, PanelLoad.Error(capturedOwner.scope, "无法读取后台进程输出，请重试。"))
                }
            }
        }
    }

    /** Issued process operations survive tab closure. Their replies remain fenced to the owner. */
    public fun operateTerminal(key: BackgroundTerminalKey, kind: TerminalOperationKind, input: String = ""): Job? {
        if (closed) return null
        val capturedOwner = owner ?: return null
        val previous = state.value ?: return null
        val output = (previous.terminal as? PanelLoad.Ready)?.value ?: return null
        if (key != output.key || key != previous.selectedTerminalKey || key.scope != capturedOwner.scope ||
            ThreadPanelTab.BackgroundOutput !in previous.openTabs) return null
        val controls = terminalOperations[key] ?: TerminalControls(key)
        if (controls.pending || controls.cancelBlocked) return null
        if (kind != TerminalOperationKind.Cancel && (controls.inputBlocked ||
            output.inputState != BackgroundInputState.Open ||
            output.lifecycle != BackgroundProcessLifecycle.Running)) return null
        if (kind == TerminalOperationKind.Cancel &&
            output.lifecycle is BackgroundProcessLifecycle.Exited) return null
        val capturedEpoch = epoch
        terminalOperations[key] = controls.copy(pending = true, lastKind = kind, lastOutcome = null, message = null)
        projectTerminalControls(key)
        val job = coroutineScope.launch(start = CoroutineStart.LAZY) {
            val result = try {
                repository.operateTerminal(capturedOwner, key, kind, input)
            } catch (cancelled: CancellationException) {
                finishTerminalOperation(capturedOwner, capturedEpoch, key, kind,
                    TerminalOperationResult(TerminalOperationOutcome.Unknown, "操作结果未知，已禁止重复操作。"))
                throw cancelled
            } catch (_: Exception) {
                TerminalOperationResult(TerminalOperationOutcome.Unknown, "操作结果未知，已禁止重复操作。")
            }
            finishTerminalOperation(capturedOwner, capturedEpoch, key, kind, result)
        }
        job.start()
        return job
    }

    private fun finishTerminalOperation(
        capturedOwner: ThreadPanelsOwner, capturedEpoch: Long, key: BackgroundTerminalKey,
        kind: TerminalOperationKind, result: TerminalOperationResult,
    ) {
        if (!valid(capturedOwner, capturedEpoch)) return
        val controls = terminalOperations[key]?.takeIf { it.pending } ?: return
        val uncertain = result.outcome == TerminalOperationOutcome.Unknown
        val applied = result.outcome == TerminalOperationOutcome.Applied
        terminalOperations[key] = controls.copy(pending = false, lastKind = kind, lastOutcome = result.outcome,
            message = result.message,
            inputBlocked = controls.inputBlocked || uncertain || (applied && kind != TerminalOperationKind.Input),
            cancelBlocked = controls.cancelBlocked || (kind == TerminalOperationKind.Cancel && (applied || uncertain)),
            inputReceipt = controls.inputReceipt + if (applied && kind == TerminalOperationKind.Input) 1 else 0)
        projectTerminalControls(key)
    }

    private fun projectTerminalControls(key: BackgroundTerminalKey) {
        val previous = state.value ?: return
        if ((previous.terminal as? PanelLoad.Ready)?.value?.key == key &&
            ThreadPanelTab.BackgroundOutput in previous.openTabs) {
            mutableState.value = previous.copy(terminalControls = terminalOperations[key])
        }
    }

    /** Safe for snapshot invalidation notifications: does not clear a valid selected detail. */
    public fun refreshOverview(): Job? {
        if (closed) return null
        val capturedOwner = owner ?: return null
        val previous = state.value ?: return null
        if (ThreadPanelTab.Subagents !in previous.openTabs) return null
        invalidateOverview()
        val request = overviewRequest
        val capturedEpoch = epoch
        val loading = previous.reduce(ThreadPanelAction.BeginOverviewRefresh(previous.scope))
        mutableState.value = loading
        if (loading.detailGeneration != previous.detailGeneration) invalidateDetail()
        val generation = loading.overviewGeneration
        return launchRead({ overviewJob = it }) {
            try {
                val result = repository.readOverview(capturedOwner).readOnlySnapshot(capturedOwner.scope)
                currentCoroutineContext().ensureActive()
                if (valid(capturedOwner, capturedEpoch) && request == overviewRequest) {
                    publishOverview(generation, result)
                }
            } catch (cancelled: CancellationException) {
                if (valid(capturedOwner, capturedEpoch) && request == overviewRequest) {
                    publishOverview(generation, PanelLoad.Error(capturedOwner.scope, "子 Agent 列表读取已取消，请重试。"))
                }
                throw cancelled
            } catch (_: Exception) {
                if (valid(capturedOwner, capturedEpoch) && request == overviewRequest) {
                    publishOverview(generation, PanelLoad.Error(capturedOwner.scope, "无法读取子 Agent 列表，请重试。"))
                }
            }
        }
    }

    public fun refreshDetail(): Job? {
        if (closed) return null
        val previous = state.value ?: return null
        if (previous.selectedSummary?.historyAvailability != ChildHistoryAvailability.Available) return null
        invalidateDetail()
        mutableState.value = previous.reduce(ThreadPanelAction.BeginDetailLoad(previous.scope))
        return startDetail()
    }

    private fun startDetail(): Job? {
        val capturedOwner = owner ?: return null
        val selected = state.value ?: return null
        val child = selected.selectedChild ?: return null
        if (selected.detail !is PanelLoad.Loading) return null
        val generation = selected.detailGeneration
        val request = detailRequest
        val capturedEpoch = epoch
        return launchRead({ detailJob = it }) {
            try {
                val result = repository.readDetail(capturedOwner, child).readOnlySnapshot(capturedOwner.scope, child)
                currentCoroutineContext().ensureActive()
                if (valid(capturedOwner, capturedEpoch) && request == detailRequest) {
                    publishDetail(child, generation, result)
                }
            } catch (cancelled: CancellationException) {
                if (valid(capturedOwner, capturedEpoch) && request == detailRequest) {
                    publishDetail(child, generation, PanelLoad.Error(capturedOwner.scope, "子会话历史读取已取消，请重试。"))
                }
                throw cancelled
            } catch (_: Exception) {
                if (valid(capturedOwner, capturedEpoch) && request == detailRequest) {
                    publishDetail(child, generation, PanelLoad.Error(capturedOwner.scope, "无法读取子会话历史，请重试。"))
                }
            }
        }
    }

    public fun close() {
        if (closed) return
        closed = true
        epoch++
        invalidateTerminal()
        acceptedOutputs.clear()
        terminalOperations.clear()
        invalidateOverview()
        invalidateDetail()
        owner = null
        mutableState.value = null
    }

    private fun publishTerminal(generation: Long, result: PanelLoad<BackgroundTerminalOutput>) {
        val previous = state.value ?: return
        if (ThreadPanelTab.BackgroundOutput !in previous.openTabs || generation != previous.terminalGeneration) return
        val incoming = (result as? PanelLoad.Ready)?.value
        val accepted = incoming?.let { acceptedOutputs[it.key] }
        val fenced = if (incoming != null && accepted != null &&
            (incoming.revision < accepted.revision || (incoming.revision == accepted.revision && incoming != accepted))) {
            PanelLoad.Error(previous.scope, "后台输出版本过期或不一致，请刷新重试。")
        } else result
        val next = previous.reduce(ThreadPanelAction.ReceiveTerminal(previous.scope, generation, fenced))
        val key = (next.terminal as? PanelLoad.Ready)?.value?.key
        mutableState.value = next.copy(terminalControls = key?.let { terminalOperations[it] })
        if (incoming != null && fenced is PanelLoad.Ready) acceptedOutputs[incoming.key] = incoming
    }

    private fun publishTerminalSelection(generation: Long, result: BackgroundTerminalSelection) {
        val previous = state.value ?: return
        if (ThreadPanelTab.BackgroundOutput !in previous.openTabs || generation != previous.terminalGeneration ||
            (previous.selectedTerminalKey != null && result.selectedKey != previous.selectedTerminalKey)) return
        mutableState.value = previous.copy(terminalInventory = result.inventory.toList(), selectedTerminalKey = result.selectedKey)
        publishTerminal(generation, result.output)
    }

    private fun publishOverview(generation: Long, result: PanelLoad<SubagentOverview>) {
        val previous = state.value ?: return
        val next = previous.reduce(ThreadPanelAction.ReceiveOverview(previous.scope, generation, result))
        mutableState.value = next
        if (next.detailGeneration != previous.detailGeneration) invalidateDetail()
    }

    private fun publishDetail(child: ChildAgentKey, generation: Long, result: PanelLoad<ChildAgentDetail>) {
        mutableState.value = state.value?.reduce(ThreadPanelAction.ReceiveDetail(child, generation, result))
    }

    private fun valid(captured: ThreadPanelsOwner, capturedEpoch: Long): Boolean =
        !closed && epoch == capturedEpoch && owner?.sameOwner(captured) == true

    private fun invalidateTerminal() {
        terminalRequest++
        terminalJob?.cancel()
        terminalJob = null
    }

    private fun invalidateOverview() {
        overviewRequest++
        overviewJob?.cancel()
        overviewJob = null
    }

    private fun invalidateDetail() {
        detailRequest++
        detailJob?.cancel()
        detailJob = null
    }

    private fun launchRead(remember: (Job) -> Unit, read: suspend () -> Unit): Job {
        val job = coroutineScope.launch(start = CoroutineStart.LAZY) { read() }
        remember(job)
        job.start()
        return job
    }
}
