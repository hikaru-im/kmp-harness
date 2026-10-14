package im.hikaru.harness.client.git

import androidx.compose.ui.graphics.Color
import kotlin.test.*

/** Pure classification and painting of a unified-diff body already returned by the backend. */
class GitDiffLinesTest {
    private val body = "@@ -1,3 +1,4 @@\n keep\n-old\n+new\n+extra\n tail"

    @Test
    fun classifiesHunkLinesAndTracksBothLineNumbers() {
        val lines = parseGitDiffLines(body)
        assertEquals(
            listOf(
                GitDiffLineKind.HunkHeader,
                GitDiffLineKind.Context,
                GitDiffLineKind.Removed,
                GitDiffLineKind.Added,
                GitDiffLineKind.Added,
                GitDiffLineKind.Context,
            ),
            lines.map { it.kind },
        )
        assertEquals(GitDiffLine(GitDiffLineKind.HunkHeader, "@@ -1,3 +1,4 @@"), lines[0])
        assertEquals(GitDiffLine(GitDiffLineKind.Context, "keep", 1, 1), lines[1])
        assertEquals(GitDiffLine(GitDiffLineKind.Removed, "old", oldNumber = 2), lines[2])
        assertEquals(GitDiffLine(GitDiffLineKind.Added, "new", newNumber = 2), lines[3])
        assertEquals(GitDiffLine(GitDiffLineKind.Added, "extra", newNumber = 3), lines[4])
        assertEquals(GitDiffLine(GitDiffLineKind.Context, "tail", 3, 4), lines[5])
    }

    @Test
    fun eachHunkRestartsNumberingFromItsHeader() {
        val lines = parseGitDiffLines("@@ -10,1 +20,1 @@\n-a\n+b\n@@ -30,1 +40,1 @@\n-c\n+d")
        assertEquals(GitDiffLine(GitDiffLineKind.Removed, "a", oldNumber = 10), lines[1])
        assertEquals(GitDiffLine(GitDiffLineKind.Added, "b", newNumber = 20), lines[2])
        assertEquals(GitDiffLine(GitDiffLineKind.Removed, "c", oldNumber = 30), lines[4])
        assertEquals(GitDiffLine(GitDiffLineKind.Added, "d", newNumber = 40), lines[5])
    }

    @Test
    fun noNewlineMarkerAndBinaryMetaStayUnnumbered() {
        val lines = parseGitDiffLines("@@ -1,1 +1,1 @@\n-old\n\\ No newline at end of file\n+new")
        assertEquals(GitDiffLine(GitDiffLineKind.NoNewline, "\\ No newline at end of file"), lines[2])
        assertNull(lines[2].oldNumber)
        assertNull(lines[2].newNumber)
        assertEquals(
            listOf(GitDiffLineKind.Meta),
            parseGitDiffLines("Binary files a/x and b/x differ").map { it.kind },
        )
    }

    @Test
    fun contentThatStartsWithItsOwnMarkerIsClassifiedByTheFirstCharacter() {
        // Inside a hunk a "++"/"--" content line still carries one leading marker of its own.
        val lines = parseGitDiffLines("@@ -1,0 +1,1 @@\n++plus")
        assertEquals(GitDiffLine(GitDiffLineKind.Added, "+plus", newNumber = 1), lines[1])
    }

    @Test
    fun emptyBodyYieldsNoLines() {
        assertTrue(parseGitDiffLines("").isEmpty())
    }

    @Test
    fun gutterPrintsOneNumberPerLineAndBuffersHeaders() {
        val lines = parseGitDiffLines(body)
        // Context/added lines show the new number, the removed line the old number; the @@ header
        // keeps a blank buffer cell. Each cell is the width plus a two-cell gap (padding is a
        // non-breaking space so it is not trimmed), and one entry per line keeps the gutter aligned.
        assertEquals(
            listOf("   ", "1  ", "2  ", "2  ", "3  ", "4  "),
            gitDiffGutterText(lines).split("\n").map { it.replace('\u00A0', ' ') },
        )
        assertEquals(lines.size, gitDiffGutterText(lines).split("\n").size)
    }

    @Test
    fun gutterPadsToACommonWidthSoTheColumnStaysAligned() {
        val lines = parseGitDiffLines("@@ -1,1 +1,1 @@\n-old\n+new\n@@ -100,1 +200,1 @@\n-x\n+y")
        assertEquals(
            listOf("     ", "  1  ", "  1  ", "     ", "100  ", "200  "),
            gitDiffGutterText(lines).split("\n").map { it.replace('\u00A0', ' ') },
        )
        // Padding is non-breaking space, so no cell is trimmed away.
        assertTrue(gitDiffGutterText(lines).none { it == ' ' })
    }

    @Test
    fun gutterBuffersUnnumberedLinesAndMapsEachKind() {
        val lines = parseGitDiffLines("@@ -1,1 +1,1 @@\n-old\n\\ No newline at end of file\n+new")
        assertNull(gitDiffGutterNumber(lines[0]))
        assertEquals(1, gitDiffGutterNumber(lines[1]))
        assertNull(gitDiffGutterNumber(lines[2]))
        assertEquals(1, gitDiffGutterNumber(lines[3]))
        assertNull(gitDiffGutterNumber(GitDiffLine(GitDiffLineKind.Meta, "Binary files differ")))
        assertEquals("", gitDiffGutterText(emptyList()))
    }

    @Test
    fun gutterTintsAddedRemovedAndSeparatorCellsLikeTheBody() {
        val lines = parseGitDiffLines(body)
        val added = Color(0x2200FF00)
        val removed = Color(0x22FF0000)
        val separator = Color(0x227F7F7F)
        val tineless = gitDiffGutterAnnotatedString(lines, Color.Unspecified, Color.Unspecified, Color.Unspecified)
        // Same layout as the plain text, so the tint never shifts the column.
        assertEquals(gitDiffGutterText(lines), tineless.text)
        val gutter = gitDiffGutterAnnotatedString(lines, added, removed, separator)
        fun backgroundAt(lineIndex: Int): Color? {
            val start = gitDiffGutterText(lines).split("\n").take(lineIndex).sumOf { it.length + 1 }
            return gutter.spanStyles.firstOrNull { it.start == start }?.item?.background
        }
        // 0 = @@ header (separator tint), 2 = removed, 3 = added, 1/5 = context (no tint).
        assertEquals(separator, backgroundAt(0))
        assertNull(backgroundAt(1))
        assertEquals(removed, backgroundAt(2))
        assertEquals(added, backgroundAt(3))
        assertNull(backgroundAt(5))
    }

    @Test
    fun annotatedBodyPreservesEveryCharacterInOrder() {
        val annotated = gitUnifiedDiffAnnotatedString(body, Color(0x2200FF00), Color(0x22FF0000), Color(0x227F7F7F), Color.Gray)
        // The markers and the newline layout must survive exactly: the selectable text is unchanged.
        assertEquals(body, annotated.text)
    }

    @Test
    fun annotatedBodyTintsAddedAndRemovedSpans() {
        val added = Color(0x2200FF00)
        val removed = Color(0x22FF0000)
        val annotated = gitUnifiedDiffAnnotatedString(body, added, removed, Color(0x227F7F7F), Color.Gray)
        val parts = body.lines()
        // Offsets: each previous line contributes its length plus one newline.
        val removedStart = parts[0].length + 1 + parts[1].length + 1
        val addedStart = removedStart + parts[2].length + 1
        assertEquals(removed, annotated.spanStyles.first { it.start == removedStart }.item.background)
        assertEquals(added, annotated.spanStyles.first { it.start == addedStart }.item.background)
    }

    @Test
    fun bodyLineTextKeepsItsMarkerAndHeader() {
        val lines = parseGitDiffLines(body)
        assertEquals("@@ -1,3 +1,4 @@", gitDiffLineText(lines[0]))
        assertEquals(" keep", gitDiffLineText(lines[1]))
        assertEquals("-old", gitDiffLineText(lines[2]))
        assertEquals("+new", gitDiffLineText(lines[3]))
    }

    @Test
    fun perLineBodyPiecesReassembleTheWholePaintedBody() {
        val added = Color(0x2200FF00)
        val removed = Color(0x22FF0000)
        val separator = Color(0x227F7F7F)
        val muted = Color.Gray
        val lines = parseGitDiffLines(body)
        val whole = gitUnifiedDiffAnnotatedString(body, added, removed, separator, muted)
        val pieces = lines.map { gitDiffUnifiedLineAnnotatedString(it, added, removed, separator, muted) }
        // The word-wrap variant paints the same body one line at a time; taken together they are the
        // whole-body rendering, text and tints included, so the two renderings cannot drift.
        assertEquals(whole.text, pieces.joinToString("\n") { it.text })
        val shifted = buildList {
            var offset = 0
            pieces.forEachIndexed { index, piece ->
                piece.spanStyles.forEach { add(it.copy(start = it.start + offset, end = it.end + offset)) }
                offset += piece.text.length + if (index < pieces.lastIndex) 1 else 0
            }
        }
        assertEquals(whole.spanStyles, shifted)
    }

    @Test
    fun perLineGutterCellsReassembleTheWholeBodyGutter() {
        val lines = parseGitDiffLines(body)
        val width = gitDiffGutterWidth(lines)
        assertEquals(1, width)
        val cells = lines.map {
            gitDiffGutterLineAnnotatedString(it, width, Color.Unspecified, Color.Unspecified, Color.Unspecified)
        }
        assertEquals(gitDiffGutterText(lines), cells.joinToString("\n") { it.text })
    }

    @Test
    fun perLineGutterSharesOneColumnWidthAcrossCells() {
        val lines = parseGitDiffLines("@@ -1,1 +1,1 @@\n+new\n@@ -100,1 +200,1 @@\n+y")
        val width = gitDiffGutterWidth(lines)
        assertEquals(3, width)
        // The shorter number pads to the widest one (non-breaking space, which no trimming removes),
        // so every wrapped row lines up on one column.
        assertEquals(
            "\u00A0\u00A01\u00A0\u00A0",
            gitDiffGutterLineAnnotatedString(lines[1], width, Color.Unspecified, Color.Unspecified, Color.Unspecified).text,
        )
        assertEquals(
            "200\u00A0\u00A0",
            gitDiffGutterLineAnnotatedString(lines[3], width, Color.Unspecified, Color.Unspecified, Color.Unspecified).text,
        )
    }

    @Test
    fun perLineGutterTintsEachCellLikeTheWholeBodyGutter() {
        val lines = parseGitDiffLines(body)
        val added = Color(0x2200FF00)
        val removed = Color(0x22FF0000)
        val separator = Color(0x227F7F7F)
        val width = gitDiffGutterWidth(lines)
        val cells = lines.map { gitDiffGutterLineAnnotatedString(it, width, added, removed, separator) }
        assertEquals(separator, cells[0].spanStyles.single().item.background)
        assertTrue(cells[1].spanStyles.isEmpty())
        assertEquals(removed, cells[2].spanStyles.single().item.background)
        assertEquals(added, cells[3].spanStyles.single().item.background)
    }

    private val splitBody = "@@ -199,2 +199,2 @@\n-gone\n+added\n kept"

    @Test
    fun splitGutterPrintsTheOldNumberLeftAndTheNewNumberRight() {
        val rows = splitDiffBody(splitBody).single().rows
        val width = gitDiffSplitGutterWidth(rows)
        // Both panes share one column width, so a shorter number pads with non-breaking spaces.
        assertEquals(3, width)
        assertEquals("199", gitDiffSplitGutterText(rows[0], width, left = true).trimEnd('\u00A0'))
        assertEquals("199", gitDiffSplitGutterText(rows[0], width, left = false).trimEnd('\u00A0'))
        // A context row advances both counters even though the panes show the same text.
        assertEquals("200", gitDiffSplitGutterText(rows[1], width, left = true).trimEnd('\u00A0'))
        assertEquals("200", gitDiffSplitGutterText(rows[1], width, left = false).trimEnd('\u00A0'))
    }

    @Test
    fun splitGutterBuffersTheFilledInSideWithASameWidthBlankCell() {
        val rows = splitDiffBody("@@ -1,1 +1,2 @@\n base\n+extra").single().rows
        val width = gitDiffSplitGutterWidth(rows)
        assertEquals(1, width)
        // The addition has no old line, so the left cell keeps a blank buffer of the same width as a
        // numbered cell (one number column plus the two-cell gap).
        assertEquals("\u00A0\u00A0\u00A0", gitDiffSplitGutterText(rows[1], width, left = true))
        assertEquals("2\u00A0\u00A0", gitDiffSplitGutterText(rows[1], width, left = false))
    }

    @Test
    fun splitGutterTintsTheChangedPaneAndLeavesContextUntinted() {
        val rows = splitDiffBody(splitBody).single().rows
        val added = Color(0x2200FF00)
        val removed = Color(0x22FF0000)
        val width = gitDiffSplitGutterWidth(rows)
        // A change row removes on the left pane and adds on the right pane, so each side is tinted
        // with its own colour while the cell text keeps the plain layout.
        val left = gitDiffSplitGutterAnnotatedString(rows[0], width, left = true, addedBackground = added, removedBackground = removed)
        val right = gitDiffSplitGutterAnnotatedString(rows[0], width, left = false, addedBackground = added, removedBackground = removed)
        assertEquals(removed, left.spanStyles.single().item.background)
        assertEquals(added, right.spanStyles.single().item.background)
        assertEquals(gitDiffSplitGutterText(rows[0], width, left = true), left.text)
        // Context shows no tint at all.
        val context = gitDiffSplitGutterAnnotatedString(rows[1], width, left = true, addedBackground = added, removedBackground = removed)
        assertTrue(context.spanStyles.isEmpty())
        assertEquals(gitDiffSplitGutterText(rows[1], width, left = true), context.text)
    }

    @Test
    fun splitGutterTintsOnlyTheSideThatCarriesAOneSidedChange() {
        val rows = splitDiffBody("@@ -1,1 +1,2 @@\n base\n+extra").single().rows
        val added = Color(0x2200FF00)
        val removed = Color(0x22FF0000)
        // Addition row: the right cell is tinted, the buffered left cell stays untinted.
        assertTrue(gitDiffSplitGutterAnnotatedString(rows[1], 1, left = true, addedBackground = added, removedBackground = removed).spanStyles.isEmpty())
        assertEquals(
            added,
            gitDiffSplitGutterAnnotatedString(rows[1], 1, left = false, addedBackground = added, removedBackground = removed).spanStyles.single().item.background,
        )
    }

    @Test
    fun splitPaneTextTintsOnlyTheChangedSideAndKeepsEveryCharacter() {
        val rows = splitDiffBody(splitBody).single().rows
        val added = Color(0x2200FF00)
        val removed = Color(0x22FF0000)
        // Change row: the left pane text carries the removal tint, the right pane the addition tint,
        // and both keep every character so the selectable text and the semantics string are unchanged.
        val left = gitDiffSplitPaneText(rows[0], left = true, addedBackground = added, removedBackground = removed)
        assertEquals("gone", left.text)
        assertEquals(removed, left.spanStyles.single().item.background)
        val right = gitDiffSplitPaneText(rows[0], left = false, addedBackground = added, removedBackground = removed)
        assertEquals("added", right.text)
        assertEquals(added, right.spanStyles.single().item.background)
        // Context row: plain text on both sides, no tint.
        val context = gitDiffSplitPaneText(rows[1], left = true, addedBackground = added, removedBackground = removed)
        assertEquals("kept", context.text)
        assertTrue(context.spanStyles.isEmpty())
    }
}
