package im.hikaru.harness.llm.koog

import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLModel
import im.hikaru.harness.llm.LlmModelReasoningInfo
import im.hikaru.harness.llm.LlmReasoningEffortInfo
import im.hikaru.harness.llm.ModelModality

/** Materialize immutable runtime routes from installed catalogs and one settings snapshot. */
fun KoogLlmSettings.resolveRoutes(
    installedRoutes: List<KoogProviderRoute>,
): List<KoogProviderRoute> {
    val installedByApi = installedRoutes.associateBy(KoogProviderRoute::id)
    require(installedByApi.size == installedRoutes.size) {
        "Installed Koog protocol templates must not contain duplicate ids"
    }
    return providers.values.filterNot { settings ->
        (settings.catalogManaged && !settings.catalogMatchesConnection() && settings.models.isNullOrEmpty()) ||
        settings.catalogMatchesConnection() && settings.discoveredCatalog != null &&
            settings.discoveredCatalog.models.isEmpty() && settings.retainedModels.isEmpty() && settings.models.isNullOrEmpty()
    }.map { settings ->
        settings.resolveRoute(installedByApi)
    }
}

private fun KoogProviderSettings.resolveRoute(
    installedByApi: Map<String, KoogProviderRoute>,
): KoogProviderRoute {
    val defaultApi = api ?: provider.takeIf(installedByApi::containsKey)
        ?: error("Koog provider '$provider' must name an installed api")
    val defaultTemplate = installedByApi[defaultApi]
        ?: error("Koog provider '$provider' names unknown api '$defaultApi'")
    require(modelOverrides.isEmpty() || models.isNullOrEmpty()) {
        "Koog provider '$provider' cannot set modelOverrides beside a non-empty models list"
    }
    modelOverrides.keys.forEach { id ->
        require(id.isNotBlank()) { "Koog provider '$provider' has a blank model override id" }
        require(defaultTemplate.models.any { model -> model.model.id == id }) {
            "Koog provider '$provider' modelOverrides names unknown model '$id'"
        }
    }

    val catalog = discoveredCatalog?.takeIf { catalogMatchesConnection() }
    val discoveredEntries = catalog?.let { (it.models + retainedModels).distinctBy { model -> model.id } }.orEmpty()
    val entries =
        if (catalog != null) {
            (models.orEmpty() + discoveredEntries.map { modelOverrides[it.id]?.withId(it.id) ?: KoogModelProfile(id = it.id) }).distinctBy { it.id }
        } else if (models.isNullOrEmpty()) {
            require(vendor == null || vendor == "openai") { "Provider '$provider' requires a selected model catalog" }
            defaultTemplate.models.map { model ->
                modelOverrides[model.model.id]?.withId(model.model.id)
                    ?: KoogModelProfile(
                        id = model.model.id,
                    )
            }
        } else {
            models
        }
    require(entries.map(KoogModelProfile::id).distinct().size == entries.size) {
        "Koog provider '$provider' models must not contain duplicate ids"
    }

    val resolved = entries.map { entry ->
        val modelApi = defaultApi
        val template = defaultTemplate
        resolveModel(
            installed = template.models.firstOrNull { model -> model.model.id == entry.id && (vendor == null || vendor == "openai") },
            template = template.customModelTemplate,
            entry = entry,
        ).copy(api = modelApi)
    }
    return defaultTemplate.copy(
        id = provider,
        name = displayName,
        models = resolved,
        api = defaultApi,
        customModelTemplate = defaultTemplate.customModelTemplate.detachedCopy(),
        vendor = vendor ?: "openai",
        serviceVariant = serviceVariant,
        apiVersion = apiVersion,
    )
}

private fun KoogProviderSettings.resolveModel(
    installed: KoogModelRoute?,
    template: LLModel,
    entry: KoogModelProfile,
): KoogModelRoute {
    val custom = installed == null
    val discovered = discoveredCatalog?.takeIf { catalogMatchesConnection() }?.let { (it.models + retainedModels).firstOrNull { model -> model.id == entry.id } }
    val sourceModel = installed?.model ?: template
    val input =
        entry.input?.takeIf(List<ModelModality>::isNotEmpty)
            ?: discovered?.inputModalities
            ?: installed?.inputModalities
            ?: defaultInput
            ?: listOf(ModelModality.TEXT)
    val reasoning =
        when (val configured = entry.reasoningEfforts) {
            null -> installed?.reasoning
            KoogReasoningEfforts.Disabled -> null
            is KoogReasoningEfforts.Supported ->
                LlmModelReasoningInfo(
                    efforts = configured.efforts.map { id ->
                        LlmReasoningEffortInfo(
                            id = id,
                            name = id.value.displayName(),
                        )
                    },
                    defaultEffort = configured.defaultEffort,
                )
        }
    val contextWindow =
        entry.contextWindow
            ?: discovered?.contextWindow
            ?: installed?.model?.contextLength
            ?: defaultContextWindow
    val maxOutputTokens =
        entry.maxOutputTokens
            ?: discovered?.maxOutputTokens
            ?: entry.maxTokens
            ?: installed?.model?.maxOutputTokens
            ?: defaultMaxTokens
    val capabilities =
        sourceModel.capabilities
            .orEmpty()
            .filterNot { capability ->
                (custom && capability == LLMCapability.Thinking) ||
                    (custom && capability == LLMCapability.Vision.Image) ||
                    (capability == LLMCapability.Thinking && reasoning == null) ||
                    (capability == LLMCapability.Vision.Image && ModelModality.IMAGE !in input)
            }
            .toMutableList()
            .apply {
                if (reasoning != null && LLMCapability.Thinking !in this) {
                    add(LLMCapability.Thinking)
                }
                if (ModelModality.IMAGE in input && LLMCapability.Vision.Image !in this) {
                    add(LLMCapability.Vision.Image)
                }
            }

    return KoogModelRoute(
        model =
            sourceModel.copy(
                id = entry.id,
                capabilities = capabilities,
                contextLength = contextWindow,
                maxOutputTokens = maxOutputTokens,
            ),
        name = entry.name ?: discovered?.name ?: installed?.name ?: entry.id,
        description = entry.description ?: discovered?.description ?: installed?.description,
        inputModalities = input.toList(),
        defaultMaxTokens = entry.maxTokens ?: installed?.defaultMaxTokens,
        reasoning = reasoning,
        reasoningCapabilities = discovered?.reasoningCapabilities,
        metadata = discovered?.metadata ?: kotlinx.serialization.json.JsonObject(emptyMap()),
    )
}

private fun String.displayName(): String =
    split('-', '_')
        .joinToString(" ") { word ->
            word.replaceFirstChar { character -> character.uppercase() }
        }

fun KoogProviderSettings.catalogMatchesConnection(): Boolean {
    val binding = discoveredCatalogConnection ?: return false
    return binding.provider == provider && binding.vendor == (vendor ?: "openai") &&
        binding.api == api && binding.baseUrl.trimEnd('/') == baseUrl?.trimEnd('/') &&
        binding.serviceVariant == serviceVariant && binding.region == region && binding.workspace == workspace &&
        binding.credentialRef == credential?.name && binding.apiVersion == apiVersion
}

private fun LLModel.detachedCopy(): LLModel =
    copy(capabilities = capabilities?.toList())
