@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package im.hikaru.harness.ui.foundation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlin.test.Test
import kotlin.test.assertEquals

class HarnessToastTest {
    @Test
    fun hostKeepsTheNewestThreeAndRunsTheActionOnce() {
        val scope = CoroutineScope(Dispatchers.Default)
        try {
            val toasts = HarnessToastState(scope)
            var retries = 0
            runSkikoComposeUiTest(size = Size(800f, 400f)) {
                setContent {
                    HarnessTheme(HarnessThemeMode.Light) {
                        Box(Modifier.fillMaxSize()) {
                            HarnessToastHost(toasts, Modifier.align(Alignment.TopCenter))
                        }
                    }
                }
                runOnIdle {
                    toasts.show("第一条")
                    toasts.show("第二条", isError = true)
                    toasts.show("第三条")
                    toasts.show("第四条", action = HarnessToastAction("重试") { retries++ })
                }
                // Newest first, at most three: the oldest message left the host.
                onNodeWithText("第一条").assertDoesNotExist()
                onNodeWithText("第二条").assertExists()
                onNodeWithText("第四条").assertExists()
                onNodeWithTag("harness-toast-error").assertExists()
                onNodeWithText("重试").performClick()
                assertEquals(1, retries)
                onNodeWithText("第四条").assertDoesNotExist()
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun errorAndInfoToastCarryDistinctSemanticsTags() {
        val scope = CoroutineScope(Dispatchers.Default)
        try {
            val toasts = HarnessToastState(scope)
            runSkikoComposeUiTest(size = Size(800f, 400f)) {
                setContent {
                    HarnessTheme(HarnessThemeMode.Light) {
                        Box(Modifier.fillMaxSize()) {
                            HarnessToastHost(toasts, Modifier.align(Alignment.TopCenter))
                        }
                    }
                }
                runOnIdle { toasts.show("已复制", isError = false) }
                onNodeWithTag("harness-toast-info").assertExists()
                onNodeWithTag("harness-toast-error").assertDoesNotExist()
                runOnIdle { toasts.show("发送未确认。", isError = true) }
                onNodeWithTag("harness-toast-error").assertExists()
            }
        } finally {
            scope.cancel()
        }
    }
}
