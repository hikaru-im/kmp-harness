package im.hikaru.harness.ui.foundation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow

/** Compact text control shared by composer triggers and popup headers. */
@Composable
public fun HarnessComposerButton(
    text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true,
    trailing: HarnessIconName? = null, tint: Color = HarnessTokens.Colors.Text, testTag: String,
) {
    TextButton(onClick = onClick, enabled = enabled,
        modifier = modifier.heightIn(min = HarnessTokens.Composer.ActionSize).semantics { this.testTag = testTag },
        contentPadding = PaddingValues(horizontal = HarnessTokens.Composer.ControlGap, vertical = HarnessTokens.Spacing.One)) {
        HarnessText(text, Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (enabled) tint else HarnessTokens.Colors.TextDisabled, style = MaterialTheme.typography.bodyMedium)
        if (trailing != null) HarnessIcon(trailing, size = HarnessTokens.Control.SelectChevron, tint = HarnessTokens.Colors.TextMuted)
    }
}

@Composable
public fun HarnessComposerChoice(
    text: String, description: String? = null, selected: Boolean, onClick: () -> Unit,
    modifier: Modifier = Modifier, icon: HarnessIconName? = null,
    tint: Color = HarnessTokens.Colors.Text, testTag: String,
) {
    TextButton(onClick = onClick,
        modifier = modifier.fillMaxWidth().semantics { this.testTag = testTag; this.selected = selected },
        contentPadding = PaddingValues(HarnessTokens.Composer.PopupInset)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two)) {
            if (icon != null) HarnessIcon(icon, tint = tint)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.One)) {
                HarnessText(text, color = tint, style = MaterialTheme.typography.bodyMedium)
                if (description != null) HarnessText(description, color = HarnessTokens.Colors.TextMuted, style = MaterialTheme.typography.bodySmall)
            }
            if (selected) HarnessIcon(HarnessIconName.Check, tint = tint)
        }
    }
}

/** A single fixed-size action slot; loading never changes its geometry. */
@Composable
public fun HarnessComposerAction(
    label: String, icon: HarnessIconName, enabled: Boolean, loading: Boolean,
    onClick: () -> Unit, testTag: String,
) {
    Surface(onClick = onClick, enabled = enabled && !loading, shape = CircleShape,
        color = if (enabled || loading) HarnessTokens.Colors.Text else HarnessTokens.Colors.SurfaceSelected,
        modifier = Modifier.size(HarnessTokens.Composer.ActionSize).semantics {
            this.testTag = testTag; contentDescription = label
            if (!enabled || loading) disabled()
        }) {
        Box(contentAlignment = Alignment.Center) {
            val tint = if (enabled || loading) HarnessTokens.Colors.Canvas else HarnessTokens.Colors.TextDisabled
            if (loading) HarnessProgress(tint) else HarnessIcon(icon, tint = tint)
        }
    }
}
