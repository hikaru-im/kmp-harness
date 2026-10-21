package im.hikaru.harness.desktop

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import im.hikaru.contracts.common.ApiResult
import im.hikaru.contracts.harness.identity.HostDescription
import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.contracts.harness.protocol.ProtocolVersion
import im.hikaru.harness.client.app.HarnessApp
import im.hikaru.harness.client.app.HarnessDestination
import im.hikaru.harness.client.connection.*
import im.hikaru.harness.client.plugins.*
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class PluginManagementInteractionTest {
    @Test
    fun localRegistrationShowsDetailEnablesThenRemovesRegistration() =
        runSkikoComposeUiTest(size = Size(1440f, 1000f)) {
            val repository = ManagementUiFixtureRepository()
            val connection = ManagementUiConnection("local-management")
            setContent {
                HarnessApp(
                    connection = connection,
                    initialDestination = HarnessDestination.PluginInventory,
                    installedPluginRepository = repository,
                    connectionTransport = ConnectionTransport.Local,
                )
            }

            waitUntil(timeoutMillis = 5_000) {
                onAllNodes(hasTestTag("plugins-add-local") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
            }
            // The page header is 插件 for both repositories; the management variant shows through
            // the empty-state wording and the registration actions below it.
            onNode(hasText("插件") and hasAnyAncestor(hasTestTag("plugins-header")), true).assertExists()
            onNodeWithText("尚无已登记插件").assertExists()
            onNodeWithTag("plugins-add-local").assertIsEnabled().performClick()
            onNodeWithTag("plugin-register-confirm").assertIsNotEnabled()
            onNodeWithTag("plugin-local-directory").performTextReplacement("relative/path")
            onNodeWithTag("plugin-register-confirm").assertIsNotEnabled()
            onNodeWithTag("plugin-local-directory").performTextReplacement("/source/plugin")
            onNodeWithTag("plugin-register-confirm").assertIsEnabled().performClick()
            waitUntil(timeoutMillis = 5_000) {
                onAllNodesWithTag("plugins-installed:fixture").fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithTag("plugin-local-directory").assertDoesNotExist()
            onNodeWithTag("plugin-mutation-receipt").assertTextEquals("登记已保存")
            onNodeWithText("revision-very-long-server-revision-that-must-not-be-rendered", substring = true).assertDoesNotExist()
            runOnIdle {
                assertEquals("/source/plugin", repository.registeredSource)
                assertEquals(PluginEnabledState.Disabled, repository.plugin!!.enabled)
            }

            onNodeWithTag("plugins-installed:fixture").performClick()
            waitUntil(timeoutMillis = 5_000) {
                onAllNodes(hasTestTag("plugin-set-enabled") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithTag("plugin-detail-back").assertTextEquals("返回已登记插件")
            onNodeWithTag("plugin-detail-list").performScrollToNode(hasText("Review source text"))
            onNodeWithText("Review source text").assertExists()
            onNodeWithTag("plugin-detail-list").performScrollToNode(hasText("Commands 尚未支持。"))
            onNodeWithText("Commands 尚未支持。").assertExists()
            onNodeWithTag("plugin-detail-list").performScrollToNode(hasText("服务确认此插件没有 Apps。"))
            onNodeWithText("服务确认此插件没有 Apps。").assertExists()
            onNodeWithTag("plugin-detail-list").performScrollToNode(hasText("MCP 服务器"))
            onNodeWithText("MCP 服务器").assertExists()
            onNodeWithText("fixture-mcp").assertExists()
            onNodeWithTag("plugin-detail-list").performScrollToNode(hasTestTag("plugin-set-enabled"))
            onNodeWithTag("plugin-set-enabled").assertIsEnabled().performClick()
            waitUntil(timeoutMillis = 5_000) {
                onAllNodes(hasTestTag("plugin-set-enabled") and hasText("停用") and isEnabled())
                    .fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithTag("plugin-set-enabled").assertTextEquals("停用")
            runOnIdle { assertEquals(PluginEnabledState.Enabled, repository.plugin!!.enabled) }

            onNodeWithTag("plugin-remove-registration").performClick()
            onNodeWithText("源目录和源文件都会保留", substring = true).assertExists()
            onNodeWithTag("plugin-remove-confirm").performClick()
            waitUntil(timeoutMillis = 5_000) {
                onAllNodesWithTag("plugin-remove-confirm").fetchSemanticsNodes().isEmpty()
            }
            onNodeWithTag("plugin-detail-list").performScrollToNode(hasTestTag("plugin-mutation-receipt"))
            onNodeWithTag("plugin-mutation-receipt").assertTextEquals("登记已保存")
            onNodeWithTag("plugin-detail-back").performScrollTo().performClick()
            onNodeWithText("尚无已登记插件").assertExists()
            runOnIdle {
                assertNull(repository.plugin)
                assertEquals(mapOf("/source/plugin/SKILL.md" to "Review source text"), repository.sourceFiles)
                assertEquals(listOf("register", "enable", "remove"), repository.operations)
            }
        }

    @Test
    fun readOnlyRepositoryKeepsInstalledLabelsAndHidesManagementControls() =
        runSkikoComposeUiTest(size = Size(1440f, 1000f)) {
            val readOnly = ReadOnlyUiFixtureRepository()
            val connection = ManagementUiConnection("read-only")
            setContent {
                HarnessApp(
                    connection = connection,
                    initialDestination = HarnessDestination.PluginInventory,
                    installedPluginRepository = readOnly,
                    connectionTransport = ConnectionTransport.Local,
                )
            }
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("plugins-installed:fixture").fetchSemanticsNodes().isNotEmpty() }
            // The page header is 插件; the read-only variant shows through the unavailable notice
            // rather than through a different title.
            onNode(hasText("插件") and hasAnyAncestor(hasTestTag("plugins-header")), true).assertExists()
            onNodeWithTag("plugin-management-unavailable").assertExists()
            onNodeWithTag("plugins-add-local").assertDoesNotExist()
            onNodeWithTag("plugins-installed:fixture").performClick()
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("installed-plugin-detail-page").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("plugin-set-enabled").assertDoesNotExist()
            onNodeWithTag("plugin-remove-registration").assertDoesNotExist()
            onNodeWithText("返回已安装插件").assertExists()
        }

    @Test
    fun relayBindingDoesNotExposeLocalManagementActions() =
        runSkikoComposeUiTest(size = Size(1440f, 1000f)) {
            val repository = ManagementUiFixtureRepository()
            val connection = ManagementUiConnection("relay")
            setContent {
                HarnessApp(
                    connection = connection,
                    initialDestination = HarnessDestination.PluginInventory,
                    installedPluginRepository = repository,
                    connectionTransport = ConnectionTransport.Relay,
                )
            }
            waitUntil(timeoutMillis = 5_000) {
            onAllNodesWithTag("installed-plugins-page").fetchSemanticsNodes().isNotEmpty()
            }
            // The page header is 插件; the read-only variant shows through the unavailable notice
            // rather than through a different title.
            onNode(hasText("插件") and hasAnyAncestor(hasTestTag("plugins-header")), true).assertExists()
            onNodeWithText("已登记插件").assertDoesNotExist()
            onNodeWithTag("plugin-management-unavailable").assertExists()
            onNodeWithTag("plugins-add-local").assertDoesNotExist()
            runOnIdle { assertFalse(repository.canManage); assertTrue(repository.operations.isEmpty()) }
        }
}

private class ManagementUiFixtureRepository : LocalPluginManagementRepository {
    override val canRead: Boolean = true
    override val canManage: Boolean get() = access?.transport == ConnectionTransport.Local

    var access: PluginAccessBinding? = null
        private set
    var plugin: InstalledPlugin? = null
        private set
    val registeredSource: String? get() = plugin?.source
    val sourceFiles = mutableMapOf("/source/plugin/SKILL.md" to "Review source text")
    val operations = CopyOnWriteArrayList<String>()
    private var revision = 0

    override fun bindAccess(binding: PluginAccessBinding?) {
        access = binding
    }

    override suspend fun listInstalled(scope: PluginScope): PluginResult<List<InstalledPlugin>> =
        PluginResult.Success(listOfNotNull(plugin))

    override suspend fun readDetail(scope: PluginScope, id: PluginId): PluginResult<InstalledPluginDetail> {
        val current = plugin?.takeIf { it.id == id }
            ?: return PluginResult.Error(PluginRepositoryError("plugin not registered"))
        return PluginResult.Success(detail(current))
    }

    override suspend fun readRevision(scope: PluginScope): PluginResult<String> =
        PluginResult.Success(revisionToken())

    override suspend fun registerLocal(scope: PluginScope, directory: String, expectedRevision: String): PluginResult<PluginMutationReceipt> {
        return commit("register", scope, expectedRevision) {
            plugin = InstalledPlugin(PluginId("fixture"), "Fixture plugin", description = "A local test plugin",
                source = directory, enabled = PluginEnabledState.Disabled)
        }
    }

    override suspend fun setEnabled(scope: PluginScope, id: PluginId, enabled: Boolean, expectedRevision: String): PluginResult<PluginMutationReceipt> {
        return commit(if (enabled) "enable" else "disable", scope, expectedRevision) {
            check(plugin?.id == id)
            plugin = plugin!!.copy(enabled = if (enabled) PluginEnabledState.Enabled else PluginEnabledState.Disabled)
        }
    }

    override suspend fun removeRegistration(scope: PluginScope, id: PluginId, expectedRevision: String): PluginResult<PluginMutationReceipt> {
        return commit("remove", scope, expectedRevision) {
            check(plugin?.id == id)
            plugin = null
        }
    }

    private fun commit(operation: String, scope: PluginScope, expectedRevision: String,
        change: () -> Unit): PluginResult<PluginMutationReceipt> {
        if (!canManage || access?.scope != scope) return PluginResult.Error(PluginRepositoryError("scope replaced"))
        if (expectedRevision != revisionToken()) return PluginResult.Error(PluginRepositoryError("CAS revision mismatch"))
        operations += operation
        change()
        revision++
        return PluginResult.Success(PluginMutationReceipt(revisionToken()))
    }

    private fun revisionToken() = "revision-very-long-server-revision-that-must-not-be-rendered-$revision"

    private fun detail(plugin: InstalledPlugin) = InstalledPluginDetail(
        plugin = plugin,
        apps = PluginAppsState.Available(emptyList()),
        skills = PluginSkillsState.Available(listOf(PluginSkillSummary("Review", "Review source text"))),
        unsupportedAssets = listOf("Commands 尚未支持。"),
        capabilities = listOf(PluginCapabilitySummary(PluginCapabilityKind.McpServer, listOf("fixture-mcp"))),
    )
}

private class ReadOnlyUiFixtureRepository : InstalledPluginRepository {
    override val canRead: Boolean = true

    override suspend fun listInstalled(scope: PluginScope): PluginResult<List<InstalledPlugin>> =
        PluginResult.Success(listOf(InstalledPlugin(PluginId("fixture"), "Read-only plugin", enabled = PluginEnabledState.Disabled)))

    override suspend fun readDetail(scope: PluginScope, id: PluginId): PluginResult<InstalledPluginDetail> =
        PluginResult.Success(InstalledPluginDetail(InstalledPlugin(id, "Read-only plugin", enabled = PluginEnabledState.Disabled)))
}

private class ManagementUiConnection(id: String) : Connection {
    private val description = HostDescription(ProtocolVersion.Current, HostId(id), "Host " + id)

    override val host = object : HostApi {
        override suspend fun describe() = ApiResult(ApiResult.SUCCESS_CODE, "", description)
    }

    override val session = object : SessionApi {
        override suspend fun list(): List<SessionSummary> = emptyList()
        override suspend fun create(id: SessionId?, options: CreateSessionOptions, agentOptions: AgentOptions): SessionSummary =
            error("Plugin management interaction must not create sessions")
        override suspend fun history(id: SessionId): List<Message> = error("Plugin management interaction must not read history")
        override suspend fun prompt(id: SessionId, message: Message): SessionPrompt = error("Plugin management interaction must not prompt")
        override suspend fun cancel(id: SessionId, keepInbox: Boolean) = error("Plugin management interaction must not cancel sessions")
    }
}
