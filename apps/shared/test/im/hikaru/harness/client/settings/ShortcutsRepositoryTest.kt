package im.hikaru.harness.client.settings

import kotlinx.coroutines.*
import kotlin.test.*

class ShortcutsRepositoryTest {
    private val platform = ShortcutPlatform.Mac
    private val custom = ShortcutChord(ShortcutKey.K, meta = true, shift = true)

    @Test
    fun defaultsOverridesMultipleBindingsRemovalAndResetRoundTrip() = runBlocking {
        val store = MemoryShortcutsStore()
        val repository = ShortcutsRepository(store, platform)
        repository.load()
        assertEquals(6, ShortcutRegistry.commands.size)
        assertEquals(emptyList(), repository.state.value.settings.bindings(ShortcutCommand.ManageTasks, platform))
        val defaults = repository.state.value.settings.bindings(ShortcutCommand.CloseWindow, platform)
        assertTrue(repository.save(ShortcutsSettings().withBindings(ShortcutCommand.CloseWindow, defaults + custom)))
        assertEquals(ShortcutCommand.CloseWindow, repository.commandFor(custom))
        assertTrue(repository.remove(ShortcutCommand.CloseWindow, 0))
        assertNull(repository.commandFor(defaults.single()))
        assertTrue(repository.remove(ShortcutCommand.CloseWindow, 0))
        assertTrue(ShortcutCommand.CloseWindow in repository.state.value.settings.overrides)
        assertTrue(repository.state.value.settings.bindings(ShortcutCommand.CloseWindow, platform).isEmpty())
        val reopened = ShortcutsRepository(store, platform).also { it.load() }
        assertEquals(repository.state.value.settings, reopened.state.value.settings)
        assertTrue(repository.reset(ShortcutCommand.CloseWindow))
        assertFalse(ShortcutCommand.CloseWindow in repository.state.value.settings.overrides)
        assertEquals(ShortcutCommand.CloseWindow, repository.commandFor(defaults.single()))
        assertNull(repository.commandFor(custom))
    }

    @Test
    fun recordingRejectsModifierOnlyReservedDuplicateAndConflictingChords() = runBlocking {
        val repository = ShortcutsRepository(platform = platform).also { it.load() }
        repository.beginRecording(ShortcutCommand.ManageTasks)
        repository.recordModifierOnly()
        assertNull(repository.state.value.editor?.chord)
        assertNotNull(repository.state.value.editor?.error)
        listOf(ShortcutChord(ShortcutKey.K), ShortcutChord(ShortcutKey.F, meta = true),
            ShortcutChord(ShortcutKey.B, meta = true), ShortcutChord(ShortcutKey.Escape, meta = true),
            ShortcutRegistry.defaults(ShortcutCommand.Settings, platform).single()).forEach { chord ->
            repository.record(chord)
            assertNotNull(repository.state.value.editor?.error)
            assertEquals(ShortcutCaptureMode.Recording, repository.state.value.capture)
            assertFalse(repository.saveEditor())
        }
        repository.record(custom)
        assertNull(repository.state.value.editor?.error)
        assertEquals(ShortcutCaptureMode.None, repository.state.value.capture)
        assertNull(repository.commandFor(custom))
        assertTrue(repository.saveEditor())
        repository.beginRecording(ShortcutCommand.ManageTasks)
        repository.record(custom)
        assertNotNull(repository.state.value.editor?.error)
        repository.cancelCapture()
        repository.requestReset()
        assertTrue(repository.resetAll())
        assertNull(repository.commandFor(custom))
    }

    @Test
    fun failedSaveRetainsEffectiveBindingsEditorAndDraftUntilRetrySucceeds() = runBlocking {
        var fail = true
        var stored: ShortcutsSettings? = null
        val store = object : ShortcutsStore {
            override val durable = true
            override suspend fun read() = stored
            override suspend fun write(settings: ShortcutsSettings) {
                if (fail) error("/private/sensitive-path: disk full")
                stored = settings
            }
        }
        val repository = ShortcutsRepository(store, platform).also { it.load() }
        val previous = repository.state.value.settings
        repository.beginRecording(ShortcutCommand.CloseWindow, 0)
        repository.record(custom)
        assertFalse(repository.saveEditor())
        assertEquals(previous, repository.state.value.settings)
        assertEquals(custom, repository.state.value.editor?.chord)
        assertNotNull(repository.state.value.draft)
        assertFalse(repository.state.value.saveError!!.contains("sensitive-path"))
        assertNull(repository.commandFor(custom))
        fail = false
        repository.retry()
        assertEquals(ShortcutCommand.CloseWindow, repository.commandFor(custom))
        assertNull(repository.state.value.editor)
        assertNull(repository.state.value.draft)
        assertEquals(1L, repository.state.value.saveRevision)
        assertEquals(repository.state.value.settings, ShortcutsRepository(store, platform).also { it.load() }.state.value.settings)
    }

    @Test
    fun concurrentSubmitIsRejectedAndPublicationWaitsForSuccessfulStore() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var writes = 0
        val repository = ShortcutsRepository(object : ShortcutsStore {
            override val durable = true
            override suspend fun read(): ShortcutsSettings? = null
            override suspend fun write(settings: ShortcutsSettings) {
                writes++
                started.complete(Unit)
                release.await()
            }
        }, platform).also { it.load() }
        val candidate = ShortcutsSettings().withBindings(ShortcutCommand.ManageTasks, listOf(custom))
        repository.beginRecording(ShortcutCommand.ManageTasks)
        repository.record(custom)
        val first = async { repository.saveEditor() }
        started.await()
        try {
        assertTrue(repository.state.value.saving)
        assertNull(repository.commandFor(custom))
        val savingState = repository.state.value
        repository.beginRecording(ShortcutCommand.ManageTasks)
        repository.startKeySearch()
        repository.stopKeySearch()
        repository.requestReset()
        repository.cancelReset()
        repository.cancelCapture()
        repository.record(custom)
        repository.recordModifierOnly()
        assertFalse(repository.remove(ShortcutCommand.CloseWindow, 0))
        assertFalse(repository.reset(ShortcutCommand.CloseWindow))
        assertFalse(repository.resetAll())
        assertFalse(repository.saveEditor())
        repository.retry()
        assertEquals(savingState, repository.state.value)
        assertEquals(1, writes)
        assertFalse(repository.save(ShortcutsSettings()))
        assertEquals(candidate, repository.state.value.draft)
        } finally { release.complete(Unit) }
        assertTrue(first.await())
        assertEquals(1, writes)
        assertEquals(ShortcutCommand.ManageTasks, repository.commandFor(custom))
    }

    @Test
    fun cancellationDuringCommitStillPublishesExactlyThePersistedMapping() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var stored: ShortcutsSettings? = null
        val repository = ShortcutsRepository(object : ShortcutsStore {
            override val durable = true
            override suspend fun read() = stored
            override suspend fun write(settings: ShortcutsSettings) {
                started.complete(Unit)
                release.await()
                stored = settings
            }
        }, platform).also { it.load() }
        val candidate = ShortcutsSettings().withBindings(ShortcutCommand.ManageTasks, listOf(custom))
        val saving = launch { repository.save(candidate) }
        started.await()
        saving.cancel()
        release.complete(Unit)
        saving.join()
        assertTrue(saving.isCancelled)
        assertEquals(candidate, stored)
        assertEquals(candidate, repository.state.value.settings)
        assertFalse(repository.state.value.saving)
        assertNull(repository.state.value.draft)
        assertEquals(ShortcutCommand.ManageTasks, repository.commandFor(custom))
    }

    @Test
    fun invalidLoadAndUnavailableAdapterNeverWrite() = runBlocking {
        var writes = 0
        val invalids = listOf(ShortcutsSettings(schemaVersion = 2),
            ShortcutsSettings().withBindings(ShortcutCommand.ManageTasks, listOf(ShortcutChord(ShortcutKey.K))),
            ShortcutsSettings().withBindings(ShortcutCommand.ManageTasks, ShortcutRegistry.defaults(ShortcutCommand.Settings, platform)))
        invalids.forEach { invalid ->
            val repository = ShortcutsRepository(object : ShortcutsStore {
                override val durable = true
                override suspend fun read() = invalid
                override suspend fun write(settings: ShortcutsSettings) { writes++ }
            }, platform)
            repository.load()
            assertNotNull(repository.state.value.loadError)
            assertFalse(repository.state.value.canEdit)
            assertFalse(repository.save(ShortcutsSettings()))
            assertNull(repository.commandFor(custom))
        }
        val unavailable = ShortcutsRepository().also { it.load() }
        assertFalse(unavailable.state.value.canEdit)
        assertFalse(unavailable.save(ShortcutsSettings()))
        assertEquals(0, writes)
    }

    @Test
    fun cancellationPropagatesAndDoesNotBreakLaterRetry() = runBlocking {
        var cancelRead = true
        var cancelWrite = true
        val repository = ShortcutsRepository(object : ShortcutsStore {
            override val durable = false
            override suspend fun read(): ShortcutsSettings? {
                if (cancelRead) throw CancellationException("read")
                return null
            }
            override suspend fun write(settings: ShortcutsSettings) {
                if (cancelWrite) throw CancellationException("write")
            }
        }, platform)
        assertFailsWith<CancellationException> { repository.load() }
        assertFalse(repository.state.value.loading)
        assertNull(repository.state.value.loadError)
        cancelRead = false
        repository.retry()
        val candidate = ShortcutsSettings().withBindings(ShortcutCommand.ManageTasks, listOf(custom))
        assertFailsWith<CancellationException> { repository.save(candidate) }
        assertFalse(repository.state.value.saving)
        assertEquals(candidate, repository.state.value.draft)
        assertNull(repository.commandFor(custom))
        cancelWrite = false
        repository.retry()
        assertEquals(ShortcutCommand.ManageTasks, repository.commandFor(custom))
    }
}
