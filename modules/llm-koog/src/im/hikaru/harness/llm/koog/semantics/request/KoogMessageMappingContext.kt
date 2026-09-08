package im.hikaru.harness.llm.koog

import im.hikaru.harness.llm.CallId
import im.hikaru.harness.llm.LlmException
import im.hikaru.harness.llm.Message
import im.hikaru.harness.llm.MessageRole
import im.hikaru.harness.llm.ModelMessageSource
import im.hikaru.harness.llm.ToolCallBlock

/**
 * 一次请求内按消息顺序积累的转换上下文。
 *
 * Koog tool result 需要 tool name，而 Harness 结果块只保存 call id；名称只能从前序 assistant
 * tool-call 恢复。上下文不允许从未来消息回填，也不使用 unknown 名称伪装成功。
 */
class KoogMessageMappingContext(
    val targetContext: KoogStreamContext? = null,
) {
    private val toolNamesByCallId = mutableMapOf<CallId, String>()

    /** 在消息成功映射后记录它产生的 tool-call，供后续结果消息解析。 */
    fun observe(message: Message) {
        val calls = message.content.filterIsInstance<ToolCallBlock>()
        if (calls.isNotEmpty() && message.role != MessageRole.ASSISTANT) {
            invalidToolHistory("Tool calls must belong to assistant messages")
        }
        calls.forEach { call ->
            if (call.name.isBlank()) {
                invalidToolHistory("Tool call '${call.id.value}' has a blank name")
            }
            if (toolNamesByCallId.putIfAbsent(call.id, call.name) != null) {
                invalidToolHistory("Tool call '${call.id.value}' appears more than once")
            }
        }
    }

    /** 解析前序 tool-call 的名称；缺失关联时显式失败。 */
    fun requireToolName(callId: CallId): String =
        toolNamesByCallId[callId]
            ?: invalidToolHistory(
                "Tool result references unknown prior call '${callId.value}'"
            )

    /** 对带私有 state 的 assistant 历史构造 source/target 身份；普通消息返回 null。 */
    fun replayContext(message: Message): KoogReplayContext? {
        val source = message.source as? ModelMessageSource ?: return null
        if (source.replayState == null) {
            return null
        }
        val target =
            targetContext
                ?: throw LlmException(
                    message = "Koog replay restoration requires a resolved target model",
                    code = KoogLlmErrorCode.INVALID_REPLAY_STATE,
                )
        return KoogReplayContext(
            sourceProvider = source.provider,
            sourceModel = source.model,
            targetProvider = target.provider,
            targetModel = target.model,
            targetKoogProvider = target.koogProvider,
            targetApi = target.api,
        )
    }
}

private fun invalidToolHistory(message: String): Nothing =
    throw LlmException(
        message = message,
        code = KoogLlmErrorCode.INVALID_TOOL_HISTORY,
    )
