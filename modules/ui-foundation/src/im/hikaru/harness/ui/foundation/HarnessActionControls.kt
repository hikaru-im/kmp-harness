package im.hikaru.harness.ui.foundation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp

internal data class HarnessActionColors(val background: Color, val foreground: Color, val border: Color)

internal fun harnessActionColors(
    colors: HarnessColors,
    state: HarnessButtonState,
    variant: HarnessButtonVariant,
    emphasized: Boolean,
    pressed: Boolean = false,
): HarnessActionColors {
    if (!state.enabled) return HarnessActionColors(colors.SurfaceSecondary, colors.TextDisabled, Color.Transparent)
    val background = when (variant) {
        HarnessButtonVariant.Primary -> if (emphasized || state.selected) colors.AccentStrong else colors.Accent
        HarnessButtonVariant.Destructive -> if (emphasized || state.selected) colors.Danger.copy(alpha = .85f) else colors.Danger
        HarnessButtonVariant.Secondary -> when {
            state.selected -> colors.SurfaceSelected
            pressed -> colors.PressedBackground
            emphasized -> colors.HoverBackground
            else -> colors.SurfaceSecondary
        }
        HarnessButtonVariant.Ghost -> when {
            state.selected -> colors.SurfaceSelected
            pressed -> colors.PressedBackground
            emphasized -> colors.HoverBackground
            else -> Color.Transparent
        }
    }
    val foreground = when {
        variant == HarnessButtonVariant.Primary || variant == HarnessButtonVariant.Destructive -> colors.OnAccent
        state.control == HarnessControlState.Error -> colors.Danger
        else -> colors.Text
    }
    return HarnessActionColors(background, foreground, if (state.control == HarnessControlState.Error) colors.Danger else Color.Transparent)
}

@Composable
internal fun HarnessActionSurface(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier,
    state: HarnessButtonState,
    variant: HarnessButtonVariant,
    tag: String,
    highlighted: Boolean = false,
    radius: Dp = HarnessTokens.Corners.Medium,
    content: @Composable BoxScope.(Color) -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val hovered by source.collectIsHoveredAsState()
    val focused by source.collectIsFocusedAsState()
    val colors = harnessActionColors(HarnessTokens.Colors, state.copy(selected = state.selected || highlighted), variant,
        state.interactive && (pressed || hovered), pressed = state.interactive && pressed)
    val shape = RoundedCornerShape(radius)
    Box(
        modifier = modifier
            .harnessPointer(state.interactive)
            .clip(shape)
            .background(colors.background)
            .border(BorderStroke(1.dp, if (focused && state.interactive) HarnessTokens.Colors.BorderFocused else colors.border), shape)
            .hoverable(source, enabled = state.interactive)
            .clickable(interactionSource = source, indication = null, enabled = state.interactive, role = Role.Button) {
                if (state.interactive) onClick()
            }
            .semantics {
                contentDescription = label
                testTag = tag
                selected = state.selected
                when (state.control) {
                    HarnessControlState.Normal -> Unit
                    HarnessControlState.Loading -> {
                        stateDescription = "Loading"
                        progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                    }
                    HarnessControlState.Error -> error("Action failed")
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.foreground) { content(colors.foreground) }
    }
}

/** Static, controlled button. The owner updates [state] after invoking an action. */
@Composable
public fun HarnessActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    state: HarnessButtonState = HarnessButtonState(),
    variant: HarnessButtonVariant = HarnessButtonVariant.Primary,
    size: HarnessControlSize = HarnessControlSize.Regular,
    testTag: String = text,
) {
    HarnessActionSurface(
        label = text, onClick = onClick,
        modifier = modifier.height(if (size == HarnessControlSize.Compact) HarnessTokens.Control.CompactHeight else HarnessTokens.Control.Height),
        state = state, variant = variant, tag = testTag,
        radius = if (size == HarnessControlSize.Compact) HarnessTokens.Corners.Control else HarnessTokens.Corners.Medium,
    ) { color ->
        Row(
            modifier = Modifier.padding(
                horizontal = if (size == HarnessControlSize.Compact) HarnessTokens.Control.CompactPaddingX else HarnessTokens.Spacing.Four,
            ),
            horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.control == HarnessControlState.Loading) HarnessProgress(color)
            HarnessText(text, color = color, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

/**
 * Outlined select trigger: a label plus a drawn chevron on a 28px, radius-9 strip.
 *
 * The 2026-10-06 settings capture draws every row dropdown (Default app, Automatic, Close shortcut only)
 * as a 28px-tall pill with a white fill and a 1px hairline border, not as a filled secondary button. The
 * left-edge fit puts the corner at 9 (Corners.Control, validated against the same capture's switch) and
 * the content inset at 14 (SelectPaddingX: the ink lands at 15-16 a side).
 */
@Composable
public fun HarnessSelectTrigger(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    state: HarnessControlState = HarnessControlState.Normal,
    testTag: String = text,
    leading: (@Composable () -> Unit)? = null,
) {
    val active = state.canInteract(enabled)
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val hovered by source.collectIsHoveredAsState()
    val focused by source.collectIsFocusedAsState()
    val shape = RoundedCornerShape(HarnessTokens.Corners.Control)
    val colors = HarnessTokens.Colors
    val background = when {
        !active -> colors.SurfaceSecondary
        pressed -> colors.PressedBackground
        hovered -> colors.HoverBackground
        else -> colors.SurfaceElevated
    }
    val foreground = if (active) colors.Text else colors.TextDisabled
    Box(
        modifier = modifier
            .height(HarnessTokens.Control.CompactHeight)
            .harnessPointer(active)
            .clip(shape)
            .background(background)
            .border(BorderStroke(1.dp, if (focused && active) colors.BorderFocused else colors.BorderSubtle), shape)
            .hoverable(source, enabled = active)
            .clickable(interactionSource = source, indication = null, enabled = active, role = Role.Button) {
                if (active) onClick()
            }
            .semantics {
                contentDescription = text
                this.testTag = testTag
                when (state) {
                    HarnessControlState.Normal -> Unit
                    HarnessControlState.Loading -> stateDescription = "Loading"
                    HarnessControlState.Error -> error("Action failed")
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = HarnessTokens.Control.SelectPaddingX),
            horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading?.invoke()
            HarnessText(text, color = foreground, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            HarnessIcon(
                HarnessIconName.ChevronDown,
                tint = if (active) colors.TextMuted else colors.TextDisabled,
                size = HarnessTokens.Control.SelectChevron,
                glyphScale = HarnessTokens.Control.SelectChevronScale,
            )
        }
    }
}

/** Typed counterpart of the existing compact icon-button API. */
@Composable
public fun HarnessIconButton(
    contentDescription: String,
    onClick: () -> Unit,
    state: HarnessButtonState,
    modifier: Modifier = Modifier,
    variant: HarnessButtonVariant = HarnessButtonVariant.Ghost,
    size: HarnessControlSize = HarnessControlSize.Compact,
    testTag: String = contentDescription,
    content: @Composable RowScope.() -> Unit,
) {
    HarnessActionSurface(
        label = contentDescription, onClick = onClick,
        modifier = modifier.size(if (size == HarnessControlSize.Compact) HarnessTokens.Control.IconButton else HarnessTokens.Control.Height),
        state = state, variant = variant, tag = testTag,
    ) { color ->
        if (state.control == HarnessControlState.Loading) HarnessProgress(color)
        else Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

@Composable
internal fun HarnessProgress(color: Color = HarnessTokens.Colors.TextMuted) {
    if (LocalHarnessThemeCustomization.current.reduceMotion == true) {
        HarnessIcon(HarnessIconName.More, tint = color, size = HarnessTokens.Control.Icon)
    } else CircularProgressIndicator(modifier = Modifier.size(HarnessTokens.Control.Icon), color = color, strokeWidth = 2.dp)
}

/** Source: app-shared CSS switch track 32x19px, thumb inset 3px (thumb 13px). */
public object HarnessToggleGeometry {
    public val SwitchWidth = 32.dp
    public val SwitchHeight = 19.dp
    public val SwitchThumbInset = 3.dp
    public val SwitchThumb = 13.dp
    /** Foundation policy for the checkbox glyph; not an independently measured Codex token. */
    public val Checkbox = 16.dp
}

@Composable
public fun HarnessSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    state: HarnessControlState = HarnessControlState.Normal,
    testTag: String = contentDescription,
) {
    val active = state.canInteract(enabled)
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    Box(
        modifier = modifier
            .harnessPointer(active)
            .size(HarnessToggleGeometry.SwitchWidth, HarnessToggleGeometry.SwitchHeight)
            .alpha(if (active) 1f else 0.5f)
            .clip(CircleShape)
            .background(if (checked) HarnessTokens.Colors.Accent else HarnessTokens.Colors.BorderStrong)
            .border(1.dp, if (focused) HarnessTokens.Colors.BorderFocused else Color.Transparent, CircleShape)
            .triStateToggleable(
                state = if (checked) ToggleableState.On else ToggleableState.Off,
                enabled = active, role = Role.Switch, interactionSource = source, indication = null,
                onClick = { if (active) onCheckedChange(!checked) },
            )
            .semantics {
                this.contentDescription = contentDescription
                this.testTag = testTag
                if (state == HarnessControlState.Loading) stateDescription = "Loading"
                if (state == HarnessControlState.Error) error("Update failed")
            }
            .padding(HarnessToggleGeometry.SwitchThumbInset),
    ) {
        Box(
            modifier = Modifier.align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                .size(HarnessToggleGeometry.SwitchThumb).background(Color.White, CircleShape),
        )
    }
}

@Composable
public fun HarnessCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    state: HarnessControlState = HarnessControlState.Normal,
    testTag: String = contentDescription,
) {
    HarnessTriStateCheckbox(
        checked = if (checked) ToggleableState.On else ToggleableState.Off,
        onClick = { onCheckedChange(!checked) }, contentDescription = contentDescription,
        modifier = modifier, enabled = enabled, state = state, testTag = testTag,
    )
}

/** Indeterminate is explicit; the owner chooses the next value after [onClick]. */
@Composable
public fun HarnessTriStateCheckbox(
    checked: ToggleableState,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    state: HarnessControlState = HarnessControlState.Normal,
    testTag: String = contentDescription,
) {
    val active = state.canInteract(enabled)
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val shape = RoundedCornerShape(HarnessTokens.Corners.Small)
    val ink = HarnessTokens.Colors.OnAccent
    Box(
        modifier = modifier.size(HarnessToggleGeometry.Checkbox)
            .alpha(if (active) 1f else 0.5f).clip(shape)
            .background(if (checked != ToggleableState.Off) HarnessTokens.Colors.Accent else Color.Transparent)
            .border(1.dp, if (focused) HarnessTokens.Colors.BorderFocused else HarnessTokens.Colors.BorderStrong, shape)
            .triStateToggleable(checked, source, null, enabled = active, role = Role.Checkbox, onClick = { if (active) onClick() })
            .semantics {
                this.contentDescription = contentDescription
                this.testTag = testTag
                if (state == HarnessControlState.Loading) stateDescription = "Loading"
                if (state == HarnessControlState.Error) error("Update failed")
            },
    ) {
        Canvas(Modifier.size(HarnessToggleGeometry.Checkbox)) {
            if (checked == ToggleableState.Indeterminate) {
                drawLine(ink, Offset(size.width * .25f, size.height * .5f), Offset(size.width * .75f, size.height * .5f), 2.dp.toPx(), StrokeCap.Round)
            } else if (checked == ToggleableState.On) {
                val middle = Offset(size.width * .43f, size.height * .7f)
                drawLine(ink, Offset(size.width * .23f, size.height * .5f), middle, 2.dp.toPx(), StrokeCap.Round)
                drawLine(ink, middle, Offset(size.width * .78f, size.height * .3f), 2.dp.toPx(), StrokeCap.Round)
            }
        }
    }
}
