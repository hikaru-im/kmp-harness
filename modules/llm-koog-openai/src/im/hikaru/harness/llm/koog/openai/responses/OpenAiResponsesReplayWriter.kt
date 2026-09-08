package im.hikaru.harness.llm.koog.openai.responses

import ai.koog.prompt.streaming.StreamFrame
import im.hikaru.harness.llm.AbortedFinishReason
import im.hikaru.harness.llm.ErrorFinishReason
import im.hikaru.harness.llm.FinishReason
import im.hikaru.harness.llm.MaxTokensFinishReason
import im.hikaru.harness.llm.StopFinishReason
import im.hikaru.harness.llm.ToolCallsFinishReason
import im.hikaru.harness.llm.koog.KoogReplayWriter
import im.hikaru.harness.llm.koog.KoogStreamBlockSnapshot
import im.hikaru.harness.llm.koog.KoogStreamContext
import im.hikaru.harness.llm.koog.KoogStreamState
import kotlinx.serialization.json.JsonElement

/** Writes Provider metadata exposed by a successfully completed Responses stream. */
public class OpenAiResponsesReplayWriter : KoogReplayWriter {
    override fun write(
        state: KoogStreamState,
        @Suppress("UNUSED_PARAMETER")
        frame: StreamFrame.End,
        context: KoogStreamContext,
        finishReason: FinishReason,
    ): JsonElement? {
        requireResponsesContext(context)
        if (finishReason !is StopFinishReason &&
            finishReason !is ToolCallsFinishReason &&
            finishReason !is MaxTokensFinishReason
        ) {
            return null
        }

        state.requireFinished()
        val blocks =
            state.replayBlocks()
                .filterNot { block ->
                    finishReason is MaxTokensFinishReason && block.type == "tool-call"
                }
        if (blocks.isEmpty()) {
            return null
        }

        return OpenAiResponsesReplayJsonCodec.encode(
            OpenAiResponsesReplayState(
                kind = OPENAI_RESPONSES_REPLAY_KIND,
                version = OPENAI_RESPONSES_REPLAY_VERSION,
                provider = context.provider,
                model = context.model,
                stopReason = finishReason.replayName(),
                blocks = blocks.mapIndexed(::toReplayBlock),
            )
        )
    }

    private fun toReplayBlock(
        index: Int,
        block: KoogStreamBlockSnapshot,
    ): OpenAiResponsesReplayBlock =
        OpenAiResponsesReplayBlock(
            index = index,
            type = block.type,
            reasoningId = block.reasoningId,
            reasoningSummary = block.reasoningSummary,
            reasoningEncrypted = block.reasoningEncrypted,
        ).also { replay ->
            if (block.type != "reasoning" &&
                (replay.reasoningId != null ||
                    replay.reasoningSummary.isNotEmpty() ||
                    replay.reasoningEncrypted != null)
            ) {
                invalidResponsesReplay(
                    "OpenAI Responses private reasoning metadata belongs to a non-reasoning block"
                )
            }
        }

    private fun requireResponsesContext(context: KoogStreamContext) {
        if (context.api != OPENAI_RESPONSES_ROUTE_ID) {
            invalidResponsesReplay(
                "OpenAI Responses replay writer received api '${context.api}'"
            )
        }
    }

    private fun FinishReason.replayName(): String =
        when (this) {
            is StopFinishReason -> "stop"
            is ToolCallsFinishReason -> "tool-calls"
            is MaxTokensFinishReason -> "max-tokens"
            is AbortedFinishReason -> "aborted"
            is ErrorFinishReason -> "error"
        }
}
