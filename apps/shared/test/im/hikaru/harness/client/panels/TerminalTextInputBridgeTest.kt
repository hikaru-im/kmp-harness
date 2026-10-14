package im.hikaru.harness.client.panels

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import im.hikaru.harness.client.connection.*
import kotlin.test.*

class TerminalTextInputBridgeTest {
    private fun bridge() = TerminalTextInputBridge().also { it.focus(true) }
    @Test fun committedUnicodeIsOneTextAndPreeditHasNoWrite() {
        val b = bridge()
        assertNull(b.edit(TextFieldValue("zhong", composition = TextRange(0, 5)), true))
        assertNull(b.edit(TextFieldValue("中😀", composition = TextRange(0, 3)), true))
        assertEquals(TerminalBridgeAction.Send(TerminalInput.Text("中😀")), b.edit(TextFieldValue("中😀"), true))
        assertEquals("", b.value.text)
        assertNull(b.edit(TextFieldValue(), true))
    }
    @Test fun cancelBlurAndOwnerResetDiscardComposition() {
        val b = bridge()
        b.edit(TextFieldValue("preedit", composition = TextRange(0, 7)), true)
        val cancelledEpoch = b.editorEpoch
        b.cancelComposition()
        assertNull(b.edit(TextFieldValue("preedit"), true, cancelledEpoch))
        assertEquals(TerminalBridgeAction.Send(TerminalInput.Text("fresh")), b.edit(TextFieldValue("fresh"), true))
        b.edit(TextFieldValue("old", composition = TextRange(0, 3)), true)
        val blurredEpoch = b.editorEpoch
        b.focus(false); assertNull(b.edit(TextFieldValue("old"), true, blurredEpoch))
        b.focus(true)
        assertEquals(TerminalBridgeAction.Send(TerminalInput.Text("z")), b.edit(TextFieldValue("z"), true))
        b.edit(TextFieldValue("old", composition = TextRange(0, 3)), true)
        val resetEpoch = b.editorEpoch
        b.reset()
        assertEquals("", b.value.text)
        assertNull(b.edit(TextFieldValue("old"), true, resetEpoch))
        assertNull(b.edit(TextFieldValue(), true))
        assertNull(b.edit(TextFieldValue("disabled"), false))
    }
    @Test fun compositionKeysAndKeyUpCannotDoubleWrite() {
        val b = bridge()
        b.edit(TextFieldValue("中", composition = TextRange(0, 1)), true)
        assertEquals(false to null, b.key(Key.Enter, down = true, up = false))
        assertEquals(false to null, b.key(Key.Backspace, down = true, up = false))
        assertEquals(TerminalBridgeAction.Send(TerminalInput.Text("中")), b.edit(TextFieldValue("中"), true))
        assertNull(b.key(Key.Enter, false, true).second)
        repeat(2) { assertEquals(TerminalBridgeAction.Send(TerminalInput.KeyPress(TerminalKeyName.Enter)), b.key(Key.Enter, true, false).second) }
        assertEquals(true to null, b.key(Key.Enter, false, true))
        assertNull(b.key(Key.A, true, false).second)
    }
    @Test fun pasteAndPlatformShortcutsHaveDistinctSemanticActions() {
        val b = bridge()
        assertEquals(TerminalBridgeAction.Copy, b.key(Key.C, true, false, meta = true).second)
        assertEquals(TerminalBridgeAction.Paste, b.key(Key.V, true, false, meta = true).second)
        assertEquals(TerminalBridgeAction.Send(TerminalInput.KeyPress(TerminalKeyName.Character,
            setOf(TerminalModifier.Control), "c")), b.key(Key.C, true, false, control = true).second)
        assertEquals(TerminalBridgeAction.Send(TerminalInput.Paste("中\r\n😀")), b.paste("中\r\n😀"))
        assertNull(b.paste("")); assertNull(b.paste(null))
        assertEquals(false to null, b.key(Key.K, true, false, meta = true))
        assertEquals(TerminalBridgeAction.Send(TerminalInput.KeyPress(TerminalKeyName.Character,
            setOf(TerminalModifier.Alt, TerminalModifier.Shift), "A")), b.key(Key.A, true, false, alt = true, shift = true).second)
    }
}
