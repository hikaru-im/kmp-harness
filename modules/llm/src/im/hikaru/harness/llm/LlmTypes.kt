package im.hikaru.harness.llm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** 可序列化、提供方无关的模型调用失败事实。 */
@Serializable
data class LlmFailure(
    val message: String,
    val code: String,
    val status: Int? = null,
    val providerRetryAfterMs: Long? = null,
    val requestId: ProviderRequestId? = null,
) {
    init {
        require(message.isNotBlank()) {
            "LLM failure message must not be blank"
        }
        require(code.isNotBlank()) {
            "LLM failure code must not be blank"
        }
        require(status == null || status in 100..599) {
            "LLM failure status must be between 100 and 599"
        }
        require(providerRetryAfterMs == null || providerRetryAfterMs > 0L) {
            "Provider retry delay must be positive"
        }
    }
}

/** 一次模型请求为什么停止。 */
@Serializable
sealed interface FinishReason

@Serializable
@SerialName("stop")
data object StopFinishReason : FinishReason

@Serializable
@SerialName("tool-calls")
data object ToolCallsFinishReason : FinishReason

@Serializable
@SerialName("max-tokens")
data object MaxTokensFinishReason : FinishReason

@Serializable
@SerialName("aborted")
data class AbortedFinishReason(
    val failure: LlmFailure,
) : FinishReason

@Serializable
@SerialName("error")
data class ErrorFinishReason(
    val failure: LlmFailure,
) : FinishReason

/** 一次模型调用的互不重叠 token 计数。 */
@Serializable
data class TokenUsage(
    val inputTokens: Long,
    val outputTokens: Long,
    val cacheReadTokens: Long? = null,
    val cacheWriteTokens: Long? = null,
    val reasoningTokens: Long? = null,
) {
    init {
        require(inputTokens >= 0L && outputTokens >= 0L) {
            "Token usage must not be negative"
        }
        require(cacheReadTokens == null || cacheReadTokens >= 0L) {
            "Cache read tokens must not be negative"
        }
        require(cacheWriteTokens == null || cacheWriteTokens >= 0L) {
            "Cache write tokens must not be negative"
        }
        require(reasoningTokens == null || reasoningTokens >= 0L) {
            "Reasoning tokens must not be negative"
        }
    }
}

/** 适配器产生的原始流式分片。 */
@Serializable
sealed interface StreamChunk

@Serializable
@SerialName("block-start")
data class BlockStartChunk(
    val index: Int,
    val blockType: String,
) : StreamChunk {
    init {
        require(index >= 0) {
            "Block index must not be negative"
        }
        require(blockType.isNotBlank()) {
            "Block type must not be blank"
        }
    }
}

@Serializable
@SerialName("text-delta")
data class TextDeltaChunk(
    val index: Int,
    val text: String,
) : StreamChunk {
    init {
        require(index >= 0) {
            "Block index must not be negative"
        }
    }
}

@Serializable
@SerialName("reasoning-delta")
data class ReasoningDeltaChunk(
    val index: Int,
    val text: String,
) : StreamChunk {
    init {
        require(index >= 0) {
            "Block index must not be negative"
        }
    }
}

@Serializable
@SerialName("tool-call-delta")
data class ToolCallDeltaChunk(
    val index: Int,
    val id: CallId,
    val name: String? = null,
    val argumentsDelta: String,
) : StreamChunk {
    init {
        require(index >= 0) {
            "Block index must not be negative"
        }
    }
}

@Serializable
@SerialName("block-end")
data class BlockEndChunk(
    val index: Int,
    val block: ContentBlock,
) : StreamChunk {
    init {
        require(index >= 0) {
            "Block index must not be negative"
        }
    }
}

@Serializable
@SerialName("usage")
data class UsageChunk(
    val usage: TokenUsage,
) : StreamChunk

@Serializable
@SerialName("finish")
data class FinishChunk(
    val reason: FinishReason,
    val replayState: JsonElement? = null,
) : StreamChunk

/** 发送给模型的工具 JSON Schema。 */
@Serializable
data class ToolSchema(
    val name: String,
    val description: String,
    val parameters: JsonObject,
) {
    init {
        require(name.isNotBlank()) {
            "Tool schema name must not be blank"
        }
    }
}

/** 一段会话保持稳定的模型调用配置。 */
@Serializable
data class LlmCallConfig(
    val provider: String,
    val model: String,
    val reasoningEffort: ReasoningEffortId? = null,
    val temperature: Double? = null,
    val maxTokens: Long? = null,
    val stop: List<String>? = null,
) {
    init {
        require(provider.isNotBlank()) {
            "LLM provider must not be blank"
        }
        require(model.isNotBlank()) {
            "LLM model must not be blank"
        }
        require(temperature == null || temperature.isFinite()) {
            "Temperature must be finite"
        }
        require(maxTokens == null || maxTokens > 0L) {
            "Max tokens must be positive"
        }
        require(stop == null || stop.all(String::isNotEmpty)) {
            "Stop sequences must not contain empty strings"
        }
    }
}

/** 由精确模型解析补入的配置字段。 */
@Serializable
data class LlmCallConfigAdapterDefaults(
    val reasoningEffort: Boolean = false,
    val maxTokens: Boolean = false,
)

/** 辅助模型调用的提供方无关用途。 */
@Serializable
enum class LlmCallPurpose {
    @SerialName("compaction")
    COMPACTION,

    @SerialName("session-title")
    SESSION_TITLE,
}

/** 一次已经组装完成、可直接交给适配器的模型请求。 */
data class GenerateOptions(
    val provider: String,
    val model: String,
    val messages: List<Message>,
    val reasoningEffort: ReasoningEffortId? = null,
    val system: String? = null,
    val tools: List<ToolSchema>? = null,
    val temperature: Double? = null,
    val maxTokens: Long? = null,
    val stop: List<String>? = null,
    val sessionId: LlmSessionId? = null,
    val purpose: LlmCallPurpose? = null,
) {
    init {
        LlmCallConfig(
            provider = provider,
            model = model,
            reasoningEffort = reasoningEffort,
            temperature = temperature,
            maxTokens = maxTokens,
            stop = stop,
        )
    }

    fun callConfig(): LlmCallConfig =
        LlmCallConfig(
            provider = provider,
            model = model,
            reasoningEffort = reasoningEffort,
            temperature = temperature,
            maxTokens = maxTokens,
            stop = stop?.toList(),
        )
}
