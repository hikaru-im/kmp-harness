@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package im.hikaru.harness.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.semantics.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.harness.client.connection.*
import im.hikaru.harness.client.conversation.ConversationHostScope
import im.hikaru.harness.client.panels.*
import im.hikaru.harness.ui.foundation.HarnessText
import im.hikaru.harness.ui.foundation.HarnessTheme
import im.hikaru.harness.ui.foundation.HarnessThemeMode
import im.hikaru.harness.ui.foundation.HarnessTokens
import java.io.File
import java.security.MessageDigest
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image

/** Production controller -> connection repository -> lineage-checked SessionApi read seam. */
class SubagentHistoryInteractionTest {
    @Test fun subagentHistory320Light() = captureMatrix(320, 600, HarnessThemeMode.Light)
    @Test fun subagentHistory320Dark() = captureMatrix(320, 600, HarnessThemeMode.Dark)
    @Test fun subagentHistory640Light() = captureMatrix(640, 800, HarnessThemeMode.Light)
    @Test fun subagentHistory640Dark() = captureMatrix(640, 800, HarnessThemeMode.Dark)

    @Test
    fun realServiceHistoryRendersSourcesAndIndependentMouseKeyboardDisclosures() {
        HistoryHarness().use { harness ->
            runSkikoComposeUiTest(size = Size(640f, 800f)) {
                mount(harness)
                selectChild(harness, CHILD_A)
                listOf("human" to "You · USER", "assistant" to "Model message · ASSISTANT",
                    "tool" to "Tool message · USER", "plugin" to "Plugin fixture-plugin · USER",
                    "nested" to "Tool message · USER").forEach { (id, description) ->
                    assertEquals(listOf(description), node("message:$id").config[SemanticsProperties.ContentDescription])
                }
                val neutral = layout("message:assistant")
                val human = layout("message:human")
                assertTrue(human.width <= neutral.width * .85f + 1f)
                assertNear(neutral.right, human.right, "Only the actual human is end aligned")
                listOf("tool", "plugin", "nested").forEach { id ->
                    assertNear(neutral.left, layout("message:$id").left, "Neutral source left edge")
                    assertNear(neutral.width, layout("message:$id").width, "Neutral source width")
                }
                completeText("message:human:content:0", harness.human(CHILD_A))
                completeText("message:tool:content:0", "Tool source with USER role")
                completeText("message:plugin:content:0", "Plugin source with USER role")
                disclosure(REASONING, false); disclosure(CALL, false)
                pointerTag("$REASONING:header")
                disclosure(REASONING, true); disclosure(CALL, false)
                completeText("$REASONING:body", REASONING_TEXT, wrapped = true)
                pointerTag("$REASONING:body")
                disclosure(REASONING, true)
                wheelUntilVisible("$CALL:header")
                focusByTab("$CALL:header")
                keyFromRoot(Key.Enter)
                disclosure(CALL, true); disclosure(REASONING, true)
                completeText("$CALL:body", ARGUMENTS, wrapped = true)
                keyFromRoot(Key.Spacebar)
                disclosure(CALL, false); disclosure(REASONING, true)
                pointerTag("$REASONING:header")
                disclosure(REASONING, false)
                onNodeWithText("Tool failed").assertExists()
                assertReadOnly(harness)
            }
        }
    }

    @Test
    fun realWheelReachesNestedErrorTailWithoutEllipsis() {
        HistoryHarness(long = true).use { harness ->
            runSkikoComposeUiTest(size = Size(320f, 600f)) {
                mount(harness); selectChild(harness, CHILD_A)
                val initial = range()
                assertEquals(0f, initial.first)
                assertTrue(initial.second > 0f)
                completeText(NESTED_TEXT, harness.raw(CHILD_A), wrapped = true)
                wheelToEdge(end = true)
                assertTrue(range().first > initial.first, "Real wheel must move the production transcript")
                endProof(harness.marker(CHILD_A))
                onNodeWithText("Tool failed").assertExists()
                assertReadOnly(harness)
            }
        }
    }

    @Test
    fun sameMessageIdsAreIsolatedAcrossChildAndOwnerSwitches() {
        HistoryHarness().use { harness ->
            runSkikoComposeUiTest(size = Size(640f, 800f)) {
                mount(harness); selectChild(harness, CHILD_A)
                pointerTag("$REASONING:header"); pointerTag("$CALL:header")
                disclosure(REASONING, true); disclosure(CALL, true)
                val oldHuman = harness.human(CHILD_A)
                pointerTag("返回子智能体")
                runOnIdle { assertNull(harness.current.selectedChild) }
                selectChild(harness, CHILD_B)
                disclosure(REASONING, false); disclosure(CALL, false)
                onNodeWithText(oldHuman, useUnmergedTree = true).assertDoesNotExist()
                completeText("message:human:content:0", harness.human(CHILD_B))
                pointerTag("$REASONING:header"); pointerTag("$CALL:header")
                val ownerGeneration = harness.current.ownerGeneration
                val sameScope = harness.owner.scope
                runOnIdle { harness.rebind(harness.owner.copy(host = harness.owner.host.copy(generation = 2)), "owner-2") }
                settle(); selectChild(harness, CHILD_B)
                runOnIdle {
                    assertEquals(sameScope, harness.current.scope)
                    assertTrue(harness.current.ownerGeneration > ownerGeneration)
                }
                disclosure(REASONING, false); disclosure(CALL, false)
                pointerTag("$REASONING:header")
                val priorScope = harness.current.scope
                runOnIdle { harness.rebind(harness.owner.copy(host = ConversationHostScope(HostId("other-host"), 3, null),
                    parentSessionId = SessionId("other-parent")), "owner-3") }
                settle(); selectChild(harness, CHILD_B)
                runOnIdle { assertNotEquals(priorScope, harness.current.scope) }
                disclosure(REASONING, false); disclosure(CALL, false)
                completeText("message:human:content:0", harness.human(CHILD_B))
                assertReadOnly(harness)
            }
        }
    }

    @Test
    fun detailLeadsWithCodexStatusAndDelegatedTaskSections() {
        HistoryHarness().use { harness ->
            runSkikoComposeUiTest(size = Size(640f, 800f)) {
                mount(harness); selectChild(harness, CHILD_A)
                // Codex `subagents.panel.status` / `.delegatedTask`: the detail view leads with the
                // current status and the task the parent delegated, both read from the summary.
                onNodeWithTag("subagent-detail-status", true).assertExists()
                onNodeWithText("状态", useUnmergedTree = true).assertExists()
                onNodeWithText("处理中", useUnmergedTree = true).assertExists()
                onNodeWithTag("subagent-detail-task", true).assertExists()
                onNodeWithText("委派的任务", useUnmergedTree = true).assertExists()
                onNodeWithText(harness.objective(CHILD_A), useUnmergedTree = true).assertExists()
                // The lineage-checked transcript still renders below the sections.
                onNodeWithTag(TRANSCRIPT, true).assertExists()
                assertReadOnly(harness)
            }
        }
    }

    @Test
    fun loadingErrorUnavailableAndSuccessfulEmptyRemainDistinctAndReadOnly() {
        HistoryHarness().use { harness ->
            val gate = CompletableDeferred<Unit>()
            harness.historyRead = { _, _ -> gate.await(); error("Fixture read failure") }
            runSkikoComposeUiTest(size = Size(640f, 800f)) {
                mount(harness); selectChild(harness, CHILD_A, ready = false)
                onNodeWithText("正在加载子智能体对话记录…").assertIsDisplayed()
                onNodeWithText("没有消息").assertDoesNotExist()
                onNodeWithTag("刷新历史").assertIsNotEnabled()
                assertReadOnly(harness)
                runOnIdle { gate.complete(Unit) }
                settle()
                onNodeWithText("无法加载对话记录").assertIsDisplayed()
                onNodeWithText("没有消息").assertDoesNotExist()
                onNodeWithTag(TRANSCRIPT, true).assertDoesNotExist()
                runOnIdle { harness.historyRead = { parent, child -> ChildHistoryResult.Unavailable(parent, child) } }
                pointerTag("刷新历史")
                onNodeWithText("对话记录不可用").assertIsDisplayed()
                onNodeWithText("没有消息").assertDoesNotExist()
                assertReadOnly(harness)
                runOnIdle { harness.historyRead = { parent, child -> harness.available(parent, child, emptyList()) } }
                pointerTag("刷新历史")
                onNodeWithText("没有消息").assertIsDisplayed()
                onNodeWithText("对话记录不可用").assertDoesNotExist()
                runOnIdle {
                    val detail = (harness.current.detail as PanelLoad.Ready).value
                    assertEquals(emptyList(), detail.typedMessages)
                    assertTrue(detail.entries.isEmpty())
                    assertEquals(harness.owner.parentSessionId to CHILD_A, harness.requests.last())
                }
                assertReadOnly(harness)
            }
        }
    }

    @Test
    fun lateHistoryCannotCrossBackChildOwnerRevocationOrRemoval() {
        LateBoundary.entries.forEach { boundary ->
            HistoryHarness().use { harness ->
                val gate = CompletableDeferred<Unit>()
                var returned = false
                harness.historyRead = { parent, child ->
                    withContext(NonCancellable) {
                        gate.await()
                        returned = true
                        harness.available(parent, child, history("LATE-FOREIGN", child, false))
                    }
                }
                runSkikoComposeUiTest(size = Size(640f, 800f)) {
                    mount(harness); selectChild(harness, CHILD_A, ready = false)
                    onNodeWithText("正在加载子智能体对话记录…").assertIsDisplayed()
                    runOnIdle { harness.historyRead = { parent, child -> harness.available(parent, child) } }
                    when (boundary) {
                        LateBoundary.Back -> pointerTag("返回子智能体")
                        LateBoundary.Child -> { pointerTag("返回子智能体"); selectChild(harness, CHILD_B) }
                        LateBoundary.Owner -> {
                            runOnIdle { harness.rebind(harness.owner.copy(host = harness.owner.host.copy(generation = 2)), "new-owner") }
                            settle(); selectChild(harness, CHILD_A)
                        }
                        LateBoundary.Revocation -> {
                            runOnIdle { harness.historyRead = { parent, child -> ChildHistoryResult.Unavailable(parent, child) } }
                            // Service invalidation is an owner event, not a synthetic selection.
                            runOnIdle { harness.controller.refreshDetail() }
                            settle(); onNodeWithText("对话记录不可用").assertIsDisplayed()
                        }
                        LateBoundary.Removal -> {
                            runOnIdle { harness.children = listOf(CHILD_B); harness.controller.refreshOverview() }
                            settle(); runOnIdle { assertNull(harness.current.selectedChild) }
                        }
                    }
                    val before = harness.current
                    runOnIdle { gate.complete(Unit) }
                    settle()
                    runOnIdle {
                        assertTrue(returned, "The non-cooperative service must really return: $boundary")
                        assertEquals(before, harness.current, "Late history must not publish across $boundary")
                    }
                    onNodeWithText("LATE-FOREIGN/child-a human", useUnmergedTree = true).assertDoesNotExist()
                    when (boundary) {
                        LateBoundary.Back, LateBoundary.Removal -> onNodeWithTag(TRANSCRIPT, true).assertDoesNotExist()
                        LateBoundary.Revocation -> onNodeWithText("对话记录不可用").assertIsDisplayed()
                        LateBoundary.Child -> completeText("message:human:content:0", harness.human(CHILD_B))
                        LateBoundary.Owner -> completeText("message:human:content:0", harness.human(CHILD_A))
                    }
                    assertReadOnly(harness)
                }
            }
        }
    }

    @Test
    fun legacyNullAndExplicitTypedEmptyKeepDistinctBranches() = runSkikoComposeUiTest(size = Size(640f, 800f)) {
        val scope = ThreadPanelScope(HostId("legacy-host"), SessionId("legacy-parent"))
        val key = ChildAgentKey(scope, CHILD_A)
        val summary = ChildAgentSummary(key, listOf(scope.conversationId, CHILD_A), ChildAgentStatus.Active,
            canInteract = false, historyAvailability = ChildHistoryAvailability.Available)
        val detail = ChildAgentDetail(key, listOf(ChildTranscriptEntry("legacy", ChildTranscriptRole.Tool,
            "Legacy raw content without invented typed source")), false)
        var state by mutableStateOf(ThreadPanelsState(scope, openTabs = listOf(ThreadPanelTab.Subagents),
            selectedTab = ThreadPanelTab.Subagents, subagents = PanelLoad.Ready(scope, SubagentOverview(scope, listOf(summary))),
            selectedChild = key, detail = PanelLoad.Ready(scope, detail)))
        setContent { HarnessTheme { ThreadPanels(state, { state = state.reduce(it) },
            childInteraction = { HarnessText("FORBIDDEN COMPOSER") }) } }
        settle()
        assertNull(detail.typedMessages)
        onNodeWithText("Legacy raw content without invented typed source", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithText("工具", useUnmergedTree = true).assertExists()
        onAllNodes(SemanticsMatcher("Typed cards") {
            it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("message:") == true
        }, true).assertCountEquals(0)
        runOnIdle { state = state.copy(detail = PanelLoad.Ready(scope, detail.copy(entries = emptyList(), typedMessages = emptyList()))) }
        settle()
        onNodeWithText("没有消息").assertIsDisplayed()
        onNodeWithText("Legacy raw content without invented typed source", useUnmergedTree = true).assertDoesNotExist()
        onNodeWithText("FORBIDDEN COMPOSER").assertDoesNotExist()
        onAllNodes(SemanticsMatcher("No editable history") { it.config.contains(SemanticsActions.SetText) }, true).assertCountEquals(0)
        runOnIdle { assertNull(state.interactiveDetail) }
    }

    private fun captureMatrix(width: Int, height: Int, theme: HarnessThemeMode) {
        HistoryHarness(long = true).use { harness ->
            runSkikoComposeUiTest(size = Size(width.toFloat(), height.toFloat())) {
                mount(harness, theme); selectChild(harness, CHILD_A)
                disclosure(REASONING, false); disclosure(CALL, false)
                completeText(NESTED_TEXT, harness.raw(CHILD_A), wrapped = true)
                wheelToEdge(end = true)
                val collapsedEnd = endProof(harness.marker(CHILD_A))
                wheelToEdge(end = false)
                assertEquals(0f, range().first)
                capture("collapsed", width, height, theme, harness, collapsedEnd, markerVisible = false)
                pointerTag("$REASONING:header")
                wheelUntilVisible("$CALL:header"); pointerTag("$CALL:header")
                disclosure(REASONING, true); disclosure(CALL, true)
                completeText("$REASONING:body", REASONING_TEXT, wrapped = true)
                completeText("$CALL:body", ARGUMENTS, wrapped = true)
                wheelToEdge(end = true)
                val expandedEnd = endProof(harness.marker(CHILD_A))
                capture("expanded-tail", width, height, theme, harness, expandedEnd, markerVisible = true)
                assertReadOnly(harness)
            }
        }
    }

    private fun ComposeUiTest.mount(harness: HistoryHarness, theme: HarnessThemeMode = HarnessThemeMode.Light) {
        setContent {
            val state by harness.controller.state.collectAsState()
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) { HarnessTheme(theme) {
                Box(Modifier.fillMaxSize().background(HarnessTokens.Colors.Canvas).semantics { testTag = FIXTURE }) {
                    state?.let { ThreadPanels(it, harness.controller::dispatch,
                        childInteraction = { HarnessText("FORBIDDEN COMPOSER") },
                        onRefreshOverview = { harness.controller.refreshOverview() },
                        onRefreshDetail = { harness.controller.refreshDetail() }) }
                }
            } }
        }
        settle()
    }

    private fun ComposeUiTest.selectChild(harness: HistoryHarness, child: SessionId, ready: Boolean = true) {
        pointer(onNode(hasClickAction() and hasText(child.value)))
        runOnIdle {
            assertEquals(ChildAgentKey(harness.owner.scope, child), harness.current.selectedChild)
            assertEquals(harness.owner.parentSessionId to child, harness.requests.last())
            assertEquals(harness.owner.parentSessionId, harness.overviewRequests.last())
            if (ready) assertTrue(harness.current.detail is PanelLoad.Ready)
        }
    }

    private fun ComposeUiTest.assertReadOnly(harness: HistoryHarness) {
        if (harness.current.selectedChild != null) {
            onNodeWithText("只读对话记录").assertIsDisplayed()
        } else {
            onNodeWithText("只读对话记录").assertDoesNotExist()
        }
        onNodeWithText("FORBIDDEN COMPOSER").assertDoesNotExist()
        onAllNodes(SemanticsMatcher("Editable child transcript") { it.config.contains(SemanticsActions.SetText) }, true).assertCountEquals(0)
        runOnIdle {
            assertNull(harness.current.interactiveDetail)
            assertTrue((harness.current.subagents as PanelLoad.Ready).value.children.all { !it.canInteract })
            (harness.current.detail as? PanelLoad.Ready)?.let { assertFalse(it.value.canInteract) }
            assertEquals(0, harness.mutations)
            assertEquals(0, harness.unrestrictedReads)
        }
    }

    private fun ComposeUiTest.settle() { mainClock.advanceTimeByFrame(); waitForIdle() }
    private fun ComposeUiTest.node(tag: String): SemanticsNode = onNodeWithTag(tag, true).fetchSemanticsNode()
    private fun fullRect(node: SemanticsNode) = Rect(node.positionInRoot, Size(node.size.width.toFloat(), node.size.height.toFloat()))
    private fun ComposeUiTest.layout(tag: String) = fullRect(node(tag))
    private fun ComposeUiTest.rootFor(node: SemanticsNode): SemanticsNodeInteraction {
        val root = generateSequence(node) { it.parent }.last()
        return onNode(SemanticsMatcher("Actual history root " + root.id) { it.id == root.id }, true)
    }
    private fun ComposeUiTest.rootFor(tag: String) = rootFor(node(tag))

    private fun ComposeUiTest.pointerTag(tag: String) {
        val target = onNodeWithTag(tag, true)
        target.assertIsEnabled()
        pointer(target)
    }

    private fun ComposeUiTest.pointer(target: SemanticsNodeInteraction) {
        val started = System.nanoTime()
        settle(); target.assertIsDisplayed()
        var root = rootFor(target.fetchSemanticsNode())
        root.performMouseInput { moveTo(target.fetchSemanticsNode().boundsInRoot.center - root.fetchSemanticsNode().boundsInRoot.topLeft) }
        settle(); target.assertIsDisplayed()
        val measured = target.fetchSemanticsNode()
        root = rootFor(measured)
        inside(fullRect(measured), measured.boundsInRoot, "Full measured pointer target")
        val point = measured.boundsInRoot.center - root.fetchSemanticsNode().boundsInRoot.topLeft
        root.performMouseInput { moveTo(point); advanceEventTime(16); click(point) }
        settle()
        assertTrue(System.nanoTime() - started < DEADLINE, "Pointer input deadline")
    }

    private fun ComposeUiTest.keyFromRoot(key: Key) {
        val started = System.nanoTime()
        rootFor(FIXTURE).performKeyInput { pressKey(key) }
        settle()
        assertTrue(System.nanoTime() - started < DEADLINE, "Keyboard input deadline")
    }

    private fun ComposeUiTest.focusByTab(tag: String) {
        val started = System.nanoTime()
        while (node(tag).config.getOrNull(SemanticsProperties.Focused) != true) {
            assertTrue(System.nanoTime() - started < DEADLINE, "Real Tab must reach $tag")
            keyFromRoot(Key.Tab)
        }
        onNodeWithTag(tag, true).assertIsFocused()
    }

    private fun ComposeUiTest.disclosure(tag: String, expanded: Boolean) {
        val header = node("$tag:header")
        assertEquals(Role.Button, header.config[SemanticsProperties.Role])
        assertEquals(if (expanded) "Expanded" else "Collapsed", header.config[SemanticsProperties.StateDescription])
        assertTrue(header.config.contains(SemanticsActions.OnClick))
        if (expanded) onNodeWithTag("$tag:body", true).assertExists()
        else onNodeWithTag("$tag:body", true).assertDoesNotExist()
    }

    private fun ComposeUiTest.range(): Pair<Float, Float> {
        val axis = node(TRANSCRIPT).config[SemanticsProperties.VerticalScrollAxisRange]
        return axis.value() to axis.maxValue() // Numeric snapshots; the semantic lambdas are live.
    }

    // Matches the verified Thread/Plugins wheel unit conversion and actual owning-root helper.
    private fun ComposeUiTest.wheelPixels(pixels: Float) {
        require(pixels.isFinite() && pixels != 0f)
        val started = System.nanoTime()
        settle()
        var list = node(TRANSCRIPT)
        var root = rootFor(TRANSCRIPT)
        fun point(): Offset {
            val viewport = list.boundsInRoot.intersect(root.fetchSemanticsNode().boundsInRoot)
            assertTrue(viewport.width > 0f && viewport.height > 0f)
            return Offset(viewport.left + minOf(4f, viewport.width / 2f), viewport.center.y) - root.fetchSemanticsNode().boundsInRoot.topLeft
        }
        root.performMouseInput { moveTo(point()) }
        settle(); list = node(TRANSCRIPT); root = rootFor(TRANSCRIPT)
        val units = when (val os = System.getProperty("os.name").orEmpty()) {
            "Mac OS X" -> 10f * list.layoutInfo.density.density
            else -> if (os.startsWith("Linux")) sqrt(list.size.height.toFloat()) else list.size.height / 20f
        }
        assertTrue(units > 0f)
        val measuredPoint = point()
        root.performMouseInput { moveTo(measuredPoint); advanceEventTime(16); scroll(pixels / units) }
        settle()
        assertTrue(System.nanoTime() - started < DEADLINE, "Wheel step deadline")
    }

    private fun ComposeUiTest.wheelToEdge(end: Boolean) {
        val started = System.nanoTime()
        while (true) {
            settle()
            val (value, max) = range()
            assertTrue(max.isFinite() && max >= 0f)
            val remaining = if (end) max - value else -value
            if (abs(remaining) <= .5f) break
            assertTrue(System.nanoTime() - started < DEADLINE, "Real wheel must reach the requested edge")
            val step = minOf(abs(remaining), node(TRANSCRIPT).boundsInRoot.height * .6f)
            wheelPixels(if (end) step else -step)
        }
        val (value, max) = range()
        assertNear(if (end) max else 0f, value, "Measured wheel edge", .5f)
    }

    private fun ComposeUiTest.wheelUntilVisible(tag: String) {
        val started = System.nanoTime()
        while (true) {
            settle()
            val viewport = node(TRANSCRIPT).boundsInRoot
            val bounds = layout(tag)
            if (bounds.top >= viewport.top - 1f && bounds.bottom <= viewport.bottom + 1f) break
            assertTrue(System.nanoTime() - started < DEADLINE, "Real wheel must reveal $tag")
            val distance = if (bounds.top < viewport.top) bounds.top - viewport.top else bounds.bottom - viewport.bottom
            val step = minOf(abs(distance), viewport.height * .6f).coerceAtLeast(1f)
            wheelPixels(if (distance < 0f) -step else step)
        }
        onNodeWithTag(tag, true).assertIsDisplayed()
    }

    private fun ComposeUiTest.textLayout(tag: String, expected: String): TextLayoutResult {
        onNodeWithTag(tag, true).assertTextEquals(expected)
        val results = mutableListOf<TextLayoutResult>()
        // A read-only measurement query, never an input substitute.
        onNodeWithTag(tag, true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single { it.layoutInput.text.text == expected }
    }

    private fun ComposeUiTest.completeText(tag: String, expected: String, wrapped: Boolean = false): JsonObject {
        val text = textLayout(tag, expected)
        val bounds = layout(tag)
        val viewport = node(TRANSCRIPT).boundsInRoot
        assertEquals(expected, text.layoutInput.text.text)
        assertEquals(expected.length, text.getLineEnd(text.lineCount - 1), "Actual text end laid out")
        assertTrue((0 until text.lineCount).none { text.isLineEllipsized(it) }, "No ellipsis: $tag")
        if (wrapped) assertTrue(text.lineCount > 1, "Measured multi-line content: $tag")
        assertEquals(text.size.width.toFloat(), bounds.width)
        assertEquals(text.size.height.toFloat(), bounds.height)
        val local = Rect(Offset.Zero, bounds.size)
        val lines = (0 until text.lineCount).map { line ->
            Rect(text.getLineLeft(line), text.getLineTop(line), text.getLineRight(line), text.getLineBottom(line))
        }
        lines.forEach { inside(it, local, "Laid out line within text") }
        expected.indices.filter { !expected[it].isWhitespace() }.forEach { offset ->
            val glyph = text.getBoundingBox(offset)
            assertTrue(glyph.left >= -1f && glyph.right <= bounds.width + 1f, "Full horizontal glyph $offset")
        }
        assertTrue(bounds.left >= viewport.left - 1f && bounds.right <= viewport.right + 1f)
        return buildJsonObject {
            put("tag", tag); put("fullText", expected); put("expectedLength", expected.length)
            put("lastLineEnd", text.getLineEnd(text.lineCount - 1)); put("lineCount", text.lineCount)
            put("maxLines", text.layoutInput.maxLines); put("overflow", text.layoutInput.overflow.toString())
            put("noEllipsis", true); put("originInRoot", bounds.topLeft.toString()); put("nodeBounds", bounds.toString())
            put("viewport", viewport.toString()); put("extraScrollSubtraction", 0)
            putJsonArray("linesLocal") { lines.forEach { add(JsonPrimitive(it.toString())) } }
        }
    }

    private fun ComposeUiTest.endProof(marker: String): JsonObject {
        val geometry = completeText(MARKER_TAG, marker)
        val text = textLayout(MARKER_TAG, marker)
        val bounds = layout(MARKER_TAG)
        val root = rootFor(TRANSCRIPT).fetchSemanticsNode()
        val viewport = node(TRANSCRIPT).boundsInRoot.intersect(root.boundsInRoot)
        val line = text.lineCount - 1
        val localEnd = Rect(text.getLineLeft(line), text.getLineTop(line), text.getLineRight(line), text.getLineBottom(line))
        // This tag is on the inner Text. Its origin already includes vertical scroll translation.
        val endInRoot = Rect(localEnd.topLeft + bounds.topLeft, localEnd.size)
        inside(bounds, viewport, "Final marker full measured bounds must be visible")
        inside(endInRoot, viewport, "Actual laid-out text end must be visible")
        val (value, max) = range()
        assertTrue(max > 0f)
        assertNear(max, value, "Actual tail reached", .5f)
        return buildJsonObject {
            put("text", geometry); put("lineEndLocal", localEnd.toString()); put("lineEndInRoot", endInRoot.toString())
            put("originInRoot", bounds.topLeft.toString()); put("extraScrollSubtraction", 0)
            put("markerBounds", bounds.toString()); put("markerClippedBounds", node(MARKER_TAG).boundsInRoot.toString())
            put("viewport", viewport.toString()); put("value", value); put("max", max); put("fullyVisible", true)
        }
    }

    private fun ComposeUiTest.capture(stage: String, width: Int, height: Int, theme: HarnessThemeMode,
        harness: HistoryHarness, priorEndProof: JsonObject, markerVisible: Boolean) {
        settle()
        val fixture = node(FIXTURE)
        val root = generateSequence(fixture) { it.parent }.last()
        assertEquals(width, root.size.width); assertEquals(height, root.size.height)
        assertEquals(width, fixture.size.width); assertEquals(height, fixture.size.height)
        inside(fullRect(fixture), root.boundsInRoot, "Actual fixture root")
        disclosure(REASONING, markerVisible); disclosure(CALL, markerVisible)
        val text = mutableListOf(completeText("message:human:content:0", harness.human(CHILD_A)),
            completeText(NESTED_TEXT, harness.raw(CHILD_A), wrapped = true),
            completeText(MARKER_TAG, harness.marker(CHILD_A)))
        if (markerVisible) {
            text += completeText("$REASONING:body", REASONING_TEXT, wrapped = true)
            text += completeText("$CALL:body", ARGUMENTS, wrapped = true)
            endProof(harness.marker(CHILD_A))
        } else {
            assertEquals(0f, range().first)
            assertTrue(layout(MARKER_TAG).top > node(TRANSCRIPT).boundsInRoot.bottom,
                "Collapsed capture is at the top; prior tail proof is explicitly separate")
        }
        val nodes = onAllNodes(SemanticsMatcher("Tagged production history") {
            it.config.contains(SemanticsProperties.TestTag)
        }, true).fetchSemanticsNodes().filter { generateSequence(it) { parent -> parent.parent }.last().id == root.id }
        val viewport = node(TRANSCRIPT).boundsInRoot
        nodes.filter { it.config[SemanticsProperties.TestTag].startsWith("message:") }.forEach {
            val bounds = fullRect(it)
            assertTrue(bounds.left >= viewport.left - 1f && bounds.right <= viewport.right + 1f)
            if (it.boundsInRoot.width > 0f && it.boundsInRoot.height > 0f) inside(it.boundsInRoot, viewport, "Actual clipped visible history")
        }
        val bytes = Image.makeFromBitmap(rootFor(FIXTURE).captureToImage().asSkiaBitmap()).use { image ->
            image.encodeToData(EncodedImageFormat.PNG)?.use { it.bytes } ?: error("PNG encoding failed")
        }
        val directory = File(System.getenv("HARNESS_SUBAGENT_HISTORY_CAPTURE_DIR")?.takeIf { it.isNotBlank() }
            ?: "/tmp/harness-subagent-history-captures-20261008")
        assertTrue(directory.isDirectory || directory.mkdirs(), "Capture directory creation")
        val name = "subagent-history-" + width + "x" + height + "-" + theme.name.lowercase() + "-" + stage
        val png = File(directory, "$name.png").also { it.writeBytes(bytes) }
        val decoded = assertNotNull(ImageIO.read(png))
        assertEquals(width, decoded.width); assertEquals(height, decoded.height)
        val (value, max) = range()
        val geometry = buildJsonObject {
            put("schema", "harness.subagent-history.capture.v1"); put("stage", stage); put("image", png.name)
            put("width", width); put("height", height); put("theme", theme.name); put("density", 1f); put("fontScale", 1f)
            put("sha256", MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
            put("captureRootId", root.id); put("fixtureId", fixture.id); put("rootBounds", root.boundsInRoot.toString())
            put("viewport", viewport.toString()); put("scrollValue", value); put("scrollMax", max)
            put("markerVisibleInCapture", markerVisible)
            put("tailProofStage", if (markerVisible) "at capture" else "before returning to top for capture")
            put("tailProof", priorEndProof)
            putJsonArray("textLayout") { text.forEach { add(it) } }
            putJsonArray("nodes") { nodes.forEach { measured -> add(buildJsonObject {
                put("tag", measured.config[SemanticsProperties.TestTag]); put("id", measured.id)
                put("fullBounds", fullRect(measured).toString()); put("clippedBounds", measured.boundsInRoot.toString())
                put("originInRoot", measured.positionInRoot.toString())
                measured.config.getOrNull(SemanticsProperties.StateDescription)?.let { put("stateDescription", it) }
            }) } }
            put("evidenceLimit", "Offscreen Skiko production panel with real controller/repository/test SessionApi; no native or original pixel-parity claim")
        }
        File(directory, "$name.geometry.json").writeText(Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), geometry))
    }

    private fun inside(actual: Rect, expected: Rect, message: String) {
        assertTrue(actual.left >= expected.left - 1f && actual.top >= expected.top - 1f &&
            actual.right <= expected.right + 1f && actual.bottom <= expected.bottom + 1f,
            "$message: $actual / $expected")
    }
    private fun assertNear(expected: Float, actual: Float, message: String, tolerance: Float = 1f) {
        assertTrue(abs(expected - actual) <= tolerance, "$message: $actual / $expected")
    }

    private enum class LateBoundary { Back, Child, Owner, Revocation, Removal }

    private class HistoryHarness(val long: Boolean = false) : AutoCloseable {
        private val work = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val requests = mutableListOf<Pair<SessionId, SessionId>>()
        val overviewRequests = mutableListOf<SessionId>()
        var children = listOf(CHILD_A, CHILD_B)
        var prefix = "owner-1"
        var mutations = 0
        var unrestrictedReads = 0
        var historyRead: suspend (SessionId, SessionId) -> ChildHistoryResult = { parent, child -> available(parent, child) }
        private val connection = object : Connection {
            override val host: HostApi = object : HostApi {
                override suspend fun describe() = error("Parent already verifies Host identity")
            }
            override val session: SessionApi = object : SessionApi {
                override suspend fun descendants(parent: SessionId): SessionDescendantsResult {
                    overviewRequests += parent
                    return SessionDescendantsResult.Available(parent, children.map { child ->
                        SessionDescendant(child, parent, listOf(parent, child), DescendantStatus.RUNNING,
                            provider = "fixture-provider", model = "fixture-model", reasoningEffort = "high",
                            objective = objective(child))
                    })
                }
                override suspend fun readChildHistory(parent: SessionId, child: SessionId): ChildHistoryResult {
                    requests += parent to child
                    return historyRead(parent, child)
                }
                override suspend fun list(): List<SessionSummary> { unrestrictedReads++; error("No unrelated sessions") }
                override suspend fun history(id: SessionId): List<Message> { unrestrictedReads++; error("No unrestricted history") }
                override suspend fun create(id: SessionId?, options: CreateSessionOptions, agentOptions: AgentOptions): SessionSummary {
                    mutations++; error("No child creation")
                }
                override suspend fun prompt(id: SessionId, message: Message): SessionPrompt { mutations++; error("Read-only child") }
                override suspend fun cancel(id: SessionId, keepInbox: Boolean) { mutations++; error("Read-only child") }
            }
        }
        var owner = ThreadPanelsOwner(connection, ConversationHostScope(HostId("history-host"), 1, null), SessionId("history-parent"))
            private set
        val controller = ThreadPanelsController(work, ConnectionThreadPanelsRepository())
        val current: ThreadPanelsState get() = assertNotNull(controller.state.value)
        init { controller.bind(owner); controller.dispatch(ThreadPanelAction.OpenTab(owner.scope, ThreadPanelTab.Subagents)) }
        fun rebind(next: ThreadPanelsOwner, nextPrefix: String) {
            owner = next; prefix = nextPrefix
            controller.bind(owner); controller.dispatch(ThreadPanelAction.OpenTab(owner.scope, ThreadPanelTab.Subagents))
        }
        fun human(child: SessionId) = prefix + "/" + child.value + " human"
        fun objective(child: SessionId) = "Delegated " + child.value + " task from " + prefix
        fun raw(child: SessionId) = raw(prefix, child, long)
        fun marker(child: SessionId) = prefix + "/" + child.value + " END"
        fun available(parent: SessionId, child: SessionId, messages: List<Message> = history(prefix, child, long)) =
            ChildHistoryResult.Available(parent, child, listOf(parent, child), messages)
        override fun close() { controller.close(); work.cancel() }
    }

    private companion object {
        const val DEADLINE = 10_000_000_000L
        const val FIXTURE = "subagent-history-fixture"
        const val TRANSCRIPT = "subagent-transcript"
        const val REASONING = "message:assistant:content:0"
        const val CALL = "message:assistant:content:1"
        const val NESTED_TEXT = "message:nested:content:0:content:1:content:0"
        const val MARKER_TAG = "message:nested:content:0:content:1:content:1"
        const val REASONING_TEXT = "Typed reasoning remains verbatim.\nThe second line is independently readable."
        const val ARGUMENTS = "{\n  \"path\": \"fixture/source.kt\",\n  \"readOnly\": true\n}"
        val CHILD_A = SessionId("child-a")
        val CHILD_B = SessionId("child-b")
        fun raw(prefix: String, child: SessionId, long: Boolean) = if (long) (1..28).joinToString("\n") {
            prefix + "/" + child.value + " raw nested line $it — complete wrapped content with no ellipsis"
        } else "Nested raw error"
        fun history(prefix: String, child: SessionId, long: Boolean) = listOf(
            Message("human", MessageRole.USER, listOf(TextContent(prefix + "/" + child.value + " human")), UserMessageSource),
            Message("assistant", MessageRole.ASSISTANT, listOf(ReasoningContent(REASONING_TEXT),
                ToolCallContent("actual-call", "read_child_file", ARGUMENTS)), ModelMessageSource("fixture-provider", "fixture-model")),
            Message("tool", MessageRole.USER, listOf(TextContent("Tool source with USER role")), ToolMessageSource("actual-call")),
            Message("plugin", MessageRole.USER, listOf(TextContent("Plugin source with USER role")), PluginMessageSource("fixture-plugin")),
            Message("nested", MessageRole.USER, listOf(ToolResultContent("actual-call", listOf(TextContent("Outer raw result"),
                ToolResultContent("nested-unmatched", listOf(TextContent(raw(prefix, child, long)),
                    TextContent(prefix + "/" + child.value + " END")), isError = true)))), ToolMessageSource("actual-call")),
        )
    }
}
