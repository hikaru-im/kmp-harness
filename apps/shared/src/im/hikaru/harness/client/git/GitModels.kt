package im.hikaru.harness.client.git

import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.harness.client.account.MemberIdentity
import im.hikaru.harness.client.connection.SessionSummary

/** LocalReadOnly must only be granted for an online, verified in-process Host. */
public enum class GitAccess { LocalReadOnly, RemoteUnavailable, Disconnected }

public data class GitBinding(
    val identity: MemberIdentity?,
    val hostId: HostId,
    val generation: Long,
    val access: GitAccess = GitAccess.RemoteUnavailable,
) {
    init { require(generation >= 0) }
    public fun sameHost(other: GitBinding): Boolean = identity == other.identity && hostId == other.hostId
}

public data class GitRepository(
    val binding: GitBinding,
    val root: String,
    val commonDir: String,
    val gitDir: String,
)

public enum class GitFailureKind {
    Unavailable, NotRepository, InvalidRequest, CommandFailed, Timeout, OutputTooLarge, MalformedOutput, StaleSnapshot,
}

public data class GitFailure(val kind: GitFailureKind, val message: String)
public class GitReadException(public val failure: GitFailure) : RuntimeException(failure.message)

/** Working-tree text for the diff rich preview. It is read from the file, never rebuilt from a patch. */
public sealed interface GitPreviewFile {
    public data object Loading : GitPreviewFile
    public data class Ready(val text: String) : GitPreviewFile
    public data class Unavailable(val reason: String) : GitPreviewFile {
        init { require(reason.isNotBlank()) }
    }
}

/** Refresh clears data: failures must never masquerade as a clean repository. */
public sealed interface GitRead<out T> {
    public data object Unselected : GitRead<Nothing>
    public data object Loading : GitRead<Nothing>
    public data class Ready<T>(val value: T) : GitRead<T>
    public data class Failed(val failure: GitFailure) : GitRead<Nothing>
}

public data class GitChange(
    val path: String,
    val originalPath: String? = null,
    val indexStatus: Char,
    val worktreeStatus: Char,
) {
    public val untracked: Boolean get() = indexStatus == '?' && worktreeStatus == '?'
    public val conflicted: Boolean get() = "$indexStatus$worktreeStatus" in setOf("DD", "AU", "UD", "UA", "DU", "AA", "UU")
    public val staged: Boolean get() = !untracked && indexStatus != ' ' && indexStatus != '!'
    public val unstaged: Boolean get() = !untracked && worktreeStatus != ' ' && worktreeStatus != '!'
}

public data class GitStatusSnapshot(
    val changes: List<GitChange>,
    val branch: String?,
    val headOid: String?,
) {
    public val unborn: Boolean get() = branch != null && headOid == null
    public val detached: Boolean get() = branch == null && headOid != null
    public val stagedCount: Int get() = changes.count { it.staged }
    public val unstagedCount: Int get() = changes.count { it.unstaged }
    public val untrackedCount: Int get() = changes.count { it.untracked }
}

public data class GitBranch(val ref: String, val oid: String, val current: Boolean, val remote: Boolean)
public data class GitWorktree(
    val path: String, val headOid: String?, val branch: String?, val detached: Boolean = false,
    val bare: Boolean = false, val locked: Boolean = false, val lockReason: String? = null,
    val prunable: Boolean = false, val pruneReason: String? = null,
)

/**
 * The sessions whose cwd is exactly this worktree directory, newest first. Backs Codex's
 * `settings.worktrees.row.conversations` (已知对话) list. A trailing slash is ignored on both sides;
 * a blank or unknown cwd never matches, so an unrelated session is never attributed to a worktree.
 * Read-only — callers supply the already-loaded session list; nothing is created or written.
 */
public fun worktreeConversations(root: String, sessions: List<SessionSummary>): List<SessionSummary> {
    val dir = root.trimEnd('/')
    if (dir.isEmpty()) return emptyList()
    return sessions.filter { it.cwd?.trimEnd('/') == dir }
        .sortedWith(compareByDescending<SessionSummary> { it.createdAt }.thenBy { it.id.value })
}

/** Explicit user destination and new branch; startRef is HEAD or a full ref from branches(). */
public data class GitCreateWorktreeRequest(
    val binding: GitBinding,
    val path: String,
    val branch: String,
    val startRef: String = "HEAD",
)

/** Only issued after worktree list confirms the actual path, branch and HEAD. */
public data class GitCreateWorktreeReceipt(
    val repository: GitRepository,
    val request: GitCreateWorktreeRequest,
    val startOid: String,
    val worktree: GitWorktree,
    val worktrees: List<GitWorktree>,
)

public data class GitCreateWorktreeFailure(
    val failure: GitFailure,
    val needsRescan: Boolean = false,
)

public sealed interface GitCreateWorktreeResult {
    public data class Created(val receipt: GitCreateWorktreeReceipt) : GitCreateWorktreeResult
    public data class Failed(val failure: GitCreateWorktreeFailure) : GitCreateWorktreeResult
}

public sealed interface GitWorktreeCreation {
    public data object Idle : GitWorktreeCreation
    public data class Creating(val request: GitCreateWorktreeRequest) : GitWorktreeCreation
    public data class Created(val receipt: GitCreateWorktreeReceipt) : GitWorktreeCreation
    public data class Failed(val failure: GitCreateWorktreeFailure) : GitWorktreeCreation
}

/** Optional local capability, separate from the compatible read-only GitBackend. */
public interface GitWorktreeCreator {
    public fun canCreateWorktree(binding: GitBinding): Boolean
    public suspend fun createWorktree(repository: GitRepository, request: GitCreateWorktreeRequest): GitCreateWorktreeResult
}

/** Expected HEAD includes a null branch only for an explicitly observed detached HEAD. */
public data class GitBranchCheckoutRequest(
    val binding: GitBinding,
    val root: String,
    val expectedBranch: String?,
    val expectedHeadOid: String,
    val targetRef: String,
    val targetOid: String,
)

/** Issued only after rereading HEAD, clean status, local refs and worktree inventory. */
public data class GitBranchCheckoutReceipt(
    val repository: GitRepository,
    val request: GitBranchCheckoutRequest,
    val status: GitStatusSnapshot,
    val branches: List<GitBranch>,
    val worktrees: List<GitWorktree>,
)

public sealed interface GitBranchCheckoutResult {
    public data class Switched(val receipt: GitBranchCheckoutReceipt) : GitBranchCheckoutResult
    public data class Rejected(val failure: GitFailure) : GitBranchCheckoutResult
    public data class UnknownOutcome(val failure: GitFailure) : GitBranchCheckoutResult
}

public sealed interface GitBranchCheckout {
    public data object Idle : GitBranchCheckout
    public data class Switching(val request: GitBranchCheckoutRequest) : GitBranchCheckout
    public data class Switched(val receipt: GitBranchCheckoutReceipt) : GitBranchCheckout
    public data class Failed(val failure: GitFailure) : GitBranchCheckout
    public data class UnknownOutcome(val failure: GitFailure) : GitBranchCheckout
    public data object Rescanned : GitBranchCheckout
}

/** Optional capability; read-only adapters remain source compatible. */
public interface GitBranchSwitcher {
    public fun canSwitchBranch(binding: GitBinding): Boolean
    public suspend fun switchBranch(repository: GitRepository, request: GitBranchCheckoutRequest): GitBranchCheckoutResult
}

/** Create-and-checkout of one new branch; failIfExists is always enforced and no force/reset/amend is used. */
public data class GitCreateBranchRequest(
    val binding: GitBinding,
    val root: String,
    val expectedBranch: String?,
    val expectedHeadOid: String,
    val branch: String,
    val startPointRef: String,
    val startPointOid: String,
)

/** Issued only after rereading HEAD, clean status, local refs and worktree inventory. */
public data class GitCreateBranchReceipt(
    val repository: GitRepository,
    val request: GitCreateBranchRequest,
    val status: GitStatusSnapshot,
    val branches: List<GitBranch>,
    val worktrees: List<GitWorktree>,
)

public sealed interface GitCreateBranchResult {
    public data class Created(val receipt: GitCreateBranchReceipt) : GitCreateBranchResult
    public data class Rejected(val failure: GitFailure) : GitCreateBranchResult
    public data class UnknownOutcome(val failure: GitFailure) : GitCreateBranchResult
}

public sealed interface GitBranchCreation {
    public data object Idle : GitBranchCreation
    public data class Creating(val request: GitCreateBranchRequest) : GitBranchCreation
    public data class Created(val receipt: GitCreateBranchReceipt) : GitBranchCreation
    public data class Failed(val failure: GitFailure) : GitBranchCreation
    public data class UnknownOutcome(val failure: GitFailure) : GitBranchCreation
    public data object Rescanned : GitBranchCreation
}

/** Optional capability; extends existing-branch switching with a fail-if-exists create-and-checkout. */
public interface GitBranchCreator {
    public fun canCreateBranch(binding: GitBinding): Boolean
    public suspend fun createBranch(repository: GitRepository, request: GitCreateBranchRequest): GitCreateBranchResult
}

/**
 * Conservative local branch name rule shared by the controller gate and the backend preflight. It is
 * narrower than git check-ref-format: the backend still lets Git reject anything it considers invalid.
 */
public fun validLocalBranchName(name: String): Boolean {
    if (name.isBlank() || name == "HEAD" || name.startsWith("-") || name.startsWith("refs/")) return false
    if (name.any { it.isWhitespace() || it.code < 32 || it.code == 127 }) return false
    if (listOf("..", "@{", "~", "^", ":", "?", "*", "[", "\\").any { it in name }) return false
    if (name.split('/').any { it.isEmpty() || it.startsWith('.') || it.endsWith('.') || it.endsWith(".lock") }) return false
    return true
}

/** C1 is index-only: no automatic staging, amend, push, hooks or signing. */
public enum class GitCommitPolicy { HooksDisabledSigningDisabled }

/** Normalized object body; a trailing LF is part of the accepted message. */
public fun normalizeGitCommitMessage(value: String): String? {
    if ('\u0000' in value) return null
    val normalized = value.replace("\r\n", "\n").replace('\r', '\n').trimEnd('\n')
    val body = normalized + "\n"
    return if (normalized.isBlank() || normalized.length > 32_768 || body.encodeToByteArray().size > 65_536) null else body
}

/** A real, coherent index/HEAD read. blockedReason is a known noncommittable state, not an empty proof. */
public data class GitCommitSnapshot(
    val repository: GitRepository,
    val status: GitStatusSnapshot,
    val indexDigest: String,
    val stagedCount: Int,
    val blockedReason: String? = null,
)

public data class GitCommitRequest(
    val snapshot: GitCommitSnapshot,
    val message: String,
    val policy: GitCommitPolicy = GitCommitPolicy.HooksDisabledSigningDisabled,
    /**
     * When true the backend runs one stage step (`add -A`) over the whole worktree before committing,
     * mirroring the original dialog's "include unstaged changes" option. The committed tree is then
     * verified against the post-stage index, not [snapshot].
     */
    val includeUnstaged: Boolean = false,
)

/** Verified object and post-command reads, never inferred from exit code or stdout alone. */
public data class GitCommitReceipt(
    val repository: GitRepository,
    val request: GitCommitRequest,
    val commitOid: String,
    val parentOid: String,
    val treeOid: String,
    val treeDigest: String,
    val message: String,
    val snapshot: GitCommitSnapshot,
    val branches: List<GitBranch>,
    val worktrees: List<GitWorktree>,
    /** True only when this commit was preceded by the request's stage step. */
    val stagedByRequest: Boolean = false,
    /** Index digest the committed tree must equal: the request snapshot, or the post-stage index. */
    val stagedIndexDigest: String = treeDigest,
)

public sealed interface GitCommitResult {
    public data class Committed(val receipt: GitCommitReceipt) : GitCommitResult
    public data class Rejected(val failure: GitFailure) : GitCommitResult
    public data class UnknownOutcome(val failure: GitFailure) : GitCommitResult
}

public sealed interface GitCommit {
    public data object Idle : GitCommit
    public data class Submitting(val request: GitCommitRequest) : GitCommit
    public data class Verified(val receipt: GitCommitReceipt) : GitCommit
    public data class Rejected(val failure: GitFailure) : GitCommit
    public data class UnknownOutcome(val failure: GitFailure) : GitCommit
    public data object Rescanned : GitCommit
}

/** Optional capability; GitBackend stays compatible with read-only implementations. */
public interface GitCommitter {
    public fun canCommit(binding: GitBinding): Boolean
    public suspend fun readCommitSnapshot(repository: GitRepository): GitCommitSnapshot
    public suspend fun commit(repository: GitRepository, request: GitCommitRequest): GitCommitResult
}

public enum class GitDiffSource { WorkingTree, Index, BaseBranch }
public data class GitDiffRequest(
    val source: GitDiffSource = GitDiffSource.WorkingTree,
    val baseRef: String? = null,
    /**
     * Codex's `codex.review.whitespace.hide`（隐藏空白字符，"hide whitespace-only diff changes"）:
     * the host computes the diff ignoring whitespace, exactly as Codex sends `hideWhitespace` with its
     * `review-diff` request. It is a diff input, not a client-side rendering filter, so toggling it
     * re-reads the patch rather than filtering the text we already have.
     */
    val hideWhitespace: Boolean = false,
    /**
     * `codex.review.loadFullFiles.enable`（加载完整文件，"load full file contents for partial diffs"）:
     * show the whole file for a partial diff instead of only the hunks.
     *
     * ASAR evidence (read-only): it is a `be.CheckboxItem` in the diff options menu, and the diff
     * renderer (`code-diff-*`) upgrades only a file whose diff `isPartial`, fetching the previous/next
     * objects by OID through the host (`fullContentLoader`, `maxObjectBytes`, falling back when an
     * object is too large) and computing the full diff on the client. This harness carries no per-file
     * object ids and no client-side line differ, so the same *observable* result is produced by asking
     * the host for a full-context diff instead. Mechanism divergence, documented in
     * `reverse-engineering/load-full-files-contract-*.md`: host re-read with full context vs. the
     * original's client-side object load. Toggling re-reads the patch.
     */
    val loadFullFiles: Boolean = false,
)
/** HEAD is fenced across this read. This is not an atomic index/filesystem snapshot. */
public data class GitDiffSnapshot(val request: GitDiffRequest, val patch: String, val headOid: String?)
/**
 * Aggregated counts for one diff, as the thread summary Environment "Changes" row shows. Values are
 * counted from the patch text the backend already returned; they are never estimated or synthesized.
 */
public data class GitDiffStats(val fileCount: Int, val additions: Int, val deletions: Int) {
    init {
        require(fileCount >= 0 && additions >= 0 && deletions >= 0)
    }
    public val lineCount: Int get() = additions + deletions
}

public data class GitState(
    val binding: GitBinding? = null,
    val selectedCwd: String? = null,
    val repository: GitRead<GitRepository> = GitRead.Unselected,
    val status: GitRead<GitStatusSnapshot> = GitRead.Unselected,
    val branches: GitRead<List<GitBranch>> = GitRead.Unselected,
    val worktrees: GitRead<List<GitWorktree>> = GitRead.Unselected,
    val diffRequest: GitDiffRequest = GitDiffRequest(),
    val diff: GitRead<GitDiffSnapshot> = GitRead.Unselected,
    /** Independent of [diff]: the Environment summary reads its own HEAD-based numbers. */
    val summaryStats: GitRead<GitDiffStats> = GitRead.Unselected,
    /** Rich-preview text per diff path, read lazily only while the preview toggle is on. */
    val previewFiles: Map<String, GitPreviewFile> = emptyMap(),
    val creationAvailable: Boolean = false,
    val creationBusy: Boolean = false,
    val creation: GitWorktreeCreation = GitWorktreeCreation.Idle,
    val creationNeedsRescan: Boolean = false,
    val checkoutAvailable: Boolean = false,
    val selectedBranchRef: String? = null,
    val checkoutBusy: Boolean = false,
    val checkout: GitBranchCheckout = GitBranchCheckout.Idle,
    val checkoutNeedsRescan: Boolean = false,
    val branchCreateAvailable: Boolean = false,
    val newBranchName: String = "",
    val newBranchStartRef: String? = null,
    val branchCreateBusy: Boolean = false,
    val branchCreation: GitBranchCreation = GitBranchCreation.Idle,
    val branchCreateNeedsRescan: Boolean = false,
    val commitAvailable: Boolean = false,
    val commitSnapshot: GitRead<GitCommitSnapshot> = GitRead.Unselected,
    val commitBusy: Boolean = false,
    val commit: GitCommit = GitCommit.Idle,
    val commitNeedsRescan: Boolean = false,
    val includeUnstaged: Boolean = false,
) {
    public val unavailableReason: String? get() = when (binding?.access) {
        null -> "尚未绑定编程 Host。"
        GitAccess.RemoteUnavailable -> "此远端 Host 尚未提供 Git 服务。"
        GitAccess.Disconnected -> "Host 已断开，Git 数据不可用。"
        GitAccess.LocalReadOnly -> null
    }
    public val canRefresh: Boolean get() = unavailableReason == null && !selectedCwd.isNullOrBlank()
    public val canCreateWorktree: Boolean get() = creationAvailable && unavailableReason == null &&
        repository is GitRead.Ready && repository.value.binding == binding && !creationBusy && !creationNeedsRescan &&
        !checkoutNeedsRescan && !commitNeedsRescan && !branchCreateNeedsRescan
    public val checkoutDisabledReason: String? get() {
        unavailableReason?.let { return it }
        if (!checkoutAvailable) return "此 Host 未提供本地分支切换能力。"
        if (creationBusy || checkoutBusy || commitBusy || branchCreateBusy) return "正在执行 Git 操作。"
        if (creationNeedsRescan || checkoutNeedsRescan || commitNeedsRescan || branchCreateNeedsRescan)
            return "结果未知，请先刷新仓库状态、分支和工作树及原工作树暂存区。"
        val repo = (repository as? GitRead.Ready<GitRepository>)?.value
        if (repo == null || repo.binding != binding) return "请先读取仓库。"
        val current = (status as? GitRead.Ready<GitStatusSnapshot>)?.value ?: return "请先读取真实仓库状态。"
        if (current.headOid == null) return "尚无提交，无法切换分支。"
        if (current.changes.isNotEmpty()) return "工作树、暂存区或未跟踪文件非干净，请先自行处理。"
        val refs = (branches as? GitRead.Ready<List<GitBranch>>)?.value ?: return "请先读取分支。"
        val target = refs.singleOrNull { it.ref == selectedBranchRef } ?: return "请选择已有本地分支。"
        if (target.remote || !target.ref.startsWith("refs/heads/")) return "仅支持已有本地分支。"
        if (target.current || target.ref == current.branch) return "所选分支已经是当前分支。"
        return null
    }
    public val canSwitchBranch: Boolean get() = checkoutDisabledReason == null
    /** Create-and-checkout gate; a same-name local branch always blocks, and detached HEAD still allows HEAD as start. */
    public val branchCreateDisabledReason: String? get() {
        unavailableReason?.let { return it }
        if (!branchCreateAvailable) return "此 Host 未提供本机新建分支能力。"
        if (creationBusy || checkoutBusy || commitBusy || branchCreateBusy) return "正在执行 Git 操作。"
        if (creationNeedsRescan || checkoutNeedsRescan || commitNeedsRescan || branchCreateNeedsRescan)
            return "结果未知，请先刷新仓库状态、分支和工作树及原工作树暂存区。"
        val repo = (repository as? GitRead.Ready<GitRepository>)?.value
        if (repo == null || repo.binding != binding) return "请先读取仓库。"
        val current = (status as? GitRead.Ready<GitStatusSnapshot>)?.value ?: return "请先读取真实仓库状态。"
        if (current.headOid == null) return "尚无提交，无法新建分支。"
        if (current.changes.isNotEmpty()) return "工作树、暂存区或未跟踪文件非干净，请先自行处理。"
        val name = newBranchName.trim()
        if (name.isBlank()) return "请输入新分支名称。"
        if (!validLocalBranchName(name)) return "分支名称不是有效的本地引用。"
        val refs = (branches as? GitRead.Ready<List<GitBranch>>)?.value ?: return "请先读取分支。"
        if (refs.any { !it.remote && it.ref == "refs/heads/$name" }) return "同名本地分支已存在。"
        val start = newBranchStartRef
        if (start == null || start == "HEAD") return null
        val base = refs.singleOrNull { it.ref == start && !it.remote && it.ref.startsWith("refs/heads/") }
            ?: return "请选择一个已有本地分支作为起点。"
        if (base.oid.isBlank()) return "起点分支没有可用提交。"
        return null
    }
    public val canCreateBranch: Boolean get() = branchCreateDisabledReason == null
    public val commitDisabledReason: String? get() {
        unavailableReason?.let { return it }
        if (!commitAvailable) return "此 Host 未提供本机提交能力。"
        if (creationBusy || checkoutBusy || commitBusy || branchCreateBusy) return "正在执行 Git 操作。"
        if (creationNeedsRescan || checkoutNeedsRescan || commitNeedsRescan || branchCreateNeedsRescan)
            return "结果未知，请先刷新原工作树暂存区与仓库状态。"
        val repo = (repository as? GitRead.Ready<GitRepository>)?.value ?: return "请先读取仓库。"
        if (repo.binding != binding) return "仓库绑定已变化，请重新读取。"
        val snapshot = (commitSnapshot as? GitRead.Ready<GitCommitSnapshot>)?.value ?: return "请先读取真实暂存区。"
        if (snapshot.repository != repo) return "暂存区读取已过期。"
        snapshot.blockedReason?.let { return it }
        if (snapshot.status.branch == null || snapshot.status.headOid == null) return "仅支持已有本地分支上的提交。"
        if (snapshot.stagedCount <= 0) {
            // With the stage step enabled an empty index is allowed as long as the worktree has
            // something to stage; the backend still rejects a request that stages nothing.
            if (!includeUnstaged) return "没有已暂存变更；请使用外部 Git 暂存，或勾选「包含未暂存变更」。"
            if (snapshot.status.changes.isEmpty()) return "没有可提交的变更。"
        }
        return null
    }
    public val canCommit: Boolean get() = commitDisabledReason == null
}

/** Read-only by construction. No checkout, init, create, delete or shell command entry point. */
public interface GitBackend {
    public suspend fun discover(binding: GitBinding, cwd: String): GitRepository
    public suspend fun status(repository: GitRepository): GitStatusSnapshot
    public suspend fun branches(repository: GitRepository): List<GitBranch>
    public suspend fun worktrees(repository: GitRepository): List<GitWorktree>
    public suspend fun diff(repository: GitRepository, request: GitDiffRequest): GitDiffSnapshot
    /**
     * Read a repository-relative file for the diff rich preview. Backends that cannot read files
     * return [GitPreviewFile.Unavailable] so the card falls back to its diff text.
     */
    public suspend fun readPreviewFile(repository: GitRepository, path: String): GitPreviewFile =
        GitPreviewFile.Unavailable("当前 Git 后端未提供文件读取。")
}

/**
 * Dedicated nested-content reading required before any destructive worktree operation. A partial or
 * truncated scan must not be interpreted as "nothing to lose", and a clean status alone is not an
 * absence proof for nested repositories, submodules or ignored content.
 */
public data class GitWorktreeContent(
    val root: String,
    val trackedChanges: Int,
    val untrackedEntries: Int,
    val ignoredEntries: Int,
    val submodules: List<String> = emptyList(),
    val nestedRepositories: List<String> = emptyList(),
    val scannedEntries: Int = 0,
    val complete: Boolean = false,
)

/** Optional capability; read-only adapters stay source compatible. */
public interface GitWorktreeContentProbe {
    public fun canProbeWorktreeContent(binding: GitBinding): Boolean
    public suspend fun probeWorktreeContent(repository: GitRepository, root: String): GitWorktreeContent
}

/** Expected identity captured in the plan and re-verified immediately before dispatch. */
public data class GitRemoveWorktreeRequest(
    val binding: GitBinding,
    val root: String,
    val expectedHeadOid: String,
    val expectedBranch: String?,
)

/** Only issued after the worktree list no longer contains the removed path. */
public data class GitRemoveWorktreeReceipt(
    val repository: GitRepository,
    val request: GitRemoveWorktreeRequest,
    val worktrees: List<GitWorktree>,
)

public sealed interface GitRemoveWorktreeResult {
    public data class Removed(val receipt: GitRemoveWorktreeReceipt) : GitRemoveWorktreeResult
    public data class Rejected(val failure: GitFailure) : GitRemoveWorktreeResult
    public data class UnknownOutcome(val failure: GitFailure) : GitRemoveWorktreeResult
}

/** Optional capability. Never force, never prune, never delete the main worktree. */
public interface GitWorktreeRemover {
    public fun canRemoveWorktree(binding: GitBinding): Boolean
    public suspend fun removeWorktree(repository: GitRepository, request: GitRemoveWorktreeRequest): GitRemoveWorktreeResult
}
