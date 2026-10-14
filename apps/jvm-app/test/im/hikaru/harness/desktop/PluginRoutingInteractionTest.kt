package im.hikaru.harness.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.dp
import im.hikaru.contracts.common.ApiResult
import im.hikaru.contracts.harness.identity.HostDescription
import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.contracts.harness.protocol.ProtocolVersion
import im.hikaru.harness.client.app.HarnessApp
import im.hikaru.harness.client.app.HarnessDestination
import im.hikaru.harness.client.connection.*
import im.hikaru.harness.client.plugins.*
import im.hikaru.harness.ui.foundation.HarnessThemeMode
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class PluginRoutingInteractionTest {
    @Test
    fun sidebarFiltersTheLoadedListAndDetailsReadTheSelectedId() =
        runSkikoComposeUiTest(size = Size(1440f, 900f)) {
            val repository = RoutingPluginRepository()
            val connection = RoutingPluginConnection("first")
            setContent { HarnessApp(connection,
                initialDestination = HarnessDestination.PluginInventory, installedPluginRepository = repository) }
            onNodeWithTag("installed-plugin-first-enabled").assertExists()
            onNodeWithTag("installed-plugin-first-disabled").assertExists()
            onNodeWithTag("plugins-filter-enabled").performClick()
            onNodeWithTag("installed-plugin-first-enabled").assertExists()
            onNodeWithTag("installed-plugin-first-disabled").assertDoesNotExist()
            onNodeWithTag("plugins-filter-all").performClick()
            onNodeWithTag("installed-plugin-first-disabled").assertExists().performClick()
            onNodeWithTag("installed-plugin-detail-page").assertExists()
            onNodeWithText("Detail for first-disabled").assertExists()
            runOnIdle { assertEquals(listOf(PluginId("first-disabled")), repository.details.map { it.second }) }
            onNodeWithTag("plugin-detail-back").performClick()
            onNodeWithTag("installed-plugins-page").assertExists()
        }

    @Test
    fun changingHostsClearsTheOldListAndDisconnectClearsAllPluginData() =
        runSkikoComposeUiTest(size = Size(1440f, 900f)) {
            val repository = RoutingPluginRepository()
            var connection: Connection? by mutableStateOf(RoutingPluginConnection("first"))
            setContent { HarnessApp(connection, initialDestination = HarnessDestination.PluginInventory,
                installedPluginRepository = repository) }
            onNodeWithTag("installed-plugin-first-enabled").assertExists()
            runOnIdle { connection = RoutingPluginConnection("second") }
            onNodeWithTag("installed-plugin-second-enabled").assertExists()
            onNodeWithTag("installed-plugin-first-enabled").assertDoesNotExist()
            runOnIdle { connection = null }
            onNodeWithTag("installed-plugin-second-enabled").assertDoesNotExist()
            onNodeWithTag("plugins-search").assertIsNotEnabled()
            runOnIdle { assertEquals(setOf("first", "second"), repository.reads.map { it.hostId.value }.toSet()) }
        }

    @Test
    fun defaultRepositoryShowsUnavailableAndHasNoFakeRuntimePluginCard() =
        runSkikoComposeUiTest(size = Size(1440f, 900f)) {
            val connection = RoutingPluginConnection("first")
            setContent { HarnessApp(connection, initialDestination = HarnessDestination.PluginInventory) }
            onNodeWithText("插件服务不可用").assertExists()
            onNodeWithText("Desktop Client UI").assertDoesNotExist()
            onNodeWithTag("plugins-search").assertIsNotEnabled()
            onNodeWithTag("plugins-refresh").assertIsNotEnabled()
        }

    @Test
    fun navigatorListsTheRealRegisteredPluginsAndSharesThePageQuery() =
        runSkikoComposeUiTest(size = Size(1440f, 900f)) {
            val repository = RoutingPluginRepository()
            val connection = RoutingPluginConnection("first")
            setContent { HarnessApp(connection,
                initialDestination = HarnessDestination.PluginInventory, installedPluginRepository = repository) }
            // The grouped list is the Host's real inventory, not a fixture of invented names.
            onNodeWithTag("plugins-navigator-header").assertExists()
            onNodeWithTag("plugins-installed:first-enabled").assertExists()
            onNodeWithTag("plugins-installed:first-disabled").assertExists()
            onNodeWithTag("plugins-installed:first-disabled").performClick()
            onNodeWithTag("installed-plugin-detail-page").assertExists()
            onNodeWithText("Detail for first-disabled").assertExists()
            onNodeWithTag("plugin-detail-back").performClick()
            // The navigator search edits the same query the page field renders.
            onNodeWithTag("plugins-toggle-search").performClick()
            onNodeWithTag("plugins-navigator-search").performTextReplacement("Enabled")
            onNodeWithTag("plugins-installed:first-enabled").assertExists()
            onNodeWithTag("plugins-installed:first-disabled").assertDoesNotExist()
            onNodeWithTag("plugins-search").assertTextContains("Enabled")
            onNodeWithTag("plugins-navigator-search").performTextReplacement("no-such-plugin")
            onNodeWithText("没有匹配的已登记插件").assertExists()
        }

    @Test
    fun navigatorUsesTheMeasuredPageOwnedGeometry() =
        runSkikoComposeUiTest(size = Size(1440f, 900f)) {
            val repository = RoutingPluginRepository()
            val connection = RoutingPluginConnection("first")
            setContent { HarnessApp(connection,
                initialDestination = HarnessDestination.PluginInventory, installedPluginRepository = repository) }
            val pane = onNodeWithTag("plugins-navigator").getUnclippedBoundsInRoot()
            val all = onNodeWithTag("plugins-filter-all").getUnclippedBoundsInRoot()
            val enabled = onNodeWithTag("plugins-filter-enabled").getUnclippedBoundsInRoot()
            val first = onNodeWithTag("plugins-installed:first-enabled").getUnclippedBoundsInRoot()
            val second = onNodeWithTag("plugins-installed:first-disabled").getUnclippedBoundsInRoot()
            // The 2026-10-06 captures put the page-owned navigator at x=52..340 in a 2560px window.
            val paneWidth = pane.right - pane.left
            assertEquals(289f, paneWidth.value, "page-owned navigator width")
            // Rows fill the pane minus the 8dp body padding, are 30px tall and sit on a 1px pitch.
            assertEquals(8f, (all.left - pane.left).value, "row inset")
            assertEquals(paneWidth.value - 16f, (all.right - all.left).value, "row fill width")
            assertEquals(30f, (all.bottom - all.top).value, "leading-glyph row height")
            assertEquals(31f, (enabled.top - all.top).value, "destination row pitch")
            assertEquals(31f, (second.top - first.top).value, "installed row pitch")
            // Label at +40: 8dp row padding + 16dp glyph + 8dp gap, matching the capture's 40/41.
            val label = onNode(hasText("Enabled plugin") and hasAnyAncestor(hasTestTag("plugins-installed:first-enabled")),
                useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertEquals(40f, (label.left - pane.left).value, "installed label inset")
        }

    /**
     * Renders the production Shell so the plugins navigator can be measured against the 2026-10-06
     * Codex capture. Writes PNG + geometry only when HARNESS_PLUGINS_NAVIGATOR_CAPTURE_DIR is set.
     */
    @Test
    fun capturesRenderedNavigatorForParityReview() {
        val directory = System.getenv("HARNESS_PLUGINS_NAVIGATOR_CAPTURE_DIR")?.takeIf { it.isNotBlank() }?.let(::File)
            ?: return
        for (size in listOf(Size(1440f, 900f), Size(2560f, 1440f)))
            for (theme in listOf(HarnessThemeMode.Light, HarnessThemeMode.Dark))
                runSkikoComposeUiTest(size = size) {
                    val repository = RoutingPluginRepository()
                    setContent { HarnessApp(RoutingPluginConnection("first"), themeMode = theme,
                        // The real window reserves a 44dp title strip; that inset is also what makes the
                        // navigator draw its right hairline, which the 2026-10-06 capture shows at x=340.
                        initialDestination = HarnessDestination.PluginInventory, installedPluginRepository = repository) }
                    onNodeWithTag("plugins-navigator").assertExists()
                    onNodeWithTag("plugins-filter-all").assertIsSelected()
                    onNodeWithTag("plugins-installed:first-enabled").assertExists()
                    onNodeWithTag("plugins-installed:first-disabled").assertExists()
                    pluginsShellCapture(directory,
                        "navigator-" + size.width.toInt() + "x" + size.height.toInt() + "-" + theme.name.lowercase(),
                        size.width.toInt(), size.height.toInt())
                }
    }
}

private class RoutingPluginRepository : InstalledPluginRepository {
    override val canRead = true
    val reads = CopyOnWriteArrayList<PluginScope>()
    val details = CopyOnWriteArrayList<Pair<PluginScope, PluginId>>()
    override suspend fun listInstalled(scope: PluginScope): PluginResult<List<InstalledPlugin>> {
        reads += scope
        return PluginResult.Success(listOf(
            InstalledPlugin(PluginId(scope.hostId.value + "-enabled"), "Enabled plugin", enabled = PluginEnabledState.Enabled),
            InstalledPlugin(PluginId(scope.hostId.value + "-disabled"), "Disabled plugin", enabled = PluginEnabledState.Disabled),
        ))
    }
    override suspend fun readDetail(scope: PluginScope, id: PluginId): PluginResult<InstalledPluginDetail> {
        details += scope to id
        return PluginResult.Success(InstalledPluginDetail(InstalledPlugin(id, "Detail for " + id.value)))
    }
}

private class RoutingPluginConnection(id: String) : Connection {
    private val description = HostDescription(ProtocolVersion.Current, HostId(id), "Host " + id)
    override val host = object : HostApi {
        override suspend fun describe() = ApiResult(ApiResult.SUCCESS_CODE, "", description)
    }
    override val session = object : SessionApi {
        override suspend fun list(): List<SessionSummary> = emptyList()
        override suspend fun create(id: SessionId?, options: CreateSessionOptions, agentOptions: AgentOptions): SessionSummary =
            error("Plugin navigation must not create sessions")
        override suspend fun history(id: SessionId): List<Message> = error("Plugin navigation must not read history")
        override suspend fun prompt(id: SessionId, message: Message): SessionPrompt = error("Plugin navigation must not prompt")
        override suspend fun cancel(id: SessionId, keepInbox: Boolean) = error("Plugin navigation must not cancel sessions")
    }
}
