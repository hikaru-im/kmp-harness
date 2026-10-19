package im.hikaru.harness.schedule

import androidx.room3.ColumnInfo
import androidx.room3.Dao
import androidx.room3.Database
import androidx.room3.Entity
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.PrimaryKey
import androidx.room3.Query
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import im.hikaru.harness.runtime.effect.Disposable
import im.hikaru.harness.session.SessionId
import java.io.File
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val SCHEDULE_DATABASE_VERSION = 1

/**
 * One durable reminder. The record itself stays session-free; the row envelope
 * binds it to exactly one Session, matching the DSH task schema.
 */
@Entity(tableName = "harness_schedules")
internal data class ScheduleRow(
    @PrimaryKey
    @ColumnInfo(name = "schedule_id")
    val scheduleId: String,
    @ColumnInfo(name = "session_id")
    val sessionId: String,
    val kind: String,
    val title: String,
    val prompt: String,
    @ColumnInfo(name = "scheduled_at")
    val scheduledAt: String,
    @ColumnInfo(name = "after_seconds")
    val afterSeconds: Long? = null,
    @ColumnInfo(name = "every_seconds")
    val everySeconds: Long? = null,
    val time: String? = null,
    @ColumnInfo(name = "time_zone")
    val timeZone: String? = null,
    val weekdays: String? = null,
    val expression: String? = null,
    val status: String,
    @ColumnInfo(name = "last_scheduled_at")
    val lastScheduledAt: String? = null,
    @ColumnInfo(name = "last_delivered_at")
    val lastDeliveredAt: String? = null,
    @ColumnInfo(name = "last_message_id")
    val lastMessageId: String? = null,
)

@Dao
internal interface ScheduleDao {
    @Query("SELECT * FROM harness_schedules ORDER BY rowid ASC")
    suspend fun all(): List<ScheduleRow>

    @Query("SELECT * FROM harness_schedules WHERE schedule_id = :id LIMIT 1")
    suspend fun find(id: String): ScheduleRow?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(row: ScheduleRow)

    @Query("DELETE FROM harness_schedules WHERE schedule_id = :id")
    suspend fun delete(id: String)
}

@Database(
    entities = [ScheduleRow::class],
    version = SCHEDULE_DATABASE_VERSION,
    exportSchema = false,
)
internal abstract class ScheduleDatabase : RoomDatabase() {
    abstract fun schedules(): ScheduleDao
}

/** Durable task storage; ordering is insertion order so batches match DSH. */
internal interface ScheduleStore : Disposable {
    suspend fun all(): List<StoredSchedule>
    suspend fun find(id: ScheduleId): StoredSchedule?
    suspend fun put(task: StoredSchedule)
    suspend fun delete(id: ScheduleId)
}

internal class RoomScheduleStore private constructor(
    private val database: ScheduleDatabase,
) : ScheduleStore {
    private val mutex = Mutex()
    private var disposed = false

    override suspend fun all(): List<StoredSchedule> = mutex.withLock {
        check(!disposed) { "Schedule store is disposed" }
        database.schedules().all().map { it.toStoredSchedule() }
    }

    override suspend fun find(id: ScheduleId): StoredSchedule? = mutex.withLock {
        check(!disposed) { "Schedule store is disposed" }
        database.schedules().find(id.value)?.toStoredSchedule()
    }

    override suspend fun put(task: StoredSchedule): Unit = mutex.withLock {
        check(!disposed) { "Schedule store is disposed" }
        database.schedules().put(task.toRow())
    }

    override suspend fun delete(id: ScheduleId): Unit = mutex.withLock {
        check(!disposed) { "Schedule store is disposed" }
        database.schedules().delete(id.value)
    }

    override suspend fun dispose(): Unit = mutex.withLock {
        if (disposed) return@withLock
        disposed = true
        database.close()
    }

    companion object {
        fun open(databaseFile: File): RoomScheduleStore {
            databaseFile.parentFile?.mkdirs()
            // JVM-only modules pass the generated implementation explicitly;
            // @ConstructedBy is only permitted on an expect declaration.
            val database = Room.databaseBuilder<ScheduleDatabase>(
                name = databaseFile.absolutePath,
                factory = { ScheduleDatabase_Impl() },
            )
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
            return RoomScheduleStore(database)
        }
    }
}

private fun StoredSchedule.toRow(): ScheduleRow {
    val record = record
    val receipt = lastDelivery
    return ScheduleRow(
        scheduleId = record.id.value,
        sessionId = sessionId.value,
        kind = record.kind.wire,
        title = record.title,
        prompt = record.prompt,
        scheduledAt = formatInstant(record.scheduledAt),
        afterSeconds = (record as? ScheduleRecord.After)?.afterSeconds,
        everySeconds = (record as? ScheduleRecord.Every)?.everySeconds,
        time = when (record) {
            is ScheduleRecord.Daily -> record.time.toString()
            is ScheduleRecord.Weekly -> record.time.toString()
            else -> null
        },
        timeZone = when (record) {
            is ScheduleRecord.Daily -> record.timeZone
            is ScheduleRecord.Weekly -> record.timeZone
            is ScheduleRecord.Cron -> record.timeZone
            else -> null
        },
        weekdays = (record as? ScheduleRecord.Weekly)?.weekdays?.joinToString(","),
        expression = (record as? ScheduleRecord.Cron)?.expression,
        status = status.wire,
        lastScheduledAt = receipt?.let { formatInstant(it.scheduledAt) },
        lastDeliveredAt = receipt?.let { formatInstant(it.deliveredAt) },
        lastMessageId = receipt?.messageId,
    )
}

private fun ScheduleRow.toStoredSchedule(): StoredSchedule {
    val id = ScheduleId(scheduleId)
    val epoch = parseStoredInstant(scheduledAt, "scheduledAt")
    val title = scheduleTitle(this.title).also {
        require(it == this.title) { "Stored title is not canonical" }
    }
    val prompt = schedulePrompt(this.prompt).also {
        require(it == this.prompt) { "Stored prompt is not canonical" }
    }
    val kind = ScheduleKind.fromWire(kind)
    val record: ScheduleRecord = when (kind) {
        ScheduleKind.After -> {
            require(expression == null && time == null && timeZone == null && weekdays == null)
            require(everySeconds == null)
            val seconds = requireNotNull(afterSeconds) { "afterSeconds is required" }
            require(seconds > 0) { "afterSeconds must be positive" }
            ScheduleRecord.After(id, title, prompt, epoch, seconds)
        }

        ScheduleKind.At -> {
            require(afterSeconds == null && everySeconds == null && time == null)
            require(timeZone == null && weekdays == null && expression == null)
            ScheduleRecord.At(id, title, prompt, epoch)
        }

        ScheduleKind.Every -> {
            require(afterSeconds == null && time == null && timeZone == null)
            require(weekdays == null && expression == null)
            val seconds = requireNotNull(everySeconds) { "everySeconds is required" }
            require(seconds >= MIN_EVERY_INTERVAL_SECONDS)
            ScheduleRecord.Every(id, title, prompt, epoch, seconds)
        }

        ScheduleKind.Daily -> {
            require(afterSeconds == null && everySeconds == null && weekdays == null && expression == null)
            val parsedTime = parseClockTime(requireNotNull(time), "daily")
            require(parsedTime.toString() == time) { "Stored time is not canonical" }
            val zone = requireNotNull(timeZone)
            require(canonicalTimeZone(zone) == zone) { "Stored time zone is invalid" }
            ScheduleRecord.Daily(id, title, prompt, epoch, parsedTime, zone)
        }

        ScheduleKind.Weekly -> {
            require(afterSeconds == null && everySeconds == null && expression == null)
            val parsedTime = parseClockTime(requireNotNull(time), "weekly")
            require(parsedTime.toString() == time) { "Stored time is not canonical" }
            val zone = requireNotNull(timeZone)
            require(canonicalTimeZone(zone) == zone) { "Stored time zone is invalid" }
            val rawWeekdays = requireNotNull(weekdays) { "weekdays are required" }
            val parsedWeekdays = rawWeekdays.split(',').map { it.toInt() }
            val normalized = normalizeWeekdays(parsedWeekdays)
            require(normalized == parsedWeekdays && normalized.joinToString(",") == rawWeekdays)
            ScheduleRecord.Weekly(id, title, prompt, epoch, parsedTime, zone, normalized)
        }

        ScheduleKind.Cron -> {
            require(afterSeconds == null && everySeconds == null && time == null && weekdays == null)
            val rawExpression = requireNotNull(expression) { "expression is required" }
            require(canonicalizeCronExpression(rawExpression) == rawExpression)
            val zone = requireNotNull(timeZone)
            require(canonicalTimeZone(zone) == zone) { "Stored time zone is invalid" }
            ScheduleRecord.Cron(id, title, prompt, epoch, rawExpression, zone)
        }
    }
    val status = ScheduleStatus.fromWire(status)
    val receipt = deliveryReceipt()
    return StoredSchedule(
        sessionId = SessionId(requireNotNull(sessionId).also { require(it.isNotBlank()) }),
        record = record,
        status = status,
        lastDelivery = receipt,
    )
}

private fun ScheduleRow.deliveryReceipt(): ScheduleDeliveryReceipt? {
    val scheduled = lastScheduledAt
    val delivered = lastDeliveredAt
    val message = lastMessageId
    if (scheduled == null && delivered == null && message == null) return null
    val scheduledAt = parseStoredInstant(requireNotNull(scheduled), "lastDelivery.scheduledAt")
    val deliveredAt = parseStoredInstant(requireNotNull(delivered), "lastDelivery.deliveredAt")
    require(requireNotNull(message).let { it.isNotBlank() && it.trim() == it })
    require(deliveredAt >= scheduledAt) { "lastDelivery.deliveredAt precedes scheduledAt" }
    return ScheduleDeliveryReceipt(scheduledAt, deliveredAt, message)
}

private fun parseStoredInstant(value: String, field: String): Long {
    val instant = try {
        Instant.parse(value)
    } catch (error: RuntimeException) {
        throw IllegalArgumentException("Stored $field is not an instant", error)
    }
    val epoch = instant.toEpochMilli()
    require(epoch in MIN_SCHEDULE_INSTANT..MAX_SCHEDULE_INSTANT) { "Stored $field is out of range" }
    require(formatInstant(epoch) == value) { "Stored $field is not canonical" }
    return epoch
}
