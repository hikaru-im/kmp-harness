package im.hikaru.harness.desktop

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import im.hikaru.harness.client.summary.ThreadSummaryActivity
import im.hikaru.harness.client.summary.ThreadSummaryActivityKind
import im.hikaru.harness.client.summary.ThreadSummaryBackgroundTerminalLabel
import im.hikaru.harness.client.summary.ThreadSummaryLoad
import im.hikaru.harness.client.summary.ThreadSummaryPanel
import im.hikaru.harness.client.summary.ThreadSummaryProcessesTitle
import im.hikaru.harness.client.summary.ThreadSummaryRunningLabel
import im.hikaru.harness.client.summary.ThreadSummaryStopAllLabel
import im.hikaru.harness.client.summary.ThreadSummarySubagentsTitle
import im.hikaru.harness.ui.foundation.HarnessTheme
import kotlin.test.*

/**
 * The summary island draws background activity sections from a real service projection, independent
 * of the Environment section, and reports the exact row a user picks.
 */
@OptIn(ExperimentalTestApi::class)
class ThreadSummaryActivityPanelInteractionTest {
    @Test
    fun activitySectionsRenderRowsWithoutAGitServiceAndReportPickedRows() {
        val subagents = listOf(
            ThreadSummaryActivity(ThreadSummaryActivityKind.Subagent, "child-1", "Reviewer", ThreadSummaryRunningLabel, running = true),
            ThreadSummaryActivity(ThreadSummaryActivityKind.Subagent, "child-2", "child-2"),
        )
        val processes = listOf(
            ThreadSummaryActivity(ThreadSummaryActivityKind.Process, "p1", "npm run dev", running = true),
            ThreadSummaryActivity(ThreadSummaryActivityKind.Process, "p2", ThreadSummaryBackgroundTerminalLabel),
        )
        val picked = mutableListOf<ThreadSummaryActivity>()
        runSkikoComposeUiTest(size = Size(420f, 640f)) {
            setContent {
                HarnessTheme {
                    ThreadSummaryPanel(
                        load = ThreadSummaryLoad.Unavailable("no git"),
                        onOpenReview = null,
                        onRefresh = null,
                        onClose = null,
                        subagents = subagents,
                        processes = processes,
                        onOpenActivity = { picked += it },
                    )
                }
            }
            // The Environment section is unavailable, but background activity still renders.
            onNodeWithTag("thread-summary-unavailable").assertExists()
            onNodeWithTag("thread-summary-section-subagents").assertTextContains(ThreadSummarySubagentsTitle, substring = true)
            onNodeWithTag("thread-summary-section-subagents-count", useUnmergedTree = true).assertTextEquals("2")
            onNodeWithTag("thread-summary-subagent-child-1").assertTextContains("Reviewer", substring = true)
            onNodeWithTag("thread-summary-subagent-child-1").assertTextContains(ThreadSummaryRunningLabel, substring = true)
            onNodeWithTag("thread-summary-subagent-child-2").assertTextContains("child-2", substring = true)
            onNodeWithTag("thread-summary-section-processes").assertTextContains(ThreadSummaryProcessesTitle, substring = true)
            onNodeWithTag("thread-summary-section-processes-count", useUnmergedTree = true).assertTextEquals("2")
            onNodeWithTag("thread-summary-process-p1").assertTextContains("npm run dev", substring = true)
            onNodeWithTag("thread-summary-process-p2").assertTextContains(ThreadSummaryBackgroundTerminalLabel, substring = true)

            onNodeWithTag("thread-summary-subagent-child-2").performClick()
            onNodeWithTag("thread-summary-process-p2").performClick()
            assertEquals(listOf("child-2", "p2"), picked.map { it.id })
        }
    }

    @Test
    fun emptyActivityListsDrawNoSections() {
        runSkikoComposeUiTest(size = Size(420f, 640f)) {
            setContent {
                HarnessTheme {
                    ThreadSummaryPanel(
                        load = ThreadSummaryLoad.Unavailable("no git"),
                        onOpenReview = null,
                        onRefresh = null,
                        onClose = null,
                    )
                }
            }
            onNodeWithTag("thread-summary-section-subagents").assertDoesNotExist()
            onNodeWithTag("thread-summary-section-processes").assertDoesNotExist()
        }
    }

    @Test
    fun everyProcessRowCarriesTheStopAllActionAndReportsOnce() {
        val subagents = listOf(
            ThreadSummaryActivity(ThreadSummaryActivityKind.Subagent, "child-1", "Reviewer", ThreadSummaryRunningLabel, running = true),
        )
        val processes = listOf(
            ThreadSummaryActivity(ThreadSummaryActivityKind.Process, "p1", "npm run dev", running = true),
            ThreadSummaryActivity(ThreadSummaryActivityKind.Process, "p2", ThreadSummaryBackgroundTerminalLabel),
        )
        var stops = 0
        runSkikoComposeUiTest(size = Size(420f, 640f)) {
            setContent {
                HarnessTheme {
                    ThreadSummaryPanel(
                        load = ThreadSummaryLoad.Unavailable("no git"),
                        onOpenReview = null,
                        onRefresh = null,
                        onClose = null,
                        subagents = subagents,
                        processes = processes,
                        onStopProcesses = { stops++ },
                    )
                }
            }
            onNodeWithTag("thread-summary-process-stop-p1").assertExists()
            onNodeWithTag("thread-summary-process-stop-p1").assertIsEnabled().performClick()
            onNodeWithTag("thread-summary-process-stop-p2").assertExists()
            // Only process rows carry the stop action.
            onNodeWithTag("thread-summary-process-stop-child-1").assertDoesNotExist()
            onAllNodesWithContentDescription(ThreadSummaryStopAllLabel).assertCountEquals(2)
            assertEquals(1, stops)
        }
    }

    @Test
    fun processRowsDrawNoStopActionWhenThereIsNoConsumer() {
        val processes = listOf(
            ThreadSummaryActivity(ThreadSummaryActivityKind.Process, "p1", "npm run dev", running = true),
        )
        runSkikoComposeUiTest(size = Size(420f, 640f)) {
            setContent {
                HarnessTheme {
                    ThreadSummaryPanel(
                        load = ThreadSummaryLoad.Unavailable("no git"),
                        onOpenReview = null,
                        onRefresh = null,
                        onClose = null,
                        processes = processes,
                    )
                }
            }
            onNodeWithTag("thread-summary-process-p1").assertExists()
            onNodeWithTag("thread-summary-process-stop-p1").assertDoesNotExist()
        }
    }

    @Test
    fun anInFlightStopDisablesTheActionAndTheServiceNoticeIsRendered() {
        val processes = listOf(
            ThreadSummaryActivity(ThreadSummaryActivityKind.Process, "p1", "npm run dev", running = true),
        )
        runSkikoComposeUiTest(size = Size(420f, 640f)) {
            setContent {
                HarnessTheme {
                    ThreadSummaryPanel(
                        load = ThreadSummaryLoad.Unavailable("no git"),
                        onOpenReview = null,
                        onRefresh = null,
                        onClose = null,
                        processes = processes,
                        processesStopping = true,
                        processesNotice = "操作结果未知，已禁止重复操作。",
                        onStopProcesses = {},
                    )
                }
            }
            onNodeWithTag("thread-summary-process-stop-p1").assertIsNotEnabled()
            onNodeWithTag("thread-summary-processes-notice", useUnmergedTree = true)
                .assertTextEquals("操作结果未知，已禁止重复操作。")
        }
    }
}
