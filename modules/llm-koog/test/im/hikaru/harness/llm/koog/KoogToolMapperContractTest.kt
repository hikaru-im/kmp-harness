package im.hikaru.harness.llm.koog

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.agents.core.tools.ToolParameterDescriptor
import ai.koog.agents.core.tools.ToolParameterType
import im.hikaru.harness.llm.LlmException
import im.hikaru.harness.llm.ToolSchema
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class KoogToolMapperContractTest {

    private val mapper = DefaultKoogToolMapper()

    @Test
    fun primitiveObjectSchemaShouldPreserveRequiredAndOptionalParameters() {
        val schema =
            ToolSchema(
                name = "lookup",
                description = "Look up a value",
                parameters =
                    buildJsonObject {
                        put("type", "object")
                        put(
                            "properties",
                            JsonObject(
                                mapOf(
                                    "query" to property("string", "Search query"),
                                    "limit" to property("integer", "Maximum results"),
                                    "score" to property("number", "Match score"),
                                    "exact" to property("boolean", "Exact match"),
                                )
                            ),
                        )
                        put("required", JsonArray(listOf(JsonPrimitive("query"))))
                    },
            )

        assertEquals(
            listOf(
                ToolDescriptor(
                    name = "lookup",
                    description = "Look up a value",
                    requiredParameters =
                        listOf(
                            ToolParameterDescriptor(
                                name = "query",
                                description = "Search query",
                                type = ToolParameterType.String,
                            )
                        ),
                    optionalParameters =
                        listOf(
                            ToolParameterDescriptor(
                                name = "limit",
                                description = "Maximum results",
                                type = ToolParameterType.Integer,
                            ),
                            ToolParameterDescriptor(
                                name = "score",
                                description = "Match score",
                                type = ToolParameterType.Float,
                            ),
                            ToolParameterDescriptor(
                                name = "exact",
                                description = "Exact match",
                                type = ToolParameterType.Boolean,
                            ),
                        ),
                )
            ),
            mapper.map(listOf(schema), testKoogStreamContext()),
        )
    }

    @Test
    fun emptyObjectSchemaShouldRemainAZeroArgumentTool() {
        val descriptor =
            mapper.map(
                listOf(
                    ToolSchema(
                        name = "ping",
                        description = "Ping",
                        parameters =
                            buildJsonObject {
                                put("type", "object")
                                put("properties", JsonObject(emptyMap()))
                            },
                    )
                ),
                testKoogStreamContext(),
            ).single()

        assertEquals("ping", descriptor.name)
        assertEquals(emptyList(), descriptor.requiredParameters)
        assertEquals(emptyList(), descriptor.optionalParameters)
    }

    @Test
    fun malformedOrUnsupportedSchemasShouldUseStableCodes() {
        val invalid =
            listOf(
                buildJsonObject {
                    put("type", "object")
                    put("properties", JsonObject(emptyMap()))
                    put("required", JsonArray(listOf(JsonPrimitive("missing"))))
                },
                buildJsonObject {
                    put("type", "object")
                    put(
                        "properties",
                        JsonObject(mapOf("value" to buildJsonObject { }))
                    )
                },
                buildJsonObject {
                    put("type", "object")
                    put(
                        "properties",
                        JsonObject(
                            mapOf(
                                "value" to
                                    buildJsonObject {
                                        put("type", "string")
                                    }
                            )
                        )
                    )
                },
            )
        invalid.forEach { parameters ->
            val error =
                assertFailsWith<LlmException> {
                    mapper.map(
                        listOf(ToolSchema("invalid", "Invalid", parameters)),
                        testKoogStreamContext(),
                    )
                }
            assertEquals(KoogLlmErrorCode.INVALID_TOOL_SCHEMA, error.code)
        }

        val unsupported =
            listOf(
                buildJsonObject { put("type", "string") },
                buildJsonObject {
                    put("type", "object")
                    put("properties", JsonObject(emptyMap()))
                    put("additionalProperties", false)
                },
                buildJsonObject {
                    put("type", "object")
                    put(
                        "properties",
                        JsonObject(
                            mapOf(
                                "values" to
                                    buildJsonObject {
                                        put("type", "array")
                                        put("description", "Values")
                                    }
                            )
                        )
                    )
                },
            )
        unsupported.forEach { parameters ->
            val error =
                assertFailsWith<LlmException> {
                    mapper.map(
                        listOf(ToolSchema("unsupported", "Unsupported", parameters)),
                        testKoogStreamContext(),
                    )
                }
            assertEquals(KoogLlmErrorCode.UNSUPPORTED_TOOL_SCHEMA, error.code)
        }
    }

    @Test
    fun duplicateToolNamesShouldFailBeforeReturningDescriptors() {
        val schema =
            ToolSchema(
                name = "lookup",
                description = "Lookup",
                parameters =
                    buildJsonObject {
                        put("type", "object")
                        put("properties", JsonObject(emptyMap()))
                    },
            )

        val error =
            assertFailsWith<LlmException> {
                mapper.map(
                    listOf(schema, schema.copy(description = "Duplicate")),
                    testKoogStreamContext(),
                )
            }
        assertEquals(KoogLlmErrorCode.INVALID_TOOL_SCHEMA, error.code)
    }

    private fun property(
        type: String,
        description: String,
    ): JsonObject =
        buildJsonObject {
            put("type", type)
            put("description", description)
        }
}
