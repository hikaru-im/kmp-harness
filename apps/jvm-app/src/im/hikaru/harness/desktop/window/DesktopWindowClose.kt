package im.hikaru.harness.desktop.window

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import im.hikaru.harness.client.settings.CloseConfirmationPolicy
import im.hikaru.harness.client.settings.ShortcutsRepository
import im.hikaru.harness.ui.foundation.HarnessConfirmDialog
import im.hikaru.harness.ui.foundation.HarnessConfirmIntent
import im.hikaru.harness.ui.foundation.HarnessConfirmSubmitState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

public enum class WindowCloseOrigin { NativeControl, KeyboardShortcut }
public enum class WindowClosePhase { Open, AwaitingConfirmation, Closing }

/** All calls are made on the window's UI thread. Repeated events consume one close at most. */
public class DesktopWindowCloseController {
    private val mutablePhase = MutableStateFlow(WindowClosePhase.Open)
    public val phase: StateFlow<WindowClosePhase> = mutablePhase.asStateFlow()
    public val blocksShortcuts: Boolean get() = mutablePhase.value != WindowClosePhase.Open

    public fun request(policy: CloseConfirmationPolicy, origin: WindowCloseOrigin, onClose: () -> Unit) {
        if (mutablePhase.value != WindowClosePhase.Open) return
        val confirm = policy == CloseConfirmationPolicy.Always ||
            (policy == CloseConfirmationPolicy.KeyboardOnly && origin == WindowCloseOrigin.KeyboardShortcut)
        if (confirm) mutablePhase.value = WindowClosePhase.AwaitingConfirmation else close(onClose)
    }

    public fun cancel() {
        if (mutablePhase.value == WindowClosePhase.AwaitingConfirmation) mutablePhase.value = WindowClosePhase.Open
    }

    public fun confirm(onClose: () -> Unit) {
        if (mutablePhase.value == WindowClosePhase.AwaitingConfirmation) close(onClose)
    }

    /** A deferred close consumer retained the window because its resources have not drained. */
    public fun retainWindow() {
        if (mutablePhase.value == WindowClosePhase.Closing) mutablePhase.value = WindowClosePhase.Open
    }

    private fun close(onClose: () -> Unit) {
        mutablePhase.value = WindowClosePhase.Closing
        onClose()
    }
}

/** Production and desktop interaction tests attach the same sole keyboard-close consumer. */
@Composable
public fun DesktopWindowShortcutBinding(
    repository: ShortcutsRepository,
    controller: DesktopWindowCloseController,
    policy: CloseConfirmationPolicy,
    onClose: () -> Unit,
) {
    val currentPolicy by rememberUpdatedState(policy)
    val currentClose by rememberUpdatedState(onClose)
    val owner = remember(repository) { Any() }
    DisposableEffect(repository, controller) {
        repository.input.attachWindow(owner, { controller.blocksShortcuts }) {
            controller.request(currentPolicy, WindowCloseOrigin.KeyboardShortcut, currentClose)
        }
        onDispose { repository.input.detachWindow(owner) }
    }
}

/** Default-key predicate retained for General's existing tests; production uses the repository. */
public fun isWindowCloseShortcut(event: KeyEvent, isMac: Boolean): Boolean =
    event.type == KeyEventType.KeyDown && event.key == Key.W &&
        !event.isAltPressed && !event.isShiftPressed &&
        if (isMac) event.isMetaPressed && !event.isCtrlPressed else event.isCtrlPressed && !event.isMetaPressed

@Composable
public fun DesktopWindowCloseDialog(controller: DesktopWindowCloseController, onClose: () -> Unit) {
    val phase by controller.phase.collectAsState()
    HarnessConfirmDialog(
        visible = phase == WindowClosePhase.AwaitingConfirmation,
        onDismissRequest = controller::cancel,
        title = "关闭窗口？",
        description = "关闭窗口将结束本次 Harness 运行。",
        cancelLabel = "取消",
        confirmLabel = "关闭窗口",
        submitState = HarnessConfirmSubmitState.Ready,
        onCancel = controller::cancel,
        onConfirm = { controller.confirm(onClose) },
        intent = HarnessConfirmIntent.Destructive,
        testTag = "window-close-dialog",
    )
}
