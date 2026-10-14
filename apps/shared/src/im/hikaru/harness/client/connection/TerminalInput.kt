package im.hikaru.harness.client.connection

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/** Committed text and semantic keys; preedit stays in the renderer. */
@Serializable
public sealed interface TerminalInput {
    @Serializable @SerialName("text")
    public data class Text(public val text: String) : TerminalInput

    @Serializable @SerialName("paste")
    public data class Paste(public val text: String) : TerminalInput

    @Serializable @SerialName("key_press")
    public class KeyPress private constructor(
        public val key: TerminalKeyName,
        public val modifiers: Set<TerminalModifier> = emptySet(),
        public val character: String? = null,
        @Transient private val captured: Boolean = true,
    ) : TerminalInput {
        public constructor(
            key: TerminalKeyName,
            modifiers: Set<TerminalModifier> = emptySet(),
            character: String? = null,
        ) : this(key, modifiers.toSet(), character, true)

        init {
            require(if (key == TerminalKeyName.Character) {
                character != null && character.length == 1 && character[0] in ' '..'~'
            } else character == null) { "Only Character carries one printable ASCII character" }
        }

        override fun equals(other: Any?): Boolean = other is KeyPress &&
            key == other.key && modifiers == other.modifiers && character == other.character
        override fun hashCode(): Int = 31 * (31 * key.hashCode() + modifiers.hashCode()) + (character?.hashCode() ?: 0)
        override fun toString(): String = "KeyPress(key=$key, modifiers=$modifiers, character=$character)"
    }
}

@Serializable
public enum class TerminalModifier { Shift, Control, Alt, Meta }

/** Keypad support is limited to these four directions and the named Home/End keys. */
@Serializable
public enum class TerminalKeyName {
    Enter, Escape, Tab, Backspace, Up, Down, Left, Right, Home, End, Insert, Delete, PageUp, PageDown,
    F1, F2, F3, F4, F5, F6, F7, F8, F9, F10, F11, F12,
    KeypadUp, KeypadDown, KeypadLeft, KeypadRight, Character,
}
