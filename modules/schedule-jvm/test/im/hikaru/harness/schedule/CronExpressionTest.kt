package im.hikaru.harness.schedule

import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CronExpressionTest {
    @Test
    fun canonicalSpellingKeepsStarPathsAndCompactsLists() {
        assertEquals("*/15 9-17 * * 1-5", canonicalizeCronExpression("*/15 9-17 * * 1-5"))
        assertEquals("0 0 * * 0", canonicalizeCronExpression("0 0 * * 7"))
        assertEquals("0 0 * * 1-3", canonicalizeCronExpression("0 0 * * 1,2,3"))
        assertEquals("*/15,7 * * * *", canonicalizeCronExpression("*/15,7 * * * *"))
        assertEquals("0 0 * * *", canonicalizeCronExpression("0   0  * * *"))
    }

    @Test
    fun invalidExpressionsCarryDSHValidationMessages() {
        for (invalid in listOf("", "0 0 * *", "60 * * * *", "*/0 * * * *", "5/15 * * * *", "* * * * 8")) {
            val error = assertFailsWith<ScheduleInputException>(invalid) {
                parseCronExpression(invalid)
            }
            assertEquals(ScheduleErrorCode.InvalidRule, error.code)
        }
    }

    @Test
    fun nextTargetUsesZoneLocalTimeAndSkipsTheGapDate() {
        val utc = ZoneId.of("UTC")
        assertEquals(
            instant("2026-01-02T09:00:00Z"),
            nextCronTarget(parseCronExpression("0 9 * * *"), utc, instant("2026-01-01T10:00:00Z")),
        )
        assertEquals(
            instant("2026-02-01T09:00:00Z"),
            nextCronTarget(parseCronExpression("0 9 1 * *"), utc, instant("2026-01-01T10:00:00Z")),
        )
        assertEquals(
            instant("2028-02-29T00:00:00Z"),
            nextCronTarget(parseCronExpression("0 0 29 2 *"), utc, instant("2026-01-01T00:00:00Z")),
        )
        assertEquals(
            instant("2026-03-09T06:30:00Z"),
            nextCronTarget(
                parseCronExpression("30 2 * * *"),
                ZoneId.of("America/New_York"),
                instant("2026-03-07T12:00:00Z"),
            ),
        )
    }

    @Test
    fun bothRestrictedDayFieldsAreOredButStarLedFieldsAreAnded() {
        val utc = ZoneId.of("UTC")
        // Either the 1st or a Friday.
        assertEquals(
            instant("2026-01-02T00:00:00Z"),
            nextCronTarget(parseCronExpression("0 0 1 * 5"), utc, instant("2026-01-01T00:00:00Z")),
        )
        // A star-led day-of-month keeps only Mondays despite the OR-looking shape.
        assertEquals(
            instant("2026-01-05T00:00:00Z"),
            nextCronTarget(parseCronExpression("0 0 * * 1"), utc, instant("2026-01-01T00:00:00Z")),
        )
        // Two stars match every date.
        assertEquals(
            instant("2026-01-02T00:00:00Z"),
            nextCronTarget(parseCronExpression("0 0 * * *"), utc, instant("2026-01-01T00:00:00Z")),
        )
    }

    @Test
    fun latestOccurrenceReturnsOnlyTheMostRecentDueInstant() {
        val utc = ZoneId.of("UTC")
        val hourly = parseCronExpression("0 * * * *")
        assertEquals(
            instant("2026-01-01T05:00:00Z"),
            latestCronOccurrence(hourly, utc, instant("2026-01-01T05:45:00Z"), instant("2026-01-01T03:00:00Z")),
        )
        assertEquals(
            instant("2026-01-02T05:00:00Z"),
            latestCronOccurrence(hourly, utc, instant("2026-01-02T05:45:00Z"), instant("2026-01-02T05:00:00Z")),
        )
    }
}
