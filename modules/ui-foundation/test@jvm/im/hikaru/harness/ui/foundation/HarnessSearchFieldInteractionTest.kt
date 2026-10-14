package im.hikaru.harness.ui.foundation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class HarnessSearchFieldInteractionTest {
    @Test
    fun inputReportsChangesAndClearEmptiesValueWithoutLosingFocus() = runComposeUiTest {
        var value by mutableStateOf("")
        val changes = mutableListOf<String>()
        setContent {
            HarnessTheme {
                HarnessSearchField(
                    value = value,
                    onValueChange = { changes += it; value = it },
                    placeholder = "Search settings",
                    accessibilityLabel = "Settings search",
                    modifier = Modifier.width(180.dp),
                    testTag = "settings-search",
                )
            }
        }

        onNodeWithContentDescription("Settings search").assertIsDisplayed()
        onNodeWithTag("settings-search").performClick()
        val longQuery = "needle".repeat(20)
        onNodeWithTag("settings-search").performTextInput(longQuery)
        onNodeWithTag("settings-search").assertTextEquals(longQuery)
        runOnIdle { assertEquals(longQuery, changes.last()) }
        onNodeWithContentDescription("清除搜索")
            .assertIsDisplayed()
            .assertIsEnabled()
            .performClick()

        onNodeWithTag("settings-search")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
            .assertIsFocused()
        runOnIdle {
            assertEquals("", value)
            assertEquals("", changes.last())
        }
    }

    @Test
    fun disabledFieldAndClearActionDoNotChangeValue() = runComposeUiTest {
        var value by mutableStateOf("locked")
        var changes = 0
        setContent {
            HarnessTheme {
                HarnessSearchField(
                    value = value,
                    onValueChange = { changes++; value = it },
                    placeholder = "Search settings",
                    accessibilityLabel = "Settings search",
                    enabled = false,
                    testTag = "disabled-search",
                )
            }
        }

        onNodeWithTag("disabled-search").assertIsNotEnabled()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.SetText))
            .performClick()
            .performKeyInput { keyDown(Key.A); keyUp(Key.A) }
        onNodeWithTag("disabled-search:clear").assertIsNotEnabled().performClick()
        onNodeWithTag("disabled-search").assertTextEquals("locked")
        runOnIdle {
            assertEquals("locked", value)
            assertEquals(0, changes)
        }
    }
}
