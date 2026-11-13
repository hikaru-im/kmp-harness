package im.hikaru.harness.client.find

import im.hikaru.harness.client.connection.Message
import im.hikaru.harness.client.connection.MessageRole
import im.hikaru.harness.client.connection.TextContent
import im.hikaru.harness.client.connection.UserMessageSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ConversationFindTest {
    @Test
    fun matchesAllRenderedTextContentCaseInsensitivelyAndPreservesOrder() {
        val messages = listOf(
            Message("one", MessageRole.USER, listOf(TextContent("Alpha beta")), UserMessageSource),
            Message("two", MessageRole.ASSISTANT, listOf(TextContent("alpha"), TextContent("ALPHA")), UserMessageSource),
        )

        val result = findConversationMatches(messages, "alpha")

        assertEquals(3, result.matches.size)
        assertEquals(listOf("one", "two", "two"), result.matches.map { it.messageId })
        assertEquals(listOf(0, 0, 1), result.matches.map { it.contentIndex })
        assertEquals(listOf(0, 0, 0), result.matches.map { it.startInText })
        assertTrue(!result.truncated)
    }

    @Test
    fun blankQueryHasNoMatchesAndLimitReportsCappedResults() {
        val messages = listOf(Message("one", MessageRole.USER, listOf(TextContent("aaaa")), UserMessageSource))

        assertTrue(findConversationMatches(messages, "   ").matches.isEmpty())
        val capped = findConversationMatches(messages, "a", limit = 2)
        assertEquals(2, capped.matches.size)
        assertTrue(capped.truncated)
    }
}
