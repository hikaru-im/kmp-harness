package im.hikaru.harness.llm.koog

import ai.koog.prompt.message.Message as KoogMessage
import ai.koog.prompt.message.MessagePart
import im.hikaru.harness.llm.CallId
import im.hikaru.harness.llm.GenerateOptions
import im.hikaru.harness.llm.TextBlock
import im.hikaru.harness.llm.ToolCallBlock
import im.hikaru.harness.llm.createAssistantMessage
import im.hikaru.harness.llm.createToolResultMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class KoogToolMessageRequestIntegrationContractTest {

    @Test
    fun requestHistoryShouldRestoreToolResultNameFromThePreviousCall() {
        val model = testKoogModel()
        val callId = CallId("call-1")
        val request =
            KoogRequestMapper().map(
                options =
                    GenerateOptions(
                        provider = "test",
                        model = model.id,
                        messages =
                            listOf(
                                createAssistantMessage(
                                    content =
                                        listOf(
                                            ToolCallBlock(
                                                id = callId,
                                                name = "lookup",
                                                arguments = "{}",
                                            )
                                        ),
                                    provider = "test",
                                    model = model.id,
                                ),
                                createToolResultMessage(
                                    callId = callId,
                                    content = listOf(TextBlock("result")),
                                    isError = false,
                                ),
                            ),
                    ),
                model = model,
            )

        val callMessage = assertIs<KoogMessage.Assistant>(request.prompt.messages[0])
        val call = assertIs<MessagePart.Tool.Call>(callMessage.parts.single())
        assertEquals("call-1", call.id)
        assertEquals("lookup", call.tool)

        val resultMessage = assertIs<KoogMessage.User>(request.prompt.messages[1])
        val result = assertIs<MessagePart.Tool.Result>(resultMessage.parts.single())
        assertEquals("call-1", result.id)
        assertEquals("lookup", result.tool)
        assertEquals("result", result.output)
    }
}
