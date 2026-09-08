package im.hikaru.harness.include

import im.hikaru.harness.loader.Entry
import kotlinx.serialization.json.*

/**
 * JSON snapshot codec. Plugin configuration stays as [JsonElement]; Registry's
 * ConfigAdapter remains the single place that converts it to a plugin type.
 */
public class JsonEntryCodec(
    private val json: Json =
        Json {
            prettyPrint = true
            explicitNulls = false
        },
    private val encodeConfig: (Any?) -> JsonElement? = ::defaultEncodeConfig,
) : EntryCodec {
    override fun decode(content: String): List<Entry> {
        val root = json.parseToJsonElement(content)
        require(root is JsonArray) {
            "Include JSON root must be an array"
        }

        return root.mapIndexed { index, element ->
            require(element is JsonObject) {
                "Include entry at index $index must be an object"
            }

            val id = element.string("id", index)
            val name = element.string("name", index)
            val disabledElement = element["disabled"]
            val disabled =
                when (disabledElement) {
                    null -> false
                    is JsonPrimitive ->
                        disabledElement.booleanOrNull
                            ?: error("Include entry $id field disabled must be a boolean")
                    else -> error("Include entry $id field disabled must be a boolean")
                }

            Entry(
                id = id,
                name = name,
                config = element["config"]?.takeUnless { it === JsonNull },
                disabled = disabled,
            )
        }
    }

    override fun encode(entries: List<Entry>): String {
        val root =
            JsonArray(
                entries.map { entry ->
                    val values = linkedMapOf<String, JsonElement>()
                    values["id"] = JsonPrimitive(entry.id)
                    values["name"] = JsonPrimitive(entry.name)
                    if (entry.disabled) {
                        values["disabled"] = JsonPrimitive(true)
                    }
                    encodeConfig(entry.config)?.let { config ->
                        values["config"] = config
                    }
                    JsonObject(values)
                }
            )

        return json.encodeToString(root)
    }

    private fun JsonObject.string(
        key: String,
        index: Int,
    ): String {
        val value = (get(key) as? JsonPrimitive)?.contentOrNull
        require(!value.isNullOrBlank()) {
            "Include entry at index $index field $key must be a non-blank string"
        }
        return value
    }

    private companion object {
        fun defaultEncodeConfig(value: Any?): JsonElement? =
            when (value) {
                null -> null
                is JsonElement -> value.takeUnless { it === JsonNull }
                is String -> JsonPrimitive(value)
                is Boolean -> JsonPrimitive(value)
                is Number -> JsonPrimitive(value)
                else -> error(
                    "Cannot encode plugin config of type ${value::class}; " +
                        "provide JsonElement or a custom encodeConfig adapter"
                )
            }
    }
}
