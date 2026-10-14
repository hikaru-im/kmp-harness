package im.hikaru.harness.client.git

import kotlin.test.*

/**
 * `codex.review.imports.hide` (隐藏导入内容): which change lines count as top-level imports and how
 * the unified and split renderings drop them. The classifier is a Harness choice (the original's is not
 * in the ASAR plaintext), so these cases pin the rule the production toggle actually uses.
 */
class GitDiffImportsTest {
    @Test
    fun detectsTopLevelImportFormsAcrossLanguages() {
        assertTrue(gitDiffIsImportOnlyContent("import im.hikaru.harness.Foo"))
        assertTrue(gitDiffIsImportOnlyContent("import \"fmt\""))
        assertTrue(gitDiffIsImportOnlyContent("from os import path"))
        assertTrue(gitDiffIsImportOnlyContent("#include <stdio.h>"))
        assertTrue(gitDiffIsImportOnlyContent("use std::collections::HashMap;"))
        assertTrue(gitDiffIsImportOnlyContent("using System.Text;"))
        assertTrue(gitDiffIsImportOnlyContent("require('./boot')"))
        assertTrue(gitDiffIsImportOnlyContent("@import \"theme\";"))
    }

    @Test
    fun ignoresIndentedImportsOrdinaryCodeAndSimilarKeywords() {
        // Not top-level: the original hides *top-level* import-only changes.
        assertFalse(gitDiffIsImportOnlyContent("    import os"))
        assertFalse(gitDiffIsImportOnlyContent("\timport os"))
        // Plain code and near-miss keywords must not be filtered.
        assertFalse(gitDiffIsImportOnlyContent("val important = 1"))
        assertFalse(gitDiffIsImportOnlyContent("importantThing()"))
        assertFalse(gitDiffIsImportOnlyContent("from = 5"))
        assertFalse(gitDiffIsImportOnlyContent("used = true"))
        assertFalse(gitDiffIsImportOnlyContent(""))
    }

    @Test
    fun unifiedDropsImportChangesButKeepsContextHeadersAndMeta() {
        val body = "leading meta\n@@ -1,3 +1,3 @@\n import a\n-import b\n+import c\n value"
        val lines = parseGitDiffLines(body)
        val kept = gitDiffHiddenImportLines(lines)
        // The meta line, the hunk header, the shared ` import a` context and the ` value` context stay;
        // only the added/removed import lines are gone.
        assertEquals(
            listOf("leading meta", "@@ -1,3 +1,3 @@", " import a", " value"),
            kept.map { gitDiffLineText(it) },
        )
    }

    @Test
    fun unifiedKeepsAnImportLookingContextLine() {
        // A context line is never a "change", so the toggle leaves it in place.
        val lines = parseGitDiffLines("@@ -1,1 +1,1 @@\n import a")
        assertEquals(lines, gitDiffHiddenImportLines(lines))
    }

    @Test
    fun unifiedKeepsNonImportChangesOnTheSameHunk() {
        val lines = parseGitDiffLines("@@ -1,2 +1,3 @@\n-import a\n+import b\n-oldThing()\n+newThing()")
        assertEquals(
            listOf("-oldThing()", "+newThing()"),
            gitDiffHiddenImportLines(lines).filter { it.kind != GitDiffLineKind.HunkHeader }.map { gitDiffLineText(it) },
        )
    }

    @Test
    fun splitDropsImportOnlyRowsAndKeepsMixedPairs() {
        val rows = listOf(
            GitDiffSplitRow("import a", "import a", GitDiffSplitRowKind.Context, oldNumber = 1, newNumber = 1),
            GitDiffSplitRow("import b", "import c", GitDiffSplitRowKind.Change, oldNumber = 2, newNumber = 2),
            GitDiffSplitRow("import d", "value = d", GitDiffSplitRowKind.Change, oldNumber = 3, newNumber = 3),
            GitDiffSplitRow("", "import e", GitDiffSplitRowKind.Addition, newNumber = 4),
            GitDiffSplitRow("oldThing()", "", GitDiffSplitRowKind.Deletion, oldNumber = 5),
        )
        assertEquals(
            listOf(GitDiffSplitRowKind.Context, GitDiffSplitRowKind.Change, GitDiffSplitRowKind.Deletion),
            gitDiffHiddenImportRows(rows).map { it.kind },
        )
    }

    @Test
    fun splitHunksKeepTheirHeadersWhenEveryRowIsHid() {
        val hunks = splitDiffBody("@@ -1,1 +1,1 @@\n-import a\n+import b")
        val kept = gitDiffHiddenImportHunks(hunks)
        assertEquals(1, kept.size)
        assertEquals("@@ -1,1 +1,1 @@", kept.single().header)
        assertTrue(kept.single().rows.isEmpty())
    }
}
