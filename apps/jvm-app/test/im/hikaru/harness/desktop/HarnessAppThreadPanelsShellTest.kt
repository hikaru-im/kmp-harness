package im.hikaru.harness.desktop

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import im.hikaru.contracts.common.ApiResult
import im.hikaru.contracts.harness.identity.HostDescription
import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.contracts.harness.protocol.ProtocolVersion
import im.hikaru.harness.client.app.HarnessApp
import im.hikaru.harness.client.connection.*
import im.hikaru.harness.client.account.BackendTenant
import im.hikaru.harness.client.account.MemberIdentity
import im.hikaru.harness.client.conversation.*
import im.hikaru.harness.ui.foundation.HarnessThemeMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import javax.imageio.ImageIO
import kotlin.test.*
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image

/**
 * Production full-shell assembly with a controlled, typed SessionApi. Pointer routing and captured
 * rendering are covered here; these captures establish neither pixel parity nor native-window or
 * real-process-backend behavior. HarnessApp owns all repositories, controllers and panel state.
 */
@OptIn(ExperimentalTestApi::class)
class HarnessAppThreadPanelsShellTest {
    @Test
    fun fullShell1440x900Light() = scenario(1440, 900, HarnessThemeMode.Light)

    @Test
    fun fullShell1440x900Dark() = scenario(1440, 900, HarnessThemeMode.Dark)

    @Test
    fun fullShell1024x768Light() = scenario(1024, 768, HarnessThemeMode.Light)

    @Test
    fun fullShell1024x768Dark() = scenario(1024, 768, HarnessThemeMode.Dark)

    @Test
    fun fullShell1024x768ResizedNavigatorLight() = scenario(1024, 768, HarnessThemeMode.Light, resizeNavigator = true)

    @Test
    fun fullShell1024x768ResizedNavigatorDark() = scenario(1024, 768, HarnessThemeMode.Dark, resizeNavigator = true)

    @Test
    fun fullShell600x800Light() = scenario(600, 800, HarnessThemeMode.Light)

    @Test
    fun fullShell600x800Dark() = scenario(600, 800, HarnessThemeMode.Dark)

    private fun scenario(width: Int, height: Int, theme: HarnessThemeMode, resizeNavigator: Boolean = false) =
        runSkikoComposeUiTest(size = Size(width.toFloat(), height.toFloat())) {
            val api = ShellPanelsSessionApi()
            val connection = ShellPanelsConnection(api)
            val compact = width == 600
            val themeSlug = if (theme == HarnessThemeMode.Light) "light" else "dark"
            val capturePrefix = "${width}x$height-$themeSlug" + if (resizeNavigator) "-navigator-resized" else ""
            val directory = File(System.getenv("HARNESS_APP_THREAD_SHELL_CAPTURE_DIR")
                ?.takeIf { it.isNotBlank() } ?: "/tmp/harness-app-thread-shell-captures-20261008")
            check(directory.isDirectory || directory.mkdirs()) { "Cannot create capture directory: $directory" }
            val captured = mutableListOf<String>()
            var measuredMainAreaWidth = 0f
            var expectedOverlay = false

            fun exists(tag: String): Boolean =
                onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

            fun awaitTag(tag: String) {
                waitUntil(timeoutMillis = 10_000) { exists(tag) }
            }

            fun awaitText(text: String) {
                waitUntil(timeoutMillis = 10_000) {
                    onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
                }
                onNodeWithText(text).assertIsDisplayed()
            }

            fun bounds(tag: String): Rect =
                onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

            fun mainAreaWidth(): Float {
                val root = onRoot().fetchSemanticsNode().boundsInRoot
                val rail = if (exists("app-rail")) bounds("app-rail").width else 0f
                val pushed = if (exists("workspace-navigator") && !exists("navigator-overlay"))
                    bounds("workspace-navigator").width else 0f
                return root.width - rail - pushed
            }

            fun pointerClick(node: SemanticsNodeInteraction) {
                node.assertIsDisplayed().assertIsEnabled()
                val target = node.fetchSemanticsNode().boundsInRoot
                val root = onRoot().fetchSemanticsNode().boundsInRoot
                assertTrue(target.width > 0f && target.height > 0f, "Empty pointer target: $target")
                assertTrue(target.left >= root.left - 1f && target.top >= root.top - 1f &&
                    target.right <= root.right + 1f && target.bottom <= root.bottom + 1f,
                    "Pointer target must be inside the viewport: $target, root=$root")
                node.performMouseInput { click() }
                waitForIdle()
            }

            fun clickTag(tag: String) {
                awaitTag(tag)
                pointerClick(onNodeWithTag(tag, useUnmergedTree = true))
            }

            fun clickText(text: String) {
                awaitText(text)
                pointerClick(onNodeWithText(text))
            }

            fun clickDescription(description: String) {
                pointerClick(onNodeWithContentDescription(description, useUnmergedTree = true))
            }

            fun assertDraft(text: String) {
                onNodeWithTag("live-composer-editor").assert(SemanticsMatcher.expectValue(
                    SemanticsProperties.EditableText, AnnotatedString(text)))
            }

            fun dismissNavigatorIfOpen() {
                if (exists("navigator-overlay")) {
                    // A compact window floats the navigator; the titlebar toggle dismisses it.
                    assertTrue(compact, "Unexpected floating navigator at $width")
                    onNodeWithContentDescription("Toggle sidebar").performClick()
                    waitUntil { !exists("navigator-overlay") }
                }
            }

            fun selectListedSession(id: SessionId, history: String) {
                if (compact && !exists("navigator-overlay")) {
                    onNodeWithContentDescription("Toggle sidebar").performClick()
                    awaitTag("navigator-overlay")
                }
                onNodeWithTag("workspace-navigator", useUnmergedTree = true).assertIsDisplayed()
                clickTag("recent-${id.value}")
                // Production may retain an Expanded floating drawer after selecting a row.
                dismissNavigatorIfOpen()
                awaitTag("live-thread")
                awaitText(history)
                waitUntil { onAllNodesWithText("终端").fetchSemanticsNodes().isNotEmpty() }
                onNodeWithTag("live-composer-editor").assertIsDisplayed().assertIsEnabled()
                onNodeWithTag("recent-${SHELL_PARENT.value}").assertIsDisplayed().assertIsEnabled()
            }

            fun assertPanelsClosed() {
                onNodeWithTag("tab:BackgroundOutput").assertDoesNotExist()
                onNodeWithTag("tab:Subagents").assertDoesNotExist()
                onNodeWithTag("thread-panel-overlay").assertDoesNotExist()
                onNodeWithTag("thread-panel-scrim").assertDoesNotExist()
                onNodeWithText(SHELL_RUNNING_OUTPUT).assertDoesNotExist()
                onNodeWithText(SHELL_CHILD_HISTORY).assertDoesNotExist()
                onNodeWithText(SHELL_CHILD.value).assertDoesNotExist()
            }

            fun capture(stage: String) {
                waitForIdle()
                assertTrue(captured.add(stage))
                val file = File(directory, "$capturePrefix-$stage.png")
                val bitmap = onRoot().captureToImage()
                assertEquals(width, bitmap.width)
                assertEquals(height, bitmap.height)
                Image.makeFromBitmap(bitmap.asSkiaBitmap()).use { image ->
                    requireNotNull(image.encodeToData(EncodedImageFormat.PNG)).use { data ->
                        assertTrue(data.bytes.isNotEmpty(), "Empty PNG for $stage")
                        file.writeBytes(data.bytes)
                    }
                }
                assertTrue(file.length() > 0L)
                val decoded = requireNotNull(ImageIO.read(file)) { "Cannot decode $file" }
                try {
                    assertEquals(width, decoded.width, "PNG width for $stage")
                    assertEquals(height, decoded.height, "PNG height for $stage")
                } finally { decoded.flush() }
                // Only synthetic viewport geometry, never host descriptions or transcript contents.
                File(directory, "$capturePrefix-$stage.geometry.txt").writeText(buildString {
                    appendLine("viewport=${width}x$height theme=$themeSlug state=$stage")
                    appendLine("mainAreaWidth=${mainAreaWidth()} overlayThreshold=672.0 expectedOverlayWhenOpen=${mainAreaWidth() < 672f} actualOverlay=${exists("thread-panel-overlay")}")
                    appendLine("navigatorExpanded=${exists("workspace-navigator")} navigatorFloating=${exists("navigator-overlay")}")
                    for (tag in listOf("app-rail", "main-surface", "main-content-column", "composer-column", "live-thread",
                        "live-composer-editor", "recent-${SHELL_PARENT.value}", "workspace-navigator",
                        "navigator-overlay", "thread-panel-overlay", "thread-panel-scrim",
                        "tab:BackgroundOutput", "tab:Subagents")) {
                        if (exists(tag)) appendLine("$tag=${bounds(tag)}")
                    }
                    for (text in listOf("终端", "子 Agent")) {
                        val nodes = onAllNodesWithText(text).fetchSemanticsNodes()
                        if (nodes.size == 1) appendLine("titlebar-$text=${nodes.single().boundsInRoot}")
                    }
                })
            }

            setContent { HarnessApp(connection = connection, themeMode = theme) }
            awaitTag("recent-${SHELL_PARENT.value}")
            selectListedSession(SHELL_PARENT, SHELL_PARENT_HISTORY)
            assertPanelsClosed()
            if (resizeNavigator) {
                val before = bounds("workspace-navigator").width
                val edge = bounds("navigator-resize-handle").center
                onRoot().performMouseInput {
                    moveTo(edge)
                    press()
                    advanceEventTime(16)
                    moveTo(edge + Offset(125f, 0f))
                    release()
                }
                waitForIdle()
                assertTrue(bounds("workspace-navigator").width > before, "Real pointer resize must widen navigator")
                assertTrue(mainAreaWidth() < 672f, "Resized navigator must exercise measured overlay threshold")
            }
            onNodeWithTag("main-content-column", useUnmergedTree = true).assertIsDisplayed()
            onNodeWithTag("composer-column", useUnmergedTree = true).assertIsDisplayed()
            clickTag("live-composer-editor")
            onNodeWithTag("live-composer-editor").performTextReplacement(SHELL_DRAFT)
            assertDraft(SHELL_DRAFT)
            val closedMain = bounds("main-content-column")
            val closedComposer = bounds("composer-column")
            val closedEditor = bounds("live-composer-editor")
            val rootBounds = onRoot().fetchSemanticsNode().boundsInRoot
            val railWidth = if (exists("app-rail")) bounds("app-rail").width else 0f
            val pushedNavigatorWidth = if (exists("workspace-navigator") && !exists("navigator-overlay"))
                bounds("workspace-navigator").width else 0f
            measuredMainAreaWidth = rootBounds.width - railWidth - pushedNavigatorWidth
            assertEquals(measuredMainAreaWidth, bounds("main-surface").width, "Measured shell regions must agree")
            expectedOverlay = measuredMainAreaWidth < 672f
            if (width == 1024) {
                assertTrue(pushedNavigatorWidth > 0f, "1024 acceptance requires expanded pushed navigator")
            }
            capture("closed")

            // The summary island is a separate production surface from the tabbed context panel. This
            // controlled connection exposes no Git service, so the Environment section must state the
            // real reason rather than draw rows.
            clickTag("thread-summary-toggle")
            awaitTag("thread-summary-panel")
            onNodeWithTag("thread-summary-title").assertIsDisplayed()
            awaitTag("thread-summary-unavailable")
            onNodeWithTag("thread-summary-changes").assertDoesNotExist()
            // Background activity is a separate read from the Environment section: it lists the real
            // child agent and the two registered processes even though no Git service is exposed.
            awaitTag("thread-summary-section-subagents")
            onNodeWithTag("thread-summary-section-subagents-count", useUnmergedTree = true).assertTextEquals("1")
            onNodeWithTag("thread-summary-subagent-${SHELL_CHILD.value}").assertExists()
            awaitTag("thread-summary-section-processes")
            onNodeWithTag("thread-summary-section-processes-count", useUnmergedTree = true).assertTextEquals("2")
            onNodeWithTag("thread-summary-process-${SHELL_RUNNING.value}").assertExists()
            onNodeWithTag("thread-summary-process-${SHELL_EXITED.value}").assertExists()
            onNodeWithTag("thread-summary-process-stop-${SHELL_RUNNING.value}").assertExists()
            onNodeWithTag("thread-summary-process-stop-${SHELL_EXITED.value}").assertExists()
            clickTag("thread-summary-toggle")
            waitUntil { !exists("thread-summary-panel") }

            fun assertPanelGeometry() {
                val main = bounds("main-content-column")
                val composer = bounds("composer-column")
                val editor = bounds("live-composer-editor")
                if (expectedOverlay) {
                    onNodeWithTag("thread-panel-overlay", useUnmergedTree = true).assertIsDisplayed()
                    onNodeWithTag("thread-panel-scrim", useUnmergedTree = true).assertExists()
                    val panel = bounds("thread-panel-overlay")
                    val scrim = bounds("thread-panel-scrim")
                    val surface = bounds("main-surface")
                    assertTrue(panel.left >= surface.left && scrim.left >= surface.left, "Overlay must leave pushed navigation exposed")
                    for (text in listOf("终端", "子 Agent")) {
                        val title = onNodeWithText(text).fetchSemanticsNode().boundsInRoot
                        assertTrue(panel.top >= title.bottom && scrim.top >= title.bottom, "Overlay must leave production titlebar reachable")
                    }
                    assertEquals(closedMain, main, "Overlay must preserve main history bounds")
                    assertEquals(closedComposer, composer, "Overlay must preserve composer bounds")
                    assertEquals(closedEditor, editor, "Overlay must preserve editor bounds")
                } else {
                    onNodeWithTag("thread-panel-overlay").assertDoesNotExist()
                    onNodeWithTag("thread-panel-scrim").assertDoesNotExist()
                    val panelLeft = listOf("tab:BackgroundOutput", "tab:Subagents")
                        .filter(::exists).minOf { bounds(it).left }
                    for ((name, region) in listOf("history" to main, "composer" to composer, "editor" to editor)) {
                        assertTrue(region.width > 0f && region.height > 0f)
                        assertTrue(region.right <= panelLeft + 1f, "Side panel overlaps $name: $region, panelLeft=$panelLeft")
                    }
                    assertTrue(main.width < closedMain.width, "Side panel must reserve its own column")
                }
                assertDraft(SHELL_DRAFT)
                onNodeWithTag("recent-${SHELL_PARENT.value}").assertIsDisplayed().assertIsEnabled()
            }

            clickText("终端")
            awaitText(SHELL_RUNNING_OUTPUT)
            onNodeWithTag("terminal-process-select").assertIsDisplayed()
            onNodeWithTag("terminal-input").assertIsDisplayed().assertIsEnabled()
            assertPanelGeometry()
            capture("terminal")
            // The selected production titlebar action must close its controller tab via pointer.
            clickText("终端")
            waitUntil { !exists("tab:BackgroundOutput") }
            assertPanelsClosed()
            capture("titlebar-closed")
            clickText("终端")
            awaitText(SHELL_RUNNING_OUTPUT)

            // Do not replace this pointer click with a semantic action: an overlay hiding this
            // titlebar entry is a shell regression that this matrix must expose.
            clickText("子 Agent")
            awaitTag("tab:Subagents")
            onNodeWithTag("tab:BackgroundOutput").assertIsDisplayed()
            awaitText(SHELL_CHILD.value)
            clickTag("tab:BackgroundOutput")
            awaitText(SHELL_RUNNING_OUTPUT)
            assertPanelGeometry()
            capture("both-tabs-terminal")

            clickTag("tab:Subagents")
            awaitText(SHELL_CHILD.value)
            assertPanelGeometry()
            capture("subagents-overview")
            clickText(SHELL_CHILD.value)
            awaitText(SHELL_CHILD_HISTORY)
            onNodeWithText("只读对话记录").assertIsDisplayed()
            assertPanelGeometry()
            capture("subagent-detail")
            clickDescription("返回子智能体")
            awaitText(SHELL_CHILD.value)
            onNodeWithText(SHELL_CHILD_HISTORY).assertDoesNotExist()

            clickDescription("关闭后台终端")
            onNodeWithTag("tab:BackgroundOutput").assertDoesNotExist()
            onNodeWithTag("tab:Subagents").assertIsDisplayed()
            awaitText(SHELL_CHILD.value)
            assertPanelGeometry()
            capture("one-tab-remains")
            if (expectedOverlay) {
                // The scrim's center is behind the panel. Click an exposed point in its left strip
                // to exercise AppShell's real onDismissContextPanel callback.
                val scrim = onNodeWithTag("thread-panel-scrim", useUnmergedTree = true).assertIsDisplayed()
                val scrimBounds = scrim.fetchSemanticsNode().boundsInRoot
                val panelBounds = bounds("thread-panel-overlay")
                val point = Offset(8f, scrimBounds.height / 2f)
                assertTrue(scrimBounds.left + point.x < panelBounds.left, "No exposed panel scrim")
                assertTrue(point.x < scrimBounds.width && point.y < scrimBounds.height)
                scrim.performMouseInput { click(point) }
            } else {
                clickDescription("关闭子智能体")
            }
            waitUntil { !exists("tab:Subagents") }
            assertPanelsClosed()
            assertEquals(closedMain, bounds("main-content-column"))
            assertEquals(closedComposer, bounds("composer-column"))
            assertEquals(closedEditor, bounds("live-composer-editor"))
            awaitText(SHELL_PARENT_HISTORY)
            clickTag("live-composer-editor")
            assertDraft(SHELL_DRAFT)
            val selection = onNodeWithTag("live-composer-editor").fetchSemanticsNode()
                .config[SemanticsProperties.TextSelectionRange]
            onNodeWithTag("live-composer-editor").performTextInput(" after dismiss")
            assertDraft(SHELL_DRAFT.take(selection.min) + " after dismiss" + SHELL_DRAFT.drop(selection.max))
            onNodeWithTag("live-composer-editor").performTextReplacement(SHELL_DRAFT)
            capture("restored")

            clickText("终端")
            awaitText(SHELL_RUNNING_OUTPUT)
            assertPanelGeometry()
            // Rail / bottom bar must still receive real pointer input with a panel open.
            clickTag("recent-${SHELL_PARENT.value}")
            waitUntil { !exists("live-thread") && !exists("tab:BackgroundOutput") }
            assertPanelsClosed()
            dismissNavigatorIfOpen()
            capture("navigation-after-panel")
            selectListedSession(SHELL_PARENT, SHELL_PARENT_HISTORY)
            assertPanelsClosed()
            clickTag("live-composer-editor")
            onNodeWithTag("live-composer-editor").performTextReplacement("Parent after navigation")
            assertDraft("Parent after navigation")
            selectListedSession(SHELL_OTHER, SHELL_OTHER_HISTORY)
            assertPanelsClosed()
            onNodeWithText(SHELL_PARENT_HISTORY).assertDoesNotExist()
            assertDraft("")
            clickTag("live-composer-editor")
            onNodeWithTag("live-composer-editor").performTextReplacement("Other thread editable")
            assertDraft("Other thread editable")
            capture("other-thread")
            selectListedSession(SHELL_PARENT, SHELL_PARENT_HISTORY)
            assertDraft("Parent after navigation")
            selectListedSession(SHELL_OTHER, SHELL_OTHER_HISTORY)
            assertDraft("Other thread editable")

            runOnIdle {
                assertEquals(10, captured.size)
                assertEquals(10, captured.distinct().size)
                assertTrue(connection.descriptionReads.isNotEmpty(), "Host must be verified through HostApi")
                assertTrue(connection.descriptionReads.all { it == SHELL_HOST })
                assertTrue(api.listReads.isNotEmpty())
                assertTrue(SHELL_PARENT in api.historyReads && SHELL_OTHER in api.historyReads)
                assertTrue(api.historyReads.all { it == SHELL_PARENT || it == SHELL_OTHER })
                assertTrue(api.inventoryReads.isNotEmpty())
                assertTrue(api.inventoryReads.all { it == SHELL_PARENT })
                assertTrue(api.outputReads.size >= 2, "Opening and reopening must read producer output")
                assertTrue(api.outputReads.all { it == SHELL_PARENT to SHELL_RUNNING })
                assertTrue(api.descendantReads.isNotEmpty())
                assertTrue(api.descendantReads.all { it == SHELL_PARENT })
                assertEquals(listOf(SHELL_PARENT to SHELL_CHILD), api.childReads.toList())
                assertTrue(api.mutations.isEmpty(), "Read-only panel scenario caused mutations: ${api.mutations}")
            }
        }
}

/** Real controller regressions supporting the complete shell's cross-thread pointer scenario. */
@OptIn(ExperimentalCoroutinesApi::class)
class HarnessAppThreadShellDraftIsolationTest {
    @Test
    fun sessionAndUncreatedDraftsRestoreAcrossSameOwnerReconnect() = runTest {
        val controller = ConversationController(this)
        val connection = ShellPanelsConnection(ShellPanelsSessionApi())
        val owner = ConversationHostScope(SHELL_HOST, 1L, null)
        try {
            controller.bind(connection, owner)
            controller.updateDraft("Uncreated draft")
            controller.select(SHELL_PARENT)
            assertEquals("", controller.state.value.draft)
            controller.updateDraft("Parent draft")
            controller.select(SHELL_OTHER)
            assertEquals("", controller.state.value.draft)
            controller.updateDraft("Other draft")
            controller.select(null)
            assertEquals("Uncreated draft", controller.state.value.draft)
            controller.select(SHELL_PARENT)
            val revision = controller.state.value.draftRevision
            controller.select(SHELL_PARENT)
            assertEquals(revision, controller.state.value.draftRevision, "Same id select is a no-op")
            controller.bind(null, owner.copy(generation = 2L))
            assertEquals(SHELL_PARENT, controller.state.value.selectedSessionId)
            assertEquals("Parent draft", controller.state.value.draft)
            controller.bind(connection, owner.copy(generation = 3L))
            controller.select(SHELL_OTHER)
            assertEquals("Other draft", controller.state.value.draft)
            controller.select(null)
            assertEquals("Uncreated draft", controller.state.value.draft)
        } finally { controller.close() }
    }

    @Test
    fun hostIdentityAndLogoutClearEveryDraftScope() = runTest {
        val controller = ConversationController(this)
        val connection = ShellPanelsConnection(ShellPanelsSessionApi())
        val owner = ConversationHostScope(SHELL_HOST, 1L, null)
        val identity = MemberIdentity(BackendTenant("https://controlled.example", 1L), 1L)
        fun populate() {
            controller.select(null); controller.updateDraft("Private home")
            controller.select(SHELL_PARENT); controller.updateDraft("Private parent")
            controller.select(SHELL_OTHER); controller.updateDraft("Private other")
        }
        fun assertEmpty() {
            assertNull(controller.state.value.selectedSessionId)
            for (id in listOf(null, SHELL_PARENT, SHELL_OTHER)) {
                controller.select(id); assertEquals("", controller.state.value.draft)
            }
        }
        try {
            controller.bind(connection, owner); populate()
            val otherHost = owner.copy(hostId = HostId("other-controlled-host"), generation = 2L)
            controller.bind(connection, otherHost); assertEmpty(); populate()
            val memberOwner = otherHost.copy(identity = identity, generation = 3L)
            controller.bind(connection, memberOwner); assertEmpty(); populate()
            controller.bind(connection, memberOwner.copy(identity = identity.copy(userId = 2L), generation = 4L))
            assertEmpty(); populate()
            controller.bind(null, null)
            controller.bind(connection, owner.copy(generation = 5L))
            assertEmpty()
        } finally { controller.close() }
    }

    @Test
    fun createRouteSameIdAndAcceptedSendCannotResurrectHomeOrSessionDraft() = runTest {
        val operations = ShellDraftOperations()
        val controller = ConversationController(this, operations)
        try {
            controller.bind(ShellPanelsConnection(ShellPanelsSessionApi()), ConversationHostScope(SHELL_HOST, 1L, null))
            controller.updateDraft("First send")
            requireNotNull(controller.submit(onSessionCreated = {
                val revision = controller.state.value.draftRevision
                controller.select(it.id)
                assertEquals(revision, controller.state.value.draftRevision)
                true
            })).join()
            assertEquals(listOf(SHELL_PARENT), operations.promptIds)
            assertEquals("", controller.state.value.draft)
            controller.select(null); assertEquals("", controller.state.value.draft)
            controller.select(SHELL_PARENT); assertEquals("", controller.state.value.draft)
        } finally { controller.close() }
    }

    @Test
    fun editsDuringCreateTransferToRealSessionAndRevisionFencesSameTextEdits() = runTest {
        val operations = ShellDraftOperations()
        val gate = CompletableDeferred<SessionSummary>()
        operations.createResult = { gate.await() }
        val controller = ConversationController(this, operations)
        try {
            controller.bind(ShellPanelsConnection(ShellPanelsSessionApi()), ConversationHostScope(SHELL_HOST, 1L, null))
            controller.updateDraft("Snapshot")
            val send = requireNotNull(controller.submit())
            advanceUntilIdle()
            controller.updateDraft("Edited away")
            controller.updateDraft("Snapshot")
            gate.complete(SessionSummary(SHELL_PARENT, 1L)); send.join()
            assertEquals("Snapshot", controller.state.value.draft, "Edit-away-and-back must defeat old revision clear")
            controller.select(null); assertEquals("", controller.state.value.draft)
            controller.select(SHELL_PARENT); assertEquals("Snapshot", controller.state.value.draft)
        } finally { controller.close() }
    }

    @Test
    fun acceptedExistingSessionSendClearsOnlyItsScope() = runTest {
        val controller = ConversationController(this, ShellDraftOperations())
        try {
            controller.bind(ShellPanelsConnection(ShellPanelsSessionApi()), ConversationHostScope(SHELL_HOST, 1L, null))
            controller.updateDraft("Home unsent")
            controller.select(SHELL_OTHER); controller.updateDraft("Other unsent")
            controller.select(SHELL_PARENT); controller.updateDraft("Send parent")
            requireNotNull(controller.submit()).join()
            controller.select(SHELL_OTHER); assertEquals("Other unsent", controller.state.value.draft)
            controller.select(null); assertEquals("Home unsent", controller.state.value.draft)
            controller.select(SHELL_PARENT); assertEquals("", controller.state.value.draft)
        } finally { controller.close() }
    }

    @Test
    fun latePromptFailureCannotLeakOrClearDraftsAfterPointerRouteSelection() = runTest {
        val operations = ShellDraftOperations()
        val gate = CompletableDeferred<Unit>()
        operations.promptResult = { id ->
            withContext(NonCancellable) { gate.await(); error("Old controlled prompt failed") }
        }
        val controller = ConversationController(this, operations)
        try {
            controller.bind(ShellPanelsConnection(ShellPanelsSessionApi()), ConversationHostScope(SHELL_HOST, 1L, null))
            controller.select(SHELL_PARENT); controller.updateDraft("Keep parent")
            val send = requireNotNull(controller.submit())
            runCurrent()
            controller.select(SHELL_OTHER)
            assertEquals("", controller.state.value.draft)
            controller.updateDraft("Keep other")
            gate.complete(Unit); send.join()
            assertEquals(SHELL_OTHER, controller.state.value.selectedSessionId)
            assertEquals("Keep other", controller.state.value.draft)
            assertEquals(ConversationAction.Idle, controller.state.value.action)
            assertNull(controller.state.value.lastAcceptedReceipt)
            assertFalse(controller.state.value.errors.containsKey(ConversationErrorKind.Prompt))
            controller.select(SHELL_PARENT); assertEquals("Keep parent", controller.state.value.draft)
            controller.select(SHELL_OTHER); assertEquals("Keep other", controller.state.value.draft)
        } finally { controller.close() }
    }
}

private class ShellDraftOperations : ConversationOperations {
    val promptIds = mutableListOf<SessionId>()
    var createResult: suspend () -> SessionSummary = { SessionSummary(SHELL_PARENT, 1L) }
    var promptResult: (suspend (SessionId) -> SessionPrompt)? = null
    override suspend fun list(connection: Connection) = emptyList<SessionSummary>()
    override suspend fun history(connection: Connection, id: SessionId) = emptyList<Message>()
    override suspend fun create(connection: Connection, options: CreateSessionOptions, agentOptions: AgentOptions) = createResult()
    override suspend fun prompt(connection: Connection, id: SessionId, message: Message): SessionPrompt {
        promptIds += id
        promptResult?.let { return it(id) }
        return object : SessionPrompt {
            override val receipt = SessionPromptReceipt(id, "controlled-accepted")
            override suspend fun awaitIdle() = Unit
        }
    }
    override suspend fun cancel(connection: Connection, id: SessionId, keepInbox: Boolean) = Unit
}

private val SHELL_HOST = HostId("shell-panels-controlled-host")
private val SHELL_PARENT = SessionId("shell-parent")
private val SHELL_OTHER = SessionId("shell-other")
private val SHELL_CHILD = SessionId("shell-verified-child")
private val SHELL_RUNNING = BackgroundProcessId("shell-running-process")
private val SHELL_EXITED = BackgroundProcessId("shell-exited-process")
private const val SHELL_PARENT_HISTORY = "Controlled parent history in the production shell"
private const val SHELL_OTHER_HISTORY = "Distinct other thread history in the production shell"
private const val SHELL_CHILD_HISTORY = "Lineage-verified child transcript from SessionApi"
private const val SHELL_RUNNING_OUTPUT = "Controlled running process output — 等待 stdin"
private const val SHELL_DRAFT = "Retained parent draft — 面板关闭后继续编辑"

private fun shellPanelsMessage(id: String, text: String) = Message(
    id = id, role = MessageRole.ASSISTANT, content = listOf(TextContent(text)),
    source = ModelMessageSource("controlled-provider", "controlled-model"),
)

private class ShellPanelsConnection(override val session: ShellPanelsSessionApi) : Connection {
    val descriptionReads = CopyOnWriteArrayList<HostId>()
    private val description = HostDescription(
        protocolVersion = ProtocolVersion.Current, hostId = SHELL_HOST, displayName = "Controlled shell test host",
    )
    override val host: HostApi = object : HostApi {
        override suspend fun describe(): ApiResult<HostDescription> {
            descriptionReads += description.hostId
            return ApiResult(ApiResult.SUCCESS_CODE, "", description)
        }
    }
}

/** Only transport services are doubled. No prebuilt ThreadPanelsState or UI fixtures are injected. */
private class ShellPanelsSessionApi : SessionApi {
    val listReads = CopyOnWriteArrayList<String>()
    val historyReads = CopyOnWriteArrayList<SessionId>()
    val inventoryReads = CopyOnWriteArrayList<SessionId>()
    val outputReads = CopyOnWriteArrayList<Pair<SessionId, BackgroundProcessId>>()
    val descendantReads = CopyOnWriteArrayList<SessionId>()
    val childReads = CopyOnWriteArrayList<Pair<SessionId, SessionId>>()
    val mutations = CopyOnWriteArrayList<String>()
    private val inventory = listOf(
        BackgroundProcess(SHELL_RUNNING, SHELL_PARENT, "controlled-running-call", "controlled-read-loop",
            "/controlled/workspace", 1L, "controlled-incarnation", BackgroundProcessLifecycle.Running, BackgroundInputState.Open),
        BackgroundProcess(SHELL_EXITED, SHELL_PARENT, "controlled-exited-call", "controlled-finished-task",
            "/controlled/workspace", 2L, "controlled-incarnation", BackgroundProcessLifecycle.Exited(0), BackgroundInputState.Closed),
    )

    override suspend fun list(): List<SessionSummary> {
        listReads += "list"
        return listOf(SessionSummary(SHELL_PARENT, 2L), SessionSummary(SHELL_OTHER, 1L))
    }

    override suspend fun history(id: SessionId): List<Message> {
        historyReads += id
        return when (id) {
            SHELL_PARENT -> listOf(shellPanelsMessage("parent-history", SHELL_PARENT_HISTORY))
            SHELL_OTHER -> listOf(shellPanelsMessage("other-history", SHELL_OTHER_HISTORY))
            else -> error("Main history must only read listed parents: $id")
        }
    }

    override suspend fun backgroundProcesses(parent: SessionId): BackgroundProcessesResult {
        inventoryReads += parent
        check(parent == SHELL_PARENT || parent == SHELL_OTHER)
        return BackgroundProcessesResult.Available(parent, if (parent == SHELL_PARENT) inventory else emptyList())
    }

    override suspend fun readBackgroundOutput(parent: SessionId, process: BackgroundProcessId): BackgroundOutputResult {
        outputReads += parent to process
        check(parent == SHELL_PARENT) { "Output owner must be the selected parent" }
        val item = inventory.single { it.process == process }
        return BackgroundOutputResult.Available(parent, process, item.ownerSession,
            if (process == SHELL_RUNNING) SHELL_RUNNING_OUTPUT else "Controlled exited process output",
            revision = 1L, truncated = false, lifecycle = item.lifecycle, inputState = item.inputState)
    }

    override suspend fun descendants(parent: SessionId): SessionDescendantsResult {
        descendantReads += parent
        check(parent == SHELL_PARENT || parent == SHELL_OTHER)
        return SessionDescendantsResult.Available(parent, if (parent == SHELL_PARENT) listOf(
            SessionDescendant(SHELL_CHILD, SHELL_PARENT, listOf(SHELL_PARENT, SHELL_CHILD),
                DescendantStatus.RUNNING, model = "controlled-model", objective = "Review the shell"),
        ) else emptyList())
    }

    override suspend fun readChildHistory(parent: SessionId, child: SessionId): ChildHistoryResult {
        childReads += parent to child
        check(parent == SHELL_PARENT && child == SHELL_CHILD) { "Child read must preserve verified lineage" }
        return ChildHistoryResult.Available(parent, child, listOf(parent, child),
            listOf(shellPanelsMessage("verified-child-history", SHELL_CHILD_HISTORY)))
    }

    private fun unexpectedMutation(operation: String): Nothing {
        mutations += operation
        error("Shell panel read-only scenario must not invoke $operation")
    }

    override suspend fun create(id: SessionId?, options: CreateSessionOptions, agentOptions: AgentOptions): SessionSummary =
        unexpectedMutation("create")
    override suspend fun prompt(id: SessionId, message: Message): SessionPrompt = unexpectedMutation("prompt:${id.value}")
    override suspend fun cancel(id: SessionId, keepInbox: Boolean): Unit = unexpectedMutation("cancel:${id.value}")
    override suspend fun writeBackgroundInput(parent: SessionId, process: BackgroundProcessId, input: String): BackgroundInputResult =
        unexpectedMutation("input:${parent.value}:${process.value}")
    override suspend fun closeBackgroundInput(parent: SessionId, process: BackgroundProcessId): BackgroundControlResult =
        unexpectedMutation("eof:${parent.value}:${process.value}")
    override suspend fun cancelBackgroundProcess(parent: SessionId, process: BackgroundProcessId): BackgroundControlResult =
        unexpectedMutation("cancel-process:${parent.value}:${process.value}")
}
