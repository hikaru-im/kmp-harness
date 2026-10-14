package im.hikaru.harness.client.git

/**
 * File-level view of one unified diff, as produced by the Harness argv reads
 * (`git diff --no-ext-diff --no-textconv --no-color`). ASAR evidence D01/D03 renders a diff as one
 * block per file with a status and +/- statistics; this parser only splits the text the backend
 * already returned and never invents content that is not in the patch.
 */
public enum class GitDiffFileStatus { Added, Deleted, Renamed, Copied, Modified }

public data class GitDiffFile(
    /** Destination path (`b/…`); for a deletion the last real path before `/dev/null`. */
    val path: String,
    /** Source path for a rename/copy, else null. */
    val originalPath: String?,
    val status: GitDiffFileStatus,
    val addedLines: Int,
    val deletedLines: Int,
    val binary: Boolean,
    /** Everything before the first hunk (or all lines when the file has no hunk). */
    val header: String,
    /** Hunk text including `@@` headers; empty for a pure rename or a binary file. */
    val body: String,
) {
    public val changeCount: Int get() = addedLines + deletedLines
}

public data class GitDiffDocument(
    val files: List<GitDiffFile>,
    val addedLines: Int,
    val deletedLines: Int,
    /** True when non-blank text appeared before the first `diff --git` header. */
    val leadingContent: Boolean = false,
)

private const val DIFF_HEADER = "diff --git "

/** Splits a unified diff into per-file blocks without reordering or dropping any line. */
public fun parseGitDiff(patch: String): GitDiffDocument {
    if (patch.isEmpty()) return GitDiffDocument(emptyList(), 0, 0)
    val lines = patch.removeSuffix("\n").split('\n')
    val files = mutableListOf<GitDiffFile>()
    var start = -1
    var leadingContent = false
    for ((index, line) in lines.withIndex()) {
        if (!line.startsWith(DIFF_HEADER)) continue
        if (start >= 0) files += buildDiffFile(lines.subList(start, index))
        else if (lines.take(index).any { it.isNotBlank() }) leadingContent = true
        start = index
    }
    if (start >= 0) files += buildDiffFile(lines.subList(start, lines.size))
    else if (lines.any { it.isNotBlank() }) leadingContent = true
    return GitDiffDocument(files, files.sumOf { it.addedLines }, files.sumOf { it.deletedLines }, leadingContent)
}

/** File and +/- totals for a patch, sharing the exact counting used by the diff cards. */
public fun gitDiffStats(patch: String): GitDiffStats {
    val document = parseGitDiff(patch)
    return GitDiffStats(document.files.size, document.addedLines, document.deletedLines)
}

private fun buildDiffFile(lines: List<String>): GitDiffFile {
    val cut = lines.indexOfFirst { it.startsWith("@@") || it.startsWith("Binary files ") || it.startsWith("GIT binary patch") }
    val headerLines = if (cut < 0) lines else lines.subList(0, cut)
    val bodyLines = if (cut < 0) emptyList() else lines.subList(cut, lines.size)
    val renameFrom = headerLines.firstNotNullOfOrNull { quotedPath(it, "rename from ") ?: quotedPath(it, "copy from ") }
    val renameTo = headerLines.firstNotNullOfOrNull { quotedPath(it, "rename to ") ?: quotedPath(it, "copy to ") }
    val plusPath = headerLines.firstNotNullOfOrNull { quotedPath(it, "+++ ") }?.takeUnless { it == "/dev/null" }
    val minusPath = headerLines.firstNotNullOfOrNull { quotedPath(it, "--- ") }?.takeUnless { it == "/dev/null" }
    val status = when {
        headerLines.any { it.startsWith("new file mode ") } -> GitDiffFileStatus.Added
        headerLines.any { it.startsWith("deleted file mode ") } -> GitDiffFileStatus.Deleted
        renameFrom != null && headerLines.any { it.startsWith("rename from ") } -> GitDiffFileStatus.Renamed
        renameFrom != null -> GitDiffFileStatus.Copied
        else -> GitDiffFileStatus.Modified
    }
    val fallback = pathsFromHeaderLine(lines.first())
    val path = (renameTo ?: plusPath ?: minusPath ?: fallback?.second)
        ?.removePrefix("b/")?.removePrefix("a/") ?: "(未知路径)"
    val original = (renameFrom ?: minusPath)?.removePrefix("a/")?.removePrefix("b/")?.takeUnless { it == path }
    var added = 0
    var deleted = 0
    for (line in bodyLines) {
        // Every hunk line carries its own +/-/space marker, so a content line starting with
        // "++"/"--" is still counted; the header's "---"/"+++" never appear past the first "@@".
        if (line.startsWith("\\ ")) continue
        if (line.startsWith("+")) added++ else if (line.startsWith("-")) deleted++
    }
    return GitDiffFile(
        path = path,
        originalPath = original,
        status = status,
        addedLines = added,
        deletedLines = deleted,
        binary = lines.any { it.startsWith("Binary files ") || it.startsWith("GIT binary patch") },
        header = headerLines.joinToString("\n"),
        body = bodyLines.joinToString("\n"),
    )
}

/** Value after [prefix], unquoted when Git quoted it; null when the line does not start with it. */
private fun quotedPath(line: String, prefix: String): String? {
    if (!line.startsWith(prefix)) return null
    val value = line.substring(prefix.length).trimEnd()
    return if (value.length >= 2 && value.startsWith('"') && value.endsWith('"')) unquote(value.substring(1, value.length - 1)) else value
}

/** `diff --git a/<x> b/<y>` where unquoted paths may contain spaces. */
private fun pathsFromHeaderLine(line: String): Pair<String, String>? {
    if (!line.startsWith(DIFF_HEADER)) return null
    val rest = line.substring(DIFF_HEADER.length)
    if (rest.startsWith('"')) {
        val first = readQuoted(rest, 0) ?: return null
        val afterFirst = rest.substring(first.second).trimStart()
        if (!afterFirst.startsWith('"')) return null
        val second = readQuoted(afterFirst, 0) ?: return null
        return first.first to second.first
    }
    // Prefer the split point whose two halves strip to the same path; otherwise the last separator.
    val candidates = Regex(" b/").findAll(rest).map { it.range.first }.toList()
    for (index in candidates) {
        val left = rest.substring(0, index)
        val right = rest.substring(index + 1)
        if (left.startsWith("a/") && right.startsWith("b/") && left.removePrefix("a/") == right.removePrefix("b/")) return left to right
    }
    val index = candidates.lastOrNull() ?: return null
    return rest.substring(0, index) to rest.substring(index + 1)
}

private fun readQuoted(text: String, start: Int): Pair<String, Int>? {
    if (start >= text.length || text[start] != '"') return null
    // Git escapes the raw path bytes as octal, so decode into bytes and reinterpret as UTF-8
    // instead of treating each octal escape as one code point.
    val bytes = ArrayList<Byte>()
    var index = start + 1
    while (index < text.length) {
        when (val char = text[index]) {
            '"' -> return bytes.toByteArray().decodeToString() to index + 1
            '\\' -> {
                index++
                if (index >= text.length) return null
                val escape = text[index]
                if (escape in '0'..'7') {
                    var value = 0
                    var digits = 0
                    while (digits < 3 && index < text.length && text[index] in '0'..'7') {
                        value = value * 8 + (text[index] - '0'); index++; digits++
                    }
                    bytes.add(value.toByte())
                    continue
                }
                val decoded = when (escape) {
                    'n' -> '\n'; 't' -> '\t'; 'r' -> '\r'; '\\' -> '\\'; '"' -> '"'; else -> escape
                }
                bytes.addAll(decoded.toString().encodeToByteArray().toList())
            }
            else -> bytes.addAll(char.toString().encodeToByteArray().toList())
        }
        index++
    }
    return null
}

private fun unquote(text: String): String = readQuoted("\"$text\"", 0)?.first ?: text
