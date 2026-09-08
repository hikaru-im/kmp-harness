package im.hikaru.ruoyi.module.sync.controller.app

import im.hikaru.contracts.app.member.MemberAddressSyncContract
import im.hikaru.contracts.sync.SyncChangeOperation
import im.hikaru.contracts.sync.SyncChangesResponse
import im.hikaru.contracts.sync.SyncCommand
import im.hikaru.contracts.sync.SyncCommandBatchRequest
import im.hikaru.contracts.sync.SyncCommandBatchResponse
import im.hikaru.contracts.sync.SyncCommandOutcome
import im.hikaru.contracts.sync.SyncPushDeviceRequest
import im.hikaru.contracts.sync.SyncPushDeviceUnregisterRequest
import im.hikaru.contracts.sync.SyncPushPlatform
import im.hikaru.ruoyi.framework.common.util.json.JsonUtils
import im.hikaru.ruoyi.module.sync.controller.app.vo.AppSyncChangeVO
import im.hikaru.ruoyi.module.sync.controller.app.vo.AppSyncChangesRespVO
import im.hikaru.ruoyi.module.sync.controller.app.vo.AppSyncCommandBatchReqVO
import im.hikaru.ruoyi.module.sync.controller.app.vo.AppSyncCommandBatchRespVO
import im.hikaru.ruoyi.module.sync.controller.app.vo.AppSyncCommandOutcomeVO
import im.hikaru.ruoyi.module.sync.controller.app.vo.AppSyncCommandResultVO
import im.hikaru.ruoyi.module.sync.controller.app.vo.AppSyncPushDeviceReqVO
import im.hikaru.ruoyi.module.sync.controller.app.vo.AppSyncPushDeviceUnregisterReqVO
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SyncWireContractTest {
    private val clientJson = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun `KMP push registration requests are readable by the Spring wire models`() {
        val registerRequest = requireNotNull(
            JsonUtils.parseObject(
                clientJson.encodeToString(
                    SyncPushDeviceRequest(
                        token = "device-token",
                        platform = SyncPushPlatform.ANDROID,
                    ),
                ),
                AppSyncPushDeviceReqVO::class.java,
            ),
        )
        val unregisterRequest = requireNotNull(
            JsonUtils.parseObject(
                clientJson.encodeToString(SyncPushDeviceUnregisterRequest(token = "device-token")),
                AppSyncPushDeviceUnregisterReqVO::class.java,
            ),
        )

        assertEquals("device-token", registerRequest.token)
        assertEquals(SyncPushPlatform.ANDROID, registerRequest.platform)
        assertEquals("device-token", unregisterRequest.token)
    }

    @Test
    fun `KMP command request is readable by the Spring wire model`() {
        val clientRequest = SyncCommandBatchRequest(
            commands = listOf(
                SyncCommand(
                    commandId = "wire-command-1",
                    aggregateType = MemberAddressSyncContract.RESOURCE,
                    aggregateId = "1024",
                    operation = MemberAddressSyncContract.UPDATE,
                    payload = buildJsonObject {
                        put("id", 1024)
                        put("name", "Alice")
                    },
                    baseVersion = 7,
                ),
            ),
        )

        val serverRequest = requireNotNull(
            JsonUtils.parseObject(
                clientJson.encodeToString(clientRequest),
                AppSyncCommandBatchReqVO::class.java,
            ),
        )
        val command = serverRequest.commands.single()

        assertEquals("wire-command-1", command.commandId)
        assertEquals(MemberAddressSyncContract.RESOURCE, command.aggregateType)
        assertEquals(MemberAddressSyncContract.UPDATE, command.operation)
        assertEquals(1024, command.payload?.get("id")?.intValue())
        assertEquals("Alice", command.payload?.get("name")?.stringValue())
        assertEquals(7L, command.baseVersion)
    }

    @Test
    fun `Spring command response is readable by the KMP wire model`() {
        val serverResponse = AppSyncCommandBatchRespVO(
            results = listOf(
                AppSyncCommandResultVO(
                    commandId = "wire-command-1",
                    outcome = AppSyncCommandOutcomeVO.CONFLICT,
                    replayed = true,
                    aggregateId = "1024",
                    serverVersion = 8,
                    serverPayload = JsonUtils.parseTree("{\"id\":1024,\"version\":8}"),
                    errorCode = "VERSION_CONFLICT",
                ),
            ),
        )

        val clientResponse = clientJson.decodeFromString<SyncCommandBatchResponse>(
            JsonUtils.toJsonString(serverResponse),
        )
        val result = clientResponse.results.single()

        assertEquals(SyncCommandOutcome.CONFLICT, result.outcome)
        assertTrue(result.replayed)
        assertEquals(8L, result.serverVersion)
        assertEquals(1024, result.serverPayload?.get("id")?.toString()?.toInt())
        assertEquals("VERSION_CONFLICT", result.errorCode)
    }

    @Test
    fun `Spring changes response keeps the KMP cursor and reset semantics`() {
        val serverResponse = AppSyncChangesRespVO(
            items = listOf(
                AppSyncChangeVO(
                    cursor = 12,
                    resource = MemberAddressSyncContract.RESOURCE,
                    aggregateId = "1024",
                    operation = SyncChangeOperation.UPSERT.name,
                    aggregateVersion = 8,
                    payload = JsonUtils.parseTree("{\"id\":1024,\"version\":8}"),
                ),
            ),
            nextCursor = 12,
            hasMore = false,
            resetRequired = false,
        )

        val clientResponse = clientJson.decodeFromString<SyncChangesResponse>(
            JsonUtils.toJsonString(serverResponse),
        )
        val change = clientResponse.items.single()

        assertEquals(12L, clientResponse.nextCursor)
        assertFalse(clientResponse.hasMore)
        assertFalse(clientResponse.resetRequired)
        assertEquals(SyncChangeOperation.UPSERT, change.operation)
        assertEquals(8L, change.aggregateVersion)
    }
}
