@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class,
    kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package im.hikaru.harness.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import im.hikaru.contracts.common.ApiResult
import im.hikaru.contracts.harness.identity.HostDescription
import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.contracts.harness.protocol.ProtocolVersion
import im.hikaru.harness.api.gateway.ApiGateway
import im.hikaru.harness.api.gateway.host.HostDescribeEndpoint
import im.hikaru.harness.bundle.desktop.DesktopUserPlugins
import im.hikaru.harness.client.connection.ConnectionTransport
import im.hikaru.harness.client.plugins.*
import im.hikaru.harness.desktop.connection.LocalConnection
import im.hikaru.harness.desktop.plugins.DesktopInstalledPluginRepository
import im.hikaru.harness.ui.foundation.HarnessTheme
import im.hikaru.harness.ui.foundation.HarnessThemeMode
import java.io.File
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardOpenOption.WRITE
import kotlinx.coroutines.*
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.serialization.json.*
import kotlin.math.abs
import kotlin.test.*

/** Success always comes from disk -> LocalConnection adapter -> production controller -> page. */
class PluginLocalUpdateInteractionTest {
    @Test fun sourceUpdatePreservesEnabledAndReloadsVersionAndSkillMetadata() {
        for (enabled in listOf(false, true)) LocalUpdateHarness(enabled).use { f ->
            runSkikoComposeUiTest(size = Size(640f, 800f)) {
                mount(f); select(f, f.first)
                val oldBytes = Files.readAllBytes(f.file)
                f.writeSource("opaque-new")
                reveal(f, "plugin-update-local"); pluginsPageMouseClick("plugin-update-local")
                settle(f)
                assertNotEquals(oldBytes.toList(), Files.readAllBytes(f.file).toList())
                assertSaved(f, "opaque-new", enabled)
                reveal(f, "plugin-detail-version"); onNodeWithTag("plugin-detail-version").assertTextEquals("opaque-new")
                reveal(f, "plugin-skill-5-name")
                onNodeWithTag("plugin-skill-5-name", true).assertTextEquals(f.lastName("opaque-new"))
                assertEquals(f.lastDescription("opaque-new"),
                    assertIs<PluginSkillsState.Available>(f.detail().skills).skills.last().description)
                reveal(f, "plugin-update-local")
                // Focus navigation and activation use real keys, with a bounded focus search.
                val root = ownRoot(onNodeWithTag("plugin-update-local"))
                val deadline = System.nanoTime() + 10_000_000_000L
                while (onNodeWithTag("plugin-update-local").fetchSemanticsNode().config
                        .getOrNull(SemanticsProperties.Focused) != true) {
                    check(System.nanoTime() < deadline) { "Update button did not receive keyboard focus" }
                    root.performKeyInput { pressKey(Key.Tab) }; settle(f)
                }
                val bytes = Files.readAllBytes(f.file)
                root.performKeyInput { pressKey(Key.Enter) }; settle(f)
                assertContentEquals(bytes, Files.readAllBytes(f.file))
                assertNotNull(f.state.mutationReceipt)
            }
        }
    }

    @Test fun failedSourceKeepsSnapshotRequiresRefreshAndNextBusyClearsOldReceipt() = LocalUpdateHarness(true).use { f ->
        runSkikoComposeUiTest(size = Size(640f, 800f)) {
            mount(f); select(f, f.first)
            f.writeSource("saved"); pluginsPageMouseClick("plugin-update-local"); settle(f)
            assertNotNull(f.state.mutationReceipt)
            val saved = Files.readAllBytes(f.file)
            val savedSnapshot = runBlocking { f.service.read() }
            for (missing in listOf(false, true)) {
                if (missing) Files.delete(f.manifest) else Files.writeString(f.manifest, "invalid-json")
                reveal(f, "plugin-update-local"); pluginsPageMouseClick("plugin-update-local"); settle(f)
                assertTrue(f.state.mutationRequiresRefresh); assertNull(f.state.mutationReceipt)
                assertNull(f.state.managementNotice); assertNotNull(f.state.mutationError)
                onNodeWithTag("plugin-update-local").assertIsNotEnabled()
                reveal(f, "plugin-mutation-error"); onNodeWithTag("plugin-mutation-error").assertTextContains("显式刷新", substring = true)
                assertContentEquals(saved, Files.readAllBytes(f.file))
                assertEquals(savedSnapshot, runBlocking { f.service.read() })
                assertEquals("saved", f.detail().plugin.version)
                f.writeSource("next")
                f.controller.load(); settle(f)
                assertFalse(f.state.canUpdateLocal, "Ordinary reload cannot unlock a failed write")
                reveal(f, "plugin-detail-refresh"); pluginsPageMouseClick("plugin-detail-refresh"); settle(f)
                assertTrue(f.state.canUpdateLocal)
            }
            // Leave an actual prior success visible, then prove the next queued write clears it.
            f.writeSource("saved"); reveal(f, "plugin-update-local")
            pluginsPageMouseClick("plugin-update-local"); settle(f)
            assertNotNull(f.state.mutationReceipt); assertNotNull(f.state.managementNotice)
            f.writeSource("next")
            f.locked {
                pluginsPageMouseClick("plugin-update-local"); settle(f)
                assertBusy(f)
                assertNull(f.state.mutationReceipt); assertNull(f.state.managementNotice)
                assertContentEquals(saved, Files.readAllBytes(f.file))
            }
            finish(f); assertSaved(f, "next", true)
        }
    }

    @Test fun updateTargetIsFrozenWhileBackAndAnotherDetailAreSelected() = LocalUpdateHarness().use { f ->
        runSkikoComposeUiTest(size = Size(640f, 800f)) {
            mount(f); select(f, f.first)
            val other = runBlocking { f.service.read() }.plugins.last()
            f.writeSource("frozen-target")
            f.locked {
                pluginsPageMouseClick("plugin-update-local"); settle(f); assertBusy(f)
                pluginsPageMouseClick("plugin-detail-back"); settle(f)
                select(f, f.second)
                assertEquals(f.second, f.detail().plugin.id)
                onNodeWithTag("plugin-update-local").assertIsNotEnabled()
                f.controller.dispatch(InstalledPluginsAction.UpdateLocal(f.second)); settle(f)
            }
            finish(f)
            val saved = runBlocking { f.service.read() }
            assertEquals("frozen-target", saved.plugins.first().version)
            assertEquals(other, saved.plugins.last())
            assertEquals(f.second, f.state.selectedId); assertEquals(other.version, f.detail().plugin.version)
            assertNull(f.state.mutationError)
        }
    }

    @Test fun generationRevocationDuringActualFileLockWaitCannotCommitOrPublishReceipt() = LocalUpdateHarness().use { f ->
        runSkikoComposeUiTest(size = Size(640f, 800f)) {
            mount(f); select(f, f.first)
            val bytes = Files.readAllBytes(f.file); f.writeSource("revoked")
            f.locked {
                pluginsPageMouseClick("plugin-update-local"); settle(f); assertBusy(f)
                f.rebind(f.scope.copy(generation = 2)); settle(f)
                assertNull(f.state.selectedId); assertNull(f.state.mutationReceipt)
                assertTrue(f.state.mutationBusy)
            }
            finish(f)
            assertContentEquals(bytes, Files.readAllBytes(f.file))
            assertEquals(2L, f.state.scope?.generation); assertNull(f.state.mutationReceipt)
            select(f, f.first); assertEquals("before", f.detail().plugin.version)
        }
    }

    @Test fun controllerCloseCancelsUpdateWaitingOnActualFileLock() = LocalUpdateHarness().use { f ->
        runSkikoComposeUiTest(size = Size(640f, 800f)) {
            mount(f); select(f, f.first)
            val bytes = Files.readAllBytes(f.file); f.writeSource("disposed")
            f.locked {
                pluginsPageMouseClick("plugin-update-local"); settle(f); assertBusy(f)
                f.controller.close(); settle(f)
            }
            f.scheduler.advanceTimeBy(20); settle(f)
            assertContentEquals(bytes, Files.readAllBytes(f.file))
            assertEquals(InstalledPluginsState(), f.state)
            f.controller.dispatch(InstalledPluginsAction.UpdateLocal(f.first)); settle(f)
            assertContentEquals(bytes, Files.readAllBytes(f.file))
        }
    }

    @Test fun lateRealHostDescribeCannotPublishOldDetailAfterOwnerReplacement() = LocalUpdateHarness().use { f ->
        runSkikoComposeUiTest(size = Size(640f, 800f)) {
            mount(f)
            val gate = CompletableDeferred<Unit>(); f.describeGate = gate
            select(f, f.first, ready = false)
            assertIs<PluginLoadState.Loading>(f.state.detail)
            val bytes = Files.readAllBytes(f.file)
            f.controller.dispatch(InstalledPluginsAction.UpdateLocal(f.first)); settle(f)
            assertContentEquals(bytes, Files.readAllBytes(f.file))
            f.describeGate = null
            f.rebind(f.scope.copy(generation = 2, accountId = PluginAccountId("replacement-owner"))); settle(f)
            gate.complete(Unit); settle(f)
            assertNull(f.state.selectedId); assertIs<PluginLoadState.NotLoaded>(f.state.detail)
            assertNull(f.state.mutationReceipt); assertContentEquals(bytes, Files.readAllBytes(f.file))
            select(f, f.second); assertEquals(f.second, f.detail().plugin.id)
        }
    }

    @Test fun forgedUnselectedAndErrorDetailUpdatesCannotWrite() = LocalUpdateHarness().use { f ->
        runSkikoComposeUiTest(size = Size(640f, 800f)) {
            mount(f); val bytes = Files.readAllBytes(f.file); f.writeSource("must-not-save")
            f.controller.dispatch(InstalledPluginsAction.UpdateLocal(f.first)); settle(f)
            select(f, f.first)
            f.controller.dispatch(InstalledPluginsAction.UpdateLocal(f.second))
            f.controller.dispatch(InstalledPluginsAction.UpdateLocal(PluginId("forged"))); settle(f)
            // An authoritative route may load a missing ID; an Error is never a write capability.
            f.controller.select(PluginId("missing")); settle(f)
            assertIs<PluginLoadState.Error>(f.state.detail)
            f.controller.dispatch(InstalledPluginsAction.UpdateLocal(PluginId("missing"))); settle(f)
            assertContentEquals(bytes, Files.readAllBytes(f.file)); assertNull(f.state.mutationReceipt)
        }
    }

    @Test fun optionalAdapterDefaultHidesUpdateAndRejectsDispatch() = LocalUpdateHarness(updateSupported = false).use { f ->
        runSkikoComposeUiTest(size = Size(640f, 800f)) {
            mount(f); select(f, f.first)
            onNodeWithTag("plugin-set-enabled").assertIsEnabled()
            onNodeWithTag("plugin-update-local").assertDoesNotExist()
            assertFalse(f.repository.canUpdateLocal)
            assertIs<PluginResult.UnsupportedHost>(runBlocking { f.repository.updateLocal(f.scope, f.first,
                assertIs<PluginLoadState.Success<String>>(f.state.revision).value) })
            val bytes = Files.readAllBytes(f.file); f.writeSource("unsupported")
            f.controller.dispatch(InstalledPluginsAction.UpdateLocal(f.first)); settle(f)
            assertContentEquals(bytes, Files.readAllBytes(f.file))
        }
    }

    @Test fun committedUpdateKeepsReceiptWhenPostWriteReadsFailAndRefreshRecovers() = LocalUpdateHarness().use { f ->
        runSkikoComposeUiTest(size = Size(640f, 800f)) {
            mount(f); select(f, f.first)
            f.readFaults.failAfterUpdate = true
            f.writeSource("saved-read-failed")
            pluginsPageMouseClick("plugin-update-local"); settle(f)
            val saved = runBlocking { f.service.read() }
            assertEquals("saved-read-failed", saved.plugins.first().version)
            assertEquals(saved.revision, assertNotNull(f.state.mutationReceipt).revision)
            assertIs<PluginLoadState.Error>(f.state.list); assertIs<PluginLoadState.Error>(f.state.detail)
            reveal(f, "plugin-saved-refresh-failure"); onNodeWithTag("plugin-saved-refresh-failure").assertIsDisplayed()
            assertFalse(f.state.canUpdateLocal)
            f.readFaults.failReads = false; f.readFaults.failAfterUpdate = false
            reveal(f, "plugin-detail-refresh"); pluginsPageMouseClick("plugin-detail-refresh"); settle(f)
            assertSaved(f, "saved-read-failed", false)
            assertTrue(f.state.canUpdateLocal)
        }
    }

    @Test fun capturesRealSourceUpdateMatrixWithControlsAndCompleteLastSkillGeometry() {
        val output = System.getenv("HARNESS_PLUGIN_LOCAL_UPDATE_CAPTURE_DIR")?.let(::File)
        for (size in listOf(Size(320f, 600f), Size(640f, 800f)))
            for (theme in listOf(HarnessThemeMode.Light, HarnessThemeMode.Dark)) LocalUpdateHarness(true).use { f ->
                runSkikoComposeUiTest(size = size) {
                    mount(f, theme); select(f, f.first)
                    for (stage in listOf("before", "after")) {
                        if (stage == "after") {
                            f.writeSource(stage); reveal(f, "plugin-update-local")
                            pluginsPageMouseClick("plugin-update-local"); settle(f)
                            assertSaved(f, stage, true)
                        }
                        val controls = buildJsonArray {
                            for (tag in listOf("plugin-detail-back", "plugin-detail-refresh", "plugin-set-enabled", "plugin-update-local", "plugin-remove-registration")) {
                                reveal(f, tag); onNodeWithTag(tag).assertIsEnabled()
                                add(geometry(onNodeWithTag(tag).fetchSemanticsNode()))
                            }
                        }
                        reveal(f, "plugin-detail-version"); onNodeWithTag("plugin-detail-version").assertTextEquals(stage)
                        reveal(f, "plugin-skill-5-name")
                        pluginsPageScrollToEnd("plugin-skill-5-name", horizontal = true); settle(f)
                        val nameProof = endProof("plugin-skill-5-name", f.lastName(stage), horizontal = true)
                        reveal(f, "plugin-skill-5-expand"); pluginsPageMouseClick("plugin-skill-5-expand"); settle(f)
                        reveal(f, "plugin-skill-5-description-reader")
                        pluginsPageScrollToEnd("plugin-skill-5-description-reader"); settle(f)
                        reveal(f, "plugin-skill-5-description-reader")
                        val descriptionProof = endProof("plugin-skill-5-description", f.lastDescription(stage), horizontal = false)
                        val last = onNodeWithTag("plugin-skill-5-description-reader", true).fetchSemanticsNode()
                        val viewport = onNodeWithTag("plugins-page-capture-viewport").fetchSemanticsNode()
                        assertEquals(size.width.toInt(), viewport.size.width); assertEquals(size.height.toInt(), viewport.size.height)
                        assertEquals(generateSequence(last) { it.parent }.last().id, generateSequence(viewport) { it.parent }.last().id)
                        assertTrue(last.boundsInRoot.intersect(viewport.boundsInRoot).height + 1f >= last.size.height)
                        val name = "local-update-" + size.width.toInt() + "x" + size.height.toInt() + "-" + theme.name.lowercase() + "-" + stage
                        output?.let { directory ->
                            pluginsPageCapture(directory, name, stage, theme, size.width.toInt(), size.height.toInt())
                            File(directory, name + ".proof.json").writeText(Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), buildJsonObject {
                                put("schema", "harness.plugin-local-update.geometry.v1")
                                put("pluginId", f.first.value); put("revision", runBlocking { f.service.read() }.revision)
                                put("controlsBeforeWheel", controls); put("lastSkill", geometry(last))
                                put("nameEnd", nameProof); put("descriptionEnd", descriptionProof)
                            }))
                        }
                    }
                }
            }
    }

    private fun ComposeUiTest.mount(f: LocalUpdateHarness, theme: HarnessThemeMode = HarnessThemeMode.Light) {
        setContent {
            val state by f.controller.state.collectAsState()
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                HarnessTheme(themeMode = theme) {
                    Box(Modifier.fillMaxSize().semantics { testTag = "plugins-page-capture-viewport" }) {
                        if (state.selectedId == null) InstalledPluginsPage(state, f.controller::dispatch)
                        else InstalledPluginDetailPage(state, f.controller::dispatch)
                    }
                }
            }
        }
        f.rebind(f.scope); settle(f)
        assertIs<PluginLoadState.Success<List<InstalledPlugin>>>(f.state.list)
    }

    private fun ComposeUiTest.settle(f: LocalUpdateHarness) {
        f.scheduler.runCurrent(); pluginsPageSettle(); f.scheduler.runCurrent(); pluginsPageSettle()
    }

    private fun ComposeUiTest.finish(f: LocalUpdateHarness) {
        val deadline = System.nanoTime() + 10_000_000_000L
        while (f.state.mutationBusy) {
            check(System.nanoTime() < deadline) { "Update did not finish within 10 seconds" }
            f.scheduler.advanceTimeBy(10); settle(f)
        }
        settle(f)
    }

    private fun ComposeUiTest.select(f: LocalUpdateHarness, id: PluginId, ready: Boolean = true) {
        reveal(f, "installed-plugin-" + id.value, "plugin-inventory-list")
        pluginsPageMouseClick("installed-plugin-" + id.value); settle(f)
        assertEquals(id, f.state.selectedId)
        if (ready) assertEquals(id, f.detail().plugin.id)
    }

    private fun ComposeUiTest.assertBusy(f: LocalUpdateHarness) {
        assertTrue(f.state.mutationBusy)
        for (tag in listOf("plugin-update-local", "plugin-set-enabled", "plugin-remove-registration"))
            onNodeWithTag(tag).assertIsNotEnabled()
        val hostReads = f.hostReads
        f.controller.dispatch(InstalledPluginsAction.UpdateLocal(f.first)); settle(f)
        assertEquals(hostReads, f.hostReads, "A duplicate busy update must not reach the real adapter")
        assertTrue(f.state.mutationBusy)
    }

    private fun assertSaved(f: LocalUpdateHarness, version: String, enabled: Boolean) {
        assertFalse(f.state.mutationBusy); assertNull(f.state.mutationError)
        val snapshot = runBlocking { DesktopUserPlugins(f.file).read() }
        assertEquals(version, snapshot.plugins.first().version)
        assertEquals(enabled, snapshot.plugins.first().enabled)
        assertEquals(f.first.value, snapshot.plugins.first().id)
        assertEquals("instructions-" + version + "-5\n", snapshot.plugins.first().skills.last().instructions)
        assertEquals(snapshot.revision, assertNotNull(f.state.mutationReceipt).revision)
        assertEquals(version, f.detail().plugin.version)
    }

    private fun ComposeUiTest.ownRoot(target: SemanticsNodeInteraction): SemanticsNodeInteraction {
        val root = generateSequence(target.fetchSemanticsNode()) { it.parent }.last()
        return onNode(SemanticsMatcher("Measured owning root") { it.id == root.id }, true)
    }

    /** The existing reader helper supplies wheel units; this list helper uses the same conversion. */
    private fun ComposeUiTest.reveal(f: LocalUpdateHarness, tag: String, listTag: String = "plugin-detail-list") {
        settle(f)
        val list = onNodeWithTag(listTag); val root = ownRoot(list)
        val deadline = System.nanoTime() + 10_000_000_000L
        while (true) {
            val node = list.fetchSemanticsNode(); val visible = node.boundsInRoot.intersect(root.fetchSemanticsNode().boundsInRoot)
            assertTrue(visible.height > 0f && visible.width > 0f)
            val target = onAllNodesWithTag(tag, true).fetchSemanticsNodes().singleOrNull()
            val layout = target?.let { Rect(it.positionInRoot, Size(it.size.width.toFloat(), it.size.height.toFloat())) }
            val clipped = target?.boundsInRoot?.intersect(visible)
            if (target != null && clipped != null && clipped.height + 1f >= minOf(target.size.height.toFloat(), visible.height) &&
                clipped.width > 0f) return
            check(System.nanoTime() < deadline) { "Real wheel did not reveal " + tag }
            val distance = when {
                layout == null -> {
                    // Compare page order only with actually visible landmarks in this same list.
                    // ScrollAxisRange is a scroll metric, not a LazyColumn item index.
                    val wanted = assertNotNull(pageOrder(f, listTag, tag), "Unknown reveal target: " + tag)
                    val landmarks = onAllNodes(SemanticsMatcher("Mounted page landmarks") {
                        it.config.getOrNull(SemanticsProperties.TestTag)?.let { label -> pageOrder(f, listTag, label) } != null
                    }, true).fetchSemanticsNodes().filter {
                        generateSequence(it.parent) { parent -> parent.parent }.any { parent -> parent.id == node.id } &&
                            it.boundsInRoot.intersect(visible).let { bounds -> bounds.width > 0f && bounds.height > 0f }
                    }
                    val comparisons = landmarks.map {
                        val order = assertNotNull(pageOrder(f, listTag, assertNotNull(it.config.getOrNull(SemanticsProperties.TestTag))))
                        val section = order.first.compareTo(wanted.first)
                        if (section != 0) section else order.second.compareTo(wanted.second)
                    }
                    when {
                        comparisons.isNotEmpty() && comparisons.all { it < 0 } -> visible.height * .6f
                        comparisons.isNotEmpty() && comparisons.all { it > 0 } -> -visible.height * .6f
                        else -> error("Cannot locate absent target relative to visible page landmarks: " + tag +
                            ", landmarks=" + landmarks.map { geometry(it) } + ", viewport=" + visible)
                    }
                }
                layout.top < visible.top -> -minOf(visible.height * .6f, visible.top - layout.top)
                layout.bottom > visible.bottom -> minOf(visible.height * .6f, layout.bottom - visible.bottom)
                else -> error("Target is clipped inside its measured list viewport: " + tag + ", layout=" + layout + ", viewport=" + visible)
            }
            val units = pixelsPerWheelUnit(node, horizontal = false)
            val point = Offset(visible.left + minOf(4f, visible.width / 2f), visible.center.y) - root.fetchSemanticsNode().boundsInRoot.topLeft
            root.performMouseInput { moveTo(point); advanceEventTime(16); scroll(distance / units) }
            settle(f)
        }
    }

    /** Semantic page sections and row identities, never numeric scroll positions or item offsets. */
    private fun pageOrder(f: LocalUpdateHarness, listTag: String, tag: String): Pair<Int, Int>? {
        if (tag.startsWith("plugin-mutation-") || tag == "plugin-saved-refresh-failure") return 1 to 0
        if (listTag == "plugin-inventory-list") {
            if (tag == "plugins-header" || tag.startsWith("plugins-")) return 0 to 0
            val row = f.state.visiblePlugins.orEmpty().indexOfFirst { "installed-plugin-" + it.id.value == tag }
            return if (row >= 0) 2 to row else null
        }
        return when {
            tag in setOf("plugin-detail-actions", "plugin-detail-back", "plugin-detail-refresh", "plugin-set-enabled",
                "plugin-update-local", "plugin-remove-registration") -> 0 to 0
            tag.startsWith("plugin-detail-name") -> 2 to 0
            tag.startsWith("plugin-detail-description") || tag.startsWith("plugin-detail-version") ||
                tag.startsWith("plugin-detail-source") || tag.startsWith("plugin-detail-status") -> 3 to 0
            tag.startsWith("plugin-skill-") -> tag.removePrefix("plugin-skill-").substringBefore('-').toIntOrNull()?.let { 4 to it }
            tag.startsWith("plugin-app-") -> tag.removePrefix("plugin-app-").substringBefore('-').toIntOrNull()?.let { 5 to it }
            else -> null
        }
    }

    private fun pixelsPerWheelUnit(node: SemanticsNode, horizontal: Boolean): Float {
        val size = (if (horizontal) node.size.width else node.size.height).toFloat()
        val os = System.getProperty("os.name").orEmpty()
        return when {
            os == "Mac OS X" -> 10f * node.layoutInfo.density.density
            os.startsWith("Linux") -> kotlin.math.sqrt(size)
            else -> size / 20f
        }
    }

    private fun ComposeUiTest.endProof(tag: String, expected: String, horizontal: Boolean): JsonObject {
        val target = onNodeWithTag(tag, true); target.assertTextEquals(expected)
        val node = target.fetchSemanticsNode(); val results = mutableListOf<TextLayoutResult>()
        assertTrue(assertNotNull(node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action).invoke(results))
        val text = results.single(); assertEquals(expected, text.layoutInput.text.text)
        for (line in 0 until text.lineCount) assertFalse(text.isLineEllipsized(line))
        val axisNode = if (horizontal) node else onNodeWithTag(tag + "-reader", true).fetchSemanticsNode()
        val axis = axisNode.config[if (horizontal) SemanticsProperties.HorizontalScrollAxisRange else SemanticsProperties.VerticalScrollAxisRange]
        val value = axis.value(); val max = axis.maxValue()
        assertTrue(max > 0f); assertTrue(abs(value - max) <= 1f)
        // The horizontalScroll tag owns the viewport. The vertical inner Text is already translated.
        val subtraction = if (horizontal) Offset(value, 0f) else Offset.Zero
        val origin = node.positionInRoot - subtraction
        val marker = expected.takeLastWhile { !it.isWhitespace() }
        val start = expected.length - marker.length
        var glyphs = text.getBoundingBox(start)
        for (index in start + 1 until expected.length) {
            val box = text.getBoundingBox(index)
            glyphs = Rect(minOf(glyphs.left, box.left), minOf(glyphs.top, box.top), maxOf(glyphs.right, box.right), maxOf(glyphs.bottom, box.bottom))
        }
        val measured = glyphs.translate(origin)
        val viewport = axisNode.boundsInRoot.intersect(onNodeWithTag("plugin-detail-list").fetchSemanticsNode().boundsInRoot)
        assertTrue(measured.left >= viewport.left - 1f && measured.top >= viewport.top - 1f &&
            measured.right <= viewport.right + 1f && measured.bottom <= viewport.bottom + 1f,
            "Complete marker must be visible: marker=" + measured + ", viewport=" + viewport)
        return buildJsonObject {
            put("fullText", expected); put("marker", marker); put("origin", node.positionInRoot.toString())
            put("scrollSubtraction", subtraction.toString()); put("markerBounds", measured.toString())
            put("visibleViewport", viewport.toString()); put("scrollValue", value); put("scrollMax", max)
            put("lineCount", text.lineCount); put("ellipsis", false)
        }
    }

    private fun geometry(node: SemanticsNode): JsonObject = buildJsonObject {
        put("tag", node.config.getOrNull(SemanticsProperties.TestTag)); put("rootId", generateSequence(node) { it.parent }.last().id)
        put("layoutOrigin", node.positionInRoot.toString()); put("layoutSize", node.size.toString())
        put("clippedBounds", node.boundsInRoot.toString()); put("disabled", node.config.contains(SemanticsProperties.Disabled))
    }
}

private class LocalUpdateHarness(enabled: Boolean = false, updateSupported: Boolean = true) : AutoCloseable {
    val directory = Files.createTempDirectory("plugin-local-update-ui-").toRealPath()
    val source = Files.createDirectories(directory.resolve("registered source"))
    val file = directory.resolve("home/plugins.json")
    val manifest = source.resolve(".codex-plugin/plugin.json")
    val service = DesktopUserPlugins(file)
    val host = HostId("plugin-local-update-ui")
    val gateway = ApiGateway()
    val connection = LocalConnection(gateway)
    val adapter = DesktopInstalledPluginRepository(host, connection, service)
    val readFaults = LocalUpdateReadFaults(adapter)
    val repository: LocalPluginManagementRepository = if (updateSupported) readFaults else LegacyUpdateAdapter(readFaults)
    val scheduler = TestCoroutineScheduler()
    private val jobs = CoroutineScope(SupervisorJob() + StandardTestDispatcher(scheduler))
    val controller = InstalledPluginsController(jobs, repository)
    var scope = PluginScope(host, 1)
    var describeGate: CompletableDeferred<Unit>? = null
    var hostReads = 0
    val first: PluginId
    val second: PluginId
    val state get() = controller.state.value

    init {
        writeSource("before")
        val other = Files.createDirectories(directory.resolve("other source"))
        Files.createDirectories(other.resolve(".codex-plugin")); Files.createDirectories(other.resolve("skills/other"))
        Files.writeString(other.resolve(".codex-plugin/plugin.json"), """{"name":"Other plugin","version":"other-old","skills":"skills"}""")
        Files.writeString(other.resolve("skills/other/SKILL.md"), "Other saved instructions\n")
        val ids = runBlocking {
            gateway.register(HostDescribeEndpoint) {
                hostReads++
                describeGate?.let { gate -> withContext(NonCancellable) { gate.await() } }
                ApiResult(ApiResult.SUCCESS_CODE, data = HostDescription(ProtocolVersion.Current, host, "Actual local plugin test host"))
            }
            var snapshot = service.registerLocal(source.toString(), service.read().revision)
            val target = PluginId(snapshot.plugins.single().id)
            snapshot = service.registerLocal(other.toString(), snapshot.revision)
            val otherId = PluginId(snapshot.plugins.last().id)
            if (enabled) service.setEnabled(target.value, true, snapshot.revision)
            target to otherId
        }
        first = ids.first; second = ids.second
    }

    fun lastName(version: String) = "Skill " + version + " " + "long-name ".repeat(16) + "NAME-END"
    fun lastDescription(version: String) = "Description " + version + " " + "Complete saved skill explanation. ".repeat(75) + "DESCRIPTION-END"
    fun writeSource(version: String) {
        Files.createDirectories(manifest.parent)
        Files.writeString(manifest, buildJsonObject {
            put("name", "Local source update plugin"); put("version", version); put("description", "Saved local snapshot " + version); put("skills", "skills")
        }.toString())
        repeat(6) { index ->
            val skill = source.resolve("skills/0" + index + "/SKILL.md"); Files.createDirectories(skill.parent)
            val name = if (index == 5) lastName(version) else "Skill " + index + " " + version
            val description = if (index == 5) lastDescription(version) else "Real source metadata " + version
            Files.writeString(skill, "---\nname: " + JsonPrimitive(name) + "\ndescription: " + JsonPrimitive(description) +
                "\n---\ninstructions-" + version + "-" + index + "\n")
        }
    }

    fun rebind(next: PluginScope) {
        scope = next; repository.bindAccess(PluginAccessBinding(next, connection, ConnectionTransport.Local)); controller.bind(next)
    }
    fun detail() = assertIs<PluginLoadState.Success<InstalledPluginDetail>>(state.detail).value
    fun locked(block: () -> Unit) {
        val lockFile = file.resolveSibling(file.fileName.toString() + ".lock")
        FileChannel.open(lockFile, WRITE).use { channel -> channel.lock().use { block() } }
    }
    override fun close() {
        describeGate?.complete(Unit); repository.bindAccess(null); controller.close(); jobs.cancel(); scheduler.runCurrent()
        directory.toFile().deleteRecursively()
    }
}

/** Injects only explicit read failures; successful updates and all other reads use the real adapter. */
private class LocalUpdateReadFaults(private val delegate: LocalPluginManagementRepository) : LocalPluginManagementRepository by delegate {
    var failAfterUpdate = false
    var failReads = false
    override suspend fun updateLocal(scope: PluginScope, id: PluginId, expectedRevision: String): PluginResult<PluginMutationReceipt> {
        val result = delegate.updateLocal(scope, id, expectedRevision)
        if (result is PluginResult.Success && failAfterUpdate) failReads = true
        return result
    }
    override suspend fun listInstalled(scope: PluginScope): PluginResult<List<InstalledPlugin>> =
        if (failReads) PluginResult.Error(PluginRepositoryError("Explicit post-update read failure")) else delegate.listInstalled(scope)
    override suspend fun readDetail(scope: PluginScope, id: PluginId): PluginResult<InstalledPluginDetail> =
        if (failReads) PluginResult.Error(PluginRepositoryError("Explicit post-update read failure")) else delegate.readDetail(scope, id)
}

/** Deliberately inherits the optional update defaults while every existing operation is real. */
private class LegacyUpdateAdapter(private val delegate: LocalPluginManagementRepository) : LocalPluginManagementRepository {
    override val canRead get() = delegate.canRead
    override val canManage get() = delegate.canManage
    override fun bindAccess(binding: PluginAccessBinding?) = delegate.bindAccess(binding)
    override suspend fun listInstalled(scope: PluginScope) = delegate.listInstalled(scope)
    override suspend fun readDetail(scope: PluginScope, id: PluginId) = delegate.readDetail(scope, id)
    override suspend fun readRevision(scope: PluginScope) = delegate.readRevision(scope)
    override suspend fun registerLocal(scope: PluginScope, directory: String, expectedRevision: String) = delegate.registerLocal(scope, directory, expectedRevision)
    override suspend fun setEnabled(scope: PluginScope, id: PluginId, enabled: Boolean, expectedRevision: String) = delegate.setEnabled(scope, id, enabled, expectedRevision)
    override suspend fun removeRegistration(scope: PluginScope, id: PluginId, expectedRevision: String) = delegate.removeRegistration(scope, id, expectedRevision)
}
