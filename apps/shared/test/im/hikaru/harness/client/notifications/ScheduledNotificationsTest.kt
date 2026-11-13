package im.hikaru.harness.client.notifications

import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.harness.client.connection.SessionId
import im.hikaru.harness.client.scheduling.ScheduledDestination
import im.hikaru.harness.client.scheduling.ScheduledModelRef
import im.hikaru.harness.client.scheduling.ScheduledOwner
import im.hikaru.harness.client.scheduling.ScheduledRun
import im.hikaru.harness.client.scheduling.ScheduledRunPhase
import im.hikaru.harness.client.scheduling.ScheduledSnapshot
import im.hikaru.harness.client.scheduling.ScheduledTarget
import im.hikaru.harness.client.scheduling.ScheduledTask
import im.hikaru.harness.client.scheduling.ScheduledTaskStatus
import im.hikaru.harness.client.scheduling.ScheduledTerminalEvidence
import im.hikaru.harness.client.scheduling.ScheduledRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScheduledNotificationsTest {
    @Test
    fun baselineDoesNotReplayHistoryAndNewTerminalRunIsDeliveredOnce() {
        val task = task()
        val running = run(task)
        val completed = running.copy(
            phase = ScheduledRunPhase.Succeeded,
            terminal = ScheduledTerminalEvidence(1, 2, 3, 4, ScheduledRunPhase.Succeeded, "completed"),
        )
        val tracker = ScheduledNotificationTracker()

        assertTrue(tracker.update(snapshot(running)).isEmpty())
        val notification = tracker.update(snapshot(completed)).single()
        assertEquals("scheduled-run:run-1", notification.id)
        assertEquals("定时任务已完成", notification.title)
        assertEquals("G14 test", notification.body)
        assertTrue(tracker.update(snapshot(completed)).isEmpty())
    }

    @Test
    fun failuresUseFailureCopyAndExecutorReplacementReseedsBaseline() {
        val task = task()
        val failed = run(task).copy(
            phase = ScheduledRunPhase.Failed,
            terminal = ScheduledTerminalEvidence(1, 2, 3, 4, ScheduledRunPhase.Failed, "failed"),
        )
        val tracker = ScheduledNotificationTracker()
        assertTrue(tracker.update(snapshot(run(task))).isEmpty())
        assertEquals("定时任务未完成", tracker.update(snapshot(failed)).single().title)

        tracker.beginSource()
        assertTrue(tracker.update(snapshot(failed)).isEmpty())
        assertTrue(tracker.update(snapshot(failed)).isEmpty())
    }

    private fun task() = ScheduledTask(
        id = "task-1", revision = 1, name = "G14 test", prompt = "not shown",
        target = ScheduledTarget.Cron(ScheduledDestination.Projectless),
        schedule = ScheduledRule.Hourly(1, 0), model = ScheduledModelRef("provider", "model"),
        status = ScheduledTaskStatus.Active,
    )

    private fun run(task: ScheduledTask) = ScheduledRun(
        id = "run-1", task = task, requestId = "request", nominalAt = null, claimedAt = 1,
        candidateSessionId = SessionId("candidate"), inputMessageId = "input",
        phase = ScheduledRunPhase.Accepted,
    )

    private fun snapshot(run: ScheduledRun) = ScheduledSnapshot(
        owner = ScheduledOwner(HostId("g14-test"), null), tasks = listOf(run.task), runs = listOf(run),
    )
}
