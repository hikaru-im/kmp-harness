package im.hikaru.harness.client.scheduling

import kotlin.test.*

class ScheduleEngineTest {
    private val engine = ScheduleEngine(HourlyTestCalendar)

    @Test fun hourlyUsesAnchorAndStrictlyFutureTimeWithoutOverflow() {
        val rule = ScheduledRule.Hourly(2, 1_000)
        assertEquals(1_000L, engine.nextOccurrence(rule, 0))
        assertEquals(7_201_000L, engine.nextOccurrence(rule, 1_000))
        assertEquals(14_401_000L, engine.nextOccurrence(rule, 7_201_000))
        assertNull(engine.nextOccurrence(rule, Long.MAX_VALUE))
        assertNull(engine.nextOccurrence(ScheduledRule.Hourly(0, 0), 0))
        assertNull(engine.nextOccurrence(ScheduledRule.Hourly(169, 0), 0))
        assertNull(engine.nextOccurrence(ScheduledRule.Hourly(1, -1), 0))
        assertNull(engine.nextOccurrence(rule, -1))
    }

    @Test fun invalidWallFieldsAndUnknownZonesHaveNoOccurrence() {
        assertNotNull(engine.validationError(ScheduledRule.Daily(24, 0, "UTC")))
        assertNotNull(engine.validationError(ScheduledRule.Weekdays(9, 60, "UTC")))
        assertNotNull(engine.validationError(ScheduledRule.Weekly(ScheduledWeekday.Monday, 9, 0, "unknown")))
        assertNull(engine.nextOccurrence(ScheduledRule.Daily(9, 0, "unknown"), 0))
        assertNull(ScheduledRuleDraft(hour = "").rule())
        assertNull(ScheduledRuleDraft(frequency = ScheduledFrequency.Hourly, intervalHours = "1.5").rule())
    }
}
