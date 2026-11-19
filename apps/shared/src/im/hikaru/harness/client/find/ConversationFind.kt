package im.hikaru.harness.client.find

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import im.hikaru.harness.client.connection.Message
import im.hikaru.harness.client.connection.TextContent
import im.hikaru.harness.ui.foundation.HarnessButton
import im.hikaru.harness.ui.foundation.HarnessButtonVariant
import im.hikaru.harness.ui.foundation.HarnessControlSize
import im.hikaru.harness.ui.foundation.HarnessSearchField
import im.hikaru.harness.ui.foundation.HarnessSurface
import im.hikaru.harness.ui.foundation.HarnessText
import im.hikaru.harness.ui.foundation.HarnessTokens

/** One literal match in text that the conversation renderer can display. */
public data class ConversationFindMatch(
    val messageId: String,
    val contentIndex: Int,
    val startInText: Int,
    val length: Int,
)

/** Bounded result so a very long transcript cannot freeze the search toolbar. */
public data class ConversationFindResult(
    val matches: List<ConversationFindMatch>,
    val truncated: Boolean = false,
)

public const val ConversationFindMatchLimit: Int = 2000

/**
 * Case-insensitive literal search over rendered message text. Markdown is searched as its source
 * text because that is what [ConversationMarkdown] renders; tool and user text use the same path.
 */
public fun findConversationMatches(
    messages: List<Message>,
    query: String,
    limit: Int = ConversationFindMatchLimit,
): ConversationFindResult {
    val needle = query.trim()
    if (needle.isEmpty() || limit <= 0) return ConversationFindResult(emptyList())
    val matches = mutableListOf<ConversationFindMatch>()
    var truncated = false
    for (message in messages) {
        message.content.filterIsInstance<TextContent>().forEachIndexed { contentIndex, content ->
            var from = 0
            while (true) {
                val hit = content.text.indexOf(needle, from, ignoreCase = true)
                if (hit < 0) break
                if (matches.size >= limit) {
                    truncated = true
                    break
                }
                matches += ConversationFindMatch(message.id, contentIndex, hit, needle.length)
                from = hit + needle.length.coerceAtLeast(1)
            }
            if (truncated) return@forEachIndexed
        }
        if (truncated) break
    }
    return ConversationFindResult(matches, truncated)
}

/** Floating Codex-style find bar shared by the conversation transcript. */
@Composable
public fun ConversationFindBar(
    query: String,
    result: ConversationFindResult,
    activeIndex: Int,
    onQueryChange: (String) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val count = when {
        query.isBlank() -> "输入以搜索"
        result.matches.isEmpty() -> "0 个结果"
        else -> "${activeIndex + 1} / ${result.matches.size}" + if (result.truncated) "+" else ""
    }
    HarnessSurface(
        modifier.widthIn(max = 520.dp).fillMaxWidth().semantics { testTag = "conversation-find" },
        radius = 20.dp,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when {
                        event.key == Key.Escape -> { onClose(); true }
                        event.key == Key.Enter && event.isShiftPressed -> { onPrevious(); true }
                        event.key == Key.Enter -> { onNext(); true }
                        else -> false
                    }
                },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HarnessSearchField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = "在对话中搜索",
                accessibilityLabel = "在对话中搜索",
                modifier = Modifier.weight(1f),
                testTag = "conversation-find-field",
                height = 32.dp,
            )
            HarnessText(count, Modifier.semantics { testTag = "conversation-find-count" },
                color = HarnessTokens.Colors.TextMuted)
            HarnessButton("上一个", onPrevious, enabled = result.matches.isNotEmpty(),
                variant = HarnessButtonVariant.Ghost, size = HarnessControlSize.Compact,
                testTag = "conversation-find-prev")
            HarnessButton("下一个", onNext, enabled = result.matches.isNotEmpty(),
                variant = HarnessButtonVariant.Ghost, size = HarnessControlSize.Compact,
                testTag = "conversation-find-next")
            HarnessButton("关闭", onClose, variant = HarnessButtonVariant.Ghost,
                size = HarnessControlSize.Compact, testTag = "conversation-find-close")
        }
    }
}
