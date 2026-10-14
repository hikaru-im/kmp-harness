package im.hikaru.harness.ui.foundation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.LayoutDirection

/** Controlled tabs with roving focus, RTL arrow navigation, Home/End and optional close buttons. */
@Composable
public fun HarnessTabs(
    state: HarnessTabsState,
    onStateChange: (HarnessTabsState) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Tabs",
    closeLabel: (HarnessTab) -> String = { "Close ${it.title}" },
) {
    val direction = LocalLayoutDirection.current
    var requestedFocus by remember { mutableStateOf<String?>(null) }
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()).selectableGroup()
            .semantics { testTag = label; contentDescription = label }
            .onPreviewKeyEvent { event ->
                val navigation = when (event.key) {
                    Key.DirectionRight -> if (direction == LayoutDirection.Ltr) HarnessNavigation.Next else HarnessNavigation.Previous
                    Key.DirectionLeft -> if (direction == LayoutDirection.Ltr) HarnessNavigation.Previous else HarnessNavigation.Next
                    Key.MoveHome -> HarnessNavigation.First
                    Key.MoveEnd -> HarnessNavigation.Last
                    else -> null
                }
                if (navigation == null) false
                else {
                    if (event.type == KeyEventType.KeyDown) {
                        val next = state.navigate(navigation)
                        requestedFocus = next.effectiveSelectedKey
                        if (next != state) onStateChange(next)
                    }
                    true
                }
            },
        horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.One),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        state.tabs.forEach { tab -> key(tab.key) {
            val selected = tab.key == state.effectiveSelectedKey
            val focus = remember(tab.key) { FocusRequester() }
            LaunchedEffect(requestedFocus, selected) {
                if (requestedFocus == tab.key && selected) {
                    focus.requestFocus()
                    requestedFocus = null
                }
            }
            Row(
                modifier = Modifier.clip(RoundedCornerShape(HarnessTokens.Corners.Medium))
                    .background(if (selected) HarnessTokens.Colors.SurfaceSelected else Color.Transparent),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HarnessText(
                    tab.title,
                    modifier = Modifier.heightIn(min = HarnessTokens.Control.Height)
                        .focusRequester(focus).focusProperties { canFocus = selected && tab.enabled }
                        .selectable(selected, enabled = tab.enabled, role = Role.Tab) {
                            val next = state.select(tab.key)
                            requestedFocus = tab.key
                            if (next != state) onStateChange(next)
                        }
                        .semantics { testTag = "tab:${tab.key}" }
                        .padding(horizontal = HarnessTokens.Spacing.Three, vertical = HarnessTokens.Spacing.Two),
                    color = if (tab.enabled) HarnessTokens.Colors.Text else HarnessTokens.Colors.TextDisabled,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                )
                if (tab.closable) {
                    HarnessIconButton(
                        contentDescription = closeLabel(tab), onClick = {
                            val next = state.close(tab.key)
                            requestedFocus = next.effectiveSelectedKey
                            if (next != state) onStateChange(next)
                        },
                        enabled = tab.enabled,
                    ) { HarnessText("×", color = if (tab.enabled) HarnessTokens.Colors.TextMuted else HarnessTokens.Colors.TextDisabled) }
                }
            }
        } }
    }
}

/**
 * Page-neutral row. Disclosure, primary activation and trailing actions are independent controls.
 * Loading blocks row activation/disclosure; the owner controls trailing-action availability.
 */
@Composable
public fun HarnessRow(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    state: HarnessRowState = HarnessRowState(),
    onClick: (() -> Unit)? = null,
    onExpandedChange: ((Boolean) -> Unit)? = null,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    onRetry: (() -> Unit)? = null,
    retryLabel: String = "Retry",
    testTag: String = title,
    /**
     * Caps the title to this many lines with an ellipsis. Null wraps, which is what every existing
     * caller already relies on; the Projects/Recent lists pass 1 so a long name keeps the row on the
     * single-line band the reference draws.
     */
    titleMaxLines: Int? = null,
    /**
     * Caps the description to this many lines with an ellipsis. Null wraps, which is the behaviour
     * every existing caller already relies on; the Projects/Recent lists pass 1 so a long directory
     * path cannot grow the row past the single-line band the reference draws.
     */
    descriptionMaxLines: Int? = null,
    children: @Composable ColumnScope.() -> Unit = {},
) {
    Column(modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = HarnessTokens.Control.Row)
                .padding(start = HarnessTokens.Spacing.Four * state.depth)
                .clip(RoundedCornerShape(HarnessTokens.Corners.Medium))
                .background(if (state.selected) HarnessTokens.Colors.SurfaceSelected else Color.Transparent)
                .then(if (onClick != null) Modifier.clickable(enabled = state.interactive, role = Role.Button) { if (state.interactive) onClick() } else Modifier)
                .semantics {
                    this.testTag = testTag
                    selected = state.selected
                    state.expanded?.let { stateDescription = if (it) "Expanded" else "Collapsed" }
                    if (state.control == HarnessControlState.Error) error(description ?: "Unable to load row")
                }
                .padding(horizontal = HarnessTokens.Spacing.Two, vertical = HarnessTokens.Spacing.One),
            horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.expanded != null) {
                HarnessIconButton(
                    contentDescription = if (state.expanded) "Collapse $title" else "Expand $title",
                    state = HarnessButtonState(enabled = state.enabled && onExpandedChange != null, control = state.control),
                    onClick = { state.toggleExpanded().expanded?.let { onExpandedChange?.invoke(it) } },
                ) { HarnessText(if (state.expanded) "⌄" else "›") }
            }
            leading?.invoke()
            Column(Modifier.weight(1f)) {
                HarnessText(title, color = if (state.enabled) HarnessTokens.Colors.Text else HarnessTokens.Colors.TextDisabled,
                    maxLines = titleMaxLines ?: Int.MAX_VALUE)
                description?.let {
                    HarnessText(it, color = if (state.control == HarnessControlState.Error) HarnessTokens.Colors.Danger else HarnessTokens.Colors.TextMuted,
                        style = MaterialTheme.typography.bodySmall, maxLines = descriptionMaxLines ?: Int.MAX_VALUE)
                }
            }
            if (state.control == HarnessControlState.Loading) HarnessProgress()
            if (state.control == HarnessControlState.Error && onRetry != null) {
                HarnessButton(retryLabel, onRetry, enabled = state.enabled, variant = HarnessButtonVariant.Secondary, size = HarnessControlSize.Compact)
            }
            trailing?.invoke()
        }
        if (state.expanded == true) Column(Modifier.padding(start = HarnessTokens.Spacing.Four * (state.depth + 1)), content = children)
    }
}

@Composable
public fun HarnessSection(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    headerAction: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two)) {
            Column(Modifier.weight(1f)) {
                HarnessText(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleMedium)
                description?.let { HarnessText(it, color = HarnessTokens.Colors.TextMuted, style = MaterialTheme.typography.bodySmall) }
            }
            headerAction?.invoke()
        }
        content()
    }
}
