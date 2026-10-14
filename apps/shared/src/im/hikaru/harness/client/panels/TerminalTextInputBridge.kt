package im.hikaru.harness.client.panels

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import im.hikaru.harness.client.connection.*
import im.hikaru.harness.ui.foundation.HarnessTokens

public sealed interface TerminalBridgeAction {
    public data class Send(val input: TerminalInput) : TerminalBridgeAction
    public data object Copy : TerminalBridgeAction
    public data object Paste : TerminalBridgeAction
}

/** The editor's composition range is authoritative; no preedit character is dispatched. */
public class TerminalTextInputBridge {
    public var value: TextFieldValue by mutableStateOf(TextFieldValue())
        private set
    public var focused: Boolean by mutableStateOf(false)
        private set
    public var editorEpoch: Long by mutableStateOf(0L)
        private set
    private val consumed = mutableSetOf<Key>()

    public fun focus(active: Boolean) {
        val lostFocus = focused && !active
        focused = active
        if (lostFocus) reset()
    }
    public fun reset() {
        editorEpoch++
        value = TextFieldValue(); consumed.clear()
    }
    public fun cancelComposition() { reset() }

    public fun edit(next: TextFieldValue, enabled: Boolean, callbackEpoch: Long = editorEpoch): TerminalBridgeAction? {
        if (callbackEpoch != editorEpoch || !focused || !enabled) return null
        if (next.composition != null) { value = next; return null }
        // Empty text also covers cancellation and editor deletion of the entire preedit.
        val text = next.text
        value = TextFieldValue()
        return text.takeIf { it.isNotEmpty() }?.let { TerminalBridgeAction.Send(TerminalInput.Text(it)) }
    }

    /** Returns consumed plus an action. Key-up only releases; repeats each produce one key-down. */
    public fun key(
        key: Key, down: Boolean, up: Boolean, control: Boolean = false, alt: Boolean = false,
        shift: Boolean = false, meta: Boolean = false, enabled: Boolean = true,
    ): Pair<Boolean, TerminalBridgeAction?> {
        if (up) return consumed.remove(key) to null
        if (!down || !focused) return false to null
        if (value.composition != null) {
            if (key == Key.Escape) { cancelComposition(); consumed.add(key); return true to null }
            return false to null
        }
        val action: TerminalBridgeAction? = when {
            meta && !control && !alt && key == Key.C -> TerminalBridgeAction.Copy
            meta && !control && !alt && key == Key.V && enabled -> TerminalBridgeAction.Paste
            meta -> null // Let the existing root bubble own application commands.
            !enabled -> null
            key in names -> TerminalBridgeAction.Send(TerminalInput.KeyPress(names.getValue(key), modifiers(control, alt, shift)))
            (control || alt) && key in ascii -> TerminalBridgeAction.Send(TerminalInput.KeyPress(TerminalKeyName.Character,
                modifiers(control, alt, shift), (if (shift) ascii.getValue(key).second else ascii.getValue(key).first).toString()))
            else -> null // Committed printable text belongs to BasicTextField/IME.
        }
        if (action != null) consumed.add(key)
        return (action != null) to action
    }

    public fun paste(text: String?): TerminalBridgeAction? = text?.takeIf { it.isNotEmpty() }?.let {
        TerminalBridgeAction.Send(TerminalInput.Paste(it))
    }

    private fun modifiers(control: Boolean, alt: Boolean, shift: Boolean): Set<TerminalModifier> = buildSet {
        if (control) add(TerminalModifier.Control)
        if (alt) add(TerminalModifier.Alt)
        if (shift) add(TerminalModifier.Shift)
    }

    private companion object {
        val names = mapOf(
            Key.Enter to TerminalKeyName.Enter, Key.NumPadEnter to TerminalKeyName.Enter,
            Key.Escape to TerminalKeyName.Escape, Key.Tab to TerminalKeyName.Tab, Key.Backspace to TerminalKeyName.Backspace,
            Key.DirectionUp to TerminalKeyName.Up, Key.DirectionDown to TerminalKeyName.Down,
            Key.DirectionLeft to TerminalKeyName.Left, Key.DirectionRight to TerminalKeyName.Right,
            Key.MoveHome to TerminalKeyName.Home, Key.MoveEnd to TerminalKeyName.End,
            Key.Insert to TerminalKeyName.Insert, Key.Delete to TerminalKeyName.Delete,
            Key.PageUp to TerminalKeyName.PageUp, Key.PageDown to TerminalKeyName.PageDown,
            Key.F1 to TerminalKeyName.F1, Key.F2 to TerminalKeyName.F2, Key.F3 to TerminalKeyName.F3,
            Key.F4 to TerminalKeyName.F4, Key.F5 to TerminalKeyName.F5, Key.F6 to TerminalKeyName.F6,
            Key.F7 to TerminalKeyName.F7, Key.F8 to TerminalKeyName.F8, Key.F9 to TerminalKeyName.F9,
            Key.F10 to TerminalKeyName.F10, Key.F11 to TerminalKeyName.F11, Key.F12 to TerminalKeyName.F12,
            Key.NumPad8 to TerminalKeyName.KeypadUp, Key.NumPad2 to TerminalKeyName.KeypadDown,
            Key.NumPad4 to TerminalKeyName.KeypadLeft, Key.NumPad6 to TerminalKeyName.KeypadRight,
            Key.NumPad7 to TerminalKeyName.Home, Key.NumPad1 to TerminalKeyName.End,
            Key.NumPadDirectionUp to TerminalKeyName.KeypadUp, Key.NumPadDirectionDown to TerminalKeyName.KeypadDown,
            Key.NumPadDirectionLeft to TerminalKeyName.KeypadLeft, Key.NumPadDirectionRight to TerminalKeyName.KeypadRight,
            Key.NumPadMoveHome to TerminalKeyName.Home, Key.NumPadMoveEnd to TerminalKeyName.End,
        )
        val ascii = mapOf(
            Key.A to ('a' to 'A'), Key.B to ('b' to 'B'), Key.C to ('c' to 'C'), Key.D to ('d' to 'D'),
            Key.E to ('e' to 'E'), Key.F to ('f' to 'F'), Key.G to ('g' to 'G'), Key.H to ('h' to 'H'),
            Key.I to ('i' to 'I'), Key.J to ('j' to 'J'), Key.K to ('k' to 'K'), Key.L to ('l' to 'L'),
            Key.M to ('m' to 'M'), Key.N to ('n' to 'N'), Key.O to ('o' to 'O'), Key.P to ('p' to 'P'),
            Key.Q to ('q' to 'Q'), Key.R to ('r' to 'R'), Key.S to ('s' to 'S'), Key.T to ('t' to 'T'),
            Key.U to ('u' to 'U'), Key.V to ('v' to 'V'), Key.W to ('w' to 'W'), Key.X to ('x' to 'X'),
            Key.Y to ('y' to 'Y'), Key.Z to ('z' to 'Z'), Key.Zero to ('0' to ')'), Key.One to ('1' to '!'),
            Key.Two to ('2' to '@'), Key.Three to ('3' to '#'), Key.Four to ('4' to '$'), Key.Five to ('5' to '%'),
            Key.Six to ('6' to '^'), Key.Seven to ('7' to '&'), Key.Eight to ('8' to '*'), Key.Nine to ('9' to '('),
            Key.Spacebar to (' ' to ' '), Key.Comma to (',' to '<'), Key.Period to ('.' to '>'),
            Key.Slash to ('/' to '?'), Key.Backslash to ('\\' to '|'), Key.LeftBracket to ('[' to '{'),
            Key.RightBracket to (']' to '}'), Key.Minus to ('-' to '_'), Key.Equals to ('=' to '+'),
            Key.Semicolon to (';' to ':'), Key.Apostrophe to ('\'' to '"'), Key.Grave to ('`' to '~'),
        )
    }
}

@Composable
public fun TerminalTextInputHost(
    bridge: TerminalTextInputBridge, enabled: Boolean, requester: FocusRequester,
    onAction: (TerminalBridgeAction) -> Unit, modifier: Modifier = Modifier,
    content: @Composable (Boolean) -> Unit,
) {
    val currentAction by rememberUpdatedState(onAction)
    val currentEnabled by rememberUpdatedState(enabled)
    val windowInfo = LocalWindowInfo.current
    val windowFocused = windowInfo.isWindowFocused
    val editorEpoch = bridge.editorEpoch
    var editorFocused by remember(bridge, editorEpoch) { mutableStateOf(false) }
    LaunchedEffect(bridge, windowFocused, editorFocused) {
        bridge.focus(editorFocused && windowFocused)
    }
    LaunchedEffect(bridge, enabled) { if (!enabled) bridge.reset() }
    DisposableEffect(bridge) { onDispose { bridge.focus(false) } }
    // A reset ends the old text-input session. Its callbacks retain their captured epoch.
    key(bridge, editorEpoch) { BasicTextField(
        value = bridge.value,
        onValueChange = { bridge.edit(it, currentEnabled && windowInfo.isWindowFocused, editorEpoch)?.let(currentAction) },
        // Keep exited text focusable for selection/copy; edit() rejects writes when disabled.
        readOnly = !enabled,
        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, color = HarnessTokens.Colors.Text),
        cursorBrush = SolidColor(Color.Transparent),
        modifier = modifier.focusRequester(requester).onFocusChanged {
            if (bridge.editorEpoch == editorEpoch) {
                editorFocused = it.isFocused; bridge.focus(it.isFocused && windowInfo.isWindowFocused)
            }
        }
            .onPreviewKeyEvent { event ->
                if (bridge.editorEpoch != editorEpoch || !windowInfo.isWindowFocused) return@onPreviewKeyEvent false
                val (consumed, action) = bridge.key(event.key, event.type == KeyEventType.KeyDown, event.type == KeyEventType.KeyUp,
                    event.isCtrlPressed, event.isAltPressed, event.isShiftPressed, event.isMetaPressed, currentEnabled)
                action?.let(currentAction); consumed
            }.semantics { testTag = "pty-terminal-input"; contentDescription = "交互终端输入" },
        decorationBox = { inner ->
            Box(Modifier.fillMaxSize()) {
                content(bridge.focused && windowFocused)
                // The real editor owns IME and its candidate anchor; only preedit is visible here.
                Box(Modifier.align(Alignment.BottomStart).padding(bottom = 2.dp)) { inner() }
            }
        },
    ) }
}
