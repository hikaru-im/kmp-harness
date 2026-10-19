package im.hikaru.harness.schedule

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScheduleDomainTest {
    private val id = ScheduleId("schedule-test")
    private val now = instant("2026-01-01T00:00:00Z")

    @Test
    fun everyKindUsesTheSameImmutableSelectorValidation() {
        val after = createScheduleRecord(id, ScheduleSelector.After(90), now, " title ", " prompt ")
        assertEquals("title", after.title)
        assertEquals("prompt", after.prompt)
        assertEquals(now + 90_000, after.scheduledAt)

        val at = createScheduleRecord(
            id,
            ScheduleSelector.At(ScheduleAtInput.Local("2026-01-01", "10:00:00", "Asia/Shanghai")),
            now,
            "at",
            "prompt",
        )
        assertEquals(instant("2026-01-01T02:00:00Z"), at.scheduledAt)

        val every = createScheduleRecord(id, ScheduleSelector.Every(60), now, "every", "prompt")
        assertEquals(now + 60_000, every.scheduledAt)

        val daily = createScheduleRecord(
            id,
            ScheduleSelector.Daily(ScheduleTime(9, 0, 0, 0), "UTC"),
            instant("2026-01-01T10:00:00Z"),
            "daily",
            "prompt",
        )
        assertEquals(instant("2026-01-02T09:00:00Z"), daily.scheduledAt)

        val weekly = createScheduleRecord(
            id,
            ScheduleSelector.Weekly(ScheduleTime(9, 0, 0, 0), "UTC", listOf(5, 1)),
            now,
            "weekly",
            "prompt",
        )
        assertEquals(listOf(1, 5), (weekly as ScheduleRecord.Weekly).weekdays)
        assertEquals(instant("2026-01-02T09:00:00Z"), weekly.scheduledAt)

        val cron = createScheduleRecord(
            id,
            ScheduleSelector.Cron("0 0 * * 7", "UTC"),
            now,
            "cron",
            "prompt",
        )
        assertEquals("0 0 * * 0", (cron as ScheduleRecord.Cron).expression)
    }

    @Test
    fun creationValidationMatchesDSHOrderAndMessages() {
        fun code(block: () -> Unit): ScheduleErrorCode =
            assertFailsWith<ScheduleInputException> { block() }.code

        assertEquals(
            ScheduleErrorCode.InvalidPrompt,
            code { createScheduleRecord(id, ScheduleSelector.Every(60), now, "  ", "p") },
        )
        assertEquals(
            ScheduleErrorCode.InvalidPrompt,
            code { createScheduleRecord(id, ScheduleSelector.Every(60), now, "t", "  ") },
        )
        assertEquals(
            ScheduleErrorCode.FrequencyTooHigh,
            code { createScheduleRecord(id, ScheduleSelector.Every(59), now, "t", "p") },
        )
        assertEquals(
            ScheduleErrorCode.InvalidRule,
            code { createScheduleRecord(id, ScheduleSelector.After(0), now, "t", "p") },
        )
        assertEquals(
            ScheduleErrorCode.NotFuture,
            code { createScheduleRecord(id, ScheduleSelector.At(ScheduleAtInput.Offset("2026-01-01T00:00:00Z")), now, "t", "p") },
        )
        assertEquals(
            ScheduleErrorCode.InvalidTimeZone,
            code {
                createScheduleRecord(
                    id,
                    ScheduleSelector.Daily(ScheduleTime(9, 0, 0, 0), "+08:00"),
                    now,
                    "t",
                    "p",
                )
            },
        )
        assertEquals(
            ScheduleErrorCode.InvalidRule,
            code {
                createScheduleRecord(
                    id,
                    ScheduleSelector.Weekly(ScheduleTime(9, 0, 0, 0), "UTC", emptyList()),
                    now,
                    "t",
                    "p",
                )
            },
        )
        assertEquals(
            ScheduleErrorCode.InvalidRule,
            code {
                createScheduleRecord(
                    id,
                    ScheduleSelector.Weekly(ScheduleTime(9, 0, 0, 0), "UTC", listOf(1, 1)),
                    now,
                    "t",
                    "p",
                )
            },
        )
    }

    @Test
    fun updateIsANoOpWhenTheTimingIsEquivalentEvenAfterTheTargetPassed() {
        val record = createScheduleRecord(
            id,
            ScheduleSelector.Daily(ScheduleTime(9, 0, 0, 0), "UTC"),
            instant("2026-01-01T10:00:00Z"),
            "title",
            "prompt",
        )
        val unchanged = resolveScheduleUpdate(
            current = record,
            expected = record,
            change = ScheduleSelector.Daily(ScheduleTime(9, 0, 0, 0), "UTC"),
            now = instant("2026-01-05T00:00:00Z"),
        )
        assertEquals(record, unchanged.record)
        assertFalse(unchanged.updated)

        val contentOnly = resolveScheduleUpdate(
            current = record,
            expected = record,
            change = null,
            now = instant("2026-01-05T00:00:00Z"),
            title = "renamed",
        )
        assertEquals("renamed", contentOnly.record?.title)
        assertEquals(record.scheduledAt, contentOnly.record?.scheduledAt)
        assertTrue(contentOnly.updated)
    }

    @Test
    fun updateRejectsAStaleExpectedRecordAndReanchorsARealChange() {
        val every = createScheduleRecord(id, ScheduleSelector.Every(60), now, "t", "p")
        val conflict = resolveScheduleUpdate(
            current = every,
            expected = every.withContent("other", "p"),
            change = null,
            now = now,
        )
        assertEquals(SCHEDULE_CONFLICT, conflict.code)
        assertNull(conflict.record)

        val later = instant("2026-01-01T00:05:00Z")
        val reanchored = resolveScheduleUpdate(
            current = every,
            expected = every,
            change = ScheduleSelector.Every(120),
            now = later,
        )
        assertTrue(reanchored.updated)
        assertEquals(later + 120_000, reanchored.record?.scheduledAt)
    }

    @Test
    fun recurringOccurrenceUsesTheLatestDueInstantAndAdvancesFromIt() {
        val base = instant("2026-01-01T00:00:00Z")
        val every = createScheduleRecord(id, ScheduleSelector.Every(60), base, "t", "p")
        val accepted = base + 5 * 60_000 + 30_000
        val occurrence = resolveRecurringOccurrence(every, accepted)
        assertEquals(base + 5 * 60_000, occurrence.occurrenceAt)
        assertEquals(base + 6 * 60_000, occurrence.nextScheduledAt)

        val daily = createScheduleRecord(
            id,
            ScheduleSelector.Daily(ScheduleTime(9, 0, 0, 0), "UTC"),
            base,
            "t",
            "p",
        )
        val dailyCatchUp = resolveRecurringOccurrence(daily, instant("2026-01-03T12:00:00Z"))
        assertEquals(instant("2026-01-03T09:00:00Z"), dailyCatchUp.occurrenceAt)
        assertEquals(instant("2026-01-04T09:00:00Z"), dailyCatchUp.nextScheduledAt)

        val weekly = createScheduleRecord(
            id,
            ScheduleSelector.Weekly(ScheduleTime(9, 0, 0, 0), "UTC", listOf(1)),
            base,
            "t",
            "p",
        )
        val weeklyCatchUp = resolveRecurringOccurrence(weekly, instant("2026-01-08T12:00:00Z"))
        assertEquals(instant("2026-01-05T09:00:00Z"), weeklyCatchUp.occurrenceAt)
        assertEquals(instant("2026-01-12T09:00:00Z"), weeklyCatchUp.nextScheduledAt)

        val cron = createScheduleRecord(
            id,
            ScheduleSelector.Cron("0 * * * *", "UTC"),
            base,
            "t",
            "p",
        )
        val cronCatchUp = resolveRecurringOccurrence(cron, instant("2026-01-01T05:45:00Z"))
        assertEquals(instant("2026-01-01T05:00:00Z"), cronCatchUp.occurrenceAt)
        assertEquals(instant("2026-01-01T06:00:00Z"), cronCatchUp.nextScheduledAt)
    }

    @Test
    fun recurringOccurrenceRetiresAtTheFourDigitCeiling() {
        val last = instant("9999-12-31T23:59:59.999Z")
        val daily = ScheduleRecord.Daily(
            id = id,
            title = "t",
            prompt = "p",
            scheduledAt = last,
            time = ScheduleTime(23, 59, 59, 999),
            timeZone = "UTC",
        )
        val occurrence = resolveRecurringOccurrence(daily, last)
        assertEquals(last, occurrence.occurrenceAt)
        assertNull(occurrence.nextScheduledAt)
    }

    @Test
    fun modelFramingIsByteCompatibleWithDSH() {
        val record = ScheduleRecord.At(
            id = id,
            title = "t",
            prompt = "say \"hi\"\nnow",
            scheduledAt = now,
        )
        assertEquals(
            """
            [SCHEDULE REMINDER]
            This is a scheduled message from the user
            schedule_id_json: "schedule-test"
            occurrence_at: 2026-01-01T00:00:00.000Z
            reminder_prompt_json: "say \"hi\"\nnow"
            """.trimIndent(),
            renderReminderFraming(record, now),
        )

        val second = ScheduleRecord.Daily(
            id = ScheduleId("schedule-two"),
            title = "t2",
            prompt = "second",
            scheduledAt = now,
            time = ScheduleTime(9, 0, 0, 0),
            timeZone = "UTC",
        )
        assertEquals(
            """
            [SCHEDULE REMINDER BATCH]
            This is a scheduled message from the user
            reminders_json: [{"schedule_id":"schedule-test","occurrence_at":"2026-01-01T00:00:00.000Z","reminder_prompt":"say \"hi\"\nnow"},{"schedule_id":"schedule-two","occurrence_at":"2026-01-01T00:00:00.000Z","reminder_prompt":"second"}]
            """.trimIndent(),
            renderRecurringReminderBatchFraming(listOf(record to now, second to now)),
        )
    }
}
