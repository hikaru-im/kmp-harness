package im.hikaru.harness.llm.koog.openai.semantics

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.executor.clients.openai.base.OpenAICompatibleToolDescriptorSchemaGenerator
import im.hikaru.harness.llm.LlmException
import im.hikaru.harness.llm.ToolSchema
import im.hikaru.harness.llm.koog.KoogLlmErrorCode
import im.hikaru.harness.llm.koog.KoogStreamContext
import im.hikaru.harness.llm.koog.KoogToolMapper
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** OpenAI accepts JSON Schema directly; do not narrow it to Koog's parameter type vocabulary. */
internal class OpenAiToolMapper : KoogToolMapper {
    override fun map(tools: List<ToolSchema>?, context: KoogStreamContext): List<ToolDescriptor> {
        val names = mutableSetOf<String>()
        return tools.orEmpty().map { tool ->
            if (tool.name.isBlank() || !names.add(tool.name)) {
                throw LlmException("Tool names must be non-blank and unique", KoogLlmErrorCode.INVALID_TOOL_SCHEMA)
            }
            if (tool.parameters["type"] != JsonPrimitive("object")) {
                throw LlmException("OpenAI tool ${tool.name} requires an object schema", KoogLlmErrorCode.UNSUPPORTED_TOOL_SCHEMA)
            }
            OpenAiSchemaToolDescriptor(tool)
        }
    }
}

/** Carries the immutable original schema to the SDK's schema-generation extension point. */
private class OpenAiSchemaToolDescriptor(tool: ToolSchema) : ToolDescriptor(
    name = tool.name,
    description = tool.description,
    requiredParameters = emptyList(),
    optionalParameters = emptyList(),
) {
    val parameters: JsonObject = tool.parameters
}

internal class OpenAiToolDescriptorSchemaGenerator : OpenAICompatibleToolDescriptorSchemaGenerator() {
    override fun generate(toolDescriptor: ToolDescriptor): JsonObject =
        (toolDescriptor as? OpenAiSchemaToolDescriptor)?.parameters ?: super.generate(toolDescriptor)
}
