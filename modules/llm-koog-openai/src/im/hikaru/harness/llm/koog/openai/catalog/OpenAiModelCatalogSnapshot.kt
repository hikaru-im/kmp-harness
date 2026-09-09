package im.hikaru.harness.llm.koog.openai.catalog

import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import im.hikaru.harness.llm.LlmModelReasoningInfo
import im.hikaru.harness.llm.LlmReasoningEffortInfo
import im.hikaru.harness.llm.ModelModality
import im.hikaru.harness.llm.ReasoningEffortId
import im.hikaru.harness.llm.koog.KoogModelRoute
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.longOrNull

internal data class OpenAiSnapshotModel(
    val id: String,
    val name: String,
    val description: String,
    val contextWindow: Long,
    val maxOutputTokens: Long,
    val inputModalities: List<ModelModality>,
    val reasoning: Boolean,
    val reasoningEfforts: List<String> = emptyList(),
    val temperature: Boolean = true,
) {
    fun toRoute(endpoint: LLMCapability): KoogModelRoute {
        val capabilities = mutableListOf(LLMCapability.Completion, endpoint)
        if (temperature) capabilities += LLMCapability.Temperature
        if (reasoning) capabilities += LLMCapability.Thinking
        if (ModelModality.IMAGE in inputModalities) capabilities += LLMCapability.Vision.Image
        val model = LLModel(
            provider = LLMProvider.OpenAI,
            id = id,
            contextLength = contextWindow,
            maxOutputTokens = maxOutputTokens,
            capabilities = capabilities,
        )
        val reasoningInfo = reasoning.takeIf { it }?.let {
            LlmModelReasoningInfo(
                reasoningEfforts.map { effort ->
                    LlmReasoningEffortInfo(ReasoningEffortId(effort), effort)
                }
            )
        }
        return KoogModelRoute(
            model = model,
            name = name,
            description = description,
            inputModalities = inputModalities,
            reasoning = reasoningInfo,
        )
    }
}

/** Bundled, offline snapshot generated from models.dev. Revision is recorded for reviewability. */
internal object OpenAiModelCatalogSnapshot {
    const val SOURCE_REVISION = "26703fa74cc2a990d4a095d5dce6d79dd2202524"
    val models: List<OpenAiSnapshotModel> by lazy {
        val stream =
            OpenAiModelCatalogSnapshot::class.java.getResourceAsStream("/model-catalog/openai.json")
                ?: error("Bundled OpenAI models.dev snapshot is missing")
        decodeOpenAiModelCatalogSnapshot(stream.bufferedReader().use { it.readText() })
    }
}

internal fun decodeOpenAiModelCatalogSnapshot(content: String): List<OpenAiSnapshotModel> {
    val root = Json.parseToJsonElement(content).requireObject("root")
    require(root.keys == setOf("schemaVersion", "source", "models")) {
        "OpenAI model snapshot root has invalid fields"
    }
    require(root.requiredLong("schemaVersion") == 1L) {
        "Unsupported OpenAI model snapshot schema"
    }
    val source = root.getValue("source").requireObject("source")
    require(source.keys == setOf("repository", "revision", "license", "notice")) {
        "OpenAI model snapshot source has invalid fields"
    }
    require(source.requiredString("repository") == "https://github.com/anomalyco/models.dev") {
        "OpenAI model snapshot repository is invalid"
    }
    require(source.requiredString("revision") == OpenAiModelCatalogSnapshot.SOURCE_REVISION) {
        "OpenAI model snapshot revision is invalid"
    }
    require(source.requiredString("license") == "MIT") {
        "OpenAI model snapshot license is invalid"
    }
    require(source.requiredString("notice").isNotBlank()) {
        "OpenAI model snapshot notice must not be blank"
    }

    val parsed = root.getValue("models").requireArray("models").mapIndexed { index, element ->
        parseOpenAiSnapshotModel(element.requireObject("models[$index]"), index)
    }
    require(parsed.map(OpenAiSnapshotModel::id).distinct().size == parsed.size) {
        "OpenAI model snapshot contains duplicate ids"
    }
    return parsed
}

private fun parseOpenAiSnapshotModel(value: JsonObject, index: Int): OpenAiSnapshotModel {
    val required =
        setOf(
            "id", "name", "description", "contextWindow", "maxOutputTokens",
            "inputModalities", "reasoning", "toolCall", "structuredOutput", "temperature",
        )
    val allowed = required + "reasoningEfforts"
    require(value.keys.all(allowed::contains) && value.keys.containsAll(required)) {
        "OpenAI model snapshot entry $index has invalid fields"
    }
    val id = value.requiredString("id")
    val name = value.requiredString("name")
    val description = value.requiredString("description")
    require(id.isNotBlank() && name.isNotBlank() && description.isNotBlank()) {
        "OpenAI model snapshot entry $index has blank text fields"
    }
    val contextWindow = value.requiredLong("contextWindow")
    val maxOutputTokens = value.requiredLong("maxOutputTokens")
    require(contextWindow > 0L && maxOutputTokens > 0L) {
        "OpenAI model snapshot entry $index has non-positive limits"
    }
    val modalities =
        value.getValue("inputModalities").requireArray("inputModalities").map { element ->
            when (element.requireString("input modality")) {
                "text" -> ModelModality.TEXT
                "image" -> ModelModality.IMAGE
                else -> error("OpenAI model snapshot entry $index has unsupported input modality")
            }
        }
    require(modalities.isNotEmpty() && modalities.distinct().size == modalities.size) {
        "OpenAI model snapshot entry $index has invalid input modalities"
    }
    val reasoning = value.requiredBoolean("reasoning")
    val efforts =
        value["reasoningEfforts"]?.requireArray("reasoningEfforts")?.map { element ->
            element.requireString("reasoning effort").also { effort ->
                require(effort.isNotBlank()) {
                    "OpenAI model snapshot entry $index has a blank reasoning effort"
                }
            }
        }.orEmpty()
    require(efforts.distinct().size == efforts.size && (reasoning || efforts.isEmpty())) {
        "OpenAI model snapshot entry $index has invalid reasoning efforts"
    }
    value.requiredBoolean("toolCall")
    value.requiredBoolean("structuredOutput")

    return OpenAiSnapshotModel(
        id = id,
        name = name,
        description = description,
        contextWindow = contextWindow,
        maxOutputTokens = maxOutputTokens,
        inputModalities = modalities,
        reasoning = reasoning,
        reasoningEfforts = efforts,
        temperature = value.requiredBoolean("temperature"),
    )
}

private fun kotlinx.serialization.json.JsonElement.requireObject(label: String): JsonObject =
    this as? JsonObject
        ?: throw IllegalArgumentException("OpenAI model snapshot $label must be an object")

private fun kotlinx.serialization.json.JsonElement.requireArray(label: String): JsonArray =
    this as? JsonArray
        ?: throw IllegalArgumentException("OpenAI model snapshot $label must be an array")

private fun kotlinx.serialization.json.JsonElement.requireString(label: String): String {
    val primitive = this as? JsonPrimitive
    require(primitive != null && primitive.isString) {
        "OpenAI model snapshot $label must be a string"
    }
    return primitive.content
}

private fun JsonObject.requiredString(name: String): String =
    getValue(name).requireString(name)

private fun JsonObject.requiredLong(name: String): Long {
    val primitive = getValue(name) as? JsonPrimitive
    require(primitive != null && !primitive.isString && primitive.longOrNull != null) {
        "OpenAI model snapshot $name must be an integer"
    }
    return checkNotNull(primitive.longOrNull)
}

private fun JsonObject.requiredBoolean(name: String): Boolean {
    val primitive = getValue(name) as? JsonPrimitive
    require(primitive != null && !primitive.isString && primitive.booleanOrNull != null) {
        "OpenAI model snapshot $name must be a boolean"
    }
    return primitive.boolean
}
