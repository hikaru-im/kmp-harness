package im.hikaru.harness.llm.koog

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import im.hikaru.harness.llm.LlmException
import im.hikaru.harness.llm.ToolSchema
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** 将 Harness ToolSchema 转换成 Koog ToolDescriptor 的边界。 */
fun interface KoogToolMapper {
    fun map(
        tools: List<ToolSchema>?,
        context: KoogStreamContext,
    ): List<ToolDescriptor>
}

/** 只映射可由 Koog ToolDescriptor 无损表达的 object + primitive schema 子集。 */
internal class DefaultKoogToolMapper : KoogToolMapper {
    override fun map(
        tools: List<ToolSchema>?,
        @Suppress("UNUSED_PARAMETER")
        context: KoogStreamContext,
    ): List<ToolDescriptor> {
        if (tools.isNullOrEmpty()) {
            return emptyList()
        }

        val names = mutableSetOf<String>()
        return tools.map { tool ->
            if (tool.name.isBlank()) {
                invalidToolSchema("Koog tool name must not be blank")
            }
            if (!names.add(tool.name)) {
                invalidToolSchema("Duplicate Koog tool name: ${tool.name}")
            }
            tool.toKoogDescriptor()
        }
    }
}

private val supportedObjectKeys = setOf("type", "properties", "required")

private val supportedPropertyKeys = setOf("type", "description")

private fun ToolSchema.toKoogDescriptor(): ToolDescriptor {
    val unsupportedKeys = parameters.keys - supportedObjectKeys
    if (unsupportedKeys.isNotEmpty()) {
        unsupportedToolSchema(
            "Koog tool $name contains unsupported object constraint(s): " +
                unsupportedKeys.sorted().joinToString()
        )
    }

    val rootType = parameters.requiredString("type", "tool $name")
    if (rootType != "object") {
        unsupportedToolSchema("Koog tool $name requires an object schema, got $rootType")
    }

    val properties = parameters.optionalObject("properties", "tool $name")
    val requiredNames = parameters.requiredNames(properties, name)
    val required = mutableListOf<ToolParameterDescriptor>()
    val optional = mutableListOf<ToolParameterDescriptor>()

    properties.forEach { (parameterName, element) ->
        if (parameterName.isBlank()) {
            invalidToolSchema("Koog tool $name contains a blank parameter name")
        }
        val property =
            element as? JsonObject
                ?: invalidToolSchema(
                    "Koog tool $name parameter $parameterName must be an object"
                )
        val unsupportedPropertyKeys = property.keys - supportedPropertyKeys
        if (unsupportedPropertyKeys.isNotEmpty()) {
            unsupportedToolSchema(
                "Koog tool $name parameter $parameterName contains unsupported constraint(s): " +
                    unsupportedPropertyKeys.sorted().joinToString()
            )
        }
        val descriptor =
            ToolParameterDescriptor(
                name = parameterName,
                description =
                    property.requiredString(
                        key = "description",
                        owner = "tool $name parameter $parameterName",
                    ),
                type = property.toKoogParameterType(name, parameterName),
            )
        if (parameterName in requiredNames) {
            required += descriptor
        } else {
            optional += descriptor
        }
    }

    return ToolDescriptor(
        name = name,
        description = description,
        requiredParameters = required,
        optionalParameters = optional,
    )
}

private fun JsonObject.requiredNames(
    properties: JsonObject,
    toolName: String,
): Set<String> {
    val value = this["required"] ?: return emptySet()
    val array =
        value as? JsonArray
            ?: invalidToolSchema("Koog tool $toolName required must be an array")
    val names =
        array.map { element ->
            val primitive =
                element as? JsonPrimitive
                    ?: invalidToolSchema(
                        "Koog tool $toolName required entries must be strings"
                    )
            if (!primitive.isString || primitive.content.isBlank()) {
                invalidToolSchema(
                    "Koog tool $toolName required entries must be non-blank strings"
                )
            }
            primitive.content
        }
    if (names.toSet().size != names.size) {
        invalidToolSchema("Koog tool $toolName required contains duplicate names")
    }
    val unknown = names.filterNot(properties::containsKey)
    if (unknown.isNotEmpty()) {
        invalidToolSchema(
            "Koog tool $toolName requires unknown parameter(s): " +
                unknown.joinToString()
        )
    }
    return names.toSet()
}

private fun JsonObject.toKoogParameterType(
    toolName: String,
    parameterName: String,
): ToolParameterType =
    when (val type = requiredString("type", "tool $toolName parameter $parameterName")) {
        "string" -> ToolParameterType.String
        "integer" -> ToolParameterType.Integer
        "number" -> ToolParameterType.Float
        "boolean" -> ToolParameterType.Boolean
        else ->
            unsupportedToolSchema(
                "Koog tool $toolName parameter $parameterName uses unsupported type $type"
            )
    }

private fun JsonObject.requiredString(
    key: String,
    owner: String,
): String {
    val value = this[key]
        ?: invalidToolSchema("Koog $owner requires string field $key")
    val primitive =
        value as? JsonPrimitive
            ?: invalidToolSchema("Koog $owner field $key must be a string")
    if (!primitive.isString) {
        invalidToolSchema("Koog $owner field $key must be a string")
    }
    return primitive.content
}

private fun JsonObject.optionalObject(
    key: String,
    owner: String,
): JsonObject {
    val value = this[key] ?: return JsonObject(emptyMap())
    return value as? JsonObject
        ?: invalidToolSchema("Koog $owner field $key must be an object")
}

private fun invalidToolSchema(message: String): Nothing =
    throw LlmException(
        message = message,
        code = KoogLlmErrorCode.INVALID_TOOL_SCHEMA,
    )

private fun unsupportedToolSchema(message: String): Nothing =
    throw LlmException(
        message = message,
        code = KoogLlmErrorCode.UNSUPPORTED_TOOL_SCHEMA,
    )
