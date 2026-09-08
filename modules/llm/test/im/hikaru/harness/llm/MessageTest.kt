package im.hikaru.harness.llm

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

class MessageTest {

    private val json =
        Json {
            classDiscriminator = "type"
            encodeDefaults = true
        }

    @Test
    fun factoriesShouldAssignStableIdentityAndCorrectSource() {
        val user =
            createUserMessage(
                content = listOf(TextBlock("hello")),
            )

        val assistant =
            createAssistantMessage(
                content = listOf(TextBlock("answer")),
                provider = "test",
                model = "model",
            )

        assertNotEquals(user.id, assistant.id)
        assertEquals(MessageRole.USER, user.role)
        assertEquals(UserMessageSource, user.source)
        assertEquals(MessageRole.ASSISTANT, assistant.role)
        assertIs<ModelMessageSource>(assistant.source)
    }

    @Test
    fun toolResultShouldKeepCallIdentityCoupled() {
        val callId = CallId("call-1")
        val message =
            createToolResultMessage(
                callId = callId,
                content = listOf(TextBlock("result")),
                isError = false,
            )

        val source = assertIs<ToolMessageSource>(message.source)
        val block = assertIs<ToolResultBlock>(message.content.single())

        assertEquals(callId, source.callId)
        assertEquals(callId, block.toolCallId)
    }

    @Test
    fun invalidRoleAndSourceCombinationShouldBeRejected() {
        assertFailsWith<IllegalArgumentException> {
            Message(
                id = MessageId("message"),
                role = MessageRole.USER,
                content = listOf(TextBlock("invalid")),
                source = ModelMessageSource(
                    provider = "test",
                    model = "model",
                ),
            )
        }

        assertFailsWith<IllegalArgumentException> {
            Message(
                id = MessageId("tool-result"),
                role = MessageRole.USER,
                content = listOf(
                    ToolResultBlock(
                        toolCallId = CallId("other"),
                        content = emptyList(),
                    )
                ),
                source = ToolMessageSource(CallId("expected")),
            )
        }
    }

    @Test
    fun messageShouldRoundTripThroughKotlinSerialization() {
        val original =
            createAssistantMessage(
                content = listOf(
                    ReasoningBlock("thinking"),
                    TextBlock("answer"),
                    ToolCallBlock(
                        id = CallId("call-1"),
                        name = "search",
                        arguments = "{\"q\":\"kmp\"}",
                    ),
                ),
                provider = "test",
                model = "model",
            )

        val encoded = json.encodeToString(original)
        val restored = json.decodeFromString<Message>(encoded)

        assertEquals(original, restored)
    }

    @Test
    fun copyMessageShouldDetachNestedContentLists() {
        val nested = mutableListOf<ContentBlock>(TextBlock("before"))
        val original =
            createToolResultMessage(
                callId = CallId("call"),
                content = nested,
                isError = false,
            )

        val copied = copyMessage(original)
        nested += TextBlock("after")

        val result = assertIs<ToolResultBlock>(copied.content.single())
        assertEquals(listOf(TextBlock("before")), result.content)
    }

    @Test
    fun replacingAssistantContentShouldClearReplayStateButPlainCopyShouldPreserveIt() {
        val replayState = buildJsonObject {
            put("kind", "provider-private")
            put("version", 1)
        }
        val original =
            createAssistantMessage(
                content = listOf(TextBlock("old")),
                provider = "provider",
                model = "model",
                replayState = replayState,
            )

        assertEquals(
            replayState,
            assertIs<ModelMessageSource>(original.copy(content = listOf(TextBlock("copy"))).source)
                .replayState,
        )
        assertEquals(
            replayState,
            assertIs<ModelMessageSource>(copyMessage(original).source).replayState,
        )
        assertEquals(
            null,
            assertIs<ModelMessageSource>(original.replaceContent(listOf(TextBlock("new"))).source)
                .replayState,
        )
        assertEquals(
            null,
            assertIs<ModelMessageSource>(original.withoutReplayState().source).replayState,
        )
    }
}
