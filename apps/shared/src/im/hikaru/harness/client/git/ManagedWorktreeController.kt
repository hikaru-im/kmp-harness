package im.hikaru.harness.client.git

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Read invalidation and binding lifetimes are separate from accepted pin operations. */
public class ManagedWorktreeController(scope: CoroutineScope, private val repository: ManagedWorktreeRepository) : AutoCloseable {
    private val owner = SupervisorJob(scope.coroutineContext[Job])
    private val tasks = CoroutineScope(scope.coroutineContext + owner)
    private val mutableState = MutableStateFlow(ManagedWorktreeState())
    public val state: StateFlow<ManagedWorktreeState> = mutableState.asStateFlow()
    private var epoch = 0L
    private var bindingEpoch = 0L
    private var operationTicket = 0L
    private var readJob: Job? = null
    private var closed = false

    init { tasks.launch { repository.revision.drop(1).collect { if (!closed) refresh() } } }

    public fun bind(repository: GitRepository?) {
        check(!closed)
        if (state.value.repository == repository) return
        epoch++
        bindingEpoch++
        readJob?.cancel()
        mutableState.value = ManagedWorktreeState(repository = repository)
        if (repository != null) refresh()
    }

    public fun refresh() {
        check(!closed)
        val repo = state.value.repository ?: return
        val ticket = ++epoch
        readJob?.cancel()
        if (repo.binding.access != GitAccess.LocalReadOnly) {
            mutableState.value = ManagedWorktreeState(repo, ManagedWorktreePhase.Unavailable, message = "此 Host 的 managed 服务不可用。")
            return
        }
        val previous = state.value.inventory
        mutableState.value = state.value.copy(phase = ManagedWorktreePhase.Loading, message = null, stale = previous != null)
        val job = tasks.launch(start = CoroutineStart.LAZY) {
            val result = try { repository.read(repo) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { ManagedWorktreeRead.Failed("无法读取 managed 登记；保护状态仍未知。") }
            if (closed || ticket != epoch || state.value.repository != repo) return@launch
            val operation = state.value
            val readState = when (result) {
                is ManagedWorktreeRead.Ready -> {
                    val inventory = result.inventory
                    if (inventory.repository != repo || inventory.owner.hostId != repo.binding.hostId ||
                        inventory.owner.identity != repo.binding.identity)
                        ManagedWorktreeState(repo, ManagedWorktreePhase.Error, previous, "读取结果的归属不匹配。", previous != null)
                    else ManagedWorktreeState(repo, ManagedWorktreePhase.Ready, inventory)
                }
                is ManagedWorktreeRead.Failed -> ManagedWorktreeState(repo,
                    if (result.unavailable) ManagedWorktreePhase.Unavailable else ManagedWorktreePhase.Error,
                    previous, result.message, previous != null)
            }
            val resultOwner = readState.inventory?.owner
            mutableState.value = readState.copy(
                pendingPin = operation.pendingPin?.takeIf { it.repository == repo && it.owner == resultOwner },
                pinReceipt = operation.pinReceipt?.takeIf { receipt ->
                    receipt.request.repository == repo && receipt.request.owner == resultOwner &&
                        readState.inventory?.entries?.singleOrNull { it.root == receipt.request.root }
                            ?.sessionObservation?.let { observation ->
                                observation.owner == resultOwner && observation.status == ManagedSessionObservationStatus.Observed &&
                                    observation.issues.isEmpty() && observation.sessions.any { it.pinKey == receipt.request.key }
                            } == true
                },
                pendingArchive = operation.pendingArchive?.takeIf { it.repository == repo && it.owner == resultOwner },
                archiveReceipt = operation.archiveReceipt?.takeIf { receipt ->
                    receipt.request.repository == repo && receipt.request.owner == resultOwner &&
                        readState.inventory?.entries?.singleOrNull { it.root == receipt.request.root }
                            ?.sessionObservation?.let { observation ->
                                observation.owner == resultOwner && observation.status == ManagedSessionObservationStatus.Observed &&
                                    observation.issues.isEmpty() && observation.sessions.any {
                                        it.id == receipt.request.key.id &&
                                            it.pinKey?.headerFingerprint == receipt.request.key.headerFingerprint
                                    }
                            } == true
                },
                // A removed worktree leaves the inventory, so its receipt is kept by repository and
                // owner only; the fresh list is the evidence that it is gone.
                pendingRemoval = operation.pendingRemoval?.takeIf {
                    it.plan.repository == repo && it.plan.owner == resultOwner
                },
                removalReceipt = operation.removalReceipt?.takeIf {
                    it.request.plan.repository == repo && it.request.plan.owner == resultOwner
                },
            )
        }
        readJob = job
        job.start()
    }

    public fun setPinned(root: String, key: PinKey, desiredPinned: Boolean, pageReady: Boolean = true) {
        if (closed || !pageReady || !state.value.canPin(root, key)) return
        val inventory = checkNotNull(state.value.inventory)
        val pins = (inventory.pinRead as ManagedWorktreePinRead.Available).snapshot
        val request = ManagedWorktreePinRequest(inventory.repository, inventory.owner, root, key, pins.revision, desiredPinned)
        val bindingTicket = bindingEpoch
        val ticket = ++operationTicket
        mutableState.value = state.value.copy(pendingPin = request, pinReceipt = null)
        // This finite operation is independent of page navigation/close. The repository keeps only
        // its commit section NonCancellable and reports the actual durable result.
        CoroutineScope(tasks.coroutineContext.minusKey(Job)).launch {
            val receipt = try { repository.setPinned(request) }
            catch (_: Exception) { ManagedWorktreePinReceipt.Unknown(request, "固定结果未知；请明确刷新，不会自动重投。") }
            if (closed || bindingTicket != bindingEpoch || ticket != operationTicket ||
                state.value.repository != request.repository || state.value.inventory?.owner != request.owner ||
                state.value.pendingPin != request) return@launch
            val observed = state.value.inventory?.entries?.singleOrNull { it.root == root }?.sessionObservation
            val matching = receipt.request == request && observed != null && observed.owner == request.owner &&
                observed.status == ManagedSessionObservationStatus.Observed && observed.issues.isEmpty() &&
                observed.sessions.any { it.id == key.id && it.pinKey == key } &&
                (receipt !is ManagedWorktreePinReceipt.Applied ||
                    (receipt.snapshot.owner == request.owner &&
                        (receipt.snapshot.revision == request.expectedRevision ||
                            (request.expectedRevision < Long.MAX_VALUE && receipt.snapshot.revision == request.expectedRevision + 1)) &&
                        (key in receipt.snapshot.pins) == desiredPinned &&
                        (receipt.snapshot.revision > request.expectedRevision || (key in pins.pins) == desiredPinned)))
            mutableState.value = state.value.copy(pendingPin = null, pinReceipt = if (matching) receipt else
                ManagedWorktreePinReceipt.Unknown(request, "固定回执归属或会话身份不匹配；请刷新核验。"))
            // Never flip a local pin bit. A fresh read supplies the displayed disk state.
            if (matching && receipt is ManagedWorktreePinReceipt.Applied) refresh()
        }
    }

    public fun setArchived(root: String, key: ChatArchiveKey, desiredArchived: Boolean, pageReady: Boolean = true) {
        if (closed || !pageReady || !state.value.canArchive(root, key)) return
        val inventory = checkNotNull(state.value.inventory)
        val archive = (inventory.archiveRead as ManagedWorktreeChatArchiveRead.Available).snapshot
        val request = ManagedWorktreeChatArchiveRequest(inventory.repository, inventory.owner, root, key, archive.revision, desiredArchived)
        val bindingTicket = bindingEpoch
        val ticket = ++operationTicket
        mutableState.value = state.value.copy(pendingArchive = request, archiveReceipt = null)
        // This finite operation is independent of page navigation/close. The repository keeps only
        // its commit section NonCancellable and reports the actual durable result.
        CoroutineScope(tasks.coroutineContext.minusKey(Job)).launch {
            val receipt = try { repository.setArchived(request) }
            catch (_: Exception) { ManagedWorktreeChatArchiveReceipt.Unknown(request, "归档结果未知；请明确刷新，不会自动重投。") }
            if (closed || bindingTicket != bindingEpoch || ticket != operationTicket ||
                state.value.repository != request.repository || state.value.inventory?.owner != request.owner ||
                state.value.pendingArchive != request) return@launch
            val observed = state.value.inventory?.entries?.singleOrNull { it.root == root }?.sessionObservation
            val matching = receipt.request == request && observed != null && observed.owner == request.owner &&
                observed.status == ManagedSessionObservationStatus.Observed && observed.issues.isEmpty() &&
                observed.sessions.any { it.id == key.id && it.pinKey?.headerFingerprint == key.headerFingerprint } &&
                (receipt !is ManagedWorktreeChatArchiveReceipt.Applied ||
                    (receipt.snapshot.owner == request.owner &&
                        (receipt.snapshot.revision == request.expectedRevision ||
                            (request.expectedRevision < Long.MAX_VALUE && receipt.snapshot.revision == request.expectedRevision + 1)) &&
                        (key in receipt.snapshot.archived) == desiredArchived &&
                        (receipt.snapshot.revision > request.expectedRevision || (key in archive.archived) == desiredArchived)))
            mutableState.value = state.value.copy(pendingArchive = null, archiveReceipt = if (matching) receipt else
                ManagedWorktreeChatArchiveReceipt.Unknown(request, "归档回执归属或会话身份不匹配；请刷新核验。"))
            // Never flip a local archive bit. A fresh read supplies the displayed disk state.
            if (matching && receipt is ManagedWorktreeChatArchiveReceipt.Applied) refresh()
        }
    }

    /**
     * Dispatch a removal that the page received as an eligible plan. The repository re-verifies the
     * whole plan before touching Git, so a stale page cannot delete a worktree that gained sessions,
     * pins, archives or content since the read. This finite operation is independent of navigation.
     */
    public fun remove(plan: ManagedWorktreeRemovalPlan, pageReady: Boolean = true) {
        if (closed || !pageReady || !state.value.canRemove(plan.root)) return
        val inventory = checkNotNull(state.value.inventory)
        if (plan.repository != inventory.repository || plan.owner != inventory.owner) return
        val request = ManagedWorktreeRemovalRequest(plan)
        val bindingTicket = bindingEpoch
        val ticket = ++operationTicket
        mutableState.value = state.value.copy(pendingRemoval = request, removalReceipt = null)
        CoroutineScope(tasks.coroutineContext.minusKey(Job)).launch {
            val raw = try { repository.removeWorktree(request) }
            catch (_: Exception) { ManagedWorktreeRemovalReceipt.UnknownOutcome(request, "删除结果未知；请明确刷新，不会自动重投。") }
            if (closed || bindingTicket != bindingEpoch || ticket != operationTicket ||
                state.value.repository != request.plan.repository || state.value.inventory?.owner != request.plan.owner ||
                state.value.pendingRemoval != request) return@launch
            val receipt = if (raw.request == request) raw
                else ManagedWorktreeRemovalReceipt.UnknownOutcome(request, "删除回执与请求不匹配；请刷新核验。")
            mutableState.value = state.value.copy(pendingRemoval = null, removalReceipt = receipt)
            // Only a fresh read republishes the inventory; nothing here marks the path as gone.
            if (receipt is ManagedWorktreeRemovalReceipt.Removed) refresh()
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        epoch++
        bindingEpoch++
        owner.cancel()
        mutableState.value = ManagedWorktreeState()
    }
}
