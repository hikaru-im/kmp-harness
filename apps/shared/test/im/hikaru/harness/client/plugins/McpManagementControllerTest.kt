package im.hikaru.harness.client.plugins

import im.hikaru.contracts.common.ApiResult
import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.contracts.harness.mcp.McpAuthStatus
import im.hikaru.contracts.harness.mcp.McpAuthenticateRequest
import im.hikaru.contracts.harness.mcp.McpAuthenticateResponse
import im.hikaru.contracts.harness.mcp.McpEnabledRequest
import im.hikaru.contracts.harness.mcp.McpListResponse
import im.hikaru.contracts.harness.mcp.McpMutationResponse
import im.hikaru.contracts.harness.mcp.McpRemoveRequest
import im.hikaru.contracts.harness.mcp.McpServerInfo
import im.hikaru.contracts.harness.mcp.McpUpsertRequest
import im.hikaru.harness.client.connection.McpApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class McpManagementControllerTest {
    @Test
    fun unavailableListNeverBecomesEmptySuccess() = runTest {
        val api = FakeMcpApi(list = ApiResult(code = 502, msg = "MCP management is unavailable"))
        val controller = controller(api)
        controller.bind(scope(), api)
        runCurrent()
        val failure = assertIs<McpManagementLoadState.Error>(controller.state.value.load)
        assertEquals(502, failure.code)
        assertNull(controller.state.value.revision)
        assertTrue(controller.state.value.servers.isEmpty())
        controller.close()
    }

    @Test
    fun successfulListRendersOnlyHostRows() = runTest {
        val api = FakeMcpApi(servers = listOf(server()))
        val errors = mutableListOf<String>()
        val controller = McpManagementController(this, onError = { errors += it })
        controller.bind(scope(), api)
        runCurrent()
        val success = assertIs<McpManagementLoadState.Success>(controller.state.value.load)
        assertEquals("rev-1", success.response.revision)
        assertEquals("alpha", controller.state.value.servers.single().serverName)
        assertEquals(null, controller.state.value.servers.single().toolCount)
        assertTrue(errors.isEmpty())
        controller.close()
    }

    @Test
    fun mutationsUseLatestRevisionAndRelistAfterConflict() = runTest {
        val api = FakeMcpApi(servers = listOf(server()), mutationCode = 409)
        val errors = mutableListOf<String>()
        val controller = McpManagementController(this, onError = { errors += it })
        controller.bind(scope(), api)
        runCurrent()
        controller.dispatch(McpManagementAction.SetEnabled(controller.state.value.servers.single(), false))
        runCurrent()
        assertEquals("rev-1", api.lastEnabledRequest?.expectedRevision)
        assertEquals("配置已被更新，请重试", errors.single())
        assertIs<McpManagementLoadState.Success>(controller.state.value.load)
        assertNull(controller.state.value.editorServer)
        controller.close()
    }

    @Test
    fun authenticateOpensOnlyValidatedHttpsUrlAndRefreshes() = runTest {
        val api = FakeMcpApi(
            servers = listOf(server(auth = McpAuthStatus.NotLoggedIn)),
            authorizationUrl = "https://example.test/authorize?state=fixture",
        )
        val opened = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val controller = McpManagementController(this,
            openExternalUrl = { opened += it; true }, onError = { errors += it })
        controller.bind(scope(), api)
        runCurrent()
        controller.dispatch(McpManagementAction.Authenticate(controller.state.value.servers.single()))
        runCurrent()
        assertEquals(listOf("https://example.test/authorize?state=fixture"), opened)
        assertEquals(1, api.authenticateCalls)
        assertEquals(2, api.listCalls)
        assertTrue(errors.isEmpty())

        api.authorizationUrl = "http://example.test/authorize"
        controller.dispatch(McpManagementAction.Authenticate(controller.state.value.servers.single()))
        runCurrent()
        assertEquals(1, opened.size)
        assertEquals("无法启动授权", errors.single())
        controller.close()
    }

    @Test
    fun scopeReplacementClearsMutationBusyFromCancelledWork() = runTest {
        val gate = CompletableDeferred<Unit>()
        val first = FakeMcpApi(servers = listOf(server()), mutationGate = gate)
        val second = FakeMcpApi(servers = listOf(server("second")))
        val controller = controller(first)
        controller.bind(scope("first"), first)
        runCurrent()
        controller.dispatch(McpManagementAction.SetEnabled(controller.state.value.servers.single(), false))
        runCurrent()
        assertTrue(controller.state.value.mutationBusy)
        controller.bind(scope("second"), second)
        runCurrent()
        val success = assertIs<McpManagementLoadState.Success>(controller.state.value.load)
        assertEquals("second", success.response.servers.single().serverName)
        assertTrue(controller.state.value.canRefresh)
        controller.dispatch(McpManagementAction.Refresh)
        runCurrent()
        assertEquals(2, second.listCalls)
        gate.complete(Unit)
        runCurrent()
        assertFalse(controller.state.value.mutationBusy)
        controller.close()
    }

    @Test
    fun scopeReplacementIgnoresStaleReadAndClearsState() = runTest {
        val firstGate = CompletableDeferred<Unit>()
        val first = FakeMcpApi(servers = listOf(server("first")), listGate = firstGate)
        val second = FakeMcpApi(servers = listOf(server("second")))
        val controller = controller(first)
        controller.bind(scope("first"), first)
        runCurrent()
        controller.bind(scope("second"), second)
        runCurrent()
        val beforeStaleReply = assertIs<McpManagementLoadState.Success>(controller.state.value.load)
        assertEquals("second", beforeStaleReply.response.servers.single().serverName)
        firstGate.complete(Unit)
        runCurrent()
        val success = assertIs<McpManagementLoadState.Success>(controller.state.value.load)
        assertEquals("rev-1", success.response.revision)
        assertEquals("second", success.response.servers.single().serverName)
        assertEquals(0, first.listCalls - 1)
        assertEquals(1, second.listCalls)
        controller.close()
    }

    private fun TestScope.controller(api: FakeMcpApi): McpManagementController =
        McpManagementController(this, onError = {})

    private fun scope(value: String = "host"): PluginScope = PluginScope(HostId(value), 1L)

    private fun server(
        name: String = "alpha",
        auth: McpAuthStatus = McpAuthStatus.Unsupported,
    ): McpServerInfo = McpServerInfo(
        entryId = "mcp-client:$name",
        serverName = name,
        config = buildJsonObject {
            put("serverName", name)
            put("transport", buildJsonObject { put("type", "stdio"); put("command", "fixture") })
        },
        enabled = true,
        editable = true,
        connectionPhase = "configured",
        authStatus = auth,
    )

    private class FakeMcpApi(
        servers: List<McpServerInfo> = emptyList(),
        var list: ApiResult<McpListResponse>? = null,
        var mutationCode: Int = ApiResult.SUCCESS_CODE,
        var authorizationUrl: String? = null,
        var listGate: CompletableDeferred<Unit>? = null,
        var mutationGate: CompletableDeferred<Unit>? = null,
    ) : McpApi {
        private val initialServers = servers
        var listCalls = 0
            private set
        var authenticateCalls = 0
            private set
        var lastEnabledRequest: McpEnabledRequest? = null
            private set
        var revision = 1

        override suspend fun list(): ApiResult<McpListResponse> {
            listCalls++
            listGate?.await()
            return list ?: ApiResult(ApiResult.SUCCESS_CODE, data = McpListResponse("rev-$revision", initialServers))
        }

        override suspend fun upsert(request: McpUpsertRequest): ApiResult<McpMutationResponse> = mutation()
        override suspend fun setEnabled(request: McpEnabledRequest): ApiResult<McpMutationResponse> {
            lastEnabledRequest = request
            return mutation()
        }
        override suspend fun remove(request: McpRemoveRequest): ApiResult<McpMutationResponse> = mutation()
        override suspend fun authenticate(request: McpAuthenticateRequest): ApiResult<McpAuthenticateResponse> {
            authenticateCalls++
            return ApiResult(ApiResult.SUCCESS_CODE, data = McpAuthenticateResponse(authorizationUrl))
        }

        private suspend fun mutation(): ApiResult<McpMutationResponse> {
            mutationGate?.await()
            if (mutationCode != ApiResult.SUCCESS_CODE) {
                return ApiResult(mutationCode, msg = "conflict")
            }
            revision++
            return ApiResult(ApiResult.SUCCESS_CODE, data = McpMutationResponse("rev-$revision", true, true))
        }
    }
}
