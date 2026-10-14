package im.hikaru.harness.client.scheduling

public sealed interface ScheduledReadResult {
    public data object Missing : ScheduledReadResult
    public data class Ready(val snapshot: ScheduledSnapshot) : ScheduledReadResult
    public data class Invalid(val message: String) : ScheduledReadResult
    public data class UnsupportedVersion(val version: Int?) : ScheduledReadResult
    public data class IoFailure(val message: String) : ScheduledReadResult
}

public sealed interface ScheduledWriteResult {
    /** Rename committed. A directory fsync failure is reported separately, never called rollback. */
    public data class Committed(val snapshot: ScheduledSnapshot, val durabilityWarning: String? = null) : ScheduledWriteResult
    public data class Failed(val message: String) : ScheduledWriteResult
    public data object StaleScope : ScheduledWriteResult
}

/** Implementations must validate the whole document and CAS, without replacing invalid input. */
public interface ScheduledStore {
    public suspend fun read(owner: ScheduledOwner): ScheduledReadResult
    public suspend fun compareAndSet(
        owner: ScheduledOwner, expectedRevision: Long, snapshot: ScheduledSnapshot,
        isCurrentScope: () -> Boolean,
    ): ScheduledWriteResult
}

/** Safe production default until a durable backend is explicitly supplied by the platform. */
public class UnavailableScheduledStore(
    private val reason: String = "当前平台未提供持久化定时任务存储，无法保存任务。",
) : ScheduledStore {
    override suspend fun read(owner: ScheduledOwner): ScheduledReadResult = ScheduledReadResult.IoFailure(reason)
    override suspend fun compareAndSet(
        owner: ScheduledOwner, expectedRevision: Long, snapshot: ScheduledSnapshot, isCurrentScope: () -> Boolean,
    ): ScheduledWriteResult = ScheduledWriteResult.Failed(reason)
}
