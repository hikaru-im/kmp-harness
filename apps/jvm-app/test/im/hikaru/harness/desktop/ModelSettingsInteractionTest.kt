package im.hikaru.harness.desktop

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import androidx.compose.ui.geometry.Size
import im.hikaru.harness.client.app.HarnessApp
import im.hikaru.harness.client.app.HarnessDestination
import im.hikaru.harness.client.page.SettingsModelsPage
import im.hikaru.harness.client.settings.*
import im.hikaru.harness.ui.foundation.*
import java.io.File
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.*
import org.jetbrains.skia.Image
import org.jetbrains.skia.EncodedImageFormat

/** Explicit test backend; production HarnessApp defaults to an unavailable backend. */
internal class ModelUiBackend : ModelSettingsBackend {
    var failWrite = false
    val keys = mutableMapOf<String, String>()
    var writes = 0
    var retries = 0
    var discoveries = 0
    var draftDiscoveries = 0
    var snapshot = ModelSettingsSnapshot(
        providers = listOf(ConfigurableProvider("openai", "OpenAI", ProviderConfiguration("openai", "OpenAI", credentialName = "MODEL_UI_TEST_KEY", modelIds = listOf("model-a", "model-b")),
            active = true, credentialWritable = true, canRemove = true,
            models = listOf(ProviderModel("model-a", "Fixture Model", contextWindow = 8192, maxTokens = 512, reasoningEfforts = listOf("low", "high"))))),
        protocols = listOf(ModelProtocol("openai-chat-completions", "Chat Completions"), ModelProtocol("openai-responses", "Responses")),
        available = true, writable = true, canAddProvider = true, unavailableReason = null,
    )
    override suspend fun setModelEnabled(providerId: String, modelId: String, enabled: Boolean, expectedRevision: Long) {
        check(expectedRevision == snapshot.revision)
        snapshot = snapshot.copy(revision = snapshot.revision + 1, providers = snapshot.providers.map {
            if (it.id != providerId) it else it.copy(disabledModelIds = if (enabled) it.disabledModelIds - modelId else it.disabledModelIds + modelId,
                selectedModelId = if (!enabled && it.selectedModelId == modelId) null else it.selectedModelId)
        })
    }
    override suspend fun selectModel(providerId: String, modelId: String, expectedRevision: Long) {
        check(expectedRevision == snapshot.revision)
        snapshot = snapshot.copy(revision = snapshot.revision + 1, providers = snapshot.providers.map {
            it.copy(selectedModelId = modelId.takeIf { _ -> it.id == providerId })
        })
    }
    override suspend fun read(): ModelSettingsSnapshot = snapshot
    override suspend fun save(configuration: ProviderConfiguration, expectedRevision: Long) {
        if (expectedRevision != snapshot.revision) throw ProviderRevisionConflict()
        if (failWrite) error("write failed")
        writes++
        val old = snapshot.providers.firstOrNull { it.id == configuration.id } ?: ConfigurableProvider(configuration.id, configuration.displayName, credentialWritable = true)
        snapshot = snapshot.copy(revision = snapshot.revision + 1, providers = snapshot.providers.filterNot { it.id == configuration.id } + old.copy(displayName = configuration.displayName, configuration = configuration, canRemove = true))
    }
    override suspend fun remove(providerId: String, expectedRevision: Long) {
        if (expectedRevision != snapshot.revision) throw ProviderRevisionConflict()
        if (failWrite) error("write failed")
        writes++; snapshot = snapshot.copy(revision = snapshot.revision + 1, providers = snapshot.providers.map { if (it.id == providerId) it.copy(configuration = null, active = false, canRemove = false, models = emptyList()) else it })
    }
    override suspend fun setCredential(name: String, value: String) {
        if (failWrite) error("invalid value " + value)
        keys[name] = value
        snapshot = snapshot.copy(providers = snapshot.providers.map { if (it.configuration?.credentialName == name) it.copy(credentialConfigured = true) else it })
    }
    override suspend fun retryApplication() {
        retries++
        snapshot = snapshot.copy(providers = snapshot.providers.map { it.copy(applicationError = null, active = true) })
    }
    override suspend fun discoverModels(providerId: String, expectedRevision: Long) {
        discoveries++
        snapshot = snapshot.copy(providers = snapshot.providers.map {
            if (it.id == providerId) it.copy(models = listOf(ProviderModel("remote-model", "Remote Model"))) else it
        })
    }

    override suspend fun discoverDraft(configuration: ProviderConfiguration, apiKey: String): ModelDiscoveryResult {
        draftDiscoveries++
        assertEquals("fixture-plugin-key", apiKey)
        assertEquals("fixture-vendor", configuration.vendor)
        return ModelDiscoveryResult(listOf(ProviderModel("remote-chat", "Discovered Chat")), "fixture-catalog", true)
    }
}

@OptIn(ExperimentalTestApi::class)
class ModelSettingsInteractionTest {
    @Test
    fun switchDoesNotSelectRowAndDisablingSelectedModelClearsDefault() = runComposeUiTest {
        val backend = ModelUiBackend()
        backend.snapshot = backend.snapshot.copy(providers = backend.snapshot.providers.map {
            it.copy(models = listOf(ProviderModel("model-a", "GPT-6 Astra"), ProviderModel("model-b", "Gemini4")))
        })
        val repository = loaded(backend)
        setContent { OwnedModels(repository) }
        onNodeWithTag("models-select-openai-model-a").performScrollTo().performClick()
        onNodeWithTag("models-select-openai-model-a").assertIsSelected()
        onNodeWithTag("models-select-openai-model-b").performScrollTo()
        captureDialog(onRoot().captureToImage(), "model-management-selected.png")
        onNodeWithTag("models-enabled-openai-model-a").performScrollTo().performClick()
        onNodeWithTag("models-enabled-openai-model-a").assertIsOff()
        onNodeWithTag("models-select-openai-model-a").assertIsNotSelected()
        onNodeWithTag("models-enabled-openai-model-a").performClick()
        onNodeWithTag("models-enabled-openai-model-a").assertIsOn()
        onNodeWithTag("models-select-openai-model-a").assertIsNotSelected()
        runOnIdle { assertNull(backend.snapshot.providers.single().selectedModelId) }
    }

    @Test
    fun pluginKeyDiscoverySavesWholeCatalogWithoutModelSelection() = runComposeUiTest {
        val backend = pluginBackend()
        val repository = loaded(backend)
        setContent { OwnedModels(repository) }
        onNodeWithTag("models-new-provider").performClick()
        onNodeWithTag("provider-editor-id").assertDoesNotExist()
        onNodeWithTag("provider-editor-api-key").performScrollTo().performTextReplacement("fixture-plugin-key")
        onNodeWithTag("provider-editor-discover").performScrollTo().performClick()
        runOnIdle { assertEquals(1, backend.draftDiscoveries); assertEquals(0, backend.writes); assertTrue(backend.keys.isEmpty()) }
        onNodeWithText("Discovered Chat").assertExists()
        onNodeWithTag("provider-editor-save").performClick()
        runOnIdle {
            val saved = requireNotNull(repository.state.value.confirmedProviderConfiguration)
            assertEquals("fixture-vendor", saved.vendor)
            assertEquals("https://fixture.test/v1", saved.baseUrl)
            assertNull(saved.modelIds)
            assertEquals("fixture-plugin-key", backend.keys[saved.credentialName])
            assertFalse(repository.state.value.toString().contains("fixture-plugin-key"))
            assertEquals(1, backend.writes)
        }
        onNodeWithTag("provider-editor-save").assertDoesNotExist()
    }

    @Test
    fun changedConnectionCanBeSavedPendingDiscovery() = runComposeUiTest {
        val backend = pluginBackend()
        val repository = loaded(backend)
        setContent { OwnedModels(repository) }
        onNodeWithTag("models-new-provider").performClick()
        onNodeWithTag("provider-editor-api-key").performScrollTo().performTextReplacement("fixture-plugin-key")
        onNodeWithTag("provider-editor-discover").performScrollTo().performClick()
        onNodeWithText("Discovered Chat").assertExists()
        onNodeWithTag("provider-editor-advanced").performScrollTo().performClick()
        onNodeWithTag("provider-editor-base-url").performScrollTo().performTextReplacement("https://second.test/v1")
        onNodeWithTag("provider-editor-discovered-model").assertDoesNotExist()
        onNodeWithTag("provider-editor-save").performClick()
        runOnIdle { assertEquals(1, backend.writes); assertNull(repository.state.value.confirmedProviderConfiguration?.modelIds) }
    }

    private fun pluginBackend(): ModelUiBackend = ModelUiBackend().also { backend ->
        backend.snapshot = backend.snapshot.copy(providers = emptyList(), plugins = listOf(ProviderPlugin(
            "fixture-vendor", "Fixture Vendor", listOf("openai-chat-completions"),
            endpoints = listOf(ProviderEndpointProfile("default", "Default", "https://fixture.test/v1", "openai-chat-completions")),
            supportsDiscovery = true, inferenceSupported = true,
        )))
    }

    @Test
    fun discoveryButtonLoadsServerModelsAndShowsFeedback() = runComposeUiTest {
        val backend = ModelUiBackend()
        backend.snapshot = backend.snapshot.copy(providers = backend.snapshot.providers.map { it.copy(credentialConfigured = true) })
        val repository = loaded(backend)
        setContent { OwnedModels(repository) }
        onNodeWithTag("models-discover-openai").performClick()
        runOnIdle { assertEquals(1, backend.discoveries) }
        onNodeWithText("模型目录已更新；来源与生效结果见下方状态。").assertExists()
        onNodeWithTag("models-view-openai").performClick()
        onAllNodesWithText("Remote Model").assertCountEquals(2)
    }

    @Test
    fun failedApplicationShowsOldRouteAndRetryIsDistinctFromRefresh() = runComposeUiTest {
        val backend = ModelUiBackend()
        backend.snapshot = backend.snapshot.copy(providers = backend.snapshot.providers.map {
            it.copy(applicationError = "引用的凭据不存在，请配置凭据后重试应用。")
        })
        val repository = loaded(backend)
        setContent { OwnedModels(repository) }
        onNodeWithText("新配置未生效，沿用上次配置").assertExists()
        onNodeWithTag("models-refresh").performClick()
        runOnIdle { assertEquals(0, backend.retries) }
        onNodeWithTag("models-retry-openai").performClick()
        onNodeWithText("已生效").assertExists()
        onNodeWithTag("models-retry-openai").assertDoesNotExist()
        runOnIdle { assertEquals(1, backend.retries) }
    }

    @Test
    fun missingReferenceCannotBeSavedAndCredentialEditorCannotRenameBinding() = runComposeUiTest {
        val backend = ModelUiBackend()
        val repository = loaded(backend)
        setContent { OwnedModels(repository) }
        onNodeWithTag("models-edit-openai").performClick()
        onNodeWithTag("provider-editor-credential").performScrollTo().performTextReplacement("")
        onNodeWithTag("provider-editor-save").performClick()
        onNodeWithText("请填写凭据引用名称").assertExists()
        runOnIdle { assertEquals(0, backend.writes) }
        onNodeWithText("取消").performClick()
        onNodeWithTag("models-credential-openai").performClick()
        onNodeWithText("凭据引用：MODEL_UI_TEST_KEY（如需更换，请编辑 Provider）").assertExists()
        onNodeWithTag("credential-editor-name").assertDoesNotExist()
    }

    @Test
    fun routeShowsOwnerDataAndSearchFiltersIt() = runComposeUiTest {
        val repository = loaded(ModelUiBackend())
        setContent { HarnessApp(null, initialDestination = HarnessDestination.Settings, modelSettingsRepository = repository, themeMode = HarnessThemeMode.Light) }
        onNodeWithTag("settings-row-models").performClick()
        onNodeWithTag("settings-models-title").assertIsDisplayed()
        onNodeWithTag("models-provider-openai").assertExists()
        onNodeWithTag("models-search").performTextInput("missing")
        onNodeWithTag("models-empty").performScrollTo().assertIsDisplayed()
        onNodeWithTag("models-search").performTextClearance()
        onNodeWithTag("models-provider-openai").assertExists()
        onNodeWithTag("settings-row-general").performClick()
        onNodeWithTag("settings-row-models").performClick()
        onNodeWithTag("models-provider-openai").assertExists()
    }

    @Test
    fun invalidDraftAndWriteFailureStayOpenThenSuccessfulSaveCloses() = runComposeUiTest {
        val backend = ModelUiBackend()
        val repository = loaded(backend)
        setContent { OwnedModels(repository) }
        onNodeWithTag("models-edit-openai").performClick()
        onNodeWithTag("provider-editor-name").performTextReplacement("Edited provider")
        onNodeWithTag("provider-editor-request-timeout").performScrollTo().performTextReplacement("-1")
        onNodeWithTag("provider-editor-save").performClick()
        onNodeWithTag("provider-editor-error").assertIsDisplayed()
        runOnIdle { assertEquals(0, backend.writes) }
        onNodeWithTag("provider-editor-request-timeout").performTextReplacement("5000")
        onNodeWithTag("provider-editor-models").performScrollTo().assertTextEquals("model-a\nmodel-b")
        runOnIdle { backend.failWrite = true }
        onNodeWithTag("provider-editor-save").performClick()
        onNodeWithTag("provider-editor-name").assertExists()
        runOnIdle { assertEquals("OpenAI", repository.state.value.snapshot.providers.single().displayName); backend.failWrite = false }
        onNodeWithTag("provider-editor-save").performClick()
        onNodeWithTag("provider-editor-name").assertDoesNotExist()
        runOnIdle {
            val actual = repository.state.value.snapshot.providers.single().configuration!!
            assertEquals("Edited provider", actual.displayName)
            assertEquals(5000, actual.requestTimeoutMillis)
            assertEquals(listOf("model-a", "model-b"), actual.modelIds)
        }
    }

    @Test
    fun conflictRequiresExplicitReloadAndKeepsTheUnsavedDraft() = runComposeUiTest {
        val backend = ModelUiBackend()
        val repository = loaded(backend)
        setContent { OwnedModels(repository) }
        onNodeWithTag("models-edit-openai").performClick()
        onNodeWithTag("provider-editor-name").performTextReplacement("Unsaved draft")
        runOnIdle { backend.snapshot = backend.snapshot.copy(revision = 1, providers = backend.snapshot.providers.map { it.copy(displayName = "External name", configuration = it.configuration!!.copy(displayName = "External name")) }) }
        onNodeWithTag("provider-editor-save").performClick()
        onNodeWithTag("provider-editor-error").assertIsDisplayed()
        onNodeWithTag("provider-editor-refresh").performClick()
        onNodeWithTag("provider-editor-name").assertTextEquals("Unsaved draft")
        onNodeWithTag("provider-editor-reload").performClick()
        onNodeWithTag("provider-editor-name").assertTextEquals("External name")
        onNodeWithTag("provider-editor-name").performTextReplacement("Reviewed name")
        onNodeWithTag("provider-editor-save").performClick()
        onNodeWithTag("provider-editor-name").assertDoesNotExist()
        runOnIdle { assertEquals("Reviewed name", repository.state.value.snapshot.providers.single().displayName) }
    }

    @Test
    fun credentialFailureIsSanitizedAndOnlyConfirmedSuccessCloses() = runComposeUiTest {
        val backend = ModelUiBackend()
        val repository = loaded(backend)
        setContent { OwnedModels(repository) }
        onNodeWithTag("models-credential-openai").performClick()
        val syntheticValue = "fixture-password-value"
        onNodeWithTag("credential-editor-value").performTextReplacement(syntheticValue)
        runOnIdle { backend.failWrite = true }
        onNodeWithTag("credential-editor-save").performClick()
        onNodeWithTag("credential-editor-error").assertIsDisplayed()
        runOnIdle { assertFalse(repository.state.value.toString().contains(syntheticValue)); backend.failWrite = false }
        onNodeWithTag("credential-editor-save").performClick()
        onNodeWithTag("credential-editor-value").assertDoesNotExist()
        runOnIdle { assertEquals(syntheticValue, backend.keys["MODEL_UI_TEST_KEY"]); assertFalse(repository.state.value.toString().contains(syntheticValue)) }
    }

    @Test
    fun removeConfirmsTheWriteAndRetainsTheDeclaredProviderEntry() = runComposeUiTest {
        val backend = ModelUiBackend()
        val repository = loaded(backend)
        setContent { OwnedModels(repository) }
        onNodeWithTag("models-remove-openai").performClick()
        onNodeWithTag("provider-delete-confirm").performClick()
        onNodeWithTag("provider-delete-confirm").assertDoesNotExist()
        onNodeWithTag("models-provider-openai").assertExists()
        onNodeWithTag("models-remove-openai").assertDoesNotExist()
        runOnIdle { assertNull(repository.state.value.snapshot.providers.single().configuration); assertEquals(1, backend.writes) }
    }

    @Test
    fun compactEditorAndModelDetailsAreReadableAndCaptureDialogs() {
        listOf("light" to HarnessThemeMode.Light, "dark" to HarnessThemeMode.Dark).forEach { (slug, mode) ->
            runSkikoComposeUiTest(size = Size(600f, 800f)) {
                val repository = loaded(ModelUiBackend())
                setContent { OwnedModels(repository, mode) }
                onNodeWithTag("models-view-openai").performScrollTo().performClick()
                onNodeWithText("上下文容量：8192").assertIsDisplayed()
                onNodeWithText("推理档位：low / high").assertIsDisplayed()
                captureDialog(onNode(isDialog()).captureToImage(), "models-detail-" + slug + ".png")
                onNodeWithText("关闭").performClick()
                onNodeWithTag("models-new-provider").performScrollTo().performClick()
                onNodeWithTag("provider-editor-id").assertIsDisplayed()
                captureDialog(onNode(isDialog()).captureToImage(), "models-editor-top-" + slug + ".png")
                onNodeWithTag("provider-editor-models").performScrollTo().assertIsDisplayed()
                onNodeWithTag("provider-editor-save").assertIsDisplayed()
                captureDialog(onNode(isDialog()).captureToImage(), "models-editor-" + slug + ".png")
                onNodeWithText("取消").performClick()
                onNodeWithTag("models-credential-openai").performScrollTo().performClick()
                onNodeWithTag("credential-editor-value").assertIsDisplayed()
                onNodeWithTag("credential-editor-save").assertIsDisplayed()
                captureDialog(onNode(isDialog()).captureToImage(), "models-credential-" + slug + ".png")
            }
        }
    }

    @Test
    fun deletionConflictRequiresReviewingTheNewConfiguration() = runComposeUiTest {
        val backend = ModelUiBackend()
        val repository = loaded(backend)
        setContent { OwnedModels(repository) }
        onNodeWithTag("models-remove-openai").performClick()
        runOnIdle { backend.snapshot = backend.snapshot.copy(revision = 1) }
        onNodeWithTag("provider-delete-confirm").performClick()
        onNodeWithTag("provider-delete-refresh").performClick()
        onNodeWithTag("provider-delete-confirm").assertIsNotEnabled()
        onNodeWithText("取消").performClick()
        onNodeWithTag("models-remove-openai").performClick()
        onNodeWithTag("provider-delete-confirm").performClick()
        runOnIdle { assertEquals(1, backend.writes); assertNull(repository.state.value.snapshot.providers.single().configuration) }
    }

    @Test
    fun unavailableHostDoesNotShowInventedProviderCards() = runComposeUiTest {
        val repository = ModelSettingsRepository()
        runBlocking { repository.load() }
        setContent { OwnedModels(repository) }
        onNodeWithTag("models-unavailable").assertIsDisplayed()
        onNodeWithTag("models-new-provider").assertIsNotEnabled()
        onNodeWithTag("models-provider-openai").assertDoesNotExist()
    }

    private fun loaded(backend: ModelSettingsBackend): ModelSettingsRepository = ModelSettingsRepository(backend).also { runBlocking { it.load() } }

    @Composable
    private fun OwnedModels(repository: ModelSettingsRepository, mode: HarnessThemeMode = HarnessThemeMode.Light) {
        val state by repository.state.collectAsState()
        val scope = rememberCoroutineScope()
        HarnessTheme(mode) {
            SettingsModelsPage(state, { scope.launch { repository.load() } },
                { config, revision -> scope.launch { repository.save(config, revision) } },
                { id, revision -> scope.launch { repository.remove(id, revision) } },
                { name, value -> scope.launch { repository.setCredential(name, value) } },
                { scope.launch { repository.retryApplication() } },
                { id, revision -> scope.launch { repository.discoverModels(id, revision) } },
                { config, key -> repository.discoverDraft(config, key) },
                { config, key, revision -> scope.launch { repository.saveWithCredential(config, key, revision) } },
                { provider, model, enabled, revision -> scope.launch { repository.setModelEnabled(provider, model, enabled, revision) } },
                { provider, model, revision -> scope.launch { repository.selectModel(provider, model, revision) } })
        }
    }

    private fun captureDialog(bitmap: androidx.compose.ui.graphics.ImageBitmap, name: String) {
        val root = File("build/test-captures/models").apply { mkdirs() }
        Image.makeFromBitmap(bitmap.asSkiaBitmap()).use { image -> File(root, name).writeBytes(requireNotNull(image.encodeToData(EncodedImageFormat.PNG)).bytes) }
    }
}
