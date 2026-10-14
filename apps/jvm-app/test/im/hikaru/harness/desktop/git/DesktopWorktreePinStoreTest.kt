package im.hikaru.harness.desktop.git

import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.harness.client.account.BackendTenant
import im.hikaru.harness.client.account.MemberIdentity
import im.hikaru.harness.client.git.*
import im.hikaru.harness.session.*
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

class DesktopWorktreePinStoreTest {
    @Test
    fun roundTripOwnersAndFreshReader() = withStore { path, owner ->
        val member = MemberIdentity(BackendTenant("https://member.example", 1), 1)
        val owners = listOf(owner, owner.copy(identity = member), owner.copy(hostId = HostId("other")),
            owner.copy(homeNamespace = path.parent.resolve("other-home").toString()),
            owner.copy(identity = member.copy(userId = 2)),
            owner.copy(identity = member.copy(target = member.target.copy(tenantId = 2))))
        val store = DesktopWorktreePinStore(path)
        owners.forEachIndexed { index, partition ->
            val read = store.read(partition)
            store.compareAndSet(read, read.snapshot.copy(revision = 1, pins = listOf(key.copy(id = "chat-$index"))))
        }
        owners.forEachIndexed { index, partition ->
            val read = DesktopWorktreePinStore(path).read(partition)
            assertEquals(partition, read.snapshot.owner); assertEquals(1L, read.snapshot.revision)
            assertEquals(listOf(key.copy(id = "chat-$index")), read.snapshot.pins)
        }
        val current = store.read(owner)
        val unpinned = store.compareAndSet(current, current.snapshot.copy(revision = 2, pins = emptyList()))
        assertEquals(unpinned.snapshot, DesktopWorktreePinStore(path).read(owner).snapshot)
        assertEquals(listOf(key.copy(id = "chat-1")), store.read(owners[1]).snapshot.pins)
    }

    @Test
    fun completeByteCasRejectsWhitespaceAndForeignOwnerDrift() = withStore { path, owner ->
        val store = DesktopWorktreePinStore(path)
        val empty = store.read(owner)
        val first = store.compareAndSet(empty, empty.snapshot.copy(revision = 1, pins = listOf(key)))
        val whitespace = Files.readAllBytes(path) + "\n ".toByteArray()
        Files.write(path, whitespace)
        assertFailsWith<WorktreePinStoreException> { store.compareAndSet(first, first.snapshot.copy(revision = 2, pins = emptyList())) }
        assertFailsWith<WorktreePinStoreException> { store.requireCurrent(first) }
        assertContentEquals(whitespace, Files.readAllBytes(path))
        val fresh = store.read(owner)
        val foreign = store.read(owner.copy(hostId = HostId("foreign")))
        store.compareAndSet(foreign, foreign.snapshot.copy(revision = 1))
        val before = Files.readAllBytes(path)
        assertFailsWith<WorktreePinStoreException> { store.compareAndSet(fresh, fresh.snapshot.copy(revision = 2)) }
        assertContentEquals(before, Files.readAllBytes(path))
        assertFailsWith<IllegalArgumentException> { store.compareAndSet(store.read(owner), fresh.snapshot) }
        assertContentEquals(before, Files.readAllBytes(path))
    }

    @Test
    fun badSchemaDuplicateKeysAndDuplicatePinsAreNeverEmpty() = withStore { path, owner ->
        val store = DesktopWorktreePinStore(path)
        val json = Json { encodeDefaults = true }
        val snapshot = json.encodeToString(ManagedWorktreePinSnapshot(owner, 1, listOf(key)))
        val duplicatePins = json.encodeToString(ManagedWorktreePinSnapshot(owner, 1, listOf(key, key)))
        val texts = listOf("{}", "{\"schemaVersion\":2,\"owners\":[]}",
            "{\"schemaVersion\":1,\"owners\":[],\"extra\":true}",
            "{\"schemaVersion\":1,\"schemaVersion\":1,\"owners\":[]}",
            "{\"schemaVersion\":1,\"schemaVer\\u0073ion\":1,\"owners\":[]}",
            "{\"schemaVersion\":1,\"owners\":[$snapshot,$snapshot]}",
            "{\"schemaVersion\":1,\"owners\":[$duplicatePins]}",
            "{\"schemaVersion\":1,\"owners\":[" + snapshot.replace("\"revision\":1", "\"revision\":-1") + "]}",
            "{\"schemaVersion\":1,\"owners\":[" + snapshot.replace("\"pins\":", "\"pins\":[],\"pins\":") + "]}",
            "{\"schemaVersion\":1,\"owners\":[" + snapshot.replace("\"revision\":1,", "") + "]}")
        for (text in texts) {
            val bytes = text.toByteArray(); Files.write(path, bytes)
            assertFailsWith<WorktreePinStoreException> { store.read(owner) }
            assertContentEquals(bytes, Files.readAllBytes(path))
        }
        Files.write(path, byteArrayOf(0xc3.toByte(), 0x28))
        assertFailsWith<WorktreePinStoreException> { store.read(owner) }
        Files.write(path, ByteArray(4 * 1024 * 1024 + 1))
        assertFailsWith<WorktreePinStoreException> { store.read(owner) }
        Files.delete(path); Files.createDirectory(path)
        assertFailsWith<WorktreePinStoreException> { store.read(owner) }
    }

    @Test
    fun fileAndLeaseSymlinksNeverMutateTargets() = withStore { path, owner ->
        val store = DesktopWorktreePinStore(path)
        val observed = store.read(owner)
        val target = path.parent.resolve("target"); val bytes = "untouched".toByteArray(); Files.write(target, bytes)
        Files.createSymbolicLink(path, target)
        assertFailsWith<WorktreePinStoreException> { store.read(owner) }
        assertFailsWith<WorktreePinStoreException> { store.compareAndSet(observed, observed.snapshot.copy(revision = 1)) }
        assertContentEquals(bytes, Files.readAllBytes(target)); assertTrue(Files.isSymbolicLink(path))
        Files.delete(path)
        val lease = path.resolveSibling(path.fileName.toString() + ".lock"); Files.delete(lease)
        Files.createSymbolicLink(lease, target)
        assertFailsWith<WorktreePinStoreException> { store.read(owner) }
        assertFailsWith<WorktreePinStoreException> { store.compareAndSet(observed, observed.snapshot.copy(revision = 1)) }
        assertContentEquals(bytes, Files.readAllBytes(target)); assertFalse(Files.exists(path))
    }

    @Test
    fun competingLeaseRejectsReadAndWrite() = withStore { path, owner ->
        val store = DesktopWorktreePinStore(path); val observed = store.read(owner)
        FileChannel.open(path.resolveSibling(path.fileName.toString() + ".lock"), CREATE, WRITE).use { channel ->
            channel.lock().use {
                assertFailsWith<WorktreePinStoreException> { store.read(owner) }
                assertFailsWith<WorktreePinStoreException> { store.compareAndSet(observed, observed.snapshot.copy(revision = 1, pins = listOf(key))) }
                assertFalse(Files.exists(path))
            }
        }
        assertEquals(0L, store.read(owner).snapshot.revision)
        val lease = path.resolveSibling(path.fileName.toString() + ".lock")
        Files.delete(lease); Files.createDirectory(lease)
        assertFailsWith<WorktreePinStoreException> { store.read(owner) }
        assertFailsWith<WorktreePinStoreException> { store.compareAndSet(observed, observed.snapshot.copy(revision = 1)) }
        assertFalse(Files.exists(path))
    }

    @Test
    fun directorySyncFailureIsCommitted() = withStore { path, owner ->
        val store = DesktopWorktreePinStore(path) { error("directory sync failed") }
        val observed = store.read(owner)
        val committed = store.compareAndSet(observed, observed.snapshot.copy(revision = 1, pins = listOf(key)))
        assertNotNull(committed.durabilityWarning); assertEquals(1L, committed.snapshot.revision)
        store.requireCurrent(committed)
        assertEquals(committed.snapshot, DesktopWorktreePinStore(path).read(owner).snapshot)
        Files.list(path.parent).use { assertFalse(it.anyMatch { entry -> entry.fileName.toString().endsWith(".pending") }) }
    }

    @Test
    fun headerKeysRemainDistinctAndInvalidKeysRejected() = withStore { path, owner ->
        val header = SessionHeader(id = SessionId("reused"), createdAt = 1, cwd = path.parent.toString())
        val first = DesktopWorktreeSessionObserver.pinKey(header)
        assertEquals(first, DesktopWorktreeSessionObserver.pinKey(header.copy(version = 9)))
        val changed = listOf(header.copy(id = SessionId("other-id")), header.copy(createdAt = 2),
            header.copy(cwd = path.parent.resolve("nested").toString()),
            header.copy(parentSession = SessionId("parent")), header.copy(seedLength = 1),
            header.copy(origin = SessionOrigin.SUBAGENT), header.copy(delegationDepth = 1), header.copy(agentPreset = "agent"))
            .map { DesktopWorktreeSessionObserver.pinKey(it) }
        assertEquals(changed.size + 1, (changed + first).distinct().size)
        val store = DesktopWorktreePinStore(path); val empty = store.read(owner)
        val all = store.compareAndSet(empty, empty.snapshot.copy(revision = 1, pins = changed + first))
        assertEquals(all.snapshot, DesktopWorktreePinStore(path).read(owner).snapshot)
        val bytes = Files.readAllBytes(path)
        for (invalid in listOf(first.copy(id = " "), first.copy(id = "bad\u0000"),
            first.copy(headerFingerprint = "f".repeat(63)), first.copy(headerFingerprint = "G".repeat(64)))) {
            assertFailsWith<IllegalArgumentException> { store.compareAndSet(all, all.snapshot.copy(revision = 2, pins = listOf(invalid))) }
            assertContentEquals(bytes, Files.readAllBytes(path))
        }
        assertFailsWith<IllegalArgumentException> { owner.copy(hostId = HostId(" ")) }
        assertContentEquals(bytes, Files.readAllBytes(path))
        for (invalid in listOf(owner.copy(homeNamespace = "relative"),
            owner.copy(homeNamespace = path.parent.toString() + "/../home"), owner.copy(homeNamespace = "//server/share"))) {
            assertFailsWith<IllegalArgumentException> { store.read(invalid) }
            assertContentEquals(bytes, Files.readAllBytes(path))
        }
    }

    private fun withStore(block: (Path, ManagedWorktreeOwner) -> Unit) {
        val home = Files.createTempDirectory("worktree-pin-store-").toRealPath()
        try { block(home.resolve("pins.json"), ManagedWorktreeOwner(null, HostId("local"), home.toString())) }
        finally { assertTrue(home.toFile().deleteRecursively()) }
    }
    companion object { private val key = PinKey("chat", "a".repeat(64)) }
}
