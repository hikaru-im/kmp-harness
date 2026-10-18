package im.hikaru.harness.ui.foundation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/** In-composition overlay, anchored in local coordinates and kept inside the available viewport. */
@Composable
public fun HarnessAnchoredOverlay(
    anchorBounds: Rect, onDismiss: () -> Unit, testTag: String,
    content: @Composable () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Box(Modifier.fillMaxSize().focusRequester(focus).onPreviewKeyEvent {
        if (it.type == KeyEventType.KeyDown && it.key == Key.Escape) { onDismiss(); true } else false
    }.focusable()) {
        Box(Modifier.fillMaxSize().clickable(
            interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss,
        ).semantics { this.testTag = "$testTag-scrim" })
        Layout(content = content, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
            val inset = 8.dp.roundToPx()
            val gap = 8.dp.roundToPx()
            val panel = measurables.single().measure(constraints.copy(
                minWidth = 0, minHeight = 0,
                maxWidth = (constraints.maxWidth - inset * 2).coerceAtLeast(0),
                maxHeight = (constraints.maxHeight - inset * 2).coerceAtLeast(0),
            ))
            layout(constraints.maxWidth, constraints.maxHeight) {
                val right = anchorBounds.right.roundToInt() + gap
                val left = anchorBounds.left.roundToInt() - gap - panel.width
                val x = if (right + panel.width <= constraints.maxWidth - inset) right else left
                panel.place(
                    x.coerceIn(inset, (constraints.maxWidth - inset - panel.width).coerceAtLeast(inset)),
                    (anchorBounds.bottom.roundToInt() - panel.height)
                        .coerceIn(inset, (constraints.maxHeight - inset - panel.height).coerceAtLeast(inset)),
                )
            }
        }
    }
}
