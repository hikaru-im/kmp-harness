package im.hikaru.harness.desktop

import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import im.hikaru.harness.api.gateway.ApiGateway
import im.hikaru.harness.client.conversation.ConversationHostScope
import im.hikaru.harness.client.connection.BackgroundInputState
import im.hikaru.harness.client.panels.*
import im.hikaru.harness.desktop.connection.LocalConnection
import im.hikaru.harness.ui.foundation.HarnessButton
import im.hikaru.harness.ui.foundation.HarnessTheme
import im.hikaru.harness.ui.foundation.HarnessThemeMode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlin.test.*

/** Real panel/reducer/controller seams; repository reads alone are controlled, with no network. */
@OptIn(ExperimentalTestApi::class)
class ThreadPanelsInteractionTest {
    @Test
    fun compactProcessSelectRoutesInputToExactKeyAndDoesNotReuseAnotherDraft() {
        val fixture = ThreadPanelsUiFixtures
        val a = fixture.output().copy(command = "command-a " + "long-argument ".repeat(30), inputState = BackgroundInputState.Open)
        val b = a.copy(key = a.key.copy(processId = "process-b", producerIncarnation = "producer-b"), command = "command-b", buffer = "B output")
        val rows = listOf(a, b).map { BackgroundTerminalSummary(it.key, it.command, it.lifecycle,
            it.cwd, inputState = it.inputState) }
        val repository = PanelsInteractionRepository().apply {
            terminalSelectionRead = { key ->
                val output = if (key == b.key) b else a
                BackgroundTerminalSelection(fixture.scope, rows, output.key, PanelLoad.Ready(fixture.scope, output))
            }
        }
        val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val controller = ThreadPanelsController(coroutineScope, repository)
        val owner = ThreadPanelsOwner(LocalConnection(ApiGateway()),
            ConversationHostScope(fixture.scope.hostId, 1, null), fixture.scope.conversationId)
        controller.bind(owner)
        controller.dispatch(ThreadPanelAction.OpenTab(fixture.scope, ThreadPanelTab.BackgroundOutput))
        val actions = mutableListOf<ThreadPanelAction>()
        val calls = mutableListOf<Triple<BackgroundTerminalKey, TerminalOperationKind, String>>()
        repository.operation = { key, kind, input ->
            calls += Triple(key, kind, input)
            TerminalOperationResult(TerminalOperationOutcome.Unknown, "Uncertain B input")
        }
        try {
            runSkikoComposeUiTest(size = Size(320f, 600f)) {
                setContent {
                    val state by controller.state.collectAsState()
                    HarnessTheme { state?.let { ThreadPanels(it, { action -> actions += action; controller.dispatch(action) },
                        onRefreshTerminal = { controller.refreshTerminal() },
                        onTerminalInput = { key, input -> controller.operateTerminal(key, TerminalOperationKind.Input, input) },
                        onTerminalEof = { controller.operateTerminal(it, TerminalOperationKind.Eof) },
                        onTerminalCancel = { controller.operateTerminal(it, TerminalOperationKind.Cancel) }) } }
                }
                val selector = onNodeWithTag("terminal-process-select").assertIsDisplayed()
                val bounds = selector.getUnclippedBoundsInRoot()
                assertTrue(bounds.left.value >= 0f && bounds.right.value <= 320f)
                selector.assertTextEquals(a.command + " · 运行中")
                onNodeWithTag("terminal-input").performTextInput("A draft")
                selector.performClick()
                onNodeWithTag("menu:1").assertTextContains("command-b · 运行中").performClick()
                onNodeWithTag("terminal-input").assertTextEquals("")
                runOnIdle {
                    assertEquals(listOf(b.key), actions.filterIsInstance<ThreadPanelAction.SelectTerminal>().map { it.key })
                    assertEquals(b.key, controller.state.value!!.selectedTerminalKey)
                    assertTrue(calls.isEmpty())
                }
                onNodeWithTag("terminal-input").performTextInput("B 输入")
                onNodeWithTag("发送一行").performClick()
                onNodeWithTag("terminal-input").assertTextEquals("B 输入")
                onNodeWithTag("发送一行").assertIsNotEnabled()
                onNodeWithTag("terminal-process-select").performClick()
                onNodeWithTag("menu:0").performClick()
                onNodeWithTag("terminal-input").assertTextEquals("").assertIsEnabled()
                onNodeWithTag("terminal-process-select").performClick()
                onNodeWithTag("menu:1").performClick()
                onNodeWithTag("发送一行").assertIsNotEnabled()
                onNodeWithTag("terminal-input").assertTextEquals("")
                onNodeWithTag("关闭后台终端").performClick()
                runOnIdle { controller.dispatch(ThreadPanelAction.OpenTab(fixture.scope, ThreadPanelTab.BackgroundOutput)) }
                onNodeWithTag("terminal-process-select").assertTextEquals("command-b · 运行中")
                onNodeWithTag("发送一行").assertIsNotEnabled()
                runOnIdle { assertEquals(listOf(Triple(b.key, TerminalOperationKind.Input, "B 输入\n")), calls) }
            }
        } finally { controller.close(); coroutineScope.cancel() }
    }

    @Test
    fun sameKeyRefreshPreservesUnknownDraftAfterEarlierDeliveryReceipt() {
        val fixture = ThreadPanelsUiFixtures
        val output = fixture.output().copy(inputState = BackgroundInputState.Open)
        val repository = PanelsInteractionRepository().apply { terminalRead = { PanelLoad.Ready(fixture.scope, output) } }
        val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val controller = ThreadPanelsController(coroutineScope, repository)
        val owner = ThreadPanelsOwner(LocalConnection(ApiGateway()),
            ConversationHostScope(fixture.scope.hostId, 1, null), fixture.scope.conversationId)
        controller.bind(owner)
        controller.dispatch(ThreadPanelAction.OpenTab(fixture.scope, ThreadPanelTab.BackgroundOutput))
        var operations = 0
        repository.operation = { key, kind, _ ->
            assertEquals(output.key, key)
            assertEquals(TerminalOperationKind.Input, kind)
            operations++
            TerminalOperationResult(if (operations == 1) TerminalOperationOutcome.Applied else TerminalOperationOutcome.Unknown, "reply")
        }
        try {
            runSkikoComposeUiTest(size = Size(320f, 600f)) {
                setContent {
                    val state by controller.state.collectAsState()
                    HarnessTheme { state?.let { ThreadPanels(it, controller::dispatch,
                        onRefreshTerminal = { controller.refreshTerminal() },
                        onTerminalInput = { key, input -> controller.operateTerminal(key, TerminalOperationKind.Input, input) }) } }
                }
                onNodeWithTag("terminal-input").performTextInput("confirmed")
                onNodeWithTag("发送一行").performClick()
                onNodeWithTag("terminal-input").assertTextEquals("")
                onNodeWithTag("terminal-input").performTextInput("unknown draft")
                onNodeWithTag("发送一行").performClick()
                onNodeWithTag("terminal-input").assertTextEquals("unknown draft").assertIsNotEnabled()
                val gate = CompletableDeferred<PanelLoad<BackgroundTerminalOutput>>()
                runOnIdle { repository.terminalRead = { gate.await() } }
                onNodeWithTag("刷新输出").performClick()
                onNodeWithText("正在加载输出…").assertIsDisplayed()
                onNodeWithTag("terminal-input").assertDoesNotExist()
                runOnIdle { gate.complete(PanelLoad.Ready(fixture.scope, output)) }
                onNodeWithTag("terminal-input").assertTextEquals("unknown draft").assertIsNotEnabled()
                onNodeWithTag("发送一行").assertIsNotEnabled()
                runOnIdle {
                    assertEquals(2, operations)
                    assertEquals(1L, controller.state.value!!.terminalControls!!.inputReceipt)
                    repository.terminalRead = { PanelLoad.Ready(fixture.scope, output) }
                    controller.bind(owner.copy(host = owner.host.copy(generation = 2)))
                    controller.dispatch(ThreadPanelAction.OpenTab(fixture.scope, ThreadPanelTab.BackgroundOutput))
                }
                onNodeWithTag("terminal-input").assertTextEquals("").assertIsEnabled()
                runOnIdle { assertEquals(2, operations) }
            }
        } finally { controller.close(); coroutineScope.cancel() }
    }

    @Test
    fun terminalInputConfirmsDeliveryAndUncertainReplyPreservesDraftInCompactPanel() {
        val fixture = ThreadPanelsUiFixtures
        val output = fixture.output().copy(inputState = BackgroundInputState.Open)
        val repository = PanelsInteractionRepository().apply { terminalRead = { PanelLoad.Ready(fixture.scope, output) } }
        val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val controller = ThreadPanelsController(coroutineScope, repository)
        controller.bind(ThreadPanelsOwner(LocalConnection(ApiGateway()),
            ConversationHostScope(fixture.scope.hostId, 1, null), fixture.scope.conversationId))
        controller.dispatch(ThreadPanelAction.OpenTab(fixture.scope, ThreadPanelTab.BackgroundOutput))
        val calls = mutableListOf<Triple<BackgroundTerminalKey, TerminalOperationKind, String>>()
        var reply = CompletableDeferred<TerminalOperationResult>()
        repository.operation = { key, kind, input -> calls += Triple(key, kind, input); reply.await() }
        try {
            runSkikoComposeUiTest(size = Size(320f, 600f)) {
                setContent {
                    val state by controller.state.collectAsState()
                    HarnessTheme { state?.let { ThreadPanels(it, controller::dispatch,
                        onTerminalInput = { key, input -> controller.operateTerminal(key, TerminalOperationKind.Input, input) },
                        onTerminalEof = { controller.operateTerminal(it, TerminalOperationKind.Eof) },
                        onTerminalCancel = { controller.operateTerminal(it, TerminalOperationKind.Cancel) }) } }
                }
                for (tag in listOf("发送一行", "EOF", "取消进程")) {
                    val bounds = onNodeWithTag(tag).assertIsDisplayed().getUnclippedBoundsInRoot()
                    assertTrue(bounds.left.value >= 0f && bounds.right.value <= 320f)
                }
                onNodeWithTag("terminal-input").performTextInput("你好")
                onNodeWithTag("发送一行").performClick()
                onNodeWithTag("发送一行").assertIsNotEnabled()
                onNodeWithTag("EOF").assertIsNotEnabled()
                onNodeWithTag("terminal-input").assertIsNotEnabled()
                runOnIdle {
                    assertEquals(listOf(Triple(output.key, TerminalOperationKind.Input, "你好\n")), calls)
                    reply.complete(TerminalOperationResult(TerminalOperationOutcome.Applied, "Delivered"))
                }
                onNodeWithTag("terminal-input").assertTextEquals("")
                onNodeWithTag("terminal-input").performTextInput("保留")
                runOnIdle { reply = CompletableDeferred() }
                onNodeWithTag("发送一行").performClick()
                runOnIdle { reply.complete(TerminalOperationResult(TerminalOperationOutcome.Unknown, "Uncertain delivery")) }
                onNodeWithTag("terminal-input").assertTextEquals("保留")
                onNodeWithTag("发送一行").assertIsNotEnabled()
                onNodeWithTag("EOF").assertIsNotEnabled()
                onNodeWithTag("取消进程").assertIsEnabled()
                runOnIdle { reply = CompletableDeferred() }
                onNodeWithTag("取消进程").performClick()
                runOnIdle {
                    assertEquals(Triple(output.key, TerminalOperationKind.Cancel, ""), calls.last())
                    reply.complete(TerminalOperationResult(TerminalOperationOutcome.Applied, "Cancellation requested"))
                }
                onNodeWithTag("取消进程").assertIsNotEnabled()
                onNodeWithText("运行中").assertIsDisplayed()
                onNodeWithTag("关闭后台终端").performClick()
                onNodeWithText("面板已关闭").assertIsDisplayed()
                runOnIdle { assertEquals(3, calls.size) }
            }
        } finally { controller.close(); coroutineScope.cancel() }
    }

    @Test
    fun eofUsesExactProcessAndClosingViewDoesNotCancelIt() {
        val fixture = ThreadPanelsUiFixtures
        val output = fixture.output().copy(inputState = BackgroundInputState.Open)
        val repository = PanelsInteractionRepository().apply { terminalRead = { PanelLoad.Ready(fixture.scope, output) } }
        val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val controller = ThreadPanelsController(coroutineScope, repository)
        controller.bind(ThreadPanelsOwner(LocalConnection(ApiGateway()),
            ConversationHostScope(fixture.scope.hostId, 1, null), fixture.scope.conversationId))
        controller.dispatch(ThreadPanelAction.OpenTab(fixture.scope, ThreadPanelTab.BackgroundOutput))
        val calls = mutableListOf<TerminalOperationKind>()
        repository.operation = { key, kind, input ->
            assertEquals(output.key, key); assertEquals("", input); calls += kind
            TerminalOperationResult(TerminalOperationOutcome.Applied, "Input closed")
        }
        try {
            runSkikoComposeUiTest(size = Size(320f, 600f)) {
                setContent {
                    val state by controller.state.collectAsState()
                    HarnessTheme { state?.let { ThreadPanels(it, controller::dispatch,
                        onTerminalInput = { key, input -> controller.operateTerminal(key, TerminalOperationKind.Input, input) },
                        onTerminalEof = { controller.operateTerminal(it, TerminalOperationKind.Eof) },
                        onTerminalCancel = { controller.operateTerminal(it, TerminalOperationKind.Cancel) }) } }
                }
                onNodeWithTag("EOF").performClick()
                onNodeWithTag("发送一行").assertIsNotEnabled()
                onNodeWithTag("EOF").assertIsNotEnabled()
                onNodeWithText("运行中").assertIsDisplayed()
                onNodeWithTag("关闭后台终端").performClick()
                runOnIdle { assertEquals(listOf(TerminalOperationKind.Eof), calls) }
            }
        } finally { controller.close(); coroutineScope.cancel() }
    }

    @Test
    fun selectBackAndShowMoreReduceActualPanelActions() = runSkikoComposeUiTest(size = Size(640f, 800f)) {
        val fixture = ThreadPanelsUiFixtures
        val active = (1..6).map { fixture.child("active-$it") }
        val done = (1..11).map { fixture.child("done-$it", ChildAgentStatus.Done) }
        var state by mutableStateOf(fixture.overview(active + done))
        val actions = mutableListOf<ThreadPanelAction>()
        setContent { HarnessTheme(themeMode = HarnessThemeMode.Light) {
            ThreadPanels(state, { actions += it; state = state.reduce(it) })
        } }
        onNodeWithText(active.last().label).assertDoesNotExist()
        onNodeWithText(done.last().label).assertDoesNotExist()
        onAllNodesWithText("显示更多")[0].performScrollTo().performMouseInput { click() }
        onNodeWithText(active.last().label).assertExists()
        onNodeWithText("显示更多").performScrollTo().performClick()
        onNodeWithText(done.last().label).performScrollTo().assertIsDisplayed().performMouseInput { click() }
        onNodeWithText("正在加载子智能体对话记录…").assertIsDisplayed()
        runOnIdle {
            assertTrue(state.showAllActive && state.showAllDone)
            assertEquals(done.last().key, state.selectedChild)
            state = state.reduce(ThreadPanelAction.ReceiveDetail(done.last().key, state.detailGeneration, fixture.history(done.last())))
        }
        onNodeWithText("只读对话记录").assertIsDisplayed()
        onNodeWithTag("返回子智能体").performMouseInput { click() }
        runOnIdle {
            assertNull(state.selectedChild)
            assertEquals(listOf(SubagentSection.Active, SubagentSection.Done),
                actions.filterIsInstance<ThreadPanelAction.ShowMore>().map { it.section })
            assertTrue(actions.any { it is ThreadPanelAction.SelectChild && it.key == done.last().key })
            assertTrue(actions.last() is ThreadPanelAction.Back)
        }
    }

    @Test
    fun keyboardSwitchAndCloseTabsClearOnlyLocalPanelState() = runSkikoComposeUiTest(size = Size(320f, 600f)) {
        val fixture = ThreadPanelsUiFixtures
        var state by mutableStateOf(fixture.overview(listOf(fixture.child("one"))).copy(
            terminal = PanelLoad.Ready(fixture.scope, fixture.output()),
        ))
        setContent { HarnessTheme { ThreadPanels(state, { state = state.reduce(it) }) } }
        onNodeWithTag("tab:BackgroundOutput").performScrollTo().performMouseInput { click() }
        onNodeWithText("运行中").assertIsDisplayed()
        onNodeWithTag("tab:BackgroundOutput").performKeyInput { pressKey(Key.DirectionRight) }
        onNodeWithTag("tab:Subagents").assertIsSelected()
        onNodeWithText("Child one").performClick()
        onNodeWithTag("关闭子智能体").performScrollTo().performMouseInput { click() }
        onNodeWithTag("tab:Subagents").assertDoesNotExist()
        onNodeWithText("运行中").assertIsDisplayed()
        runOnIdle {
            assertNull(state.selectedChild)
            assertEquals(ThreadPanelTab.BackgroundOutput, state.selectedTab)
            assertTrue(state.detail is PanelLoad.Unavailable)
            assertEquals(fixture.output(), (state.terminal as PanelLoad.Ready).value)
        }
        onNodeWithTag("关闭后台终端").performScrollTo().performClick()
        onNodeWithText("面板已关闭").assertIsDisplayed()
        runOnIdle { assertTrue(state.openTabs.isEmpty()); assertNull(state.selectedTab) }
    }

    @Test
    fun refreshAndErrorRetryReadThroughRealControllerAndDisablePendingActions() {
        val fixture = ThreadPanelsUiFixtures
        val repository = PanelsInteractionRepository()
        val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val owner = ThreadPanelsOwner(LocalConnection(ApiGateway()),
            ConversationHostScope(fixture.scope.hostId, 1, null), fixture.scope.conversationId)
        val controller = ThreadPanelsController(coroutineScope, repository)
        controller.bind(owner)
        controller.dispatch(ThreadPanelAction.OpenTab(fixture.scope, ThreadPanelTab.Subagents))
        controller.dispatch(ThreadPanelAction.OpenTab(fixture.scope, ThreadPanelTab.BackgroundOutput))
        try {
            runSkikoComposeUiTest(size = Size(640f, 800f)) {
                setContent {
                    val state by controller.state.collectAsState()
                    HarnessTheme { state?.let { ThreadPanels(it, controller::dispatch,
                        onRefreshTerminal = { controller.refreshTerminal() },
                        onRefreshOverview = { controller.refreshOverview() },
                        onRefreshDetail = { controller.refreshDetail() }) } }
                }
                onNodeWithText("无法加载输出").assertIsDisplayed()
                val terminalGate = CompletableDeferred<PanelLoad<BackgroundTerminalOutput>>()
                runOnIdle { repository.terminalRead = { terminalGate.await() } }
                onNodeWithTag("重试输出").performMouseInput { click() }
                onNodeWithTag("刷新输出").assertIsNotEnabled().performMouseInput { click() }
                runOnIdle { assertEquals(2, repository.terminalReads); terminalGate.complete(PanelLoad.Ready(fixture.scope, fixture.output())) }
                waitUntil(timeoutMillis = 5_000) { controller.state.value?.terminal is PanelLoad.Ready }
                onNodeWithText("运行中").assertIsDisplayed()
                runOnIdle { repository.terminalRead = { PanelLoad.Ready(fixture.scope, fixture.output().copy(revision = 2, buffer = "Refreshed output")) } }
                onNodeWithTag("刷新输出").performClick()
                onNodeWithText("Refreshed output").assertIsDisplayed()

                onNodeWithTag("tab:Subagents").performClick()
                onNodeWithText("无法加载子智能体").assertIsDisplayed()
                val overviewGate = CompletableDeferred<PanelLoad<SubagentOverview>>()
                val child = fixture.child("controller-child", canInteract = true)
                runOnIdle { repository.overviewRead = { overviewGate.await() } }
                onNodeWithTag("刷新子智能体").performMouseInput { click() }
                onNodeWithTag("刷新子智能体").assertIsNotEnabled().performMouseInput { click() }
                runOnIdle {
                    assertEquals(2, repository.overviewReads)
                    overviewGate.complete(PanelLoad.Ready(fixture.scope, SubagentOverview(fixture.scope, listOf(child))))
                }
                waitUntil(timeoutMillis = 5_000) { controller.state.value?.subagents is PanelLoad.Ready }
                onNodeWithText(child.label).performMouseInput { click() }
                onNodeWithText("无法加载对话记录").assertIsDisplayed()
                val detailGate = CompletableDeferred<PanelLoad<ChildAgentDetail>>()
                runOnIdle { repository.detailRead = { detailGate.await() } }
                onNodeWithTag("刷新历史").performClick()
                onNodeWithTag("刷新历史").assertIsNotEnabled().performMouseInput { click() }
                runOnIdle { assertEquals(2, repository.detailReads); detailGate.complete(fixture.history(child, canInteract = true)) }
                waitUntil(timeoutMillis = 5_000) { controller.state.value?.detail is PanelLoad.Ready }
                onNodeWithText("只读对话记录").assertIsDisplayed()
                runOnIdle {
                    assertNull(controller.state.value!!.interactiveDetail)
                    assertEquals(child.key, repository.selectedChild)
                    assertEquals(3, repository.terminalReads)
                }
                onNodeWithTag("返回子智能体").performClick()
                val refreshGate = CompletableDeferred<PanelLoad<SubagentOverview>>()
                runOnIdle { repository.overviewRead = { refreshGate.await() } }
                onNodeWithTag("刷新子智能体").performClick()
                onNodeWithText("正在刷新子智能体…").assertIsDisplayed()
                onNodeWithText(child.label).assertIsDisplayed()
                onNodeWithTag("刷新子智能体").assertIsNotEnabled()
                runOnIdle { refreshGate.complete(PanelLoad.Ready(fixture.scope, SubagentOverview(fixture.scope, listOf(child)))) }
                waitUntil(timeoutMillis = 5_000) { controller.state.value?.overviewRefreshing == false }
                runOnIdle { assertEquals(3, repository.overviewReads) }
            }
        } finally {
            controller.close()
            coroutineScope.cancel()
        }
    }

    @Test
    fun readOnlyDetailNeverMountsConsumerAndBothAuthorizationsAreRequired() =
        runSkikoComposeUiTest(size = Size(320f, 600f)) {
            val fixture = ThreadPanelsUiFixtures
            var state by mutableStateOf(fixture.selected(fixture.child("readonly"), fixture.history(fixture.child("readonly"))))
            var mounts = 0
            var sends = 0
            setContent { HarnessTheme {
                ThreadPanels(state, { state = state.reduce(it) }, childInteraction = {
                    SideEffect { mounts++ }
                    HarnessButton("Test consumer", { sends++ }, testTag = "thread-panel-test-consumer")
                })
            } }
            for ((listPermission, detailPermission) in listOf(false to false, false to true, true to false)) {
                runOnIdle {
                    val child = fixture.child("readonly", canInteract = listPermission)
                    state = fixture.selected(child, fixture.history(child, canInteract = detailPermission))
                }
                onNodeWithTag("thread-panel-test-consumer").assertDoesNotExist()
                onNodeWithText("只读对话记录").assertIsDisplayed()
                runOnIdle { assertEquals(0, mounts); assertEquals(0, sends) }
            }
            runOnIdle {
                val child = fixture.child("readonly", canInteract = true)
                state = fixture.selected(child, fixture.history(child, canInteract = true))
            }
            onNodeWithTag("thread-panel-test-consumer").assertIsDisplayed().performMouseInput { click() }
            runOnIdle { assertTrue(mounts > 0); assertEquals(1, sends) }
            onNodeWithTag("返回子智能体").performClick()
            onNodeWithTag("thread-panel-test-consumer").assertDoesNotExist()
        }

    @Test
    fun compactLongTitleAndTranscriptRemainReachableByScrolling() = runSkikoComposeUiTest(size = Size(320f, 600f)) {
        val fixture = ThreadPanelsUiFixtures
        val child = fixture.child("long", label = fixture.longTitle)
        var state by mutableStateOf(fixture.overview(listOf(child)))
        setContent { HarnessTheme(themeMode = HarnessThemeMode.Dark) { ThreadPanels(state, { state = state.reduce(it) }) } }
        val title = onNodeWithText(fixture.longTitle).assertIsDisplayed()
        val bounds = title.getUnclippedBoundsInRoot()
        assertTrue(bounds.right > bounds.left)
        assertTrue(bounds.left.value >= 0f && bounds.right.value <= 320f)
        title.performMouseInput { click() }
        runOnIdle {
            val entries = (1..30).map { ChildTranscriptEntry("line-$it", ChildTranscriptRole.Assistant,
                "Transcript paragraph $it: " + "Compact panel text must remain readable and scrollable. ".repeat(3)) } +
                ChildTranscriptEntry("last", ChildTranscriptRole.Tool, "END-OF-TRANSCRIPT")
            state = state.reduce(ThreadPanelAction.ReceiveDetail(child.key, state.detailGeneration,
                PanelLoad.Ready(fixture.scope, ChildAgentDetail(child.key, entries, false))))
        }
        onNodeWithText("END-OF-TRANSCRIPT").performScrollTo().assertIsDisplayed()
        onNodeWithTag("返回子智能体").assertIsDisplayed().performMouseInput { click() }
        onNodeWithText(fixture.longTitle).assertIsDisplayed()
        runOnIdle { assertNull(state.selectedChild) }
    }
}

private class PanelsInteractionRepository : ThreadPanelsRepository {
    private val scope = ThreadPanelsUiFixtures.scope
    var terminalReads = 0
    var overviewReads = 0
    var detailReads = 0
    var selectedChild: ChildAgentKey? = null
    var terminalRead: suspend () -> PanelLoad<BackgroundTerminalOutput> = { PanelLoad.Error(scope, "Fixture output error") }
    var terminalSelectionRead: (suspend (BackgroundTerminalKey?) -> BackgroundTerminalSelection)? = null
    var overviewRead: suspend () -> PanelLoad<SubagentOverview> = { PanelLoad.Error(scope, "Fixture overview error") }
    var detailRead: suspend () -> PanelLoad<ChildAgentDetail> = { PanelLoad.Error(scope, "Fixture history error") }
    var operation: suspend (BackgroundTerminalKey, TerminalOperationKind, String) -> TerminalOperationResult =
        { _, _, _ -> TerminalOperationResult(TerminalOperationOutcome.Rejected, "Unavailable") }

    override suspend fun operateTerminal(owner: ThreadPanelsOwner, key: BackgroundTerminalKey,
        kind: TerminalOperationKind, input: String): TerminalOperationResult {
        check(owner.scope == scope && key.scope == scope)
        return operation(key, kind, input)
    }

    override suspend fun readTerminal(owner: ThreadPanelsOwner): PanelLoad<BackgroundTerminalOutput> {
        check(owner.scope == scope)
        terminalReads++
        return terminalRead()
    }
    override suspend fun readTerminalSelection(owner: ThreadPanelsOwner, selectedKey: BackgroundTerminalKey?): BackgroundTerminalSelection {
        check(owner.scope == scope)
        val read = terminalSelectionRead ?: return super<ThreadPanelsRepository>.readTerminalSelection(owner, selectedKey)
        terminalReads++
        return read(selectedKey)
    }
    override suspend fun readOverview(owner: ThreadPanelsOwner): PanelLoad<SubagentOverview> {
        check(owner.scope == scope)
        overviewReads++
        return overviewRead()
    }
    override suspend fun readDetail(owner: ThreadPanelsOwner, child: ChildAgentKey): PanelLoad<ChildAgentDetail> {
        check(owner.scope == scope && child.scope == scope)
        detailReads++
        selectedChild = child
        return detailRead()
    }
}
