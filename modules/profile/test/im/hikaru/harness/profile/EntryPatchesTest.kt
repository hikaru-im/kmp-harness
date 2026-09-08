package im.hikaru.harness.profile

import im.hikaru.harness.loader.Entry
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class EntryPatchesTest {
    @Test
    fun rootInsertShouldBeDetachedAndTargetableByLaterPatch() {
        val inserted = Entry("provider", "provider", JsonObject(mapOf("old" to JsonPrimitive(1))))
        val replacement = JsonObject(mapOf("new" to JsonPrimitive(2)))

        val result =
            applyEntryPatches(
                data = emptyList(),
                patches =
                    listOf(
                        EntryPatch(insert = listOf(inserted)),
                        EntryPatch(
                            id = "provider",
                            config = PatchValue.Present(replacement),
                            disabled = PatchValue.Present(true),
                        ),
                    ),
            )

        assertNotSame(inserted, result.single())
        assertEquals(replacement, result.single().config)
        assertTrue(result.single().disabled)
        assertFalse(inserted.disabled)
    }

    @Test
    fun configOverrideShouldReplaceTheWholeObject() {
        val original =
            Entry(
                id = "service",
                name = "service",
                config = JsonObject(mapOf("keep" to JsonPrimitive(true), "replace" to JsonPrimitive(1))),
            )
        val replacement = JsonObject(mapOf("replace" to JsonPrimitive(2)))

        val result =
            applyEntryPatches(
                listOf(original),
                listOf(EntryPatch(id = "service", config = PatchValue.Present(replacement))),
            )

        assertEquals(replacement, result.single().config)
    }

    @Test
    fun nameAssertionAndMissingTargetsShouldWarnAndSkip() {
        val warnings = mutableListOf<String>()
        val original = Entry("service", "actual")

        val result =
            applyEntryPatches(
                data = listOf(original),
                patches =
                    listOf(
                        EntryPatch(
                            id = "service",
                            name = "different",
                            disabled = PatchValue.Present(true),
                        ),
                        EntryPatch(id = "missing", disabled = PatchValue.Present(true)),
                        EntryPatch(disabled = PatchValue.Present(true)),
                    ),
                warn = warnings::add,
            )

        assertEquals(listOf(original), result)
        assertEquals(3, warnings.size)
        assertTrue(warnings[0].contains("name mismatch"))
        assertTrue(warnings[1].contains("not found"))
        assertTrue(warnings[2].contains("id is required"))
    }

    @Test
    fun allLayersShouldBeFlattenedBeforeApplying() {
        val result =
            composeProfile(
                layers =
                    listOf(
                        listOf(EntryPatch(insert = listOf(Entry("added", "plugin")))),
                        listOf(EntryPatch(id = "added", disabled = PatchValue.Present(true))),
                    )
            )

        assertTrue(result.entries.single().disabled)
    }

    @Test
    fun targetedInsertShouldMatchNonGroupDshWarning() {
        val warnings = mutableListOf<String>()

        val result =
            applyEntryPatches(
                data = listOf(Entry("plain", "plugin")),
                patches = listOf(EntryPatch(id = "plain", insert = listOf(Entry("nested", "plugin")))),
                warn = warnings::add,
            )

        assertEquals(listOf("plain"), result.map(Entry::id))
        assertTrue(warnings.single().contains("not a group"))
    }
}
