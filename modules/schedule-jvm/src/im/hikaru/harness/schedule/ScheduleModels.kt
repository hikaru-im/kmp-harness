package im.hikaru.harness.schedule

import im.hikaru.harness.session.SessionId
import java.util.UUID

/** DSH-compatible `schedule-<uuid>` identity. */
@JvmInline
public value class ScheduleId(public val value: String) {
    init {
        require(value.isNotBlank()) { "Schedule id must not be blank" }
        require(value.trim() == value) { "Schedule id must not have surrounding whitespace" }
    }

    public companion object {
        public fun random(): ScheduleId = ScheduleId("schedule-${UUID.randomUUID()}")
    }
}

public enum class ScheduleKind(public val wire: String) {
    After("after"),
    At("at"),
    Every("every"),
    Daily("daily"),
    Weekly("weekly"),
    Cron("cron"),
    ;

    public val isRecurring: Boolean get() = this == Every || this == Daily || this == Weekly || this == Cron

    public companion object {
        public fun fromWire(value: String): ScheduleKind =
            entries.firstOrNull { it.wire == value } ?: error("Unknown schedule kind '$value'")
    }
}

/**
 * Local wall-clock time normalized to `HH:mm:ss.SSS`, as stored by DSH.
 * Milliseconds are preserved even when the input had fewer fractional digits.
 */
public data class ScheduleTime(
    val hour: Int,
    val minute: Int,
    val second: Int,
    val millisecond: Int,
) {
    init {
        require(hour in 0..23)
        require(minute in 0..59)
        require(second in 0..59)
        require(millisecond in 0..999)
    }

    override fun toString(): String =
        "%02d:%02d:%02d.%03d".format(hour, minute, second, millisecond)
}

/** Immutable reminder record. It carries no Session binding; see [StoredSchedule]. */
public sealed interface ScheduleRecord {
    public val id: ScheduleId
    public val kind: ScheduleKind
    public val title: String
    public val prompt: String

    /** Committed next occurrence, or the final occurrence once inactive. */
    public val scheduledAt: Long

    public fun withScheduledAt(value: Long): ScheduleRecord

    public fun withContent(title: String, prompt: String): ScheduleRecord

    public data class After(
        override val id: ScheduleId,
        override val title: String,
        override val prompt: String,
        override val scheduledAt: Long,
        val afterSeconds: Long,
    ) : ScheduleRecord {
        override val kind: ScheduleKind get() = ScheduleKind.After
        override fun withScheduledAt(value: Long): ScheduleRecord = copy(scheduledAt = value)
        override fun withContent(title: String, prompt: String): ScheduleRecord = copy(title = title, prompt = prompt)
    }

    public data class At(
        override val id: ScheduleId,
        override val title: String,
        override val prompt: String,
        override val scheduledAt: Long,
    ) : ScheduleRecord {
        override val kind: ScheduleKind get() = ScheduleKind.At
        override fun withScheduledAt(value: Long): ScheduleRecord = copy(scheduledAt = value)
        override fun withContent(title: String, prompt: String): ScheduleRecord = copy(title = title, prompt = prompt)
    }

    public data class Every(
        override val id: ScheduleId,
        override val title: String,
        override val prompt: String,
        override val scheduledAt: Long,
        val everySeconds: Long,
    ) : ScheduleRecord {
        override val kind: ScheduleKind get() = ScheduleKind.Every
        override fun withScheduledAt(value: Long): ScheduleRecord = copy(scheduledAt = value)
        override fun withContent(title: String, prompt: String): ScheduleRecord = copy(title = title, prompt = prompt)
    }

    public data class Daily(
        override val id: ScheduleId,
        override val title: String,
        override val prompt: String,
        override val scheduledAt: Long,
        val time: ScheduleTime,
        val timeZone: String,
    ) : ScheduleRecord {
        override val kind: ScheduleKind get() = ScheduleKind.Daily
        override fun withScheduledAt(value: Long): ScheduleRecord = copy(scheduledAt = value)
        override fun withContent(title: String, prompt: String): ScheduleRecord = copy(title = title, prompt = prompt)
    }

    public data class Weekly(
        override val id: ScheduleId,
        override val title: String,
        override val prompt: String,
        override val scheduledAt: Long,
        val time: ScheduleTime,
        val timeZone: String,
        /** ISO weekdays, Monday=1..Sunday=7, unique and ascending. */
        val weekdays: List<Int>,
    ) : ScheduleRecord {
        override val kind: ScheduleKind get() = ScheduleKind.Weekly
        override fun withScheduledAt(value: Long): ScheduleRecord = copy(scheduledAt = value)
        override fun withContent(title: String, prompt: String): ScheduleRecord = copy(title = title, prompt = prompt)
    }

    public data class Cron(
        override val id: ScheduleId,
        override val title: String,
        override val prompt: String,
        override val scheduledAt: Long,
        /** Canonical five-field Vixie expression. */
        val expression: String,
        val timeZone: String,
    ) : ScheduleRecord {
        override val kind: ScheduleKind get() = ScheduleKind.Cron
        override fun withScheduledAt(value: Long): ScheduleRecord = copy(scheduledAt = value)
        override fun withContent(title: String, prompt: String): ScheduleRecord = copy(title = title, prompt = prompt)
    }
}

public enum class ScheduleStatus(public val wire: String) {
    Active("active"),
    Inactive("inactive"),
    ;

    public companion object {
        public fun fromWire(value: String): ScheduleStatus =
            entries.firstOrNull { it.wire == value } ?: error("Unknown schedule status '$value'")
    }
}

/** Proof that one occurrence was delivered into a Session. */
public data class ScheduleDeliveryReceipt(
    /** Occurrence actually delivered, not necessarily the stored target. */
    val scheduledAt: Long,
    /** Sampled after the Session flush acknowledgment. */
    val deliveredAt: Long,
    val messageId: String,
) {
    init {
        require(messageId.isNotBlank())
        require(deliveredAt >= 0)
    }
}

public data class StoredSchedule(
    val sessionId: SessionId,
    val record: ScheduleRecord,
    val status: ScheduleStatus,
    val lastDelivery: ScheduleDeliveryReceipt? = null,
)

/** Input selectors accepted by create/update. */
public sealed interface ScheduleSelector {
    public val kind: ScheduleKind

    public data class After(val seconds: Long) : ScheduleSelector {
        override val kind: ScheduleKind get() = ScheduleKind.After
    }

    public data class At(val input: ScheduleAtInput) : ScheduleSelector {
        override val kind: ScheduleKind get() = ScheduleKind.At
    }

    public data class Every(val seconds: Long) : ScheduleSelector {
        override val kind: ScheduleKind get() = ScheduleKind.Every
    }

    public data class Daily(val time: ScheduleTime, val timeZone: String) : ScheduleSelector {
        override val kind: ScheduleKind get() = ScheduleKind.Daily
    }

    public data class Weekly(
        val time: ScheduleTime,
        val timeZone: String,
        val weekdays: List<Int>,
    ) : ScheduleSelector {
        override val kind: ScheduleKind get() = ScheduleKind.Weekly
    }

    public data class Cron(val expression: String, val timeZone: String) : ScheduleSelector {
        override val kind: ScheduleKind get() = ScheduleKind.Cron
    }
}

/** Absolute `at` input, either an RFC 3339 offset string or a local date/time plus zone. */
public sealed interface ScheduleAtInput {
    public data class Offset(val text: String) : ScheduleAtInput
    public data class Local(val date: String, val time: String, val timeZone: String) : ScheduleAtInput
}

public enum class ScheduleErrorCode(public val wire: String) {
    InvalidPrompt("invalid_prompt"),
    InvalidSelector("invalid_selector"),
    InvalidRule("invalid_rule"),
    InvalidTimeZone("invalid_time_zone"),
    NotFuture("not_future"),
    TimeOutOfRange("time_out_of_range"),
    FrequencyTooHigh("frequency_too_high"),
    SubagentSession("subagent_session"),
    InternalError("internal_error"),
}

public class ScheduleInputException(
    public val code: ScheduleErrorCode,
    message: String,
) : IllegalArgumentException(message)

public data class ScheduleConfig(
    /** Retry/scan cadence after a wake; delivery is armed at the next due instant. */
    val scanIntervalMillis: Long = 1_000,
    /** Long waits are re-armed in segments; matches DSH's 2^31-1 ms ceiling. */
    val maxTimerDelayMillis: Long = 2_147_483_647L,
) {
    init {
        require(scanIntervalMillis > 0)
        require(maxTimerDelayMillis > 0)
    }
}

/** Injectable wall clock; calendar math never uses a monotonic timer clock. */
public fun interface ScheduleClock {
    public fun nowEpochMillis(): Long
}

public object SystemScheduleClock : ScheduleClock {
    override fun nowEpochMillis(): Long = System.currentTimeMillis()
}
