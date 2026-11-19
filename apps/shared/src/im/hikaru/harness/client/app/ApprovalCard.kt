package im.hikaru.harness.client.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import im.hikaru.harness.client.connection.ApprovalChoice
import im.hikaru.harness.client.conversation.PendingApproval
import im.hikaru.harness.ui.foundation.*

/**
 * Pending tool approvals, pinned above the composer.
 *
 * The card prints the command in full rather than abbreviating it: the user is deciding whether that
 * exact text runs. 取消 means "dismiss without deciding" and 拒绝 records a refusal, so the Host can
 * tell the two apart; neither ever executes the tool.
 */
@Composable
internal fun ApprovalPanel(
    approvals: List<PendingApproval>,
    enabled: Boolean,
    onChoice: (requestId: String, choice: ApprovalChoice) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (approvals.isEmpty()) return
    Column(modifier.fillMaxWidth().semantics { testTag = "approval-panel" },
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        approvals.forEach { pending -> ApprovalCard(pending, enabled, onChoice) }
    }
}

@Composable
private fun ApprovalCard(
    pending: PendingApproval,
    enabled: Boolean,
    onChoice: (requestId: String, choice: ApprovalChoice) -> Unit,
) {
    val request = pending.request
    val id = request.id
    val inactive = !enabled || pending.responding
    Column(Modifier.fillMaxWidth()
        .background(ConversationAppearance.codeBackground, RoundedCornerShape(12.dp))
        .border(1.dp, ConversationAppearance.popupRing, RoundedCornerShape(12.dp))
        .padding(12.dp)
        .semantics { testTag = "approval-card:$id" },
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // `execApprovalRequest.prompt` = 是否运行此命令？ for a command; `composer.mcpToolCallApproval`
        // builds 允许运行 {name} 工具？ for a tool with no command to show. Codex's card is that question
        // plus the command — no "your approval is needed" banner and no status caption.
        val asksForCommand = request.command.isNotBlank()
        HarnessText(
            if (asksForCommand) "是否运行此命令？" else "允许运行 ${request.toolName} 工具？",
            Modifier.semantics { testTag = "approval-title:$id" },
            color = ConversationAppearance.ink, style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
        )
        // The tool is the card's subtitle — a fact from the request, printed bare. The question above
        // already names it when there is no command, so it is not repeated there.
        if (asksForCommand) {
            HarnessText(request.toolName, Modifier.semantics { testTag = "approval-tool:$id" },
                color = ConversationAppearance.muted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
        if (asksForCommand) {
            SelectionContainer {
                Box(Modifier.fillMaxWidth().heightIn(max = 160.dp)
                    .background(ConversationAppearance.popup, RoundedCornerShape(8.dp))
                    .verticalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 8.dp)) {
                    // The full command, never a shortened form; the block scrolls instead of truncating.
                    HarnessText(request.command, Modifier.semantics { testTag = "approval-command:$id" },
                        color = ConversationAppearance.ink,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        overflow = TextOverflow.Visible)
                }
            }
        }
        request.reason?.takeIf { it.isNotBlank() }?.let { reason ->
            Column(Modifier.semantics { testTag = "approval-reason:$id" },
                verticalArrangement = Arrangement.spacedBy(2.dp)) {
                // `approvalRequestCard.reasonLabel` = 原因.
                HarnessText("原因", color = ConversationAppearance.tertiary,
                    style = MaterialTheme.typography.bodySmall, maxLines = 1)
                HarnessText(reason, color = ConversationAppearance.muted,
                    style = MaterialTheme.typography.bodySmall, overflow = TextOverflow.Visible)
            }
        }
        pending.error?.let { message ->
            HarnessText(message, Modifier.semantics { testTag = "approval-error:$id" },
                color = HarnessTokens.Colors.Danger, style = MaterialTheme.typography.bodySmall)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            // Cancelling and refusing sit left of the two allow actions; only offered choices appear.
            // While a decision is in flight Codex spins the primary submit rather than captioning the
            // card with a status line, so the spinner sits on the allow action itself.
            val offered = ApprovalChoice.entries.filter { it in request.choices }
                .sortedBy { APPROVAL_CHOICE_ORDER.indexOf(it) }
            val spinning = if (pending.responding) offered.lastOrNull() else null
            offered.forEach { choice ->
                HarnessButton(approvalChoiceLabel(choice), { onChoice(id, choice) },
                    enabled = !inactive, size = HarnessControlSize.Compact,
                    state = if (choice == spinning) HarnessControlState.Loading else HarnessControlState.Normal,
                    variant = approvalChoiceVariant(choice),
                    testTag = "approval:$id:${approvalChoiceTag(choice)}")
            }
        }
    }
}

/** Visual order: dismissive actions first, the default allow action last (closest to the edge). */
private val APPROVAL_CHOICE_ORDER = listOf(
    ApprovalChoice.CANCEL,
    ApprovalChoice.DENY,
    ApprovalChoice.ALLOW_SESSION,
    ApprovalChoice.ALLOW_ONCE,
)

/** `execApprovalRequest.menu.runOnce` = 是; `execApprovalRequest.menu.runAlways` = 是，且本次会话不再询问. */
private fun approvalChoiceLabel(choice: ApprovalChoice): String = when (choice) {
    ApprovalChoice.ALLOW_ONCE -> "是"
    ApprovalChoice.ALLOW_SESSION -> "是，且本次会话不再询问"
    ApprovalChoice.DENY -> "拒绝"
    // `approvalRequestCard.cancel` = 取消, `approvalRequestCard.allow` = 允许.
    ApprovalChoice.CANCEL -> "取消"
}

private fun approvalChoiceTag(choice: ApprovalChoice): String = when (choice) {
    ApprovalChoice.ALLOW_ONCE -> "allow-once"
    ApprovalChoice.ALLOW_SESSION -> "allow-session"
    ApprovalChoice.DENY -> "deny"
    ApprovalChoice.CANCEL -> "cancel"
}

private fun approvalChoiceVariant(choice: ApprovalChoice): HarnessButtonVariant = when (choice) {
    ApprovalChoice.ALLOW_ONCE -> HarnessButtonVariant.Primary
    ApprovalChoice.ALLOW_SESSION -> HarnessButtonVariant.Secondary
    ApprovalChoice.DENY -> HarnessButtonVariant.Destructive
    ApprovalChoice.CANCEL -> HarnessButtonVariant.Ghost
}
