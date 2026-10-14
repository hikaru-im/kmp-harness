@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package im.hikaru.harness.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ScrollAxisRange
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
import androidx.compose.ui.unit.dp
import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.harness.api.gateway.ApiGateway
import im.hikaru.harness.client.connection.BackgroundProcessLifecycle
import im.hikaru.harness.client.connection.SessionId
import im.hikaru.harness.client.conversation.ConversationHostScope
import im.hikaru.harness.client.panels.*
import im.hikaru.harness.desktop.connection.LocalConnection
import im.hikaru.harness.ui.foundation.HarnessTheme
import im.hikaru.harness.ui.foundation.HarnessThemeMode
import im.hikaru.harness.ui.foundation.HarnessTokens
import java.io.File
import java.security.MessageDigest
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.*
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image

/**
 * Real production ThreadPanels driven by the real ThreadPanelsController and a repository whose
 * reads can be delayed. Output, identities and positions are typed; no network is contacted.
 * Every movement is a real pointer/wheel/keyboard event located from measured node geometry.
 */
class TerminalReadingInteractionTest {
    @Test
    fun firstLongOutputShowsTheTailThenUpwardScrollSurvivesAppendAndBottomFollowsAgain() {
        val repository = TerminalReadingRepository(TERMINAL_SCOPE)
        val first = terminalOutput(revision = 1, lines = 90)
        repository.rows = listOf(terminalSummary(first))
        repository.current = first
        val harness = TerminalHarness(repository)
        try {
            runSkikoComposeUiTest(size = Size(640f, 800f)) {
                mountTerminalPanels(harness.controller)
                awaitTerminalSettled()
                val tail = verticalAxis()
                assertTrue(tail.maxValue() > 0f, "Long output must overflow the viewport")
                assertTrue(tail.value() >= tail.maxValue() - 1f, "First non-empty display shows the tail")
                assertEquals(0f, horizontalAxis().value(), "Horizontal reading starts at 0")

                wheelVerticalUp()
                awaitTerminalSettled()
                val reading = verticalAxis().value()
                assertTrue(reading < verticalAxis().maxValue() - 1f, "A real upward wheel leaves the bottom")

                runOnIdle {
                    repository.current = appended(repository.current!!, revision = 2, lines = 30, tail = "TAIL-AFTER-APPEND")
                    harness.controller.refreshTerminal()
                }
                awaitTerminalSettled()
                assertNear(reading, verticalAxis().value(), "Upward reader keeps its pixel offset", 1f)
                onNodeWithText("TAIL-AFTER-APPEND", substring = true).assertExists()

                wheelVerticalToBottom()
                assertTrue(verticalAxis().value() >= verticalAxis().maxValue() - 1f, "Wheel must reach the tail")
                runOnIdle {
                    repository.current = appended(repository.current!!, revision = 3, lines = 30, tail = "TAIL-AFTER-SECOND-APPEND")
                    harness.controller.refreshTerminal()
                }
                awaitTerminalSettled()
                val followed = verticalAxis()
                assertTrue(followed.value() >= followed.maxValue() - 1f, "Bottom reader follows the new tail")
                onNodeWithText("TAIL-AFTER-SECOND-APPEND", substring = true).assertExists()
            }
        } finally {
            harness.close()
        }
    }

    @Test
    fun sameKeyLoadingErrorRetryPreservesBothAxesAndIdenticalBufferRevisionDoesNotSteal() {
        val repository = TerminalReadingRepository(TERMINAL_SCOPE)
        val wide = terminalOutput(revision = 1, lines = 90, wide = true)
        repository.rows = listOf(terminalSummary(wide))
        repository.current = wide
        val harness = TerminalHarness(repository)
        try {
            runSkikoComposeUiTest(size = Size(640f, 800f)) {
                mountTerminalPanels(harness.controller)
                awaitTerminalSettled()
                wheelVerticalUp()
                awaitTerminalSettled()
                terminalWheel("terminal-output-text", 140f, horizontal = true)
                awaitTerminalSettled()
                val vertical = verticalAxis().value()
                val horizontal = horizontalAxis().value()
                assertTrue(vertical < verticalAxis().maxValue() - 1f, "Fixture must be read above the bottom")
                assertTrue(horizontal > 0f, "Fixture must be read right of the origin")

                // The Ready branch unmounts during Loading; the hoisted reading state must survive.
                val gate = CompletableDeferred<Unit>()
                runOnIdle {
                    repository.gate = gate
                    harness.controller.refreshTerminal()
                }
                settle()
                onNodeWithText("正在加载输出…").assertIsDisplayed()
                runOnIdle {
                    repository.gate = null
                    gate.complete(Unit)
                }
                awaitTerminalSettled()
                assertNear(vertical, verticalAxis().value(), "Loading keeps the vertical position", 1f)
                assertNear(horizontal, horizontalAxis().value(), "Loading keeps the horizontal position", 1f)

                // An Error is explicit and is recovered through the real Retry output control.
                runOnIdle {
                    repository.failure = "后台输出读取失败"
                    harness.controller.refreshTerminal()
                }
                settle()
                onNodeWithText("无法加载输出").assertIsDisplayed()
                onNodeWithText("暂无输出").assertDoesNotExist()
                runOnIdle { repository.failure = null }
                terminalClick("重试输出")
                awaitTerminalSettled()
                assertNear(vertical, verticalAxis().value(), "Retry keeps the vertical position", 1f)
                assertNear(horizontal, horizontalAxis().value(), "Retry keeps the horizontal position", 1f)

                // A revision bump with an identical buffer must not move the reader.
                runOnIdle {
                    repository.current = wide.copy(revision = wide.revision + 1)
                    harness.controller.refreshTerminal()
                }
                awaitTerminalSettled()
                assertNear(vertical, verticalAxis().value(), "Identical buffer revision does not steal the position", 1f)
                assertNear(horizontal, horizontalAxis().value(), "Identical buffer revision keeps the axis", 1f)
            }
        } finally {
            harness.close()
        }
    }

    @Test
    fun realHorizontalWheelReachesTheEndMarkerAndFullTextIsNotOmitted() {
        val repository = TerminalReadingRepository(TERMINAL_SCOPE)
        val wide = terminalOutput(lines = 40, wide = true)
        repository.rows = listOf(terminalSummary(wide))
        repository.current = wide
        val harness = TerminalHarness(repository)
        try {
            runSkikoComposeUiTest(size = Size(640f, 800f)) {
                mountTerminalPanels(harness.controller)
                awaitTerminalSettled()
                assertTrue(wide.buffer.endsWith(WIDE_MARKER), "The fixture must end with the wide marker")
                // The whole raw buffer is one laid-out text node: no truncation and no ellipsis.
                val layouts = mutableListOf<TextLayoutResult>()
                onNodeWithTag("terminal-output-text", useUnmergedTree = true)
                    .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertTrue(layouts.isNotEmpty(), "The reader must expose its text layout")
                val layout = layouts.first()
                assertEquals(wide.buffer, layout.layoutInput.text.text, "The buffer must be laid out in full")
                val lastLine = layout.lineCount - 1
                assertTrue((0 until layout.lineCount).none { layout.isLineEllipsized(it) }, "No line may be ellipsized")
                assertEquals(wide.buffer.length, layout.getLineEnd(lastLine), "The final character must be laid out")
                val markerRight = layout.getLineRight(lastLine)
                assertTrue(markerRight > 0f, "The wide marker must have measurable geometry")

                val start = horizontalAxis()
                val viewport = onNodeWithTag("terminal-output-text", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot.width
                assertTrue(start.maxValue() > 0f, "The long line must overflow horizontally")
                assertEquals(0f, start.value(), "Horizontal reading starts at the origin")
                assertTrue(viewport < markerRight - 1f, "The marker must start outside the viewport: " + markerRight)

                wheelHorizontalToEnd()
                val end = horizontalAxis()
                assertTrue(abs(end.maxValue() - end.value()) <= 1f,
                    "Real horizontal wheel must reach the laid-out end: value=" + end.value() + " max=" + end.maxValue())
                assertTrue(end.value() + viewport >= markerRight - 1f,
                    "Marker right edge must be inside the visible window: marker=" + markerRight +
                        " windowRight=" + (end.value() + viewport))
            }
        } finally {
            harness.close()
        }
    }

    @Test
    fun nonPrefixReplacementClampsAndReadyEmptyDiffersFromUnavailable() {
        val repository = TerminalReadingRepository(TERMINAL_SCOPE)
        val long = terminalOutput(revision = 1, lines = 90)
        repository.rows = listOf(terminalSummary(long))
        repository.current = long
        val harness = TerminalHarness(repository)
        try {
            runSkikoComposeUiTest(size = Size(640f, 800f)) {
                mountTerminalPanels(harness.controller)
                awaitTerminalSettled()
                wheelVerticalUp()
                awaitTerminalSettled()
                val before = verticalAxis().value()
                assertTrue(before > 0f, "Fixture must be read above the top")

                val replacement = BackgroundTerminalOutput(long.key, "REPLACEMENT-ONLY\nSHORT-TAIL", 2,
                    command = long.command, truncated = true, lifecycle = long.lifecycle, cwd = long.cwd)
                runOnIdle {
                    repository.current = replacement
                    harness.controller.refreshTerminal()
                }
                awaitTerminalSettled()
                val clamped = verticalAxis()
                assertTrue(clamped.value() <= clamped.maxValue() + 1f, "Position must clamp into the new range")
                assertTrue(clamped.value() <= before + 1f, "An upward reader must not advance past its offset")
                assertNear(before.coerceAtMost(clamped.maxValue()), clamped.value(), "Truncation clamps the old offset", 1f)
                onNodeWithText("输出缓冲区已被来源截断").assertIsDisplayed()
                assertEquals(replacement.buffer, terminalTextLayout().layoutInput.text.text)
                runOnIdle { assertTrue((harness.controller.state.value!!.terminal as PanelLoad.Ready).value.truncated) }
                assertTrue(onAllNodesWithText("SHORT-TAIL", substring = true).fetchSemanticsNodes().isNotEmpty(),
                    "The new complete buffer is shown")
                assertTrue(onAllNodesWithText(TAIL_MARKER, substring = true).fetchSemanticsNodes().isEmpty(),
                    "The replaced buffer is gone")

                // A real empty buffer is still Ready and shows the explicit empty state.
                runOnIdle {
                    repository.current = long.copy(buffer = "", revision = 3)
                    harness.controller.refreshTerminal()
                }
                settle()
                onNodeWithText("暂无输出").assertIsDisplayed()
                runOnIdle { assertTrue(harness.controller.state.value!!.terminal is PanelLoad.Ready) }

                // Unavailable is a capability state, never an empty success.
                runOnIdle {
                    repository.unavailable = true
                    repository.current = null
                    repository.rows = emptyList()
                    harness.controller.refreshTerminal()
                }
                settle()
                onNodeWithText("终端不可用").assertIsDisplayed()
                onNodeWithText("暂无输出").assertDoesNotExist()
                runOnIdle { assertTrue(harness.controller.state.value!!.terminal is PanelLoad.Unavailable) }
            }
        } finally {
            harness.close()
        }
    }

    @Test
    fun tabSwitchPreservesWhileSelectionIdentityChangeAndOwnerChangeReset() {
        val repository = TerminalReadingRepository(TERMINAL_SCOPE)
        val first = terminalOutput(lines = 90)
        repository.rows = listOf(terminalSummary(first))
        repository.current = first
        val harness = TerminalHarness(repository)
        try {
            runSkikoComposeUiTest(size = Size(640f, 800f)) {
                mountTerminalPanels(harness.controller)
                awaitTerminalSettled()
                wheelVerticalUp()
                awaitTerminalSettled()
                val preserved = verticalAxis().value()
                assertTrue(preserved < verticalAxis().maxValue() - 1f, "Fixture must be read above the bottom")

                // Switching to the Subagents tab and back keeps the same reading state.
                terminalClick("tab:Subagents")
                onNodeWithText("没有子智能体").assertIsDisplayed()
                terminalClick("tab:BackgroundOutput")
                awaitTerminalSettled()
                assertNear(preserved, verticalAxis().value(), "Subagents tab switch preserves the reader", 1f)

                // Closing BackgroundOutput releases its reader; the Subagents tab stays open.
                terminalClick("关闭后台终端")
                settle()
                onNodeWithTag("terminal-output", useUnmergedTree = true).assertDoesNotExist()
                onNodeWithText("没有子智能体").assertIsDisplayed()
                runOnIdle { assertTrue(ThreadPanelTab.BackgroundOutput !in harness.controller.state.value!!.openTabs) }
                runOnIdle { harness.controller.dispatch(ThreadPanelAction.OpenTab(TERMINAL_SCOPE, ThreadPanelTab.BackgroundOutput)) }
                awaitTerminalSettled()
                assertTrue(verticalAxis().value() >= verticalAxis().maxValue() - 1f, "Reopening is a first display at the tail")

                // A different process id: refresh the inventory, then select in the real dropdown.
                val second = terminalOutput(key = terminalKey(processId = "fixture-process-2"), lines = 90)
                runOnIdle {
                    repository.rows = listOf(terminalSummary(first), terminalSummary(second))
                    repository.current = first
                    harness.controller.refreshTerminal()
                }
                awaitTerminalSettled()
                wheelVerticalUp()
                awaitTerminalSettled()
                assertTrue(verticalAxis().value() < verticalAxis().maxValue() - 1f, "Fixture must be read above the bottom")
                runOnIdle { repository.current = second }
                terminalClick("terminal-process-select")
                terminalClick("menu:1")
                awaitTerminalSettled()
                runOnIdle { assertEquals(second.key, harness.controller.state.value!!.selectedTerminalKey) }
                assertTrue(verticalAxis().value() >= verticalAxis().maxValue() - 1f,
                    "A new process id is a new reading identity and starts at the tail")

                // The same process id with a new producer incarnation: the retained selection reports
                // a stale error, the inventory is refreshed, and the real dropdown picks the new key.
                wheelVerticalUp()
                awaitTerminalSettled()
                assertTrue(verticalAxis().value() < verticalAxis().maxValue() - 1f, "The old incarnation must be read above the tail")
                val incarnation = terminalOutput(key = terminalKey(processId = second.key.processId, incarnation = "incarnation-2"), lines = 90)
                assertEquals(second.key.processId, incarnation.key.processId)
                assertNotEquals(second.key.producerIncarnation, incarnation.key.producerIncarnation)
                runOnIdle {
                    repository.rows = listOf(terminalSummary(incarnation))
                    harness.controller.refreshTerminal()
                }
                settle()
                onNodeWithText("无法加载输出").assertIsDisplayed()
                runOnIdle {
                    val current = harness.controller.state.value!!
                    assertEquals(listOf(incarnation.key), current.terminalInventory.map { it.key })
                    assertEquals(second.key, current.selectedTerminalKey)
                    assertTrue(current.terminal is PanelLoad.Error)
                }
                runOnIdle { repository.current = incarnation }
                terminalClick("terminal-process-select")
                terminalClick("menu:0")
                awaitTerminalSettled()
                runOnIdle { assertEquals(incarnation.key, harness.controller.state.value!!.selectedTerminalKey) }
                assertTrue(verticalAxis().value() >= verticalAxis().maxValue() - 1f,
                    "A new producer incarnation is a new reading identity and starts at the tail")

                // An owner incarnation change closes every tab and shows a fresh first display.
                runOnIdle { harness.rebind(generation = 2) }
                settle()
                onNodeWithText("面板已关闭").assertIsDisplayed()
                runOnIdle {
                    assertTrue(harness.controller.state.value!!.ownerGeneration > 1)
                    harness.controller.dispatch(ThreadPanelAction.OpenTab(TERMINAL_SCOPE, ThreadPanelTab.BackgroundOutput))
                }
                awaitTerminalSettled()
                assertTrue(verticalAxis().value() >= verticalAxis().maxValue() - 1f,
                    "Owner incarnation change reopens at the tail")
            }
        } finally {
            harness.close()
        }
    }

    @Test
    fun realUpwardScrollDuringPendingFollowCancelsFollowingForLaterAppendsToo() {
        val repository = TerminalReadingRepository(TERMINAL_SCOPE)
        val long = terminalOutput(revision = 1, lines = 120)
        repository.rows = listOf(terminalSummary(long))
        repository.current = long
        val harness = TerminalHarness(repository)
        try {
            runSkikoComposeUiTest(size = Size(640f, 800f)) {
                var panelHeight by mutableStateOf(600.dp)
                setContent {
                    val state by harness.controller.state.collectAsState()
                    CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                        HarnessTheme(themeMode = HarnessThemeMode.Light) {
                            Box(Modifier.size(640.dp, panelHeight)) {
                                state?.let { current ->
                                    ThreadPanels(current, { harness.controller.dispatch(it) },
                                        onRefreshTerminal = { harness.controller.refreshTerminal() })
                                }
                            }
                        }
                    }
                }
                settle()
                awaitTerminalSettled()
                val atBottom = verticalAxis()
                // ScrollAxisRange lambdas are live: retain numeric values BEFORE publishing.
                val oldValue = atBottom.value()
                val oldMax = atBottom.maxValue()
                assertTrue(oldValue >= oldMax - 1f, "Fixture starts at the tail")

                // Hold the clock so the follow effect for the longer output cannot resume yet.
                mainClock.autoAdvance = false
                runOnIdle {
                    repository.current = appended(long, revision = 2, lines = 60, tail = "PENDING-TAIL")
                    harness.controller.refreshTerminal()
                }
                mainClock.advanceTimeByFrame()
                waitForIdle()
                val pending = verticalAxis()
                val pendingMax = pending.maxValue()
                val pendingValue = pending.value()
                assertTrue(pendingMax > oldMax, "The appended output must grow the range")
                assertNear(oldValue, pendingValue, "Measured append still holds the old offset before follow", 1f)
                assertTrue(pendingValue < pendingMax - 1f, "Follow must be pending, not already settled at the new tail")
                assertEquals(repository.current!!.buffer, terminalTextLayout().layoutInput.text.text,
                    "The new content must have a real text layout before pending input")
                // One real upward wheel while the pending follow has not run.
                terminalWheelImmediate("terminal-output", -260f)
                // Let the pending effect resume; it must observe the user's upward position.
                mainClock.advanceTimeByFrame()
                waitForIdle()
                mainClock.autoAdvance = true
                awaitTerminalSettled()
                val cancelled = verticalAxis()
                assertTrue(cancelled.value() < cancelled.maxValue() - 1f,
                    "A real upward scroll must cancel the pending follow")
                val cancelledValue = cancelled.value()

                // A later append must not re-attach the reader to the bottom.
                runOnIdle {
                    repository.current = appended(repository.current!!, revision = 3, lines = 60, tail = "SECOND-PENDING-TAIL")
                    harness.controller.refreshTerminal()
                }
                awaitTerminalSettled()
                val after = verticalAxis()
                assertTrue(after.value() < after.maxValue() - 1f, "Cancellation persists across later appends")
                assertNear(cancelledValue, after.value(), "The reader keeps its pixel offset", 1f)
                onNodeWithText("SECOND-PENDING-TAIL", substring = true).assertExists()

                // A resize shrinks the range and clamps a bottom reader automatically. Upward
                // input within that same pending window must still cancel the subsequent follow.
                wheelVerticalToBottom()
                awaitTerminalSettled()
                val beforeResizeValue = verticalAxis().value()
                val beforeResizeMax = verticalAxis().maxValue()
                mainClock.autoAdvance = false
                runOnIdle { panelHeight = 780.dp }
                mainClock.advanceTimeByFrame()
                waitForIdle()
                val resizedMax = verticalAxis().maxValue()
                assertTrue(resizedMax < beforeResizeMax, "A larger viewport must shrink the real range")
                assertNear(beforeResizeValue.coerceAtMost(resizedMax), verticalAxis().value(),
                    "System resize clamp is measured before user input", 1f)
                terminalWheelImmediate("terminal-output", -260f)
                mainClock.advanceTimeByFrame()
                waitForIdle()
                mainClock.autoAdvance = true
                awaitTerminalSettled()
                val resizeCancelledValue = verticalAxis().value()
                assertTrue(resizeCancelledValue < verticalAxis().maxValue() - 1f,
                    "Upward input must survive a simultaneous range shrink")
                runOnIdle {
                    repository.current = appended(repository.current!!, revision = 4, lines = 30, tail = "AFTER-RESIZE-CANCEL")
                    harness.controller.refreshTerminal()
                }
                awaitTerminalSettled()
                assertNear(resizeCancelledValue, verticalAxis().value(),
                    "Shrink-window cancellation also survives later output", 1f)
                assertTrue(verticalAxis().value() < verticalAxis().maxValue() - 1f)
            }
        } finally {
            harness.close()
        }
    }

    @Test
    fun pendingReadIsFencedByOwnerChangeAndTabClose() {
        val repository = TerminalReadingRepository(TERMINAL_SCOPE)
        val output = terminalOutput(lines = 80)
        repository.rows = listOf(terminalSummary(output))
        repository.current = output
        val harness = TerminalHarness(repository)
        try {
            runSkikoComposeUiTest(size = Size(640f, 800f)) {
                mountTerminalPanels(harness.controller)
                awaitTerminalSettled()

                // An owner change while a read is in flight must drop the stale response.
                val gate = CompletableDeferred<Unit>()
                runOnIdle {
                    repository.gate = gate
                    harness.controller.refreshTerminal()
                }
                settle()
                onNodeWithText("正在加载输出…").assertIsDisplayed()
                runOnIdle { harness.rebind(generation = 2) }
                runOnIdle {
                    repository.gate = null
                    gate.complete(Unit)
                }
                settle()
                onNodeWithText("面板已关闭").assertIsDisplayed()
                assertEquals(0, onAllNodesWithText("运行中").fetchSemanticsNodes().size,
                    "A stale read must not resurrect output for the previous owner")
                runOnIdle { assertTrue(harness.controller.state.value!!.terminal is PanelLoad.Unavailable) }

                // Closing the tab while a read is in flight must also drop it.
                runOnIdle { harness.controller.dispatch(ThreadPanelAction.OpenTab(TERMINAL_SCOPE, ThreadPanelTab.BackgroundOutput)) }
                val closeGate = CompletableDeferred<Unit>()
                runOnIdle {
                    repository.gate = closeGate
                    harness.controller.refreshTerminal()
                }
                settle()
                terminalClick("关闭后台终端")
                onNodeWithText("面板已关闭").assertIsDisplayed()
                runOnIdle {
                    repository.gate = null
                    closeGate.complete(Unit)
                }
                settle()
                onNodeWithText("面板已关闭").assertIsDisplayed()
                runOnIdle { assertTrue(ThreadPanelTab.BackgroundOutput !in harness.controller.state.value!!.openTabs) }
            }
        } finally {
            harness.close()
        }
    }

    @Test
    fun sameIdentityResizeClampsTheUpReaderAndKeepsTheBottomReaderAtTheTail() {
        val repository = TerminalReadingRepository(TERMINAL_SCOPE)
        val output = terminalOutput(lines = 150)
        repository.rows = listOf(terminalSummary(output))
        repository.current = output
        val harness = TerminalHarness(repository)
        try {
            runSkikoComposeUiTest(size = Size(640f, 800f)) {
                var panelHeight by mutableStateOf(700.dp)
                setContent {
                    val state by harness.controller.state.collectAsState()
                    CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                        HarnessTheme(themeMode = HarnessThemeMode.Light) {
                            Box(Modifier.size(120.dp, panelHeight).background(HarnessTokens.Colors.Surface)) {
                                state?.let { current ->
                                    ThreadPanels(current, { harness.controller.dispatch(it) },
                                        onRefreshTerminal = { harness.controller.refreshTerminal() })
                                }
                            }
                        }
                    }
                }
                settle()
                awaitTerminalSettled()
                wheelVerticalUp()
                awaitTerminalSettled()
                val up = verticalAxis().value()
                assertTrue(up < verticalAxis().maxValue() - 1f, "Fixture must be read above the bottom")
                val runningHeight = onNodeWithText("运行中").fetchSemanticsNode().size.height
                val runningLayout = onNodeWithText("运行中").textLayoutResult()
                assertEquals(1, runningLayout.lineCount, "Running status must fit on one measured line")
                val runningMax = verticalAxis().maxValue()

                // A lifecycle-only refresh changes the measured status line while preserving the
                // same key and reader offset.
                runOnIdle {
                    repository.current = output.copy(
                        lifecycle = BackgroundProcessLifecycle.Exited(Int.MIN_VALUE), revision = output.revision + 1,
                    )
                    harness.controller.refreshTerminal()
                }
                awaitTerminalSettled()
                assertNear(up, verticalAxis().value(), "Lifecycle transition preserves same-identity reading", 1f)
                val exitedHeight = onNodeWithText("已退出 · 代码 " + Int.MIN_VALUE).fetchSemanticsNode().size.height
                val exitedLayout = onNodeWithText("已退出 · 代码 " + Int.MIN_VALUE).textLayoutResult()
                assertTrue(exitedLayout.lineCount > 1, "Exited status must have measured multiple lines")
                assertTrue(exitedLayout.size.height > runningLayout.size.height,
                    "Lifecycle text layout must actually grow in height")
                assertTrue(exitedHeight > runningHeight, "The lifecycle label must actually wrap in this narrow viewport")
                assertTrue(verticalAxis().maxValue() > runningMax, "Lifecycle-only refresh grows the measured range")
                runOnIdle { assertEquals(output.key, harness.controller.state.value!!.selectedTerminalKey) }

                wheelVerticalToBottom()
                runOnIdle {
                    repository.current = output.copy(revision = output.revision + 2)
                    harness.controller.refreshTerminal()
                }
                awaitTerminalSettled()
                assertTrue(verticalAxis().value() >= verticalAxis().maxValue() - 1f,
                    "Lifecycle height shrink keeps a same-identity bottom reader at the tail")
                assertEquals(runningHeight, onNodeWithText("运行中").fetchSemanticsNode().size.height)
                assertEquals(1, onNodeWithText("运行中").textLayoutResult().lineCount)
                assertNear(runningMax, verticalAxis().maxValue(), "Running restores the measured range", 1f)
                wheelVerticalUp()
                awaitTerminalSettled()
                val resizeUp = verticalAxis().value()

                // Shrinking the viewport grows the range; the up reader keeps its offset.
                runOnIdle { panelHeight = 320.dp }
                awaitTerminalSettled()
                val smaller = verticalAxis()
                assertTrue(smaller.value() <= smaller.maxValue() + 1f, "Up reader must clamp into the new range")
                assertNear(resizeUp, smaller.value(), "Same-identity resize keeps the upward offset", 1f)

                // A bottom reader stays at the tail across a resize.
                wheelVerticalToBottom()
                assertTrue(verticalAxis().value() >= verticalAxis().maxValue() - 1f)
                runOnIdle { panelHeight = 700.dp }
                awaitTerminalSettled()
                val grown = verticalAxis()
                assertTrue(grown.value() >= grown.maxValue() - 1f, "Bottom reader stays at the tail after resize")
            }
        } finally {
            harness.close()
        }
    }

    @Test
    fun capturesTerminalReadingMatrix() {
        val directory = File(System.getenv("HARNESS_TERMINAL_READING_CAPTURE_DIR")?.takeIf { it.isNotBlank() }
            ?: "/tmp/harness-terminal-reading-captures-20261008")
        assertTrue(directory.isDirectory || directory.mkdirs(), "Cannot create capture directory: " + directory)
        val repository = TerminalReadingRepository(TERMINAL_SCOPE)
        val wide = terminalOutput(lines = 90, wide = true)
        repository.rows = listOf(terminalSummary(wide))
        repository.current = wide
        val harness = TerminalHarness(repository)
        try {
            for ((width, height) in listOf(320 to 600, 640 to 800)) {
                for (theme in listOf(HarnessThemeMode.Light, HarnessThemeMode.Dark)) {
                    runSkikoComposeUiTest(size = Size(width.toFloat(), height.toFloat())) {
                        mountCaptureViewport(harness.controller, width, height, theme)
                        awaitTerminalSettled()
                        val themeSlug = theme.name.lowercase()
                        captureTerminalReading(directory, "terminal-reading-tail-" + width + "x" + height + "-" + themeSlug,
                            "tail", theme, width, height, wide.buffer)
                        wheelVerticalUp(fraction = 0.4f)
                        awaitTerminalSettled()
                        captureTerminalReading(directory, "terminal-reading-up-" + width + "x" + height + "-" + themeSlug,
                            "up", theme, width, height, wide.buffer)
                        wheelVerticalToBottom()
                        awaitTerminalSettled()
                        wheelHorizontalToEnd()
                        awaitTerminalSettled()
                        captureTerminalReading(directory, "terminal-reading-horizontal-end-" + width + "x" + height + "-" + themeSlug,
                            "horizontal-end", theme, width, height, wide.buffer)
                    }
                }
            }
        } finally {
            harness.close()
        }
    }
}

private const val TERMINAL_DEADLINE_NANOS = 10_000_000_000L
private const val TERMINAL_PROCESS = "fixture-process"
private const val WIDE_MARKER = "WIDE-END-MARKER"
private const val TAIL_MARKER = "TAIL-MARKER"
private const val STALE_SELECTION = "所选后台进程已过期，请选择当前列表中的进程。"

private val TERMINAL_SCOPE = ThreadPanelScope(HostId("terminal-reading-host"), SessionId("parent"))

private fun terminalKey(processId: String = TERMINAL_PROCESS, incarnation: String = "fixture-incarnation") =
    BackgroundTerminalKey(TERMINAL_SCOPE, processId, incarnation)

/** Numbered lines grow predictably; the optional wide line is always the last line of the buffer. */
private fun terminalBuffer(lines: Int, tail: String, wide: Boolean): String = buildString {
    append((1..lines).joinToString("\n") { "line " + it + " 终端输出内容用于纵向滚动验收" })
    append('\n').append(tail)
    if (wide) append('\n').append("START ").append("wide-segment/".repeat(40)).append(WIDE_MARKER)
}

private fun terminalOutput(
    revision: Long = 1,
    lines: Int = 80,
    tail: String = TAIL_MARKER,
    key: BackgroundTerminalKey = terminalKey(),
    wide: Boolean = false,
): BackgroundTerminalOutput = BackgroundTerminalOutput(
    key = key, buffer = terminalBuffer(lines, tail, wide), revision = revision,
    command = "fixture-command --review-output",
    lifecycle = BackgroundProcessLifecycle.Running, cwd = "/fixture/project with spaces")

/** A real suffix append: the previous buffer stays a prefix and the scroll range strictly grows. */
private fun appended(base: BackgroundTerminalOutput, revision: Long, lines: Int, tail: String = TAIL_MARKER) =
    base.copy(revision = revision, buffer = base.buffer + "\n" +
        (1..lines).joinToString("\n") { "appended " + it + " 追加输出内容" } + "\n" + tail)

private fun terminalSummary(output: BackgroundTerminalOutput) =
    BackgroundTerminalSummary(output.key, output.command, output.lifecycle, output.cwd, inputState = output.inputState)

/** Real controller with a repository whose reads can be delayed; no test-only display component. */
private class TerminalReadingRepository(private val scope: ThreadPanelScope) : ThreadPanelsRepository {
    var rows: List<BackgroundTerminalSummary> = emptyList()
    var current: BackgroundTerminalOutput? = null
    var failure: String? = null
    var unavailable = false
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun readTerminalSelection(
        owner: ThreadPanelsOwner,
        selectedKey: BackgroundTerminalKey?,
    ): BackgroundTerminalSelection {
        check(owner.scope == scope) { "Terminal reading fixtures only serve their own scope" }
        gate?.await()
        if (unavailable) return BackgroundTerminalSelection(scope, emptyList(), selectedKey,
            PanelLoad.Unavailable(scope, PanelUnavailableReason.TerminalServiceMissing))
        val key = selectedKey ?: rows.firstOrNull()?.key
        val output = when {
            failure != null -> PanelLoad.Error(scope, failure!!)
            key == null -> PanelLoad.Empty(scope)
            rows.none { it.key == key } -> PanelLoad.Error(scope, STALE_SELECTION)
            current?.key != key -> PanelLoad.Error(scope, STALE_SELECTION)
            else -> PanelLoad.Ready(scope, current!!)
        }
        return BackgroundTerminalSelection(scope, rows, key, output)
    }

    override suspend fun readOverview(owner: ThreadPanelsOwner): PanelLoad<SubagentOverview> =
        PanelLoad.Empty(scope)

    override suspend fun readDetail(owner: ThreadPanelsOwner, child: ChildAgentKey): PanelLoad<ChildAgentDetail> =
        PanelLoad.Unavailable(scope, PanelUnavailableReason.ChildHistoryUnavailable)
}

private class TerminalHarness(val repository: TerminalReadingRepository) : AutoCloseable {
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val connection = LocalConnection(ApiGateway())
    val controller = ThreadPanelsController(coroutineScope, repository)

    init {
        controller.bind(owner(generation = 1))
        // Both tabs are real views; the reading suite observes the BackgroundOutput tab.
        controller.dispatch(ThreadPanelAction.OpenTab(TERMINAL_SCOPE, ThreadPanelTab.Subagents))
        controller.dispatch(ThreadPanelAction.OpenTab(TERMINAL_SCOPE, ThreadPanelTab.BackgroundOutput))
    }

    fun rebind(generation: Long) {
        controller.bind(owner(generation))
    }

    private fun owner(generation: Long) = ThreadPanelsOwner(
        connection, ConversationHostScope(TERMINAL_SCOPE.hostId, generation, null), TERMINAL_SCOPE.conversationId,
    )

    override fun close() {
        controller.close()
        coroutineScope.cancel()
    }
}

private fun ComposeUiTest.settle() {
    mainClock.advanceTimeByFrame()
    waitForIdle()
}

private fun ComposeUiTest.mountTerminalPanels(
    controller: ThreadPanelsController,
    theme: HarnessThemeMode = HarnessThemeMode.Light,
) {
    setContent {
        val state by controller.state.collectAsState()
        CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
            HarnessTheme(themeMode = theme) {
                state?.let { current ->
                    ThreadPanels(current, { controller.dispatch(it) },
                        onRefreshTerminal = { controller.refreshTerminal() })
                }
            }
        }
    }
    settle()
}

private fun ComposeUiTest.mountCaptureViewport(
    controller: ThreadPanelsController,
    width: Int,
    height: Int,
    theme: HarnessThemeMode,
) {
    setContent {
        val state by controller.state.collectAsState()
        CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
            HarnessTheme(themeMode = theme) {
                Box(Modifier.size(width.dp, height.dp).background(HarnessTokens.Colors.Surface)
                    .semantics { testTag = "terminal-capture-viewport" }) {
                    state?.let { current ->
                        ThreadPanels(current, { controller.dispatch(it) },
                            onRefreshTerminal = { controller.refreshTerminal() })
                    }
                }
            }
        }
    }
    settle()
}

private fun ComposeUiTest.verticalAxis(tag: String = "terminal-output"): ScrollAxisRange =
    onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        .config[SemanticsProperties.VerticalScrollAxisRange]

private fun ComposeUiTest.horizontalAxis(tag: String = "terminal-output-text"): ScrollAxisRange =
    onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode()
        .config[SemanticsProperties.HorizontalScrollAxisRange]

private fun ComposeUiTest.terminalTextLayout(tag: String = "terminal-output-text"): TextLayoutResult =
    onNodeWithTag(tag, useUnmergedTree = true).textLayoutResult()

private fun SemanticsNodeInteraction.textLayoutResult(): TextLayoutResult {
    val layouts = mutableListOf<TextLayoutResult>()
    performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
    return layouts.firstOrNull() ?: error("Measured text layout is unavailable")
}

private fun ComposeUiTest.horizontalAxisOrNull(tag: String = "terminal-output-text"): ScrollAxisRange? =
    onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().firstOrNull()
        ?.config?.getOrNull(SemanticsProperties.HorizontalScrollAxisRange)

private fun ComposeUiTest.pointerRoot(target: SemanticsNodeInteraction): SemanticsNodeInteraction {
    val root = generateSequence(target.fetchSemanticsNode()) { it.parent }.last()
    return onNode(SemanticsMatcher("Terminal pointer root " + root.id) { it.id == root.id }, useUnmergedTree = true)
}

/** Visible centre of a target in its own pointer root, re-read after any hover remeasure. */
private fun pointerPoint(target: SemanticsNodeInteraction, rootNode: SemanticsNode) = run {
    val node = target.assertIsDisplayed().fetchSemanticsNode()
    val viewport = node.boundsInRoot.intersect(rootNode.boundsInRoot)
    assertTrue(viewport.width > 0f && viewport.height > 0f, "Target must expose a pointer viewport")
    viewport.center - rootNode.boundsInRoot.topLeft
}

/** Native Skiko wheel units to pixels; the Skiko dispatcher sends the delta through unchanged. */
private fun pixelsPerWheelUnit(node: SemanticsNode, horizontal: Boolean): Float {
    val axisSize = (if (horizontal) node.size.width else node.size.height).toFloat()
    return when (val osName = System.getProperty("os.name").orEmpty()) {
        "Mac OS X" -> 10f * node.layoutInfo.density.density
        else -> if (osName.startsWith("Linux")) sqrt(axisSize) else axisSize / 20f
    }
}

private fun ComposeUiTest.terminalWheel(tag: String, pixels: Float, horizontal: Boolean = false) {
    require(pixels.isFinite() && pixels != 0f) { "Wheel delta must be a finite non-zero pixel amount" }
    settle()
    val started = System.nanoTime()
    val target = onNodeWithTag(tag, useUnmergedTree = true).assertIsDisplayed()
    val root = pointerRoot(target)
    val rootNode = root.fetchSemanticsNode()
    val hover = pointerPoint(target, rootNode)
    // Hover can remeasure the reader; resolve the visible centre again before the wheel.
    root.performMouseInput { moveTo(hover) }
    settle()
    val point = pointerPoint(target, rootNode)
    val units = pixels / pixelsPerWheelUnit(target.fetchSemanticsNode(), horizontal)
    root.performMouseInput {
        moveTo(point)
        advanceEventTime(16)
        if (horizontal) scroll(units, ScrollWheel.Horizontal) else scroll(units)
    }
    settle()
    assertTrue(System.nanoTime() - started < TERMINAL_DEADLINE_NANOS, "Wheel deadline: " + tag)
}

/** Real wheel with no settle, so a pending follow effect has not run yet. */
private fun ComposeUiTest.terminalWheelImmediate(tag: String, pixels: Float) {
    require(pixels.isFinite() && pixels != 0f) { "Wheel delta must be a finite non-zero pixel amount" }
    val target = onNodeWithTag(tag, useUnmergedTree = true)
    val root = pointerRoot(target)
    val rootNode = root.fetchSemanticsNode()
    val node = target.fetchSemanticsNode()
    val viewport = node.boundsInRoot.intersect(rootNode.boundsInRoot)
    assertTrue(viewport.width > 0f && viewport.height > 0f, "Reader must expose a pointer viewport: " + tag)
    val point = viewport.center - rootNode.boundsInRoot.topLeft
    val units = pixels / pixelsPerWheelUnit(node, horizontal = false)
    root.performMouseInput {
        moveTo(point)
        advanceEventTime(16)
        scroll(units)
    }
}

private fun ComposeUiTest.terminalClick(tag: String) {
    settle()
    val started = System.nanoTime()
    val target = onNodeWithTag(tag).assertIsDisplayed()
    val root = pointerRoot(target)
    val rootNode = root.fetchSemanticsNode()
    val hover = pointerPoint(target, rootNode)
    // Hover updates HarnessActionSurface; finish that remeasure before resolving the press position.
    root.performMouseInput { moveTo(hover) }
    settle()
    val point = pointerPoint(target, rootNode)
    root.performMouseInput {
        moveTo(point)
        advanceEventTime(16)
        click(point)
    }
    settle()
    assertTrue(System.nanoTime() - started < TERMINAL_DEADLINE_NANOS, "Click deadline: " + tag)
}

/** A real wheel that leaves the reader above the bottom without guessing a fixed delta. */
private fun ComposeUiTest.wheelVerticalUp(tag: String = "terminal-output", fraction: Float = 0.5f) {
    val axis = verticalAxis(tag)
    assertTrue(axis.maxValue() > 4f, "Reader must overflow before an upward wheel: max=" + axis.maxValue())
    terminalWheel(tag, -axis.maxValue() * fraction)
}

private fun ComposeUiTest.wheelVerticalToBottom(tag: String = "terminal-output") {
    val started = System.nanoTime()
    while (true) {
        val axis = verticalAxis(tag)
        val remaining = axis.maxValue() - axis.value()
        if (remaining <= 1f) return
        assertTrue(System.nanoTime() - started < TERMINAL_DEADLINE_NANOS,
            "Real wheel could not reach the tail: value=" + axis.value() + " max=" + axis.maxValue())
        terminalWheel(tag, remaining.coerceAtMost(600f))
    }
}

private fun ComposeUiTest.wheelHorizontalToEnd(tag: String = "terminal-output-text") {
    val started = System.nanoTime()
    var direction = 1f
    var previous = horizontalAxis(tag).value()
    while (true) {
        val axis = horizontalAxis(tag)
        val remaining = axis.maxValue() - axis.value()
        if (remaining <= 1f) return
        assertTrue(System.nanoTime() - started < TERMINAL_DEADLINE_NANOS,
            "Real horizontal wheel could not reach the end: value=" + axis.value() + " max=" + axis.maxValue())
        terminalWheel(tag, direction * remaining.coerceAtMost(400f), horizontal = true)
        val moved = horizontalAxis(tag).value()
        if (moved <= previous + 0.5f) direction = -direction
        previous = moved
    }
}

/**
 * Real frames first, then three equal samples. A pending follow effect consumes frames before it
 * can report, so a two-sample comparison could accept the position from before it ran.
 */
private fun ComposeUiTest.awaitTerminalSettled(tag: String = "terminal-output") {
    val started = System.nanoTime()
    var frames = 0
    var stable = 0
    var previousValue = Float.NaN
    var previousMax = Float.NaN
    while (true) {
        settle()
        frames++
        val axis = verticalAxis(tag)
        val value = axis.value()
        val max = axis.maxValue()
        stable = if (value == previousValue && max == previousMax) stable + 1 else 0
        previousValue = value
        previousMax = max
        if (frames >= 8 && stable >= 2) return
        assertTrue(System.nanoTime() - started < TERMINAL_DEADLINE_NANOS,
            "Terminal reader did not settle: value=" + value + " max=" + max + " frames=" + frames)
    }
}

private fun assertNear(expected: Float, actual: Float, label: String, tolerance: Float) {
    assertTrue(abs(expected - actual) <= tolerance, label + ": expected=" + expected + " actual=" + actual)
}

private fun ComposeUiTest.captureTerminalReading(
    directory: File,
    name: String,
    stage: String,
    theme: HarnessThemeMode,
    width: Int,
    height: Int,
    buffer: String,
) {
    settle()
    val target = onNodeWithTag("terminal-capture-viewport")
    val node = target.fetchSemanticsNode()
    val rootId = generateSequence(node) { it.parent }.last().id
    val bytes = Image.makeFromBitmap(target.captureToImage().asSkiaBitmap()).use { image ->
        image.encodeToData(EncodedImageFormat.PNG)?.use { it.bytes } ?: error("PNG encoding failed")
    }
    val png = File(directory, name + ".png").also { it.writeBytes(bytes) }
    val decoded = assertNotNull(ImageIO.read(png), "Unreadable PNG: " + png)
    assertEquals(width, decoded.width)
    assertEquals(height, decoded.height)
    val vertical = verticalAxis()
    val horizontal = horizontalAxisOrNull()
    val text = onNodeWithTag("terminal-output-text", useUnmergedTree = true).fetchSemanticsNode()
    val fullText = text.config[SemanticsProperties.Text].joinToString(separator = "") { it.text }
    assertEquals(buffer, fullText, "Every capture must contain the full raw buffer")
    val layout = terminalTextLayout()
    assertEquals(buffer, layout.layoutInput.text.text, "Every capture must lay out the full buffer")
    assertTrue((0 until layout.lineCount).none { layout.isLineEllipsized(it) }, "No capture may ellipsize output")
    assertEquals(buffer.length, layout.getLineEnd(layout.lineCount - 1), "The final character is laid out")
    val markerStart = buffer.lastIndexOf(WIDE_MARKER)
    assertTrue(markerStart >= 0 && buffer.endsWith(WIDE_MARKER), "Capture fixture ends in the marker")
    val markerBoxes = (markerStart until buffer.length).map { layout.getBoundingBox(it) }
    val markerLocal = Rect(markerBoxes.minOf { it.left }, markerBoxes.minOf { it.top },
        markerBoxes.maxOf { it.right }, markerBoxes.maxOf { it.bottom })
    // The tag belongs to horizontalScroll: positionInRoot is its viewport origin. The
    // ancestor vertical scroll is already reflected in Y; subtract the horizontal value
    // exactly once to convert inner text coordinates, as in the real horizontal-wheel test.
    val viewportOrigin = text.positionInRoot
    val horizontalOffset = assertNotNull(horizontal).value()
    val textOrigin = viewportOrigin - Offset(horizontalOffset, 0f)
    val marker = markerLocal.translate(textOrigin)
    val readerBounds = onNodeWithTag("terminal-output", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    val visibleWindow = text.boundsInRoot.intersect(readerBounds).intersect(node.boundsInRoot)
    val clippedMarker = marker.intersect(visibleWindow)
    val markerFullyVisible = marker.left >= visibleWindow.left - 1f &&
        marker.right <= visibleWindow.right + 1f && marker.top >= visibleWindow.top - 1f &&
        marker.bottom <= visibleWindow.bottom + 1f
    when (stage) {
        "tail" -> {
            assertNear(vertical.maxValue(), vertical.value(), "Tail capture is at bottom", 1f)
            assertNear(0f, assertNotNull(horizontal).value(), "Tail capture starts horizontally at origin", 1f)
            assertTrue(marker.top >= readerBounds.top - 1f && marker.bottom <= readerBounds.bottom + 1f,
                "Tail marker is inside the vertical viewport")
            assertTrue(marker.right > visibleWindow.right + 1f, "Wide end starts horizontally clipped")
        }
        "up" -> {
            assertTrue(vertical.value() < vertical.maxValue() - 1f, "Up capture must be above bottom")
            assertTrue(marker.top >= readerBounds.bottom, "The final marker is below the upward reader's viewport")
        }
        "horizontal-end" -> {
            assertNear(vertical.maxValue(), vertical.value(), "Horizontal end capture is also at vertical bottom", 1f)
            val axis = assertNotNull(horizontal)
            assertTrue(axis.maxValue() > 0f, "The horizontal capture must overflow")
            assertNear(axis.maxValue(), axis.value(), "Horizontal end capture reaches the end", 1f)
            assertTrue(markerFullyVisible, "Laid-out marker must be inside BOTH clipped axes: marker=" +
                marker + " visible=" + visibleWindow)
        }
        else -> error("Unknown terminal capture stage: " + stage)
    }
    val geometry = buildJsonObject {
        put("schema", "harness.terminal-reading.capture.v3")
        put("stage", stage); put("image", png.name)
        put("width", width); put("height", height); put("theme", theme.name)
        put("density", 1f); put("units", "pixels"); put("captureRootId", rootId)
        put("sha256", MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
        put("verticalValue", vertical.value()); put("verticalMax", vertical.maxValue())
        if (horizontal != null) {
            put("horizontalValue", horizontal.value()); put("horizontalMax", horizontal.maxValue())
        }
        put("bufferLength", buffer.length)
        put("renderedTextLength", fullText.length)
        put("renderedTextMatchesBuffer", fullText == buffer)
        put("tail", fullText.takeLast(64))
        put("textBoundsInRoot", text.boundsInRoot.toString())
        put("viewportBoundsInRoot", node.boundsInRoot.toString())
        put("readerBoundsInRoot", readerBounds.toString())
        put("horizontalViewportOriginInRoot", viewportOrigin.toString())
        put("horizontalOffsetSubtracted", horizontalOffset)
        put("textOriginInRoot", textOrigin.toString())
        put("coordinateContract", "markerLocal + horizontalViewportOriginInRoot - Offset(horizontalOffsetSubtracted, 0); ancestor vertical translation already included")
        put("markerLocalBounds", markerLocal.toString())
        put("markerBoundsInRoot", marker.toString())
        put("markerClippedBoundsInRoot", clippedMarker.toString())
        put("visibleTextWindowInRoot", visibleWindow.toString())
        put("markerFullyVisible", markerFullyVisible)
        put("noEllipsis", true)
        put("laidOutTextEnd", layout.getLineEnd(layout.lineCount - 1))
        put("evidenceLimit", "Test-local Skiko panel viewport from typed fixtures; not original pixel parity or a native window.")
    }
    File(directory, name + ".json").writeText(Json { prettyPrint = true }.encodeToString(JsonObject.serializer(), geometry))
}
