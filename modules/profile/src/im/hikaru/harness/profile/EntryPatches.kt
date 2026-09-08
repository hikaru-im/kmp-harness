package im.hikaru.harness.profile

import im.hikaru.harness.loader.Entry

/**
 * Applies the portable DSH patch semantics without mutating the input.
 *
 * Every source layer must be flattened before this call. That allows a later
 * patch to target a row inserted by an earlier bundle or user layer.
 */
public fun applyEntryPatches(
    data: List<Entry>,
    patches: List<EntryPatch>,
    warn: (String) -> Unit = {},
): List<Entry> {
    val entries = data.mapTo(mutableListOf()) { entry -> entry.copy() }
    val indices = linkedMapOf<String, Int>()

    fun index(entry: Entry) {
        indices[entry.id] = entries.indexOfLast { candidate -> candidate === entry }
    }

    entries.forEachIndexed { index, entry ->
        indices[entry.id] = index
    }

    patches.forEach { patch ->
        val inserted = patch.insert
        if (inserted != null) {
            if (patch.id != null) {
                val target = indices[patch.id]
                if (target == null) {
                    warn("patch insert: entry '${patch.id}' not found")
                } else {
                    warn("patch insert: entry '${patch.id}' is not a group")
                }
                return@forEach
            }

            inserted.forEach { source ->
                val detached = source.copy()
                entries += detached
                index(detached)
            }
            return@forEach
        }

        val id = patch.id
        if (id == null) {
            warn("patch: id is required for non-insert patches")
            return@forEach
        }
        val targetIndex = indices[id]
        if (targetIndex == null) {
            warn("patch: entry '$id' not found")
            return@forEach
        }
        val target = entries[targetIndex]
        if (patch.name != null && patch.name != target.name) {
            warn(
                "patch: name mismatch for '$id' " +
                    "(expected '${target.name}', got '${patch.name}'), skipping"
            )
            return@forEach
        }

        entries[targetIndex] =
            target.copy(
                config =
                    when (val value = patch.config) {
                        PatchValue.Absent -> target.config
                        is PatchValue.Present -> value.value
                    },
                disabled =
                    when (val value = patch.disabled) {
                        PatchValue.Absent -> target.disabled
                        is PatchValue.Present -> value.value
                    },
            )
    }

    return entries
}

/** Applies all layers in one flattened call, preserving DSH cross-layer targeting. */
public fun composeProfile(
    root: List<Entry> = emptyList(),
    layers: List<List<EntryPatch>>,
): ProfileComposition {
    val warnings = mutableListOf<String>()
    val entries =
        applyEntryPatches(
            data = root,
            patches = layers.flatten(),
            warn = warnings::add,
        )
    return ProfileComposition(entries, warnings)
}
