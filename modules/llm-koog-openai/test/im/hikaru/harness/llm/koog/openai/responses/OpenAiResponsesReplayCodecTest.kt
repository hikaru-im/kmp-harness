package im.hikaru.harness.llm.koog.openai.responses

import ai.koog.prompt.message.Message as KoogMessage
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.streaming.StreamFrame
import im.hikaru.harness.llm.BlockEndChunk
import im.hikaru.harness.llm.CallId
import im.hikaru.harness.llm.ContentBlock
import im.hikaru.harness.llm.LlmException
import im.hikaru.harness.llm.MaxTokensFinishReason
import im.hikaru.harness.llm.Message as HarnessMessage
import im.hikaru.harness.llm.ModelMessageSource
import im.hikaru.harness.llm.ReasoningBlock
import im.hikaru.harness.llm.StopFinishReason
import im.hikaru.harness.llm.TextBlock
import im.hikaru.harness.llm.ToolCallBlock
import im.hikaru.harness.llm.ToolResultBlock
import im.hikaru.harness.llm.createAssistantMessage
import im.hikaru.harness.llm.createToolResultMessage
import im.hikaru.harness.llm.koog.KoogLlmErrorCode
import im.hikaru.harness.llm.koog.KoogMessageMappingContext
import im.hikaru.harness.llm.koog.KoogReplayContext
import im.hikaru.harness.llm.koog.KoogStreamBlockType
import im.hikaru.harness.llm.koog.KoogStreamContext
import im.hikaru.harness.llm.koog.KoogStreamState
import im.hikaru.harness.llm.koog.openai.catalog.OpenAiKoogCatalog
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class OpenAiResponsesReplayCodecTest {

    @Test
    fun writerAndRestorerShouldRoundTripPrivateReasoningMetadataOnly() {
        val raw = writeReasoningState()
        val message = assistantWithState(raw, listOf(ReasoningBlock("durable reasoning"), TextBlock("answer")))
        val mapped = map(message)

        val restored =
            OpenAiResponsesReplayRestorer().restore(
                message = message,
                base = mapped,
                context = replayContext(),
            )
        val reasoning = assertIs<MessagePart.Reasoning>(restored.parts[0])

        assertEquals(listOf("durable reasoning"), reasoning.content)
        assertEquals("reasoning-1", reasoning.id)
        assertEquals(listOf("final summary"), reasoning.summary)
        assertEquals("encrypted-1", reasoning.encrypted)
        assertEquals(MessagePart.Text("answer"), restored.parts[1])
        assertEquals(false, raw.toString().contains("durable reasoning"))
        assertEquals(false, raw.toString().contains("state-only"))
    }

    @Test
    fun reasoningCompleteSummaryShouldReplaceEarlierDeltaSummary() {
        val state = KoogStreamState()
        val mapper = OpenAiResponsesReasoningMapper()
        mapper.map(
            StreamFrame.ReasoningDelta(
                id = "reasoning-1",
                text = "draft",
                summary = "draft summary",
                index = 0,
            ),
            state,
            streamContext(),
        )
        mapper.map(
            StreamFrame.ReasoningComplete(
                id = "reasoning-1",
                content = listOf("durable"),
                summary = listOf("final summary"),
                encrypted = "encrypted-1",
                index = 0,
            ),
            state,
            streamContext(),
        )

        val block = state.replayBlocks().single()
        assertEquals(listOf("final summary"), block.reasoningSummary)
        assertEquals("durable", block.text)
    }

    @Test
    fun unknownOrMismatchedReplayIdentityShouldFailLoudly() {
        val raw = writeReasoningState()
        val message = assistantWithState(raw, listOf(ReasoningBlock("durable reasoning"), TextBlock("answer")))

        listOf(
            raw.withField("kind", JsonPrimitive("unknown")) to replayContext(),
            raw.withField("version", JsonPrimitive(2)) to replayContext(),
            raw.withField("provider", JsonPrimitive("other-route")) to replayContext(),
            raw.withField("model", JsonPrimitive("other-model")) to replayContext(),
            raw to replayContext().copy(sourceProvider = "other-route"),
            raw to replayContext().copy(sourceModel = "other-model"),
            raw to replayContext().copy(targetProvider = "other-route"),
            raw to replayContext().copy(targetModel = "other-model"),
        ).forEach { (state, context) ->
            val candidate = assistantWithState(state, message.content)
            val error =
                assertFailsWith<LlmException> {
                    OpenAiResponsesReplayRestorer().restore(
                        message = candidate,
                        base = map(candidate),
                        context = context,
                    )
                }
            assertEquals(KoogLlmErrorCode.INVALID_REPLAY_STATE, error.code)
        }
    }

    @Test
    fun blockMismatchAndUnknownFieldsShouldFailInsteadOfChangingDurableContent() {
        val raw = writeReasoningState()
        val oneBlock = assistantWithState(raw, listOf(ReasoningBlock("durable reasoning")))
        assertInvalidReplay(oneBlock, replayContext())

        val wrongType =
            assistantWithState(
                raw,
                listOf(TextBlock("durable reasoning"), TextBlock("answer")),
            )
        assertInvalidReplay(wrongType, replayContext())

        val withUnknown = assistantWithState(
            raw.withField("unexpected", JsonPrimitive(true)),
            listOf(ReasoningBlock("durable reasoning"), TextBlock("answer")),
        )
        assertInvalidReplay(withUnknown, replayContext())

        val malformedBlocks = assistantWithState(
            raw.withField("blocks", JsonPrimitive("not-an-array")),
            listOf(ReasoningBlock("durable reasoning"), TextBlock("answer")),
        )
        assertInvalidReplay(malformedBlocks, replayContext())
    }

    @Test
    fun foreignStateShouldFallBackToProviderNeutralMappedMessage() {
        val foreign =
            buildJsonObject {
                put("kind", "foreign")
                put("version", 1)
                put("provider", "other-provider")
                put("model", "other-model")
                put("stopReason", "stop")
                put("blocks", rawBlocks())
            }
        val message = assistantWithState(foreign, listOf(ReasoningBlock("durable reasoning"), TextBlock("answer")))
        val restored =
            OpenAiResponsesReplayRestorer().restore(
                message = message,
                base = map(message),
                context = replayContext().copy(sourceProvider = "other-provider", sourceModel = "other-model"),
            )

        assertEquals(map(message), restored)
    }

    @Test
    fun toolResultMapperShouldRejectNonTextNestedContent() {
        val message =
            createToolResultMessage(
                callId = CallId("call-1"),
                content = listOf(ReasoningBlock("private")),
                isError = false,
            )
        val error =
            assertFailsWith<LlmException> {
                OpenAiResponsesMessageMapper().map(message, KoogMessageMappingContext())
            }
        assertEquals(KoogLlmErrorCode.UNSUPPORTED_CONTENT, error.code)
    }

    @Test
    fun writerShouldReturnNullForFailedFinishReasons() {
        val state = completedReasoningState()
        val writer = OpenAiResponsesReplayWriter()
        val frame = StreamFrame.End(finishReason = "stop")
        val context = streamContext()

        assertEquals(
            null,
            writer.write(
                state = state,
                frame = frame,
                context = context,
                finishReason = im.hikaru.harness.llm.ErrorFinishReason(
                    im.hikaru.harness.llm.LlmFailure("failed", "SERVER")
                ),
            ),
        )
    }

    @Test
    fun maxTokensReplayShouldMatchDurableBlockAssemblerFiltering() {
        val state =
            KoogStreamState().also { stream ->
                stream.open(0, KoogStreamBlockType.TEXT).also { block ->
                    block.startIfNeeded()
                    block.complete(finalText = "answer")
                }
                stream.open(1, KoogStreamBlockType.TOOL_CALL).also { block ->
                    block.observeToolCallIdentity(id = "call-1", name = "lookup")
                    block.startIfNeeded()
                    block.complete(finalText = "{}")
                }
                stream.finish()
            }
        val raw =
            checkNotNull(
                OpenAiResponsesReplayWriter().write(
                    state = state,
                    frame = StreamFrame.End(finishReason = "max_output_tokens"),
                    context = streamContext(),
                    finishReason = MaxTokensFinishReason,
                )
            )

        val blocks = raw.jsonObject.getValue("blocks").jsonArray
        assertEquals(1, blocks.size)
        assertEquals(0, blocks.single().jsonObject.getValue("index").jsonPrimitive.int)
        assertEquals("text", blocks.single().jsonObject.getValue("type").jsonPrimitive.content)
    }

    private fun writeReasoningState(): JsonElement {
        val state = completedReasoningState()
        return checkNotNull(
            OpenAiResponsesReplayWriter().write(
                state = state,
                frame = StreamFrame.End(finishReason = null),
                context = streamContext(),
                finishReason = StopFinishReason,
            )
        )
    }

    private fun completedReasoningState(): KoogStreamState =
        KoogStreamState().also { state ->
            state.open(0, KoogStreamBlockType.REASONING).also { block ->
                block.startIfNeeded()
                block.observeReasoning(
                    id = "reasoning-1",
                    summary = listOf("final summary"),
                    encrypted = "encrypted-1",
                )
                block.append("state-only")
                block.complete(finalText = "durable reasoning")
            }
            state.open(1, KoogStreamBlockType.TEXT).also { block ->
                block.startIfNeeded()
                block.append("answer")
                block.complete()
            }
            state.finish()
        }

    private fun assistantWithState(state: JsonElement, content: List<ContentBlock>): HarnessMessage =
        createAssistantMessage(
            content = content,
            provider = OpenAiKoogCatalog.OPENAI_PROVIDER_ID,
            model = MODEL,
            replayState = state,
        )

    private fun map(message: HarnessMessage): KoogMessage.Assistant =
        assertIs(
            OpenAiResponsesMessageMapper().map(
                message = message,
                context = KoogMessageMappingContext(targetContext = streamContext()),
            )
        )

    private fun assertInvalidReplay(message: HarnessMessage, context: KoogReplayContext) {
        val error =
            assertFailsWith<LlmException> {
                OpenAiResponsesReplayRestorer().restore(message, map(message), context)
            }
        assertEquals(KoogLlmErrorCode.INVALID_REPLAY_STATE, error.code)
    }

    private fun replayContext() =
        KoogReplayContext(
            sourceProvider = OpenAiKoogCatalog.OPENAI_PROVIDER_ID,
            sourceModel = MODEL,
            targetProvider = OpenAiKoogCatalog.OPENAI_PROVIDER_ID,
            targetModel = MODEL,
            targetKoogProvider = "openai",
            targetApi = OpenAiResponsesOptionMapper.OPENAI_RESPONSES_API_ID,
        )

    private fun streamContext() =
        KoogStreamContext(
            provider = OpenAiKoogCatalog.OPENAI_PROVIDER_ID,
            model = MODEL,
            koogProvider = "openai",
            api = OpenAiResponsesOptionMapper.OPENAI_RESPONSES_API_ID,
        )

    private fun JsonElement.withField(name: String, value: JsonElement): JsonObject =
        buildJsonObject {
            jsonObject.forEach { (key, element) -> put(key, element) }
            put(name, value)
        }

    private fun rawBlocks(): JsonElement =
        writeReasoningState().jsonObject.getValue("blocks")

    private companion object {
        const val MODEL = "gpt-4o-mini"
    }
}
