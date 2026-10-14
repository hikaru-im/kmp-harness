package im.hikaru.harness.desktop.scheduling

import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.harness.bundle.desktop.requireDesktopModelCatalog
import im.hikaru.harness.client.connection.SessionId as ClientSessionId
import im.hikaru.harness.client.scheduling.*
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.session.*
import im.hikaru.harness.session.persistence.persistence
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** Bound to one verified local runtime. Parent retains it until shutdown reports Drained.
 * Reads raw typed log, never infers success from UI history, awaitIdle or an assistant message. */
public class DesktopScheduledSessionEvidence(
    private val context: Context, private val localHostId: HostId,
) : ScheduledSessionEvidence {
    override suspend fun unavailableReason(scope: ScheduledScope, task: ScheduledTask?): String? {
        if (!isLocalScope(scope)) return "执行 evidence 仅属于无账户归属的已验证本机 Host；Relay 不可执行。"
        val heartbeat = task?.target as? ScheduledTarget.Heartbeat
        return try {
            context.sessions
            context.persistence
            if (heartbeat != null) {
                // Session follow-up is delivered into an existing live session through the same
                // SessionApi.prompt admission as a manual send; it can neither create nor restore one.
                if (task.model != null) return "会话跟进必须沿用既有会话模型。"
                val target = context.sessions.get(SessionId(heartbeat.sessionId.value))
                    ?: return "目标会话当前不是本机活动会话；会话跟进不能创建或恢复它。"
                if (target.id.value != heartbeat.sessionId.value) return "目标会话身份与当前目录不一致。"
                return null
            }
            // Reached through the bundle so this Desktop source never imports provider internals.
            val modelCatalog = requireDesktopModelCatalog(context)
            if (task != null && task.model == null) return "任务未选择明确的 provider/model。"
            task?.model?.let { modelCatalog.requireResolvable(it.providerId, it.modelId) }
            val destination = (task?.target as? ScheduledTarget.Cron)?.destination
            if (destination is ScheduledDestination.Cwd) {
                val valid = withContext(Dispatchers.IO) {
                    val path = Path.of(destination.path)
                    path.isAbsolute && path.normalize() == path && Files.isDirectory(path) && Files.isReadable(path)
                }
                if (!valid) return "任务 cwd 在本机不存在、不可读或不是目录。"
            }
            null
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { "本机 session、持久化或指定模型当前不可用。" }
    }

    override suspend fun flushCreated(scope: ScheduledScope, sessionId: ClientSessionId) {
        check(isLocalScope(scope)) { "Host 或账户归属与本机 evidence 不一致。" }
        val session = context.sessions.get(SessionId(sessionId.value)) ?: error("create 返回后本机会话不可见。")
        context.persistence.flush(session)
    }

    override suspend fun inspect(scope: ScheduledScope, run: ScheduledRun): ScheduledEvidenceResult {
        val coroutineContext = currentCoroutineContext()
        coroutineContext.ensureActive()
        if (!isLocalScope(scope)) return ScheduledEvidenceResult.Unavailable("Host 或账户归属与本机 evidence 不一致。")
        val id = run.sessionRef ?: run.candidateSessionId.takeIf { run.deliveryIntent } ?: return ScheduledEvidenceResult.Pending
        return try {
            val sessionId = SessionId(id.value)
            val persistence = context.persistence
            val session = context.sessions.get(sessionId)
            if (session != null) {
                // Only a live terminal candidate warrants a flush; durable evidence is decisive.
                val candidate = terminalEvidence(session.events(), run.inputMessageId)
                coroutineContext.ensureActive()
                if (candidate == null) return ScheduledEvidenceResult.Pending
                persistence.flush(session)
                coroutineContext.ensureActive()
            }
            // Ordinary load repairs open turns, so it cannot be used as historical evidence.
            val events = persistence.readEvents(sessionId)
            coroutineContext.ensureActive()
            if (events == null) return ScheduledEvidenceResult.Unavailable("本机持久化未提供只读事件证据；结果保持 Unknown。")
            val evidence = terminalEvidence(events, run.inputMessageId) ?: return ScheduledEvidenceResult.Pending
            ScheduledEvidenceResult.Terminal(evidence)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { ScheduledEvidenceResult.Unavailable("无法读取或持久化本机精确 TurnEnd；结果保持 Unknown。") }
    }

    private fun isLocalScope(scope: ScheduledScope): Boolean =
        scope.hostId == localHostId && scope.memberIdentity == null

    public companion object {
        /** Deterministic extraction is also exercised by tests with real serialized event types. */
        public fun terminalEvidence(events: List<SessionEventEnvelope>, inputMessageId: String): ScheduledTerminalEvidence? {
            if (inputMessageId.isBlank() || events.withIndex().any { (index, event) ->
                    event.seq != index.toLong() || event.time < 0
                }) return null
            val json = Json { ignoreUnknownKeys = false }
            val users = events.filter { it.type == SessionEventNames.USER_MESSAGE }.map {
                it to (runCatching { json.decodeFromJsonElement(UserMessageEvent.serializer(), it.data) }.getOrNull() ?: return null)
            }.filter { it.second.message.id.value == inputMessageId }
            if (users.size != 1) return null
            val (userEnvelope, user) = users.single()
            if (user.turn <= 0 || user.message.source !is im.hikaru.harness.llm.UserMessageSource) return null
            val ends = events.filter { it.type == SessionEventNames.TURN_END }.map {
                it to (runCatching { json.decodeFromJsonElement(TurnEndEvent.serializer(), it.data) }.getOrNull() ?: return null)
            }.filter { it.second.turn == user.turn }
            if (ends.size != 1) return null
            val (endEnvelope, end) = ends.single()
            if (end.turn <= 0 || endEnvelope.seq <= userEnvelope.seq || endEnvelope.time < userEnvelope.time) return null
            val outcome = when (end.reason) {
                CompletedTurnEndReason -> ScheduledRunPhase.Succeeded
                is ErrorTurnEndReason -> ScheduledRunPhase.Failed
                BlockedTurnEndReason -> ScheduledRunPhase.Blocked
                MaxTokensTurnEndReason -> ScheduledRunPhase.Incomplete
                InterruptedTurnEndReason -> ScheduledRunPhase.Interrupted
                is AbortedTurnEndReason -> ScheduledRunPhase.Aborted
            }
            val reason = when (val cause = end.reason) {
                CompletedTurnEndReason -> "completed"
                is ErrorTurnEndReason -> "error"
                BlockedTurnEndReason -> "blocked"
                MaxTokensTurnEndReason -> "max-tokens"
                InterruptedTurnEndReason -> "interrupted"
                is AbortedTurnEndReason -> when (cause.cause) {
                    UserTurnAbortCause -> "aborted/user"
                    ParentTurnAbortCause -> "aborted/parent"
                    is HookTurnAbortCause -> "aborted/hook"
                    DisposedTurnAbortCause -> "aborted/disposed"
                    LegacyTurnAbortCause -> "aborted/legacy"
                }
            }
            return ScheduledTerminalEvidence(user.turn, userEnvelope.seq, endEnvelope.seq, endEnvelope.time,
                outcome, reason, (end.reason as? ErrorTurnEndReason)?.failure?.code)
        }
    }
}
