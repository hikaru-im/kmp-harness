package im.hikaru.harness.client.settings

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.input.key.*

/** The desktop supplies both locals; nested page/app roots never dispatch the same event twice. */
public val LocalShortcutsRepository = staticCompositionLocalOf<ShortcutsRepository?> { null }
public val LocalShortcutsEventRoot = staticCompositionLocalOf<ShortcutsInputController?> { null }

public enum class ShortcutKeyPhase { Down, Up, Other }

public data class ShortcutInputEvent(
    val keyId: Long,
    val phase: ShortcutKeyPhase,
    val chord: ShortcutChord? = null,
    val modifierOnly: Boolean = false,
    val escape: Boolean = false,
)

/** Preview owns capture/release; bubbling owns commands after focused controls have had their turn. */
public class ShortcutsInputController(private val repository: ShortcutsRepository) {
    private val held = mutableSetOf<Long>()
    private val eligible = mutableSetOf<Long>()
    private val consumed = mutableSetOf<Long>()
    private var appOwner: Any? = null
    private var appConsumer: ((ShortcutCommand) -> Boolean)? = null
    private var appBlocked: () -> Boolean = { false }
    private var windowOwner: Any? = null
    private var closeConsumer: (() -> Unit)? = null
    private var windowBlocked: () -> Boolean = { false }
    public val windowAttached: Boolean get() = windowOwner != null

    /** Owner tokens prevent an old composition from clearing a newer consumer. */
    public fun attachApp(owner: Any, blocked: () -> Boolean, consume: (ShortcutCommand) -> Boolean) {
        appOwner = owner; appConsumer = consume; appBlocked = blocked
    }

    public fun detachApp(owner: Any) {
        if (appOwner === owner) { appOwner = null; appConsumer = null; appBlocked = { false } }
    }

    public fun attachWindow(owner: Any, blocked: () -> Boolean, close: () -> Unit) {
        windowOwner = owner; closeConsumer = close; windowBlocked = blocked
    }

    public fun detachWindow(owner: Any) {
        if (windowOwner === owner) {
            windowOwner = null; closeConsumer = null; windowBlocked = { false }; releaseAll()
        }
    }

    public fun releaseAll() { held.clear(); eligible.clear(); consumed.clear() }

    public fun preview(event: ShortcutInputEvent): Boolean {
        if (event.phase == ShortcutKeyPhase.Other) return false
        if (event.phase == ShortcutKeyPhase.Up) {
            held.remove(event.keyId); eligible.remove(event.keyId)
            return consumed.remove(event.keyId)
        }
        if (!held.add(event.keyId)) {
            eligible.remove(event.keyId)
            return event.keyId in consumed
        }
        val current = repository.state.value
        // An already open dialog/menu owns its keys, even if capture began before it opened.
        if (appBlocked() || windowBlocked()) return false
        if (current.capture != ShortcutCaptureMode.None) {
            consumed.add(event.keyId)
            if (event.escape) {
                if (current.capture == ShortcutCaptureMode.Search) repository.stopKeySearch() else repository.cancelCapture()
            } else if (event.modifierOnly) repository.recordModifierOnly() else repository.record(event.chord)
            return true
        }
        if (event.escape && current.editor != null && !current.saving) {
            repository.cancelCapture(); consumed.add(event.keyId); return true
        }
        eligible.add(event.keyId)
        return false
    }

    public fun dispatch(event: ShortcutInputEvent): Boolean {
        if (event.phase != ShortcutKeyPhase.Down || !eligible.remove(event.keyId)) return false
        if (repository.state.value.dispatchBlocked || appBlocked() || windowBlocked()) return false
        val command = event.chord?.let(repository::commandFor) ?: return false
        val handled = if (command == ShortcutCommand.CloseWindow) {
            closeConsumer?.let { it(); true } ?: false
        } else appConsumer?.invoke(command) == true
        if (handled) consumed.add(event.keyId)
        return handled
    }
}

/** Explicit static adapter: an unknown key can never become an executable persisted chord. */
public fun KeyEvent.toShortcutInput(): ShortcutInputEvent {
    val physical = shortcutKeys[key]
    return ShortcutInputEvent(
        keyId = key.keyCode,
        phase = when (type) {
            KeyEventType.KeyDown -> ShortcutKeyPhase.Down
            KeyEventType.KeyUp -> ShortcutKeyPhase.Up
            else -> ShortcutKeyPhase.Other
        },
        chord = physical?.let { ShortcutChord(it, ctrl = isCtrlPressed, meta = isMetaPressed, alt = isAltPressed, shift = isShiftPressed) },
        modifierOnly = key in modifierKeys,
        escape = key == Key.Escape,
    )
}

private val modifierKeys: Set<Key> = setOf(Key.CtrlLeft, Key.CtrlRight, Key.MetaLeft, Key.MetaRight,
    Key.AltLeft, Key.AltRight, Key.ShiftLeft, Key.ShiftRight)

private val shortcutKeys: Map<Key, ShortcutKey> = mapOf(
    Key.A to ShortcutKey.A, Key.B to ShortcutKey.B, Key.C to ShortcutKey.C, Key.D to ShortcutKey.D,
    Key.E to ShortcutKey.E, Key.F to ShortcutKey.F, Key.G to ShortcutKey.G, Key.H to ShortcutKey.H,
    Key.I to ShortcutKey.I, Key.J to ShortcutKey.J, Key.K to ShortcutKey.K, Key.L to ShortcutKey.L,
    Key.M to ShortcutKey.M, Key.N to ShortcutKey.N, Key.O to ShortcutKey.O, Key.P to ShortcutKey.P,
    Key.Q to ShortcutKey.Q, Key.R to ShortcutKey.R, Key.S to ShortcutKey.S, Key.T to ShortcutKey.T,
    Key.U to ShortcutKey.U, Key.V to ShortcutKey.V, Key.W to ShortcutKey.W, Key.X to ShortcutKey.X,
    Key.Y to ShortcutKey.Y, Key.Z to ShortcutKey.Z,
    Key.Zero to ShortcutKey.Digit0, Key.One to ShortcutKey.Digit1, Key.Two to ShortcutKey.Digit2,
    Key.Three to ShortcutKey.Digit3, Key.Four to ShortcutKey.Digit4, Key.Five to ShortcutKey.Digit5,
    Key.Six to ShortcutKey.Digit6, Key.Seven to ShortcutKey.Digit7, Key.Eight to ShortcutKey.Digit8, Key.Nine to ShortcutKey.Digit9,
    Key.Comma to ShortcutKey.Comma, Key.Slash to ShortcutKey.Slash, Key.LeftBracket to ShortcutKey.LeftBracket,
    Key.RightBracket to ShortcutKey.RightBracket, Key.Period to ShortcutKey.Period,
    Key.F1 to ShortcutKey.F1, Key.F2 to ShortcutKey.F2, Key.F3 to ShortcutKey.F3, Key.F4 to ShortcutKey.F4,
    Key.F5 to ShortcutKey.F5, Key.F6 to ShortcutKey.F6, Key.F7 to ShortcutKey.F7, Key.F8 to ShortcutKey.F8,
    Key.F9 to ShortcutKey.F9, Key.F10 to ShortcutKey.F10, Key.F11 to ShortcutKey.F11, Key.F12 to ShortcutKey.F12,
    Key.Escape to ShortcutKey.Escape, Key.Enter to ShortcutKey.Enter, Key.Spacebar to ShortcutKey.Space,
    Key.Tab to ShortcutKey.Tab, Key.Backspace to ShortcutKey.Backspace,
    Key.DirectionLeft to ShortcutKey.Left, Key.DirectionRight to ShortcutKey.Right,
    Key.DirectionUp to ShortcutKey.Up, Key.DirectionDown to ShortcutKey.Down,
)
