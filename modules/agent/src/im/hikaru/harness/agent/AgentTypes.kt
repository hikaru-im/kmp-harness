package im.hikaru.harness.agent

import im.hikaru.harness.llm.GenerateOptions
import im.hikaru.harness.llm.LlmCallConfig
import im.hikaru.harness.llm.LlmFailure
import im.hikaru.harness.llm.Message
import im.hikaru.harness.llm.ReasoningEffortId
import im.hikaru.harness.llm.RetryPolicy
import im.hikaru.harness.llm.defaultRetryPolicy
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.event.EventKey
import im.hikaru.harness.runtime.event.SequentialEventKey
import im.hikaru.harness.runtime.event.SuspendWaterfallEventKey
import im.hikaru.harness.session.Session
import im.hikaru.harness.session.SessionId
import im.hikaru.harness.session.TurnEndReason
import kotlinx.serialization.Serializable

@Serializable
data class AgentOptions(
    val preset: String? = null,
    val cwd: String? = null,
    val provider: String? = null,
    val model: String? = null,
    val reasoningEffort: ReasoningEffortId? = null,
    val maxTokens: Long? = null,
) {
    init {
        require(preset == null || preset.isNotBlank()) { "Agent preset must not be blank" }
        require(cwd == null || cwd.isNotBlank()) { "Agent cwd must not be blank" }
        require(provider == null || provider.isNotBlank()) { "Agent provider must not be blank" }
        require(model == null || model.isNotBlank()) { "Agent model must not be blank" }
        require(maxTokens == null || maxTokens > 0L) { "Agent max tokens must be positive" }
    }

    fun requireCallConfig(): LlmCallConfig {
        val selectedProvider = provider
        val selectedModel = model
        if (selectedProvider == null || selectedModel == null) {
            throw AgentException(
                "Agent model is not configured; both provider and model are required",
                AgentErrorCode.MODEL_NOT_CONFIGURED,
            )
        }
        return LlmCallConfig(
            provider = selectedProvider,
            model = selectedModel,
            reasoningEffort = reasoningEffort,
            maxTokens = maxTokens,
        )
    }
}

enum class AgentStatus {
    IDLE,
    RUNNING,
}

enum class InboxQueue {
    NEXT_TURN,
    NEXT_STEP,
}

enum class InboxSpliceOperation {
    INSERT,
    CLAIM,
    REPLACE,
    CLEAR,
}

enum class AgentSendTarget {
    NEXT_TURN,
    NEXT_STEP,
}

enum class AgentWakeup {
    NONE,
    IF_IDLE,
    ALWAYS,
}

/** 一条已经分配稳定身份、可进入 Inbox 的工作项。 */
@Serializable
data class InboxItem(
    val id: String,
    val message: Message,
    val target: AgentSendTarget,
    val wakeup: AgentWakeup,
) {
    init {
        require(id.isNotBlank()) { "Inbox item id must not be blank" }
    }
}

@Serializable
data class InboxSnapshot(
    val version: Long,
    val nextTurn: List<InboxItem>,
    val nextStep: List<InboxItem>,
)

/** Session 中记录 Inbox durable projection 的事件。 */
@Serializable
data class InboxSpliceEvent(
    val agent: SessionId,
    val queue: InboxQueue,
    val operation: InboxSpliceOperation,
    val version: Long,
    val items: List<InboxItem>,
) {
    init {
        require(version >= 0L) { "Inbox version must not be negative" }
    }
}

data class AgentNotice(
    val agent: Agent,
)

data class AgentStatusNotice(
    val agent: Agent,
    val status: AgentStatus,
)

data class AgentPreStepEvent(
    val agent: Agent,
    val turn: Long,
    val step: Long,
)

data class AgentRequestEvent(
    val agent: Agent,
    val turn: Long,
    val step: Long,
    val attempt: Int,
    val options: GenerateOptions,
)

data class AgentRequestErrorEvent(
    val agent: Agent,
    val turn: Long,
    val step: Long,
    val attempt: Int,
    val failure: LlmFailure,
    val retryPolicy: RetryPolicy = defaultRetryPolicy(),
)

sealed interface AgentRequestErrorDecision {
    data object Retry : AgentRequestErrorDecision
    data object Unhandled : AgentRequestErrorDecision
}

data class AgentTurnStoppingEvent(
    val agent: Agent,
    val turn: Long,
    val reason: TurnEndReason? = null,
)

data class AgentRestoreEvent(
    val agent: Agent,
)

object AgentEvents {
    val Created = EventKey<AgentNotice>("agent/created")
    val Disposed = EventKey<AgentNotice>("agent/disposed")
    val Status = EventKey<AgentStatusNotice>("agent/status")
    val InboxInserted = EventKey<AgentNotice>("agent/inbox/inserted")
    val InboxClaimed = EventKey<AgentNotice>("agent/inbox/claimed")
    val InboxDiscarded = EventKey<AgentNotice>("agent/inbox/discarded")
    val SessionStart = EventKey<AgentNotice>("agent/session-start")
    val Restore = SequentialEventKey<AgentRestoreEvent>("agent/restore")
    val PreStep = SuspendWaterfallEventKey<AgentPreStepEvent, Unit>("agent/pre-step")
    val Request = SuspendWaterfallEventKey<AgentRequestEvent, Unit>("agent/request")
    val RequestError =
        SuspendWaterfallEventKey<AgentRequestErrorEvent, AgentRequestErrorDecision>("agent/request-error")
    val TurnStopping = SequentialEventKey<AgentTurnStoppingEvent>("agent/turn-stopping")
}

/** 在共享 EventsService 上按 Agent identity 过滤 listener。 */
fun Context.onAgent(
    key: EventKey<AgentNotice>,
    agentId: AgentId,
    listener: (AgentNotice) -> Unit,
) = on(key) { notice ->
    if (notice.agent.id == agentId) listener(notice)
}

fun Context.onAgentStatus(
    agentId: AgentId,
    listener: (AgentStatusNotice) -> Unit,
) = on(AgentEvents.Status) { notice ->
    if (notice.agent.id == agentId) listener(notice)
}
