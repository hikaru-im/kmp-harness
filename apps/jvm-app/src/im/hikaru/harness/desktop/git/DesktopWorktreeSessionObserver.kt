package im.hikaru.harness.desktop.git

import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.harness.bundle.desktop.resolveDesktopHarnessHome
import im.hikaru.harness.bundle.desktop.startDesktopProfile
import im.hikaru.harness.client.git.*
import im.hikaru.harness.profile.file.ProfiledHarnessHost
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.session.SessionHeader
import im.hikaru.harness.session.SessionId
import im.hikaru.harness.session.SESSION_FORMAT_VERSION
import im.hikaru.harness.session.SessionKey
import im.hikaru.harness.session.SessionArchiveAcquire
import im.hikaru.harness.session.SessionArchiveToken
import im.hikaru.harness.session.SessionReservationBusyReason
import im.hikaru.harness.session.SessionStore
import im.hikaru.harness.session.persistence.SessionPersistence
import im.hikaru.harness.session.persistence.SessionPersistenceKey
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.security.MessageDigest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** The bootstrap itself supplies provenance; an arbitrary Context with the same HostId cannot bind. */
public class DesktopObservedHost internal constructor(
    public val profiledHost: ProfiledHarnessHost,
    public val sessionObserver: DesktopWorktreeSessionObserver,
)

/** Header consumer only. Captured services are never replaced, loaded, flushed or repaired here. */
public class DesktopWorktreeSessionObserver private constructor(
    private val context: Context,
    private val hostId: HostId,
    private val home: Path?,
    private val live: SessionStore?,
    private val durable: SessionPersistence?,
    private val captureIssue: ManagedSessionIssue?,
) {
    public suspend fun observe(
        binding: GitBinding, owner: ManagedWorktreeOwner, roots: List<String>,
        currentOwner: suspend () -> ManagedWorktreeOwner?,
    ): Map<String, ManagedSessionObservation> {
        currentCoroutineContext().ensureActive()
        val started = System.currentTimeMillis()
        fun result(sessions: Map<String, List<ManagedObservedSession>>, issues: Map<String, List<ManagedSessionIssue>>)
            : Map<String, ManagedSessionObservation> {
            val completed = System.currentTimeMillis().coerceAtLeast(started)
            return roots.distinct().associateWith { root ->
                val reasons = issues[root].orEmpty()
                val status = when {
                    reasons.isEmpty() -> ManagedSessionObservationStatus.Observed
                    reasons.all { it.kind in setOf(ManagedSessionIssueKind.Unsupported, ManagedSessionIssueKind.SourceUnavailable) } ->
                        ManagedSessionObservationStatus.Unavailable
                    else -> ManagedSessionObservationStatus.Unknown
                }
                ManagedSessionObservation(owner, started, completed, status, sessions[root].orEmpty(), reasons)
            }
        }
        fun rejected(issue: ManagedSessionIssue) = result(emptyMap(), roots.associateWith { listOf(issue) })
        try {
            fence(binding, owner, currentOwner)?.let { return rejected(it) }
            captureIssue?.let { return rejected(it) }
            val store = live ?: return rejected(ManagedSessionIssue(ManagedSessionIssueKind.SourceUnavailable, "本机 live 会话服务不可用。"))
            val headers = linkedMapOf<String, MutableList<Pair<SessionHeader, ManagedSessionSource>>>()
            val issues = mutableListOf<ManagedSessionIssue>()
            store.list().forEach { session ->
                headers.getOrPut(session.id.value) { mutableListOf() }.add(session.header to ManagedSessionSource.Live)
            }
            currentCoroutineContext().ensureActive()
            val persisted = durable?.listHeaders()
            if (persisted == null) {
                issues += ManagedSessionIssue(
                    if (durable == null) ManagedSessionIssueKind.SourceUnavailable else ManagedSessionIssueKind.Unsupported,
                    if (durable == null) "本机持久会话目录服务不可用。" else "此持久会话服务不支持只读 header 目录。",
                )
            } else persisted.forEach { header ->
                headers.getOrPut(header.id.value) { mutableListOf() }.add(header to ManagedSessionSource.Durable)
            }
            // Raw headers retain deleted identities to prevent reuse; they are not visible chats.
            // Apply the same tombstone to live entries whose cleanup may not have completed.
            for (id in headers.keys.toList()) {
                if (durable?.metadata(SessionId(id))?.deleted == true) headers.remove(id)
            }
            currentCoroutineContext().ensureActive()
            val resolved = withContext(Dispatchers.IO) {
                headers.mapNotNull { (id, versions) ->
                    currentCoroutineContext().ensureActive()
                    val first = versions.first().first
                    // Format/event version does not affect cwd or lineage. Every other header field does.
                    if (versions.any { (header, _) -> header.copy(version = first.version) != first }) {
                        issues += ManagedSessionIssue(ManagedSessionIssueKind.HeaderConflict, "Live 与 Durable header 身份元数据冲突。", id)
                        null
                    } else {
                        val path = resolveDirectory(first.cwd, id)
                        path.issue?.let { issues += it }
                        path.path?.let { real -> ManagedObservedSession(id, real.toString(), first.parentSession?.value,
                            versions.map { it.second }.toSet(), pinKey(first)) to real }
                    }
                }
            }
            val byRoot = linkedMapOf<String, List<ManagedObservedSession>>()
            val rootIssues = linkedMapOf<String, List<ManagedSessionIssue>>()
            withContext(Dispatchers.IO) {
                roots.distinct().forEach { root ->
                    currentCoroutineContext().ensureActive()
                    val directory = resolveDirectory(root, null)
                    byRoot[root] = directory.path?.let { realRoot ->
                        resolved.filter { (_, cwd) -> cwd.startsWith(realRoot) }.map { it.first }
                    }.orEmpty()
                    rootIssues[root] = issues.toList() + listOfNotNull(directory.issue?.copy(
                        kind = ManagedSessionIssueKind.InvalidRoot, reason = "工作树根无法核验：" + directory.issue.reason))
                }
            }
            fence(binding, owner, currentOwner)?.let { return rejected(it) }
            currentCoroutineContext().ensureActive()
            return result(byRoot, rootIssues)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            currentCoroutineContext().ensureActive()
            // Reject partial data after a failed source or lifetime check, never manufacture an empty success.
            val issue = try { fence(binding, owner, currentOwner) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { ManagedSessionIssue(ManagedSessionIssueKind.SourceChanged, "会话来源已释放或替换。") }
            return rejected(issue ?: ManagedSessionIssue(ManagedSessionIssueKind.SourceFailed, "无法完成本次只读会话观察。"))
        }
    }

    internal suspend fun isCurrent(binding: GitBinding, owner: ManagedWorktreeOwner,
        currentOwner: suspend () -> ManagedWorktreeOwner?): Boolean = captureIssue == null && fence(binding, owner, currentOwner) == null

    /**
     * Busy-reject fence for archive. It closes new operation admission on the exact live chat and
     * reports any in-flight Session activity instead of queueing. It never cancels, clears or
     * loads anything by itself, and it is not an absence or deletion proof.
     */
    internal sealed interface ArchiveFence {
        data class Fenced(val token: SessionArchiveToken) : ArchiveFence
        data class Busy(val reason: SessionReservationBusyReason) : ArchiveFence
        data class Rejected(val issue: ManagedSessionIssue) : ArchiveFence
        data object NotLive : ArchiveFence
    }

    internal suspend fun acquireArchive(
        binding: GitBinding, owner: ManagedWorktreeOwner, id: String, headerFingerprint: String,
        currentOwner: suspend () -> ManagedWorktreeOwner?,
    ): ArchiveFence {
        fence(binding, owner, currentOwner)?.let { return ArchiveFence.Rejected(it) }
        captureIssue?.let { return ArchiveFence.Rejected(it) }
        val store = live ?: return ArchiveFence.Rejected(
            ManagedSessionIssue(ManagedSessionIssueKind.SourceUnavailable, "本机 live 会话服务不可用。"))
        val session = store.list().singleOrNull { it.id.value == id } ?: return ArchiveFence.NotLive
        if (pinKey(session.header) != PinKey(id, headerFingerprint)) return ArchiveFence.Rejected(
            ManagedSessionIssue(ManagedSessionIssueKind.SourceChanged, "live 会话 header 身份与归档键不一致。", id))
        return when (val result = store.admission.acquireArchive(session)) {
            is SessionArchiveAcquire.Granted -> ArchiveFence.Fenced(result.token)
            is SessionArchiveAcquire.Busy -> ArchiveFence.Busy(result.reason)
            is SessionArchiveAcquire.Rejected -> ArchiveFence.Rejected(
                ManagedSessionIssue(ManagedSessionIssueKind.SourceChanged, "会话准入拒绝：${result.reason}", id))
        }
    }

    private suspend fun fence(binding: GitBinding, owner: ManagedWorktreeOwner,
        currentOwner: suspend () -> ManagedWorktreeOwner?): ManagedSessionIssue? {
        currentCoroutineContext().ensureActive()
        if (binding.access != GitAccess.LocalReadOnly || binding.identity != null || owner.identity != null ||
            binding.hostId != hostId || owner.hostId != hostId || home?.toString() != owner.homeNamespace ||
            currentOwner() != owner) return ManagedSessionIssue(ManagedSessionIssueKind.OwnerChanged, "本机 Host、home 或匿名连接归属已变化；不显示会话目录。")
        // Context.get also checks disposing/disposed. Exact service references fence profile reconciliation.
        if (captureIssue != null) {
            context.get(SessionKey)
            context.get(SessionPersistenceKey)
            return null
        }
        if (context.get(SessionKey) !== live || context.get(SessionPersistenceKey) !== durable)
            return ManagedSessionIssue(ManagedSessionIssueKind.SourceChanged, "会话服务已替换；本次来源失效。")
        currentCoroutineContext().ensureActive()
        return null
    }

    private data class Directory(val path: Path? = null, val issue: ManagedSessionIssue? = null)
    private fun resolveDirectory(value: String?, id: String?): Directory {
        fun failed(kind: ManagedSessionIssueKind, reason: String) = Directory(issue = ManagedSessionIssue(kind, reason, id))
        if (value == null) return failed(ManagedSessionIssueKind.MissingCwd, "会话缺少 cwd，关联尚未判定。")
        return try {
            if (java.io.File.separatorChar == '/' && (value.startsWith("\\") || value.startsWith("//") ||
                    Regex("^[A-Za-z]:").containsMatchIn(value)))
                return failed(ManagedSessionIssueKind.InvalidPath, "cwd 不是本机 native 绝对路径。")
            val parsed = Path.of(value)
            if (!parsed.isAbsolute) return failed(ManagedSessionIssueKind.InvalidPath, "cwd 不是本机 native 绝对路径。")
            val real = parsed.normalize().toRealPath()
            when {
                !Files.isDirectory(real) -> failed(ManagedSessionIssueKind.NotDirectory, "cwd 不是目录。")
                !Files.isReadable(real) -> failed(ManagedSessionIssueKind.UnreadablePath, "cwd 目录不可读。")
                else -> Directory(path = real)
            }
        } catch (_: NoSuchFileException) { failed(ManagedSessionIssueKind.MissingPath, "cwd 目录不存在。") }
        catch (_: Exception) { failed(ManagedSessionIssueKind.InvalidPath, "无法核验 cwd 的真实目录。") }
    }

    public companion object {
        private val pinJson = Json { encodeDefaults = true }
        internal fun pinKey(header: SessionHeader): PinKey {
            val bytes = pinJson.encodeToString(header.copy(version = SESSION_FORMAT_VERSION)).toByteArray(Charsets.UTF_8)
            return PinKey(header.id.value, MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) })
        }

        /** Only this factory grants production provenance: DesktopBundle chooses home/harness-sessions.db. */
        public suspend fun startDesktopHost(harnessHome: Path? = null, overlays: List<Path> = emptyList()): DesktopObservedHost {
            val resolved = resolveDesktopHarnessHome(harnessHome).directory
            val profiled = startDesktopProfile(harnessHome = resolved, overlays = overlays)
            return DesktopObservedHost(profiled, capture(profiled.host.runtime.context,
                profiled.host.profile.hostDescription.hostId, resolved))
        }

        /** Error-contract seam only; normal consumers bind through the production bootstrap above. */
        internal fun captureForTest(context: Context, hostId: HostId, home: Path): DesktopWorktreeSessionObserver =
            capture(context, hostId, home)

        private fun capture(context: Context, hostId: HostId, home: Path): DesktopWorktreeSessionObserver {
            var capturedHome = home.toAbsolutePath().normalize()
            return try {
                val canonicalHome = home.toRealPath()
                capturedHome = canonicalHome
                check(Files.isDirectory(canonicalHome) && Files.isReadable(canonicalHome))
                DesktopWorktreeSessionObserver(context, hostId, canonicalHome, context.get(SessionKey),
                    context.get(SessionPersistenceKey), null)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                DesktopWorktreeSessionObserver(context, hostId, capturedHome, null, null,
                    ManagedSessionIssue(ManagedSessionIssueKind.SourceUnavailable, "本机 Runtime/home 会话来源不可用。"))
            }
        }
    }
}
