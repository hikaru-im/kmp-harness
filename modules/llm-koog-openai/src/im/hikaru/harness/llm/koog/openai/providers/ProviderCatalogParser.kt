package im.hikaru.harness.llm.koog.openai.providers

import im.hikaru.harness.llm.LlmDiscoveredModel
import im.hikaru.harness.llm.ModelModality
import kotlinx.serialization.json.*

/** Parses documented directory shapes; absent or null capabilities remain unknown. */
internal object ProviderCatalogParser {
    fun parse(vendor: String, body: String): ProviderCatalogPage {
        try {
            val root = Json.parseToJsonElement(body).jsonObject
            if (vendor == "alibaba" && root["success"].booleanValue() == false) error("Catalog request failed")
            val container = if (vendor == "alibaba") root["output"]?.jsonObject
                ?: error("Missing output") else root
            val arrayKey = when (vendor) { "alibaba", "google" -> "models"; else -> "data" }
            val entries = container[arrayKey] as? JsonArray
                ?: if (vendor == "google" && arrayKey !in container) JsonArray(emptyList()) else error("Missing models")
            val models = entries.map { parseModel(vendor, it.jsonObject) }
            val hasMore = if (vendor == "anthropic") root["has_more"].booleanValue()
                ?: error("Missing pagination state") else null
            val next = when (vendor) {
                "anthropic" -> if (hasMore == true) root["last_id"].stringValue()
                    ?.takeIf { it.isNotBlank() } ?: error("Missing pagination cursor") else null
                "google" -> root["nextPageToken"].stringValue()?.takeIf { it.isNotBlank() }
                else -> null
            }
            return ProviderCatalogPage(
                models = models,
                cursor = next,
                total = if (vendor == "alibaba") container["total"].nonnegativeLong() else null,
                pageNumber = if (vendor == "alibaba") container["page_no"].nonnegativeLong() else null,
                unsupportedPagination = vendor !in setOf("anthropic", "alibaba", "google") &&
                    root["has_more"].booleanValue() == true,
            )
        } catch (failure: ProviderDiscoveryFailure) {
            throw failure
        } catch (_: Exception) {
            throw ProviderDiscoveryFailure("INVALID_RESPONSE", "服务返回的模型目录格式无效。")
        }
    }

    private fun parseModel(vendor: String, model: JsonObject): LlmDiscoveredModel {
        val rawId = model[when (vendor) { "alibaba" -> "model"; "google" -> "name"; else -> "id" }].stringValue()
            ?: error("Missing model ID")
        val id = if (vendor == "google") rawId.removePrefix("models/") else rawId
        require(id.isNotBlank() && id.length <= 512 && id.none { it.isWhitespace() || it.isISOControl() })
        val nameKey = when (vendor) { "anthropic" -> "display_name"; "google" -> "displayName"; else -> "name" }
        val name = model[nameKey].stringValue()?.takeIf { it.isNotBlank() }?.take(1024) ?: id
        val info = (model["model_info"] as? JsonObject).orEmpty()
        val inference = (model["inference_metadata"] as? JsonObject).orEmpty()
        val context = when (vendor) {
            "google" -> model["inputTokenLimit"].positiveLong()
            "anthropic" -> model["max_input_tokens"].positiveLong()
            "moonshot" -> model["context_length"].positiveLong()
            "alibaba" -> info["context_window"].positiveLong()
            else -> model["context_window"].positiveLong()
        }
        val output = when (vendor) {
            "google" -> model["outputTokenLimit"].positiveLong()
            "anthropic" -> model["max_tokens"].positiveLong()
            "alibaba" -> info["max_output_tokens"].positiveLong()
            else -> model["max_output_tokens"].positiveLong()
        }
        val inputs: List<String>? = when (vendor) {
            "alibaba" -> inference["request_modality"].stringList()
            "moonshot" -> if (model.containsKey("supports_image_in")) buildList {
                add("text")
                if (model["supports_image_in"].booleanValue() == true) add("image")
                if (model["supports_video_in"].booleanValue() == true) add("video")
            } else null
            else -> model["input_modalities"].stringList()
        }
        val outputs: List<String>? = if (vendor == "alibaba") inference["response_modality"].stringList()
            else model["output_modalities"].stringList()
        val operations: List<String>? = when (vendor) {
            "google" -> model["supportedGenerationMethods"].stringList()
            "alibaba" -> model["capabilities"].stringList()?.let { capabilities ->
                capabilities + model["features"].stringList().orEmpty()
            }
            else -> null
        }
        return LlmDiscoveredModel(
            id = id,
            name = name,
            contextWindow = context,
            maxOutputTokens = output,
            description = model["description"].stringValue()?.take(4096),
            // The neutral enum currently represents only text/image. The full list is preserved below.
            inputModalities = inputs?.mapNotNull { when (it.lowercase()) {
                "text" -> ModelModality.TEXT
                "image" -> ModelModality.IMAGE
                else -> null
            } }?.distinct(),
            outputModalities = outputs?.map(String::lowercase),
            supportedOperations = operations,
            metadata = buildJsonObject {
                put("vendor", vendor)
                put("raw", model)
                if (inputs != null) put("input_modalities", JsonArray(inputs.map { JsonPrimitive(it.lowercase()) }))
                if (vendor in setOf("google", "anthropic")) put("context_limit_kind", "input")
            },
        )
    }
}

internal data class ProviderCatalogPage(
    val models: List<LlmDiscoveredModel>,
    val cursor: String? = null,
    val total: Long? = null,
    val pageNumber: Long? = null,
    val unsupportedPagination: Boolean = false,
)

private fun JsonElement?.stringValue(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content
private fun JsonElement?.booleanValue(): Boolean? = (this as? JsonPrimitive)?.booleanOrNull
private fun JsonElement?.positiveLong(): Long? = (this as? JsonPrimitive)?.longOrNull?.takeIf { it > 0 }
private fun JsonElement?.nonnegativeLong(): Long? = (this as? JsonPrimitive)?.longOrNull?.takeIf { it >= 0 }
private fun JsonElement?.stringList(): List<String>? = (this as? JsonArray)?.mapNotNull { it.stringValue() }
