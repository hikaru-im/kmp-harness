package im.hikaru.harness.ui.foundation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** One transient message. The host shows at most [HarnessToastState.MaxVisible] of them at a time. */
public data class HarnessToastMessage(
    val id: Long,
    val text: String,
    val isError: Boolean = false,
    val action: HarnessToastAction? = null,
)

/** An optional inline command on a toast, e.g. the retry a failed read offers. */
public data class HarnessToastAction(
    val label: String,
    val onClick: () -> Unit,
)

/**
 * Transient notifications, following the Codex toaster contract: messages stack at the top center of
 * the window, show at most three, and dismiss themselves after a few seconds. They are reserved for
 * outcomes the user cannot keep on screen as page content (failed commands, errors) — never a
 * permanent sentence underneath an editor.
 */
public class HarnessToastState internal constructor(
    private val scope: CoroutineScope,
) {
    public var messages: List<HarnessToastMessage> by mutableStateOf(emptyList())
        private set
    private var nextId = 0L

    /** Newest first: the reference's toaster lays top-anchored toasts out in arrival order. */
    public fun show(
        text: String,
        isError: Boolean = false,
        action: HarnessToastAction? = null,
        durationMillis: Long = DefaultDurationMillis,
    ) {
        val message = HarnessToastMessage(++nextId, text, isError, action)
        messages = (listOf(message) + messages).take(MaxVisible)
        scope.launch {
            delay(durationMillis)
            dismiss(message.id)
        }
    }

    public fun dismiss(id: Long) {
        messages = messages.filterNot { it.id == id }
    }

    public companion object {
        public const val MaxVisible: Int = 3
        public const val DefaultDurationMillis: Long = 4_000
    }
}

@Composable
public fun rememberHarnessToastState(): HarnessToastState {
    val scope = rememberCoroutineScope()
    return remember(scope) { HarnessToastState(scope) }
}

/**
 * Draws [state]'s messages at the top center of the window, up to the composer column's width.
 *
 * Codex mounts its sonner toaster in a `pointer-events-none fixed z-[60]` area centred over the
 * composer (`position: top-center`, `offset: 0`, `gap: 8`, `visibleToasts: 3`), and sizes it to
 * `--composer-adjacent-max-width`. The toast itself is the elevated surface with a hairline ring,
 * the same shadow family as the rail menus.
 */
@Composable
public fun HarnessToastHost(
    state: HarnessToastState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.widthIn(max = HarnessTokens.Composer.MaxWidth),
        verticalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        state.messages.forEach { message ->
            HarnessToastCard(message, onDismiss = { state.dismiss(message.id) })
        }
    }
}

@Composable
private fun HarnessToastCard(
    message: HarnessToastMessage,
    onDismiss: () -> Unit,
) {
    val shape = RoundedCornerShape(HarnessTokens.Corners.Medium)
    Row(
        modifier =
            Modifier
                .dropShadow(
                    shape = shape,
                    shadow =
                        Shadow(
                            radius = HarnessTokens.Control.MenuShadowBlur,
                            spread = HarnessTokens.Control.MenuShadowSpread,
                            offset = DpOffset(x = 0.dp, y = HarnessTokens.Control.MenuShadowOffsetY),
                            color = HarnessTokens.Control.MenuShadowColor,
                        ),
                )
                .clip(shape)
                .background(HarnessTokens.Colors.SurfaceElevated)
                // Same 1px hairline ring the rail menus wear.
                .border(1.dp, HarnessTokens.Colors.BorderSubtle, shape)
                .padding(horizontal = HarnessTokens.Spacing.Three, vertical = HarnessTokens.Spacing.Two)
                .semantics { testTag = "harness-toast-${if (message.isError) "error" else "info"}" },
        horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (message.isError) {
            HarnessIcon(
                name = HarnessIconName.Warning,
                tint = HarnessTokens.Colors.Danger,
                size = HarnessTokens.Control.NavigatorIcon,
            )
        }
        HarnessText(
            text = message.text,
            color = HarnessTokens.Colors.Text,
            style = MaterialTheme.typography.bodySmall,
        )
        message.action?.let { action ->
            HarnessText(
                text = action.label,
                color = HarnessTokens.Colors.Accent,
                style = MaterialTheme.typography.bodySmall,
                modifier =
                    Modifier.clickable {
                        action.onClick()
                        onDismiss()
                    },
            )
        }
    }
}
