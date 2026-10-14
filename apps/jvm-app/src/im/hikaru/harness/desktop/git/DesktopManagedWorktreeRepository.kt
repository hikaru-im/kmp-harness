package im.hikaru.harness.desktop.git

import im.hikaru.harness.agent.AgentArchiveBegin
import im.hikaru.harness.agent.AgentArchivePreparation
import im.hikaru.harness.client.git.*
import im.hikaru.harness.runtime.effect.Disposable
import im.hikaru.harness.session.SessionArchiveToken
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/** Registration is independent of the UI scope. Reads never adopt or repair intents. */
public class DesktopManagedWorktreeRepository(
    private val backend: GitBackend,
    private val store: DesktopManagedWorktreeStore,
    private val ownerValidator: (suspend (GitBinding) -> ManagedWorktreeOwner?)? = null,
    private val sessionObserver: DesktopWorktreeSessionObserver? = null,
    private val pinStore: DesktopWorktreePinStore? = null,
    private val archiveAgents: (suspend (String) -> AgentArchiveBegin)? = null,
    private val restoreChat: (suspend (String) -> ManagedWorktreeChatRestore)? = null,
    private val archiveStore: DesktopWorktreeChatArchiveStore? = null,
) : ManagedWorktreeRepository {
    /** Preserve existing trailing-lambda calls as owner validators. */
    public constructor(backend: GitBackend, store: DesktopManagedWorktreeStore,
        ownerValidator: suspend (GitBinding) -> ManagedWorktreeOwner?) : this(backend, store, ownerValidator, null)
    private val mutableRevision = MutableStateFlow(0L)
    override val revision: StateFlow<Long> = mutableRevision.asStateFlow()
    private data class WarningKey(val owner: ManagedWorktreeOwner, val operationId: String)
    private val warnings = ConcurrentHashMap<WarningKey, String>()

    internal fun canRegister(binding: GitBinding): Boolean =
        ownerValidator != null && binding.access == GitAccess.LocalReadOnly

    internal data class Prepared(
        val repository: GitRepository,
        val request: GitCreateWorktreeRequest,
        val owner: ManagedWorktreeOwner,
        val intent: ManagedWorktreeIntent,
        val observation: DesktopManagedWorktreeStore.Observation,
    )

    internal suspend fun prepare(repository: GitRepository, request: GitCreateWorktreeRequest): Prepared {
        require(request.binding == repository.binding) { "创建请求与仓库 scope 不一致。" }
        val capturedOwner = verifiedOwner(repository.binding)
            ?: throw ManagedWorktreeStoreException("本机 owner 验证不可用；不会创建工作树。")
        verifyRepository(repository)
        val destination = Path.of(request.path)
        require(destination.isAbsolute && destination.normalize() == destination && destination.fileName != null)
        require(!Files.exists(destination, NOFOLLOW_LINKS))
        val root = destination.parent.toRealPath().resolve(destination.fileName).toString()
        val intent = ManagedWorktreeIntent(
            UUID.randomUUID().toString(), canonical(repository.commonDir), canonical(repository.root),
            canonical(repository.gitDir), root, request.branch, request.startRef, System.currentTimeMillis(),
        )
        val prepared = withContext(Dispatchers.IO) {
            val observed = store.read(capturedOwner)
            store.compareAndSet(observed, observed.snapshot.copy(
                revision = observed.snapshot.revision + 1, intents = observed.snapshot.intents + intent,
            ))
        }
        rememberWarning(capturedOwner, intent, prepared)
        changed()
        return Prepared(repository, request, capturedOwner, intent, prepared)
    }

    internal suspend fun beforeDispatch(prepared: Prepared) {
        require(prepared.observation.durabilityWarning == null) {
            "Prepared 已提交但持久性未确认；保留意图，不调用 Git 创建。"
        }
        require(verifiedOwner(prepared.repository.binding) == prepared.owner) { "创建前 owner 已变化。" }
        verifyRepository(prepared.repository)
        withContext(Dispatchers.IO) { store.requireCurrent(prepared.observation) }
        currentCoroutineContext().ensureActive()
    }

    /** A real Created remains Created even if any registration step fails or the caller cancels. */
    internal suspend fun register(prepared: Prepared, receipt: GitCreateWorktreeReceipt) = withContext(NonCancellable) {
        val warningKey = WarningKey(prepared.owner, prepared.intent.operationId)
        try {
            require(receipt.repository == prepared.repository && receipt.request == prepared.request)
            val tree = receipt.worktree
            require(tree in receipt.worktrees && !tree.bare && !tree.detached && !tree.prunable)
            require(tree.path == prepared.intent.root && tree.branch == "refs/heads/" + prepared.request.branch &&
                tree.headOid == receipt.startOid && receipt.startOid.matches(Regex("(?:[0-9a-fA-F]{40}|[0-9a-fA-F]{64})")))
            val target = backend.discover(prepared.repository.binding, tree.path)
            require(target.binding == prepared.repository.binding && canonical(target.root) == prepared.intent.root &&
                canonical(target.commonDir) == prepared.intent.commonDir)
            val live = backend.worktrees(target).singleOrNull { canonicalIfPresent(it.path) == prepared.intent.root }
            require(live == tree) { "创建后 Git 指纹已变化，登记证据无法确认。" }
            val evidence = ManagedWorktreeEvidence(canonical(target.gitDir), receipt.startOid, requireNotNull(tree.branch))
            val verified = commitPhase(prepared, prepared.observation, ManagedRegistrationPhase.Verified, evidence)
            // If the second CAS fails, the durable Verified evidence remains inspectable at restart.
            commitPhase(prepared, verified, ManagedRegistrationPhase.Registered, evidence)
        } catch (_: Exception) {
            warnings[warningKey] = "Git 已创建工作树，但 managed 登记失败；保留文件，不会重发或回滚。"
        } finally { changed() }
    }

    internal suspend fun stopped(prepared: Prepared, phase: ManagedRegistrationPhase) = withContext(NonCancellable) {
        try { commitPhase(prepared, prepared.observation, phase, null) }
        catch (_: Exception) {
            warnings[WarningKey(prepared.owner, prepared.intent.operationId)] =
                "登记未完成；结果与保护仍未知，不会自动认领或重发。"
        } finally { changed() }
    }

    private suspend fun commitPhase(
        prepared: Prepared, observed: DesktopManagedWorktreeStore.Observation,
        phase: ManagedRegistrationPhase, evidence: ManagedWorktreeEvidence?,
    ): DesktopManagedWorktreeStore.Observation = withContext(Dispatchers.IO) {
        val next = observed.snapshot.copy(revision = observed.snapshot.revision + 1,
            intents = observed.snapshot.intents.map { intent ->
                if (intent.operationId == prepared.intent.operationId) intent.copy(phase = phase, evidence = evidence) else intent
            })
        store.compareAndSet(observed, next).also { rememberWarning(prepared.owner, prepared.intent, it) }
    }

    override suspend fun read(repository: GitRepository): ManagedWorktreeRead {
        return try {
            val owner = verifiedOwner(repository.binding)
                ?: return ManagedWorktreeRead.Failed("本机 managed 归属验证服务不可用。", unavailable = true)
            verifyRepository(repository)
            val observed = withContext(Dispatchers.IO) { store.read(owner) }
            val common = canonical(repository.commonDir)
            val intents = observed.snapshot.intents.filter { it.commonDir == common }
            val trees = backend.worktrees(repository)
            val entries = trees.map { tree ->
                val root = canonicalIfPresent(tree.path)
                val intent = intents.filter { it.root == root }.maxByOrNull { it.createdAt }
                val evidence = intent?.evidence
                var ownership = ManagedOwnership.UnknownOwnership
                if (evidence != null && intent.phase in
                    setOf(ManagedRegistrationPhase.Registered, ManagedRegistrationPhase.Verified)) {
                    ownership = if (tree.bare || tree.prunable) ManagedOwnership.FingerprintMismatch else {
                        val actual = backend.discover(repository.binding, tree.path)
                        if (actual.binding == repository.binding && canonical(actual.root) == intent.root &&
                            canonical(actual.commonDir) == intent.commonDir &&
                            canonical(actual.gitDir) == evidence.targetGitDir) ManagedOwnership.Managed
                        else ManagedOwnership.FingerprintMismatch
                    }
                }
                val actualTree = if (!tree.bare && !tree.prunable) {
                    val actual = backend.discover(repository.binding, tree.path)
                    require(actual.binding == repository.binding && canonical(actual.root) == root && canonical(actual.commonDir) == common)
                    val head = backend.status(actual)
                    require(head.branch == tree.branch && head.headOid == tree.headOid) { "读取期间 Git HEAD 已变化。" }
                    tree.copy(branch = head.branch, headOid = head.headOid)
                } else tree
                ManagedWorktreeEntry(root, actualTree, ownership, intent?.phase, intent?.let { warning(owner, it) })
            }.toMutableList()
            intents.filter { intent -> entries.none { it.root == intent.root } }.forEach { intent ->
                entries += ManagedWorktreeEntry(intent.root, null, ManagedOwnership.Missing, intent.phase, warning(owner, intent))
            }
            val sessions = sessionObserver?.observe(repository.binding, owner, entries.map { it.root }) {
                verifiedOwner(repository.binding)
            }
            val pins = readPins(owner)
            val archives = readArchives(owner)
            // Revalidation is outside the store lease. No returned owner data survives a changed binding.
            require(verifiedOwner(repository.binding) == owner) { "读取期间 owner 已变化。" }
            val removalReads = entries.associate { entry ->
                entry.root to removalEvidence(repository, owner, trees, entry, sessions?.get(entry.root), pins, archives)
            }
            ManagedWorktreeRead.Ready(ManagedWorktreeInventory(owner, repository, entries.map { entry ->
                entry.copy(sessionObservation = sessions?.get(entry.root), removal = removalReads[entry.root])
            },
                entries.count { it.ownership == ManagedOwnership.Managed }, pins, archives))
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { ManagedWorktreeRead.Failed("无法完整读取 managed 登记与 Git 指纹；保护状态仍未知。") }
    }

    private suspend fun readPins(owner: ManagedWorktreeOwner): ManagedWorktreePinRead {
        val service = pinStore ?: return ManagedWorktreePinRead.Unavailable(owner, "聊天固定服务不可用。")
        return try { withContext(Dispatchers.IO) { ManagedWorktreePinRead.Available(service.read(owner).snapshot) } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { ManagedWorktreePinRead.Unknown(owner, "聊天固定存储读取失败；状态未知，请刷新。") }
    }

    private suspend fun readArchives(owner: ManagedWorktreeOwner): ManagedWorktreeChatArchiveRead {
        val service = archiveStore ?: return ManagedWorktreeChatArchiveRead.Unavailable(owner, "聊天归档服务不可用。")
        return try { withContext(Dispatchers.IO) { ManagedWorktreeChatArchiveRead.Available(service.read(owner).snapshot) } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { ManagedWorktreeChatArchiveRead.Unknown(owner, "聊天归档存储读取失败；状态未知，请刷新。") }
    }

    override suspend fun setPinned(request: ManagedWorktreePinRequest): ManagedWorktreePinReceipt {
        val service = pinStore ?: return ManagedWorktreePinReceipt.Rejected(request, "聊天固定服务不可用。")
        val observer = sessionObserver ?: return ManagedWorktreePinReceipt.Rejected(request, "完整会话观察服务不可用。")
        val repository = request.repository
        return try {
            currentCoroutineContext().ensureActive()
            require(repository.binding.identity == null && request.owner.identity == null)
            require(verifiedOwner(repository.binding) == request.owner) { "固定请求的 owner 已变化。" }
            require(request.expectedRevision >= 0 && request.key.id.isNotBlank())
            verifyRepository(repository)
            val observed = withContext(Dispatchers.IO) { service.read(request.owner) }
            require(observed.snapshot.revision == request.expectedRevision) { "聊天固定 revision 已过期；请刷新。" }
            val trees = backend.worktrees(repository)
            val tree = trees.singleOrNull { canonicalIfPresent(it.path) == request.root }
                ?: throw IllegalArgumentException("目标工作树 missing；不会固定。")
            require(!tree.prunable && !tree.bare && canonical(tree.path) == request.root)
            val target = backend.discover(repository.binding, tree.path)
            require(target.binding == repository.binding && canonical(target.root) == request.root &&
                canonical(target.commonDir) == canonical(repository.commonDir))
            val head = backend.status(target)
            require(head.branch == tree.branch && head.headOid == tree.headOid) { "工作树 HEAD 已变化。" }
            val sessions = observer.observe(repository.binding, request.owner, trees.map { canonicalIfPresent(it.path) }) {
                verifiedOwner(repository.binding)
            }
            require(sessions.values.all { it.owner == request.owner &&
                it.status == ManagedSessionObservationStatus.Observed && it.issues.isEmpty() }) { "会话来源未完成核验。" }
            val current = requireNotNull(sessions[request.root])
            require(current.sessions.count { it.id == request.key.id && it.pinKey == request.key } == 1) {
                "完整 header 身份已变化；不会绑定旧 Pin。"
            }
            verifyRepository(repository)
            require(backend.worktrees(repository).singleOrNull { canonicalIfPresent(it.path) == request.root } == tree)
            val finalTarget = backend.discover(repository.binding, tree.path)
            require(finalTarget == target && backend.status(finalTarget).let { it.branch == tree.branch && it.headOid == tree.headOid })
            // Git revalidation may suspend; check complete header identity again after it.
            val finalSessions = observer.observe(repository.binding, request.owner, trees.map { canonicalIfPresent(it.path) }) {
                verifiedOwner(repository.binding)
            }
            require(finalSessions.values.all { it.owner == request.owner &&
                it.status == ManagedSessionObservationStatus.Observed && it.issues.isEmpty() } &&
                finalSessions[request.root]?.sessions?.count { it.id == request.key.id && it.pinKey == request.key } == 1) {
                "提交前完整 header 身份或会话来源已变化。"
            }
            currentCoroutineContext().ensureActive()
            // Only the finite final fence and file commit survive caller cancellation. No Git or
            // session mutation occurs, and a committed directory-sync warning remains Applied.
            withContext(NonCancellable) {
                withContext(Dispatchers.IO) {
                    require(observer.isCurrent(repository.binding, request.owner) { verifiedOwner(repository.binding) })
                    require(verifiedOwner(repository.binding) == request.owner) { "提交前 owner 已变化。" }
                    val snapshot = observed.snapshot
                    val committed = if ((request.key in snapshot.pins) == request.desiredPinned) {
                        service.requireCurrent(observed)
                        observed
                    } else {
                        require(snapshot.revision < Long.MAX_VALUE)
                        service.compareAndSet(observed, snapshot.copy(revision = snapshot.revision + 1,
                            pins = if (request.desiredPinned) snapshot.pins + request.key else snapshot.pins - request.key))
                    }
                    changed()
                    ManagedWorktreePinReceipt.Applied(request, committed.snapshot, committed.durabilityWarning)
                }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (rejected: IllegalArgumentException) { ManagedWorktreePinReceipt.Rejected(request, rejected.message ?: "固定请求核验失败。") }
        catch (conflict: WorktreePinStoreException) { ManagedWorktreePinReceipt.Rejected(request, conflict.message ?: "固定存储冲突；请刷新。") }
        catch (_: Exception) { ManagedWorktreePinReceipt.Unknown(request, "固定结果无法确认；请刷新，不会自动重投。") }
    }

    override suspend fun setArchived(request: ManagedWorktreeChatArchiveRequest): ManagedWorktreeChatArchiveReceipt {
        val service = archiveStore ?: return ManagedWorktreeChatArchiveReceipt.Rejected(request, "聊天归档服务不可用。")
        val observer = sessionObserver ?: return ManagedWorktreeChatArchiveReceipt.Rejected(request, "完整会话观察服务不可用。")
        val repository = request.repository
        return try {
            currentCoroutineContext().ensureActive()
            require(repository.binding.identity == null && request.owner.identity == null)
            require(verifiedOwner(repository.binding) == request.owner) { "归档请求的 owner 已变化。" }
            require(request.expectedRevision >= 0 && request.key.id.isNotBlank())
            verifyRepository(repository)
            val observed = withContext(Dispatchers.IO) { service.read(request.owner) }
            require(observed.snapshot.revision == request.expectedRevision) { "聊天归档 revision 已过期；请刷新。" }
            val trees = backend.worktrees(repository)
            val tree = trees.singleOrNull { canonicalIfPresent(it.path) == request.root }
                ?: throw IllegalArgumentException("目标工作树 missing；不会归档。")
            require(!tree.prunable && !tree.bare && canonical(tree.path) == request.root)
            val target = backend.discover(repository.binding, tree.path)
            require(target.binding == repository.binding && canonical(target.root) == request.root &&
                canonical(target.commonDir) == canonical(repository.commonDir))
            val head = backend.status(target)
            require(head.branch == tree.branch && head.headOid == tree.headOid) { "工作树 HEAD 已变化。" }
            val sessions = observer.observe(repository.binding, request.owner, trees.map { canonicalIfPresent(it.path) }) {
                verifiedOwner(repository.binding)
            }
            require(sessions.values.all { it.owner == request.owner &&
                it.status == ManagedSessionObservationStatus.Observed && it.issues.isEmpty() }) { "会话来源未完成核验。" }
            val current = requireNotNull(sessions[request.root])
            require(current.sessions.count {
                it.id == request.key.id && it.pinKey?.headerFingerprint == request.key.headerFingerprint
            } == 1) {
                "完整 header 身份已变化；不会绑定旧归档。"
            }
            verifyRepository(repository)
            require(backend.worktrees(repository).singleOrNull { canonicalIfPresent(it.path) == request.root } == tree)
            val finalTarget = backend.discover(repository.binding, tree.path)
            require(finalTarget == target && backend.status(finalTarget).let { it.branch == tree.branch && it.headOid == tree.headOid })
            // Git revalidation may suspend; check complete header identity again after it.
            val finalSessions = observer.observe(repository.binding, request.owner, trees.map { canonicalIfPresent(it.path) }) {
                verifiedOwner(repository.binding)
            }
            require(finalSessions.values.all { it.owner == request.owner &&
                it.status == ManagedSessionObservationStatus.Observed && it.issues.isEmpty() } &&
                finalSessions[request.root]?.sessions?.count {
                    it.id == request.key.id && it.pinKey?.headerFingerprint == request.key.headerFingerprint
                } == 1) {
                "提交前完整 header 身份或会话来源已变化。"
            }
            currentCoroutineContext().ensureActive()
            // Exclusive archive fence: Busy is reported instead of queued. The live Agent is stopped
            // and its Session detached only after the durable commit succeeds; a durable-only chat
            // has no live instance to fence. Unarchive only removes the durable record.
            val fence = if (!request.desiredArchived) {
                null
            } else if (archiveAgents != null) {
                when (val begin = archiveAgents.invoke(request.key.id)) {
                    is AgentArchiveBegin.Granted -> AgentFence(begin.preparation)
                    is AgentArchiveBegin.Busy -> return ManagedWorktreeChatArchiveReceipt.Rejected(
                        request, "归档被拒绝：关联聊天仍有在途操作（${begin.reason}）。")
                    is AgentArchiveBegin.Rejected -> return ManagedWorktreeChatArchiveReceipt.Rejected(request, begin.reason)
                    is AgentArchiveBegin.NotRegistered -> null
                }
            } else {
                when (val acquired = observer.acquireArchive(repository.binding, request.owner, request.key.id,
                    request.key.headerFingerprint) { verifiedOwner(repository.binding) }) {
                    is DesktopWorktreeSessionObserver.ArchiveFence.Fenced -> SessionFence(acquired.token)
                    is DesktopWorktreeSessionObserver.ArchiveFence.Busy -> return ManagedWorktreeChatArchiveReceipt.Rejected(
                        request, "归档被拒绝：关联聊天仍有在途操作（${acquired.reason}）。")
                    is DesktopWorktreeSessionObserver.ArchiveFence.Rejected -> return ManagedWorktreeChatArchiveReceipt.Rejected(
                        request, acquired.issue.reason)
                    is DesktopWorktreeSessionObserver.ArchiveFence.NotLive -> null
                }
            }
            try {
                // Only the finite final fence, the durable commit and the post-commit quiesce survive
                // caller cancellation. No Git mutation occurs; a committed directory-sync warning stays
                // Applied, and a failed quiesce is reported separately from the durable commit.
                val committed = withContext(NonCancellable) {
                    withContext(Dispatchers.IO) {
                        require(observer.isCurrent(repository.binding, request.owner) { verifiedOwner(repository.binding) })
                        require(verifiedOwner(repository.binding) == request.owner) { "提交前 owner 已变化。" }
                        val snapshot = observed.snapshot
                        val result = if ((request.key in snapshot.archived) == request.desiredArchived) {
                            service.requireCurrent(observed)
                            observed
                        } else {
                            require(snapshot.revision < Long.MAX_VALUE)
                            service.compareAndSet(observed, snapshot.copy(revision = snapshot.revision + 1,
                                archived = if (request.desiredArchived) snapshot.archived + request.key else snapshot.archived - request.key))
                        }
                        changed()
                        result
                    }
                }
                val quiesceWarning = withContext(NonCancellable) { fence?.commit() }
                // Unarchive reports the quiescent rehydration separately from the durable commit;
                // a failed restore never rewrites a committed unarchive into a failure.
                val restoreFn = restoreChat
                val restore = if (!request.desiredArchived && restoreFn != null) {
                    withContext(NonCancellable) { restoreFn.invoke(request.key.id) }
                } else null
                ManagedWorktreeChatArchiveReceipt.Applied(request, committed.snapshot,
                    quiesceWarning ?: committed.durabilityWarning, restore)
            } finally {
                withContext(NonCancellable) { fence?.dispose() }
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (rejected: IllegalArgumentException) { ManagedWorktreeChatArchiveReceipt.Rejected(request, rejected.message ?: "归档请求核验失败。") }
        catch (conflict: WorktreeChatArchiveStoreException) { ManagedWorktreeChatArchiveReceipt.Rejected(request, conflict.message ?: "归档存储冲突；请刷新。") }
        catch (_: Exception) { ManagedWorktreeChatArchiveReceipt.Unknown(request, "归档结果无法确认；请刷新，不会自动重投。") }
    }


    /**
     * Removal evidence comes from the same read that publishes the inventory, so a page can never
     * show a delete affordance built from stale data. A missing service reports Unavailable instead
     * of a plan: an incomplete evidence chain must not look like a verdict.
     */
    private suspend fun removalEvidence(
        repository: GitRepository, owner: ManagedWorktreeOwner, trees: List<GitWorktree>,
        entry: ManagedWorktreeEntry, observation: ManagedSessionObservation?,
        pins: ManagedWorktreePinRead, archives: ManagedWorktreeChatArchiveRead,
    ): ManagedWorktreeRemovalRead {
        val remover = backend as? GitWorktreeRemover
        if (remover == null || !remover.canRemoveWorktree(repository.binding))
            return ManagedWorktreeRemovalRead.Unavailable(owner, "此 Host 未提供本机工作树删除能力。")
        if (sessionObserver == null) return ManagedWorktreeRemovalRead.Unavailable(owner, "完整会话观察服务不可用。")
        if (pinStore == null) return ManagedWorktreeRemovalRead.Unavailable(owner, "聊天固定服务不可用。")
        if (archiveStore == null) return ManagedWorktreeRemovalRead.Unavailable(owner, "聊天归档服务不可用。")
        val prober = backend as? GitWorktreeContentProbe
        if (prober == null || !prober.canProbeWorktreeContent(repository.binding))
            return ManagedWorktreeRemovalRead.Unavailable(owner, "工作树内容核验服务不可用。")
        val blocks = computeRemovalBlocks(repository, trees, entry, observation, pins, archives, prober)
        return ManagedWorktreeRemovalRead.Available(ManagedWorktreeRemovalPlan(
            owner, repository, entry.root, entry.worktree?.headOid, entry.worktree?.branch, blocks))
    }

    private suspend fun computeRemovalBlocks(
        repository: GitRepository, trees: List<GitWorktree>, entry: ManagedWorktreeEntry,
        observation: ManagedSessionObservation?, pins: ManagedWorktreePinRead,
        archives: ManagedWorktreeChatArchiveRead, prober: GitWorktreeContentProbe,
    ): List<ManagedRemovalBlock> {
        val blocks = mutableListOf<ManagedRemovalBlock>()
        val tree = entry.worktree
        if (entry.ownership != ManagedOwnership.Managed)
            blocks += ManagedRemovalBlock(ManagedRemovalBlockKind.NotManaged, "归属未核验为 Managed（" + entry.ownership + "）。")
        if (tree == null) {
            blocks += ManagedRemovalBlock(ManagedRemovalBlockKind.Missing, "当前 Git 工作树列表未包含此路径。")
            return blocks
        }
        if (tree.bare) blocks += ManagedRemovalBlock(ManagedRemovalBlockKind.Bare, "目标是 bare 工作树。")
        if (tree.prunable) blocks += ManagedRemovalBlock(ManagedRemovalBlockKind.Prunable, "Git 已将目标标记为 prunable。")
        if (tree.locked) blocks += ManagedRemovalBlock(ManagedRemovalBlockKind.Locked, "目标工作树已锁定。")
        // A null branch is a detached HEAD: the plan has no branch fingerprint to re-verify, so a
        // null==null match would silently count as verification. Refuse instead of guessing.
        if (tree.branch == null)
            blocks += ManagedRemovalBlock(ManagedRemovalBlockKind.Detached, "目标是 detached 工作树，缺少可复核的分支指纹。")
        val rootPath = runCatching { Path.of(entry.root).toRealPath() }.getOrNull()
        val repositoryRoot = runCatching { Path.of(repository.root).toRealPath() }.getOrNull()
        if (rootPath == null) blocks += ManagedRemovalBlock(ManagedRemovalBlockKind.Missing, "目标路径无法解析。")
        if (rootPath != null && repositoryRoot != null && rootPath == repositoryRoot)
            blocks += ManagedRemovalBlock(ManagedRemovalBlockKind.PrimaryWorktree, "目标是仓库的主工作树。")
        if (rootPath != null) {
            // A linked worktree that merely lives under the primary root is normal; only another
            // linked worktree nested with the target blocks removal.
            val others = trees.mapNotNull { candidate -> runCatching { Path.of(candidate.path).toRealPath() }.getOrNull() }
                .filter { it != rootPath && it != repositoryRoot }
            if (others.any { it.startsWith(rootPath) })
                blocks += ManagedRemovalBlock(ManagedRemovalBlockKind.NestedPath, "目标内部还包含另一个工作树。")
            if (others.any { rootPath.startsWith(it) })
                blocks += ManagedRemovalBlock(ManagedRemovalBlockKind.NestedPath, "目标位于另一个工作树内部。")
        }
        if (observation == null) {
            blocks += ManagedRemovalBlock(ManagedRemovalBlockKind.SessionsUnavailable, "关联会话未核验。")
        } else if (observation.status != ManagedSessionObservationStatus.Observed || observation.issues.isNotEmpty()) {
            blocks += ManagedRemovalBlock(ManagedRemovalBlockKind.SessionsUnavailable, "关联会话来源未完成核验。")
        } else {
            if (observation.sessions.any { ManagedSessionSource.Live in it.sources })
                blocks += ManagedRemovalBlock(ManagedRemovalBlockKind.RunningAgent, "仍有聊天在此工作树中运行。")
            if (observation.sessions.any { ManagedSessionSource.Durable in it.sources })
                blocks += ManagedRemovalBlock(ManagedRemovalBlockKind.DurableSessions, "仍有持久关联聊天历史。")
        }
        val observedKeys = observation?.sessions?.mapNotNull { it.pinKey }.orEmpty()
        when (pins) {
            is ManagedWorktreePinRead.Available -> if (observedKeys.any { it in pins.snapshot.pins })
                blocks += ManagedRemovalBlock(ManagedRemovalBlockKind.PinnedChats, "有关联聊天处于固定状态。")
            is ManagedWorktreePinRead.Unknown ->
                blocks += ManagedRemovalBlock(ManagedRemovalBlockKind.PinsUnavailable, pins.reason)
            is ManagedWorktreePinRead.Unavailable ->
                blocks += ManagedRemovalBlock(ManagedRemovalBlockKind.PinsUnavailable, pins.reason)
        }
        val archivedKeys = observation?.sessions?.mapNotNull { session ->
            session.pinKey?.let { ChatArchiveKey(it.id, it.headerFingerprint) }
        }.orEmpty()
        when (archives) {
            is ManagedWorktreeChatArchiveRead.Available -> if (archivedKeys.any { it in archives.snapshot.archived })
                blocks += ManagedRemovalBlock(ManagedRemovalBlockKind.ArchivedChats, "有关联聊天处于归档状态。")
            is ManagedWorktreeChatArchiveRead.Unknown ->
                blocks += ManagedRemovalBlock(ManagedRemovalBlockKind.ArchiveUnavailable, archives.reason)
            is ManagedWorktreeChatArchiveRead.Unavailable ->
                blocks += ManagedRemovalBlock(ManagedRemovalBlockKind.ArchiveUnavailable, archives.reason)
        }
        // The content scan is the most expensive evidence and a candidate that is already blocked
        // cannot become removable from it, so only otherwise-clean candidates are probed.
        if (blocks.none { it.kind in CONTENT_PRECLUDING_KINDS }) {
            val content = runCatching { prober.probeWorktreeContent(repository, entry.root) }.getOrNull()
            if (content == null) {
                blocks += ManagedRemovalBlock(ManagedRemovalBlockKind.ContentUnavailable, "无法核验工作树内容。")
            } else {
                if (!content.complete)
                    blocks += ManagedRemovalBlock(ManagedRemovalBlockKind.ContentScanIncomplete, "内容扫描未覆盖全部条目。")
                if (content.trackedChanges > 0 || content.untrackedEntries > 0)
                    blocks += ManagedRemovalBlock(ManagedRemovalBlockKind.UncommittedChanges,
                        "有 " + content.trackedChanges + " 个已跟踪变更与 " + content.untrackedEntries + " 个未跟踪条目。")
                if (content.submodules.isNotEmpty())
                    blocks += ManagedRemovalBlock(ManagedRemovalBlockKind.Submodules, "包含子模块：" + content.submodules.joinToString())
                if (content.nestedRepositories.isNotEmpty())
                    blocks += ManagedRemovalBlock(ManagedRemovalBlockKind.NestedRepositories,
                        "包含嵌套仓库：" + content.nestedRepositories.joinToString())
                if (content.ignoredEntries > 0)
                    blocks += ManagedRemovalBlock(ManagedRemovalBlockKind.IgnoredContent,
                        "包含 " + content.ignoredEntries + " 个被忽略条目。")
            }
        }
        return blocks
    }

    /**
     * Destructive and last: every dimension is re-read here, Git itself must accept the removal, and
     * only then is the managed intent retired. There is no force and no prune.
     */
    override suspend fun removeWorktree(request: ManagedWorktreeRemovalRequest): ManagedWorktreeRemovalReceipt {
        val remover = backend as? GitWorktreeRemover
            ?: return ManagedWorktreeRemovalReceipt.Unsupported(request, "此 Host 未提供本机工作树删除能力。")
        val repository = request.plan.repository
        val observer = sessionObserver
        val pinService = pinStore
        val archiveService = archiveStore
        val prober = backend as? GitWorktreeContentProbe
        if (observer == null || pinService == null || archiveService == null || prober == null)
            return ManagedWorktreeRemovalReceipt.Unsupported(request, "移除保护服务不完整；不会删除。")
        return try {
            currentCoroutineContext().ensureActive()
            val owner = request.plan.owner
            require(repository.binding.identity == null && owner.identity == null)
            require(verifiedOwner(repository.binding) == owner) { "删除请求的 owner 已变化。" }
            require(request.plan.blocks.isEmpty()) { "删除计划仍含未核验的保护项；请刷新。" }
            verifyRepository(repository)
            val trees = backend.worktrees(repository)
            val tree = trees.singleOrNull { canonicalIfPresent(it.path) == canonicalIfPresent(request.plan.root) }
                ?: throw IllegalArgumentException("目标工作树已不在 Git 列表中；不会删除。")
            require(canonical(tree.path) == canonicalIfPresent(request.plan.root)) { "目标路径已变化。" }
            require(request.plan.headOid != null && tree.headOid == request.plan.headOid && tree.branch == request.plan.branch) {
                "工作树 HEAD 或分支已变化；请刷新后重试。"
            }
            val observation = observer.observe(repository.binding, owner, trees.map { canonicalIfPresent(it.path) }) {
                verifiedOwner(repository.binding)
            }[request.plan.root]
            val pins = readPins(owner)
            val archives = readArchives(owner)
            val entry = ManagedWorktreeEntry(request.plan.root, tree, ManagedOwnership.Managed)
            val blocks = computeRemovalBlocks(repository, trees, entry, observation, pins, archives, prober)
            if (blocks.isNotEmpty()) return ManagedWorktreeRemovalReceipt.Blocked(
                request, blocks, "删除被拒绝：" + blocks.first().detail)
            // Git revalidation and the content scan may suspend; re-read the fingerprint immediately
            // before dispatch so a plan built from an older read cannot delete a changed worktree.
            verifyRepository(repository)
            val finalTree = backend.worktrees(repository)
                .singleOrNull { canonicalIfPresent(it.path) == canonicalIfPresent(request.plan.root) }
                ?: return ManagedWorktreeRemovalReceipt.Blocked(request, listOf(ManagedRemovalBlock(
                    ManagedRemovalBlockKind.Missing, "目标工作树已消失。")), "删除被拒绝：目标已不在列表中。")
            if (finalTree.headOid != tree.headOid || finalTree.branch != tree.branch)
                return ManagedWorktreeRemovalReceipt.Blocked(request, listOf(ManagedRemovalBlock(
                    ManagedRemovalBlockKind.HeadChanged, "HEAD 或分支已变化。")), "删除被拒绝：工作树已变化。")
            currentCoroutineContext().ensureActive()
            val target = GitRemoveWorktreeRequest(repository.binding, request.plan.root,
                requireNotNull(finalTree.headOid), finalTree.branch)
            when (val result = remover.removeWorktree(repository, target)) {
                is GitRemoveWorktreeResult.Removed -> {
                    withContext(NonCancellable) { retireIntent(owner, request.plan.root) }
                    ManagedWorktreeRemovalReceipt.Removed(request, result.receipt.worktrees)
                }
                is GitRemoveWorktreeResult.Rejected ->
                    ManagedWorktreeRemovalReceipt.Failed(request, result.failure.message)
                is GitRemoveWorktreeResult.UnknownOutcome ->
                    ManagedWorktreeRemovalReceipt.UnknownOutcome(request, result.failure.message)
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (rejected: IllegalArgumentException) {
            ManagedWorktreeRemovalReceipt.Blocked(request, request.plan.blocks, rejected.message ?: "删除请求核验失败。")
        }
        catch (_: Exception) { ManagedWorktreeRemovalReceipt.UnknownOutcome(request, "删除结果无法确认；请刷新，不会自动重投。") }
    }

    /** The managed intent is retired only after Git reports the path is gone. Best effort. */
    private suspend fun retireIntent(owner: ManagedWorktreeOwner, root: String) {
        runCatching {
            withContext(Dispatchers.IO) {
                val observed = store.read(owner)
                val remaining = observed.snapshot.intents.filterNot {
                    canonicalIfPresent(it.root) == canonicalIfPresent(root)
                }
                if (remaining.size != observed.snapshot.intents.size)
                    store.compareAndSet(observed, observed.snapshot.copy(
                        revision = observed.snapshot.revision + 1, intents = remaining))
            }
            changed()
        }
    }

    private companion object {
        val CONTENT_PRECLUDING_KINDS = setOf(
            ManagedRemovalBlockKind.NotManaged, ManagedRemovalBlockKind.PrimaryWorktree,
            ManagedRemovalBlockKind.NestedPath, ManagedRemovalBlockKind.RunningAgent,
            ManagedRemovalBlockKind.DurableSessions, ManagedRemovalBlockKind.PinnedChats,
            ManagedRemovalBlockKind.ArchivedChats, ManagedRemovalBlockKind.PinsUnavailable,
            ManagedRemovalBlockKind.ArchiveUnavailable, ManagedRemovalBlockKind.SessionsUnavailable,
            ManagedRemovalBlockKind.Bare, ManagedRemovalBlockKind.Prunable, ManagedRemovalBlockKind.Locked,
            ManagedRemovalBlockKind.Missing,
        )
    }


    private interface LiveFence : Disposable {
        /** Best-effort quiesce after the durable commit. Returns a warning when it could not complete. */
        suspend fun commit(): String?
    }

    private class AgentFence(private val preparation: AgentArchivePreparation) : LiveFence {
        override suspend fun commit(): String? = preparation.commit()

        override suspend fun dispose() { preparation.dispose() }
    }

    /**
     * A live Session without a registered Agent has no Agent to stop. The durable archive record
     * stays authoritative, so only the exclusive fence is released here.
     */
    private class SessionFence(private val token: SessionArchiveToken) : LiveFence {
        override suspend fun commit(): String? = null

        override suspend fun dispose() { token.dispose() }
    }

    private fun warning(owner: ManagedWorktreeOwner, intent: ManagedWorktreeIntent): String? =
        warnings[WarningKey(owner, intent.operationId)] ?: when (intent.phase) {
            ManagedRegistrationPhase.Prepared -> "Prepared 意图缺少持久 verified evidence；归属未知。"
            ManagedRegistrationPhase.UnknownOutcome -> "创建结果未知；不会重发或自动认领。"
            ManagedRegistrationPhase.Rejected -> "创建未完成；保留登记意图。"
            ManagedRegistrationPhase.Verified -> "已保存创建证据，最终登记未完成。"
            ManagedRegistrationPhase.Registered -> null
        }

    private fun rememberWarning(owner: ManagedWorktreeOwner, intent: ManagedWorktreeIntent,
        observation: DesktopManagedWorktreeStore.Observation) {
        observation.durabilityWarning?.let { warnings[WarningKey(owner, intent.operationId)] = it }
    }

    private fun changed() { mutableRevision.update { it + 1 } }

    private suspend fun verifiedOwner(binding: GitBinding): ManagedWorktreeOwner? {
        if (binding.access != GitAccess.LocalReadOnly) return null
        val owner = ownerValidator?.invoke(binding) ?: return null
        if (owner.identity != binding.identity || owner.hostId != binding.hostId) return null
        if (canonical(owner.homeNamespace) != owner.homeNamespace) return null
        return owner
    }

    private suspend fun verifyRepository(repository: GitRepository) {
        val actual = backend.discover(repository.binding, repository.root)
        require(actual.binding == repository.binding && canonical(actual.root) == canonical(repository.root) &&
            canonical(actual.commonDir) == canonical(repository.commonDir) && canonical(actual.gitDir) == canonical(repository.gitDir))
    }

    private fun canonical(path: String): String = Path.of(path).toRealPath().toString()
    private fun canonicalIfPresent(path: String): String =
        if (Files.exists(Path.of(path), NOFOLLOW_LINKS)) canonical(path) else Path.of(path).toAbsolutePath().normalize().toString()
}
