package im.hikaru.harness.client.projects

import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.harness.client.connection.Connection
import im.hikaru.harness.client.connection.ConnectionTransport
import im.hikaru.harness.client.connection.SessionSummary
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

@JvmInline @Serializable
public value class LocalProjectId(public val value: String) {
    init { require(value.isNotBlank()) }
}

@Serializable
public data class LocalProjectOwner(val hostId: HostId)

public data class LocalProjectScope(val hostId: HostId, val generation: Long) {
    init { require(generation >= 0) }
}

/** The caller supplies a verified connection. Transport is explicit; an account is not transport. */
public data class LocalProjectBinding(
    val connection: Connection, val scope: LocalProjectScope, val transport: ConnectionTransport,
) {
    public val owner: LocalProjectOwner get() = LocalProjectOwner(scope.hostId)
    public fun sameOwner(other: LocalProjectBinding): Boolean =
        connection === other.connection && scope == other.scope && transport == other.transport
}

@Serializable
public data class LocalProjectRoot(val path: String) {
    init { require(path.isNotBlank()) }
}

@Serializable
public data class LocalProject(
    val id: LocalProjectId, val name: String, val owner: LocalProjectOwner,
    val roots: List<LocalProjectRoot>,
) {
    init { require(name.isNotBlank()); require(roots.size == 1) { "Only one confirmed root is supported" } }
}

@Serializable
public data class CreateLocalProject(val requestId: String, val name: String, val directory: String)

/** Receipt is durable: an ambiguous write can be retried without making another project. */
@Serializable
public data class LocalProjectCreateReceipt(val request: CreateLocalProject, val projectId: LocalProjectId)

@Serializable
public data class LocalProjectMembership(val projectId: LocalProjectId, val session: SessionSummary)

@Serializable
public data class LocalProjectSnapshot(
    val owner: LocalProjectOwner, val revision: Long = 0,
    val projects: List<LocalProject> = emptyList(),
    val memberships: List<LocalProjectMembership> = emptyList(),
    val createReceipts: List<LocalProjectCreateReceipt> = emptyList(),
) {
    public fun project(id: LocalProjectId): LocalProject? = projects.firstOrNull { it.id == id }
}

public enum class LocalProjectUnavailableReason {
    ProjectServiceNotExposed, LocalOnly, HostNotVerified, SessionServiceNotExposed,
}

public sealed interface LocalProjectFailure {
    public data class Unavailable(val reason: LocalProjectUnavailableReason, val message: String) : LocalProjectFailure
    public data class Error(val message: String) : LocalProjectFailure
    public data class AccessDenied(val message: String) : LocalProjectFailure
    public data class UnsupportedVersion(val version: Int) : LocalProjectFailure
    public data class Conflict(val message: String) : LocalProjectFailure
    public data class Rejected(val message: String) : LocalProjectFailure
    public data object StaleBinding : LocalProjectFailure
}

public sealed interface LocalProjectsResult {
    public data class Ready(val snapshot: LocalProjectSnapshot) : LocalProjectsResult
    public data class Failed(val failure: LocalProjectFailure) : LocalProjectsResult
}

public sealed interface LocalProjectWriteResult {
    public data class Committed(
        val snapshot: LocalProjectSnapshot, val projectId: LocalProjectId,
        val replayed: Boolean = false, val durabilityWarning: String? = null,
    ) : LocalProjectWriteResult
    public data class Failed(val failure: LocalProjectFailure) : LocalProjectWriteResult
}

/** Capture before create; pass this same target at the asynchronous create confirmation point. */
public data class ValidatedLocalProjectTarget(
    val binding: LocalProjectBinding, val projectId: LocalProjectId, val root: LocalProjectRoot,
    val controllerEpoch: Long? = null,
)

public sealed interface LocalProjectRootResult {
    public data class Ready(val target: ValidatedLocalProjectTarget) : LocalProjectRootResult
    public data class Failed(val failure: LocalProjectFailure) : LocalProjectRootResult
}

public enum class LocalProjectsPhase { Unbound, Loading, Ready, Empty, Error, Unavailable, Unsupported, AccessDenied }
public enum class LocalProjectMutation { Create, BindDirectory, AttachSession }

public data class LocalProjectsState(
    val binding: LocalProjectBinding? = null, val phase: LocalProjectsPhase = LocalProjectsPhase.Unbound,
    val snapshot: LocalProjectSnapshot? = null, val selectedProjectId: LocalProjectId? = null,
    val query: String = "", val mutation: LocalProjectMutation? = null,
    val failure: LocalProjectFailure? = null, val mutationFailure: LocalProjectFailure? = null,
    val pendingCreate: CreateLocalProject? = null, val canRefresh: Boolean = false,
    val durabilityWarning: String? = null,
) {
    public val projects: List<LocalProject> get() =
        if (phase == LocalProjectsPhase.Ready || phase == LocalProjectsPhase.Empty) snapshot?.projects.orEmpty() else emptyList()
    public val visibleProjects: List<LocalProject> get() = projects.filter {
        query.isBlank() || it.name.contains(query, true) || it.roots.any { root -> root.path.contains(query, true) }
    }
    public val selectedProject: LocalProject? get() = projects.firstOrNull { it.id == selectedProjectId }
}
