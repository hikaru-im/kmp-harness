package im.hikaru.harness.desktop.window

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import im.hikaru.harness.client.settings.CloseConfirmationPolicy
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class DesktopWindowCloseTest {
    @Test
    fun closeShortcutRequiresPlatformModifierAndKeyDownWithoutExtraModifiers() = runComposeUiTest {
        val events = mutableListOf<KeyEvent>()
        setContent {
            DesktopWindowContent(onPreviewKeyEvent = { events += it; false }) {
                val focus = remember { FocusRequester() }
                Box(Modifier.size(100.dp).focusRequester(focus).focusable()
                    .semantics { testTag = "shortcut-target" })
                LaunchedEffect(Unit) { focus.requestFocus() }
            }
        }
        (0..15).forEach { modifiers ->
            runOnIdle { events.clear() }
            onNodeWithTag("shortcut-target").assertIsFocused().performKeyInput {
                val keys = listOf(Key.CtrlLeft, Key.MetaLeft, Key.AltLeft, Key.ShiftLeft)
                    .filterIndexed { index, _ -> modifiers and (1 shl index) != 0 }
                keys.forEach { keyDown(it) }
                listOf(Key.W, Key.Q, Key.F4).forEach { pressKey(it) }
                keys.asReversed().forEach { keyUp(it) }
            }
            runOnIdle {
                assertEquals(6, events.count { it.key in listOf(Key.W, Key.Q, Key.F4) })
                listOf(false, true).forEach { isMac ->
                    val platformModifier = if (isMac) 2 else 1
                    events.forEach { event ->
                        assertEquals(
                            event.key == Key.W && event.type == KeyEventType.KeyDown && modifiers == platformModifier,
                            isWindowCloseShortcut(event, isMac),
                            "isMac=" + isMac + ", modifiers=" + modifiers + ", key=" + event.key + ", type=" + event.type,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun everyPolicyHandlesNativeAndKeyboardCloseAndConsumesCloseOnce() {
        CloseConfirmationPolicy.entries.forEach { policy ->
            WindowCloseOrigin.entries.forEach { origin ->
                val controller = DesktopWindowCloseController()
                var closes = 0
                val close = { closes++; Unit }
                val shouldConfirm = policy == CloseConfirmationPolicy.Always ||
                    (policy == CloseConfirmationPolicy.KeyboardOnly && origin == WindowCloseOrigin.KeyboardShortcut)
                controller.request(policy, origin, close)
                assertEquals(if (shouldConfirm) WindowClosePhase.AwaitingConfirmation else WindowClosePhase.Closing,
                    controller.phase.value)
                assertEquals(if (shouldConfirm) 0 else 1, closes)
                controller.request(policy, origin, close)
                controller.confirm(close)
                controller.confirm(close)
                controller.cancel()
                controller.request(policy, origin, close)
                assertEquals(1, closes)
                assertEquals(WindowClosePhase.Closing, controller.phase.value)
            }
        }
    }

    @Test
    fun cancellationReopensAndNextRequestUsesCurrentPolicy() {
        val controller = DesktopWindowCloseController()
        var closes = 0
        controller.confirm { closes++ }
        controller.cancel()
        assertEquals(WindowClosePhase.Open, controller.phase.value)
        controller.request(CloseConfirmationPolicy.Always, WindowCloseOrigin.NativeControl) { closes++ }
        controller.cancel()
        assertEquals(0, closes)
        assertEquals(WindowClosePhase.Open, controller.phase.value)
        controller.request(CloseConfirmationPolicy.Never, WindowCloseOrigin.KeyboardShortcut) { closes++ }
        assertEquals(1, closes)
    }

    @Test
    fun retainedDeferredCloseAllowsAnotherNativeOrKeyboardRequest() {
        WindowCloseOrigin.entries.forEach { origin ->
            val controller = DesktopWindowCloseController()
            var requests = 0
            val close = { requests++; Unit }
            controller.request(CloseConfirmationPolicy.Never, origin, close)
            controller.request(CloseConfirmationPolicy.Never, origin, close)
            assertEquals(1, requests)
            assertTrue(controller.blocksShortcuts)
            controller.retainWindow()
            assertFalse(controller.blocksShortcuts)
            controller.request(CloseConfirmationPolicy.Always, origin, close)
            controller.retainWindow()
            assertEquals(WindowClosePhase.AwaitingConfirmation, controller.phase.value)
            controller.confirm(close)
            assertEquals(2, requests)
            assertEquals(WindowClosePhase.Closing, controller.phase.value)
        }
    }
}
