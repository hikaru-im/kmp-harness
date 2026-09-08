package im.hikaru.harness.llm.koog.openai.chat

import ai.koog.prompt.streaming.StreamFrame
import im.hikaru.harness.llm.koog.KoogStreamContext
import im.hikaru.harness.llm.koog.KoogToolCallTerminalPolicy

/**
 * OpenAI Chat Completions 会以 `tool_calls` End 结束仅含 delta 的工具调用流。
 *
 * 该 policy 只声明何时允许公共 terminal 层补齐 block-end，本身不能修改流状态。
 */
public class OpenAiChatToolCallTerminalPolicy : KoogToolCallTerminalPolicy {
    override fun shouldCompleteOpenToolCalls(
        frame: StreamFrame.End,
        @Suppress("UNUSED_PARAMETER")
        context: KoogStreamContext,
    ): Boolean = frame.finishReason == "tool_calls"
}
