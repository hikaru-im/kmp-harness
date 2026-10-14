package im.hikaru.harness.desktop.git

import im.hikaru.harness.client.git.*
import kotlin.test.*

class GitParsersTest {
    @Test
    fun porcelainUsesNulPathsAndDestinationBeforeOriginal() {
        val changes = parseGitStatus("RM 新 name\n.kt\u0000old name\n.kt\u0000?? untracked\t文件\u0000UU conflicted\u0000 M unstaged\u0000")
        assertEquals(4, changes.size)
        assertEquals("新 name\n.kt", changes[0].path)
        assertEquals("old name\n.kt", changes[0].originalPath)
        assertTrue(changes[0].staged)
        assertTrue(changes[0].unstaged)
        assertTrue(changes[1].untracked)
        assertFalse(changes[1].staged)
        assertEquals("untracked\t文件", changes[1].path)
        assertTrue(changes[2].conflicted)
        assertEquals(2, GitStatusSnapshot(changes, null, null).stagedCount)
        assertEquals(3, GitStatusSnapshot(changes, null, null).unstagedCount)
    }

    @Test
    fun branchReferencesUseFieldTripletsAndCurrentMarker() {
        val oid = "a".repeat(40)
        val branches = parseGitBranches("refs/heads/main\u0000$oid\u0000*\u0000\nrefs/remotes/origin/main\u0000$oid\u0000 \u0000\n")
        assertEquals(listOf("refs/heads/main", "refs/remotes/origin/main"), branches.map { it.ref })
        assertTrue(branches[0].current)
        assertTrue(branches[1].remote)
        assertFalse(branches[1].current)
        assertEquals(emptyList(), parseGitBranches(""))
    }

    @Test
    fun worktreesKeepPathsAndReasonsIncludingNewlines() {
        val oid = "b".repeat(40)
        val records = parseGitWorktrees("worktree /main 项目\npath\u0000HEAD $oid\u0000branch refs/heads/main\u0000locked user reason\nkeep\u0000\u0000" +
            "worktree /detached\u0000HEAD $oid\u0000detached\u0000prunable gitdir file missing\u0000\u0000" +
            "worktree /bare\u0000bare\u0000\u0000")
        assertEquals(3, records.size)
        assertEquals("/main 项目\npath", records[0].path)
        assertTrue(records[0].locked)
        assertEquals("user reason\nkeep", records[0].lockReason)
        assertTrue(records[1].detached)
        assertTrue(records[1].prunable)
        assertEquals("gitdir file missing", records[1].pruneReason)
        assertTrue(records[2].bare)
        assertNull(records[2].headOid)
    }

    @Test
    fun malformedAndTruncatedOutputsFailInsteadOfReturningCleanData() {
        listOf(" M no terminator", "R  destination\u0000", "ZZ path\u0000", " M \u0000").forEach { output ->
            assertEquals(GitFailureKind.MalformedOutput, assertFailsWith<GitReadException> { parseGitStatus(output) }.failure.kind)
        }
        listOf("refs/heads/main\u0000${"a".repeat(40)}\u0000*", "refs/heads/main\u0000invalid\u0000*\u0000\n").forEach { output ->
            assertFailsWith<GitReadException> { parseGitBranches(output) }
        }
        assertFailsWith<GitReadException> { parseGitWorktrees("worktree /repo\u0000HEAD invalid\u0000\u0000") }
        assertFailsWith<GitReadException> { parseGitWorktrees("worktree /repo") }
        assertEquals(emptyList(), parseGitStatus(""))
        assertEquals(emptyList(), parseGitWorktrees(""))
    }
}
