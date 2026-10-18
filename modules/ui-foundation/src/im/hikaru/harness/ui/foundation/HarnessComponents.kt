package im.hikaru.harness.ui.foundation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Badge
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

public enum class HarnessControlState {
    Normal,
    Loading,
    Error,
}

/** Card-like surface: elevated background with a low-contrast hairline border. */
@Composable
public fun HarnessSurface(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    dashed: Boolean = false,
    radius: Dp = HarnessTokens.Corners.Medium,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(radius)
    val fill = if (selected) HarnessTokens.Colors.SurfaceSelected else HarnessTokens.Colors.SurfaceElevated
    val outline = if (selected) HarnessTokens.Colors.BorderFocused else HarnessTokens.Colors.BorderSubtle
    if (!dashed) {
        Surface(
            modifier = modifier,
            color = fill,
            contentColor = HarnessTokens.Colors.Text,
            shape = shape,
            border = BorderStroke(1.dp, outline),
        ) { Box(content = content) }
        return
    }
    // Material's border cannot dash. The 2026-10-06 Scheduled capture draws its template cards with a
    // 1px 3-on/2-off dash, so that outline is a stroked round rect; the settings capture is solid and
    // keeps the unchanged branch above.
    Box(
        modifier = modifier
            .clip(shape)
            .background(fill)
            .drawBehind {
                val width = 1.dp.toPx()
                val inset = width / 2f
                val corner = (radius.toPx() - inset).coerceAtLeast(0f)
                val dashed =
                    Path().apply {
                        addRoundRect(
                            RoundRect(
                                inset, inset, size.width - inset, size.height - inset,
                                radiusX = corner, radiusY = corner,
                            ),
                        )
                    }
                drawPath(
                    dashed, outline,
                    style = Stroke(
                        width = width,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx())),
                    ),
                )
            },
    ) {
        CompositionLocalProvider(LocalContentColor provides HarnessTokens.Colors.Text) { content() }
    }
}

@Composable
public fun HarnessText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = HarnessTokens.Colors.Text,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Ellipsis,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = style,
        maxLines = maxLines,
        overflow = overflow,
    )
}

/**
 * Codex's `text-fade-truncate`: a single clipped line whose overflow **fades out** over the last
 * [fade] instead of ending in an ellipsis.
 *
 * Measured on the live tab strip, a tab title carries `white-space: nowrap; overflow: clip;
 * -webkit-mask-image: linear-gradient(to right, #000 calc(100% - 1rem), transparent)` — the mask is
 * what truncates, so a title too long for its slot simply runs out of ink. The class is on the title
 * at rest *and* while the tab is hovered or focused
 * (`group-hover/tab:text-fade-truncate`), so the fade is the only truncation the strip ever shows.
 *
 * The mask is applied to the content layer with [BlendMode.DstIn], which is what a CSS `mask-image`
 * does: the content keeps its own alpha and loses it where the gradient does. The layer has to be
 * composited offscreen for the blend to see what the text drew, hence the explicit
 * [CompositingStrategy.Offscreen].
 *
 * **The caller has to clip.** A CSS mask clips to the element's own box; Compose draws outside the
 * bounds happily, so without the clip a too-long title would paint past its slot and the fade would
 * fall over the wrong ink. A [Text] laid out with `TextOverflow.Clip` in a fixed width clips itself,
 * which is the caller's half of this contract.
 */
public fun Modifier.harnessFadeTruncate(
    fade: Dp = HarnessTokens.Control.TextFadeTruncate,
    fadeColor: Color = Color.Black,
): Modifier = this
    // The layer goes first so the content and the mask below are drawn into the same offscreen
    // surface: `DstIn` can only take alpha away from pixels it shares a layer with.
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        drawRect(
            // The gradient spans the content's own trailing edge and `fade` inwards, so the band is
            // always the last `fade` of whatever width the title was given.
            brush = Brush.horizontalGradient(
                colors = listOf(fadeColor, Color.Transparent),
                startX = size.width - fade.toPx(),
                endX = size.width,
            ),
            blendMode = BlendMode.DstIn,
        )
    }

/** Compact icon button without Material's 48dp minimum touch target, so shell geometry stays exact. */
@Composable
public fun HarnessIconButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    HarnessIconButton(
        contentDescription = contentDescription, onClick = onClick, modifier = modifier,
        state = HarnessButtonState(enabled = enabled, selected = selected), content = content,
    )
}

@Composable
public fun HarnessButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    state: HarnessControlState = HarnessControlState.Normal,
    testTag: String = text,
    selected: Boolean = false,
    variant: HarnessButtonVariant = HarnessButtonVariant.Primary,
    size: HarnessControlSize = HarnessControlSize.Regular,
) {
    HarnessActionButton(
        text = text, onClick = onClick, modifier = modifier,
        state = HarnessButtonState(enabled = enabled, selected = selected, control = state),
        variant = variant, size = size, testTag = testTag,
    )
}

/** External label/support keep the compact editor's full height available for text and its cursor. */
@Composable
public fun HarnessTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    supportingText: String? = null,
    singleLine: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    testTag: String = label,
    readOnly: Boolean = false,
) {
    val colors = HarnessTokens.Colors
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(HarnessTokens.Corners.Medium)
    val borderColor = when {
        !enabled -> colors.Border
        isError -> colors.Danger
        focused -> colors.BorderFocused
        else -> colors.Border
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.One)) {
        HarnessText(
            label,
            color = when {
                !enabled -> colors.TextDisabled
                isError -> colors.Danger
                focused -> colors.BorderFocused
                else -> colors.TextMuted
            },
            style = MaterialTheme.typography.labelLarge,
        )
        BasicTextField(
            value = value, onValueChange = onValueChange, enabled = enabled, readOnly = readOnly,
            singleLine = singleLine, maxLines = if (singleLine) 1 else Int.MAX_VALUE,
            visualTransformation = visualTransformation, interactionSource = interactionSource,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = if (enabled) colors.Text else colors.TextDisabled),
            cursorBrush = SolidColor(if (isError) colors.Danger else colors.Accent),
            modifier = Modifier.fillMaxWidth()
                .semantics {
                    this.testTag = testTag
                    contentDescription = label
                    if (isError) error(supportingText ?: label)
                }
                .heightIn(min = HarnessTokens.Control.Height)
                .border(1.dp, borderColor, shape)
                .background(colors.SurfaceElevated, shape)
                .padding(horizontal = HarnessTokens.Spacing.Three, vertical = HarnessTokens.Spacing.Two),
            decorationBox = { innerTextField ->
                Box(contentAlignment = if (singleLine) Alignment.CenterStart else Alignment.TopStart) {
                    innerTextField()
                }
            },
        )
        supportingText?.let { message ->
            HarnessText(message, color = if (isError) colors.Danger else colors.TextMuted)
        }
    }
}

@Composable
public fun HarnessMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier.background(HarnessTokens.Colors.SurfaceElevated),
        content = content,
    )
}

@Composable
public fun HarnessMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    state: HarnessControlState = HarnessControlState.Normal,
    selected: Boolean = false,
    destructive: Boolean = false,
) {
    HarnessActionSurface(
        label = text, onClick = onClick, modifier = modifier.fillMaxWidth().height(HarnessTokens.Control.Height),
        state = HarnessButtonState(enabled, selected, state), variant = HarnessButtonVariant.Ghost, tag = text,
    ) { color ->
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = HarnessTokens.Spacing.Three),
            horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state == HarnessControlState.Loading) HarnessProgress(color)
            HarnessText(text, color = if (destructive && enabled) HarnessTokens.Colors.Danger else color)
        }
    }
}

@Composable
public fun HarnessDialog(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    if (!visible) return
    Dialog(onDismissRequest = onDismissRequest) {
        HarnessSurface(modifier = Modifier.widthIn(max = HarnessTokens.Control.ContentMaxWidth).padding(HarnessTokens.Spacing.Four)) {
            Column(
                modifier = Modifier.padding(HarnessTokens.Spacing.Four),
                verticalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Three),
                content = {
                    HarnessText(title, color = HarnessTokens.Colors.Text)
                    content()
                },
            )
        }
    }
}

@Composable
public fun HarnessTooltip(
    text: String,
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    HarnessControlledTooltip(text, visible, modifier, content)
}

@Composable
public fun HarnessBadge(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = HarnessTokens.Colors.Accent,
) {
    Badge(
        modifier = modifier,
        containerColor = color,
        contentColor = HarnessTokens.Colors.OnAccent,
    ) { HarnessText(text, color = HarnessTokens.Colors.OnAccent, style = MaterialTheme.typography.labelMedium) }
}

@Composable
public fun HarnessScrollbar(
    fraction: Float,
    position: Float,
    modifier: Modifier = Modifier,
) {
    val clampedFraction = fraction.coerceIn(0.05f, 1f)
    val clampedPosition = position.coerceIn(0f, 1f - clampedFraction)
    Box(modifier = modifier.width(6.dp).fillMaxHeight()) {
        Box(
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxHeight(clampedFraction)
                    .padding(top = (clampedPosition * 100).dp)
                    .width(6.dp)
                    .background(HarnessTokens.Colors.TextMuted, RoundedCornerShape(3.dp)),
        )
    }
}
