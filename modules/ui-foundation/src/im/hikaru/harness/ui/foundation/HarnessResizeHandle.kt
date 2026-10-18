package im.hikaru.harness.ui.foundation

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

internal expect val HorizontalResizePointerIcon: PointerIcon

/** A shared resize boundary with a generous hit region and theme-aware hairline. */
@Composable
public fun HarnessResizeHandle(
    onResize: (Float) -> Unit,
    modifier: Modifier = Modifier,
    description: String = "调整区域宽度",
    lineAlignment: Alignment = Alignment.Center,
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    var dragging by remember { mutableStateOf(false) }
    val latestResize by rememberUpdatedState(onResize)
    val edge = HarnessTokens.Colors.ChromeSeamEdge
    val ink = HarnessTokens.Colors.ChromeSeam
    Box(
        modifier.width(HarnessTokens.Control.SplitSeamWidth).fillMaxHeight()
            .hoverable(source)
            .pointerHoverIcon(HorizontalResizePointerIcon, overrideDescendants = true)
            .pointerInput(Unit) {
                try {
                    detectHorizontalDragGestures(
                        onDragStart = { dragging = true },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                    ) { change, amount ->
                        change.consume()
                        latestResize(amount)
                    }
                } finally {
                    dragging = false
                }
            }
            .semantics { contentDescription = description },
        contentAlignment = lineAlignment,
    ) {
        Box(Modifier.width(HarnessTokens.Control.SplitSeamLine).fillMaxHeight().drawBehind {
            drawRect(edge)
            if (hovered || dragging) {
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, ink, Color.Transparent)))
            }
        })
    }
}
