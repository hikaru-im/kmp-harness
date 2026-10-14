package im.hikaru.harness.client.page

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import im.hikaru.harness.ui.foundation.*

/**
 * Page-private header band of the Projects/Recent surface.
 *
 * The Codex navigator/project pages lay the section title, its one-line description and the page
 * actions on the same band instead of three full-width rows (see the measured navigator rows in
 * `docs/desktop-ui/reference/rail-measurement-2026-10-05.md`: the `项目`/`最近` section rows are one
 * 30px line box with 14px/21px title ink, the trailing row actions sharing that line 8px away).
 * Below [ActionsBesideFrom] there is no room left for the actions beside the text, so the band
 * stacks and the actions keep a full row of their own. The threshold is the same compact-shell
 * width the shell itself switches at (`reference/shell-width-classes.md`).
 */
internal val ActionsBesideFrom: Dp = 640.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProjectsRecentHeader(
    rowTag: String,
    title: String,
    description: String?,
    actions: @Composable FlowRowScope.() -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        FlowRow(
            Modifier.fillMaxWidth().semantics { testTag = rowTag },
            horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Three),
            verticalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two),
            maxItemsInEachRow = if (maxWidth < ActionsBesideFrom) 1 else Int.MAX_VALUE,
        ) {
            // `fill = false` keeps the text column at its content width so the actions stay beside it,
            // while still letting a long title shrink instead of pushing the actions off the line.
            Column(Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.One)) {
                HarnessText(title, style = MaterialTheme.typography.headlineSmall, maxLines = 1)
                description?.let {
                    HarnessText(it, color = HarnessTokens.Colors.TextMuted, maxLines = 1, modifier = Modifier.weight(1f, fill = false))
                }
            }
            HeaderActions("$rowTag-actions", actions)
        }
    }
}

/** Keeps the header's trailing actions in one row and on the same flow line as the title. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeaderActions(tag: String, content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(
        Modifier.semantics { testTag = tag },
        horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two),
        verticalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two),
        content = content,
    )
}

/** Page-private layout: the available content width, including the shell, controls wrapping. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProjectsRecentActions(tag: String, stackBelow: Dp = 0.dp, content: @Composable FlowRowScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        FlowRow(Modifier.fillMaxWidth().semantics { testTag = tag },
            maxItemsInEachRow = if (maxWidth < stackBelow) 1 else Int.MAX_VALUE,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

/** Full value in semantics, selection and clipboard; independent horizontal scroll avoids clipping. */
@Composable
internal fun ProjectsRecentValue(label: String, value: String, tag: String) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ProjectsRecentActions("$tag-actions") {
            HarnessText(label, style = MaterialTheme.typography.bodySmall, color = HarnessTokens.Colors.TextMuted)
            ProjectsRecentCopy(value, "复制$label", "$tag-copy")
        }
        SelectionContainer {
            HarnessText(value, Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .semantics { testTag = tag; contentDescription = "$label：$value" },
                color = HarnessTokens.Colors.TextMuted, maxLines = 1, overflow = TextOverflow.Clip)
        }
    }
}

@Composable
internal fun ProjectsRecentCopy(value: String, label: String, tag: String) {
    val clipboard = LocalClipboardManager.current
    HarnessIconButton(label, { clipboard.setText(AnnotatedString(value)) },
        state = HarnessButtonState(), testTag = tag) { HarnessIcon(HarnessIconName.Copy) }
}
