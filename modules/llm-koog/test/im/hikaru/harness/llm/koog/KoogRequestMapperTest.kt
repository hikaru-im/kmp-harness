package im.hikaru.harness.llm.koog

import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message as KoogMessage
import ai.koog.prompt.message.MessagePart
import ai.koog.prompt.params.LLMParams
import im.hikaru.harness.llm.GenerateOptions
import im.hikaru.harness.llm.LlmException
import im.hikaru.harness.llm.LlmSessionId
import im.hikaru.harness.llm.ReasoningBlock
import im.hikaru.harness.llm.ReasoningEffortId
import im.hikaru.harness.llm.TextBlock
import im.hikaru.harness.llm.ToolSchema
import im.hikaru.harness.llm.createAssistantMessage
import im.hikaru.harness.llm.createUserMessage
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class KoogRequestMapperTest {

    @Test
    fun shouldMapTextConversationAndCallParameters() {
        val userMessage =
            createUserMessage(
                content = listOf(
                    TextBlock("hello"),
                    TextBlock("world"),
                )
            )
        val assistantMessage =
            createAssistantMessage(
                content = listOf(TextBlock("previous answer")),
                provider = "test",
                model = "test-model",
            )
        val model =
            LLModel(
                provider = LLMProvider("test", "Test Provider"),
                id = "test-model",
            )
        val options =
            GenerateOptions(
                provider = "test",
                model = "test-model",
                messages = listOf(userMessage, assistantMessage),
                system = "You are a concise assistant.",
                temperature = 0.25,
                maxTokens = 1_024,
                sessionId = LlmSessionId("session-42"),
            )

        val request =
            KoogRequestMapper().map(
                options = options,
                model = model,
            )

        assertSame(model, request.model)
        assertTrue(request.tools.isEmpty())
        assertEquals("session-42", request.prompt.id)
        assertEquals(0.25, request.prompt.params.temperature)
        assertEquals(1_024, request.prompt.params.maxTokens)

        val messages = request.prompt.messages
        assertEquals(3, messages.size)

        val system = assertIs<KoogMessage.System>(messages[0])
        assertEquals(
            listOf("You are a concise assistant."),
            system.textParts(),
        )

        val user = assertIs<KoogMessage.User>(messages[1])
        assertEquals(userMessage.id.value, user.id)
        assertEquals(listOf("hello", "world"), user.textParts())

        val assistant = assertIs<KoogMessage.Assistant>(messages[2])
        assertEquals(assistantMessage.id.value, assistant.id)
        assertEquals(listOf("previous answer"), assistant.textParts())
    }

    @Test
    fun unsupportedRequestFeaturesShouldFailWithStableCodes() {
        val model = testKoogModel()
        val base =
            GenerateOptions(
                provider = "test",
                model = model.id,
                messages = listOf(createUserMessage(listOf(TextBlock("hello")))),
            )
        val mapper = KoogRequestMapper()

        listOf(
            base.copy(stop = listOf("END")),
            base.copy(reasoningEffort = ReasoningEffortId("high")),
        ).forEach { options ->
            val error =
                assertFailsWith<LlmException> {
                    mapper.map(options, model)
                }
            assertEquals(KoogLlmErrorCode.UNSUPPORTED_OPTION, error.code)
        }

        val unsupportedContent =
            assertFailsWith<LlmException> {
                mapper.map(
                    options =
                        base.copy(
                            messages =
                                listOf(
                                    createAssistantMessage(
                                        content = listOf(ReasoningBlock("thinking")),
                                        provider = "test",
                                        model = model.id,
                                    )
                                )
                        ),
                    model = model,
                )
            }
        assertEquals(KoogLlmErrorCode.UNSUPPORTED_CONTENT, unsupportedContent.code)

        val unsupportedTool =
            assertFailsWith<LlmException> {
                mapper.map(
                    options =
                        base.copy(
                            tools =
                                listOf(
                                    ToolSchema(
                                        name = "lookup",
                                        description = "Look up a value",
                                        parameters = JsonObject(emptyMap()),
                                    )
                                )
                        ),
                    model = model,
                )
            }
        assertEquals(
            KoogLlmErrorCode.INVALID_TOOL_SCHEMA,
            unsupportedTool.code,
        )
    }

    @Test
    fun genericKoogLimitsShouldFailWithStableOptionCode() {
        val model = testKoogModel()
        val base =
            GenerateOptions(
                provider = "test",
                model = model.id,
                messages = listOf(createUserMessage(listOf(TextBlock("hello")))),
            )

        listOf(
            base.copy(temperature = -0.1),
            base.copy(temperature = 2.1),
            base.copy(maxTokens = Int.MAX_VALUE.toLong() + 1L),
        ).forEach { options ->
            val error =
                assertFailsWith<LlmException> {
                    KoogRequestMapper().map(options, model)
                }
            assertEquals(KoogLlmErrorCode.UNSUPPORTED_OPTION, error.code)
        }
    }

    @Test
    fun customOptionMapperShouldReceiveResolvedModelAndOwnParams() {
        val model = testKoogModel(id = "provider-model")
        var mappedModel: LLModel? = null
        val mapper =
            KoogRequestMapper(
                optionMapper =
                    KoogOptionMapper { options, resolvedModel ->
                        mappedModel = resolvedModel
                        LLMParams(
                            temperature = options.temperature,
                            user = "mapped-by-${resolvedModel.provider.id}",
                        )
                    }
            )

        val request =
            mapper.map(
                options =
                    GenerateOptions(
                        provider = "test",
                        model = model.id,
                        messages = listOf(createUserMessage(listOf(TextBlock("hello")))),
                        temperature = 0.4,
                    ),
                model = model,
            )

        assertSame(model, mappedModel)
        assertEquals(0.4, request.prompt.params.temperature)
        assertEquals("mapped-by-test", request.prompt.params.user)
    }

    private fun KoogMessage.textParts(): List<String> =
        parts.filterIsInstance<MessagePart.Text>().map(MessagePart.Text::text)
}
