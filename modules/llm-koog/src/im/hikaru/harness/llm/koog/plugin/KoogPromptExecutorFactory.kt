package im.hikaru.harness.llm.koog

import ai.koog.prompt.executor.model.PromptExecutor
import im.hikaru.harness.llm.LlmConfigurableProvider
import im.hikaru.harness.settings.SettingsNamespace

/** Creates a fresh PromptExecutor whenever a static plugin instance activates. */
fun interface KoogPromptExecutorFactory {
    suspend fun create(): PromptExecutor
}

/** Runtime inputs required by the live settings reload lifecycle. */
data class DynamicKoogConfiguration(
    val namespace: SettingsNamespace,
    val defaults: KoogLlmSettings,
    val factory: KoogProviderExecutorFactory,
    val configurableProviders: List<LlmConfigurableProvider>,
)

internal fun providerPromptExecutorFactory(
    routes: List<KoogProviderRoute>,
    settings: List<KoogProviderSettings>,
    credentials: KoogCredentialResolver,
    factory: KoogProviderExecutorFactory,
): KoogPromptExecutorFactory {
    val routeIds = routes.map(KoogProviderRoute::id)
    require(routeIds.distinct().size == routeIds.size) {
        "Koog plugin provider ids must not contain duplicates"
    }

    val settingIds = settings.map(KoogProviderSettings::provider)
    require(settingIds.distinct().size == settingIds.size) {
        "Koog provider settings must not contain duplicate providers"
    }

    val routeIdSet = routeIds.toSet()
    val settingIdSet = settingIds.toSet()
    val missingSettings = routeIds.filterNot(settingIdSet::contains)
    val unknownSettings = settingIds.filterNot(routeIdSet::contains)
    require(missingSettings.isEmpty() && unknownSettings.isEmpty()) {
        buildString {
            append("Koog provider settings must match routes exactly")
            if (missingSettings.isNotEmpty()) {
                append("; missing: ${missingSettings.joinToString()}")
            }
            if (unknownSettings.isNotEmpty()) {
                append("; unknown: ${unknownSettings.joinToString()}")
            }
        }
    }

    val settingsByProvider = settings.associateBy(KoogProviderSettings::provider)
    val settingsSnapshot = routeIds.map(settingsByProvider::getValue)
    return KoogPromptExecutorFactory {
        factory.create(
            routes = routes,
            settings = settingsSnapshot,
            credentials = credentials,
        )
    }
}
