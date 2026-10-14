package im.hikaru.harness.ui.foundation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment.Companion.CenterStart
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp

/** Controlled, compact search input with an optional clear action. */
@Composable
public fun HarnessSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    accessibilityLabel: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    clearLabel: String = "清除搜索",
    testTag: String = accessibilityLabel,
    height: Dp = HarnessTokens.Control.SettingsSearchHeight,
    cornerRadius: Dp = HarnessTokens.Control.SettingsSearchRadius,
) {
    val focusRequester = remember { FocusRequester() }
    val colors = HarnessTokens.Colors

    Row(
        modifier =
            modifier
                .height(height)
                .clip(RoundedCornerShape(cornerRadius))
                .background(colors.SurfaceSecondary)
                // The 2026-10-06 Settings capture measures the field's leading action at 12px from
                // the pill edge (magnifier ink x rel 22 with the pill at rel 8), so the inner inset
                // is Spacing.Three, not Spacing.Two.
                .padding(horizontal = HarnessTokens.Spacing.Three),
        horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HarnessIcon(
            name = HarnessIconName.Search,
            tint = if (enabled) colors.TextMuted else colors.TextDisabled,
            size = HarnessTokens.Control.NavigatorIcon,
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier =
                Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .semantics {
                        contentDescription = accessibilityLabel
                        this.testTag = testTag
                    },
            enabled = enabled,
            singleLine = true,
            maxLines = 1,
            textStyle =
                MaterialTheme.typography.bodyMedium.merge(
                    TextStyle(color = if (enabled) colors.Text else colors.TextDisabled),
                ),
            cursorBrush = SolidColor(colors.Accent),
            decorationBox = { innerTextField ->
                Box(contentAlignment = CenterStart) {
                    if (value.isEmpty()) {
                        HarnessText(
                            text = placeholder,
                            color = colors.TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                        )
                    }
                    innerTextField()
                }
            },
        )
        if (value.isNotEmpty()) {
            HarnessIconButton(
                contentDescription = clearLabel,
                onClick = {
                    onValueChange("")
                    if (enabled) focusRequester.requestFocus()
                },
                state = HarnessButtonState(enabled = enabled),
                size = HarnessControlSize.Compact,
                testTag = "$testTag:clear",
            ) {
                HarnessIcon(
                    name = HarnessIconName.Close,
                    tint = if (enabled) colors.TextMuted else colors.TextDisabled,
                    size = HarnessTokens.Control.NavigatorIcon,
                )
            }
        }
    }
}
