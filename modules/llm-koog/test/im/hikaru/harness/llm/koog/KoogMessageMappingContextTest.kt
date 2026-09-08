package im.hikaru.harness.llm.koog

import im.hikaru.harness.llm.CallId
import im.hikaru.harness.llm.LlmException
import im.hikaru.harness.llm.ToolCallBlock
import im.hikaru.harness.llm.createAssistantMessage
import im.hikaru.harness.llm.createUserMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KoogMessageMappingContextTest {

    @Test
    fun previousAssistantToolCallShouldProvideResultName() {
        val context = KoogMessageMappingContext()
        val callId = CallId("call-1")

        context.observe(
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
                model = "test-model",
            )
        )

        assertEquals("lookup", context.requireToolName(callId))
    }

    @Test
    fun missingDuplicateOrMalformedCallsShouldUseStableHistoryError() {
        val callId = CallId("call-1")
        val call =
            ToolCallBlock(
                id = callId,
                name = "lookup",
                arguments = "{}",
            )
        val assistant =
            createAssistantMessage(
                content = listOf(call),
                provider = "test",
                model = "test-model",
            )
        val context = KoogMessageMappingContext()

        assertInvalidHistory {
            context.requireToolName(CallId("missing"))
        }

        context.observe(assistant)
        assertInvalidHistory {
            context.observe(assistant)
        }

        assertInvalidHistory {
            KoogMessageMappingContext().observe(
                createUserMessage(listOf(call))
            )
        }

        assertInvalidHistory {
            KoogMessageMappingContext().observe(
                createAssistantMessage(
                    content = listOf(call.copy(name = " ")),
                    provider = "test",
                    model = "test-model",
                )
            )
        }
    }

    private fun assertInvalidHistory(block: () -> Unit) {
        val error = assertFailsWith<LlmException>(block = block)
        assertEquals(KoogLlmErrorCode.INVALID_TOOL_HISTORY, error.code)
    }
}
