package im.hikaru.harness.llm.koog.openai.providers

import im.hikaru.harness.credentials.CredentialsPlugin
import im.hikaru.harness.credentials.InMemoryCredentialProvider
import im.hikaru.harness.llm.*
import im.hikaru.harness.llm.koog.openai.OpenAiKoogPlugin
import im.hikaru.harness.runtime.Runtime
import im.hikaru.harness.settings.InMemorySettingsDocumentStore
import im.hikaru.harness.settings.SettingsPlugin
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class ProviderContributionRegistrationTest {
    @Test
    fun installingPluginExposesContributionsAndDraftDiscoveryWithoutAnActiveModelRoute() = runTest {
        val runtime = Runtime()
        runtime.install(SettingsPlugin(InMemorySettingsDocumentStore()))
        runtime.install(CredentialsPlugin(InMemoryCredentialProvider()))
        runtime.install(LlmPlugin())
        runtime.install(OpenAiKoogPlugin())
        try {
            val llm = runtime.context.llm
            assertEquals(setOf("openai", "anthropic", "google", "deepseek", "alibaba", "moonshot", "zhipu"), llm.listProviderDescriptors().map { it.id }.toSet())
            assertTrue(llm.listProviders().isEmpty())
            val result = llm.discoverModels("zhipu", LlmProviderAccess(LlmProviderConnection(
                provider = "unsaved-glm-draft", vendor = "zhipu", api = BuiltinProviderContributions.CHAT,
                baseUrl = "https://open.bigmodel.cn/api/paas/v4", region = "china", serviceVariant = "standard",
            )))
            assertEquals("bundled:zhipu:2026-10-21", result.source)
            assertEquals(LlmCatalogCompleteness.UNKNOWN, result.completeness)
            assertTrue(result.models.isNotEmpty())
            assertTrue(llm.listProviders().isEmpty())
        } finally { runtime.context.dispose() }
    }
}
