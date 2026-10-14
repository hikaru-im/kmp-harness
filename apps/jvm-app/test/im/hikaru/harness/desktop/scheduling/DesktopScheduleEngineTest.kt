package im.hikaru.harness.desktop.scheduling

import im.hikaru.harness.client.scheduling.*
import java.time.Instant
import kotlin.test.*

class DesktopScheduleEngineTest {
    private val engine = ScheduleEngine(DesktopScheduleCalendar())
    private fun instant(value: String): Long = Instant.parse(value).toEpochMilli()

    @Test fun dailyAndWeeklyAreStrictlyFutureAcrossYearAndLeapBoundaries() {
        assertEquals(instant("2027-01-01T09:00:00Z"), engine.nextOccurrence(
            ScheduledRule.Daily(9, 0, "UTC"), instant("2026-12-31T09:00:00Z")))
        assertEquals(instant("2028-02-29T09:00:00Z"), engine.nextOccurrence(
            ScheduledRule.Daily(9, 0, "UTC"), instant("2028-02-28T09:00:00Z")))
        assertEquals(instant("2026-10-12T09:00:00Z"), engine.nextOccurrence(
            ScheduledRule.Weekdays(9, 0, "UTC"), instant("2026-10-09T09:00:00Z")))
        assertEquals(instant("2026-10-14T09:00:00Z"), engine.nextOccurrence(
            ScheduledRule.Weekly(ScheduledWeekday.Wednesday, 9, 0, "UTC"), instant("2026-10-07T09:00:00Z")))
    }

    @Test fun springGapShiftsForwardAndFallOverlapRunsOnlyAtEarlierOffset() {
        val zone = "America/New_York"
        assertEquals(instant("2026-03-08T07:30:00Z"), engine.nextOccurrence(
            ScheduledRule.Daily(2, 30, zone), instant("2026-03-08T05:00:00Z")))
        val overlap = ScheduledRule.Daily(1, 30, zone)
        assertEquals(instant("2026-11-01T05:30:00Z"), engine.nextOccurrence(overlap, instant("2026-11-01T04:00:00Z")))
        // No second invocation at 06:30Z on the same local date.
        assertEquals(instant("2026-11-02T06:30:00Z"), engine.nextOccurrence(overlap, instant("2026-11-01T05:30:00Z")))
        assertEquals(instant("2026-11-02T06:30:00Z"), engine.nextOccurrence(overlap, instant("2026-11-01T06:00:00Z")))
    }

    @Test fun hourlyRemainsFixedDurationAcrossDstAndInvalidZoneNeverFallsBack() {
        val anchor = instant("2026-03-08T06:30:00Z")
        assertEquals(instant("2026-03-08T07:30:00Z"), engine.nextOccurrence(ScheduledRule.Hourly(1, anchor), anchor))
        assertNull(engine.nextOccurrence(ScheduledRule.Daily(9, 0, "Missing/Zone"), anchor))
        assertNull(engine.nextOccurrence(ScheduledRule.Daily(24, 0, "UTC"), anchor))
    }

    @Test fun nextRunLabelUsesTheRuleZoneAndNamesTodayTomorrowAndDates() {
        val calendar = DesktopScheduleCalendar()
        val now = instant("2026-10-11T00:00:00Z")
        assertEquals("今天 01:00", calendar.nextRunLabel(instant("2026-10-11T01:00:00Z"), now, "UTC"))
        assertEquals("明天 09:30", calendar.nextRunLabel(instant("2026-10-12T09:30:00Z"), instant("2026-10-11T10:00:00Z"), "UTC"))
        // Same local date in Asia/Shanghai (UTC+8) even though the instants differ in UTC.
        assertEquals("今天 09:00", calendar.nextRunLabel(instant("2026-10-11T01:00:00Z"), now, "Asia/Shanghai"))
        assertEquals("11月5日 08:05", calendar.nextRunLabel(instant("2026-11-05T08:05:00Z"), now, "UTC"))
        assertEquals("2027年3月5日 08:05", calendar.nextRunLabel(instant("2027-03-05T08:05:00Z"), now, "UTC"))
        assertNull(calendar.nextRunLabel(instant("2026-10-11T01:00:00Z"), now, "Missing/Zone"))
    }

    @Test fun displayZoneIsNotBlankOnTheDesktopAdapter() {
        assertTrue(DesktopScheduleCalendar().displayZoneId.isNotBlank())
    }

    @Test fun relativeRunLabelCoversMinutesHoursTheDayBoundaryAndTheFuture() {
        val calendar = DesktopScheduleCalendar()
        val now = instant("2026-10-11T12:00:00Z")
        assertEquals("20 分钟前", calendar.relativeRunLabel(instant("2026-10-11T11:40:00Z"), now, "UTC"))
        assertEquals("1 分钟前", calendar.relativeRunLabel(now, now, "UTC"))
        assertEquals("2 小时前", calendar.relativeRunLabel(instant("2026-10-11T10:00:00Z"), now, "UTC"))
        // At least one day old, or in the future: the relative branch declines so the day branch owns it.
        assertNull(calendar.relativeRunLabel(instant("2026-10-10T12:00:00Z"), now, "UTC"))
        assertNull(calendar.relativeRunLabel(instant("2026-10-11T12:00:01Z"), now, "UTC"))
    }

    @Test fun runDayLabelNamesTodayYesterdayWeekdaysAndDatedYears() {
        val calendar = DesktopScheduleCalendar()
        val now = instant("2026-10-11T12:00:00Z")
        assertEquals("今天 09:00", calendar.runDayLabel(instant("2026-10-11T09:00:00Z"), now, "UTC"))
        assertEquals("昨天 09:00", calendar.runDayLabel(instant("2026-10-10T09:00:00Z"), now, "UTC"))
        assertEquals("星期四", calendar.runDayLabel(instant("2026-10-08T09:00:00Z"), now, "UTC"))
        assertEquals("2026年10月1日", calendar.runDayLabel(instant("2026-10-01T09:00:00Z"), now, "UTC"))
        // The viewer zone decides the calendar day, not UTC.
        assertEquals("今天 09:00", calendar.runDayLabel(instant("2026-10-11T01:00:00Z"), now, "Asia/Shanghai"))
        assertNull(calendar.runDayLabel(instant("2026-10-11T09:00:00Z"), now, "Missing/Zone"))
    }
}
