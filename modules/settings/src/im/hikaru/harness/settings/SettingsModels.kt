package im.hikaru.harness.settings

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject

private val NAMESPACE_PATTERN =
    Regex("^[a-z][a-z0-9-]*$")

@JvmInline
value class SettingsNamespace(
    val value: String,
) {
    init {
        require(NAMESPACE_PATTERN.matches(value)) {
            "Settings namespace must match ${NAMESPACE_PATTERN.pattern}"
        }
    }

    override fun toString(): String =
        value
}

fun settingsNamespace(value: String): SettingsNamespace =
    SettingsNamespace(value)

enum class SettingsApplies {
    LIVE,
    RESTART,
}

data class SettingsDescriptor(
    val namespace: SettingsNamespace,
    val value: JsonObject,
    val user: JsonObject?,
    val base: JsonObject,
    val revision: Long,
    val applies: SettingsApplies,
    val secrets: Set<List<String>> = emptySet(),
)

data class SettingsChange(
    val namespace: SettingsNamespace,
    val next: JsonObject,
    val previous: JsonObject,
    val revision: Long,
)

class SettingsConflictException(
    val namespace: SettingsNamespace,
    val expectedRevision: Long,
    val actualRevision: Long,
) : IllegalStateException(
    "Settings namespace '$namespace' changed since it was read " +
        "(expected revision $expectedRevision, actual $actualRevision)"
)

/**
 * Merge JSON object layers without mutating either input.
 *
 * Nested objects merge recursively; arrays and scalar values are replaced.
 */
fun mergeSettingsObjects(
    lower: JsonObject,
    upper: JsonObject,
): JsonObject =
    buildJsonObject {
        lower.forEach { (key, value) ->
            put(key, value)
        }
        upper.forEach { (key, value) ->
            val previous = lower[key]
            if (previous is JsonObject && value is JsonObject) {
                put(key, mergeSettingsObjects(previous, value))
            } else {
                put(key, value)
            }
        }
    }

/**
 * Redact configured secret paths for descriptors sent to configuration
 * surfaces. The raw service value remains unchanged.
 */
fun redactSettingsObject(
    value: JsonObject,
    secretPaths: Set<List<String>>,
): JsonObject {
    if (secretPaths.isEmpty()) return value

    fun redact(
        element: JsonElement,
        path: List<String>,
    ): JsonElement {
        if (secretPaths.any { it == path }) {
            return JsonPrimitive("***")
        }
        val jsonObject = element as? JsonObject ?: return element
        return buildJsonObject {
            jsonObject.forEach { (key, child) ->
                put(key, redact(child, path + key))
            }
        }
    }

    return redact(value, emptyList()).jsonObject
}
