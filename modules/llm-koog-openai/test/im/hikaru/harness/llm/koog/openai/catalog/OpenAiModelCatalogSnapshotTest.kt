package im.hikaru.harness.llm.koog.openai.catalog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OpenAiModelCatalogSnapshotTest {
    @Test
    fun bundledSnapshotShouldDecodeAllPinnedModels() {
        assertEquals(29, OpenAiModelCatalogSnapshot.models.size)
    }

    @Test
    fun decoderShouldRejectInvalidTypesLimitsAndDuplicateCapabilities() {
        val invalidSnapshots =
            listOf(
                validSnapshot.replace("\"schemaVersion\": 1", "\"schemaVersion\": \"1\""),
                validSnapshot.replace("\"id\": \"model\"", "\"id\": 1"),
                validSnapshot.replace("\"contextWindow\": 128", "\"contextWindow\": 0"),
                validSnapshot.replace("[\"text\", \"image\"]", "[\"text\", \"text\"]"),
                validSnapshot.replace("[\"low\", \"high\"]", "[\"low\", \"low\"]"),
                validSnapshot.replace("\"toolCall\": true", "\"toolCall\": \"true\""),
                validSnapshot.replace("\"structuredOutput\": false", "\"structuredOutput\": null"),
            )

        invalidSnapshots.forEach { content ->
            assertFailsWith<IllegalArgumentException> {
                decodeOpenAiModelCatalogSnapshot(content)
            }
        }
    }

    private val validSnapshot =
        """
        {
          "schemaVersion": 1,
          "source": {
            "repository": "https://github.com/anomalyco/models.dev",
            "revision": "26703fa74cc2a990d4a095d5dce6d79dd2202524",
            "license": "MIT",
            "notice": "Generated from models.dev; see repository license."
          },
          "models": [
            {
              "id": "model",
              "name": "Model",
              "description": "Model description",
              "contextWindow": 128,
              "maxOutputTokens": 32,
              "inputModalities": ["text", "image"],
              "reasoning": true,
              "reasoningEfforts": ["low", "high"],
              "toolCall": true,
              "structuredOutput": false,
              "temperature": true
            }
          ]
        }
        """.trimIndent()
}
