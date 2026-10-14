@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package im.hikaru.harness.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import im.hikaru.harness.client.git.*
import im.hikaru.harness.desktop.git.WorktreeObservationFixture
import im.hikaru.harness.session.SessionHeader
import im.hikaru.harness.session.sessions
import im.hikaru.harness.session.persistence.*
import im.hikaru.harness.ui.foundation.HarnessTheme
import im.hikaru.harness.ui.foundation.HarnessThemeMode
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.*

/** Successful reads use real Runtime/Room/Git/registry, never a fabricated header directory. */
class WorktreeSessionObservationInteractionTest {
    @Test
    fun realRefreshMovesFromEmptyToLiveDurableDualAndUnknownWithoutWritingSources() {
        val f = WorktreeObservationFixture()
        runBlocking { f.open() }
        val page = ObservationPage(f)
        try {
            runSkikoComposeUiTest(size = Size(640f, 800f)) {
                mountObservation(page)
                readyObservation(page)
                val empty = page.observation(f.root.toString())
                assertEquals(ManagedSessionObservationStatus.Observed, empty.status)
                assertTrue(empty.sessions.isEmpty())
                onNodeWithTag("managed-session-observation-status").assertTextEquals("本次未观察到关联会话")
                onNodeWithTag("managed-sessions").assertTextContains("保护仍未完整核验", substring = true)
                onNodeWithText("关联会话未知：尚无完整持久会话目录。", substring = true).assertDoesNotExist()
                runOnIdle { runBlocking {
                    val parent = f.session("history-parent")
                    f.context.persistence.flush(parent.session)
                    parent.dispose()
                    val dual = f.session("dual-child", parent = "history-parent")
                    f.context.persistence.flush(dual.session)
                    f.session("live-grandchild", f.nested.toString(), "dual-child")
                } }
                val registry = Files.readAllBytes(f.registry)
                val rows = runBlocking { f.database { it.sessions().listSessions() } }
                val events = runBlocking { f.database { db -> rows.associate { it.sessionId to db.sessions().findEvents(it.sessionId) } } }
                val live = runBlocking { f.context.sessions.list() }
                refreshObservation(page)
                val observed = page.observation(f.root.toString())
                assertEquals(ManagedSessionObservationStatus.Observed, observed.status)
                assertEquals(f.owner, observed.owner)
                assertTrue(observed.startedAtMillis >= empty.completedAtMillis)
                assertTrue(observed.completedAtMillis >= observed.startedAtMillis)
                val byId = observed.sessions.associateBy { it.id }
                assertEquals(setOf("history-parent", "dual-child", "live-grandchild"), byId.keys)
                assertEquals(setOf(ManagedSessionSource.Durable), byId.getValue("history-parent").sources)
                assertEquals(setOf(ManagedSessionSource.Live, ManagedSessionSource.Durable), byId.getValue("dual-child").sources)
                assertEquals(setOf(ManagedSessionSource.Live), byId.getValue("live-grandchild").sources)
                assertEquals("history-parent", byId.getValue("dual-child").parentSessionId)
                assertEquals("dual-child", byId.getValue("live-grandchild").parentSessionId)
                onNodeWithText("来源：本次内存会话 + 持久历史").assertExists()
                onNodeWithText("父会话：history-parent").assertExists()
                onNodeWithTag("managed-session-observation-time").assertTextEquals(observationTime(observed))
                assertEquals(live, runBlocking { f.context.sessions.list() })
                assertEquals(rows, runBlocking { f.database { it.sessions().listSessions() } })
                assertEquals(events, runBlocking { f.database { db -> rows.associate { it.sessionId to db.sessions().findEvents(it.sessionId) } } })
                assertContentEquals(registry, Files.readAllBytes(f.registry))
                runOnIdle { runBlocking { f.session("unassigned", cwd = null) } }
                refreshObservation(page)
                val unknown = page.observation(f.root.toString())
                assertEquals(ManagedSessionObservationStatus.Unknown, unknown.status)
                assertEquals(byId.keys, unknown.sessions.map { it.id }.toSet())
                assertEquals(ManagedSessionIssueKind.MissingCwd, unknown.issues.single().kind)
                assertEquals("unassigned", unknown.issues.single().sessionId)
                onNodeWithTag("managed-session-observation-status").assertTextEquals("本次只读观察：关联尚未完整判定")
                revealObservation(onNodeWithTag("managed-session-observation-boundary"))
                onNodeWithTag("managed-session-observation-boundary").assertIsDisplayed()
                revealObservation(onNodeWithTag("managed-destructive-unavailable"))
                onNodeWithTag("managed-destructive-unavailable").assertTextContains("不可用", substring = true)
                assertContentEquals(registry, Files.readAllBytes(f.registry))
            }
        } finally { page.close(); f.close() }
    }

    @Test
    fun realRegistryFailureRetainsExpiredObservationAndRetrySelectsActualLinkedWorktree() {
        val f = WorktreeObservationFixture()
        runBlocking { f.open(); f.registerLinkedWorktree(); f.session("main-live") }
        val page = ObservationPage(f)
        try {
            runSkikoComposeUiTest(size = Size(640f, 800f)) {
                mountObservation(page)
                readyObservation(page)
                assertEquals(1, page.managed.state.value.inventory!!.registeredCount)
                val before = page.managed.state.value.inventory
                val registry = Files.readAllBytes(f.registry)
                runOnIdle { Files.writeString(f.registry, "{ invalid registry") }
                clickObservation("managed-refresh")
                waitUntil(timeoutMillis = 10_000) { page.managed.state.value.phase == ManagedWorktreePhase.Error }
                assertEquals(before, page.managed.state.value.inventory)
                assertTrue(page.managed.state.value.stale)
                onNodeWithTag("managed-error").assertExists()
                onNodeWithTag("managed-stale").assertTextEquals("上次观察已过期；当前归属与保护尚未确认。")
                onAllNodesWithTag("managed-session-observation-status").fetchSemanticsNodes().forEach {
                    assertEquals("上次只读会话观察已过期；当前关联未知。", it.config[SemanticsProperties.Text].single().text)
                }
                runOnIdle { Files.write(f.registry, registry) }
                refreshObservation(page)
                onNodeWithTag("managed-error").assertDoesNotExist()
                onNodeWithTag("managed-stale").assertDoesNotExist()
                assertEquals(ManagedSessionObservationStatus.Observed, page.observation(f.linked.toString()).status)
                val path = onNode(hasTestTag("git-worktree-path") and hasText(f.linked.toString()), true)
                val card = generateSequence(path.fetchSemanticsNode().parent) { it.parent }.first { node ->
                    node.children.any { it.config.getOrNull(SemanticsProperties.TestTag) == "git-worktree-select" }
                }
                val button = card.children.single { it.config.getOrNull(SemanticsProperties.TestTag) == "git-worktree-select" }
                val select = observationNode(button.id)
                revealObservation(select)
                mouseObservation(select)
                waitUntil(timeoutMillis = 10_000) {
                    val git = page.git.state.value
                    git.selectedCwd == f.linked.toString() && git.observationRepositoryReady()
                }
                readyObservation(page)
                assertEquals(f.linked.toString(), page.managed.state.value.inventory!!.repository.root)
                assertContentEquals(registry, Files.readAllBytes(f.registry))
            }
        } finally { page.close(); f.close() }
    }

    @Test
    fun ownerRevokedDuringRealCatalogRefreshRejectsSuccessAndNewBindingHidesOldRows() {
        val f = WorktreeObservationFixture()
        runBlocking { f.open(); f.session("private-old-owner") }
        val isolated = f.context.isolate(SessionPersistenceKey)
        val entered = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<Unit>()
        var pause = false
        runBlocking { isolated.provide(SessionPersistenceKey, object : SessionPersistence by f.context.persistence {
            override suspend fun listHeaders(): List<SessionHeader>? {
                val real = f.context.persistence.listHeaders()
                if (pause) { entered.complete(Unit); gate.await() }
                return real
            }
        }) }
        val page = ObservationPage(f, f.repository(f.capture(isolated)))
        try {
            runSkikoComposeUiTest(size = Size(320f, 600f)) {
                mountObservation(page)
                readyObservation(page)
                assertEquals(listOf("private-old-owner"), page.observation(f.root.toString()).sessions.map { it.id })
                runOnIdle { pause = true }
                clickObservation("managed-refresh")
                waitUntil(timeoutMillis = 10_000) { entered.isCompleted }
                assertEquals(ManagedWorktreePhase.Loading, page.managed.state.value.phase)
                onNodeWithTag("managed-refresh").assertIsNotEnabled()
                runOnIdle { f.currentOwner = null; gate.complete(Unit) }
                waitUntil(timeoutMillis = 10_000) { page.managed.state.value.phase == ManagedWorktreePhase.Error }
                assertTrue(page.managed.state.value.stale)
                onNodeWithTag("managed-session-observation-status").assertTextEquals("上次只读会话观察已过期；当前关联未知。")
                runOnIdle { page.git.bind(f.binding.copy(generation = 2, access = GitAccess.RemoteUnavailable)) }
                pluginsPageSettle()
                waitUntil(timeoutMillis = 10_000) { page.managed.state.value.inventory == null }
                onNodeWithTag("managed-session-id").assertDoesNotExist()
                assertNull(page.managed.state.value.inventory)
                assertEquals(listOf("private-old-owner"), runBlocking { f.context.sessions.list().map { it.id.value } })
            }
        } finally { gate.complete(Unit); page.close(); runBlocking { isolated.dispose() }; f.close() }
    }

    @Test
    fun capturesFourRealCatalogLayoutsWithWheelReachableTailAndCompleteEndMarker() {
        val f = WorktreeObservationFixture()
        val marker = "HISTORY-END-MARKER"
        val parentId = "durable-" + "complete-parent-".repeat(16) + marker
        runBlocking {
            f.open()
            val parent = f.session(parentId)
            f.context.persistence.flush(parent.session)
            parent.dispose()
            val child = f.session("live-and-durable-child", parent = parentId)
            f.context.persistence.flush(child.session)
        }
        val page = ObservationPage(f)
        try {
            for (size in listOf(Size(640f, 800f), Size(320f, 600f))) {
                for (mode in listOf(HarnessThemeMode.Light, HarnessThemeMode.Dark)) {
                    runSkikoComposeUiTest(size = size) {
                        mountObservation(page, mode)
                        readyObservation(page)
                        val observed = page.observation(f.root.toString())
                        assertEquals(ManagedSessionObservationStatus.Observed, observed.status)
                        assertEquals(2, observed.sessions.size)
                        assertEquals(setOf(ManagedSessionSource.Durable), observed.sessions.single { it.id == parentId }.sources)
                        assertEquals(parentId, observed.sessions.single { it.id == "live-and-durable-child" }.parentSessionId)
                        onNodeWithTag("managed-session-observation-time").assertTextEquals(observationTime(observed))
                        onNodeWithText("来源：本次内存会话 + 持久历史").assertExists()
                        val list = onNodeWithTag("managed-inventory-list", true)
                        revealObservation(list)
                        assertTrue(list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].maxValue() > 0f)
                        pluginsPageScrollToEnd("managed-inventory-list")
                        val end = list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
                        assertTrue(abs(end.maxValue() - end.value()) <= 1f)
                        completeObservationText(onNode(hasTestTag("managed-session-id") and
                            hasText("观察到会话：" + parentId), true), "观察到会话：" + parentId)
                        val text = "父会话：" + parentId
                        val tail = onNode(hasTestTag("managed-session-parent") and hasText(text), true)
                        val boundary = onNodeWithTag("managed-session-observation-boundary", true)
                        // The fixed-height list ends after several protection labels. At narrow widths
                        // their wrapping can put lineage above the end viewport: prove both stages.
                        revealObservation(tail, through = boundary)
                        val before = tail.fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange]
                        assertTrue(before.maxValue() > 0f)
                        wheelObservation(tail, before.maxValue() - before.value(), horizontal = true)
                        val horizontal = tail.fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange]
                        assertTrue(abs(horizontal.maxValue() - horizontal.value()) <= 1f)
                        val geometry = observationMarker(tail, text, marker, list)
                        val boundaryGeometry = observationVisibleText(boundary,
                            "两个来源独立读取；本次观察不证明无会话或安全删除。", list)
                        captureObservation(size, mode, observed, buildJsonObject {
                            put("markerProof", geometry); put("boundaryProof", boundaryGeometry)
                        }, list, stage = "lineage")
                        pluginsPageScrollToEnd("managed-inventory-list")
                        val finalLabel = onNodeWithTag("managed-other-protection", true)
                        // Reveal the end through the outer viewport without moving the inner list.
                        revealObservation(list)
                        val finalGeometry = observationVisibleText(finalLabel,
                            page.managed.state.value.inventory!!.entries.single().protection.other.reason, list)
                        captureObservation(size, mode, observed, buildJsonObject {
                            put("lastProtectionProof", finalGeometry)
                        }, list, stage = "end")
                    }
                }
            }
        } finally { page.close(); f.close() }
    }
}

private class ObservationPage(
    private val f: WorktreeObservationFixture,
    private val repository: ManagedWorktreeRepository = f.repository(),
) : AutoCloseable {
    class Controllers(val git: GitController, val managed: ManagedWorktreeController)
    private var mounted: Controllers? = null
    val git: GitController get() = checkNotNull(mounted).git
    val managed: ManagedWorktreeController get() = checkNotNull(mounted).managed

    fun mount(scope: CoroutineScope): Controllers {
        check(mounted == null) { "The previous observation composition must be disposed before remounting" }
        return Controllers(GitController(scope, f.actual), ManagedWorktreeController(scope, repository))
            .also { mounted = it }
    }

    fun start(controllers: Controllers) {
        check(mounted === controllers)
        controllers.git.bind(f.binding)
        controllers.git.selectRepository(f.root.toString())
    }

    fun unmount(controllers: Controllers) {
        check(mounted === controllers)
        controllers.managed.close()
        controllers.git.close()
        mounted = null
    }

    fun observation(root: String): ManagedSessionObservation = assertNotNull(
        assertNotNull(managed.state.value.inventory).entries.single { it.root == root }.sessionObservation)
    override fun close() {
        check(mounted == null) { "Observation controllers must close on their composition's UI context" }
    }
}

private fun ComposeUiTest.mountObservation(page: ObservationPage, mode: HarnessThemeMode = HarnessThemeMode.Light) {
    setContent {
        // Like HarnessApp, both controllers resume reads and handle UI actions on the Compose scope.
        // Each matrix scene owns fresh controllers; a scope cancelled by a prior scene is never reused.
        val scope = rememberCoroutineScope()
        val controllers = remember(page, scope) { page.mount(scope) }
        DisposableEffect(controllers) { onDispose { page.unmount(controllers) } }
        LaunchedEffect(controllers) { page.start(controllers) }
        CompositionLocalProvider(LocalDensity provides Density(1f)) {
            HarnessTheme(themeMode = mode) {
                Box(Modifier.fillMaxSize().testTag("worktree-observation-root")) {
                    WorktreesPage(controllers.git, managedController = controllers.managed)
                }
            }
        }
    }
    // Composition/layout must run before polling controller state: the production page's
    // LaunchedEffect is responsible for binding managed, including an asynchronously ready Git.
    pluginsPageSettle()
}

private fun GitState.observationRepositoryReady(): Boolean =
    unavailableReason == null && binding != null &&
        (repository as? GitRead.Ready<GitRepository>)?.value?.binding == binding &&
        status is GitRead.Ready && branches is GitRead.Ready && worktrees is GitRead.Ready &&
        !creationBusy && !checkoutBusy && !commitBusy &&
        diff !is GitRead.Loading && commitSnapshot !is GitRead.Loading

private fun ComposeUiTest.readyObservation(page: ObservationPage) {
    pluginsPageSettle()
    try {
        // The readiness gate includes the commit snapshot, whose real git read can exceed 10s when
        // the whole module runs in one JVM; keep the bound generous but finite.
        waitUntil(timeoutMillis = 30_000) {
            // Let collected Git state recompose and its actual page effect bind the new repository.
            pluginsPageSettle()
            val git = page.git.state.value
            val managed = page.managed.state.value
            val repository = (git.repository as? GitRead.Ready<GitRepository>)?.value
            git.observationRepositoryReady() && managed.phase == ManagedWorktreePhase.Ready &&
                repository != null && managed.inventory?.repository == repository
        }
    } catch (failure: ComposeTimeoutException) {
        val git = page.git.state.value
        val managed = page.managed.state.value
        throw AssertionError("Worktree observation did not become ready within 10000 ms. " +
            "gitReady=${git.observationRepositoryReady()}; Git=$git; Managed=$managed", failure)
    }
    pluginsPageSettle()
    assertFalse(page.managed.state.value.stale)
    assertEquals((page.git.state.value.repository as GitRead.Ready).value, page.managed.state.value.inventory!!.repository)
}

private fun ComposeUiTest.refreshObservation(page: ObservationPage) {
    val previous = page.managed.state.value.inventory
    clickObservation("managed-refresh")
    waitUntil(timeoutMillis = 10_000) { page.managed.state.value.phase == ManagedWorktreePhase.Ready && page.managed.state.value.inventory !== previous }
    readyObservation(page)
}

private fun observationTime(value: ManagedSessionObservation) =
    "本次观察（UTC）：开始 " + kotlin.time.Instant.fromEpochMilliseconds(value.startedAtMillis) +
        "；完成 " + kotlin.time.Instant.fromEpochMilliseconds(value.completedAtMillis)

private fun ComposeUiTest.observationNode(id: Int) = onNode(SemanticsMatcher("Observation node $id") { it.id == id }, true)
private fun SemanticsNode.observationLayout() = Rect(positionInRoot, Size(size.width.toFloat(), size.height.toFloat()))
private fun ComposeUiTest.observationRoot(target: SemanticsNodeInteraction) =
    observationNode(generateSequence(target.fetchSemanticsNode()) { it.parent }.last().id)

/** Outside-in measured wheel input, in each scroll ancestor's gutter to avoid nested readers. */
private fun ComposeUiTest.revealObservation(target: SemanticsNodeInteraction,
    through: SemanticsNodeInteraction = target) {
    pluginsPageSettle()
    val ancestors = generateSequence(target.fetchSemanticsNode().parent) { it.parent }.filter {
        it.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange) != null
    }.toList().asReversed()
    val deadline = System.nanoTime() + 10_000_000_000L
    ancestors.forEachIndexed { index, ancestor ->
        val scroll = observationNode(ancestor.id)
        val next = if (index + 1 < ancestors.size) observationNode(ancestors[index + 1].id) else target
        while (true) {
            assertTrue(System.nanoTime() < deadline, "Real wheel reveal deadline")
            val viewport = scroll.fetchSemanticsNode().boundsInRoot
                .intersect(observationRoot(scroll).fetchSemanticsNode().boundsInRoot)
            assertTrue(viewport.width > 0f && viewport.height > 0f, "Scroll ancestor must be visible: $viewport")
            val first = next.fetchSemanticsNode().observationLayout()
            val last = if (index + 1 < ancestors.size) first else through.fetchSemanticsNode().observationLayout()
            val layout = Rect(minOf(first.left, last.left), minOf(first.top, last.top),
                maxOf(first.right, last.right), maxOf(first.bottom, last.bottom))
            if (layout.intersect(viewport).height + 1f >= minOf(layout.height, viewport.height)) break
            val delta = if (layout.top < viewport.top) layout.top - viewport.top else layout.bottom - viewport.bottom
            val axis = scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
            assertTrue(if (delta < 0) axis.value() > 0f else axis.value() < axis.maxValue(),
                "Target unreachable: ancestor=${ancestor.id} target=$layout viewport=$viewport " +
                    "range=${axis.value()}/${axis.maxValue()}")
            wheelObservation(scroll, delta.coerceIn(-viewport.height * .75f, viewport.height * .75f), gutter = true)
        }
    }
    target.assertIsDisplayed()
    through.assertIsDisplayed()
}

private fun ComposeUiTest.wheelObservation(target: SemanticsNodeInteraction, pixels: Float,
    horizontal: Boolean = false, gutter: Boolean = false) {
    val root = observationRoot(target)
    fun point(): Offset {
        val viewport = target.fetchSemanticsNode().boundsInRoot.intersect(root.fetchSemanticsNode().boundsInRoot)
        assertTrue(viewport.width > 0f && viewport.height > 0f, "Measured pointer viewport required")
        return (if (gutter) Offset(viewport.left + minOf(4f, viewport.width / 2f), viewport.center.y) else viewport.center) -
            root.fetchSemanticsNode().boundsInRoot.topLeft
    }
    val hover = point()
    root.performMouseInput { moveTo(hover) }
    pluginsPageSettle()
    val node = target.fetchSemanticsNode()
    val axisSize = (if (horizontal) node.size.width else node.size.height).toFloat()
    val os = System.getProperty("os.name").orEmpty()
    val units = when {
        os == "Mac OS X" -> 10f * node.layoutInfo.density.density
        os.startsWith("Linux") -> sqrt(axisSize)
        else -> axisSize / 20f
    }
    assertTrue(units > 0f && pixels.isFinite())
    val position = point()
    root.performMouseInput {
        moveTo(position); advanceEventTime(16)
        if (horizontal) scroll(pixels / units, ScrollWheel.Horizontal) else scroll(pixels / units)
    }
    pluginsPageSettle()
}

private fun ComposeUiTest.clickObservation(tag: String) {
    revealObservation(onNodeWithTag(tag))
    pluginsPageMouseClick(tag)
}

private fun ComposeUiTest.mouseObservation(target: SemanticsNodeInteraction) {
    val root = observationRoot(target)
    val hover = target.fetchSemanticsNode().boundsInRoot.center - root.fetchSemanticsNode().boundsInRoot.topLeft
    root.performMouseInput { moveTo(hover) }
    pluginsPageSettle()
    val node = target.fetchSemanticsNode()
    assertTrue(node.boundsInRoot.height + 1f >= node.size.height && node.boundsInRoot.width + 1f >= node.size.width)
    val point = node.boundsInRoot.center - root.fetchSemanticsNode().boundsInRoot.topLeft
    root.performMouseInput { moveTo(point); advanceEventTime(16); click(point) }
    pluginsPageSettle()
}

private fun completeObservationText(target: SemanticsNodeInteraction, expected: String): TextLayoutResult {
    val layouts = mutableListOf<TextLayoutResult>()
    target.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { assertTrue(it(layouts)) }
    val layout = layouts.single()
    assertEquals(expected, layout.layoutInput.text.text)
    assertTrue((0 until layout.lineCount).none { layout.isLineEllipsized(it) }, "Complete text cannot be ellipsized")
    return layout
}

private fun ComposeUiTest.observationMarker(target: SemanticsNodeInteraction, text: String, marker: String,
    list: SemanticsNodeInteraction): JsonObject {
    val layout = completeObservationText(target, text)
    assertEquals(1, layout.lineCount)
    val node = target.fetchSemanticsNode()
    val start = text.indexOf(marker)
    assertTrue(start >= 0)
    val boxes = (start until start + marker.length).map { layout.getBoundingBox(it) }
    val local = Rect(boxes.minOf { it.left }, boxes.minOf { it.top }, boxes.maxOf { it.right }, boxes.maxOf { it.bottom })
    // The tag sits on horizontalScroll: this is the viewport origin, minus actual offset once.
    val scroll = node.config[SemanticsProperties.HorizontalScrollAxisRange].value()
    val origin = node.positionInRoot
    val actual = local.translate(origin - Offset(scroll, 0f))
    val viewport = node.boundsInRoot.intersect(list.fetchSemanticsNode().boundsInRoot)
        .intersect(observationRoot(target).fetchSemanticsNode().boundsInRoot)
    assertTrue(actual.left >= viewport.left - 1f && actual.right <= viewport.right + 1f &&
        actual.top >= viewport.top - 1f && actual.bottom <= viewport.bottom + 1f,
        "Full laid-out marker must be visible: $actual / $viewport origin=$origin scroll=$scroll")
    return buildJsonObject {
        put("fullText", text); put("marker", marker); put("viewportOrigin", origin.toString())
        put("subtractedScrollPixels", scroll); put("markerLocal", local.toString()); put("markerInRoot", actual.toString())
        put("visibleViewport", viewport.toString()); put("fullMarkerVisible", true); put("ellipsis", false)
    }
}

private fun ComposeUiTest.observationVisibleText(target: SemanticsNodeInteraction, expected: String,
    list: SemanticsNodeInteraction): JsonObject {
    target.assertIsDisplayed()
    val layout = completeObservationText(target, expected)
    val bounds = target.fetchSemanticsNode().observationLayout()
    val viewport = target.fetchSemanticsNode().boundsInRoot.intersect(list.fetchSemanticsNode().boundsInRoot)
        .intersect(observationRoot(target).fetchSemanticsNode().boundsInRoot)
    assertTrue(bounds.left >= viewport.left - 1f && bounds.right <= viewport.right + 1f &&
        bounds.top >= viewport.top - 1f && bounds.bottom <= viewport.bottom + 1f,
        "Complete text must be visible: $expected bounds=$bounds viewport=$viewport")
    return buildJsonObject {
        put("fullText", expected); put("lineCount", layout.lineCount); put("boundsInRoot", bounds.toString())
        put("visibleViewport", viewport.toString()); put("fullTextVisible", true); put("ellipsis", false)
    }
}

private fun ComposeUiTest.captureObservation(size: Size, mode: HarnessThemeMode, observed: ManagedSessionObservation,
    proof: JsonObject, list: SemanticsNodeInteraction, stage: String) {
    val root = onNodeWithTag("worktree-observation-root").fetchSemanticsNode()
    assertEquals(size.width.toInt(), root.size.width)
    assertEquals(size.height.toInt(), root.size.height)
    val axis = list.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
    val value = axis.value()
    val max = axis.maxValue()
    val atEnd = abs(max - value) <= 1f
    when (stage) {
        "end" -> {
            assertTrue(atEnd, "Final list must be at its measured end: $value / $max")
            assertTrue(proof.containsKey("lastProtectionProof"))
        }
        "lineage" -> {
            assertTrue(proof.containsKey("markerProof"))
            assertTrue(proof.containsKey("boundaryProof"))
        }
        else -> error("Unknown observation capture stage: $stage")
    }
    val viewport = list.fetchSemanticsNode().boundsInRoot.intersect(root.boundsInRoot)
    // Environment controls file output only; state and geometry assertions always execute.
    val directory = System.getenv("HARNESS_WORKTREE_SESSION_OBSERVATION_CAPTURE_DIR")?.let(::File) ?: return
    directory.mkdirs()
    val name = "worktree-session-" + size.width.toInt() + "x" + size.height.toInt() + "-" + mode.name.lowercase() +
        if (stage == "lineage") "-lineage" else ""
    val png = File(directory, "$name.png")
    val bytes = Image.makeFromBitmap(onNodeWithTag("worktree-observation-root").captureToImage().asSkiaBitmap())
        .encodeToData(EncodedImageFormat.PNG)!!.bytes
    png.writeBytes(bytes)
    val geometry = buildJsonObject {
        put("schema", "harness.worktree-session-observation.geometry.v1")
        put("width", root.size.width); put("height", root.size.height); put("theme", mode.name)
        put("png", png.name); put("sha256", MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
        put("status", observed.status.name); put("ownerHost", observed.owner.hostId.value); put("ownerHome", observed.owner.homeNamespace)
        put("startedAtMillis", observed.startedAtMillis); put("completedAtMillis", observed.completedAtMillis)
        put("stage", stage); put("listViewport", viewport.toString()); put("listValue", value); put("listMax", max)
        put("verticalEnd", atEnd); put("proof", proof)
    }
    File(directory, "$name.geometry.json").writeText(Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), geometry))
}
