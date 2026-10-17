package im.hikaru.harness.ui.foundation

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Compact text control shared by composer triggers and popup headers. The reference footer chip is a
 * 28px pill with `0 6px` inset, a 16px leading icon, a 4px gap and 13 / 18 label text; hovering fills
 * it with `rgba(255,255,255,.08)` (measured on the expanded permission chip).
 */
@Composable
public fun HarnessComposerButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    leading: HarnessIconName? = null, leadingTint: Color? = null,
    trailing: HarnessIconName? = null, tint: Color = HarnessTokens.Colors.Text, testTag: String,
    contentPaddingX: Dp = HarnessTokens.Composer.ButtonInsetX,
    leadingArtwork: (@Composable () -> Unit)? = null,
    trailingArtwork: (@Composable () -> Unit)? = null,
    /** Keeps the live `data-[state=open]` 8% fill while the trigger's popup is open. */
    active: Boolean = false,
    labelContent: (@Composable RowScope.() -> Unit)? = null,
) {
    val interactions = remember { MutableInteractionSource() }
    val hovered by interactions.collectIsHoveredAsState()
    TextButton(onClick = onClick, enabled = enabled, interactionSource = interactions, shape = CircleShape,
        colors = ButtonDefaults.textButtonColors(
            containerColor = if ((hovered || active) && enabled) HarnessTokens.Colors.Text.copy(alpha = 0.08f) else Color.Transparent,
            contentColor = tint,
        ),
        // The live footer slot is exactly 28 high; Material's 40dp button minimum must not stretch it.
        modifier = modifier.height(HarnessTokens.Composer.ActionSize).semantics { this.testTag = testTag },
        contentPadding = PaddingValues(horizontal = contentPaddingX, vertical = 0.dp)) {
        when {
            leadingArtwork != null -> leadingArtwork()
            leading != null -> HarnessIcon(leading, size = 16.dp, tint = leadingTint ?: tint)
        }
        // Measured chip: `0 6px` inset, 16px icon, 4px gap, 13/18 label.
        if (leadingArtwork != null || leading != null) Spacer(Modifier.width(4.dp))
        if (labelContent != null) labelContent() else HarnessText(text, Modifier.weight(1f, fill = false),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (enabled) tint else HarnessTokens.Colors.TextDisabled,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = HarnessTokens.Composer.LabelSize,
                lineHeight = HarnessTokens.Composer.LabelLineHeight))
        when {
            trailingArtwork != null -> { Spacer(Modifier.width(HarnessTokens.Composer.ControlGap)); trailingArtwork() }
            trailing != null -> HarnessIcon(trailing, size = HarnessTokens.Control.SelectChevron, tint = HarnessTokens.Colors.TextMuted)
        }
    }
}

/**
 * A permission/model popup row. The live row is 312×42.6: `5px 8px` inset, radius 15, a 13 / 18.6 label
 * directly over a 12 / 14 muted description (no gap), and an 18px leading icon.
 */
@Composable
public fun HarnessComposerChoice(
    text: String, description: String? = null, selected: Boolean, onClick: () -> Unit,
    modifier: Modifier = Modifier, icon: HarnessIconName? = null,
    tint: Color = HarnessTokens.Colors.Text, testTag: String,
    artwork: (@Composable () -> Unit)? = null,
    descriptionTint: Color = HarnessTokens.Colors.TextMuted,
    selectedArtwork: (@Composable () -> Unit)? = null,
) {
    TextButton(onClick = onClick,
        modifier = modifier.fillMaxWidth().semantics { this.testTag = testTag; this.selected = selected },
        shape = androidx.compose.foundation.shape.RoundedCornerShape(HarnessTokens.Composer.PopupItemRadius),
        contentPadding = PaddingValues(horizontal = HarnessTokens.Composer.PopupItemInsetX,
            vertical = HarnessTokens.Composer.PopupItemInsetY)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Composer.PopupItemGap)) {
            // Live row icon is 18px inside an 18px box, drawn at 75% opacity.
            if (artwork != null) Box(Modifier.alpha(0.75f)) { artwork() }
            else if (icon != null) Box(Modifier.alpha(0.75f)) { HarnessIcon(icon, size = 18.dp, tint = tint) }
            Column(Modifier.weight(1f)) {
                HarnessText(text, color = tint, style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = HarnessTokens.Composer.LabelSize, lineHeight = HarnessTokens.Composer.LabelLineHeight))
                if (description != null) HarnessText(description, color = descriptionTint,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 14.sp))
            }
            if (selected) {
                if (selectedArtwork != null) Box(Modifier.alpha(0.75f)) { selectedArtwork() }
                else HarnessIcon(HarnessIconName.Check, size = 16.dp, tint = tint)
            }
        }
    }
}

/** A single fixed-size action slot; loading never changes its geometry. */
@Composable
public fun HarnessComposerAction(
    label: String, icon: HarnessIconName? = null, enabled: Boolean, loading: Boolean,
    onClick: () -> Unit, testTag: String,
    containerColor: Color = HarnessTokens.Colors.Text,
    contentColor: Color = HarnessTokens.Colors.Canvas,
    // The live empty composer keeps its full primary disc while showing the voice glyph.
    forceActive: Boolean = false,
    content: (@Composable () -> Unit)? = null,
) {
    val active = forceActive || enabled || loading
    Surface(onClick = onClick, enabled = enabled && !loading, shape = CircleShape,
        color = if (active) containerColor else containerColor.copy(alpha = 0.35f),
        modifier = Modifier.size(HarnessTokens.Composer.ActionSize).semantics {
            this.testTag = testTag; contentDescription = label
            if (!enabled || loading) disabled()
        }) {
        Box(contentAlignment = Alignment.Center) {
            val tint = if (active) contentColor else contentColor.copy(alpha = 0.7f)
            when {
                loading -> HarnessProgress(tint)
                content != null -> content()
                icon != null -> HarnessIcon(icon, size = 16.dp, tint = tint)
            }
        }
    }
}
