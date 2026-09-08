package im.hikaru.harness.llm.koog.openai

import im.hikaru.harness.credentials.CredentialsKey
import im.hikaru.harness.credentials.CredentialsPlugin
import im.hikaru.harness.credentials.InMemoryCredentialProvider
import im.hikaru.harness.llm.LlmKey
import im.hikaru.harness.llm.LlmPlugin
import im.hikaru.harness.llm.llm
import im.hikaru.harness.llm.koog.KoogCredentialResolver
import im.hikaru.harness.llm.koog.resolveRoutes
import im.hikaru.harness.llm.koog.openai.catalog.OpenAiKoogCatalog
import im.hikaru.harness.llm.koog.openai.chat.OpenAiChatOptionMapper
import im.hikaru.harness.llm.koog.openai.responses.OpenAiResponsesOptionMapper
import im.hikaru.harness.llm.koog.openai.semantics.OpenAiKoogSemantics
import im.hikaru.harness.settings.SettingsKey
import im.hikaru.harness.settings.InMemorySettingsDocumentStore
import im.hikaru.harness.settings.SettingsPlugin
import im.hikaru.harness.runtime.Runtime
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OpenAiKoogPluginTest {

    @Test
    fun catalogShouldSeparateInstalledApisFromTheDefaultProviderProfile() {
        val routeIds = OpenAiKoogCatalog.installedRoutes().map { route -> route.id }
        val settingsIds = OpenAiKoogCatalog.defaultSettings().providers.keys.toList()
        val semanticsIds = OpenAiKoogSemantics.bundles().map { semantics -> semantics.provider }
        val resolved =
            OpenAiKoogCatalog.defaultSettings().resolveRoutes(OpenAiKoogCatalog.installedRoutes())

        assertEquals(
            listOf(
                OpenAiChatOptionMapper.OPENAI_CHAT_COMPLETIONS_API_ID,
                OpenAiResponsesOptionMapper.OPENAI_RESPONSES_API_ID,
            ),
            routeIds,
        )
        assertEquals(listOf(OpenAiKoogCatalog.OPENAI_PROVIDER_ID), settingsIds)
        assertEquals(routeIds, semanticsIds)
        assertEquals(listOf(OpenAiKoogCatalog.OPENAI_PROVIDER_ID), resolved.map { it.id })
        assertTrue(
            resolved.single().models.all { model ->
                model.api == OpenAiChatOptionMapper.OPENAI_CHAT_COMPLETIONS_API_ID
            }
        )
    }

    @Test
    fun dynamicPluginShouldRequireSharedConfigurationServices() {
        val dependencies = OpenAiKoogPlugin().inject
        val keys = dependencies.map { spec -> spec.key }.toSet()

        assertEquals(3, keys.size)
        assertTrue(LlmKey in keys)
        assertTrue(SettingsKey in keys)
        assertTrue(CredentialsKey in keys)
        assertTrue(dependencies.all { spec -> spec.required })
    }

    @Test
    fun dynamicPluginShouldStartDormantAndExposeOnlyTheOpenAiDirectoryEntry() = runTest {
        val runtime = Runtime()
        runtime.install(SettingsPlugin(InMemorySettingsDocumentStore()))
        runtime.install(CredentialsPlugin(InMemoryCredentialProvider()))
        runtime.install(LlmPlugin())
        runtime.install(OpenAiKoogPlugin())

        assertTrue(runtime.context.llm.listProviders().isEmpty())
        assertEquals(
            listOf(OpenAiKoogCatalog.OPENAI_PROVIDER_ID to false),
            runtime.context.llm.listConfigurableProviders().map { entry ->
                entry.provider to entry.declared
            },
        )

        runtime.context.dispose()
    }

    @Test
    fun staticPluginShouldRequireOnlyTheLlmRuntime() {
        val plugin =
            OpenAiKoogPlugin(
                OpenAiKoogPluginConfig(
                    settingsSource =
                        OpenAiKoogSettingsSource.Static(
                            KoogCredentialResolver { null }
                        )
                )
            )

        assertEquals(setOf(LlmKey), plugin.inject.map { spec -> spec.key }.toSet())
    }
}
