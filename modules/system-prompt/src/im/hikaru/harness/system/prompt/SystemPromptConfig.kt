package im.hikaru.harness.system.prompt

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/** Deployment-authored system prompt configuration, aligned with DSH. */
public data class SystemPromptConfig(
    public val includeHarnessIdentity: Boolean = true,
    public val includeRuntimeContext: Boolean = true,
    public val personaPrefix: String = "",
    public val personaSuffix: String = "",
    public val toolOrder: List<String>? = null,
) {
    init {
        if (toolOrder != null) {
            require(toolOrder.distinct().size == toolOrder.size) {
                "toolOrder lists a tool more than once"
            }
            require(TOOL_ORDER_REST in toolOrder) {
                "toolOrder must contain the \"$TOOL_ORDER_REST\" rest entry"
            }
            require(toolOrder.count { it == TOOL_ORDER_REST } == 1) {
                "toolOrder must contain the \"$TOOL_ORDER_REST\" rest entry exactly once"
            }
        }
    }
}

/** Reserved toolOrder marker for every schema not named explicitly. */
public const val TOOL_ORDER_REST: String = "<unlisted-tools>"

internal fun decodeSystemPromptConfig(value: JsonObject): SystemPromptConfig {
    val allowed = setOf(
        "includeHarnessIdentity",
        "includeRuntimeContext",
        "personaPrefix",
        "personaSuffix",
        "toolOrder",
    )
    require(value.keys.all(allowed::contains)) {
        "system-prompt config contains unknown fields: ${(value.keys - allowed).sorted().joinToString()}"
    }
    fun boolean(name: String): Boolean? = value[name]?.let { element ->
        (element as? JsonPrimitive)?.takeUnless(JsonPrimitive::isString)?.booleanOrNull
            ?: error("system-prompt $name must be a boolean")
    }
    fun string(name: String): String? = value[name]?.let { element ->
        (element as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content
            ?: error("system-prompt $name must be a string")
    }
    val toolOrder = value["toolOrder"]?.let { element ->
        val array = element as? JsonArray
            ?: error("system-prompt toolOrder must be an array of strings")
        array.map { item ->
            (item as? JsonPrimitive)?.takeIf(JsonPrimitive::isString)?.content
                ?: error("system-prompt toolOrder must be an array of strings")
        }
    }
    return SystemPromptConfig(
        includeHarnessIdentity = boolean("includeHarnessIdentity") ?: true,
        includeRuntimeContext = boolean("includeRuntimeContext") ?: true,
        personaPrefix = string("personaPrefix") ?: "",
        personaSuffix = string("personaSuffix") ?: "",
        toolOrder = toolOrder,
    )
}
