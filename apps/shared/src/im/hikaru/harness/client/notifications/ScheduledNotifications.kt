package im.hikaru.harness.client.notifications

import im.hikaru.harness.client.scheduling.ScheduledExecutorOwner
import im.hikaru.harness.client.scheduling.ScheduledRun
import im.hikaru.harness.client.scheduling.ScheduledRunPhase
import im.hikaru.harness.client.scheduling.ScheduledSnapshot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** The platform-level outcome of attempting to show a notification. */
public sealed interface SystemNotificationResult {
    public data object Delivered : SystemNotificationResult
    public data class Denied(val reason: String) : SystemNotificationResult
    public data class Unsupported(val reason: String) : SystemNotificationResult
    public data class Failed(val reason: String) : SystemNotificationResult
}

public data class SystemNotification(
    val id: String,
    val title: String,
    val body: String,
)

/** Host-owned system notification seam. The shared client never assumes permission is granted. */
public interface SystemNotificationService {
    public fun send(notification: SystemNotification): SystemNotificationResult
    public fun close()
}

/**
 * Detects newly terminal scheduled runs without replaying old evidence on startup or after an
 * executor replacement. A run is marked seen even when its outcome is not user-notifiable, so an
 * unsupported future outcome cannot produce repeated alerts on every state emission.
 */
public class ScheduledNotificationTracker {
    private val seenTerminalRunIds = mutableSetOf<String>()
    private var baselinePending: Boolean = true

    /** Start observing a new executor source; its first snapshot is historical baseline only. */
    public fun beginSource() {
        baselinePending = true
    }

    public fun update(snapshot: ScheduledSnapshot): List<SystemNotification> {
        val terminalRuns = snapshot.runs.filter { it.terminal != null }
        if (baselinePending) {
            seenTerminalRunIds += terminalRuns.map { it.id }
            baselinePending = false
            return emptyList()
        }
        return terminalRuns.mapNotNull { run ->
            if (!seenTerminalRunIds.add(run.id)) null else run.systemNotification()
        }
    }
}

/** Observe executor-owned run evidence for the lifetime of the desktop application. */
public fun CoroutineScope.launchScheduledNotifications(
    owner: ScheduledExecutorOwner,
    service: SystemNotificationService,
): Job = launch {
    val tracker = ScheduledNotificationTracker()
    try {
        owner.state.map { it.executor }.distinctUntilChanged().collectLatest { executor ->
            tracker.beginSource()
            executor.state.map { state -> state.snapshot }.collectLatest { snapshot ->
                if (snapshot == null) return@collectLatest
                tracker.update(snapshot).forEach { service.send(it) }
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    }
}

private fun ScheduledRun.systemNotification(): SystemNotification? {
    val terminal = terminal ?: return null
    return when (terminal.outcome) {
        ScheduledRunPhase.Succeeded -> SystemNotification(
            id = "scheduled-run:$id",
            title = "定时任务已完成",
            body = task.name,
        )
        ScheduledRunPhase.Failed,
        ScheduledRunPhase.Blocked,
        ScheduledRunPhase.Incomplete,
        ScheduledRunPhase.Interrupted,
        ScheduledRunPhase.Aborted,
        ScheduledRunPhase.Rejected,
        ScheduledRunPhase.Unknown -> SystemNotification(
            id = "scheduled-run:$id",
            title = "定时任务未完成",
            body = task.name,
        )
        ScheduledRunPhase.Claimed,
        ScheduledRunPhase.CreatingSession,
        ScheduledRunPhase.SessionCreated,
        ScheduledRunPhase.Dispatching,
        ScheduledRunPhase.Accepted,
        ScheduledRunPhase.Withdrawn -> null
    }
}
