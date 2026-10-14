package im.hikaru.harness.client.git

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle

/**
 * Word-level ("词级差异") highlighting for the diff body.
 *
 * ASAR evidence (read-only): the Codex diff settings menu has a `codex.review.wordDiffs.label` =
 * 「词级差异」 toggle whose persisted setting key is `wordDiffsEnabled.2` and whose default value is
 * `true` (`app-initial-6350d04672d0.js`: `Fu(`wordDiffsEnabled.2`,!0)`; the menu component `jr`/`nx`
 * takes `wordDiffsEnabled` and `onToggleWordDiffs`). The original emphasises the changed words inside
 * a paired deletion/addition line; the exact emphasis colour token and matching algorithm were not
 * observed, so this module implements the standard token-prefix/suffix trim and the page supplies an
 * emphasis colour, documented as a Harness choice.
 */

/** A highlighted range inside one line's text, not counting the leading `+`/`-`/space marker. */
public data class GitWordSpan(val start: Int, val end: Int)

/** The changed ranges of one paired deletion/addition line. */
public data class GitWordDiff(val removed: List<GitWordSpan>, val added: List<GitWordSpan>)

/**
 * Alpha for the word-diff emphasis layer. The original's exact emphasis token was not observed; this
 * value is deliberately stronger than the line background (`0x1A`/`0x2E`) so a changed word stands out
 * against its tinted line. A Harness choice, recorded in the batch contract.
 */
internal const val WORD_DIFF_EMPHASIS_ALPHA = 0.35f

/** Appends [text], tinting the changed [spans] with [emphasis]; an unspecified emphasis appends plainly. */
internal fun AnnotatedString.Builder.appendWordSpans(text: String, spans: List<GitWordSpan>?, emphasis: Color) {
    if (spans.isNullOrEmpty() || emphasis == Color.Unspecified) {
        append(text)
        return
    }
    var position = 0
    for (span in spans) {
        val start = span.start.coerceIn(position, text.length)
        val end = span.end.coerceIn(start, text.length)
        append(text.substring(position, start))
        if (end > start) withStyle(SpanStyle(background = emphasis)) { append(text.substring(start, end)) }
        position = end
    }
    append(text.substring(position))
}

/**
 * Splits text into runs of word characters, whitespace, or single other characters. Ranges index the
 * original string, so a span built from a token maps straight back onto the line text.
 */
internal fun gitWordTokens(text: String): List<IntRange> {
    val tokens = mutableListOf<IntRange>()
    var i = 0
    while (i < text.length) {
        val start = i
        val c = text[i]
        when {
            c.isLetterOrDigit() || c == '_' -> while (i < text.length && (text[i].isLetterOrDigit() || text[i] == '_')) i++
            c.isWhitespace() -> while (i < text.length && text[i].isWhitespace()) i++
            else -> i++
        }
        tokens += start until i
    }
    return tokens
}

private fun gitWordTokenText(text: String, token: IntRange): String = text.substring(token.first, token.last + 1)

/** Collapses touching/adjacent spans so the renderer adds as few emphasis runs as possible. */
private fun mergeAdjacent(spans: List<GitWordSpan>): List<GitWordSpan> {
    if (spans.size < 2) return spans
    val merged = mutableListOf<GitWordSpan>()
    var current = spans.first()
    for (span in spans.drop(1)) {
        if (span.start <= current.end) current = GitWordSpan(current.start, maxOf(current.end, span.end))
        else { merged += current; current = span }
    }
    merged += current
    return merged
}

/**
 * Changed ranges of `old` and `new`, computed by trimming the common token prefix and suffix and
 * marking the middle as changed. Equal strings yield no ranges; a wholly different line marks the
 * whole text (which is already tinted by the line background). Scattered or reordered edits mark the
 * enclosed range rather than resolving each token — the original's exact algorithm was not observed.
 */
public fun gitWordDiff(old: String, new: String): GitWordDiff {
    if (old == new) return GitWordDiff(emptyList(), emptyList())
    val a = gitWordTokens(old)
    val b = gitWordTokens(new)
    var prefix = 0
    while (prefix < a.size && prefix < b.size && gitWordTokenText(old, a[prefix]) == gitWordTokenText(new, b[prefix])) prefix++
    var suffix = 0
    while (suffix < a.size - prefix && suffix < b.size - prefix &&
        gitWordTokenText(old, a[a.size - 1 - suffix]) == gitWordTokenText(new, b[b.size - 1 - suffix])) suffix++
    val removed = mergeAdjacent(a.subList(prefix, a.size - suffix).map { GitWordSpan(it.first, it.last + 1) })
    val added = mergeAdjacent(b.subList(prefix, b.size - suffix).map { GitWordSpan(it.first, it.last + 1) })
    return GitWordDiff(removed, added)
}

/**
 * Unified body: changed ranges by line index, computed only for paired deletions/additions.
 *
 * Lines inside a hunk group into runs of `-`/`+` lines; a run is flushed on any context/header/meta
 * line. Within a run the deletions and additions are paired in order (the i-th deletion with the
 * i-th addition), matching how the split layout pairs them; an unpaired trailing line gets no
 * intra-line ranges because the whole line is already tinted.
 */
public fun gitUnifiedWordDiff(lines: List<GitDiffLine>): Map<Int, GitWordDiff> {
    val result = mutableMapOf<Int, GitWordDiff>()
    var removals = mutableListOf<Pair<Int, String>>()
    var additions = mutableListOf<Pair<Int, String>>()
    fun flush() {
        for (index in 0 until minOf(removals.size, additions.size)) {
            val (removedIndex, removedText) = removals[index]
            val (addedIndex, addedText) = additions[index]
            val diff = gitWordDiff(removedText, addedText)
            if (diff.removed.isNotEmpty()) result[removedIndex] = diff
            if (diff.added.isNotEmpty()) result[addedIndex] = diff
        }
        removals = mutableListOf()
        additions = mutableListOf()
    }
    lines.forEachIndexed { index, line ->
        when (line.kind) {
            GitDiffLineKind.Removed -> removals += index to line.text
            GitDiffLineKind.Added -> additions += index to line.text
            else -> flush()
        }
    }
    flush()
    return result
}

/**
 * Split body: changed ranges by row index, computed only for `Change` rows (both sides present).
 * The split layout already pairs a deletion with its addition, so the two sides are computed from
 * exactly the row text the panes render.
 */
public fun gitSplitWordDiff(rows: List<GitDiffSplitRow>): Map<Int, GitWordDiff> {
    val result = mutableMapOf<Int, GitWordDiff>()
    rows.forEachIndexed { index, row ->
        if (row.kind == GitDiffSplitRowKind.Change) {
            val diff = gitWordDiff(row.left, row.right)
            if (diff.removed.isNotEmpty() || diff.added.isNotEmpty()) result[index] = diff
        }
    }
    return result
}
