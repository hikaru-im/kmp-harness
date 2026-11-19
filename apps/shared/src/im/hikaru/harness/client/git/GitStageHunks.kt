package im.hikaru.harness.client.git

/**
 * Builds the single-hunk patch a stage/unstage action sends for one hunk of [file], mirroring Codex
 * `review/diff-patch-builder.ts` `extractHunkPatchText`/`buildHunkPatch`: keep only the header lines
 * Git needs (`diff --git`, an add/delete marker, `index`, `---`, `+++`), then append exactly one
 * `@@` section. Everything else in the file's header (rename/copy/mode/binary markers) is dropped,
 * so the backend's strict validator accepts the result and Git cannot read it as another operation.
 *
 * Returns null when [hunkIndex] does not name a hunk of [file]; it never fabricates a hunk.
 */
public fun gitHunkPatch(file: GitDiffFile, hunkIndex: Int): String? {
    val hunks = parseGitDiffHunks(file.body)
    val hunk = hunks.getOrNull(hunkIndex) ?: return null
    val header = file.header.lineSequence().filter { line ->
        line.startsWith("diff --git ") || line.startsWith("new file mode ") ||
            line.startsWith("deleted file mode ") || line.startsWith("index ") ||
            line.startsWith("--- ") || line.startsWith("+++ ")
    }.toList()
    if (header.isEmpty() || header.none { it.startsWith("--- ") } || header.none { it.startsWith("+++ ") }) return null
    val body = buildString {
        header.forEach { append(it).append('\n') }
        append("@@ -${hunk.oldStart},${hunk.oldCount} +${hunk.newStart},${hunk.newCount} @@")
        append(hunk.section).append('\n')
        hunk.lines.forEach { append(it).append('\n') }
    }
    return body
}
