package im.hikaru.harness.llm.koog

import ai.koog.prompt.message.Message as KoogMessage
import ai.koog.prompt.message.MessagePart
import im.hikaru.harness.llm.CallId
import im.hikaru.harness.llm.LlmException
import im.hikaru.harness.llm.ReasoningBlock
import im.hikaru.harness.llm.TextBlock
import im.hikaru.harness.llm.ToolCallBlock
import im.hikaru.harness.llm.createAssistantMessage
import im.hikaru.harness.llm.createToolResultMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class KoogToolMessageMapperContractTest {

    private val mapper = DefaultKoogMessageMapper()

    @Test
    fun assistantToolCallShouldPreserveRawArgumentsAndMessageId() {
        val message =
            createAssistantMessage(
                content =
                    listOf(
                        TextBlock("I will look it up."),
                        ToolCallBlock(
                            id = CallId("call-1"),
                            name = "lookup",
                            arguments = "{\"query\":\"value\"}",
                        ),
                    ),
                provider = "test",
                model = "test-model",
            )

        val mapped =
            assertIs<KoogMessage.Assistant>(
                mapper.map(message, KoogMessageMappingContext())
            )

        assertEquals(message.id.value, mapped.id)
        assertEquals("I will look it up.", assertIs<MessagePart.Text>(mapped.parts[0]).text)
        val call = assertIs<MessagePart.Tool.Call>(mapped.parts[1])
        assertEquals("call-1", call.id)
        assertEquals("lookup", call.tool)
        assertEquals("{\"query\":\"value\"}", call.args)
    }

    @Test
    fun toolResultShouldResolveNameOnlyFromPreviousAssistantCall() {
        val callId = CallId("call-1")
        val context = KoogMessageMappingContext()
        context.observe(
            createAssistantMessage(
                content = listOf(ToolCallBlock(callId, "lookup", "{}")),
                provider = "test",
                model = "test-model",
            )
        )
        val resultMessage =
            createToolResultMessage(
                callId = callId,
                content = listOf(TextBlock("result")),
                isError = true,
            )

        val mapped = assertIs<KoogMessage.User>(mapper.map(resultMessage, context))
        assertEquals(resultMessage.id.value, mapped.id)
        val result = assertIs<MessagePart.Tool.Result>(mapped.parts.single())
        assertEquals("call-1", result.id)
        assertEquals("lookup", result.tool)
        assertEquals("result", result.output)
        assertEquals(true, result.isError)
    }

    @Test
    fun toolResultShouldPreserveMultipleTextPartsWithoutJoiningOrReordering() {
        val callId = CallId("call-1")
        val context = KoogMessageMappingContext()
        context.observe(
            createAssistantMessage(
                content = listOf(ToolCallBlock(callId, "lookup", "{}")),
                provider = "test",
                model = "test-model",
            )
        )

        val mapped =
            assertIs<KoogMessage.User>(
                mapper.map(
                    createToolResultMessage(
                        callId = callId,
                        content = listOf(TextBlock("first"), TextBlock("second")),
                        isError = false,
                    ),
                    context,
                )
            )
        val result = assertIs<MessagePart.Tool.Result>(mapped.parts.single())

        assertEquals(
            listOf("first", "second"),
            result.parts.map { part -> assertIs<MessagePart.Text>(part).text },
        )
    }

    @Test
    fun resultWithoutPreviousCallShouldFailInsteadOfUsingUnknownName() {
        val error =
            assertFailsWith<LlmException> {
                mapper.map(
                    createToolResultMessage(
                        callId = CallId("missing"),
                        content = listOf(TextBlock("result")),
                        isError = false,
                    ),
                    KoogMessageMappingContext(),
                )
            }

        assertEquals(KoogLlmErrorCode.INVALID_TOOL_HISTORY, error.code)
    }

    @Test
    fun unsupportedNestedToolResultContentShouldFailInsteadOfBeingDropped() {
        val callId = CallId("call-1")
        val context = KoogMessageMappingContext()
        context.observe(
            createAssistantMessage(
                content = listOf(ToolCallBlock(callId, "lookup", "{}")),
                provider = "test",
                model = "test-model",
            )
        )

        val error =
            assertFailsWith<LlmException> {
                mapper.map(
                    createToolResultMessage(
                        callId = callId,
                        content = listOf(ReasoningBlock("private")),
                        isError = false,
                    ),
                    context,
                )
            }

        assertEquals(KoogLlmErrorCode.UNSUPPORTED_CONTENT, error.code)
    }
}
