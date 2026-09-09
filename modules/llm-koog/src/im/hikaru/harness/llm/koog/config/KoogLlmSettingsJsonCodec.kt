package im.hikaru.harness.llm.koog

import im.hikaru.harness.llm.ModelModality
import im.hikaru.harness.llm.ReasoningEffortId
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** Manual wire codec for the settings namespace; no credential value is ever encoded. */
internal object KoogLlmSettingsJsonCodec {
    fun encode(settings: KoogLlmSettings): JsonObject =
        buildJsonObject {
            put(
                "providers",
                buildJsonObject {
                    settings.providers.forEach { (id, provider) ->
                        put(id, provider.toJson())
                    }
                },
            )
        }

    fun decode(value: JsonObject): KoogLlmSettings {
        val providerObject = value["providers"]?.jsonObject ?: value
        return KoogLlmSettings(
            providerObject.map { (id, element) ->
                id to parseProvider(id, element)
            }.toMap()
        )
    }

    private fun parseProvider(
        id: String,
        element: JsonElement,
    ): KoogProviderSettings {
        val value = element.jsonObject
        val allowed =
            setOf(
                "provider", "displayName", "api", "baseUrl", "credential",
                "requestTimeoutMillis", "connectTimeoutMillis", "socketTimeoutMillis",
                "models", "modelOverrides", "defaultContextWindow", "defaultMaxTokens",
                "defaultInput",
            )
        require(value.keys.all(allowed::contains)) { "Koog provider '$id' contains unknown fields" }
        val provider = value.string("provider") ?: id
        val displayName = value.string("displayName") ?: provider
        val credential =
            when (val raw = value["credential"]) {
                null -> null
                is JsonObject ->
                    KoogCredentialRef(
                        raw.string("name")
                            ?: error("Credential reference for '$id' must contain name")
                    )
                is JsonPrimitive ->
                    raw.takeIf(JsonPrimitive::isString)?.content?.let(::KoogCredentialRef)
                        ?: error("Credential reference for '$id' must be a string or object")
                else -> error("Credential reference for '$id' must be a string or object")
            }
        return KoogProviderSettings(
            provider = provider,
            displayName = displayName,
            api = value.string("api"),
            baseUrl = value.string("baseUrl"),
            credential = credential,
            requestTimeoutMillis = value.long("requestTimeoutMillis"),
            connectTimeoutMillis = value.long("connectTimeoutMillis"),
            socketTimeoutMillis = value.long("socketTimeoutMillis"),
            models = value["models"]?.jsonArray?.map(::parseModelProfile),
            modelOverrides =
                value["modelOverrides"]?.jsonObject?.mapValues { (modelId, override) ->
                    parseModelOverride(modelId, override)
                }.orEmpty(),
            defaultContextWindow = value.long("defaultContextWindow"),
            defaultMaxTokens = value.long("defaultMaxTokens"),
            defaultInput = value.modalities("defaultInput"),
        )
    }

    private fun parseModelProfile(element: JsonElement): KoogModelProfile {
        val value = element.jsonObject
        require(value.keys.all { it in setOf("id", "name", "description", "contextWindow", "maxTokens", "input", "reasoningEfforts", "defaultReasoningEffort") }) {
            "Koog model entry contains unknown fields"
        }
        val id = value.string("id") ?: error("Koog model entry must contain id")
        return KoogModelProfile(
            id = id,
            name = value.string("name"),
            description = value.string("description"),
            contextWindow = value.long("contextWindow"),
            maxTokens = value.long("maxTokens"),
            input = value.modalities("input"),
            reasoningEfforts = value.reasoningEfforts(id),
        )
    }

    private fun parseModelOverride(
        id: String,
        element: JsonElement,
    ): KoogModelOverride {
        val value = element.jsonObject
        require(value.keys.all { it in setOf("name", "description", "contextWindow", "maxTokens", "input", "reasoningEfforts", "defaultReasoningEffort") }) {
            "Koog model override '$id' contains unknown fields"
        }
        require("id" !in value) {
            "Koog model override '$id' must use its map key as the model id"
        }
        return KoogModelOverride(
            name = value.string("name"),
            description = value.string("description"),
            contextWindow = value.long("contextWindow"),
            maxTokens = value.long("maxTokens"),
            input = value.modalities("input"),
            reasoningEfforts = value.reasoningEfforts(id),
        )
    }

    private fun KoogProviderSettings.toJson(): JsonObject =
        buildJsonObject {
            put("provider", provider)
            put("displayName", displayName)
            api?.let { put("api", it) }
            baseUrl?.let { put("baseUrl", it) }
            credential?.let { reference ->
                put(
                    "credential",
                    buildJsonObject {
                        put("name", reference.name)
                    },
                )
            }
            requestTimeoutMillis?.let { put("requestTimeoutMillis", it) }
            connectTimeoutMillis?.let { put("connectTimeoutMillis", it) }
            socketTimeoutMillis?.let { put("socketTimeoutMillis", it) }
            models?.let { configured ->
                put(
                    "models",
                    buildJsonArray {
                        configured.forEach { add(it.toJson()) }
                    },
                )
            }
            if (modelOverrides.isNotEmpty()) {
                put(
                    "modelOverrides",
                    buildJsonObject {
                        modelOverrides.forEach { (id, override) ->
                            put(id, override.toJson())
                        }
                    },
                )
            }
            defaultContextWindow?.let { put("defaultContextWindow", it) }
            defaultMaxTokens?.let { put("defaultMaxTokens", it) }
            defaultInput?.let { put("defaultInput", it.toJson()) }
        }

    private fun KoogModelProfile.toJson(): JsonObject =
        buildJsonObject {
            put("id", id)
            writeModelFields(
                name,
                description,
                contextWindow,
                maxTokens,
                input,
                reasoningEfforts,
            )
        }

    private fun KoogModelOverride.toJson(): JsonObject =
        buildJsonObject {
            writeModelFields(
                name,
                description,
                contextWindow,
                maxTokens,
                input,
                reasoningEfforts,
            )
        }

    private fun JsonObjectBuilder.writeModelFields(
        name: String?,
        description: String?,
        contextWindow: Long?,
        maxTokens: Long?,
        input: List<ModelModality>?,
        reasoningEfforts: KoogReasoningEfforts?,
    ) {
        name?.let { put("name", it) }
        description?.let { put("description", it) }
        contextWindow?.let { put("contextWindow", it) }
        maxTokens?.let { put("maxTokens", it) }
        input?.let { put("input", it.toJson()) }
        when (reasoningEfforts) {
            null -> Unit
            KoogReasoningEfforts.Disabled -> put("reasoningEfforts", false)
            is KoogReasoningEfforts.Supported -> {
                put(
                    "reasoningEfforts",
                    buildJsonObject {
                        reasoningEfforts.efforts.forEach { effort ->
                            put(effort.value, effort.value)
                        }
                    },
                )
                reasoningEfforts.defaultEffort?.let {
                    put("defaultReasoningEffort", it.value)
                }
            }
        }
    }

    private fun List<ModelModality>.toJson(): JsonArray =
        buildJsonArray {
            this@toJson.forEach { modality ->
                add(
                    JsonPrimitive(
                        when (modality) {
                            ModelModality.TEXT -> "text"
                            ModelModality.IMAGE -> "image"
                        }
                    )
                )
            }
        }

    private fun JsonObject.string(key: String): String? =
        this[key]?.let { element ->
            (element as? JsonPrimitive)?.takeIf { it.isString }?.content
                ?: error("Setting '$key' must be a string")
        }

    private fun JsonObject.long(key: String): Long? =
        this[key]?.let { element ->
            (element as? JsonPrimitive)?.longOrNull
                ?: error("Setting '$key' must be an integer")
        }

    private fun JsonObject.modalities(key: String): List<ModelModality>? =
        this[key]?.jsonArray?.map { element ->
            val value =
                (element as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content
                    ?: error("Setting '$key' entries must be strings")
            when (value) {
                "text" -> ModelModality.TEXT
                "image" -> ModelModality.IMAGE
                else -> error("Setting '$key' contains unsupported modality '$value'")
            }
        }

    private fun JsonObject.reasoningEfforts(modelId: String): KoogReasoningEfforts? {
        val element =
            this["reasoningEfforts"]
                ?: run {
                    require("defaultReasoningEffort" !in this) {
                        "Koog model '$modelId' sets defaultReasoningEffort without reasoningEfforts"
                    }
                    return null
                }
        if (element is JsonPrimitive) {
            require(element.booleanOrNull == false) {
                "Koog model '$modelId' reasoningEfforts must be false, an array, or an object"
            }
            require("defaultReasoningEffort" !in this) {
                "Koog model '$modelId' disables reasoning but sets defaultReasoningEffort"
            }
            return KoogReasoningEfforts.Disabled
        }
        val ids =
            when (element) {
                is JsonArray ->
                    element.map { effort ->
                        effort.stringValue("reasoningEfforts")
                    }
                is JsonObject ->
                    element.map { (id, wire) ->
                        val wireValue = wire.stringValue("reasoningEfforts.$id")
                        require(wireValue.isNotBlank()) {
                            "Koog model '$modelId' reasoning effort '$id' must have a wire value"
                        }
                        require(wireValue == id) {
                            "Koog model '$modelId' reasoning effort '$id' cannot map to '$wireValue'; " +
                                "configurable wire aliases are not supported by the installed Koog semantics"
                        }
                        id
                    }
            }
        val efforts = ids.map(::ReasoningEffortId)
        val default = string("defaultReasoningEffort")?.let(::ReasoningEffortId)
        return KoogReasoningEfforts.Supported(efforts, default)
    }

    private fun JsonElement.stringValue(key: String): String =
        (this as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content
            ?: error("Setting '$key' must be a string")
}
