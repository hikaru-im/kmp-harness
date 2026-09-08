package im.hikaru.harness.llm

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** 一条消息跨收件箱、Session 日志和模型请求时保持不变的标识。 */
@JvmInline
@Serializable
value class MessageId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) {
            "MessageId must not be blank"
        }
    }
}

/** 关联模型发起的工具调用及其工具结果。 */
@JvmInline
@Serializable
value class CallId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) {
            "CallId must not be blank"
        }
    }
}

/** 提供方返回的请求标识，只用于诊断。 */
@JvmInline
@Serializable
value class ProviderRequestId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) {
            "ProviderRequestId must not be blank"
        }
    }
}

/** 适配器定义的推理强度标识，不在 Harness Core 中枚举具体取值。 */
@JvmInline
@Serializable
value class ReasoningEffortId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) {
            "ReasoningEffortId must not be blank"
        }
    }
}

/**
 * LLM 请求携带的 Session 标识。
 *
 * 它刻意不依赖未来的 session 模块，避免形成 llm <-> session 循环依赖。
 */
@JvmInline
@Serializable
value class LlmSessionId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) {
            "LlmSessionId must not be blank"
        }
    }
}

@OptIn(ExperimentalUuidApi::class)
internal fun newMessageId(): MessageId =
    MessageId(Uuid.random().toString())
