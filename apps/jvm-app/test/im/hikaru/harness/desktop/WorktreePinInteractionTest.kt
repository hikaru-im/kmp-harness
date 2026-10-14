@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package im.hikaru.harness.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.harness.client.git.*
import im.hikaru.harness.desktop.git.*
import im.hikaru.harness.session.sessions
import im.hikaru.harness.session.persistence.persistence
import im.hikaru.harness.ui.foundation.HarnessTheme
import im.hikaru.harness.ui.foundation.HarnessThemeMode
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import javax.imageio.ImageIO
import kotlin.math.sqrt
import kotlin.test.*

class WorktreePinInteractionTest {
    @Test
    fun realMousePinAndUnpinPersistToFreshReader() {
        val p = WorktreePinFixture(); runBlocking { p.fixture.open(); p.fixture.session("mouse-chat") }
        val page = PinPage(p)
        try {
            val before = runBlocking { pinSourceEvidence(p.fixture) }
            runSkikoComposeUiTest(size = Size(640f, 800f)) {
                mountPinPage(page); readyPinPage(page)
                val key = page.key("mouse-chat")
                val button = onNodeWithTag("managed-session-pin-toggle")
                revealPin(button); button.assertIsEnabled().assertTextEquals("固定聊天")
                mousePin(button); awaitPin(page, key, true)
                assertTrue(key in DesktopWorktreePinStore(p.path).read(p.fixture.owner).snapshot.pins)
                onNodeWithTag("managed-session-pin-status").assertTextEquals("聊天已固定")
                revealPin(button); button.assertIsEnabled().assertTextEquals("取消固定聊天")
                mousePin(button); awaitPin(page, key, false)
                val fresh = DesktopWorktreePinStore(p.path).read(p.fixture.owner).snapshot
                assertEquals(2L, fresh.revision); assertTrue(fresh.pins.isEmpty())
                onNodeWithTag("managed-session-pin-status").assertTextEquals("此聊天未设置固定")
                clickPinTag("managed-refresh"); readyPinPage(page)
                assertTrue(page.snapshot().pins.isEmpty())
            }
            assertEquals(before, runBlocking { pinSourceEvidence(p.fixture) })
        } finally { page.close(); p.close() }
    }

    @Test
    fun realMouseArchiveAndUnarchivePersistToFreshReader() {
        val p = WorktreePinFixture(); runBlocking { p.fixture.open(); p.fixture.session("mouse-archive-chat") }
        val archivePath = p.fixture.home.resolve("managed-chat-archive.json")
        val page = PinPage(p, p.repository(archiveStore = DesktopWorktreeChatArchiveStore(archivePath)))
        try {
            val before = runBlocking { pinSourceEvidence(p.fixture) }
            runSkikoComposeUiTest(size = Size(640f, 800f)) {
                mountPinPage(page); readyPinPage(page)
                val key = page.key("mouse-archive-chat")
                val archiveKey = ChatArchiveKey(key.id, key.headerFingerprint)
                val button = onNodeWithTag("managed-session-archive-toggle")
                revealPin(button); button.assertIsEnabled().assertTextEquals("归档聊天")
                mousePin(button); awaitArchive(page, archiveKey, true)
                assertTrue(archiveKey in DesktopWorktreeChatArchiveStore(archivePath).read(p.fixture.owner).snapshot.archived)
                onNodeWithTag("managed-session-archive-status").assertTextEquals("聊天已归档")
                revealPin(button); button.assertIsEnabled().assertTextEquals("取消归档聊天")
                mousePin(button); awaitArchive(page, archiveKey, false)
                val fresh = DesktopWorktreeChatArchiveStore(archivePath).read(p.fixture.owner).snapshot
                assertEquals(2L, fresh.revision); assertTrue(fresh.archived.isEmpty())
                onNodeWithTag("managed-session-archive-status").assertTextEquals("此聊天未设置归档")
                clickPinTag("managed-refresh"); readyPinPage(page)
                assertTrue(page.archiveSnapshot().archived.isEmpty())
            }
            assertEquals(before, runBlocking { pinSourceEvidence(p.fixture) })
        } finally { page.close(); p.close() }
    }

    @Test
    fun unknownUnavailableAndScopeChangeDisableAndHidePins() {
        val p = WorktreePinFixture()
        runBlocking {
            p.fixture.open(); p.fixture.session("private-pinned-chat")
            assertIs<ManagedWorktreePinReceipt.Applied>(p.repository.setPinned(p.request("private-pinned-chat")))
        }
        val page = PinPage(p)
        try {
            val bytes = Files.readAllBytes(p.path)
            runSkikoComposeUiTest(size = Size(640f, 800f)) {
                mountPinPage(page); readyPinPage(page)
                val button = onNodeWithTag("managed-session-pin-toggle")
                val unknown = "{ invalid pin store".toByteArray()
                runOnIdle { Files.write(p.path, unknown) }
                refreshPinPage(page)
                assertIs<ManagedWorktreePinRead.Unknown>(page.managed.state.value.inventory?.pinRead)
                revealPin(button); button.assertIsNotEnabled(); mousePin(button)
                onNodeWithTag("managed-session-pin-status").assertTextContains("未知", substring = true)
                assertContentEquals(unknown, Files.readAllBytes(p.path))
                runOnIdle { Files.write(p.path, bytes) }; refreshPinPage(page)
                val bad = runOnIdle { runBlocking { p.fixture.session("unassigned", cwd = null) } }
                refreshPinPage(page)
                assertEquals(ManagedSessionObservationStatus.Unknown, page.managed.state.value.inventory!!.entries.single().sessionObservation!!.status)
                revealPin(button); button.assertIsNotEnabled(); mousePin(button)
                assertContentEquals(bytes, Files.readAllBytes(p.path))
                runOnIdle { runBlocking { bad.dispose() } }; refreshPinPage(page)
                val registry = Files.readAllBytes(p.fixture.registry)
                runOnIdle { Files.writeString(p.fixture.registry, "{ malformed registry") }
                clickPinTag("managed-refresh")
                waitUntil(timeoutMillis = 10_000) { pluginsPageSettle(); page.managed.state.value.phase == ManagedWorktreePhase.Error }
                assertTrue(page.managed.state.value.stale); revealPin(button); button.assertIsNotEnabled(); mousePin(button)
                assertContentEquals(bytes, Files.readAllBytes(p.path))
                runOnIdle { Files.write(p.fixture.registry, registry) }; refreshPinPage(page)
                // Change Git binding without explicitly rebinding managed: the production render
                // fence must hide the old owner's pin before the asynchronous page bind finishes.
                runOnIdle { page.git.bind(p.fixture.binding.copy(hostId = HostId("foreign-host"), generation = 2, access = GitAccess.RemoteUnavailable)) }
                pluginsPageSettle()
                onNodeWithText("聊天已固定").assertDoesNotExist()
                onNodeWithTag("managed-session-pin-toggle").assertDoesNotExist()
                onNodeWithTag("managed-pin-receipt").assertDoesNotExist()
                assertContentEquals(bytes, Files.readAllBytes(p.path))
            }
            val f = p.fixture
            val unavailable = DesktopManagedWorktreeRepository(f.actual, DesktopManagedWorktreeStore(f.registry),
                ownerValidator = { f.currentOwner }, sessionObserver = f.host.sessionObserver)
            val absentPage = PinPage(p, unavailable)
            try {
                runSkikoComposeUiTest(size = Size(320f, 600f)) {
                    mountPinPage(absentPage); readyPinPage(absentPage)
                    val button = onNodeWithTag("managed-session-pin-toggle")
                    revealPin(button); button.assertIsNotEnabled(); mousePin(button)
                    onNodeWithTag("managed-session-pin-status").assertTextEquals("聊天固定服务不可用")
                    assertContentEquals(bytes, Files.readAllBytes(p.path))
                }
            } finally { absentPage.close() }
        } finally { page.close(); p.close() }
    }

    @Test
    fun capturesWideCompactLightDarkRealPins() {
        val p = WorktreePinFixture()
        runBlocking {
            p.fixture.open()
            val history = p.fixture.session("real-durable-pin")
            p.fixture.context.persistence.flush(history.session); history.dispose()
            assertIs<ManagedWorktreePinReceipt.Applied>(p.repository.setPinned(p.request("real-durable-pin")))
        }
        val page = PinPage(p)
        try {
            val before = runBlocking { pinSourceEvidence(p.fixture) }
            for (size in listOf(Size(640f, 800f), Size(320f, 600f)))
                for (theme in listOf(HarnessThemeMode.Light, HarnessThemeMode.Dark))
                    runSkikoComposeUiTest(size = size) {
                        mountPinPage(page, theme); readyPinPage(page)
                        val button = onNodeWithTag("managed-session-pin-toggle")
                        revealPin(button)
                        button.assertIsEnabled().assertTextEquals("取消固定聊天")
                        onNodeWithTag("managed-session-pin-status").assertTextEquals("聊天已固定")
                        val snapshot = page.snapshot(); val key = page.key("real-durable-pin")
                        assertEquals(p.fixture.owner, snapshot.owner); assertTrue(key in snapshot.pins)
                        assertEquals(snapshot, DesktopWorktreePinStore(p.path).read(p.fixture.owner).snapshot)
                        assertTrue(runBlocking { p.fixture.context.sessions.list() }.isEmpty())
                        capturePins(size, theme, snapshot, key, button)
                    }
            assertEquals(before, runBlocking { pinSourceEvidence(p.fixture) })
        } finally { page.close(); p.close() }
    }
}

private class PinPage(val pins: WorktreePinFixture, private val repository: ManagedWorktreeRepository = pins.repository) : AutoCloseable {
    class Controllers(val git: GitController, val managed: ManagedWorktreeController)
    private var mounted: Controllers? = null
    val git: GitController get() = checkNotNull(mounted).git
    val managed: ManagedWorktreeController get() = checkNotNull(mounted).managed
    fun mount(scope: CoroutineScope): Controllers {
        check(mounted == null)
        return Controllers(GitController(scope, pins.fixture.actual), ManagedWorktreeController(scope, repository)).also { mounted = it }
    }
    fun start(controllers: Controllers) {
        check(mounted === controllers); controllers.git.bind(pins.fixture.binding); controllers.git.selectRepository(pins.fixture.root.toString())
    }
    fun unmount(controllers: Controllers) {
        check(mounted === controllers); controllers.managed.close(); controllers.git.close(); mounted = null
    }
    fun snapshot() = assertIs<ManagedWorktreePinRead.Available>(managed.state.value.inventory?.pinRead).snapshot
    fun archiveSnapshot() = assertIs<ManagedWorktreeChatArchiveRead.Available>(managed.state.value.inventory?.archiveRead).snapshot
    fun key(id: String) = assertNotNull(managed.state.value.inventory!!.entries.flatMap { it.sessionObservation?.sessions.orEmpty() }.single { it.id == id }.pinKey)
    override fun close() { check(mounted == null) }
}

private fun ComposeUiTest.mountPinPage(page: PinPage, theme: HarnessThemeMode = HarnessThemeMode.Light) {
    setContent {
        val scope = rememberCoroutineScope()
        val controllers = remember(page, scope) { page.mount(scope) }
        DisposableEffect(controllers) { onDispose { page.unmount(controllers) } }
        LaunchedEffect(controllers) { page.start(controllers) }
        CompositionLocalProvider(LocalDensity provides Density(1f)) {
            HarnessTheme(themeMode = theme) {
                Box(Modifier.fillMaxSize().testTag("worktree-pin-root")) { WorktreesPage(controllers.git, managedController = controllers.managed) }
            }
        }
    }
    pluginsPageSettle()
}

private fun GitState.pinGitReady(): Boolean = unavailableReason == null && binding != null &&
    (repository as? GitRead.Ready<GitRepository>)?.value?.binding == binding &&
    status is GitRead.Ready && branches is GitRead.Ready && worktrees is GitRead.Ready &&
    !creationBusy && !checkoutBusy && !commitBusy && diff !is GitRead.Loading && commitSnapshot !is GitRead.Loading

private fun ComposeUiTest.readyPinPage(page: PinPage) {
    waitUntil(timeoutMillis = 10_000) {
        pluginsPageSettle()
        page.git.state.value.pinGitReady() && page.managed.state.value.phase == ManagedWorktreePhase.Ready &&
            page.managed.state.value.repository == (page.git.state.value.repository as? GitRead.Ready<GitRepository>)?.value
    }
    pluginsPageSettle(); assertFalse(page.managed.state.value.stale)
}

private fun ComposeUiTest.refreshPinPage(page: PinPage) {
    val previous = page.managed.state.value.inventory
    clickPinTag("managed-refresh")
    waitUntil(timeoutMillis = 10_000) { pluginsPageSettle(); page.managed.state.value.phase == ManagedWorktreePhase.Ready && page.managed.state.value.inventory !== previous }
    readyPinPage(page)
}

private fun ComposeUiTest.awaitPin(page: PinPage, key: PinKey, pinned: Boolean) {
    waitUntil(timeoutMillis = 10_000) {
        pluginsPageSettle()
        val state = page.managed.state.value
        val read = state.inventory?.pinRead as? ManagedWorktreePinRead.Available
        state.phase == ManagedWorktreePhase.Ready && !state.stale && !state.pinBusy &&
            state.pinReceipt is ManagedWorktreePinReceipt.Applied && read != null && (key in read.snapshot.pins) == pinned
    }
    readyPinPage(page)
}

private fun ComposeUiTest.awaitArchive(page: PinPage, key: ChatArchiveKey, archived: Boolean) {
    waitUntil(timeoutMillis = 10_000) {
        pluginsPageSettle()
        val state = page.managed.state.value
        val read = state.inventory?.archiveRead as? ManagedWorktreeChatArchiveRead.Available
        state.phase == ManagedWorktreePhase.Ready && !state.stale && !state.archiveBusy &&
            state.archiveReceipt is ManagedWorktreeChatArchiveReceipt.Applied && read != null &&
            (key in read.snapshot.archived) == archived
    }
    readyPinPage(page)
}

private fun ComposeUiTest.capturePins(size: Size, theme: HarnessThemeMode, snapshot: ManagedWorktreePinSnapshot,
    key: PinKey, button: SemanticsNodeInteraction) {
    val root = onNodeWithTag("worktree-pin-root").fetchSemanticsNode()
    val node = button.fetchSemanticsNode()
    val viewport = onNodeWithTag("managed-inventory-list", true).fetchSemanticsNode().boundsInRoot.intersect(root.boundsInRoot)
    val bounds = Rect(node.positionInRoot, Size(node.size.width.toFloat(), node.size.height.toFloat()))
    assertEquals(size.width.toInt(), root.size.width); assertEquals(size.height.toInt(), root.size.height)
    assertTrue(bounds.width > 0 && bounds.height > 0)
    assertTrue(bounds.left >= viewport.left - 1 && bounds.right <= viewport.right + 1 &&
        bounds.top >= viewport.top - 1 && bounds.bottom <= viewport.bottom + 1, "Complete pin button must fit its actual viewport")
    assertFalse(node.config.contains(SemanticsProperties.Disabled)); assertTrue(key in snapshot.pins)
    // State and geometry checks above always run. Only artifact output is optional.
    val directory = System.getenv("HARNESS_WORKTREE_PIN_CAPTURE_DIR")?.let(::File) ?: return
    assertTrue(directory.isDirectory || directory.mkdirs())
    val name = "worktree-pin-${root.size.width}x${root.size.height}-${theme.name.lowercase()}"
    val bytes = Image.makeFromBitmap(onNodeWithTag("worktree-pin-root").captureToImage().asSkiaBitmap()).use { image ->
        image.encodeToData(EncodedImageFormat.PNG)?.use { it.bytes } ?: error("PNG encoding failed")
    }
    val png = File(directory, "$name.png"); png.writeBytes(bytes)
    val decoded = assertNotNull(ImageIO.read(png)); assertEquals(root.size.width, decoded.width); assertEquals(root.size.height, decoded.height)
    fun rect(value: Rect) = buildJsonObject { put("left", value.left); put("top", value.top); put("right", value.right); put("bottom", value.bottom) }
    val geometry = buildJsonObject {
        put("schema", "harness.worktree-pin.geometry.v1"); put("png", png.name)
        put("sha256", MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
        put("width", root.size.width); put("height", root.size.height); put("theme", theme.name); put("density", 1f)
        put("viewport", rect(viewport)); put("buttonBounds", rect(bounds)); put("buttonEnabled", true)
        put("ownerHost", snapshot.owner.hostId.value); put("ownerHome", snapshot.owner.homeNamespace)
        put("ownerIdentity", JsonNull); put("revision", snapshot.revision); put("id", key.id)
        put("headerFingerprint", key.headerFingerprint); put("pinned", true)
    }
    File(directory, "$name.geometry.json").writeText(Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), geometry))
}

private fun ComposeUiTest.pinNode(id: Int) = onNode(SemanticsMatcher("Observation node $id") { it.id == id }, true)
private fun SemanticsNode.pinLayout() = Rect(positionInRoot, Size(size.width.toFloat(), size.height.toFloat()))
private fun ComposeUiTest.pinRoot(target: SemanticsNodeInteraction) =
    pinNode(generateSequence(target.fetchSemanticsNode()) { it.parent }.last().id)

/** Outside-in measured wheel input, in each scroll ancestor's gutter to avoid nested readers. */
private fun ComposeUiTest.revealPin(target: SemanticsNodeInteraction,
    through: SemanticsNodeInteraction = target) {
    pluginsPageSettle()
    val ancestors = generateSequence(target.fetchSemanticsNode().parent) { it.parent }.filter {
        it.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange) != null
    }.toList().asReversed()
    val deadline = System.nanoTime() + 10_000_000_000L
    ancestors.forEachIndexed { index, ancestor ->
        val scroll = pinNode(ancestor.id)
        val next = if (index + 1 < ancestors.size) pinNode(ancestors[index + 1].id) else target
        while (true) {
            assertTrue(System.nanoTime() < deadline, "Real wheel reveal deadline")
            val viewport = scroll.fetchSemanticsNode().boundsInRoot
                .intersect(pinRoot(scroll).fetchSemanticsNode().boundsInRoot)
            assertTrue(viewport.width > 0f && viewport.height > 0f, "Scroll ancestor must be visible: $viewport")
            val first = next.fetchSemanticsNode().pinLayout()
            val last = if (index + 1 < ancestors.size) first else through.fetchSemanticsNode().pinLayout()
            val layout = Rect(minOf(first.left, last.left), minOf(first.top, last.top),
                maxOf(first.right, last.right), maxOf(first.bottom, last.bottom))
            if (layout.intersect(viewport).height + 1f >= minOf(layout.height, viewport.height)) break
            val delta = if (layout.top < viewport.top) layout.top - viewport.top else layout.bottom - viewport.bottom
            val axis = scroll.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
            assertTrue(if (delta < 0) axis.value() > 0f else axis.value() < axis.maxValue(),
                "Target unreachable: ancestor=${ancestor.id} target=$layout viewport=$viewport " +
                    "range=${axis.value()}/${axis.maxValue()}")
            wheelPin(scroll, delta.coerceIn(-viewport.height * .75f, viewport.height * .75f), gutter = true)
        }
    }
    target.assertIsDisplayed()
    through.assertIsDisplayed()
}

private fun ComposeUiTest.wheelPin(target: SemanticsNodeInteraction, pixels: Float,
    horizontal: Boolean = false, gutter: Boolean = false) {
    val root = pinRoot(target)
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

private fun ComposeUiTest.clickPinTag(tag: String) {
    revealPin(onNodeWithTag(tag))
    pluginsPageMouseClick(tag)
}

private fun ComposeUiTest.mousePin(target: SemanticsNodeInteraction) {
    val root = pinRoot(target)
    val hover = target.fetchSemanticsNode().boundsInRoot.center - root.fetchSemanticsNode().boundsInRoot.topLeft
    root.performMouseInput { moveTo(hover) }
    pluginsPageSettle()
    val node = target.fetchSemanticsNode()
    assertTrue(node.boundsInRoot.height + 1f >= node.size.height && node.boundsInRoot.width + 1f >= node.size.width)
    val point = node.boundsInRoot.center - root.fetchSemanticsNode().boundsInRoot.topLeft
    root.performMouseInput { moveTo(point); advanceEventTime(16); click(point) }
    pluginsPageSettle()
}
