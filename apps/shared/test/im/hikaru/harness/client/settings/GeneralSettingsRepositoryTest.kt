package im.hikaru.harness.client.settings

import kotlinx.coroutines.*
import kotlin.test.*

class GeneralSettingsRepositoryTest {
    @Test
    fun failedWriteKeepsEffectivePolicyAndRetryPublishesPersistedValue() = runBlocking {
        var fail = true
        var stored = GeneralSettings(CloseConfirmationPolicy.Never)
        val store = object : GeneralSettingsStore {
            override val durable = true
            override suspend fun read() = stored
            override suspend fun write(settings: GeneralSettings) {
                if (fail) error("disk full")
                stored = settings
            }
        }
        val repository = GeneralSettingsRepository(store, closePolicyAvailable = true)
        repository.load()
        val candidate = GeneralSettings(CloseConfirmationPolicy.Always)
        assertFalse(repository.save(candidate))
        assertEquals(CloseConfirmationPolicy.Never, repository.state.value.settings.closeConfirmation)
        assertNotNull(repository.state.value.saveError)
        assertEquals(0, repository.state.value.saveRevision)
        fail = false
        repository.retry()
        assertEquals(candidate, repository.state.value.settings)
        assertEquals(candidate, GeneralSettingsRepository(store, true).also { it.load() }.state.value.settings)
        assertEquals(1, repository.state.value.saveRevision)
        assertNull(repository.state.value.saveError)
    }

    @Test
    fun loadFailurePreventsWritesButAnUnavailableClosePolicyStillAllowsEnterBehavior() = runBlocking {
        var fail = true
        var writes = 0
        var written: GeneralSettings? = null
        val store = object : GeneralSettingsStore {
            override val durable = true
            override suspend fun read(): GeneralSettings? {
                if (fail) error("bad json")
                return GeneralSettings(CloseConfirmationPolicy.Always)
            }
            override suspend fun write(settings: GeneralSettings) { writes++; written = settings }
        }
        val repository = GeneralSettingsRepository(store, true)
        repository.load()
        assertFalse(repository.state.value.canEdit)
        assertFalse(repository.save(GeneralSettings()))
        assertEquals(0, writes)
        fail = false
        repository.retry()
        assertTrue(repository.state.value.canEdit)
        assertEquals(CloseConfirmationPolicy.Always, repository.state.value.settings.closeConfirmation)

        // SET-10: enter behavior needs no platform adapter, so it stays editable...
        val unavailable = GeneralSettingsRepository(store)
        unavailable.load()
        assertTrue(unavailable.state.value.canEdit)
        // ...while the close policy row keeps its own availability gate.
        assertFalse(unavailable.state.value.closePolicyCanEdit)
        assertTrue(unavailable.save(GeneralSettings(CloseConfirmationPolicy.Never, EnterBehavior.CmdAlways)))
        assertEquals(1, writes)
        // A caller cannot smuggle a close-policy change through a document saved by this platform.
        assertEquals(GeneralSettings(CloseConfirmationPolicy.Always, EnterBehavior.CmdAlways), written)
        assertEquals(EnterBehavior.CmdAlways, unavailable.state.value.settings.enterBehavior)
        assertEquals(CloseConfirmationPolicy.Always, unavailable.state.value.settings.closeConfirmation)
    }

    @Test
    fun duplicateSubmitDoesNotChangeEffectivePolicyDuringWrite() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var writes = 0
        val repository = GeneralSettingsRepository(object : GeneralSettingsStore {
            override val durable = false
            override suspend fun read(): GeneralSettings? = null
            override suspend fun write(settings: GeneralSettings) {
                writes++
                started.complete(Unit)
                release.await()
            }
        }, true)
        repository.load()
        val candidate = GeneralSettings(CloseConfirmationPolicy.Always)
        val first = async { repository.save(candidate) }
        started.await()
        assertTrue(repository.state.value.saving)
        assertEquals(CloseConfirmationPolicy.KeyboardOnly, repository.state.value.settings.closeConfirmation)
        assertFalse(repository.save(GeneralSettings(CloseConfirmationPolicy.Never)))
        release.complete(Unit)
        assertTrue(first.await())
        assertEquals(1, writes)
        assertEquals(candidate, repository.state.value.settings)
    }

    @Test
    fun cancellingIoPropagatesAndRepositoryCanRecover() = runBlocking {
        var cancelRead = true
        var cancelWrite = true
        val repository = GeneralSettingsRepository(object : GeneralSettingsStore {
            override val durable = false
            override suspend fun read(): GeneralSettings? {
                if (cancelRead) throw CancellationException("read")
                return null
            }
            override suspend fun write(settings: GeneralSettings) {
                if (cancelWrite) throw CancellationException("write")
            }
        }, true)
        assertFailsWith<CancellationException> { repository.load() }
        assertFalse(repository.state.value.loading)
        assertNull(repository.state.value.loadError)
        assertFalse(repository.state.value.canEdit)
        cancelRead = false
        repository.retry()
        val candidate = GeneralSettings(CloseConfirmationPolicy.Never)
        assertFailsWith<CancellationException> { repository.save(candidate) }
        assertFalse(repository.state.value.saving)
        assertNull(repository.state.value.saveError)
        assertEquals(CloseConfirmationPolicy.KeyboardOnly, repository.state.value.settings.closeConfirmation)
        cancelWrite = false
        assertTrue(repository.save(candidate))
    }
}
