package im.hikaru.harness.llm.koog.openai.semantics

import ai.koog.agents.core.tools.ToolDescriptor
import im.hikaru.harness.llm.LlmException
import im.hikaru.harness.llm.ToolSchema
import im.hikaru.harness.llm.koog.KoogLlmErrorCode
import im.hikaru.harness.llm.koog.KoogStreamContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OpenAiToolMapperTest {
    private val mapper = OpenAiToolMapper()
    private val context = KoogStreamContext("custom", "model", "openai", "openai-chat-completions")
    private val generator = OpenAiToolDescriptorSchemaGenerator()

    @Test
    fun nestedSchemaAndConstraintsArePreservedWithoutParameterDescriptions() {
        val parameters = Json.parseToJsonElement("""{
          "type":"object","additionalProperties":false,"required":["argv"],
          "properties":{
            "argv":{"type":"array","minItems":1,"items":{"type":"string"}},
            "timeout":{"type":"integer","minimum":1},
            "background":{"type":"boolean","default":false},
            "options":{"type":"object","properties":{"mode":{"type":"string","enum":["a","b"]}}}
          }
        }""").jsonObject
        val descriptor = mapper.map(listOf(ToolSchema("exec", "Execute", parameters)), context).single()
        assertEquals("exec", descriptor.name)
        assertEquals(parameters, generator.generate(descriptor))
    }

    @Test
    fun duplicateNamesAndNonObjectRootsFailBeforeDispatch() {
        val tool = ToolSchema("exec", "Execute", Json.parseToJsonElement("""{"type":"object"}""").jsonObject)
        assertEquals(KoogLlmErrorCode.INVALID_TOOL_SCHEMA, assertFailsWith<LlmException> {
            mapper.map(listOf(tool, tool), context)
        }.code)
        assertEquals(KoogLlmErrorCode.UNSUPPORTED_TOOL_SCHEMA, assertFailsWith<LlmException> {
            mapper.map(listOf(tool.copy(parameters = Json.parseToJsonElement("""{"type":"array"}""").jsonObject)), context)
        }.code)
    }

    @Test
    fun ordinaryKoogDescriptorsStillUseTheSdkGenerator() {
        val descriptor = ToolDescriptor("ping", "Ping", emptyList(), emptyList())
        assertEquals(
            ai.koog.prompt.executor.clients.openai.base.OpenAICompatibleToolDescriptorSchemaGenerator().generate(descriptor),
            generator.generate(descriptor),
        )
    }
}
