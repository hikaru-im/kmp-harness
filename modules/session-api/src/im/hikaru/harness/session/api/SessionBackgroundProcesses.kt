package im.hikaru.harness.session.api

import im.hikaru.harness.llm.CallId
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.service.ServiceKey
import im.hikaru.harness.session.SessionId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@JvmInline
public value class BackgroundProcessId(val value: String) {
    init { require(value.isNotBlank()) }
}

@Serializable
public sealed interface BackgroundProcessLifecycle {
    @Serializable @SerialName("running")
    public data object Running : BackgroundProcessLifecycle
    @Serializable @SerialName("exited")
    public data class Exited(val exitCode: Int) : BackgroundProcessLifecycle
    @Serializable @SerialName("unknown")
    public data object Unknown : BackgroundProcessLifecycle
}

@Serializable
public enum class BackgroundInputState { Unsupported, Open, Closed, Unknown }

/** Only processes owned by this producer incarnation are enumerated. No OS attach or restart recovery. */
@Serializable
public data class BackgroundProcess(
    val process: BackgroundProcessId,
    val ownerSession: SessionId,
    val callId: CallId,
    val command: String,
    val cwd: String,
    val startedAtEpochMillis: Long,
    val producerIncarnation: String,
    val lifecycle: BackgroundProcessLifecycle,
    val inputState: BackgroundInputState = BackgroundInputState.Unsupported,
) {
    init { require(startedAtEpochMillis >= 0); require(producerIncarnation.isNotBlank()) }
}

@Serializable
public sealed interface BackgroundProcessesResult {
    public val parent: SessionId
    @Serializable @SerialName("unavailable")
    public data class Unavailable(override val parent: SessionId, val reason: String) : BackgroundProcessesResult
    @Serializable @SerialName("available")
    public data class Available(override val parent: SessionId, val processes: List<BackgroundProcess>) : BackgroundProcessesResult {
        init {
            require(processes.all { it.ownerSession == parent })
            require(processes.map { it.process }.distinct().size == processes.size)
        }
    }
}

@Serializable
public sealed interface BackgroundOutputResult {
    public val parent: SessionId
    public val process: BackgroundProcessId
    @Serializable @SerialName("unavailable")
    public data class Unavailable(override val parent: SessionId, override val process: BackgroundProcessId, val reason: String) : BackgroundOutputResult
    @Serializable @SerialName("not-found")
    public data class NotFound(override val parent: SessionId, override val process: BackgroundProcessId) : BackgroundOutputResult
    @Serializable @SerialName("available")
    public data class Available(
        override val parent: SessionId,
        override val process: BackgroundProcessId,
        val ownerSession: SessionId,
        val buffer: String,
        val revision: Long,
        val truncated: Boolean,
        val lifecycle: BackgroundProcessLifecycle,
        val inputState: BackgroundInputState = BackgroundInputState.Unsupported,
        /** Absolute number of UTF-16 code units captured before the bounded tail was applied. */
        val totalChars: Long = buffer.length.toLong(),
        /** The producer's watchdog ended the process at its configured deadline. */
        val timedOut: Boolean = false,
    ) : BackgroundOutputResult {
        init {
            require(ownerSession == parent)
            require(revision >= 0)
            require(totalChars >= buffer.length)
        }
    }
}

@Serializable
public sealed interface BackgroundInputResult {
    public val parent: SessionId
    public val process: BackgroundProcessId

    @Serializable @SerialName("unavailable")
    public data class Unavailable(
        override val parent: SessionId,
        override val process: BackgroundProcessId,
        val reason: String,
    ) : BackgroundInputResult

    @Serializable @SerialName("not-found")
    public data class NotFound(
        override val parent: SessionId,
        override val process: BackgroundProcessId,
    ) : BackgroundInputResult

    @Serializable @SerialName("accepted")
    public data class Accepted(
        override val parent: SessionId,
        override val process: BackgroundProcessId,
        val bytes: Int,
    ) : BackgroundInputResult {
        init { require(bytes >= 0) }
    }

    @Serializable @SerialName("rejected")
    public data class Rejected(
        override val parent: SessionId,
        override val process: BackgroundProcessId,
        val reason: String,
    ) : BackgroundInputResult

    /** Bytes may have reached the process; callers must not retry this input. */
    @Serializable @SerialName("unknown-outcome")
    public data class UnknownOutcome(
        override val parent: SessionId,
        override val process: BackgroundProcessId,
        val reason: String,
    ) : BackgroundInputResult
}

@Serializable
public sealed interface BackgroundControlResult {
    public val parent: SessionId
    public val process: BackgroundProcessId

    @Serializable @SerialName("unavailable")
    public data class Unavailable(
        override val parent: SessionId,
        override val process: BackgroundProcessId,
        val reason: String,
    ) : BackgroundControlResult

    @Serializable @SerialName("not-found")
    public data class NotFound(
        override val parent: SessionId,
        override val process: BackgroundProcessId,
    ) : BackgroundControlResult

    @Serializable @SerialName("applied")
    public data class Applied(
        override val parent: SessionId,
        override val process: BackgroundProcessId,
    ) : BackgroundControlResult

    @Serializable @SerialName("rejected")
    public data class Rejected(
        override val parent: SessionId,
        override val process: BackgroundProcessId,
        val reason: String,
    ) : BackgroundControlResult

    @Serializable @SerialName("unknown-outcome")
    public data class UnknownOutcome(
        override val parent: SessionId,
        override val process: BackgroundProcessId,
        val reason: String,
    ) : BackgroundControlResult
}

public interface BackgroundProcessesService {
    public suspend fun list(parent: SessionId): BackgroundProcessesResult
    public suspend fun read(parent: SessionId, process: BackgroundProcessId): BackgroundOutputResult
    public suspend fun write(parent: SessionId, process: BackgroundProcessId, input: String): BackgroundInputResult =
        BackgroundInputResult.Unavailable(parent, process, "Background input service is unavailable")
    public suspend fun closeInput(parent: SessionId, process: BackgroundProcessId): BackgroundControlResult =
        BackgroundControlResult.Unavailable(parent, process, "Background input service is unavailable")
    public suspend fun cancel(parent: SessionId, process: BackgroundProcessId): BackgroundControlResult =
        BackgroundControlResult.Unavailable(parent, process, "Background process control service is unavailable")
}

public object BackgroundProcessesKey : ServiceKey<BackgroundProcessesService>("session.background-processes")
public val Context.backgroundProcesses: BackgroundProcessesService?
    get() = get(BackgroundProcessesKey)
