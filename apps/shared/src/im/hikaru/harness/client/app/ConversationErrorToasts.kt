package im.hikaru.harness.client.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import im.hikaru.harness.client.conversation.ConversationError
import im.hikaru.harness.client.conversation.ConversationErrorKind
import im.hikaru.harness.client.conversation.ConversationRetry
import im.hikaru.harness.ui.foundation.HarnessToastAction
import im.hikaru.harness.ui.foundation.HarnessToastState

/**
 * Conversation outcomes are transient notifications, not page content: a newly raised error becomes
 * one toast and then leaves the screen. Nothing stays pinned underneath the composer, so an error the
 * user already read cannot keep looking like a live warning. The same error kept across
 * recompositions is notified once; an error that was cleared and then raised again notifies again.
 *
 * The retry that used to sit under an inline error block rides on the toast: a failed read keeps its
 * retry, while execution retries stay with Send/Stop and are never performed from a notification.
 */
@Composable
internal fun ConversationErrorToasts(
    errors: Map<ConversationErrorKind, ConversationError>,
    toasts: HarnessToastState,
    onRetryHistory: () -> Unit,
) {
    val notified = remember { mutableMapOf<ConversationErrorKind, String>() }
    LaunchedEffect(errors) {
        notified.keys.retainAll(errors.keys)
        errors.forEach { (kind, error) ->
            if (notified[kind] == error.message) return@forEach
            notified[kind] = error.message
            toasts.show(
                text = error.message,
                isError = true,
                action =
                    when (error.retry) {
                        ConversationRetry.History, ConversationRetry.Refresh ->
                            HarnessToastAction("重试", onRetryHistory)
                        else -> null
                    },
            )
        }
    }
}
