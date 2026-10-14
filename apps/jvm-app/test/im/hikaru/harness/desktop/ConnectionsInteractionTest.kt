package im.hikaru.harness.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import im.hikaru.contracts.common.ApiResult
import im.hikaru.contracts.harness.identity.HostDescription
import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.contracts.harness.protocol.Capability
import im.hikaru.contracts.harness.protocol.CapabilityId
import im.hikaru.contracts.harness.protocol.ProtocolVersion
import im.hikaru.harness.boot.DesktopProfile
import im.hikaru.harness.boot.HarnessHost
import im.hikaru.harness.client.account.BackendTenant
import im.hikaru.harness.client.account.MemberIdentity
import im.hikaru.harness.client.app.HarnessApp
import im.hikaru.harness.client.app.HarnessDestination
import im.hikaru.harness.client.connection.*
import im.hikaru.harness.client.page.SettingsConnectionsPage
import im.hikaru.harness.desktop.connection.LocalConnection
import im.hikaru.harness.ui.foundation.*
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import kotlin.test.*

/** Offline fixtures only: no Desktop bundle, credential store, HTTP transport or real account. */
@OptIn(ExperimentalTestApi::class)
class ConnectionsInteractionTest {
    @Test
    fun localDescribeRefreshAndHostsRouteShareTheRealOwnerData() = withLocalHost { description, connection ->
        runSkikoComposeUiTest(size = Size(1440f, 900f)) {
            setContent {
                HarnessApp(connection, initialDestination = HarnessDestination.Settings, themeMode = HarnessThemeMode.Light)
            }
            onNodeWithTag("settings-row-connections").clickWithMouse()
            onNodeWithTag("settings-row-connections").assertIsSelected()
            onNodeWithTag("settings-content-connections").assertIsDisplayed()
            onNodeWithTag(hostTag(description)).performScrollTo().assertExists()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "当前 Host · 在线"))
            runOnIdle { assertEquals(1, connection.describeCalls.get()); assertSame(description, connection.lastDescription) }
            onNodeWithTag("connections-refresh").performScrollTo().clickWithMouse()
            runOnIdle { assertEquals(2, connection.describeCalls.get()); assertSame(description, connection.lastDescription) }
            onNodeWithTag(detailsTag(description)).performScrollTo().clickWithMouse()
            dialogText(description.hostId.value).assertIsDisplayed()
            dialogText(protocolLabel(description)).assertIsDisplayed()
            onNodeWithTag("connections-details-close").clickWithMouse()

            // Hosts is no longer a page: the connection list lives in Settings > Connections.
            onNodeWithTag("settings-connections-title").assertIsDisplayed()
            onNodeWithTag("hosts-navigator").assertDoesNotExist()
            onNodeWithTag(hostTag(description)).performScrollTo()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "当前 Host · 在线"))
            onNodeWithText(description.displayName).assertExists()
            runOnIdle { assertEquals(2, connection.describeCalls.get()) }
            onNodeWithTag("connections-refresh").performScrollTo().clickWithMouse()
            runOnIdle { assertEquals(3, connection.describeCalls.get()); assertSame(description, connection.lastDescription) }
            onNodeWithTag("rail-account").clickWithMouse()
            onNodeWithTag("account-menu-settings").clickWithMouse()
            onNodeWithTag("settings-row-connections").clickWithMouse()
            onNodeWithTag(hostTag(description)).assertExists()
            runOnIdle { assertEquals(3, connection.describeCalls.get()) }
        }
    }

    @Test
    fun missingConnectionAndSshWslAdaptersStayDisabledWithoutInventedHosts() = runComposeUiTest {
        setContent { HarnessApp(null, initialDestination = HarnessDestination.Settings, themeMode = HarnessThemeMode.Light) }
        onNodeWithTag("settings-row-connections").performClick()
        onNodeWithTag("connections-refresh").performScrollTo().assertIsNotEnabled()
        onNodeWithText("当前没有可刷新的连接服务。").assertExists()
        onNodeWithText("尚无当前 Host 信息。").assertExists()
        onNodeWithText("暂无已发现的 Host").performScrollTo().assertIsDisplayed()
        onAllNodes(SemanticsMatcher("a host supplied by describe/discovery") {
            it.config.getOrElse(SemanticsProperties.TestTag) { "" }.startsWith("connections-host-")
        }).assertCountEquals(0)
        onNodeWithTag("connections-ssh-add").performScrollTo().assertIsNotEnabled().performMouseInput { click() }
        onNodeWithTag("connections-wsl-connect").performScrollTo().assertIsNotEnabled().performMouseInput { click() }
        onNodeWithText("尚未接入 SSH 连接适配器，暂不可发现、添加或连接 SSH Host。").assertExists()
        onNodeWithText("尚未接入 WSL 连接适配器，暂不可发现或连接 WSL Host。").assertExists()
        onNodeWithTag("connections-details-dialog").assertDoesNotExist()
    }

    @Test
    fun relayDiscoveryIsNotOnlineAndConnectingGuardsRepeatedSelection() = runComposeUiTest {
        val current = fixtureHost("current")
        val candidate = fixtureHost("candidate")
        var state by mutableStateOf(relayState(current, listOf(current, candidate)))
        val selections = mutableListOf<HostDescription>()
        var refreshes = 0
        setContent {
            ConnectionsContent(state, onRefresh = { refreshes++ }, onSelect = {
                selections += it
                state = state.copy(phase = ConnectionsPhase.Connecting)
            })
        }
        onNodeWithTag(hostTag(candidate)).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "已发现 · 不代表在线"),
        )
        onNodeWithText("发现结果不代表已建立连接。切换 Host 会丢弃旧 Host 的事件订阅，后续会话操作将使用所选 Host。").assertExists()
        onNodeWithTag(selectTag(current)).assertTextEquals("已连接").assertIsNotEnabled()
        onNodeWithTag(selectTag(candidate)).performScrollTo().assertTextEquals("连接").clickWithMouse()
        runOnIdle { assertEquals(listOf(candidate), selections) }
        onNodeWithTag(hostTag(current)).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "当前 Host · 连接中"),
        )
        listOf(ConnectionsPhase.Discovering, ConnectionsPhase.Connecting, ConnectionsPhase.Reconnecting).forEach { phase ->
            runOnIdle { state = state.copy(phase = phase) }
            listOf(current, candidate).forEach { host ->
                onNodeWithTag(selectTag(host)).performScrollTo().assertIsNotEnabled().performMouseInput { click() }
            }
            runOnIdle { assertEquals(listOf(candidate), selections) }
        }
        runOnIdle { state = state.copy(phase = ConnectionsPhase.Online, refreshing = true) }
        onNodeWithTag(selectTag(candidate)).assertIsNotEnabled().performMouseInput { click() }
        onNodeWithTag("connections-refresh").performScrollTo().assertIsNotEnabled().performMouseInput { click() }
        runOnIdle { assertEquals(0, refreshes); assertEquals(listOf(candidate), selections) }
        runOnIdle { state = state.copy(currentHost = candidate, refreshing = false) }
        onNodeWithTag(selectTag(candidate)).performScrollTo().assertTextEquals("已连接").assertIsNotEnabled()
        onNodeWithTag(selectTag(current)).assertTextEquals("连接").assertIsEnabled()
    }

    @Test
    fun discoveredDetailsFollowTheLatestDtoAndCloseWhenCandidateDisappears() = runComposeUiTest {
        val current = fixtureHost("current")
        val candidate = fixtureHost("candidate")
        var state by mutableStateOf(relayState(current, listOf(candidate)))
        setContent { ConnectionsContent(state) }
        onNodeWithTag(detailsTag(candidate)).performScrollTo().performClick()
        dialogText(candidate.displayName).assertIsDisplayed()
        val updated = candidate.copy(
            displayName = "Updated discovery workstation", protocolVersion = ProtocolVersion(1, 7),
            capabilities = listOf(Capability(CapabilityId("fixture.updated"), 3)),
        )
        runOnIdle { state = state.copy(hosts = listOf(updated)) }
        dialogText(updated.displayName).assertIsDisplayed()
        dialogText("1.7").assertIsDisplayed()
        dialogText("fixture.updated · v3").performScrollTo().assertIsDisplayed()
        dialogText(candidate.displayName).assertDoesNotExist()
        runOnIdle { state = state.copy(hosts = emptyList()) }
        onNodeWithTag("connections-details-dialog").assertDoesNotExist()
        runOnIdle { state = state.copy(hosts = listOf(updated)) }
        onNodeWithTag(detailsTag(updated)).assertExists()
        onNodeWithTag("connections-details-dialog").assertDoesNotExist()
    }

    @Test
    fun currentDetailsFollowDescribeAndCloseOnCurrentHostLossOrReplacement() = runComposeUiTest {
        val current = fixtureHost("current")
        var state by mutableStateOf(relayState(current, emptyList()))
        setContent { ConnectionsContent(state) }
        onNodeWithTag(detailsTag(current)).performScrollTo().performClick()
        val updated = current.copy(displayName = "Updated current workstation", protocolVersion = ProtocolVersion(1, 4))
        runOnIdle { state = state.copy(currentHost = updated) }
        dialogText(updated.displayName).assertIsDisplayed()
        dialogText("1.4").assertIsDisplayed()
        dialogText(current.displayName).assertDoesNotExist()
        runOnIdle { state = state.copy(currentHost = null, phase = ConnectionsPhase.Disconnected) }
        onNodeWithTag("connections-details-dialog").assertDoesNotExist()
        runOnIdle { state = state.copy(currentHost = updated, phase = ConnectionsPhase.Online) }
        onNodeWithTag("connections-details-dialog").assertDoesNotExist()
        onNodeWithTag(detailsTag(updated)).performScrollTo().performClick()
        runOnIdle { state = state.copy(currentHost = fixtureHost("replacement")) }
        onNodeWithTag("connections-details-dialog").assertDoesNotExist()
    }

    @Test
    fun accountScopeChangeAndLogoutCloseDetailsEvenWhenHostIdIsReused() = runComposeUiTest {
        val candidate = fixtureHost("same-host-in-two-accounts")
        var state by mutableStateOf(relayState(null, listOf(candidate)))
        setContent { ConnectionsContent(state) }
        onNodeWithTag(detailsTag(candidate)).performScrollTo().performClick()
        // Preserve the entire DTO list: closure must be caused by scope, not host removal.
        runOnIdle { state = state.copy(scope = fixtureIdentity(userId = 2)) }
        onNodeWithTag("connections-details-dialog").assertDoesNotExist()
        onNodeWithTag(detailsTag(candidate)).performScrollTo().performClick()
        runOnIdle { state = state.copy(scope = fixtureIdentity(userId = 2, tenantId = 2)) }
        onNodeWithTag("connections-details-dialog").assertDoesNotExist()
        onNodeWithTag(detailsTag(candidate)).performScrollTo().performClick()
        runOnIdle { state = state.copy(scope = null, phase = ConnectionsPhase.RequiresLogin) }
        onNodeWithTag("connections-details-dialog").assertDoesNotExist()
    }

    @Test
    fun localReadFailureRetriesAndInFlightDescribeRejectsDuplicateOperations() = withLocalHost { description, connection ->
        val gate = CompletableDeferred<Unit>()
        connection.failuresRemaining = 1
        try {
            runComposeUiTest {
                setContent { HarnessApp(connection, initialDestination = HarnessDestination.Settings) }
                onNodeWithTag("settings-row-connections").performClick()
                onNodeWithText("无法读取 Host 信息，请重试。").performScrollTo().assertIsDisplayed()
                onNodeWithTag(hostTag(description)).assertDoesNotExist()
                onNodeWithTag("connections-retry").assertIsEnabled()
                runOnIdle { assertEquals(1, connection.describeCalls.get()); connection.describeGate = gate }
                onNodeWithTag("connections-retry").performClick()
                waitUntil { connection.describeCalls.get() == 2 }
                onNodeWithTag("connections-retry").assertDoesNotExist()
                onNodeWithTag("connections-refresh").performScrollTo().assertIsNotEnabled()
                    .performMouseInput { click(); click() }
                runOnIdle { assertEquals(2, connection.describeCalls.get()); gate.complete(Unit) }
                onNodeWithTag(hostTag(description)).performScrollTo().assertExists()
                onNodeWithText("无法读取 Host 信息，请重试。").assertDoesNotExist()
                onNodeWithTag("connections-refresh").performScrollTo().assertIsEnabled()
                runOnIdle { assertEquals(2, connection.describeCalls.get()); assertSame(description, connection.lastDescription) }
            }
        } finally { gate.complete(Unit) }
    }

    @Test
    fun repositoryGuardRejectsTwoRefreshActionsBeforeRecomposition() = withLocalHost { description, connection ->
        val repository = ConnectionDescriptionRepository(connection)
        runBlocking { repository.refresh() }
        val gate = CompletableDeferred<Unit>()
        connection.describeGate = gate
        try {
            runComposeUiTest {
                setContent {
                    val owner by repository.state.collectAsState()
                    val scope = rememberCoroutineScope()
                    ConnectionsContent(ConnectionsState(
                        phase = owner.phase, currentHost = owner.host, hosts = listOfNotNull(owner.host),
                        refreshing = owner.refreshing, error = owner.error, refreshUnavailableReason = null,
                    ), onRefresh = { scope.launch { repository.refresh() } })
                }
                // Invoke the same enabled action twice without an intervening test idle/recomposition.
                onNodeWithTag("connections-refresh").performSemanticsAction(SemanticsActions.OnClick) { action ->
                    action(); action()
                }
                waitUntil { connection.describeCalls.get() >= 2 }
                onNodeWithTag("connections-refresh").assertIsNotEnabled()
                runOnIdle { assertEquals(2, connection.describeCalls.get()); gate.complete(Unit) }
                onNodeWithTag("connections-refresh").assertIsEnabled()
                runOnIdle {
                    assertEquals(2, connection.describeCalls.get())
                    assertSame(description, repository.state.value.host)
                    assertEquals(ConnectionsPhase.Online, repository.state.value.phase)
                }
            }
        } finally { gate.complete(Unit) }
    }

    @Test
    fun relayErrorRetryClearsOnlyAfterOwnerSuccessAndGuardsRefreshAndSelection() = runComposeUiTest {
        val candidate = fixtureHost("candidate")
        var state by mutableStateOf(relayState(null, listOf(candidate)).copy(error = "无法刷新连接，请重试。"))
        var refreshes = 0
        var selections = 0
        setContent {
            ConnectionsContent(state, onRefresh = {
                refreshes++
                state = state.copy(refreshing = true)
            }, onSelect = { selections++ })
        }
        onNodeWithTag("connections-retry").performScrollTo().clickWithMouse()
        onNodeWithTag("connections-retry").assertIsNotEnabled().performMouseInput { click(); click() }
        onNodeWithText("无法刷新连接，请重试。").assertExists()
        onNodeWithTag(selectTag(candidate)).performScrollTo().assertIsNotEnabled().performMouseInput { click() }
        onNodeWithTag("connections-refresh").performScrollTo().assertIsNotEnabled().performMouseInput { click() }
        runOnIdle { assertEquals(1, refreshes); assertEquals(0, selections) }
        runOnIdle { state = state.copy(refreshing = false, error = null) }
        onNodeWithTag("connections-retry").assertDoesNotExist()
        onNodeWithTag("connections-refresh").assertIsEnabled()
        onNodeWithTag(selectTag(candidate)).performScrollTo().assertIsEnabled()
        runOnIdle { state = state.copy(refreshUnavailableReason = "当前未接入发现适配器。", selectionUnavailableReason = "当前未接入远程 Host 连接服务。", error = "无法刷新连接，请重试。") }
        onNodeWithTag("connections-retry").performScrollTo().assertIsNotEnabled().performMouseInput { click() }
        onNodeWithTag(selectTag(candidate)).performScrollTo().assertIsNotEnabled().performMouseInput { click() }
        runOnIdle { assertEquals(1, refreshes); assertEquals(0, selections) }
    }

    @Test
    fun captureLocalSettingsAndHostsInLightAndDarkAt420600And1440() = withLocalHost { description, connection ->
        captureCases { suffix, mode, size ->
            runSkikoComposeUiTest(size = size) {
                setContent { HarnessApp(connection, initialDestination = HarnessDestination.Settings, themeMode = mode) }
                onNodeWithTag("settings-row-connections").performClick()
                if (onAllNodesWithTag("navigator-overlay").fetchSemanticsNodes().isNotEmpty()) {
                    onNodeWithContentDescription("Toggle sidebar").performClick()
                }
                // The Settings content column is shifted 8px left of the 728px page box to match
                // Settings > General, so the page contents are measured against the wider main surface.
                val page = onNodeWithTag("main-surface")
                onNodeWithTag("settings-connections-title").assertFits(page)
                onNodeWithTag("connections-refresh").assertFits(page)
                capture(onRoot().captureToImage(), "local-settings-top-$suffix.png")
                onNodeWithTag(hostTag(description)).performScrollTo().assertFits(page)
                onNodeWithText(description.displayName).assertTextDoesNotOverflow()
                onNodeWithTag(detailsTag(description)).assertFits(page).clickWithMouse()
                val dialog = onNode(isDialog())
                onNodeWithTag("connections-details-dialog").assertFits(dialog)
                dialogText(description.displayName).assertIsDisplayed().assertTextDoesNotOverflow()
                onNodeWithTag("connections-details-close").assertFits(dialog)
                capture(dialog.captureToImage(), "local-details-$suffix.png")
                onNodeWithTag("connections-details-close").clickWithMouse()
                onNodeWithTag("connections-wsl-connect").performScrollTo().assertIsNotEnabled().assertFits(page)
                onNodeWithTag("connections-ssh-add").assertIsNotEnabled().assertFits(page)
                capture(onRoot().captureToImage(), "local-settings-adapters-$suffix.png")
                // Already on Settings > Connections; nothing to navigate to for the hosts capture.
                if (onAllNodesWithTag("navigator-overlay").fetchSemanticsNodes().isNotEmpty()) {
                    onNodeWithContentDescription("Toggle sidebar").performClick()
                }
                onNodeWithTag("settings-connections-title").performScrollTo().assertFits(page)
                onNodeWithTag(hostTag(description)).assertExists()
                capture(onRoot().captureToImage(), "local-hosts-top-$suffix.png")
            }
        }
    }

    @Test
    fun captureRelayCandidatesConnectingErrorAndScrollableDetailsWithoutClipping() {
        val current = fixtureHost("current")
        val candidate = fixtureHost("candidate").copy(
            displayName = "Connections fixture workstation with a long name for compact wrapping",
            capabilities = (1..18).map { Capability(CapabilityId("fixture.capability.$it"), it) },
        )
        captureCases { suffix, mode, size ->
            runSkikoComposeUiTest(size = size) {
                var state by mutableStateOf(relayState(current, listOf(current, candidate)))
                setContent { ConnectionsContent(state, mode) }
                val page = onNodeWithTag("settings-connections-page")
                onNodeWithTag("settings-connections-title").assertFits(page)
                capture(onRoot().captureToImage(), "relay-top-$suffix.png")
                onNodeWithTag(hostTag(candidate)).performScrollTo().assertFits(page)
                onNodeWithText(candidate.displayName).assertTextDoesNotOverflow()
                onNodeWithTag(selectTag(candidate)).assertIsEnabled().assertFits(page)
                onNodeWithTag(detailsTag(candidate)).assertFits(page)
                capture(onRoot().captureToImage(), "relay-candidate-$suffix.png")
                onNodeWithTag(detailsTag(candidate)).clickWithMouse()
                val dialog = onNode(isDialog())
                onNodeWithTag("connections-details-dialog").assertFits(dialog)
                dialogText(candidate.displayName).assertIsDisplayed().assertTextDoesNotOverflow()
                onNodeWithTag("connections-details-close").assertFits(dialog)
                capture(dialog.captureToImage(), "relay-details-top-$suffix.png")
                dialogText("协议能力声明不代表当前用户的使用权限，也不保证服务当前可用。").performScrollTo().assertFits(dialog)
                onNodeWithTag("connections-details-close").assertFits(dialog)
                capture(dialog.captureToImage(), "relay-details-bottom-$suffix.png")
                onNodeWithTag("connections-details-close").clickWithMouse()
                runOnIdle { state = state.copy(phase = ConnectionsPhase.Connecting) }
                onNodeWithTag(selectTag(candidate)).performScrollTo().assertIsNotEnabled().assertFits(page)
                capture(onRoot().captureToImage(), "relay-connecting-$suffix.png")
                runOnIdle { state = state.copy(phase = ConnectionsPhase.Disconnected, error = "无法刷新连接，请重试。") }
                onNodeWithTag("connections-retry").performScrollTo().assertIsEnabled().assertFits(page)
                capture(onRoot().captureToImage(), "relay-error-$suffix.png")
            }
        }
    }

    @Composable
    private fun ConnectionsContent(
        state: ConnectionsState, mode: HarnessThemeMode = HarnessThemeMode.Light,
        onRefresh: () -> Unit = {}, onSelect: (HostDescription) -> Unit = {},
    ) {
        HarnessTheme(mode) {
            Box(Modifier.fillMaxSize().background(HarnessTokens.Colors.Canvas)) {
                SettingsConnectionsPage(state, onRefresh, onSelect)
            }
        }
    }

    private fun SemanticsNodeInteractionsProvider.dialogText(text: String): SemanticsNodeInteraction =
        onNode(hasText(text) and hasAnyAncestor(hasTestTag("connections-details-dialog")))

    private fun SemanticsNodeInteraction.clickWithMouse() {
        assertIsDisplayed().assertIsEnabled().performMouseInput { click() }
    }

    /** Compare untrimmed geometry, not only assertIsDisplayed (which accepts partial visibility). */
    private fun SemanticsNodeInteraction.assertFits(viewport: SemanticsNodeInteraction): SemanticsNodeInteraction {
        assertIsDisplayed()
        val bounds = getUnclippedBoundsInRoot()
        val frame = viewport.getUnclippedBoundsInRoot()
        val tolerance = 0.5f
        assertTrue(bounds.left.value >= frame.left.value - tolerance, "left clipped: $bounds vs $frame")
        assertTrue(bounds.right.value <= frame.right.value + tolerance, "right clipped: $bounds vs $frame")
        assertTrue(bounds.top.value >= frame.top.value - tolerance, "top clipped: $bounds vs $frame")
        assertTrue(bounds.bottom.value <= frame.bottom.value + tolerance, "bottom clipped: $bounds vs $frame")
        assertTrue(bounds.right > bounds.left && bounds.bottom > bounds.top, "empty bounds: $bounds")
        return this
    }

    private fun SemanticsNodeInteraction.assertTextDoesNotOverflow() {
        val layouts = mutableListOf<TextLayoutResult>()
        performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.isNotEmpty(), "expected a measured text layout")
        layouts.forEach { layout ->
            // Paragraph width is the incoming constraint, not the occupied line bounds.
            // Compare horizontal line bounds and the paragraph's measured height. Line top/bottom
            // include font leading that Compose may trim outside the first/last line.
            val tolerance = 0.5f
            val diagnostic = "text clipped or ellipsized: ${layout.layoutInput.text}; size=${layout.size}; paragraph=${layout.multiParagraph.width} x ${layout.multiParagraph.height}"
            assertFalse(layout.multiParagraph.didExceedMaxLines, diagnostic)
            assertTrue(layout.lineCount > 0, diagnostic)
            assertTrue(layout.multiParagraph.height <= layout.size.height + tolerance, diagnostic)
            repeat(layout.lineCount) { line ->
                assertFalse(layout.isLineEllipsized(line), diagnostic)
                assertTrue(layout.getLineLeft(line) >= -tolerance, diagnostic)
                assertTrue(layout.getLineRight(line) <= layout.size.width + tolerance, diagnostic)
            }
            assertEquals(layout.layoutInput.text.length, layout.getLineEnd(layout.lineCount - 1), diagnostic)
        }
    }

    private fun captureCases(block: (String, HarnessThemeMode, Size) -> Unit) {
        listOf("light" to HarnessThemeMode.Light, "dark" to HarnessThemeMode.Dark).forEach { (slug, mode) ->
            listOf(Size(420f, 800f), Size(600f, 800f), Size(1440f, 900f)).forEach { size ->
                block("$slug-${size.width.toInt()}x${size.height.toInt()}", mode, size)
            }
        }
    }

    private fun capture(bitmap: ImageBitmap, name: String) {
        val root = File("/Volumes/Data/IdeaProjects/kmp-harness/docs/desktop-ui/captures/connections")
        check(root.isDirectory || root.mkdirs()) { "Cannot create connections capture directory" }
        Image.makeFromBitmap(bitmap.asSkiaBitmap()).use { image ->
            val file = File(root, name)
            file.writeBytes(requireNotNull(image.encodeToData(EncodedImageFormat.PNG)).bytes)
            assertTrue(file.length() > 0, "empty screenshot: $file")
        }
    }

    private fun withLocalHost(block: (HostDescription, ObservedLocalConnection) -> Unit) {
        val description = fixtureHost("local")
        // Empty catalog starts the real Host API without loading filesystem/account/provider plugins.
        val host = runBlocking { HarnessHost.start(DesktopProfile(hostDescription = description)) }
        try { block(description, ObservedLocalConnection(LocalConnection(host.gateway))) }
        finally { runBlocking { host.close() } }
    }

    private fun fixtureHost(id: String) = HostDescription(
        protocolVersion = ProtocolVersion.Current, hostId = HostId("connections-ui-$id"),
        displayName = "Connections fixture $id workstation",
        capabilities = listOf(Capability(CapabilityId("fixture.describe"), 1)),
    )

    private fun fixtureIdentity(userId: Long = 1, tenantId: Long = 1) =
        MemberIdentity(BackendTenant("https://connections-ui.invalid", tenantId), userId)

    private fun relayState(current: HostDescription?, hosts: List<HostDescription>) = ConnectionsState(
        scope = fixtureIdentity(), transport = ConnectionTransport.Relay,
        phase = if (current == null) ConnectionsPhase.Disconnected else ConnectionsPhase.Online,
        currentHost = current, hosts = hosts, refreshUnavailableReason = null, selectionUnavailableReason = null,
    )

    private fun hostTag(host: HostDescription) = "connections-host-${host.hostId.value}"
    private fun detailsTag(host: HostDescription) = "connections-details-${host.hostId.value}"
    private fun selectTag(host: HostDescription) = "connections-select-${host.hostId.value}"
    private fun protocolLabel(host: HostDescription) = "${host.protocolVersion.major}.${host.protocolVersion.minor}"

    /** Observe/defer the real describe call; successful responses still come from LocalConnection. */
    private class ObservedLocalConnection(private val local: LocalConnection) : Connection by local {
        val describeCalls = AtomicInteger()
        var failuresRemaining = 0
        var describeGate: CompletableDeferred<Unit>? = null
        var lastDescription: HostDescription? = null
        override val host: HostApi = object : HostApi {
            override suspend fun describe(): ApiResult<HostDescription> {
                describeCalls.incrementAndGet()
                describeGate?.await()
                if (failuresRemaining > 0) {
                    failuresRemaining--
                    return ApiResult(code = 500, msg = "offline fixture describe failure")
                }
                return local.host.describe().also { lastDescription = it.data }
            }
        }
    }
}
