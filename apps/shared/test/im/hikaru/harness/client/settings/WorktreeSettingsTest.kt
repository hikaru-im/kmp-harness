package im.hikaru.harness.client.settings

import kotlin.test.*

/** Pure helpers behind the Worktrees "worktree root" field. */
class WorktreeSettingsTest {
    @Test
    fun rootValueTrimsAndNormalisesBackslashes() {
        assertEquals("", worktreeRootValue("   "))
        assertEquals("/tmp/managed", worktreeRootValue("  /tmp/managed  "))
        assertEquals("C:/worktrees", worktreeRootValue("C:\\worktrees"))
    }

    @Test
    fun configuredRootIsNullOnlyWhenBlank() {
        assertEquals(null, WorktreeSettings("  ").configuredRoot)
        assertEquals("/tmp/r", WorktreeSettings(" /tmp/r ").configuredRoot)
    }

    @Test
    fun rootErrorAcceptsBlankAndAbsoluteRejectsEverythingElse() {
        assertNull(worktreeRootError(""))
        assertNull(worktreeRootError("   "))
        assertNull(worktreeRootError("/tmp/managed"))
        assertNull(worktreeRootError("C:\\worktrees"))
        assertNull(worktreeRootError("c:/worktrees"))
        assertNotNull(worktreeRootError("relative/dir"))
        assertNotNull(worktreeRootError("./here"))
        assertNotNull(worktreeRootError("/tmp/\u0000bad"))
    }

    @Test
    fun defaultPathSeedsUnderTheRootOnlyForARootAndRealBranch() {
        assertEquals("/tmp/r/feature-x", worktreeDefaultPath("/tmp/r", "feature/x"))
        assertEquals("/tmp/r/main", worktreeDefaultPath("/tmp/r/", "main"))
        // No root: the page keeps asking for an explicit path.
        assertNull(worktreeDefaultPath("", "feature"))
        assertNull(worktreeDefaultPath("   ", "feature"))
        // Placeholder or blank branch: nothing to seed from.
        assertNull(worktreeDefaultPath("/tmp/r", "codex/"))
        assertNull(worktreeDefaultPath("/tmp/r", "  "))
        // A non-absolute root is never used.
        assertNull(worktreeDefaultPath("relative", "feature"))
    }

    @Test
    fun branchSlugStripsUnsafeCharacters() {
        assertEquals("feature-x", branchSlug("feature/x"))
        assertEquals("codex-page-new", branchSlug("codex/page-new"))
        assertEquals("v1.2.3", branchSlug("v1.2.3"))
        assertEquals("a-b", branchSlug("a  b"))
        assertNull(branchSlug("codex/"))
        assertNull(branchSlug("   "))
        assertNull(branchSlug("///"))
    }

    @Test
    fun storedDefaultsMatchTheObservedSettingKeys() {
        val settings = WorktreeSettings()
        assertTrue(settings.autoCleanupEnabled)
        assertEquals(15, settings.keepCount)
        assertEquals(WorktreeUpstreamRefreshMode.Never, settings.upstreamRefreshMode)
        assertEquals("never", WorktreeUpstreamRefreshMode.Never.wire)
        assertEquals("best-effort", WorktreeUpstreamRefreshMode.BestEffort.wire)
    }

    @Test
    fun keepCountOnlyAcceptsIntegersAtLeastOne() {
        assertEquals(1, keepCountValue("1"))
        assertEquals(20, keepCountValue(" 20 "))
        assertEquals(15, keepCountValue("15"))
        assertNull(keepCountValue("0"))
        assertNull(keepCountValue("-3"))
        assertNull(keepCountValue("2.5"))
        assertNull(keepCountValue("abc"))
        assertNull(keepCountValue(""))
        assertNull(keepCountError("1"))
        assertNotNull(keepCountError("0"))
    }
}
