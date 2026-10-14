package im.hikaru.harness.exec.jvm

import com.jediterm.core.input.InputEvent
import com.jediterm.core.input.KeyEvent
import com.jediterm.core.input.KeyInputEvent
import com.jediterm.terminal.TerminalDisplay
import com.jediterm.terminal.TerminalKeyEncoder
import com.jediterm.terminal.emulator.keyboard.KeyEventProcessingResult
import com.jediterm.terminal.emulator.keyboard.KeyEventProcessingSettings
import com.jediterm.terminal.emulator.keyboard.TerminalKeyEventProcessor
import com.jediterm.terminal.model.JediTerminal
import com.jediterm.terminal.model.StyleState
import com.jediterm.terminal.model.TerminalTextBuffer
import im.hikaru.harness.session.api.TerminalInput
import im.hikaru.harness.session.api.TerminalKeyName
import im.hikaru.harness.session.api.TerminalModifier
import java.nio.charset.StandardCharsets.UTF_8

/** All callbacks and encoding run under the owning Entry's model gate. */
internal class JvmTerminalInputAdapter(
    display: TerminalDisplay,
    buffer: TerminalTextBuffer,
    styles: StyleState,
) : JediTerminal(display, buffer, styles) {
    // Super construction invokes reset(true) and setAutoNewLine(false). These callbacks
    // use only primitive fields, never a Kotlin object which has not been initialized.
    private var applicationArrows = false
    private var applicationKeypad = false
    private var autoNewLine = false
    private var altSendsEscape = true

    override fun setApplicationArrowKeys(enabled: Boolean) {
        applicationArrows = enabled
        super.setApplicationArrowKeys(enabled)
    }

    override fun setApplicationKeypad(enabled: Boolean) {
        applicationKeypad = enabled
        super.setApplicationKeypad(enabled)
    }

    override fun setAutoNewLine(enabled: Boolean) {
        autoNewLine = enabled
        super.setAutoNewLine(enabled)
    }

    override fun setAltSendsEscape(enabled: Boolean) {
        altSendsEscape = enabled
        super.setAltSendsEscape(enabled)
    }

    override fun getCodeForKey(key: Int, modifiersEx: Int): ByteArray? {
        // getCode mutates SS3 entries for modified keys, including F1..F4. Each call
        // owns an entirely fresh encoder, so a later plain key cannot inherit that mutation.
        val encoder = TerminalKeyEncoder()
        if (applicationArrows) encoder.arrowKeysApplicationSequences() else encoder.arrowKeysAnsiCursorSequences()
        if (applicationKeypad) encoder.keypadApplicationSequences() else encoder.keypadAnsiSequences()
        encoder.setAutoNewLine(autoNewLine)
        encoder.setAltSendsEscape(altSendsEscape)
        return encoder.getCode(key, modifiersEx)?.copyOf()
    }

    /** null is a known unsupported request or the processor's Unhandled result. */
    fun encode(input: TerminalInput.KeyPress): ByteArray? {
        if (TerminalModifier.Meta in input.modifiers) return null
        val modifiers = input.modifiers.fold(0) { mask, modifier -> mask or when (modifier) {
            TerminalModifier.Shift -> InputEvent.SHIFT_DOWN_MASK
            TerminalModifier.Control -> InputEvent.CTRL_DOWN_MASK
            TerminalModifier.Alt -> InputEvent.ALT_DOWN_MASK
            TerminalModifier.Meta -> InputEvent.META_DOWN_MASK
        } }
        val keyCode: Int
        val keyChar: Char
        if (input.key == TerminalKeyName.Character) {
            val character = input.character!![0]
            when {
                TerminalModifier.Control in input.modifiers &&
                    input.modifiers.all { it == TerminalModifier.Control || it == TerminalModifier.Shift } -> {
                    // A character's physical key code can collide with navigation/function
                    // constants. Use an unmapped code and explicitly synthesize its control char.
                    keyCode = 0
                    keyChar = controlCharacter(character) ?: return null
                }
                input.modifiers == setOf(TerminalModifier.Alt) &&
                    (character in 'a'..'z' || character in 'A'..'Z') -> {
                    keyCode = character.uppercaseChar().code
                    keyChar = character
                }
                else -> return null
            }
        } else {
            keyCode = namedKeyCode(input.key)
            keyChar = when (input.key) {
                TerminalKeyName.Enter -> '\r'
                TerminalKeyName.Backspace -> '\b'
                TerminalKeyName.Tab -> '\t'
                TerminalKeyName.Escape -> '\u001b'
                else -> '\uffff' // CHAR_UNDEFINED
            }
        }
        return when (val result = TerminalKeyEventProcessor.processKey(
            KeyInputEvent(KeyInputEvent.Type.PRESSED, keyCode, keyChar, modifiers), this,
            KeyEventProcessingSettings(shiftEnterSendsEscCR = false, scrollToBottomOnTyping = false,
                altSendsEscape = altSendsEscape),
        )) {
            is KeyEventProcessingResult.BytesResult -> result.bytes.copyOf()
            is KeyEventProcessingResult.StringResult -> result.string.toByteArray(UTF_8)
            KeyEventProcessingResult.Unhandled -> null
        }
    }

    private fun controlCharacter(character: Char): Char? = when (character) {
        in 'a'..'z' -> (character.code - 'a'.code + 1).toChar()
        in 'A'..'Z' -> (character.code - 'A'.code + 1).toChar()
        ' ', '@' -> '\u0000'
        '[' -> '\u001b'
        '\\' -> '\u001c'
        ']' -> '\u001d'
        '^' -> '\u001e'
        '_' -> '\u001f'
        '?' -> '\u007f'
        else -> null
    }

    private fun namedKeyCode(key: TerminalKeyName): Int = when (key) {
        TerminalKeyName.Enter -> KeyEvent.VK_ENTER
        TerminalKeyName.Escape -> KeyEvent.VK_ESCAPE
        TerminalKeyName.Tab -> KeyEvent.VK_TAB
        TerminalKeyName.Backspace -> KeyEvent.VK_BACK_SPACE
        TerminalKeyName.Up -> KeyEvent.VK_UP
        TerminalKeyName.Down -> KeyEvent.VK_DOWN
        TerminalKeyName.Left -> KeyEvent.VK_LEFT
        TerminalKeyName.Right -> KeyEvent.VK_RIGHT
        TerminalKeyName.Home -> KeyEvent.VK_HOME
        TerminalKeyName.End -> KeyEvent.VK_END
        TerminalKeyName.Insert -> KeyEvent.VK_INSERT
        TerminalKeyName.Delete -> KeyEvent.VK_DELETE
        TerminalKeyName.PageUp -> KeyEvent.VK_PAGE_UP
        TerminalKeyName.PageDown -> KeyEvent.VK_PAGE_DOWN
        TerminalKeyName.F1 -> KeyEvent.VK_F1
        TerminalKeyName.F2 -> KeyEvent.VK_F2
        TerminalKeyName.F3 -> KeyEvent.VK_F3
        TerminalKeyName.F4 -> KeyEvent.VK_F4
        TerminalKeyName.F5 -> KeyEvent.VK_F5
        TerminalKeyName.F6 -> KeyEvent.VK_F6
        TerminalKeyName.F7 -> KeyEvent.VK_F7
        TerminalKeyName.F8 -> KeyEvent.VK_F8
        TerminalKeyName.F9 -> KeyEvent.VK_F9
        TerminalKeyName.F10 -> KeyEvent.VK_F10
        TerminalKeyName.F11 -> KeyEvent.VK_F11
        TerminalKeyName.F12 -> KeyEvent.VK_F12
        TerminalKeyName.KeypadUp -> KeyEvent.VK_KP_UP
        TerminalKeyName.KeypadDown -> KeyEvent.VK_KP_DOWN
        TerminalKeyName.KeypadLeft -> KeyEvent.VK_KP_LEFT
        TerminalKeyName.KeypadRight -> KeyEvent.VK_KP_RIGHT
        TerminalKeyName.Character -> error("Character uses an explicit control or base letter mapping")
    }
}
