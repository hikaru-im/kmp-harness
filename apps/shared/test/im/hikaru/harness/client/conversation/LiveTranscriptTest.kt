package im.hikaru.harness.client.conversation

import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.contracts.harness.relay.EventId
import im.hikaru.contracts.harness.relay.RelayEvent
import im.hikaru.contracts.harness.relay.StreamId
import im.hikaru.harness.client.connection.ReasoningContent
import im.hikaru.harness.client.connection.TextContent
import im.hikaru.harness.client.connection.ToolCallContent
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LiveTranscriptTest {
    @Test
    fun chunksFoldIntoTextReasoningAndVisibleToolCalls() {
        val fold = LiveAssistantFold()
        fold.apply(1, 1, LiveChunk.Start(0, "reasoning"))
        fold.apply(1, 1, LiveChunk.Reasoning(0, "think"))
        fold.apply(1, 1, LiveChunk.End(0, ReasoningContent("think")))
        fold.apply(1, 1, LiveChunk.Start(1, "text"))
        fold.apply(1, 1, LiveChunk.Text(1, "Hel"))
        fold.apply(1, 1, LiveChunk.Text(1, "lo"))
        fold.apply(1, 1, LiveChunk.Start(2, "tool-call"))
        fold.apply(1, 1, LiveChunk.ToolCall(2, "", null, ""))
        // A tool call stays hidden until its identity (id and name) is known.
        assertEquals(2, assertNotNull(fold.message()).content.size)
        fold.apply(1, 1, LiveChunk.ToolCall(2, "call-1", "read_file", "{\"path\":"))

        val message = assertNotNull(fold.message())
        assertEquals("live:1:1", message.id)
        val content = message.content
        assertEquals(3, content.size)
        assertEquals("think", assertIs<ReasoningContent>(content[0]).text)
        assertEquals("Hello", assertIs<TextContent>(content[1]).text)
        assertEquals("{\"path\":", assertIs<ToolCallContent>(content[2]).arguments)

        fold.apply(1, 1, LiveChunk.ToolCall(2, "call-1", "read_file", "\"a.txt\"}"))
        fold.apply(1, 1, LiveChunk.End(2, ToolCallContent("call-1", "read_file", "{\"path\":\"a.txt\"}")))
        val completed = assertNotNull(fold.message()).content
        assertEquals(3, completed.size)
        assertEquals("{\"path\":\"a.txt\"}", assertIs<ToolCallContent>(completed[2]).arguments)
    }

    @Test
    fun aNewStepStartsAnEmptyTail() {
        val fold = LiveAssistantFold()
        fold.apply(1, 1, LiveChunk.Text(0, "first"))
        assertNotNull(fold.message())
        fold.apply(1, 2, LiveChunk.Text(0, "second"))
        val message = assertNotNull(fold.message())
        assertEquals("live:1:2", message.id)
        assertEquals("second", assertIs<TextContent>(message.content.single()).text)
    }

    @Test
    fun completedBlocksWinOverAccumulatedDeltas() {
        val fold = LiveAssistantFold()
        fold.apply(1, 1, LiveChunk.Text(0, "partial"))
        fold.apply(1, 1, LiveChunk.End(0, TextContent("canonical")))
        fold.apply(1, 1, LiveChunk.Text(0, "-late"))
        assertEquals("canonical", assertIs<TextContent>(assertNotNull(fold.message()).content.single()).text)
    }

    @Test
    fun theWireCodecReadsHostChunkAndSettlementPayloads() {
        val chunk = LiveSessionEventCodec.decode(
            relay("assistant/chunk", buildJsonObject {
                put("turn", 3)
                put("step", 4)
                put("chunk", buildJsonObject {
                    put("type", "text-delta")
                    put("index", 0)
                    put("text", "hi")
                })
            }),
        )
        val update = assertIs<LiveUpdate.Chunk>(chunk)
        assertEquals(3L, update.turn)
        assertEquals(4L, update.step)
        assertEquals("hi", assertIs<LiveChunk.Text>(update.chunk).text)

        val settled = LiveSessionEventCodec.decode(
            relay("assistant/message", buildJsonObject {
                put("turn", 3)
                put("step", 4)
                put("message", buildJsonObject {
                    put("id", "m-1")
                    put("role", "assistant")
                    put("content", buildJsonArray {
                        add(buildJsonObject { put("type", "text"); put("text", "done") })
                    })
                    put("source", buildJsonObject {
                        put("type", "model")
                        put("provider", "p")
                        put("model", "m")
                    })
                })
            }),
        )
        val message = assertIs<LiveUpdate.AssistantSettled>(settled).message
        assertEquals("m-1", message.id)
        assertEquals("done", assertIs<TextContent>(message.content.single()).text)
    }

    @Test
    fun malformedPayloadsAreIgnoredInsteadOfThrowing() {
        assertNull(LiveSessionEventCodec.decode(relay("assistant/chunk", JsonPrimitive("noise"))))
        assertNull(LiveSessionEventCodec.decode(relay("assistant/message", JsonObject(emptyMap()))))
        assertNull(LiveSessionEventCodec.decode(relay("turn/end", JsonPrimitive(1))))
        assertTrue(LiveAssistantFold.isLiveId("live:1:1"))
        assertNull(LiveAssistantFold().message())
    }

    private fun relay(type: String, payload: JsonElement) = RelayEvent(
        eventId = EventId("event-$type"),
        hostId = HostId("host"),
        streamId = StreamId("session"),
        sequence = 1,
        event = type,
        payload = payload,
    )
}
