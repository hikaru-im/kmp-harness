@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package im.hikaru.harness.desktop

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import im.hikaru.harness.client.connection.SessionId
import im.hikaru.harness.client.scheduling.*
import kotlinx.coroutines.CompletableDeferred
import kotlin.test.*

class ScheduledPageInteractionTest {
    @Test fun navigatorSearchSelectionAndNewUseMouseWithoutMutatingEffectiveTasks() =
        runSkikoComposeUiTest(size = Size(1440f, 900f)) {
            ScheduledPageTestSupport().use { f ->
                setContent { ScheduledPageTestSurface(f.repository) }
                onNodeWithTag("scheduled-toggle-search").scheduledMouseClick()
                onNodeWithTag("scheduled-search").performTextReplacement("Task 2")
                onNodeWithTag("scheduled-task:task-1").assertDoesNotExist()
                onNodeWithTag("scheduled-task:task-2").scheduledMouseClick()
                onNodeWithTag("scheduled-detail-name").assertTextEquals("Task 2")
                onNodeWithTag("scheduled-editor").assertDoesNotExist()
                onNodeWithTag("scheduled-new-task").scheduledMouseClick()
                onNodeWithTag("scheduled-editor").assertExists()
                onNodeWithTag("scheduled-task:task-2").assertIsNotEnabled()
                onNodeWithTag("scheduled-new-task").assertIsNotEnabled()
                runOnIdle {
                    assertEquals("task-2", f.repository.state.value.selectedTaskId)
                    assertEquals(f.tasks, f.repository.state.value.effective!!.tasks)
                    assertEquals(0, f.store.writes.get())
                }
            }
        }

    @Test fun allSixExampleCardsOnlyPrefillCancelableDrafts() = runSkikoComposeUiTest(size = Size(600f, 800f)) {
        ScheduledPageTestSupport().use { f ->
            setContent { ScheduledPageTestSurface(f.repository) }
            for (id in listOf("workspace", "changes", "notes", "todos", "docs", "review")) {
                onNodeWithTag("scheduled-template:$id").scheduledMouseClick()
                onNodeWithTag("scheduled-editor").assertExists()
                runOnIdle {
                    val draft = assertNotNull(f.repository.state.value.draft)
                    assertTrue(draft.name.isNotBlank() && draft.prompt.isNotBlank())
                    assertNull(draft.target)
                    assertNull(draft.model)
                    assertEquals(f.tasks, f.repository.state.value.effective!!.tasks)
                    assertEquals(f.runs, f.repository.state.value.effective!!.runs)
                    assertEquals(0, f.store.writes.get())
                    assertEquals(0, f.connection.executionCalls.get())
                }
                onNodeWithTag("scheduled-save").performScrollTo().assertIsNotEnabled()
                onNodeWithTag("scheduled-cancel").scheduledMouseClick()
            }
            onNodeWithTag("scheduled-landing").assertExists()
        }
    }

    @Test fun draftModificationCancelCreateSaveAndDetailEditKeepTasksPaused() =
        runSkikoComposeUiTest(size = Size(600f, 800f)) {
            ScheduledPageTestSupport(includeRuns = false).use { f ->
                setContent { ScheduledPageTestSurface(f.repository) }
                onNodeWithTag("scheduled-new-task").scheduledMouseClick()
                onNodeWithTag("scheduled-name").performTextReplacement("Discard me")
                onNodeWithTag("scheduled-cancel").scheduledMouseClick()
                runOnIdle { assertEquals(0, f.store.writes.get()); assertNull(f.repository.state.value.draft) }
                onNodeWithTag("scheduled-new-task").scheduledMouseClick()
                onNodeWithTag("scheduled-name").performTextReplacement("Created by mouse")
                onNodeWithTag("scheduled-prompt").performScrollTo().performTextReplacement("Review this workspace only.")
                onNodeWithTag("scheduled-target").scheduledMouseClick()
                onNodeWithTag("scheduled-target-option:0").scheduledMouseClick()
                onNodeWithTag("scheduled-model").scheduledMouseClick()
                onNodeWithTag("scheduled-model-option:1").scheduledMouseClick()
                onNodeWithTag("scheduled-frequency").scheduledMouseClick()
                onNodeWithTag("scheduled-frequency-option:0").scheduledMouseClick()
                onNodeWithTag("scheduled-interval").performScrollTo().performTextReplacement("2")
                onNodeWithTag("scheduled-save").scheduledMouseClick()
                onNodeWithTag("scheduled-detail-name").assertTextEquals("Created by mouse")
                runOnIdle {
                    val task = f.store.snapshot.tasks.last()
                    assertEquals(f.target, task.target)
                    assertEquals(f.context.models[1], task.model)
                    assertEquals(ScheduledTaskStatus.Paused, task.status)
                    assertEquals(2, (task.schedule as ScheduledRule.Hourly).intervalHours)
                }
                onNodeWithTag("scheduled-edit").scheduledMouseClick()
                onNodeWithTag("scheduled-prompt").performScrollTo().performTextReplacement("Canceled edit")
                onNodeWithTag("scheduled-cancel").scheduledMouseClick()
                onNodeWithTag("scheduled-detail-prompt").assertTextEquals("Review this workspace only.")
                onNodeWithTag("scheduled-edit").scheduledMouseClick()
                onNodeWithTag("scheduled-prompt").performScrollTo().performTextReplacement("Persisted edit")
                onNodeWithTag("scheduled-save").scheduledMouseClick()
                runOnIdle {
                    assertEquals("Persisted edit", f.store.snapshot.tasks.last().prompt)
                    assertEquals(2L, f.store.snapshot.tasks.last().revision)
                    assertEquals(ScheduledTaskStatus.Paused, f.store.snapshot.tasks.last().status)
                    assertEquals(2, f.store.writes.get())
                    assertTrue(f.store.snapshot.runs.isEmpty())
                }
            }
        }

    @Test fun unavailableExecutionAndDeleteConfirmationDoNotCancelOrCleanHistory() =
        runSkikoComposeUiTest(size = Size(600f, 800f)) {
            ScheduledPageTestSupport().use { f ->
                setContent { ScheduledPageTestSurface(f.repository) }
                onNodeWithTag("scheduled-task:task-1").scheduledMouseClick()
                onNodeWithTag("scheduled-pause").performScrollTo().assertIsNotEnabled().performMouseInput { click() }
                onNodeWithTag("scheduled-run-now").performScrollTo().assertIsNotEnabled().performMouseInput { click() }
                onNodeWithTag("scheduled-execution-unavailable").assertExists()
                onNodeWithTag("scheduled-delete").scheduledMouseClick()
                onNodeWithText("这将永久删除定时任务，并停止日后的运行；已开始的任务不会被取消，会话历史也不会被清理。")
                    .assertIsDisplayed()
                onNodeWithTag("scheduled-delete-confirmation:cancel").scheduledMouseClick()
                onNodeWithTag("scheduled-delete-confirmation").assertDoesNotExist()
                runOnIdle { assertEquals(0, f.store.writes.get()); assertEquals(f.tasks, f.store.snapshot.tasks) }
                onNodeWithTag("scheduled-delete").scheduledMouseClick()
                onNodeWithTag("scheduled-delete-confirmation:confirm").scheduledMouseClick()
                onNodeWithTag("scheduled-task:task-1").assertDoesNotExist()
                onNodeWithTag("scheduled-show-history").scheduledMouseClick()
                onNodeWithTag("scheduled-page-run:confirmed").assertExists()
                runOnIdle {
                    assertEquals(f.runs, f.store.snapshot.runs)
                    assertEquals(listOf("task-1"), f.store.snapshot.deletedTaskIds)
                    assertEquals(0, f.connection.executionCalls.get())
                }
            }
        }

    @Test fun deleteDialogIsModalAndARejectedWriteKeepsItActionable() =
        runSkikoComposeUiTest(size = Size(600f, 800f)) {
            ScheduledPageTestSupport().use { f ->
                setContent { ScheduledPageTestSurface(f.repository) }
                onNodeWithTag("scheduled-task:task-1").scheduledMouseClick()
                onNodeWithTag("scheduled-delete").scheduledMouseClick()
                onNodeWithTag("scheduled-delete-confirmation").assertIsDisplayed()
                onNodeWithText("删除 Task 1？").assertIsDisplayed()
                onNodeWithText("删除定时任务").assertIsDisplayed()
                // A rejected write must keep the modal open and surface the real reason instead of closing.
                f.store.failWrite = true
                onNodeWithTag("scheduled-delete-confirmation:confirm").scheduledMouseClick()
                onNodeWithTag("scheduled-delete-confirmation").assertIsDisplayed()
                onNodeWithTag("scheduled-delete-confirmation:error").assertExists()
                runOnIdle {
                    assertEquals(f.tasks, f.repository.state.value.effective!!.tasks)
                    assertEquals(1, f.store.writes.get())
                }
            }
        }

    @Test fun historyNavigatesOnlyConfirmedSessionAndPreservesUnknownEvidence() =
        runSkikoComposeUiTest(size = Size(600f, 800f)) {
            ScheduledPageTestSupport().use { f ->
                val opened = mutableListOf<SessionId>()
                setContent { ScheduledPageTestSurface(f.repository, onOpenRun = { opened += it }) }
                onNodeWithTag("scheduled-execution-model").assertExists()
                    .assertTextEquals(ScheduledLocalExecutionModel)
                onNodeWithTag("scheduled-task:task-1").scheduledMouseClick()
                onNodeWithTag("scheduled-run-blocked").assertExists()
                onNodeWithTag("scheduled-blocked-runs").performScrollTo().assertIsDisplayed()
                onNodeWithTag("scheduled-page-open-run:intent").performScrollTo().assertIsNotEnabled().performMouseInput { click() }
                onNodeWithTag("scheduled-page-open-run:confirmed").scheduledMouseClick()
                onNodeWithText("Unknown：缺少持久化 TurnEnd；不能重复投递。").assertExists()
                runOnIdle {
                    assertEquals(listOf(f.confirmedSession), opened)
                    assertEquals(f.runs, f.repository.state.value.effective!!.runs)
                    assertEquals(0, f.store.writes.get())
                }
            }
        }

    @Test fun failedSaveRetainsLatestDraftEffectiveSnapshotAndNoExecution() =
        runSkikoComposeUiTest(size = Size(1440f, 900f)) {
            ScheduledPageTestSupport().use { f ->
                f.store.failWrite = true
                setContent { ScheduledPageTestSurface(f.repository) }
                onNodeWithTag("scheduled-task:task-1").scheduledMouseClick()
                onNodeWithTag("scheduled-edit").scheduledMouseClick()
                onNodeWithTag("scheduled-prompt").performTextReplacement("Keep this unsaved prompt")
                onNodeWithTag("scheduled-save").scheduledMouseClick()
                onNodeWithTag("scheduled-mutation-error").assertExists()
                onNodeWithTag("scheduled-prompt").assertTextEquals("Keep this unsaved prompt")
                onNodeWithTag("scheduled-task:task-2").assertIsNotEnabled()
                runOnIdle {
                    assertEquals("Keep this unsaved prompt", f.repository.state.value.draft!!.prompt)
                    assertEquals(f.tasks, f.repository.state.value.effective!!.tasks)
                    assertEquals(f.runs, f.repository.state.value.effective!!.runs)
                    assertEquals(1L, f.store.snapshot.revision)
                    assertEquals(0, f.connection.executionCalls.get())
                }
            }
        }

    @Test fun savingLocksSelectionInputsAndCancelThenReportsDurabilityWarning() =
        runSkikoComposeUiTest(size = Size(600f, 800f)) {
            ScheduledPageTestSupport(includeRuns = false).use { f ->
                val gate = CompletableDeferred<Unit>()
                f.store.writeGate = gate
                f.store.warning = "配置已提交，但目录同步未确认。"
                setContent { ScheduledPageTestSurface(f.repository) }
                onNodeWithTag("scheduled-task:task-1").scheduledMouseClick()
                onNodeWithTag("scheduled-edit").scheduledMouseClick()
                onNodeWithTag("scheduled-prompt").performScrollTo().performTextReplacement("Saving prompt")
                onNodeWithTag("scheduled-save").scheduledMouseClick()
                onNodeWithTag("scheduled-saving").assertExists()
                onNodeWithTag("scheduled-task:task-2").assertIsNotEnabled().performMouseInput { click() }
                onNodeWithTag("scheduled-new-task").assertIsNotEnabled()
                onNodeWithTag("scheduled-name").assertIsNotEnabled()
                onNodeWithTag("scheduled-cancel").assertIsNotEnabled()
                onNodeWithTag("scheduled-save").assertIsNotEnabled()
                runOnIdle {
                    assertEquals("task-1", f.repository.state.value.selectedTaskId)
                    gate.complete(Unit)
                }
                onNodeWithTag("scheduled-durability-warning").assertTextEquals("配置已提交，但目录同步未确认。")
                runOnIdle {
                    assertNull(f.repository.state.value.draft)
                    assertEquals("Saving prompt", f.store.snapshot.tasks.first().prompt)
                    assertEquals(ScheduledTaskStatus.Paused, f.store.snapshot.tasks.first().status)
                    assertEquals(0, f.connection.executionCalls.get())
                }
            }
        }

    @Test fun changedDraftAndContextClosePopupAndRejectOldOptionCallback() =
        runSkikoComposeUiTest(size = Size(1440f, 900f)) {
            ScheduledPageTestSupport().use { f ->
                f.beginEdit()
                setContent { ScheduledPageTestSurface(f.repository) }
                onNodeWithTag("scheduled-target").scheduledMouseClick()
                val oldOption = onNodeWithTag("scheduled-target-option:1").fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
                runOnIdle { f.repository.updateDraft { it.copy(name = "New draft revision") } }
                onNodeWithTag("scheduled-target-menu").assertDoesNotExist()
                runOnIdle { oldOption(); assertEquals(f.target, f.repository.state.value.draft!!.target) }
                onNodeWithTag("scheduled-model").scheduledMouseClick()
                val oldModel = onNodeWithTag("scheduled-model-option:1").fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
                runOnIdle { f.repository.bind(f.scope, f.context.copy(models = emptyList())) }
                onNodeWithTag("scheduled-model-menu").assertDoesNotExist()
                onNodeWithTag("scheduled-model").assertIsNotEnabled()
                onNodeWithTag("scheduled-save").performScrollTo().assertIsNotEnabled()
                runOnIdle { oldModel(); assertEquals(f.model, f.repository.state.value.draft!!.model); assertEquals(0, f.store.writes.get()) }
            }
        }

    @Test fun scopeAndDisconnectHideOldHistoryAndRejectSavedOpenCallback() =
        runSkikoComposeUiTest(size = Size(1440f, 900f)) {
            ScheduledPageTestSupport().use { f ->
                val opened = mutableListOf<SessionId>()
                f.selectFirst()
                setContent { ScheduledPageTestSurface(f.repository, onOpenRun = { opened += it }) }
                val oldOpen = onNodeWithTag("scheduled-page-open-run:confirmed").performScrollTo()
                    .fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
                runOnIdle { f.repository.bind(f.scope.copy(generation = 2), f.context, connected = false); oldOpen() }
                onNodeWithTag("scheduled-task:task-1").assertDoesNotExist()
                onNodeWithTag("scheduled-page-run:confirmed").assertDoesNotExist()
                onNodeWithTag("scheduled-detail").assertDoesNotExist()
                onNodeWithTag("scheduled-disconnected").assertExists()
                onNodeWithTag("scheduled-new-task").assertIsNotEnabled()
                runOnIdle { assertTrue(opened.isEmpty()); assertNull(f.repository.runSessionRef("confirmed")) }
            }
        }

    @Test fun replacingRepositoryClosesPickerAndRejectsOldDraftCallback() =
        runSkikoComposeUiTest(size = Size(1440f, 900f)) {
            ScheduledPageTestSupport().use { first -> ScheduledPageTestSupport().use { second ->
                first.beginEdit()
                second.beginEdit()
                var repository by mutableStateOf(first.repository)
                setContent { ScheduledPageTestSurface(repository) }
                onNodeWithTag("scheduled-target").scheduledMouseClick()
                val oldOption = onNodeWithTag("scheduled-target-option:1").fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
                runOnIdle { repository = second.repository }
                onNodeWithTag("scheduled-target-menu").assertDoesNotExist()
                runOnIdle {
                    oldOption()
                    assertEquals(first.target, first.repository.state.value.draft!!.target)
                    assertEquals(second.target, second.repository.state.value.draft!!.target)
                }
            } }
        }

    @Test fun longPickersStayBoundedAndNavigatorHeaderSurvivesIndependentScroll() =
        runSkikoComposeUiTest(size = Size(600f, 800f)) {
            ScheduledPageTestSupport(taskCount = 24, longFields = true).use { f ->
                setContent { ScheduledPageTestSurface(f.repository) }
                val header = onNodeWithTag("scheduled-navigator-header").fetchSemanticsNode().boundsInRoot
                onNodeWithTag("scheduled-task:task-24").scheduledMouseClick()
                assertEquals(header, onNodeWithTag("scheduled-navigator-header").fetchSemanticsNode().boundsInRoot)
                val range = onNodeWithTag("scheduled-navigator-list").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
                assertTrue(range.value() > 0f)
                onNodeWithTag("scheduled-edit").scheduledMouseClick()
                onNodeWithTag("scheduled-target-value").assertTextContains(f.target.let { (it.destination as ScheduledDestination.Cwd).path }, substring = true)
                onNodeWithTag("scheduled-target-value-copy").scheduledMouseClick()
                onNodeWithTag("scheduled-target").scheduledMouseClick()
                val bounds = onNodeWithTag("scheduled-target-menu").fetchSemanticsNode().boundsInRoot
                assertTrue(bounds.width in 1f..600f && bounds.height in 1f..281f)
                onNodeWithTag("scheduled-target-option:0").scheduledMouseClick()
                onNodeWithTag("scheduled-model").scheduledMouseClick()
                val modelBounds = onNodeWithTag("scheduled-model-menu").fetchSemanticsNode().boundsInRoot
                assertTrue(modelBounds.width in 1f..600f && modelBounds.height in 1f..281f)
                onNodeWithTag("scheduled-model-option:0").scheduledMouseClick()
                onNodeWithTag("scheduled-model-value").assertTextContains(f.model.modelId, substring = true)
            }
        }

    @Test fun absentHostCatalogCannotBeReplacedWithInventedTargetOrModel() =
        runSkikoComposeUiTest(size = Size(600f, 800f)) {
            ScheduledPageTestSupport(includeRuns = false).use { f ->
                f.repository.bind(f.scope, ScheduledValidationContext(emptyList(), emptyList()))
                setContent { ScheduledPageTestSurface(f.repository) }
                onNodeWithTag("scheduled-new-task").assertIsNotEnabled()
                onNodeWithTag("scheduled-template:workspace").assertIsNotEnabled()
                onNodeWithTag("scheduled-task:task-1").scheduledMouseClick()
                onNodeWithTag("scheduled-catalog-unavailable").assertExists()
                onNodeWithTag("scheduled-edit").scheduledMouseClick()
                onNodeWithTag("scheduled-target").performScrollTo().assertIsNotEnabled()
                onNodeWithTag("scheduled-model").performScrollTo().assertIsNotEnabled()
                onNodeWithTag("scheduled-save").performScrollTo().assertIsNotEnabled()
                runOnIdle { assertEquals(0, f.store.writes.get()); assertEquals(f.tasks, f.store.snapshot.tasks) }
            }
        }
}
