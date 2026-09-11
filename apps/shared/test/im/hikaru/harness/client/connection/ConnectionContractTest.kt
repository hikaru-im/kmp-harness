package im.hikaru.harness.client.connection

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals

class ConnectionContractTest {
    @Test
    fun messageJsonRoundTripPreservesIdentityRoleSourceReplayStateAndNestedToolResult() {
        val message =
            Message(
                id = "assistant-1",
                role = MessageRole.ASSISTANT,
                content =
                    listOf(
                        ReasoningContent("thinking"),
                        ToolCallContent("call-1", "lookup", "{\"q\":\"x\"}"),
                        ToolResultContent(
                            "call-1",
                            listOf(TextContent("result"), ReasoningContent("trace")),
                            isError = true,
                        ),
                    ),
                source = ModelMessageSource("openai", "gpt-test", JsonPrimitive("replay-token")),
            )

        val json = Json { encodeDefaults = true }
        val encoded = json.encodeToString(message)
        val decoded = json.decodeFromString<Message>(encoded)

        assertEquals(message, decoded)
        assertEquals("assistant", json.parseToJsonElement(encoded).jsonObject["role"]?.toString()?.trim('"'))
        assertEquals("model", json.parseToJsonElement(encoded).jsonObject["source"]?.jsonObject?.get("type")?.toString()?.trim('"'))
    }
}
