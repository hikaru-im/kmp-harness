package im.hikaru.harness.client.settings

import kotlinx.coroutines.runBlocking
import kotlin.test.*

class ShortcutsInputControllerTest {
    private fun down(id: Long, chord: ShortcutChord) = ShortcutInputEvent(id, ShortcutKeyPhase.Down, chord)
    private fun up(id: Long) = ShortcutInputEvent(id, ShortcutKeyPhase.Up)

    @Test
    fun keyDownDispatchesOnceAndKeyUpReleasesEvenWhenModifierChanges() = runBlocking {
        val repository = ShortcutsRepository(platform = ShortcutPlatform.Mac).also { it.load() }
        val input = repository.input
        var appCalls = 0
        var closes = 0
        input.attachApp(Any(), { false }) { appCalls++; true }
        input.attachWindow(Any(), { false }) { closes++ }
        val settings = down(1, ShortcutRegistry.defaults(ShortcutCommand.Settings, repository.platform).single())
        assertFalse(input.preview(settings))
        assertTrue(input.dispatch(settings))
        assertFalse(input.dispatch(settings)) // A second bubbling root cannot execute again.
        assertTrue(input.preview(settings)) // OS repeat is consumed.
        assertFalse(input.dispatch(settings))
        assertTrue(input.preview(up(1)))
        assertFalse(input.preview(settings))
        assertTrue(input.dispatch(settings))
        assertEquals(2, appCalls)
        input.releaseAll() // Window focus loss can occur without delivering KeyUp.
        assertFalse(input.preview(settings))
        assertTrue(input.dispatch(settings))
        assertEquals(3, appCalls)
        val close = down(2, ShortcutRegistry.defaults(ShortcutCommand.CloseWindow, repository.platform).single())
        input.preview(close)
        assertTrue(input.dispatch(close))
        assertEquals(1, closes)
        assertEquals(3, appCalls)
    }

    @Test
    fun captureAndKeySearchSuppressBothConsumersAndEscapeCancels() = runBlocking {
        val repository = ShortcutsRepository(platform = ShortcutPlatform.Windows).also { it.load() }
        var calls = 0
        repository.input.attachApp(Any(), { false }) { calls++; true }
        repository.input.attachWindow(Any(), { false }) { calls++ }
        val close = down(1, ShortcutChord(ShortcutKey.W, ctrl = true))
        repository.beginRecording(ShortcutCommand.CloseWindow, 0)
        assertTrue(repository.input.preview(ShortcutInputEvent(2, ShortcutKeyPhase.Down, modifierOnly = true)))
        assertNull(repository.state.value.editor?.chord)
        assertTrue(repository.input.preview(close))
        assertFalse(repository.input.dispatch(close))
        assertEquals(close.chord, repository.state.value.editor?.chord)
        assertTrue(repository.input.preview(up(1)))
        val escape = ShortcutInputEvent(3, ShortcutKeyPhase.Down, escape = true)
        assertTrue(repository.input.preview(escape))
        assertNull(repository.state.value.editor)
        repository.input.preview(up(3))
        repository.startKeySearch()
        assertTrue(repository.input.preview(close))
        assertFalse(repository.input.dispatch(close))
        assertEquals(close.chord, repository.state.value.keyQuery)
        assertTrue(repository.input.preview(escape))
        assertEquals(ShortcutCaptureMode.None, repository.state.value.capture)
        assertNull(repository.state.value.keyQuery)
        assertEquals(0, calls)
    }

    @Test
    fun blockedDialogsAndMenusLeaveTheirOwnKeysUnhandledAndFocusLossReleasesHeldKeys() = runBlocking {
        val repository = ShortcutsRepository(platform = ShortcutPlatform.Linux).also { it.load() }
        var appBlocked = true
        var windowBlocked = false
        var calls = 0
        repository.input.attachApp(Any(), { appBlocked }) { calls++; true }
        repository.input.attachWindow(Any(), { windowBlocked }) { calls++ }
        val event = down(1, ShortcutChord(ShortcutKey.Comma, ctrl = true))
        assertFalse(repository.input.preview(event))
        assertFalse(repository.input.dispatch(event))
        repository.input.preview(up(1))
        appBlocked = false
        windowBlocked = true
        assertFalse(repository.input.preview(event))
        assertFalse(repository.input.dispatch(event))
        windowBlocked = false
        repository.input.releaseAll()
        assertFalse(repository.input.preview(event))
        assertTrue(repository.input.dispatch(event))
        assertEquals(1, calls)
        val plainEnter = down(4, ShortcutChord(ShortcutKey.Enter))
        assertFalse(repository.input.preview(plainEnter))
        assertFalse(repository.input.dispatch(plainEnter))
        repository.startKeySearch()
        windowBlocked = true
        val escape = ShortcutInputEvent(5, ShortcutKeyPhase.Down, escape = true)
        assertFalse(repository.input.preview(escape))
        assertFalse(repository.input.dispatch(escape))
        assertEquals(ShortcutCaptureMode.Search, repository.state.value.capture)
        assertNull(repository.state.value.keyQuery)
        repository.input.preview(up(5))
        windowBlocked = false
        appBlocked = true
        assertFalse(repository.input.preview(escape))
        assertFalse(repository.input.dispatch(escape))
        assertEquals(ShortcutCaptureMode.Search, repository.state.value.capture)
    }

    @Test
    fun focusLossClearsCapturedKeysWithoutRequiringTheirMissingKeyUp() = runBlocking {
        val repository = ShortcutsRepository(platform = ShortcutPlatform.Mac).also { it.load() }
        val input = repository.input
        var closes = 0
        input.attachWindow(Any(), { false }) { closes++ }
        val event = down(1, ShortcutChord(ShortcutKey.W, meta = true))
        repository.startKeySearch()
        assertTrue(input.preview(event))
        assertFalse(input.dispatch(event))
        repository.stopKeySearch()
        input.releaseAll()
        assertFalse(input.preview(event))
        assertTrue(input.dispatch(event))
        assertTrue(input.preview(event))
        assertFalse(input.dispatch(event))
        assertEquals(1, closes)
        assertTrue(input.preview(up(1)))
        assertFalse(input.preview(up(1)))
    }

    @Test
    fun focusedChildConsumptionAndOldOwnerDisposalCannotDoubleDispatch() = runBlocking {
        val repository = ShortcutsRepository(platform = ShortcutPlatform.Mac).also { it.load() }
        var calls = 0
        val oldOwner = Any()
        repository.input.attachApp(oldOwner, { false }) { error("old consumer") }
        repository.input.attachApp(Any(), { false }) { calls++; true }
        repository.input.detachApp(oldOwner)
        val event = down(1, ShortcutChord(ShortcutKey.Comma, meta = true))
        assertFalse(repository.input.preview(event))
        // Focused child handles this first event; bubbling never reaches the root.
        assertFalse(repository.input.preview(event))
        assertFalse(repository.input.dispatch(event))
        repository.input.preview(up(1))
        repository.input.preview(event)
        assertTrue(repository.input.dispatch(event))
        assertEquals(1, calls)
        assertFalse(repository.input.preview(event.copy(phase = ShortcutKeyPhase.Other)))
    }
}
