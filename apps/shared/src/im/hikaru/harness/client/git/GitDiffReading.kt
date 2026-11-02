package im.hikaru.harness.client.git

/**
 * Reading aids for one unified diff, kept separate from the Compose page so the thresholds, hunk
 * arithmetic and search can be tested without a UI.
 *
 * Restored evidence: `review/editor-diff-page.tsx` opens every file when a diff has at most
 * `MAX_AUTO_OPEN_FILES = 25` files and at most `MAX_AUTO_OPEN_CHANGED_LINES = 2000` changed lines.
 * `review/large-diff-banner.tsx` owns the single-file banner copy but exports no threshold of its
 * own, so Harness reuses the same two limits for that mode and records the choice here.
 */
public const val GitDiffAutoOpenMaxFiles: Int = 25
public const val GitDiffAutoOpenMaxChangedLines: Int = 2000
public const val GitDiffLargeBannerCopy: String = "此差异较大，每次仅显示一个文件"

/** Hidden lines one context-expansion click reveals. Harness choice; the renderer is not restored. */
public const val GitDiffContextChunk: Int = 20

/** Above this many hits the search reports a truncated result instead of growing without bound. */
public const val GitDiffSearchLimit: Int = 500

/**
 * True when the whole diff opens by default. A diff that fails either restored limit switches to the
 * single-file mode whose banner is [GitDiffLargeBannerCopy].
 */
public fun gitDiffOpensByDefault(
    document: GitDiffDocument,
    maxFiles: Int = GitDiffAutoOpenMaxFiles,
    maxChangedLines: Int = GitDiffAutoOpenMaxChangedLines,
): Boolean =
    document.files.size <= maxFiles && document.addedLines + document.deletedLines <= maxChangedLines

/** One `@@` hunk of a file body. `section` keeps its leading space, exactly as git printed it. */
public data class GitDiffHunk(
    val oldStart: Int,
    val oldCount: Int,
    val newStart: Int,
    val newCount: Int,
    val section: String,
    /** Hunk body lines, `@@` header excluded, in patch order. */
    val lines: List<String>,
)

/**
 * Unchanged lines between two hunks (or before the first / after the last), addressed by the index
 * of the hunk that follows it. The final gap's [hunkIndex] equals the hunk count.
 */
public data class GitDiffContextGap(val hunkIndex: Int, val lines: List<String>)

private val HUNK_HEADER_PATTERN =
    Regex("""^@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@(.*)$""")

/** Splits a hunk body into hunks without dropping or reordering any line. */
public fun parseGitDiffHunks(body: String): List<GitDiffHunk> {
    if (body.isEmpty()) return emptyList()
    val lines = body.removeSuffix("\n").split('\n')
    val hunks = mutableListOf<GitDiffHunk>()
    var currentHeader: MatchResult? = null
    var currentLines = mutableListOf<String>()
    fun flush() {
        val match = currentHeader ?: return
        hunks += GitDiffHunk(
            oldStart = match.groupValues[1].toInt(),
            oldCount = match.groupValues[2].takeIf { it.isNotEmpty() }?.toInt() ?: 1,
            newStart = match.groupValues[3].toInt(),
            newCount = match.groupValues[4].takeIf { it.isNotEmpty() }?.toInt() ?: 1,
            section = match.groupValues[5],
            lines = currentLines.toList(),
        )
        currentLines = mutableListOf()
    }
    for (line in lines) {
        val match = HUNK_HEADER_PATTERN.matchEntire(line)
        if (match != null) {
            flush()
            currentHeader = match
        } else if (currentHeader != null) {
            currentLines += line
        }
    }
    flush()
    return hunks
}

/**
 * Lines the patch hid between hunks, read from the file's new-side text. Both sides are identical in
 * these gaps, so a line shown above a hunk keeps the hunk's new numbering; the rebuilt header shifts
 * its start back by the same count. A file whose text is unavailable has no gaps to expand.
 */
public fun gitDiffContextGaps(
    hunks: List<GitDiffHunk>,
    fullText: String,
): List<GitDiffContextGap> {
    if (hunks.isEmpty()) return emptyList()
    val normalized = fullText.replace("\r\n", "\n").replace('\r', '\n')
    val lines = if (normalized.isEmpty()) emptyList() else normalized.removeSuffix("\n").split('\n')
    val gaps = mutableListOf<GitDiffContextGap>()
    var cursor = 1
    for ((index, hunk) in hunks.withIndex()) {
        // A pure deletion occupies no new-side line; git then points `newStart` at the line before it.
        val start = if (hunk.newCount > 0) hunk.newStart else hunk.newStart + 1
        val gapLines = readGap(lines, cursor, start)
        gaps += GitDiffContextGap(index, gapLines)
        cursor = maxOf(cursor, start + hunk.newCount)
    }
    gaps += GitDiffContextGap(hunks.size, readGap(lines, cursor, lines.size + 1))
    return gaps
}

private fun readGap(lines: List<String>, fromLine: Int, toLineExclusive: Int): List<String> {
    if (lines.isEmpty() || toLineExclusive <= fromLine) return emptyList()
    val from = (fromLine - 1).coerceIn(0, lines.size)
    val to = (toLineExclusive - 1).coerceIn(from, lines.size)
    return lines.subList(from, to)
}

/**
 * Rebuilds a body with the requested hidden lines made visible. [above] counts lines taken from the
 * end of a gap (adjacent to the hunk below it); [below] counts lines taken from its start. Counts are
 * clamped so the two ends never overlap and never exceed the gap, and the rebuilt `@@` header keeps
 * the renderer's line numbers correct.
 */
public fun gitDiffExpandedBody(
    hunks: List<GitDiffHunk>,
    gaps: List<GitDiffContextGap>,
    above: Map<Int, Int> = emptyMap(),
    below: Map<Int, Int> = emptyMap(),
): String {
    if (hunks.isEmpty() || gaps.size != hunks.size + 1) return ""
    val shown = gaps.mapIndexed { index, gap ->
        val top = if (index == 0) 0 else (below[index] ?: 0).coerceIn(0, gap.lines.size)
        val bottom = if (index == gaps.lastIndex) 0
        else (above[index] ?: 0).coerceIn(0, gap.lines.size - top)
        top to bottom
    }
    val output = mutableListOf<String>()
    hunks.forEachIndexed { index, hunk ->
        val prepended = gaps[index].lines.takeLast(shown[index].second)
        val appended = gaps[index + 1].lines.take(shown[index + 1].first)
        val oldStart = hunk.oldStart - prepended.size
        val newStart = hunk.newStart - prepended.size
        val added = prepended.size + appended.size
        output += "@@ -$oldStart,${hunk.oldCount + added} +$newStart,${hunk.newCount + added} @@${hunk.section}"
        prepended.forEach { output += " $it" }
        output += hunk.lines
        appended.forEach { output += " $it" }
    }
    return output.joinToString("\n")
}

/**
 * Maps a line index in the original body to its index in [gitDiffExpandedBody]'s output. Callers use
 * it to reveal a search hit after context has been expanded above it.
 */
public fun gitDiffExpandedLineIndex(
    originalLine: Int,
    hunks: List<GitDiffHunk>,
    gaps: List<GitDiffContextGap>,
    above: Map<Int, Int> = emptyMap(),
    below: Map<Int, Int> = emptyMap(),
): Int {
    if (hunks.isEmpty() || gaps.size != hunks.size + 1) return originalLine
    // Original headers start after every preceding hunk header and its body; indexes are 0-based.
    val headerLines = mutableListOf<Int>()
    var offset = 0
    for (hunk in hunks) {
        headerLines += offset
        offset += 1 + hunk.lines.size
    }
    val hunkIndex = headerLines.indexOfLast { it <= originalLine }
    if (hunkIndex < 0) return originalLine
    val shown = gaps.mapIndexed { index, gap ->
        val top = if (index == 0) 0 else (below[index] ?: 0).coerceIn(0, gap.lines.size)
        val bottom = if (index == gaps.lastIndex) 0
        else (above[index] ?: 0).coerceIn(0, gap.lines.size - top)
        top to bottom
    }
    // Gaps before this hunk contribute in full. Its own prepended lines sit after its `@@` header,
    // so the header itself stays at the same offset and only the body lines move down.
    var shift = 0
    for (index in 0 until hunkIndex) shift += shown[index].first + shown[index].second
    // The gap directly above this hunk shows its top lines before the header and its bottom lines
    // after it; only the top ones shift the header itself.
    shift += shown[hunkIndex].first
    if (originalLine > headerLines[hunkIndex]) shift += shown[hunkIndex].second
    return originalLine + shift
}

/** One query hit inside a file body, addressed by the body line the renderer later displays. */
public data class GitDiffSearchMatch(
    val filePath: String,
    val fileHeader: String,
    val bodyLineIndex: Int,
    val startInLine: Int,
    val length: Int,
    val lineText: String,
)

public data class GitDiffSearchResult(
    val matches: List<GitDiffSearchMatch>,
    val truncated: Boolean = false,
)

/**
 * Case-insensitive literal search over every file body and its `@@` headers. A blank query has no
 * hits; [GitDiffSearchLimit] caps the result so one huge patch cannot freeze the toolbar.
 */
public fun findGitDiffMatches(
    document: GitDiffDocument,
    query: String,
    limit: Int = GitDiffSearchLimit,
): GitDiffSearchResult {
    val needle = query.trim()
    if (needle.isEmpty()) return GitDiffSearchResult(emptyList())
    val matches = mutableListOf<GitDiffSearchMatch>()
    var truncated = false
    for (file in document.files) {
        val lines = if (file.body.isEmpty()) emptyList() else file.body.removeSuffix("\n").split('\n')
        for ((index, line) in lines.withIndex()) {
            var from = 0
            while (true) {
                val hit = line.indexOf(needle, from, ignoreCase = true)
                if (hit < 0) break
                if (matches.size >= limit) {
                    truncated = true
                    break
                }
                matches += GitDiffSearchMatch(
                    filePath = file.path,
                    fileHeader = file.header,
                    bodyLineIndex = index,
                    startInLine = hit,
                    length = needle.length,
                    lineText = line,
                )
                from = hit + needle.length.coerceAtLeast(1)
            }
            if (truncated) break
        }
        if (truncated) break
    }
    return GitDiffSearchResult(matches, truncated)
}
