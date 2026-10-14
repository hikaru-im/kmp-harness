package im.hikaru.harness.client.scheduling

import im.hikaru.harness.client.connection.SessionId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Pure projection behind the scheduled navigator's cadence + next-run row summary. */
class ScheduledTaskSummaryTest {
    private val daily = ScheduledTask(
        id = "t1", revision = 1, name = "每日摘要", prompt = "总结进展。",
        target = ScheduledTarget.Cron(ScheduledDestination.Projectless),
        schedule = ScheduledRule.Daily(9, 0, "Asia/Shanghai"), model = null,
        status = ScheduledTaskStatus.Active,
    )

    private object LabelingCalendar : ScheduleCalendar {
        override fun isValidZone(zoneId: String): Boolean = true
        override fun dateAt(epochMillis: Long, zoneId: String): ScheduleDate =
            ScheduleDate(2026, 10, 11, ScheduledWeekday.Sunday)
        override fun plusDays(date: ScheduleDate, days: Int): ScheduleDate = date
        override fun resolve(date: ScheduleDate, hour: Int, minute: Int, zoneId: String): Long = 0L
        override val displayZoneId: String = "Asia/Shanghai"
        override fun nextRunLabel(nextEpochMillis: Long, nowEpochMillis: Long, zoneId: String): String =
            "今天 09:00@" + zoneId
    }

    private object NoLabelCalendar : ScheduleCalendar {
        override fun isValidZone(zoneId: String): Boolean = true
        override fun dateAt(epochMillis: Long, zoneId: String): ScheduleDate =
            ScheduleDate(2026, 10, 11, ScheduledWeekday.Sunday)
        override fun plusDays(date: ScheduleDate, days: Int): ScheduleDate = date
        override fun resolve(date: ScheduleDate, hour: Int, minute: Int, zoneId: String): Long = 0L
    }

    private object LastRunCalendar : ScheduleCalendar {
        override fun isValidZone(zoneId: String): Boolean = true
        override fun dateAt(epochMillis: Long, zoneId: String): ScheduleDate =
            ScheduleDate(2026, 10, 11, ScheduledWeekday.Sunday)
        override fun plusDays(date: ScheduleDate, days: Int): ScheduleDate = date
        override fun resolve(date: ScheduleDate, hour: Int, minute: Int, zoneId: String): Long = 0L
        override val displayZoneId: String = "Asia/Shanghai"
        override fun relativeRunLabel(runEpochMillis: Long, nowEpochMillis: Long, zoneId: String): String? =
            if (runEpochMillis == 7_000L) "22 分钟前@" + zoneId else null
        override fun runDayLabel(runEpochMillis: Long, nowEpochMillis: Long, zoneId: String): String? =
            if (runEpochMillis == 8L) "昨天 09:00@" + zoneId else null
    }

    private fun run(id: String, claimedAt: Long, endedAt: Long? = null) = ScheduledRun(
        id = id, task = daily, requestId = "request-$id", nominalAt = null, claimedAt = claimedAt,
        candidateSessionId = SessionId("session-$id"), inputMessageId = "input-$id",
        terminal = endedAt?.let {
            ScheduledTerminalEvidence(turn = 1, userMessageSeq = 1, turnEndSeq = 2, endedAt = it,
                outcome = ScheduledRunPhase.Succeeded, reason = "completed")
        },
    )

    @Test
    fun pausedTaskShowsOnlyThePausedState() {
        assertEquals(ScheduledPausedLabel, scheduledTaskSummary(
            daily.copy(status = ScheduledTaskStatus.Paused),
            ScheduledCursor("t1", 1, 1_000L), running = false, nowEpochMillis = 0L, calendar = LabelingCalendar))
    }

    @Test
    fun activeTaskUsesCadenceAndTheDurableNextInstantInTheRuleZone() {
        assertEquals("每天 · 下次运行：今天 09:00@Asia/Shanghai", scheduledTaskSummary(
            daily, ScheduledCursor("t1", 1, 1_000L), running = false, nowEpochMillis = 0L, calendar = LabelingCalendar))
    }

    @Test
    fun hourlyRulesFallBackToTheViewerZoneForTheLabel() {
        val hourly = daily.copy(schedule = ScheduledRule.Hourly(2, 0L), status = ScheduledTaskStatus.Active)
        assertEquals("每小时 · 下次运行：今天 09:00@Asia/Shanghai", scheduledTaskSummary(
            hourly, ScheduledCursor("t1", 1, 1_000L), running = false, nowEpochMillis = 0L, calendar = LabelingCalendar))
    }

    @Test
    fun blockingRunWinsOverTheNextInstant() {
        assertEquals("每天 · " + ScheduledRunningLabel, scheduledTaskSummary(
            daily, ScheduledCursor("t1", 1, 1_000L), running = true, nowEpochMillis = 0L, calendar = LabelingCalendar))
    }

    @Test
    fun missingCalendarOrCursorDegradesToCadenceInsteadOfInventingATime() {
        assertEquals("每天", scheduledTaskSummary(daily, null, running = false, nowEpochMillis = 0L, calendar = LabelingCalendar))
        assertEquals("每天", scheduledTaskSummary(daily, ScheduledCursor("t1", 1, 1_000L), running = false, nowEpochMillis = 0L, calendar = null))
        assertEquals("每天", scheduledTaskSummary(
            daily, ScheduledCursor("t1", 1, 1_000L), running = false, nowEpochMillis = 0L, calendar = NoLabelCalendar))
    }

    @Test
    fun cursorForADifferentTaskIsIgnored() {
        assertEquals("每周", scheduledTaskSummary(
            daily.copy(schedule = ScheduledRule.Weekly(ScheduledWeekday.Monday, 9, 0, "UTC")),
            ScheduledCursor("other", 1, 1_000L), running = false, nowEpochMillis = 0L, calendar = LabelingCalendar))
    }

    @Test
    fun lastRunUsesTheNewestTerminalOutcomeNotTheNewestClaim() {
        assertEquals(5_000L, lastRunInstant(listOf(run("old", claimedAt = 1_000L, endedAt = 5_000L),
            run("inflight", claimedAt = 9_999L))))
    }

    @Test
    fun lastRunFallsBackToTheNewestClaimWithoutTerminalEvidence() {
        assertEquals(9_999L, lastRunInstant(listOf(run("a", claimedAt = 1_000L), run("b", claimedAt = 9_999L))))
    }

    @Test
    fun lastRunIsNullWithoutAnyRun() {
        assertNull(lastRunInstant(emptyList()))
    }

    @Test
    fun lastRunLabelPrefersTheRelativeBranchWithinADay() {
        assertEquals("上次运行于 22 分钟前@Asia/Shanghai", scheduledLastRunLabel(7_000L, 8_000L, LastRunCalendar))
    }

    @Test
    fun lastRunLabelFallsBackToTheDayBranch() {
        assertEquals("上次运行于 昨天 09:00@Asia/Shanghai", scheduledLastRunLabel(8L, 8_000L, LastRunCalendar))
    }

    @Test
    fun lastRunLabelIsNullWithoutARunCalendarOrLabel() {
        assertNull(scheduledLastRunLabel(null, 8_000L, LastRunCalendar))
        assertNull(scheduledLastRunLabel(7_000L, 8_000L, null))
        assertNull(scheduledLastRunLabel(7_000L, 8_000L, NoLabelCalendar))
    }

    @Test
    fun lastUpdatedLabelPrefersTheRelativeBranchWithinADay() {
        assertEquals("上次更新 · 22 分钟前@Asia/Shanghai", scheduledLastUpdatedLabel(7_000L, 8_000L, LastRunCalendar))
    }

    @Test
    fun lastUpdatedLabelFallsBackToTheDayBranch() {
        // `showLastUpdateLabel` is only set on the relative branch, so an older instant has no prefix.
        assertEquals("昨天 09:00@Asia/Shanghai", scheduledLastUpdatedLabel(8L, 8_000L, LastRunCalendar))
    }

    @Test
    fun lastUpdatedLabelIsNullWithoutATimeCalendarOrLabel() {
        assertNull(scheduledLastUpdatedLabel(null, 8_000L, LastRunCalendar))
        assertNull(scheduledLastUpdatedLabel(7_000L, 8_000L, null))
        assertNull(scheduledLastUpdatedLabel(7_000L, 8_000L, NoLabelCalendar))
    }

    @Test
    fun compactCadenceCoversPausedHourlyDailyWeekdayAndWeeklyTokens() {
        assertEquals("已暂停", scheduledSummaryCadence(dailyTask(ScheduledTaskStatus.Paused, ScheduledRule.Daily(9, 0, "Asia/Shanghai"))))
        assertEquals("每小时", scheduledSummaryCadence(dailyTask(ScheduledTaskStatus.Active, ScheduledRule.Hourly(1, 0L))))
        assertEquals("每 3 小时", scheduledSummaryCadence(dailyTask(ScheduledTaskStatus.Active, ScheduledRule.Hourly(3, 0L))))
        assertEquals("每天 · 09:00", scheduledSummaryCadence(dailyTask(ScheduledTaskStatus.Active, ScheduledRule.Daily(9, 0, "Asia/Shanghai"))))
        assertEquals("每天 · 09:05", scheduledSummaryCadence(dailyTask(ScheduledTaskStatus.Active, ScheduledRule.Daily(9, 5, "Asia/Shanghai"))))
        // The original summary tokens carry an exact time only for the daily recurrence.
        assertEquals("周一至周五", scheduledSummaryCadence(dailyTask(ScheduledTaskStatus.Active, ScheduledRule.Weekdays(9, 0, "Asia/Shanghai"))))
        assertEquals("每周三", scheduledSummaryCadence(dailyTask(ScheduledTaskStatus.Active, ScheduledRule.Weekly(ScheduledWeekday.Wednesday, 9, 0, "Asia/Shanghai"))))
    }

    private fun dailyTask(status: ScheduledTaskStatus, schedule: ScheduledRule) = ScheduledTask(
        id = "t1", revision = 1, name = "每日摘要", prompt = "总结进展。",
        target = ScheduledTarget.Cron(ScheduledDestination.Projectless),
        schedule = schedule, model = null, status = status,
    )
}
