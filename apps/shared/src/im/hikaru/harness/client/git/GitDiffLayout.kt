package im.hikaru.harness.client.git

/**
 * Diff layouts offered by the observed `codex.diffView` selector (D01): unified, split and an
 * automatic per-file choice. The original persists the choice in a store; the persisted default is
 * not observed, so the page keeps [Unified] as its own default.
 */
public enum class GitDiffLayout { Unified, Split, Auto }

public enum class GitDiffSplitRowKind { Context, Change, Addition, Deletion }

/** One aligned row of a split diff: the old side on the left, the new side on the right. */
public data class GitDiffSplitRow(
    val left: String,
    val right: String,
    val kind: GitDiffSplitRowKind,
    /** 1-based old-file line number shown in the left gutter; null where the original buffers it. */
    val oldNumber: Int? = null,
    /** 1-based new-file line number shown in the right gutter; null where the original buffers it. */
    val newNumber: Int? = null,
)

public data class GitDiffSplitHunk(val header: String, val rows: List<GitDiffSplitRow>)

/**
 * Aligns a unified hunk body (a [GitDiffFile.body]) into paired left/right rows.
 *
 * `@@` headers start a hunk; a run of deletions and the run of additions that replaces them are
 * paired in order; context lines appear on both sides; the leading marker character is stripped.
 * `\ No newline` markers are ignored. Lines inside a hunk always carry a `+`/`-`/` ` marker, so a
 * content line that itself starts with `++`/`--` is still classified by its own marker.
 *
 * Each row also carries the old-file and new-file line numbers the two gutters print, counted from
 * the hunk header exactly like [parseGitDiffLines] counts the unified gutter.
 */
public fun splitDiffBody(body: String): List<GitDiffSplitHunk> {
    if (body.isEmpty()) return emptyList()
    val hunks = mutableListOf<GitDiffSplitHunk>()
    val rows = mutableListOf<GitDiffSplitRow>()
    val deletions = mutableListOf<String>()
    val additions = mutableListOf<String>()
    val deletionNumbers = mutableListOf<Int>()
    val additionNumbers = mutableListOf<Int>()
    var header: String? = null
    var oldNumber = 0
    var newNumber = 0

    fun flush() {
        val size = maxOf(deletions.size, additions.size)
        for (index in 0 until size) {
            val left = deletions.getOrNull(index)
            val right = additions.getOrNull(index)
            val kind = when {
                left != null && right != null -> GitDiffSplitRowKind.Change
                left != null -> GitDiffSplitRowKind.Deletion
                else -> GitDiffSplitRowKind.Addition
            }
            rows += GitDiffSplitRow(
                left = left.orEmpty(),
                right = right.orEmpty(),
                kind = kind,
                // The left pane keeps the old-file number, the right pane the new-file number; a
                // filled-in blank side stays without a number, matching the original's buffered gutter.
                oldNumber = if (left != null) deletionNumbers[index] else null,
                newNumber = if (right != null) additionNumbers[index] else null,
            )
        }
        deletions.clear()
        additions.clear()
        deletionNumbers.clear()
        additionNumbers.clear()
    }

    fun closeHunk() {
        flush()
        val current = header
        if (current != null) {
            hunks += GitDiffSplitHunk(current, rows.toList())
            rows.clear()
        }
    }

    for (raw in body.split('\n')) {
        when {
            raw.startsWith("@@") -> {
                closeHunk()
                header = raw
                // Same `@@ -oldStart +newStart @@` contract as the unified parser: the counters
                // restart at the numbers the header states, so both gutters stay in step with it.
                HUNK_HEADER.find(raw)?.let { match ->
                    oldNumber = match.groupValues[1].toInt()
                    newNumber = match.groupValues[2].toInt()
                }
            }
            raw.startsWith("\\") -> Unit
            raw.startsWith("+") -> {
                additions += raw.substring(1)
                additionNumbers += newNumber
                newNumber++
            }
            raw.startsWith("-") -> {
                // Additions before deletions belong to the same run; pair them in the emitted order.
                if (additions.isNotEmpty()) flush()
                deletions += raw.substring(1)
                deletionNumbers += oldNumber
                oldNumber++
            }
            else -> {
                flush()
                val content = raw.removePrefix(" ")
                rows += GitDiffSplitRow(
                    content, content, GitDiffSplitRowKind.Context,
                    oldNumber = oldNumber, newNumber = newNumber,
                )
                oldNumber++
                newNumber++
            }
        }
    }
    closeHunk()
    return hunks
}

/**
 * Auto resolves to split only on a wide pane whose file has both additions and removals, matching the
 * observed auto description "split for additions and removals, inline for narrow panes or one-sided
 * changes"; otherwise it stays unified.
 */
public fun effectiveGitDiffLayout(
    selected: GitDiffLayout,
    file: GitDiffFile,
    narrowPane: Boolean,
): GitDiffLayout = when (selected) {
    GitDiffLayout.Unified, GitDiffLayout.Split -> selected
    GitDiffLayout.Auto ->
        if (!narrowPane && file.addedLines > 0 && file.deletedLines > 0) GitDiffLayout.Split else GitDiffLayout.Unified
}
