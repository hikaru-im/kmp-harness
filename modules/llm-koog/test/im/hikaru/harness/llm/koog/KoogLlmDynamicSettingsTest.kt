package im.hikaru.harness.llm.koog

import ai.koog.prompt.streaming.StreamFrame
import im.hikaru.harness.credentials.CredentialRef
import im.hikaru.harness.credentials.CredentialsPlugin
import im.hikaru.harness.credentials.InMemoryCredentialProvider
import im.hikaru.harness.llm.GenerateOptions
import im.hikaru.harness.llm.LlmCallConfig
import im.hikaru.harness.llm.LlmException
import im.hikaru.harness.llm.LlmPlugin
import im.hikaru.harness.llm.TextBlock
import im.hikaru.harness.llm.createUserMessage
import im.hikaru.harness.llm.llm
import im.hikaru.harness.runtime.Runtime
import im.hikaru.harness.settings.InMemorySettingsDocumentStore
import im.hikaru.harness.settings.SettingsKey
import im.hikaru.harness.settings.SettingsPlugin
import im.hikaru.harness.settings.settingsNamespace
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KoogLlmDynamicSettingsTest {
    @Test
    fun preparedCallShouldKeepItsGenerationWhileNewCallsUseReplacementCatalog() = runTest {
        val runtime = Runtime()
        val installedRoute = testKoogRoute()
        val settingsStore = InMemorySettingsDocumentStore()
        runtime.install(SettingsPlugin(settingsStore))
        runtime.install(CredentialsPlugin(InMemoryCredentialProvider()))
        runtime.install(LlmPlugin())

        val created = mutableListOf<RecordingPromptExecutor>()
        val generationModels = mutableListOf<List<String>>()
        val namespace = settingsNamespace("llm-koog-model-generation-test")
        runtime.install(
            KoogLlmPlugin(
                routes = listOf(installedRoute),
                providerExecutorFactory =
                    KoogProviderExecutorFactory { routes, _, _ ->
                        generationModels +=
                            routes.single().models.map { route -> route.model.id }
                        RecordingPromptExecutor(
                            frames =
                                flowOf(
                                    StreamFrame.TextComplete("ok", index = 0),
                                    StreamFrame.End(finishReason = "stop"),
                                )
                        ).also(created::add)
                    },
                settingsNamespace = namespace,
                defaults = KoogLlmSettings.fromRoutes(listOf(installedRoute)),
            )
        )

        val oldPrepared =
            runtime.context.llm.prepareCall(
                LlmCallConfig(provider = "test", model = "test-model")
            )
        val settings = runtime.context.require(SettingsKey).scope(namespace)
        settings.update(
            buildJsonObject {
                put(
                    "providers",
                    buildJsonObject {
                        put(
                            "test",
                            buildJsonObject {
                                put(
                                    "models",
                                    kotlinx.serialization.json.buildJsonArray {
                                        add(
                                            buildJsonObject {
                                                put("id", "replacement-model")
                                                put("name", "Replacement Model")
                                                put("maxTokens", 777)
                                            }
                                        )
                                    },
                                )
                            },
                        )
                    },
                )
            }
        )

        assertEquals(
            listOf(listOf("test-model"), listOf("replacement-model")),
            generationModels,
        )
        assertEquals(
            listOf("replacement-model"),
            runtime.context.llm.listModels("test").map { model -> model.id },
        )
        assertFalse(created[0].closed)

        oldPrepared.stream(
            GenerateOptions(
                provider = "test",
                model = "test-model",
                messages = listOf(createUserMessage(listOf(TextBlock("old")))),
            )
        ).toList()
        assertEquals("test-model", created[0].lastStreamingModel?.id)
        assertTrue(created[0].closed)

        val unknown =
            assertFailsWith<LlmException> {
                runtime.context.llm.resolveModelInfo("test", "test-model")
            }
        assertEquals(KoogLlmErrorCode.UNKNOWN_MODEL, unknown.code)

        val replacementPrepared =
            runtime.context.llm.prepareCall(
                LlmCallConfig(provider = "test", model = "replacement-model")
            )
        assertEquals(777, replacementPrepared.config.maxTokens)
        replacementPrepared.stream(
            GenerateOptions(
                provider = "test",
                model = "replacement-model",
                messages = listOf(createUserMessage(listOf(TextBlock("new")))),
                maxTokens = 777,
            )
        ).toList()
        assertEquals("replacement-model", created[1].lastStreamingModel?.id)

        runtime.context.dispose()
        assertTrue(created[1].closed)
    }

    @Test
    fun settingsUpdateShouldBuildNewGenerationAndCloseIdlePreviousGeneration() = runTest {
        val runtime = Runtime()
        val settingsStore = InMemorySettingsDocumentStore()
        runtime.install(SettingsPlugin(settingsStore))
        val credentialProvider =
            InMemoryCredentialProvider(
                mapOf(CredentialRef("test_key") to "key-v1")
            )
        runtime.install(CredentialsPlugin(credentialProvider))
        runtime.install(LlmPlugin())

        val created = mutableListOf<RecordingPromptExecutor>()
        val baseUrls = mutableListOf<String?>()
        val expectedKeys = mutableListOf("key-v1")
        val defaults =
            KoogLlmSettings(
                mapOf(
                    "test" to
                        KoogProviderSettings(
                            provider = "test",
                            displayName = "Test",
                            baseUrl = "https://one.example",
                            credential = KoogCredentialRef("test_key"),
                        )
                )
            )
        runtime.install(
            KoogLlmPlugin(
                routes = listOf(testKoogRoute()),
                providerExecutorFactory =
                    KoogProviderExecutorFactory { _, settings, credentials ->
                        baseUrls += settings.single().baseUrl
                        assertEquals(
                            expectedKeys.last(),
                            credentials.resolveRequired(
                                provider = "test",
                                reference = KoogCredentialRef("test_key"),
                            ),
                        )
                        RecordingPromptExecutor(
                            frames =
                                flowOf(
                                    StreamFrame.TextComplete("ok", index = 0),
                                    StreamFrame.End(finishReason = "stop"),
                                )
                        ).also(created::add)
                    },
                settingsNamespace = settingsNamespace("llm-koog-test"),
                defaults = defaults,
            )
        )

        assertEquals(listOf<String?>("https://one.example"), baseUrls)
        val settings =
            runtime.context.require(SettingsKey).scope(settingsNamespace("llm-koog-test"))
        settings.update(
            buildJsonObject {
                put(
                    "providers",
                    buildJsonObject {
                        put(
                            "test",
                            buildJsonObject {
                                put("baseUrl", "https://two.example")
                            },
                        )
                    },
                )
            }
        )

        assertEquals(
            listOf<String?>("https://one.example", "https://two.example"),
            baseUrls,
        )
        assertTrue(created.first().closed)
        assertFalse(created.last().closed)

        val chunks =
            runtime.context.llm.stream(
                GenerateOptions(
                    provider = "test",
                    model = "test-model",
                    messages = listOf(createUserMessage(listOf(TextBlock("hello")))),
                )
            ).toList()
        assertTrue(chunks.isNotEmpty())

        expectedKeys += "key-v2"
        credentialProvider.set(CredentialRef("test_key"), "key-v2")
        yield()
        assertEquals(3, created.size)
        assertTrue(created[1].closed)
        assertFalse(created[2].closed)

        runtime.context.dispose()
        assertTrue(created.last().closed)
    }

    @Test
    fun emptySettingsShouldSleepThenAddRemoveAndRestoreConfiguredRoutes() = runTest {
        val runtime = Runtime()
        runtime.install(SettingsPlugin(InMemorySettingsDocumentStore()))
        runtime.install(CredentialsPlugin(InMemoryCredentialProvider()))
        runtime.install(LlmPlugin())

        val installedRoute = testKoogRoute()
        val created = mutableListOf<RecordingPromptExecutor>()
        val factoryProviders = mutableListOf<List<String>>()
        val namespace = settingsNamespace("llm-koog-dormant-test")
        runtime.install(
            KoogLlmPlugin(
                routes = listOf(installedRoute),
                providerExecutorFactory =
                    KoogProviderExecutorFactory { routes, settings, _ ->
                        factoryProviders += settings.map(KoogProviderSettings::provider)
                        assertEquals(
                            routes.map(KoogProviderRoute::id),
                            settings.map(KoogProviderSettings::provider),
                        )
                        RecordingPromptExecutor().also(created::add)
                    },
                settingsNamespace = namespace,
            )
        )

        assertTrue(runtime.context.llm.listProviders().isEmpty())
        assertTrue(created.isEmpty())
        assertEquals(
            listOf("test" to false),
            runtime.context.llm.listConfigurableProviders().map { entry ->
                entry.provider to entry.declared
            },
        )

        val settings = runtime.context.require(SettingsKey).scope(namespace)
        val configured =
            KoogLlmSettings(
                mapOf(
                    "custom" to
                        KoogProviderSettings(
                            provider = "custom",
                            displayName = "Custom Endpoint",
                            api = "test",
                            models = listOf(KoogModelProfile(id = "custom-model")),
                        )
                )
            )
        settings.replace(configured.toJson())

        assertEquals(listOf(listOf("custom")), factoryProviders)
        assertEquals(listOf("custom"), runtime.context.llm.listProviders().map { it.id })
        assertEquals(
            listOf("test" to false, "custom" to true),
            runtime.context.llm.listConfigurableProviders().map { entry ->
                entry.provider to entry.declared
            },
        )
        assertEquals(
            listOf("custom-model"),
            runtime.context.llm.listModels("custom").map { model -> model.id },
        )

        settings.replace(KoogLlmSettings(emptyMap()).toJson())

        assertTrue(runtime.context.llm.listProviders().isEmpty())
        assertTrue(created.single().closed)
        assertEquals(
            listOf("test" to false),
            runtime.context.llm.listConfigurableProviders().map { entry ->
                entry.provider to entry.declared
            },
        )

        settings.replace(configured.toJson())

        assertEquals(2, created.size)
        assertFalse(created.last().closed)
        assertEquals(listOf("custom"), runtime.context.llm.listProviders().map { it.id })

        runtime.context.dispose()
        assertTrue(created.last().closed)
    }

    @Test
    fun conflictingDynamicRouteShouldRollbackDirectoryAndCloseRejectedGeneration() = runTest {
        val runtime = Runtime()
        runtime.install(SettingsPlugin(InMemorySettingsDocumentStore()))
        runtime.install(CredentialsPlugin(InMemoryCredentialProvider()))
        runtime.install(LlmPlugin())

        val claimedRoute = testKoogRoute().copy(id = "claimed", name = "Claimed")
        val staticExecutor = RecordingPromptExecutor()
        runtime.install(
            KoogLlmPlugin(
                executorFactory = KoogPromptExecutorFactory { staticExecutor },
                routes = listOf(claimedRoute),
            )
        )

        val rejected = mutableListOf<RecordingPromptExecutor>()
        val namespace = settingsNamespace("llm-koog-conflict-test")
        runtime.install(
            KoogLlmPlugin(
                routes = listOf(testKoogRoute()),
                providerExecutorFactory =
                    KoogProviderExecutorFactory { _, _, _ ->
                        RecordingPromptExecutor().also(rejected::add)
                    },
                settingsNamespace = namespace,
            )
        )

        runtime.context.require(SettingsKey).scope(namespace).replace(
            KoogLlmSettings(
                mapOf(
                    "claimed" to
                        KoogProviderSettings(
                            provider = "claimed",
                            api = "test",
                        )
                )
            ).toJson()
        )

        assertEquals(listOf("claimed"), runtime.context.llm.listProviders().map { it.id })
        assertEquals(
            listOf("test" to false),
            runtime.context.llm.listConfigurableProviders().map { entry ->
                entry.provider to entry.declared
            },
        )
        assertTrue(rejected.single().closed)
        assertFalse(staticExecutor.closed)

        runtime.context.dispose()
        assertTrue(staticExecutor.closed)
    }
}
