package im.hikaru.harness.llm.koog.openai.responses

import ai.koog.prompt.streaming.StreamFrame
import im.hikaru.harness.llm.BlockEndChunk
import im.hikaru.harness.llm.BlockStartChunk
import im.hikaru.harness.llm.LlmException
import im.hikaru.harness.llm.ReasoningBlock
import im.hikaru.harness.llm.ReasoningDeltaChunk
import im.hikaru.harness.llm.StreamChunk
import im.hikaru.harness.llm.koog.KoogLlmErrorCode
import im.hikaru.harness.llm.koog.KoogReasoningMapper
import im.hikaru.harness.llm.koog.KoogStreamBlockType
import im.hikaru.harness.llm.koog.KoogStreamContext
import im.hikaru.harness.llm.koog.KoogStreamState

/** OpenAI Responses reasoning mapper that keeps provider-private fields for native replay. */
public class OpenAiResponsesReasoningMapper : KoogReasoningMapper {
    override fun map(
        frame: StreamFrame,
        state: KoogStreamState,
        @Suppress("UNUSED_PARAMETER")
        context: KoogStreamContext,
    ): List<StreamChunk> =
        when (frame) {
            is StreamFrame.ReasoningDelta -> {
                requirePrivateDeltaFields(frame)
                val block =
                    state.open(
                        providerIndex = frame.index,
                        type = KoogStreamBlockType.REASONING,
                    )
                block.observeReasoning(
                    id = frame.id,
                    summary = frame.summary?.let(::listOf) ?: emptyList(),
                )
                val text = frame.text
                buildList {
                    if (text != null) {
                        if (block.startIfNeeded()) {
                            add(
                                BlockStartChunk(
                                    index = block.outputIndex,
                                    blockType = block.type.harnessType,
                                )
                            )
                        }
                        block.append(text)
                        add(ReasoningDeltaChunk(block.outputIndex, text))
                    }
                }
            }

            is StreamFrame.ReasoningComplete -> {
                requirePrivateCompleteFields(frame)
                val block =
                    state.open(
                        providerIndex = frame.index,
                        type = KoogStreamBlockType.REASONING,
                    )
                block.observeReasoning(
                    id = frame.id,
                    summary = frame.summary ?: emptyList(),
                    encrypted = frame.encrypted,
                    replaceSummary = frame.summary != null,
                )
                val wasStarted = block.started
                block.startIfNeeded()
                val content = frame.content.joinToString("")
                block.complete(finalText = content)
                buildList {
                    if (!wasStarted) {
                        add(
                            BlockStartChunk(
                                index = block.outputIndex,
                                blockType = block.type.harnessType,
                            )
                        )
                    }
                    add(
                        BlockEndChunk(
                            index = block.outputIndex,
                            block = ReasoningBlock(content),
                        )
                    )
                }
            }

            else ->
                throw LlmException(
                    message = "OpenAI Responses reasoning mapper received a non-reasoning frame",
                    code = KoogLlmErrorCode.INVALID_STREAM_STATE,
                )
        }
}

private fun requirePrivateDeltaFields(frame: StreamFrame.ReasoningDelta) {
    val id = frame.id
    val summary = frame.summary
    if (id != null && id.isBlank()) {
        unsupportedPrivateReasoning("OpenAI Responses reasoning delta id must not be blank")
    }
    if (summary != null && summary.isBlank()) {
        unsupportedPrivateReasoning("OpenAI Responses reasoning delta summary must not be blank")
    }
}

private fun requirePrivateCompleteFields(frame: StreamFrame.ReasoningComplete) {
    val id = frame.id
    val summary = frame.summary
    val encrypted = frame.encrypted
    if (id != null && id.isBlank()) {
        unsupportedPrivateReasoning("OpenAI Responses reasoning complete id must not be blank")
    }
    if (summary != null && summary.isEmpty()) {
        unsupportedPrivateReasoning("OpenAI Responses reasoning complete summary must not be empty")
    }
    if (summary?.any(String::isBlank) == true) {
        unsupportedPrivateReasoning("OpenAI Responses reasoning complete summary must not contain blanks")
    }
    if (encrypted != null && encrypted.isBlank()) {
        unsupportedPrivateReasoning("OpenAI Responses reasoning encrypted content must not be blank")
    }
}

private fun unsupportedPrivateReasoning(message: String): Nothing =
    throw LlmException(
        message = message,
        code = KoogLlmErrorCode.UNSUPPORTED_REASONING_CONTENT,
    )
