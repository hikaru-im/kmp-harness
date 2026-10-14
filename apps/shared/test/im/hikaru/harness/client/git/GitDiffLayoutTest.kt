package im.hikaru.harness.client.git

import kotlin.test.*

/** Pure alignment of an already-parsed hunk body and the auto-layout decision (ASAR D01). */
class GitDiffLayoutTest {
    private fun file(added: Int, deleted: Int) = GitDiffFile(
        path = "src/app.kt", originalPath = null, status = GitDiffFileStatus.Modified,
        addedLines = added, deletedLines = deleted, binary = false, header = "", body = "",
    )

    @Test
    fun pairsDeletionsWithAdditionsAndKeepsContextOnBothSides() {
        val hunks = splitDiffBody("@@ -1,3 +1,3 @@\n keep\n-old\n+new\n+extra\n tail")
        assertEquals(1, hunks.size)
        assertEquals("@@ -1,3 +1,3 @@", hunks.single().header)
        assertEquals(
            listOf(
                GitDiffSplitRow("keep", "keep", GitDiffSplitRowKind.Context, oldNumber = 1, newNumber = 1),
                GitDiffSplitRow("old", "new", GitDiffSplitRowKind.Change, oldNumber = 2, newNumber = 2),
                GitDiffSplitRow("", "extra", GitDiffSplitRowKind.Addition, newNumber = 3),
                GitDiffSplitRow("tail", "tail", GitDiffSplitRowKind.Context, oldNumber = 3, newNumber = 4),
            ),
            hunks.single().rows,
        )
    }

    @Test
    fun unevenRunsFillTheMissingSideAndSplitMultipleHunks() {
        val hunks = splitDiffBody(
            "@@ -1,2 +1,1 @@\n-gone-a\n-gone-b\n+kept\n@@ -9,1 +8,1 @@\n-only\n+new",
        )
        assertEquals(2, hunks.size)
        assertEquals(
            listOf(
                GitDiffSplitRow("gone-a", "kept", GitDiffSplitRowKind.Change, oldNumber = 1, newNumber = 1),
                GitDiffSplitRow("gone-b", "", GitDiffSplitRowKind.Deletion, oldNumber = 2),
            ),
            hunks[0].rows,
        )
        assertEquals(
            listOf(GitDiffSplitRow("only", "new", GitDiffSplitRowKind.Change, oldNumber = 9, newNumber = 8)),
            hunks[1].rows,
        )
    }

    @Test
    fun contentThatStartsWithMarkersIsClassifiedByItsOwnMarkerAndNewlineMarkerIsIgnored() {
        val hunks = splitDiffBody("@@ -1,1 +1,1 @@\n---foo\n+++bar\n\\ No newline at end of file")
        assertEquals(
            listOf(GitDiffSplitRow("--foo", "++bar", GitDiffSplitRowKind.Change, oldNumber = 1, newNumber = 1)),
            hunks.single().rows,
        )
        assertTrue(splitDiffBody("").isEmpty())
    }

    @Test
    fun eachHunkRestartsBothSplitGutterCountersFromItsHeader() {
        // The two panes number from the header's old/new start, so a second hunk must not continue
        // the first hunk's counters.
        val hunks = splitDiffBody("@@ -10,1 +20,1 @@\n-a\n+b\n@@ -30,1 +40,1 @@\n-c\n+d")
        assertEquals(GitDiffSplitRow("a", "b", GitDiffSplitRowKind.Change, oldNumber = 10, newNumber = 20), hunks[0].rows.single())
        assertEquals(GitDiffSplitRow("c", "d", GitDiffSplitRowKind.Change, oldNumber = 30, newNumber = 40), hunks[1].rows.single())
    }

    @Test
    fun autoSplitsOnlyWideTwoSidedFiles() {
        val twoSided = file(added = 3, deleted = 2)
        val oneSided = file(added = 3, deleted = 0)
        assertEquals(GitDiffLayout.Split, effectiveGitDiffLayout(GitDiffLayout.Auto, twoSided, narrowPane = false))
        assertEquals(GitDiffLayout.Unified, effectiveGitDiffLayout(GitDiffLayout.Auto, twoSided, narrowPane = true))
        assertEquals(GitDiffLayout.Unified, effectiveGitDiffLayout(GitDiffLayout.Auto, oneSided, narrowPane = false))
        assertEquals(GitDiffLayout.Split, effectiveGitDiffLayout(GitDiffLayout.Split, oneSided, narrowPane = true))
        assertEquals(GitDiffLayout.Unified, effectiveGitDiffLayout(GitDiffLayout.Unified, twoSided, narrowPane = false))
    }
}
