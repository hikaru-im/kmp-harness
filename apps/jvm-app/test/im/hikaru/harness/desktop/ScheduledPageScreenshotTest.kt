@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class, androidx.compose.ui.test.ExperimentalTestApi::class)

package im.hikaru.harness.desktop

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import im.hikaru.harness.client.scheduling.*
import im.hikaru.harness.ui.foundation.HarnessThemeMode
import java.io.File
import java.security.MessageDigest
import javax.imageio.ImageIO
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import kotlin.test.*

/** Captures the actual tested composition; every PNG has a geometry/state/hash sidecar. */
class ScheduledPageScreenshotTest {
    @Test fun captureLandingAndReadAvailability() = capture(listOf(Case.Landing, Case.Empty, Case.Loading,
        Case.Error, Case.Disconnected, Case.Unbound))

    @Test fun captureDetailEditorSavingAndRunEvidence() = capture(listOf(Case.Detail, Case.Editor,
        Case.Saving, Case.Unknown, Case.Confirmed, Case.SaveFailure, Case.Durability, Case.MissingCatalog))

    @Test fun captureLongValuesBoundedMenusAndIndependentNavigatorList() =
        capture(listOf(Case.LongFields, Case.ManyTasks, Case.TargetMenu, Case.ModelMenu))

    private fun capture(cases: List<Case>) {
        val directory = File(System.getenv("HARNESS_SCHEDULED_PAGE_CAPTURE_DIR")?.takeIf { it.isNotBlank() }
            ?: "/tmp/harness-scheduled-page-captures")
        assertTrue(directory.isDirectory || directory.mkdirs())
        for (case in cases) for ((width, height) in listOf(1440 to 900, 600 to 800))
            for ((themeName, theme) in listOf("light" to HarnessThemeMode.Light, "dark" to HarnessThemeMode.Dark)) {
                ScheduledPageTestSupport(taskCount = when (case) { Case.Empty -> 0; Case.ManyTasks -> 24; else -> 2 },
                    longFields = case in listOf(Case.LongFields, Case.TargetMenu, Case.ModelMenu),
                    includeRuns = case !in listOf(Case.Detail, Case.Editor, Case.Saving, Case.LongFields,
                        Case.TargetMenu, Case.ModelMenu, Case.SaveFailure, Case.Durability, Case.MissingCatalog),
                    loading = case == Case.Loading, readError = case == Case.Error).use { f ->
                    configure(case, f)
                    runSkikoComposeUiTest(size = Size(width.toFloat(), height.toFloat())) {
                        setContent { ScheduledPageTestSurface(f.repository, theme, onOpenRun = {}) }
                        val prefix = case.name.lowercase() + "-" + themeName + "-" + width + "x" + height
                        fun save(suffix: String, captureTag: String = "scheduled-capture-root", scrollTarget: String = "initial") {
                            val node = onNodeWithTag(captureTag).assertIsDisplayed()
                            val bitmap = node.captureToImage()
                            val file = File(directory, prefix + "-" + suffix + ".png")
                            if (captureTag == "scheduled-capture-root") {
                                assertEquals(width, bitmap.width); assertEquals(height, bitmap.height)
                            } else {
                                assertTrue(bitmap.width in 1..width)
                                assertTrue(bitmap.height in 1..281)
                            }
                            Image.makeFromBitmap(bitmap.asSkiaBitmap()).use { image ->
                                requireNotNull(image.encodeToData(EncodedImageFormat.PNG)).use { file.writeBytes(it.bytes) }
                            }
                            val decoded = assertNotNull(ImageIO.read(file))
                            assertEquals(bitmap.width, decoded.width); assertEquals(bitmap.height, decoded.height)
                            writeGeometry(file, case.name, themeName, width, height, bitmap.width, bitmap.height,
                                captureTag, scrollTarget, f.repository.state.value)
                        }
                        save("viewport")
                        when (case) {
                            Case.Landing, Case.Empty -> {
                                onNodeWithTag("scheduled-landing-clock").assertExists()
                                if (width == 600) {
                                    onNodeWithTag("scheduled-template:review").performScrollTo().assertIsDisplayed()
                                    save("templates-bottom", scrollTarget = "scheduled-template:review")
                                }
                            }
                            Case.Loading, Case.Error -> {
                                onNodeWithTag("scheduled-empty").assertDoesNotExist()
                                onNodeWithTag(if (case == Case.Error) "scheduled-page-retry" else "scheduled-loading").assertExists()
                            }
                            Case.Disconnected, Case.Unbound -> {
                                onNodeWithTag("scheduled-detail").assertDoesNotExist()
                                onNodeWithTag("scheduled-page-run:confirmed").assertDoesNotExist()
                                onNodeWithTag("scheduled-new-task").assertIsNotEnabled()
                            }
                            Case.Detail, Case.Unknown, Case.Confirmed -> {
                                onNodeWithTag("scheduled-run-now").performScrollTo().assertIsNotEnabled()
                                val tag = if (case == Case.Detail) "scheduled-page-history-empty" else "scheduled-page-open-run:confirmed"
                                onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
                                save("history", scrollTarget = tag)
                                onNodeWithTag("scheduled-execution-model").assertExists()
                                if (case != Case.Detail) {
                                    onNodeWithTag("scheduled-run-blocked").performScrollTo().assertIsDisplayed()
                                    onNodeWithTag("scheduled-blocked-runs").performScrollTo().assertIsDisplayed()
                                }
                            }
                            Case.Editor, Case.Saving, Case.LongFields, Case.SaveFailure, Case.MissingCatalog -> {
                                onNodeWithTag("scheduled-save").performScrollTo().assertIsDisplayed()
                                save("editor-actions", scrollTarget = "scheduled-save")
                                if (case == Case.Saving || case == Case.MissingCatalog) onNodeWithTag("scheduled-save").assertIsNotEnabled()
                                if (case == Case.LongFields) {
                                    for (tag in listOf("scheduled-target-value", "scheduled-model-value")) {
                                        onNodeWithTag(tag).performScrollTo().assertIsDisplayed()
                                        save(tag + "-start", scrollTarget = tag)
                                        val value = onNodeWithTag(tag)
                                        value.performSemanticsAction(SemanticsActions.ScrollBy) { it(800f, 0f) }
                                        val range = value.fetchSemanticsNode().config[SemanticsProperties.HorizontalScrollAxisRange]
                                        assertTrue(range.value() > 0f)
                                        save(tag + "-scrolled", scrollTarget = tag + ":horizontal")
                                    }
                                }
                            }
                            Case.ManyTasks -> {
                                val header = onNodeWithTag("scheduled-navigator-header").fetchSemanticsNode().boundsInRoot
                                onNodeWithTag("scheduled-task:task-24").performScrollTo().assertIsDisplayed()
                                assertEquals(header, onNodeWithTag("scheduled-navigator-header").fetchSemanticsNode().boundsInRoot)
                                val scroll = onNodeWithTag("scheduled-navigator-list").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
                                assertTrue(scroll.value() > 0f)
                                save("list-bottom", scrollTarget = "scheduled-task:task-24")
                            }
                            Case.TargetMenu, Case.ModelMenu -> {
                                val tag = if (case == Case.TargetMenu) "scheduled-target" else "scheduled-model"
                                onNodeWithTag(tag).scheduledMouseClick()
                                val bounds = onNodeWithTag(tag + "-menu").fetchSemanticsNode().boundsInRoot
                                assertTrue(bounds.width <= width && bounds.height <= 281f)
                                save("menu-anchor", scrollTarget = tag)
                                save("menu-popup", tag + "-menu", tag)
                            }
                            Case.Durability -> onNodeWithTag("scheduled-durability-warning").assertExists()
                        }
                    }
                }
            }
    }

    private fun configure(case: Case, f: ScheduledPageTestSupport) {
        when (case) {
            Case.Disconnected -> { f.selectFirst(); f.repository.bind(f.scope, f.context, connected = false) }
            Case.Unbound -> f.repository.clear()
            Case.Detail, Case.Unknown, Case.Confirmed -> f.selectFirst()
            Case.Editor, Case.LongFields, Case.TargetMenu, Case.ModelMenu -> f.beginEdit()
            Case.Saving -> f.startSaving()
            Case.SaveFailure -> {
                f.beginEdit(); f.repository.updateDraft { it.copy(prompt = "Unsaved draft remains after failure.") }
                f.store.failWrite = true
                runBlocking { assertFalse(f.repository.save()) }
            }
            Case.Durability -> {
                f.beginEdit(); f.store.warning = "配置已提交，但目录同步未确认。"
                runBlocking { assertTrue(f.repository.save()) }
            }
            Case.MissingCatalog -> {
                f.repository.bind(f.scope, ScheduledValidationContext(emptyList(), emptyList()))
                runBlocking { f.repository.load() }; f.beginEdit()
            }
            else -> Unit
        }
        if (case == Case.Confirmed) {
            f.store.snapshot = f.store.snapshot.copy(runs = f.runs.filter { it.id == "confirmed" }.map {
                it.copy(phase = ScheduledRunPhase.Accepted, receiptId = "screenshot-test-receipt-confirmed",
                    detail = "会话已确认；尚无终态证据。")
            })
            assertNull(ScheduledValidator.snapshotError(f.store.snapshot), "Confirmed screenshot fixture must be valid")
            runBlocking { f.repository.load() }
            assertEquals(ScheduledLoadPhase.Ready, f.repository.state.value.phase,
                "Confirmed screenshot fixture failed to load: ${f.repository.state.value.loadError}")
            assertEquals(f.tasks.first().id, f.repository.state.value.selectedTaskId,
                "Confirmed screenshot fixture must retain the selected task")
        }
    }

    private enum class Case { Landing, Empty, Loading, Error, Disconnected, Unbound, Detail, Editor,
        Saving, Unknown, Confirmed, SaveFailure, Durability, MissingCatalog, LongFields, ManyTasks, TargetMenu, ModelMenu }
}

@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.writeGeometry(png: File, stateName: String, theme: String, width: Int, height: Int,
    bitmapWidth: Int, bitmapHeight: Int, captureTag: String, scrollTarget: String, state: ScheduledManagementState) {
    val capture = onNodeWithTag(captureTag).fetchSemanticsNode()
    val root = capture.captureRootId()
    val viewport = capture.boundsInRoot
    val nodes = onAllNodes(SemanticsMatcher("actual semantic nodes") { true }, useUnmergedTree = true).fetchSemanticsNodes()
    val manifest = buildJsonObject {
        put("schema", "harness.scheduled-page.geometry.v1")
        put("state", stateName); put("theme", theme); put("width", width); put("height", height)
        put("bitmapWidth", bitmapWidth); put("bitmapHeight", bitmapHeight); put("density", density.density)
        put("image", png.name); put("captureTag", captureTag); put("captureRootId", root)
        put("captureBounds", viewport.geometryJson()); put("scrollTarget", scrollTarget)
        put("sha256", MessageDigest.getInstance("SHA-256").digest(png.readBytes()).joinToString("") { "%02x".format(it) })
        put("repository", buildJsonObject {
            put("scope", state.scope?.toString()?.let(::JsonPrimitive) ?: JsonNull)
            put("connected", state.connected); put("phase", state.phase.name)
            put("selectedTaskId", state.selectedTaskId?.let(::JsonPrimitive) ?: JsonNull)
            put("draftId", state.draft?.taskId?.let(::JsonPrimitive) ?: JsonNull)
            put("draftRevision", state.draft?.draftRevision?.let(::JsonPrimitive) ?: JsonNull)
            put("mutation", state.mutation?.name?.let(::JsonPrimitive) ?: JsonNull)
            put("taskCount", state.tasks.size); put("runCount", state.effective?.runs?.size ?: 0)
            put("loadError", state.loadError?.let(::JsonPrimitive) ?: JsonNull)
            put("mutationError", state.mutationError?.let(::JsonPrimitive) ?: JsonNull)
        })
        put("scrollContainers", buildJsonArray {
            nodes.forEach { node ->
                val vertical = node.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)
                val horizontal = node.config.getOrNull(SemanticsProperties.HorizontalScrollAxisRange)
                if (vertical != null || horizontal != null) add(buildJsonObject {
                    put("nodeId", node.id); put("rootId", node.captureRootId())
                    put("tag", node.config.getOrNull(SemanticsProperties.TestTag)?.let(::JsonPrimitive) ?: JsonNull)
                    put("bounds", node.boundsInRoot.geometryJson())
                    vertical?.let { put("vertical", buildJsonObject { put("value", it.value()); put("max", it.maxValue()) }) }
                    horizontal?.let { put("horizontal", buildJsonObject { put("value", it.value()); put("max", it.maxValue()) }) }
                })
            }
        })
        put("visibleKeyTags", buildJsonArray {
            nodes.forEach { node ->
                val tag = node.config.getOrNull(SemanticsProperties.TestTag) ?: return@forEach
                val bounds = node.boundsInRoot
                if (node.captureRootId() == root && bounds.width > 0f && bounds.height > 0f &&
                    bounds.left < viewport.right && bounds.right > viewport.left && bounds.top < viewport.bottom && bounds.bottom > viewport.top) {
                    add(buildJsonObject {
                        put("tag", tag); put("nodeId", node.id); put("bounds", bounds.geometryJson())
                        put("layoutBounds", Rect(node.positionInRoot.x, node.positionInRoot.y,
                            node.positionInRoot.x + node.size.width, node.positionInRoot.y + node.size.height).geometryJson())
                        put("enabled", !node.config.contains(SemanticsProperties.Disabled))
                    })
                }
            }
        })
    }
    assertTrue(nodes.any { it.captureRootId() == root && it.config.contains(SemanticsProperties.TestTag) })
    File(png.parentFile, png.nameWithoutExtension + ".geometry.json").writeText(manifest.toString() + "\n")
}

private fun SemanticsNode.captureRootId(): Int = generateSequence(this) { it.parent }.last().id
private fun Rect.geometryJson(): JsonObject = buildJsonObject {
    put("left", left); put("top", top); put("right", right); put("bottom", bottom)
    put("width", width); put("height", height)
}
