package im.hikaru.harness.client.git

import androidx.compose.ui.graphics.Color
import kotlin.test.*

/** Pure word-level diff: token prefix/suffix trim plus the emphasis rendering invariants. */
class GitWordDiffTest {
    @Test
    fun equalLinesHaveNoChangedSpans() {
        val diff = gitWordDiff("same text", "same text")
        assertTrue(diff.removed.isEmpty())
        assertTrue(diff.added.isEmpty())
    }

    @Test
    fun aChangedMiddleWordMarksOnlyThatWordOnBothSides() {
        val diff = gitWordDiff("foo bar baz", "foo qux baz")
        // Common prefix "foo " and suffix " baz" are trimmed, so only the middle word is emphasised.
        assertEquals(listOf(GitWordSpan(4, 7)), diff.removed)
        assertEquals(listOf(GitWordSpan(4, 7)), diff.added)
    }

    @Test
    fun aWhitespaceOnlyChangeMarksTheWhitespaceRun() {
        val diff = gitWordDiff("a  b", "a b")
        assertEquals(listOf(GitWordSpan(1, 3)), diff.removed)
        assertEquals(listOf(GitWordSpan(1, 2)), diff.added)
    }

    @Test
    fun aTrailingAdditionMarksOnlyTheAddedTail() {
        val diff = gitWordDiff("a b", "a b c")
        assertTrue(diff.removed.isEmpty())
        // The inserted tail is " c": the space before the new word is part of the change.
        assertEquals(listOf(GitWordSpan(3, 5)), diff.added)
    }

    @Test
    fun aWhollyDifferentLineMarksTheWholeTextOnBothSides() {
        val diff = gitWordDiff("only", "first")
        assertEquals(listOf(GitWordSpan(0, 4)), diff.removed)
        assertEquals(listOf(GitWordSpan(0, 5)), diff.added)
    }

    @Test
    fun unifiedPairsDeletionsWithAdditionsInOrderAndLeavesUnpairedLinesAlone() {
        val body = "@@ -1,1 +1,2 @@\n-only\n+first\n+second"
        val lines = parseGitDiffLines(body)
        val spans = gitUnifiedWordDiff(lines)
        // The deletion pairs with the first addition; the second addition is unpaired and stays plain.
        assertEquals(setOf(1, 2), spans.keys)
        assertEquals(listOf(GitWordSpan(0, 4)), spans.getValue(1).removed)
        assertEquals(listOf(GitWordSpan(0, 5)), spans.getValue(2).added)
    }

    @Test
    fun unifiedIgnoresContextAndSeparatesGroups() {
        val body = "@@ -1,3 +1,3 @@\n keep\n-old value\n+new value\n tail"
        val lines = parseGitDiffLines(body)
        val spans = gitUnifiedWordDiff(lines)
        // Indices: 0 header, 1 context, 2 removed, 3 added, 4 context.
        assertEquals(setOf(2, 3), spans.keys)
        assertEquals(listOf(GitWordSpan(0, 3)), spans.getValue(2).removed)
        assertEquals(listOf(GitWordSpan(0, 3)), spans.getValue(3).added)
    }

    @Test
    fun splitPairsOnlyChangeRows() {
        val body = "@@ -1,2 +1,2 @@\n-old value\n+new value\n only-added\n+extra"
        val rows = splitDiffBody(body).single().rows
        val spans = gitSplitWordDiff(rows)
        // Row 0 is a Change row; the one-sided Addition row carries no intra-line emphasis.
        assertEquals(setOf(0), spans.keys)
        assertEquals(listOf(GitWordSpan(0, 3)), spans.getValue(0).removed)
        assertEquals(listOf(GitWordSpan(0, 3)), spans.getValue(0).added)
    }

    @Test
    fun unifiedEmphasisKeepsEveryCharacterAndAddsTheStrongerTint() {
        val body = "@@ -1,2 +1,2 @@\n keep\n-old value\n+new value"
        val lines = parseGitDiffLines(body)
        val spans = gitUnifiedWordDiff(lines)
        val added = Color(0x2200FF00)
        val removed = Color(0x22FF0000)
        val wordAdded = Color(0x4000FF00)
        val wordRemoved = Color(0x40FF0000)
        val annotated = gitUnifiedDiffAnnotatedString(
            body, added, removed, Color(0x227F7F7F), Color.Gray, spans, wordAdded, wordRemoved,
        )
        // The selectable body is unchanged: the emphasis only layers a stronger tint over changed words.
        assertEquals(body, annotated.text)
        assertTrue(annotated.spanStyles.any { it.item.background == wordAdded })
        assertTrue(annotated.spanStyles.any { it.item.background == wordRemoved })
        // Without word diffs the same body carries no emphasis tint.
        val plain = gitUnifiedDiffAnnotatedString(body, added, removed, Color(0x227F7F7F), Color.Gray)
        assertTrue(plain.spanStyles.none { it.item.background == wordAdded || it.item.background == wordRemoved })
    }

    @Test
    fun splitEmphasisKeepsEveryCharacterAndTintsTheChangedSide() {
        val body = "@@ -1,1 +1,1 @@\n-old value\n+new value"
        val rows = splitDiffBody(body).single().rows
        val spans = gitSplitWordDiff(rows).getValue(0)
        val added = Color(0x2200FF00)
        val removed = Color(0x22FF0000)
        val wordAdded = Color(0x4000FF00)
        val wordRemoved = Color(0x40FF0000)
        val left = gitDiffSplitPaneText(rows[0], left = true, addedBackground = added, removedBackground = removed,
            wordDiff = spans, wordAddedBackground = wordAdded, wordRemovedBackground = wordRemoved)
        val right = gitDiffSplitPaneText(rows[0], left = false, addedBackground = added, removedBackground = removed,
            wordDiff = spans, wordAddedBackground = wordAdded, wordRemovedBackground = wordRemoved)
        assertEquals("old value", left.text)
        assertEquals("new value", right.text)
        assertTrue(left.spanStyles.any { it.item.background == wordRemoved })
        assertTrue(right.spanStyles.any { it.item.background == wordAdded })
    }
}
