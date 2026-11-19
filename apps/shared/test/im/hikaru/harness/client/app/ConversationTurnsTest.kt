package im.hikaru.harness.client.app

import im.hikaru.harness.client.connection.Message
import im.hikaru.harness.client.connection.MessageRole
import im.hikaru.harness.client.connection.ModelMessageSource
import im.hikaru.harness.client.connection.TextContent
import im.hikaru.harness.client.connection.ToolCallContent
import im.hikaru.harness.client.connection.UserMessageSource
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * CONV-02 step one: turns are the ownership unit for time separators, activity headers, row actions
 * and the transcript's lazy items. These tests stay on the data model so grouping can change
 * independently of rendering.
 */
class ConversationTurnsTest {
    private val model = ModelMessageSource("test-provider", "test-model")

    @Test
    fun humanMessagesOpenTurnsAndOwnEverythingUntilTheNextHumanMessage() {
        val messages = listOf(
            human("user-1", 1_000),
            assistant("assistant-1", 2_000),
            assistant("assistant-2", 3_000),
            human("user-2", 4_000),
            assistant("assistant-3", 5_000),
        )
        val turns = conversationTurns(messages)
        assertEquals(listOf("user-1", "user-2"), turns.map { it.id })
        assertEquals(listOf("user-1", "user-2"), turns.map { it.prompt?.id })
        assertEquals(listOf(listOf("user-1", "assistant-1", "assistant-2"), listOf("user-2", "assistant-3")),
            turns.map { turn -> turn.messages.map { it.id } })
        // CONV-01/09 reserve the field; M1 must not invent a diff summary.
        assertTrue(turns.all { it.diffSummary == null })
        assertEquals(TurnDiffSummary(1, 2, 3), turns.first().copy(diffSummary = TurnDiffSummary(1, 2, 3)).diffSummary)
        assertEquals(1_000, turns.first().anchorTimestampMillis)
    }

    @Test
    fun assistantLeadingHistoryStillFormsOneTurnWithoutAPrompt() {
        val turns = conversationTurns(listOf(
            assistant("assistant-1", 1_000),
            assistant("assistant-2", 1_500),
            human("user-1", 2_000),
        ))
        assertEquals(listOf("assistant-1", "user-1"), turns.map { it.id })
        assertNull(turns.first().prompt)
        assertEquals(listOf("assistant-1", "assistant-2"), turns.first().messages.map { it.id })
    }

    @Test
    fun emptyHistoryHasNoTurns() {
        assertTrue(conversationTurns(emptyList()).isEmpty())
    }

    @Test
    fun workedForUsesTheTurnClockAndTheFirstActivityMessage() {
        val turns = conversationTurns(listOf(
            human("user-1", 1_000),
            assistantText("assistant-1", 2_000),
            Message("assistant-2", MessageRole.ASSISTANT,
                listOf(ToolCallContent("call-1", "test_tool", "{}")), model, 4_000),
            assistantText("assistant-3", 9_000),
            human("user-2", 20_000),
            assistantText("assistant-4", 21_000),
        ))
        // The header belongs to the first message holding reasoning/tool activity, not the prompt
        // and not the last text, and it measures prompt -> last event inside that same turn.
        assertEquals(mapOf("assistant-2" to 8_000L), turnWorkedFor(turns))
    }

    @Test
    fun lastTextMessageActionsStayInsideTheirOwnTurn() {
        val turns = conversationTurns(listOf(
            human("user-1", 1_000),
            assistantText("assistant-1", 2_000),
            Message("assistant-2", MessageRole.ASSISTANT,
                listOf(TextContent("last text"), ToolCallContent("call-1", "test_tool", "{}")), model, 3_000),
            human("user-2", 4_000),
            assistantText("assistant-3", 5_000),
        ))
        assertEquals(setOf("assistant-2", "assistant-3"), turnLastTextMessageIds(turns))
    }

    @Test
    fun timelineRowsCarryTurnOwnership() {
        val rows = conversationTimeline(listOf(
            human("user-1", 1_000),
            assistant("assistant-1", 2_000),
            human("user-2", 3_000),
        ), TimeZone.UTC)
        assertEquals(listOf("marker:user-1", "user-1", "assistant-1", "marker:user-2", "user-2"),
            rows.map { row ->
                when (row) {
                    is ConversationTimelineRow.TimeMarkerRow -> "marker:${row.anchorMessageId}"
                    is ConversationTimelineRow.MessageRow -> row.message.id
                }
            })
        assertEquals(listOf("user-1", "user-1", "user-1", "user-2", "user-2"),
            rows.map { row -> row.turnId() })
    }

    private fun ConversationTimelineRow.turnId(): String = when (this) {
        is ConversationTimelineRow.TimeMarkerRow -> anchorMessageId
        is ConversationTimelineRow.MessageRow -> turnId
    }

    private fun human(id: String, time: Long) =
        Message(id, MessageRole.USER, listOf(TextContent("prompt $id")), UserMessageSource, time)

    private fun assistant(id: String, time: Long) = assistantText(id, time)

    private fun assistantText(id: String, time: Long) =
        Message(id, MessageRole.ASSISTANT, listOf(TextContent("text $id")), model, time)
}
