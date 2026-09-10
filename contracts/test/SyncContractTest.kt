package im.hikaru.contracts

import im.hikaru.contracts.app.member.MemberAddressSyncContract
import im.hikaru.contracts.sync.SyncCommand
import im.hikaru.contracts.sync.SyncCommandBatchRequest
import im.hikaru.contracts.sync.SyncCommandBatchResponse
import im.hikaru.contracts.sync.SyncCommandOutcome
import im.hikaru.contracts.sync.SyncCommandResult
import im.hikaru.contracts.sync.SyncChangeNotification
import im.hikaru.contracts.sync.SyncChangesResponse
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyncContractTest {
    @Test
    fun commandPayloadRemainsAJsonObjectOnTheWire() {
        val request = SyncCommandBatchRequest(
            commands = listOf(
                SyncCommand(
                    commandId = "command-1",
                    aggregateType = MemberAddressSyncContract.RESOURCE,
                    aggregateId = "1024",
                    operation = MemberAddressSyncContract.UPDATE,
                    baseVersion = 7,
                    payload = buildJsonObject {
                        put("id", 1024)
                        put("name", "Alice")
                    },
                ),
            ),
        )

        val encoded = Json.encodeToString(request)
        val decoded = Json.decodeFromString<SyncCommandBatchRequest>(encoded)

        assertEquals(request, decoded)
        assertTrue("\"payload\":{\"id\":1024" in encoded)
    }

    @Test
    fun replayIsIndependentFromThePersistedOutcome() {
        val response = SyncCommandBatchResponse(
            listOf(
                SyncCommandResult(
                    commandId = "command-1",
                    outcome = SyncCommandOutcome.CONFLICT,
                    replayed = true,
                    aggregateId = "1024",
                    serverVersion = 8,
                    serverPayload = buildJsonObject {
                        put("id", 1024)
                        put("version", 8)
                    },
                    errorCode = "VERSION_CONFLICT",
                ),
            ),
        )

        assertEquals(response, Json.decodeFromString(Json.encodeToString(response)))
        val legacy = Json.decodeFromString<SyncCommandResult>(
            """{"commandId":"legacy","outcome":"CONFLICT","replayed":false}""",
        )
        assertEquals(null, legacy.serverPayload)
    }

    @Test
    fun changesResponseKeepsResetFieldsBackwardCompatible() {
        val legacy = Json.decodeFromString<SyncChangesResponse>(
            """{"items":[],"nextCursor":7,"hasMore":false}""",
        )
        val reset = SyncChangesResponse(
            items = emptyList(),
            nextCursor = 12,
            hasMore = false,
            resetRequired = true,
            resetCursor = 12,
        )

        assertFalse(legacy.resetRequired)
        assertEquals(null, legacy.resetCursor)
        assertEquals(reset, Json.decodeFromString(Json.encodeToString(reset)))
    }

    @Test
    fun changeNotificationContainsOnlyAPullHint() {
        val notification = SyncChangeNotification(
            resource = MemberAddressSyncContract.RESOURCE,
            cursor = 42,
        )

        val encoded = Json.encodeToString(notification)

        assertEquals(notification, Json.decodeFromString(encoded))
        assertEquals("""{"resource":"member-address","cursor":42}""", encoded)
    }
}
