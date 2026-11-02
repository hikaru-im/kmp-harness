package im.hikaru.harness.client.conversation

import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.contracts.harness.relay.EventId
import im.hikaru.contracts.harness.relay.RelayEvent
import im.hikaru.contracts.harness.relay.StreamId
import im.hikaru.harness.client.connection.ApprovalChoice
import im.hikaru.harness.client.connection.ApprovalDecision
import im.hikaru.harness.client.connection.ApprovalRequest
import im.hikaru.harness.client.connection.SessionId
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.*

class ApprovalProtocolTest {
    @Test
    fun theRequestEventCarriesTheFullCommandReasonAndOfferedChoices() {
        val request = assertNotNull(LiveApprovalEventCodec.requested(relay("approval/requested", buildJsonObject {
            put("request", buildJsonObject {
                put("id", "approval-1")
                put("sessionId", SESSION.value)
                put("toolName", "shell")
                put("command", "git commit -m \"$(rm -rf /tmp/example)\"")
                put("reason", "命令会写入仓库")
                put("choices", buildJsonArray {
                    add("allow-once")
                    add("deny")
                    add("cancel")
                })
            })
        })))
        assertEquals("approval-1", request.id)
        assertEquals(SESSION, request.sessionId)
        assertEquals("shell", request.toolName)
        assertEquals("git commit -m \"$(rm -rf /tmp/example)\"", request.command)
        assertEquals("命令会写入仓库", request.reason)
        assertEquals(listOf(ApprovalChoice.ALLOW_ONCE, ApprovalChoice.DENY, ApprovalChoice.CANCEL), request.choices)
    }

    @Test
    fun theResolutionEventIdentifiesTheRequestItSettles() {
        val resolved = assertNotNull(LiveApprovalEventCodec.resolved(relay("approval/resolved", buildJsonObject {
            put("requestId", "approval-1")
            put("choice", "deny")
        })))
        assertEquals("approval-1", resolved)
    }

    @Test
    fun malformedPayloadsAreIgnoredInsteadOfBreakingTheStream() {
        assertNull(LiveApprovalEventCodec.requested(relay("approval/requested", buildJsonObject { })))
        assertNull(LiveApprovalEventCodec.requested(relay("approval/requested", buildJsonObject {
            put("request", buildJsonObject {
                put("id", "approval-1")
                // sessionId, toolName and choices are missing.
            })
        })))
        assertNull(LiveApprovalEventCodec.resolved(relay("approval/resolved", buildJsonObject { })))
    }

    @Test
    fun aRequestMustOfferAtLeastOneDistinctChoice() {
        val request = ApprovalRequest("approval-1", SESSION, "shell", "ls")
        assertEquals(ApprovalChoice.entries.toList(), request.choices)
        assertFailsWith<IllegalArgumentException> {
            ApprovalRequest("approval-1", SESSION, "shell", "ls", choices = emptyList())
        }
        assertFailsWith<IllegalArgumentException> {
            ApprovalRequest("approval-1", SESSION, "shell", "ls",
                choices = listOf(ApprovalChoice.DENY, ApprovalChoice.DENY))
        }
        assertFailsWith<IllegalArgumentException> {
            ApprovalRequest("", SESSION, "shell", "ls")
        }
        assertFailsWith<IllegalArgumentException> {
            ApprovalDecision("", ApprovalChoice.DENY)
        }
    }

    @Test
    fun theHostMayOmitTheCommandAndReasonForNonShellTools() {
        val request = ApprovalRequest("approval-1", SESSION, "filesystem", choices = listOf(ApprovalChoice.ALLOW_ONCE))
        assertEquals("", request.command)
        assertNull(request.reason)
    }

    private fun relay(type: String, payload: kotlinx.serialization.json.JsonElement) = RelayEvent(
        eventId = EventId("event-1"),
        hostId = HostId("host-a"),
        streamId = StreamId(SESSION.value),
        sequence = 1,
        event = type,
        payload = payload,
    )

    private companion object {
        val SESSION = SessionId("session-a")
    }
}
