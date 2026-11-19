@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package im.hikaru.harness.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.harness.client.app.LiveThreadSurface
import im.hikaru.harness.client.connection.ApprovalChoice
import im.hikaru.harness.client.connection.ApprovalRequest
import im.hikaru.harness.client.connection.SessionId
import im.hikaru.harness.client.conversation.ConversationDataPhase
import im.hikaru.harness.client.conversation.ConversationHostScope
import im.hikaru.harness.client.conversation.ConversationRunState
import im.hikaru.harness.client.conversation.ConversationState
import im.hikaru.harness.client.conversation.PendingApproval
import im.hikaru.harness.ui.foundation.HarnessTheme
import im.hikaru.harness.ui.foundation.HarnessThemeMode
import im.hikaru.harness.ui.foundation.HarnessTokens
import kotlin.test.*

/** Approval cards are the only place a tool's exact command is shown before it runs. */
class ApprovalCardInteractionTest {
    @Test
    fun theFullCommandIsVisibleAndItsBlockScrollsInsteadOfTruncating() =
        runSkikoComposeUiTest(size = Size(600f, 800f)) {
            // Forty lines exceed the block's 160dp cap, so the block must scroll, not cut the text.
            val command = (1..40).joinToString("\n") { "echo line-$it && git status --short" } +
                "\nrm -rf /tmp/approval-fixture"
            mount(state(listOf(PendingApproval(request(command = command)))))
            onNodeWithText(command, useUnmergedTree = true).assertExists()
            val text = onNodeWithTag("approval-command:approval-1", true).fetchSemanticsNode()
            assertTrue(text.size.height > 160, "full command should overflow the visible block: ${text.size.height}")
            val block = onNode(hasAnyDescendant(hasTestTag("approval-command:approval-1")) and hasScrollAction(), true)
                .fetchSemanticsNode()
            assertEquals(160, block.size.height)
            // `execApprovalRequest.prompt` = 是否运行此命令？; the tool is the bare subtitle, and the
            // card carries no separate "your approval is needed" title or 待批准 status line.
            onNodeWithTag("approval-title:approval-1", true).assertTextEquals("是否运行此命令？")
            onNodeWithTag("approval-tool:approval-1", true).assertTextEquals("shell")
            onNodeWithText("需要你的批准").assertDoesNotExist()
            onNodeWithText("待批准").assertDoesNotExist()
        }

    @Test
    fun aRequestWithoutACommandAsksForTheToolInstead() = runSkikoComposeUiTest(size = Size(600f, 800f)) {
        mount(state(listOf(PendingApproval(request(toolName = "filesystem", command = "")))))
        // `composer.mcpToolCallApproval.formattedToolTitlePrefix` + `.formattedToolTitleSuffix`.
        onNodeWithTag("approval-title:approval-1", true).assertTextEquals("允许运行 filesystem 工具？")
        onNodeWithText("是否运行此命令？").assertDoesNotExist()
        onNodeWithTag("approval-command:approval-1", true).assertDoesNotExist()
        // The question already names the tool, so no bare subtitle repeats it.
        onNodeWithTag("approval-tool:approval-1", true).assertDoesNotExist()
    }

    @Test
    fun onlyTheChoicesTheHostOfferedBecomeButtons() = runSkikoComposeUiTest(size = Size(600f, 800f)) {
        mount(state(listOf(PendingApproval(request(choices = listOf(ApprovalChoice.ALLOW_ONCE, ApprovalChoice.DENY))))))
        onNodeWithTag("approval:approval-1:allow-once", true).assertExists()
        onNodeWithTag("approval:approval-1:deny", true).assertExists()
        onNodeWithTag("approval:approval-1:allow-session", true).assertDoesNotExist()
        onNodeWithTag("approval:approval-1:cancel", true).assertDoesNotExist()
    }

    @Test
    fun everyChoiceReportsItsRequestAndChoice() = runSkikoComposeUiTest(size = Size(600f, 800f)) {
        val decisions = mutableListOf<Pair<String, ApprovalChoice>>()
        mount(state(listOf(PendingApproval(request())))) { id, choice -> decisions += id to choice }
        onNodeWithTag("approval:approval-1:cancel", true).performClick()
        onNodeWithTag("approval:approval-1:deny", true).performClick()
        onNodeWithTag("approval:approval-1:allow-session", true).performClick()
        onNodeWithTag("approval:approval-1:allow-once", true).performClick()
        assertEquals(
            listOf(
                "approval-1" to ApprovalChoice.CANCEL,
                "approval-1" to ApprovalChoice.DENY,
                "approval-1" to ApprovalChoice.ALLOW_SESSION,
                "approval-1" to ApprovalChoice.ALLOW_ONCE,
            ),
            decisions,
        )
    }

    @Test
    fun twoPendingRequestsKeepTheirOwnCommandsAndPlaces() = runSkikoComposeUiTest(size = Size(600f, 800f)) {
        val decisions = mutableListOf<Pair<String, ApprovalChoice>>()
        mount(
            state(listOf(
                PendingApproval(request(id = "first", command = "git status")),
                PendingApproval(request(id = "second", command = "git push")),
            )),
        ) { id, choice -> decisions += id to choice }
        onNodeWithTag("approval-card:first", true).assertExists()
        onNodeWithTag("approval-card:second", true).assertExists()
        onNodeWithText("git status", useUnmergedTree = true).assertExists()
        onNodeWithText("git push", useUnmergedTree = true).assertExists()
        assertTrue(
            onNodeWithTag("approval-card:first", true).fetchSemanticsNode().positionInRoot.y <
                onNodeWithTag("approval-card:second", true).fetchSemanticsNode().positionInRoot.y,
        )
        onNodeWithTag("approval:second:allow-once", true).performClick()
        assertEquals(listOf("second" to ApprovalChoice.ALLOW_ONCE), decisions)
    }

    @Test
    fun theReasonAppearsOnlyWhenTheHostGaveOne() = runSkikoComposeUiTest(size = Size(600f, 800f)) {
        mount(state(listOf(PendingApproval(request(reason = "命令会写入工作树")))))
        onNodeWithTag("approval-reason:approval-1", true).assertExists()
        onNodeWithText("原因").assertExists()
        onNodeWithText("命令会写入工作树").assertExists()
    }

    @Test
    fun aRequestWithoutAReasonHasNoReasonSection() = runSkikoComposeUiTest(size = Size(600f, 800f)) {
        mount(state(listOf(PendingApproval(request()))))
        onNodeWithTag("approval-reason:approval-1", true).assertDoesNotExist()
        onNodeWithText("原因").assertDoesNotExist()
    }

    @Test
    fun aSubmittingRequestSpinsItsAllowActionAndCannotBeAnsweredTwice() = runSkikoComposeUiTest(size = Size(600f, 800f)) {
        val decisions = mutableListOf<Pair<String, ApprovalChoice>>()
        mount(state(listOf(PendingApproval(request(), responding = true)))) { id, choice -> decisions += id to choice }
        // Codex shows progress on the action being submitted, never as a caption on the card.
        onNodeWithTag("approval-status:approval-1", true).assertDoesNotExist()
        onNodeWithText("提交中…", useUnmergedTree = true).assertDoesNotExist()
        onNodeWithTag("approval:approval-1:allow-once", true).assert(SemanticsMatcher.keyIsDefined(
            androidx.compose.ui.semantics.SemanticsProperties.ProgressBarRangeInfo))
        for (choice in listOf("cancel", "deny", "allow-session", "allow-once")) {
            onNodeWithTag("approval:approval-1:$choice", true).assertIsNotEnabled()
        }
        onNodeWithTag("approval:approval-1:allow-once", true).performMouseInput { click() }
        assertTrue(decisions.isEmpty())
    }

    @Test
    fun aFailedDecisionStaysVisibleBesideTheCardForRetry() = runSkikoComposeUiTest(size = Size(600f, 800f)) {
        mount(state(listOf(PendingApproval(request(), error = "无法提交审批决定，请重试。"))))
        onNodeWithTag("approval-error:approval-1", true).assertExists()
        onNodeWithText("无法提交审批决定，请重试。", useUnmergedTree = true).assertExists()
        onNodeWithTag("approval:approval-1:allow-once", true).assertIsEnabled()
    }

    @Test
    fun aDisconnectedThreadShowsItsNoticeAndRefusesChoices() = runSkikoComposeUiTest(size = Size(600f, 800f)) {
        val decisions = mutableListOf<Pair<String, ApprovalChoice>>()
        mount(state(listOf(PendingApproval(request())), connected = false)) { id, choice -> decisions += id to choice }
        onNodeWithTag("live-thread-disconnected", true).assertExists()
        onNodeWithTag("approval:approval-1:allow-once", true).assertIsNotEnabled()
        assertTrue(decisions.isEmpty())
    }

    private fun ComposeUiTest.mount(
        state: ConversationState,
        onChoice: (String, ApprovalChoice) -> Unit = { _, _ -> },
    ) {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1f)) {
                HarnessTheme(HarnessThemeMode.Light) {
                    Box(Modifier.fillMaxSize().background(HarnessTokens.Colors.Canvas)
                        .semantics { testTag = "approval-fixture" }) {
                        LiveThreadSurface(state, onRetry = { fail("Unexpected history retry") }, onApprovalChoice = onChoice)
                    }
                }
            }
        }
        mainClock.advanceTimeByFrame()
        waitForIdle()
    }

    private fun state(approvals: List<PendingApproval>, connected: Boolean = true) = ConversationState(
        host = HOST, connected = connected,
        listPhase = ConversationDataPhase.Ready, historyPhase = ConversationDataPhase.Ready,
        selectedSessionId = SESSION, runState = ConversationRunState.Idle, approvals = approvals,
    )

    private fun request(
        id: String = "approval-1",
        toolName: String = "shell",
        command: String = "git commit -m \"完成波次 2\"",
        reason: String? = null,
        choices: List<ApprovalChoice> = ApprovalChoice.entries.toList(),
    ) = ApprovalRequest(id, SESSION, toolName, command, reason, choices)

    private companion object {
        val SESSION = SessionId("approval-session")
        val HOST = ConversationHostScope(HostId("approval-host"), 1, null)
    }
}
