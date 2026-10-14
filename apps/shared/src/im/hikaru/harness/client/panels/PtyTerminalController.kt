package im.hikaru.harness.client.panels

import im.hikaru.harness.client.connection.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** UI calls and publications run on the supplied scope's dispatcher (Compose's UI dispatcher). */
public class PtyTerminalController(
    parentScope: CoroutineScope,
    private val launchProvider: TerminalLaunchProvider = UnavailableTerminalLaunchProvider,
    private val pollMillis: Long = 200,
) {
    private val job = SupervisorJob(parentScope.coroutineContext[Job])
    private val scope = CoroutineScope(parentScope.coroutineContext + job)
    private val mutable = MutableStateFlow(PtyTerminalState())
    public val state: StateFlow<PtyTerminalState> = mutable.asStateFlow()
    private var owner: ThreadPanelsOwner? = null
    private var epoch = 0L
    private var request = 0L
    private var readRequest = 0L
    private var observation: Job? = null
    private var refreshJob: Job? = null
    private val readGate = Mutex()
    private val highWater = mutableMapOf<TerminalKey, TerminalSnapshot>()
    private var terminalOwner: TerminalOwner? = null
    private val attemptedSizes = mutableMapOf<TerminalKey, TerminalSize>()
    private val queue = ArrayDeque<Event>()
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private var queuedBytes = 0
    private var inFlight: Event? = null
    private var closed = false

    private class Event(
        val owner: ThreadPanelsOwner, val epoch: Long, val request: Long, val operation: PtyOperation,
        val key: TerminalKey? = null, val input: TerminalInput? = null, val size: TerminalSize? = null,
        val create: TerminalCreateRequest? = null, val bytes: Int = 0,
    )

    init {
        require(pollMillis > 0)
        scope.launch {
            for (signal in wake) {
                while (queue.isNotEmpty()) {
                    val event = queue.removeFirst()
                    queuedBytes -= event.bytes
                    if (!validEvent(event)) {
                        receipt(event, PtyOutcome.Unsent, "终端身份已改变，操作未发送。")
                        continue
                    }
                    if (event.key in mutable.value.fencedKeys && event.operation != PtyOperation.Close) {
                        receipt(event, PtyOutcome.Unsent, "结果未知后的操作未发送。")
                        continue
                    }
                    inFlight = event
                    try { execute(event) } finally { inFlight = null }
                }
            }
        }
    }

    public fun isBoundTo(expected: ThreadPanelsOwner?): Boolean = !closed && when {
        expected == null -> owner == null
        else -> owner?.sameOwner(expected) == true
    }

    public fun bind(next: ThreadPanelsOwner?) {
        if (closed || isBoundTo(next)) return
        epoch++
        stopReads()
        queue.clear(); queuedBytes = 0
        highWater.clear()
        terminalOwner = null; attemptedSizes.clear()
        owner = next
        mutable.value = PtyTerminalState(bindingEpoch = epoch,
            launchUnavailableReason = next?.let(launchProvider::availability))
        if (next != null) refresh()
    }

    private fun valid(expected: ThreadPanelsOwner, expectedEpoch: Long): Boolean =
        !closed && epoch == expectedEpoch && owner?.sameOwner(expected) == true

    private fun validEvent(event: Event): Boolean = valid(event.owner, event.epoch) &&
        (event.key?.owner ?: event.create?.owner) == terminalOwner

    public fun setVisible(visible: Boolean) {
        if (closed || mutable.value.visible == visible) return
        mutable.value = mutable.value.copy(visible = visible, selectionEpoch = mutable.value.selectionEpoch + 1)
        observation?.cancel(); observation = null
        if (!visible) discardQueued(null, "视图已关闭，操作未发送。")
        else if (mutable.value.discovery !is PtyLoad.Ready) refresh() else startObservation()
    }

    /** Discovery and inventory refresh never clears mutation uncertainty. */
    public fun refresh() {
        val bound = owner ?: return
        if (closed) return
        val capturedEpoch = epoch
        val ticket = ++readRequest
        refreshJob?.cancel()
        observation?.cancel(); observation = null
        mutable.value = mutable.value.copy(discovery = PtyLoad.Loading, inventory = PtyLoad.Loading)
        refreshJob = scope.launch {
            readGate.withLock {
                if (!valid(bound, capturedEpoch) || ticket != readRequest) return@withLock
                try {
                    val discovery = bound.connection.session.terminalCapability(bound.parentSessionId)
                    if (!valid(bound, capturedEpoch) || ticket != readRequest) return@withLock
                    require(discovery.session == bound.parentSessionId)
                    if (discovery !is TerminalDiscoveryResult.Available) {
                        val load = when (discovery) {
                            is TerminalDiscoveryResult.Rejected -> PtyLoad.Error(discovery.reason)
                            is TerminalDiscoveryResult.Unavailable -> PtyLoad.Unavailable(discovery.reason)
                            else -> error("Invalid discovery")
                        }
                        mutable.value = mutable.value.copy(discovery = load, inventory = load)
                        return@withLock
                    }
                    require(discovery.owner.session == bound.parentSessionId)
                    if (terminalOwner != null && terminalOwner != discovery.owner) {
                        discardQueued(null, "终端 owner incarnation 已改变，操作未发送。")
                        mutable.value = mutable.value.copy(selectedKey = null, screen = PtyLoad.NotLoaded,
                            retainedSnapshot = null, selectionEpoch = mutable.value.selectionEpoch + 1,
                            createPending = false, createUncertain = mutable.value.createUncertain ||
                                inFlight?.operation == PtyOperation.Create)
                    }
                    terminalOwner = discovery.owner.copy()
                    mutable.value = mutable.value.copy(discovery = PtyLoad.Ready(discovery.copy(owner = discovery.owner.copy())))
                    val inventory = bound.connection.session.terminals(discovery.owner)
                    if (!valid(bound, capturedEpoch) || ticket != readRequest) return@withLock
                    require(inventory.owner == discovery.owner)
                    val load: PtyLoad<List<TerminalMetadata>> = when (inventory) {
                        is TerminalListResult.Available -> {
                            require(inventory.terminals.all { it.key.owner == discovery.owner })
                            if (inventory.terminals.isEmpty()) PtyLoad.Empty else PtyLoad.Ready(inventory.terminals.map { it.detached() })
                        }
                        is TerminalListResult.Rejected -> PtyLoad.Error(inventory.reason)
                        is TerminalListResult.Unavailable -> PtyLoad.Unavailable(inventory.reason)
                    }
                    val oldKey = mutable.value.selectedKey
                    mutable.value = mutable.value.copy(inventory = load)
                    if (oldKey != null && inventory is TerminalListResult.Available && inventory.terminals.none { it.key == oldKey }) {
                        observation?.cancel()
                        mutable.value = mutable.value.copy(screen = PtyLoad.Error("所选终端不在当前 inventory；请选择真实终端。"))
                    } else if (mutable.value.visible) startObservation()
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) {
                    if (valid(bound, capturedEpoch) && ticket == readRequest) mutable.value = mutable.value.copy(
                        discovery = PtyLoad.Error("无法确认当前会话的终端能力，请刷新。"),
                        inventory = PtyLoad.Error("无法读取终端列表，请刷新。"))
                }
            }
        }
    }

    public fun select(key: TerminalKey) {
        val rows = (mutable.value.inventory as? PtyLoad.Ready)?.value ?: return
        if (closed || rows.none { it.key == key } || mutable.value.selectedKey == key) return
        observation?.cancel()
        discardQueued(null, "已切换终端，操作未发送。")
        mutable.value = mutable.value.copy(selectedKey = key.copy(owner = key.owner.copy()),
            selectionEpoch = mutable.value.selectionEpoch + 1, screen = PtyLoad.NotLoaded, retainedSnapshot = highWater[key])
        startObservation()
    }

    public fun createTerminal(): Boolean {
        val bound = owner ?: return false
        val discovery = (mutable.value.discovery as? PtyLoad.Ready)?.value ?: return false
        if (!mutable.value.visible || !mutable.value.canCreate || !valid(bound, epoch)) return false
        if (discovery.session != bound.parentSessionId || discovery.owner.session != bound.parentSessionId) return false
        val preparation = try { launchProvider.prepare(bound, discovery) } catch (_: Exception) {
            TerminalLaunchPreparation.Unavailable("无法准备本机 shell。")
        }
        if (preparation is TerminalLaunchPreparation.Unavailable) {
            mutable.value = mutable.value.copy(launchUnavailableReason = preparation.reason)
            return false
        }
        val plan = (preparation as TerminalLaunchPreparation.Ready).plan
        if (plan.owner != discovery.owner || plan.cwd != discovery.canonicalCreationCwd) return false
        val event = Event(bound, epoch, ++request, PtyOperation.Create, create = plan.request(mutable.value.viewport))
        mutable.value = mutable.value.copy(createPending = true)
        return enqueue(event).also { if (!it) mutable.value = mutable.value.copy(createPending = false) }
    }

    public fun input(input: TerminalInput): Boolean {
        val bound = owner ?: return false
        val key = mutable.value.selectedKey ?: return false
        if (!mutable.value.canInput) return false
        val frozen = when (input) {
            is TerminalInput.Text -> TerminalInput.Text(input.text)
            is TerminalInput.Paste -> TerminalInput.Paste(input.text)
            is TerminalInput.KeyPress -> TerminalInput.KeyPress(input.key, input.modifiers.toSet(), input.character)
        }
        val bytes = when (frozen) {
            is TerminalInput.Text -> frozen.text.encodeToByteArray().size
            is TerminalInput.Paste -> frozen.text.encodeToByteArray().size
            is TerminalInput.KeyPress -> 0
        }
        return enqueue(Event(bound, epoch, ++request, PtyOperation.Input, key, frozen, bytes = bytes))
    }

    public fun viewport(size: TerminalSize): Boolean {
        mutable.value = mutable.value.copy(viewport = size.copy())
        return resizeSelected()
    }

    private fun resizeSelected(): Boolean {
        val bound = owner ?: return false
        val state = mutable.value
        val key = state.selectedKey ?: return false
        if (!state.canInput) return false
        val latestSize = queue.lastOrNull { it.key == key && it.operation == PtyOperation.Resize }?.size
            ?: inFlight?.takeIf { it.key == key && it.operation == PtyOperation.Resize }?.size
            ?: attemptedSizes[key]
            ?: state.confirmedSizes[key] ?: state.snapshot?.size
        if (latestSize == state.viewport) return false
        val enqueued = enqueue(Event(bound, epoch, ++request, PtyOperation.Resize, key, size = state.viewport.copy()))
        return enqueued
    }

    public fun closeTerminal(): Boolean {
        val bound = owner ?: return false
        val key = mutable.value.selectedKey ?: return false
        if (!mutable.value.canClose) return false
        val event = Event(bound, epoch, ++request, PtyOperation.Close, key)
        mutable.value = mutable.value.copy(closeAttemptedKeys = mutable.value.closeAttemptedKeys + key)
        if (!enqueue(event)) {
            mutable.value = mutable.value.copy(closeAttemptedKeys = mutable.value.closeAttemptedKeys - key)
            return false
        }
        return true
    }

    private fun enqueue(event: Event): Boolean {
        if (closed || queue.size >= 32 || event.bytes > 65_536 - queuedBytes) {
            receipt(event, PtyOutcome.Rejected, "未发送：待派发队列最多 32 项、文字最多 64 KiB。")
            return false
        }
        queue.addLast(event); queuedBytes += event.bytes
        receipt(event, PtyOutcome.Pending, "等待派发。")
        wake.trySend(Unit)
        return true
    }

    private fun receipt(event: Event, outcome: PtyOutcome, message: String) {
        if (!valid(event.owner, event.epoch)) return
        val receipt = PtyReceipt(event.request, event.key, event.operation, outcome, message)
        mutable.value = mutable.value.copy(receipts = (mutable.value.receipts.filter { it.request != event.request } + receipt).takeLast(128))
    }

    private fun discardQueued(key: TerminalKey?, message: String) {
        val discarded = queue.filter { key == null || it.key == key }
        queue.removeAll(discarded.toSet())
        queuedBytes -= discarded.sumOf { it.bytes }
        discarded.forEach {
            receipt(it, PtyOutcome.Unsent, message)
            if (it.operation == PtyOperation.Create) mutable.value = mutable.value.copy(createPending = false)
            if (it.operation == PtyOperation.Close) mutable.value = mutable.value.copy(closeAttemptedKeys = mutable.value.closeAttemptedKeys - it.key!!)
        }
    }

    private suspend fun execute(event: Event) {
        if (event.operation == PtyOperation.Create) {
            val created = try { event.owner.connection.session.createTerminal(event.create!!) }
            catch (_: Exception) { TerminalCreateResult.UnknownOutcome(event.create!!.owner, null, "创建已派发，回执未知；请刷新 inventory。") }
            if (!validEvent(event)) return
            mutable.value = mutable.value.copy(createPending = false)
            if (created.owner != event.create!!.owner) {
                mutable.value = mutable.value.copy(createUncertain = true)
                receipt(event, PtyOutcome.Unknown, "创建回执身份不匹配；未确认创建，请刷新列表。")
                return
            }
            when (created) {
                is TerminalCreateResult.Created -> {
                    val metadata = created.metadata.detached()
                    require(metadata.key.owner == created.owner)
                    val rows = (mutable.value.inventory as? PtyLoad.Ready)?.value.orEmpty()
                    mutable.value = mutable.value.copy(inventory = PtyLoad.Ready(rows.filter { it.key != metadata.key } + metadata),
                        selectedKey = metadata.key, selectionEpoch = mutable.value.selectionEpoch + 1,
                        screen = PtyLoad.NotLoaded, retainedSnapshot = null)
                    receipt(event, PtyOutcome.Applied, "终端已创建。")
                    startObservation()
                }
                is TerminalCreateResult.Rejected -> receipt(event, PtyOutcome.Rejected, created.reason)
                is TerminalCreateResult.Unavailable -> receipt(event, PtyOutcome.Unavailable, created.reason)
                is TerminalCreateResult.UnknownOutcome -> {
                    mutable.value = mutable.value.copy(createUncertain = true,
                        fencedKeys = mutable.value.fencedKeys + listOfNotNull(created.key))
                    receipt(event, PtyOutcome.Unknown, "创建回执未知，禁止重试；刷新列表后选择已有终端。")
                }
            }
            return
        }
        val key = event.key!!
        if (event.operation == PtyOperation.Resize) attemptedSizes[key] = event.size!!
        val result = try {
            when (event.operation) {
                PtyOperation.Input -> event.owner.connection.session.inputTerminal(key, event.input!!)
                PtyOperation.Resize -> event.owner.connection.session.resizeTerminal(key, event.size!!)
                PtyOperation.Close -> event.owner.connection.session.closeTerminal(key)
                PtyOperation.Create -> error("Create handled above")
            }
        } catch (_: Exception) { TerminalMutationResult.UnknownOutcome(key, "操作已派发，回执未知。") }
        if (!validEvent(event)) return
        val verified = if (result.key == key) result else TerminalMutationResult.UnknownOutcome(key, "回执身份不匹配，结果未知。")
        when (verified) {
            is TerminalMutationResult.Applied -> {
                if (event.operation == PtyOperation.Resize && verified.size != event.size) {
                    fence(event, "尺寸回执不匹配；resize 未确认。")
                    return
                }
                if (event.operation == PtyOperation.Resize) mutable.value = mutable.value.copy(
                    confirmedSizes = mutable.value.confirmedSizes + (key to event.size!!))
                receipt(event, PtyOutcome.Applied, "操作已确认。")
                if (event.operation == PtyOperation.Close) {
                    discardQueued(key, "终端已关闭，操作未发送。")
                    if (mutable.value.selectedKey == key) {
                        observation?.cancel(); observation = null
                        mutable.value = mutable.value.copy(screen = PtyLoad.Error("终端已显式关闭。"))
                    }
                    refresh()
                }
            }
            is TerminalMutationResult.UnknownOutcome -> fence(event, verified.reason)
            is TerminalMutationResult.Rejected -> {
                receipt(event, PtyOutcome.Rejected, verified.reason)
                if (event.operation == PtyOperation.Close) mutable.value = mutable.value.copy(closeAttemptedKeys = mutable.value.closeAttemptedKeys - key)
            }
            is TerminalMutationResult.Unavailable -> {
                receipt(event, PtyOutcome.Unavailable, verified.reason)
                if (event.operation == PtyOperation.Close) mutable.value = mutable.value.copy(closeAttemptedKeys = mutable.value.closeAttemptedKeys - key)
            }
            is TerminalMutationResult.NotFound -> receipt(event, PtyOutcome.Rejected, "终端已失效，请刷新真实列表。")
        }
    }

    private fun fence(event: Event, reason: String) {
        mutable.value = mutable.value.copy(fencedKeys = mutable.value.fencedKeys + event.key!!)
        receipt(event, PtyOutcome.Unknown, reason + " 当前页面停止该终端的输入与 resize；这不证明 native 已停止接收。")
        discardQueued(event.key, "前序结果未知，此操作未发送。")
    }

    private fun startObservation() {
        observation?.cancel(); observation = null
        val bound = owner ?: return
        val state = mutable.value
        val key = state.selectedKey ?: return
        val discovery = (state.discovery as? PtyLoad.Ready)?.value ?: return
        if (!state.visible || key.owner != discovery.owner || key in state.closeAttemptedKeys) return
        val capturedEpoch = epoch
        val selection = state.selectionEpoch
        mutable.value = mutable.value.copy(screen = PtyLoad.Loading)
        observation = scope.launch {
            var sawExit = false
            while (isActive && valid(bound, capturedEpoch) && mutable.value.visible && mutable.value.selectedKey == key &&
                mutable.value.selectionEpoch == selection) {
                var keepPolling = false
                readGate.withLock {
                    if (!valid(bound, capturedEpoch) || !mutable.value.visible || mutable.value.selectedKey != key ||
                        mutable.value.selectionEpoch != selection) return@withLock
                    try {
                        val result = bound.connection.session.readTerminal(key)
                        if (!isActive || !valid(bound, capturedEpoch) || !mutable.value.visible || mutable.value.selectedKey != key ||
                            mutable.value.selectionEpoch != selection) return@withLock
                        require(result.key == key)
                        when (result) {
                            is TerminalReadResult.Available -> {
                                require(result.snapshot.metadata.key == key)
                                val snapshot = result.snapshot.detached()
                                val previous = highWater[key]
                                when {
                                    previous != null && snapshot.revision < previous.revision -> {
                                        mutable.value = mutable.value.copy(screen = PtyLoad.Ready(previous), retainedSnapshot = previous)
                                        keepPolling = previous.metadata.lifecycle !is TerminalLifecycle.Exited
                                    }
                                    previous != null && snapshot.revision == previous.revision && snapshot != previous ->
                                        mutable.value = mutable.value.copy(screen = PtyLoad.Error("同 revision 的快照内容冲突，请刷新。"), retainedSnapshot = previous)
                                    else -> {
                                        highWater[key] = snapshot
                                        mutable.value = mutable.value.copy(screen = PtyLoad.Ready(snapshot), retainedSnapshot = snapshot,
                                            fencedKeys = if (snapshot.metadata.input == TerminalInputState.Unknown) mutable.value.fencedKeys + key else mutable.value.fencedKeys)
                                        if (snapshot.metadata.lifecycle is TerminalLifecycle.Exited) {
                                            keepPolling = !sawExit; sawExit = true
                                        } else { keepPolling = true; resizeSelected() }
                                    }
                                }
                            }
                            is TerminalReadResult.NotFound -> mutable.value = mutable.value.copy(screen = PtyLoad.Error("所选终端已失效，请刷新列表。"))
                            is TerminalReadResult.Rejected -> mutable.value = mutable.value.copy(screen = PtyLoad.Error(result.reason))
                            is TerminalReadResult.Unavailable -> mutable.value = mutable.value.copy(screen = PtyLoad.Unavailable(result.reason))
                        }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) {
                        if (valid(bound, capturedEpoch) && mutable.value.selectedKey == key && mutable.value.selectionEpoch == selection)
                            mutable.value = mutable.value.copy(screen = PtyLoad.Error("无法读取终端快照，请显式刷新。"))
                    }
                }
                if (!keepPolling) break
                delay(pollMillis)
            }
        }
    }

    private fun stopReads() {
        readRequest++
        refreshJob?.cancel(); refreshJob = null
        observation?.cancel(); observation = null
    }

    public fun close() {
        if (closed) return
        closed = true; epoch++
        stopReads(); queue.clear(); queuedBytes = 0
        owner = null; wake.close(); job.cancel()
    }
}
