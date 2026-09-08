package im.hikaru.harness.include

import im.hikaru.harness.loader.Entry

/** Separates a configuration format from the resource used to store it. */
public interface EntryCodec {
    public fun decode(content: String): List<Entry>

    public fun encode(entries: List<Entry>): String
}

/** A pure transform applied to a full snapshot before reconciliation. */
public fun interface EntryTransform {
    public fun apply(entries: List<Entry>): List<Entry>
}

public class CompositeEntryTransform(
    transforms: List<EntryTransform>,
) : EntryTransform {
    private val transforms = transforms.toList()

    override fun apply(entries: List<Entry>): List<Entry> =
        transforms.fold(entries) { current, transform ->
            transform.apply(current)
        }
}

/**
 * Updates exactly one entry without making patching part of Loader itself.
 * The id remains stable because it is the reconciliation identity.
 */
public class PatchEntry(
    private val id: String,
    private val expectedName: String? = null,
    private val transform: (Entry) -> Entry,
) : EntryTransform {
    override fun apply(entries: List<Entry>): List<Entry> {
        var found = false
        val patched =
            entries.map { entry ->
                if (entry.id != id) {
                    entry
                } else {
                    check(!found) { "Duplicate entry id: $id" }
                    found = true
                    if (expectedName != null) {
                        check(entry.name == expectedName) {
                            "Entry $id name mismatch: expected $expectedName, actual ${entry.name}"
                        }
                    }

                    transform(entry).also { result ->
                        check(result.id == id) {
                            "Entry transform cannot change id $id to ${result.id}"
                        }
                    }
                }
            }

        check(found) { "Entry not found: $id" }
        return patched
    }
}
