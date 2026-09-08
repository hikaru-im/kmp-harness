package im.hikaru.harness.llm.koog.openai.responses

import ai.koog.prompt.streaming.StreamFrame
import im.hikaru.harness.llm.koog.KoogStreamContext
import im.hikaru.harness.llm.koog.KoogTextTerminalPolicy

/** Koog Responses client uses End(null) instead of TextComplete for streamed text. */
public class OpenAiResponsesTextTerminalPolicy : KoogTextTerminalPolicy {
    override fun shouldCompleteOpenTextBlocks(
        frame: StreamFrame.End,
        @Suppress("UNUSED_PARAMETER")
        context: KoogStreamContext,
    ): Boolean = frame.finishReason == null
}
