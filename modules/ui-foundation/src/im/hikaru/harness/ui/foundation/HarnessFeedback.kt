package im.hikaru.harness.ui.foundation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag

@Composable
public fun HarnessLoadingState(message: String = "Loading…", modifier: Modifier = Modifier) {
    Row(
        modifier.padding(HarnessTokens.Spacing.Three).semantics {
            testTag = "loading-state"
            liveRegion = LiveRegionMode.Polite
            progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
        },
        horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HarnessProgress()
        HarnessText(message, color = HarnessTokens.Colors.TextMuted)
    }
}

@Composable
public fun HarnessEmptyState(title: String, modifier: Modifier = Modifier, description: String? = null) {
    Column(modifier.padding(HarnessTokens.Spacing.Three).semantics { testTag = "empty-state"; liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.One)) {
        HarnessText(title, style = MaterialTheme.typography.titleMedium)
        description?.let { HarnessText(it, color = HarnessTokens.Colors.TextMuted) }
    }
}

@Composable
public fun HarnessErrorState(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    retryLabel: String = "Retry",
    retryEnabled: Boolean = true,
) {
    Column(
        modifier.padding(HarnessTokens.Spacing.Three).semantics { testTag = "error-state"; error(message); liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two),
    ) {
        HarnessText(message, color = HarnessTokens.Colors.Danger)
        if (onRetry != null) HarnessButton(retryLabel, onRetry, enabled = retryEnabled, variant = HarnessButtonVariant.Secondary, size = HarnessControlSize.Compact)
    }
}

/** Exactly one branch is composed; loading/error/empty never mount ready content. */
@Composable
public fun HarnessStateContent(
    state: HarnessContentState,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    when (state) {
        HarnessContentState.Ready -> Column(modifier) { content() }
        is HarnessContentState.Loading -> HarnessLoadingState(state.message, modifier)
        is HarnessContentState.Empty -> HarnessEmptyState(state.title, modifier, state.description)
        is HarnessContentState.Error -> HarnessErrorState(state.message, modifier, onRetry, state.retryLabel)
    }
}
