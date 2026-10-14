package im.hikaru.harness.desktop.git

import im.hikaru.harness.client.git.*

private fun malformed(): Nothing = throw GitReadException(GitFailure(GitFailureKind.MalformedOutput, "无法安全解析 Git 输出。"))

/** Porcelain v1 -z places destination first, then the original path for renames/copies. */
internal fun parseGitStatus(output: String): List<GitChange> {
    if (output.isEmpty()) return emptyList()
    if (!output.endsWith('\u0000')) malformed()
    val fields = output.split('\u0000').dropLast(1)
    val changes = mutableListOf<GitChange>()
    var index = 0
    while (index < fields.size) {
        val entry = fields[index++]
        if (entry.length < 4 || entry[2] != ' ') malformed()
        val x = entry[0]; val y = entry[1]
        if (x !in " MADRCU?!T" || y !in " MADRCU?!T") malformed()
        val path = entry.substring(3)
        if (path.isEmpty()) malformed()
        val original = if (x in "RC" || y in "RC") {
            if (index >= fields.size) malformed()
            fields[index++].also { if (it.isEmpty()) malformed() }
        } else null
        changes.add(GitChange(path, original, x, y))
    }
    return changes.toList()
}

internal fun parseGitBranches(output: String): List<GitBranch> {
    if (output.isEmpty()) return emptyList()
    if (!output.removeSuffix("\n").endsWith('\u0000')) malformed()
    val fields = output.split('\u0000')
    val branches = mutableListOf<GitBranch>()
    var index = 0
    while (index < fields.size) {
        val ref = fields[index++].trimStart('\n', '\r')
        if (ref.isEmpty() && index == fields.size) break
        if (index + 1 >= fields.size || (!ref.startsWith("refs/heads/") && !ref.startsWith("refs/remotes/"))) malformed()
        val oid = fields[index++]
        val current = fields[index++]
        if (!oid.matches(Regex("(?:[0-9a-fA-F]{40}|[0-9a-fA-F]{64})")) || current !in listOf("*", " ")) malformed()
        branches.add(GitBranch(ref, oid, current == "*", ref.startsWith("refs/remotes/")))
    }
    return branches.toList()
}

internal fun parseGitWorktrees(output: String): List<GitWorktree> {
    if (output.isEmpty()) return emptyList()
    if (!output.endsWith('\u0000')) malformed()
    val result = mutableListOf<GitWorktree>()
    var fields = mutableListOf<String>()
    fun finish() {
        if (fields.isEmpty()) return
        val first = fields.first()
        if (!first.startsWith("worktree ") || first.length == 9) malformed()
        val values = fields.drop(1).associate { it.substringBefore(' ') to it.substringAfter(' ', "") }
        val head = values["HEAD"]
        if (head != null && !head.matches(Regex("(?:[0-9a-fA-F]{40}|[0-9a-fA-F]{64})"))) malformed()
        result.add(GitWorktree(first.substring(9), head, values["branch"],
            detached = "detached" in values, bare = "bare" in values, locked = "locked" in values,
            lockReason = values["locked"]?.takeIf { it.isNotEmpty() },
            prunable = "prunable" in values, pruneReason = values["prunable"]?.takeIf { it.isNotEmpty() }))
        fields = mutableListOf()
    }
    output.split('\u0000').forEach { if (it.isEmpty()) finish() else fields.add(it) }
    finish()
    return result.toList()
}
