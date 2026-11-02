package im.hikaru.harness.desktop

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import im.hikaru.harness.client.summary.ThreadSummaryLoad
import im.hikaru.harness.client.summary.ThreadSummaryOutput
import im.hikaru.harness.client.summary.ThreadSummaryPanel
import im.hikaru.harness.client.summary.ThreadSummaryPlan
import im.hikaru.harness.client.summary.ThreadSummarySection
import im.hikaru.harness.client.summary.ThreadSummarySectionMode
import im.hikaru.harness.client.summary.ThreadSummarySectionState
import im.hikaru.harness.client.summary.ThreadSummarySideChat
import im.hikaru.harness.client.summary.ThreadSummarySource
import im.hikaru.harness.client.summary.ThreadSummarySourceKind
import im.hikaru.harness.ui.foundation.HarnessText
import im.hikaru.harness.ui.foundation.HarnessTheme
import kotlin.test.*

/**
 * The summary island's accordion primitive and the sections Codex restores beyond activity:
 * plan, outputs, side chats and sources.
 */
@OptIn(ExperimentalTestApi::class)
class ThreadSummarySectionInteractionTest {
    @Test
    fun accordionHeaderTogglesItsContent() {
        runSkikoComposeUiTest(size = Size(420f, 640f)) {
            setContent {
                HarnessTheme {
                    ThreadSummarySection("demo", "演示", ThreadSummarySectionState()) {
                        HarnessText("内容", Modifier.semantics { testTag = "demo-content" })
                    }
                }
            }

            onNodeWithTag("demo-content").assertExists()
            onNodeWithTag("thread-summary-section-demo").performClick()
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("demo-content").fetchSemanticsNodes().isEmpty() }

            onNodeWithTag("thread-summary-section-demo").performClick()
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("demo-content").fetchSemanticsNodes().isNotEmpty() }
        }
    }

    @Test
    fun autoCollapseCollapsesAfterTheDelayAndTheHeaderReopensIt() {
        runSkikoComposeUiTest(size = Size(420f, 640f)) {
            setContent {
                HarnessTheme {
                    ThreadSummarySection(
                        sectionKey = "demo",
                        title = "演示",
                        state = ThreadSummarySectionState(),
                        autoCollapse = true,
                        autoCollapseDelayMillis = 50,
                    ) {
                        HarnessText("内容", Modifier.semantics { testTag = "demo-content" })
                    }
                }
            }

            onNodeWithTag("demo-content").assertExists()
            mainClock.advanceTimeBy(1_000)
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("demo-content").fetchSemanticsNodes().isEmpty() }

            onNodeWithTag("thread-summary-section-demo").performClick()
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag("demo-content").fetchSemanticsNodes().isNotEmpty() }
        }
    }

    @Test
    fun clickingWhileAutoCollapseIsPendingKeepsTheSectionOpen() {
        runSkikoComposeUiTest(size = Size(420f, 640f)) {
            setContent {
                HarnessTheme {
                    ThreadSummarySection(
                        sectionKey = "demo",
                        title = "演示",
                        state = ThreadSummarySectionState(),
                        autoCollapse = true,
                        autoCollapseDelayMillis = 200,
                    ) {
                        HarnessText("内容", Modifier.semantics { testTag = "demo-content" })
                    }
                }
            }

            // Collapse and re-open before the delay elapses; both clicks cancel the pending collapse.
            onNodeWithTag("thread-summary-section-demo").performClick()
            onNodeWithTag("thread-summary-section-demo").performClick()
            mainClock.advanceTimeBy(1_000)

            onNodeWithTag("demo-content").assertExists()
        }
    }

    @Test
    fun headerlessSectionDrawsNoHeaderAndAlwaysShowsContent() {
        runSkikoComposeUiTest(size = Size(420f, 640f)) {
            setContent {
                HarnessTheme {
                    ThreadSummarySection(
                        sectionKey = "demo",
                        title = "演示",
                        state = ThreadSummarySectionState(),
                        mode = ThreadSummarySectionMode.Headerless,
                    ) {
                        HarnessText("内容", Modifier.semantics { testTag = "demo-content" })
                    }
                }
            }

            onNodeWithTag("thread-summary-section-demo").assertDoesNotExist()
            onNodeWithTag("demo-content").assertExists()
        }
    }

    @Test
    fun dropdownSectionShowsContentAndIsNotAToggle() {
        runSkikoComposeUiTest(size = Size(420f, 640f)) {
            setContent {
                HarnessTheme {
                    ThreadSummarySection(
                        sectionKey = "demo",
                        title = "演示",
                        state = ThreadSummarySectionState(),
                        mode = ThreadSummarySectionMode.Dropdown,
                    ) {
                        HarnessText("内容", Modifier.semantics { testTag = "demo-content" })
                    }
                }
            }

            onNodeWithTag("thread-summary-section-demo").assertExists().assertHasNoClickAction()
            onNodeWithTag("demo-content").assertExists()
        }
    }

    @Test
    fun planOutputsSideChatsAndSourcesRenderTheirRows() {
        val plan = ThreadSummaryPlan("迁移计划", "1. 建立索引\n2. 补齐小节")
        val outputs = listOf(ThreadSummaryOutput("o1", "report.md", "docs/report.md"))
        val sideChats = listOf(ThreadSummarySideChat("tab-1", "终端排查", inProgress = true))
        val sources = listOf(
            ThreadSummarySource("tool:github", "GitHub", ThreadSummarySourceKind.Tool, detail = "GitHub 插件"),
            ThreadSummarySource(
                "web:kotlin",
                "kotlinlang.org",
                ThreadSummarySourceKind.Web,
                url = "https://kotlinlang.org",
            ),
        )
        val openedSources = mutableListOf<ThreadSummarySource>()
        val openedChats = mutableListOf<ThreadSummarySideChat>()

        runSkikoComposeUiTest(size = Size(420f, 1_600f)) {
            setContent {
                HarnessTheme {
                    ThreadSummaryPanel(
                        load = ThreadSummaryLoad.Unavailable("no git"),
                        onOpenReview = null,
                        onRefresh = null,
                        onClose = null,
                        plan = plan,
                        outputs = outputs,
                        sideChats = sideChats,
                        sources = sources,
                        onOpenSideChat = { openedChats += it },
                        onOpenSource = { openedSources += it },
                    )
                }
            }

            onNodeWithTag("thread-summary-plan-title").assertTextEquals("迁移计划")
            onNodeWithTag("thread-summary-plan-content").assertTextContains("建立索引", substring = true)
            onNodeWithTag("thread-summary-output-o1").assertTextContains("report.md", substring = true)
            onNodeWithTag("thread-summary-section-side-chats-count", useUnmergedTree = true).assertTextEquals("1")
            onNodeWithTag("thread-summary-side-chat-tab-1").assertTextContains("终端排查", substring = true)
            onNodeWithTag("thread-summary-section-sources-count", useUnmergedTree = true).assertTextEquals("2")
            onNodeWithTag("thread-summary-source-tool:github").assertTextContains("GitHub", substring = true)

            onNodeWithTag("thread-summary-source-web:kotlin").performClick()
            onNodeWithTag("thread-summary-side-chat-tab-1").performClick()
            assertEquals(listOf("web:kotlin"), openedSources.map { it.id })
            assertEquals(listOf("tab-1"), openedChats.map { it.tabId })
        }
    }

    @Test
    fun sourcesAndOutputsOwnTheirEmptyStates() {
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

            onNodeWithTag("thread-summary-sources-empty").assertTextEquals("暂无来源")
            onNodeWithTag("thread-summary-outputs-empty").assertTextEquals("暂无产物")
            onNodeWithTag("thread-summary-section-plan").assertDoesNotExist()
            onNodeWithTag("thread-summary-section-side-chats").assertDoesNotExist()
        }
    }
}
