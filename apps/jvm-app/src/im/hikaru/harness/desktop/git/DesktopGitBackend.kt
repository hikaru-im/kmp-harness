package im.hikaru.harness.desktop.git

import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.harness.client.git.*
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import kotlinx.coroutines.*

/** An adapter for exactly one verified, in-process Host. It never proxies a remote scope. */
public class DesktopGitBackend(
    private val localHostId: HostId,
    private val runner: GitCommandRunner = DesktopGitCommandRunner(),
) : GitBackend, GitWorktreeCreator, GitBranchSwitcher, GitBranchCreator, GitCommitter, GitWorktreeContentProbe, GitWorktreeRemover {
    override fun canCommit(binding: GitBinding): Boolean =
        binding.access == GitAccess.LocalReadOnly && binding.hostId == localHostId

    override suspend fun readCommitSnapshot(repository: GitRepository): GitCommitSnapshot =
        commitProof(repository).snapshot

    /**
     * Reads a working-tree file for the diff rich preview. The diff path is untrusted input, so it
     * must stay a safe repository-relative path; anything else, a directory, a symlink, a
     * non-UTF-8 body or an over-limit file falls back to the diff text with a named reason.
     */
    override suspend fun readPreviewFile(repository: GitRepository, path: String): GitPreviewFile =
        withContext(Dispatchers.IO) {
            local(repository.binding)
            if (!gitPreviewPathIsSafe(path))
                return@withContext GitPreviewFile.Unavailable("预览路径不在仓库内，未显示富文本预览。")
            val file = Path.of(repository.root).resolve(path)
            if (!Files.isRegularFile(file, NOFOLLOW_LINKS))
                return@withContext GitPreviewFile.Unavailable("未找到可预览的文件，先显示差异文本。")
            val bytes = ByteArrayOutputStream()
            try {
                Files.newInputStream(file).use { input ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (bytes.size() + count > GitPreviewTextLimit)
                            return@withContext GitPreviewFile.Unavailable("文件过大，未显示富文本预览。")
                        bytes.write(buffer, 0, count)
                    }
                }
            } catch (_: java.nio.file.NoSuchFileException) {
                return@withContext GitPreviewFile.Unavailable("未找到可预览的文件，先显示差异文本。")
            } catch (_: Exception) {
                return@withContext GitPreviewFile.Unavailable("读取预览失败，请重试。")
            }
            try {
                GitPreviewFile.Ready(bytes.toByteArray().decodeToString(throwOnInvalidSequence = true))
            } catch (_: Exception) {
                GitPreviewFile.Unavailable("文件不是有效 UTF-8 文本，未显示富文本预览。")
            }
        }

    override suspend fun commit(repository: GitRepository, request: GitCommitRequest): GitCommitResult {
        var dispatched = false
        try {
            local(repository.binding)
            val accepted = request.snapshot
            if (accepted.repository != repository || request.policy != GitCommitPolicy.HooksDisabledSigningDisabled ||
                normalizeGitCommitMessage(request.message) != request.message || accepted.blockedReason != null ||
                (if (request.includeUnstaged) accepted.status.changes.isEmpty() else accepted.stagedCount <= 0) ||
                accepted.status.branch?.startsWith("refs/heads/") != true ||
                !validObjectId(accepted.status.headOid) || !accepted.indexDigest.matches(Regex("[0-9a-f]{64}")))
                fail(GitFailureKind.InvalidRequest, "提交请求、消息或仓库绑定无效。")
            val proof = commitProof(repository)
            proof.snapshot.blockedReason?.let { fail(GitFailureKind.InvalidRequest, it) }
            if (!sameCommitSelection(accepted, proof.snapshot))
                fail(GitFailureKind.StaleSnapshot, "HEAD 或暂存内容已变化，请重新读取后确认。")
            // Last coherent reread is a preflight, not an atomic lock against external Git.
            var last = commitProof(repository)
            if (last.snapshot.blockedReason != null || !sameCommitSelection(accepted, last.snapshot))
                fail(GitFailureKind.StaleSnapshot, "提交前仓库或暂存区已变化。")
            if (request.includeUnstaged) {
                // The original dialog's "include unstaged changes" option stages the whole worktree
                // before committing. Staging precedes dispatch, so a failure here stays a rejection
                // and the controller re-reads the real index instead of claiming an unknown commit.
                val staged = command(repository.root, listOf("add", "-A", "--", "."))
                if (staged.exitCode != 0)
                    fail(GitFailureKind.CommandFailed, "暂存未暂存变更失败（exit ${staged.exitCode}）。")
                last = commitProof(repository)
                last.snapshot.blockedReason?.let { fail(GitFailureKind.InvalidRequest, it) }
                if (last.snapshot.stagedCount <= 0)
                    fail(GitFailureKind.InvalidRequest, "暂存后没有可提交的变更。")
            }
            currentCoroutineContext().ensureActive()
            dispatched = true // Runner has no process-start receipt: invocation onward is conservatively unknown.
            val result = command(repository.root, listOf(
                "-c", "core.hooksPath=/dev/null", "-c", "commit.gpgSign=false",
                "-c", "i18n.commitEncoding=UTF-8", "-c", "maintenance.auto=false", "-c", "gc.auto=0",
                "commit", "--no-verify", "--no-gpg-sign", "--no-edit", "--cleanup=verbatim", "-m", request.message,
            ))
            // Do not copy hook/config output or the user's message into public errors.
            if (result.exitCode != 0) fail(GitFailureKind.CommandFailed, "Git 提交调用失败（exit ${result.exitCode}）。")
            verifyCreationRepository(repository)
            val actual = head(repository)
            val oldOid = requireNotNull(accepted.status.headOid)
            val newOid = actual.oid
            if (actual.branch != accepted.status.branch || newOid == null || newOid == oldOid)
                fail(GitFailureKind.StaleSnapshot, "无法确认提交后的 HEAD。")
            val objectText = success(repository.root, listOf("cat-file", "commit", newOid))
            val separator = objectText.indexOf("\n\n")
            if (separator < 0) fail(GitFailureKind.MalformedOutput, "提交对象不完整。")
            val headers = objectText.substring(0, separator).lines()
            val parents = headers.filter { it.startsWith("parent ") }.map { it.removePrefix("parent ") }
            val tree = headers.singleOrNull { it.startsWith("tree ") }?.removePrefix("tree ")
            val message = objectText.substring(separator + 2)
            if (parents != listOf(oldOid) || !validObjectId(tree) || message != request.message ||
                headers.any { it.startsWith("gpgsig ") || it.startsWith("gpgsig-sha256 ") })
                fail(GitFailureKind.StaleSnapshot, "提交对象与接受的 parent、消息或签名策略不一致。")
            val committedEntries = treeEntries(repository, requireNotNull(tree))
            if (committedEntries != last.entries)
                fail(GitFailureKind.StaleSnapshot, "真实提交 tree 与接受的暂存内容不一致。")
            val after = commitProof(repository)
            val refs = branches(repository)
            val trees = worktrees(repository)
            if (after.snapshot.status.branch != actual.branch || after.snapshot.status.headOid != newOid ||
                after.snapshot.blockedReason != null || after.entries != committedEntries || after.snapshot.stagedCount != 0 ||
                refs.singleOrNull { it.ref == actual.branch && it.current && !it.remote }?.oid != newOid ||
                trees.none { it.path == repository.root && it.branch == actual.branch && it.headOid == newOid &&
                    !it.bare && !it.detached && !it.prunable })
                fail(GitFailureKind.StaleSnapshot, "无法确认提交后的暂存区、分支和工作树。")
            checkHead(repository, actual)
            if (indexEntries(repository) != committedEntries || operationReason(repository) != null)
                fail(GitFailureKind.StaleSnapshot, "确认期间暂存区或操作状态已变化。")
            return GitCommitResult.Committed(GitCommitReceipt(repository, request, newOid, oldOid, requireNotNull(tree),
                digestEntries(committedEntries), message, after.snapshot, refs.toList(), trees.toList(),
                stagedByRequest = request.includeUnstaged, stagedIndexDigest = last.snapshot.indexDigest))
        } catch (e: Exception) {
            val failure = if (e is CancellationException) GitFailure(GitFailureKind.CommandFailed,
                if (dispatched) "提交调用已取消，结果未知，请刷新原工作树。" else "提交在调用 Git 前已取消。")
            else (e as? GitReadException)?.failure ?: GitFailure(GitFailureKind.CommandFailed, "无法确认本机提交结果。")
            return if (dispatched) GitCommitResult.UnknownOutcome(failure.copy(
                message = "提交调用后结果未知，请重新读取原工作树暂存区与仓库状态；不要直接重试。"))
            else GitCommitResult.Rejected(failure)
        }
    }

    private data class CommitEntry(val path: String, val mode: String, val oid: String, val stage: Int = 0)
    private data class CommitProof(val snapshot: GitCommitSnapshot, val entries: List<CommitEntry>)
    private fun validObjectId(value: String?): Boolean = value?.matches(Regex("(?:[0-9a-f]{40}|[0-9a-f]{64})")) == true
    private fun sameCommitSelection(a: GitCommitSnapshot, b: GitCommitSnapshot): Boolean =
        a.repository == b.repository && a.status.branch == b.status.branch && a.status.headOid == b.status.headOid &&
            a.indexDigest == b.indexDigest && a.stagedCount == b.stagedCount

    private suspend fun commitProof(repository: GitRepository): CommitProof {
        verifyCreationRepository(repository)
        val before = head(repository)
        val operationBefore = operationReason(repository)
        val entries = indexEntries(repository)
        val current = status(repository)
        var blocked = operationBefore ?: when {
            before.oid == null -> "尚无提交；C1 不支持 unborn 分支。"
            before.branch == null -> "Detached HEAD 不支持本机提交。"
            entries.any { it.stage != 0 } || current.changes.any { it.conflicted } -> "暂存区存在冲突，请使用外部 Git 处理。"
            else -> null
        }
        var changed = 0
        if (before.oid != null && entries.all { it.stage == 0 }) {
            val base = treeEntries(repository, before.oid)
            val reconstructed = base.associateBy { it.path }.toMutableMap()
            val delta = nulRecords(success(repository.root, listOf("diff", "--cached", "--raw", "-z",
                "--no-renames", "--no-abbrev", "--no-ext-diff", "--no-textconv", before.oid, "--")))
            if (delta.size % 2 != 0) fail(GitFailureKind.MalformedOutput, "暂存 delta 不完整。")
            for (pair in delta.chunked(2)) {
                val fields = pair[0].removePrefix(":").split(' ')
                if (!pair[0].startsWith(':') || fields.size != 5 || fields[4] !in listOf("A", "M", "D", "T") ||
                    !validObjectId(fields[2]) || !validObjectId(fields[3]))
                    fail(GitFailureKind.MalformedOutput, "暂存 delta 格式不受支持。")
                val path = pair[1]
                val previous = reconstructed[path]
                if (fields[0] == "000000") {
                    if (previous != null || fields[2].any { it != '0' }) fail(GitFailureKind.StaleSnapshot, "暂存 delta 与 HEAD tree 不一致。")
                } else if (previous?.mode != fields[0] || previous?.oid != fields[2])
                    fail(GitFailureKind.StaleSnapshot, "暂存 delta 与 HEAD tree 不一致。")
                if (fields[0] == "160000" || fields[1] == "160000") blocked = "C1 不支持暂存的子模块变更。"
                if (fields[1] == "000000") {
                    if (fields[3].any { it != '0' }) fail(GitFailureKind.MalformedOutput, "删除 delta 无效。")
                    reconstructed.remove(path)
                } else reconstructed[path] = CommitEntry(path, fields[1], fields[3])
                changed++
            }
            if (reconstructed.values.sortedBy { it.path } != entries)
                fail(GitFailureKind.InvalidRequest, "暂存区与实际 delta 不一致；intent-to-add 或特殊 index 暂不可用。")
        }
        val reasonAfter = operationReason(repository)
        if (operationBefore != reasonAfter || entries != indexEntries(repository) ||
            current.branch != before.branch || current.headOid != before.oid)
            fail(GitFailureKind.StaleSnapshot, "读取期间暂存区或操作状态已变化。")
        checkHead(repository, before)
        val refs = branches(repository)
        if (before.branch != null && before.oid != null &&
            refs.singleOrNull { it.ref == before.branch && it.current && !it.remote }?.oid != before.oid)
            fail(GitFailureKind.StaleSnapshot, "当前本地分支引用与 HEAD 不一致。")
        checkHead(repository, before)
        return CommitProof(GitCommitSnapshot(repository, current.copy(changes = current.changes.toList()),
            digestEntries(entries), changed, blocked), entries)
    }

    private suspend fun operationReason(repository: GitRepository): String? {
        val gitDir = Path.of(repository.gitDir)
        val markers = listOf("MERGE_HEAD", "REBASE_HEAD", "CHERRY_PICK_HEAD", "REVERT_HEAD",
            "rebase-merge", "rebase-apply", "sequencer", "BISECT_LOG", "index.lock")
        return if (markers.any { Files.exists(gitDir.resolve(it), NOFOLLOW_LINKS) })
            "Git 操作正在进行或 index 已锁定，请使用外部 Git 处理后刷新。"
        else null
    }

    private fun nulRecords(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        if (!text.endsWith('\u0000')) fail(GitFailureKind.MalformedOutput, "Git NUL 输出不完整。")
        return text.dropLast(1).split('\u0000')
    }
    private suspend fun indexEntries(repository: GitRepository): List<CommitEntry> =
        parseCommitEntries(success(repository.root, listOf("ls-files", "--stage", "-z")), index = true)
    private suspend fun treeEntries(repository: GitRepository, oid: String): List<CommitEntry> {
        if (!validObjectId(oid)) fail(GitFailureKind.InvalidRequest, "无效 tree/commit ID。")
        return parseCommitEntries(success(repository.root, listOf("ls-tree", "-r", "-z", "--full-tree", oid)), index = false)
    }
    private fun parseCommitEntries(text: String, index: Boolean): List<CommitEntry> {
        val entries = nulRecords(text).map { record ->
            val tab = record.indexOf('\t')
            if (tab < 0) fail(GitFailureKind.MalformedOutput, "Git entry 无效。")
            val fields = record.substring(0, tab).split(' ')
            val path = record.substring(tab + 1)
            val oid = fields.getOrNull(if (index) 1 else 2)
            val stage = if (index) fields.getOrNull(2)?.toIntOrNull() else 0
            if (fields.size != 3 || fields[0] !in listOf("100644", "100755", "120000", "160000") ||
                !validObjectId(oid) || stage == null || stage !in 0..3 || path.isEmpty() ||
                (!index && fields[1] != (if (fields[0] == "160000") "commit" else "blob")))
                fail(GitFailureKind.MalformedOutput, "特殊、sparse 或无效 index entry 暂不可用。")
            CommitEntry(path, fields[0], requireNotNull(oid), stage)
        }.sortedWith(compareBy<CommitEntry> { it.path }.thenBy { it.stage })
        if (entries.map { it.path to it.stage }.distinct().size != entries.size)
            fail(GitFailureKind.MalformedOutput, "Git entries 存在重复路径/stage。")
        return entries
    }
    private fun digestEntries(entries: List<CommitEntry>): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        for (entry in entries) digest.update("${entry.mode} ${entry.oid} ${entry.stage}\t${entry.path}\u0000".toByteArray(Charsets.UTF_8))
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    override fun canSwitchBranch(binding: GitBinding): Boolean =
        binding.access == GitAccess.LocalReadOnly && binding.hostId == localHostId

    override suspend fun switchBranch(
        repository: GitRepository, request: GitBranchCheckoutRequest,
    ): GitBranchCheckoutResult {
        var dispatched = false
        try {
            local(request.binding)
            if (request.binding != repository.binding || request.root != repository.root)
                fail(GitFailureKind.InvalidRequest, "切换请求与仓库 scope 不一致。")
            val ref = request.targetRef
            val branch = ref.removePrefix("refs/heads/")
            if (!ref.startsWith("refs/heads/") || branch.isBlank() || branch == "HEAD" ||
                branch.startsWith("-") || branch.startsWith("refs/") ||
                branch.any { it.isWhitespace() || it.code < 32 || it.code == 127 } ||
                listOf("..", "@{", "~", "^", ":", "?", "*", "[", "\\").any { it in branch } ||
                branch.split('/').any { it.isEmpty() || it.startsWith('.') || it.endsWith('.') || it.endsWith(".lock") })
                fail(GitFailureKind.InvalidRequest, "目标必须是实际读取的已有本地分支。")
            val oidPattern = Regex("(?:[0-9a-fA-F]{40}|[0-9a-fA-F]{64})")
            if (!request.expectedHeadOid.matches(oidPattern) || !request.targetOid.matches(oidPattern) ||
                request.expectedBranch?.startsWith("refs/heads/") == false)
                fail(GitFailureKind.InvalidRequest, "必须提供明确的来源 HEAD 与目标 commit ID。")
            if (request.expectedBranch == ref) fail(GitFailureKind.InvalidRequest, "目标已是当前分支。")
            verifyCreationRepository(repository)
            if (command(repository.root, listOf("check-ref-format", ref)).exitCode != 0)
                fail(GitFailureKind.InvalidRequest, "Git 拒绝了目标分支引用。")
            suspend fun verifySnapshot() {
                val actual = branches(repository).singleOrNull { it.ref == ref && !it.remote }
                    ?: fail(GitFailureKind.StaleSnapshot, "目标本地分支已不存在，请刷新。")
                val resolved = success(repository.root, listOf("rev-parse", "--verify", "--end-of-options", "$ref^{commit}")).line()
                if (actual.oid != request.targetOid || resolved != request.targetOid)
                    fail(GitFailureKind.StaleSnapshot, "目标分支已变化，请刷新。")
                val before = status(repository)
                if (before.headOid == null || before.branch != request.expectedBranch || before.headOid != request.expectedHeadOid)
                    fail(GitFailureKind.StaleSnapshot, "来源 HEAD 已变化或尚无提交，请刷新。")
                if (before.changes.isNotEmpty())
                    fail(GitFailureKind.InvalidRequest, "工作树、暂存区、未跟踪文件或冲突非干净；不会自动处理文件。")
            }
            verifySnapshot()
            verifyCreationRepository(repository)
            verifySnapshot()
            currentCoroutineContext().ensureActive()
            // Preconditions are not an atomic lock against external Git commands.
            // From invocation onward even failure/cancellation requires a real rescan.
            dispatched = true
            val result = command(repository.root, listOf("switch", "--no-guess", "--no-overwrite-ignore", "--no-recurse-submodules", "--", branch))
            if (result.exitCode != 0) commandFailure(result)
            currentCoroutineContext().ensureActive()
            verifyCreationRepository(repository)
            val after = status(repository)
            val refs = branches(repository)
            val trees = worktrees(repository)
            if (after.branch != ref || after.headOid != request.targetOid || after.changes.isNotEmpty() ||
                refs.singleOrNull { it.ref == ref && !it.remote && it.current }?.oid != request.targetOid ||
                trees.none { it.path == repository.root && it.branch == ref && it.headOid == request.targetOid &&
                    !it.bare && !it.detached && !it.prunable })
                fail(GitFailureKind.StaleSnapshot, "无法确认切换后的 HEAD、状态与本地分支。")
            checkHead(repository, Head(ref, request.targetOid))
            currentCoroutineContext().ensureActive()
            return GitBranchCheckoutResult.Switched(GitBranchCheckoutReceipt(repository, request, after, refs.toList(), trees.toList()))
        } catch (e: CancellationException) {
            if (!dispatched) throw e
            return GitBranchCheckoutResult.UnknownOutcome(GitFailure(GitFailureKind.CommandFailed,
                "切换调用后已取消，结果未知；请刷新仓库，勿直接重试。"))
        } catch (e: Exception) {
            val failure = (e as? GitReadException)?.failure ?:
                GitFailure(GitFailureKind.CommandFailed, "无法确认本地分支切换结果。")
            return if (dispatched) GitBranchCheckoutResult.UnknownOutcome(
                failure.copy(message = failure.message + " 结果未知，请刷新仓库；不要直接重试。"))
            else GitBranchCheckoutResult.Rejected(failure)
        }
    }

    override fun canCreateBranch(binding: GitBinding): Boolean =
        binding.access == GitAccess.LocalReadOnly && binding.hostId == localHostId

    override suspend fun createBranch(
        repository: GitRepository, request: GitCreateBranchRequest,
    ): GitCreateBranchResult {
        var dispatched = false
        try {
            local(request.binding)
            if (request.binding != repository.binding || request.root != repository.root)
                fail(GitFailureKind.InvalidRequest, "新建分支请求与仓库 scope 不一致。")
            val branch = request.branch
            if (!validLocalBranchName(branch)) fail(GitFailureKind.InvalidRequest, "新分支名称不是有效的本地引用。")
            val ref = "refs/heads/$branch"
            val oidPattern = Regex("(?:[0-9a-fA-F]{40}|[0-9a-fA-F]{64})")
            if (!request.expectedHeadOid.matches(oidPattern) || !request.startPointOid.matches(oidPattern) ||
                request.expectedBranch?.startsWith("refs/heads/") == false)
                fail(GitFailureKind.InvalidRequest, "必须提供明确的来源 HEAD 与起点 commit ID。")
            if (request.startPointRef != "HEAD" &&
                (!request.startPointRef.startsWith("refs/heads/") ||
                    !validLocalBranchName(request.startPointRef.removePrefix("refs/heads/"))))
                fail(GitFailureKind.InvalidRequest, "起点必须是 HEAD 或已有本地分支。")
            if (request.startPointRef == ref) fail(GitFailureKind.InvalidRequest, "起点不能与新分支同名。")
            verifyCreationRepository(repository)
            if (command(repository.root, listOf("check-ref-format", ref)).exitCode != 0)
                fail(GitFailureKind.InvalidRequest, "Git 拒绝了新分支引用。")
            suspend fun verifySnapshot() {
                val refs = branches(repository)
                if (refs.any { it.ref == ref }) fail(GitFailureKind.InvalidRequest, "同名本地分支已存在，未创建。")
                if (request.startPointRef != "HEAD") {
                    val base = refs.singleOrNull { it.ref == request.startPointRef && !it.remote }
                        ?: fail(GitFailureKind.StaleSnapshot, "起点本地分支已不存在，请刷新。")
                    if (base.oid != request.startPointOid) fail(GitFailureKind.StaleSnapshot, "起点分支已变化，请刷新。")
                }
                val before = status(repository)
                if (before.headOid == null || before.branch != request.expectedBranch || before.headOid != request.expectedHeadOid)
                    fail(GitFailureKind.StaleSnapshot, "来源 HEAD 已变化或尚无提交，请刷新。")
                if (before.changes.isNotEmpty())
                    fail(GitFailureKind.InvalidRequest, "工作树、暂存区、未跟踪文件或冲突非干净；不会自动处理文件。")
                if (request.startPointRef == "HEAD" && before.headOid != request.startPointOid)
                    fail(GitFailureKind.StaleSnapshot, "HEAD 起点已变化，请刷新。")
            }
            verifySnapshot()
            verifyCreationRepository(repository)
            verifySnapshot()
            currentCoroutineContext().ensureActive()
            // Preconditions are not an atomic lock against external Git commands.
            // From invocation onward even failure/cancellation requires a real rescan.
            dispatched = true
            val result = command(repository.root, listOf("switch", "--no-guess", "--no-overwrite-ignore",
                "--no-recurse-submodules", "--create", branch, request.startPointRef))
            if (result.exitCode != 0) commandFailure(result)
            currentCoroutineContext().ensureActive()
            verifyCreationRepository(repository)
            val after = status(repository)
            val refs = branches(repository)
            val trees = worktrees(repository)
            if (after.branch != ref || after.headOid != request.startPointOid || after.changes.isNotEmpty() ||
                refs.singleOrNull { it.ref == ref && !it.remote && it.current }?.oid != request.startPointOid ||
                trees.none { it.path == repository.root && it.branch == ref && it.headOid == request.startPointOid &&
                    !it.bare && !it.detached && !it.prunable })
                fail(GitFailureKind.StaleSnapshot, "无法确认新建分支后的 HEAD、状态与本地分支。")
            checkHead(repository, Head(ref, request.startPointOid))
            currentCoroutineContext().ensureActive()
            return GitCreateBranchResult.Created(GitCreateBranchReceipt(repository, request, after, refs.toList(), trees.toList()))
        } catch (e: CancellationException) {
            if (!dispatched) throw e
            return GitCreateBranchResult.UnknownOutcome(GitFailure(GitFailureKind.CommandFailed,
                "新建分支调用后已取消，结果未知；请刷新仓库，勿直接重试。"))
        } catch (e: Exception) {
            val failure = (e as? GitReadException)?.failure ?:
                GitFailure(GitFailureKind.CommandFailed, "无法确认新建分支结果。")
            return if (dispatched) GitCreateBranchResult.UnknownOutcome(
                failure.copy(message = failure.message + " 结果未知，请刷新仓库；不要直接重试。"))
            else GitCreateBranchResult.Rejected(failure)
        }
    }

    override fun canCreateWorktree(binding: GitBinding): Boolean =
        binding.access == GitAccess.LocalReadOnly && binding.hostId == localHostId

    override suspend fun createWorktree(
        repository: GitRepository, request: GitCreateWorktreeRequest,
    ): GitCreateWorktreeResult {
        var dispatched = false
        try {
            local(request.binding)
            if (request.binding != repository.binding)
                fail(GitFailureKind.InvalidRequest, "创建请求与仓库 scope 不一致。")
            val branch = request.branch
            if (branch.isBlank() || branch.startsWith("-") || branch.startsWith("refs/") ||
                branch.any { it.isWhitespace() || it.code < 32 || it.code == 127 } ||
                listOf("..", "@{", "~", "^", ":", "?", "*", "[", "\\").any { it in branch } ||
                branch.split('/').any { it.isEmpty() || it.startsWith('.') || it.endsWith('.') || it.endsWith(".lock") })
                fail(GitFailureKind.InvalidRequest, "请输入有效的新分支名称。")
            val input = request.path
            if (input.isBlank() || input.startsWith("-") || input.any { it.code < 32 || it.code == 127 })
                fail(GitFailureKind.InvalidRequest, "请输入新的本地绝对路径。")
            val requestedPath = try { Path.of(input) } catch (_: Exception) {
                fail(GitFailureKind.InvalidRequest, "目标路径无效。")
            }
            if (!requestedPath.isAbsolute || requestedPath.normalize() != requestedPath ||
                requestedPath.any { it.toString().startsWith("-") } || requestedPath.fileName == null)
                fail(GitFailureKind.InvalidRequest, "目标必须是没有相对段或选项段的绝对路径。")
            if (Files.exists(requestedPath, NOFOLLOW_LINKS))
                fail(GitFailureKind.InvalidRequest, "目标已存在，请选择新的目录；不会覆盖现有目录。")
            val parent = try { requestedPath.parent.toRealPath() } catch (_: Exception) {
                fail(GitFailureKind.InvalidRequest, "目标父目录不存在或不可访问。")
            }
            if (!Files.isDirectory(parent)) fail(GitFailureKind.InvalidRequest, "目标父路径不是目录。")
            val destination = parent.resolve(requestedPath.fileName)
            verifyCreationRepository(repository)
            val protectedPaths = listOf(repository.root, repository.commonDir, repository.gitDir).map { Path.of(it).toRealPath() }
            if (protectedPaths.any { destination.startsWith(it) })
                fail(GitFailureKind.InvalidRequest, "目标不能位于当前仓库或 Git 元数据目录内。")
            val start = request.startRef
            if (start != "HEAD" && (start.startsWith("-") || start.any { it.code < 32 || it.code == 127 } ||
                    branches(repository).none { it.ref == start }))
                fail(GitFailureKind.InvalidRequest, "起点必须是 HEAD 或仓库实际分支引用。")
            val validBranch = command(repository.root, listOf("check-ref-format", "refs/heads/$branch"))
            if (validBranch.exitCode != 0) fail(GitFailureKind.InvalidRequest, "Git 拒绝了新分支名称。")
            if (branches(repository).any { it.ref == "refs/heads/$branch" })
                fail(GitFailureKind.InvalidRequest, "新分支已存在，请使用其他名称。")
            val oid = success(repository.root, listOf("rev-parse", "--verify", "--end-of-options", "$start^{commit}")).line()
            if (!oid.matches(Regex("(?:[0-9a-fA-F]{40}|[0-9a-fA-F]{64})")))
                fail(GitFailureKind.MalformedOutput, "Git 返回了无效的起点 commit ID。")
            if (worktrees(repository).any { Path.of(it.path).normalize() == destination })
                fail(GitFailureKind.InvalidRequest, "目标已在 Git 工作树列表中。")
            // Revalidate after suspending reads. The user selected this repository and parent.
            verifyCreationRepository(repository)
            if (requestedPath.parent.toRealPath() != parent)
                fail(GitFailureKind.InvalidRequest, "目标父目录已变化，请重新选择。")
            currentCoroutineContext().ensureActive()
            // Atomic reservation rejects even a concurrently created empty directory or symlink.
            // Git may populate only the empty directory that this operation just created.
            try { Files.createDirectory(destination) } catch (_: Exception) {
                fail(GitFailureKind.InvalidRequest, "无法创建新目录，目标可能已存在或没有写入权限。")
            }
            dispatched = true
            val added = command(repository.root, listOf("worktree", "add", "-b", branch, "--", destination.toString(), oid))
            if (added.exitCode != 0) fail(GitFailureKind.CommandFailed,
                "Git 创建失败（exit ${added.exitCode}）：${added.stderr.take(2048)}")
            currentCoroutineContext().ensureActive()
            val scanned = worktrees(repository)
            val actual = scanned.singleOrNull { it.path == destination.toRealPath().toString() &&
                it.branch == "refs/heads/$branch" && it.headOid == oid && !it.bare && !it.detached && !it.prunable }
                ?: fail(GitFailureKind.StaleSnapshot, "无法从 Git 工作树列表确认创建结果。")
            currentCoroutineContext().ensureActive()
            return GitCreateWorktreeResult.Created(GitCreateWorktreeReceipt(repository, request, oid, actual, scanned.toList()))
        } catch (e: CancellationException) {
            // Preserve structured cancellation. The controller reports unknown; never invent a receipt.
            throw e
        } catch (e: Exception) {
            val failure = (e as? GitReadException)?.failure ?:
                GitFailure(GitFailureKind.CommandFailed, "无法确认本地工作树创建结果。")
            return GitCreateWorktreeResult.Failed(GitCreateWorktreeFailure(
                if (dispatched) failure.copy(message = failure.message + " 结果未知，请刷新工作树列表；不要直接重试。") else failure,
                needsRescan = dispatched,
            ))
        }
    }

    private suspend fun verifyCreationRepository(repository: GitRepository) {
        local(repository.binding)
        val actual = discover(repository.binding, repository.root)
        fun canonical(path: String): Path = Path.of(path).toRealPath()
        if (actual.root != repository.root || canonical(actual.commonDir) != canonical(repository.commonDir) ||
            canonical(actual.gitDir) != canonical(repository.gitDir))
            fail(GitFailureKind.InvalidRequest, "仓库目录或 Git scope 已变化，请重新读取仓库。")
    }
    override suspend fun discover(binding: GitBinding, cwd: String): GitRepository {
        local(binding)
        val path = try { Path.of(cwd).toRealPath() } catch (_: Exception) {
            fail(GitFailureKind.InvalidRequest, "仓库目录不存在或不可访问。")
        }
        if (!Files.isDirectory(path)) fail(GitFailureKind.InvalidRequest, "请选择真实目录。")
        val result = command(path.toString(), listOf("rev-parse", "--is-inside-work-tree"))
        if (result.exitCode != 0 || result.stdout.line() != "true")
            fail(GitFailureKind.NotRepository, "目录不是可读取的 Git 工作树。")
        fun args(flag: String): List<String> = listOf("rev-parse", "--path-format=absolute", flag)
        val root = success(path.toString(), args("--show-toplevel")).line()
        val common = success(root, args("--git-common-dir")).line()
        val gitDir = success(root, args("--git-dir")).line()
        return GitRepository(binding, root, common, gitDir)
    }

    override suspend fun status(repository: GitRepository): GitStatusSnapshot {
        local(repository.binding)
        val before = head(repository)
        val text = success(repository.root, listOf("status", "--porcelain=v1", "-z", "--untracked-files=all", "--renames"))
        val changes = parseGitStatus(text)
        checkHead(repository, before)
        return GitStatusSnapshot(changes, before.branch, before.oid)
    }

    override suspend fun branches(repository: GitRepository): List<GitBranch> {
        local(repository.binding)
        return parseGitBranches(success(repository.root, listOf("for-each-ref",
            "--format=%(refname)%00%(objectname)%00%(HEAD)%00", "refs/heads", "refs/remotes")))
    }

    override suspend fun worktrees(repository: GitRepository): List<GitWorktree> {
        local(repository.binding)
        return parseGitWorktrees(success(repository.root, listOf("worktree", "list", "--porcelain", "-z")))
    }

    override fun canProbeWorktreeContent(binding: GitBinding): Boolean =
        binding.access == GitAccess.LocalReadOnly && binding.hostId == localHostId

    /**
     * Reads everything a destructive removal would destroy. Every failure and every truncation is
     * reported as such: a clean status alone is never treated as an absence proof.
     */
    override suspend fun probeWorktreeContent(repository: GitRepository, root: String): GitWorktreeContent {
        local(repository.binding)
        val target = try { Path.of(root).toRealPath() } catch (_: Exception) {
            fail(GitFailureKind.NotRepository, "工作树目录不存在或不可访问。")
        }
        if (!Files.isDirectory(target)) fail(GitFailureKind.NotRepository, "工作树目标不是目录。")
        // NUL separation keeps spaces, newlines and Unicode paths exact. Renames are disabled so a
        // record is always one path, never a two-path pair that could be miscounted.
        val changes = command(root, listOf("status", "--porcelain=v1", "-z", "--no-renames", "--untracked-files=all"))
        if (changes.exitCode != 0) commandFailure(changes)
        val records = changes.stdout.split('\u0000').filter { it.isNotBlank() }
        val untracked = records.count { it.startsWith("??") }
        // Git rejects `--ignored=matching` combined with `--untracked-files=no` (exit 128), so keep
        // the default untracked mode and count only the `!!` records.
        val ignoredResult = command(root, listOf("status", "--porcelain=v1", "-z", "--ignored=matching"))
        if (ignoredResult.exitCode != 0) commandFailure(ignoredResult)
        val ignored = ignoredResult.stdout.split('\u0000').count { it.startsWith("!!") }
        val submoduleResult = command(root, listOf("submodule", "status"))
        if (submoduleResult.exitCode != 0)
            fail(GitFailureKind.CommandFailed, "无法核验子模块；不会把失败当作没有子模块。")
        val submodules = submoduleResult.stdout.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }
            .map { it.trimStart('+', '-', 'U', ' ') }.toList()
        val scan = scanNestedRepositories(target)
        return GitWorktreeContent(root = root, trackedChanges = records.size - untracked, untrackedEntries = untracked,
            ignoredEntries = ignored, submodules = submodules, nestedRepositories = scan.repositories,
            scannedEntries = scan.entries, complete = scan.complete)
    }

    private data class NestedScan(val repositories: List<String>, val entries: Int, val complete: Boolean)

    /** Bounded breadth-first scan. Exceeding the budget reports incomplete instead of "none". */
    private fun scanNestedRepositories(root: Path): NestedScan {
        val budget = 4_000
        val maxDepth = 4
        val found = mutableListOf<String>()
        val queue = ArrayDeque<Pair<Path, Int>>()
        queue += root to 0
        var seen = 0
        while (queue.isNotEmpty()) {
            val (dir, depth) = queue.removeFirst()
            val children = try { Files.newDirectoryStream(dir).use { it.toList() } }
            catch (_: Exception) { return NestedScan(found, seen, false) }
            for (child in children) {
                seen++
                if (seen > budget) return NestedScan(found, seen, false)
                val name = child.fileName?.toString() ?: continue
                if (name == ".git") continue
                if (!Files.isDirectory(child, NOFOLLOW_LINKS)) continue
                if (Files.exists(child.resolve(".git"), NOFOLLOW_LINKS)) {
                    found += child.toString()
                    continue
                }
                if (depth < maxDepth) queue += child to (depth + 1)
            }
        }
        return NestedScan(found.distinct(), seen, true)
    }

    override fun canRemoveWorktree(binding: GitBinding): Boolean =
        binding.access == GitAccess.LocalReadOnly && binding.hostId == localHostId

    /**
     * Removes exactly one non-primary, unlocked worktree. There is no force and no prune: Git itself
     * must agree that nothing tracked would be lost, and the path must leave the worktree list.
     */
    override suspend fun removeWorktree(
        repository: GitRepository, request: GitRemoveWorktreeRequest,
    ): GitRemoveWorktreeResult {
        var dispatched = false
        try {
            local(request.binding)
            if (request.binding != repository.binding)
                fail(GitFailureKind.InvalidRequest, "删除请求与仓库 scope 不一致。")
            if (!validObjectId(request.expectedHeadOid))
                fail(GitFailureKind.InvalidRequest, "删除请求缺少有效的 HEAD 指纹。")
            val requested = try { Path.of(request.root) } catch (_: Exception) {
                fail(GitFailureKind.InvalidRequest, "目标路径无效。")
            }
            if (!requested.isAbsolute || requested.normalize() != requested)
                fail(GitFailureKind.InvalidRequest, "目标必须是没有相对段的绝对路径。")
            verifyCreationRepository(repository)
            val trees = worktrees(repository)
            val tree = trees.singleOrNull { canonicalPath(it.path) == canonicalPath(request.root) }
                ?: fail(GitFailureKind.InvalidRequest, "目标工作树不在当前工作树列表中。")
            if (canonicalPath(tree.path) == canonicalPath(repository.root))
                fail(GitFailureKind.InvalidRequest, "不能删除主工作树。")
            if (tree.bare) fail(GitFailureKind.InvalidRequest, "目标工作树为 bare，不能删除。")
            if (tree.prunable) fail(GitFailureKind.InvalidRequest, "目标工作树已被 Git 标记为 prunable。")
            if (tree.locked) fail(GitFailureKind.InvalidRequest, "目标工作树已锁定，请先在外部解锁。")
            if (tree.branch == null || request.expectedBranch == null)
                fail(GitFailureKind.InvalidRequest, "目标工作树处于 detached 状态或缺少分支指纹，不能删除。")
            if (tree.headOid != request.expectedHeadOid || tree.branch != request.expectedBranch)
                fail(GitFailureKind.StaleSnapshot, "工作树 HEAD 或分支已变化，请刷新后重试。")
            val target = discover(request.binding, tree.path)
            if (target.root != canonicalPath(tree.path) || target.binding != repository.binding)
                fail(GitFailureKind.StaleSnapshot, "目标工作树已变化，请刷新后重试。")
            verifyCreationRepository(repository)
            currentCoroutineContext().ensureActive()
            dispatched = true
            val result = command(repository.root, listOf("worktree", "remove", "--", tree.path))
            if (result.exitCode != 0) return GitRemoveWorktreeResult.Rejected(GitFailure(
                GitFailureKind.CommandFailed,
                "Git 拒绝删除工作树（exit ${result.exitCode}）：${result.stderr.take(2048)}"))
            val after = worktrees(repository)
            // The target is already gone from disk, so its real path can no longer be resolved.
            val removedPath = canonicalIfResolvable(tree.path)
            if (after.any { canonicalIfResolvable(it.path) == removedPath })
                return GitRemoveWorktreeResult.UnknownOutcome(GitFailure(GitFailureKind.CommandFailed,
                    "Git 报告成功但目标仍在工作树列表中；结果未知，请刷新。"))
            return GitRemoveWorktreeResult.Removed(GitRemoveWorktreeReceipt(repository, request, after))
        } catch (cancelled: CancellationException) {
            if (!dispatched) throw cancelled
            return GitRemoveWorktreeResult.UnknownOutcome(GitFailure(GitFailureKind.CommandFailed,
                "删除调用后已取消，结果未知；请刷新仓库，勿直接重试。"))
        } catch (e: Exception) {
            val failure = (e as? GitReadException)?.failure
                ?: GitFailure(GitFailureKind.CommandFailed, "无法确认本地工作树删除结果。")
            return if (dispatched) GitRemoveWorktreeResult.UnknownOutcome(
                failure.copy(message = failure.message + " 结果未知，请刷新仓库；不要直接重试。"))
            else GitRemoveWorktreeResult.Rejected(failure)
        }
    }

    private fun canonicalPath(path: String): String = Path.of(path).toRealPath().toString()

    /** A removed worktree has no real path any more; fall back to a normalized absolute form. */
    private fun canonicalIfResolvable(path: String): String =
        runCatching { Path.of(path).toRealPath().toString() }
            .getOrElse { Path.of(path).toAbsolutePath().normalize().toString() }

    override suspend fun diff(repository: GitRepository, request: GitDiffRequest): GitDiffSnapshot {
        local(repository.binding)
        val baseRef = request.baseRef
        if (request.source == GitDiffSource.BaseBranch &&
            (baseRef.isNullOrBlank() || baseRef.startsWith("-") || baseRef.contains('\u0000')))
            fail(GitFailureKind.InvalidRequest, "请选择实际存在的基准分支。")
        val before = head(repository)
        val args = mutableListOf("diff", "--no-ext-diff", "--no-textconv", "--no-color", "--submodule=short")
        when (request.source) {
            GitDiffSource.WorkingTree -> Unit
            GitDiffSource.Index -> args.add("--cached")
            GitDiffSource.BaseBranch -> {
                val ref = request.baseRef
                if (ref.isNullOrBlank() || ref.startsWith("-") || ref.contains('\u0000'))
                    fail(GitFailureKind.InvalidRequest, "请选择实际存在的基准分支。")
                // Resolve to an object id before constructing a diff argument; never pass an option-like ref.
                val resolved = success(repository.root, listOf("rev-parse", "--verify", "--end-of-options", "$ref^{commit}")).line()
                if (!resolved.matches(Regex("(?:[0-9a-fA-F]{40}|[0-9a-fA-F]{64})")))
                    fail(GitFailureKind.MalformedOutput, "Git 返回了无效的 commit ID。")
                args.add(resolved)
            }
        }
        // Codex's `hideWhitespace` reaches the host as a diff input (`review-diff` params), so the
        // patch is computed with whitespace ignored instead of being filtered after the fact.
        if (request.hideWhitespace) args.add("--ignore-all-space")
        // `codex.review.loadFullFiles.enable` (加载完整文件): show the whole file for a partial diff.
        // The original loads the prev/next objects client-side and re-diffs them; this host instead
        // widens git's context to the whole file, so the same patch (full file, changes inline) comes
        // back. The flag is a diff input like `--ignore-all-space`, so it lands before the separator.
        if (request.loadFullFiles) args.add("--unified=$FULL_FILE_CONTEXT_LINES")
        args.add("--")
        val patch = success(repository.root, args)
        checkHead(repository, before)
        return GitDiffSnapshot(request, patch, before.oid)
    }

    private data class Head(val branch: String?, val oid: String?)
    private suspend fun head(repository: GitRepository): Head {
        val ref = command(repository.root, listOf("symbolic-ref", "--quiet", "HEAD"))
        if (ref.exitCode !in listOf(0, 1)) commandFailure(ref)
        val oid = command(repository.root, listOf("rev-parse", "--verify", "--quiet", "HEAD"))
        if (oid.exitCode !in listOf(0, 1)) commandFailure(oid)
        val branch = ref.stdout.line().takeIf { ref.exitCode == 0 }
        val commit = oid.stdout.line().takeIf { oid.exitCode == 0 }
        if (branch != null && !branch.startsWith("refs/heads/") ||
            commit != null && !commit.matches(Regex("(?:[0-9a-fA-F]{40}|[0-9a-fA-F]{64})")))
            fail(GitFailureKind.MalformedOutput, "Git 返回了无效的 HEAD。")
        return Head(branch, commit)
    }

    private suspend fun checkHead(repository: GitRepository, before: Head) {
        if (head(repository) != before) fail(GitFailureKind.StaleSnapshot, "读取期间 HEAD 已变化，请刷新。")
    }

    private fun local(binding: GitBinding) {
        if (binding.access != GitAccess.LocalReadOnly || binding.hostId != localHostId)
            fail(GitFailureKind.Unavailable, "此 Host 没有可用的本机 Git 服务。")
    }

    private suspend fun command(cwd: String, args: List<String>): GitCommandResult = runner.run(cwd, args)
    private suspend fun success(cwd: String, args: List<String>): String {
        val result = command(cwd, args)
        if (result.exitCode != 0) commandFailure(result)
        return result.stdout
    }
    private fun commandFailure(result: GitCommandResult): Nothing =
        fail(GitFailureKind.CommandFailed, "Git 读取失败（exit ${result.exitCode}）：${result.stderr.take(2048)}")
}

/** Injectable argv process boundary for reads and the explicit worktree creation capability. */
public fun interface GitCommandRunner {
    /** Output must be losslessly decoded UTF-8; malformed bytes must throw, never become replacement characters. */
    public suspend fun run(cwd: String, arguments: List<String>): GitCommandResult
}
public data class GitCommandResult(val exitCode: Int, val stdout: String, val stderr: String)

/** Concurrent pipe drains, bounded output/time, cancellation destroys only this owned process. */
public class DesktopGitCommandRunner(
    private val timeoutMillis: Long = 15_000,
    private val maxOutputBytes: Int = 8 * 1024 * 1024,
    /** Optional process environment seam for isolated temporary-repository tests. Defaults preserve local Git configuration. */
    private val environmentOverrides: Map<String, String> = emptyMap(),
) : GitCommandRunner {
    init { require(timeoutMillis > 0 && maxOutputBytes > 0) }

    override suspend fun run(cwd: String, arguments: List<String>): GitCommandResult = withContext(Dispatchers.IO) {
        val process = try {
            ProcessBuilder(listOf("git", "--no-optional-locks", "-c", "core.fsmonitor=false") + arguments)
                .directory(Path.of(cwd).toFile()).apply {
                    environment().putAll(environmentOverrides)
                    environment()["GIT_OPTIONAL_LOCKS"] = "0"
                    environment()["GIT_PAGER"] = "cat"
                    environment()["GIT_TERMINAL_PROMPT"] = "0"
                    // Parent shell repository overrides must not redirect the selected workspace.
                    listOf("GIT_DIR", "GIT_WORK_TREE", "GIT_COMMON_DIR", "GIT_INDEX_FILE",
                        "GIT_OBJECT_DIRECTORY", "GIT_ALTERNATE_OBJECT_DIRECTORIES").forEach { environment().remove(it) }
                }.start()
        } catch (_: Exception) {
            fail(GitFailureKind.Unavailable, "无法启动 Git，请检查安装与目录权限。")
        }
        try {
            process.outputStream.close()
            withTimeout(timeoutMillis) {
                coroutineScope {
                    val stdout = async(Dispatchers.IO) { drain(process.inputStream, maxOutputBytes) }
                    val stderr = async(Dispatchers.IO) { drain(process.errorStream, 64 * 1024) }
                    try {
                        while (process.isAlive) { ensureActive(); delay(10) }
                        GitCommandResult(process.exitValue(), stdout.await(), stderr.await())
                    } finally {
                        // Unblock readers before coroutineScope waits for their cancellation.
                        if (process.isAlive) process.destroyForcibly()
                    }
                }
            }
        } catch (_: TimeoutCancellationException) {
            // Preserve cancellation from the caller's timeout; only our own deadline is a Git failure.
            currentCoroutineContext().ensureActive()
            fail(GitFailureKind.Timeout, "Git 读取超时，请重试。")
        } finally {
            if (process.isAlive) process.destroyForcibly()
            runCatching { process.inputStream.close() }
            runCatching { process.errorStream.close() }
        }
    }

    private fun drain(stream: InputStream, limit: Int): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            if (output.size() + count > limit) fail(GitFailureKind.OutputTooLarge, "Git 输出过大，未显示不完整结果。")
            output.write(buffer, 0, count)
        }
        return try { output.toByteArray().decodeToString(throwOnInvalidSequence = true) } catch (_: Exception) {
            fail(GitFailureKind.MalformedOutput, "Git 输出不是有效 UTF-8，无法安全显示。")
        }
    }
}

private fun String.line(): String = removeSuffix("\n")
private fun fail(kind: GitFailureKind, message: String): Nothing = throw GitReadException(GitFailure(kind, message))

/**
 * Context lines asked of git when `codex.review.loadFullFiles.enable` is on. Large enough that any
 * realistically reviewable file comes back whole; a file longer than this still degrades to a partial
 * hunk rather than failing. The patch-level output limit in [DesktopGitBackend.drain] still applies.
 */
private const val FULL_FILE_CONTEXT_LINES = 100_000
