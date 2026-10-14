package im.hikaru.harness.ui.foundation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** The owner reports asynchronous submission progress and failures to the dialog. */
public sealed interface HarnessConfirmSubmitState {
    public data object Ready : HarnessConfirmSubmitState
    public data object Submitting : HarnessConfirmSubmitState
    public data class Failed(public val message: String) : HarnessConfirmSubmitState
}

/** Whether confirmation is a routine action or an irreversible/destructive one. */
public enum class HarnessConfirmIntent { Standard, Destructive }

/**
 * Confirmation dialog with a caller-owned submit lifecycle. Failed submissions remain open and
 * actionable; the owner must set [HarnessConfirmSubmitState.Submitting] while work is in flight.
 */
@Composable
public fun HarnessConfirmDialog(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    description: String,
    cancelLabel: String,
    confirmLabel: String,
    submitState: HarnessConfirmSubmitState,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    intent: HarnessConfirmIntent = HarnessConfirmIntent.Standard,
    testTag: String = "confirm-dialog",
) {
    if (!visible) return

    val submitting = submitState is HarnessConfirmSubmitState.Submitting
    val dismiss = { if (!submitting) onDismissRequest() }
    val windowHeight = LocalWindowInfo.current.containerSize.height
    val density = LocalDensity.current
    val verticalMargin = with(density) { HarnessTokens.Spacing.Four.roundToPx() * 2 }
    val maxHeight = with(density) { (windowHeight - verticalMargin).coerceAtLeast(0).toDp() }
    val cancelFocus = remember { FocusRequester() }
    val confirmFocus = remember { FocusRequester() }
    val initialFocus = if (intent == HarnessConfirmIntent.Destructive) cancelFocus else confirmFocus

    Dialog(
        onDismissRequest = dismiss,
        properties = DialogProperties(
            dismissOnBackPress = !submitting,
            dismissOnClickOutside = !submitting,
            usePlatformDefaultWidth = false,
        ),
    ) {
        LaunchedEffect(intent) { initialFocus.requestFocus() }
        HarnessSurface(
            modifier = modifier
                .widthIn(max = HarnessTokens.Control.ContentMaxWidth)
                .heightIn(max = maxHeight)
                .semantics { this.testTag = testTag }
                .onPreviewKeyEvent { event ->
                    if (event.key == Key.Escape && event.type == KeyEventType.KeyDown) {
                        dismiss()
                        true
                    } else false
                },
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(HarnessTokens.Spacing.Four),
                verticalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Three),
            ) {
                HarnessText(title, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                Column(
                    modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                ) {
                    HarnessText(description, color = HarnessTokens.Colors.TextMuted)
                    (submitState as? HarnessConfirmSubmitState.Failed)?.let { failure ->
                        HarnessText(
                            failure.message,
                            modifier = Modifier.padding(top = HarnessTokens.Spacing.Two).semantics { this.testTag = "$testTag:error" },
                            color = HarnessTokens.Colors.Danger,
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(HarnessTokens.Spacing.Two, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HarnessButton(
                        text = cancelLabel,
                        onClick = { if (!submitting) onCancel() },
                        modifier = Modifier.focusRequester(cancelFocus),
                        enabled = !submitting,
                        variant = HarnessButtonVariant.Secondary,
                        testTag = "$testTag:cancel",
                    )
                    HarnessButton(
                        text = confirmLabel,
                        onClick = { if (!submitting) onConfirm() },
                        modifier = Modifier.focusRequester(confirmFocus),
                        enabled = !submitting,
                        state = when (submitState) {
                            HarnessConfirmSubmitState.Ready -> HarnessControlState.Normal
                            HarnessConfirmSubmitState.Submitting -> HarnessControlState.Loading
                            is HarnessConfirmSubmitState.Failed -> HarnessControlState.Error
                        },
                        variant = if (intent == HarnessConfirmIntent.Destructive) HarnessButtonVariant.Destructive else HarnessButtonVariant.Primary,
                        testTag = "$testTag:confirm",
                    )
                }
            }
        }
    }
}

/** A pending destination is consumed exactly once, only when the caller chooses to discard changes. */
public data class HarnessUnsavedNavigationState<Destination>(
    public val pendingDestination: Destination? = null,
) {
    public fun request(destination: Destination): HarnessUnsavedNavigationState<Destination> =
        copy(pendingDestination = destination)

    public fun keepEditing(): HarnessUnsavedNavigationState<Destination> =
        copy(pendingDestination = null)

    public fun discard(): HarnessDiscardNavigation<Destination> =
        pendingDestination?.let { HarnessDiscardNavigation(state = keepEditing(), destination = it) }
            ?: HarnessDiscardNavigation(state = this, destination = null)
}

/** Result of resolving a pending leave request; a null destination means there was nothing to consume. */
public data class HarnessDiscardNavigation<Destination>(
    public val state: HarnessUnsavedNavigationState<Destination>,
    public val destination: Destination?,
)
