package im.hikaru.harness.client.settings

import kotlinx.coroutines.*
import kotlin.test.*

class ModelSettingsRepositoryTest {
    private class Backend : ModelSettingsBackend {
        var snapshot = ModelSettingsSnapshot(protocols = listOf(ModelProtocol("openai-chat-completions", "Chat")), available = true, writable = true, canAddProvider = true)
        var failRead = false
        var failWrite = false
        var failAfterCommit = false
        var writes = 0
        var keyValue: String? = null
        override suspend fun read(): ModelSettingsSnapshot { if (failRead) error("read failed"); return snapshot }
        override suspend fun save(configuration: ProviderConfiguration, expectedRevision: Long) {
            if (expectedRevision != snapshot.revision) throw ProviderRevisionConflict()
            if (failWrite) error("disk failed")
            writes++
            snapshot = snapshot.copy(revision = snapshot.revision + 1, providers = listOf(ConfigurableProvider(configuration.id, configuration.displayName, configuration, canRemove = true)))
            if (failAfterCommit) failRead = true
        }
        override suspend fun remove(providerId: String, expectedRevision: Long) {
            if (expectedRevision != snapshot.revision) throw ProviderRevisionConflict()
            writes++; snapshot = snapshot.copy(revision = snapshot.revision + 1, providers = emptyList())
        }
        override suspend fun setCredential(name: String, value: String) { if (failWrite) error("rejected value " + value); keyValue = value }
    }

    @Test
    fun unavailableBackendHasNoInventedProvidersOrWritableActions() = runBlocking {
        val repository = ModelSettingsRepository()
        repository.load()
        assertTrue(repository.state.value.loaded)
        assertFalse(repository.state.value.canEdit)
        assertTrue(repository.state.value.snapshot.providers.isEmpty())
        assertFalse(repository.save(ProviderConfiguration("openai")))
    }

    @Test
    fun failedWriteRetainsStateAndSuccessfulWritePublishesTheReadback() = runBlocking {
        val backend = Backend()
        val repository = ModelSettingsRepository(backend)
        repository.load()
        val before = repository.state.value.snapshot
        val candidate = ProviderConfiguration("custom", baseUrl = "https://example.test/v1", credentialName = "CUSTOM_KEY")
        backend.failWrite = true
        assertFalse(repository.save(candidate))
        assertEquals(before, repository.state.value.snapshot)
        assertNull(repository.state.value.confirmedProviderConfiguration)
        assertEquals(0, repository.state.value.saveRevision)
        backend.failWrite = false
        assertTrue(repository.save(candidate))
        assertEquals(candidate, repository.state.value.snapshot.providers.single().configuration)
        assertEquals(candidate, repository.state.value.confirmedProviderConfiguration)
        assertEquals("provider:custom", repository.state.value.completedOperation)
        assertNull(repository.state.value.saveError)
    }

    @Test
    fun commitFollowedByReadFailureRequiresRefreshInsteadOfRepeatingTheWrite() = runBlocking {
        val backend = Backend()
        val repository = ModelSettingsRepository(backend)
        repository.load()
        backend.failAfterCommit = true
        val candidate = ProviderConfiguration("custom", credentialName = "CUSTOM_KEY")
        assertFalse(repository.save(candidate))
        assertTrue(repository.state.value.refreshRequired)
        assertEquals(candidate, repository.state.value.confirmedProviderConfiguration)
        assertEquals(1, repository.state.value.saveRevision)
        assertTrue(repository.state.value.saveError!!.startsWith("已保存"))
        assertFalse(repository.save(candidate))
        assertEquals(1, backend.writes)
        backend.failRead = false
        repository.load()
        assertEquals(candidate, repository.state.value.snapshot.providers.single().configuration)
        assertTrue(repository.state.value.canEdit)
        assertEquals(1, backend.writes)
    }

    @Test
    fun conflictNeverOverwritesTheNewerHostState() = runBlocking {
        val backend = Backend()
        val repository = ModelSettingsRepository(backend)
        repository.load()
        backend.snapshot = backend.snapshot.copy(revision = 9)
        assertFalse(repository.save(ProviderConfiguration("custom", credentialName = "CUSTOM_KEY"), 0))
        assertEquals(0, backend.writes)
        assertTrue(repository.state.value.refreshRequired)
        repository.load()
        assertEquals(9, repository.state.value.snapshot.revision)
        assertFalse(repository.save(ProviderConfiguration("custom", credentialName = "CUSTOM_KEY"), 0))
        repository.load()
        assertTrue(repository.save(ProviderConfiguration("custom", credentialName = "CUSTOM_KEY"), 9))
    }

    @Test
    fun credentialValueNeverEntersPublicStateOrErrorDetails() = runBlocking {
        val backend = Backend()
        val repository = ModelSettingsRepository(backend)
        repository.load()
        val syntheticValue = "fixture-private-value"
        backend.failWrite = true
        assertFalse(repository.setCredential("CUSTOM_KEY", syntheticValue))
        assertFalse(repository.state.value.toString().contains(syntheticValue))
        backend.failWrite = false
        assertTrue(repository.setCredential("CUSTOM_KEY", syntheticValue))
        assertEquals(syntheticValue, backend.keyValue)
        assertFalse(repository.state.value.toString().contains(syntheticValue))
        assertEquals("credential:CUSTOM_KEY", repository.state.value.completedOperation)
    }

    @Test
    fun duplicateSavesAreBlockedWhileTheFirstIsInFlight() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val backend = Backend()
        val delayed = object : ModelSettingsBackend by backend {
            override suspend fun save(configuration: ProviderConfiguration, expectedRevision: Long) {
                entered.complete(Unit); release.await(); backend.save(configuration, expectedRevision)
            }
        }
        val repository = ModelSettingsRepository(delayed)
        repository.load()
        val candidate = ProviderConfiguration("custom", credentialName = "CUSTOM_KEY")
        val first = async { repository.save(candidate) }
        entered.await()
        assertFalse(repository.save(candidate))
        release.complete(Unit)
        assertTrue(first.await())
        assertEquals(1, backend.writes)
    }

    @Test
    fun cancelledInitialReadBlocksWritesUntilRetryAndWriteCancellationPropagates() = runBlocking {
        val backend = Backend()
        var cancelRead = true
        val cancellable = object : ModelSettingsBackend by backend {
            override suspend fun read(): ModelSettingsSnapshot { if (cancelRead) throw CancellationException(); return backend.read() }
            override suspend fun save(configuration: ProviderConfiguration, expectedRevision: Long) { throw CancellationException() }
        }
        val repository = ModelSettingsRepository(cancellable)
        assertFailsWith<CancellationException> { repository.load() }
        assertFalse(repository.state.value.canEdit)
        cancelRead = false
        repository.load()
        assertFailsWith<CancellationException> { repository.save(ProviderConfiguration("custom", credentialName = "CUSTOM_KEY")) }
        assertFalse(repository.state.value.saving)
        assertNull(repository.state.value.completedOperation)
    }

    @Test
    fun providerValidationRejectsInvalidNumbersReferencesUrlsAndDuplicateModels() {
        ProviderConfiguration("custom", baseUrl = "http://localhost:8080/v1", credentialName = "CUSTOM_KEY", modelIds = listOf("model-a", "model-b")).validate()
        listOf("file:///tmp/models", "https://user:pass@example.test", "https://example.test?key=value", "https://white space.test").forEach { url ->
            assertFailsWith<IllegalArgumentException> { ProviderConfiguration("custom", baseUrl = url, credentialName = "CUSTOM_KEY").validate() }
        }
        assertFailsWith<IllegalArgumentException> { ProviderConfiguration("custom", credentialName = "invalid-name").validate() }
        assertFailsWith<IllegalArgumentException> { ProviderConfiguration("custom").validate() }
        assertFailsWith<IllegalArgumentException> { ProviderConfiguration("custom", credentialName = " ").validate() }
        assertFailsWith<IllegalArgumentException> { ProviderConfiguration("custom", credentialName = "CUSTOM_KEY", requestTimeoutMillis = 0).validate() }
        assertFailsWith<IllegalArgumentException> { ProviderConfiguration("custom", credentialName = "CUSTOM_KEY", modelIds = listOf("same", "same")).validate() }
    }
}
