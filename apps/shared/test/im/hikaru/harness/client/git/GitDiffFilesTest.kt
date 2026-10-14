package im.hikaru.harness.client.git

import kotlin.test.*

/** Pure text splitting of the argv `git diff` output already returned by the backend. */
class GitDiffFilesTest {
    @Test
    fun modifiedFileKeepsPathAndCountsOnlyHunkLines() {
        val document = parseGitDiff(
            """
            diff --git a/src/app.kt b/src/app.kt
            index 1111111..2222222 100644
            --- a/src/app.kt
            +++ b/src/app.kt
            @@ -1,3 +1,4 @@
             keep
            -old
            +new
            +extra
             tail
            """.trimIndent() + "\n",
        )
        assertEquals(1, document.files.size)
        val file = document.files.single()
        assertEquals("src/app.kt", file.path)
        assertNull(file.originalPath)
        assertEquals(GitDiffFileStatus.Modified, file.status)
        assertEquals(2, file.addedLines)
        assertEquals(1, file.deletedLines)
        assertEquals(3, file.changeCount)
        assertFalse(file.binary)
        assertEquals(2, document.addedLines)
        assertEquals(1, document.deletedLines)
        assertFalse(document.leadingContent)
        assertEquals(
            "@@ -1,3 +1,4 @@\n keep\n-old\n+new\n+extra\n tail",
            file.body,
        )
        assertTrue(file.header.startsWith("diff --git a/src/app.kt b/src/app.kt"))
    }

    @Test
    fun addedDeletedRenamedCopiedAndBinaryAreClassified() {
        val document = parseGitDiff(
            """
            diff --git a/new.kt b/new.kt
            new file mode 100644
            index 0000000..1111111
            --- /dev/null
            +++ b/new.kt
            @@ -0,0 +1,1 @@
            +only
            diff --git a/gone.kt b/gone.kt
            deleted file mode 100644
            index 1111111..0000000
            --- a/gone.kt
            +++ /dev/null
            @@ -1,1 +0,0 @@
            -only
            diff --git a/old.kt b/renamed.kt
            similarity index 100%
            rename from old.kt
            rename to renamed.kt
            diff --git a/src/a.kt b/src/b.kt
            similarity index 100%
            copy from src/a.kt
            copy to src/b.kt
            diff --git a/logo.png b/logo.png
            index 1111111..2222222 100644
            Binary files a/logo.png and b/logo.png differ
            """.trimIndent() + "\n",
        )
        assertEquals(5, document.files.size)
        assertEquals(listOf(GitDiffFileStatus.Added, GitDiffFileStatus.Deleted, GitDiffFileStatus.Renamed,
            GitDiffFileStatus.Copied, GitDiffFileStatus.Modified), document.files.map { it.status })
        assertEquals("new.kt", document.files[0].path)
        assertEquals(1, document.files[0].addedLines)
        assertEquals("gone.kt", document.files[1].path)
        assertEquals(1, document.files[1].deletedLines)
        assertEquals("renamed.kt", document.files[2].path)
        assertEquals("old.kt", document.files[2].originalPath)
        assertEquals("", document.files[2].body)
        assertEquals("src/b.kt", document.files[3].path)
        assertEquals("src/a.kt", document.files[3].originalPath)
        assertTrue(document.files[4].binary)
        assertEquals(0, document.files[4].addedLines)
        assertEquals("logo.png", document.files[4].path)
    }

    @Test
    fun multipleFilesSplitTotalsAndNoCrossFileBleed() {
        val document = parseGitDiff(MULTI_FILE_PATCH)
        assertEquals(2, document.files.size)
        assertEquals(listOf("src/new.kt", "src/app.kt"), document.files.map { it.path })
        assertEquals(2, document.addedLines)
        assertEquals(1, document.deletedLines)
        assertEquals("src/old.kt", document.files[0].originalPath)
        assertTrue(document.files[0].body.startsWith("@@ -1,2 +1,2 @@"))
        assertFalse(document.files[0].body.contains("extra"))
        assertTrue(document.files[1].body.contains("+extra"))
        assertFalse(document.files[1].body.contains("changed"))
    }

    @Test
    fun quotedUnicodeAndUnquotedSpacedPathsAreDecoded() {
        val quoted = parseGitDiff(
            "diff --git \"a/\\346\\226\\207.txt\" \"b/\\346\\226\\207.txt\"\n" +
                "index 1111111..2222222 100644\n--- \"a/\\346\\226\\207.txt\"\n+++ \"b/\\346\\226\\207.txt\"\n" +
                "@@ -1,1 +1,1 @@\n-a\n+b\n",
        )
        assertEquals("文.txt", quoted.files.single().path)
        assertNull(quoted.files.single().originalPath)
        val spaced = parseGitDiff(
            "diff --git a/my file.txt b/my file.txt\n--- a/my file.txt\n+++ b/my file.txt\n@@ -1,1 +1,1 @@\n-a\n+b\n",
        )
        assertEquals("my file.txt", spaced.files.single().path)
    }

    @Test
    fun emptyAndLeadingTextAreReportedWithoutInventingFiles() {
        assertEquals(GitDiffDocument(emptyList(), 0, 0), parseGitDiff(""))
        val document = parseGitDiff("stray preamble\n" + MULTI_FILE_PATCH)
        assertTrue(document.leadingContent)
        assertEquals(2, document.files.size)
    }

    @Test
    fun hunkLinesThatThemselvesStartWithDoubleMarkersAreCounted() {
        // Inside a hunk every line carries its own marker, so content like "--old"/"++new" is still
        // an ordinary deletion/addition rather than a "---"/"+++" file header.
        val document = parseGitDiff(
            "diff --git a/f.kt b/f.kt\n--- a/f.kt\n+++ b/f.kt\n@@ -1,1 +1,1 @@\n---old\n+++new\n",
        )
        val file = document.files.single()
        assertEquals(1, file.addedLines)
        assertEquals(1, file.deletedLines)
        assertEquals(2, document.addedLines + document.deletedLines)
    }
}

internal const val MULTI_FILE_PATCH = "diff --git a/src/old.kt b/src/new.kt\n" +
    "similarity index 90%\n" +
    "rename from src/old.kt\n" +
    "rename to src/new.kt\n" +
    "--- a/src/old.kt\n" +
    "+++ b/src/new.kt\n" +
    "@@ -1,2 +1,2 @@\n" +
    "-changed\n" +
    "+keep\n" +
    " same\n" +
    "diff --git a/src/app.kt b/src/app.kt\n" +
    "--- a/src/app.kt\n" +
    "+++ b/src/app.kt\n" +
    "@@ -1,1 +1,2 @@\n" +
    " base\n" +
    "+extra\n"
