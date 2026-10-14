package im.hikaru.harness.desktop.git

import im.hikaru.harness.client.git.*
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.*
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.StandardOpenOption.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

public class ManagedWorktreeStoreException(message: String) : IllegalStateException(message)

/** Short OS leases cover file operations only; never held during Git or connection IO. */
public open class DesktopManagedWorktreeStore(
    path: Path,
    private val directorySync: (Path) -> Unit = { directory ->
        FileChannel.open(directory, READ).use { it.force(true) }
    },
) {
    private val file = path.toAbsolutePath().normalize()
    private val lockFile = file.resolveSibling(file.fileName.toString() + ".lock")
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = false; isLenient = false }

    @Serializable
    private data class Document(val schemaVersion: Int, val owners: List<ManagedWorktreeSnapshot>)

    public class Observation internal constructor(
        public val snapshot: ManagedWorktreeSnapshot,
        internal val bytes: ByteArray?, internal val source: Path,
        public val durabilityWarning: String? = null,
    )

    public open fun read(owner: ManagedWorktreeOwner): Observation = leased {
        val bytes = bytes()
        val document = decode(bytes)
        Observation(document.owners.singleOrNull { it.owner == owner } ?: ManagedWorktreeSnapshot(owner), bytes, file)
    }

    /** CAS is against the complete original bytes, including whitespace and every other owner. */
    public open fun compareAndSet(expected: Observation, update: ManagedWorktreeSnapshot): Observation = leased {
        require(expected.source == file && update.owner == expected.snapshot.owner)
        require(update.revision == expected.snapshot.revision + 1)
        if (!same(bytes(), expected.bytes)) throw ManagedWorktreeStoreException("Managed 登记发生磁盘 CAS 冲突。")
        val old = decode(expected.bytes)
        val next = Document(1, old.owners.filterNot { it.owner == update.owner } + update)
        validate(next)
        val encoded = json.encodeToString(next).toByteArray(Charsets.UTF_8)
        val temporary = Files.createTempFile(file.parent, file.fileName.toString(), ".pending")
        var committed = false
        var warning: String? = null
        try {
            FileChannel.open(temporary, WRITE, TRUNCATE_EXISTING).use { channel ->
                val buffer = ByteBuffer.wrap(encoded)
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
            // Detect edits made while writing the temporary file. External non-cooperating writers
            // are not atomically locked by our lease; no stronger guarantee is claimed.
            if (!same(bytes(), expected.bytes)) throw ManagedWorktreeStoreException("Managed 登记发生磁盘 CAS 冲突。")
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            committed = true
            try { directorySync(file.parent) } catch (_: Exception) {
                warning = "登记已提交，但目录 fsync 失败；崩溃持久性尚未确认。"
            }
        } finally {
            try { Files.deleteIfExists(temporary) } catch (cleanup: Exception) {
                if (!committed) throw cleanup
                warning = warning ?: "登记已提交，但临时文件收尾失败。"
            }
        }
        Observation(update, encoded, file, warning)
    }

    /** Detect a competing edit before dispatch without changing the durable intent. */
    public fun requireCurrent(expected: Observation): Unit = leased {
        require(expected.source == file)
        if (!same(bytes(), expected.bytes)) throw ManagedWorktreeStoreException("Managed 登记发生磁盘 CAS 冲突。")
    }

    private fun <T> leased(block: () -> T): T {
        Files.createDirectories(file.parent)
        if (Files.isSymbolicLink(file) || Files.isSymbolicLink(lockFile))
            throw ManagedWorktreeStoreException("Managed 登记文件不能是符号链接。")
        FileChannel.open(lockFile, CREATE, WRITE, NOFOLLOW_LINKS).use { channel ->
            val lease = try { channel.tryLock() } catch (_: java.nio.channels.OverlappingFileLockException) { null }
                ?: throw ManagedWorktreeStoreException("Managed 登记正在被其他 owner 读取或写入。")
            lease.use { return block() }
        }
    }

    private fun bytes(): ByteArray? {
        if (!Files.exists(file, NOFOLLOW_LINKS)) return null
        if (!Files.isRegularFile(file, NOFOLLOW_LINKS) || Files.size(file) > 4 * 1024 * 1024)
            throw ManagedWorktreeStoreException("Managed 登记文件无效或过大。")
        return Files.readAllBytes(file)
    }

    private fun same(left: ByteArray?, right: ByteArray?): Boolean =
        if (left == null || right == null) left == null && right == null else left.contentEquals(right)

    private fun decode(bytes: ByteArray?): Document {
        if (bytes == null) return Document(1, emptyList())
        try {
            val text = bytes.toString(Charsets.UTF_8)
            rejectDuplicateKeys(text)
            return json.decodeFromString<Document>(text).also(::validate)
        } catch (_: Exception) { throw ManagedWorktreeStoreException("Managed 登记 schema 或内容损坏；不会当作空登记。") }
    }

    private fun validate(document: Document) {
        require(document.schemaVersion == 1)
        require(document.owners.map { it.owner }.distinct().size == document.owners.size)
        fun path(value: String) {
            val p = Path.of(value)
            require(p.isAbsolute && p.normalize().toString() == value && value.none { it.code < 32 })
        }
        document.owners.forEach { snapshot ->
            path(snapshot.owner.homeNamespace)
            require(snapshot.owner.hostId.value.isNotBlank() && snapshot.revision >= 0)
            require(snapshot.intents.map { it.operationId }.distinct().size == snapshot.intents.size)
            snapshot.intents.forEach { intent ->
                require(intent.operationId.isNotBlank() && intent.branch.isNotBlank() && intent.startRef.isNotBlank() && intent.createdAt >= 0)
                listOf(intent.commonDir, intent.sourceRoot, intent.sourceGitDir, intent.root).forEach(::path)
                if (intent.phase in setOf(ManagedRegistrationPhase.Verified, ManagedRegistrationPhase.Registered)) {
                    val evidence = requireNotNull(intent.evidence)
                    path(evidence.targetGitDir)
                    require(evidence.startOid.matches(Regex("(?:[0-9a-fA-F]{40}|[0-9a-fA-F]{64})")))
                    require(evidence.branch == "refs/heads/${intent.branch}")
                } else require(intent.evidence == null)
            }
        }
    }

    /** Serialization rejects unknown fields, but its JSON parser alone accepts duplicate keys. */
    private fun rejectDuplicateKeys(text: String) {
        var index = 0
        fun whitespace() { while (index < text.length && text[index].isWhitespace()) index++ }
        fun string(): String {
            val start = index
            require(text[index++] == '"')
            while (index < text.length) {
                when (text[index++]) {
                    '\\' -> { require(index < text.length); index++ }
                    '"' -> return json.decodeFromString<String>(text.substring(start, index))
                }
            }
            error("Unterminated string")
        }
        fun value() {
            whitespace()
            require(index < text.length)
            when (text[index]) {
                '{' -> {
                    index++; whitespace()
                    val keys = mutableSetOf<String>()
                    if (text.getOrNull(index) != '}') while (true) {
                        whitespace(); require(keys.add(string())); whitespace()
                        require(text[index++] == ':'); value(); whitespace()
                        if (text.getOrNull(index) != ',') break
                        index++
                    }
                    require(text[index++] == '}')
                }
                '[' -> {
                    index++; whitespace()
                    if (text.getOrNull(index) != ']') while (true) {
                        value(); whitespace()
                        if (text.getOrNull(index) != ',') break
                        index++
                    }
                    require(text[index++] == ']')
                }
                '"' -> string()
                else -> {
                    val start = index
                    while (index < text.length && text[index] !in " ,]}\r\n\t") index++
                    require(index > start)
                }
            }
        }
        value(); whitespace(); require(index == text.length)
    }
}
