package im.hikaru.harness.schedule

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.serialization.json.JsonPrimitive

private const val CRON_SEARCH_HORIZON_YEARS = 400L

private data class CronFieldSpec(
    val name: String,
    val min: Int,
    val max: Int,
    val canonicalMax: Int,
)

private val CRON_FIELDS = listOf(
    CronFieldSpec("minute", 0, 59, 59),
    CronFieldSpec("hour", 0, 23, 23),
    CronFieldSpec("day-of-month", 1, 31, 31),
    CronFieldSpec("month", 1, 12, 12),
    CronFieldSpec("day-of-week", 0, 7, 6),
)

private data class ParsedCronField(
    val values: List<Int>,
    val canonical: String,
    val star: Boolean,
)

internal class ParsedCronExpression(
    val expression: String,
    val minutes: List<Int>,
    val hours: List<Int>,
    val daysOfMonth: List<Int>,
    val months: List<Int>,
    val daysOfWeek: List<Int>,
    val dayOfMonthStar: Boolean,
    val dayOfWeekStar: Boolean,
) {
    /** Local times of day in ascending wall-clock order. */
    val times: List<ScheduleTime> =
        hours.flatMap { hour -> minutes.map { minute -> ScheduleTime(hour, minute, 0, 0) } }

    fun matchesDate(date: LocalDate): Boolean {
        if (date.monthValue !in months) return false
        val dayOfMonth = date.dayOfMonth in daysOfMonth
        val dayOfWeek = (date.dayOfWeek.value % 7) in daysOfWeek
        // Vixie: two restricted day fields are ORed; a star-led field makes the pair ANDed.
        return if (!dayOfMonthStar && !dayOfWeekStar) dayOfMonth || dayOfWeek else dayOfMonth && dayOfWeek
    }
}

internal fun canonicalizeCronExpression(expression: String): String = parseCronExpression(expression).expression

internal fun parseCronExpression(expression: String): ParsedCronExpression {
    if (expression.isEmpty() || expression.trim() != expression) {
        throw ScheduleInputException(
            ScheduleErrorCode.InvalidRule,
            "cron.expression must be a non-empty trimmed string.",
        )
    }
    val fields = expression.split(Regex("""\s+"""))
    if (fields.size != 5) {
        throw ScheduleInputException(
            ScheduleErrorCode.InvalidRule,
            "cron.expression must contain exactly five whitespace-separated fields: " +
                "minute hour day-of-month month day-of-week.",
        )
    }
    val parsed = fields.mapIndexed { index, field -> parseCronField(field, CRON_FIELDS[index]) }
    return ParsedCronExpression(
        expression = parsed.joinToString(" ") { it.canonical },
        minutes = parsed[0].values,
        hours = parsed[1].values,
        daysOfMonth = parsed[2].values,
        months = parsed[3].values,
        daysOfWeek = parsed[4].values,
        dayOfMonthStar = parsed[2].star,
        dayOfWeekStar = parsed[4].star,
    )
}

private fun parseCronField(raw: String, spec: CronFieldSpec): ParsedCronField {
    val values = cronFieldValues(raw, spec)
    val star = raw.startsWith("*")
    return ParsedCronField(
        values = values,
        canonical = if (star) encodeStarCronField(values, spec) else encodeCronField(values),
        star = star,
    )
}

private fun invalidCronField(spec: CronFieldSpec, element: String): ScheduleInputException =
    ScheduleInputException(
        ScheduleErrorCode.InvalidRule,
        "cron.expression ${spec.name} field element ${JsonPrimitive(element)} must be *, a value, a-b, */n, " +
            "a-b/n, or a comma-separated list of those.",
    )

private fun cronFieldValue(text: String, spec: CronFieldSpec): Int {
    val value = text.toIntOrNull()
    if (value == null || value < spec.min || value > spec.max) {
        throw ScheduleInputException(
            ScheduleErrorCode.InvalidRule,
            "cron.expression ${spec.name} field value $text is outside ${spec.min}-${spec.max}.",
        )
    }
    return value
}

private fun cronFieldStep(text: String, spec: CronFieldSpec): Int {
    val step = text.toIntOrNull()
    if (step == null || step < 1) {
        throw ScheduleInputException(
            ScheduleErrorCode.InvalidRule,
            "cron.expression ${spec.name} field step must be a positive integer.",
        )
    }
    return step
}

private fun cronFieldValues(raw: String, spec: CronFieldSpec): List<Int> {
    val matched = linkedSetOf<Int>()
    for (element in raw.split(',')) {
        if (element.isEmpty()) {
            throw ScheduleInputException(
                ScheduleErrorCode.InvalidRule,
                "cron.expression ${spec.name} field must not contain an empty list element.",
            )
        }
        if (element == "*") {
            for (value in spec.min..spec.max) matched += value
            continue
        }
        if (element.startsWith("*")) {
            val stepText = Regex("""^\*/(\d+)$""").matchEntire(element)
                ?: throw invalidCronField(spec, element)
            val step = cronFieldStep(stepText.groupValues[1], spec)
            var value = spec.min
            while (value <= spec.max) {
                matched += value
                value += step
            }
            continue
        }
        val match = Regex("""^(\d+)(?:-(\d+))?(?:/(\d+))?$""").matchEntire(element)
            ?: throw invalidCronField(spec, element)
        val start = cronFieldValue(match.groupValues[1], spec)
        val endText = match.groupValues[2]
        val stepText = match.groupValues[3]
        if (endText.isEmpty()) {
            if (stepText.isNotEmpty()) throw invalidCronField(spec, element)
            matched += start
            continue
        }
        val last = cronFieldValue(endText, spec)
        if (start > last) {
            throw ScheduleInputException(
                ScheduleErrorCode.InvalidRule,
                "cron.expression ${spec.name} field range $start-$last is inverted.",
            )
        }
        val step = if (stepText.isEmpty()) 1 else cronFieldStep(stepText, spec)
        var value = start
        while (value <= last) {
            matched += value
            value += step
        }
    }
    return matched
        .map { if (spec.canonicalMax != spec.max && it == spec.max) spec.min else it }
        .distinct()
        .sorted()
}

private fun encodeCronField(values: List<Int>): String {
    val first = values.first()
    if (values.size == 1) return first.toString()
    val last = values.last()
    val step = values[1] - first
    var uniform = true
    for (index in 2 until values.size) {
        if (values[index] - values[index - 1] != step) {
            uniform = false
            break
        }
    }
    if (uniform) {
        return if (step == 1) "$first-$last" else "$first-$last/$step"
    }
    val parts = mutableListOf<String>()
    var runStart = first
    for (index in 1 until values.size) {
        val current = values[index]
        val previous = values[index - 1]
        if (current == previous + 1) continue
        parts += if (runStart == previous) runStart.toString() else "$runStart-$previous"
        runStart = current
    }
    val final = values.last()
    parts += if (runStart == final) runStart.toString() else "$runStart-$final"
    return parts.joinToString(",")
}

private fun starWalk(spec: CronFieldSpec, step: Int): List<Int> {
    val walked = linkedSetOf<Int>()
    var value = spec.min
    while (value <= spec.max) {
        walked += if (spec.canonicalMax != spec.max && value == spec.max) spec.min else value
        value += step
    }
    return walked.sorted()
}

private fun encodeStarCronField(values: List<Int>, spec: CronFieldSpec): String {
    if (values.size == spec.canonicalMax - spec.min + 1) return "*"
    val present = values.toSet()
    var bestStep: Int? = null
    var bestWalk: List<Int> = emptyList()
    for (step in 1..(spec.max - spec.min + 1)) {
        val walk = starWalk(spec, step)
        if (walk.size > bestWalk.size && walk.all { it in present }) {
            bestStep = step
            bestWalk = walk
        }
    }
    val step = bestStep
        ?: throw ScheduleInputException(
            ScheduleErrorCode.InvalidRule,
            "${spec.name} cannot keep a leading `*`.",
        )
    val walked = bestWalk.toSet()
    val remaining = values.filter { it !in walked }
    val walkText = "*/$step"
    return if (remaining.isEmpty()) walkText else "$walkText,${encodeCronField(remaining)}"
}

/** First strictly future cron target, bounded by 400 years and the four-digit ceiling. */
internal fun nextCronTarget(
    parsed: ParsedCronExpression,
    zone: ZoneId,
    floor: Long,
): Long? {
    val firstDate = localDateOf(floor, zone)
    val floorTime = Instant.ofEpochMilli(floor).atZone(zone).toLocalTime()
    val ceiling = LocalDate.of(9999, 12, 31).plusDays(1)
    val horizon = firstDate.plusYears(CRON_SEARCH_HORIZON_YEARS)
    val lastDate = if (horizon.isBefore(ceiling)) horizon else ceiling
    var date = firstDate
    while (!date.isAfter(lastDate)) {
        if (date.monthValue !in parsed.months) {
            date = LocalDate.of(date.year, date.monthValue, 1).plusMonths(1)
            continue
        }
        if (parsed.matchesDate(date)) {
            for (time in parsed.times) {
                if (date == firstDate && localTimeOf(time).isBefore(floorTime)) continue
                val target = localInstant(date, time, zone) ?: continue
                if (target > MAX_SCHEDULE_INSTANT) return null
                if (target >= MIN_SCHEDULE_INSTANT && target > floor) return target
            }
        }
        date = date.plusDays(1)
    }
    return null
}

/**
 * Latest matching cron occurrence at or before [decision], floored by [savedTarget].
 * Only the most recent occurrence is returned; missed history is never replayed.
 */
internal fun latestCronOccurrence(
    parsed: ParsedCronExpression,
    zone: ZoneId,
    decision: Long,
    savedTarget: Long,
): Long {
    val timesAscending = parsed.times
    var date = localDateOf(decision, ZoneOffset.UTC).plusDays(1)
    val floorDate = localDateOf(MIN_SCHEDULE_INSTANT, zone)
    val horizon = date.minusYears(CRON_SEARCH_HORIZON_YEARS)
    val firstDate = if (horizon.isAfter(floorDate)) horizon else floorDate
    while (!date.isBefore(firstDate)) {
        if (date.monthValue !in parsed.months) {
            date = LocalDate.of(date.year, date.monthValue, 1).minusDays(1)
            continue
        }
        if (parsed.matchesDate(date)) {
            val earliest = timesAscending.asSequence()
                .mapNotNull { localInstant(date, it, zone) }
                .firstOrNull()
            if (earliest != null && earliest > decision) {
                date = date.minusDays(1)
                continue
            }
            for (time in timesAscending.asReversed()) {
                val candidate = localInstant(date, time, zone) ?: continue
                if (candidate > decision || candidate < MIN_SCHEDULE_INSTANT) continue
                return maxOf(candidate, savedTarget)
            }
        }
        date = date.minusDays(1)
    }
    return savedTarget
}

private fun localTimeOf(time: ScheduleTime): LocalTime =
    LocalTime.of(time.hour, time.minute, time.second, time.millisecond * 1_000_000)
