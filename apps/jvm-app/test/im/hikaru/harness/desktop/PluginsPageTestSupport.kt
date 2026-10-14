@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package im.hikaru.harness.desktop

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import im.hikaru.contracts.common.ApiResult
import im.hikaru.contracts.harness.identity.HostDescription
import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.contracts.harness.protocol.ProtocolVersion
import im.hikaru.harness.api.gateway.ApiGateway
import im.hikaru.harness.api.gateway.host.HostDescribeEndpoint
import im.hikaru.harness.api.gateway.session.SessionListEndpoint
import im.hikaru.harness.bundle.desktop.DesktopUserPlugins
import im.hikaru.harness.client.connection.*
import im.hikaru.harness.client.plugins.*
import im.hikaru.harness.desktop.connection.LocalConnection
import im.hikaru.harness.desktop.plugins.DesktopInstalledPluginRepository
import im.hikaru.harness.ui.foundation.HarnessThemeMode
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import javax.imageio.ImageIO
import kotlin.test.*

/** Uses the disk service and its real LocalConnection adapter, with an isolated temporary home. */
internal class PluginsPageTestSupport : AutoCloseable {
    val directory = Files.createTempDirectory("plugins-page-ui-").toRealPath()
    val home = Files.createDirectories(directory.resolve("home"))
    val source = Files.createDirectories(directory.resolve("source with spaces"))
    val inventoryFile = home.resolve(".harness/plugins.json")
    val host = HostId("plugins-page-local-host")
    val gateway = ApiGateway()
    val connection = LocalConnection(gateway)
    val service = DesktopUserPlugins(inventoryFile)
    val repository = DesktopInstalledPluginRepository(host, connection, service)
    val manifest = source.resolve(".codex-plugin/plugin.json")
    val skill = source.resolve("skills/review/SKILL.md")

    init {
        Files.createDirectories(manifest.parent)
        Files.createDirectories(skill.parent)
        Files.writeString(manifest,
            """{"name":"Temporary review plugin","description":"Real disk registration","version":"1.0.0","skills":"skills"}""")
        Files.writeString(skill, "---\nname: review\ndescription: Review the temporary workspace\n---\nRead source before editing.\n")
        println("PLUGINS PAGE PERSISTENCE: home=$home; inventory=$inventoryFile; source=$source; manifest=$manifest; skill=$skill")
        runBlocking {
            gateway.register(HostDescribeEndpoint) {
                ApiResult(ApiResult.SUCCESS_CODE, data = HostDescription(ProtocolVersion.Current, host, "Plugins test Host"))
            }
            gateway.register(SessionListEndpoint) {
                ApiResult(ApiResult.SUCCESS_CODE, data = emptyList<im.hikaru.harness.session.api.SessionSummary>())
            }
        }
    }

    // Call after runSkikoComposeUiTest has disposed its composition and controller jobs.
    override fun close() { directory.toFile().deleteRecursively() }
}

/** Failures wrap the real adapter; writes still commit to the actual temporary inventory. */
internal class PluginsPageReadFaults(private val delegate: LocalPluginManagementRepository) :
    LocalPluginManagementRepository by delegate {
    @Volatile var failReads = false
    @Volatile var failAfterRegister = false
    @Volatile var readGate: CompletableDeferred<Unit>? = null
    private fun failure() = PluginResult.Error(PluginRepositoryError("Test plugin read failed"))
    override suspend fun listInstalled(scope: PluginScope): PluginResult<List<InstalledPlugin>> {
        readGate?.await()
        return if (failReads) failure() else delegate.listInstalled(scope)
    }
    override suspend fun readRevision(scope: PluginScope): PluginResult<String> =
        if (failReads) failure() else delegate.readRevision(scope)
    override suspend fun readDetail(scope: PluginScope, id: PluginId): PluginResult<InstalledPluginDetail> =
        if (failReads) failure() else delegate.readDetail(scope, id)
    override suspend fun registerLocal(scope: PluginScope, directory: String, expectedRevision: String): PluginResult<PluginMutationReceipt> {
        val result = delegate.registerLocal(scope, directory, expectedRevision)
        if (result is PluginResult.Success && failAfterRegister) failReads = true
        return result
    }
}

/** Adapter resources live only in tests; scope changes produce different plugin IDs. */
internal class PluginsPageFixtureRepository(val count: Int = 3) : LocalPluginManagementRepository {
    private var access: PluginAccessBinding? = null
    val listReads = java.util.concurrent.CopyOnWriteArrayList<PluginScope>()
    var readGate: CompletableDeferred<Unit>? = null
    override val canRead = true
    override val canManage get() = access?.transport == ConnectionTransport.Local
    override fun bindAccess(binding: PluginAccessBinding?) { access = binding }
    private fun plugins(scope: PluginScope) = PluginsPageDisplayData.plugins(count)
        .mapIndexed { index, plugin -> plugin.copy(id = PluginId(scope.hostId.value + "-" + index)) }
    override suspend fun listInstalled(scope: PluginScope): PluginResult<List<InstalledPlugin>> {
        listReads += scope
        readGate?.await()
        return PluginResult.Success(plugins(scope))
    }
    override suspend fun readDetail(scope: PluginScope, id: PluginId): PluginResult<InstalledPluginDetail> {
        val plugin = plugins(scope).single { it.id == id }
        return PluginResult.Success(PluginsPageDisplayData.detail(plugin))
    }
    override suspend fun readRevision(scope: PluginScope) = PluginResult.Success("test-revision")
    override suspend fun registerLocal(scope: PluginScope, directory: String, expectedRevision: String): PluginResult<PluginMutationReceipt> =
        error("Use PluginsPageTestSupport for real registration")
    override suspend fun setEnabled(scope: PluginScope, id: PluginId, enabled: Boolean, expectedRevision: String): PluginResult<PluginMutationReceipt> =
        error("Use PluginsPageTestSupport for real enable")
    override suspend fun removeRegistration(scope: PluginScope, id: PluginId, expectedRevision: String): PluginResult<PluginMutationReceipt> =
        error("Use PluginsPageTestSupport for real removal")
}

internal class PluginsPageTestConnection(id: String) : Connection {
    private val description = HostDescription(ProtocolVersion.Current, HostId(id), "Host " + id)
    override val host = object : HostApi {
        override suspend fun describe() = ApiResult(ApiResult.SUCCESS_CODE, "", description)
    }
    override val session = object : SessionApi {
        override suspend fun list(): List<SessionSummary> = emptyList()
        override suspend fun create(id: SessionId?, options: CreateSessionOptions, agentOptions: AgentOptions): SessionSummary = error("Unexpected session create")
        override suspend fun history(id: SessionId): List<Message> = error("Unexpected history read")
        override suspend fun prompt(id: SessionId, message: Message): SessionPrompt = error("Unexpected prompt")
        override suspend fun cancel(id: SessionId, keepInbox: Boolean) = error("Unexpected session cancel")
    }
}

internal object PluginsPageDisplayData {
    val longName = "跨平台插件审查 / " + "Long plugin name ".repeat(18)
    val longPath = "/test-only/" + "long directory with spaces/".repeat(24) + "source-end"
    val longDescription = "可选择复制的完整说明。".repeat(100) + "DESCRIPTION-END"
    val longSkillName = "Review / " + "long skill name ".repeat(20) + "SKILL-END"
    val longAppName = "App / " + "long app name ".repeat(20) + "APP-END"
    fun plugins(count: Int = 3) = (0 until count).map { index -> InstalledPlugin(
        PluginId("display-" + index), if (index == 0) "Review toolkit" else "Plugin " + index,
        description = "Review source and document changes.", version = "1.0." + index,
        source = "/test-only/plugins/" + index,
        enabled = if (index % 2 == 0) PluginEnabledState.Enabled else PluginEnabledState.Disabled) }
    val longPlugin = plugins().first().copy(name = longName, source = longPath, description = longDescription)
    fun detail(plugin: InstalledPlugin = longPlugin) = InstalledPluginDetail(plugin,
        skills = PluginSkillsState.Available(listOf(PluginSkillSummary(longSkillName, longDescription), PluginSkillSummary("Short skill", "Short summary"))),
        apps = PluginAppsState.Available(listOf(longAppName, "Short app")),
        unsupportedAssets = listOf("Test adapter resources; integration is unavailable."))
    fun state(count: Int = 3) = InstalledPluginsState(
        scope = PluginScope(HostId("plugins-display-host"), 1), list = PluginLoadState.Success(plugins(count)),
        hasReadAdapter = true, hasManagementAdapter = true, revision = PluginLoadState.Success("test-revision"))
    fun detailState() = state().copy(selectedId = longPlugin.id, detail = PluginLoadState.Success(detail()))
}

/** Semantics can become enabled before the next layout has placed the actual pointer target. */
internal fun ComposeUiTest.pluginsPageSettle() {
    mainClock.advanceTimeByFrame()
    waitForIdle()
}

/** Resolve the target's own root, including dialogs, rather than assuming the app root. */
private fun ComposeUiTest.pluginsPagePointerRoot(target: SemanticsNodeInteraction): SemanticsNodeInteraction {
    val root = generateSequence(target.fetchSemanticsNode()) { it.parent }.last()
    return onNode(SemanticsMatcher("Pointer root ${root.id}") { it.id == root.id }, useUnmergedTree = true)
}

/** Selected geometry only: never dump the semantics tree or infer a screen origin. */
private fun pluginsPageInputGeometry(node: SemanticsNode): JsonObject = buildJsonObject {
    val root = generateSequence(node) { it.parent }.last()
    put("tag", node.config.getOrNull(SemanticsProperties.TestTag))
    put("id", node.id); put("rootId", root.id)
    put("positionInRoot", node.positionInRoot.toString()); put("size", node.size.toString())
    put("layoutInRoot", Rect(node.positionInRoot, Size(node.size.width.toFloat(), node.size.height.toFloat())).toString())
    put("boundsInRoot", node.boundsInRoot.toString()); put("boundsInWindow", node.boundsInWindow.toString())
    put("rootPositionInRoot", root.positionInRoot.toString())
    put("rootBoundsInRootOrigin", root.boundsInRoot.topLeft.toString())
    put("rootBoundsInWindowOrigin", root.boundsInWindow.topLeft.toString())
    put("rootBoundsInRoot", root.boundsInRoot.toString())
    put("disabled", node.config.contains(SemanticsProperties.Disabled))
    put("hasOnClick", node.config.contains(SemanticsActions.OnClick))
    put("focused", node.config.getOrNull(SemanticsProperties.Focused))
    putJsonArray("text") { node.config.getOrNull(SemanticsProperties.Text)?.take(3)?.forEach { add(it.text.take(120)) } }
    node.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)?.let {
        put("verticalAxisValue", it.value().toString()); put("verticalAxisMax", it.maxValue().toString())
    }
    node.config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange)?.let {
        put("horizontalAxisValue", it.value().toString()); put("horizontalAxisMax", it.maxValue().toString())
    }
}

private fun ComposeUiTest.pluginsPageInputDiagnostic(label: String, tag: String, inputRootId: Int,
    point: Offset, listTag: String? = null, wheelSteps: Int? = null, outcome: String? = null) {
    val evidence = runCatching {
        val inputRoot = onAllNodes(SemanticsMatcher("Input root $inputRootId") { it.id == inputRootId },
            useUnmergedTree = true).fetchSemanticsNodes().singleOrNull()
        val targets = onAllNodesWithTag(tag, useUnmergedTree = listTag != null).fetchSemanticsNodes()
        val overlays = onAllNodes(isDialog() or isPopup() or SemanticsMatcher("Plugin dialog controls and overlays") {
            val nodeTag = it.config.getOrNull(SemanticsProperties.TestTag).orEmpty()
            nodeTag.startsWith("plugin-register-") || nodeTag.startsWith("plugin-remove-") ||
                nodeTag == "plugin-local-directory" ||
                listOf("popup", "overlay", "scrim", "dialog", "menu").any { part -> nodeTag.contains(part, ignoreCase = true) }
        }, useUnmergedTree = true).fetchSemanticsNodes()
        buildJsonObject {
            put("schema", "harness.plugins-page.input-diagnostic.v1")
            put("label", label); put("targetTag", tag); put("inputRootId", inputRootId)
            put("pointerInInputRoot", point.toString())
            put("inputRoot", inputRoot?.let(::pluginsPageInputGeometry) ?: JsonNull)
            putJsonArray("targets") { targets.forEach { add(pluginsPageInputGeometry(it)) } }
            putJsonArray("popupOverlayNodes") { overlays.forEach { add(pluginsPageInputGeometry(it)) } }
            if (listTag != null) {
                put("listTag", listTag); put("wheelSteps", wheelSteps); put("outcome", outcome)
                val list = onAllNodesWithTag(listTag).fetchSemanticsNodes().singleOrNull()
                put("list", list?.let(::pluginsPageInputGeometry) ?: JsonNull)
                if (list != null && inputRoot != null) {
                    val viewport = list.boundsInRoot.intersect(inputRoot.boundsInRoot)
                    put("viewportInRoot", viewport.toString())
                    put("gutterInInputRoot", (Offset(viewport.left + minOf(4f, viewport.width / 2f), viewport.center.y) -
                        inputRoot.boundsInRoot.topLeft).toString())
                }
            }
        }
    }.getOrElse { failure -> buildJsonObject {
        put("label", label); put("targetTag", tag); put("inputRootId", inputRootId)
        put("pointerInInputRoot", point.toString())
        if (listTag != null) {
            put("listTag", listTag); put("wheelSteps", wheelSteps); put("outcome", outcome)
        }
        put("diagnosticErrorType", failure.javaClass.name)
    } }
    println("PLUGINS_INPUT_DIAGNOSTIC $evidence")
}

internal fun ComposeUiTest.pluginsPageMouseClick(tag: String) {
    pluginsPageSettle()
    val target = onNodeWithTag(tag).assertIsDisplayed()
    val root = pluginsPagePointerRoot(target)
    val inputRootId = root.fetchSemanticsNode().id
    val hoverPoint = target.fetchSemanticsNode().boundsInRoot.center - root.fetchSemanticsNode().boundsInRoot.topLeft
    pluginsPageInputDiagnostic("click.before", tag, inputRootId, hoverPoint)
    root.performMouseInput { moveTo(hoverPoint) }
    // Hover updates HarnessActionSurface. Finish that remeasure before resolving the press position.
    pluginsPageSettle()
    pluginsPageInputDiagnostic("click.hover-after", tag, inputRootId, hoverPoint)
    target.assertIsDisplayed()
    val node = target.fetchSemanticsNode()
    val requiredHeight = if (tag.startsWith("installed-plugin-"))
        minOf(node.size.height.toFloat(), onNodeWithTag("plugin-inventory-list").fetchSemanticsNode().boundsInRoot.height)
    else node.size.height.toFloat()
    assertTrue(node.boundsInRoot.width + 1f >= node.size.width && node.boundsInRoot.height + 1f >= requiredHeight,
        "Pointer target must be visible: $tag, layout=${node.size}, visible=${node.boundsInRoot}")
    val point = node.boundsInRoot.center - root.fetchSemanticsNode().boundsInRoot.topLeft
    pluginsPageInputDiagnostic("click.press-before", tag, inputRootId, point)
    root.performMouseInput {
        moveTo(point)
        advanceEventTime(16)
        click(point)
    }
    pluginsPageSettle()
    pluginsPageInputDiagnostic("click.after", tag, inputRootId, point)
}

/** Scroll exactly the remaining field range, so excess delta does not reach the parent list. */
internal fun ComposeUiTest.pluginsPageScrollToEnd(tag: String, horizontal: Boolean = false) {
    pluginsPageSettle()
    val target = onNodeWithTag(tag, useUnmergedTree = true).assertIsDisplayed()
    val root = pluginsPagePointerRoot(target)
    val inputRootId = root.fetchSemanticsNode().id
    val listTag = generateSequence(target.fetchSemanticsNode().parent) { it.parent }.firstOrNull {
        it.config.getOrNull(SemanticsProperties.TestTag) != null &&
            (it.config.contains(SemanticsProperties.VerticalScrollAxisRange) ||
                it.config.contains(SemanticsProperties.HorizontalScrollAxisRange))
    }?.config?.getOrNull(SemanticsProperties.TestTag)
    fun pointerPoint(): Offset {
        target.assertIsDisplayed()
        val node = target.fetchSemanticsNode()
        assertEquals(inputRootId, generateSequence(node) { it.parent }.last().id,
            "Reader must remain in its pointer root: $tag")
        val visible = node.boundsInRoot
        assertTrue(visible.width > 0f && visible.height > 0f, "Reader must have a pointer viewport: $tag")
        return visible.center - root.fetchSemanticsNode().boundsInRoot.topLeft
    }
    var point = pointerPoint()
    var wheelSteps = 0
    var outcome = "interrupted"
    pluginsPageInputDiagnostic("reader.before", tag, inputRootId, point, listTag, wheelSteps, "pending")
    try {
        root.performMouseInput { moveTo(point) }
        pluginsPageSettle()
        pluginsPageInputDiagnostic("reader.hover-after", tag, inputRootId, point, listTag, wheelSteps, "pending")
        // Hover can remeasure the reader; resolve its visible center again before the wheel.
        point = pointerPoint()
        val axis = if (horizontal) SemanticsProperties.HorizontalScrollAxisRange else SemanticsProperties.VerticalScrollAxisRange
        val reader = target.fetchSemanticsNode()
        val before = reader.config[axis]
        assertTrue(before.maxValue() > 0f, "Expected an overflowing reader")
        val remaining = before.maxValue() - before.value()
        pluginsPageInputDiagnostic("reader.wheel-before", tag, inputRootId, point, listTag, wheelSteps, "pending")
        if (remaining > 0f) {
            // Compose 1.11.1 DesktopScrollable converts wheel units to pixels; the Skiko
            // dispatcher sends the delta unchanged, without an AWT event (scrollAmount = 1).
            val osName = System.getProperty("os.name").orEmpty()
            val axisSize = (if (horizontal) reader.size.width else reader.size.height).toFloat()
            val pixelsPerWheelUnit = when {
                osName == "Mac OS X" -> 10f * reader.layoutInfo.density.density
                osName.startsWith("Linux") -> kotlin.math.sqrt(axisSize)
                else -> axisSize / 20f // WindowsWinUIConfig, also the unknown-platform default.
            }
            val wheelDelta = remaining / pixelsPerWheelUnit
            root.performMouseInput {
                moveTo(point)
                advanceEventTime(16)
                if (horizontal) scroll(wheelDelta, ScrollWheel.Horizontal) else scroll(wheelDelta)
            }
            wheelSteps = 1
        }
        pluginsPageSettle()
        val after = target.fetchSemanticsNode().config[axis]
        assertTrue(kotlin.math.abs(after.maxValue() - after.value()) <= 1f, "Reader must expose its end")
        outcome = "reached"
    } finally {
        pluginsPageInputDiagnostic("reader.after", tag, inputRootId, point, listTag, wheelSteps, outcome)
    }
}

/** Mouse wheel in the list gutter avoids nested readers and exercises actual scroll input. */
internal fun ComposeUiTest.pluginsPageWheelTo(listTag: String, itemTag: String, delta: Float = 300f) {
    require(delta.isFinite() && delta != 0f)
    pluginsPageSettle()
    val list = onNodeWithTag(listTag).assertIsDisplayed()
    val root = pluginsPagePointerRoot(list)
    val inputRootId = root.fetchSemanticsNode().id
    fun viewport(): Rect = list.fetchSemanticsNode().boundsInRoot.intersect(root.fetchSemanticsNode().boundsInRoot)
    fun gutter(): Offset {
        val visible = viewport()
        assertTrue(visible.width > 0f && visible.height > 0f, "List must have a pointer viewport: $listTag")
        return Offset(visible.left + minOf(4f, visible.width / 2f), visible.center.y) -
            root.fetchSemanticsNode().boundsInRoot.topLeft
    }
    val hoverPoint = gutter()
    root.performMouseInput { moveTo(hoverPoint) }
    pluginsPageSettle()
    var wheelSteps = 0
    var lastPoint = hoverPoint
    var outcome = "interrupted"
    pluginsPageInputDiagnostic("wheel.first-before", itemTag, inputRootId, lastPoint, listTag, wheelSteps, "pending")
    try {
        repeat(90) {
            val viewport = viewport()
            val step = minOf(kotlin.math.abs(delta), viewport.height * .75f)
            val item = onAllNodesWithTag(itemTag, useUnmergedTree = true).fetchSemanticsNodes().singleOrNull()
            var distance = if (delta > 0f) step else -step
            if (item != null) {
                val layout = Rect(item.positionInRoot, Size(item.size.width.toFloat(), item.size.height.toFloat()))
                // A tall card exposes a viewport-sized slice; controls and bounded readers still fit in full.
                val requiredHeight = minOf(layout.height, viewport.height)
                val visible = item.boundsInRoot.intersect(viewport)
                if (visible.height + 1f >= requiredHeight && requiredHeight > 0f &&
                    visible.width > 0f) {
                    outcome = "reached"
                    return
                }
                distance = when {
                    layout.bottom <= viewport.top -> -minOf(step, viewport.top - layout.top)
                    layout.top >= viewport.bottom -> minOf(step, layout.bottom - viewport.bottom)
                    layout.top < viewport.top -> -minOf(step, viewport.top - layout.top)
                    layout.bottom > viewport.bottom -> minOf(step, layout.bottom - viewport.bottom)
                    else -> error("Target is clipped outside the list viewport: $itemTag, layout=$layout, visible=$visible, viewport=$viewport")
                }
            }
            val point = gutter()
            lastPoint = point
            root.performMouseInput { moveTo(point); advanceEventTime(16); scroll(distance) }
            pluginsPageSettle()
            wheelSteps = it + 1
        }
        outcome = "exhausted"
        error("Mouse wheel could not reveal the pointer viewport for $itemTag in $listTag")
    } finally {
        pluginsPageInputDiagnostic("wheel.last-after", itemTag, inputRootId, lastPoint, listTag, wheelSteps, outcome)
    }
}

/** Every PNG gets tagged geometry, with explicit separate-root popup coordinates. */
internal fun SemanticsNodeInteractionsProvider.pluginsPageCapture(directory: File, name: String, state: String, theme: HarnessThemeMode,
    width: Int, height: Int, dialog: Boolean = false) {
    directory.mkdirs()
    val target = if (dialog) onNode(isDialog()) else onNodeWithTag("plugins-page-capture-viewport")
    val captureNode = target.fetchSemanticsNode()
    val rootId = generateSequence(captureNode) { it.parent }.last().id
    val bitmap = target.captureToImage()
    val bytes = Image.makeFromBitmap(bitmap.asSkiaBitmap()).use { image ->
        image.encodeToData(EncodedImageFormat.PNG)?.use { it.bytes } ?: error("PNG encoding failed")
    }
    val png = File(directory, name + ".png").also { it.writeBytes(bytes) }
    val decoded = assertNotNull(ImageIO.read(png))
    assertEquals(width, decoded.width); assertEquals(height, decoded.height)
    val nodes = onAllNodes(SemanticsMatcher("tagged nodes") { it.config.contains(SemanticsProperties.TestTag) },
        useUnmergedTree = true).fetchSemanticsNodes()
    val geometry = buildJsonObject {
        put("schema", "harness.plugins-page.capture.v1"); put("image", png.name)
        put("state", state); put("theme", theme.name); put("width", width); put("height", height)
        put("density", 1f); put("units", "pixels")
        put("captureRootId", rootId); put("dialog", dialog)
        put("coordinates", if (dialog) "Dialog capture is the full popup viewport including scrim. Each node has its own rootId; boundsInRoot is root-local. boundsInWindow is separate; page and popup origins must not be conflated."
            else "Page viewport. boundsInRoot is root-local; layout bounds may extend beyond the clipped viewport.")
        put("sha256", MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
        putJsonArray("tags") { nodes.forEach { node -> add(buildJsonObject {
            put("tag", node.config[SemanticsProperties.TestTag]); put("id", node.id)
            val nodeRoot = generateSequence(node) { it.parent }.last().id
            put("rootId", nodeRoot); put("inCaptureRoot", nodeRoot == rootId)
            putJsonObject("layoutBounds") {
                put("x", node.positionInRoot.x); put("y", node.positionInRoot.y)
                put("width", node.size.width); put("height", node.size.height)
            }
            putJsonObject("boundsInRoot") {
                put("left", node.boundsInRoot.left); put("top", node.boundsInRoot.top)
                put("right", node.boundsInRoot.right); put("bottom", node.boundsInRoot.bottom)
            }
            putJsonObject("boundsInWindow") {
                put("left", node.boundsInWindow.left); put("top", node.boundsInWindow.top)
                put("right", node.boundsInWindow.right); put("bottom", node.boundsInWindow.bottom)
            }
            put("disabled", node.config.contains(SemanticsProperties.Disabled))
            node.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)?.let {
                put("verticalScroll", it.value()); put("verticalScrollMax", it.maxValue())
            }
            node.config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange)?.let {
                put("horizontalScroll", it.value()); put("horizontalScrollMax", it.maxValue())
            }
        }) } }
    }
    File(directory, name + ".json").writeText(Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), geometry))
}

/**
 * Captures the whole production Shell (icon rail + page-owned navigator + page) and writes the
 * tagged geometry beside the PNG. Writes nothing when [directory] is null, so ordinary runs stay
 * side-effect free. Used to compare the plugins navigator against the 2026-10-06 Codex capture.
 */
internal fun ComposeUiTest.pluginsShellCapture(directory: File?, name: String, width: Int, height: Int) {
    if (directory == null) return
    directory.mkdirs()
    val bitmap = onRoot().captureToImage()
    assertEquals(width, bitmap.width); assertEquals(height, bitmap.height)
    val bytes = Image.makeFromBitmap(bitmap.asSkiaBitmap()).use { image ->
        image.encodeToData(EncodedImageFormat.PNG)?.use { it.bytes } ?: error("PNG encoding failed")
    }
    val png = File(directory, name + ".png").also { it.writeBytes(bytes) }
    val decoded = assertNotNull(ImageIO.read(png))
    assertEquals(width, decoded.width); assertEquals(height, decoded.height)
    val nodes = onAllNodes(SemanticsMatcher("tagged nodes") { it.config.contains(SemanticsProperties.TestTag) },
        useUnmergedTree = true).fetchSemanticsNodes()
    val geometry = buildJsonObject {
        put("schema", "harness.plugins-navigator.shell.v1"); put("image", png.name)
        put("width", width); put("height", height); put("density", 1f); put("units", "pixels")
        put("note", "Whole production Shell capture. boundsInRoot is window-local and excludes native chrome.")
        put("sha256", MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
        putJsonArray("tags") { nodes.forEach { node -> add(buildJsonObject {
            put("tag", node.config[SemanticsProperties.TestTag]); put("id", node.id)
            put("rootId", generateSequence(node) { it.parent }.last().id)
            putJsonObject("layoutBounds") {
                put("x", node.positionInRoot.x); put("y", node.positionInRoot.y)
                put("width", node.size.width); put("height", node.size.height)
            }
            putJsonObject("boundsInRoot") {
                put("left", node.boundsInRoot.left); put("top", node.boundsInRoot.top)
                put("right", node.boundsInRoot.right); put("bottom", node.boundsInRoot.bottom)
            }
            put("disabled", node.config.contains(SemanticsProperties.Disabled))
            put("selected", node.config.contains(SemanticsProperties.Selected))
        }) } }
    }
    File(directory, name + ".json").writeText(Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), geometry))
}
