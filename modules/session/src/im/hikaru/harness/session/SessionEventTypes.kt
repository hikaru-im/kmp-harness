package im.hikaru.harness.session

import im.hikaru.harness.llm.LlmCallConfig
import im.hikaru.harness.llm.LlmCallConfigAdapterDefaults
import im.hikaru.harness.llm.LlmFailure
import im.hikaru.harness.llm.Message
import im.hikaru.harness.llm.MessageRole
import im.hikaru.harness.llm.ModelMessageSource
import im.hikaru.harness.llm.PluginMessageSource
import im.hikaru.harness.llm.StreamChunk
import im.hikaru.harness.llm.TokenUsage
import im.hikaru.harness.llm.ToolSchema
import im.hikaru.harness.llm.UserMessageSource
import im.hikaru.harness.llm.ToolMessageSource
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class TurnStartEvent(
    val turn: Long,
) {
    init {
        require(turn > 0L) { "Turn number must be positive" }
    }
}

@Serializable
sealed interface TurnAbortCause

@Serializable
@SerialName("user")
data object UserTurnAbortCause : TurnAbortCause

@Serializable
@SerialName("parent")
data object ParentTurnAbortCause : TurnAbortCause

@Serializable
@SerialName("hook")
data class HookTurnAbortCause(
    val reason: String,
) : TurnAbortCause {
    init {
        require(reason.isNotBlank()) { "Hook abort reason must not be blank" }
    }
}

@Serializable
@SerialName("disposed")
data object DisposedTurnAbortCause : TurnAbortCause

@Serializable
@SerialName("legacy")
data object LegacyTurnAbortCause : TurnAbortCause

@Serializable
sealed interface TurnEndReason

@Serializable
@SerialName("completed")
data object CompletedTurnEndReason : TurnEndReason

@Serializable
@SerialName("aborted")
data class AbortedTurnEndReason(
    val cause: TurnAbortCause,
) : TurnEndReason

@Serializable
@SerialName("blocked")
data object BlockedTurnEndReason : TurnEndReason

@Serializable
@SerialName("error")
data class ErrorTurnEndReason(
    val failure: LlmFailure,
) : TurnEndReason

@Serializable
@SerialName("max-tokens")
data object MaxTokensTurnEndReason : TurnEndReason

@Serializable
@SerialName("interrupted")
data object InterruptedTurnEndReason : TurnEndReason

@Serializable
data class TurnEndEvent(
    val turn: Long,
    val reason: TurnEndReason,
) {
    init {
        require(turn > 0L) { "Turn number must be positive" }
    }
}

@Serializable
data class StepStartEvent(
    val turn: Long,
    val step: Long,
) {
    init {
        require(turn > 0L) { "Turn number must be positive" }
        require(step > 0L) { "Step number must be positive" }
    }
}

@Serializable
data class StepEndEvent(
    val turn: Long,
    val step: Long,
) {
    init {
        require(turn > 0L) { "Turn number must be positive" }
        require(step > 0L) { "Step number must be positive" }
    }
}

@Serializable
data class UserMessageEvent(
    val turn: Long,
    val message: Message,
) {
    init {
        require(turn > 0L) { "Turn number must be positive" }
        require(message.role == MessageRole.USER) {
            "User message event requires user role"
        }
        require(
            message.source is UserMessageSource ||
                message.source is PluginMessageSource ||
                message.source is ToolMessageSource
        ) {
            "User message event requires user or plugin source"
        }
    }
}

@Serializable
data class AssistantChunkEvent(
    val turn: Long,
    val step: Long,
    val chunk: StreamChunk,
) {
    init {
        require(turn > 0L) { "Turn number must be positive" }
        require(step > 0L) { "Step number must be positive" }
    }
}

@Serializable
data class AssistantMessageEvent(
    val turn: Long,
    val step: Long,
    val message: Message,
    val usage: TokenUsage? = null,
    val interrupted: Boolean? = null,
) {
    init {
        require(turn > 0L) { "Turn number must be positive" }
        require(step > 0L) { "Step number must be positive" }
        require(message.role == MessageRole.ASSISTANT && message.source is ModelMessageSource) {
            "Assistant message event requires assistant role and model source"
        }
        require(interrupted == null || interrupted) {
            "Assistant message interrupted must be true when present"
        }
    }
}

@Serializable
enum class EpochReason {
    @SerialName("initial")
    INITIAL,

    @SerialName("resume")
    RESUME,

    @SerialName("change")
    CHANGE,

    @SerialName("series")
    SERIES,
}

@Serializable
data class EpochHeader(
    val config: LlmCallConfig,
    val adapterDefaults: LlmCallConfigAdapterDefaults? = null,
    val system: String? = null,
    val tools: List<ToolSchema>? = null,
    val reason: EpochReason,
    val startsSeries: Boolean? = null,
) {
    init {
        require(startsSeries == null || startsSeries) {
            "Epoch startsSeries must be true when present"
        }
    }
}

@Serializable
data class RequestHeaderEvent(
    val turn: Long,
    val step: Long,
    val header: EpochHeader,
) {
    init {
        require(turn > 0L) { "Turn number must be positive" }
        require(step > 0L) { "Step number must be positive" }
    }
}

@Serializable
data class SessionRequestContext(
    val provider: String,
    val model: String,
    val contextWindow: Long? = null,
) {
    init {
        require(provider.isNotBlank()) { "Request context provider must not be blank" }
        require(model.isNotBlank()) { "Request context model must not be blank" }
        require(contextWindow == null || contextWindow > 0L) {
            "Request context window must be positive"
        }
    }
}

@Serializable
data class RequestContextEvent(
    val turn: Long,
    val step: Long,
    val context: SessionRequestContext,
) {
    init {
        require(turn > 0L) { "Turn number must be positive" }
        require(step > 0L) { "Step number must be positive" }
    }
}

fun canonicalHeader(header: EpochHeader): EpochHeader =
    header.copy(
        config = header.config.copy(stop = header.config.stop?.toList()),
        adapterDefaults = header.adapterDefaults?.copy(),
        system = header.system?.takeIf(String::isNotEmpty),
        tools =
            header.tools
                ?.takeIf(List<ToolSchema>::isNotEmpty)
                ?.map { tool ->
                    tool.copy(
                        parameters = tool.parameters.detachedJson() as JsonObject,
                    )
                },
    )

fun headerEquals(
    first: EpochHeader,
    second: EpochHeader,
): Boolean =
    canonicalHeader(first) == canonicalHeader(second)
