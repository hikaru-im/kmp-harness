@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE") // Exercise the production picker without widening its API.

package im.hikaru.harness.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import im.hikaru.harness.client.scheduling.*
import im.hikaru.harness.ui.foundation.*
import java.io.File
import java.security.MessageDigest
import javax.imageio.ImageIO
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import kotlin.test.*

/** Real production picker/page and repository; all normal selection uses measured pointer/key input. */
@OptIn(ExperimentalTestApi::class)
class ScheduledPickerMigrationInteractionTest {
    @Test fun popup600Light() = pickerMatrix(600, 800, HarnessThemeMode.Light)
    @Test fun popup600Dark() = pickerMatrix(600, 800, HarnessThemeMode.Dark)
    @Test fun popup1440Light() = pickerMatrix(1440, 900, HarnessThemeMode.Light)
    @Test fun popup1440Dark() = pickerMatrix(1440, 900, HarnessThemeMode.Dark)

    private fun pickerMatrix(width: Int, height: Int, theme: HarnessThemeMode) {
        ScheduledPageTestSupport(includeRuns = false).use { fixture ->
            val duplicates = listOf(ScheduledModelRef("first-provider", "first-model"),
                ScheduledModelRef("second-provider", "second-model"))
            val last = ScheduledModelRef("last-provider", "/workspace/" + "目录 path segment/".repeat(18) + "END")
            val options = duplicates + (1..24).map { ScheduledModelRef("provider", "model-$it") } + last
            fixture.repository.bind(fixture.scope, fixture.context.copy(models = options))
            runBlocking { fixture.repository.load() }
            fixture.repository.beginCreate()
            assertNotNull(fixture.repository.state.value.draft)
            val duplicateLabel = "Duplicate model / " + "complete wrapped label 工作区 / ".repeat(3)
            fun label(model: ScheduledModelRef) = if (model in duplicates) duplicateLabel else model.providerId + "/" + model.modelId
            runSkikoComposeUiTest(size = Size(width.toFloat(), height.toFloat())) {
                var selected by mutableStateOf(duplicates.first())
                val fence = Any()
                val calls = mutableListOf<ScheduledModelRef>()
                var popupContent: SemanticsNode? = null
                setContent { CompositionLocalProvider(LocalDensity provides Density(1f)) { HarnessTheme(theme) {
                    Box(Modifier.fillMaxSize().padding(24.dp)) {
                        ScheduledPagePicker(selected, options, ::label, true, "scheduled-migration", fence) { model ->
                            assertFalse(assertNotNull(popupContent).layoutInfo.isAttached,
                                "Business callback must observe the actual popup content detached")
                            calls += model
                            selected = model
                            fixture.repository.updateDraft { it.copy(model = model) }
                        }
                    }
                } } }
                pointer("scheduled-migration")
                popupContent = onNodeWithTag("scheduled-migration-menu", true).fetchSemanticsNode()
                assertTrue(assertNotNull(popupContent).layoutInfo.isAttached)
                capturePopup("scheduled-${width}x$height-${theme.name.lowercase()}", width, height, theme)
                onNodeWithTag("scheduled-migration-option:0").assertIsSelected()
                for ((index, model) in duplicates.withIndex()) {
                    val tag = "scheduled-migration-option:$index"
                    onNodeWithTag(tag).assertTextContains(duplicateLabel)
                    assertWrappedLabel(tag, duplicateLabel)
                    pointer(tag)
                    waitUntil(timeoutMillis = 10_000) { calls.size == index + 1 }
                    assertEquals(model, fixture.repository.state.value.draft?.model)
                    onNodeWithTag("scheduled-migration-menu").assertDoesNotExist()
                    pointer("scheduled-migration")
                    popupContent = onNodeWithTag("scheduled-migration-menu", true).fetchSemanticsNode()
                    onNodeWithTag(tag).assertIsSelected()
                }
                wheelToEnd("scheduled-migration-menu")
                val lastTag = "scheduled-migration-option:${options.lastIndex}"
                assertWrappedLabel(lastTag, label(last))
                pointer(lastTag)
                waitUntil(timeoutMillis = 10_000) { fixture.repository.state.value.draft?.model == last }
                assertEquals(duplicates + last, calls)
                onNodeWithTag("scheduled-migration-value").assertTextEquals(label(last))
                onNodeWithTag("scheduled-migration-value-copy").assertIsEnabled()
                wheelToEnd("scheduled-migration-value", horizontal = true)
                assertEquals(0, fixture.store.writes.get())
                assertEquals(0, fixture.connection.executionCalls.get())
            }
        }
    }

    @Test fun fourEditorPickersUpdateTheRealDraft() {
        ScheduledPageTestSupport(includeRuns = false).use { fixture ->
            fixture.beginEdit()
            runSkikoComposeUiTest(size = Size(1440f, 900f)) {
                setContent { ScheduledPageTestSurface(fixture.repository) }
                fun choose(tag: String, index: Int) {
                    reveal("scheduled-content-scroll", tag)
                    pointer(tag)
                    pointer("$tag-option:$index")
                    onNodeWithTag("$tag-menu").assertDoesNotExist()
                }
                choose("scheduled-target", 1)
                waitUntil(timeoutMillis = 10_000) { fixture.repository.state.value.draft?.target == fixture.context.targets[1] }
                assertNull(fixture.repository.state.value.draft?.model)
                choose("scheduled-target", 0)
                waitUntil(timeoutMillis = 10_000) { fixture.repository.state.value.draft?.target == fixture.target }
                choose("scheduled-model", 1)
                waitUntil(timeoutMillis = 10_000) { fixture.repository.state.value.draft?.model == fixture.context.models[1] }
                choose("scheduled-frequency", ScheduledFrequency.Weekly.ordinal)
                waitUntil(timeoutMillis = 10_000) { fixture.repository.state.value.draft?.schedule?.frequency == ScheduledFrequency.Weekly }
                choose("scheduled-weekday", ScheduledWeekday.Sunday.ordinal)
                waitUntil(timeoutMillis = 10_000) { fixture.repository.state.value.draft?.schedule?.day == ScheduledWeekday.Sunday }
                assertEquals(0, fixture.store.writes.get())
                assertEquals(0, fixture.connection.executionCalls.get())
                assertFalse(fixture.repository.state.value.canActivate)
                assertFalse(fixture.repository.state.value.canRunNow)
            }
        }
    }

    @Test fun queuedSelectionRejectsDisabled() = invalidatedSelection(Invalidation.Disabled)
    @Test fun queuedSelectionRejectsRemovedValue() = invalidatedSelection(Invalidation.Removed)
    @Test fun queuedSelectionRejectsReorderedValues() = invalidatedSelection(Invalidation.Reordered)
    @Test fun queuedSelectionRejectsChangedFence() = invalidatedSelection(Invalidation.Fence)
    @Test fun queuedSelectionRejectsDisposedOwner() = invalidatedSelection(Invalidation.Disposed)
    @Test fun queuedSelectionRejectsChangedSelectedValue() = invalidatedSelection(Invalidation.Value)

    private enum class Invalidation { Disabled, Removed, Reordered, Fence, Disposed, Value }

    private fun invalidatedSelection(invalidation: Invalidation) {
        ScheduledPageTestSupport(includeRuns = false).use { fixture ->
            fixture.beginEdit()
            val before = fixture.repository.state.value.draft
            runSkikoComposeUiTest(size = Size(600f, 800f)) {
                var options by mutableStateOf(fixture.context.models)
                var selected by mutableStateOf(fixture.model)
                var enabled by mutableStateOf(true)
                var present by mutableStateOf(true)
                var fence by mutableStateOf(Any())
                val calls = mutableListOf<ScheduledModelRef>()
                setContent { HarnessTheme { if (present) {
                    ScheduledPagePicker(selected, options, { "Duplicate label" }, enabled, "scheduled-migration", fence) { model ->
                        calls += model
                        fixture.repository.updateDraft { it.copy(model = model) }
                    }
                } } }
                pointer("scheduled-migration")
                val popup = onNodeWithTag("scheduled-migration-menu", true).fetchSemanticsNode()
                val retained = assertNotNull(onNodeWithTag("scheduled-migration-option:1")
                    .fetchSemanticsNode().config[SemanticsActions.OnClick].action)
                queuedPointer("scheduled-migration-option:1") {
                    assertTrue(popup.layoutInfo.isAttached, "Queue must still own real popup content")
                    assertTrue(calls.isEmpty(), "Queue must precede business dispatch")
                    when (invalidation) {
                        Invalidation.Disabled -> enabled = false
                        Invalidation.Removed -> options = options.take(1)
                        Invalidation.Reordered -> options = options.reversed()
                        Invalidation.Fence -> fence = Any()
                        Invalidation.Disposed -> present = false
                        Invalidation.Value -> selected = options[1]
                    }
                }
                waitUntil(timeoutMillis = 10_000) { !popup.layoutInfo.isAttached }
                onNodeWithTag("scheduled-migration-menu").assertDoesNotExist()
                runOnIdle { retained() } // Only deliberate stale-action injection uses the retained semantic callback.
                settleGitLayout()
                assertTrue(calls.isEmpty(), "Queued and retained selection must be rejected: $invalidation")
                assertEquals(before, fixture.repository.state.value.draft)
                assertEquals(0, fixture.store.writes.get())
            }
        }
    }

    @Test fun retainedIndexCannotSelectAReorderedDuplicateLabel() {
        ScheduledPageTestSupport(includeRuns = false).use { fixture ->
            fixture.beginEdit()
            runSkikoComposeUiTest(size = Size(600f, 800f)) {
                var options by mutableStateOf(fixture.context.models)
                val fence = Any()
                val calls = mutableListOf<ScheduledModelRef>()
                setContent { HarnessTheme { ScheduledPagePicker(fixture.model, options, { "Same label" }, true,
                    "scheduled-migration", fence) { model -> calls += model; fixture.repository.updateDraft { it.copy(model = model) } } } }
                pointer("scheduled-migration")
                val retained = assertNotNull(onNodeWithTag("scheduled-migration-option:1")
                    .fetchSemanticsNode().config[SemanticsActions.OnClick].action)
                runOnIdle { options = options.reversed() }
                settleGitLayout()
                onNodeWithTag("scheduled-migration-menu").assertDoesNotExist()
                pointer("scheduled-migration")
                runOnIdle { retained() }
                settleGitLayout()
                assertTrue(calls.isEmpty())
                assertEquals(fixture.model, fixture.repository.state.value.draft?.model)
                pointer("scheduled-migration-option:0")
                waitUntil(timeoutMillis = 10_000) { calls.size == 1 }
                assertEquals(listOf(fixture.context.models[1]), calls)
                assertEquals(fixture.context.models[1], fixture.repository.state.value.draft?.model)
            }
        }
    }

    @Test fun equalOrderedValuesDispatchTheLatestCallbackAfterDisposal() {
        ScheduledPageTestSupport(includeRuns = false).use { fixture ->
            fixture.beginEdit()
            runSkikoComposeUiTest(size = Size(600f, 800f)) {
                var version by mutableStateOf(0)
                val fence = Any()
                val calls = mutableListOf<Pair<Int, ScheduledModelRef>>()
                var popup: SemanticsNode? = null
                setContent { HarnessTheme {
                    val captured = version
                    val options = fixture.context.models.map { it.copy() }
                    ScheduledPagePicker(fixture.model, options, { it.modelId }, true, "scheduled-migration", fence) { model ->
                        assertFalse(assertNotNull(popup).layoutInfo.isAttached)
                        calls += captured to model
                        fixture.repository.updateDraft { it.copy(model = model) }
                    }
                } }
                pointer("scheduled-migration")
                popup = onNodeWithTag("scheduled-migration-menu", true).fetchSemanticsNode()
                queuedPointer("scheduled-migration-option:1") {
                    assertTrue(assertNotNull(popup).layoutInfo.isAttached)
                    assertTrue(calls.isEmpty())
                    version = 1
                }
                waitUntil(timeoutMillis = 10_000) { calls.size == 1 }
                assertEquals(listOf(1 to fixture.context.models[1]), calls)
                assertEquals(fixture.context.models[1], fixture.repository.state.value.draft?.model)
            }
        }
    }

    @Test fun keyboardNavigationAndOutsideDismissalPreserveSelection() = runSkikoComposeUiTest(size = Size(600f, 800f)) {
        var selected by mutableStateOf("a")
        val calls = mutableListOf<String>()
        val fence = Any()
        setContent { HarnessTheme { Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            ScheduledPagePicker(selected, listOf("a", "b", "c"), { it }, true, "scheduled-migration", fence) { selected = it; calls += it }
            HarnessButton("Outside", {}, testTag = "scheduled-outside")
        } } }
        pointer("scheduled-migration")
        onNodeWithTag("scheduled-migration-option:0").assertIsSelected()
        press(0, Key.DirectionDown)
        press(1, Key.DirectionUp)
        press(0, Key.MoveEnd)
        press(2, Key.MoveHome)
        press(0, Key.Enter)
        waitUntil(timeoutMillis = 10_000) { calls == listOf("a") }
        pointer("scheduled-migration")
        press(0, Key.MoveEnd)
        press(2, Key.Spacebar)
        waitUntil(timeoutMillis = 10_000) { calls == listOf("a", "c") }
        for (dismiss in listOf(Key.Escape, Key.Tab)) {
            pointer("scheduled-migration")
            onNodeWithTag("scheduled-migration-option:2").assertIsSelected()
            // A changed value creates a fresh picker lifetime, whose first enabled item gets focus.
            press(0, dismiss)
            onNodeWithTag("scheduled-migration-menu").assertDoesNotExist()
        }
        pointer("scheduled-migration")
        val popup = onNodeWithTag("scheduled-migration-menu", true).fetchSemanticsNode().boundsInWindow
        val outside = onNodeWithTag("scheduled-outside").fetchSemanticsNode().boundsInWindow.center
        assertFalse(popup.contains(outside))
        pointer("scheduled-outside")
        onNodeWithTag("scheduled-migration-menu").assertDoesNotExist()
        assertEquals(listOf("a", "c"), calls)
        assertEquals("c", selected)
    }

    private fun ComposeUiTest.pointer(tag: String) {
        val started = System.nanoTime()
        clickGitNode(onNodeWithTag(tag))
        assertTrue(System.nanoTime() - started < 10_000_000_000L, "Pointer deadline: $tag")
    }

    /** Pause the frame clock only between actual mouse release and the owner applying its closed frame. */
    private fun ComposeUiTest.queuedPointer(tag: String, beforeClosedFrame: () -> Unit) {
        val started = System.nanoTime()
        settleGitLayout()
        val target = onNodeWithTag(tag).assertIsDisplayed()
        val first = target.fetchSemanticsNode()
        val root = generateSequence(first) { it.parent }.last()
        val input = onNode(SemanticsMatcher("Queued pointer root ${root.id}") { it.id == root.id }, true)
        input.performMouseInput { moveTo(first.boundsInRoot.center - root.boundsInRoot.topLeft) }
        settleGitLayout()
        val node = target.assertIsDisplayed().fetchSemanticsNode()
        assertEquals(root.id, generateSequence(node) { it.parent }.last().id)
        assertTrue(node.boundsInRoot.width > 0f && node.boundsInRoot.height > 0f)
        val point = node.boundsInRoot.center - root.boundsInRoot.topLeft
        mainClock.autoAdvance = false
        try {
            input.performMouseInput { moveTo(point); advanceEventTime(16); click(point) }
            beforeClosedFrame()
        } finally { mainClock.autoAdvance = true }
        settleGitLayout()
        assertTrue(System.nanoTime() - started < 10_000_000_000L, "Queued pointer deadline: $tag")
    }

    private fun ComposeUiTest.press(index: Int, key: Key) {
        val started = System.nanoTime()
        onNodeWithTag("scheduled-migration-option:$index").assertIsFocused().performKeyInput { keyDown(key); keyUp(key) }
        settleGitLayout()
        assertTrue(System.nanoTime() - started < 10_000_000_000L, "Keyboard deadline: $key")
    }

    private fun ComposeUiTest.reveal(listTag: String, tag: String) {
        val started = System.nanoTime()
        pluginsPageWheelTo(listTag, tag)
        assertTrue(System.nanoTime() - started < 10_000_000_000L, "Wheel reveal deadline: $tag")
    }

    private fun ComposeUiTest.wheelToEnd(tag: String, horizontal: Boolean = false) {
        val started = System.nanoTime()
        pluginsPageScrollToEnd(tag, horizontal)
        assertTrue(System.nanoTime() - started < 10_000_000_000L, "Wheel deadline: $tag")
    }

    private fun ComposeUiTest.assertWrappedLabel(tag: String, text: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        onNode(hasText(text) and hasAnyAncestor(hasTestTag(tag)), true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.any { it.layoutInput.text.text == text && it.lineCount > 1 && !it.hasVisualOverflow &&
            it.getLineEnd(it.lineCount - 1) == text.length && (0 until it.lineCount).none { line -> it.isLineEllipsized(line) } },
            "Complete label must wrap without clipping or ellipsis: $tag")
    }

    private fun ComposeUiTest.capturePopup(name: String, width: Int, height: Int, theme: HarnessThemeMode) {
        settleGitLayout()
        val menu = onNodeWithTag("scheduled-migration-menu", true).assertIsDisplayed().fetchSemanticsNode()
        val root = generateSequence(menu) { it.parent }.last()
        val anchor = onNodeWithTag("scheduled-migration").fetchSemanticsNode()
        val anchorRoot = generateSequence(anchor) { it.parent }.last()
        assertTrue(root.id != anchorRoot.id, "Capture the real independent popup root")
        val bounds = menu.boundsInWindow
        assertTrue(bounds.left >= 8f && bounds.top >= 8f && bounds.right <= width - 8f && bounds.bottom <= height - 8f)
        assertTrue(bounds.width <= minOf(anchor.size.width.toFloat(), 520f) && bounds.height <= 280f)
        val bitmap = onNode(SemanticsMatcher("Actual scheduled popup root ${root.id}") { it.id == root.id }, true).captureToImage()
        val bytes = Image.makeFromBitmap(bitmap.asSkiaBitmap()).use { image ->
            image.encodeToData(EncodedImageFormat.PNG)?.use { it.bytes } ?: error("PNG encoding failed")
        }
        val directory = File(System.getenv("HARNESS_SCHEDULED_PICKER_CAPTURE_DIR")?.takeIf { it.isNotBlank() }
            ?: "/tmp/harness-scheduled-picker-captures-20261008")
        assertTrue(directory.isDirectory || directory.mkdirs())
        val png = File(directory, "$name.png").also { it.writeBytes(bytes) }
        val decoded = assertNotNull(ImageIO.read(png))
        assertEquals(root.size.width, decoded.width)
        assertEquals(root.size.height, decoded.height)
        val geometry = buildJsonObject {
            put("schema", "harness.scheduled-picker.popup.v1"); put("image", png.name)
            put("windowWidth", width); put("windowHeight", height); put("theme", theme.name); put("density", 1f)
            put("captureRootId", root.id); put("anchorRootId", anchorRoot.id)
            put("captureWidth", decoded.width); put("captureHeight", decoded.height)
            put("rootBoundsInWindow", root.boundsInWindow.toString()); put("menuBoundsInWindow", bounds.toString())
            put("sha256", MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
            putJsonArray("nodes") {
                onAllNodes(SemanticsMatcher("Popup tags") { it.config.contains(SemanticsProperties.TestTag) }, true)
                    .fetchSemanticsNodes().filter { generateSequence(it) { node -> node.parent }.last().id == root.id }.forEach { node ->
                        add(buildJsonObject {
                            put("tag", node.config[SemanticsProperties.TestTag]); put("id", node.id)
                            put("boundsInRoot", node.boundsInRoot.toString()); put("boundsInWindow", node.boundsInWindow.toString())
                            put("layoutWidth", node.size.width); put("layoutHeight", node.size.height)
                        })
                    }
            }
        }
        File(directory, "$name.geometry.json").writeText(Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), geometry))
    }
}
