package im.hikaru.harness.desktop.git

import im.hikaru.harness.client.git.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlin.test.*

/** Process-level checks for argv discipline and lossless Git output decoding. */
class DesktopGitCommitProcessTest {
    @Test
    fun commitUsesFixedNoHookNoSigningArgvAndNeverStages() = runBlocking {
        val f = CommitFixture()
        try {
            f.stage("staged bytes\n")
            Files.writeString(f.workspace.resolve("untracked.txt"), "keep\n")
            val seen = mutableListOf<List<String>>()
            val backend = DesktopGitBackend(f.host, GitCommandRunner { cwd, args ->
                if ("commit" in args && args.firstOrNull() != "cat-file") seen.add(args.toList())
                f.runner.run(cwd, args)
            })
            val repo = backend.discover(f.binding, f.workspace.toString())
            val request = GitCommitRequest(backend.readCommitSnapshot(repo), "process argv\n")
            assertIs<GitCommitResult.Committed>(backend.commit(repo, request))
            val argv = assertNotNull(seen.singleOrNull())
            assertEquals(listOf(
                "-c", "core.hooksPath=/dev/null", "-c", "commit.gpgSign=false",
                "-c", "i18n.commitEncoding=UTF-8", "-c", "maintenance.auto=false", "-c", "gc.auto=0",
                "commit", "--no-verify", "--no-gpg-sign", "--no-edit", "--cleanup=verbatim", "-m", "process argv\n",
            ), argv)
            assertFalse(argv.any { it == "-a" || it == "--amend" || it == "--include" || it == "--only" || it == "add" })
            assertEquals("keep\n", Files.readString(f.workspace.resolve("untracked.txt")))
        } finally { f.close() }
    }

    @Test
    fun malformedUtf8PathFailsClosedBeforeDigestAndCommitInvocation() = runBlocking {
        val f = CommitFixture()
        try {
            f.stage("accepted UTF-8 path\n")
            val acceptedRepo = f.repository()
            val accepted = f.request(acceptedRepo)
            val blob = f.git(f.workspace, "rev-parse", "HEAD:tracked.txt").trim()
            // Write an actual non-UTF-8 path into the real on-disk index. This is independent of
            // the temp filesystem's Unicode filename restrictions; no replacement String is made.
            rawIndexEntry(f.workspace, ("100644 " + blob + "\tbad").encodeToByteArray() +
                byteArrayOf(0xff.toByte()) + ".txt\u0000".encodeToByteArray())
            val runner = DesktopGitCommandRunner(environmentOverrides = mapOf(
                "GIT_CONFIG_GLOBAL" to "/dev/null", "GIT_CONFIG_NOSYSTEM" to "1",
                "GIT_TERMINAL_PROMPT" to "0", "GIT_OPTIONAL_LOCKS" to "0",
            ))
            val direct = assertFailsWith<GitReadException> {
                runner.run(f.workspace.toString(), listOf("ls-files", "--stage", "-z"))
            }
            assertEquals(GitFailureKind.MalformedOutput, direct.failure.kind)
            var invocations = 0
            val backend = DesktopGitBackend(f.host, GitCommandRunner { cwd, args ->
                if ("commit" in args && args.firstOrNull() != "cat-file") invocations++
                runner.run(cwd, args)
            })
            val repo = backend.discover(f.binding, f.workspace.toString())
            val index = Files.readAllBytes(Path.of(repo.gitDir).resolve("index"))
            val snapshot = assertFailsWith<GitReadException> { backend.readCommitSnapshot(repo) }
            assertEquals(GitFailureKind.MalformedOutput, snapshot.failure.kind)
            assertFalse(snapshot.failure.message.contains("�"), "replacement characters cannot be treated as a safe path")
            assertFalse(Files.exists(f.workspace.resolve("bad�.txt")))
            assertEquals(GitFailureKind.MalformedOutput,
                assertIs<GitCommitResult.Rejected>(backend.commit(repo, accepted)).failure.kind)
            assertEquals(0, invocations)
            assertContentEquals(index, Files.readAllBytes(Path.of(repo.gitDir).resolve("index")))
            assertEquals(f.baseOid, f.git(f.workspace, "rev-parse", "HEAD").trim())
        } finally { f.close() }
    }

    @Test
    fun malformedMessageAndPreflightCancellationDoNotInvokeCommit() = runBlocking {
        val f = CommitFixture()
        try {
            f.stage("staged\n")
            val repo = f.repository()
            val request = f.request(repo)
            val index = Files.readAllBytes(Path.of(repo.gitDir).resolve("index"))
            assertIs<GitCommitResult.Rejected>(f.backend.commit(repo, request.copy(message = "bad\u0000message")))
            assertIs<GitCommitResult.Rejected>(f.backend.commit(repo, request.copy(message = "   ")))
            val cancelled = DesktopGitBackend(f.host, GitCommandRunner { _, _ -> throw CancellationException("before invocation") })
            assertIs<GitCommitResult.Rejected>(cancelled.commit(repo, request))
            assertEquals(0, f.commitInvocations)
            assertContentEquals(index, Files.readAllBytes(Path.of(repo.gitDir).resolve("index")))
            assertEquals(f.baseOid, f.git(f.workspace, "rev-parse", "HEAD").trim())
        } finally { f.close() }
    }

    @Test
    fun realCommitBeforeCancellationOrTimeoutRemainsUnknownWithObjectAndFilesPreserved() = runBlocking {
        for (mode in listOf("cancel", "timeout")) {
            val f = CommitFixture()
            val entered = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            try {
                f.stage("accepted before " + mode + "\n")
                Files.writeString(f.workspace.resolve("tracked.txt"), "unstaged keep\n")
                Files.writeString(f.workspace.resolve("untracked.txt"), "untracked keep\n")
                val original = f.originalEvidence()
                val repo = f.repository()
                val request = f.request(repo)
                val backend = DesktopGitBackend(f.host, GitCommandRunner { cwd, args ->
                    val result = f.runner.run(cwd, args)
                    if ("commit" in args && args.firstOrNull() != "cat-file") {
                        entered.complete(Unit)
                        if (mode == "timeout") withTimeout(100) { release.await() }
                        else release.await()
                    }
                    result
                })
                val result = CompletableDeferred<GitCommitResult>()
                val job = launch(Dispatchers.Default) { result.complete(backend.commit(repo, request)) }
                try {
                    withTimeout(15_000) { entered.await() }
                    if (mode == "cancel") job.cancel(CancellationException("lost command acknowledgement"))
                    assertIs<GitCommitResult.UnknownOutcome>(withTimeout(5_000) { result.await() }, mode)
                    withTimeout(5_000) { job.join() }
                } finally { release.complete(Unit); job.cancelAndJoin() }
                val oid = f.git(f.workspace, "rev-parse", "HEAD").trim()
                assertNotEquals(f.baseOid, oid)
                assertEquals(f.baseOid, f.git(f.workspace, "rev-parse", "HEAD^").trim())
                assertEquals(request.message, f.git(f.workspace, "cat-file", "commit", oid).substringAfter("\n\n"))
                assertEquals("accepted before " + mode + "\n", f.git(f.workspace, "show", "HEAD:tracked.txt"))
                assertEquals("unstaged keep\n", Files.readString(f.workspace.resolve("tracked.txt")))
                assertEquals("untracked keep\n", Files.readString(f.workspace.resolve("untracked.txt")))
                assertEquals(original, f.originalEvidence())
                assertEquals(1, f.commitInvocations)
                assertEquals(0, f.backend.readCommitSnapshot(repo).stagedCount)
            } finally { release.complete(Unit); f.close() }
        }
    }

    @Test
    fun competingIndexAfterRealCommitCannotProduceVerifiedReceiptOrOverwriteCompetingBytes() = runBlocking {
        val f = CommitFixture()
        try {
            f.stage("accepted tree\n")
            val original = f.originalEvidence()
            val repo = f.repository()
            val request = f.request(repo)
            var competingIndex: ByteArray? = null
            val backend = DesktopGitBackend(f.host, GitCommandRunner { cwd, args ->
                val result = f.runner.run(cwd, args)
                if ("commit" in args && args.firstOrNull() != "cat-file") {
                    f.stage("competing staged bytes\n")
                    competingIndex = Files.readAllBytes(Path.of(repo.gitDir).resolve("index"))
                }
                result
            })
            assertIs<GitCommitResult.UnknownOutcome>(backend.commit(repo, request))
            assertEquals("accepted tree\n", f.git(f.workspace, "show", "HEAD:tracked.txt"))
            assertEquals("competing staged bytes\n", Files.readString(f.workspace.resolve("tracked.txt")))
            assertContentEquals(requireNotNull(competingIndex), Files.readAllBytes(Path.of(repo.gitDir).resolve("index")))
            assertEquals(original, f.originalEvidence())
            assertEquals(1, f.commitInvocations)
        } finally { f.close() }
    }

    @Test
    fun changedSourceHeadRejectsAcceptedIndexWithoutInvokingCommitAgain() = runBlocking {
        val f = CommitFixture()
        try {
            f.stage("accepted tree\n")
            val repo = f.repository()
            val request = f.request(repo)
            f.git(f.workspace, "commit", "-m", "external competing commit")
            val head = f.git(f.workspace, "rev-parse", "HEAD").trim()
            val index = Files.readAllBytes(Path.of(repo.gitDir).resolve("index"))
            assertEquals(GitFailureKind.StaleSnapshot, assertIs<GitCommitResult.Rejected>(f.backend.commit(repo, request)).failure.kind)
            assertEquals(head, f.git(f.workspace, "rev-parse", "HEAD").trim())
            assertContentEquals(index, Files.readAllBytes(Path.of(repo.gitDir).resolve("index")))
            assertEquals(0, f.commitInvocations, "only the fixture's explicit external command ran")
        } finally { f.close() }
    }

    private fun rawIndexEntry(cwd: Path, input: ByteArray) {
        val process = ProcessBuilder("git", "--no-optional-locks", "-c", "core.fsmonitor=false",
            "update-index", "-z", "--index-info").directory(cwd.toFile()).apply {
            listOf("GIT_DIR", "GIT_WORK_TREE", "GIT_COMMON_DIR", "GIT_INDEX_FILE", "GIT_OBJECT_DIRECTORY",
                "GIT_ALTERNATE_OBJECT_DIRECTORIES", "GIT_CONFIG_COUNT", "GIT_CONFIG_PARAMETERS").forEach { environment().remove(it) }
            environment()["GIT_CONFIG_GLOBAL"] = "/dev/null"
            environment()["GIT_CONFIG_NOSYSTEM"] = "1"
            environment()["GIT_OPTIONAL_LOCKS"] = "0"
            environment()["GIT_TERMINAL_PROMPT"] = "0"
        }.start()
        try {
            process.outputStream.use { it.write(input) }
            assertTrue(process.waitFor(10, TimeUnit.SECONDS), "bounded raw filename fixture command")
            assertTrue(process.inputStream.readAllBytes().isEmpty())
            val stderr = process.errorStream.readAllBytes().decodeToString(throwOnInvalidSequence = true)
            assertEquals(0, process.exitValue(), stderr)
        } finally { if (process.isAlive) process.destroyForcibly() }
    }
}
