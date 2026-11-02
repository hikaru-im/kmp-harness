package im.hikaru.harness.ui.foundation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity

/** The two scrollbar presentations used by the desktop shell. */
public enum class HarnessScrollbarVariant {
    Arrowless,
    Arrowed,
}

/** The semantic region under a pointer in a scrollbar. */
public enum class HarnessScrollbarHitTarget {
    StepBackward,
    PageBackward,
    Thumb,
    PageForward,
    StepForward,
}

/** Scroll position and thumb size expressed as normalized values. */
public data class HarnessScrollbarMetrics(
    val positionFraction: Float,
    val thumbFraction: Float,
    val visible: Boolean = true,
) {
    public val clampedPosition: Float
        get() = positionFraction.coerceIn(0f, 1f)

    public val clampedThumb: Float
        get() = thumbFraction.coerceIn(0f, 1f)
}

/** Pixel geometry for one vertical scrollbar track. */
public data class HarnessScrollbarGeometry(
    val trackStart: Float,
    val trackLength: Float,
    val thumbTop: Float,
    val thumbExtent: Float,
    val arrowExtent: Float,
) {
    public val trackEnd: Float
        get() = trackStart + trackLength

    public val thumbBottom: Float
        get() = thumbTop + thumbExtent

    public val thumbTravel: Float
        get() = (trackLength - thumbExtent).coerceAtLeast(0f)

    public fun hitTarget(y: Float, variant: HarnessScrollbarVariant): HarnessScrollbarHitTarget {
        if (variant == HarnessScrollbarVariant.Arrowed && y < trackStart) return HarnessScrollbarHitTarget.StepBackward
        if (variant == HarnessScrollbarVariant.Arrowed && y > trackEnd) return HarnessScrollbarHitTarget.StepForward
        return when {
            y < thumbTop -> HarnessScrollbarHitTarget.PageBackward
            y > thumbBottom -> HarnessScrollbarHitTarget.PageForward
            else -> HarnessScrollbarHitTarget.Thumb
        }
    }
}

/**
 * Computes the track and thumb geometry without Compose state. [containerExtent] and all dimensions are
 * pixels, which keeps this function usable by Canvas and desktop geometry tests alike.
 */
public fun harnessScrollbarGeometry(
    containerExtent: Float,
    metrics: HarnessScrollbarMetrics,
    variant: HarnessScrollbarVariant = HarnessScrollbarVariant.Arrowless,
    arrowExtent: Float = 18f,
    minimumThumbExtent: Float = 16f,
): HarnessScrollbarGeometry {
    val extent = containerExtent.coerceAtLeast(0f)
    val resolvedArrow = if (variant == HarnessScrollbarVariant.Arrowed) {
        arrowExtent.coerceAtLeast(0f).coerceAtMost(extent / 2f)
    } else {
        0f
    }
    val trackLength = (extent - resolvedArrow * 2f).coerceAtLeast(0f)
    val thumbExtent = if (trackLength == 0f) {
        0f
    } else {
        (trackLength * metrics.clampedThumb).coerceIn(
            minOf(minimumThumbExtent.coerceAtLeast(0f), trackLength),
            trackLength,
        )
    }
    val travel = (trackLength - thumbExtent).coerceAtLeast(0f)
    return HarnessScrollbarGeometry(
        trackStart = resolvedArrow,
        trackLength = trackLength,
        thumbTop = resolvedArrow + travel * metrics.clampedPosition,
        thumbExtent = thumbExtent,
        arrowExtent = resolvedArrow,
    )
}

/** Metrics adapter for Compose's pixel-based [androidx.compose.foundation.ScrollState]. */
public fun harnessScrollbarMetrics(
    value: Int,
    maxValue: Int,
    viewportSize: Int,
    minimumFraction: Float = 0.05f,
): HarnessScrollbarMetrics {
    val viewport = viewportSize.coerceAtLeast(0)
    val range = maxValue.coerceAtLeast(0)
    val content = (viewport + range).coerceAtLeast(1)
    return HarnessScrollbarMetrics(
        positionFraction = if (range == 0) 0f else value.toFloat() / range,
        thumbFraction = (viewport.toFloat() / content).coerceIn(minimumFraction.coerceIn(0f, 1f), 1f),
        visible = range > 0 && viewport > 0,
    )
}

/**
 * A small overlay scrollbar. The caller owns scrolling; this component only translates pointer
 * gestures into normalized positions or pixel deltas.
 */
@Composable
public fun HarnessOverlayScrollbar(
    metrics: HarnessScrollbarMetrics,
    modifier: Modifier = Modifier,
    variant: HarnessScrollbarVariant = HarnessScrollbarVariant.Arrowless,
    arrowExtent: Dp = 18.dp,
    minimumThumbExtent: Dp = 16.dp,
    stepDistance: Dp = 40.dp,
    pageDistance: Dp? = null,
    onScrollToFraction: (Float) -> Unit,
    onScrollBy: (Float) -> Unit = {},
    testTag: String? = null,
    contentDescription: String = "滚动条",
) {
    if (!metrics.visible) {
        Canvas(
            modifier = modifier.semantics {
                this.contentDescription = contentDescription
                if (testTag != null) this.testTag = testTag
            },
        ) {}
        return
    }
    val density = LocalDensity.current
    val arrowPx = with(density) { arrowExtent.toPx() }
    val minimumThumbPx = with(density) { minimumThumbExtent.toPx() }
    val stepPx = with(density) { stepDistance.toPx() }
    val pagePx = with(density) { (pageDistance ?: (arrowExtent * 2 + stepDistance)).toPx() }
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    var dragging by remember { mutableStateOf(false) }
    val controlColor by animateColorAsState(
        targetValue = if (hovered || dragging) HarnessTokens.Colors.ScrollbarThumbStrong else HarnessTokens.Colors.ScrollbarThumb,
        animationSpec = tween(100),
        label = "harness-scrollbar-color",
    )
    val tagModifier = if (testTag == null) Modifier else Modifier.semantics { this.testTag = testTag }
    val interactionModifier = Modifier
        .fillMaxHeight()
        .hoverable(interactions)
        .semantics { this.contentDescription = contentDescription }
        .then(tagModifier)
        .pointerInput(metrics, variant, arrowPx, minimumThumbPx, stepPx, pagePx) {
            detectTapGestures { position ->
                val geometry = harnessScrollbarGeometry(
                    containerExtent = size.height.toFloat(),
                    metrics = metrics,
                    variant = variant,
                    arrowExtent = arrowPx,
                    minimumThumbExtent = minimumThumbPx,
                )
                when (geometry.hitTarget(position.y, variant)) {
                    HarnessScrollbarHitTarget.StepBackward -> onScrollBy(-stepPx)
                    HarnessScrollbarHitTarget.PageBackward -> onScrollBy(-pagePx)
                    HarnessScrollbarHitTarget.Thumb -> Unit
                    HarnessScrollbarHitTarget.PageForward -> onScrollBy(pagePx)
                    HarnessScrollbarHitTarget.StepForward -> onScrollBy(stepPx)
                }
            }
        }
        .pointerInput(metrics, variant, arrowPx, minimumThumbPx) {
            var startY = 0f
            var startFraction = 0f
            var target = HarnessScrollbarHitTarget.Thumb
            detectDragGestures(
                onDragStart = { position ->
                    val geometry = harnessScrollbarGeometry(
                        containerExtent = size.height.toFloat(),
                        metrics = metrics,
                        variant = variant,
                        arrowExtent = arrowPx,
                        minimumThumbExtent = minimumThumbPx,
                    )
                    target = geometry.hitTarget(position.y, variant)
                    if (target == HarnessScrollbarHitTarget.Thumb) {
                        startY = position.y
                        startFraction = metrics.clampedPosition
                        dragging = true
                    }
                },
                onDragEnd = { dragging = false },
                onDragCancel = { dragging = false },
                onDrag = { change, _ ->
                    if (target == HarnessScrollbarHitTarget.Thumb) {
                        val geometry = harnessScrollbarGeometry(
                            containerExtent = size.height.toFloat(),
                            metrics = metrics,
                            variant = variant,
                            arrowExtent = arrowPx,
                            minimumThumbExtent = minimumThumbPx,
                        )
                        if (geometry.thumbTravel > 0f) {
                            onScrollToFraction(startFraction + (change.position.y - startY) / geometry.thumbTravel)
                        }
                        change.consume()
                    }
                },
            )
        }
    Canvas(modifier.then(interactionModifier).clipToBounds()) {
        val geometry = harnessScrollbarGeometry(
            containerExtent = size.height,
            metrics = metrics,
            variant = variant,
            arrowExtent = arrowPx,
            minimumThumbExtent = minimumThumbPx,
        )
        if (geometry.thumbExtent > 0f) {
            val thumbWidth = with(density) { (if (variant == HarnessScrollbarVariant.Arrowed) 8.dp else 6.dp).toPx() }
            val endInset = with(density) { if (variant == HarnessScrollbarVariant.Arrowed) 2.dp.toPx() else 0f }
            // Codex centres the platform's thin thumb in the reserved gutter (the transcript
            // reserves 11px per side); right-aligning it would sit the bar ~2.5dp off that axis.
            drawRoundRect(
                color = controlColor,
                topLeft = Offset((size.width - thumbWidth - endInset) / 2f, geometry.thumbTop),
                size = Size(thumbWidth, geometry.thumbExtent),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(thumbWidth / 2f),
            )
        }
        if (variant == HarnessScrollbarVariant.Arrowed && geometry.arrowExtent > 0f) {
            drawScrollbarArrow(controlColor, geometry.arrowExtent / 2f, upward = true)
            drawScrollbarArrow(controlColor, size.height - geometry.arrowExtent / 2f, upward = false)
        }
    }
}

private fun DrawScope.drawScrollbarArrow(color: Color, centerY: Float, upward: Boolean) {
    val width = 8.dp.toPx()
    val height = 5.dp.toPx()
    val centerX = size.width / 2f
    val path = Path().apply {
        if (upward) {
            moveTo(centerX - width / 2f, centerY + height / 2f)
            lineTo(centerX, centerY - height / 2f)
            lineTo(centerX + width / 2f, centerY + height / 2f)
        } else {
            moveTo(centerX - width / 2f, centerY - height / 2f)
            lineTo(centerX, centerY + height / 2f)
            lineTo(centerX + width / 2f, centerY - height / 2f)
        }
    }
    drawPath(path, color = color, style = Stroke(width = 1.25.dp.toPx()))
}
