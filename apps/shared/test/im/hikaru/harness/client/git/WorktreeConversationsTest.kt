package im.hikaru.harness.client.git

import im.hikaru.harness.client.connection.SessionId
import im.hikaru.harness.client.connection.SessionSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorktreeConversationsTest {
    private fun session(id: String, createdAt: Long, cwd: String?) =
        SessionSummary(SessionId(id), createdAt, cwd = cwd)

    @Test
    fun onlySessionsWhoseCwdIsExactlyTheWorktreeDirAreListed() {
        val root = "/Volumes/Data/worktrees/feature"
        val mine = session("mine", 1L, root)
        val nested = session("nested", 2L, "$root/subdir")
        val other = session("other", 3L, "/Volumes/Data/worktrees/other")
        val unknown = session("unknown", 4L, null)
        assertEquals(listOf(mine), worktreeConversations(root, listOf(nested, other, unknown, mine)))
    }

    @Test
    fun trailingSlashIsIgnoredOnBothSidesAndBlankRootNeverMatches() {
        val root = "/Volumes/Data/worktrees/feature/"
        val withSlash = session("with-slash", 1L, "/Volumes/Data/worktrees/feature/")
        val withoutSlash = session("without-slash", 2L, "/Volumes/Data/worktrees/feature")
        assertEquals(setOf(withSlash, withoutSlash), worktreeConversations(root, listOf(withSlash, withoutSlash)).toSet())
        assertTrue(worktreeConversations("", listOf(withSlash)).isEmpty())
        assertTrue(worktreeConversations("/", listOf(withSlash)).isEmpty())
    }

    @Test
    fun resultsAreNewestFirstThenBySessionId() {
        val root = "/repo"
        val older = session("b-old", 10L, root)
        val newer = session("a-new", 20L, root)
        val tieA = session("tie-a", 15L, root)
        val tieB = session("tie-b", 15L, root)
        assertEquals(listOf(newer, tieA, tieB, older), worktreeConversations(root, listOf(older, tieB, tieA, newer)))
    }
}
