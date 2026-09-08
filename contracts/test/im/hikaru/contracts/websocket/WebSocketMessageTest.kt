package im.hikaru.contracts.websocket

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WebSocketMessageTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun shouldMatchRuoyiTypeAndContentShape() {
        val message =
            WebSocketMessage(
                type = "sync-change",
                content = "{\"resource\":\"member-profile\"}",
            )

        val encoded = json.encodeToString(message)

        assertEquals(message, json.decodeFromString(encoded))
        assertEquals(
            "{\"type\":\"sync-change\",\"content\":\"{\\\"resource\\\":\\\"member-profile\\\"}\"}",
            encoded,
        )
    }

    @Test
    fun shouldRejectBlankMessageType() {
        assertFailsWith<IllegalArgumentException> {
            WebSocketMessage(type = " ", content = "{}")
        }
    }
}
