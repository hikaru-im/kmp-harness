package im.hikaru.harness.agent

import im.hikaru.harness.llm.Message
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.effect.Disposable
import im.hikaru.harness.session.Session

/** Agent 的稳定公开句柄；具体循环由 agent-loop 提供。 */
interface Agent {
    val id: AgentId
    val options: AgentOptions
    val session: Session
    val status: AgentStatus
    val inbox: Inbox
    val context: Context

    suspend fun awaitIdle()

    suspend fun cancel(
        cause: Throwable? = null,
        keepInbox: Boolean = false,
    )

    suspend fun send(
        message: Message,
        target: AgentSendTarget = AgentSendTarget.NEXT_TURN,
        wakeup: AgentWakeup = AgentWakeup.IF_IDLE,
    ): String

    suspend fun followup(message: Message): String =
        send(message, AgentSendTarget.NEXT_TURN, AgentWakeup.IF_IDLE)

    suspend fun steer(message: Message): String =
        send(message, AgentSendTarget.NEXT_STEP, AgentWakeup.NONE)

    suspend fun inject(message: Message): String =
        send(message, AgentSendTarget.NEXT_STEP, AgentWakeup.IF_IDLE)
}

/**
 * Provider-neutral continuation of an already-open durable Agent step.
 *
 * The owner of the recovery protocol supplies [beforeAttempt] so AgentLoop
 * does not need to know which plugin persisted the continuation marker.
 */
class AgentAttemptResume(
    val turn: Long,
    val step: Long,
    val attempt: Int,
    val beforeAttempt: suspend () -> Unit = {},
) {
    init {
        require(turn > 0L) { "Resume turn must be positive" }
        require(step > 0L) { "Resume step must be positive" }
        require(attempt > 0) { "Resume attempt must be positive" }
    }
}

/** Optional capability implemented by Agents that can continue a durable attempt. */
interface AgentAttemptResumer {
    suspend fun resumeAttempt(request: AgentAttemptResume)
}

/** 用于自定义 Agent 实现的工厂请求；Factory 不需要依赖 agent-loop。 */
data class AgentFactoryRequest(
    val id: AgentId,
    val options: AgentOptions,
    val session: Session,
    val inbox: Inbox,
    val context: Context,
)

fun interface AgentFactory {
    suspend fun create(request: AgentFactoryRequest): Agent
}

interface AgentFactoryHandle : Disposable

interface AgentHandle : Disposable {
    val agent: Agent
}
