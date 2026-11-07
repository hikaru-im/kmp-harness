package im.hikaru.harness.desktop.git

import im.hikaru.harness.client.git.GitFailure
import im.hikaru.harness.client.git.GitFailureKind
import im.hikaru.harness.client.git.GitReadException
import im.hikaru.harness.client.git.gitDiffPathToken

/** Largest hunk patch accepted for one index write; a real hunk is far smaller. */
internal const val STAGE_PATCH_LIMIT: Int = 1024 * 1024

private val HUNK_HEADER = Regex("^@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@.*$")
private val INDEX_LINE = Regex("^index [0-9a-f]{4,64}\\.\\.[0-9a-f]{4,64}(?: [0-7]{6})?$")

private fun reject(message: String): Nothing =
    throw GitReadException(GitFailure(GitFailureKind.InvalidRequest, message))

/**
 * Accepts only the patch shape this harness itself builds for one hunk of one file: a single
 * `diff --git` section, optional `new file mode`/`deleted file mode`/`index`, matching `---`/`+++`
 * and exactly one `@@` hunk whose body matches the header counts.
 *
 * Everything Git could interpret as a different operation is rejected before any command runs:
 * renames, copies, mode-only changes, binary patches, submodule links, unmerged or truncated hunks,
 * and any path other than the reviewed one. The patch never names a path on its own — the file-level
 * argument is separately validated by the caller.
 */
internal fun validateStagePatch(path: String, patch: String) {
    if (patch.isEmpty()) reject("补丁为空，无法暂存。")
    if (patch.length > STAGE_PATCH_LIMIT) reject("补丁过大，无法作为单个 hunk 暂存。")
    if (patch.contains('\u0000')) reject("补丁包含无效字符。")
    if (!patch.endsWith('\n')) reject("补丁必须以换行结束。")
    val lines = patch.removeSuffix("\n").split('\n')
    if (!lines.first().startsWith(DIFF_GIT_PREFIX)) reject("补丁必须只包含一个文件的差异。")
    if (lines.count { it.startsWith(DIFF_GIT_PREFIX) } != 1) reject("补丁必须只包含一个文件的差异。")
    if (lines.count { it.startsWith("@@") } != 1) reject("补丁必须只包含一个 hunk。")
    val hunkIndex = lines.indexOfFirst { it.startsWith("@@") }
    if (!diffHeaderNames(lines.first(), path)) reject("补丁路径与所选文件不一致。")
    var added = false
    var deleted = false
    var oldPath: String? = null
    var newPath: String? = null
    for (line in lines.subList(1, hunkIndex)) {
        when {
            line.startsWith("new file mode ") -> {
                if (!line.removePrefix("new file mode ").matches(Regex("[0-7]{6}")))
                    reject("补丁文件模式无效。")
                added = true
            }
            line.startsWith("deleted file mode ") -> {
                if (!line.removePrefix("deleted file mode ").matches(Regex("[0-7]{6}")))
                    reject("补丁文件模式无效。")
                deleted = true
            }
            line.startsWith("index ") -> if (!INDEX_LINE.matches(line)) reject("补丁 index 行无效。")
            line.startsWith("--- ") -> oldPath = patchSidePath(line.removePrefix("--- ")) ?: reject("补丁缺少有效文件路径。")
            line.startsWith("+++ ") -> newPath = patchSidePath(line.removePrefix("+++ ")) ?: reject("补丁缺少有效文件路径。")
            // rename/copy headers, old mode/new mode, binary markers, submodule links, anything else.
            else -> reject("补丁包含不支持的头部；只接受单个文件的普通修改或新增/删除。")
        }
    }
    if (added && deleted) reject("补丁不能同时新增和删除文件。")
    val old = oldPath ?: reject("补丁缺少 --- 文件头。")
    val new = newPath ?: reject("补丁缺少 +++ 文件头。")
    if (old != "/dev/null" && old != "a/$path") reject("补丁路径与所选文件不一致。")
    if (new != "/dev/null" && new != "b/$path") reject("补丁路径与所选文件不一致。")
    if ((old == "/dev/null") != added) reject("补丁的新增标记与文件头不一致。")
    if ((new == "/dev/null") != deleted) reject("补丁的删除标记与文件头不一致。")
    val match = HUNK_HEADER.matchEntire(lines[hunkIndex]) ?: reject("补丁 hunk 头部无效。")
    val oldCount = match.groupValues[2].ifEmpty { "1" }.toIntOrNull() ?: reject("补丁 hunk 头部无效。")
    val newCount = match.groupValues[4].ifEmpty { "1" }.toIntOrNull() ?: reject("补丁 hunk 头部无效。")
    var oldSeen = 0
    var newSeen = 0
    for (line in lines.subList(hunkIndex + 1, lines.size)) {
        if (line.startsWith("\\")) continue // "\ No newline at end of file"
        when (line.firstOrNull()) {
            ' ' -> { oldSeen++; newSeen++ }
            '-' -> oldSeen++
            '+' -> newSeen++
            else -> reject("补丁正文不是有效的差异行。")
        }
    }
    if (oldSeen != oldCount || newSeen != newCount) reject("补丁 hunk 行数与头部不一致。")
}

private const val DIFF_GIT_PREFIX = "diff --git "

/** End index (exclusive) of a double-quoted C-style token, or null when it is unterminated. */
private fun quotedTokenEnd(text: String): Int? {
    if (!text.startsWith('"')) return null
    var index = 1
    while (index < text.length) {
        when (text[index]) {
            '\\' -> index += 2
            '"' -> return index + 1
            else -> index++
        }
    }
    return null
}

/** `diff --git a/<path> b/<path>` for exactly [path]; quoted (non-ASCII) and plain forms both pass. */
internal fun diffHeaderNames(line: String, path: String): Boolean {
    val rest = line.removePrefix(DIFF_GIT_PREFIX)
    if (rest.startsWith('"')) {
        val firstEnd = quotedTokenEnd(rest) ?: return false
        val first = gitDiffPathToken(rest.substring(0, firstEnd)) ?: return false
        val remainder = rest.substring(firstEnd).trimStart(' ')
        val secondEnd = quotedTokenEnd(remainder) ?: return false
        if (secondEnd != remainder.length) return false
        val second = gitDiffPathToken(remainder) ?: return false
        return first == "a/$path" && second == "b/$path"
    }
    // A plain header is ambiguous when the path itself contains " b/", so accept only a split whose
    // two halves are exactly this file's a/ and b/ names.
    for (index in Regex(" b/").findAll(rest).map { it.range.first }) {
        if (rest.substring(0, index) == "a/$path" && rest.substring(index + 1) == "b/$path") return true
    }
    return false
}

/** Decodes one `---`/`+++` path token; Git appends a tab when the name contains a space. */
private fun patchSidePath(raw: String): String? {
    val text = raw.removeSuffix("\t")
    if (text.startsWith('"') && quotedTokenEnd(text) != text.length) return null
    return gitDiffPathToken(text)?.takeIf { it.isNotEmpty() }
}
