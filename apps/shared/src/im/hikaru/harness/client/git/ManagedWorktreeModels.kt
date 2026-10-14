package im.hikaru.harness.client.git

import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.harness.client.account.MemberIdentity
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

/** null identity is a distinct anonymous partition, never the current remote account. */
@Serializable
public data class ManagedWorktreeOwner(
    val identity: MemberIdentity?, val hostId: HostId, val homeNamespace: String,
)

@Serializable
public enum class ManagedRegistrationPhase { Prepared, Verified, Registered, UnknownOutcome, Rejected }

/** Creation evidence only. Current branch/HEAD always comes from Git. */
@Serializable
public data class ManagedWorktreeEvidence(val targetGitDir: String, val startOid: String, val branch: String)

@Serializable
public data class ManagedWorktreeIntent(
    val operationId: String, val commonDir: String, val sourceRoot: String, val sourceGitDir: String,
    val root: String, val branch: String, val startRef: String, val createdAt: Long,
    val phase: ManagedRegistrationPhase = ManagedRegistrationPhase.Prepared,
    val evidence: ManagedWorktreeEvidence? = null,
)

@Serializable
public data class ManagedWorktreeSnapshot(
    val owner: ManagedWorktreeOwner, val revision: Long = 0, val intents: List<ManagedWorktreeIntent> = emptyList(),
)

public enum class ManagedOwnership { Managed, UnknownOwnership, FingerprintMismatch, Missing }
public enum class ManagedProtectionAvailability { Unknown, Unavailable }
public data class ManagedProtection(val availability: ManagedProtectionAvailability, val reason: String)

/** Two independent header reads, never an absence proof or a deletion capability. */
public enum class ManagedSessionObservationStatus { Observed, Unknown, Unavailable }
public enum class ManagedSessionSource { Live, Durable }
public enum class ManagedSessionIssueKind {
    Unsupported, SourceUnavailable, SourceFailed, SourceChanged, OwnerChanged,
    HeaderConflict, MissingCwd, InvalidPath, MissingPath, NotDirectory, UnreadablePath, InvalidRoot,
}
public data class ManagedSessionIssue(
    val kind: ManagedSessionIssueKind, val reason: String, val sessionId: String? = null,
)
public data class ManagedObservedSession(
    val id: String, val cwd: String, val parentSessionId: String?, val sources: Set<ManagedSessionSource>,
    val pinKey: PinKey? = null,
)

/** Complete header identity; an id reused by a different header never inherits a pin. */
@Serializable
public data class PinKey(val id: String, val headerFingerprint: String)

@Serializable
public data class ManagedWorktreePinSnapshot(
    val owner: ManagedWorktreeOwner, val revision: Long = 0, val pins: List<PinKey> = emptyList(),
)

public sealed interface ManagedWorktreePinRead {
    public val owner: ManagedWorktreeOwner
    public data class Available(val snapshot: ManagedWorktreePinSnapshot) : ManagedWorktreePinRead {
        override val owner: ManagedWorktreeOwner get() = snapshot.owner
    }
    public data class Unknown(override val owner: ManagedWorktreeOwner, val reason: String) : ManagedWorktreePinRead
    public data class Unavailable(override val owner: ManagedWorktreeOwner, val reason: String) : ManagedWorktreePinRead
}

public data class ManagedWorktreePinRequest(
    val repository: GitRepository, val owner: ManagedWorktreeOwner, val root: String,
    val key: PinKey, val expectedRevision: Long, val desiredPinned: Boolean,
)

public sealed interface ManagedWorktreePinReceipt {
    public val request: ManagedWorktreePinRequest
    public data class Applied(
        override val request: ManagedWorktreePinRequest, val snapshot: ManagedWorktreePinSnapshot,
        val durabilityWarning: String? = null,
    ) : ManagedWorktreePinReceipt
    public data class Rejected(override val request: ManagedWorktreePinRequest, val reason: String) : ManagedWorktreePinReceipt
    public data class Unknown(override val request: ManagedWorktreePinRequest, val reason: String) : ManagedWorktreePinReceipt
}
/** Complete header identity for a Worktree-associated chat archive entry. */
@Serializable
public data class ChatArchiveKey(val id: String, val headerFingerprint: String)

@Serializable
public data class ManagedWorktreeChatArchiveSnapshot(
    val owner: ManagedWorktreeOwner, val revision: Long = 0, val archived: List<ChatArchiveKey> = emptyList(),
)

public sealed interface ManagedWorktreeChatArchiveRead {
    public val owner: ManagedWorktreeOwner
    public data class Available(val snapshot: ManagedWorktreeChatArchiveSnapshot) : ManagedWorktreeChatArchiveRead {
        override val owner: ManagedWorktreeOwner get() = snapshot.owner
    }
    public data class Unknown(override val owner: ManagedWorktreeOwner, val reason: String) : ManagedWorktreeChatArchiveRead
    public data class Unavailable(override val owner: ManagedWorktreeOwner, val reason: String) : ManagedWorktreeChatArchiveRead
}

public data class ManagedWorktreeChatArchiveRequest(
    val repository: GitRepository, val owner: ManagedWorktreeOwner, val root: String,
    val key: ChatArchiveKey, val expectedRevision: Long, val desiredArchived: Boolean,
)

public sealed interface ManagedWorktreeChatArchiveReceipt {
    public val request: ManagedWorktreeChatArchiveRequest
    public data class Applied(
        override val request: ManagedWorktreeChatArchiveRequest, val snapshot: ManagedWorktreeChatArchiveSnapshot,
        val durabilityWarning: String? = null, val restore: ManagedWorktreeChatRestore? = null,
    ) : ManagedWorktreeChatArchiveReceipt
    public data class Rejected(override val request: ManagedWorktreeChatArchiveRequest, val reason: String) : ManagedWorktreeChatArchiveReceipt
    public data class Unknown(override val request: ManagedWorktreeChatArchiveRequest, val reason: String) : ManagedWorktreeChatArchiveReceipt
}

/**
 * Outcome of rehydrating a chat after its unarchive. It is reported separately from the durable
 * commit and never turns a committed unarchive into a failed one.
 */
public sealed interface ManagedWorktreeChatRestore {
    /** The exact chat is live again and can be opened without any new model request. */
    public data object Restored : ManagedWorktreeChatRestore

    /** A live instance already exists for this chat id; nothing was rehydrated. */
    public data object AlreadyLive : ManagedWorktreeChatRestore

    /** No durable history exists for this chat id. */
    public data object NoHistory : ManagedWorktreeChatRestore

    /** This build has no quiescent restore capability or no durable history service. */
    public data object Unavailable : ManagedWorktreeChatRestore

    /** The durable snapshot is not safely restorable, or the Agent could not be attached. */
    public data class Failed(val reason: String) : ManagedWorktreeChatRestore
}

/**
 * One reason a managed worktree cannot be removed. Every kind maps to a distinct evidence source,
 * so a blocked verdict never hides which protection actually stopped it.
 */
public enum class ManagedRemovalBlockKind {
    NotManaged, UnknownOwner, OwnerUnavailable, Missing, Bare, Prunable, Locked, Detached,
    PrimaryWorktree, NestedPath, RunningAgent, DurableSessions, PinnedChats, ArchivedChats,
    PinsUnavailable, ArchiveUnavailable, SessionsUnavailable, ContentUnavailable, UncommittedChanges,
    Submodules, NestedRepositories, IgnoredContent, ContentScanIncomplete, HeadChanged,
}

public data class ManagedRemovalBlock(val kind: ManagedRemovalBlockKind, val detail: String)

/**
 * The complete evidence a destructive removal must re-verify. The plan is only a request: the
 * implementation re-reads Git, sessions, pins, archives and content before dispatching anything.
 */
public data class ManagedWorktreeRemovalPlan(
    val owner: ManagedWorktreeOwner, val repository: GitRepository, val root: String,
    val headOid: String?, val branch: String?, val blocks: List<ManagedRemovalBlock> = emptyList(),
) {
    public val eligible: Boolean get() = blocks.isEmpty()
}

public sealed interface ManagedWorktreeRemovalRead {
    public val owner: ManagedWorktreeOwner
    public data class Available(val plan: ManagedWorktreeRemovalPlan) : ManagedWorktreeRemovalRead {
        override val owner: ManagedWorktreeOwner get() = plan.owner
    }
    public data class Unknown(override val owner: ManagedWorktreeOwner, val reason: String) : ManagedWorktreeRemovalRead
    public data class Unavailable(override val owner: ManagedWorktreeOwner, val reason: String) : ManagedWorktreeRemovalRead
}

public data class ManagedWorktreeRemovalRequest(val plan: ManagedWorktreeRemovalPlan)

/** Removed is only issued after a fresh worktree list no longer contains the target path. */
public sealed interface ManagedWorktreeRemovalReceipt {
    public val request: ManagedWorktreeRemovalRequest
    public data class Removed(
        override val request: ManagedWorktreeRemovalRequest, val worktrees: List<GitWorktree>,
    ) : ManagedWorktreeRemovalReceipt
    public data class Blocked(
        override val request: ManagedWorktreeRemovalRequest, val blocks: List<ManagedRemovalBlock>,
        val reason: String,
    ) : ManagedWorktreeRemovalReceipt
    public data class Unsupported(override val request: ManagedWorktreeRemovalRequest, val reason: String) :
        ManagedWorktreeRemovalReceipt
    public data class Failed(override val request: ManagedWorktreeRemovalRequest, val reason: String) :
        ManagedWorktreeRemovalReceipt
    public data class UnknownOutcome(override val request: ManagedWorktreeRemovalRequest, val reason: String) :
        ManagedWorktreeRemovalReceipt
}

public data class ManagedSessionObservation(
    val owner: ManagedWorktreeOwner, val startedAtMillis: Long, val completedAtMillis: Long,
    val status: ManagedSessionObservationStatus, val sessions: List<ManagedObservedSession> = emptyList(),
    val issues: List<ManagedSessionIssue> = emptyList(),
)

/** M1 has no absence proof and deliberately no destructive consumer. */
public data class ManagedWorktreeProtection(
    val sessions: ManagedProtection = ManagedProtection(ManagedProtectionAvailability.Unknown, "关联会话未知：尚无完整持久会话目录。"),
    val pins: ManagedProtection = ManagedProtection(ManagedProtectionAvailability.Unavailable, "Pin 保护服务不可用。"),
    val archive: ManagedProtection = ManagedProtection(ManagedProtectionAvailability.Unavailable, "归档服务不可用。"),
    val other: ManagedProtection = ManagedProtection(ManagedProtectionAvailability.Unknown, "共享、主工作树与内容保护尚未完整核验。"),
)

public data class ManagedWorktreeEntry(
    val root: String, val worktree: GitWorktree?, val ownership: ManagedOwnership,
    val registration: ManagedRegistrationPhase? = null, val registrationWarning: String? = null,
    val protection: ManagedWorktreeProtection = ManagedWorktreeProtection(),
    val sessionObservation: ManagedSessionObservation? = null,
    val removal: ManagedWorktreeRemovalRead? = null,
)

public data class ManagedWorktreeInventory(
    val owner: ManagedWorktreeOwner, val repository: GitRepository, val entries: List<ManagedWorktreeEntry>,
    val registeredCount: Int,
    val pinRead: ManagedWorktreePinRead? = null,
    val archiveRead: ManagedWorktreeChatArchiveRead? = null,
)

public sealed interface ManagedWorktreeRead {
    public data class Ready(val inventory: ManagedWorktreeInventory) : ManagedWorktreeRead
    public data class Failed(val message: String, val unavailable: Boolean = false) : ManagedWorktreeRead
}

public interface ManagedWorktreeRepository {
    /** Invalidation only, never owner data. Accepted registrations outlive page navigation. */
    public val revision: StateFlow<Long>
    public suspend fun read(repository: GitRepository): ManagedWorktreeRead
    public suspend fun setPinned(request: ManagedWorktreePinRequest): ManagedWorktreePinReceipt =
        ManagedWorktreePinReceipt.Rejected(request, "聊天固定服务不可用。")
    public suspend fun setArchived(request: ManagedWorktreeChatArchiveRequest): ManagedWorktreeChatArchiveReceipt =
        ManagedWorktreeChatArchiveReceipt.Rejected(request, "聊天归档服务不可用。")
    /**
     * Destructive and therefore off by default. It is only reachable when the read supplied a plan
     * built from complete evidence and the implementation re-verifies that evidence before dispatch.
     */
    public suspend fun removeWorktree(request: ManagedWorktreeRemovalRequest): ManagedWorktreeRemovalReceipt =
        ManagedWorktreeRemovalReceipt.Unsupported(request, "工作树删除服务不可用。")
}

public enum class ManagedWorktreePhase { Unbound, Loading, Ready, Error, Unavailable }
public data class ManagedWorktreeState(
    val repository: GitRepository? = null, val phase: ManagedWorktreePhase = ManagedWorktreePhase.Unbound,
    val inventory: ManagedWorktreeInventory? = null, val message: String? = null, val stale: Boolean = false,
    val pendingPin: ManagedWorktreePinRequest? = null, val pinReceipt: ManagedWorktreePinReceipt? = null,
    val pendingArchive: ManagedWorktreeChatArchiveRequest? = null,
    val archiveReceipt: ManagedWorktreeChatArchiveReceipt? = null,
    val pendingRemoval: ManagedWorktreeRemovalRequest? = null,
    val removalReceipt: ManagedWorktreeRemovalReceipt? = null,
) {
    public val canRefresh: Boolean get() = repository?.binding?.access == GitAccess.LocalReadOnly
    public val pinBusy: Boolean get() = pendingPin != null
    public val archiveBusy: Boolean get() = pendingArchive != null
    public val removalBusy: Boolean get() = pendingRemoval != null

    /** Removal additionally requires a plan whose own evidence is complete and unblocked. */
    public fun canRemove(root: String): Boolean {
        if (phase != ManagedWorktreePhase.Ready || stale || removalBusy) return false
        val current = inventory ?: return false
        if (repository != current.repository || repository?.binding?.access != GitAccess.LocalReadOnly) return false
        if (current.owner.identity != repository?.binding?.identity || current.owner.hostId != repository?.binding?.hostId) return false
        val entry = current.entries.singleOrNull { it.root == root } ?: return false
        val removal = entry.removal as? ManagedWorktreeRemovalRead.Available ?: return false
        return removal.plan.owner == current.owner && removal.plan.repository == current.repository &&
            removal.plan.root == root && removal.plan.eligible
    }

    public fun canPin(root: String, key: PinKey): Boolean {
        val current = inventory ?: return false
        val available = current.pinRead as? ManagedWorktreePinRead.Available ?: return false
        val entry = current.entries.singleOrNull { it.root == root } ?: return false
        val observed = entry.sessionObservation ?: return false
        val tree = entry.worktree ?: return false
        return phase == ManagedWorktreePhase.Ready && !stale && !pinBusy &&
            repository == current.repository && repository?.binding?.access == GitAccess.LocalReadOnly &&
            current.owner.identity == null && repository?.binding?.identity == null &&
            current.owner.identity == repository?.binding?.identity && current.owner.hostId == repository?.binding?.hostId &&
            available.owner == current.owner && available.snapshot.revision >= 0 && observed.owner == current.owner &&
            observed.status == ManagedSessionObservationStatus.Observed && observed.issues.isEmpty() &&
            entry.ownership != ManagedOwnership.Missing && !tree.bare && !tree.prunable &&
            key.id.isNotBlank() && key.headerFingerprint.matches(Regex("[0-9a-f]{64}")) &&
            observed.sessions.count { it.id == key.id && it.pinKey == key } == 1
    }

    /**
     * Archive eligibility mirrors [canPin]. The observed session's [ManagedObservedSession.pinKey]
     * carries the complete header identity, so it also fences the archived [ChatArchiveKey].
     */
    public fun canArchive(root: String, key: ChatArchiveKey): Boolean {
        val current = inventory ?: return false
        val available = current.archiveRead as? ManagedWorktreeChatArchiveRead.Available ?: return false
        val entry = current.entries.singleOrNull { it.root == root } ?: return false
        val observed = entry.sessionObservation ?: return false
        val tree = entry.worktree ?: return false
        return phase == ManagedWorktreePhase.Ready && !stale && !archiveBusy &&
            repository == current.repository && repository?.binding?.access == GitAccess.LocalReadOnly &&
            current.owner.identity == null && repository?.binding?.identity == null &&
            current.owner.identity == repository?.binding?.identity && current.owner.hostId == repository?.binding?.hostId &&
            available.owner == current.owner && available.snapshot.revision >= 0 && observed.owner == current.owner &&
            observed.status == ManagedSessionObservationStatus.Observed && observed.issues.isEmpty() &&
            entry.ownership != ManagedOwnership.Missing && !tree.bare && !tree.prunable &&
            key.id.isNotBlank() && key.headerFingerprint.matches(Regex("[0-9a-f]{64}")) &&
            observed.sessions.count { it.id == key.id && it.pinKey?.headerFingerprint == key.headerFingerprint } == 1
    }
}
