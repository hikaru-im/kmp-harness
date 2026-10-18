package im.hikaru.harness.client.git

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import im.hikaru.harness.client.page.SettingsFormSection
import im.hikaru.harness.ui.foundation.*

/** Display names only: controller requests continue to carry the complete Git reference. */
internal fun gitRefLabel(ref: String): String = ref.removePrefix("refs/heads/").removePrefix("refs/remotes/")

@Composable
internal fun GitFormSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    SettingsFormSection(title) { GitPageCard(content) }
}

@Composable
internal fun GitStatusLabel(text: String) {
    HarnessText(text, Modifier.background(HarnessTokens.Colors.SurfaceSecondary, RoundedCornerShape(6.dp))
        .padding(horizontal = 8.dp, vertical = 4.dp),
        style = MaterialTheme.typography.bodySmall, color = HarnessTokens.Colors.TextMuted)
}

@Composable
internal fun GitChangeRow(change: GitChange) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.width(64.dp)) {
            GitStatusLabel(when {
                change.conflicted -> "冲突"
                change.untracked -> "未跟踪"
                'D' in "${change.indexStatus}${change.worktreeStatus}" -> "删除"
                'R' in "${change.indexStatus}${change.worktreeStatus}" -> "重命名"
                'A' in "${change.indexStatus}${change.worktreeStatus}" -> "新增"
                else -> "修改"
            })
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            GitPageValue((change.originalPath?.let { "$it → " } ?: "") + change.path, "git-change")
            if (!change.untracked) HarnessText(
                listOfNotNull("已暂存".takeIf { change.staged }, "未暂存".takeIf { change.unstaged }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = HarnessTokens.Colors.TextMuted)
        }
    }
}

@Composable
internal fun GitBranchRow(branch: GitBranch) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        HarnessIcon(HarnessIconName.Branch, size = 16.dp, tint = HarnessTokens.Colors.TextMuted)
        Box(Modifier.weight(1f)) { GitPageValue(gitRefLabel(branch.ref), "git-branch") }
        GitStatusLabel(if (branch.current) "当前" else if (branch.remote) "远程" else "本地")
    }
}
