package im.hikaru.harness.client.scheduling

import im.hikaru.harness.client.connection.*

public sealed interface ScheduledEvidenceResult {
    public data object Pending : ScheduledEvidenceResult
    /** Adapter has committed the exact session log through its real persistence provider. */
    public data class Terminal(val evidence: ScheduledTerminalEvidence) : ScheduledEvidenceResult
    public data class Unavailable(val reason: String) : ScheduledEvidenceResult
}

public interface ScheduledSessionEvidence {
    public suspend fun unavailableReason(scope: ScheduledScope, task: ScheduledTask?): String?
    public suspend fun flushCreated(scope: ScheduledScope, sessionId: SessionId)
    public suspend fun inspect(scope: ScheduledScope, run: ScheduledRun): ScheduledEvidenceResult
}

public interface ScheduledSessionAccess {
    public suspend fun unavailableReason(scope: ScheduledScope, task: ScheduledTask?): String?
    public suspend fun create(scope: ScheduledScope, run: ScheduledRun): SessionId
    public suspend fun prompt(scope: ScheduledScope, run: ScheduledRun): SessionPromptReceipt
    public suspend fun inspect(scope: ScheduledScope, run: ScheduledRun): ScheduledEvidenceResult
}

/** Consumes the real connection.session. A heartbeat is delivered through the same SessionApi.prompt
 * path as a manual send, so both share the Session's atomic operation admission. */
public class LocalScheduledSessionAccess(
    private val session: SessionApi, private val evidence: ScheduledSessionEvidence,
) : ScheduledSessionAccess {
    override suspend fun unavailableReason(scope: ScheduledScope, task: ScheduledTask?): String? =
        evidence.unavailableReason(scope, task)

    override suspend fun create(scope: ScheduledScope, run: ScheduledRun): SessionId {
        admit(scope, run)
        val cron = run.task.target as? ScheduledTarget.Cron
            ?: error("会话跟进运行不创建会话；它的投递目标是既有会话。")
        val model = requireNotNull(run.task.model)
        val cwd = (cron.destination as? ScheduledDestination.Cwd)?.path
        val created = session.create(run.candidateSessionId, CreateSessionOptions(cwd = cwd),
            AgentOptions(cwd = cwd, provider = model.providerId, model = model.modelId))
        check(created.id == run.candidateSessionId) { "Host 返回的会话与 create intent 不一致。" }
        evidence.flushCreated(scope, created.id)
        return created.id
    }

    override suspend fun prompt(scope: ScheduledScope, run: ScheduledRun): SessionPromptReceipt {
        admit(scope, run)
        val id = requireNotNull(run.sessionRef)
        val target = run.task.target
        // A heartbeat never created anything: its reference must be exactly the task's target session.
        if (target is ScheduledTarget.Heartbeat) check(id == target.sessionId) { "会话跟进与目标会话身份不一致。" }
        else check(id == run.candidateSessionId) { "prompt 会话与 create intent 不一致。" }
        val result = session.prompt(id, Message(run.inputMessageId, MessageRole.USER,
            listOf(TextContent(run.task.prompt)), UserMessageSource)).receipt
        check(result.sessionId == id && result.messageId.isNotBlank()) { "Host 返回的投递收据无效。" }
        // receipt.messageId is the inbox item ID. Never compare it with the input Message.id.
        return result
    }

    override suspend fun inspect(scope: ScheduledScope, run: ScheduledRun): ScheduledEvidenceResult = evidence.inspect(scope, run)

    /** Independent admission for direct callers, before any SessionApi side effect. Generation is
     * still fenced by the owner of this connection; evidence must validate its actual Host/member. */
    private suspend fun admit(scope: ScheduledScope, run: ScheduledRun) {
        evidence.unavailableReason(scope, null)?.let { error(it) }
        ScheduledValidator.structuralError(run.task)?.let { error(it) }
        unavailableReason(scope, run.task)?.let { error(it) }
    }
}
