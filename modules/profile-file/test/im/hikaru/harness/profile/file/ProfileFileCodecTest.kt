package im.hikaru.harness.profile.file

import im.hikaru.harness.profile.PatchValue
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ProfileFileCodecTest {
    private val codec = ProfilePatchYamlCodec()

    @Test
    fun shouldDecodeInsertAndWholeConfigOverride() {
        val patches =
            codec.decode(
                """
                - insert:
                    - id: service
                      name: plugin
                      config:
                        first: 1
                - id: service
                  name: plugin
                  config:
                    second: true
                  disabled: false
                """.trimIndent()
            )

        assertEquals("service", patches.first().insert?.single()?.id)
        val config = assertIs<JsonObject>(assertIs<PatchValue.Present<*>>(patches[1].config).value)
        assertEquals(JsonPrimitive(true), config["second"])
    }

    @Test
    fun shouldRejectUnsupportedRuntimeFieldsJsAndSecrets() {
        listOf(
            "- id: x\n  group: true\n",
            "- id: x\n  disabled: !!js process.platform === 'win32'\n",
            "- id: x\n  config:\n    apiKey: exposed\n",
        ).forEach { content ->
            assertFailsWith<IllegalArgumentException> {
                codec.decode(content)
            }
        }
    }

    @Test
    fun explicitNullConfigShouldRemainDifferentFromAbsentConfig() {
        val patches = codec.decode("- id: x\n  config: null\n- id: x\n")

        assertIs<PatchValue.Present<*>>(patches[0].config)
        assertEquals(null, (patches[0].config as PatchValue.Present).value)
        assertEquals(PatchValue.Absent, patches[1].config)
    }
}
