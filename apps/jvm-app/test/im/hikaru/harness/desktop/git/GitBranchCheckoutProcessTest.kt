package im.hikaru.harness.desktop.git

import im.hikaru.harness.client.git.*
import kotlinx.coroutines.*
import kotlin.test.*

/** Real Git is delegated through the process seam; gates never manufacture a successful receipt. */
class GitBranchCheckoutProcessTest {
    @Test
    fun exactArgvUsesExistingLocalBranchAndDisablesIgnoredOverwriteAndSubmoduleRecursion() = runBlocking {
        val fixture = BranchCheckoutFixture()
        try {
            val real = DesktopGitCommandRunner()
            val invocations = mutableListOf<Pair<String, List<String>>>()
            val backend = DesktopGitBackend(fixture.host, GitCommandRunner { cwd, args ->
                invocations += cwd to args.toList()
                real.run(cwd, args)
            })
            val repo = fixture.repository()
            val result = withTimeout(15_000) { backend.switchBranch(repo, fixture.request(repo)) }
            assertIs<GitBranchCheckoutResult.Switched>(result)
            assertEquals(listOf(repo.root to listOf("switch", "--no-guess", "--no-overwrite-ignore",
                "--no-recurse-submodules", "--", "feature")), invocations.filter { it.second.firstOrNull() == "switch" })
            assertFalse(invocations.any { (_, args) -> args.any { it in listOf("--force", "--discard-changes",
                "--ignore-other-worktrees", "stash", "reset", "commit", "fetch") } })
            assertEquals(fixture.featureOid, fixture.git(fixture.workspace, "rev-parse", "HEAD").trim())
        } finally { fixture.close() }
    }

    @Test
    fun actualSwitchFollowedByConfirmationFailureIsUnknownAndNeverRepeated() = runBlocking {
        val fixture = BranchCheckoutFixture()
        try {
            val real = DesktopGitCommandRunner()
            var switches = 0
            var accepted = false
            val backend = DesktopGitBackend(fixture.host, GitCommandRunner { cwd, args ->
                if (accepted) throw GitReadException(GitFailure(GitFailureKind.CommandFailed, "confirmation unavailable"))
                val result = real.run(cwd, args)
                if (args.firstOrNull() == "switch") { switches++; accepted = true }
                result
            })
            val repo = fixture.repository()
            val before = fixture.originalEvidence()
            val result = withTimeout(15_000) { backend.switchBranch(repo, fixture.request(repo)) }
            assertIs<GitBranchCheckoutResult.UnknownOutcome>(result)
            assertEquals(1, switches)
            assertEquals("refs/heads/feature", fixture.git(fixture.workspace, "symbolic-ref", "HEAD").trim())
            assertEquals(before, fixture.originalEvidence())
        } finally { fixture.close() }
    }

    @Test
    fun cancellationAfterRealCommandKeepsUnknownEvidenceAndPreservesActualHead() = runBlocking {
        val fixture = BranchCheckoutFixture()
        val entered = CompletableDeferred<Unit>()
        var operation: Job? = null
        try {
            val real = DesktopGitCommandRunner()
            var switches = 0
            val backend = DesktopGitBackend(fixture.host, GitCommandRunner { cwd, args ->
                val result = real.run(cwd, args)
                if (args.firstOrNull() == "switch") {
                    switches++
                    entered.complete(Unit)
                    awaitCancellation()
                }
                result
            })
            val repo = fixture.repository()
            val request = fixture.request(repo)
            var observed: GitBranchCheckoutResult? = null
            val acceptedOperation = launch { observed = backend.switchBranch(repo, request) }
            operation = acceptedOperation
            withTimeout(15_000) { entered.await() }
            withTimeout(5_000) { acceptedOperation.cancelAndJoin() }
            assertIs<GitBranchCheckoutResult.UnknownOutcome>(observed)
            assertEquals(1, switches)
            assertEquals(fixture.featureOid, fixture.git(fixture.workspace, "rev-parse", "HEAD").trim())
        } finally { operation?.cancelAndJoin(); fixture.close() }
    }

    @Test
    fun timeoutOrNonzeroAfterActualInvocationCannotBecomeSwitched() = runBlocking {
        for (timeout in listOf(true, false)) {
            val fixture = BranchCheckoutFixture()
            try {
                val real = DesktopGitCommandRunner()
                var switches = 0
                val backend = DesktopGitBackend(fixture.host, GitCommandRunner { cwd, args ->
                    val result = real.run(cwd, args)
                    if (args.firstOrNull() == "switch") {
                        switches++
                        if (timeout) throw GitReadException(GitFailure(GitFailureKind.Timeout, "owned invocation timed out"))
                        // Failed transport/exit observation after real execution is deliberately ambiguous.
                        GitCommandResult(1, "", "exit observation failed")
                    } else result
                })
                val repo = fixture.repository()
                val result = withTimeout(15_000) { backend.switchBranch(repo, fixture.request(repo)) }
                assertIs<GitBranchCheckoutResult.UnknownOutcome>(result)
                assertEquals(1, switches)
                assertEquals(fixture.featureOid, fixture.git(fixture.workspace, "rev-parse", "HEAD").trim())
            } finally { fixture.close() }
        }
    }
}
