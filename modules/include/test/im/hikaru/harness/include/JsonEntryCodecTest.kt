package im.hikaru.harness.include

import im.hikaru.harness.loader.Entry
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JsonEntryCodecTest {
    private val codec = JsonEntryCodec()

    @Test
    fun shouldDecodeAndEncodeEntrySnapshots() {
        val entries =
            codec.decode(
                """
                [
                  {
                    "id": "worker",
                    "name": "sample",
                    "config": { "value": "first" }
                  },
                  {
                    "id": "optional",
                    "name": "missing",
                    "disabled": true
                  }
                ]
                """.trimIndent()
            )

        assertEquals(2, entries.size)
        assertEquals("first", entries[0].configValue())
        assertFalse(entries[0].disabled)
        assertTrue(entries[1].disabled)
        assertEquals(entries, codec.decode(codec.encode(entries)))
    }

    @Test
    fun shouldRejectInvalidRootAndFields() {
        assertFailsWith<IllegalArgumentException> {
            codec.decode("{}")
        }
        assertFailsWith<IllegalArgumentException> {
            codec.decode("""[{"id":"", "name":"sample"}]""")
        }
        assertFailsWith<IllegalStateException> {
            codec.decode("""[{"id":"one", "name":"sample", "disabled":"yes"}]""")
        }
    }

    @Test
    fun patchShouldRemainPureAndGuardIdentity() {
        val original =
            listOf(
                Entry(
                    id = "worker",
                    name = "sample",
                    config = JsonObject(mapOf("value" to JsonPrimitive("first"))),
                )
            )
        val patch =
            PatchEntry(
                id = "worker",
                expectedName = "sample",
            ) { entry ->
                entry.copy(disabled = true)
            }

        val result = patch.apply(original)

        assertFalse(original.single().disabled)
        assertTrue(result.single().disabled)
        assertFailsWith<IllegalStateException> {
            PatchEntry("worker") { it.copy(id = "changed") }.apply(original)
        }
        assertFailsWith<IllegalStateException> {
            PatchEntry("missing") { it }.apply(original)
        }
    }
}

private fun Entry.configValue(): String =
    ((config as JsonObject)["value"] as JsonPrimitive).content
