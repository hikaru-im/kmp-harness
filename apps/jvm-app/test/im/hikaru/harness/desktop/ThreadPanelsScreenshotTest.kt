@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package im.hikaru.harness.desktop

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.harness.client.connection.BackgroundProcessLifecycle
import im.hikaru.harness.client.connection.BackgroundInputState
import im.hikaru.harness.client.connection.SessionId
import im.hikaru.harness.client.panels.*
import im.hikaru.harness.ui.foundation.HarnessTheme
import im.hikaru.harness.ui.foundation.HarnessThemeMode
import java.io.File
import javax.imageio.ImageIO
import org.jetbrains.skia.EncodedImageFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Production panel viewports only. All data is test-local; no Host or provider is contacted. */
class ThreadPanelsScreenshotTest {
    @Test
    fun captureBackgroundOutputMatrix() {
        val fixture = ThreadPanelsUiFixtures
        capture(listOf(
            "output-running" to fixture.terminal(fixture.output()),
            "output-exited" to fixture.terminal(fixture.output().copy(
                lifecycle = BackgroundProcessLifecycle.Exited(7), buffer = "Task finished\nExit status: 7",
            )),
            "output-no-output" to fixture.terminal(fixture.output().copy(buffer = "")),
            "output-truncated" to fixture.terminal(fixture.output().copy(truncated = true)),
            "output-long-line" to fixture.terminal(fixture.output().copy(
                buffer = "LONG-LINE-START " + "path/with-a-long-segment/".repeat(35) + " LONG-LINE-END",
            )),
            "output-empty" to fixture.terminalLoad(PanelLoad.Empty(fixture.scope)),
            "output-loading" to fixture.terminalLoad(PanelLoad.Loading(fixture.scope)),
            "output-error" to fixture.terminalLoad(PanelLoad.Error(fixture.scope, "Fixture output read failed. Retry is available.")),
            "output-unavailable" to fixture.terminalLoad(PanelLoad.Unavailable(
                fixture.scope, PanelUnavailableReason.TerminalServiceMissing,
            )),
        ))
    }

    @Test
    fun captureBackgroundControlsMatrix() {
        val fixture = ThreadPanelsUiFixtures
        val output = fixture.output().copy(inputState = BackgroundInputState.Open)
        fun controls(value: TerminalControls) = fixture.terminal(output).copy(terminalControls = value)
        capture(listOf(
            "input-open" to controls(TerminalControls(output.key)),
            "input-pending" to controls(TerminalControls(output.key, pending = true, lastKind = TerminalOperationKind.Input)),
            "input-unknown" to controls(TerminalControls(output.key, inputBlocked = true,
                lastKind = TerminalOperationKind.Input, lastOutcome = TerminalOperationOutcome.Unknown,
                message = "输入结果未知，已禁止重发；仍可取消当前进程。")),
            "input-eof" to controls(TerminalControls(output.key, inputBlocked = true,
                lastKind = TerminalOperationKind.Eof, lastOutcome = TerminalOperationOutcome.Applied,
                message = "已关闭 stdin（EOF）。")),
            "input-cancel-requested" to controls(TerminalControls(output.key, inputBlocked = true, cancelBlocked = true,
                lastKind = TerminalOperationKind.Cancel, lastOutcome = TerminalOperationOutcome.Applied,
                message = "已请求取消；刷新可查看退出状态。")),
        ), terminalControlsEnabled = true)
    }

    @Test
    fun captureBackgroundSelectionMatrix() {
        val fixture = ThreadPanelsUiFixtures
        val output = fixture.output().copy(inputState = BackgroundInputState.Open)
        val rows = listOf(
            BackgroundTerminalSummary(output.key, fixture.longTitle, BackgroundProcessLifecycle.Running, inputState = BackgroundInputState.Open),
            BackgroundTerminalSummary(BackgroundTerminalKey(fixture.scope, "fixture-exited", "fixture-incarnation"),
                "Finished fixture", BackgroundProcessLifecycle.Exited(0)),
            BackgroundTerminalSummary(BackgroundTerminalKey(fixture.scope, "fixture-unknown", "fixture-incarnation"),
                "Status fixture", BackgroundProcessLifecycle.Unknown),
        )
        val selected = fixture.terminal(output).copy(terminalInventory = rows, selectedTerminalKey = output.key)
        capture(listOf(
            "selection-running" to selected,
            "selection-output-loading" to selected.copy(terminal = PanelLoad.Loading(fixture.scope)),
            "selection-process-missing" to selected.copy(terminalInventory = rows.drop(1),
                terminal = PanelLoad.Error(fixture.scope, "所选后台进程已过期或已被替换，请选择现有进程。")),
        ), terminalControlsEnabled = true)
    }

    @Test
    fun captureSubagentOverviewMatrix() {
        val fixture = ThreadPanelsUiFixtures
        capture(listOf(
            "subagents-active-unknown-done" to fixture.overview(listOf(
                fixture.child("active", ChildAgentStatus.Active, fixture.longTitle),
                fixture.child("done", ChildAgentStatus.Done, "Completed review"),
                fixture.child("unknown", ChildAgentStatus.Unknown, "Idle status remains unknown"),
            )),
            "subagents-empty" to fixture.overviewLoad(PanelLoad.Empty(fixture.scope)),
            "subagents-loading" to fixture.overviewLoad(PanelLoad.Loading(fixture.scope)),
            "subagents-error" to fixture.overviewLoad(PanelLoad.Error(fixture.scope, "Fixture descendants read failed. Refresh to retry.")),
            "subagents-unavailable" to fixture.overviewLoad(PanelLoad.Unavailable(
                fixture.scope, PanelUnavailableReason.ChildrenServiceMissing,
            )),
        ))
    }

    @Test
    fun captureSubagentDetailMatrix() {
        val fixture = ThreadPanelsUiFixtures
        val child = fixture.child("detail", label = fixture.longTitle)
        capture(listOf(
            "detail-normal" to fixture.selected(child, fixture.history(child)),
            "detail-loading" to fixture.selected(child, PanelLoad.Loading(fixture.scope)),
            "detail-error" to fixture.selected(child, PanelLoad.Error(fixture.scope, "Fixture history read failed. Refresh history to retry.")),
            "detail-empty" to fixture.selected(child, PanelLoad.Empty(fixture.scope)),
            "detail-unavailable" to fixture.selected(
                child.copy(historyAvailability = ChildHistoryAvailability.Unavailable),
                PanelLoad.Unavailable(fixture.scope, PanelUnavailableReason.ChildHistoryUnavailable),
            ),
        ))
    }

    private fun capture(cases: List<Pair<String, ThreadPanelsState>>, terminalControlsEnabled: Boolean = false) {
        val directory = File(System.getenv("HARNESS_THREAD_PANELS_CAPTURE_DIR")?.takeIf { it.isNotBlank() }
            ?: File(System.getProperty("java.io.tmpdir"), "harness-thread-panels-captures").path)
        check(directory.isDirectory || directory.mkdirs()) { "Cannot create capture directory: $directory" }
        for ((slug, state) in cases) for ((width, height) in listOf(640 to 800, 320 to 600)) {
            for ((themeSlug, theme) in listOf("light" to HarnessThemeMode.Light, "dark" to HarnessThemeMode.Dark)) {
                val file = File(directory, "$slug-panel-viewport-$themeSlug-${width}x$height.png")
                val scene = ImageComposeScene(width = width, height = height, density = Density(1f), content = {
                    HarnessTheme(themeMode = theme) {
                        ThreadPanels(state, onAction = {}, onRefreshTerminal = {},
                            onRefreshOverview = {}, onRefreshDetail = {},
                            onTerminalInput = if (terminalControlsEnabled) { _, _ -> } else null,
                            onTerminalEof = if (terminalControlsEnabled) { _ -> } else null,
                            onTerminalCancel = if (terminalControlsEnabled) { _ -> } else null)
                    }
                })
                try {
                    scene.render().close()
                    scene.render().use { image ->
                        requireNotNull(image.encodeToData(EncodedImageFormat.PNG)).use { data ->
                            file.writeBytes(data.bytes)
                        }
                    }
                    val decoded = requireNotNull(ImageIO.read(file)) { "Unreadable PNG: $file" }
                    assertEquals(width, decoded.width, file.name)
                    assertEquals(height, decoded.height, file.name)
                    assertTrue(file.length() > 0, file.name)
                } finally {
                    scene.close()
                }
            }
        }
    }
}

/** Shared only by these two test files; these are snapshots, not producer receipts. */
internal object ThreadPanelsUiFixtures {
    val scope = ThreadPanelScope(HostId("thread-panels-test-host"), SessionId("parent"))
    const val longTitle = "Cross-platform terminal and subagent review — 紧凑布局下的超长标题与可操作状态验收"

    fun output() = BackgroundTerminalOutput(
        key = BackgroundTerminalKey(scope, "fixture-process", "fixture-incarnation"),
        buffer = "[fixture] Reading project files\n[fixture] Waiting for task completion\n输出包含中英文，面板仅供读取。",
        revision = 1, command = "fixture-command --review-layout --no-network",
        lifecycle = BackgroundProcessLifecycle.Running, cwd = "/fixture/project with spaces",
    )

    fun terminal(output: BackgroundTerminalOutput) = terminalLoad(PanelLoad.Ready(scope, output))
    fun terminalLoad(load: PanelLoad<BackgroundTerminalOutput>) = ThreadPanelsState(scope, terminal = load)

    fun child(id: String, status: ChildAgentStatus = ChildAgentStatus.Active, label: String = "Child $id",
        canInteract: Boolean = false) = ChildAgentSummary(
        key = ChildAgentKey(scope, SessionId(id)), verifiedAncestorPath = listOf(scope.conversationId, SessionId(id)),
        status = status, canInteract = canInteract, historyAvailability = ChildHistoryAvailability.Available,
        displayName = label, objective = "Review layout, current status, and transcript readability across panel sizes.",
        model = "fixture-model-with-a-long-name", reasoningEffort = "high",
    )

    fun overview(children: List<ChildAgentSummary>) = overviewLoad(PanelLoad.Ready(scope, SubagentOverview(scope, children)))
    fun overviewLoad(load: PanelLoad<SubagentOverview>) = ThreadPanelsState(
        scope, selectedTab = ThreadPanelTab.Subagents, subagents = load,
    )

    fun history(child: ChildAgentSummary, canInteract: Boolean = false): PanelLoad<ChildAgentDetail> = PanelLoad.Ready(
        scope, ChildAgentDetail(child.key, listOf(
            ChildTranscriptEntry("user", ChildTranscriptRole.User, "检查终端与子Agent面板。保留真实状态和只读边界。"),
            ChildTranscriptEntry("assistant", ChildTranscriptRole.Assistant,
                "The panel shows available history without inferring completion from idle. " +
                    "A long paragraph should wrap inside a compact viewport. ".repeat(4)),
            ChildTranscriptEntry("tool", ChildTranscriptRole.Tool, "fixture/layout.kt\nRead complete: no external request."),
        ), canInteract),
    )

    fun selected(child: ChildAgentSummary, detail: PanelLoad<ChildAgentDetail>) =
        overview(listOf(child)).reduce(ThreadPanelAction.SelectChild(child.key)).let { selected ->
            selected.reduce(ThreadPanelAction.ReceiveDetail(child.key, selected.detailGeneration, detail))
        }
}
