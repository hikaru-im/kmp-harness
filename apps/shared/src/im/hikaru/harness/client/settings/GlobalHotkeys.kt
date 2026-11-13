package im.hikaru.harness.client.settings

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

public enum class GlobalHotkeyStatus { Disabled, Registered, Unsupported, Conflict, Failed }

public data class GlobalHotkeyState(
    val status: GlobalHotkeyStatus = GlobalHotkeyStatus.Disabled,
    val chord: ShortcutChord? = null,
    val message: String? = null,
) {
    public val label: String
        get() = when (status) {
            GlobalHotkeyStatus.Disabled -> "未启用"
            GlobalHotkeyStatus.Registered -> "已启用"
            GlobalHotkeyStatus.Unsupported -> "不可用"
            GlobalHotkeyStatus.Conflict -> "存在冲突"
            GlobalHotkeyStatus.Failed -> "注册失败"
        }
}

public sealed interface GlobalHotkeyRegistration {
    public data object Registered : GlobalHotkeyRegistration
    public data class Unsupported(val reason: String) : GlobalHotkeyRegistration
    public data class Conflict(val reason: String) : GlobalHotkeyRegistration
    public data class Failed(val reason: String) : GlobalHotkeyRegistration
}

/** Native hosts implement this seam; shared code never pretends an in-process key listener is global. */
public interface GlobalHotkeyRegistrar {
    public val supported: Boolean
    public fun register(chord: ShortcutChord, onTrigger: () -> Unit): GlobalHotkeyRegistration
    public fun unregister()
}

/** Keeps the configurable OpenQuickChat binding synchronized with the native global-hotkey host. */
public class GlobalHotkeyController(
    private val registrar: GlobalHotkeyRegistrar,
) {
    private val mutableState = MutableStateFlow(GlobalHotkeyState())
    private val mutableTriggers = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var registeredChord: ShortcutChord? = null

    public val state: StateFlow<GlobalHotkeyState> = mutableState.asStateFlow()
    public val triggers: SharedFlow<Unit> = mutableTriggers.asSharedFlow()

    public fun sync(settings: ShortcutsSettings, platform: ShortcutPlatform) {
        val chord = settings.bindings(ShortcutCommand.OpenQuickChat, platform).firstOrNull()
        if (chord == null) {
            clear(GlobalHotkeyState(GlobalHotkeyStatus.Disabled))
            return
        }
        if (!registrar.supported) {
            clear(GlobalHotkeyState(GlobalHotkeyStatus.Unsupported, chord, "当前平台未接入系统级热键。"))
            return
        }
        val conflict = ShortcutRegistry.globalHotkeyError(settings, platform, chord)
        if (conflict != null) {
            clear(GlobalHotkeyState(GlobalHotkeyStatus.Conflict, chord, conflict))
            return
        }
        if (registeredChord == chord && mutableState.value.status == GlobalHotkeyStatus.Registered) return
        registrar.unregister()
        registeredChord = null
        when (val result = registrar.register(chord) { mutableTriggers.tryEmit(Unit) }) {
            GlobalHotkeyRegistration.Registered -> {
                registeredChord = chord
                mutableState.value = GlobalHotkeyState(GlobalHotkeyStatus.Registered, chord)
            }
            is GlobalHotkeyRegistration.Unsupported ->
                mutableState.value = GlobalHotkeyState(GlobalHotkeyStatus.Unsupported, chord, result.reason)
            is GlobalHotkeyRegistration.Conflict ->
                mutableState.value = GlobalHotkeyState(GlobalHotkeyStatus.Conflict, chord, result.reason)
            is GlobalHotkeyRegistration.Failed ->
                mutableState.value = GlobalHotkeyState(GlobalHotkeyStatus.Failed, chord, result.reason)
        }
    }

    public fun close() {
        registrar.unregister()
        registeredChord = null
        mutableState.value = GlobalHotkeyState(GlobalHotkeyStatus.Disabled)
    }

    private fun clear(next: GlobalHotkeyState) {
        if (registeredChord != null) registrar.unregister()
        registeredChord = null
        mutableState.value = next
    }
}
