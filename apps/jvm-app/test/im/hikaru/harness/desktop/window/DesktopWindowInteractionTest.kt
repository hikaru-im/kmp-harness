package im.hikaru.harness.desktop.window

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import im.hikaru.harness.client.settings.CloseConfirmationPolicy
import im.hikaru.harness.ui.foundation.HarnessButton
import kotlin.test.*

@OptIn(ExperimentalTestApi::class)
class DesktopWindowInteractionTest {
    @Test
    fun previewKeyHandlerConsumesCloseKeyDownAndLetsOtherEventsReachFocusedContent() = runComposeUiTest {
        var isMac by mutableStateOf(false)
        val previewEvents = mutableListOf<KeyEvent>()
        val childEvents = mutableListOf<KeyEvent>()
        setContent {
            DesktopWindowContent(onPreviewKeyEvent = { event ->
                previewEvents += event
                isWindowCloseShortcut(event, isMac)
            }) {
                val focus = remember { FocusRequester() }
                Box(Modifier.size(100.dp).focusRequester(focus)
                    .onKeyEvent { childEvents += it; false }
                    .focusable().semantics { testTag = "window-key-target" })
                LaunchedEffect(Unit) { focus.requestFocus() }
            }
        }
        listOf(false, true).forEach { mac ->
            runOnIdle { isMac = mac; previewEvents.clear(); childEvents.clear() }
            onNodeWithTag("window-key-target").assertIsFocused().performKeyInput {
                pressKey(Key.W)
                val modifier = if (mac) Key.MetaLeft else Key.CtrlLeft
                keyDown(modifier)
                pressKey(Key.Q)
                pressKey(Key.W)
                keyUp(modifier)
            }
            runOnIdle {
                assertEquals(1, previewEvents.count { isWindowCloseShortcut(it, mac) })
                assertEquals(0, childEvents.count { isWindowCloseShortcut(it, mac) })
                assertEquals(2, previewEvents.count { it.key == Key.W && it.type == KeyEventType.KeyDown })
                assertEquals(1, childEvents.count { it.key == Key.W && it.type == KeyEventType.KeyDown })
                assertEquals(2, childEvents.count { it.key == Key.W && it.type == KeyEventType.KeyUp })
                assertEquals(1, childEvents.count { it.key == Key.Q && it.type == KeyEventType.KeyDown })
            }
        }
    }

    @Test
    fun everyPolicyAndOriginShowsOnlyRequiredDialogAndMouseCancelOrConfirmConsumesCloseOnce() = runComposeUiTest {
        var controller by mutableStateOf(DesktopWindowCloseController())
        var policy by mutableStateOf(CloseConfirmationPolicy.Never)
        var isMac by mutableStateOf(false)
        var closes = 0
        val close = { closes++; Unit }
        setContent {
            // Exercise the real content modifier and dialog without creating a native application window.
            DesktopWindowContent(onPreviewKeyEvent = { event ->
                if (isWindowCloseShortcut(event, isMac)) {
                    controller.request(policy, WindowCloseOrigin.KeyboardShortcut, close)
                    true
                } else false
            }) {
                Column {
                    HarnessButton("Native close request", {
                        controller.request(policy, WindowCloseOrigin.NativeControl, close)
                    }, testTag = "native-close-request")
                    val focus = remember { FocusRequester() }
                    Box(Modifier.size(100.dp).focusRequester(focus).focusable()
                        .semantics { testTag = "window-key-target" })
                    LaunchedEffect(controller) { focus.requestFocus() }
                }
                DesktopWindowCloseDialog(controller, close)
            }
        }
        listOf(false, true).forEach { mac ->
            CloseConfirmationPolicy.entries.forEach { candidate ->
                WindowCloseOrigin.entries.forEach { origin ->
                    runOnIdle {
                        controller = DesktopWindowCloseController()
                        policy = candidate
                        isMac = mac
                        closes = 0
                    }
                    fun requestClose() {
                        if (origin == WindowCloseOrigin.NativeControl) {
                            onNodeWithTag("native-close-request").clickWithMouse()
                        } else {
                            onNodeWithTag("window-key-target").assertIsFocused().performKeyInput {
                                val modifier = if (mac) Key.MetaLeft else Key.CtrlLeft
                                keyDown(modifier)
                                pressKey(Key.W)
                                keyUp(modifier)
                            }
                        }
                    }
                    val shouldConfirm = candidate == CloseConfirmationPolicy.Always ||
                        (candidate == CloseConfirmationPolicy.KeyboardOnly && origin == WindowCloseOrigin.KeyboardShortcut)
                    requestClose()
                    if (shouldConfirm) {
                        onNodeWithTag("window-close-dialog").assertIsDisplayed()
                        onNodeWithTag("window-close-dialog:cancel").assertIsFocused()
                        runOnIdle {
                            assertEquals(0, closes)
                            assertEquals(WindowClosePhase.AwaitingConfirmation, controller.phase.value)
                            controller.request(candidate, origin, close)
                        }
                        onNodeWithTag("window-close-dialog:cancel").clickWithMouse()
                        onNodeWithTag("window-close-dialog").assertDoesNotExist()
                        runOnIdle {
                            assertEquals(0, closes)
                            assertEquals(WindowClosePhase.Open, controller.phase.value)
                        }
                        requestClose()
                        onNodeWithTag("window-close-dialog:confirm").clickWithMouse()
                    }
                    onNodeWithTag("window-close-dialog").assertDoesNotExist()
                    runOnIdle {
                        controller.confirm(close)
                        controller.cancel()
                        controller.request(candidate, origin, close)
                        assertEquals(1, closes, "$candidate / $origin / isMac=$mac")
                        assertEquals(WindowClosePhase.Closing, controller.phase.value)
                    }
                }
            }
        }
    }

    @Test
    fun escapeAndEnterOnInitialCancelFocusReopenWithoutClosing() = runComposeUiTest {
        val controller = DesktopWindowCloseController()
        var closes = 0
        val close = { closes++; Unit }
        setContent {
            DesktopWindowContent {
                HarnessButton("Close", {
                    controller.request(CloseConfirmationPolicy.Always, WindowCloseOrigin.NativeControl, close)
                }, testTag = "native-close-request")
                DesktopWindowCloseDialog(controller, close)
            }
        }
        listOf(Key.Escape, Key.Enter).forEach { key ->
            onNodeWithTag("native-close-request").clickWithMouse()
            onNodeWithTag("window-close-dialog:cancel").assertIsFocused().performKeyInput { pressKey(key) }
            onNodeWithTag("window-close-dialog").assertDoesNotExist()
            runOnIdle {
                assertEquals(0, closes)
                assertEquals(WindowClosePhase.Open, controller.phase.value)
            }
        }
    }

    private fun SemanticsNodeInteraction.clickWithMouse() {
        assertIsDisplayed().assertIsEnabled().performMouseInput { click() }
    }
}
