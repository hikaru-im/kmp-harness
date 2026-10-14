@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package im.hikaru.harness.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import im.hikaru.harness.client.account.AppSession
import im.hikaru.harness.client.account.AppSessionStore
import im.hikaru.harness.client.account.BackendTenant
import im.hikaru.harness.client.account.MemberAccount
import im.hikaru.harness.client.account.MemberIdentity
import im.hikaru.harness.client.account.MemberTransport
import im.hikaru.harness.client.app.HarnessApp
import im.hikaru.harness.client.app.HarnessDestination
import im.hikaru.harness.client.connection.Connection
import im.hikaru.harness.client.connection.ConnectionTransport
import im.hikaru.harness.client.plugins.*
import im.hikaru.harness.ui.foundation.HarnessThemeMode
import java.nio.file.Files
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlin.test.*

/** Complete app routes and controller; navigation/confirm/cancel always use mouse input. */
class PluginsPageInteractionTest {
    @Test fun mouseSearchFilterDetailAndBackAtBothWidths() {
        listOf(Size(1440f, 900f), Size(600f, 800f)).forEach { size ->
            runSkikoComposeUiTest(size = size) {
                val repository = PluginsPageFixtureRepository()
                val connection = PluginsPageTestConnection("search-host")
                setContent { HarnessApp(connection,
                    initialDestination = HarnessDestination.PluginInventory, installedPluginRepository = repository,
                    connectionTransport = ConnectionTransport.Local, themeMode = HarnessThemeMode.Light) }
                pluginsPageAwaitEnabled("plugins-search")
                pluginsPageAssertNavigator(pushed = size.width == 1440f)
                pluginsPageMouseClick("plugins-search")
                onNodeWithTag("plugins-search").performTextInput("Plugin 1")
                pluginsPageSettle()
                onNodeWithTag("plugins-search").assertTextContains("Plugin 1")
                onNodeWithTag("installed-plugin-search-host-1").assertIsDisplayed()
                onNodeWithTag("installed-plugin-search-host-0").assertDoesNotExist()
                pluginsPageMouseClick("plugins-enabled-only")
                waitUntil(timeoutMillis = 5_000) { onAllNodes(hasTestTag("plugins-enabled-only") and hasText("显示全部插件"))
                    .fetchSemanticsNodes().isNotEmpty() }
                onNodeWithText("没有匹配的插件").assertExists()
                pluginsPageMouseClick("plugins-enabled-only")
                waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("installed-plugin-search-host-1").fetchSemanticsNodes().isNotEmpty() }
                pluginsPageMouseClick("installed-plugin-search-host-1")
                pluginsPageAwaitEnabled("plugin-set-enabled")
                pluginsPageAssertNavigator(pushed = size.width == 1440f)
                onNodeWithTag("plugin-detail-name").assertTextEquals("Plugin 1")
                pluginsPageMouseClick("plugin-detail-back")
                pluginsPageAssertNavigator(pushed = size.width == 1440f)
                onNodeWithTag("plugins-search").assertTextContains("Plugin 1")
                onNodeWithTag("installed-plugin-search-host-1").assertIsDisplayed()
            }
        }
    }

    @Test fun compactPluginsRouteFloatsItsNavigatorAndKeepsTheRail() =
        runSkikoComposeUiTest(size = Size(600f, 800f)) {
            val repository = PluginsPageFixtureRepository()
            val connection = PluginsPageTestConnection("compact")
            setContent { HarnessApp(connection, initialDestination = HarnessDestination.Account,
                installedPluginRepository = repository, connectionTransport = ConnectionTransport.Local) }
            // The rail is always mounted; at 600dp it is the navigation, with no bottom bar.
            pluginsPageAwaitTag("app-rail")
            pluginsPageMouseClick("rail-plugins")
            pluginsPageAwaitEnabled("plugins-search")
            // A compact window floats the page-owned navigator instead of pushing it.
            pluginsPageAssertNavigator(pushed = false)

            // The titlebar toggle opens and dismisses the floating drawer.
            onNodeWithContentDescription("Toggle sidebar").performMouseInput { moveTo(center); click() }
            pluginsPageSettle()
            onNodeWithTag("navigator-scrim").assertIsDisplayed()
            onNodeWithTag("plugins-navigator").assertIsDisplayed()
            onNodeWithContentDescription("Toggle sidebar").performMouseInput { moveTo(center); click() }
            pluginsPageSettle()
            pluginsPageAssertNavigator(pushed = false)

            // Start a real refresh before opening the drawer, then complete it while the drawer is open.
            val gate = CompletableDeferred<Unit>()
            val readsBefore = repository.listReads.size
            runOnIdle { repository.readGate = gate }
            pluginsPageMouseClick("plugins-refresh")
            waitUntil(timeoutMillis = 5_000) { repository.listReads.size > readsBefore }
            onNodeWithContentDescription("Toggle sidebar").performMouseInput { moveTo(center); click() }
            onNodeWithTag("navigator-scrim").assertIsDisplayed()
            runOnIdle { repository.readGate = null; gate.complete(Unit) }
            pluginsPageAwaitEnabled("plugins-search")
            onNodeWithTag("navigator-scrim").assertIsDisplayed()
            onNodeWithTag("navigator-overlay").assertIsDisplayed()

            pluginsPageMouseClick("plugins-filter-enabled")
            pluginsPageAssertNavigator(pushed = false)
            onNodeWithTag("installed-plugin-drawer-1").assertDoesNotExist()
            onNodeWithTag("plugins-filter-all").performMouseInput { moveTo(center); click() }
            pluginsPageSettle()
            pluginsPageAssertNavigator(pushed = false)
            onNodeWithTag("installed-plugin-drawer-1").assertIsDisplayed()
            pluginsPageMouseClick("installed-plugin-drawer-1")
            pluginsPageAwaitEnabled("plugin-set-enabled")
            pluginsPageAssertNavigator(pushed = false)
        }


    @Test fun pushedNavigatorSelectionsAndRoutesPreserveUserMode() =
        runSkikoComposeUiTest(size = Size(1440f, 900f)) {
            val repository = PluginsPageFixtureRepository()
            val connection = PluginsPageTestConnection("pushed")
            setContent { HarnessApp(connection, initialDestination = HarnessDestination.PluginInventory,
                installedPluginRepository = repository, connectionTransport = ConnectionTransport.Local) }
            pluginsPageAwaitEnabled("plugins-search")
            pluginsPageAssertNavigator(pushed = true)
            pluginsPageMouseClick("plugins-filter-enabled")
            pluginsPageAssertNavigator(pushed = true)
            onNodeWithTag("installed-plugin-pushed-1").assertDoesNotExist()
            pluginsPageMouseClick("plugins-filter-all")
            pluginsPageAssertNavigator(pushed = true)

            onNodeWithContentDescription("Toggle sidebar").performMouseInput { moveTo(center); click() }
            pluginsPageSettle()
            pluginsPageAssertNavigator(pushed = false)
            pluginsPageMouseClick("installed-plugin-pushed-1")
            pluginsPageAwaitEnabled("plugin-set-enabled")
            pluginsPageAssertNavigator(pushed = false)
            pluginsPageMouseClick("plugin-detail-back")
            pluginsPageAssertNavigator(pushed = false)
            onNodeWithContentDescription("Toggle sidebar").performMouseInput { moveTo(center); click() }
            pluginsPageSettle()
            pluginsPageAssertNavigator(pushed = true)
        }

    @Test fun realDiskRegistrationStartsDisabledEnablesAndRemovalKeepsSource() {
        PluginsPageTestSupport().use { fixture ->
            val manifestBytes = Files.readAllBytes(fixture.manifest)
            val skillBytes = Files.readAllBytes(fixture.skill)
            runSkikoComposeUiTest(size = Size(600f, 800f)) {
                setContent { HarnessApp(fixture.connection, initialDestination = HarnessDestination.PluginInventory,
                    installedPluginRepository = fixture.repository, connectionTransport = ConnectionTransport.Local,
                    themeMode = HarnessThemeMode.Dark) }
                pluginsPageAwaitEnabled("plugins-add-local")
                pluginsPageMouseClick("plugins-add-local")
                pluginsPageAwaitTag("plugin-register-confirm")
                onNodeWithTag("plugin-register-confirm").assertIsNotEnabled()
                pluginsPageMouseClick("plugin-local-directory")
                onNodeWithTag("plugin-local-directory").performTextInput("relative/path")
                onNodeWithTag("plugin-register-confirm").assertIsNotEnabled()
                onNodeWithTag("plugin-local-directory").performTextReplacement(fixture.source.toString())
                pluginsPageMouseClick("plugin-register-confirm")
                waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("plugin-local-directory").fetchSemanticsNodes().isEmpty() }
                pluginsPageAwaitEnabled("plugins-add-local")
                onNodeWithTag("plugin-local-directory").assertDoesNotExist()
                val registered = runBlocking { fixture.service.read() }.plugins.single()
                assertFalse(registered.enabled)
                assertEquals(fixture.source.toString(), registered.source)
                assertEquals("Review the temporary workspace", registered.skills.single().description)
                val row = "installed-plugin-" + registered.id
                pluginsPageWheelTo("plugin-inventory-list", row)
                pluginsPageMouseClick(row)
                pluginsPageAwaitEnabled("plugin-set-enabled")
                onNodeWithTag("plugin-set-enabled").assertTextEquals("启用")
                pluginsPageMouseClick("plugin-set-enabled")
                waitUntil(timeoutMillis = 5_000) { onAllNodes(hasTestTag("plugin-set-enabled") and hasText("停用") and isEnabled())
                    .fetchSemanticsNodes().isNotEmpty() }
                assertTrue(runBlocking { fixture.service.read() }.plugins.single().enabled)
                pluginsPageMouseClick("plugin-remove-registration")
                onNodeWithText("源目录和源文件都会保留", substring = true).assertIsDisplayed()
                pluginsPageMouseClick("plugin-remove-cancel")
                onNodeWithTag("plugin-remove-confirm").assertDoesNotExist()
                assertEquals(1, runBlocking { fixture.service.read() }.plugins.size)
                pluginsPageMouseClick("plugin-remove-registration")
                pluginsPageMouseClick("plugin-remove-confirm")
                waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("plugin-remove-confirm").fetchSemanticsNodes().isEmpty() }
                // Removal clears the selected controller ID; the parent owns returning to inventory.
                if (onAllNodesWithTag("plugin-detail-back").fetchSemanticsNodes().isNotEmpty()) {
                    pluginsPageWheelTo("plugin-detail-list", "plugin-detail-back", -300f)
                    pluginsPageMouseClick("plugin-detail-back")
                }
                onNodeWithText("尚无已登记插件").assertExists()
                onNodeWithTag("plugin-mutation-receipt").assertTextEquals("登记已保存")
                assertTrue(runBlocking { fixture.service.read() }.plugins.isEmpty())
            }
            assertTrue(Files.isRegularFile(fixture.inventoryFile))
            assertContentEquals(manifestBytes, Files.readAllBytes(fixture.manifest))
            assertContentEquals(skillBytes, Files.readAllBytes(fixture.skill))
        }
    }

    @Test fun hostReplacementAndDisconnectHideDataAndBothDialogs() =
        runSkikoComposeUiTest(size = Size(1440f, 900f)) {
            val repository = PluginsPageFixtureRepository()
            var connection: Connection? by mutableStateOf(PluginsPageTestConnection("scope-first"))
            setContent { HarnessApp(connection, initialDestination = HarnessDestination.PluginInventory,
                installedPluginRepository = repository, connectionTransport = ConnectionTransport.Local) }
            pluginsPageAwaitEnabled("plugins-add-local")
            pluginsPageMouseClick("plugins-add-local")
            pluginsPageAwaitTag("plugin-local-directory")
            pluginsPageMouseClick("plugin-local-directory")
            onNodeWithTag("plugin-local-directory").performTextInput("/old-scope/source")
            val gate = CompletableDeferred<Unit>()
            runOnIdle { repository.readGate = gate; connection = PluginsPageTestConnection("scope-second") }
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("plugin-local-directory").fetchSemanticsNodes().isEmpty() }
            onNodeWithTag("installed-plugin-scope-first-0").assertDoesNotExist()
            onNodeWithText("正在读取插件信息…").assertExists()
            onNodeWithTag("plugins-add-local").assertIsNotEnabled()
            runOnIdle { repository.readGate = null; gate.complete(Unit) }
            pluginsPageAwaitEnabled("plugins-add-local")
            pluginsPageMouseClick("installed-plugin-scope-second-0")
            pluginsPageAwaitEnabled("plugin-remove-registration")
            pluginsPageMouseClick("plugin-remove-registration")
            runOnIdle { connection = PluginsPageTestConnection("scope-third") }
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("plugin-remove-confirm").fetchSemanticsNodes().isEmpty() }
            onNodeWithText("主机：scope-second").assertDoesNotExist()
            onNodeWithTag("installed-plugin-scope-second-0").assertDoesNotExist()
            runOnIdle { connection = null }
            onNodeWithTag("plugin-detail-name").assertDoesNotExist()
            onNodeWithTag("plugin-remove-confirm").assertDoesNotExist()
            if (onAllNodesWithTag("plugin-detail-back").fetchSemanticsNodes().isNotEmpty()) {
                pluginsPageMouseClick("plugin-detail-back")
            }
            onNodeWithTag("plugins-search").assertIsNotEnabled()
            onNodeWithTag("plugins-refresh").assertIsNotEnabled()
        }

    @Test fun failedReadIsErrorAndExplicitRefreshRecovers() {
        PluginsPageTestSupport().use { fixture ->
            val repository = PluginsPageReadFaults(fixture.repository).apply { failReads = true }
            runSkikoComposeUiTest(size = Size(1440f, 900f)) {
                setContent { HarnessApp(fixture.connection, initialDestination = HarnessDestination.PluginInventory,
                    installedPluginRepository = repository, connectionTransport = ConnectionTransport.Local) }
                waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Test plugin read failed").fetchSemanticsNodes().isNotEmpty() }
                onNodeWithText("尚无已登记插件").assertDoesNotExist()
                onNode(hasText("重试") and hasAnyAncestor(hasTestTag("error-state"))).assertExists()
                onAllNodesWithText("Retry").assertCountEquals(0)
                onNodeWithTag("plugins-search").assertIsNotEnabled()
                onNodeWithTag("plugins-add-local").assertIsNotEnabled()
                runOnIdle { repository.failReads = false }
                pluginsPageMouseClick("plugins-refresh")
                pluginsPageAwaitEnabled("plugins-add-local")
                onNodeWithText("尚无已登记插件").assertExists()
            }
        }
    }

    @Test fun savedRegistrationReceiptSurvivesRefreshFailure() {
        PluginsPageTestSupport().use { fixture ->
            val repository = PluginsPageReadFaults(fixture.repository).apply { failAfterRegister = true }
            runSkikoComposeUiTest(size = Size(600f, 800f)) {
                setContent { HarnessApp(fixture.connection, initialDestination = HarnessDestination.PluginInventory,
                    installedPluginRepository = repository, connectionTransport = ConnectionTransport.Local) }
                pluginsPageAwaitEnabled("plugins-add-local")
                pluginsPageMouseClick("plugins-add-local")
                pluginsPageAwaitTag("plugin-local-directory")
                pluginsPageMouseClick("plugin-local-directory")
                onNodeWithTag("plugin-local-directory").performTextInput(fixture.source.toString())
                pluginsPageMouseClick("plugin-register-confirm")
                waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("plugin-saved-refresh-failure").fetchSemanticsNodes().isNotEmpty() }
                onNodeWithTag("plugin-mutation-receipt").assertTextEquals("登记已保存")
                onNodeWithText("尚无已登记插件").assertDoesNotExist()
                onNodeWithTag("plugins-add-local").assertIsNotEnabled()
                val plugin = runBlocking { fixture.service.read() }.plugins.single()
                assertFalse(plugin.enabled)
                runOnIdle { repository.failReads = false; repository.failAfterRegister = false }
                pluginsPageMouseClick("plugins-refresh")
                pluginsPageAwaitEnabled("plugins-add-local")
                onNodeWithTag("plugin-saved-refresh-failure").assertDoesNotExist()
                onNodeWithTag("installed-plugin-" + plugin.id).assertExists()
                assertEquals(1, runBlocking { fixture.service.read() }.plugins.size)
            }
        }
    }

    @Test fun unavailableAndReadOnlyConnectionsDisableManagement() {
        runSkikoComposeUiTest(size = Size(1440f, 900f)) {
            val connection = PluginsPageTestConnection("unavailable")
            setContent { HarnessApp(connection,
                initialDestination = HarnessDestination.PluginInventory) }
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("插件服务不可用").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithText("插件服务不可用").assertExists()
            onNodeWithTag("plugins-search").assertIsNotEnabled()
            onNodeWithTag("plugins-refresh").assertIsNotEnabled()
            onNodeWithTag("plugins-add-local").assertDoesNotExist()
        }
        listOf(ConnectionTransport.Local, ConnectionTransport.Relay).forEach { transport ->
            val account = if (transport == ConnectionTransport.Relay) pluginsPageRelayAccount() else null
            try {
                val relayIdentity = account?.state?.value?.session?.identity
                if (transport == ConnectionTransport.Relay) assertNotNull(relayIdentity)
                runSkikoComposeUiTest(size = Size(600f, 800f)) {
                    val delegate = PluginsPageFixtureRepository()
                    // Local exposes only the read interface; Relay also verifies the transport guard.
                    val repository: InstalledPluginRepository = if (transport == ConnectionTransport.Local)
                        object : InstalledPluginRepository {
                            override val canRead = true
                            override suspend fun listInstalled(scope: PluginScope) = delegate.listInstalled(scope)
                            override suspend fun readDetail(scope: PluginScope, id: PluginId) = delegate.readDetail(scope, id)
                        } else delegate
                    val connection = PluginsPageTestConnection("readonly")
                    setContent { HarnessApp(connection, account = account,
                        initialDestination = HarnessDestination.PluginInventory, installedPluginRepository = repository,
                        connectionTransport = transport) }
                    // Prove the injected read adapter was called before waiting for its successful-list controls.
                    waitUntil(timeoutMillis = 5_000) { delegate.listReads.isNotEmpty() }
                    assertEquals("readonly", delegate.listReads.last().hostId.value)
                    assertEquals(relayIdentity, account?.state?.value?.session?.identity)
                    assertEquals(relayIdentity?.let { PluginAccountId(it.toString()) }, delegate.listReads.last().accountId)
                    pluginsPageAwaitEnabled("plugins-search")
                    onNodeWithTag("plugins-add-local").assertDoesNotExist()
                    pluginsPageMouseClick("installed-plugin-readonly-0")
                    waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("plugin-detail-name").fetchSemanticsNodes().isNotEmpty() }
                    onNodeWithTag("plugin-set-enabled").assertDoesNotExist()
                    onNodeWithTag("plugin-remove-registration").assertDoesNotExist()
                    pluginsPageWheelTo("plugin-detail-list", "plugin-management-unavailable")
                    onNodeWithTag("plugin-management-unavailable").assertIsDisplayed().assertIsNotEnabled()
                }
            } finally {
                account?.let { runBlocking { it.shutdown() } }
            }
        }
    }

    @Test fun mouseWheelReachesListEndAndCompleteSkillAndAppValues() =
        runSkikoComposeUiTest(size = Size(600f, 800f)) {
            val repository = PluginsPageFixtureRepository(24)
            val connection = PluginsPageTestConnection("scroll")
            setContent { HarnessApp(connection,
                initialDestination = HarnessDestination.PluginInventory, installedPluginRepository = repository,
                connectionTransport = ConnectionTransport.Local) }
            pluginsPageAwaitEnabled("plugins-search")
            pluginsPageWheelTo("plugin-inventory-list", "installed-plugin-scroll-23")
            pluginsPageMouseClick("installed-plugin-scroll-23")
            pluginsPageAwaitEnabled("plugin-set-enabled")
            pluginsPageWheelTo("plugin-detail-list", "plugin-skill-0-expand")
            pluginsPageMouseClick("plugin-skill-0-expand")
            pluginsPageWheelTo("plugin-detail-list", "plugin-skill-0-description-reader")
            val description = onNodeWithTag("plugin-skill-0-description-reader", useUnmergedTree = true)
            description.performMouseInput { moveTo(center); scroll(240f) }
            waitForIdle()
            assertTrue(description.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value() > 0f)
            onNodeWithTag("plugin-skill-0-description", useUnmergedTree = true)
                .assertTextEquals(PluginsPageDisplayData.longDescription)
            pluginsPageWheelTo("plugin-detail-list", "plugin-skill-0-name", -200f)
            val skill = onNodeWithTag("plugin-skill-0-name", useUnmergedTree = true)
            skill.performMouseInput { moveTo(center); scroll(500f, ScrollWheel.Horizontal) }
            waitForIdle()
            assertTrue(skill.fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange].value() > 0f)
            skill.assertTextEquals(PluginsPageDisplayData.longSkillName)
            pluginsPageWheelTo("plugin-detail-list", "plugin-app-0-name")
            val app = onNodeWithTag("plugin-app-0-name", useUnmergedTree = true)
            app.performMouseInput { moveTo(center); scroll(500f, ScrollWheel.Horizontal) }
            waitForIdle()
            assertTrue(app.fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange].value() > 0f)
            app.assertTextEquals(PluginsPageDisplayData.longAppName)
        }
}

private fun ComposeUiTest.pluginsPageAwaitEnabled(tag: String) {
    waitUntil(timeoutMillis = 5_000) { onAllNodes(hasTestTag(tag) and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
    pluginsPageSettle()
}

/** Restores a Relay identity without network calls or a platform secure store. */
private fun pluginsPageRelayAccount(): MemberAccount {
    val session = AppSession(
        MemberIdentity(BackendTenant("https://plugins.example.invalid", 1), 1),
        "plugins-test-access-token", null, null,
    )
    return MemberAccount(object : MemberTransport {
        override suspend fun login(target: BackendTenant, mobile: String, password: String) = error("Unexpected account IO")
        override suspend fun refresh(session: AppSession) = error("Unexpected account IO")
        override suspend fun logout(session: AppSession) = error("Unexpected account IO")
        override suspend fun profile(session: AppSession) = error("Unexpected account IO")
        override suspend fun updateProfile(session: AppSession, request: im.hikaru.contracts.app.member.MemberProfileUpdateRequest) =
            error("Unexpected account IO")
        override suspend fun tenant(target: BackendTenant, website: String) = error("Unexpected account IO")
        override fun close() = Unit
    }, object : AppSessionStore {
        override val durable = false
        override fun read() = session
        override fun write(session: AppSession) = error("Unexpected account write")
        override fun clear() = error("Unexpected account clear")
    })
}

private fun ComposeUiTest.pluginsPageAwaitTag(tag: String) {
    waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    pluginsPageSettle()
}

private fun ComposeUiTest.pluginsPageAssertNavigator(pushed: Boolean) {
    onNodeWithTag("navigator-scrim").assertDoesNotExist()
    onNodeWithTag("navigator-overlay").assertDoesNotExist()
    if (pushed) onNodeWithTag("plugins-navigator").assertIsDisplayed()
    else onNodeWithTag("plugins-navigator").assertDoesNotExist()
}
