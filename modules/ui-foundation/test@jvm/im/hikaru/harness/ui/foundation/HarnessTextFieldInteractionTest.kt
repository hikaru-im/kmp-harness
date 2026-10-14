package im.hikaru.harness.ui.foundation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class HarnessTextFieldInteractionTest {
    /**
     * Compare painted ink against independent Text references, including ascenders/descenders.
     * EditableText semantics alone passed the old 36dp OutlinedTextField with invisible contents.
     * The editable semantics capture excludes the padded border; retain the complete glyph area.
     */
    @Test
    fun compactPathLabelAndSupportPaintWithoutClipping() {
        listOf(HarnessThemeMode.Light, HarnessThemeMode.Dark).forEach { theme ->
            listOf(280, 480).forEach { width ->
                listOf(1f, 1.5f).forEach { fontScale ->
                    Mode.entries.forEach { mode ->
                        runSkikoComposeUiTest(size = Size(600f, 800f)) {
                            var value by mutableStateOf(path)
                            var ink = Color.Unspecified
                            var labelInk = Color.Unspecified
                            var supportInk = Color.Unspecified
                            var background = Color.Unspecified
                            setContent {
                                CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                                    HarnessTheme(theme) {
                                        val colors = HarnessTokens.Colors
                                        ink = if (mode == Mode.Disabled) colors.TextDisabled else colors.Text
                                        labelInk = when (mode) {
                                            Mode.Disabled -> colors.TextDisabled
                                            Mode.Error -> colors.Danger
                                            else -> colors.TextMuted
                                        }
                                        supportInk = if (mode == Mode.Error) colors.Danger else colors.TextMuted
                                        background = colors.SurfaceElevated
                                        Column(Modifier.fillMaxSize().background(background).padding(16.dp),
                                            verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                            HarnessTextField(
                                                value, { value = it }, label, Modifier.width(width.dp),
                                                enabled = mode != Mode.Disabled, readOnly = mode == Mode.ReadOnly,
                                                isError = mode == Mode.Error, supportingText = support, testTag = fieldTag,
                                            )
                                            HarnessText(path, tagged("value-reference"), color = ink)
                                            HarnessText(label, tagged("label-reference"), color = labelInk,
                                                style = MaterialTheme.typography.labelLarge)
                                            HarnessText(support, tagged("support-reference"), color = supportInk)
                                        }
                                    }
                                }
                            }
                            val context = "$theme width=$width fontScale=$fontScale mode=$mode"
                            val editor = onNodeWithTag(fieldTag).assertIsDisplayed()
                            assertPaintedLikeReference(editor.captureToImage(),
                                onNodeWithTag("value-reference").captureToImage(), ink, background, context + " value")
                            assertPaintedLikeReference(onNode(hasText(label) and !hasTestTag("label-reference")).captureToImage(),
                                onNodeWithTag("label-reference").captureToImage(), labelInk, background, context + " label")
                            assertPaintedLikeReference(onNode(hasText(support) and !hasTestTag("support-reference")).captureToImage(),
                                onNodeWithTag("support-reference").captureToImage(), supportInk, background, context + " support")
                            val editorBounds = editor.getUnclippedBoundsInRoot()
                            val labelBounds = onNode(hasText(label) and !hasTestTag("label-reference")).getUnclippedBoundsInRoot()
                            val supportBounds = onNode(hasText(support) and !hasTestTag("support-reference")).getUnclippedBoundsInRoot()
                            // BasicTextField's editable semantics report the inner text layout, not its padded border.
                            // The two external labels delimit the complete editor plus two 4dp gaps.
                            val decoratedHeight = supportBounds.top - labelBounds.bottom - 8.dp
                            assertTrue(decoratedHeight >= 36.dp, context + " decorated editor too short")
                            if (fontScale == 1f) assertEquals(36.dp, decoratedHeight, context)
                            assertTrue(labelBounds.bottom <= editorBounds.top, context + " label overlaps editor")
                            assertTrue(supportBounds.top >= editorBounds.bottom, context + " support overlaps editor")
                            // Unfocused blank inputs must paint no fake value/placeholder ink.
                            runOnIdle { value = "" }
                            assertEquals(0, inkRows(editor.captureToImage(), ink, background, 0).sum(), context + " empty")
                        }
                    }
                }
            }
        }
    }

    @Test
    fun typingSelectionReadOnlyDisabledAndErrorContractsRemainAvailable() =
        runSkikoComposeUiTest(size = Size(600f, 800f)) {
            var value by mutableStateOf("")
            var readOnly by mutableStateOf(false)
            var enabled by mutableStateOf(true)
            var changes = 0
            setContent {
                HarnessTheme(HarnessThemeMode.Light) {
                    HarnessTextField(value, { changes++; value = it }, label, Modifier.width(280.dp),
                        enabled = enabled, readOnly = readOnly, isError = true, supportingText = support, testTag = fieldTag)
                }
            }
            val editor = onNodeWithTag(fieldTag)
            editor.assert(SemanticsMatcher.expectValue(SemanticsProperties.Error, support))
            editor.performClick()
            editor.performTextInput(path)
            editor.assertTextEquals(path)
            editor.performTextReplacement("/tmp/replaced")
            editor.assertTextEquals("/tmp/replaced")
            editor.performKeyInput { keyDown(Key.Enter); keyUp(Key.Enter) }
            val layouts = mutableListOf<TextLayoutResult>()
            editor.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals(1, layouts.single().lineCount)
            val writes = changes
            runOnIdle { readOnly = true }
            editor.assertIsEnabled().performClick().performKeyInput { keyDown(Key.A); keyUp(Key.A) }
            editor.assert(SemanticsMatcher.keyNotDefined(SemanticsActions.SetText))
            editor.assertTextEquals("/tmp/replaced")
            runOnIdle { assertEquals(writes, changes); enabled = false }
            editor.assertIsNotEnabled().performClick()
                .performKeyInput { keyDown(Key.A); keyUp(Key.A) }
            editor.assertTextEquals("/tmp/replaced")
            runOnIdle { assertEquals(writes, changes) }
        }

    @Test
    fun multilineAndVisualTransformationKeepTheirTextLayouts() =
        runSkikoComposeUiTest(size = Size(600f, 800f)) {
            setContent {
                HarnessTheme(HarnessThemeMode.Dark) {
                    Column {
                        HarnessTextField("First line\nSecond gyp line", {}, "Notes", Modifier.width(280.dp),
                            singleLine = false, testTag = "notes")
                        HarnessTextField("private", {}, "Secret", Modifier.width(280.dp),
                            visualTransformation = PasswordVisualTransformation(), testTag = "secret")
                    }
                }
            }
            val notes = mutableListOf<TextLayoutResult>()
            val secret = mutableListOf<TextLayoutResult>()
            onNodeWithTag("notes").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(notes) }
            onNodeWithTag("secret").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(secret) }
            assertEquals(2, notes.single().lineCount)
            assertEquals("•".repeat(7), secret.single().layoutInput.text.text)
            val notesBounds = onNodeWithTag("notes").getUnclippedBoundsInRoot()
            assertTrue(notesBounds.bottom - notesBounds.top > 36.dp)
        }

    private fun assertPaintedLikeReference(
        actual: ImageBitmap, reference: ImageBitmap, ink: Color, background: Color, context: String, inset: Int = 0,
    ) {
        val actualRows = inkRows(actual, ink, background, inset).dropWhile { it == 0 }.dropLastWhile { it == 0 }
        val referenceRows = inkRows(reference, ink, background, 0).dropWhile { it == 0 }.dropLastWhile { it == 0 }
        assertTrue(referenceRows.isNotEmpty(), "$context reference contains no text ink")
        assertTrue(actualRows.isNotEmpty(), "$context semantic text exists but no value pixels were drawn")
        assertTrue(abs(actualRows.size - referenceRows.size) <= 1,
            "$context glyph top/bottom clipped: actual=${actualRows.size}, reference=${referenceRows.size}")
        assertTrue(actualRows.sum() >= referenceRows.sum() * .9f, "$context missing/clipped glyph pixels")
        assertTrue(actualRows.sum() <= referenceRows.sum() * 1.1f, "$context unexpected non-text ink")
    }

    private fun inkRows(bitmap: ImageBitmap, ink: Color, background: Color, inset: Int): List<Int> {
        val pixels = bitmap.toPixelMap()
        val fullContrast = distance(ink, background)
        assertTrue(fullContrast > .05f, "Fixture text must contrast with the background")
        return (inset until bitmap.height - inset).map { y ->
            (inset until bitmap.width - inset).count { x ->
                val pixel = pixels[x, y]
                distance(pixel, background) > fullContrast * .65f && distance(pixel, ink) < fullContrast * .4f
            }
        }
    }

    private fun distance(a: Color, b: Color): Float = abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue)
    private fun tagged(tag: String) = Modifier.semantics { testTag = tag }
    private enum class Mode { Normal, Error, Disabled, ReadOnly }

    private companion object {
        const val fieldTag = "plugin-source"
        const val path = "/tmp/Plugin-gyp_42"
        const val label = "Plugin source / gyp42"
        const val support = "Path must be absolute."
    }
}
