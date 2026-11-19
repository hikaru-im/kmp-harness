@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package im.hikaru.harness.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import im.hikaru.contracts.common.ApiResult
import im.hikaru.contracts.harness.mcp.McpAuthStatus
import im.hikaru.contracts.harness.mcp.McpAuthenticateRequest
import im.hikaru.contracts.harness.mcp.McpAuthenticateResponse
import im.hikaru.contracts.harness.mcp.McpEnabledRequest
import im.hikaru.contracts.harness.mcp.McpListResponse
import im.hikaru.contracts.harness.mcp.McpMutationResponse
import im.hikaru.contracts.harness.mcp.McpRemoveRequest
import im.hikaru.contracts.harness.mcp.McpServerInfo
import im.hikaru.contracts.harness.mcp.McpUpsertRequest
import im.hikaru.harness.client.app.HarnessApp
import im.hikaru.harness.client.app.HarnessDestination
import im.hikaru.harness.client.app.HarnessTitleStripState
import im.hikaru.harness.client.app.LocalHarnessTitleStrip
import im.hikaru.harness.client.connection.ConnectionTransport
import im.hikaru.harness.client.connection.McpApi
import im.hikaru.harness.client.plugins.McpExternalUrlOpener
import im.hikaru.harness.client.connection.Connection
import im.hikaru.harness.ui.foundation.HarnessButton
import im.hikaru.harness.ui.foundation.HarnessThemeMode
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class McpManagementPageInteractionTest {
    @Test
    fun mcpCategoryAndOAuthRowsAtBothWidths() {
        listOf(Size(1440f, 900f), Size(600f, 800f)).forEach { size ->
            val api = FakeMcpApi()
            val connection = PluginsPageTestConnection("mcp-${size.width}").withMcp(api)
            val opened = mutableListOf<String>()
            runSkikoComposeUiTest(size = size) {
                val app = @Composable {
                    HarnessApp(
                        connection,
                        initialDestination = if (size.width == 600f) HarnessDestination.Account
                        else HarnessDestination.PluginInventory,
                        connectionTransport = ConnectionTransport.Local,
                        onOpenExternalUrl = McpExternalUrlOpener { opened += it; true },
                        themeMode = HarnessThemeMode.Light,
                    )
                }
                setContent { if (size.width == 600f) McpTestWindow(content = app) else app() }
                if (size.width == 600f) {
                    awaitTag("app-rail")
                    pluginsPageMouseClick("rail-plugins")
                    awaitTag("plugins-management-tab-mcps")
                    toggleNavigator()
                }
                awaitTag("plugins-management-tab-mcps")
                pluginsPageMouseClick("plugins-management-tab-mcps")
                pluginsPageSettle()
                awaitTag("mcp-management-page")
                onAllNodesWithTag("plugins-management-tab-mcps").assertAll(isSelected())
                onNodeWithTag("mcp-server-alpha").assertExists()
                onNodeWithTag("mcp-server-authenticate-alpha").assertExists()
                onNodeWithTag("mcp-server-beta").assertExists()
                onNodeWithTag("mcp-server-authenticate-beta").assertDoesNotExist()
                onNodeWithTag("mcp-server-tools-alpha").assertDoesNotExist()
                onNodeWithTag("mcp-server-enabled-alpha").assertIsEnabled()
                onAllNodesWithTag("plugins-management-tab-apps").assertAll(isNotEnabled())
                onNodeWithText("暂不可用").assertDoesNotExist()
                pluginsPageMouseClick("mcp-server-authenticate-alpha")
                awaitTag("mcp-management-page")
                waitUntil(timeoutMillis = 5_000) { opened.isNotEmpty() }
                assertEquals(listOf(AUTHORIZATION_URL), opened)
                assertTrue(api.listCalls >= 2)
                onNodeWithText(AUTHORIZATION_URL).assertDoesNotExist()
                pluginsPageMouseClick("mcp-server-settings-alpha")
                awaitTag("mcp-editor-command")
                pluginsPageMouseClick("mcp-editor-cancel")
            }
        }
    }

    private fun ComposeUiTest.awaitTag(tag: String) {
        waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
        pluginsPageSettle()
    }

    private fun ComposeUiTest.toggleNavigator() {
        pluginsPageMouseClick("mcp-test-toggle")
        pluginsPageSettle()
    }

    private companion object {
        const val AUTHORIZATION_URL = "https://example.test/authorize?state=fixture"
    }

    private fun Connection.withMcp(api: McpApi): Connection = object : Connection by this {
        override val mcp: McpApi = api
    }

    @Composable
    private fun McpTestWindow(content: @Composable () -> Unit) {
        val strip = remember { HarnessTitleStripState() }
        CompositionLocalProvider(LocalHarnessTitleStrip provides strip) {
            Box(Modifier.fillMaxSize()) {
                content()
                HarnessButton(
                    "侧栏",
                    { strip.navigatorToggle?.invoke() },
                    Modifier.align(Alignment.TopEnd),
                    enabled = strip.navigatorToggle != null,
                    testTag = "mcp-test-toggle",
                )
            }
        }
    }

    private class FakeMcpApi : McpApi {
        private var revision = 1
        var listCalls = 0
            private set

        private fun servers(): List<McpServerInfo> = listOf(
            McpServerInfo(
                entryId = "mcp-client:alpha", serverName = "alpha", config = config("alpha"),
                enabled = true, editable = true, connectionPhase = "connected",
                authStatus = McpAuthStatus.NotLoggedIn, toolCount = null,
            ),
            McpServerInfo(
                entryId = "mcp-client:beta", serverName = "beta", config = config("beta"),
                enabled = true, editable = true, connectionPhase = "configured",
                authStatus = McpAuthStatus.Unsupported, toolCount = 0,
            ),
        )

        override suspend fun list(): ApiResult<McpListResponse> {
            listCalls++
            return ApiResult(ApiResult.SUCCESS_CODE, data = McpListResponse("rev-$revision", servers()))
        }
        override suspend fun upsert(request: McpUpsertRequest): ApiResult<McpMutationResponse> = success()
        override suspend fun setEnabled(request: McpEnabledRequest): ApiResult<McpMutationResponse> = success()
        override suspend fun remove(request: McpRemoveRequest): ApiResult<McpMutationResponse> = success()
        override suspend fun authenticate(request: McpAuthenticateRequest): ApiResult<McpAuthenticateResponse> =
            ApiResult(ApiResult.SUCCESS_CODE, data = McpAuthenticateResponse(AUTHORIZATION_URL))

        private fun success(): ApiResult<McpMutationResponse> =
            ApiResult(ApiResult.SUCCESS_CODE, data = McpMutationResponse("rev-${++revision}", true, true))

        private fun config(name: String) = buildJsonObject {
            put("serverName", name)
            put("transport", buildJsonObject { put("type", "stdio"); put("command", "fixture") })
        }
    }
}
