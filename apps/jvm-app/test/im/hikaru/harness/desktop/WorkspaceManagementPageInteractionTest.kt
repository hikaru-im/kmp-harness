package im.hikaru.harness.desktop

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import im.hikaru.contracts.app.member.*
import im.hikaru.contracts.app.system.AppTenantResponse
import im.hikaru.harness.client.account.*
import im.hikaru.harness.client.app.*
import im.hikaru.harness.client.connection.ConnectionTransport
import im.hikaru.harness.ui.foundation.*
import java.io.File
import kotlinx.coroutines.*
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class WorkspaceManagementPageInteractionTest {
    @Test
    fun tenantSelectionAndMaskedLoginRejectDuplicateSubmitsThenLogoutWorks() {
        val transport = WorkspaceAccountTransport()
        val account = MemberAccount(transport, MemoryAppSessionStore())
        var loggedIn = 0
        try {
            runSkikoComposeUiTest(size = Size(600f, 800f)) {
                setContent {
                    val state by account.state.collectAsState()
                    HarnessTheme { AccountWorkspacePage(account, state, HarnessConnectionState.Local, null, {}, { loggedIn++ }) }
                }
                onNodeWithTag("account-login").assertIsNotEnabled()
                onNodeWithTag("account-select-tenant").performClick()
                onNodeWithTag("account-tenant-confirm").performClick()
                onNodeWithTag("account-tenant-error").assertIsDisplayed()
                onNodeWithTag("account-tenant-url").performTextReplacement("https://example.test")
                onNodeWithTag("account-tenant-id").performTextInput("7")
                onNodeWithTag("account-tenant-confirm").performClick()
                waitUntil { account.state.value.target != null }
                onNodeWithTag("account-current-tenant").assertTextEquals("租户 7")
                onNodeWithTag("account-mobile").performTextInput("13800000000")
                onNodeWithTag("account-password").performTextInput("test-password")
                onNodeWithTag("account-password").assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Password))
                onNodeWithTag("account-login").performClick()
                waitUntil { transport.loginCalls == 1 }
                onNodeWithTag("account-login").assertIsNotEnabled().performMouseInput { click() }
                onNodeWithTag("account-select-tenant").assertIsNotEnabled()
                runOnIdle { transport.loginGate.complete(Unit) }
                waitUntil { loggedIn == 1 }
                onNodeWithTag("account-password").assertDoesNotExist()
                onNodeWithTag("account-user").assertTextEquals("用户 11")
                onNodeWithTag("account-logout").performScrollTo().performClick()
                waitUntil { account.state.value.session == null }
                onNodeWithTag("account-password").assertTextEquals("")
                assertEquals(1, transport.loginCalls)
            }
        } finally { transport.loginGate.complete(Unit); runBlocking { account.shutdown() } }
    }

    @Test
    fun changingTenantClearsDraftCredentialsAndLocalModeKeepsSettingsEntry() {
        val account = MemberAccount(WorkspaceAccountTransport(), MemoryAppSessionStore())
        try {
            runBlocking { account.selectTarget(BackendTenant("https://example.test", 7)) }
            runSkikoComposeUiTest(size = Size(420f, 800f)) {
                var settings = 0
                var local by mutableStateOf(false)
                setContent {
                    val state by account.state.collectAsState()
                    HarnessTheme { AccountWorkspacePage(if (local) null else account, state,
                        HarnessConnectionState.Local, null, { settings++ }, {}) }
                }
                onNodeWithTag("account-mobile").performTextInput("13800000000")
                onNodeWithTag("account-password").performTextInput("test-password")
                onNodeWithTag("account-select-tenant").performClick()
                onNodeWithTag("account-tenant-id").performTextReplacement("8")
                onNodeWithTag("account-tenant-confirm").performClick()
                waitUntil { account.state.value.target?.tenantId == 8L }
                onNodeWithTag("account-mobile").assertTextEquals("")
                onNodeWithTag("account-password").assertTextEquals("")
                runOnIdle { local = true }
                onNodeWithText("本地模式无需登录。").assertExists()
                onNodeWithTag("account-login").assertIsNotEnabled()
                onNodeWithTag("account-settings").performScrollTo().performClick()
                assertEquals(1, settings)
            }
        } finally { runBlocking { account.shutdown() } }
    }

    @Test
    fun captureWorkspacePagesAtBothWidthsAndThemes() {
        val directory = System.getenv("HARNESS_WORKSPACE_CAPTURE_DIR")?.let(::File) ?: return
        directory.mkdirs()
        for (size in listOf(Size(1440f, 900f), Size(600f, 800f))) {
            for (theme in listOf(HarnessThemeMode.Light, HarnessThemeMode.Dark)) {
                val suffix = "${size.width.toInt()}x${size.height.toInt()}-${theme.name.lowercase()}"
                for (count in listOf(0, 3)) runSkikoComposeUiTest(size = size) {
                    setContent { HarnessApp(PluginsPageTestConnection("workspace"),
                        initialDestination = HarnessDestination.PluginInventory,
                        installedPluginRepository = PluginsPageFixtureRepository(count),
                        connectionTransport = ConnectionTransport.Local, themeMode = theme) }
                    workspaceAwaitEnabled("plugins-search")
                    val surface = onNodeWithTag("main-content-column").getUnclippedBoundsInRoot()
                    val page = onNodeWithTag("installed-plugins-page").getUnclippedBoundsInRoot()
                    assertEquals(surface.left, page.left)
                    assertEquals(surface.right, page.right)
                    workspaceCapture(directory, "plugins-$count-$suffix")
                    if (count > 0) {
                        if (size.width == 1440f) onNodeWithTag("plugins-search-actions").assertDoesNotExist()
                        pluginsPageMouseClick("installed-plugin-workspace-0")
                        workspaceAwaitEnabled("plugin-set-enabled")
                        workspaceCapture(directory, "plugin-detail-$suffix")
                    }
                }
                runSkikoComposeUiTest(size = size) {
                    setContent { HarnessApp(null, initialDestination = HarnessDestination.Account, themeMode = theme) }
                    onNodeWithTag("account-card").assertIsDisplayed()
                    workspaceCapture(directory, "account-local-$suffix")
                }
                val account = MemberAccount(WorkspaceAccountTransport(), MemoryAppSessionStore())
                try {
                    runSkikoComposeUiTest(size = size) {
                        setContent { HarnessApp(null, account = account, initialDestination = HarnessDestination.Account, themeMode = theme) }
                        onNodeWithTag("account-select-tenant").assertIsDisplayed()
                        workspaceCapture(directory, "account-login-$suffix")
                        onNodeWithTag("account-select-tenant").performClick()
                        workspaceCapture(directory, "account-tenant-$suffix")
                    }
                } finally { runBlocking { account.shutdown() } }
            }
        }
    }
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.workspaceCapture(directory: File, name: String) {
    val target = if (name.startsWith("account-tenant-"))
        onNode(isRoot() and hasAnyDescendant(hasTestTag("account-tenant-url"))) else onRoot()
    val bitmap = target.captureToImage()
    Image.makeFromBitmap(bitmap.asSkiaBitmap()).use { image ->
        val bytes = image.encodeToData(EncodedImageFormat.PNG)?.use { it.bytes } ?: error("PNG encoding failed")
        File(directory, "$name.png").writeBytes(bytes)
    }
}

private class WorkspaceAccountTransport : MemberTransport {
    val loginGate = CompletableDeferred<Unit>()
    @Volatile var loginCalls = 0
    override suspend fun login(target: BackendTenant, mobile: String, password: String): MemberAuthLoginResponse {
        loginCalls++
        loginGate.await()
        return MemberAuthLoginResponse(userId = 11, accessToken = "test-token")
    }
    override suspend fun refresh(session: AppSession): MemberAuthLoginResponse = error("Unexpected refresh")
    override suspend fun logout(session: AppSession) = Unit
    override suspend fun profile(session: AppSession) = MemberProfileResponse(id = 11)
    override suspend fun updateProfile(session: AppSession, request: MemberProfileUpdateRequest) = true
    override suspend fun tenant(target: BackendTenant, website: String): AppTenantResponse? = null
    override fun close() = Unit
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.workspaceAwaitEnabled(tag: String) {
    waitUntil(timeoutMillis = 5_000) { onAllNodes(hasTestTag(tag) and isEnabled()).fetchSemanticsNodes().isNotEmpty() }
}