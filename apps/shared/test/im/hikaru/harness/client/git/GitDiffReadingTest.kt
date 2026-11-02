package im.hikaru.harness.client.git

import kotlin.test.*

class GitDiffReadingTest {
    private val patch = """
        diff --git a/a.kt b/a.kt
        --- a/a.kt
        +++ b/a.kt
        @@ -2,3 +2,4 @@ fun a() {
         context2
        -old
        +new
        +extra
         context3
        @@ -10,3 +11,3 @@ fun b() {
         c10
        -c11
        +d11
         c12
    """.trimIndent()

    @Test
    fun restoredLimitsOpenExactlyTwentyFiveFilesAndTwoThousandChangedLines() {
        val exactly = GitDiffDocument(
            files = List(GitDiffAutoOpenMaxFiles) { file("f$it.kt", added = 40, deleted = 40) },
            addedLines = 1000, deletedLines = 1000,
        )
        assertTrue(gitDiffOpensByDefault(exactly))
        assertFalse(gitDiffOpensByDefault(exactly.copy(files = exactly.files + file("extra.kt", 1, 0), addedLines = 1001)))
        assertFalse(gitDiffOpensByDefault(exactly.copy(addedLines = 1001, deletedLines = 1000)))
        assertTrue(gitDiffOpensByDefault(exactly.copy(addedLines = 1001, deletedLines = 999)))
    }

    @Test
    fun hunksKeepCountsDefaultsAndTheirOwnBodyLines() {
        val hunks = parseGitDiffHunks(parseGitDiff(patch).files.single().body)
        assertEquals(2, hunks.size)
        assertEquals(GitDiffHunk(2, 3, 2, 4, " fun a() {", listOf(
            " context2", "-old", "+new", "+extra", " context3",
        )), hunks[0])
        assertEquals(10, hunks[1].oldStart)
        assertEquals(3, hunks[1].oldCount)
        assertEquals(11, hunks[1].newStart)
        assertEquals(3, hunks[1].newCount)
        assertEquals(" fun b() {", hunks[1].section)

        val omitted = parseGitDiffHunks("@@ -1 +1 @@\n-a\n+b")
        assertEquals(GitDiffHunk(1, 1, 1, 1, "", listOf("-a", "+b")), omitted.single())

        val deletion = parseGitDiffHunks("@@ -3,2 +2,0 @@\n-a\n-b")
        assertEquals(0, deletion.single().newCount)
    }

    @Test
    fun contextGapsUseNewSideLineNumbersAndClampAtTheFileEnd() {
        val file = parseGitDiff(patch).files.single()
        val hunks = parseGitDiffHunks(file.body)
        val fullText = (1..13).joinToString("\n") { "l$it" } + "\n"
        val gaps = gitDiffContextGaps(hunks, fullText)
        assertEquals(3, gaps.size)
        assertEquals(listOf("l1"), gaps[0].lines)
        assertEquals(listOf("l6", "l7", "l8", "l9", "l10"), gaps[1].lines)
        assertTrue(gaps[2].lines.isEmpty())
        assertEquals(listOf(0, 1, 2), gaps.map { it.hunkIndex })
    }

    @Test
    fun expansionRebuildsHeaderArithmeticAndKeepsBothGapEndsDistinct() {
        val file = parseGitDiff(patch).files.single()
        val hunks = parseGitDiffHunks(file.body)
        val fullText = (1..13).joinToString("\n") { "l$it" }
        val gaps = gitDiffContextGaps(hunks, fullText)

        val expanded = gitDiffExpandedBody(
            hunks, gaps,
            above = mapOf(0 to 1, 1 to 1),
            below = mapOf(1 to 1),
        )
        val lines = expanded.split('\n')
        assertEquals("@@ -1,5 +1,6 @@ fun a() {", lines[0])
        assertEquals(listOf(" l1", " context2", "-old", "+new", "+extra", " context3", " l6"), lines.subList(1, 8))
        assertEquals("@@ -9,4 +10,4 @@ fun b() {", lines[8])
        assertEquals(listOf(" l10", " c10", "-c11", "+d11", " c12"), lines.subList(9, 14))
        assertEquals(14, lines.size)
    }

    @Test
    fun overlappingAndOverlongExpansionIsClampedToTheGap() {
        val file = parseGitDiff(patch).files.single()
        val hunks = parseGitDiffHunks(file.body)
        val fullText = (1..13).joinToString("\n") { "l$it" }
        val gaps = gitDiffContextGaps(hunks, fullText)

        // The middle gap has five lines; asking for both ends more than once must not duplicate any.
        val expanded = gitDiffExpandedBody(hunks, gaps, above = mapOf(1 to 9), below = mapOf(1 to 9))
        val lines = expanded.split('\n')
        assertEquals(1, lines.count { it == " l6" })
        assertEquals(1, lines.count { it == " l10" })
        assertEquals(5, lines.count { it in listOf(" l6", " l7", " l8", " l9", " l10") })
    }

    @Test
    fun aSearchHitMapsToTheExpandedBodyLineAfterPrependedContext() {
        val file = parseGitDiff(patch).files.single()
        val hunks = parseGitDiffHunks(file.body)
        val fullText = (1..13).joinToString("\n") { "l$it" }
        val gaps = gitDiffContextGaps(hunks, fullText)
        val above = mapOf(0 to 1, 1 to 1)
        val below = mapOf(1 to 1)

        assertEquals(0, gitDiffExpandedLineIndex(0, hunks, gaps, above, below))
        assertEquals(2, gitDiffExpandedLineIndex(1, hunks, gaps, above, below))
        assertEquals(8, gitDiffExpandedLineIndex(6, hunks, gaps, above, below))
        assertEquals(10, gitDiffExpandedLineIndex(7, hunks, gaps, above, below))
    }

    @Test
    fun searchIsCaseInsensitiveCountsEveryOccurrenceAndHonorsTheLimit() {
        val files = listOf(
            file("a.kt", 1, 0, body = "@@ -1 +1 @@\n+Alpha alpha ALPHA"),
            file("b.kt", 1, 0, body = "@@ -1 +1 @@\n+beta"),
        )
        val document = GitDiffDocument(files, 2, 0)
        val result = findGitDiffMatches(document, "alpha")
        assertEquals(3, result.matches.size)
        assertFalse(result.truncated)
        assertEquals(listOf("a.kt", "a.kt", "a.kt"), result.matches.map { it.filePath })
        assertEquals(listOf(1, 7, 13), result.matches.map { it.startInLine })
        assertEquals("+Alpha alpha ALPHA", result.matches.first().lineText)

        val limited = findGitDiffMatches(document, "a", limit = 2)
        assertEquals(2, limited.matches.size)
        assertTrue(limited.truncated)

        assertTrue(findGitDiffMatches(document, "  ").matches.isEmpty())
    }

    private fun file(
        path: String,
        added: Int,
        deleted: Int,
        body: String = "@@ -1 +1 @@\n+x",
    ) = GitDiffFile(
        path = path,
        originalPath = null,
        status = GitDiffFileStatus.Modified,
        addedLines = added,
        deletedLines = deleted,
        binary = false,
        header = "diff --git a/$path b/$path\n--- a/$path\n+++ b/$path",
        body = body,
    )
}
