package im.hikaru.harness.desktop.window

import im.hikaru.harness.client.settings.GlobalHotkeyRegistrar
import im.hikaru.harness.client.settings.GlobalHotkeyRegistration
import im.hikaru.harness.client.settings.ShortcutChord

/**
 * Desktop seam for a future OS-native registration backend. Compose key events are intentionally
 * not reported as global: they only observe the focused Harness window.
 */
public class DesktopGlobalHotkeyRegistrar : GlobalHotkeyRegistrar {
    override val supported: Boolean = false

    override fun register(chord: ShortcutChord, onTrigger: () -> Unit): GlobalHotkeyRegistration =
        GlobalHotkeyRegistration.Unsupported("当前桌面宿主未接入系统级热键。")

    override fun unregister() = Unit
}
