package im.hikaru.harness.client.git

/**
 * `codex.review.imports.hide` (隐藏导入内容) — "hide top-level import-only diff changes".
 *
 * ASAR evidence (read-only): the diff options menu renders this as a `be.CheckboxItem` whose label is
 * `codex.review.imports.hide` = `隐藏导入内容`, and the persisted setting key is `hideDiffImports`
 * (`app-initial-*`), read through `Fu(hideDiffImports, false)` so the item starts unchecked. The menu
 * only threads `hideImports`/`onToggleHideImports` down to the diff view; the classifier that decides
 * which changes are "import-only" is not present in the ASAR plaintext, so the rule set below is a
 * Harness choice and is stated here so a reviewer can see exactly which lines the toggle removes.
 *
 * A change line counts as a top-level import when, after the `+`/`-` marker is stripped, it carries no
 * leading indentation and begins with one of the common import forms of the languages the diff already
 * handles: `import …` (Kotlin/Java/Go/TS/JS/Swift/…), `from … import …` (Python), `#include …`
 * (C/C++), `use …` (Rust), `using …` (C#), `require(…)` (CommonJS) and `@import …` (Sass).
 */
private val GIT_IMPORT_LINE = Regex(
    """^(?:import\s|from\s+\S+\s+import\b|#\s*include\b|use\s|using\s|require\(|@import\s)""",
)

/** Whether one marker-stripped diff line is a *top-level* import statement (no leading indentation). */
internal fun gitDiffIsImportOnlyContent(text: String): Boolean {
    if (text.isEmpty() || text[0] == ' ' || text[0] == '\t') return false
    return GIT_IMPORT_LINE.containsMatchIn(text)
}

private fun GitDiffLine.isHiddenImportChange(): Boolean =
    (kind == GitDiffLineKind.Added || kind == GitDiffLineKind.Removed) && gitDiffIsImportOnlyContent(text)

/**
 * Drops added/removed lines that are top-level imports and keeps everything else — context lines, hunk
 * headers and leading meta — so the unified gutter stays aligned (each surviving line keeps its own
 * recorded line number) and the selectable text is exactly the visible changes.
 */
internal fun gitDiffHiddenImportLines(lines: List<GitDiffLine>): List<GitDiffLine> =
    lines.filterNot { it.isHiddenImportChange() }

private fun GitDiffSplitRow.isHiddenImportRow(): Boolean = when (kind) {
    GitDiffSplitRowKind.Context -> false
    GitDiffSplitRowKind.Addition -> gitDiffIsImportOnlyContent(right)
    GitDiffSplitRowKind.Deletion -> gitDiffIsImportOnlyContent(left)
    // A paired edit is only import-only when BOTH sides are imports; a mixed pair keeps its row.
    GitDiffSplitRowKind.Change -> gitDiffIsImportOnlyContent(left) && gitDiffIsImportOnlyContent(right)
}

/** Drops split rows whose only change is a top-level import, keeping context rows and hunk headers. */
internal fun gitDiffHiddenImportRows(rows: List<GitDiffSplitRow>): List<GitDiffSplitRow> =
    rows.filterNot { it.isHiddenImportRow() }

/**
 * Applies [gitDiffHiddenImportRows] to every hunk. Hunk headers are kept even when a hunk loses all of
 * its rows, matching the unified view, which also keeps the `@@` line above a now-empty body.
 */
internal fun gitDiffHiddenImportHunks(hunks: List<GitDiffSplitHunk>): List<GitDiffSplitHunk> =
    hunks.map { it.copy(rows = gitDiffHiddenImportRows(it.rows)) }
