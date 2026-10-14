package im.hikaru.harness.desktop

import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.test.*
import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.harness.client.git.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.assertTrue

/** Complete layout after state/scroll changes before resolving a mouse target's coordinates. */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.settleGitLayout() {
    mainClock.advanceTimeByFrame()
    waitForIdle()
}

/** Scroll the page to the bounded list first, then scroll inside that list. */
@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.scrollGitNodeIntoView(
    target: SemanticsNodeInteraction, listTag: String? = null,
): SemanticsNodeInteraction {
    if (listTag != null) {
        onNodeWithTag(listTag).performScrollTo()
        settleGitLayout()
    }
    target.performScrollTo()
    settleGitLayout()
    return target.assertIsDisplayed()
}

@OptIn(ExperimentalTestApi::class)
internal fun ComposeUiTest.clickGitNode(target: SemanticsNodeInteraction): String {
    settleGitLayout()
    val before = target.assertIsDisplayed().fetchSemanticsNode()
    val beforeEvidence = gitPointerEvidence(before)
    val beforeRoot = generateSequence(before) { it.parent }.last()
    val hoverPoint = before.boundsInRoot.center - beforeRoot.boundsInRoot.topLeft
    assertTrue(before.boundsInRoot.width > 0f && before.boundsInRoot.height > 0f,
        "Mouse target must have a visible hit area: ${gitPointerEvidence(before)}")
    gitPointerRoot(beforeRoot).performMouseInput { moveTo(hoverPoint) }
    // Flush hover separately: its hit path/layout must be current before batching press/release.
    settleGitLayout()
    val pressed = target.assertIsDisplayed().fetchSemanticsNode()
    val pressEvidence = gitPointerEvidence(pressed)
    val pressedRoot = generateSequence(pressed) { it.parent }.last()
    val point = pressed.boundsInRoot.center - pressedRoot.boundsInRoot.topLeft
    assertTrue(pressed.boundsInRoot.width > 0f && pressed.boundsInRoot.height > 0f,
        "Mouse target must remain visible after hover: ${gitPointerEvidence(pressed)}")
    var injected = ""
    gitPointerRoot(pressedRoot).performMouseInput {
        moveTo(point)
        advanceEventTime(16)
        click(point)
        injected = "rootLocal=$currentPosition"
    }
    settleGitLayout()
    return "before={$beforeEvidence}, hoverLocal=$hoverPoint; " +
        "press={$pressEvidence}, point=$point, $injected; " +
        "after={${runCatching { gitPointerEvidence(target.fetchSemanticsNode()) }.getOrElse { it.toString() }}}"
}

/** A popup has its own pointer root; identify that root through the target's ancestry. */
@OptIn(ExperimentalTestApi::class)
private fun ComposeUiTest.gitPointerRoot(root: SemanticsNode): SemanticsNodeInteraction =
    onNode(SemanticsMatcher("Git pointer root ${root.id}") { it.id == root.id }, useUnmergedTree = true)

private fun gitPointerEvidence(node: SemanticsNode): String {
    val root = generateSequence(node) { it.parent }.last()
    return "node=${node.id}, layout=${node.size}, bounds=${node.boundsInRoot}, window=${node.positionInWindow}, " +
        "root=${root.id}, rootBounds=${root.boundsInRoot}, rootWindow=${root.positionInWindow}"
}

/** All paths are display strings. No process/filesystem Git, reflection or controller state injection. */
internal class GitWorktreesPageFixture(select: Boolean = true) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    val backend = GitWorktreesPageAdapter()
    val managedReads = GitWorktreesManagedReads()
    val git = GitController(scope, backend)
    val managed = ManagedWorktreeController(scope, managedReads)
    init {
        git.bind(binding)
        if (select) git.selectRepository(root)
    }
    fun ready(): Boolean = git.state.value.repository is GitRead.Ready && git.state.value.status is GitRead.Ready &&
        git.state.value.branches is GitRead.Ready && git.state.value.worktrees is GitRead.Ready && git.state.value.diff is GitRead.Ready
    override fun close() { managed.close(); git.close(); scope.cancel() }

    companion object {
        val binding = GitBinding(null, HostId("git-page-controlled-local"), 1, GitAccess.LocalReadOnly)
        const val root = "/test-only/source repository"
        val longRoot = "/test-only/" + "long directory with Unicode 工作树/".repeat(9) + "actual-root"
        val longRef = "refs/heads/codex/" + "long-branch-segment/".repeat(12) + "feature"
        val oid = "a".repeat(40)
        val featureOid = "b".repeat(40)
        val largePatch = "diff --git a/long.kt b/long.kt\n--- a/long.kt\n+++ b/long.kt\n@@ -1,500 +1,500 @@\n" +
            (1..500).joinToString("\n") { "+line-$it " + "long horizontal diff content 工作树 ".repeat(18) }
        /**
         * A small paired edit whose only textual change is one inserted word, so the word-level diff
         * highlights one deterministic range (`Codex `) on the added line and nothing on the removed
         * line — used by the screenshot case that captures the "词级差异" emphasis on and off.
         */
        val wordLevelPatch = "diff --git a/src/greeting.kt b/src/greeting.kt\n" +
            "--- a/src/greeting.kt\n" +
            "+++ b/src/greeting.kt\n" +
            "@@ -1,4 +1,4 @@\n" +
            " fun greet(name: String) {\n" +
            "-    println(\"Hello \" + name + \" from the harness\")\n" +
            "+    println(\"Hello \" + name + \" from the Codex harness\")\n" +
            " }\n"
        /**
         * A hunk whose only import change sits beside a shared context and a real edit, so the
         * "隐藏导入内容" capture can show the same file both with the import lines and without them.
         */
        val importPatch = "diff --git a/src/app.kt b/src/app.kt\n" +
            "--- a/src/app.kt\n" +
            "+++ b/src/app.kt\n" +
            "@@ -1,4 +1,5 @@\n" +
            " import kotlin.math.max\n" +
            "-import old.Deprecated\n" +
            "+import new.Replacement\n" +
            " import shared.Thing\n" +
            " val total = 1\n" +
            "+val extra = 2\n"
        /**
         * A partial diff with its full-context counterpart, so the "加载完整文件" capture can show the
         * same change twice: only the hunks, then the whole file once the host widens the context.
         */
        val partialPatch = "diff --git a/src/app.kt b/src/app.kt\n" +
            "--- a/src/app.kt\n" +
            "+++ b/src/app.kt\n" +
            "@@ -1,2 +1,3 @@\n" +
            " fun main() {\n" +
            "+    println(\"hello\")\n" +
            " }\n"
        val fullFilePatch = "diff --git a/src/app.kt b/src/app.kt\n" +
            "--- a/src/app.kt\n" +
            "+++ b/src/app.kt\n" +
            "@@ -1,5 +1,6 @@\n" +
            " fun main() {\n" +
            "+    println(\"hello\")\n" +
            " }\n" +
            " \n" +
            " // untouched tail of the same file\n" +
            " // still unchanged\n"
        fun repository(binding: GitBinding = this.binding, cwd: String = root) =
            GitRepository(binding, cwd, root + "/.git", if (cwd == root) root + "/.git" else root + "/.git/worktrees/controlled")
    }
}

/** Controls the real controller at its existing adapter seams, including mutation completion gates. */
internal class GitWorktreesPageAdapter : GitBackend, GitWorktreeCreator, GitBranchSwitcher {
    val discoveries = mutableListOf<Pair<GitBinding, String>>()
    val statusReads = mutableListOf<GitRepository>()
    val diffReads = mutableListOf<Pair<GitRepository, GitDiffRequest>>()
    val creations = mutableListOf<Pair<GitRepository, GitCreateWorktreeRequest>>()
    val switches = mutableListOf<Pair<GitRepository, GitBranchCheckoutRequest>>()
    val previewReads = mutableListOf<Pair<GitRepository, String>>()
    var discoveryGate: CompletableDeferred<Unit>? = null
    var readGate: CompletableDeferred<Unit>? = null
    var diffGate: CompletableDeferred<Unit>? = null
    var creationGate: CompletableDeferred<Unit>? = null
    var switchGate: CompletableDeferred<Unit>? = null
    var previewGate: CompletableDeferred<Unit>? = null
    var discoveryFailure: GitFailure? = null
    var branchesFailure: GitFailure? = null
    var diffFailure: GitFailure? = null
    var statusFailure: GitFailure? = null
    var worktreesFailure: GitFailure? = null
    var creationFailure: GitCreateWorktreeFailure? = null
    var currentRef = "refs/heads/main"
    var currentOid = GitWorktreesPageFixture.oid
    var patch = ""
    /**
     * What a real host returns when `loadFullFiles` widens the diff to the whole file. Left null for
     * the default partial behaviour so every existing case reads [patch] unchanged.
     */
    var fullPatch: String? = null
    /** Working-tree text by diff path; a path absent from the map reads as Unavailable. */
    var previewFiles = mapOf<String, String>()
    var changes = emptyList<GitChange>()
    var emptyRefs = false
    var emptyWorktrees = false
    var extraRefs = emptyList<GitBranch>()
    var extraWorktrees = emptyList<GitWorktree>()
    var creationAvailable = true
    var checkoutAvailable = true

    override fun canCreateWorktree(binding: GitBinding) = creationAvailable && binding.access == GitAccess.LocalReadOnly
    override fun canSwitchBranch(binding: GitBinding) = checkoutAvailable && binding.access == GitAccess.LocalReadOnly
    override suspend fun discover(binding: GitBinding, cwd: String): GitRepository {
        discoveries += binding to cwd
        discoveryGate?.await()
        discoveryFailure?.let { throw GitReadException(it) }
        return GitWorktreesPageFixture.repository(binding, cwd)
    }
    override suspend fun status(repository: GitRepository): GitStatusSnapshot {
        statusReads += repository
        readGate?.await()
        statusFailure?.let { throw GitReadException(it) }
        return GitStatusSnapshot(changes, currentRef, currentOid)
    }
    override suspend fun branches(repository: GitRepository): List<GitBranch> {
        readGate?.await()
        branchesFailure?.let { throw GitReadException(it) }
        if (emptyRefs) return emptyList()
        return listOf(
            GitBranch("refs/heads/main", GitWorktreesPageFixture.oid, currentRef == "refs/heads/main", false),
            GitBranch(GitWorktreesPageFixture.longRef, GitWorktreesPageFixture.featureOid,
                currentRef == GitWorktreesPageFixture.longRef, false),
            GitBranch("refs/remotes/origin/remote-only", GitWorktreesPageFixture.featureOid, false, true),
        ) + extraRefs
    }
    override suspend fun worktrees(repository: GitRepository): List<GitWorktree> {
        readGate?.await()
        worktreesFailure?.let { throw GitReadException(it) }
        if (emptyWorktrees) return emptyList()
        return listOf(GitWorktree(repository.root, currentOid, currentRef)) + extraWorktrees
    }
    override suspend fun diff(repository: GitRepository, request: GitDiffRequest): GitDiffSnapshot {
        diffReads += repository to request
        readGate?.await()
        diffGate?.await()
        diffFailure?.let { throw GitReadException(it) }
        if (request.source == GitDiffSource.BaseBranch && request.baseRef.isNullOrBlank()) {
            throw GitReadException(GitFailure(GitFailureKind.InvalidRequest, "请选择实际存在的基准分支。"))
        }
        return GitDiffSnapshot(request, if (request.loadFullFiles) fullPatch ?: patch else patch, currentOid)
    }
    override suspend fun readPreviewFile(repository: GitRepository, path: String): GitPreviewFile {
        previewReads += repository to path
        previewGate?.await()
        val text = previewFiles[path] ?: return GitPreviewFile.Unavailable("受控夹具没有该路径的预览内容。")
        return GitPreviewFile.Ready(text)
    }
    override suspend fun createWorktree(repository: GitRepository, request: GitCreateWorktreeRequest): GitCreateWorktreeResult {
        creations += repository to request
        creationGate?.await()
        creationFailure?.let { return GitCreateWorktreeResult.Failed(it) }
        val actual = GitWorktree(request.path, GitWorktreesPageFixture.oid, "refs/heads/${request.branch}")
        currentRef = requireNotNull(actual.branch)
        currentOid = requireNotNull(actual.headOid)
        return GitCreateWorktreeResult.Created(GitCreateWorktreeReceipt(repository, request, currentOid,
            actual, listOf(GitWorktree(repository.root, GitWorktreesPageFixture.oid, "refs/heads/main"), actual)))
    }
    override suspend fun switchBranch(repository: GitRepository, request: GitBranchCheckoutRequest): GitBranchCheckoutResult {
        switches += repository to request
        switchGate?.await()
        currentRef = request.targetRef
        currentOid = request.targetOid
        return GitBranchCheckoutResult.Switched(GitBranchCheckoutReceipt(repository, request, status(repository),
            branches(repository), worktrees(repository)))
    }
}

internal class GitWorktreesManagedReads : ManagedWorktreeRepository {
    override val revision = MutableStateFlow(0L)
    val calls = mutableListOf<GitRepository>()
    var gate: CompletableDeferred<Unit>? = null
    var failure: ManagedWorktreeRead.Failed? = null
    var ownerships = listOf(ManagedOwnership.UnknownOwnership)
    override suspend fun read(repository: GitRepository): ManagedWorktreeRead {
        calls += repository
        gate?.await()
        failure?.let { return it }
        val entries = ownerships.mapIndexed { index, ownership ->
            val root = if (index == 0) repository.root else GitWorktreesPageFixture.longRoot + "/entry-$index"
            ManagedWorktreeEntry(root,
                if (ownership == ManagedOwnership.Missing) null else GitWorktree(root, GitWorktreesPageFixture.oid,
                    GitWorktreesPageFixture.longRef), ownership,
                registration = if (ownership == ManagedOwnership.UnknownOwnership) null else ManagedRegistrationPhase.Registered,
                registrationWarning = if (ownership == ManagedOwnership.FingerprintMismatch) "登记指纹与实际 Git 不匹配。" else null)
        }
        return ManagedWorktreeRead.Ready(ManagedWorktreeInventory(
            ManagedWorktreeOwner(repository.binding.identity, repository.binding.hostId, "/test-only/home"),
            repository, entries, entries.count { it.ownership == ManagedOwnership.Managed }))
    }
}
