package im.hikaru.harness.client.settings

import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GlobalHotkeysTest {
    @Test
    fun unsupportedRegistrarExposesUnsupportedStateWithoutRegistration() {
        val registrar = FakeRegistrar(supported = false)
        val controller = GlobalHotkeyController(registrar)

        controller.sync(ShortcutsSettings(), ShortcutPlatform.Mac)

        assertEquals(GlobalHotkeyStatus.Unsupported, controller.state.value.status)
        assertEquals(0, registrar.registerCalls)
        assertTrue(controller.state.value.message!!.contains("系统级热键"))
    }

    @Test
    fun conflictingCommandIsRejectedBeforeNativeRegistration() {
        val registrar = FakeRegistrar(supported = true)
        val controller = GlobalHotkeyController(registrar)
        val settings = ShortcutsSettings().withBindings(
            ShortcutCommand.OpenQuickChat,
            listOf(ShortcutChord(ShortcutKey.P, meta = true)),
        )

        controller.sync(settings, ShortcutPlatform.Mac)

        assertEquals(GlobalHotkeyStatus.Conflict, controller.state.value.status)
        assertEquals(0, registrar.registerCalls)
        assertTrue(controller.state.value.message!!.contains("搜索文件"))
    }

    @Test
    fun registeredHotkeyEmitsQuickChatTrigger() = runBlocking {
        val registrar = FakeRegistrar(supported = true)
        val controller = GlobalHotkeyController(registrar)
        controller.sync(ShortcutsSettings(), ShortcutPlatform.Mac)
        assertEquals(GlobalHotkeyStatus.Registered, controller.state.value.status)

        val trigger = launch { controller.triggers.first() }
        kotlinx.coroutines.yield()
        registrar.trigger()
        trigger.join()
        assertEquals(1, registrar.registerCalls)
    }

    private class FakeRegistrar(
        override val supported: Boolean,
    ) : GlobalHotkeyRegistrar {
        var registerCalls: Int = 0
        private var callback: (() -> Unit)? = null

        override fun register(chord: ShortcutChord, onTrigger: () -> Unit): GlobalHotkeyRegistration {
            registerCalls += 1
            callback = onTrigger
            return GlobalHotkeyRegistration.Registered
        }

        override fun unregister() {
            callback = null
        }

        fun trigger() {
            callback?.invoke()
        }
    }
}
