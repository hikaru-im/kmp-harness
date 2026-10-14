package im.hikaru.harness.desktop.projects

import im.hikaru.harness.client.projects.*
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.OverlappingFileLockException
import java.nio.file.AccessDeniedException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/** Each read owns its CAS token. An overlapping read cannot replace another operation's bytes. */
public class DesktopLocalProjectReadToken internal constructor(
    internal val source: Path, internal val owner: LocalProjectOwner,
    internal val revision: Long, bytes: ByteArray?,
) {
    internal val bytes: ByteArray? = bytes?.copyOf()
}

public sealed interface DesktopLocalProjectReadResult {
    public data class Ready(val snapshot: LocalProjectSnapshot, val token: DesktopLocalProjectReadToken) : DesktopLocalProjectReadResult
    public data class Failed(val failure: LocalProjectFailure) : DesktopLocalProjectReadResult
}

public sealed interface DesktopLocalProjectStoreWriteResult {
    public data class Committed(val snapshot: LocalProjectSnapshot, val durabilityWarning: String? = null) : DesktopLocalProjectStoreWriteResult
    public data class Failed(val failure: LocalProjectFailure) : DesktopLocalProjectStoreWriteResult
}

/** Version 1 local registrations, not session history. Atomic rename is required; no unsafe fallback. */
public class DesktopLocalProjectStore(path: Path) {
    private val target = path.toAbsolutePath().normalize()
    private val mutex = locks.computeIfAbsent(target) { Mutex() }
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = false; isLenient = false }

    public suspend fun read(owner: LocalProjectOwner): DesktopLocalProjectReadResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                currentCoroutineContext().ensureActive()
                val bytes = source()
                when (val parsed = parse(bytes)) {
                    is Parsed.Failed -> DesktopLocalProjectReadResult.Failed(parsed.failure)
                    is Parsed.Ready -> {
                        val snapshot = parsed.document.owners.firstOrNull { it.owner == owner } ?: LocalProjectSnapshot(owner)
                        DesktopLocalProjectReadResult.Ready(snapshot, DesktopLocalProjectReadToken(target, owner, snapshot.revision, bytes))
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { DesktopLocalProjectReadResult.Failed(ioFailure(error, "无法读取项目登记文件。")) }
        }
    }

    public suspend fun compareAndSet(
        token: DesktopLocalProjectReadToken, snapshot: LocalProjectSnapshot,
        isCurrent: () -> Boolean = { true },
    ): DesktopLocalProjectStoreWriteResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            currentCoroutineContext().ensureActive()
            if (!isCurrent()) return@withLock failed(LocalProjectFailure.StaleBinding)
            if (token.source != target || snapshot.owner != token.owner || token.revision == Long.MAX_VALUE ||
                token.revision < 0 || snapshot.revision != token.revision + 1 || snapshotError(snapshot) != null) {
                return@withLock failed(LocalProjectFailure.Rejected("无效项目快照或 CAS 令牌。"))
            }
            // Once rename succeeds, report its durable result even if the caller cancelled during commit.
            // withContext may still deliver cancellation on return; the persisted receipt permits exact retry.
            withContext(NonCancellable) commit@{
                var committed: DesktopLocalProjectStoreWriteResult.Committed? = null
                try {
                    Files.createDirectories(target.parent)
                    FileChannel.open(target.resolveSibling(target.fileName.toString() + ".lock"),
                        StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { channel ->
                        val lock = try { channel.tryLock() } catch (_: OverlappingFileLockException) { null }
                            ?: return@commit failed(LocalProjectFailure.Conflict("另一个进程正在保存项目，请重新读取后重试。"))
                        lock.use {
                            if (!isCurrent()) return@commit failed(LocalProjectFailure.StaleBinding)
                            val bytes = source()
                            if (!sameBytes(bytes, token.bytes)) return@commit conflict()
                            val document = when (val parsed = parse(bytes)) {
                                is Parsed.Failed -> return@commit failed(parsed.failure)
                                is Parsed.Ready -> parsed.document
                            }
                            val old = document.owners.firstOrNull { it.owner == token.owner } ?: LocalProjectSnapshot(token.owner)
                            if (old.revision != token.revision) return@commit conflict()
                            if (!validTransition(old, snapshot)) return@commit failed(LocalProjectFailure.Rejected("项目变更会破坏已有登记或创建凭据。"))
                            val next = document.copy(owners = document.owners.filterNot { it.owner == token.owner } + snapshot)
                            val encoded = json.encodeToString(Document.serializer(), next).toByteArray(Charsets.UTF_8)
                            // Directory fsync must work before the previous file is touched.
                            FileChannel.open(target.parent, StandardOpenOption.READ).use { directory ->
                                directory.force(true)
                                val temp = Files.createTempFile(target.parent, ".local-projects-", ".tmp")
                                try {
                                    FileChannel.open(temp, StandardOpenOption.WRITE).use { file ->
                                        val buffer = ByteBuffer.wrap(encoded)
                                        while (buffer.hasRemaining()) file.write(buffer)
                                        file.force(true)
                                    }
                                    if (!isCurrent()) return@commit failed(LocalProjectFailure.StaleBinding)
                                    if (!sameBytes(source(), token.bytes)) return@commit conflict()
                                    Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                                    committed = DesktopLocalProjectStoreWriteResult.Committed(snapshot)
                                    try { directory.force(true) } catch (_: Exception) {
                                        committed = committed!!.copy(durabilityWarning = "项目已原子保存，但目录同步失败；请重新读取核对。")
                                    }
                                } finally { Files.deleteIfExists(temp) }
                            }
                        }
                    }
                    committed ?: failed(LocalProjectFailure.Error("项目保存没有完成。"))
                } catch (error: Exception) {
                    committed?.copy(durabilityWarning = committed?.durabilityWarning ?: "项目已原子保存，但关闭或同步文件失败；请重新读取核对。")
                        ?: failed(ioFailure(error, "项目保存失败；旧文件未被替换。"))
                }
            }
        }
    }

    private fun source(): ByteArray? = try { Files.readAllBytes(target) } catch (_: NoSuchFileException) { null }

    private fun parse(bytes: ByteArray?): Parsed {
        if (bytes == null) return Parsed.Ready(Document())
        return try {
            val text = bytes.toString(Charsets.UTF_8)
            if (!text.toByteArray(Charsets.UTF_8).contentEquals(bytes)) return invalid()
            val root = json.parseToJsonElement(text) as? JsonObject ?: return invalid()
            val version = (root["schemaVersion"] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull ?: return invalid()
            if (version != 1) return Parsed.Failed(LocalProjectFailure.UnsupportedVersion(version))
            if (root.keys != setOf("schemaVersion", "owners")) return invalid()
            val document = json.decodeFromString(Document.serializer(), text)
            if (document.owners.distinctBy { it.owner }.size != document.owners.size ||
                document.owners.any { snapshotError(it) != null }) return invalid()
            Parsed.Ready(document)
        } catch (_: Exception) { invalid() }
    }

    private fun snapshotError(snapshot: LocalProjectSnapshot): String? {
        if (snapshot.revision < 0 || snapshot.owner.hostId.value.isBlank()) return "owner/revision"
        if (snapshot.projects.distinctBy { it.id }.size != snapshot.projects.size) return "duplicate project"
        if (snapshot.projects.any { it.owner != snapshot.owner || it.name.isBlank() || it.roots.size != 1 ||
                !absoluteNormal(it.roots.single().path) }) return "project/root"
        if (snapshot.memberships.distinctBy { it.session.id }.size != snapshot.memberships.size) return "duplicate session"
        if (snapshot.memberships.any { snapshot.project(it.projectId) == null || it.session.createdAt < 0 ||
                it.session.cwd?.let(::absolutePath) != true }) return "membership"
        if (snapshot.createReceipts.distinctBy { it.request.requestId }.size != snapshot.createReceipts.size) return "duplicate receipt"
        if (snapshot.createReceipts.any { snapshot.project(it.projectId) == null || it.request.requestId.isBlank() ||
                it.request.name.isBlank() || !absolutePath(it.request.directory) }) return "receipt"
        return null
    }

    private fun validTransition(old: LocalProjectSnapshot, next: LocalProjectSnapshot): Boolean =
        old.projects.all { project -> next.project(project.id)?.let { it.owner == project.owner && it.name == project.name } == true } &&
            old.memberships.all { it in next.memberships } && old.createReceipts.all { it in next.createReceipts }

    private fun absoluteNormal(value: String): Boolean = try {
        val path = Path.of(value)
        path.isAbsolute && path.normalize().toString() == value
    } catch (_: Exception) { false }

    private fun absolutePath(value: String): Boolean = try { Path.of(value).isAbsolute } catch (_: Exception) { false }

    private fun invalid() = Parsed.Failed(LocalProjectFailure.Error("项目登记文件损坏或包含未知字段；禁止覆盖原文件。"))
    private fun failed(failure: LocalProjectFailure) = DesktopLocalProjectStoreWriteResult.Failed(failure)
    private fun conflict() = failed(LocalProjectFailure.Conflict("项目文件已改变；请重新读取后重试，原文件保持不变。"))
    private fun ioFailure(error: Exception, message: String): LocalProjectFailure = when (error) {
        is AccessDeniedException, is SecurityException -> LocalProjectFailure.AccessDenied(message)
        else -> LocalProjectFailure.Error(message)
    }

    @Serializable private data class Document(val schemaVersion: Int = 1, val owners: List<LocalProjectSnapshot> = emptyList())
    private sealed interface Parsed {
        data class Ready(val document: Document) : Parsed
        data class Failed(val failure: LocalProjectFailure) : Parsed
    }
    private companion object {
        val locks = ConcurrentHashMap<Path, Mutex>()
        fun sameBytes(a: ByteArray?, b: ByteArray?): Boolean =
            if (a == null || b == null) a == null && b == null else a.contentEquals(b)
    }
}
