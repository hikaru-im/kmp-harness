package im.hikaru.harness.session.api

import im.hikaru.harness.runtime.service.ServiceKey
import im.hikaru.harness.session.SessionId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Discovery names the requested Session without granting authority from its textual id. */
@Serializable
public sealed interface HostTerminalDiscoveryResult {
    public val session: SessionId

    @Serializable @SerialName("available")
    public data class Available(
        override val session: SessionId,
        val owner: TerminalOwner,
        val canonicalCwd: String,
        val capacity: Int,
    ) : HostTerminalDiscoveryResult {
        init { require(owner.session == session && canonicalCwd.isNotBlank() && capacity > 0) }
    }

    @Serializable @SerialName("rejected")
    public data class Rejected(override val session: SessionId, val reason: String) : HostTerminalDiscoveryResult

    @Serializable @SerialName("unavailable")
    public data class Unavailable(override val session: SessionId, val reason: String) : HostTerminalDiscoveryResult
}

/** A lost acknowledgement may have no known key. It never authorizes retrying create. */
@Serializable
public sealed interface HostTerminalCreateResult {
    public val owner: TerminalOwner

    @Serializable @SerialName("created")
    public data class Created(override val owner: TerminalOwner, val metadata: TerminalMetadata) : HostTerminalCreateResult {
        init { require(metadata.key.owner == owner) }
    }

    @Serializable @SerialName("rejected")
    public data class Rejected(override val owner: TerminalOwner, val reason: String) : HostTerminalCreateResult

    @Serializable @SerialName("unavailable")
    public data class Unavailable(override val owner: TerminalOwner, val reason: String) : HostTerminalCreateResult

    @Serializable @SerialName("unknown")
    public data class UnknownOutcome(override val owner: TerminalOwner, val key: TerminalKey?, val reason: String) : HostTerminalCreateResult {
        init { require(key == null || key.owner == owner) }
    }
}

/** Runtime-owned PTYs. Only explicit create may start a process. */
public interface SessionTerminalHostService {
    public suspend fun discover(session: SessionId): HostTerminalDiscoveryResult
    public suspend fun create(request: TerminalCreateRequest): HostTerminalCreateResult
    public suspend fun list(owner: TerminalOwner): TerminalListResult
    public suspend fun read(key: TerminalKey): TerminalReadResult
    public suspend fun write(key: TerminalKey, input: String): TerminalMutationResult
    public suspend fun input(key: TerminalKey, input: TerminalInput): TerminalMutationResult =
        TerminalMutationResult.Unavailable(key, "Terminal Host semantic input is unavailable")
    public suspend fun resize(key: TerminalKey, size: TerminalSize): TerminalMutationResult
    public suspend fun close(key: TerminalKey): TerminalMutationResult
}

public object SessionTerminalHostKey : ServiceKey<SessionTerminalHostService>("terminal-host")
