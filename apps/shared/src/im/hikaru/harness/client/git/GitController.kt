package im.hikaru.harness.client.git

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Call public actions on one owner lane (normally the UI dispatcher). Backend IO may suspend. */
public class GitController(scope: CoroutineScope, private val backend: GitBackend) : AutoCloseable {
    private val owner = SupervisorJob(scope.coroutineContext[Job])
    private val tasks = CoroutineScope(scope.coroutineContext + owner)
    private val mutableState = MutableStateFlow(GitState())
    public val state: StateFlow<GitState> = mutableState.asStateFlow()
    private enum class Query { Repository, Status, Branches, Worktrees, Diff, Commit, SummaryStats }
    private val jobs = mutableMapOf<Query, Job>()
    private val requests = mutableMapOf<Query, Long>()
    private val previewJobs = mutableMapOf<String, Job>()
    private var epoch = 0L
    private var closed = false
    private val creator = backend as? GitWorktreeCreator
    private val switcher = backend as? GitBranchSwitcher
    private val branchCreator = backend as? GitBranchCreator
    private val committer = backend as? GitCommitter
    private var mutationBusy = false
    // Kept across navigation; a fenced unknown result must still require a scan when revisited.
    private data class UncertaintyKey(val owner: GitBinding, val commonDir: String)
    private fun uncertaintyKey(repo: GitRepository): UncertaintyKey =
        UncertaintyKey(repo.binding.copy(generation = 0, access = GitAccess.LocalReadOnly), repo.commonDir)
    private val uncertainRepositories = mutableMapOf<UncertaintyKey, GitCreateWorktreeFailure>()
    private class CheckoutUncertainty(val failure: GitFailure)
    private val uncertainCheckouts = mutableMapOf<UncertaintyKey, CheckoutUncertainty>()
    private class BranchCreateUncertainty(val failure: GitFailure)
    private val uncertainBranchCreates = mutableMapOf<UncertaintyKey, BranchCreateUncertainty>()
    private data class IndexKey(val repository: UncertaintyKey, val root: String, val gitDir: String)
    private class CommitUncertainty
    private fun indexKey(repo: GitRepository) = IndexKey(uncertaintyKey(repo), repo.root, repo.gitDir)
    private val uncertainCommits = mutableMapOf<IndexKey, CommitUncertainty>()
    private fun commitUnknownFor(repo: GitRepository): Boolean = uncertainCommits.keys.any { it.repository == uncertaintyKey(repo) }
    private fun commitAvailable(binding: GitBinding?): Boolean = binding != null &&
        binding.access == GitAccess.LocalReadOnly && committer?.canCommit(binding) == true
    private fun switchAvailable(binding: GitBinding?): Boolean = binding != null &&
        binding.access == GitAccess.LocalReadOnly && switcher?.canSwitchBranch(binding) == true
    private fun branchCreateAvailable(binding: GitBinding?): Boolean = binding != null &&
        binding.access == GitAccess.LocalReadOnly && branchCreator?.canCreateBranch(binding) == true

    private fun available(binding: GitBinding?): Boolean = binding != null &&
        binding.access == GitAccess.LocalReadOnly && creator?.canCreateWorktree(binding) == true

    public fun bind(binding: GitBinding?) {
        check(!closed)
        if (state.value.binding == binding) return
        val old = state.value
        val sameHost = binding != null && old.binding?.sameHost(binding) == true
        invalidate()
        mutableState.value = GitState(binding = binding, selectedCwd = old.selectedCwd.takeIf { sameHost },
            creationAvailable = available(binding), creationBusy = mutationBusy, checkoutAvailable = switchAvailable(binding),
            branchCreateAvailable = branchCreateAvailable(binding), commitAvailable = commitAvailable(binding))
        if (state.value.canRefresh) discover()
    }

    /** Merely selects and reads; selecting a worktree directory never changes its branch. */
    public fun selectRepository(cwd: String?) {
        check(!closed)
        val selected = cwd?.takeUnless { it.isBlank() }
        if (state.value.selectedCwd == selected) return
        invalidate()
        mutableState.value = GitState(binding = state.value.binding, selectedCwd = selected,
            creationAvailable = available(state.value.binding), creationBusy = mutationBusy, checkoutAvailable = switchAvailable(state.value.binding),
            branchCreateAvailable = branchCreateAvailable(state.value.binding), commitAvailable = commitAvailable(state.value.binding))
        if (state.value.canRefresh) discover()
    }

    public fun selectWorktree(path: String) { selectRepository(path) }

    /** Explicit index-only mutation. Draft text is accepted once; navigation cannot redirect it. */
    public fun commit(message: String) {
        check(!closed)
        if (mutationBusy || !state.value.canCommit) return
        val acceptedMessage = normalizeGitCommitMessage(message) ?: return
        val repo = (state.value.repository as? GitRead.Ready<GitRepository>)?.value ?: return
        val snapshot = (state.value.commitSnapshot as? GitRead.Ready<GitCommitSnapshot>)?.value ?: return
        val capability = committer ?: return
        val request = GitCommitRequest(snapshot, acceptedMessage, includeUnstaged = state.value.includeUnstaged)
        invalidate()
        val capturedEpoch = epoch
        fun current(): Boolean = !closed && epoch == capturedEpoch && state.value.binding == repo.binding &&
            (state.value.repository as? GitRead.Ready<GitRepository>)?.value == repo
        mutationBusy = true
        mutableState.value = state.value.copy(creationBusy = true, commitBusy = true,
            commit = GitCommit.Submitting(request), commitSnapshot = GitRead.Unselected,
            status = GitRead.Unselected, branches = GitRead.Unselected, worktrees = GitRead.Unselected, diff = GitRead.Unselected)
        var started = false
        val job = tasks.launch(start = CoroutineStart.LAZY) {
            started = true
            var refreshFencedSelection = false
            try {
                when (val result = capability.commit(repo, request)) {
                    is GitCommitResult.Committed -> {
                        if (!validCommitReceipt(repo, request, result.receipt)) {
                            recordCommitUnknown(repo, current())
                        } else if (current()) {
                            invalidate()
                            val receipt = result.receipt
                            mutableState.value = state.value.copy(commit = GitCommit.Verified(receipt),
                                commitSnapshot = GitRead.Ready(receipt.snapshot), status = GitRead.Ready(receipt.snapshot.status),
                                branches = GitRead.Ready(receipt.branches.toList()), worktrees = GitRead.Ready(receipt.worktrees.toList()),
                                selectedBranchRef = null)
                            refreshDiff()
                        } else refreshFencedSelection = true
                    }
                    is GitCommitResult.Rejected -> if (current()) {
                        mutableState.value = state.value.copy(commit = GitCommit.Rejected(result.failure))
                        refreshFencedSelection = true
                    }
                    is GitCommitResult.UnknownOutcome -> recordCommitUnknown(repo, current())
                }
            } catch (_: CancellationException) {
                recordCommitUnknown(repo, current())
            } catch (_: Exception) {
                recordCommitUnknown(repo, current())
            } finally {
                mutationBusy = false
                if (!closed) mutableState.value = state.value.copy(creationBusy = false, commitBusy = false)
                if (refreshFencedSelection && !closed) refreshCurrentCommonDir(repo)
            }
        }
        job.invokeOnCompletion {
            if (!started) {
                // A cancelled owner can prevent the body (and its finally) from running at all.
                // No capability invocation occurred, so this is a rejection rather than unknown.
                mutationBusy = false
                if (!closed) mutableState.value = state.value.copy(creationBusy = false, commitBusy = false,
                    commit = if (current()) GitCommit.Rejected(GitFailure(GitFailureKind.CommandFailed,
                        "提交在调用 Git 前已取消。")) else state.value.commit)
            }
        }
        job.start()
    }

    private fun validCommitReceipt(repo: GitRepository, request: GitCommitRequest, receipt: GitCommitReceipt): Boolean {
        val accepted = request.snapshot
        val after = receipt.snapshot
        val oid = Regex("(?:[0-9a-f]{40}|[0-9a-f]{64})")
        // The committed tree must equal the index the backend committed from: the accepted snapshot,
        // or the post-stage index when the request asked for the stage step.
        val stagedMatches = if (request.includeUnstaged) {
            receipt.stagedByRequest && receipt.stagedIndexDigest.matches(Regex("[0-9a-f]{64}"))
        } else {
            !receipt.stagedByRequest && receipt.stagedIndexDigest == accepted.indexDigest
        }
        return receipt.repository == repo && receipt.request == request && after.repository == repo &&
            receipt.commitOid.matches(oid) && receipt.treeOid.matches(oid) && receipt.commitOid != accepted.status.headOid &&
            receipt.parentOid == accepted.status.headOid && receipt.message == request.message &&
            stagedMatches && receipt.treeDigest == receipt.stagedIndexDigest &&
            after.indexDigest == receipt.stagedIndexDigest &&
            after.status.headOid == receipt.commitOid && after.status.branch == accepted.status.branch &&
            after.stagedCount == 0 && after.blockedReason == null && !after.status.changes.any { it.staged || it.conflicted } &&
            receipt.branches.singleOrNull { it.ref == accepted.status.branch && it.current && !it.remote }?.oid == receipt.commitOid &&
            receipt.worktrees.any { it.path == repo.root && it.branch == accepted.status.branch && it.headOid == receipt.commitOid &&
                !it.bare && !it.detached && !it.prunable }
    }

    private fun commitUnknownFailure() = GitFailure(GitFailureKind.CommandFailed,
        "结果未知：先前提交可能已完成，请重新读取原工作树暂存区与仓库状态；不要直接重试。")

    private fun recordCommitUnknown(repo: GitRepository, publish: Boolean) {
        uncertainCommits[indexKey(repo)] = CommitUncertainty()
        val selected = (state.value.repository as? GitRead.Ready<GitRepository>)?.value
        if (!closed && (publish || selected != null && uncertaintyKey(selected) == uncertaintyKey(repo))) {
            invalidate()
            mutableState.value = state.value.copy(commit = GitCommit.UnknownOutcome(commitUnknownFailure()), commitNeedsRescan = true,
                commitSnapshot = GitRead.Unselected, status = GitRead.Unselected, branches = GitRead.Unselected,
                worktrees = GitRead.Unselected, diff = GitRead.Unselected)
        }
    }

    private fun refreshCurrentCommonDir(repo: GitRepository) {
        val selected = (state.value.repository as? GitRead.Ready<GitRepository>)?.value
        if (selected != null && uncertaintyKey(selected) == uncertaintyKey(repo)) {
            invalidate()
            mutableState.value = state.value.copy(status = GitRead.Unselected, branches = GitRead.Unselected,
                worktrees = GitRead.Unselected, diff = GitRead.Unselected, commitSnapshot = GitRead.Unselected, selectedBranchRef = null)
            refreshData()
        } else if (selected == null && state.value.canRefresh && state.value.binding?.sameHost(repo.binding) == true) {
            invalidate()
            discover()
        }
    }

    /** Selecting a picker entry never mutates Git. Only actual branch inventory entries are accepted. */
    public fun selectBranch(ref: String?) {
        check(!closed)
        val refs = (state.value.branches as? GitRead.Ready<List<GitBranch>>)?.value.orEmpty()
        if (ref != null && refs.none { it.ref == ref && !it.remote && it.ref.startsWith("refs/heads/") }) return
        mutableState.value = state.value.copy(selectedBranchRef = ref)
    }

    public fun switchBranch() {
        check(!closed)
        if (mutationBusy || !state.value.canSwitchBranch) return
        val repo = (state.value.repository as? GitRead.Ready<GitRepository>)?.value ?: return
        val source = (state.value.status as? GitRead.Ready<GitStatusSnapshot>)?.value ?: return
        val target = (state.value.branches as? GitRead.Ready<List<GitBranch>>)?.value
            ?.singleOrNull { it.ref == state.value.selectedBranchRef } ?: return
        val capability = switcher ?: return
        val request = GitBranchCheckoutRequest(repo.binding, repo.root, source.branch, source.headOid ?: return, target.ref, target.oid)
        invalidate()
        val capturedEpoch = epoch
        fun current(): Boolean = !closed && epoch == capturedEpoch && state.value.binding == repo.binding &&
            (state.value.repository as? GitRead.Ready<GitRepository>)?.value == repo
        mutationBusy = true
        mutableState.value = state.value.copy(creationBusy = true, checkoutBusy = true,
            checkout = GitBranchCheckout.Switching(request), status = GitRead.Unselected, branches = GitRead.Unselected,
            worktrees = GitRead.Unselected, diff = GitRead.Unselected, commitSnapshot = GitRead.Unselected)
        val job = tasks.launch(start = CoroutineStart.LAZY) {
            var refreshFencedSelection = false
            try {
                when (val result = capability.switchBranch(repo, request)) {
                    is GitBranchCheckoutResult.Switched -> {
                        val receipt = result.receipt
                        if (!validCheckoutReceipt(repo, request, receipt)) {
                            recordCheckoutUnknown(repo, checkoutUnknown("切换回执与请求不一致，结果未知，请刷新仓库。"), current())
                        } else if (current()) {
                            invalidate()
                            mutableState.value = state.value.copy(checkout = GitBranchCheckout.Switched(receipt),
                                status = GitRead.Ready(receipt.status.copy(changes = receipt.status.changes.toList())),
                                branches = GitRead.Ready(receipt.branches.toList()), worktrees = GitRead.Ready(receipt.worktrees.toList()),
                            diff = GitRead.Unselected, selectedBranchRef = null, commitSnapshot = GitRead.Unselected)
                            refreshDiff()
                            refreshCommitSnapshot()
                        } else {
                            refreshFencedSelection = true
                        }
                    }
                    is GitBranchCheckoutResult.Rejected -> if (current()) {
                        mutableState.value = state.value.copy(checkout = GitBranchCheckout.Failed(result.failure))
                        refreshData()
                    }
                    is GitBranchCheckoutResult.UnknownOutcome -> recordCheckoutUnknown(repo, result.failure, current())
                }
            } catch (e: CancellationException) {
                recordCheckoutUnknown(repo, checkoutUnknown("切换已取消，结果未知，请刷新仓库。"), current())
                throw e
            } catch (_: Exception) {
                recordCheckoutUnknown(repo, checkoutUnknown("切换结果未知，请刷新仓库。"), current())
            } finally {
                mutationBusy = false
                if (!closed) mutableState.value = state.value.copy(creationBusy = false, checkoutBusy = false)
                if (refreshFencedSelection && !closed) {
                    val selected = (state.value.repository as? GitRead.Ready<GitRepository>)?.value
                    if (selected != null && uncertaintyKey(selected) == uncertaintyKey(repo)) {
                        // Only refresh the current selection. Never expose a fenced scope's receipt.
                        invalidate()
                        mutableState.value = state.value.copy(status = GitRead.Unselected, branches = GitRead.Unselected,
                            worktrees = GitRead.Unselected, diff = GitRead.Unselected, selectedBranchRef = null)
                        refreshData()
                    } else if (selected == null && state.value.canRefresh && state.value.binding?.sameHost(repo.binding) == true) {
                        // Discovery was still pending when the mutation finished; fence it too.
                        invalidate()
                        discover()
                    }
                }
            }
        }
        job.start()
    }

    private fun validCheckoutReceipt(repo: GitRepository, request: GitBranchCheckoutRequest, receipt: GitBranchCheckoutReceipt): Boolean =
        receipt.repository == repo && receipt.request == request && receipt.status.branch == request.targetRef &&
        receipt.status.headOid == request.targetOid && receipt.status.changes.isEmpty() &&
        receipt.branches.singleOrNull { it.ref == request.targetRef && !it.remote && it.current }?.oid == request.targetOid &&
        receipt.worktrees.any { it.path == repo.root && it.branch == request.targetRef && it.headOid == request.targetOid &&
            !it.detached && !it.bare && !it.prunable }

    private fun checkoutUnknown(message: String): GitFailure = GitFailure(GitFailureKind.CommandFailed, message)

    private fun recordCheckoutUnknown(repo: GitRepository, failure: GitFailure, publish: Boolean) {
        val key = uncertaintyKey(repo)
        val safe = checkoutUnknown("先前分支切换结果未知，请刷新仓库状态、分支和工作树。")
        // New identity invalidates scans that started before this result. Never replay the mutation.
        uncertainCheckouts[key] = CheckoutUncertainty(safe)
        val selected = (state.value.repository as? GitRead.Ready<GitRepository>)?.value
        if (!closed && (publish || selected != null && uncertaintyKey(selected) == key)) {
            invalidate()
            mutableState.value = state.value.copy(checkout = GitBranchCheckout.UnknownOutcome(if (publish) failure else safe),
                checkoutNeedsRescan = true, status = GitRead.Unselected, branches = GitRead.Unselected,
                worktrees = GitRead.Unselected, diff = GitRead.Unselected)
        }
    }

    /** Readiness-only name draft; it never creates anything and is reset when the repository changes. */
    public fun setNewBranchName(value: String) {
        check(!closed)
        if (mutationBusy || state.value.newBranchName == value) return
        mutableState.value = state.value.copy(newBranchName = value)
    }

    /** Selecting a start point never mutates Git; only HEAD or an actual local branch is accepted. */
    public fun setNewBranchStart(ref: String?) {
        check(!closed)
        if (mutationBusy) return
        val refs = (state.value.branches as? GitRead.Ready<List<GitBranch>>)?.value.orEmpty()
        if (ref != null && ref != "HEAD" && refs.none { it.ref == ref && !it.remote && it.ref.startsWith("refs/heads/") }) return
        mutableState.value = state.value.copy(newBranchStartRef = ref)
    }

    /** Create-and-checkout. One admission slot with the other mutations; never creates silently. */
    public fun createBranch() {
        check(!closed)
        if (mutationBusy || !state.value.canCreateBranch) return
        val repo = (state.value.repository as? GitRead.Ready<GitRepository>)?.value ?: return
        val current = (state.value.status as? GitRead.Ready<GitStatusSnapshot>)?.value ?: return
        val capability = branchCreator ?: return
        val headOid = current.headOid ?: return
        val name = state.value.newBranchName.trim()
        val startRef = state.value.newBranchStartRef ?: "HEAD"
        val refs = (state.value.branches as? GitRead.Ready<List<GitBranch>>)?.value.orEmpty()
        val startOid = if (startRef == "HEAD") headOid
            else refs.singleOrNull { it.ref == startRef && !it.remote }?.oid ?: return
        val request = GitCreateBranchRequest(repo.binding, repo.root, current.branch, headOid, name, startRef, startOid)
        invalidate()
        val capturedEpoch = epoch
        fun current(): Boolean = !closed && epoch == capturedEpoch && state.value.binding == repo.binding &&
            (state.value.repository as? GitRead.Ready<GitRepository>)?.value == repo
        mutationBusy = true
        mutableState.value = state.value.copy(creationBusy = true, branchCreateBusy = true,
            branchCreation = GitBranchCreation.Creating(request), status = GitRead.Unselected, branches = GitRead.Unselected,
            worktrees = GitRead.Unselected, diff = GitRead.Unselected, commitSnapshot = GitRead.Unselected)
        val job = tasks.launch(start = CoroutineStart.LAZY) {
            var refreshFencedSelection = false
            try {
                when (val result = capability.createBranch(repo, request)) {
                    is GitCreateBranchResult.Created -> {
                        val receipt = result.receipt
                        if (!validBranchCreateReceipt(repo, request, receipt)) {
                            recordBranchCreateUnknown(repo, branchCreateUnknown("新建分支回执与请求不一致，结果未知，请刷新仓库。"), current())
                        } else if (current()) {
                            invalidate()
                            mutableState.value = state.value.copy(branchCreation = GitBranchCreation.Created(receipt),
                                status = GitRead.Ready(receipt.status.copy(changes = receipt.status.changes.toList())),
                                branches = GitRead.Ready(receipt.branches.toList()), worktrees = GitRead.Ready(receipt.worktrees.toList()),
                                diff = GitRead.Unselected, selectedBranchRef = null, newBranchStartRef = null, commitSnapshot = GitRead.Unselected)
                            refreshDiff()
                            refreshCommitSnapshot()
                        } else {
                            refreshFencedSelection = true
                        }
                    }
                    is GitCreateBranchResult.Rejected -> if (current()) {
                        mutableState.value = state.value.copy(branchCreation = GitBranchCreation.Failed(result.failure))
                        refreshData()
                    }
                    is GitCreateBranchResult.UnknownOutcome -> recordBranchCreateUnknown(repo, result.failure, current())
                }
            } catch (e: CancellationException) {
                recordBranchCreateUnknown(repo, branchCreateUnknown("新建分支已取消，结果未知，请刷新仓库。"), current())
                throw e
            } catch (_: Exception) {
                recordBranchCreateUnknown(repo, branchCreateUnknown("新建分支结果未知，请刷新仓库。"), current())
            } finally {
                mutationBusy = false
                if (!closed) mutableState.value = state.value.copy(creationBusy = false, branchCreateBusy = false)
                if (refreshFencedSelection && !closed) refreshCurrentCommonDir(repo)
            }
        }
        job.start()
    }

    private fun validBranchCreateReceipt(repo: GitRepository, request: GitCreateBranchRequest, receipt: GitCreateBranchReceipt): Boolean {
        val newRef = "refs/heads/" + request.branch
        return receipt.repository == repo && receipt.request == request &&
            receipt.status.branch == newRef && receipt.status.headOid == request.startPointOid &&
            receipt.status.changes.isEmpty() &&
            receipt.branches.singleOrNull { it.ref == newRef && !it.remote && it.current }?.oid == request.startPointOid &&
            (request.startPointRef == "HEAD" ||
                receipt.branches.any { it.ref == request.startPointRef && !it.remote && it.oid == request.startPointOid }) &&
            receipt.worktrees.any { it.path == repo.root && it.branch == newRef && it.headOid == request.startPointOid &&
                !it.detached && !it.bare && !it.prunable }
    }

    private fun branchCreateUnknown(message: String): GitFailure = GitFailure(GitFailureKind.CommandFailed, message)

    private fun recordBranchCreateUnknown(repo: GitRepository, failure: GitFailure, publish: Boolean) {
        val key = uncertaintyKey(repo)
        val safe = branchCreateUnknown("先前新建分支结果未知，请刷新仓库状态、分支和工作树。")
        uncertainBranchCreates[key] = BranchCreateUncertainty(safe)
        val selected = (state.value.repository as? GitRead.Ready<GitRepository>)?.value
        if (!closed && (publish || selected != null && uncertaintyKey(selected) == key)) {
            invalidate()
            mutableState.value = state.value.copy(branchCreation = GitBranchCreation.UnknownOutcome(if (publish) failure else safe),
                branchCreateNeedsRescan = true, status = GitRead.Unselected, branches = GitRead.Unselected,
                worktrees = GitRead.Unselected, diff = GitRead.Unselected)
        }
    }

    /** Refresh owns only reads; it never cancels a creation or releases its admission slot. */
    public fun refresh() {
        check(!closed)
        if (!state.value.canRefresh) return
        if (state.value.repository is GitRead.Ready) refreshData() else discover()
    }

    public fun selectDiff(request: GitDiffRequest) {
        check(!closed)
        mutableState.value = state.value.copy(diffRequest = request, diff = GitRead.Unselected)
        refreshDiff()
    }

    /**
     * Read the thread summary's Environment numbers: the local uncommitted-to-HEAD diff, expressed as
     * file/additions/deletions counts. This reuses the read-only diff capability with the resolved
     * HEAD commit as base; it never stages, checks out, commits or writes. It stays independent of the
     * page-selected [selectDiff] request so the two never clobber each other.
     */
    public fun refreshSummaryStats() {
        check(!closed)
        if (!state.value.canRefresh) return
        val repo = (state.value.repository as? GitRead.Ready<GitRepository>)?.value ?: return
        val status = (state.value.status as? GitRead.Ready<GitStatusSnapshot>)?.value ?: return
        if (status.headOid == null) {
            // Without a real HEAD commit there is no base to diff against; report no numbers, not zero.
            mutableState.value = state.value.copy(summaryStats = GitRead.Unselected)
            return
        }
        read(Query.SummaryStats, { it.copy(summaryStats = GitRead.Loading) },
            { backend.diff(repo, GitDiffRequest(GitDiffSource.BaseBranch, "HEAD")) },
            { snapshot -> mutableState.value = state.value.copy(summaryStats = GitRead.Ready(gitDiffStats(snapshot.patch))) },
            { s, e -> s.copy(summaryStats = GitRead.Failed(e)) })
    }

    /**
     * Reads the working-tree text for the given diff paths, only while the rich preview is on. Each
     * path is read at most once per owner incarnation, and a stale read is dropped by the same epoch
     * fence as the other queries. Reading never mutates the repository.
     */
    public fun refreshPreviewFiles(paths: Collection<String>) {
        if (closed) return
        val repo = (state.value.repository as? GitRead.Ready<GitRepository>)?.value ?: return
        val capturedEpoch = epoch
        paths.distinct().forEach { path ->
            if (path in previewJobs) return@forEach
            val known = state.value.previewFiles[path]
            if (known is GitPreviewFile.Ready || known is GitPreviewFile.Unavailable) return@forEach
            mutableState.value = state.value.copy(previewFiles = state.value.previewFiles + (path to GitPreviewFile.Loading))
            val job = tasks.launch(start = CoroutineStart.LAZY) {
                val result = try {
                    backend.readPreviewFile(repo, path)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    GitPreviewFile.Unavailable("读取预览失败，请重试。")
                }
                if (!closed && epoch == capturedEpoch) {
                    mutableState.value = state.value.copy(previewFiles = state.value.previewFiles + (path to result))
                }
            }
            previewJobs[path] = job
            job.invokeOnCompletion { if (previewJobs[path] === job) previewJobs.remove(path) }
            job.start()
        }
    }

    /** Readiness-only toggle; it never stages anything and is reset when the repository changes. */
    public fun setIncludeUnstaged(value: Boolean) {
        check(!closed)
        if (mutationBusy || state.value.includeUnstaged == value) return
        mutableState.value = state.value.copy(includeUnstaged = value)
    }

    public fun createWorktree(path: String, branch: String, startRef: String = "HEAD") {
        check(!closed)
        if (mutationBusy || !state.value.canCreateWorktree) return
        val repo = (state.value.repository as? GitRead.Ready<GitRepository>)?.value ?: return
        val capability = creator ?: return
        val request = GitCreateWorktreeRequest(repo.binding, path, branch, startRef)
        invalidate()
        val capturedEpoch = epoch
        fun current(): Boolean = !closed && epoch == capturedEpoch && state.value.binding == repo.binding
        mutationBusy = true
        mutableState.value = state.value.copy(creationBusy = true, creation = GitWorktreeCreation.Creating(request))
        val job = tasks.launch(start = CoroutineStart.LAZY) {
            try {
                val result = capability.createWorktree(repo, request)
                when (result) {
                    is GitCreateWorktreeResult.Created -> {
                        val receipt = result.receipt
                        if (receipt.repository != repo || receipt.request != request ||
                            receipt.worktree.branch != "refs/heads/$branch" ||
                            receipt.worktree.headOid != receipt.startOid ||
                            receipt.worktree !in receipt.worktrees || receipt.worktree.bare || receipt.worktree.detached || receipt.worktree.prunable) {
                            recordFailure(repo, unknownFailure("创建回执与请求不一致，结果未知，请刷新工作树列表。"), current())
                        } else if (current()) {
                            uncertainRepositories.remove(uncertaintyKey(repo))
                            // Fence all reads of the old selection before selecting the confirmed destination.
                            invalidate()
                            mutableState.value = GitState(binding = repo.binding, selectedCwd = receipt.worktree.path,
                                creationAvailable = available(repo.binding), creationBusy = true,
                                checkoutAvailable = switchAvailable(repo.binding), branchCreateAvailable = branchCreateAvailable(repo.binding),
                                commitAvailable = commitAvailable(repo.binding),
                                creation = GitWorktreeCreation.Created(receipt))
                            discover()
                        }
                    }
                    is GitCreateWorktreeResult.Failed -> recordFailure(repo, result.failure, current())
                }
            } catch (e: CancellationException) {
                recordFailure(repo, unknownFailure("创建已取消，结果未知，请刷新工作树列表。"), current())
                throw e
            } catch (_: Exception) {
                recordFailure(repo, unknownFailure("创建结果未知，请刷新工作树列表。"), current())
            } finally {
                mutationBusy = false
                if (!closed) mutableState.value = state.value.copy(creationBusy = false)
            }
        }
        job.start()
    }

    private fun unknownFailure(message: String): GitCreateWorktreeFailure =
        GitCreateWorktreeFailure(GitFailure(GitFailureKind.CommandFailed, message), needsRescan = true)

    private fun recordFailure(repo: GitRepository, failure: GitCreateWorktreeFailure, publish: Boolean) {
        val key = uncertaintyKey(repo)
        val safeFailure = unknownFailure("先前创建结果未知，请刷新工作树列表并检查实际目录和分支。")
        if (failure.needsRescan) uncertainRepositories[key] = safeFailure
        if (publish) mutableState.value = state.value.copy(creation = GitWorktreeCreation.Failed(failure),
            creationNeedsRescan = failure.needsRescan)
        else if (failure.needsRescan && !closed) {
            val selected = (state.value.repository as? GitRead.Ready<GitRepository>)?.value
            if (selected != null && uncertaintyKey(selected) == key)
                mutableState.value = state.value.copy(creation = GitWorktreeCreation.Failed(safeFailure), creationNeedsRescan = true)
        }
    }

    private fun discover() {
        val binding = state.value.binding ?: return
        val cwd = state.value.selectedCwd ?: return
        read(Query.Repository, { it.copy(repository = GitRead.Loading, status = GitRead.Unselected,
            branches = GitRead.Unselected, worktrees = GitRead.Unselected, diff = GitRead.Unselected,
            summaryStats = GitRead.Unselected, commitSnapshot = GitRead.Unselected) },
            { backend.discover(binding, cwd) },
            { repository ->
                if (repository.binding != binding) throw GitReadException(GitFailure(GitFailureKind.Unavailable, "Git 服务返回了其他 Host 的仓库。"))
                val pending = uncertainRepositories[uncertaintyKey(repository)]
                val checkoutPending = uncertainCheckouts[uncertaintyKey(repository)]
                val branchCreatePending = uncertainBranchCreates[uncertaintyKey(repository)]
                mutableState.value = state.value.copy(repository = GitRead.Ready(repository),
                    creationNeedsRescan = pending != null,
                    checkoutNeedsRescan = checkoutPending != null,
                    branchCreateNeedsRescan = branchCreatePending != null,
                    commitNeedsRescan = commitUnknownFor(repository),
                    commit = if (commitUnknownFor(repository)) GitCommit.UnknownOutcome(commitUnknownFailure()) else state.value.commit,
                    checkout = checkoutPending?.let { GitBranchCheckout.UnknownOutcome(it.failure) } ?: state.value.checkout,
                    branchCreation = branchCreatePending?.let { GitBranchCreation.UnknownOutcome(it.failure) } ?: state.value.branchCreation,
                    creation = pending?.let { GitWorktreeCreation.Failed(it) } ?: state.value.creation)
                refreshData()
            }, { s, e -> s.copy(repository = GitRead.Failed(e)) })
    }

    private fun refreshData() {
        val repo = (state.value.repository as? GitRead.Ready<GitRepository>)?.value ?: return
        val checkoutScan = uncertainCheckouts[uncertaintyKey(repo)].takeIf { !mutationBusy && state.value.checkoutNeedsRescan }
        val branchCreateScan = uncertainBranchCreates[uncertaintyKey(repo)].takeIf { !mutationBusy && state.value.branchCreateNeedsRescan }
        val scanEpoch = epoch
        var scannedStatus: GitStatusSnapshot? = null
        var scannedBranches: List<GitBranch>? = null
        var scannedWorktrees: List<GitWorktree>? = null
        fun resolveCheckoutScan() {
            val status = scannedStatus ?: return
            val refs = scannedBranches ?: return
            val trees = scannedWorktrees ?: return
            if (checkoutScan == null || mutationBusy || scanEpoch != epoch ||
                uncertainCheckouts[uncertaintyKey(repo)] !== checkoutScan) return
            if (status.branch != null && status.headOid != null &&
                refs.singleOrNull { it.ref == status.branch && it.current && !it.remote }?.oid != status.headOid) return
            if (trees.none { it.path == repo.root && it.branch == status.branch && it.headOid == status.headOid && !it.bare }) return
            uncertainCheckouts.remove(uncertaintyKey(repo))
            mutableState.value = state.value.copy(checkoutNeedsRescan = false, checkout = GitBranchCheckout.Rescanned,
                selectedBranchRef = null)
        }
        fun resolveBranchCreateScan() {
            val status = scannedStatus ?: return
            val refs = scannedBranches ?: return
            val trees = scannedWorktrees ?: return
            if (branchCreateScan == null || mutationBusy || scanEpoch != epoch ||
                uncertainBranchCreates[uncertaintyKey(repo)] !== branchCreateScan) return
            if (status.branch != null && status.headOid != null &&
                refs.singleOrNull { it.ref == status.branch && it.current && !it.remote }?.oid != status.headOid) return
            if (trees.none { it.path == repo.root && it.branch == status.branch && it.headOid == status.headOid && !it.bare }) return
            uncertainBranchCreates.remove(uncertaintyKey(repo))
            mutableState.value = state.value.copy(branchCreateNeedsRescan = false, branchCreation = GitBranchCreation.Rescanned,
                newBranchStartRef = null)
        }
        read(Query.Status, { it.copy(status = GitRead.Loading) }, { backend.status(repo).let { it.copy(changes = it.changes.toList()) } },
            { mutableState.value = state.value.copy(status = GitRead.Ready(it)); scannedStatus = it; resolveCheckoutScan(); resolveBranchCreateScan() },
            { s, e -> s.copy(status = GitRead.Failed(e)) })
        read(Query.Branches, { it.copy(branches = GitRead.Loading) }, { backend.branches(repo).toList() },
            { mutableState.value = state.value.copy(branches = GitRead.Ready(it)); scannedBranches = it; resolveCheckoutScan(); resolveBranchCreateScan() },
            { s, e -> s.copy(branches = GitRead.Failed(e)) })
        val scanUncertainty = uncertainRepositories[uncertaintyKey(repo)].takeIf { !mutationBusy && state.value.creationNeedsRescan }
        read(Query.Worktrees, { it.copy(worktrees = GitRead.Loading) }, { backend.worktrees(repo).toList() },
            {
                val resolved = scanUncertainty != null && uncertainRepositories[uncertaintyKey(repo)] === scanUncertainty
                if (resolved) uncertainRepositories.remove(uncertaintyKey(repo))
                mutableState.value = state.value.copy(worktrees = GitRead.Ready(it),
                    creationNeedsRescan = if (resolved) false else state.value.creationNeedsRescan)
                scannedWorktrees = it
                resolveCheckoutScan()
                resolveBranchCreateScan()
            }, { s, e -> s.copy(worktrees = GitRead.Failed(e)) })
        refreshDiff()
        refreshCommitSnapshot()
    }

    /** Stronger rescan than checkout: the exact worktree index plus coherent HEAD/status/refs/inventory. */
    private fun refreshCommitSnapshot() {
        val repo = (state.value.repository as? GitRead.Ready<GitRepository>)?.value ?: return
        val capability = committer?.takeIf { it.canCommit(repo.binding) } ?: return
        val pending = uncertainCommits[indexKey(repo)].takeIf { !mutationBusy }
        data class Scan(val snapshot: GitCommitSnapshot, val status: GitStatusSnapshot, val refs: List<GitBranch>, val trees: List<GitWorktree>)
        read(Query.Commit, { it.copy(commitSnapshot = GitRead.Loading) }, {
            val first = capability.readCommitSnapshot(repo)
            val status = backend.status(repo)
            val refs = backend.branches(repo).toList()
            val trees = backend.worktrees(repo).toList()
            val last = capability.readCommitSnapshot(repo)
            if (first.repository != repo || last.repository != repo || first.indexDigest != last.indexDigest ||
                first.stagedCount != last.stagedCount || first.blockedReason != last.blockedReason ||
                first.status.branch != last.status.branch || first.status.headOid != last.status.headOid ||
                status.branch != last.status.branch || status.headOid != last.status.headOid ||
                last.status.branch != null && last.status.headOid != null &&
                    refs.singleOrNull { it.ref == last.status.branch && it.current && !it.remote }?.oid != last.status.headOid ||
                trees.none { it.path == repo.root && it.branch == last.status.branch && it.headOid == last.status.headOid && !it.bare })
                throw GitReadException(GitFailure(GitFailureKind.StaleSnapshot, "暂存区、HEAD 与仓库读取不一致，请刷新。"))
            Scan(last.copy(status = last.status.copy(changes = last.status.changes.toList())), status, refs, trees)
        }, { scan ->
            val resolved = pending != null && !mutationBusy && uncertainCommits[indexKey(repo)] === pending
            if (resolved) uncertainCommits.remove(indexKey(repo))
            mutableState.value = state.value.copy(commitSnapshot = GitRead.Ready(scan.snapshot),
                commitNeedsRescan = commitUnknownFor(repo),
                commit = if (resolved && !commitUnknownFor(repo)) GitCommit.Rescanned else state.value.commit)
        }, { s, e -> s.copy(commitSnapshot = GitRead.Failed(e)) })
    }

    private fun refreshDiff() {
        if (!state.value.canRefresh) return
        val repo = (state.value.repository as? GitRead.Ready<GitRepository>)?.value ?: return
        val request = state.value.diffRequest
        // Rich-preview text is read from the working tree and cached per path for this incarnation.
        // A diff whose text actually changed must drop those previews instead of keeping text that
        // describes the previous content; an unchanged refresh keeps the cache.
        val previousPatch = (state.value.diff as? GitRead.Ready)?.value?.patch
        read(Query.Diff, { it.copy(diff = GitRead.Loading) }, { backend.diff(repo, request) },
            { snapshot -> mutableState.value = state.value.copy(diff = GitRead.Ready(snapshot),
                previewFiles = if (previousPatch != snapshot.patch) emptyMap() else state.value.previewFiles) },
            { s, e -> s.copy(diff = GitRead.Failed(e)) })
    }

    private fun <T> read(
        key: Query, loading: (GitState) -> GitState, operation: suspend () -> T,
        success: (T) -> Unit, failure: (GitState, GitFailure) -> GitState,
    ) {
        val capturedEpoch = epoch
        val id = (requests[key] ?: 0L) + 1
        requests[key] = id
        jobs.remove(key)?.cancel()
        mutableState.value = loading(state.value)
        fun current(): Boolean = !closed && capturedEpoch == epoch && requests[key] == id
        val job = tasks.launch(start = CoroutineStart.LAZY) {
            try {
                val value = operation()
                if (current()) success(value)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (current()) mutableState.value = failure(state.value,
                    (e as? GitReadException)?.failure ?: GitFailure(GitFailureKind.CommandFailed, "Git 读取失败，请重试。"))
            }
        }
        jobs[key] = job
        job.invokeOnCompletion { if (jobs[key] === job) jobs.remove(key) }
        job.start()
    }

    private fun invalidate() {
        epoch++
        requests.clear()
        val previous = jobs.values.toList()
        jobs.clear()
        previous.forEach { it.cancel() }
        val previews = previewJobs.values.toList()
        previewJobs.clear()
        previews.forEach { it.cancel() }
    }

    override fun close() {
        if (closed) return
        closed = true
        invalidate()
        owner.cancel()
        mutableState.value = GitState()
    }
}
