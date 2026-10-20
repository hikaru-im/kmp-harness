@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package im.hikaru.harness.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import im.hikaru.contracts.harness.identity.HostDescription
import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.contracts.harness.protocol.ProtocolVersion
import im.hikaru.harness.agent.AgentPlugin
import im.hikaru.harness.agent.loop.AgentLoopPlugin
import im.hikaru.harness.api.gateway.ApiGateway
import im.hikaru.harness.api.gateway.host.HostApiPlugin
import im.hikaru.harness.api.gateway.host.registerHostApi
import im.hikaru.harness.api.gateway.session.registerSessionApi
import im.hikaru.harness.client.app.ClientUiSchema
import im.hikaru.harness.client.app.DesktopClientEntries
import im.hikaru.harness.client.app.DesktopClientPluginCatalog
import im.hikaru.harness.client.app.HarnessApp
import im.hikaru.harness.client.app.HarnessDestination
import im.hikaru.harness.client.connection.Connection
import im.hikaru.harness.client.connection.ConnectionTransport
import im.hikaru.harness.client.connection.TextContent
import im.hikaru.harness.client.scheduling.*
import im.hikaru.harness.client.settings.ModelSettingsRepository
import im.hikaru.harness.credentials.CredentialsPlugin
import im.hikaru.harness.credentials.local.LocalCredentialsConfig
import im.hikaru.harness.credentials.local.LocalCredentialsProvider
import im.hikaru.harness.desktop.connection.HostEventStream
import im.hikaru.harness.desktop.connection.LocalConnection
import im.hikaru.harness.desktop.scheduling.*
import im.hikaru.harness.desktop.settings.DesktopModelSettingsBackend
import im.hikaru.harness.llm.*
import im.hikaru.harness.runtime.Runtime
import im.hikaru.harness.session.*
import im.hikaru.harness.session.api.SessionApiPlugin
import im.hikaru.harness.session.persistence.*
import im.hikaru.harness.settings.SettingsKey
import im.hikaru.harness.settings.SettingsPlugin
import im.hikaru.harness.settings.file.FileSettingsDocumentStore
import im.hikaru.harness.settings.file.SettingsFileConfig
import im.hikaru.harness.settings.settingsNamespace
import im.hikaru.harness.ui.foundation.HarnessThemeMode
import im.hikaru.harness.ui.slot.ClientHarnessHost
import java.awt.Component
import java.awt.event.KeyEvent as AwtKeyEvent
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.*
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image

/** Every normal operation reaches production through mouse, keyboard or wheel input. */
class ScheduledCronPageIntegrationTest {
    @Test fun realPageCreatesPausedRunsOnceAndOpensPersistedThread() = cronUi { f ->
        val task = createPaused(f)
        assertEquals(ScheduledTaskStatus.Paused, task.status)
        val accepted = runFromPage(f)
        assertNull(accepted.nominalAt)
        assertNotNull(accepted.receiptId)
        assertNotEquals(accepted.inputMessageId, accepted.receiptId)
        val completed = completeFromPage(f, accepted)
        assertEquals(1, f.snapshot().runs.size)
        assertEquals(1, f.adapter.streams.get())
        openRealThread(f, completed)
    }

    @Test fun realPageActivationDueOccurrenceAndPausePersistWithoutFutureDelivery() = cronUi { f ->
        val task = createPaused(f)
        reveal("scheduled-pause")
        mouse("scheduled-pause")
        waitUntil(timeoutMillis = 10_000) { f.snapshot().tasks.single().status == ScheduledTaskStatus.Active }
        val cursor = f.snapshot().cursors.single()
        assertEquals(task.id, cursor.taskId)
        assertTrue(cursor.nextNominalAt > f.clock.nowEpochMillis())
        assertEquals(ScheduledTaskStatus.Active, f.freshSnapshot().tasks.single().status)
        f.clock.set(cursor.nextNominalAt)
        runBlocking { f.executor.tick() }
        val accepted = f.awaitRun { it.phase == ScheduledRunPhase.Accepted }
        assertEquals(cursor.nextNominalAt, accepted.nominalAt)
        assertEquals("due:${task.id}:${cursor.nextNominalAt}", accepted.requestId)
        val options = runBlocking { withTimeout(10_000) { f.adapter.started.await() } }
        assertEquals("scripted", options.provider)
        assertEquals("test", options.model)
        assertEquals(1, f.adapter.streams.get())
        assertFalse(f.adapter.finish.isCompleted)
        assertNull(accepted.terminal)
        repeat(3) { runBlocking { f.executor.tick() } }
        assertEquals(1, f.snapshot().runs.size)
        assertEquals(1, f.adapter.streams.get())
        completeFromPage(f, accepted)
        reveal("scheduled-pause")
        mouse("scheduled-pause")
        waitUntil(timeoutMillis = 10_000) { f.snapshot().tasks.single().status == ScheduledTaskStatus.Paused }
        assertEquals(ScheduledTaskStatus.Paused, f.freshSnapshot().tasks.single().status)
        val before = f.snapshot()
        f.clock.set(cursor.nextNominalAt + 3 * 3_600_000L)
        repeat(3) { runBlocking { f.executor.tick() } }
        assertEquals(before.runs, f.snapshot().runs)
        assertEquals(1, f.adapter.streams.get())
    }

    @Test fun leavingPageRetainsApplicationOwnerAndOpenTurnWithoutDuplicateDelivery() = cronUi { f ->
        val task = createPaused(f)
        val executor = f.executor
        val accepted = runFromPage(f)
        mouse("rail-home")
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag("scheduled-page", true).fetchSemanticsNodes().isEmpty() }
        assertSame(executor, f.executor)
        assertFalse(f.execution.owner.state.value.invalidated)
        assertTrue(f.executor.state.value.accepting)
        assertEquals(accepted.id, f.snapshot().runs.single().id)
        assertEquals(1, f.adapter.streams.get())
        // The rail is always mounted, including at this viewport, so 定时任务 is one click away.
        mouse("rail-scheduled")
        selectSavedTask(task)
        assertSame(executor, f.executor)
        completeFromPage(f, accepted)
        assertEquals(1, f.snapshot().runs.size)
        assertEquals(1, f.adapter.streams.get())
    }

    @Test fun losingVerifiedBindingPermanentlyFencesOldOwnerEvenAfterConnectionReturns() = cronUi { f ->
        val task = createPaused(f)
        val before = f.snapshot()
        runOnIdle { f.visibleConnection.value = null }
        waitUntil(timeoutMillis = 10_000) { f.execution.owner.state.value.invalidated }
        assertIs<ScheduledClaimResult.Refused>(runBlocking { f.executor.runNow(task.id, "old-disconnected") })
        assertIs<ScheduledActivationResult.Refused>(runBlocking { f.executor.activate(task.id, task.revision, true) })
        runOnIdle { f.visibleConnection.value = f.connection }
        waitForScheduledPage()
        selectSavedTask(task)
        assertIs<ScheduledStartResult.Unavailable>(runBlocking { f.execution.start() })
        assertIs<ScheduledStartResult.Unavailable>(runBlocking { f.execution.owner.recover(f.execution.binding, 10_000) })
        assertEquals(before, f.snapshot())
        assertEquals(0, f.adapter.streams.get())
        assertTrue(runBlocking { f.database.dao.listSessions() }.isEmpty())
        assertInvalidatedExecution()
    }

    @Test fun sameHostReplacementConnectionCannotBorrowPreviousOwnerExecutionCapability() = cronUi { f ->
        val task = createPaused(f)
        val before = f.snapshot()
        val replacement = LocalConnection(f.gateway)
        assertNotSame(f.connection, replacement)
        assertEquals(f.host, runBlocking { replacement.host.describe() }.data?.hostId)
        runOnIdle { f.visibleConnection.value = replacement }
        waitUntil(timeoutMillis = 10_000) { f.execution.owner.state.value.invalidated }
        waitForScheduledPage()
        selectSavedTask(task)
        assertIs<ScheduledClaimResult.Refused>(runBlocking { f.executor.runNow(task.id, "old-replaced") })
        assertIs<ScheduledActivationResult.Refused>(runBlocking { f.executor.activate(task.id, task.revision, true) })
        assertIs<ScheduledStartResult.Unavailable>(runBlocking { f.execution.start() })
        assertEquals(before, f.snapshot())
        assertEquals(0, f.adapter.streams.get())
        assertTrue(runBlocking { replacement.session.list() }.isEmpty())
        assertInvalidatedExecution()
    }

    @Test fun capturesTwelveRealPausedAcceptedCompletedPagesAndReachableThreadHistory() {
        val directory = System.getenv("HARNESS_SCHEDULED_CRON_CAPTURE_DIR")?.let(::File)
            ?: Files.createTempDirectory("harness-cron-captures-").toFile()
        check(directory.mkdirs() || directory.isDirectory)
        val captures = mutableListOf<JsonObject>()
        for ((width, height) in listOf(600 to 800, 1440 to 900)) {
            for (theme in listOf(HarnessThemeMode.Light, HarnessThemeMode.Dark)) {
                cronUi(width, height, theme) { f ->
                    val task = createPaused(f)
                    reveal("scheduled-run-now")
                    onNodeWithTag("scheduled-run-now", true).assertIsEnabled()
                    captures += capturePage(directory, width, height, theme, "paused", f, task, null)
                    val accepted = runFromPage(f)
                    reveal("scheduled-page-open-run:${accepted.id}")
                    captures += capturePage(directory, width, height, theme, "accepted", f, task, accepted)
                    val completed = completeFromPage(f, accepted)
                    reveal("scheduled-page-open-run:${completed.id}")
                    captures += capturePage(directory, width, height, theme, "completed", f, task, completed)
                    openRealThread(f, completed)
                }
            }
        }
        assertEquals(12, captures.size)
        assertEquals(12, captures.map { it["name"] }.distinct().size)
        File(directory, "matrix.json").writeText(Json { prettyPrint = true }.encodeToString(JsonArray.serializer(), JsonArray(captures)))
        println("SCHEDULED CRON MATRIX: ${directory.absolutePath}; captures=${captures.size}")
    }
}

private fun cronUi(width: Int = 600, height: Int = 800, theme: HarnessThemeMode = HarnessThemeMode.Light,
    block: ComposeUiTest.(CronHarness) -> Unit) {
    val f = runBlocking { CronHarness.create() }
    try {
        assertEquals(ScheduledStartResult.Started, runBlocking { f.execution.start() })
        runSkikoComposeUiTest(size = Size(width.toFloat(), height.toFloat())) {
            setContent { CronApp(f, theme) }
            waitForScheduledPage()
            block(f)
        }
    } finally { runBlocking { f.close() } }
}

@Composable private fun CronApp(f: CronHarness, theme: HarnessThemeMode) {
    // The titlebar toggle is window chrome this bare app does not mount; the fixtures dismiss the
    // floating navigator with ⇧⌘S, so the app needs the macOS chord bindings.
    val shortcuts = remember { loadedMacShortcuts() }
    CompositionLocalProvider(LocalDensity provides Density(1f)) {
        Box(Modifier.fillMaxSize().semantics { testTag = "cron-capture-viewport" }) {
            WithWindowNavigatorToggle {
                HarnessApp(
                    connection = f.visibleConnection.value, connectionTransport = ConnectionTransport.Local,
                    sessionEvents = f.hostEvents.events, uiSnapshot = f.clientHost.snapshot,
                    initialDestination = HarnessDestination.ScheduledTasks, themeMode = theme,
                    modelSettingsRepository = f.models, scheduledStore = f.execution.store,
                    scheduleCalendar = f.execution.calendar, scheduledExecutorConnection = f.execution.connection,
                    scheduledExecutorOwner = f.execution.owner, scheduledExecutionRecoveryEnabled = f.execution.recoveryEnabled,
                    shortcutsRepository = shortcuts,
                )
            }
        }
    }
}

private fun ComposeUiTest.createPaused(f: CronHarness): ScheduledTask {
    waitForScheduledPage()
    if (onAllNodesWithTag("scheduled-new-task", true).fetchSemanticsNodes().isEmpty()) {
        toggleNavigator()
    }
    waitForTag("scheduled-new-task")
    mouse("scheduled-new-task")
    if (onAllNodesWithTag("navigator-overlay", true).fetchSemanticsNodes().isNotEmpty()) {
        toggleNavigator()
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag("navigator-overlay", true).fetchSemanticsNodes().isEmpty() }
    }
    waitForTag("scheduled-name")
    reveal("scheduled-name")
    typeAscii("scheduled-name", "local scheduled review")
    reveal("scheduled-prompt")
    typeAscii("scheduled-prompt", CronHarness.prompt)
    reveal("scheduled-target")
    onNodeWithTag("scheduled-target", true).assertIsEnabled()
    mouse("scheduled-target")
    waitForTag("scheduled-target-option:0")
    mouse("scheduled-target-option:0")
    reveal("scheduled-model")
    waitUntil(timeoutMillis = 10_000) { !onNodeWithTag("scheduled-model", true).fetchSemanticsNode().config.contains(SemanticsProperties.Disabled) }
    mouse("scheduled-model")
    waitForTag("scheduled-model-option:0")
    mouse("scheduled-model-option:0")
    reveal("scheduled-frequency")
    mouse("scheduled-frequency")
    waitForTag("scheduled-frequency-option:0")
    rootFor(onNodeWithTag("scheduled-frequency-option:0", true)).performKeyInput { pressKey(Key.MoveHome); pressKey(Key.Enter) }
    waitForTag("scheduled-interval")
    onNodeWithTag("scheduled-interval", true).assertTextContains("1")
    reveal("scheduled-save")
    onNodeWithTag("scheduled-save", true).assertIsEnabled()
    mouse("scheduled-save")
    waitForTag("scheduled-detail-name")
    waitUntil(timeoutMillis = 10_000) { f.snapshotOrNull()?.tasks?.size == 1 }
    val task = f.freshSnapshot().tasks.single()
    assertEquals("local scheduled review", task.name)
    assertEquals(CronHarness.prompt, task.prompt)
    assertEquals(ScheduledTarget.Cron(ScheduledDestination.Projectless), task.target)
    assertEquals(ScheduledModelRef("scripted", "test"), task.model)
    assertIs<ScheduledRule.Hourly>(task.schedule)
    assertEquals(ScheduledTaskStatus.Paused, task.status)
    assertTrue(f.snapshot().runs.isEmpty())
    assertEquals(0, f.adapter.streams.get())
    assertTrue(runBlocking { f.database.dao.listSessions() }.isEmpty())
    onNodeWithTag("scheduled-detail-prompt", true).assertTextEquals(CronHarness.prompt)
    return task
}

private fun ComposeUiTest.selectSavedTask(task: ScheduledTask) {
    waitForScheduledPage()
    if (onAllNodesWithTag("scheduled-task:${task.id}", true).fetchSemanticsNodes().isEmpty()) {
        toggleNavigator()
    }
    waitForTag("scheduled-task:${task.id}")
    mouse("scheduled-task:${task.id}")
    if (onAllNodesWithTag("navigator-overlay", true).fetchSemanticsNodes().isNotEmpty()) {
        toggleNavigator()
        waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag("navigator-overlay", true).fetchSemanticsNodes().isEmpty() }
    }
    waitForTag("scheduled-detail-name")
    onNodeWithTag("scheduled-detail-name", true).assertTextEquals(task.name)
}

private fun ComposeUiTest.assertInvalidatedExecution() {
    reveal("scheduled-execution-unavailable")
    onNodeWithTag("scheduled-execution-unavailable", true).assertExists().assertIsDisplayed()
    reveal("scheduled-run-now")
    onNodeWithTag("scheduled-run-now", true).assertIsDisplayed().assertIsNotEnabled()
    reveal("scheduled-pause")
    onNodeWithTag("scheduled-pause", true).assertIsDisplayed().assertIsNotEnabled()
    onNodeWithTag("scheduled-execution-recover", true).assertDoesNotExist()
}

private fun ComposeUiTest.runFromPage(f: CronHarness): ScheduledRun {
    reveal("scheduled-run-now")
    onNodeWithTag("scheduled-run-now", true).assertIsEnabled()
    mouse("scheduled-run-now")
    val accepted = f.awaitRun { it.phase == ScheduledRunPhase.Accepted }
    val options = runBlocking { withTimeout(10_000) { f.adapter.started.await() } }
    assertEquals("scripted", options.provider)
    assertEquals("test", options.model)
    assertEquals(1, f.adapter.streams.get())
    assertFalse(f.adapter.finish.isCompleted)
    assertNull(accepted.terminal)
    waitForTag("scheduled-page-run:${accepted.id}")
    onNodeWithTag("scheduled-run-now", true).assertIsNotEnabled()
    return accepted
}

private fun ComposeUiTest.completeFromPage(f: CronHarness, accepted: ScheduledRun): ScheduledRun {
    f.adapter.finish.complete(Unit)
    val terminal = f.awaitRun { it.phase == ScheduledRunPhase.Succeeded }
    assertEquals(accepted.id, terminal.id)
    assertEquals(accepted.inputMessageId, terminal.inputMessageId)
    assertEquals(accepted.sessionRef, terminal.sessionRef)
    runBlocking { f.assertExactPersistedTerminal(terminal) }
    val completedTitle = hasText("${terminal.task.name} · 已成功") and
        hasAnyAncestor(hasTestTag("scheduled-page-run:${terminal.id}"))
    waitUntil(timeoutMillis = 10_000) {
        onAllNodes(completedTitle, true).fetchSemanticsNodes().size == 1
    }
    onNode(completedTitle, true).assertExists()
    return terminal
}

private fun ComposeUiTest.openRealThread(f: CronHarness, run: ScheduledRun) {
    reveal("scheduled-page-open-run:${run.id}")
    onNodeWithTag("scheduled-page-open-run:${run.id}", true).assertIsEnabled()
    mouse("scheduled-page-open-run:${run.id}")
    val history = runBlocking { f.connection.session.history(assertNotNull(run.sessionRef)) }
    val input = history.single { it.id == run.inputMessageId }
    assertEquals(CronHarness.prompt, input.content.filterIsInstance<TextContent>().single().text)
    val answer = history.single { message -> message.content.filterIsInstance<TextContent>().any { it.text == CronHarness.answer } }
    waitForTag("message:${run.inputMessageId}:content:0")
    onNodeWithTag("message:${run.inputMessageId}:content:0", true).assertTextEquals(CronHarness.prompt)
    val answerIndex = answer.content.indexOfFirst { it is TextContent && it.text == CronHarness.answer }
    val answerTag = "message:${answer.id}:content:$answerIndex"
    revealThreadAnswer(answerTag)
    // The tag sits on the markdown selection container, so the rendered text is its descendant
    // rather than the tagged node's own property.
    onNode(hasText(CronHarness.answer) and hasAnyAncestor(hasTestTag(answerTag)), true)
        .assertExists().assertIsDisplayed()
    onNodeWithTag("composer-column", true).assertExists()
    onNodeWithTag("scheduled-page", true).assertDoesNotExist()
}

private fun ComposeUiTest.toggleNavigator() {
    // The drawer is opened and dismissed from the window's navigator toggle. The ⇧⌘S chord would do
    // the same, but desktop routes key input to a focused form field, and this flow types into the
    // scheduler fields before it needs the drawer again.
    mouse("fixture-navigator-toggle")
}

/** Lazy history may not mount the answer until real wheel input exposes the next message. */
private fun ComposeUiTest.revealThreadAnswer(tag: String) {
    waitForTag("live-thread-timeline")
    val deadline = System.nanoTime() + 10_000_000_000L
    while (System.nanoTime() < deadline) {
        settle()
        val timeline = onNodeWithTag("live-thread-timeline", true)
        val node = timeline.fetchSemanticsNode()
        val root = rootFor(timeline)
        val viewport = node.boundsInRoot.intersect(root.fetchSemanticsNode().boundsInRoot)
        assertTrue(viewport.width > 0f && viewport.height > 0f)
        val answer = onAllNodesWithTag(tag, true).fetchSemanticsNodes().singleOrNull()
        val distance = if (answer == null) viewport.height * .6f else {
            val required = layout(answer)
            val visible = answer.boundsInRoot.intersect(viewport)
            if (visible.width + 1f >= required.width && visible.height + 1f >= required.height) return
            when {
                required.bottom > viewport.bottom + 1f -> minOf(required.bottom - viewport.bottom, viewport.height * .6f)
                required.top < viewport.top - 1f -> -minOf(viewport.top - required.top, viewport.height * .6f)
                else -> error("Thread answer is clipped horizontally: layout=$required viewport=$viewport visible=$visible")
            }
        }
        val point = viewport.center - root.fetchSemanticsNode().boundsInRoot.topLeft
        root.performMouseInput { moveTo(point); advanceEventTime(16); scroll(distance / wheelUnits(node)) }
    }
    error("Real wheel did not expose the persisted Thread answer within ten seconds: $tag")
}

/** HarnessApp owns the shell; ScheduledManagementPage owns its tagged descendant. */
private fun ComposeUiTest.waitForScheduledPage() {
    val pageTag = hasTestTag("scheduled-page")
    val shell = pageTag and !hasAnyAncestor(pageTag)
    val content = pageTag and hasAnyAncestor(shell)
    waitUntil(timeoutMillis = 10_000) {
        onAllNodes(shell, true).fetchSemanticsNodes().size == 1 &&
            onAllNodes(content, true).fetchSemanticsNodes().size == 1
    }
    settle()
    onNode(shell, true).assertExists()
    onNode(content, true).assertExists()
}

private fun ComposeUiTest.waitForTag(tag: String) {
    waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(tag, true).fetchSemanticsNodes().size == 1 }
    settle()
}

private fun ComposeUiTest.settle() { mainClock.advanceTimeByFrame(); waitForIdle() }

private fun ComposeUiTest.rootFor(target: SemanticsNodeInteraction): SemanticsNodeInteraction {
    val root = generateSequence(target.fetchSemanticsNode()) { it.parent }.last()
    return onNode(SemanticsMatcher("Measured owning root ${root.id}") { it.id == root.id }, true)
}

private fun layout(node: SemanticsNode) = Rect(node.positionInRoot, Size(node.size.width.toFloat(), node.size.height.toFloat()))

private fun ComposeUiTest.mouse(tag: String) = mouseNode(onNodeWithTag(tag, true))

private fun ComposeUiTest.mouseNode(target: SemanticsNodeInteraction) {
    settle()
    target.assertIsDisplayed()
    val root = rootFor(target)
    root.performMouseInput { moveTo(target.fetchSemanticsNode().boundsInRoot.center - root.fetchSemanticsNode().boundsInRoot.topLeft) }
    settle()
    val node = target.fetchSemanticsNode()
    val required = layout(node)
    val visible = node.boundsInRoot.intersect(root.fetchSemanticsNode().boundsInRoot)
    assertTrue(visible.width + 1f >= required.width && visible.height + 1f >= required.height,
        "Pointer control must be fully reachable: layout=$required visible=$visible")
    val point = visible.center - root.fetchSemanticsNode().boundsInRoot.topLeft
    root.performMouseInput { moveTo(point); advanceEventTime(16); click(point) }
    settle()
}

@OptIn(androidx.compose.ui.InternalComposeUiApi::class)
private fun ComposeUiTest.typeAscii(tag: String, text: String) {
    mouse(tag)
    onNodeWithTag(tag, true).assertIsFocused()
    val skiko = this as? SkikoComposeUiTest ?: error("Real Desktop character events require SkikoComposeUiTest")
    val eventSource = object : Component() {}
    val deadline = System.nanoTime() + 10_000_000_000L
    val alphabet = listOf(Key.A, Key.B, Key.C, Key.D, Key.E, Key.F, Key.G, Key.H, Key.I, Key.J, Key.K, Key.L, Key.M,
        Key.N, Key.O, Key.P, Key.Q, Key.R, Key.S, Key.T, Key.U, Key.V, Key.W, Key.X, Key.Y, Key.Z)
    text.forEachIndexed { index, character ->
        check(System.nanoTime() < deadline) { "Real character input exceeded ten seconds: $tag at $index" }
        require(character == ' ' || character in 'a'..'z')
        onNodeWithTag(tag, true).assertIsFocused()
        val key = if (character == ' ') Key.Spacebar else alphabet[character.code - 'a'.code]
        val root = rootFor(onNodeWithTag(tag, true))
        root.performKeyInput { keyDown(key) }
        try {
            // Desktop commits printable KEY_TYPED events; Down/Up alone carry no native event.
            runOnUiThread {
                val nativeEvent = AwtKeyEvent(eventSource, AwtKeyEvent.KEY_TYPED,
                    System.currentTimeMillis(), 0, AwtKeyEvent.VK_UNDEFINED, character)
                skiko.scene.sendKeyEvent(KeyEvent(key = key, type = KeyEventType.Unknown,
                    codePoint = character.code, nativeEvent = nativeEvent))
            }
        } finally {
            root.performKeyInput { keyUp(key) }
        }
        settle()
        onNodeWithTag(tag, true).assertIsFocused()
        assertEquals(text.take(index + 1),
            onNodeWithTag(tag, true).fetchSemanticsNode().config[SemanticsProperties.EditableText].text,
            "Real character prefix after event $index on $tag")
    }
    settle()
    assertEquals(text, onNodeWithTag(tag, true).fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
}

/** Match the desktop pixel conversion; the Skiko dispatcher supplies scrollAmount = 1. */
private fun wheelUnits(node: SemanticsNode): Float = when (val os = System.getProperty("os.name").orEmpty()) {
    "Mac OS X" -> 10f * node.layoutInfo.density.density
    else -> if (os.startsWith("Linux")) sqrt(node.size.height.toFloat()) else node.size.height / 20f
}

private fun ComposeUiTest.reveal(tag: String) {
    waitForTag(tag)
    val deadline = System.nanoTime() + 10_000_000_000L
    while (System.nanoTime() < deadline) {
        settle()
        val list = onNodeWithTag("scheduled-content-scroll", true)
        val listNode = list.fetchSemanticsNode()
        val root = rootFor(list)
        val viewport = listNode.boundsInRoot.intersect(root.fetchSemanticsNode().boundsInRoot)
        assertTrue(viewport.width > 0f && viewport.height > 0f)
        val item = onNodeWithTag(tag, true).fetchSemanticsNode()
        val bounds = layout(item)
        val visible = item.boundsInRoot.intersect(viewport)
        if (visible.width + 1f >= bounds.width && visible.height + 1f >= bounds.height) return
        val distance = when {
            bounds.bottom > viewport.bottom + 1f -> minOf(bounds.bottom - viewport.bottom, viewport.height * .6f)
            bounds.top < viewport.top - 1f -> -minOf(viewport.top - bounds.top, viewport.height * .6f)
            else -> error("Control is clipped horizontally: $tag layout=$bounds viewport=$viewport visible=$visible")
        }
        val point = Offset(viewport.left + minOf(4f, viewport.width / 2), viewport.center.y) - root.fetchSemanticsNode().boundsInRoot.topLeft
        root.performMouseInput { moveTo(point); advanceEventTime(16); scroll(distance / wheelUnits(listNode)) }
    }
    error("Real wheel did not expose $tag within ten seconds")
}

private fun ComposeUiTest.wheelToEnd() {
    val deadline = System.nanoTime() + 10_000_000_000L
    while (System.nanoTime() < deadline) {
        settle()
        val list = onNodeWithTag("scheduled-content-scroll", true)
        val node = list.fetchSemanticsNode()
        val axis = node.config[SemanticsProperties.VerticalScrollAxisRange]
        val remaining = axis.maxValue() - axis.value()
        if (remaining <= 1f) return
        val root = rootFor(list)
        val viewport = node.boundsInRoot.intersect(root.fetchSemanticsNode().boundsInRoot)
        assertTrue(viewport.width > 0f && viewport.height > 0f)
        val point = Offset(viewport.left + minOf(4f, viewport.width / 2), viewport.center.y) - root.fetchSemanticsNode().boundsInRoot.topLeft
        root.performMouseInput { moveTo(point); advanceEventTime(16); scroll(minOf(remaining, viewport.height * .6f) / wheelUnits(node)) }
    }
    error("Real wheel did not reach the page history end within ten seconds")
}

private fun geometry(node: SemanticsNode): JsonObject = buildJsonObject {
    val root = generateSequence(node) { it.parent }.last()
    put("id", node.id); put("rootId", root.id)
    put("positionInRoot", node.positionInRoot.toString()); put("size", node.size.toString())
    put("layout", layout(node).toString()); put("visible", node.boundsInRoot.toString())
    put("rootOrigin", root.boundsInRoot.topLeft.toString())
    put("disabled", node.config.contains(SemanticsProperties.Disabled))
    node.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)?.let {
        put("scrollValue", it.value()); put("scrollMax", it.maxValue())
    }
}

private fun ComposeUiTest.capturePage(directory: File, width: Int, height: Int, theme: HarnessThemeMode,
    stage: String, f: CronHarness, task: ScheduledTask, run: ScheduledRun?): JsonObject {
    if (run != null) wheelToEnd()
    settle()
    val snapshot = f.freshSnapshot()
    assertEquals(task.id, snapshot.tasks.single().id)
    assertEquals(CronHarness.prompt, snapshot.tasks.single().prompt)
    onNodeWithTag("scheduled-detail-prompt", true).assertTextEquals(CronHarness.prompt)
    val control = if (run == null) "scheduled-run-now" else "scheduled-page-open-run:${run.id}"
    val node = onNodeWithTag(control, true).assertIsEnabled().fetchSemanticsNode()
    val scroll = onNodeWithTag("scheduled-content-scroll", true).fetchSemanticsNode()
    val visible = node.boundsInRoot.intersect(scroll.boundsInRoot)
    assertTrue(visible.width + 1f >= node.size.width && visible.height + 1f >= node.size.height,
        "Capture must show reachable control $control: layout=${layout(node)} visible=$visible")
    when (stage) {
        "paused" -> { assertTrue(snapshot.runs.isEmpty()); assertEquals(ScheduledTaskStatus.Paused, snapshot.tasks.single().status) }
        "accepted" -> { assertEquals(ScheduledRunPhase.Accepted, snapshot.runs.single().phase); assertFalse(f.adapter.finish.isCompleted) }
        "completed" -> { assertEquals(ScheduledRunPhase.Succeeded, snapshot.runs.single().phase); runBlocking { f.assertExactPersistedTerminal(assertNotNull(run)) } }
        else -> error("Unknown capture stage $stage")
    }
    if (run != null) {
        assertEquals(run.id, snapshot.runs.single().id)
        assertEquals(run.inputMessageId, snapshot.runs.single().inputMessageId)
        val axis = scroll.config[SemanticsProperties.VerticalScrollAxisRange]
        assertTrue(abs(axis.maxValue() - axis.value()) <= 1f, "Last run/open control must be at the real scroll end")
    }
    val name = "${width}x${height}-${theme.name.lowercase()}-$stage"
    val bitmap = onNodeWithTag("cron-capture-viewport", true).captureToImage()
    val bytes = Image.makeFromBitmap(bitmap.asSkiaBitmap()).use { image ->
        image.encodeToData(EncodedImageFormat.PNG)?.use { it.bytes } ?: error("PNG encoding failed")
    }
    val png = File(directory, "$name.png").also { it.writeBytes(bytes) }
    val decoded = assertNotNull(ImageIO.read(png))
    assertEquals(width, decoded.width); assertEquals(height, decoded.height)
    return buildJsonObject {
        put("name", name); put("path", png.absolutePath); put("width", decoded.width); put("height", decoded.height)
        put("sha256", MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
        put("theme", theme.name); put("stage", stage); put("taskId", task.id); put("taskStatus", snapshot.tasks.single().status.name)
        put("runId", run?.id); put("inputMessageId", run?.inputMessageId); put("runPhase", run?.phase?.name)
        put("control", geometry(node)); put("scroll", geometry(scroll)); put("controlInViewport", visible.toString())
        put("prompt", geometry(onNodeWithTag("scheduled-detail-prompt", true).fetchSemanticsNode()))
    }
}

/** Shared only by these contract suites; the temporary home owns every file and service. */
internal class CronHarness private constructor(
    val home: Path, val host: HostId, val runtime: Runtime, val database: SessionDatabaseAccess,
    val gateway: ApiGateway, val connection: LocalConnection, val adapter: CronControlledAdapter,
    val clock: CronClock, val execution: DesktopScheduledExecution, val executionScope: CoroutineScope,
    private val job: Job, val clientHost: ClientHarnessHost, val hostEvents: HostEventStream,
) {
    val visibleConnection = mutableStateOf<Connection?>(connection)
    val models = ModelSettingsRepository(DesktopModelSettingsBackend(runtime.context))
    val executor: ScheduledExecutor get() = execution.owner.state.value.executor
    val ledgerPath: Path = home.resolve("scheduled-tasks.json")
    val leaderPath: Path = home.resolve("scheduled-leader.lock")
    private var closed = false

    fun snapshotOrNull(): ScheduledSnapshot? = runBlocking {
        (execution.store.read(execution.binding.owner) as? ScheduledReadResult.Ready)?.snapshot
    }
    fun snapshot(): ScheduledSnapshot = assertNotNull(snapshotOrNull())
    fun freshSnapshot(): ScheduledSnapshot = runBlocking {
        assertIs<ScheduledReadResult.Ready>(DesktopScheduledStore(ledgerPath).read(execution.binding.owner)).snapshot
    }
    fun awaitRun(predicate: (ScheduledRun) -> Boolean): ScheduledRun = runBlocking {
        withTimeout(10_000) {
            var run = snapshotOrNull()?.runs?.singleOrNull()
            while (run == null || !predicate(run)) { delay(10); run = snapshotOrNull()?.runs?.singleOrNull() }
            run
        }
    }

    suspend fun assertExactPersistedTerminal(run: ScheduledRun) {
        val id = assertNotNull(run.sessionRef).value
        val rows = withTimeout(10_000) {
            var rows = database.dao.findEvents(id)
            while (rows.none { it.type == SessionEventNames.TURN_END }) { delay(10); rows = database.dao.findEvents(id) }
            rows
        }
        val userRow = rows.single { it.type == SessionEventNames.USER_MESSAGE }
        val user = Json.decodeFromString(UserMessageEvent.serializer(), userRow.data)
        assertEquals(run.inputMessageId, user.message.id.value)
        assertEquals(run.task.prompt, user.message.content.filterIsInstance<TextBlock>().single().text)
        val endRow = rows.single { it.type == SessionEventNames.TURN_END }
        val end = Json.decodeFromString(TurnEndEvent.serializer(), endRow.data)
        assertEquals(user.turn, end.turn)
        assertEquals(CompletedTurnEndReason, end.reason)
        assertEquals(userRow.seq, run.terminal?.userMessageSeq)
        assertEquals(endRow.seq, run.terminal?.turnEndSeq)
        assertEquals(user.turn, run.terminal?.turn)
        assertEquals("completed", run.terminal?.reason)
        assertEquals(ScheduledRunPhase.Succeeded, run.phase)
        val evidence = DesktopScheduledSessionEvidence(runtime.context, host).inspect(execution.binding, run)
        assertEquals(run.terminal, assertIs<ScheduledEvidenceResult.Terminal>(evidence).evidence)
        assertEquals(1, database.dao.listSessions().size)
    }

    suspend fun savePausedTask(): ScheduledTask {
        val task = ScheduledTask("assembly-task", 1, "assembly task", prompt,
            ScheduledTarget.Cron(ScheduledDestination.Projectless), ScheduledRule.Hourly(1, 0), ScheduledModelRef("scripted", "test"))
        assertEquals(ScheduledReadResult.Missing, execution.store.read(execution.binding.owner))
        assertIs<ScheduledWriteResult.Committed>(execution.store.compareAndSet(execution.binding.owner, 0,
            ScheduledSnapshot(execution.binding.owner, 1, listOf(task))) { true })
        return task
    }

    suspend fun close() {
        if (closed) return
        closed = true
        withContext(NonCancellable) {
            adapter.finish.complete(Unit)
            try { assertEquals(ScheduledShutdownResult.Drained, execution.owner.shutdown(10_000)) }
            finally {
                // Forced cleanup is confined to fixture resources and is never shutdown evidence.
                try { job.cancelAndJoin() } finally {
                    try { clientHost.close() } finally {
                        hostEvents.close()
                        try { runtime.context.dispose() } finally { check(home.toFile().deleteRecursively()) }
                    }
                }
            }
        }
    }

    companion object {
        const val answer = "controlled local answer"
        val prompt = "read source and report evidence ".repeat(14).trimEnd()
        fun verifiedConfig() = ScheduledExecutorConfig(verifiedCronExecution = true, tickIntervalMillis = 3_600_000,
            evidencePollMillis = 10, observationTimeoutMillis = 30_000)

        suspend fun create(
            config: ScheduledExecutorConfig = verifiedConfig(),
            description: HostDescription = HostDescription(ProtocolVersion.Current, HostId("cron-page-local"), "Cron page local host"),
            expectedHostId: HostId = HostId("cron-page-local"),
        ): CronHarness {
            val home = Files.createTempDirectory("harness-cron-page-").toRealPath()
            val runtime = Runtime()
            val job = SupervisorJob()
            val scope = CoroutineScope(job + Dispatchers.Default)
            val adapter = CronControlledAdapter()
            var clientHost: ClientHarnessHost? = null
            try {
                runtime.install(SettingsPlugin(FileSettingsDocumentStore(SettingsFileConfig(home.resolve("settings.json"), watch = false))))
                runtime.context.require(SettingsKey).register(settingsNamespace("llm-koog"))
                runtime.install(CredentialsPlugin(LocalCredentialsProvider(LocalCredentialsConfig(home.resolve("credentials.yaml"),
                    projectDir = home, userEnvPath = null, environment = { null }, watch = false))))
                runtime.install(SessionPlugin())
                runtime.install(LlmPlugin())
                runtime.install(AgentPlugin())
                runtime.context.llm.registerAdapter(listOf("scripted"), adapter)
                runtime.context.llm.registerConfigurableProviders(listOf(LlmConfigurableProvider(
                    "scripted", "Controlled local model", "llm-koog", listOf("providers", "scripted"))))
                runtime.install(AgentLoopPlugin())
                val factory = jvmSessionPersistenceDatabaseFactory(home.resolve("sessions.db").toFile())
                lateinit var database: SessionDatabaseAccess
                runtime.install(SessionPersistencePlugin(SessionPersistenceDatabaseFactory { factory.create().also { database = it } }))
                runtime.install(SessionApiPlugin())
                val gateway = ApiGateway()
                gateway.registerSessionApi(runtime.context)
                runtime.install(HostApiPlugin, description)
                gateway.registerHostApi(runtime.context)
                val connection = LocalConnection(gateway)
                val clock = CronClock(Instant.parse("2026-01-05T08:59:00Z").toEpochMilli())
                val execution = DesktopScheduledExecution(runtime.context, expectedHostId, connection,
                    DesktopScheduledStore(home.resolve("scheduled-tasks.json")), DesktopScheduleCalendar(),
                    home.resolve("scheduled-leader.lock"), scope, config, clock)
                clientHost = ClientHarnessHost.start(DesktopClientPluginCatalog, DesktopClientEntries, ClientUiSchema)
                return CronHarness(home, expectedHostId, runtime, database, gateway, connection, adapter, clock, execution,
                    scope, job, clientHost, HostEventStream(runtime.context, expectedHostId))
            } catch (failure: Throwable) {
                withContext(NonCancellable) {
                    adapter.finish.complete(Unit)
                    job.cancelAndJoin()
                    try { clientHost?.close() } finally {
                        try { runtime.context.dispose() } finally { check(home.toFile().deleteRecursively()) }
                    }
                }
                throw failure
            }
        }
    }
}

internal class CronClock(initial: Long) : ScheduledClock {
    private val value = AtomicLong(initial)
    override fun nowEpochMillis(): Long = value.get()
    fun set(now: Long) { value.set(now) }
}

internal class CronControlledAdapter : LlmAdapter {
    val started = CompletableDeferred<GenerateOptions>()
    val finish = CompletableDeferred<Unit>()
    val streams = AtomicInteger()
    override suspend fun listModels(provider: String) = listOf(LlmModelInfo(provider, "test", "Controlled local model"))
    override suspend fun resolveModel(provider: String, model: String): LlmResolvedModelInfo {
        require(provider == "scripted" && model == "test")
        return LlmResolvedModelInfo(provider, model, "Controlled local model")
    }
    override fun stream(options: GenerateOptions): Flow<StreamChunk> = flow {
        streams.incrementAndGet()
        emit(BlockStartChunk(0, "text"))
        emit(TextDeltaChunk(0, CronHarness.answer))
        started.complete(options)
        finish.await()
        emit(FinishChunk(StopFinishReason))
    }
}
