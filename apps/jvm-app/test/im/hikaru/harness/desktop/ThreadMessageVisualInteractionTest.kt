@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package im.hikaru.harness.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
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
import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.harness.client.account.BackendTenant
import im.hikaru.harness.client.account.MemberIdentity
import im.hikaru.harness.client.app.LiveThreadSurface
import im.hikaru.harness.client.connection.*
import im.hikaru.harness.client.conversation.*
import im.hikaru.harness.ui.foundation.HarnessTheme
import im.hikaru.harness.ui.foundation.HarnessThemeMode
import im.hikaru.harness.ui.foundation.HarnessTokens
import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.json.*
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.*

/** Typed fixtures exercise the production timeline. No controller, transport or model is contacted. */
class ThreadMessageVisualInteractionTest {
    private val textGeometry = linkedMapOf<String, JsonObject>()

    @Test fun threadMessages600Light() = captureScenario(600, 800, HarnessThemeMode.Light)
    @Test fun threadMessages600Dark() = captureScenario(600, 800, HarnessThemeMode.Dark)
    @Test fun threadMessages1440Light() = captureScenario(1440, 900, HarnessThemeMode.Light)
    @Test fun threadMessages1440Dark() = captureScenario(1440, 900, HarnessThemeMode.Dark)

    @Test
    fun typedSourcesNeverTurnToolPluginOrOddRoleMessagesIntoHumanBubbles() =
        runSkikoComposeUiTest(size = Size(600f, 800f)) {
            val messages = listOf(
                Message("human", MessageRole.USER, listOf(TextContent("Human input")), UserMessageSource),
                Message("tool-user", MessageRole.USER, listOf(TextContent("Raw tool content")), ToolMessageSource("call")),
                Message("plugin-user", MessageRole.USER, listOf(TextContent("Raw plugin content")), PluginMessageSource("fixture-plugin")),
                Message("model-user", MessageRole.USER, listOf(TextContent("Model with USER role")), MODEL),
                Message("user-assistant", MessageRole.ASSISTANT, listOf(TextContent("User source with ASSISTANT role")), UserMessageSource),
                Message("model-assistant", MessageRole.ASSISTANT, listOf(TextContent("Assistant body")), MODEL),
            )
            mount({ ready(messages) })
            messages.forEach { message ->
                textIsComplete("message:${message.id}:content:0", (message.content.single() as TextContent).text)
            }
            // Rows are full-width frames, so bubble geometry is not directly measurable. The human path
            // shows through its directly tagged Text; every other source goes through the markdown wrapper.
            assertTrue(node("message:human:content:0").config.contains(SemanticsProperties.Text))
            messages.drop(1).forEach { message ->
                assertFalse(node("message:${message.id}:content:0").config.contains(SemanticsProperties.Text))
                val neutral = layout("message:${message.id}")
                assertNear(layout("message:model-assistant").left, neutral.left, "Neutral source left edge")
                assertNear(layout("message:model-assistant").width, neutral.width, "Neutral source full content width")
            }
            onNodeWithText("You").assertDoesNotExist()
            onNodeWithText("Harness").assertDoesNotExist()
        }

    @Test
    fun realHeaderMouseTogglesWhileExpandedBodyClicksLeaveDisclosureOpen() =
        runSkikoComposeUiTest(size = Size(600f, 800f)) {
            mount({ ready(disclosureHistory()) })
            assertDisclosure(REASONING, false, REASONING_RENDERED)
            assertDisclosure(CALL, false, ARGUMENTS)
            pointer("$REASONING:header")
            assertDisclosure(REASONING, true, REASONING_RENDERED)
            val reasoning = textTarget(REASONING, REASONING_RENDERED)
            textIsCompleteAt(reasoning, REASONING_RENDERED, REASONING, wrapped = false)
            assertFalse(reasoning.fetchSemanticsNode().config.contains(SemanticsActions.OnClick))
            pointerTarget(reasoning)
            assertDisclosure(REASONING, true, REASONING_RENDERED)
            pointer("$CALL:header")
            assertDisclosure(CALL, true, ARGUMENTS)
            val arguments = textTarget(CALL, ARGUMENTS)
            textIsCompleteAt(arguments, ARGUMENTS, CALL, wrapped = false)
            assertFalse(arguments.fetchSemanticsNode().config.contains(SemanticsActions.OnClick))
            pointerTarget(arguments)
            assertDisclosure(CALL, true, ARGUMENTS)
            pointer("$REASONING:header")
            pointer("$CALL:header")
            assertDisclosure(REASONING, false)
            assertDisclosure(CALL, false)
        }

    @Test
    fun realTabEnterAndSpaceOperateIndependentFocusableHeaders() =
        runSkikoComposeUiTest(size = Size(600f, 800f)) {
            mount({ ready(disclosureHistory()) })
            // The scrollable timeline is itself a Tab stop before the first activity header.
            focusByTab("$REASONING:header")
            keyFromRoot(Key.Enter)
            assertDisclosure(REASONING, true)
            keyFromRoot(Key.Spacebar)
            assertDisclosure(REASONING, false)
            focusByTab("$CALL:header")
            keyFromRoot(Key.Spacebar)
            assertDisclosure(CALL, true, ARGUMENTS)
            textIsCompleteValue(ARGUMENTS)
            assertDisclosure(REASONING, false)
            keyFromRoot(Key.Enter)
            assertDisclosure(CALL, false)
        }

    @Test
    fun sameScopeLoadingFailureAndGenerationRefreshPreserveDisclosureAndScroll() =
        runSkikoComposeUiTest(size = Size(600f, 800f)) {
            var state by mutableStateOf(ready(disclosureHistory(long = true)))
            mount({ state })
            // The list follows the newest message on mount, so reach the disclosure at the top first.
            wheelUntilVisible("$REASONING:header", direction = -1f)
            pointer("$REASONING:header")
            pointer("$CALL:header")
            wheelPixels(180f)
            val before = scrollValue()
            assertTrue(before > 0f, "Real wheel must move the timeline")
            listOf(ConversationDataPhase.Loading, ConversationDataPhase.Failed, ConversationDataPhase.Ready).forEach { phase ->
                runOnIdle { state = state.copy(historyPhase = phase) }
                settle()
                assertDisclosure(REASONING, true, REASONING_RENDERED)
                assertDisclosure(CALL, true, ARGUMENTS)
                textIsCompleteValue(REASONING_RENDERED)
                textIsCompleteValue(ARGUMENTS)
                assertNear(before, scrollValue(), "Same-scope $phase keeps first item/offset", 0.02f)
            }
            val replacement = "Updated typed reasoning in the same session"
            runOnIdle { state = state.copy(host = state.host!!.copy(generation = 2),
                history = state.history.map { it.copy(content = listOf(ReasoningContent(replacement)) + it.content.drop(1)) }) }
            settle()
            assertDisclosure(REASONING, true, replacement)
            assertDisclosure(CALL, true, ARGUMENTS)
            textIsCompleteValue(replacement)
            textIsCompleteValue(ARGUMENTS)
            assertNear(before, scrollValue(), "Generation is excluded from timeline composition key", 0.02f)
        }

    @Test fun equalHistorySessionSwitchResetsDisclosureAndScroll() = scopeSwitch(ScopeChange.Session)
    @Test fun equalHistoryHostSwitchResetsDisclosureAndScroll() = scopeSwitch(ScopeChange.Host)
    @Test fun equalHistoryMemberSwitchResetsDisclosureAndScroll() = scopeSwitch(ScopeChange.Member)

    @Test
    fun unmatchedRepeatedCallIdsAndNestedResultsKeepRawContentAndChronologicalOrder() =
        runSkikoComposeUiTest(size = Size(600f, 800f)) {
            val history = listOf(
                Message("orphan", MessageRole.USER, listOf(ToolResultContent("unmatched", listOf(TextContent("Unmatched result before calls")))), ToolMessageSource("unmatched")),
                Message("repeated", MessageRole.ASSISTANT, listOf(
                    ToolCallContent("same-call-id", "first_tool", "{\"first\":true}"),
                    ToolCallContent("same-call-id", "second_tool", "{\"second\":true}"),
                    ToolResultContent("same-call-id", listOf(TextContent("Outer first"),
                        ToolResultContent("nested-unmatched", listOf(TextContent("Nested output")), true),
                        TextContent("Outer last"))), TextContent("Final assistant text")), MODEL),
            )
            mount({ ready(history) })
            val first = "message:repeated:content:0"
            val second = "message:repeated:content:1"
            val result = "message:repeated:content:2"
            // An unmatched result is its own row; it never folds into a call.
            pointer("message:orphan:content:0:header")
            assertDisclosure("message:orphan:content:0", true, "Unmatched result before calls")
            textIsComplete("message:orphan:content:0:content:0", "Unmatched result before calls")

            // Both calls share one id, so the result folds under whichever call is open; only one is open
            // at a time here, because two open calls would compose the same result tags twice.
            pointer("$first:header")
            assertDisclosure(first, true)
            assertDisclosure(second, false)
            val ordered = listOf(
                "$result:content:0" to "Outer first",
                "$result:content:1" to "Nested output",
                "$result:content:2" to "Outer last",
            )
            pointer("$result:content:1:header")
            assertDisclosure("$result:content:1", true, "Nested output")
            textIsComplete("$result:content:0", "Outer first")
            textIsComplete("$result:content:1:content:0", "Nested output")
            textIsComplete("$result:content:2", "Outer last")
            ordered.zipWithNext().forEach { (before, after) ->
                assertTrue(layout(before.first).bottom <= layout(after.first).top + 1f, "Raw order: $before before $after")
            }
            textIsComplete("message:repeated:content:3", "Final assistant text")

            pointer("$first:header")
            assertDisclosure(first, false)
            pointer("$second:header")
            assertDisclosure(second, true)
            assertDisclosure(first, false)
            pointer("$second:header")
            // The direct result row keeps the same raw children in the same order.
            pointer("$result:header")
            assertDisclosure(result, true, "Outer first")
            textIsComplete("$result:content:0", "Outer first")
            textIsComplete("$result:content:2", "Outer last")
            pointer("$result:content:1:header")
            assertDisclosure("$result:content:1", true, "Nested output")
            textIsComplete("$result:content:1:content:0", "Nested output")
        }

    @Test
    fun isErrorResultsKeepRawOutputWithoutInventingExecutionState() =
        runSkikoComposeUiTest(size = Size(600f, 800f)) {
            mount({ ready(listOf(Message("results", MessageRole.USER, listOf(
                ToolResultContent("normal", listOf(TextContent("Normal raw output"))),
                ToolResultContent("error", listOf(TextContent("Error raw output")), true)), ToolMessageSource("normal")))) })
            // A failed result keeps the same neutral label; only its raw output is shown when expanded.
            onAllNodesWithText("工具输出").assertCountEquals(2)
            pointer("message:results:content:0:header")
            pointer("message:results:content:1:header")
            assertDisclosure("message:results:content:0", true, "Normal raw output")
            assertDisclosure("message:results:content:1", true, "Error raw output")
            textIsComplete("message:results:content:0:content:0", "Normal raw output")
            textIsComplete("message:results:content:1:content:0", "Error raw output")
            listOf("Success", "Completed", "Running", "Duration", "Tool call", "Tool failed").forEach {
                onNodeWithText(it, substring = true).assertDoesNotExist()
            }
            val labels = onAllNodes(SemanticsMatcher("Rendered text") { it.config.contains(SemanticsProperties.Text) }, true)
                .fetchSemanticsNodes().flatMap { it.config[SemanticsProperties.Text].map { text -> text.text } }
            assertFalse(labels.any { it == "✓" || Regex("[0-9]+(?:\\.[0-9]+)?\\s*(?:ms|seconds|s)").containsMatchIn(it) })
        }

    @Test
    fun realWheelTraversesLongWrappedMessagesAndReachesTheActualFinalMarker() =
        runSkikoComposeUiTest(size = Size(600f, 800f)) {
            val history = (0..14).map { index -> Message("scroll-$index", MessageRole.ASSISTANT,
                listOf(TextContent("Raw message $index\n" + "长目录/".repeat(80))) +
                    if (index == 14) listOf(TextContent(FINAL_MARKER)) else emptyList(), MODEL) }
            mount({ ready(history) })
            // The timeline follows the newest message on mount.
            assertTrue(scrollValue() > 0f)
            wheelUntilVisible("message:scroll-14:content:1")
            assertTrue(scrollValue() > 0f)
            textIsComplete("message:scroll-14:content:0",
                rendered((history.last().content.first() as TextContent).text), wrapped = true)
            textIsComplete("message:scroll-14:content:1", FINAL_MARKER)
            assertInside(layout("message:scroll-14:content:1"), node(TIMELINE).boundsInRoot, "Final marker is truly visible")
            wheelUntilVisible("message:scroll-0:content:0", direction = -1f)
            textIsComplete("message:scroll-0:content:0",
                rendered((history.first().content.first() as TextContent).text), wrapped = true)
            assertEquals(0f, scrollValue())
        }

    private enum class ScopeChange { Session, Host, Member }

    private fun scopeSwitch(change: ScopeChange) = runSkikoComposeUiTest(size = Size(600f, 800f)) {
        val identity = MemberIdentity(BackendTenant("https://thread-fixture.invalid", 1), 1)
        val initial = ready(disclosureHistory(long = true)).copy(host = HOST.copy(identity = identity))
        var state by mutableStateOf(initial)
        mount({ state })
        wheelUntilVisible("$REASONING:header", direction = -1f)
        pointer("$REASONING:header")
        pointer("$CALL:header")
        wheelUntilVisible("message:assistant:content:3")
        assertTrue(scrollValue() > 0f)
        runOnIdle { state = when (change) {
            ScopeChange.Session -> state.copy(selectedSessionId = SessionId("other-session"))
            ScopeChange.Host -> state.copy(host = state.host!!.copy(hostId = HostId("other-host")))
            ScopeChange.Member -> state.copy(host = state.host!!.copy(identity = identity.copy(userId = 2)))
        } }
        settle()
        assertDisclosure(REASONING, false)
        assertDisclosure(CALL, false)
        // The new scope starts following its own tail, like a fresh mount.
        assertNear(scrollMax(), scrollValue(), "$change resets timeline scroll with equal message IDs/text")
        wheelUntilVisible("$REASONING:header", direction = -1f)
        pointer("$REASONING:header")
        assertDisclosure(REASONING, true)
        runOnIdle { state = initial }
        settle()
        assertDisclosure(REASONING, false)
        assertDisclosure(CALL, false)
        assertNear(scrollMax(), scrollValue(), "Returning to a prior scope must not revive its disclosure state")
    }

    private fun captureScenario(width: Int, height: Int, theme: HarnessThemeMode) =
        runSkikoComposeUiTest(size = Size(width.toFloat(), height.toFloat())) {
            var state by mutableStateOf(ready(visualHistory()))
            mount({ state }, theme)
            val column = layout("message:assistant")
            val timeline = layout(TIMELINE)
            // The list pads 11dp per side and each message slot pads 16dp inside a 768dp cap.
            assertNear(minOf(timeline.width - 22f, 768f) - 32f, column.width, "Bounded content column")
            assertNear(timeline.center.x, column.center.x, "Centered content column")
            // Message frames are full-width; only the human bubble caps itself, and its Text is the
            // naturally sized, right-aligned part. Long user content still lays out completely.
            textIsComplete("message:long-user:content:0", LONG_USER, wrapped = true)
            assertDisclosure(REASONING, false, REASONING_RENDERED)
            assertDisclosure(CALL, false, RAW_RESULT)
            capture("collapsed", width, height, theme)
            pointer("$REASONING:header")
            pointer("$CALL:header")
            assertDisclosure(REASONING, true, REASONING_RENDERED)
            // The result message folds into this call row, so this row prints the result, not arguments.
            assertDisclosure(CALL, true, RAW_RESULT)
            textIsCompleteValue(REASONING_RENDERED)
            textIsComplete("message:result:content:0:content:0", RAW_RESULT)
            capture("expanded", width, height, theme)
            runOnIdle { state = ready(listOf(Message("long-output", MessageRole.ASSISTANT,
                listOf(TextContent(LONG_OUTPUT), TextContent(FINAL_MARKER)), MODEL)))
                .copy(selectedSessionId = SessionId("long-content-session")) }
            settle()
            textIsComplete("message:long-output:content:0", rendered(LONG_OUTPUT), wrapped = true)
            wheelUntilVisible("message:long-output:content:1")
            textIsComplete("message:long-output:content:1", FINAL_MARKER)
            assertInside(layout("message:long-output:content:1"), node(TIMELINE).boundsInRoot, "Long content marker")
            capture("long-content", width, height, theme)
        }

    private fun ComposeUiTest.mount(state: () -> ConversationState, theme: HarnessThemeMode = HarnessThemeMode.Light) {
        setContent { CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) { HarnessTheme(theme) {
            Box(Modifier.fillMaxSize().background(HarnessTokens.Colors.Canvas).semantics { testTag = FIXTURE }) {
                LiveThreadSurface(state(), onRetry = { error("Unexpected history retry in message fixture") })
            }
        } } }
        settle()
    }

    private fun ComposeUiTest.settle() { mainClock.advanceTimeByFrame(); waitForIdle() }
    private fun ComposeUiTest.node(tag: String): SemanticsNode = onNodeWithTag(tag, true).fetchSemanticsNode()
    private fun ComposeUiTest.layout(tag: String): Rect = node(tag).let {
        Rect(it.positionInRoot, Size(it.size.width.toFloat(), it.size.height.toFloat()))
    }
    private fun ComposeUiTest.rootFor(tag: String): SemanticsNodeInteraction = rootFor(node(tag))

    private fun ComposeUiTest.rootFor(target: SemanticsNode): SemanticsNodeInteraction {
        val root = generateSequence(target) { it.parent }.last()
        return onNode(SemanticsMatcher("Thread input root ${root.id}") { it.id == root.id }, true)
    }

    /** The timeline tag sits on the surface slot; the LazyColumn below it owns the scroll axis. */
    private fun ComposeUiTest.scrollNode(): SemanticsNodeInteraction =
        onNode(hasAnyAncestor(hasTestTag(TIMELINE)) and SemanticsMatcher("Vertical scroll axis") {
            it.config.contains(SemanticsProperties.VerticalScrollAxisRange)
        }, true)

    private fun ComposeUiTest.pointer(tag: String) = pointerTarget(onNodeWithTag(tag, true))

    private fun ComposeUiTest.pointerTarget(target: SemanticsNodeInteraction) {
        val started = System.nanoTime()
        settle()
        target.assertIsDisplayed()
        var root = rootFor(target.fetchSemanticsNode())
        root.performMouseInput { moveTo(target.fetchSemanticsNode().boundsInRoot.center - root.fetchSemanticsNode().boundsInRoot.topLeft) }
        settle()
        target.assertIsDisplayed()
        val measured = target.fetchSemanticsNode()
        root = rootFor(measured)
        assertInside(Rect(measured.positionInRoot, Size(measured.size.width.toFloat(), measured.size.height.toFloat())),
            measured.boundsInRoot, "Unclipped mouse target ${measured.id}")
        val point = measured.boundsInRoot.center - root.fetchSemanticsNode().boundsInRoot.topLeft
        root.performMouseInput { moveTo(point); advanceEventTime(16); click(point) }
        settle()
        assertTrue(System.nanoTime() - started < DEADLINE, "Mouse deadline: ${measured.id}")
    }

    private fun ComposeUiTest.focusByTab(tag: String) {
        val started = System.nanoTime()
        while (node(tag).config.getOrNull(SemanticsProperties.Focused) != true) {
            assertTrue(System.nanoTime() - started < DEADLINE, "Real Tab must reach $tag")
            keyFromRoot(Key.Tab)
        }
        onNodeWithTag(tag).assertIsFocused()
    }

    private fun ComposeUiTest.keyFromRoot(key: Key) {
        val started = System.nanoTime()
        rootFor(TIMELINE).performKeyInput { pressKey(key) }
        settle()
        assertTrue(System.nanoTime() - started < DEADLINE, "Keyboard deadline: $key")
    }

    private fun ComposeUiTest.assertDisclosure(tag: String, expanded: Boolean, bodyText: String? = null) {
        val header = onNodeWithTag("$tag:header").fetchSemanticsNode()
        assertEquals(Role.Button, header.config[SemanticsProperties.Role])
        assertEquals(if (expanded) "已展开" else "已折叠", header.config[SemanticsProperties.StateDescription])
        assertTrue(header.config.contains(SemanticsActions.OnClick))
        // Expanded bodies are plain markdown without their own tag, so the text identifies them.
        if (bodyText != null) {
            if (expanded) onNode(hasText(bodyText), true).assertExists()
            else onNode(hasText(bodyText), true).assertDoesNotExist()
        }
    }

    /** Markdown content tags a selection wrapper above the laid-out Text, while user text tags the Text. */
    private fun ComposeUiTest.textTarget(tag: String, expected: String): SemanticsNodeInteraction =
        onNode(hasText(expected) and (hasTestTag(tag) or hasAnyAncestor(hasTestTag(tag))), true)

    private fun ComposeUiTest.textIsComplete(tag: String, expected: String, wrapped: Boolean = false) =
        textIsCompleteAt(textTarget(tag, expected), expected, tag, wrapped)

    private fun ComposeUiTest.textIsCompleteValue(expected: String, wrapped: Boolean = false) =
        textIsCompleteAt(onNode(hasText(expected), true), expected, "text:$expected", wrapped)

    private fun ComposeUiTest.textIsCompleteAt(
        target: SemanticsNodeInteraction, expected: String, tag: String, wrapped: Boolean,
    ) {
        target.assertTextEquals(expected)
        val results = mutableListOf<TextLayoutResult>()
        // Read-only layout query: all interaction remains real pointer/key/wheel input.
        target.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        val text = results.single { it.layoutInput.text.text == expected }
        val measured = target.fetchSemanticsNode()
        val bounds = Rect(measured.positionInRoot, Size(measured.size.width.toFloat(), measured.size.height.toFloat()))
        val viewport = node(TIMELINE).boundsInRoot
        val localBounds = Rect(Offset.Zero, Size(bounds.width, bounds.height))
        val lines = (0 until text.lineCount).map { line ->
            Rect(text.getLineLeft(line), text.getLineTop(line), text.getLineRight(line), text.getLineBottom(line))
        }
        // TextOverflow.Visible can report fractional paragraph overflow against rounded IntSize.
        // getBoundingBox() forwards to Paragraph.getRectsForRange with STRUT height and TIGHT width.
        // These character layout bounds are not independently measured raster glyph ink or baseline bounds.
        // Line metrics provide vertical layout containment; character bounds provide horizontal
        // containment and diagnostics. Neither establishes original Codex pixel parity.
        val glyphs = expected.indices.filter { !expected[it].isWhitespace() }.map { offset ->
            offset to text.getBoundingBox(offset)
        }
        val geometry = buildJsonObject {
            put("tag", tag); put("size", text.size.toString()); put("nodeBounds", bounds.toString())
            put("paragraphWidth", text.multiParagraph.width); put("paragraphHeight", text.multiParagraph.height)
            put("didOverflowWidth", text.didOverflowWidth); put("didOverflowHeight", text.didOverflowHeight)
            put("hasVisualOverflow", text.hasVisualOverflow); put("lineCount", text.lineCount)
            put("overflow", text.layoutInput.overflow.toString()); put("maxLines", text.layoutInput.maxLines)
            put("constraints", text.layoutInput.constraints.toString())
            put("lastLineEnd", text.getLineEnd(text.lineCount - 1)); put("expectedLength", expected.length)
            putJsonArray("lines") { lines.forEachIndexed { line, rect -> add(buildJsonObject {
                put("line", line); put("left", rect.left); put("top", rect.top)
                put("right", rect.right); put("bottom", rect.bottom)
                put("ellipsized", text.isLineEllipsized(line))
            }) } }
            putJsonArray("characterBounds") { glyphs.forEach { (offset, rect) -> add(buildJsonObject {
                put("offset", offset); put("left", rect.left); put("top", rect.top)
                put("right", rect.right); put("bottom", rect.bottom)
            }) } }
        }
        textGeometry[tag] = geometry
        // Persist measured diagnostics in the parent test log even if an assertion prevents capture.
        println("THREAD_TEXT_GEOMETRY " + geometry)
        if (wrapped) assertTrue(text.lineCount > 1, "Expected full wrapping: $tag")
        assertEquals(expected.length, text.getLineEnd(text.lineCount - 1), "Last character must be laid out: $tag")
        assertTrue((0 until text.lineCount).none { text.isLineEllipsized(it) }, "No ellipsis: $tag")
        assertEquals(text.size.width.toFloat(), bounds.width, "Measured text width: $tag")
        assertEquals(text.size.height.toFloat(), bounds.height, "Measured text height: $tag")
        lines.forEachIndexed { line, rect ->
            assertInside(rect, localBounds, "Complete line $line within measured text: $tag")
        }
        glyphs.forEach { (offset, rect) ->
            assertTrue(rect.left >= -1f && rect.right <= bounds.width + 1f,
                "Character $offset horizontal bounds within measured text: $tag $rect / $localBounds")
            val inRoot = Rect(rect.topLeft + bounds.topLeft, rect.size)
            assertTrue(inRoot.left >= viewport.left - 1f && inRoot.right <= viewport.right + 1f,
                "Character $offset horizontal viewport containment: $tag $inRoot / $viewport")
        }
        assertTrue(bounds.left >= viewport.left - 1f && bounds.right <= viewport.right + 1f, "Horizontal containment: $tag $bounds / $viewport")
    }

    private fun ComposeUiTest.scrollValue(): Float =
        scrollNode().fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()

    private fun ComposeUiTest.scrollMax(): Float =
        scrollNode().fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].maxValue()

    private fun ComposeUiTest.wheelPixels(pixels: Float) {
        require(pixels.isFinite() && pixels != 0f)
        val started = System.nanoTime()
        settle()
        var list = scrollNode().fetchSemanticsNode()
        var root = rootFor(list)
        fun point(): Offset {
            val viewport = list.boundsInRoot.intersect(root.fetchSemanticsNode().boundsInRoot)
            assertTrue(viewport.width > 0f && viewport.height > 0f, "Measured timeline viewport")
            return Offset(viewport.left + minOf(4f, viewport.width / 2f), viewport.center.y) - root.fetchSemanticsNode().boundsInRoot.topLeft
        }
        root.performMouseInput { moveTo(point()) }
        settle()
        list = scrollNode().fetchSemanticsNode(); root = rootFor(list)
        // Same conversion as PluginsPageTestSupport: Skiko wheel delta has no AWT scrollAmount.
        val pixelsPerWheelUnit = when (val os = System.getProperty("os.name").orEmpty()) {
            "Mac OS X" -> 10f * list.layoutInfo.density.density
            else -> if (os.startsWith("Linux")) sqrt(list.size.height.toFloat()) else list.size.height / 20f
        }
        assertTrue(pixelsPerWheelUnit > 0f)
        val measuredPoint = point()
        root.performMouseInput { moveTo(measuredPoint); advanceEventTime(16); scroll(pixels / pixelsPerWheelUnit) }
        settle()
        assertTrue(System.nanoTime() - started < DEADLINE, "Wheel step deadline")
    }

    private fun ComposeUiTest.wheelUntilVisible(tag: String, direction: Float = 1f) {
        val started = System.nanoTime()
        var steps = 0
        while (true) {
            settle()
            val viewport = node(TIMELINE).boundsInRoot
            val target = onAllNodesWithTag(tag, true).fetchSemanticsNodes().singleOrNull()
            if (target != null) {
                val bounds = Rect(target.positionInRoot, Size(target.size.width.toFloat(), target.size.height.toFloat()))
                if (bounds.height > 0f && bounds.top >= viewport.top - 1f && bounds.bottom <= viewport.bottom + 1f) break
            }
            assertTrue(System.nanoTime() - started < DEADLINE,
                "Real wheel did not reach $tag in 10s: steps=$steps scroll=${scrollValue()} viewport=$viewport target=${target?.boundsInRoot}")
            wheelPixels(viewport.height * 0.6f * direction)
            steps++
        }
        assertTrue(System.nanoTime() - started < DEADLINE, "Wheel traversal deadline: $tag")
        onNodeWithTag(tag, true).assertIsDisplayed()
    }

    private fun ComposeUiTest.capture(stage: String, width: Int, height: Int, theme: HarnessThemeMode) {
        settle()
        val fixture = node(FIXTURE)
        val root = generateSequence(fixture) { it.parent }.last()
        assertEquals(width, root.size.width); assertEquals(height, root.size.height)
        assertEquals(width, fixture.size.width); assertEquals(height, fixture.size.height)
        assertInside(layout(FIXTURE), root.boundsInRoot, "Fixture root")
        val target = onNode(SemanticsMatcher("Actual Thread fixture root ${root.id}") { it.id == root.id }, true)
        val bytes = Image.makeFromBitmap(target.captureToImage().asSkiaBitmap()).use { image ->
            image.encodeToData(EncodedImageFormat.PNG)?.use { it.bytes } ?: error("PNG encoding failed")
        }
        val directory = File(System.getenv("HARNESS_THREAD_MESSAGE_CAPTURE_DIR")?.takeIf { it.isNotBlank() }
            ?: "/tmp/harness-thread-message-captures-20261008")
        assertTrue(directory.isDirectory || directory.mkdirs(), "Cannot create capture directory")
        val name = "thread-message-${width}x$height-${theme.name.lowercase()}-$stage"
        val png = File(directory, "$name.png").also { it.writeBytes(bytes) }
        val decoded = assertNotNull(ImageIO.read(png))
        assertEquals(width, decoded.width); assertEquals(height, decoded.height)
        val viewport = node(TIMELINE).boundsInRoot
        val nodes = onAllNodes(SemanticsMatcher("Tagged Thread nodes") { it.config.contains(SemanticsProperties.TestTag) }, true)
            .fetchSemanticsNodes().filter { generateSequence(it) { parent -> parent.parent }.last().id == root.id }
        nodes.filter { it.config[SemanticsProperties.TestTag].startsWith("message:") }.forEach { node ->
            val bounds = Rect(node.positionInRoot, Size(node.size.width.toFloat(), node.size.height.toFloat()))
            assertTrue(bounds.left >= viewport.left - 1f && bounds.right <= viewport.right + 1f, "Captured content horizontal bounds: $bounds / $viewport")
            if (node.boundsInRoot.width > 0f && node.boundsInRoot.height > 0f) assertInside(node.boundsInRoot, viewport, "Visible content clip")
        }
        val geometry = buildJsonObject {
            put("schema", "harness.thread-message.capture.v1"); put("stage", stage); put("image", png.name)
            put("width", width); put("height", height); put("theme", theme.name); put("density", 1f); put("fontScale", 1f)
            put("captureRootId", root.id); put("fixtureId", fixture.id); put("scrollValue", scrollValue())
            put("sha256", MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
            put("rootBoundsInWindow", root.boundsInWindow.toString())
            putJsonArray("textLayout") { textGeometry.values.forEach { add(it) } }
            put("evidenceLimit", "Offscreen Skiko production surface with typed fixtures; no native window, original same-state comparison or pixel parity proof")
            putJsonArray("nodes") { nodes.forEach { node -> add(buildJsonObject {
                put("tag", node.config[SemanticsProperties.TestTag]); put("id", node.id)
                put("positionInRoot", node.positionInRoot.toString()); put("layoutWidth", node.size.width); put("layoutHeight", node.size.height)
                put("boundsInRoot", node.boundsInRoot.toString()); put("boundsInWindow", node.boundsInWindow.toString())
                node.config.getOrNull(SemanticsProperties.StateDescription)?.let { put("stateDescription", it) }
            }) } }
        }
        File(directory, "$name.geometry.json").writeText(Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), geometry))
    }

    private companion object {
        const val DEADLINE = 10_000_000_000L
        const val FIXTURE = "thread-message-fixture"
        const val TIMELINE = "live-thread-timeline"
        const val REASONING = "message:assistant:content:0"
        const val CALL = "message:assistant:content:1"
        const val REASONING_TEXT = "Typed reasoning is preserved verbatim.\nA second line remains readable."
        // Markdown soft line breaks render as spaces in the laid-out paragraph.
        const val REASONING_RENDERED = "Typed reasoning is preserved verbatim. A second line remains readable."
        const val ARGUMENTS = "{\"path\":\"fixture/source.kt\",\"readOnly\":true}"
        const val RAW_RESULT = "Raw result remains visible"
        const val FINAL_MARKER = "THREAD-FINAL-CONTENT-MARKER"
        val HOST = ConversationHostScope(HostId("thread-visual-host"), 1, null)
        val MODEL = ModelMessageSource("fixture-provider", "fixture-model")
        val LONG_USER = "请完整保留这段用户内容与长目录/" + "中文路径/with-spaces/".repeat(14) + "USER-END"
        val LONG_OUTPUT = (1..70).joinToString("\n") { "Raw output line $it: " + "完整路径/segment/".repeat(5) }
        fun rendered(text: String) = text.replace("\n", " ")
        fun ready(history: List<Message>) = ConversationState(host = HOST, connected = true,
            listPhase = ConversationDataPhase.Ready, historyPhase = ConversationDataPhase.Ready,
            selectedSessionId = SessionId("thread-visual-session"), history = history, runState = ConversationRunState.Idle)
        fun disclosureHistory(long: Boolean = false) = listOf(Message("assistant", MessageRole.ASSISTANT,
            listOf(ReasoningContent(REASONING_TEXT), ToolCallContent("call", "read_fixture", ARGUMENTS)) +
                if (long) listOf(TextContent(LONG_OUTPUT), TextContent(FINAL_MARKER)) else emptyList(), MODEL))
        fun visualHistory() = listOf(
            Message("short-user", MessageRole.USER, listOf(TextContent("Read the source.")), UserMessageSource),
            Message("long-user", MessageRole.USER, listOf(TextContent(LONG_USER)), UserMessageSource),
        ) + disclosureHistory() + listOf(Message("result", MessageRole.USER,
            listOf(ToolResultContent("call", listOf(TextContent("Raw result remains visible")))), ToolMessageSource("call")))
        fun assertNear(expected: Float, actual: Float, label: String, tolerance: Float = 1f) {
            assertTrue(abs(expected - actual) <= tolerance, "$label: expected=$expected actual=$actual tolerance=$tolerance")
        }
        fun assertInside(inner: Rect, outer: Rect, label: String) {
            assertTrue(inner.left >= outer.left - 1f && inner.top >= outer.top - 1f &&
                inner.right <= outer.right + 1f && inner.bottom <= outer.bottom + 1f, "$label: $inner / $outer")
        }
    }
}
