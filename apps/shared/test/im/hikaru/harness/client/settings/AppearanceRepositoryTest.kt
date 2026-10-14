package im.hikaru.harness.client.settings

import kotlinx.coroutines.*
import kotlin.test.*

class AppearanceRepositoryTest {
    @Test
    fun failedWritesPreserveThemeAndRetryPublishesOnlyAfterSuccess() = runBlocking {
        var fail = true
        var stored = AppearanceSettings(mode = AppearanceMode.Light)
        val store = object : AppearanceStore {
            override val durable = true
            override suspend fun read() = stored
            override suspend fun write(settings: AppearanceSettings) {
                if (fail) error("disk full")
                stored = settings
            }
        }
        val repository = AppearanceRepository(store)
        repository.load()
        val candidate = stored.copy(mode = AppearanceMode.Dark, codeFontSize = 18)
        assertFalse(repository.save(candidate))
        assertEquals(AppearanceMode.Light, repository.state.value.settings.mode)
        assertEquals("disk full", repository.state.value.saveError)
        assertEquals(0, repository.state.value.saveRevision)
        fail = false
        repository.retry()
        assertEquals(candidate, repository.state.value.settings)
        assertEquals(candidate, AppearanceRepository(store).also { it.load() }.state.value.settings)
        assertEquals(1, repository.state.value.saveRevision)
        assertNull(repository.state.value.saveError)
    }

    @Test
    fun corruptReadBlocksEditsUntilSuccessfulRetry() = runBlocking {
        var fail = true
        var writes = 0
        val store = object : AppearanceStore {
            override val durable = true
            override suspend fun read(): AppearanceSettings? {
                if (fail) error("corrupt document")
                return AppearanceSettings(mode = AppearanceMode.Dark)
            }
            override suspend fun write(settings: AppearanceSettings) { writes++ }
        }
        val repository = AppearanceRepository(store)
        repository.load()
        assertFalse(repository.state.value.canEdit)
        assertFalse(repository.save(AppearanceSettings()))
        assertEquals(0, writes)
        fail = false
        repository.retry()
        assertTrue(repository.state.value.canEdit)
        assertEquals(AppearanceMode.Dark, repository.state.value.settings.mode)
    }

    @Test
    fun duplicateSubmitIsIgnoredWhileWriteIsInFlight() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var writes = 0
        val store = object : AppearanceStore {
            override val durable = false
            override suspend fun read(): AppearanceSettings? = null
            override suspend fun write(settings: AppearanceSettings) { writes++; started.complete(Unit); release.await() }
        }
        val repository = AppearanceRepository(store)
        repository.load()
        val candidate = AppearanceSettings(mode = AppearanceMode.Dark)
        val first = async { repository.save(candidate) }
        started.await()
        assertTrue(repository.state.value.saving)
        assertEquals(AppearanceMode.System, repository.state.value.settings.mode)
        assertFalse(repository.save(candidate.copy(mode = AppearanceMode.Light)))
        release.complete(Unit)
        assertTrue(first.await())
        assertEquals(1, writes)
        assertEquals(candidate, repository.state.value.settings)
    }

    @Test
    fun cancellingWritePropagatesAndReleasesTheRepository() = runBlocking {
        var cancel = true
        val store = object : AppearanceStore {
            override val durable = false
            override suspend fun read(): AppearanceSettings? = null
            override suspend fun write(settings: AppearanceSettings) { if (cancel) throw CancellationException("cancel") }
        }
        val repository = AppearanceRepository(store)
        repository.load()
        assertFailsWith<CancellationException> { repository.save(AppearanceSettings(mode = AppearanceMode.Dark)) }
        assertFalse(repository.state.value.saving)
        assertNull(repository.state.value.saveError)
        assertEquals(AppearanceMode.System, repository.state.value.settings.mode)
        cancel = false
        assertTrue(repository.save(AppearanceSettings(mode = AppearanceMode.Dark)))
    }

    @Test
    fun cancellingLoadPropagatesWithoutAFalseError() = runBlocking {
        var cancel = true
        val store = object : AppearanceStore {
            override val durable = false
            override suspend fun read(): AppearanceSettings? {
                if (cancel) throw CancellationException("cancel")
                return AppearanceSettings(mode = AppearanceMode.Dark)
            }
            override suspend fun write(settings: AppearanceSettings) = Unit
        }
        val repository = AppearanceRepository(store)
        assertFailsWith<CancellationException> { repository.load() }
        assertFalse(repository.state.value.loading)
        assertNull(repository.state.value.loadError)
        assertFalse(repository.state.value.canEdit)
        assertFalse(repository.save(AppearanceSettings()))
        cancel = false
        repository.retry()
        assertTrue(repository.state.value.canEdit)
        assertEquals(AppearanceMode.Dark, repository.state.value.settings.mode)
    }

    @Test
    fun explicitLaunchModeOverridesStoredModeOnlyForTheInitialLoad() = runBlocking {
        val stored = AppearanceSettings(mode = AppearanceMode.Dark, codeFontSize = 17)
        val repository = AppearanceRepository(MemoryAppearanceStore(stored), launchMode = AppearanceMode.Light)
        repository.load()
        assertEquals(stored.copy(mode = AppearanceMode.Light), repository.state.value.settings)
        repository.load()
        assertEquals(stored, repository.state.value.settings)
    }

    @Test
    fun resetAdvancedKeepsVisualSettingsAndModesIndependent() {
        val light = AppearanceVariant(accent = "#123456", background = "#ffffff", uiFont = AppearanceFont.Serif, contrast = 90, codeFontStyle = AppearanceFontStyle.Bold)
        val original = AppearanceSettings(mode = AppearanceMode.Dark, light = light, dark = light.copy(accent = "#abcdef"), uiFontSize = 16, codeFontSize = 24)
        val reset = original.resetAdvanced()
        assertEquals(AppearanceMode.Dark, reset.mode)
        assertEquals(light.accent, reset.light.accent)
        assertEquals(light.background, reset.light.background)
        assertEquals(light.uiFont, reset.light.uiFont)
        assertEquals("#abcdef", reset.dark.accent)
        assertEquals(50, reset.light.contrast)
        assertEquals(AppearanceFontStyle.Regular, reset.light.codeFontStyle)
        assertEquals(14, reset.uiFontSize)
        assertEquals(12, reset.codeFontSize)
        assertEquals(original.dark, original.withVariant(AppearanceMode.Light, AppearanceVariant()).dark)
    }

    @Test
    fun shareRoundTripsAndRejectsUnsupportedOrInvalidDocuments() {
        val theme = AppearanceVariant(accent = "#abcdef", contentFont = AppearanceFont.Serif, codeFontStyle = AppearanceFontStyle.Italic)
        assertEquals(AppearanceThemeShare(AppearanceMode.Dark, theme), AppearanceThemeCodec.decode(AppearanceThemeCodec.encode(AppearanceMode.Dark, theme)))
        assertFailsWith<IllegalArgumentException> { AppearanceThemeCodec.decode("codex-theme-v1:unverified") }
        assertFailsWith<IllegalArgumentException> { AppearanceSettings(uiFontSize = 17).validate() }
        assertFailsWith<IllegalArgumentException> { AppearanceSettings(codeFontSize = 7).validate() }
        assertFailsWith<IllegalArgumentException> { AppearanceVariant(accent = "red").validate() }
        assertFailsWith<IllegalArgumentException> { AppearanceVariant(contrast = 101).validate() }
    }
}
