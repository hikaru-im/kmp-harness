package im.hikaru.harness.client.git

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

/**
 * Line-level view of one unified-diff body, matching the observed Codex diff renderer.
 *
 * ASAR evidence (read-only): `codex-diff-css` styles `[data-unified]` bodies with
 * `[data-line-type='change-addition'] { --diffs-computed-diff-line-bg: var(--diffs-bg-addition) }`
 * and the matching deletion rule; `[data-separator='line-info']` renders an `@@` hunk header as a
 * separator with its own column-number cell; `[data-no-newline]` marks the `\ No newline` line. This
 * parser only classifies the lines the backend already returned and never invents content.
 */
public enum class GitDiffLineKind { Context, Added, Removed, HunkHeader, NoNewline, Meta }

/** One line of a diff body. `text` drops the leading `+`/`-`/space marker; headers/meta keep theirs. */
public data class GitDiffLine(
    val kind: GitDiffLineKind,
    val text: String,
    /** 1-based old-file line number, null on added lines and headers. */
    val oldNumber: Int? = null,
    /** 1-based new-file line number, null on removed lines and headers. */
    val newNumber: Int? = null,
)

/**
 * The single number the observed unified gutter prints for one line, or null where the original
 * leaves a gutter buffer (hunk header, `\ No newline`, leading meta). Added and context lines show
 * their new-file number, removed lines their old-file number; the unified view has one number column
 * (`[data-column-number]`), not the split view's two.
 */
internal fun gitDiffGutterNumber(line: GitDiffLine): Int? = when (line.kind) {
    GitDiffLineKind.Added, GitDiffLineKind.Context -> line.newNumber
    GitDiffLineKind.Removed -> line.oldNumber
    GitDiffLineKind.HunkHeader, GitDiffLineKind.NoNewline, GitDiffLineKind.Meta -> null
}

/**
 * Padding character for the gutter column. A non-breaking space is not trimmed as trailing
 * whitespace, so the cell background reaches the body band instead of stopping at the last digit;
 * it has the same advance as a normal space in the monospace body font, so the column stays aligned.
 */
private const val GUTTER_PAD = '\u00A0'

/** Padding cells between the number column and the patch body, so the two line backgrounds stay adjacent. */
private const val GUTTER_GAP_CELLS = 2

/**
 * Column width of the unified gutter: as many cells as the widest number it prints. Exposed so a
 * body rendered line-by-line (the word-wrap variant) can share one width across its cells.
 */
internal fun gitDiffGutterWidth(lines: List<GitDiffLine>): Int =
    lines.mapNotNull(::gitDiffGutterNumber).maxOfOrNull { it.toString().length } ?: 1

/**
 * One gutter cell: the right-aligned number (blank where the original buffers the column) padded to
 * the column width, then a fixed gap of padding cells. Monospace layout makes this constant width.
 */
private fun gitDiffGutterCell(line: GitDiffLine, width: Int): String {
    val number = gitDiffGutterNumber(line)?.toString().orEmpty()
    return GUTTER_PAD.toString().repeat(width - number.length) + number + GUTTER_PAD.toString().repeat(GUTTER_GAP_CELLS)
}

/**
 * The unselectable gutter body: one right-aligned number per parsed line, blank where the original
 * buffers the column. Same layout as [gitDiffGutterAnnotatedString], so the plain text and the tinted
 * gutter always agree. The line count equals [parseGitDiffLines], so it stays aligned with the
 * selectable patch text.
 */
internal fun gitDiffGutterText(lines: List<GitDiffLine>): String {
    if (lines.isEmpty()) return ""
    val width = gitDiffGutterWidth(lines)
    return lines.joinToString("\n") { line -> gitDiffGutterCell(line, width) }
}

/**
 * One gutter cell with the original's per-line background, for a body rendered line-by-line. Same
 * cell text and tint as [gitDiffGutterAnnotatedString], so the wrap variant and the whole-body
 * gutter always agree.
 */
internal fun gitDiffGutterLineAnnotatedString(
    line: GitDiffLine,
    width: Int,
    addedBackground: Color,
    removedBackground: Color,
    separatorBackground: Color,
): AnnotatedString = buildAnnotatedString {
    val cell = gitDiffGutterCell(line, width)
    val background = gitDiffGutterCellBackground(line, addedBackground, removedBackground, separatorBackground)
    if (background == null) append(cell) else withStyle(SpanStyle(background = background)) { append(cell) }
}

/** The cell background one gutter cell carries: the body tint of added/removed lines and `@@` headers. */
private fun gitDiffGutterCellBackground(
    line: GitDiffLine,
    addedBackground: Color,
    removedBackground: Color,
    separatorBackground: Color,
): Color? = when (line.kind) {
    GitDiffLineKind.Added -> addedBackground
    GitDiffLineKind.Removed -> removedBackground
    GitDiffLineKind.HunkHeader -> separatorBackground
    GitDiffLineKind.Context, GitDiffLineKind.NoNewline, GitDiffLineKind.Meta -> null
}

/**
 * The gutter with the original's per-line cell background. `[data-column-number]` and
 * `[data-gutter-buffer]` cells participate in the line background, so added/removed lines tint the
 * gutter the same colour as the body and a `@@` separator spans the gutter too. Built as one
 * `AnnotatedString` so the gutter remains line-for-line aligned with the single body text; the tint
 * covers the number and the trailing gap, so the gutter and body bands meet without a seam.
 */
internal fun gitDiffGutterAnnotatedString(
    lines: List<GitDiffLine>,
    addedBackground: Color,
    removedBackground: Color,
    separatorBackground: Color,
): AnnotatedString = buildAnnotatedString {
    if (lines.isEmpty()) return@buildAnnotatedString
    val width = gitDiffGutterWidth(lines)
    lines.forEachIndexed { index, line ->
        if (index > 0) append('\n')
        val cell = gitDiffGutterCell(line, width)
        val background = gitDiffGutterCellBackground(line, addedBackground, removedBackground, separatorBackground)
        if (background == null) append(cell) else withStyle(SpanStyle(background = background)) { append(cell) }
    }
}

/** `@@ -oldStart[,oldCount] +newStart[,newCount] @@ [context]`. Shared with the split parser. */
internal val HUNK_HEADER = Regex("""^@@ -(\d+)(?:,\d+)? \+(\d+)(?:,\d+)? @@""")

/**
 * The number one side of the split view prints in its gutter, or null where the original leaves a
 * `[data-gutter-buffer]` cell. The split layout has two number columns — the old-file number on the
 * left pane and the new-file number on the right pane — unlike the unified view's single column.
 */
internal fun gitDiffSplitGutterNumber(row: GitDiffSplitRow, left: Boolean): Int? =
    if (left) row.oldNumber else row.newNumber

/**
 * Column width for both split gutters. The original drives every pane from one
 * `--diffs-column-number-width` grid variable, so the two panes share a width instead of sizing
 * independently; a blank side still reserves the same cells, keeping the two panes aligned.
 */
internal fun gitDiffSplitGutterWidth(rows: List<GitDiffSplitRow>): Int =
    rows.flatMap { listOfNotNull(it.oldNumber, it.newNumber) }.maxOfOrNull { it.toString().length } ?: 1

private fun gitDiffSplitGutterCell(number: Int?, width: Int): String {
    val text = number?.toString().orEmpty()
    return GUTTER_PAD.toString().repeat(width - text.length) + text + GUTTER_PAD.toString().repeat(GUTTER_GAP_CELLS)
}

/** One split gutter cell as plain text, for tests that assert the printed numbers. */
internal fun gitDiffSplitGutterText(row: GitDiffSplitRow, width: Int, left: Boolean): String =
    gitDiffSplitGutterCell(gitDiffSplitGutterNumber(row, left), width)

/**
 * One split gutter cell with the original's per-line background. The changed side of a row inherits
 * the body tint (`[data-gutter]` participates in the line background in both panes), a deletion tints
 * only the left cell, an addition only the right, and context/hunk rows keep no tint.
 */
internal fun gitDiffSplitGutterAnnotatedString(
    row: GitDiffSplitRow,
    width: Int,
    left: Boolean,
    addedBackground: Color,
    removedBackground: Color,
): AnnotatedString {
    val cell = gitDiffSplitGutterCell(gitDiffSplitGutterNumber(row, left), width)
    val background = gitDiffSplitSideBackground(row, left, addedBackground, removedBackground)
    return buildAnnotatedString {
        if (background == null) append(cell) else withStyle(SpanStyle(background = background)) { append(cell) }
    }
}

/** The line background one split side carries: the removed tint on the left, the added tint on the right. */
private fun gitDiffSplitSideBackground(
    row: GitDiffSplitRow,
    left: Boolean,
    addedBackground: Color,
    removedBackground: Color,
): Color? = when {
    left && (row.kind == GitDiffSplitRowKind.Deletion || row.kind == GitDiffSplitRowKind.Change) -> removedBackground
    !left && (row.kind == GitDiffSplitRowKind.Addition || row.kind == GitDiffSplitRowKind.Change) -> addedBackground
    else -> null
}

/**
 * The selectable pane text of one split row, tinted on the side that carries the change. Matches the
 * unified view, where the tint covers the patch text and the gutter cell beside it — each pane keeps
 * every character of the row text, so the semantics string is unchanged.
 */
internal fun gitDiffSplitPaneText(
    row: GitDiffSplitRow,
    left: Boolean,
    addedBackground: Color,
    removedBackground: Color,
    wordDiff: GitWordDiff? = null,
    wordAddedBackground: Color = Color.Unspecified,
    wordRemovedBackground: Color = Color.Unspecified,
): AnnotatedString {
    val text = if (left) row.left else row.right
    val background = gitDiffSplitSideBackground(row, left, addedBackground, removedBackground)
    val spans = if (left) wordDiff?.removed else wordDiff?.added
    val emphasis = if (left) wordRemovedBackground else wordAddedBackground
    return buildAnnotatedString {
        if (background == null) appendWordSpans(text, spans, emphasis)
        else withStyle(SpanStyle(background = background)) { appendWordSpans(text, spans, emphasis) }
    }
}

public fun parseGitDiffLines(body: String): List<GitDiffLine> {
    if (body.isEmpty()) return emptyList()
    val lines = mutableListOf<GitDiffLine>()
    var oldNumber = 0
    var newNumber = 0
    for (raw in body.split('\n')) {
        val hunk = HUNK_HEADER.find(raw)
        when {
            hunk != null -> {
                oldNumber = hunk.groupValues[1].toInt()
                newNumber = hunk.groupValues[2].toInt()
                lines += GitDiffLine(GitDiffLineKind.HunkHeader, raw)
            }
            raw.startsWith("\\") -> lines += GitDiffLine(GitDiffLineKind.NoNewline, raw)
            // Inside a hunk every line carries its own marker, so a content line that itself starts
            // with "++"/"--" is still classified by its own first character.
            raw.startsWith("+") -> {
                lines += GitDiffLine(GitDiffLineKind.Added, raw.substring(1), newNumber = newNumber)
                newNumber++
            }
            raw.startsWith("-") -> {
                lines += GitDiffLine(GitDiffLineKind.Removed, raw.substring(1), oldNumber = oldNumber)
                oldNumber++
            }
            raw.startsWith(" ") -> {
                lines += GitDiffLine(GitDiffLineKind.Context, raw.substring(1), oldNumber, newNumber)
                oldNumber++
                newNumber++
            }
            else -> lines += GitDiffLine(GitDiffLineKind.Meta, raw)
        }
    }
    return lines
}
