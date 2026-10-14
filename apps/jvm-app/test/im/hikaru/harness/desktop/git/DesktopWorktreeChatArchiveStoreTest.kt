package im.hikaru.harness.desktop.git

import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.harness.client.account.BackendTenant
import im.hikaru.harness.client.account.MemberIdentity
import im.hikaru.harness.client.git.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

class DesktopWorktreeChatArchiveStoreTest {
    @Test
    fun roundTripOwnersAndFreshReader() = withStore { path, owner ->
        val member = MemberIdentity(BackendTenant("https://member.example", 1), 1)
        val owners = listOf(owner, owner.copy(identity = member),
            owner.copy(hostId = HostId("other")),
            owner.copy(homeNamespace = path.parent.resolve("other-home").toString()))
        val store = DesktopWorktreeChatArchiveStore(path)
        owners.forEachIndexed { index, partition ->
            val read = store.read(partition)
            store.compareAndSet(read, read.snapshot.copy(revision = 1, archived = listOf(key.copy(id = "chat-$index"))))
        }
        owners.forEachIndexed { index, partition ->
            val read = DesktopWorktreeChatArchiveStore(path).read(partition)
            assertEquals(partition, read.snapshot.owner)
            assertEquals(1L, read.snapshot.revision)
            assertEquals(listOf(key.copy(id = "chat-$index")), read.snapshot.archived)
        }
        val current = store.read(owner)
        val restored = store.compareAndSet(current, current.snapshot.copy(revision = 2, archived = emptyList()))
        assertEquals(restored.snapshot, DesktopWorktreeChatArchiveStore(path).read(owner).snapshot)
        assertEquals(listOf(key.copy(id = "chat-1")), store.read(owners[1]).snapshot.archived)
    }

    @Test
    fun completeByteCasRejectsWhitespaceAndForeignOwnerDrift() = withStore { path, owner ->
        val store = DesktopWorktreeChatArchiveStore(path)
        val empty = store.read(owner)
        val first = store.compareAndSet(empty, empty.snapshot.copy(revision = 1, archived = listOf(key)))
        val whitespace = Files.readAllBytes(path) + "\n ".toByteArray()
        Files.write(path, whitespace)
        assertFailsWith<WorktreeChatArchiveStoreException> {
            store.compareAndSet(first, first.snapshot.copy(revision = 2, archived = emptyList()))
        }
        assertFailsWith<WorktreeChatArchiveStoreException> { store.requireCurrent(first) }
        assertContentEquals(whitespace, Files.readAllBytes(path))
        val fresh = store.read(owner)
        val foreign = store.read(owner.copy(hostId = HostId("foreign")))
        store.compareAndSet(foreign, foreign.snapshot.copy(revision = 1))
        val before = Files.readAllBytes(path)
        assertFailsWith<WorktreeChatArchiveStoreException> {
            store.compareAndSet(fresh, fresh.snapshot.copy(revision = 2))
        }
        assertContentEquals(before, Files.readAllBytes(path))
        assertContentEquals(before, Files.readAllBytes(path))
    }

    @Test
    fun badSchemaDuplicateKeysAndDuplicateEntriesAreNeverEmpty() = withStore { path, owner ->
        val store = DesktopWorktreeChatArchiveStore(path)
        val json = Json { encodeDefaults = true }
        val snapshot = json.encodeToString(ManagedWorktreeChatArchiveSnapshot(owner, 1, listOf(key)))
        val duplicate = json.encodeToString(ManagedWorktreeChatArchiveSnapshot(owner, 1, listOf(key, key)))
        val texts = listOf("{}", "{\"schemaVersion\":2,\"owners\":[]}",
            "{\"schemaVersion\":1,\"owners\":[],\"extra\":true}",
            "{\"schemaVersion\":1,\"schemaVersion\":1,\"owners\":[]}",
            "{\"schemaVersion\":1,\"owners\":[$snapshot,$snapshot]}",
            "{\"schemaVersion\":1,\"owners\":[$duplicate]}",
            "{\"schemaVersion\":1,\"owners\":[" + snapshot.replace("\"revision\":1", "\"revision\":-1") + "]}",
            "{\"schemaVersion\":1,\"owners\":[" + snapshot.replace("\"archived\":", "\"archived\":[],\"archived\":") + "]}",
            "{\"schemaVersion\":1,\"owners\":[" + snapshot.replace("\"revision\":1,", "") + "]}")
        for (text in texts) {
            val bytes = text.toByteArray()
            Files.write(path, bytes)
            assertFailsWith<WorktreeChatArchiveStoreException> { store.read(owner) }
            assertContentEquals(bytes, Files.readAllBytes(path))
        }
        Files.write(path, ByteArray(4 * 1024 * 1024 + 1))
        assertFailsWith<WorktreeChatArchiveStoreException> { store.read(owner) }
    }

    @Test
    fun headerKeysRemainDistinctAndInvalidKeysRejected() = withStore { path, owner ->
        val store = DesktopWorktreeChatArchiveStore(path)
        val empty = store.read(owner)
        val all = store.compareAndSet(empty, empty.snapshot.copy(revision = 1, archived = listOf(key, key.copy(id = "other"))))
        assertEquals(all.snapshot, DesktopWorktreeChatArchiveStore(path).read(owner).snapshot)
        val bytes = Files.readAllBytes(path)
        for (invalid in listOf(key.copy(id = " "), key.copy(id = "bad\u0000"),
            key.copy(headerFingerprint = "f".repeat(63)), key.copy(headerFingerprint = "G".repeat(64)))) {
            assertFailsWith<IllegalArgumentException> {
                store.compareAndSet(all, all.snapshot.copy(revision = 2, archived = listOf(invalid)))
            }
            assertContentEquals(bytes, Files.readAllBytes(path))
        }
        for (invalid in listOf(owner.copy(homeNamespace = "relative"),
            owner.copy(homeNamespace = path.parent.toString() + "/../home"))) {
            assertFailsWith<IllegalArgumentException> { store.read(invalid) }
            assertContentEquals(bytes, Files.readAllBytes(path))
        }
    }

    private fun withStore(block: (Path, ManagedWorktreeOwner) -> Unit) {
        val home = Files.createTempDirectory("worktree-chat-archive-store-").toRealPath()
        try { block(home.resolve("archive.json"), ManagedWorktreeOwner(null, HostId("local"), home.toString())) }
        finally { assertTrue(home.toFile().deleteRecursively()) }
    }

    companion object {
        private val key = ChatArchiveKey("chat", "a".repeat(64))
    }
}
