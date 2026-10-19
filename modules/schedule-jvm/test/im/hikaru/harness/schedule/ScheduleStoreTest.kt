package im.hikaru.harness.schedule

import im.hikaru.harness.session.SessionId
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class ScheduleStoreTest {
    @Test
    fun everyKindAndReceiptSurviveReopenInInsertionOrder() = runTest {
        val database = Files.createTempDirectory("schedule-store").resolve("harness-schedule.db").toFile()
        val session = SessionId("session-one")
        val other = SessionId("session-two")
        val t = instant("2026-01-01T00:00:00Z")
        val records = listOf(
            StoredSchedule(
                session,
                ScheduleRecord.After(ScheduleId("schedule-z"), "after", "prompt", t + 1_000, 1),
                ScheduleStatus.Inactive,
                ScheduleDeliveryReceipt(t + 1_000, t + 1_500, "message-1"),
            ),
            StoredSchedule(
                other,
                ScheduleRecord.At(ScheduleId("schedule-a"), "at", "prompt", t + 2_000),
                ScheduleStatus.Active,
            ),
            StoredSchedule(
                session,
                ScheduleRecord.Every(ScheduleId("schedule-e"), "every", "prompt", t + 60_000, 60),
                ScheduleStatus.Active,
                ScheduleDeliveryReceipt(t, t + 1, "message-2"),
            ),
            StoredSchedule(
                session,
                ScheduleRecord.Daily(
                    ScheduleId("schedule-d"), "daily", "prompt", t + 3_000,
                    ScheduleTime(9, 30, 0, 250), "Asia/Shanghai",
                ),
                ScheduleStatus.Active,
            ),
            StoredSchedule(
                other,
                ScheduleRecord.Weekly(
                    ScheduleId("schedule-w"), "weekly", "prompt", t + 4_000,
                    ScheduleTime(9, 0, 0, 0), "America/New_York", listOf(1, 3, 5),
                ),
                ScheduleStatus.Active,
            ),
            StoredSchedule(
                session,
                ScheduleRecord.Cron(
                    ScheduleId("schedule-c"), "cron", "prompt", t + 5_000,
                    "*/15 9-17 * * 1-5", "UTC",
                ),
                ScheduleStatus.Active,
            ),
        )

        RoomScheduleStore.open(database).useStore { store ->
            records.forEach { store.put(it) }
            assertEquals(records, store.all())
            assertEquals(records[0], store.find(records[0].record.id))
            assertNull(store.find(ScheduleId("schedule-missing")))
            store.delete(records[1].record.id)
            assertEquals(records.filterIndexed { index, _ -> index != 1 }, store.all())
        }

        // The receipt and status envelope are durable, not process state.
        RoomScheduleStore.open(database).useStore { reopened ->
            assertEquals(records.filterIndexed { index, _ -> index != 1 }, reopened.all())
        }
    }

    private suspend fun RoomScheduleStore.useStore(block: suspend (RoomScheduleStore) -> Unit) {
        try {
            block(this)
        } finally {
            dispose()
        }
    }
}
