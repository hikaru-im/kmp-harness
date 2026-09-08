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
    return providers.values.map { settings ->
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
        val overrideApi = modelOverrides.getValue(id).api ?: defaultApi
        val template = installedByApi[overrideApi]
            ?: error("Koog provider '$provider' model '$id' names unknown api '$overrideApi'")
        require(template.models.any { model -> model.model.id == id }) {
            "Koog provider '$provider' modelOverrides names unknown model '$id' for api '$overrideApi'"
        }
    }

    val entries =
        if (models.isNullOrEmpty()) {
            defaultTemplate.models.map { model ->
                modelOverrides[model.model.id]?.withId(model.model.id)
                    ?: KoogModelProfile(
                        id = model.model.id,
                        api = defaultTemplate.apiFor(model),
                    )
            }
        } else {
            models
        }
    require(entries.map(KoogModelProfile::id).distinct().size == entries.size) {
        "Koog provider '$provider' models must not contain duplicate ids"
    }

    val resolved = entries.map { entry ->
        val modelApi = entry.api ?: defaultApi
        val template = installedByApi[modelApi]
            ?: error("Koog provider '$provider' model '${entry.id}' names unknown api '$modelApi'")
        resolveModel(
            installed = template.models.firstOrNull { model -> model.model.id == entry.id },
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
    )
}

private fun KoogProviderSettings.resolveModel(
    installed: KoogModelRoute?,
    template: LLModel,
    entry: KoogModelProfile,
): KoogModelRoute {
    val custom = installed == null
    val sourceModel = installed?.model ?: template
    val input =
        entry.input?.takeIf(List<ModelModality>::isNotEmpty)
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
            ?: installed?.model?.contextLength
            ?: defaultContextWindow
            ?: DEFAULT_CONTEXT_WINDOW
    val maxOutputTokens =
        entry.maxTokens
            ?: installed?.model?.maxOutputTokens
            ?: defaultMaxTokens
            ?: DEFAULT_MAX_TOKENS
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
        name = entry.name ?: installed?.name ?: entry.id,
        description = entry.description ?: installed?.description,
        inputModalities = input.toList(),
        defaultMaxTokens = entry.maxTokens ?: installed?.defaultMaxTokens,
        reasoning = reasoning,
    )
}

private fun String.displayName(): String =
    split('-', '_')
        .joinToString(" ") { word ->
            word.replaceFirstChar { character -> character.uppercase() }
        }

private fun LLModel.detachedCopy(): LLModel =
    copy(capabilities = capabilities?.toList())

private const val DEFAULT_CONTEXT_WINDOW: Long = 262_144L
private const val DEFAULT_MAX_TOKENS: Long = 32_768L
