package im.hikaru.harness.desktop

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
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
import im.hikaru.harness.client.settings.EnterBehavior
import im.hikaru.harness.client.settings.ShortcutPlatform
import im.hikaru.harness.ui.foundation.HarnessTheme
import im.hikaru.harness.ui.foundation.HarnessThemeMode
import kotlin.math.sqrt
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
                // A model without adjustable efforts leaves the effort slot empty; Codex prints no
                // sentence to explain it.
                onNodeWithText("此模型未提供可调整的思考强度，跟随厂商。").assertDoesNotExist()
                onNodeWithTag("live-composer-model-popup").performKeyInput { pressKey(Key.Escape) }
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
    fun enterBehaviorSettingDrivesTheSubmitChord() = runComposeUiTest {
        val owner = ComposerOwner()
        setContent { HarnessTheme { owner.Content() } }
        val editor = onNodeWithTag("live-composer-editor")
        editor.performSemanticsAction(SemanticsActions.RequestFocus) { it() }

        // `enter` (default): a bare Enter sends and Shift+Enter inserts the newline.
        editor.performTextReplacement("enter mode")
        editor.performKeyInput { keyDown(Key.ShiftLeft); pressKey(Key.Enter); keyUp(Key.ShiftLeft) }
        runOnIdle { assertTrue(owner.state.draft.contains("\n")); assertTrue(owner.submitted.isEmpty()) }
        editor.performTextReplacement("enter sends")
        editor.performKeyInput { pressKey(Key.Enter) }
        runOnIdle { assertEquals(listOf("enter sends"), owner.submitted) }

        // `cmdAlways`: Enter only inserts the newline; ⌘+Enter sends (Codex modifier chord).
        runOnIdle {
            owner.submitted.clear()
            owner.state = owner.state.copy(action = ConversationAction.Idle, draft = "")
            owner.enterBehavior = EnterBehavior.CmdAlways
        }
        editor.performTextReplacement("always newline")
        editor.performKeyInput { pressKey(Key.Enter) }
        runOnIdle { assertTrue(owner.submitted.isEmpty()); assertTrue(owner.state.draft.contains("\n")) }
        editor.performKeyInput { keyDown(Key.MetaLeft); pressKey(Key.Enter); keyUp(Key.MetaLeft) }
        runOnIdle { assertEquals(listOf(owner.state.draft), owner.submitted) }

        // `cmdIfMultiline`: a single line still sends on Enter...
        runOnIdle {
            owner.submitted.clear()
            owner.state = owner.state.copy(action = ConversationAction.Idle, draft = "")
            owner.enterBehavior = EnterBehavior.CmdIfMultiline
        }
        editor.performTextReplacement("single line")
        editor.performKeyInput { pressKey(Key.Enter) }
        runOnIdle { assertEquals(listOf("single line"), owner.submitted) }

        // ...but a real newline in the draft switches Enter to the newline and requires ⌘+Enter.
        runOnIdle {
            owner.submitted.clear()
            owner.state = owner.state.copy(action = ConversationAction.Idle, draft = "")
        }
        editor.performTextReplacement("first\nsecond")
        editor.performKeyInput { pressKey(Key.Enter) }
        runOnIdle { assertTrue(owner.submitted.isEmpty()); assertTrue(owner.state.draft.endsWith("\n")) }
        editor.performKeyInput { keyDown(Key.MetaLeft); pressKey(Key.Enter); keyUp(Key.MetaLeft) }
        runOnIdle { assertEquals(listOf(owner.state.draft), owner.submitted) }
    }

    @Test
    fun composerHintTracksTheEnterBehaviorAndDraftShape() = runComposeUiTest {
        val owner = ComposerOwner()
        setContent { HarnessTheme { owner.Content() } }
        onNodeWithTag("live-composer-editor").performTextInput("hint")
        fun hoverSend() {
            onNodeWithTag("live-composer-send").performMouseInput { moveTo(center) }
            mainClock.advanceTimeBy(300)
            waitForIdle()
        }
        fun leaveSend() {
            onNodeWithTag("live-composer-editor").performMouseInput { moveTo(center) }
            mainClock.advanceTimeBy(300)
            waitForIdle()
        }
        hoverSend()
        onNodeWithText("发送 · Enter；换行 · Shift+Enter").assertIsDisplayed()

        leaveSend()
        runOnIdle { owner.enterBehavior = EnterBehavior.CmdAlways }
        hoverSend()
        onNodeWithText("发送 · ⌘ + Enter；换行 · Enter").assertIsDisplayed()

        leaveSend()
        runOnIdle { owner.enterBehavior = EnterBehavior.CmdIfMultiline }
        hoverSend()
        onNodeWithText("发送 · Enter；换行 · Shift+Enter").assertIsDisplayed()

        // Multiline means the text contains a newline; visual wrapping never switches the chord.
        onNodeWithTag("live-composer-editor").performTextReplacement("first\nsecond")
        leaveSend()
        hoverSend()
        onNodeWithText("发送 · ⌘ + Enter；换行 · Enter").assertIsDisplayed()
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
        // `ComposerSubmitButton`'s blocked state is `opacity-50`, not `disabled`: the dimmed disc
        // still takes the click so `BlockedSubmitDialog` can open from it.
        onNodeWithTag("live-composer-send").assertIsEnabled()
        // The block is carried by the button, never by a sentence under the draft.
        onNodeWithTag("live-composer-submit-unavailable").assertDoesNotExist()
        onNodeWithTag("live-composer-model-unavailable").assertDoesNotExist()
        // Enter stays inert while blocked: the dialog is the only affordance, never a silent send.
        editor.performKeyInput { pressKey(Key.Enter) }
        runOnIdle {
            assertTrue(owner.submitted.isEmpty())
            assertEquals("retained draft", owner.state.draft)
            owner.submitReason = null
        }
        onNodeWithTag("live-composer-send").assertIsEnabled()
    }

    @Test
    fun phaseErrorsAndRunningRemoteStateKeepStopTruthfulAndRetryable() = runComposeUiTest {
        val owner = ComposerOwner()
        owner.state = readyState("next message").copy(selectedSessionId = SessionId("actual-session"),
            runState = ConversationRunState.Running, action = ConversationAction.RunningUnknown,
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
        // Selection returns to the reasoning panel even when the model has no adjustable efforts.
        // `IntelligenceDropdown` renders nothing there in that case — no explanatory sentence.
        onNodeWithText("此模型未提供可调整的思考强度，跟随厂商。").assertDoesNotExist()
        onNodeWithText("请先选择模型").assertDoesNotExist()
        runOnIdle { assertEquals(listOf(SECOND), owner.selected); assertTrue(owner.submitted.isEmpty()) }
        onNodeWithTag("live-composer-model-popup").performKeyInput { pressKey(Key.Escape) }
        onNodeWithTag("live-composer-model-popup").assertDoesNotExist()
        onNodeWithTag("live-composer-model").performClick()
        onNodeWithTag("live-composer-model-popup").performKeyInput { pressKey(Key.Escape) }
        onNodeWithTag("live-composer-model-popup").assertDoesNotExist()
        runOnIdle { owner.state = owner.state.copy(selectedSessionId = SessionId("thread")) }
        onNodeWithTag("live-composer-model").assertIsEnabled().performClick()
        // Existing sessions can select the next turn's model through the same reasoning stack.
        onNodeWithTag("live-composer-effort-slider").assertDoesNotExist()
        onNodeWithTag("live-composer-model-select").performClick()
        onNodeWithTag(menuTag(FIRST)).performClick()
        runOnIdle { assertEquals(listOf(SECOND, FIRST), owner.selected) }
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
        setContent { HarnessTheme { LiveThreadSurface(state, onRetry = { retries++ }) } }
        onNodeWithTag("live-thread-loading").assertExists()
        onNodeWithText("Actual user question").assertExists()
        onNodeWithText("Actual assistant text").assertExists()
        onNodeWithText("已完成思考").performClick()
        onNodeWithText("Actual typed reasoning").assertExists()
        // The result carries the call's label, so both rows read 加载了工具; the output lives in the
        // result row (content index 3), not the call row.
        onAllNodesWithText("加载了工具 · test_tool").assertCountEquals(2)
        onNodeWithTag("message:assistant:content:3:header").performClick()
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
        setContent { HarnessTheme { LiveThreadSurface(state, onRetry = { retries++ }) } }
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
        // One human message per turn: with turn-grouped lazy items the final turn is still directly
        // addressable by index, and its long unbroken text must stay inside the narrow viewport.
        val messages = (0..24).map { index -> Message("long-$index", MessageRole.USER,
            listOf(TextContent("Actual message $index " + "unbroken".repeat(100))), UserMessageSource) }
        setContent { HarnessTheme(HarnessThemeMode.Dark) {
            LiveThreadSurface(readyState().copy(history = messages, historyPhase = ConversationDataPhase.Ready),
                onRetry = { error("No retry expected") })
        } }
        onNodeWithContentDescription("会话", useUnmergedTree = true).performScrollToIndex(24)
        val last = onNodeWithText("Actual message 24 ", substring = true).assertExists().getUnclippedBoundsInRoot()
        val root = onNodeWithTag("live-thread").getUnclippedBoundsInRoot()
        assertTrue(last.left >= root.left && last.right <= root.right)
    }

    @Test
    fun codexPrintsOneTimeSeparatorPerTurnInsteadOfAPerMessageTimestamp() = runSkikoComposeUiTest(size = Size(600f, 900f)) {
        setContent { HarnessTheme { LiveThreadSurface(
            readyState().copy(selectedSessionId = SessionId("thread"), history = timedHistory(),
                historyPhase = ConversationDataPhase.Ready),
            onRetry = { error("No retry expected") }) } }
        val marker = onNodeWithTag("conversation-time-marker:user-1").assertExists().getUnclippedBoundsInRoot()
        // Codex's separator: a 52dp row (16dp padding either side of a 20dp line), centred on the
        // transcript column, whose accessible label is the `10月7日周三 1:02` form.
        assertEquals(52f, (marker.bottom - marker.top).value, 0.5f)
        val label = onNodeWithTag("conversation-time-marker:user-1")
            .fetchSemanticsNode().config[SemanticsProperties.ContentDescription].single()
        assertTrue(MARKER_LABEL.matches(label), "Separator label was $label")
        // One separator per turn: the turn's own messages carry none, and the next day adds exactly
        // one more.
        onNodeWithTag("conversation-time-marker:user-2").assertExists()
        onNodeWithTag("conversation-time-marker:user-3").assertExists()
        onAllNodes(hasTestTagPrefix("conversation-time-marker:")).assertCountEquals(3)
        onNodeWithTag("message:user-1").assertExists()
        // The separator shares the message column's centre, so the ticks read as one axis.
        val column = onNodeWithTag("message:assistant-1").getUnclippedBoundsInRoot()
        assertEquals((column.left + column.right).value / 2f, (marker.left + marker.right).value / 2f, 1f)
    }

    @Test
    fun streamingTailNeverFabricatesASeparatorAndTheSettledTurnSuppliesIt() = runSkikoComposeUiTest(size = Size(600f, 900f)) {
        val session = SessionId("thread")
        val settled = Message("user-1", MessageRole.USER, listOf(TextContent("Actual question")),
            UserMessageSource, FIRST_TURN_MILLIS)
        var live by mutableStateOf(LiveTranscript(session, listOf(
            Message("live:1:1", MessageRole.ASSISTANT, listOf(TextContent("Streaming answer")),
                ModelMessageSource("test", "model")))))
        setContent { HarnessTheme { LiveThreadSurface(
            readyState().copy(selectedSessionId = session, history = listOf(settled),
                historyPhase = ConversationDataPhase.Ready),
            live = live, onRetry = { error("No retry expected") }) } }
        // The tail has no durable timestamp, so it adds rows without inventing a date for them.
        onNodeWithTag("conversation-time-marker:user-1").assertExists()
        onAllNodes(hasTestTagPrefix("conversation-time-marker:")).assertCountEquals(1)
        runOnIdle { live = LiveTranscript(session, emptyList()) }
        waitForIdle()
        onNodeWithTag("conversation-time-marker:user-1").assertExists()
        onAllNodes(hasTestTagPrefix("conversation-time-marker:")).assertCountEquals(1)
    }

    @Test
    fun timeSeparatorsKeepIndexScrollingAndBottomFollowIntact() = runSkikoComposeUiTest(size = Size(420f, 600f)) {
        val messages = (0..24).flatMap { index ->
            listOf(
                Message("long-$index", MessageRole.USER, listOf(TextContent("Actual message $index " + "unbroken".repeat(100))),
                    UserMessageSource, FIRST_TURN_MILLIS + index * 60_000L),
                Message("long-reply-$index", MessageRole.ASSISTANT, listOf(TextContent("Reply $index")),
                    ModelMessageSource("test", "model"), FIRST_TURN_MILLIS + index * 60_000L + 1_000L),
            )
        }
        setContent { HarnessTheme(HarnessThemeMode.Dark) {
            LiveThreadSurface(readyState().copy(history = messages, historyPhase = ConversationDataPhase.Ready),
                onRetry = { error("No retry expected") })
        } }
        // CONV-02: one lazy item per turn, so the final turn is index 24 (the end anchor follows it).
        // Scrolling still reaches the final reply and the timeline is still pinned to the bottom.
        onNodeWithContentDescription("会话", useUnmergedTree = true).performScrollToIndex(24)
        onNodeWithTag("conversation-turn:long-24").assertExists()
        val last = onNodeWithText("Reply 24").assertExists().getUnclippedBoundsInRoot()
        val root = onNodeWithTag("live-thread").getUnclippedBoundsInRoot()
        assertTrue(last.left >= root.left && last.right <= root.right)
        // Scrolling back up finds the first turn and its separator on the same lazy item, so the
        // marker is real timeline content rather than decoration pinned to a message.
        onNodeWithContentDescription("会话", useUnmergedTree = true).performScrollToIndex(0)
        onNodeWithTag("conversation-turn:long-0").assertExists()
        onNodeWithTag("conversation-time-marker:long-0").assertExists()
    }

    @Test
    fun newMessageDoesNotMoveAReaderParkedAboveTheBottom() = runSkikoComposeUiTest(size = Size(600f, 700f)) {
        val history = (0..24).map { scrollableTurn(it) }
        var state by mutableStateOf(readyState().copy(history = history, historyPhase = ConversationDataPhase.Ready))
        setContent { HarnessTheme(HarnessThemeMode.Dark) {
            LiveThreadSurface(state, onRetry = { error("No retry expected") })
        } }
        parkOnTurn(index = 12, tag = "anchor-12")
        val parked = turnBounds("anchor-12")
        // A dropped-in message only appends a turn; the reader stays exactly where they parked.
        runOnIdle { state = state.copy(history = history + scrollableTurn(25)) }
        waitForIdle()
        assertEquals(parked.top.value, turnBounds("anchor-12").top.value, 0.5f)
        onNodeWithText("Actual message 25 ", substring = true).assertDoesNotExist()
    }

    @Test
    fun toolExpansionAndStreamingGrowthKeepTheReaderParked() = runSkikoComposeUiTest(size = Size(600f, 700f)) {
        val session = SessionId("thread")
        val history = (0..15).flatMap { index ->
            if (index == 12) listOf(
                Message("tool-12", MessageRole.USER, listOf(TextContent("Actual message 12")), UserMessageSource),
                Message("tool-12-assistant", MessageRole.ASSISTANT, listOf(
                    TextContent("Answer 12"),
                    ToolCallContent("call-12", "read_fixture", "{\"path\":\"fixture.kt\"}"),
                ), ModelMessageSource("test", "model")),
                Message("tool-12-result", MessageRole.USER,
                    listOf(ToolResultContent("call-12", listOf(TextContent("Raw tool output 12")))),
                    ToolMessageSource("call-12")),
            ) else listOf(
                Message("tool-$index", MessageRole.USER,
                    listOf(TextContent("Actual message $index " + "scrollable ".repeat(60))), UserMessageSource),
                Message("tool-$index-assistant", MessageRole.ASSISTANT, listOf(TextContent("Answer $index")),
                    ModelMessageSource("test", "model")),
            )
        }
        var live by mutableStateOf(LiveTranscript(session, listOf(streamingAnswer("Streaming answer"))))
        var state by mutableStateOf(readyState().copy(
            selectedSessionId = session, history = history, historyPhase = ConversationDataPhase.Ready))
        setContent { HarnessTheme(HarnessThemeMode.Dark) {
            LiveThreadSurface(state, live = live, onRetry = { error("No retry expected") })
        } }
        parkOnTurn(index = 12, tag = "tool-12")
        val parked = turnBounds("tool-12")
        // Expanding a disclosure below the anchored turn top only grows the item's tail.
        val header = onNodeWithTag("message:tool-12-assistant:content:1:header", useUnmergedTree = true)
        header.assertIsDisplayed()
        header.performClick()
        waitForIdle()
        val timeline = onNodeWithContentDescription("会话", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals("已展开", header.fetchSemanticsNode().config[SemanticsProperties.StateDescription],
            "turn=$parked timeline=$timeline header=${header.getUnclippedBoundsInRoot()}")
        onNodeWithTag("message:tool-12-result:content:0:content:0", useUnmergedTree = true).assertExists()
        onNodeWithText("Raw tool output 12", substring = true, useUnmergedTree = true).assertExists()
        assertEquals(parked.top.value, turnBounds("tool-12").top.value, 0.5f)
        // The streaming tail keeps growing at the end of the transcript, far below the reader.
        runOnIdle { live = LiveTranscript(session, listOf(streamingAnswer("Streaming answer " + "grow ".repeat(80)))) }
        waitForIdle()
        assertEquals(parked.top.value, turnBounds("tool-12").top.value, 0.5f)
    }

    @Test
    fun historyRefreshKeepsTheReaderOnTheSameTurn() = runSkikoComposeUiTest(size = Size(600f, 700f)) {
        val recent = (5..24).map { scrollableTurn(it) }
        val older = (0..4).map { scrollableTurn(it) }
        var state by mutableStateOf(readyState().copy(
            selectedSessionId = SessionId("thread"), history = recent, historyPhase = ConversationDataPhase.Ready))
        setContent { HarnessTheme(HarnessThemeMode.Dark) {
            LiveThreadSurface(state, onRetry = { error("No retry expected") })
        } }
        // The reader parked on turn 12 while it was item 7 of the loaded page.
        parkOnTurn(index = 7, tag = "anchor-12")
        val parked = turnBounds("anchor-12")
        // Refreshing with an older page shifts every index by five; the turn id does not move.
        runOnIdle { state = state.copy(history = older + recent) }
        waitForIdle()
        assertEquals(parked.top.value, turnBounds("anchor-12").top.value, 0.5f)
    }

    @Test
    fun sessionSwitchStartsAtTheNewSessionsBottom() = runSkikoComposeUiTest(size = Size(600f, 700f)) {
        val parkedHistory = (0..24).map { scrollableTurn(it) }
        val otherHistory = (0..24).map { index -> Message("other-$index", MessageRole.USER,
            listOf(TextContent("Other message $index " + "scrollable ".repeat(60))), UserMessageSource) }
        var state by mutableStateOf(readyState().copy(
            selectedSessionId = SessionId("thread"), history = parkedHistory, historyPhase = ConversationDataPhase.Ready))
        setContent { HarnessTheme(HarnessThemeMode.Dark) {
            LiveThreadSurface(state, onRetry = { error("No retry expected") })
        } }
        parkOnTurn(index = 12, tag = "anchor-12")
        // Switching sessions recreates the viewport: no stale anchor may survive into the new thread.
        runOnIdle { state = state.copy(selectedSessionId = SessionId("other"), history = otherHistory) }
        waitForIdle()
        onNodeWithTag("conversation-turn:other-24", useUnmergedTree = true).assertExists()
        onNodeWithTag("conversation-turn:other-0", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun viewportResizeKeepsTheReaderOnTheSameTurn() = runSkikoComposeUiTest(size = Size(900f, 700f)) {
        val history = (0..24).map { scrollableTurn(it) }
        var width by mutableStateOf(900.dp)
        setContent { HarnessTheme(HarnessThemeMode.Dark) {
            Box(Modifier.requiredSize(width, 700.dp)) {
                LiveThreadSurface(readyState().copy(history = history, historyPhase = ConversationDataPhase.Ready),
                    onRetry = { error("No retry expected") })
            }
        } }
        parkOnTurn(index = 12, tag = "anchor-12")
        val parked = turnBounds("anchor-12")
        // Narrowing reflows every wrapped message; the parked turn's top must stay at its offset.
        runOnIdle { width = 440.dp }
        waitForIdle()
        assertEquals(parked.top.value, turnBounds("anchor-12").top.value, 0.5f)
    }

    private fun scrollableTurn(index: Int) = Message("anchor-$index", MessageRole.USER,
        listOf(TextContent("Actual message $index " + "scrollable ".repeat(60))), UserMessageSource)

    private fun streamingAnswer(text: String) = Message("live:1:1", MessageRole.ASSISTANT,
        listOf(TextContent(text)), ModelMessageSource("test", "model"))

    private fun ComposeUiTest.settle() { mainClock.advanceTimeByFrame(); waitForIdle() }

    private fun ComposeUiTest.turnBounds(tag: String) =
        onNodeWithTag("conversation-turn:$tag", useUnmergedTree = true).assertExists().getUnclippedBoundsInRoot()

    /**
     * Park deterministically on [index], then emit a real wheel gesture: a semantics index jump is
     * not observed as a user scroll, and the extra offset proves the anchor restores more than zero.
     * The jump must land first; a wheel dispatched into its animation leaves an arbitrary offset.
     */
    private fun ComposeUiTest.parkOnTurn(index: Int, tag: String) {
        onNodeWithContentDescription("会话", useUnmergedTree = true).performScrollToIndex(index)
        settle()
        wheelTranscript(notches = 1f)
        onNodeWithTag("conversation-turn:$tag", useUnmergedTree = true).assertExists()
    }

    private fun ComposeUiTest.wheelTranscript(notches: Float) {
        val timeline = onNodeWithContentDescription("会话", useUnmergedTree = true).fetchSemanticsNode()
        val rootNode = generateSequence(timeline) { it.parent }.last()
        val root = onNode(SemanticsMatcher("Transcript root ${rootNode.id}") { it.id == rootNode.id }, true)
        val viewport = timeline.boundsInRoot.intersect(rootNode.boundsInRoot)
        assertTrue(viewport.width > 0f && viewport.height > 0f, "Measured transcript viewport")
        val point = Offset(viewport.left + minOf(4f, viewport.width / 2f), viewport.center.y) -
            rootNode.boundsInRoot.topLeft
        // Same conversion as the other Skiko wheel tests: this input path has no AWT scrollAmount.
        val unit = when (val os = System.getProperty("os.name").orEmpty()) {
            "Mac OS X" -> 10f * timeline.layoutInfo.density.density
            else -> if (os.startsWith("Linux")) sqrt(timeline.size.height.toFloat()) else timeline.size.height / 20f
        }
        root.performMouseInput { moveTo(point); advanceEventTime(16); scroll(notches * unit) }
        settle()
    }

    @Test
    fun turnsGroupEachHumanPromptWithItsOwnMessages() = runSkikoComposeUiTest(size = Size(600f, 900f)) {
        setContent { HarnessTheme { LiveThreadSurface(
            readyState().copy(selectedSessionId = SessionId("thread"), history = timedHistory(),
                historyPhase = ConversationDataPhase.Ready),
            onRetry = { error("No retry expected") }) } }
        // One lazy item per human-delimited turn; the separator and both messages belong to it.
        onNodeWithTag("conversation-turn:user-1", useUnmergedTree = true).assertExists()
            .assert(hasAnyDescendant(hasTestTag("conversation-time-marker:user-1")))
            .assert(hasAnyDescendant(hasTestTag("message:user-1")))
            .assert(hasAnyDescendant(hasTestTag("message:assistant-1")))
        onNodeWithTag("conversation-turn:user-2", useUnmergedTree = true).assertExists()
            .assert(hasAnyDescendant(hasTestTag("message:user-2")))
        onNodeWithTag("conversation-turn:user-3", useUnmergedTree = true).assertExists()
    }

    @Test
    fun editingStateStillRendersAboveTheComposer() = runSkikoComposeUiTest(size = Size(600f, 600f)) {
        val owner = ComposerOwner()
        owner.state = readyState("edited draft").copy(
            selectedSessionId = SessionId("thread"),
            editing = ConversationEdit("message-1", "edited draft", ""),
        )
        setContent { HarnessTheme { owner.Content() } }
        onNodeWithTag("live-composer-editing").assertExists()
        onNodeWithTag("live-composer-editor").assertExists()
    }

    @Test
    fun escapeStopsARunningTurnAndStaysInertWhenSettled() = runSkikoComposeUiTest(size = Size(600f, 600f)) {
        val owner = ComposerOwner()
        owner.state = readyState("keep this draft").copy(
            selectedSessionId = SessionId("thread"), runState = ConversationRunState.Running)
        setContent { HarnessTheme { owner.Content() } }
        onNodeWithTag("live-composer-editor").performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        onNodeWithTag("live-composer-editor").performKeyInput { pressKey(Key.Escape) }
        runOnIdle {
            assertEquals(1, owner.cancelled)
            assertEquals("keep this draft", owner.state.draft)
        }
        // A settled turn leaves Escape to the existing popup/back handling.
        runOnIdle { owner.state = owner.state.copy(cancelling = false, runState = ConversationRunState.Idle) }
        onNodeWithTag("live-composer-editor").performKeyInput { pressKey(Key.Escape) }
        runOnIdle { assertEquals(1, owner.cancelled) }
    }

    @Test
    fun unavailableMessageActionsKeepDisabledStateWithoutReasonCopy() = runSkikoComposeUiTest(size = Size(600f, 600f)) {
        val answer = Message("answer", MessageRole.ASSISTANT, listOf(TextContent("Answer")),
            ModelMessageSource("test", "model"))
        setContent { HarnessTheme { LiveThreadSurface(
            readyState().copy(selectedSessionId = SessionId("thread"), history = listOf(answer),
                historyPhase = ConversationDataPhase.Ready), onRetry = {}) } }
        listOf("朗读", "评价回复", "从这里创建聊天分支").forEach { label ->
            onNodeWithContentDescription(label, useUnmergedTree = true).assertIsNotEnabled()
            onNodeWithContentDescription("$label（暂不可用）", useUnmergedTree = true).assertDoesNotExist()
        }
    }

    @Test
    fun streamingTailJoinsTheTimelineAndLeavesWhenItSettles() = runSkikoComposeUiTest(size = Size(420f, 600f)) {
        val session = SessionId("thread")
        val settled = Message("user", MessageRole.USER, listOf(TextContent("Actual question")), UserMessageSource)
        var live by mutableStateOf(
            LiveTranscript(session, listOf(
                Message("live:1:1", MessageRole.ASSISTANT,
                    listOf(ReasoningContent("still thinking"), TextContent("Streaming answer")),
                    ModelMessageSource("test", "model")),
            )),
        )
        setContent { HarnessTheme(HarnessThemeMode.Dark) {
            LiveThreadSurface(
                readyState().copy(selectedSessionId = session, history = listOf(settled),
                    historyPhase = ConversationDataPhase.Ready),
                live = live,
                onRetry = { error("No retry expected") },
            )
        } }
        onNodeWithTag("message:live:1:1").assertExists()
        onNodeWithText("Streaming answer", substring = true).assertExists()
        // Reasoning is still arriving, and a streaming row offers no settled-message actions.
        onNodeWithText("正在思考…").assertExists()
        onNodeWithContentDescription("复制").assertDoesNotExist()

        // The settled message retires the tail; the timeline keeps only durable history.
        runOnIdle { live = LiveTranscript(session, emptyList()) }
        waitForIdle()
        onNodeWithTag("message:live:1:1").assertDoesNotExist()
        onNodeWithText("Streaming answer", substring = true).assertDoesNotExist()
    }

    @Test
    fun welcomeAndThreadUseDifferentPromptsAndOnlyAdvertisedEffortsAreSelectable() = runComposeUiTest {
        val owner = ComposerOwner()
        owner.chosen = FIRST.copy(reasoningEfforts = listOf("low", "high"))
        setContent { HarnessTheme { owner.Content() } }
        onNodeWithText("随心输入").assertExists()
        onNodeWithTag("live-composer-model").performClick()
        onNodeWithTag("live-composer-effort-slider").performSemanticsAction(SemanticsActions.SetProgress) { it(2f) }
        runOnIdle { assertEquals("high", owner.effort) }
        onNodeWithTag("live-composer-effort-slider").performKeyInput { pressKey(Key.Escape) }
        runOnIdle { owner.state = owner.state.copy(selectedSessionId = SessionId("thread")) }
        onNodeWithText("与 Codex 协作").assertExists()
        onNodeWithTag("live-composer-effort-slider").assertDoesNotExist()
        onNodeWithTag("live-composer-model-unavailable").assertDoesNotExist()
        runOnIdle { owner.state = owner.state.copy(selectedSessionId = null); owner.chosen = FIRST }
        onNodeWithTag("live-composer-effort-slider").assertDoesNotExist()
    }

    /**
     * `ComposerSubmitButton` + `BlockedSubmitDialog`: a blocked submit never prints a standing line
     * above the composer. The reason rides the submit button's tooltip, and the click — which the
     * dimmed disc still accepts — opens 无法发送消息 with the reason as its subtitle.
     */
    @Test
    fun blockedSubmitShowsNoStandingHintAndOpensTheBlockedSubmitDialog() = runComposeUiTest {
        val owner = ComposerOwner()
        runOnIdle { owner.submitReason = "远程连接已断开"; owner.state = owner.state.copy(draft = "hello") }
        setContent { HarnessTheme { owner.Content() } }
        // No standing copy, and the send never happened.
        onNodeWithText("远程连接已断开").assertDoesNotExist()
        onNodeWithTag("live-composer-send").assertExists()
        onNodeWithTag("live-composer-send").performMouseInput { click() }
        // The dimmed disc still takes the click, which is what opens the dialog.
        onNodeWithText("无法发送消息").assertIsDisplayed()
        onNodeWithTag("blocked-submit-message").assertIsDisplayed()
        onNodeWithText("远程连接已断开").assertIsDisplayed()
        onNodeWithTag("blocked-submit-ok").performMouseInput { click() }
        onNodeWithText("无法发送消息").assertDoesNotExist()
        assertTrue(owner.submitted.isEmpty())
    }

    private class ComposerOwner {
        var state by mutableStateOf(readyState())
        var models by mutableStateOf(listOf(FIRST, SECOND))
        var chosen by mutableStateOf<ConversationModelChoice?>(null)
        var submitReason by mutableStateOf<String?>(null)
        val submitted = mutableListOf<String>()
        val selected = mutableListOf<ConversationModelChoice>()
        var cancelled = 0
        var effort by mutableStateOf<String?>(null)
        var permission by mutableStateOf(ComposerPermission.Ask)
        var enterBehavior by mutableStateOf(EnterBehavior.Enter)
        var shortcutPlatform by mutableStateOf(ShortcutPlatform.Mac)

        @Composable
        fun Content() {
            LiveConversationComposer(state,
                enterBehavior = enterBehavior, shortcutPlatform = shortcutPlatform,
                onDraftChange = { state = state.copy(draft = it, draftRevision = state.draftRevision + 1) },
                onSubmit = { if (state.canSubmit && submitReason == null) {
                    submitted += state.draft
                    state = state.copy(action = if (state.selectedSessionId == null) ConversationAction.Creating else ConversationAction.Sending)
                } },
                onCancel = { if (state.canCancel) { cancelled++; state = state.copy(cancelling = true) } },
                models = models, selectedModel = chosen, onModelSelect = { selected += it; chosen = it },
                submitUnavailableReason = submitReason,
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
        // Fixed instants, never the wall clock: the separator label must be reproducible. The two
        // 2026-10-07 turns are 43 minutes apart, exactly like the live reference's 1:02/1:45 pair.
        const val FIRST_TURN_MILLIS = 1_791_364_500_000L
        const val SECOND_TURN_MILLIS = FIRST_TURN_MILLIS + 43 * 60_000L
        const val NEXT_DAY_TURN_MILLIS = 1_791_453_600_000L

        fun typedHistory() = listOf(
            Message("user", MessageRole.USER, listOf(TextContent("Actual user question")), UserMessageSource),
            Message("assistant", MessageRole.ASSISTANT, listOf(TextContent("Actual assistant text"),
                ReasoningContent("Actual typed reasoning"), ToolCallContent("call-1", "test_tool", "{\"actual\":true}"),
                ToolResultContent("call-1", listOf(TextContent("Actual tool output")))), ModelMessageSource("test", "model")))

        /** Three turns over two local days: two separators on one date, one across the boundary. */
        fun timedHistory() = listOf(
            Message("user-1", MessageRole.USER, listOf(TextContent("第一天第一个问题")), UserMessageSource, FIRST_TURN_MILLIS),
            Message("assistant-1", MessageRole.ASSISTANT, listOf(TextContent("第一天第一个回答")),
                ModelMessageSource("test", "model"), FIRST_TURN_MILLIS + 1_000L),
            Message("user-2", MessageRole.USER, listOf(TextContent("第一天第二个问题")), UserMessageSource, SECOND_TURN_MILLIS),
            Message("assistant-2", MessageRole.ASSISTANT, listOf(TextContent("第一天第二个回答")),
                ModelMessageSource("test", "model"), SECOND_TURN_MILLIS + 1_000L),
            Message("user-3", MessageRole.USER, listOf(TextContent("第二天的问题")), UserMessageSource, NEXT_DAY_TURN_MILLIS),
            Message("assistant-3", MessageRole.ASSISTANT, listOf(TextContent("第二天的回答")),
                ModelMessageSource("test", "model"), NEXT_DAY_TURN_MILLIS + 1_000L),
        )

        /**
         * Codex's `10月7日周三 1:02` label, matched by shape rather than by recomputing it, so the
         * assertion stays honest without pinning the host's timezone.
         */
        val MARKER_LABEL = Regex("""^\d{1,2}月\d{1,2}日周[一二三四五六日] \d{1,2}:\d{2}$""")

        fun hasTestTagPrefix(prefix: String) =
            SemanticsMatcher("TestTag starts with $prefix") { node ->
                node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true
            }
    }
}
