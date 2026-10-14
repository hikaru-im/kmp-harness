package im.hikaru.harness.client.settings

import kotlinx.coroutines.runBlocking
import kotlin.test.*

class WorktreeSettingsRepositoryTest {
    private class FakeStore(
        var value: WorktreeSettings? = null,
        override val durable: Boolean = true,
    ) : WorktreeSettingsStore {
        var failRead = false
        var failWrite = false
        val writes = mutableListOf<WorktreeSettings>()
        override suspend fun read(): WorktreeSettings? {
            if (failRead) throw IllegalStateException("read failed")
            return value
        }
        override suspend fun write(settings: WorktreeSettings) {
            if (failWrite) throw IllegalStateException("write failed")
            value = settings
            writes += settings
        }
    }

    @Test
    fun durableStoreLoadsAndSavesWithRevision() = runBlocking {
        val store = FakeStore(WorktreeSettings("/tmp/managed"))
        val repository = WorktreeSettingsRepository(store)
        repository.load()
        assertTrue(repository.state.value.loaded)
        assertTrue(repository.state.value.canEdit)
        assertEquals("/tmp/managed", repository.state.value.settings.root)

        assertTrue(repository.save(WorktreeSettings("/tmp/next")))
        assertEquals("/tmp/next", store.value?.root)
        assertEquals("/tmp/next", repository.state.value.settings.root)
        assertEquals(1, repository.state.value.saveRevision)
        assertEquals(1, store.writes.size)
        assertNull(repository.state.value.saveError)
    }

    @Test
    fun memoryStoreIsReadOnly() = runBlocking {
        val store = MemoryWorktreeSettingsStore()
        val repository = WorktreeSettingsRepository(store)
        repository.load()
        assertFalse(repository.state.value.canEdit)
        assertFalse(repository.save(WorktreeSettings("/tmp/x")))
        assertNull(store.read())
    }

    @Test
    fun readFailureSurfacesLoadErrorAndDisablesEdit() = runBlocking {
        val store = FakeStore().apply { failRead = true }
        val repository = WorktreeSettingsRepository(store)
        repository.load()
        assertNotNull(repository.state.value.loadError)
        assertFalse(repository.state.value.loaded)
        assertFalse(repository.state.value.canEdit)
        assertFalse(repository.save(WorktreeSettings("/tmp/x")))
    }

    @Test
    fun writeFailureKeepsLoadedValueAndRetryResaves() = runBlocking {
        val store = FakeStore(WorktreeSettings("/tmp/managed"))
        val repository = WorktreeSettingsRepository(store)
        repository.load()
        store.failWrite = true
        assertFalse(repository.save(WorktreeSettings("/tmp/broken")))
        assertNotNull(repository.state.value.saveError)
        assertEquals("/tmp/managed", repository.state.value.settings.root)

        store.failWrite = false
        repository.retry()
        assertEquals("/tmp/broken", store.value?.root)
        assertEquals(1, repository.state.value.saveRevision)
        assertNull(repository.state.value.saveError)
    }
}
