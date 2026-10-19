package im.hikaru.harness.schedule

import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

/** Four-digit-year bounds shared by every durable decision. */
internal val MIN_SCHEDULE_INSTANT: Long = Instant.parse("0001-01-01T00:00:00Z").toEpochMilli()
internal val MAX_SCHEDULE_INSTANT: Long = Instant.parse("9999-12-31T23:59:59.999Z").toEpochMilli()

private val UTC_INSTANT_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'")
        .withResolverStyle(ResolverStyle.STRICT)
        .withZone(ZoneOffset.UTC)

private val LOCAL_DATE_FORMAT: DateTimeFormatter =
    DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT)

private val LOCAL_CLOCK_TIME = Regex("""^(?:[01]\d|2[0-3]):[0-5]\d:[0-5]\d(?:\.\d{1,3})?$""")
private val OFFSET_INSTANT = Regex(
    """^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})(?:\.(\d{1,3}))?(Z|[+-]\d{2}:\d{2})$""",
)

/** Canonical DSH instant: UTC with exactly milliseconds. */
internal fun formatInstant(epochMillis: Long): String =
    UTC_INSTANT_FORMAT.format(Instant.ofEpochMilli(epochMillis))

internal fun localDateOf(epochMillis: Long, zone: ZoneId): LocalDate =
    Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

/**
 * Converts one local wall-clock value to an instant.
 *
 * A value inside a DST gap has no valid offset and returns `null` (the caller
 * skips that local date). An overlap resolves to the earlier offset, matching
 * DSH's `disambiguation: 'earlier'`.
 */
internal fun localInstant(date: LocalDate, time: ScheduleTime, zone: ZoneId): Long? {
    val local = LocalDateTime.of(
        date.year,
        date.monthValue,
        date.dayOfMonth,
        time.hour,
        time.minute,
        time.second,
        time.millisecond * 1_000_000,
    )
    val zoned = try {
        local.atZone(zone)
    } catch (_: DateTimeException) {
        return null
    }
    // A gap is shifted forward by java.time; DSH skips it instead.
    if (zoned.toLocalDateTime() != local) return null
    val epoch = zoned.toInstant().toEpochMilli()
    return epoch.takeIf { it in MIN_SCHEDULE_INSTANT..MAX_SCHEDULE_INSTANT }
}

/** Parses `HH:mm:ss` with optional 1-3 fractional digits and normalizes to ms. */
internal fun parseClockTime(value: String, selector: String): ScheduleTime {
    if (!LOCAL_CLOCK_TIME.matches(value)) {
        throw ScheduleInputException(
            ScheduleErrorCode.InvalidRule,
            "$selector.time must use HH:mm:ss with optional 1-3 fractional digits, " +
                "without leap seconds or 24:00.",
        )
    }
    val main = value.substringBefore('.')
    val fraction = value.substringAfter('.', "")
    val parts = main.split(':')
    val millis = when (fraction.length) {
        0 -> 0
        1 -> fraction.toInt() * 100
        2 -> fraction.toInt() * 10
        else -> fraction.toInt()
    }
    return ScheduleTime(parts[0].toInt(), parts[1].toInt(), parts[2].toInt(), millis)
}

/**
 * Validates an explicit IANA zone. Offset strings and short ids are rejected,
 * matching DSH's `UTC or Area/Location` rule. The stored spelling is preserved;
 * alias canonicalization is an intentional local difference.
 */
internal fun canonicalTimeZone(value: String): String {
    if (value.isEmpty() || value.trim() != value || (value != "UTC" && '/' !in value)) {
        throw ScheduleInputException(
            ScheduleErrorCode.InvalidTimeZone,
            "time_zone must be UTC or a valid IANA Area/Location name.",
        )
    }
    try {
        ZoneId.of(value)
    } catch (_: DateTimeException) {
        throw ScheduleInputException(
            ScheduleErrorCode.InvalidTimeZone,
            "time_zone must be UTC or a valid IANA Area/Location name.",
        )
    }
    return value
}

/** Parses an `at` selector without applying the creation-time future check. */
internal fun parseAtInstant(input: ScheduleAtInput): Long = when (input) {
    is ScheduleAtInput.Offset -> parseOffsetInstant(input.text)
    is ScheduleAtInput.Local -> parseLocalInstant(input)
}

/** Resolves an `at` selector to a representable instant. */
internal fun resolveAt(input: ScheduleAtInput, now: Long): Long {
    val epoch = parseAtInstant(input)
    if (epoch <= now) {
        throw ScheduleInputException(
            ScheduleErrorCode.NotFuture,
            "The scheduled time must be strictly in the future.",
        )
    }
    return requireRepresentable(epoch)
}

private fun parseOffsetInstant(text: String): Long {
    val match = OFFSET_INSTANT.matchEntire(text)
        ?: throw ScheduleInputException(
            ScheduleErrorCode.InvalidRule,
            "at must be an RFC 3339 instant with a Z or numeric offset, " +
                "or an object with date, time, and time_zone.",
        )
    val offsetText = match.groupValues[8]
    if (offsetText == "-00:00") {
        throw ScheduleInputException(ScheduleErrorCode.InvalidRule, "at offset must not be -00:00.")
    }
    val year = match.groupValues[1].toInt()
    if (year == 0) {
        throw ScheduleInputException(ScheduleErrorCode.InvalidRule, "at must use a four-digit year from 0001.")
    }
    val date = try {
        LocalDate.of(
            year,
            match.groupValues[2].toInt(),
            match.groupValues[3].toInt(),
        )
    } catch (_: DateTimeException) {
        throw ScheduleInputException(ScheduleErrorCode.InvalidRule, "at contains an invalid calendar date.")
    }
    val time = parseOffsetClockTime(match.groupValues[4], match.groupValues[5], match.groupValues[6], match.groupValues[7])
    val offset = try {
        if (offsetText == "Z") ZoneOffset.UTC else ZoneOffset.of(offsetText)
    } catch (_: DateTimeException) {
        throw ScheduleInputException(ScheduleErrorCode.InvalidRule, "at contains an invalid numeric offset.")
    }
    return LocalDateTime.of(date, time).toInstant(offset).toEpochMilli()
}

private fun parseOffsetClockTime(hour: String, minute: String, second: String, fraction: String): LocalTime {
    val millis = when (fraction.length) {
        0 -> 0
        1 -> fraction.toInt() * 100
        2 -> fraction.toInt() * 10
        else -> fraction.toInt()
    }
    return try {
        LocalTime.of(hour.toInt(), minute.toInt(), second.toInt(), millis * 1_000_000)
    } catch (_: DateTimeException) {
        throw ScheduleInputException(ScheduleErrorCode.InvalidRule, "at contains an invalid clock time.")
    }
}

private fun parseLocalInstant(input: ScheduleAtInput.Local): Long {
    val date = try {
        LocalDate.parse(input.date, LOCAL_DATE_FORMAT)
    } catch (_: DateTimeException) {
        throw ScheduleInputException(
            ScheduleErrorCode.InvalidRule,
            "at.date must use YYYY-MM-DD and name a real calendar date.",
        )
    }
    if (date.year == 0) {
        throw ScheduleInputException(
            ScheduleErrorCode.InvalidRule,
            "at.date must use a four-digit year from 0001.",
        )
    }
    val time = parseClockTime(input.time, "at")
    val zoneName = canonicalTimeZone(input.timeZone)
    val zone = ZoneId.of(zoneName)
    return localInstant(date, time, zone)
        ?: throw ScheduleInputException(
            ScheduleErrorCode.InvalidRule,
            "The local at time does not exist in the selected time zone.",
        )
}

internal fun requireRepresentable(epoch: Long): Long {
    if (epoch !in MIN_SCHEDULE_INSTANT..MAX_SCHEDULE_INSTANT) {
        throw ScheduleInputException(
            ScheduleErrorCode.TimeOutOfRange,
            "The scheduled time must fall within years 0001-9999 UTC.",
        )
    }
    return epoch
}

/** First daily occurrence strictly after [after], optionally skipping [exclude]. */
internal fun nextDaily(
    time: ScheduleTime,
    zone: ZoneId,
    after: Long,
    excludeDate: LocalDate? = null,
): Long? {
    var date = localDateOf(after, zone)
    val last = LocalDate.of(9999, 12, 31)
    while (!date.isAfter(last)) {
        if (date != excludeDate) {
            val candidate = localInstant(date, time, zone)
            if (candidate != null && candidate > after) return candidate
        }
        date = date.plusDays(1)
        // A daily rule always finds a target within two local dates.
        if (date.isAfter(last)) break
    }
    return null
}

/** First weekly occurrence strictly after [after], optionally skipping [exclude]. */
internal fun nextWeekly(
    time: ScheduleTime,
    zone: ZoneId,
    weekdays: Set<Int>,
    after: Long,
    excludeDate: LocalDate? = null,
): Long? {
    var date = localDateOf(after, zone)
    val last = LocalDate.of(9999, 12, 31)
    var checked = 0
    while (!date.isAfter(last) && checked <= 15) {
        if (date.dayOfWeek.value in weekdays && date != excludeDate) {
            val candidate = localInstant(date, time, zone)
            if (candidate != null && candidate > after) return candidate
        }
        date = date.plusDays(1)
        checked++
    }
    return null
}

/**
 * Latest matching wall-clock occurrence at or before [acceptedAt], floored by
 * [savedTarget]. Only the latest missed occurrence is considered, never each
 * occurrence in between.
 */
internal fun latestWallClock(
    acceptedAt: Long,
    savedTarget: Long,
    zone: ZoneId,
    matches: (LocalDate) -> Boolean,
    time: ScheduleTime,
): Long {
    // An offset is less than a day, so starting one day after the UTC decision
    // and walking back covers date-line rollback plus a full weekly cycle.
    var date = localDateOf(acceptedAt, ZoneOffset.UTC).plusDays(1)
    repeat(16) {
        if (matches(date)) {
            val candidate = localInstant(date, time, zone)
            if (candidate != null && candidate <= acceptedAt) {
                return maxOf(candidate, savedTarget)
            }
        }
        date = date.minusDays(1)
    }
    return savedTarget
}
