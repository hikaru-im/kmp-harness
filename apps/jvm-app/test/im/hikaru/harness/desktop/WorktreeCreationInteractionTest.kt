package im.hikaru.harness.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.*
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import im.hikaru.contracts.common.ApiResult
import im.hikaru.contracts.harness.identity.HostDescription
import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.contracts.harness.protocol.ProtocolVersion
import im.hikaru.harness.api.gateway.ApiGateway
import im.hikaru.harness.api.gateway.host.HostDescribeEndpoint
import im.hikaru.harness.api.gateway.session.SessionListEndpoint
import im.hikaru.harness.client.app.HarnessApp
import im.hikaru.harness.client.app.HarnessDestination
import im.hikaru.harness.client.connection.ConnectionTransport
import im.hikaru.harness.desktop.connection.LocalConnection
import im.hikaru.harness.desktop.git.DesktopGitBackend
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import kotlin.test.*

/** Real settings route, Git process, repository and worktree. Session inventory alone is a fixture. */
@OptIn(ExperimentalTestApi::class)
class WorktreeCreationInteractionTest {
    @Test
    fun settingsCreateWorktreeReadsActualDirectoryAndPreservesSourceChanges() {
        val fixture = WorktreeUiFixture()
        try {
            val target = fixture.directory.resolve("新工作树 with spaces")
            val sourceIndex = Files.readAllBytes(fixture.root.resolve(".git/index"))
            val sourceStatus = fixture.git(fixture.root, "status", "--porcelain=v1", "-z")
            runSkikoComposeUiTest(size = Size(1440f, 1100f)) {
                setContent {
                    HarnessApp(fixture.connection, initialDestination = HarnessDestination.Settings,
                        connectionTransport = ConnectionTransport.Local, gitBackend = fixture.backend,
                        gitInitialCwd = fixture.root.toString())
                }
                onNodeWithTag("settings-row-worktrees").performClick()
                waitUntil(timeoutMillis = 10_000) {
                    onAllNodes(hasTestTag("git-repository-root") and hasText(fixture.root.toString()))
                        .fetchSemanticsNodes().isNotEmpty() &&
                        listOf("git-worktree-create-path", "git-worktree-create-branch", "git-worktree-create-start").all { tag ->
                            onAllNodes(hasTestTag(tag) and isEnabled()).fetchSemanticsNodes().isNotEmpty()
                        }
                }
                onNodeWithTag("git-worktree-create-path").performScrollTo().performTextReplacement(target.toString())
                onNodeWithTag("git-worktree-create-branch").performScrollTo().performTextReplacement("codex/ui-worktree")
                onNodeWithTag("git-worktree-create-submit").performScrollTo().assertIsEnabled().performClick()
                waitUntil(timeoutMillis = 15_000) {
                    onAllNodesWithTag("git-worktree-create-success").fetchSemanticsNodes().isNotEmpty()
                }
                waitUntil(timeoutMillis = 10_000) {
                    onAllNodes(hasTestTag("git-repository-root") and hasText(target.toString()))
                        .fetchSemanticsNodes().isNotEmpty()
                }
                onNodeWithTag("git-worktree-create-success").assertTextEquals("已创建：$target")
                assertEquals("refs/heads/codex/ui-worktree", fixture.git(target, "symbolic-ref", "HEAD").trim())
                assertEquals(fixture.originalHead, fixture.git(target, "rev-parse", "HEAD").trim())
                assertEquals("committed\n", Files.readString(target.resolve("tracked.txt")))
                assertFalse(Files.exists(target.resolve("untracked.txt")))
                assertEquals(sourceStatus, fixture.git(fixture.root, "status", "--porcelain=v1", "-z"))
                assertContentEquals(sourceIndex, Files.readAllBytes(fixture.root.resolve(".git/index")))
                assertEquals("working\n", Files.readString(fixture.root.resolve("tracked.txt")))
                assertEquals("keep me\n", Files.readString(fixture.root.resolve("untracked.txt")))
                assertEquals(fixture.originalHead, fixture.git(fixture.root, "rev-parse", "HEAD").trim())
            }
        } finally { fixture.close() }
    }

    @Test
    fun relayDisablesLocalWorktreeCreationControlsWithoutCreatingDirectories() {
        val fixture = WorktreeUiFixture()
        val target = fixture.directory.resolve("must-not-be-created")
        var transport by mutableStateOf(ConnectionTransport.Local)
        try {
            runSkikoComposeUiTest(size = Size(1440f, 1100f)) {
                setContent {
                    HarnessApp(fixture.connection, initialDestination = HarnessDestination.Settings,
                        connectionTransport = transport, gitBackend = fixture.backend, gitInitialCwd = fixture.root.toString())
                }
                onNodeWithTag("settings-row-worktrees").performClick()
                waitUntil(timeoutMillis = 10_000) {
                    onAllNodes(hasTestTag("git-repository-root") and hasText(fixture.root.toString()))
                        .fetchSemanticsNodes().isNotEmpty() &&
                        listOf("git-worktree-create-path", "git-worktree-create-branch", "git-worktree-create-start").all { tag ->
                            onAllNodes(hasTestTag(tag) and isEnabled()).fetchSemanticsNodes().isNotEmpty()
                        }
                }
                onNodeWithTag("git-worktree-create-path").performScrollTo().performTextReplacement(target.toString())
                onNodeWithTag("git-worktree-create-branch").performScrollTo().performTextReplacement("codex/must-not-create")
                onNodeWithTag("git-worktree-create-submit").performScrollTo().assertIsEnabled()
                runOnIdle { transport = ConnectionTransport.Relay }
                onNodeWithTag("git-unavailable").assertExists()
                onNodeWithTag("git-worktree-create-submit").assertDoesNotExist()
                assertFalse(Files.exists(target))
                assertEquals(listOf(fixture.root.toString()), fixture.git(fixture.root, "worktree", "list", "--porcelain")
                    .lineSequence().filter { it.startsWith("worktree ") }.map { it.removePrefix("worktree ") }.toList())
            }
        } finally { fixture.close() }
    }
}

private class WorktreeUiFixture {
    val directory: Path = Files.createTempDirectory("harness-worktree-ui-").toRealPath()
    val root: Path = Files.createDirectory(directory.resolve("source repo"))
    val host = HostId("worktree-ui-local")
    val gateway = ApiGateway()
    val connection = LocalConnection(gateway)
    val backend = DesktopGitBackend(host)
    val originalHead: String

    init {
        git(root, "init", "--initial-branch=main")
        Files.writeString(root.resolve("tracked.txt"), "committed\n")
        git(root, "add", "--", "tracked.txt")
        git(root, "-c", "user.name=Harness Test", "-c", "user.email=test@example.invalid",
            "-c", "commit.gpgsign=false", "-c", "core.hooksPath=/dev/null", "commit", "-m", "fixture")
        originalHead = git(root, "rev-parse", "HEAD").trim()
        Files.writeString(root.resolve("tracked.txt"), "staged\n")
        git(root, "add", "--", "tracked.txt")
        Files.writeString(root.resolve("tracked.txt"), "working\n")
        Files.writeString(root.resolve("untracked.txt"), "keep me\n")
        runBlocking {
            gateway.register(HostDescribeEndpoint) {
                ApiResult(ApiResult.SUCCESS_CODE, data = HostDescription(ProtocolVersion.Current, host, "Worktree UI fixture"))
            }
            gateway.register(SessionListEndpoint) { ApiResult(ApiResult.SUCCESS_CODE, data = emptyList()) }
        }
    }

    fun git(cwd: Path, vararg arguments: String): String {
        val process = ProcessBuilder(listOf("git", "-c", "core.hooksPath=/dev/null") + arguments)
            .directory(cwd.toFile()).redirectErrorStream(true).apply {
                listOf("GIT_DIR", "GIT_WORK_TREE", "GIT_COMMON_DIR", "GIT_INDEX_FILE",
                    "GIT_OBJECT_DIRECTORY", "GIT_ALTERNATE_OBJECT_DIRECTORIES").forEach { environment().remove(it) }
                environment()["GIT_CONFIG_NOSYSTEM"] = "1"
                environment()["GIT_CONFIG_GLOBAL"] = "/dev/null"
                environment()["GIT_TERMINAL_PROMPT"] = "0"
            }.start()
        process.outputStream.close()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        assertEquals(0, process.waitFor(), "git ${arguments.toList()}: $output")
        return output
    }

    fun close() { directory.toFile().deleteRecursively() }
}
