package im.hikaru.harness.client.connection

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull

class ReasoningOptionsTest {
    @Test
    fun legacyMessagesAndExplicitProviderDefaultStayDistinctOnTheWire() {
        val legacy = Json.decodeFromString<AgentOptions>("""{"provider":"test","model":"m","reasoningEffort":"high"}""")
        assertNull(legacy.reasoning)
        assertEquals(ReasoningEffortId("high"), legacy.reasoningEffort)

        val request = AgentOptions(provider = "test", model = "m", reasoning = ReasoningOptions())
        val restored = Json.decodeFromString<AgentOptions>(Json.encodeToString(request))
        assertNotNull(restored.reasoning)
        assertEquals("provider-default", restored.reasoning.mode)
        assertNull(restored.reasoning.effort)
    }
}
