package im.hikaru.harness.schedule

internal class FakeScheduleClock(var now: Long) : ScheduleClock {
    override fun nowEpochMillis(): Long = now
}

internal fun instant(text: String): Long = java.time.Instant.parse(text).toEpochMilli()
