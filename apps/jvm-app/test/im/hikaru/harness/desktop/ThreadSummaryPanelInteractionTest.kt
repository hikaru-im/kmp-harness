package im.hikaru.harness.desktop

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.harness.client.git.GitAccess
import im.hikaru.harness.client.git.GitBinding
import im.hikaru.harness.client.git.GitChange
import im.hikaru.harness.client.git.GitRead
import im.hikaru.harness.client.git.GitState
import im.hikaru.harness.client.connection.SessionId
import im.hikaru.harness.client.scheduling.ScheduledRule
import im.hikaru.harness.client.scheduling.ScheduledTarget
import im.hikaru.harness.client.scheduling.ScheduledTask
import im.hikaru.harness.client.scheduling.ScheduledTaskStatus
import im.hikaru.harness.client.scheduling.ScheduledWeekday
import im.hikaru.harness.client.summary.ThreadSummaryPanel
import im.hikaru.harness.client.summary.ThreadSummaryLoad
import im.hikaru.harness.client.summary.threadSummaryEnvironment
import im.hikaru.harness.ui.foundation.HarnessTheme
import kotlin.test.*

/** Production summary island + the real bound GitController over a controlled local adapter. */
@OptIn(ExperimentalTestApi::class)
class ThreadSummaryPanelInteractionTest {
    @Test
    fun environmentSectionShowsRealBranchStatsAndTrackedChanges() {
        val f = GitWorktreesPageFixture()
        try {
            f.backend.patch = MULTI_FILE_DIFF
            f.backend.changes = listOf(
                GitChange("src/a.kt", indexStatus = 'M', worktreeStatus = ' '),
                GitChange("src/b.kt", indexStatus = ' ', worktreeStatus = 'M'),
                GitChange("notes.txt", indexStatus = '?', worktreeStatus = '?'),
            )
            f.git.refresh()
            f.git.refreshSummaryStats()
            val load = threadSummaryEnvironment(f.git.state.value)
            var opened = 0
            runSkikoComposeUiTest(size = Size(420f, 640f)) {
                setContent {
                    HarnessTheme {
                        ThreadSummaryPanel(load = load, onOpenReview = { opened++ }, onRefresh = null, onClose = null)
                    }
                }
                onNodeWithTag("thread-summary-title").assertTextEquals("摘要")
                onNodeWithTag("thread-summary-section-environment").assertTextEquals("环境")
                // The merged row exposes the label and both +/- counts of the HEAD-based diff.
                onNodeWithTag("thread-summary-changes").assertTextContains("变更", substring = true)
                onNodeWithTag("thread-summary-changes").assertTextContains("+2", substring = true)
                onNodeWithTag("thread-summary-changes").assertTextContains("−1", substring = true)
                onNodeWithTag("thread-summary-branch").assertTextContains("main", substring = true)
                // Untracked notes.txt is excluded: only the two tracked files are "uncommitted changes".
                onNodeWithTag("thread-summary-uncommitted").assertTextContains("未提交：2 个文件", substring = true)
                onNodeWithTag("thread-summary-changes").performClick()
                assertEquals(1, opened)
            }
        } finally {
            f.close()
        }
    }

    @Test
    fun absentGitServiceShowsItsReasonInsteadOfFabricatedRows() {
        val state = GitState(binding = GitBinding(null, HostId("summary-remote"), 1, GitAccess.RemoteUnavailable))
        runSkikoComposeUiTest(size = Size(420f, 640f)) {
            setContent {
                HarnessTheme {
                    ThreadSummaryPanel(load = threadSummaryEnvironment(state), onOpenReview = null, onRefresh = null, onClose = null)
                }
            }
            onNodeWithTag("thread-summary-unavailable").assertTextContains("远端", substring = true)
            onNodeWithTag("thread-summary-changes").assertDoesNotExist()
            onNodeWithTag("thread-summary-branch").assertDoesNotExist()
        }
    }

    @Test
    fun loadingStatsKeepTheRowWithoutNumbers() {
        val f = GitWorktreesPageFixture()
        try {
            val state = f.git.state.value.copy(summaryStats = GitRead.Loading)
            runSkikoComposeUiTest(size = Size(420f, 640f)) {
                setContent {
                    HarnessTheme {
                        ThreadSummaryPanel(load = threadSummaryEnvironment(state), onOpenReview = null, onRefresh = null, onClose = null)
                    }
                }
                onNodeWithTag("thread-summary-changes-loading", useUnmergedTree = true).assertTextEquals("…")
                onNodeWithTag("thread-summary-changes-stats", useUnmergedTree = true).assertDoesNotExist()
            }
        } finally {
            f.close()
        }
    }

    @Test
    fun scheduledSectionListsBoundCadencesAndOpensTheTask() {
        val bound = scheduled("nightly", "每日摘要", ScheduledRule.Daily(9, 0, "Asia/Shanghai"))
        val weekly = scheduled("weekly", "每周回顾", ScheduledRule.Weekly(ScheduledWeekday.Wednesday, 9, 5, "Asia/Shanghai"))
        val paused = scheduled("paused", "暂停的任务", ScheduledRule.Hourly(3, 0L), status = ScheduledTaskStatus.Paused)
        val opened = mutableListOf<String>()
        runSkikoComposeUiTest(size = Size(420f, 640f)) {
            setContent {
                HarnessTheme {
                    ThreadSummaryPanel(
                        load = ThreadSummaryLoad.Unavailable("no git"),
                        onOpenReview = null, onRefresh = null, onClose = null,
                        scheduledTasks = listOf(bound, weekly, paused),
                        onOpenScheduled = { opened += it },
                    )
                }
            }
            onNodeWithTag("thread-summary-section-scheduled").assertTextContains("定时任务", substring = true)
            onNodeWithTag("thread-summary-section-scheduled-count", useUnmergedTree = true).assertTextEquals("3")
            onNodeWithTag("thread-summary-scheduled-nightly").assertTextContains("每日摘要", substring = true)
            onNodeWithTag("thread-summary-scheduled-nightly").assertTextContains("每天 · 09:00", substring = true)
            onNodeWithTag("thread-summary-scheduled-weekly").assertTextContains("每周三", substring = true)
            onNodeWithTag("thread-summary-scheduled-paused").assertTextContains("已暂停", substring = true)
            onNodeWithTag("thread-summary-scheduled-nightly").performClick()
            assertEquals(listOf("nightly"), opened)
        }
    }

    @Test
    fun scheduledSectionShowsTheReloadActionOnReadError() {
        var retried = 0
        runSkikoComposeUiTest(size = Size(420f, 640f)) {
            setContent {
                HarnessTheme {
                    ThreadSummaryPanel(
                        load = ThreadSummaryLoad.Unavailable("no git"),
                        onOpenReview = null, onRefresh = null, onClose = null,
                        scheduledTasks = emptyList(),
                        scheduledError = true,
                        onRetryScheduled = { retried++ },
                    )
                }
            }
            onNodeWithTag("thread-summary-section-scheduled").assertTextContains("定时任务", substring = true)
            onNodeWithTag("thread-summary-section-scheduled-count", useUnmergedTree = true).assertTextEquals("0")
            onNodeWithTag("thread-summary-scheduled-retry").assertTextContains("重试加载已安排的任务", substring = true)
            onNodeWithTag("thread-summary-scheduled-retry").performClick()
            assertEquals(1, retried)
        }
    }

    private fun scheduled(
        id: String,
        name: String,
        schedule: ScheduledRule,
        status: ScheduledTaskStatus = ScheduledTaskStatus.Active,
    ) = ScheduledTask(
        id = id, revision = 1, name = name, prompt = "prompt",
        target = ScheduledTarget.Heartbeat(SessionId("session-1")),
        schedule = schedule, model = null, status = status,
    )

    private companion object {
        const val MULTI_FILE_DIFF = "diff --git a/src/old.kt b/src/new.kt\n" +
            "similarity index 90%\n" +
            "rename from src/old.kt\n" +
            "rename to src/new.kt\n" +
            "--- a/src/old.kt\n" +
            "+++ b/src/new.kt\n" +
            "@@ -1,2 +1,2 @@\n" +
            "-old\n" +
            "+new\n" +
            " same\n" +
            "diff --git a/src/app.kt b/src/app.kt\n" +
            "--- a/src/app.kt\n" +
            "+++ b/src/app.kt\n" +
            "@@ -1,1 +1,2 @@\n" +
            " base\n" +
            "+extra\n"
    }
}
