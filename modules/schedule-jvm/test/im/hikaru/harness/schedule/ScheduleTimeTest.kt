package im.hikaru.harness.schedule

import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScheduleTimeTest {
    @Test
    fun clockTimeNormalizesToOneToThreeFractionalDigitsAsMilliseconds() {
        assertEquals(ScheduleTime(9, 0, 0, 0), parseClockTime("09:00:00", "daily"))
        assertEquals("09:00:00.500", parseClockTime("09:00:00.5", "daily").toString())
        assertEquals("09:00:00.050", parseClockTime("09:00:00.05", "daily").toString())
        assertEquals("23:59:59.999", parseClockTime("23:59:59.999", "daily").toString())
    }

    @Test
    fun clockTimeRejectsLeapSecondsAndOutOfRangeValues() {
        for (invalid in listOf("24:00:00", "23:60:00", "23:59:60", "9:00:00", "09:00", "09:00:00.1234")) {
            assertFailsWith<ScheduleInputException>(invalid) {
                parseClockTime(invalid, "daily")
            }
        }
    }

    @Test
    fun timeZoneAcceptsUtcAndAreaNamesButNotOffsetsOrShortIds() {
        assertEquals("UTC", canonicalTimeZone("UTC"))
        assertEquals("Asia/Shanghai", canonicalTimeZone("Asia/Shanghai"))
        assertEquals("America/New_York", canonicalTimeZone("America/New_York"))
        for (invalid in listOf("", "utc", "GMT+8", "+08:00", " Asia/Shanghai", "Asia/Shanghai ", "Mars/Olympus")) {
            assertFailsWith<ScheduleInputException>(invalid) {
                canonicalTimeZone(invalid)
            }
        }
    }

    @Test
    fun localInstantSkipsDstGapsAndUsesTheEarlierOverlap() {
        val newYork = ZoneId.of("America/New_York")

        // 2026-03-08 02:30 does not exist in New York.
        assertNull(localInstant(LocalDate.of(2026, 3, 8), ScheduleTime(2, 30, 0, 0), newYork))
        // 2026-11-01 01:30 happens twice; DSH chooses the earlier (EDT) instant.
        assertEquals(
            instant("2026-11-01T05:30:00Z"),
            localInstant(LocalDate.of(2026, 11, 1), ScheduleTime(1, 30, 0, 0), newYork),
        )
    }

    @Test
    fun dailyRuleSkipsTheGapDateAndStillFiresTheNextDay() {
        val zone = ZoneId.of("America/New_York")
        val now = instant("2026-03-08T00:00:00Z")

        assertEquals(
            instant("2026-03-09T06:30:00Z"),
            nextDaily(ScheduleTime(2, 30, 0, 0), zone, now),
        )
    }

    @Test
    fun dailyRuleFiresOnceAtTheEarlierOverlap() {
        val zone = ZoneId.of("America/New_York")
        val now = instant("2026-11-01T00:00:00Z")

        assertEquals(
            instant("2026-11-01T05:30:00Z"),
            nextDaily(ScheduleTime(1, 30, 0, 0), zone, now),
        )
    }

    @Test
    fun weeklyRuleHonorsIsoWeekdaysAndSkipsTheGapDate() {
        val zone = ZoneId.of("America/New_York")
        val now = instant("2026-03-08T00:00:00Z")
        // Sunday 2026-03-08 is in the gap; the next Sunday is 2026-03-15.
        assertEquals(
            instant("2026-03-15T06:30:00Z"),
            nextWeekly(ScheduleTime(2, 30, 0, 0), zone, setOf(7), now),
        )
    }

    @Test
    fun atParsesOffsetsAndRejectsNonFutureOrInvalidValues() {
        val now = instant("2026-01-01T00:00:00Z")
        assertEquals(
            instant("2026-01-01T02:00:00Z"),
            parseAtInstant(ScheduleAtInput.Offset("2026-01-01T10:00:00+08:00")),
        )
        assertEquals(
            instant("2026-01-01T02:00:00Z"),
            resolveAt(ScheduleAtInput.Offset("2026-01-01T10:00:00+08:00"), now),
        )
        assertEquals(
            instant("2026-01-01T02:00:00Z"),
            parseAtInstant(ScheduleAtInput.Local("2026-01-01", "10:00:00", "Asia/Shanghai")),
        )

        val error = assertFailsWith<ScheduleInputException> {
            resolveAt(ScheduleAtInput.Offset("2026-01-01T00:00:00Z"), now)
        }
        assertEquals(ScheduleErrorCode.NotFuture, error.code)
        assertFailsWith<ScheduleInputException> {
            parseAtInstant(ScheduleAtInput.Offset("2026-01-01T10:00:00"))
        }
        assertFailsWith<ScheduleInputException> {
            parseAtInstant(ScheduleAtInput.Offset("0000-01-01T00:00:00Z"))
        }
        assertFailsWith<ScheduleInputException> {
            parseAtInstant(ScheduleAtInput.Local("2026-03-08", "02:30:00", "America/New_York"))
        }
    }

    @Test
    fun instantRenderingIsCanonicalUtcMillis() {
        assertEquals("1970-01-01T00:00:00.000Z", formatInstant(0))
        assertTrue(formatInstant(instant("2026-10-19T12:34:56.789Z")) == "2026-10-19T12:34:56.789Z")
    }
}
