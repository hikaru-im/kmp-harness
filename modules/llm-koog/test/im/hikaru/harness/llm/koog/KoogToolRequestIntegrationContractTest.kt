package im.hikaru.harness.llm.koog

import im.hikaru.harness.llm.GenerateOptions
import im.hikaru.harness.llm.TextBlock
import im.hikaru.harness.llm.ToolSchema
import im.hikaru.harness.llm.createUserMessage
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals

class KoogToolRequestIntegrationContractTest {

    @Test
    fun requestShouldForwardMappedToolDescriptorsWithoutReordering() {
        val model = testKoogModel()
        val request =
            KoogRequestMapper().map(
                options =
                    GenerateOptions(
                        provider = "test",
                        model = model.id,
                        messages =
                            listOf(
                                createUserMessage(listOf(TextBlock("ping")))
                            ),
                        tools =
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
                    ),
                model = model,
            )

        val descriptor = request.tools.single()
        assertEquals("ping", descriptor.name)
        assertEquals("Ping", descriptor.description)
        assertEquals(emptyList(), descriptor.requiredParameters)
        assertEquals(emptyList(), descriptor.optionalParameters)
    }
}
