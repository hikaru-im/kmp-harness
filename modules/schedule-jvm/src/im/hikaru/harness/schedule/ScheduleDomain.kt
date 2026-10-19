package im.hikaru.harness.schedule

import java.time.ZoneId
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal const val MAX_TITLE_LENGTH: Int = 120
internal const val MIN_EVERY_INTERVAL_SECONDS: Long = 60

internal const val REQUIRED_TITLE_MESSAGE: String = "title is required and must be non-empty after trimming."
private const val REQUIRED_PROMPT_MESSAGE: String = "prompt must be non-empty after trimming."

/** Durable title normalization: trimmed, non-empty, at most 120 characters. */
internal fun scheduleTitle(title: String): String {
    val normalized = title.trim()
    if (normalized.isEmpty()) {
        throw ScheduleInputException(ScheduleErrorCode.InvalidPrompt, REQUIRED_TITLE_MESSAGE)
    }
    if (normalized.length > MAX_TITLE_LENGTH) {
        throw ScheduleInputException(
            ScheduleErrorCode.InvalidPrompt,
            "title must be at most $MAX_TITLE_LENGTH characters.",
        )
    }
    return normalized
}

internal fun schedulePrompt(prompt: String): String {
    val normalized = prompt.trim()
    if (normalized.isEmpty()) {
        throw ScheduleInputException(ScheduleErrorCode.InvalidPrompt, REQUIRED_PROMPT_MESSAGE)
    }
    return normalized
}

internal fun normalizeWeekdays(weekdays: List<Int>): List<Int> {
    if (weekdays.isEmpty()) {
        throw ScheduleInputException(
            ScheduleErrorCode.InvalidRule,
            "weekly.weekdays must be a non-empty array of ISO weekday numbers.",
        )
    }
    if (weekdays.any { it !in 1..7 }) {
        throw ScheduleInputException(
            ScheduleErrorCode.InvalidRule,
            "weekly.weekdays must contain ISO weekday numbers 1-7.",
        )
    }
    if (weekdays.size != weekdays.toSet().size) {
        throw ScheduleInputException(
            ScheduleErrorCode.InvalidRule,
            "weekly.weekdays must not contain duplicate weekdays.",
        )
    }
    return weekdays.sorted()
}

private fun requireRepresentableNow(now: Long) {
    if (now !in MIN_SCHEDULE_INSTANT..MAX_SCHEDULE_INSTANT) {
        throw ScheduleInputException(
            ScheduleErrorCode.TimeOutOfRange,
            "The current time is outside the representable four-digit-year range.",
        )
    }
}

private fun futureInstant(epoch: Long, now: Long): Long {
    if (epoch !in MIN_SCHEDULE_INSTANT..MAX_SCHEDULE_INSTANT) {
        throw ScheduleInputException(
            ScheduleErrorCode.TimeOutOfRange,
            "The scheduled time must be representable as a four-digit-year UTC instant.",
        )
    }
    if (epoch <= now) {
        throw ScheduleInputException(
            ScheduleErrorCode.NotFuture,
            "The scheduled time must be strictly in the future.",
        )
    }
    return epoch
}

/** Creates one immutable record for exactly the supplied selector. */
internal fun createScheduleRecord(
    id: ScheduleId,
    selector: ScheduleSelector,
    now: Long,
    title: String,
    prompt: String,
): ScheduleRecord {
    requireRepresentableNow(now)
    val normalizedTitle = scheduleTitle(title)
    val normalizedPrompt = schedulePrompt(prompt)
    return when (selector) {
        is ScheduleSelector.After -> {
            val seconds = selector.seconds
            if (seconds <= 0) {
                throw ScheduleInputException(
                    ScheduleErrorCode.InvalidRule,
                    "after_seconds must be a positive safe integer.",
                )
            }
            val millis = if (seconds > (Long.MAX_VALUE - now) / 1_000) {
                throw ScheduleInputException(
                    ScheduleErrorCode.TimeOutOfRange,
                    "The scheduled time must be representable as a four-digit-year UTC instant.",
                )
            } else {
                seconds * 1_000
            }
            ScheduleRecord.After(
                id = id,
                title = normalizedTitle,
                prompt = normalizedPrompt,
                scheduledAt = futureInstant(now + millis, now),
                afterSeconds = seconds,
            )
        }

        is ScheduleSelector.At -> ScheduleRecord.At(
            id = id,
            title = normalizedTitle,
            prompt = normalizedPrompt,
            scheduledAt = resolveAt(selector.input, now),
        )

        is ScheduleSelector.Every -> {
            val seconds = selector.seconds
            if (seconds < MIN_EVERY_INTERVAL_SECONDS) {
                throw ScheduleInputException(
                    ScheduleErrorCode.FrequencyTooHigh,
                    "every_seconds must be at least $MIN_EVERY_INTERVAL_SECONDS.",
                )
            }
            if (seconds > (Long.MAX_VALUE - now) / 1_000) {
                throw ScheduleInputException(
                    ScheduleErrorCode.TimeOutOfRange,
                    "The scheduled time must be representable as a four-digit-year UTC instant.",
                )
            }
            ScheduleRecord.Every(
                id = id,
                title = normalizedTitle,
                prompt = normalizedPrompt,
                scheduledAt = futureInstant(now + seconds * 1_000, now),
                everySeconds = seconds,
            )
        }

        is ScheduleSelector.Daily -> {
            val zoneName = canonicalTimeZone(selector.timeZone)
            val zone = ZoneId.of(zoneName)
            val target = nextDaily(selector.time, zone, now)
                ?: throw ScheduleInputException(
                    ScheduleErrorCode.TimeOutOfRange,
                    "No future daily occurrence is representable as a four-digit-year UTC instant.",
                )
            ScheduleRecord.Daily(
                id = id,
                title = normalizedTitle,
                prompt = normalizedPrompt,
                scheduledAt = target,
                time = selector.time,
                timeZone = zoneName,
            )
        }

        is ScheduleSelector.Weekly -> {
            val zoneName = canonicalTimeZone(selector.timeZone)
            val zone = ZoneId.of(zoneName)
            val weekdays = normalizeWeekdays(selector.weekdays)
            val target = nextWeekly(selector.time, zone, weekdays.toSet(), now)
                ?: throw ScheduleInputException(
                    ScheduleErrorCode.TimeOutOfRange,
                    "No future weekly occurrence is representable as a four-digit-year UTC instant.",
                )
            ScheduleRecord.Weekly(
                id = id,
                title = normalizedTitle,
                prompt = normalizedPrompt,
                scheduledAt = target,
                time = selector.time,
                timeZone = zoneName,
                weekdays = weekdays,
            )
        }

        is ScheduleSelector.Cron -> {
            val zoneName = canonicalTimeZone(selector.timeZone)
            val parsed = parseCronExpression(selector.expression)
            val target = nextCronTarget(parsed, ZoneId.of(zoneName), now)
                ?: throw ScheduleInputException(
                    ScheduleErrorCode.TimeOutOfRange,
                    "No future cron occurrence is representable as a four-digit-year UTC instant.",
                )
            ScheduleRecord.Cron(
                id = id,
                title = normalizedTitle,
                prompt = normalizedPrompt,
                scheduledAt = target,
                expression = parsed.expression,
                timeZone = zoneName,
            )
        }
    }
}

/** Outcome of resolving an in-place update; `record == null` means no record is returned. */
internal data class ScheduleUpdateResolution(
    val record: ScheduleRecord?,
    val updated: Boolean,
    val code: String? = null,
)

internal const val SCHEDULE_NOT_FOUND: String = "schedule_not_found"
internal const val SCHEDULE_ENDED: String = "schedule_ended"
internal const val SCHEDULE_CONFLICT: String = "schedule_conflict"

/**
 * Resolves an update against the exact current record. Equivalent timing keeps the
 * committed target and stored spelling; omitted content keeps its stored value.
 */
internal fun resolveScheduleUpdate(
    current: ScheduleRecord,
    expected: ScheduleRecord,
    change: ScheduleSelector?,
    now: Long,
    title: String? = null,
    prompt: String? = null,
): ScheduleUpdateResolution {
    if (current != expected) {
        return ScheduleUpdateResolution(record = null, updated = false, code = SCHEDULE_CONFLICT)
    }
    val retainedTitle = scheduleTitle(current.title)
    val resolvedTitle = if (title == null) retainedTitle else scheduleTitle(title)
    val resolvedPrompt = if (prompt == null) schedulePrompt(current.prompt) else schedulePrompt(prompt)
    if (change == null) {
        val record = current.withContent(resolvedTitle, resolvedPrompt)
        return ScheduleUpdateResolution(record = record, updated = record != current)
    }
    requireRepresentableNow(now)
    val record = when (change) {
        is ScheduleSelector.After ->
            throw ScheduleInputException(
                ScheduleErrorCode.InvalidSelector,
                "Timing change must be an object with exactly one supported timing selector.",
            )

        is ScheduleSelector.At -> {
            // Target equality is a no-op even for an overdue record, so parse the
            // input without the creation-time future check first.
            val target = parseAtInstant(change.input)
            if ((current.kind == ScheduleKind.After || current.kind == ScheduleKind.At) &&
                target == current.scheduledAt
            ) {
                current.withContent(resolvedTitle, resolvedPrompt)
            } else {
                createScheduleRecord(current.id, change, now, resolvedTitle, resolvedPrompt)
            }
        }

        is ScheduleSelector.Every -> {
            if (current is ScheduleRecord.Every && current.everySeconds == change.seconds) {
                current.withContent(resolvedTitle, resolvedPrompt)
            } else {
                createScheduleRecord(current.id, change, now, resolvedTitle, resolvedPrompt)
            }
        }

        is ScheduleSelector.Daily -> {
            val zone = canonicalTimeZone(change.timeZone)
            if (current is ScheduleRecord.Daily &&
                current.time == change.time &&
                canonicalTimeZone(current.timeZone) == zone
            ) {
                current.withContent(resolvedTitle, resolvedPrompt)
            } else {
                createScheduleRecord(current.id, change, now, resolvedTitle, resolvedPrompt)
            }
        }

        is ScheduleSelector.Weekly -> {
            val zone = canonicalTimeZone(change.timeZone)
            val weekdays = normalizeWeekdays(change.weekdays)
            if (current is ScheduleRecord.Weekly &&
                current.time == change.time &&
                canonicalTimeZone(current.timeZone) == zone &&
                current.weekdays == weekdays
            ) {
                current.withContent(resolvedTitle, resolvedPrompt)
            } else {
                createScheduleRecord(current.id, change, now, resolvedTitle, resolvedPrompt)
            }
        }

        is ScheduleSelector.Cron -> {
            val zone = canonicalTimeZone(change.timeZone)
            val expression = canonicalizeCronExpression(change.expression)
            if (current is ScheduleRecord.Cron &&
                current.expression == expression &&
                canonicalTimeZone(current.timeZone) == zone
            ) {
                current.withContent(resolvedTitle, resolvedPrompt)
            } else {
                createScheduleRecord(current.id, change, now, resolvedTitle, resolvedPrompt)
            }
        }
    }
    return ScheduleUpdateResolution(record = record, updated = record != current)
}

/** One recurring decision: the latest due occurrence and the next committed target. */
internal data class RecurringOccurrence(
    val occurrenceAt: Long,
    val nextScheduledAt: Long? = null,
)

internal fun resolveRecurringOccurrence(
    record: ScheduleRecord,
    acceptedAt: Long,
): RecurringOccurrence {
    requireRepresentableNow(acceptedAt)
    val savedTarget = record.scheduledAt
    if (acceptedAt < savedTarget) {
        error("Schedule dispatch cannot precede the active scheduledAt")
    }
    return when (record) {
        is ScheduleRecord.After, is ScheduleRecord.At ->
            error("One-shot schedules do not resolve a recurring occurrence")

        is ScheduleRecord.Every -> {
            val interval = record.everySeconds * 1_000
            val steps = (acceptedAt - savedTarget) / interval
            val occurrence = savedTarget + steps * interval
            val next = occurrence + interval
            if (next < occurrence || next > MAX_SCHEDULE_INSTANT) {
                RecurringOccurrence(occurrenceAt = occurrence)
            } else {
                RecurringOccurrence(occurrenceAt = occurrence, nextScheduledAt = next)
            }
        }

        is ScheduleRecord.Daily -> {
            val zone = ZoneId.of(record.timeZone)
            val occurrence = latestWallClock(
                acceptedAt = acceptedAt,
                savedTarget = savedTarget,
                zone = zone,
                matches = { true },
                time = record.time,
            )
            val excluded = localDateOf(occurrence, zone)
            RecurringOccurrence(
                occurrenceAt = occurrence,
                nextScheduledAt = nextDaily(record.time, zone, acceptedAt, excludeDate = excluded),
            )
        }

        is ScheduleRecord.Weekly -> {
            val zone = ZoneId.of(record.timeZone)
            val weekdays = record.weekdays.toSet()
            val occurrence = latestWallClock(
                acceptedAt = acceptedAt,
                savedTarget = savedTarget,
                zone = zone,
                matches = { it.dayOfWeek.value in weekdays },
                time = record.time,
            )
            val excluded = localDateOf(occurrence, zone)
            RecurringOccurrence(
                occurrenceAt = occurrence,
                nextScheduledAt = nextWeekly(record.time, zone, weekdays, acceptedAt, excludeDate = excluded),
            )
        }

        is ScheduleRecord.Cron -> {
            val zone = ZoneId.of(record.timeZone)
            val parsed = parseCronExpression(record.expression)
            RecurringOccurrence(
                occurrenceAt = latestCronOccurrence(parsed, zone, acceptedAt, savedTarget),
                nextScheduledAt = nextCronTarget(parsed, zone, acceptedAt),
            )
        }
    }
}

/** `scheduled` until the committed instant, then `overdue` (equality counts). */
internal fun scheduleState(record: ScheduleRecord, now: Long): String =
    if (now >= record.scheduledAt) "overdue" else "scheduled"

/** Model-visible one-shot framing, byte-compatible with DSH. */
internal fun renderReminderFraming(record: ScheduleRecord, occurrenceAt: Long): String =
    listOf(
        "[SCHEDULE REMINDER]",
        "This is a scheduled message from the user",
        "schedule_id_json: ${JsonPrimitive(record.id.value)}",
        "occurrence_at: ${formatInstant(occurrenceAt)}",
        "reminder_prompt_json: ${JsonPrimitive(record.prompt)}",
    ).joinToString("\n")

/** Model-visible recurring batch framing, byte-compatible with DSH. */
internal fun renderRecurringReminderBatchFraming(
    reminders: List<Pair<ScheduleRecord, Long>>,
): String {
    val payload = buildJsonArray {
        reminders.forEach { (record, occurrenceAt) ->
            add(
                buildJsonObject {
                    put("schedule_id", record.id.value)
                    put("occurrence_at", formatInstant(occurrenceAt))
                    put("reminder_prompt", record.prompt)
                },
            )
        }
    }
    return listOf(
        "[SCHEDULE REMINDER BATCH]",
        "This is a scheduled message from the user",
        "reminders_json: $payload",
    ).joinToString("\n")
}
