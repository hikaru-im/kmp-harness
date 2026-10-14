package im.hikaru.harness.desktop.git

import im.hikaru.harness.client.git.*
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.*
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.StandardOpenOption.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

public class WorktreePinStoreException(message: String) : IllegalStateException(message)

/** Short OS leases cover file operations only; never held during Git or connection IO. */
public open class DesktopWorktreePinStore(
    path: Path,
    private val directorySync: (Path) -> Unit = { directory ->
        FileChannel.open(directory, READ).use { it.force(true) }
    },
) {
    private val file = path.toAbsolutePath().normalize()
    private val lockFile = file.resolveSibling(file.fileName.toString() + ".lock")
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = false; isLenient = false }

    @Serializable
    private data class Document(val schemaVersion: Int, val owners: List<ManagedWorktreePinSnapshot>)

    public class Observation internal constructor(
        public val snapshot: ManagedWorktreePinSnapshot,
        internal val bytes: ByteArray?, internal val source: Path,
        public val durabilityWarning: String? = null,
    )

    public open fun read(owner: ManagedWorktreeOwner): Observation = leased {
        validateOwner(owner)
        val bytes = bytes()
        val document = decode(bytes)
        Observation(document.owners.singleOrNull { it.owner == owner } ?: ManagedWorktreePinSnapshot(owner), bytes, file)
    }

    /** CAS is against the complete original bytes, including whitespace and every other owner. */
    public open fun compareAndSet(expected: Observation, update: ManagedWorktreePinSnapshot): Observation = leased {
        require(expected.source == file && update.owner == expected.snapshot.owner)
        require(expected.snapshot.revision < Long.MAX_VALUE && update.revision == expected.snapshot.revision + 1)
        if (!same(bytes(), expected.bytes)) throw WorktreePinStoreException("聊天固定存储发生磁盘 CAS 冲突。")
        val old = decode(expected.bytes)
        val next = Document(1, old.owners.filterNot { it.owner == update.owner } + update)
        validate(next)
        val encoded = json.encodeToString(next).toByteArray(Charsets.UTF_8)
        require(encoded.size <= 4 * 1024 * 1024) { "聊天固定文件过大；不会写入。" }
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
            if (!same(bytes(), expected.bytes)) throw WorktreePinStoreException("聊天固定存储发生磁盘 CAS 冲突。")
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            committed = true
            try { directorySync(file.parent) } catch (_: Exception) {
                warning = "聊天固定已提交，但目录 fsync 失败；崩溃持久性尚未确认。"
            }
        } finally {
            try { Files.deleteIfExists(temporary) } catch (cleanup: Exception) {
                if (!committed) throw cleanup
                warning = warning ?: "聊天固定已提交，但临时文件收尾失败。"
            }
        }
        Observation(update, encoded, file, warning)
    }

    /** Detect a competing edit before dispatch without changing the durable intent. */
    public fun requireCurrent(expected: Observation): Unit = leased {
        require(expected.source == file)
        if (!same(bytes(), expected.bytes)) throw WorktreePinStoreException("聊天固定存储发生磁盘 CAS 冲突。")
    }

    private fun <T> leased(block: () -> T): T {
        Files.createDirectories(file.parent)
        if (Files.isSymbolicLink(file) || Files.isSymbolicLink(lockFile) ||
            (Files.exists(lockFile, NOFOLLOW_LINKS) && !Files.isRegularFile(lockFile, NOFOLLOW_LINKS)))
            throw WorktreePinStoreException("聊天固定存储文件不能是符号链接。")
        FileChannel.open(lockFile, CREATE, WRITE, NOFOLLOW_LINKS).use { channel ->
            val lease = try { channel.tryLock() } catch (_: java.nio.channels.OverlappingFileLockException) { null }
                ?: throw WorktreePinStoreException("聊天固定存储正在被其他 owner 读取或写入。")
            lease.use { return block() }
        }
    }

    private fun bytes(): ByteArray? {
        if (!Files.exists(file, NOFOLLOW_LINKS)) return null
        if (!Files.isRegularFile(file, NOFOLLOW_LINKS) || Files.size(file) > 4 * 1024 * 1024)
            throw WorktreePinStoreException("聊天固定存储文件无效或过大。")
        return FileChannel.open(file, READ, NOFOLLOW_LINKS).use { channel ->
            val size = channel.size()
            if (size > 4 * 1024 * 1024) throw WorktreePinStoreException("聊天固定文件过大。")
            val buffer = ByteBuffer.allocate(size.toInt())
            while (buffer.hasRemaining()) {
                if (channel.read(buffer) < 0) throw WorktreePinStoreException("读取期间聊天固定文件已变化。")
            }
            if (channel.size() != size) throw WorktreePinStoreException("读取期间聊天固定文件已变化。")
            buffer.array()
        }
    }

    private fun same(left: ByteArray?, right: ByteArray?): Boolean =
        if (left == null || right == null) left == null && right == null else left.contentEquals(right)

    private fun decode(bytes: ByteArray?): Document {
        if (bytes == null) return Document(1, emptyList())
        try {
            val text = Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString()
            rejectDuplicateKeys(text)
            val root = json.parseToJsonElement(text).jsonObject
            require(root.keys == setOf("schemaVersion", "owners"))
            root.getValue("owners").jsonArray.forEach { value ->
                val snapshot = value.jsonObject
                require(snapshot.keys == setOf("owner", "revision", "pins"))
                require(snapshot.getValue("owner").jsonObject.keys == setOf("identity", "hostId", "homeNamespace"))
                snapshot.getValue("pins").jsonArray.forEach {
                    require(it.jsonObject.keys == setOf("id", "headerFingerprint"))
                }
            }
            return json.decodeFromString<Document>(text).also(::validate)
        } catch (_: Exception) { throw WorktreePinStoreException("聊天固定存储 schema 或内容损坏；不会当作空登记。") }
    }

    private fun validateOwner(owner: ManagedWorktreeOwner) {
        val value = owner.homeNamespace
        val home = Path.of(value)
        require(home.isAbsolute && home.normalize().toString() == value && value.none { it.code < 32 })
        if (java.io.File.separatorChar == '/')
            require(!value.startsWith("\\") && !value.startsWith("//") && !Regex("^[A-Za-z]:").containsMatchIn(value))
        require(owner.hostId.value.isNotBlank())
    }

    private fun validate(document: Document) {
        require(document.schemaVersion == 1)
        require(document.owners.map { it.owner }.distinct().size == document.owners.size)
        document.owners.forEach { snapshot ->
            validateOwner(snapshot.owner)
            require(snapshot.revision >= 0)
            require(snapshot.pins.distinct().size == snapshot.pins.size)
            snapshot.pins.forEach { key ->
                require(key.id.isNotBlank() && key.id.none { it.code < 32 })
                require(key.headerFingerprint.matches(Regex("[0-9a-f]{64}")))
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
        fun value(depth: Int = 0) {
            require(depth <= 64)
            whitespace()
            require(index < text.length)
            when (text[index]) {
                '{' -> {
                    index++; whitespace()
                    val keys = mutableSetOf<String>()
                    if (text.getOrNull(index) != '}') while (true) {
                        whitespace(); require(keys.add(string())); whitespace()
                        require(text[index++] == ':'); value(depth + 1); whitespace()
                        if (text.getOrNull(index) != ',') break
                        index++
                    }
                    require(text[index++] == '}')
                }
                '[' -> {
                    index++; whitespace()
                    if (text.getOrNull(index) != ']') while (true) {
                        value(depth + 1); whitespace()
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
