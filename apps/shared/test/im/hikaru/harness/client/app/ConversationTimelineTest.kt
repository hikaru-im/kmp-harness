package im.hikaru.harness.client.app

import im.hikaru.harness.client.connection.Message
import im.hikaru.harness.client.connection.MessageRole
import im.hikaru.harness.client.connection.ModelMessageSource
import im.hikaru.harness.client.connection.TextContent
import im.hikaru.harness.client.connection.UserMessageSource
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * The conversation separator contract measured from Codex Desktop through CDP on 2026-10-23:
 * `<div role="separator" aria-label="10月7日周三 1:02"><time datetime=…>` per turn, with the
 * date span at `font-medium` and the clock at normal weight.
 */
class ConversationTimelineTest {
    private val zone = TimeZone.UTC
    private val model = ModelMessageSource("test-provider", "test-model")

    @Test
    fun markerLabelsUseTheCodexChineseDateWeekdayAndUnpaddedHour() {
        val morning = at("2026-10-07T01:02:40")
        assertEquals("10月7日周三", timeMarkerDateLabel(morning, zone))
        assertEquals("1:02", timeMarkerClockLabel(morning, zone))
        assertEquals("10月7日周三 1:02", timeMarkerLabel(morning, zone))
    }

    @Test
    fun clockKeepsTwoMinuteDigitsAcrossTheDateBoundary() {
        // The reference window renders `10月7日周三 1:02` for 2026-10-06T17:02:40Z, i.e. the
        // marker follows the *local* clock, not the event's UTC date.
        val boundary = at("2026-10-06T17:02:40")
        assertEquals("1:02", timeMarkerClockLabel(boundary, TimeZone.of("Asia/Shanghai")))
        assertEquals("10月7日周三", timeMarkerDateLabel(boundary, TimeZone.of("Asia/Shanghai")))
        // The same instant is still the previous afternoon in Los Angeles (PDT, UTC-7 in October).
        assertEquals("10月6日周二", timeMarkerDateLabel(boundary, TimeZone.of("America/Los_Angeles")))
        assertEquals("10:02", timeMarkerClockLabel(boundary, TimeZone.of("America/Los_Angeles")))
    }

    /** The live reference prints separators at 1:02, 1:45 and 2:28 in one session: per turn, not per gap. */
    @Test
    fun everyHumanTurnCarriesItsOwnSeparator() {
        val rows = timeline(
            listOf(
                human("user-1", at("2026-10-07T01:02:40")),
                assistant("assistant-1", at("2026-10-07T01:02:41")),
                human("user-2", at("2026-10-07T01:45:33")),
                assistant("assistant-2", at("2026-10-07T01:45:40")),
            ),
        )
        assertEquals(
            listOf("marker:user-1", "user-1", "assistant-1", "marker:user-2", "user-2", "assistant-2"),
            rows.map { row ->
                when (row) {
                    is ConversationTimelineRow.MessageRow -> row.message.id
                    is ConversationTimelineRow.TimeMarkerRow -> "marker:${row.anchorMessageId}"
                }
            },
        )
        val markers = rows.filterIsInstance<ConversationTimelineRow.TimeMarkerRow>()
        assertEquals(listOf("user-1", "user-2"), markers.map { it.anchorMessageId })
        assertEquals(listOf(at("2026-10-07T01:02:40"), at("2026-10-07T01:45:33")), markers.map { it.timestampMillis })
    }

    @Test
    fun assistantLeadingHistoryAnchorsItsSeparatorOnTheFirstDurableTime() {
        val rows = timeline(
            listOf(
                assistant("assistant-1", at("2026-10-07T02:00:00")),
                assistant("assistant-2", null),
                human("user-1", at("2026-10-07T03:00:00")),
            ),
        )
        val markers = rows.filterIsInstance<ConversationTimelineRow.TimeMarkerRow>()
        assertEquals(listOf("assistant-1", "user-1"), markers.map { it.anchorMessageId })
        assertEquals(at("2026-10-07T02:00:00"), markers.first().timestampMillis)
    }

    @Test
    fun aTurnWithNoDurableTimeLeavesThePreviousSeparatorStanding() {
        val rows = timeline(
            listOf(
                human("user-1", at("2026-10-07T01:02:40")),
                assistant("assistant-1", at("2026-10-07T01:02:41")),
                human("user-2", null),
                assistant("assistant-2", null),
            ),
        )
        assertEquals(1, rows.filterIsInstance<ConversationTimelineRow.TimeMarkerRow>().size)
        assertEquals(4, rows.filterIsInstance<ConversationTimelineRow.MessageRow>().size)
    }

    @Test
    fun streamingTurnsWithoutTimestampsNeverInventAMarker() {
        val rows = timeline(
            listOf(
                human("user-1", at("2026-10-07T01:02:40")),
                assistant("live:1:1", null),
            ),
        )
        val markers = rows.filterIsInstance<ConversationTimelineRow.TimeMarkerRow>()
        assertEquals(listOf("user-1"), markers.map { it.anchorMessageId })
        // The tail is still drawn; only the separator is withheld.
        assertEquals("live:1:1", rows.last().let { assertIs<ConversationTimelineRow.MessageRow>(it).message.id })
    }

    @Test
    fun historyWithoutTimestampsRendersMessagesOnly() {
        val rows = timeline(listOf(human("user-1", null), assistant("assistant-1", null)))
        assertEquals(2, rows.size)
        assertNull(rows.firstOrNull { it is ConversationTimelineRow.TimeMarkerRow })
    }

    @Test
    fun aNewLocalDayAlwaysStartsANewSeparator() {
        val rows = timeline(
            listOf(
                human("user-1", at("2026-10-07T23:58:00")),
                assistant("assistant-1", at("2026-10-07T23:58:30")),
                human("user-2", at("2026-10-08T00:01:00")),
            ),
        )
        assertEquals(
            listOf(at("2026-10-07T23:58:00"), at("2026-10-08T00:01:00")),
            rows.filterIsInstance<ConversationTimelineRow.TimeMarkerRow>().map { it.timestampMillis },
        )
        assertEquals("10月8日周四", timeMarkerDateLabel(at("2026-10-08T00:01:00"), zone))
    }

    @Test
    fun outOfOrderHistoryStillPrintsASeparatorForEveryTurn() {
        val rows = timeline(
            listOf(
                human("user-1", at("2026-10-07T05:00:00")),
                human("user-2", at("2026-10-07T04:00:00")),
            ),
        )
        assertEquals(
            listOf(at("2026-10-07T05:00:00"), at("2026-10-07T04:00:00")),
            rows.filterIsInstance<ConversationTimelineRow.TimeMarkerRow>().map { it.timestampMillis },
        )
    }

    private fun at(local: String): Long = LocalDateTime.parse(local).toInstant(zone).toEpochMilliseconds()

    /** The marker cadence under test is zone-dependent, so the fixtures pin UTC rather than the host's. */
    private fun timeline(messages: List<Message>) = conversationTimeline(messages, zone)

    private fun human(id: String, time: Long?) =
        Message(id, MessageRole.USER, listOf(TextContent(id)), UserMessageSource, time)

    private fun assistant(id: String, time: Long?) =
        Message(id, MessageRole.ASSISTANT, listOf(TextContent(id)), model, time)
}
