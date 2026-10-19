package im.hikaru.harness.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.dp
import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.harness.client.app.*
import im.hikaru.harness.client.connection.*
import im.hikaru.harness.client.conversation.*
import im.hikaru.harness.ui.foundation.HarnessTheme
import im.hikaru.harness.ui.foundation.HarnessThemeMode
import kotlin.test.*

/** Test-only state/messages/catalogs; production callbacks belong to the app owner. */
@OptIn(ExperimentalTestApi::class)
class LiveConversationInteractionTest {
    @Test
    fun welcomeGroupCentersInAvailableViewportAndShortWindowsScroll() {
        listOf(Size(1440f, 900f), Size(420f, 800f), Size(420f, 260f)).forEach { size ->
            runSkikoComposeUiTest(size = size) {
                val owner = ComposerOwner()
                setContent { HarnessTheme { LiveHomeSurface(null) { owner.Content() } } }
                val root = onNodeWithTag("live-home").getUnclippedBoundsInRoot()
                val group = onNodeWithTag("live-home-group").getUnclippedBoundsInRoot()
                assertTrue(kotlin.math.abs((group.left + group.right).value - (root.left + root.right).value) < 2)
                if (size.height >= 800f) {
                    assertTrue(kotlin.math.abs((group.top + group.bottom).value - (root.top + root.bottom).value) < 2,
                        "Group must center vertically: group=$group root=$root")
                } else onNodeWithTag("live-composer-model").performScrollTo().assertIsDisplayed()
            }
        }
    }

    @Test
    fun captureActualPermissionAndModelPopupInteractionsInBothThemes() {
        listOf(HarnessThemeMode.Light, HarnessThemeMode.Dark).forEach { mode ->
            runSkikoComposeUiTest(size = Size(420f, 800f)) {
                val owner = ComposerOwner()
                owner.chosen = FIRST.copy(reasoningEfforts = listOf("low", "medium", "high"))
                owner.models = listOf(owner.chosen!!, SECOND)
                setContent { HarnessTheme(mode) { LiveHomeSurface(null) { owner.Content() } } }
                onNodeWithTag("live-composer-permissions").performClick()
                onNodeWithTag("live-composer-permission-FullAccess").performClick()
                onNodeWithTag("live-composer-permissions").performClick()
                savePopup(onNodeWithTag("live-composer-permission-popup"), "permissions-$mode")
                onNodeWithTag("live-composer-permission-Ask").performKeyInput { pressKey(Key.Escape) }
                onNodeWithTag("live-composer-model").performClick()
                onNodeWithTag("live-composer-effort-slider").performMouseInput {
                    moveTo(centerLeft); press(); moveTo(centerRight, delayMillis = 200); release()
                }
                runOnIdle { assertEquals("high", owner.effort) }
                savePopup(onNodeWithTag("live-composer-model-popup"), "reasoning-$mode")
                onNodeWithTag("live-composer-model-select").performClick()
                savePopup(onNodeWithTag("live-composer-model-popup"), "models-$mode")
                onNodeWithTag(menuTag(SECOND)).performClick()
                // A model without effort stops closes the picker instead of showing an empty,
                // disabled slider panel (Codex filters that view out entirely).
                onNodeWithTag("live-composer-model-popup").assertDoesNotExist()
            }
        }
    }

    private fun savePopup(node: SemanticsNodeInteraction, name: String) {
        val root = generateSequence(java.io.File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
            .first { java.io.File(it, "project.yaml").exists() }
        val directory = java.io.File(root, "build/composer-preview").apply { mkdirs() }
        val image = org.jetbrains.skia.Image.makeFromBitmap(node.captureToImage().asSkiaBitmap())
        try { java.io.File(directory, "$name.png").writeBytes(requireNotNull(image.encodeToData()).bytes) }
        finally { image.close() }
    }

    @Test
    fun homeSlotUsesSuppliedHostAndDraftAndSubmitsWithoutSelectedSession() = runComposeUiTest {
        val owner = ComposerOwner()
        var label by mutableStateOf<String?>("Test workstation")
        setContent { HarnessTheme { LiveHomeSurface(label) { owner.Content() } } }
        onNodeWithTag("live-home-hero").assertTextEquals("你想让我们在 Test workstation 中构建什么？")
        onNodeWithText("开始新的会话").assertDoesNotExist()
        onNodeWithTag("live-composer-editor").performTextReplacement("Build a real app")
        onNodeWithTag("live-composer-send").assertIsEnabled().performClick()
        runOnIdle {
            assertEquals(listOf("Build a real app"), owner.submitted)
            assertNull(owner.state.selectedSessionId)
            assertEquals(ConversationAction.Creating, owner.state.action)
            label = null
        }
        // With no Host to name, the hero falls back to the reference's shorter question.
        onNodeWithTag("live-home-hero").assertTextEquals("我们要构建什么？")
    }

    @Test
    fun enterSubmitsShiftEnterEditsAndBusyStateBlocksDuplicateSubmission() = runComposeUiTest {
        val owner = ComposerOwner()
        setContent { HarnessTheme { owner.Content() } }
        val editor = onNodeWithTag("live-composer-editor")
        editor.performTextReplacement("first")
        editor.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        editor.performKeyInput { keyDown(Key.ShiftLeft); pressKey(Key.Enter); keyUp(Key.ShiftLeft) }
        runOnIdle { assertTrue(owner.state.draft.contains("\n")); assertTrue(owner.submitted.isEmpty()) }
        editor.performTextReplacement("send snapshot")
        editor.performKeyInput { keyDown(Key.Enter) }
        runOnIdle { assertEquals(listOf("send snapshot"), owner.submitted) }
        onNodeWithTag("live-composer-send").assertDoesNotExist()
        onNodeWithTag("live-composer-busy").assertExists()
        editor.performKeyInput { keyUp(Key.Enter); pressKey(Key.Enter); pressKey(Key.Enter) }
        editor.performTextReplacement("edited while creating")
        runOnIdle {
            assertEquals(1, owner.submitted.size)
            assertEquals("edited while creating", owner.state.draft)
            owner.state = owner.state.copy(action = ConversationAction.Sending)
        }
        editor.performTextReplacement("edited while sending")
        editor.performKeyInput { pressKey(Key.Enter) }
        runOnIdle {
            assertEquals(1, owner.submitted.size)
            assertEquals("edited while sending", owner.state.draft)
            owner.state = owner.state.copy(action = ConversationAction.Idle)
        }
        editor.performKeyInput { pressKey(Key.Enter) }
        runOnIdle { assertEquals(listOf("send snapshot", "edited while sending"), owner.submitted) }
    }

    @Test
    fun blankDisconnectedAndUnavailableDisableSendWithoutDroppingDraft() = runComposeUiTest {
        val owner = ComposerOwner()
        setContent { HarnessTheme { owner.Content() } }
        val editor = onNodeWithTag("live-composer-editor")
        editor.performTextReplacement("   ")
        onNodeWithTag("live-composer-send").assertIsNotEnabled()
        editor.performTextReplacement("retained draft")
        runOnIdle { owner.state = owner.state.copy(connected = false) }
        onNodeWithTag("live-composer-send").assertIsNotEnabled()
        onNodeWithTag("live-composer-disconnected").assertDoesNotExist()
        editor.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        editor.performKeyInput { pressKey(Key.Enter) }
        runOnIdle { owner.state = owner.state.copy(connected = true); owner.submitReason = "当前 Host 无法创建会话。" }
        onNodeWithTag("live-composer-send").assertIsNotEnabled()
        // The disabled button carries the block; no explanation sentence sits under the draft.
        onNodeWithTag("live-composer-submit-unavailable").assertDoesNotExist()
        editor.performKeyInput { pressKey(Key.Enter) }
        runOnIdle {
            assertTrue(owner.submitted.isEmpty())
            assertEquals("retained draft", owner.state.draft)
            owner.submitReason = null
        }
        onNodeWithTag("live-composer-send").assertIsEnabled()
    }

    @Test
    fun phaseErrorsAndUnknownRemoteRunStateKeepStopTruthfulAndRetryable() = runComposeUiTest {
        val owner = ComposerOwner()
        owner.state = readyState("next message").copy(selectedSessionId = SessionId("actual-session"),
            runState = ConversationRunState.Unknown, action = ConversationAction.RunningUnknown,
            errors = mapOf(
                ConversationErrorKind.Prompt to ConversationError(ConversationErrorKind.Prompt, "发送未确认。", ConversationRetry.SubmitExplicitly),
                ConversationErrorKind.Completion to ConversationError(ConversationErrorKind.Completion, "运行状态不可用。", null)))
        setContent { HarnessTheme { owner.Content() } }
        // Failures are toasts now: no error sentence is pinned underneath the draft.
        onNodeWithTag("live-composer-error-Prompt").assertDoesNotExist()
        onNodeWithTag("live-composer-error-Completion").assertDoesNotExist()
        // Progress never prints: the stop button is the only busy indicator.
        onNodeWithTag("live-composer-action").assertDoesNotExist()
        onNodeWithTag("live-composer-send").assertDoesNotExist()
        onNodeWithTag("live-composer-stop").assertIsEnabled().performClick()
        onNodeWithTag("live-composer-stop").assertIsNotEnabled()
        onNodeWithTag("live-composer-action").assertDoesNotExist()
        runOnIdle {
            assertEquals(1, owner.cancelled)
            owner.state = owner.state.copy(cancelling = false, errors = mapOf(
                ConversationErrorKind.Cancel to ConversationError(ConversationErrorKind.Cancel, "停止失败，可重试。", ConversationRetry.Cancel)))
        }
        onNodeWithTag("live-composer-error-Cancel").assertDoesNotExist()
        onNodeWithTag("live-composer-stop").assertIsEnabled().performClick()
        runOnIdle { assertEquals(2, owner.cancelled) }
    }

    @Test
    fun modelPanelSwitchesInPlaceAndReadOnlySessionsRemainInspectable() = runComposeUiTest {
        val owner = ComposerOwner()
        owner.chosen = FIRST.copy(reasoningEfforts = listOf("low", "high"))
        setContent { HarnessTheme { owner.Content() } }
        onNodeWithTag("live-composer-model").performClick()
        onNodeWithTag("live-composer-effort-slider").assertExists()
        onNodeWithTag("live-composer-model-select").performClick()
        onNodeWithTag("live-composer-effort-slider").assertDoesNotExist()
        onNodeWithTag(menuTag(SECOND)).performClick()
        // SECOND declares no efforts, so the picker closes rather than swapping to the slider view.
        onNodeWithTag("live-composer-model-popup").assertDoesNotExist()
        runOnIdle { assertEquals(listOf(SECOND), owner.selected); assertTrue(owner.submitted.isEmpty()) }
        onNodeWithTag("live-composer-model").performClick()
        onNodeWithTag("live-composer-model-popup").performKeyInput { pressKey(Key.Escape) }
        onNodeWithTag("live-composer-model-popup").assertDoesNotExist()
        runOnIdle { owner.state = owner.state.copy(selectedSessionId = SessionId("thread")) }
        onNodeWithTag("live-composer-model").assertIsEnabled().performClick()
        // Read-only still opens the list for a model with no effort stops, but choosing is inert.
        onNodeWithTag("live-composer-effort-slider").assertDoesNotExist()
        onNodeWithTag(menuTag(FIRST)).performClick()
        runOnIdle { assertEquals(listOf(SECOND), owner.selected) }
    }

    @Test
    fun permissionsSelectAndReflectLocallyWhileAttachmentStaysUnavailable() = runComposeUiTest {
        val owner = ComposerOwner()
        setContent { HarnessTheme { owner.Content() } }
        val attachment = onNodeWithTag("live-composer-attachment").assertIsNotEnabled().fetchSemanticsNode()
        assertFalse(attachment.config.contains(SemanticsActions.OnClick))
        onNodeWithTag("live-composer-permissions").assertIsEnabled().performClick()
        onNodeWithTag("live-composer-permission-FullAccess").performClick()
        onNodeWithTag("live-composer-permission-popup").assertDoesNotExist()
        onNodeWithTag("live-composer-permissions").assertTextEquals("完全访问")
        onNodeWithTag("live-composer-permissions").performClick()
        onNodeWithTag("live-composer-permission-FullAccess").assertIsSelected()
        onNodeWithTag("live-composer-permission-Ask").performKeyInput { pressKey(Key.Escape) }
        onNodeWithTag("live-composer-permission-popup").assertDoesNotExist()
        runOnIdle { assertEquals(ComposerPermission.FullAccess, owner.permission); assertTrue(owner.submitted.isEmpty()) }
    }
    @Test
    fun typedHistorySurvivesLoadingAndFailureAndOnlyHistoryUsesGenericRetry() = runSkikoComposeUiTest(size = Size(600f, 900f)) {
        var state by mutableStateOf(readyState().copy(selectedSessionId = SessionId("thread"),
            history = typedHistory(), historyPhase = ConversationDataPhase.Loading))
        var retries = 0
        setContent { HarnessTheme { LiveThreadSurface(state) { retries++ } } }
        onNodeWithTag("live-thread-loading").assertExists()
        onNodeWithText("Actual user question").assertExists()
        onNodeWithText("Actual assistant text").assertExists()
        onNodeWithText("Reasoning · 展开").performClick()
        onNodeWithText("Actual typed reasoning").assertExists()
        onNodeWithText("test_tool").performClick()
        onNodeWithText("{\"actual\":true}").assertExists()
        onNodeWithText("Actual tool output").performScrollTo().assertExists()
        runOnIdle { state = state.copy(historyPhase = ConversationDataPhase.Failed, errors = mapOf(
            ConversationErrorKind.History to ConversationError(ConversationErrorKind.History, "历史读取失败。", ConversationRetry.History),
            ConversationErrorKind.Prompt to ConversationError(ConversationErrorKind.Prompt, "发送未确认。", ConversationRetry.SubmitExplicitly))) }
        // The surface keeps the blocking retry; the failure sentences themselves are toasts.
        onNodeWithTag("live-thread-retry").performClick()
        onNodeWithTag("live-thread-submit-recovery").assertDoesNotExist()
        onNodeWithTag("live-thread-error-History").assertDoesNotExist()
        onNodeWithTag("live-thread-error-Prompt").assertDoesNotExist()
        onNodeWithTag("live-composer").assertDoesNotExist()
        runOnIdle { assertEquals(1, retries) }
        onNodeWithText("Actual user question").performScrollTo().assertExists()
    }

    @Test
    fun emptyLoadingDisconnectedAndFailedThreadStatesDoNotInventMessages() = runComposeUiTest {
        var state by mutableStateOf(readyState().copy(historyPhase = ConversationDataPhase.Empty))
        var retries = 0
        setContent { HarnessTheme { LiveThreadSurface(state) { retries++ } } }
        onNodeWithTag("live-thread-empty").assertExists()
        onNodeWithText("开始新的会话").assertDoesNotExist()
        runOnIdle { state = state.copy(historyPhase = ConversationDataPhase.Loading) }
        onNodeWithTag("live-thread-loading").assertExists()
        onNodeWithTag("live-thread-empty").assertDoesNotExist()
        runOnIdle { state = state.copy(connected = false, historyPhase = ConversationDataPhase.Disconnected) }
        onNodeWithTag("live-thread-disconnected").assertExists()
        onNodeWithTag("live-thread-empty").assertDoesNotExist()
        runOnIdle { state = state.copy(connected = true, historyPhase = ConversationDataPhase.Failed) }
        onNodeWithTag("live-thread-retry").performClick()
        runOnIdle { assertEquals(1, retries) }
    }

    @Test
    fun longEditorAndFooterFit4206001440LightAndDarkViewports() {
        listOf(420, 600, 1440).forEach { width ->
            listOf(HarnessThemeMode.Light, HarnessThemeMode.Dark).forEach { mode ->
                runSkikoComposeUiTest(size = Size(width.toFloat(), 900f)) {
                    val owner = ComposerOwner()
                    owner.state = readyState((1..100).joinToString("\n") { "long draft line $it " + "text ".repeat(24) })
                    setContent { HarnessTheme(mode) { LiveHomeSurface(null) { owner.Content() } } }
                    val root = onNodeWithTag("live-home").getUnclippedBoundsInRoot()
                    val editor = onNodeWithTag("live-composer-editor").getUnclippedBoundsInRoot()
                    val editorHeight = editor.bottom - editor.top
                    val rootHeight = root.bottom - root.top
                    assertTrue(editorHeight >= 40.dp && editorHeight <= 160.dp, "$width/$mode editor=$editor")
                    assertTrue(editorHeight <= rootHeight * 0.25f)
                    listOf("live-composer", "live-composer-send", "live-composer-model").forEach { tag ->
                        val bounds = onNodeWithTag(tag).getUnclippedBoundsInRoot()
                        assertTrue(bounds.left >= root.left && bounds.right <= root.right && bounds.top >= root.top && bounds.bottom <= root.bottom,
                            "$width/$mode $tag=$bounds root=$root")
                    }
                    onNodeWithTag("live-composer-footer").assertExists()
                    runOnIdle { assertEquals(100, owner.state.draft.lines().size) }
                }
            }
        }
    }

    @Test
    fun configuredEditorCapAndViewportQuarterAreBothRespected() = runComposeUiTest {
        val owner = ComposerOwner()
        owner.state = readyState((1..80).joinToString("\n") { "line $it" })
        var viewport by mutableStateOf(900.dp)
        setContent { HarnessTheme {
            CompositionLocalProvider(LocalConversationEditorMaxHeight provides 80.dp, LocalConversationViewportHeight provides viewport) {
                Box(Modifier.requiredSize(420.dp, 600.dp)) { owner.Content() }
            }
        } }
        val configuredBounds = onNodeWithTag("live-composer-editor").getUnclippedBoundsInRoot()
        assertTrue(configuredBounds.bottom - configuredBounds.top <= 80.dp)
        runOnIdle { viewport = 240.dp }
        val viewportBounds = onNodeWithTag("live-composer-editor").getUnclippedBoundsInRoot()
        assertTrue(viewportBounds.bottom - viewportBounds.top <= 60.dp)
    }

    @Test
    fun longTypedTimelineScrollsInsideNarrowViewport() = runSkikoComposeUiTest(size = Size(420f, 600f)) {
        val messages = (0..24).map { index -> Message("long-$index", MessageRole.ASSISTANT,
            listOf(TextContent("Actual message $index " + "unbroken".repeat(100))), ModelMessageSource("test", "model")) }
        setContent { HarnessTheme(HarnessThemeMode.Dark) {
            LiveThreadSurface(readyState().copy(history = messages, historyPhase = ConversationDataPhase.Ready)) { error("No retry expected") }
        } }
        onNodeWithTag("live-thread-timeline").performScrollToIndex(24)
        val last = onNodeWithText("Actual message 24 ", substring = true).assertExists().getUnclippedBoundsInRoot()
        val root = onNodeWithTag("live-thread").getUnclippedBoundsInRoot()
        assertTrue(last.left >= root.left && last.right <= root.right)
    }

    @Test
    fun welcomeAndThreadUseDifferentPromptsAndOnlyAdvertisedEffortsAreSelectable() = runComposeUiTest {
        val owner = ComposerOwner()
        owner.chosen = FIRST.copy(reasoningEfforts = listOf("low", "high"))
        setContent { HarnessTheme { owner.Content() } }
        onNodeWithText("描述你想构建的内容…").assertExists()
        onNodeWithTag("live-composer-model").performClick()
        onNodeWithTag("live-composer-effort-slider").performSemanticsAction(SemanticsActions.SetProgress) { it(2f) }
        runOnIdle { assertEquals("high", owner.effort) }
        onNodeWithTag("live-composer-effort-slider").performKeyInput { pressKey(Key.Escape) }
        runOnIdle { owner.state = owner.state.copy(selectedSessionId = SessionId("thread")) }
        onNodeWithText("继续提问，或补充任务细节…").assertExists()
        onNodeWithTag("live-composer-effort-slider").assertDoesNotExist()
        onNodeWithTag("live-composer-model-unavailable").assertDoesNotExist()
        runOnIdle { owner.state = owner.state.copy(selectedSessionId = null); owner.chosen = FIRST }
        onNodeWithTag("live-composer-effort-slider").assertDoesNotExist()
    }

    private class ComposerOwner {
        var state by mutableStateOf(readyState())
        var models by mutableStateOf(listOf(FIRST, SECOND))
        var chosen by mutableStateOf<ConversationModelChoice?>(null)
        var modelReason by mutableStateOf<String?>(null)
        var submitReason by mutableStateOf<String?>(null)
        val submitted = mutableListOf<String>()
        val selected = mutableListOf<ConversationModelChoice>()
        var cancelled = 0
        var effort by mutableStateOf<String?>(null)
        var permission by mutableStateOf(ComposerPermission.Ask)

        @Composable
        fun Content() {
            LiveConversationComposer(state,
                onDraftChange = { state = state.copy(draft = it, draftRevision = state.draftRevision + 1) },
                onSubmit = { if (state.canSubmit && submitReason == null) {
                    submitted += state.draft
                    state = state.copy(action = if (state.selectedSessionId == null) ConversationAction.Creating else ConversationAction.Sending)
                } },
                onCancel = { if (state.canCancel) { cancelled++; state = state.copy(cancelling = true) } },
                models = models, selectedModel = chosen, onModelSelect = { selected += it; chosen = it },
                modelUnavailableReason = modelReason, submitUnavailableReason = submitReason,
                selectedReasoningEffort = effort, onReasoningEffortSelect = { effort = it },
                permission = permission, onPermissionSelect = { permission = it })
        }
    }

    private companion object {
        val FIRST = ConversationModelChoice("test-provider-a", "model-a", "First test model")
        val SECOND = ConversationModelChoice("test-provider-b", "model-b", "Second test model")
        fun menuTag(choice: ConversationModelChoice) = "menu:conversation-model:" + choice.providerId.length + ":" +
            choice.providerId + ":" + choice.modelId.length + ":" + choice.modelId
        fun readyState(draft: String = "") = ConversationState(
            host = ConversationHostScope(HostId("ui-test-host"), 1, null), connected = true,
            listPhase = ConversationDataPhase.Ready, historyPhase = ConversationDataPhase.Empty,
            runState = ConversationRunState.Idle, draft = draft)
        fun typedHistory() = listOf(
            Message("user", MessageRole.USER, listOf(TextContent("Actual user question")), UserMessageSource),
            Message("assistant", MessageRole.ASSISTANT, listOf(TextContent("Actual assistant text"),
                ReasoningContent("Actual typed reasoning"), ToolCallContent("call-1", "test_tool", "{\"actual\":true}"),
                ToolResultContent("call-1", listOf(TextContent("Actual tool output")))), ModelMessageSource("test", "model")))
    }
}
