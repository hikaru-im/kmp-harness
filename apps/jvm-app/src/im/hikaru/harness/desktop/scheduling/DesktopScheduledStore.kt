package im.hikaru.harness.desktop.scheduling

import im.hikaru.harness.client.scheduling.*
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

/** One versioned document can retain several Host/member owners; generation is never persisted.
 * Cooperative writers use the same sidecar lock and byte CAS. Never deletes the old document.
 */
public class DesktopScheduledStore(path: Path) : ScheduledStore {
    private val target = path.toAbsolutePath().normalize()
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = false; isLenient = false }
    private val mutex = locks.computeIfAbsent(target) { Mutex() }
    private var observed = false
    private var expectedBytes: ByteArray? = null

    override suspend fun read(owner: ScheduledOwner): ScheduledReadResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val bytes = source()
                when (val parsed = parse(bytes)) {
                    is Parsed.Valid -> {
                        expectedBytes = bytes
                        observed = true
                        parsed.document.owners.firstOrNull { it.owner == owner }?.let { ScheduledReadResult.Ready(it) }
                            ?: ScheduledReadResult.Missing
                    }
                    is Parsed.Version -> { observed = false; ScheduledReadResult.UnsupportedVersion(parsed.version) }
                    is Parsed.Invalid -> { observed = false; ScheduledReadResult.Invalid(parsed.message) }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                observed = false
                ScheduledReadResult.IoFailure("无法读取定时任务文件：" + (error.message ?: error.javaClass.simpleName))
            }
        }
    }

    override suspend fun compareAndSet(
        owner: ScheduledOwner, expectedRevision: Long, snapshot: ScheduledSnapshot, isCurrentScope: () -> Boolean,
    ): ScheduledWriteResult = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (!isCurrentScope()) return@withLock ScheduledWriteResult.StaleScope
            if (!observed) return@withLock ScheduledWriteResult.Failed("必须先成功读取有效配置；坏文件不会被覆盖。")
            if (snapshot.owner != owner || expectedRevision < 0 || expectedRevision == Long.MAX_VALUE ||
                snapshot.revision != expectedRevision + 1 || ScheduledValidator.snapshotError(snapshot) != null) {
                return@withLock ScheduledWriteResult.Failed("任务快照或预期版本无效。")
            }
            // Once rename commits, cancellation cannot turn an actual commit into a reported rollback.
            withContext(NonCancellable) commit@{
                var committed: ScheduledWriteResult.Committed? = null
                try {
                    Files.createDirectories(target.parent)
                    FileChannel.open(target.resolveSibling(target.fileName.toString() + ".lock"),
                        StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { lockChannel ->
                        val lock = lockChannel.tryLock() ?: return@commit ScheduledWriteResult.Failed("另一个进程正在写入任务配置，请稍后重新读取。")
                        lock.use {
                            val bytes = source()
                            if (!sameBytes(bytes, expectedBytes)) return@commit ScheduledWriteResult.Failed("任务文件已被外部修改；请重新读取，当前草稿保留。")
                            val parsed = parse(bytes) as? Parsed.Valid
                                ?: return@commit ScheduledWriteResult.Failed("任务文件版本或格式无效，禁止覆盖。")
                            val old = parsed.document.owners.firstOrNull { it.owner == owner } ?: ScheduledSnapshot(owner)
                            if (old.revision != expectedRevision) return@commit ScheduledWriteResult.Failed("任务版本冲突，请重新读取。")
                            if (!ScheduledLedgerRules.validTransition(old, snapshot)) return@commit ScheduledWriteResult.Failed("任务、游标或运行日志变更无效。")
                            // Always write the current shape; a legacy v1/v2 read is upgraded on its next commit.
                            val document = parsed.document.copy(schemaVersion = CurrentSchemaVersion,
                                owners = parsed.document.owners.filterNot { it.owner == owner } + snapshot)
                            val encoded = json.encodeToString(Document.serializer(), document).toByteArray(Charsets.UTF_8)
                            // Preflight directory fsync before touching the old file. Unsupported filesystems fail safely.
                            FileChannel.open(target.parent, StandardOpenOption.READ).use { directory ->
                                directory.force(true)
                                val temp = Files.createTempFile(target.parent, ".scheduled-", ".tmp")
                                try {
                                    FileChannel.open(temp, StandardOpenOption.WRITE).use { file ->
                                        val buffer = ByteBuffer.wrap(encoded)
                                        while (buffer.hasRemaining()) file.write(buffer)
                                        file.force(true)
                                    }
                                    if (!isCurrentScope()) return@commit ScheduledWriteResult.StaleScope
                                    if (!sameBytes(source(), expectedBytes)) return@commit ScheduledWriteResult.Failed("任务文件在保存期间发生变更；旧文件保持不变。")
                                    Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                                    committed = ScheduledWriteResult.Committed(snapshot)
                                    expectedBytes = encoded
                                    observed = true
                                    val warning = try { directory.force(true); null } catch (error: Exception) {
                                        "任务已原子替换，但目录同步失败，断电持久性未确认：" + (error.message ?: error.javaClass.simpleName)
                                    }
                                    committed = ScheduledWriteResult.Committed(snapshot, warning)
                                    return@commit committed!!
                                } finally { try { Files.deleteIfExists(temp) } catch (_: Exception) { /* Do not disguise a commit. */ } }
                            }
                        }
                    }
                } catch (error: Exception) {
                    committed?.copy(durabilityWarning = committed?.durabilityWarning ?:
                        "任务已原子替换，但关闭/同步文件时失败：" + (error.message ?: error.javaClass.simpleName))
                        ?: ScheduledWriteResult.Failed("保存失败；旧任务文件保持不变：" + (error.message ?: error.javaClass.simpleName))
                }
            }
        }
    }

    private fun parse(bytes: ByteArray?): Parsed {
        if (bytes == null) return Parsed.Valid(Document())
        return try {
            val text = bytes.toString(Charsets.UTF_8)
            if (!text.toByteArray(Charsets.UTF_8).contentEquals(bytes)) return Parsed.Invalid("文件不是有效 UTF-8；原文件保持不变。")
            val root = json.parseToJsonElement(text) as? JsonObject ?: return Parsed.Invalid("配置根节点无效。")
            val version = (root["schemaVersion"] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull
            if (version == null || version !in 1..3) return Parsed.Version(version)
            if (root.keys != setOf("schemaVersion", "owners")) return Parsed.Invalid("配置包含未知或缺失字段；禁止破坏性写入。")
            // v1 lacks the ledger and cursors; decode it with its original strict shape so injected v2/v3
            // fields are rejected. v2 and v3 share one shape: v3 only adds the optional task `updatedAt`,
            // which a v2 file omits and decodes to null; the next commit rewrites the document as v3.
            val document = if (version == 1) {
                val legacy = json.decodeFromString(LegacyDocument.serializer(), text)
                if (legacy.owners.any { owner -> owner.tasks.any { it.status != ScheduledTaskStatus.Paused } }) {
                    return Parsed.Invalid("v1 文件存在无执行游标的启用任务；须人工检查，禁止自动执行或覆盖。")
                }
                Document(owners = legacy.owners.map { ScheduledSnapshot(it.owner, it.revision, it.tasks, it.deletedTaskIds) })
            } else json.decodeFromString(Document.serializer(), text)
            if (document.owners.map { it.owner }.distinct().size != document.owners.size) return Parsed.Invalid("重复 Host/member 归属。")
            document.owners.firstNotNullOfOrNull(ScheduledValidator::snapshotError)?.let { return Parsed.Invalid(it) }
            val engine = ScheduleEngine(DesktopScheduleCalendar())
            document.owners.flatMap { it.tasks }.firstNotNullOfOrNull { engine.validationError(it.schedule) }
                ?.let { return Parsed.Invalid(it) }
            Parsed.Valid(document)
        } catch (error: Exception) { Parsed.Invalid("配置格式损坏或包含未知字段/规则；原文件保持不变：" + (error.message ?: "解析失败")) }
    }

    private fun source(): ByteArray? = if (Files.notExists(target)) null else Files.readAllBytes(target)
    private fun sameBytes(left: ByteArray?, right: ByteArray?): Boolean =
        if (left == null || right == null) left == null && right == null else left.contentEquals(right)

    @Serializable private data class Document(
        val schemaVersion: Int = CurrentSchemaVersion, val owners: List<ScheduledSnapshot> = emptyList(),
    )
    @Serializable private data class LegacyDocument(val schemaVersion: Int = 1, val owners: List<LegacySnapshot> = emptyList())
    @Serializable private data class LegacySnapshot(
        val owner: ScheduledOwner, val revision: Long = 0, val tasks: List<ScheduledTask> = emptyList(),
        val deletedTaskIds: List<String> = emptyList(),
    )
    private sealed interface Parsed {
        data class Valid(val document: Document) : Parsed
        data class Version(val version: Int?) : Parsed
        data class Invalid(val message: String) : Parsed
    }
    private companion object {
        /** Shape this build writes: v3 adds the optional task `updatedAt`; v1 and v2 stay read-only legacy shapes. */
        const val CurrentSchemaVersion = 3
        val locks = ConcurrentHashMap<Path, Mutex>()
    }
}
