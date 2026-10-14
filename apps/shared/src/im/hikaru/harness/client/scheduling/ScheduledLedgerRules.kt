package im.hikaru.harness.client.scheduling

/** Shared validation applies to disk input as well as writes; unknown states are never ignored. */
public object ScheduledLedgerRules {
    private val terminalPhases = setOf(ScheduledRunPhase.Succeeded, ScheduledRunPhase.Failed, ScheduledRunPhase.Blocked,
        ScheduledRunPhase.Incomplete, ScheduledRunPhase.Interrupted, ScheduledRunPhase.Aborted)

    public fun error(snapshot: ScheduledSnapshot): String? {
        if (snapshot.runs.map { it.id }.distinct().size != snapshot.runs.size ||
            snapshot.runs.map { it.requestId }.distinct().size != snapshot.runs.size) return "重复运行 ID 或请求 ID。"
        if (snapshot.runs.filter { it.nominalAt != null }.map { it.task.id to it.nominalAt }.distinct().size !=
            snapshot.runs.count { it.nominalAt != null }) return "重复定时 occurrence。"
        if (snapshot.runs.filter { it.blocksTask }.groupBy { it.task.id }.any { it.value.size > 1 }) return "任务运行重叠。"
        for (run in snapshot.runs) {
            if (run.id.isBlank() || run.requestId.isBlank() || run.inputMessageId.isBlank() || run.claimedAt < 0 ||
                run.nominalAt?.let { it < 0 } == true || ScheduledValidator.structuralError(run.task) != null ||
                (run.task.target !is ScheduledTarget.Cron && run.task.target !is ScheduledTarget.Heartbeat) ||
                run.task.revision > snapshot.revision ||
                run.sessionRef?.let { it != run.candidateSessionId } == true) return "运行快照无效。"
            // A heartbeat never invents a session: its intent and reference are the exact target session.
            if ((run.task.target as? ScheduledTarget.Heartbeat)?.sessionId?.let { it != run.candidateSessionId } == true)
                return "会话跟进缺少与目标会话一致的身份。"
            if (run.phase in setOf(ScheduledRunPhase.SessionCreated, ScheduledRunPhase.Dispatching, ScheduledRunPhase.Accepted) && run.sessionRef == null) return "运行缺少已确认会话。"
            if (run.phase == ScheduledRunPhase.Accepted && run.receiptId.isNullOrBlank()) return "运行缺少投递收据。"
            if (run.phase == ScheduledRunPhase.Withdrawn && (!run.deliveryIntent || run.sessionRef == null ||
                    run.receiptId != null || run.terminal != null || run.detail.isNullOrBlank())) return "撤回缺少未调用 prompt 的投递意图审计。"
            if (run.phase in terminalPhases) {
                val evidence = run.terminal ?: return "终态缺少精确 TurnEnd 证据。"
                if (run.sessionRef == null || !run.deliveryIntent || evidence.outcome != run.phase || evidence.turn <= 0 ||
                    evidence.userMessageSeq < 0 || evidence.turnEndSeq <= evidence.userMessageSeq || evidence.endedAt < 0 ||
                    evidence.reason.isBlank()) return "运行终态证据无效。"
            } else if (run.terminal != null) return "非终态附带终态证据。"
            if (run.phase in setOf(ScheduledRunPhase.Dispatching, ScheduledRunPhase.Accepted) && !run.deliveryIntent) return "缺少投递意图。"
            if (run.phase in setOf(ScheduledRunPhase.Claimed, ScheduledRunPhase.CreatingSession, ScheduledRunPhase.SessionCreated, ScheduledRunPhase.Rejected) && run.deliveryIntent) return "投递意图与阶段冲突。"
        }
        if (snapshot.cursors.map { it.taskId }.distinct().size != snapshot.cursors.size) return "重复调度游标。"
        for (cursor in snapshot.cursors) {
            val task = snapshot.tasks.firstOrNull { it.id == cursor.taskId } ?: return "游标任务不存在。"
            if (task.status != ScheduledTaskStatus.Active ||
                (task.target !is ScheduledTarget.Cron && task.target !is ScheduledTarget.Heartbeat) ||
                cursor.taskRevision != task.revision ||
                cursor.nextNominalAt < 0 || cursor.skippedThrough?.let { it < 0 || it >= cursor.nextNominalAt } == true) return "调度游标无效。"
        }
        if (snapshot.tasks.any { it.status == ScheduledTaskStatus.Active && snapshot.cursors.none { c -> c.taskId == it.id } }) return "启用任务缺少持久化游标。"
        return null
    }

    public fun validTransition(old: ScheduledSnapshot, next: ScheduledSnapshot): Boolean {
        if (error(next) != null || !next.deletedTaskIds.containsAll(old.deletedTaskIds)) return false
        val removed = old.tasks.filter { task -> next.tasks.none { it.id == task.id } }.map { it.id }
        if ((next.deletedTaskIds - old.deletedTaskIds.toSet()).toSet() != removed.toSet()) return false
        if (!next.tasks.all { task ->
            val prior = old.tasks.firstOrNull { it.id == task.id }
            task == prior || (task.revision == (prior?.revision ?: 0) + 1 && task.id !in old.deletedTaskIds &&
                (prior != null || task.status == ScheduledTaskStatus.Paused))
        }) return false
        if (!old.runs.all { prior -> next.runs.firstOrNull { it.id == prior.id }?.let { validRunChange(prior, it) } == true }) return false
        for (run in next.runs.filter { candidate -> old.runs.none { it.id == candidate.id } }) {
            if (run.phase != ScheduledRunPhase.Claimed || run.sessionRef != null || run.receiptId != null || run.deliveryIntent ||
                run.task != old.tasks.firstOrNull { it.id == run.task.id } || old.runs.any { it.task.id == run.task.id && it.blocksTask }) return false
        }
        return true
    }

    private fun validRunChange(old: ScheduledRun, next: ScheduledRun): Boolean {
        if (old == next) return true
        // Restart may replace the diagnostic while preserving every piece of delivery evidence.
        // Unknown cannot gain a receipt, create a session or reopen delivery via this same phase.
        if (old.phase == ScheduledRunPhase.Unknown && next.phase == ScheduledRunPhase.Unknown)
            return old.copy(detail = next.detail) == next
        if (old.phase == ScheduledRunPhase.Withdrawn) return false
        if (old.copy(phase = next.phase, sessionRef = next.sessionRef, receiptId = next.receiptId, terminal = next.terminal,
                detail = next.detail, deliveryIntent = next.deliveryIntent) != next) return false
        if (old.sessionRef != null && next.sessionRef != old.sessionRef || old.receiptId != null && next.receiptId != old.receiptId ||
            old.deliveryIntent && !next.deliveryIntent) return false
        if (!old.deliveryIntent && next.deliveryIntent && next.phase != ScheduledRunPhase.Dispatching) return false
        if (old.sessionRef == null && next.sessionRef != null && next.phase != ScheduledRunPhase.SessionCreated &&
            !(old.deliveryIntent && next.phase in terminalPhases)) return false
        if (old.receiptId == null && next.receiptId != null && next.phase != ScheduledRunPhase.Accepted) return false
        val heartbeat = old.task.target is ScheduledTarget.Heartbeat
        val allowed = when (old.phase) {
            // A heartbeat has no create step, so its claimed intent goes straight to the session it
            // already names; a Cron run must still pass through CreatingSession.
            ScheduledRunPhase.Claimed -> buildSet {
                add(ScheduledRunPhase.CreatingSession); add(ScheduledRunPhase.Rejected); add(ScheduledRunPhase.Unknown)
                if (heartbeat) add(ScheduledRunPhase.SessionCreated)
            }
            ScheduledRunPhase.CreatingSession -> setOf(ScheduledRunPhase.SessionCreated, ScheduledRunPhase.Unknown)
            ScheduledRunPhase.SessionCreated -> setOf(ScheduledRunPhase.Dispatching, ScheduledRunPhase.Rejected, ScheduledRunPhase.Unknown)
            ScheduledRunPhase.Dispatching -> setOf(ScheduledRunPhase.Accepted, ScheduledRunPhase.Unknown, ScheduledRunPhase.Withdrawn) + terminalPhases
            ScheduledRunPhase.Accepted -> terminalPhases + ScheduledRunPhase.Unknown
            ScheduledRunPhase.Unknown -> terminalPhases
            else -> emptySet()
        }
        return next.phase in allowed
    }
}
