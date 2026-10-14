package im.hikaru.harness.desktop.git

import im.hikaru.harness.client.git.*
import kotlinx.coroutines.CancellationException

/** Preserves the original read/checkout capabilities; adds registration only to explicit creation. */
public class DesktopManagedGitBackend(
    private val delegate: GitBackend,
    private val repository: DesktopManagedWorktreeRepository,
) : GitBackend by delegate, GitWorktreeCreator, GitBranchSwitcher, GitCommitter {
    override fun canCommit(binding: GitBinding): Boolean = (delegate as? GitCommitter)?.canCommit(binding) == true

    override suspend fun readCommitSnapshot(repository: GitRepository): GitCommitSnapshot =
        (delegate as? GitCommitter)?.readCommitSnapshot(repository)
            ?: throw GitReadException(GitFailure(GitFailureKind.Unavailable, "本机提交不可用。"))

    override suspend fun commit(repository: GitRepository, request: GitCommitRequest): GitCommitResult =
        (delegate as? GitCommitter)?.commit(repository, request)
            ?: GitCommitResult.Rejected(GitFailure(GitFailureKind.Unavailable, "本机提交不可用。"))

    override fun canCreateWorktree(binding: GitBinding): Boolean =
        repository.canRegister(binding) && (delegate as? GitWorktreeCreator)?.canCreateWorktree(binding) == true

    override fun canSwitchBranch(binding: GitBinding): Boolean =
        (delegate as? GitBranchSwitcher)?.canSwitchBranch(binding) == true

    override suspend fun switchBranch(repository: GitRepository, request: GitBranchCheckoutRequest): GitBranchCheckoutResult =
        (delegate as? GitBranchSwitcher)?.switchBranch(repository, request)
            ?: GitBranchCheckoutResult.Rejected(GitFailure(GitFailureKind.Unavailable, "本机分支切换不可用。"))

    override suspend fun createWorktree(repository: GitRepository, request: GitCreateWorktreeRequest): GitCreateWorktreeResult {
        val creator = delegate as? GitWorktreeCreator
            ?: return rejected("本机工作树创建不可用。")
        val prepared = try { this.repository.prepare(repository, request) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { return rejected("无法持久登记 Prepared 意图；未调用 Git 创建。") }
        try { this.repository.beforeDispatch(prepared) }
        catch (cancelled: CancellationException) {
            if (prepared.observation.durabilityWarning == null)
                this.repository.stopped(prepared, ManagedRegistrationPhase.Rejected)
            throw cancelled
        } catch (_: Exception) {
            if (prepared.observation.durabilityWarning != null)
                return rejected("Prepared 已提交但持久性未确认；保留意图与告警，未调用 Git 创建。")
            this.repository.stopped(prepared, ManagedRegistrationPhase.Rejected)
            return rejected("创建前 owner、仓库或登记磁盘字节发生变化；未调用 Git 创建。")
        }
        // From capability invocation onward exceptions/cancellation cannot be labeled preflight failures.
        val result = try { creator.createWorktree(repository, request) }
        catch (cancelled: CancellationException) {
            this.repository.stopped(prepared, ManagedRegistrationPhase.UnknownOutcome)
            throw cancelled
        } catch (_: Exception) {
            this.repository.stopped(prepared, ManagedRegistrationPhase.UnknownOutcome)
            return GitCreateWorktreeResult.Failed(GitCreateWorktreeFailure(
                GitFailure(GitFailureKind.CommandFailed, "Git 创建调用后结果未知；请读取实际工作树，不要直接重试。"), true))
        }
        when (result) {
            is GitCreateWorktreeResult.Created -> this.repository.register(prepared, result.receipt)
            is GitCreateWorktreeResult.Failed -> this.repository.stopped(prepared,
                if (result.failure.needsRescan) ManagedRegistrationPhase.UnknownOutcome else ManagedRegistrationPhase.Rejected)
        }
        return result
    }

    private fun rejected(message: String): GitCreateWorktreeResult = GitCreateWorktreeResult.Failed(
        GitCreateWorktreeFailure(GitFailure(GitFailureKind.Unavailable, message)))
}
