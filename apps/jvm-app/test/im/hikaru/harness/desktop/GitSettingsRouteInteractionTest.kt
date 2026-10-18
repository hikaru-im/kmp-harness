package im.hikaru.harness.desktop

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import im.hikaru.contracts.common.ApiResult
import im.hikaru.contracts.harness.identity.HostDescription
import im.hikaru.contracts.harness.protocol.ProtocolVersion
import im.hikaru.harness.api.gateway.ApiGateway
import im.hikaru.harness.api.gateway.host.HostDescribeEndpoint
import im.hikaru.harness.client.app.HarnessApp
import im.hikaru.harness.client.app.HarnessDestination
import im.hikaru.harness.client.connection.*
import im.hikaru.harness.client.git.*
import im.hikaru.harness.client.settings.LocalShortcutsRepository
import im.hikaru.harness.client.settings.MemoryShortcutsStore
import im.hikaru.harness.client.settings.ShortcutPlatform
import im.hikaru.harness.client.settings.ShortcutsRepository
import im.hikaru.harness.desktop.connection.LocalConnection
import im.hikaru.harness.ui.foundation.HarnessThemeMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicReference
import java.io.File
import org.jetbrains.skia.Image
import org.jetbrains.skia.EncodedImageFormat
import kotlin.test.*

/** Complete production route and app-owned GitController; only existing service seams are adapters. */
@OptIn(ExperimentalTestApi::class)
class GitSettingsRouteInteractionTest {
    @Test
    fun captureIntegratedGitAndWorktreesForReview() {
        val directory = System.getenv("HARNESS_GIT_SETTINGS_REVIEW_DIR")?.takeIf { it.isNotBlank() }?.let(::File) ?: return
        directory.mkdirs()
        for ((width, height) in listOf(1440 to 900, 600 to 800)) {
            for (theme in listOf(HarnessThemeMode.Light, HarnessThemeMode.Dark)) {
                val fixture = GitRouteFixture()
                runSkikoComposeUiTest(size = Size(width.toFloat(), height.toFloat())) {
                    setGitRouteContent(fixture) {
                        HarnessApp(fixture.connection, initialDestination = HarnessDestination.Settings,
                            connectionTransport = ConnectionTransport.Local, gitBackend = fixture.backend,
                            gitInitialCwd = GitWorktreesPageFixture.root, themeMode = theme)
                    }
                    enterGit()
                    fun save(page: String) {
                        settleGitLayout()
                        val bitmap = onRoot().captureToImage()
                        Image.makeFromBitmap(bitmap.asSkiaBitmap()).use { image ->
                            val bytes = image.encodeToData(EncodedImageFormat.PNG)?.use { it.bytes } ?: error("PNG encoding failed")
                            File(directory, "$page-$width-$height-${theme.name.lowercase()}.png").writeBytes(bytes)
                        }
                    }
                    onNodeWithTag("git-repository-path").assertDoesNotExist()
                    onAllNodesWithText(GitWorktreesPageFixture.root).assertCountEquals(1)
                    save("git")
                    selectWorktrees()
                    awaitRepository(GitWorktreesPageFixture.root)
                    onNodeWithTag("git-repository-path").assertDoesNotExist()
                    onAllNodesWithTag("git-repository-root").assertCountEquals(1)
                    save("worktrees")
                }
            }
        }
    }

    @Test
    fun mouseSettingsAndSessionDirectorySelectionEnterGitAndWorktreesWithActualRepository() {
        for ((width, height) in listOf(1440 to 900, 600 to 800)) {
            val f = GitRouteFixture()
            runSkikoComposeUiTest(size = Size(width.toFloat(), height.toFloat())) {
                setGitRouteContent(f) { HarnessApp(f.connection, initialDestination = HarnessDestination.Settings,
                    connectionTransport = ConnectionTransport.Local, gitBackend = f.backend,
                    gitInitialCwd = GitWorktreesPageFixture.root, themeMode = HarnessThemeMode.Light) }
                if (width == 600) {
                    onNodeWithTag("navigator-scrim").assertIsDisplayed()
                    onNodeWithTag("navigator-overlay").assertIsDisplayed()
                }
                enterGit()
                onNodeWithText("会话目录 / 本机初始目录").assertDoesNotExist()
                onNodeWithTag("git-repository-path").assertDoesNotExist()
                onAllNodesWithText(GitWorktreesPageFixture.root).assertCountEquals(1)
                if (width == 600) {
                    onNodeWithTag("settings-navigator").assertDoesNotExist()
                } else {
                    onNodeWithTag("settings-navigator").assertIsDisplayed()
                    onNodeWithTag("settings-row-git").assertIsSelected()
                }
                openDirectoryMenu(f)
                val menu = onNodeWithTag("git-session-cwd-menu").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                assertTrue(menu.width in 1f..width.toFloat())
                assertTrue(menu.height in 1f..321f)
                clickGitNode(scrollGitNodeIntoView(directoryOption(GitWorktreesPageFixture.longRoot)))
                awaitRepository(GitWorktreesPageFixture.longRoot)
                awaitEnabledPicker()
                assertEquals(GitWorktreesPageFixture.longRoot, f.reads.discoveries.last().second)
                assertEquals(GitWorktreesPageFixture.longRoot, f.reads.statusReads.last().root)
                assertEquals(GitWorktreesPageFixture.binding.hostId, f.reads.statusReads.last().binding.hostId)
                onNodeWithTag("git-repository-root").assertTextEquals(GitWorktreesPageFixture.longRoot)
                val picker = onNodeWithTag("git-session-cwd").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
                assertTrue(picker.height in 36f..100f, "a long cwd must not expand the compact directory control")
                assertTrue(picker.left >= 0f && picker.right <= width.toFloat())
                scrollGitNodeIntoView(onNodeWithTag("git-diff-source"))
                assertNotEquals(picker, onNodeWithTag("git-session-cwd").fetchSemanticsNode().boundsInRoot,
                    "directory selection now belongs to the page's repository card")
                selectWorktrees()
                if (width == 600) {
                    onNodeWithTag("settings-navigator").assertDoesNotExist()
                } else {
                    onNodeWithTag("settings-navigator").assertIsDisplayed()
                    onNodeWithTag("settings-row-worktrees").assertIsSelected()
                }
                onNodeWithTag("settings-content-worktrees").assertIsDisplayed()
                onNodeWithTag("worktrees-page").assertIsDisplayed()
                awaitRepository(GitWorktreesPageFixture.longRoot)
                assertTrue(f.reads.creations.isEmpty())
                assertTrue(f.reads.switches.isEmpty())
            }
        }
    }

    @Test
    fun sessionPickerRejectsMouseAndRetainedOptionDuringDiscoveryAndDataReads() {
        val f = GitRouteFixture()
        val discovery = CompletableDeferred<Unit>()
        val reads = CompletableDeferred<Unit>()
        try {
            runSkikoComposeUiTest(size = Size(600f, 800f)) {
                setGitRouteContent(f) { HarnessApp(f.connection, initialDestination = HarnessDestination.Settings,
                    connectionTransport = ConnectionTransport.Local, gitBackend = f.backend,
                    gitInitialCwd = GitWorktreesPageFixture.root) }
                enterGit()
                onNodeWithText("会话目录 / 本机初始目录").assertDoesNotExist()
                onNodeWithTag("git-repository-path").assertDoesNotExist()
                onAllNodesWithText(GitWorktreesPageFixture.root).assertCountEquals(1)
                val oldOption = retainClearOption(f)
                runOnIdle { f.reads.discoveryGate = discovery; f.reads.readGate = reads }
                openDirectoryMenu(f)
                clickGitNode(scrollGitNodeIntoView(directoryOption(GitWorktreesPageFixture.longRoot)))
                awaitDisabledPicker()
                val before = f.reads.discoveries.size
                assertDisabledPickerRejectsMouse()
                runOnIdle { oldOption() }
                assertEquals(before, f.reads.discoveries.size)
                runOnIdle { discovery.complete(Unit) }
                waitUntil { onAllNodesWithTag("git-repository-root").fetchSemanticsNodes().isNotEmpty() }
                // Repository ready, but status/refs/worktrees/diff/index reads still gated.
                assertDisabledPickerRejectsMouse()
                runOnIdle { oldOption() }
                assertEquals(before, f.reads.discoveries.size)
                assertSelectedDirectory(GitWorktreesPageFixture.longRoot)
                runOnIdle { reads.complete(Unit) }
                awaitEnabledPicker()
                // Refresh a ready repository separately: retained current-selection callbacks must
                // also be rejected while only its data reads (not discovery) are pending.
                val currentOption = retainClearOption(f)
                val refreshReads = CompletableDeferred<Unit>()
                runOnIdle { f.reads.readGate = refreshReads }
                try {
                    clickGitNode(scrollGitNodeIntoView(onNodeWithTag("git-refresh")))
                    awaitDisabledPicker()
                    assertDisabledPickerRejectsMouse()
                    runOnIdle { currentOption() }
                    assertSelectedDirectory(GitWorktreesPageFixture.longRoot)
                    assertEquals(before, f.reads.discoveries.size)
                    runOnIdle { refreshReads.complete(Unit) }
                    awaitEnabledPicker()
                } finally { refreshReads.complete(Unit) }
            }
        } finally { discovery.complete(Unit); reads.complete(Unit) }
    }

    @Test fun checkoutDisablesCompleteRouteSessionPicker() = mutationBlocksPicker(Mutation.Checkout)
    @Test fun creationDisablesCompleteRouteSessionPicker() = mutationBlocksPicker(Mutation.Create)
    @Test fun commitDisablesCompleteRouteSessionPicker() = mutationBlocksPicker(Mutation.Commit)

    @Test
    fun worktreeNewChatBindsDirectoryReturnsHomeWithoutCreatingASession() {
        val f = GitRouteFixture()
        runSkikoComposeUiTest(size = Size(1440f, 900f)) {
            setGitRouteContent(f) { HarnessApp(f.connection, initialDestination = HarnessDestination.Settings,
                connectionTransport = ConnectionTransport.Local, gitBackend = f.backend,
                gitInitialCwd = GitWorktreesPageFixture.root, themeMode = HarnessThemeMode.Light) }
            enterGit()
            selectWorktrees()
            awaitRepository(GitWorktreesPageFixture.root)
            clickGitNode(scrollGitNodeIntoView(onNodeWithTag("git-worktree-new-chat"),
                "git-worktrees-list").assertIsEnabled())
            // The row action only records the pending directory and routes Home; nothing is created here.
            waitUntil { onAllNodesWithTag("local-worktree-chat-pending").fetchSemanticsNodes().isNotEmpty() }
            onNodeWithTag("local-worktree-chat-pending")
                .assertTextContains(GitWorktreesPageFixture.root, substring = true)
            assertTrue(f.reads.creations.isEmpty())
            assertTrue(f.reads.switches.isEmpty())
            clickGitNode(onNodeWithTag("local-worktree-chat-cancel"))
            onNodeWithTag("local-worktree-chat-pending").assertDoesNotExist()
        }
    }

    @Test
    fun retainedDirectoryOptionCannotClearANewerCwdSelection() {
        val f = GitRouteFixture()
        runSkikoComposeUiTest(size = Size(600f, 800f)) {
            setGitRouteContent(f) { HarnessApp(f.connection, initialDestination = HarnessDestination.Settings,
                connectionTransport = ConnectionTransport.Local, gitBackend = f.backend,
                gitInitialCwd = GitWorktreesPageFixture.root) }
            enterGit()
            openDirectoryMenu(f)
            val oldClear = requireNotNull(onNodeWithTag("menu:0").fetchSemanticsNode().config[SemanticsActions.OnClick].action)
            clickGitNode(scrollGitNodeIntoView(directoryOption(GitWorktreesPageFixture.longRoot)))
            awaitRepository(GitWorktreesPageFixture.longRoot)
            val before = f.reads.discoveries.size
            runOnIdle { oldClear() }
            awaitRepository(GitWorktreesPageFixture.longRoot)
            assertEquals(before, f.reads.discoveries.size)
            // A -> B -> A invalidates the original callback's selection lifetime as well.
            awaitEnabledPicker()
            openDirectoryMenu(f)
            clickGitNode(scrollGitNodeIntoView(directoryOption(GitWorktreesPageFixture.root)))
            awaitRepository(GitWorktreesPageFixture.root)
            runOnIdle { oldClear() }
            awaitRepository(GitWorktreesPageFixture.root)
            awaitEnabledPicker()
        }
    }

    @Test
    fun retainedMenuCallbackIsFencedAcrossConnectionGenerationAndUnavailableScope() {
        val f = GitRouteFixture()
        var connection by mutableStateOf<Connection?>(f.connection)
        var transport by mutableStateOf(ConnectionTransport.Local)
        runSkikoComposeUiTest(size = Size(600f, 800f)) {
            setGitRouteContent(f) { HarnessApp(connection, initialDestination = HarnessDestination.Settings,
                connectionTransport = transport, gitBackend = f.backend, gitInitialCwd = GitWorktreesPageFixture.root) }
            enterGit()
            val oldClear = retainClearOption(f)
            val oldBinding = f.reads.discoveries.last().first
            runOnIdle { connection = f.newConnection() }
            waitUntil { f.reads.discoveries.last().first.generation != oldBinding.generation }
            awaitRepository(GitWorktreesPageFixture.root)
            awaitEnabledPicker()
            val before = f.reads.discoveries.size
            runOnIdle { oldClear() }
            awaitRepository(GitWorktreesPageFixture.root)
            assertEquals(before, f.reads.discoveries.size)
            val localClear = retainClearOption(f)
            runOnIdle { transport = ConnectionTransport.Relay }
            waitUntil { onAllNodesWithTag("git-unavailable").fetchSemanticsNodes().isNotEmpty() }
            runOnIdle { localClear(); oldClear() }
            onNodeWithTag("git-session-cwd").assertDoesNotExist()
            onNodeWithTag("git-repository-root").assertDoesNotExist()
            assertEquals(before, f.reads.discoveries.size)
        }
    }

    private fun mutationBlocksPicker(mutation: Mutation) {
        val f = GitRouteFixture()
        val gate = CompletableDeferred<Unit>()
        if (mutation == Mutation.Commit) f.reads.changes = listOf(GitChange("staged.kt", indexStatus = 'M', worktreeStatus = ' '))
        f.reads.switchGate = gate
        f.reads.creationGate = gate
        f.backend.commitGate = gate
        try {
            runSkikoComposeUiTest(size = Size(600f, 800f)) {
                setGitRouteContent(f) { HarnessApp(f.connection, initialDestination = HarnessDestination.Settings,
                    connectionTransport = ConnectionTransport.Local, gitBackend = f.backend,
                    gitInitialCwd = GitWorktreesPageFixture.root) }
                enterGit()
                onNodeWithText("会话目录 / 本机初始目录").assertDoesNotExist()
                onNodeWithTag("git-repository-path").assertDoesNotExist()
                onAllNodesWithText(GitWorktreesPageFixture.root).assertCountEquals(1)
                val oldOption = retainClearOption(f)
                when (mutation) {
                    Mutation.Checkout -> {
                        clickGitNode(scrollGitNodeIntoView(onNodeWithTag("git-checkout-branch")))
                        clickGitNode(scrollGitNodeIntoView(onNodeWithTag("menu:2")))
                        clickGitNode(scrollGitNodeIntoView(onNodeWithTag("git-checkout-submit")).assertIsEnabled())
                        waitUntil { f.reads.switches.size == 1 }
                    }
                    Mutation.Create -> {
                        selectWorktrees()
                        onNodeWithTag("git-worktree-create-path").performScrollTo().performTextReplacement("/test-only/created")
                        onNodeWithTag("git-worktree-create-branch").performScrollTo().performTextReplacement("codex/route-picker")
                        clickGitNode(scrollGitNodeIntoView(onNodeWithTag("git-worktree-create-submit")).assertIsEnabled())
                        waitUntil { f.reads.creations.size == 1 }
                    }
                    Mutation.Commit -> {
                        clickGitNode(scrollGitNodeIntoView(onNodeWithTag("git-commit-open")))
                        onNodeWithTag("git-commit-message").performTextReplacement("controlled route commit")
                        clickGitNode(scrollGitNodeIntoView(onNodeWithTag("git-commit-submit")).assertIsEnabled())
                        waitUntil { f.backend.commits.size == 1 }
                        clickGitNode(scrollGitNodeIntoView(onNodeWithTag("git-commit-close")))
                    }
                }
                awaitDisabledPicker()
                val before = f.reads.discoveries.size
                assertDisabledPickerRejectsMouse()
                runOnIdle { oldOption() }
                assertSelectedDirectory(GitWorktreesPageFixture.root)
                assertEquals(before, f.reads.discoveries.size)
                assertEquals(1, f.reads.switches.size + f.reads.creations.size + f.backend.commits.size)
                runOnIdle { gate.complete(Unit) }
                awaitEnabledPicker()
            }
        } finally { gate.complete(Unit) }
    }

    private fun ComposeUiTest.setGitRouteContent(f: GitRouteFixture, content: @Composable () -> Unit) {
        setContent {
            val shortcuts = remember { ShortcutsRepository(MemoryShortcutsStore(), ShortcutPlatform.Windows) }
            CompositionLocalProvider(LocalGitSelectEventObserver provides f.selectObserver,
                LocalShortcutsRepository provides shortcuts) {
                // Observe the complete app hit path without consuming or replacing any input.
                Box(Modifier.fillMaxSize().pointerInput(f) {
                    f.events.add("app-probe-start")
                    try {
                        awaitPointerEventScope {
                            while (true) {
                                val initial = awaitPointerEvent(PointerEventPass.Initial)
                                if (initial.type == PointerEventType.Press || initial.type == PointerEventType.Release ||
                                    initial.type == PointerEventType.Scroll) {
                                    f.events.add("app-initial type=${initial.type}, changes=${initial.changes.map {
                                        "${it.id}:position=${it.position}, pressed=${it.pressed}, previous=${it.previousPressed}, consumed=${it.isConsumed}"
                                    }}")
                                    val final = awaitPointerEvent(PointerEventPass.Final)
                                    f.events.add("app-final type=${final.type}, changes=${final.changes.map {
                                        "${it.id}:position=${it.position}, pressed=${it.pressed}, previous=${it.previousPressed}, consumed=${it.isConsumed}"
                                    }}")
                                }
                            }
                        }
                    } finally { f.events.add("app-probe-stop") }
                }) { content() }
            }
        }
    }
    private fun ComposeUiTest.enterGit() {
        clickGitNode(onNodeWithTag("settings-row-git").assertIsDisplayed())
        awaitRepository(GitWorktreesPageFixture.root)
        awaitEnabledPicker()
        onNodeWithTag("settings-content-git").assertIsDisplayed()
        onNodeWithTag("navigator-scrim").assertDoesNotExist()
        onNodeWithTag("navigator-overlay").assertDoesNotExist()
    }
    private fun ComposeUiTest.selectWorktrees() {
        if (onAllNodesWithTag("settings-navigator").fetchSemanticsNodes().isEmpty()) {
            // This headless shell has no native title bar; use the production sidebar shortcut.
            scrollGitNodeIntoView(onNodeWithTag("git-session-cwd"))
                .performSemanticsAction(SemanticsActions.RequestFocus) { request -> assertTrue(request()) }
            onRoot().performKeyInput {
                keyDown(Key.CtrlLeft); keyDown(Key.ShiftLeft); pressKey(Key.S)
                keyUp(Key.ShiftLeft); keyUp(Key.CtrlLeft)
            }
            waitUntil(timeoutMillis = 5_000) {
                onAllNodesWithTag("settings-navigator").fetchSemanticsNodes().isNotEmpty()
            }
            onNodeWithTag("navigator-scrim").assertIsDisplayed()
            onNodeWithTag("navigator-overlay").assertIsDisplayed()
        }
        clickGitNode(onNodeWithTag("settings-row-worktrees").assertIsDisplayed())
        onNodeWithTag("navigator-scrim").assertDoesNotExist()
        onNodeWithTag("navigator-overlay").assertDoesNotExist()
    }
    private fun ComposeUiTest.awaitRepository(root: String) {
        waitUntil(timeoutMillis = 10_000) {
            onAllNodes(hasTestTag("git-repository-root") and hasText(root)).fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithTag("git-repository-root").assertTextEquals(root)
    }
    private fun ComposeUiTest.retainClearOption(f: GitRouteFixture): () -> Boolean {
        openDirectoryMenu(f)
        val clear = requireNotNull(onNodeWithTag("menu:0").fetchSemanticsNode().config[SemanticsActions.OnClick].action)
        // Dismiss with a real option click, retaining only the platform callback for the fence check.
        val selected = onNodeWithTag("git-session-cwd").fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.StateDescription]
        clickGitNode(scrollGitNodeIntoView(directoryOption(selected)))
        return clear
    }
    private fun ComposeUiTest.openDirectoryMenu(f: GitRouteFixture) {
        // Distinguish service completion from popup/matcher failure without changing the deadline.
        try {
            waitUntil(conditionDescription = "fixture session list returned or failed", timeoutMillis = 10_000) {
                val result = f.sessionListResult.get()
                result.cwds != null || result.failure != null
            }
        } catch (timeout: ComposeTimeoutException) {
            throw AssertionError("Session list precondition: ${f.sessionListResult.get()}").apply { initCause(timeout) }
        }
        val listed = f.sessionListResult.get()
        listed.failure?.let { failure ->
            throw AssertionError("Fixture session list failed after ${listed.attempts} attempt(s)").apply { initCause(failure) }
        }
        assertEquals(listOf(GitWorktreesPageFixture.root, GitWorktreesPageFixture.longRoot), listed.cwds,
            "The production binding must consume the fixture session list")
        settleGitLayout()
        awaitEnabledPicker()
        val shellBefore = directoryMenuEvidence()
        f.events.add("menu-click-begin shell={$shellBefore}")
        val pointerEvidence = clickGitNode(scrollGitNodeIntoView(onNodeWithTag("git-session-cwd")))
        f.events.add("menu-click-end")
        try {
            waitUntil(conditionDescription = "mouse-opened directory menu contains the long cwd option", timeoutMillis = 10_000) {
                onAllNodes(directoryOptionMatcher(GitWorktreesPageFixture.longRoot))
                    .fetchSemanticsNodes().isNotEmpty()
            }
        } catch (timeout: ComposeTimeoutException) {
            throw AssertionError("Directory menu precondition after real mouse click; list=${f.sessionListResult.get()}; " +
                "pointer=$pointerEvidence; " +
                "events=${f.events.snapshot()}; " +
                runCatching { directoryMenuEvidence() }.getOrElse { "Menu evidence unavailable: $it" })
                .apply { initCause(timeout) }
        }
    }
    private fun ComposeUiTest.directoryMenuEvidence(): String {
        // Only this anchor, popup, options, and potential shell interceptors; no whole-tree dump.
        val tags = listOf("git-session-cwd", "git-session-cwd-menu", "menu:0", "menu:1", "menu:2", "menu:3",
            "navigator-overlay", "navigator-scrim", "thread-panel-overlay", "thread-panel-scrim",
            "account-menu", "account-menu-scrim", "explore-menu", "explore-menu-scrim")
        val targeted = tags.map(::hasTestTag).reduce { a, b -> a or b } or hasText(GitWorktreesPageFixture.longRoot)
        return listOf(false, true).joinToString("; ") { unmerged ->
            val nodes = onAllNodes(targeted, useUnmergedTree = unmerged).fetchSemanticsNodes()
            "unmerged=$unmerged " + nodes.joinToString { node ->
                val config = node.config
                val root = generateSequence(node) { it.parent }.last()
                "node=${node.id}, root=${root.id}, tag=${config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.TestTag)}, " +
                    "text=${config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)}, " +
                    "click=${config.contains(SemanticsActions.OnClick)}, " +
                    "disabled=${config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled)}, " +
                    "bounds=${node.boundsInRoot}"
            }
        }
    }
    private fun directoryOptionMatcher(cwd: String) = hasText(cwd) and hasClickAction() and !hasTestTag("git-session-cwd") and
        hasAnyAncestor(hasTestTag("git-session-cwd-menu"))
    private fun ComposeUiTest.directoryOption(cwd: String) = onNode(directoryOptionMatcher(cwd))
    private fun ComposeUiTest.assertSelectedDirectory(cwd: String) {
        onNodeWithTag("git-session-cwd").assert(SemanticsMatcher.expectValue(
            androidx.compose.ui.semantics.SemanticsProperties.StateDescription, cwd))
    }
    private fun ComposeUiTest.awaitDisabledPicker() = waitUntil {
        onAllNodes(hasTestTag("git-session-cwd") and isNotEnabled()).fetchSemanticsNodes().isNotEmpty()
    }
    private fun ComposeUiTest.awaitEnabledPicker() = waitUntil(timeoutMillis = 10_000) {
        onAllNodes(hasTestTag("git-session-cwd") and isEnabled()).fetchSemanticsNodes().isNotEmpty()
    }
    private fun ComposeUiTest.assertDisabledPickerRejectsMouse() {
        clickGitNode(scrollGitNodeIntoView(onNodeWithTag("git-session-cwd")).assertIsNotEnabled())
        onNodeWithTag("git-session-cwd-menu").assertDoesNotExist()
    }
    private enum class Mutation { Checkout, Create, Commit }
}

/** Bounded, ordered evidence only; never observable Compose state or an input/controller mutation. */
private class GitRouteEvents {
    private val entries = ArrayDeque<String>()
    private var sequence = 0L
    @Synchronized fun add(detail: String) {
        if (entries.size == 256) entries.removeFirst()
        entries.addLast("${++sequence}: $detail")
    }
    @Synchronized fun snapshot(): String = entries.joinToString(" | ")
}

private data class SessionListResult(
    val attempts: Int = 0,
    val cwds: List<String?>? = null,
    val failure: Throwable? = null,
)

private class GitRouteFixture {
    val events = GitRouteEvents()
    val selectObserver: (GitSelectEvent) -> Unit = { event ->
        events.add("tag=${event.tag}, instance=${System.identityHashCode(event.instance)}, kind=${event.kind}, " +
            "enabled=${event.enabled}, expanded=${event.expanded}, ${event.detail}")
    }
    val reads = GitWorktreesPageAdapter()
    val backend = GitRouteCommitAdapter(reads)
    private val gateway = ApiGateway()
    val connection: Connection
    var sessionListResult = AtomicReference(SessionListResult())
        private set
    init {
        runBlocking {
            gateway.register(HostDescribeEndpoint) {
                ApiResult(ApiResult.SUCCESS_CODE, data = HostDescription(ProtocolVersion.Current,
                    GitWorktreesPageFixture.binding.hostId, "Git route fixture"))
            }
        }
        connection = newConnection()
    }
    fun newConnection(): Connection {
        val result = AtomicReference(SessionListResult())
        sessionListResult = result
        val local = LocalConnection(gateway)
        return object : Connection by local {
            override val session: SessionApi = object : SessionApi by local.session {
                override suspend fun list(): List<SessionSummary> {
                    result.updateAndGet { SessionListResult(attempts = it.attempts + 1) }
                    return try {
                        listOf(
                            SessionSummary(SessionId("source"), 1L, GitWorktreesPageFixture.root),
                            SessionSummary(SessionId("long-directory"), 2L, GitWorktreesPageFixture.longRoot),
                        ).also { sessions -> result.updateAndGet { it.copy(cwds = sessions.map { session -> session.cwd }) } }
                    } catch (failure: Throwable) {
                        result.updateAndGet { it.copy(failure = failure) }
                        throw failure
                    }
                }
            }
        }
    }
}

private class GitRouteCommitAdapter(private val reads: GitWorktreesPageAdapter) :
    GitBackend by reads, GitBranchSwitcher by reads, GitWorktreeCreator by reads, GitCommitter {
    var commitGate: CompletableDeferred<Unit>? = null
    val commits = mutableListOf<GitCommitRequest>()
    override fun canCommit(binding: GitBinding) = binding.access == GitAccess.LocalReadOnly
    override suspend fun readCommitSnapshot(repository: GitRepository): GitCommitSnapshot {
        val status = reads.status(repository)
        return GitCommitSnapshot(repository, status, "c".repeat(64), status.stagedCount)
    }
    override suspend fun commit(repository: GitRepository, request: GitCommitRequest): GitCommitResult {
        commits += request
        commitGate?.await()
        return GitCommitResult.Rejected(GitFailure(GitFailureKind.CommandFailed, "Controlled commit rejection"))
    }
}
