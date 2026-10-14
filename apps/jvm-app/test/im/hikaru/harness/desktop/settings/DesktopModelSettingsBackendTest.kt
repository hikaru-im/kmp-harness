package im.hikaru.harness.desktop.settings

import im.hikaru.harness.client.settings.*
import im.hikaru.harness.credentials.*
import im.hikaru.harness.llm.LlmPlugin
import im.hikaru.harness.llm.llm
import im.hikaru.harness.llm.koog.openai.OpenAiKoogPlugin
import im.hikaru.harness.llm.koog.openai.catalog.OpenAiKoogCatalog
import im.hikaru.harness.runtime.Runtime
import im.hikaru.harness.settings.*
import im.hikaru.harness.settings.file.*
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import kotlin.test.*

class DesktopModelSettingsBackendTest {
    private fun credentials() = InMemoryCredentialProvider(mapOf(CredentialRef("MODEL_TEST_KEY") to "synthetic-test-value"))
    private suspend fun host(store: SettingsDocumentStore, credentials: CredentialProvider = credentials()): Runtime {
        val runtime = Runtime()
        try {
            runtime.install(SettingsPlugin(store))
            runtime.install(CredentialsPlugin(credentials))
            runtime.install(LlmPlugin())
            runtime.install(OpenAiKoogPlugin())
            return runtime
        } catch (error: Throwable) { runtime.context.dispose(); throw error }
    }

    @Test
    fun realFileAndHostCatalogRoundTripWithoutPuttingCredentialValuesInSettings() = runBlocking {
        val path = Files.createTempDirectory("harness-model-settings-").resolve("settings.json")
        val store = FileSettingsDocumentStore(SettingsFileConfig(path, watch = false))
        store.write(buildJsonObject { putJsonObject("unrelated") { put("keep", "yes") } })
        val runtime = host(store)
        val configured = ProviderConfiguration("custom", "Custom endpoint", baseUrl = "https://example.test/v1", credentialName = "MODEL_TEST_KEY")
        try {
            val backend = DesktopModelSettingsBackend(runtime.context)
            val before = backend.read()
            assertEquals(listOf("openai"), before.providers.map { it.id })
            assertNull(before.providers.single().configuration)
            assertFalse(before.providers.single().active)
            backend.save(configured, before.revision)
            val after = backend.read()
            val provider = after.providers.single { it.id == "custom" }
            assertEquals(configured, provider.configuration)
            assertTrue(provider.active)
            assertTrue(provider.models.isNotEmpty())
            assertTrue(provider.models.any { it.contextWindow != null })
            val text = Files.readString(path)
            assertFalse(text.contains("synthetic-test-value"))
            assertEquals("yes", Json.parseToJsonElement(text).jsonObject["unrelated"]!!.jsonObject["keep"]!!.jsonPrimitive.content)
        } finally { runtime.context.dispose() }
        val reopened = host(FileSettingsDocumentStore(SettingsFileConfig(path, watch = false)))
        try {
            val backend = DesktopModelSettingsBackend(reopened.context)
            val loaded = backend.read()
            assertEquals(configured, loaded.providers.single { it.id == "custom" }.configuration)
            backend.remove("custom", loaded.revision)
            assertTrue(backend.read().providers.none { it.id == "custom" })
            assertEquals(listOf("openai"), backend.read().providers.map { it.id })
        } finally { reopened.context.dispose() }
    }

    @Test
    fun editingKeepsOtherProvidersAndUnexposedModelCapabilities() = runBlocking {
        val runtime = host(InMemorySettingsDocumentStore())
        try {
            val modelId = OpenAiKoogCatalog.installedRoutes().first().models.first().model.id
            val section = buildJsonObject {
                putJsonObject("providers") {
                    putJsonObject("openai") {
                        put("api", "openai-chat-completions")
                        putJsonObject("credential") { put("name", "MODEL_TEST_KEY") }
                        put("models", buildJsonArray { add(buildJsonObject { put("id", modelId); put("name", "User model name"); put("maxTokens", 64); put("input", buildJsonArray { add("text") }) }) })
                        put("defaultInput", buildJsonArray { add("text") })
                    }
                    putJsonObject("other") { put("api", "openai-responses"); putJsonObject("credential") { put("name", "MODEL_TEST_KEY") } }
                }
            }
            val scope = runtime.context.require(SettingsKey).scope(settingsNamespace("llm-koog"))
            scope.replace(section)
            val backend = DesktopModelSettingsBackend(runtime.context)
            val loaded = backend.read()
            val original = loaded.providers.single { it.id == "openai" }.configuration!!
            backend.save(original.copy(displayName = "Edited name", baseUrl = "https://other.example.test/v1"), loaded.revision)
            val stored = scope.describe().user!!["providers"]!!.jsonObject
            assertEquals(section["providers"]!!.jsonObject["other"], stored["other"])
            val before = section["providers"]!!.jsonObject["openai"]!!.jsonObject
            val after = stored["openai"]!!.jsonObject
            listOf("models", "defaultInput").forEach { key -> assertEquals(before[key], after[key]) }
            val actualModel = backend.read().providers.single { it.id == "openai" }.models.single()
            assertEquals("User model name", actualModel.name)
            assertEquals(64, actualModel.maxTokens)
        } finally { runtime.context.dispose() }
    }

    @Test
    fun editingInstalledCatalogPreservesOverridesAndRejectsConflictingCustomModels() = runBlocking {
        val runtime = host(InMemorySettingsDocumentStore())
        try {
            val modelId = OpenAiKoogCatalog.installedRoutes().first().models.first().model.id
            val section = buildJsonObject {
                putJsonObject("providers") {
                    putJsonObject("openai") {
                        put("api", "openai-chat-completions")
                        putJsonObject("credential") { put("name", "MODEL_TEST_KEY") }
                        putJsonObject("modelOverrides") {
                            putJsonObject(modelId) {
                                put("description", "Keep this capability description")
                                put("maxTokens", 32)
                            }
                        }
                    }
                }
            }
            val scope = runtime.context.require(SettingsKey).scope(settingsNamespace("llm-koog"))
            scope.replace(section)
            val backend = DesktopModelSettingsBackend(runtime.context)
            val loaded = backend.read()
            val original = loaded.providers.single { it.id == "openai" }.configuration!!
            assertNull(original.modelIds)
            backend.save(original.copy(displayName = "Edited name"), loaded.revision)
            val stored = scope.describe().user!!
            assertEquals(section["providers"]!!.jsonObject["openai"]!!.jsonObject["modelOverrides"],
                stored["providers"]!!.jsonObject["openai"]!!.jsonObject["modelOverrides"])
            val edited = backend.read()
            val actualModel = edited.providers.single { it.id == "openai" }.models.single { it.id == modelId }
            assertEquals("Keep this capability description", actualModel.description)
            assertEquals(32, actualModel.maxTokens)
            assertFailsWith<IllegalArgumentException> {
                backend.save(original.copy(modelIds = listOf(modelId)), edited.revision)
            }
            assertEquals(stored, scope.describe().user)
        } finally { runtime.context.dispose() }
    }

    @Test
    fun staleRevisionCannotReplaceExternalEdits() = runBlocking {
        val runtime = host(InMemorySettingsDocumentStore())
        try {
            val backend = DesktopModelSettingsBackend(runtime.context)
            val first = backend.read()
            val newer = ProviderConfiguration("openai", "External edit", credentialName = "MODEL_TEST_KEY")
            backend.save(newer, first.revision)
            assertFailsWith<ProviderRevisionConflict> { backend.save(newer.copy(displayName = "Stale UI"), first.revision) }
            assertEquals("External edit", backend.read().providers.single { it.id == "openai" }.displayName)
        } finally { runtime.context.dispose() }
    }

    @Test
    fun settingAndUpdatingAnUnreferencedCredentialUsesTheActualCredentialService() = runBlocking {
        val credentialProvider = credentials()
        val runtime = host(InMemorySettingsDocumentStore(), credentialProvider)
        try {
            val backend = DesktopModelSettingsBackend(runtime.context)
            backend.setCredential("NEW_MODEL_KEY", "synthetic-new-value")
            assertTrue(credentialProvider.describe(CredentialRef("NEW_MODEL_KEY")).configured)
            assertEquals("synthetic-new-value", credentialProvider.resolve(CredentialRef("NEW_MODEL_KEY"))?.value)
            assertFalse(backend.read().toString().contains("synthetic-new-value"))
            assertFalse(runtime.context.require(SettingsKey).scope(settingsNamespace("llm-koog")).get().toString().contains("synthetic-new-value"))
        } finally { runtime.context.dispose() }
    }

    @Test
    fun readOnlyCredentialSourcesAndInvalidProtocolsDoNotWrite() = runBlocking {
        var writes = 0
        val readOnly = object : CredentialProvider by credentials() {
            override suspend fun describe(reference: CredentialRef) = CredentialInfo(false, writable = false)
            override suspend fun set(reference: CredentialRef, value: String) { writes++ }
        }
        val runtime = host(InMemorySettingsDocumentStore(), readOnly)
        try {
            val backend = DesktopModelSettingsBackend(runtime.context)
            assertFalse(backend.read().providers.single().credentialWritable)
            assertFailsWith<IllegalStateException> { backend.setCredential("MODEL_TEST_KEY", "synthetic-test-value") }
            assertEquals(0, writes)
            assertFailsWith<IllegalArgumentException> { backend.save(ProviderConfiguration("custom", api = "missing-protocol"), backend.read().revision) }
            assertNull(backend.read().providers.single().configuration)
        } finally { runtime.context.dispose() }
    }
}
