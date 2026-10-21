package im.hikaru.harness.desktop

import androidx.compose.runtime.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import im.hikaru.harness.client.app.*
import im.hikaru.harness.client.conversation.ConversationState
import im.hikaru.harness.ui.foundation.HarnessTheme
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
class ModelPickerStackInteractionTest {
    @Test
    fun pickerAlwaysReturnsToReasoningIncludingModelsWithoutEfforts() = runComposeUiTest {
        val first = ConversationModelChoice("p", "a", "First", listOf("low", "high"))
        val second = ConversationModelChoice("p", "b", "Second")
        setContent {
            var selected by remember { mutableStateOf(first) }
            HarnessTheme {
                LiveConversationComposer(ConversationState(), {}, {}, {}, models = listOf(first, second),
                    selectedModel = selected, onModelSelect = { selected = it },
                    modelUnavailableReason = null, submitUnavailableReason = null,
                    permission = ComposerPermission.Ask, onPermissionSelect = {}, onReasoningEffortSelect = {})
            }
        }
        onNodeWithTag("live-composer-model").performClick()
        onNodeWithTag("live-composer-effort-slider").assertExists()
        onNodeWithTag("live-composer-model-select").performClick()
        onNodeWithTag("menu:conversation-model:1:p:1:b").performClick()
        onNodeWithTag("live-composer-model-select").assertExists()
        onNodeWithTag("live-composer-model-search").assertDoesNotExist()
        onNodeWithText("此模型未提供可调整的思考强度，跟随厂商。").assertExists()
        onNodeWithTag("live-composer-model-select").performClick()
        onNodeWithTag("live-composer-model-back").assertDoesNotExist()
        onNodeWithTag("live-composer-model-search").assertDoesNotExist()
        onNodeWithTag("menu:conversation-model:1:p:1:b").performKeyInput { pressKey(Key.Escape) }
        onNodeWithTag("live-composer-model-select").performKeyInput { pressKey(Key.Escape) }
        onNodeWithTag("live-composer-model-popup").assertDoesNotExist()
        onNodeWithTag("live-composer-model").performClick()
        onNodeWithTag("live-composer-model-select").assertExists()
        onNodeWithTag("live-composer-model-search").assertDoesNotExist()
    }
}
