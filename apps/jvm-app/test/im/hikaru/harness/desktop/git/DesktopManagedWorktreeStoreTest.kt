package im.hikaru.harness.desktop.git

import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.harness.client.account.BackendTenant
import im.hikaru.harness.client.account.MemberIdentity
import im.hikaru.harness.client.git.*
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.*
import kotlin.test.*

class DesktopManagedWorktreeStoreTest {
    @Test
    fun completeByteCasPreservesExternalWhitespaceAndOtherOwnerEdits() = withStore { path, owner ->
        val store = DesktopManagedWorktreeStore(path)
        val initial = store.read(owner)
        val committed = store.compareAndSet(initial, initial.snapshot.copy(revision = 1))
        val competingBytes = Files.readAllBytes(path) + "\n ".toByteArray()
        Files.write(path, competingBytes)
        assertFailsWith<ManagedWorktreeStoreException> {
            store.compareAndSet(committed, committed.snapshot.copy(revision = 2))
        }
        assertFailsWith<ManagedWorktreeStoreException> { store.requireCurrent(committed) }
        assertContentEquals(competingBytes, Files.readAllBytes(path))
        val fresh = store.read(owner)
        val other = owner.copy(identity = MemberIdentity(BackendTenant("https://member.example", 1), 1))
        val observedOther = store.read(other)
        store.compareAndSet(observedOther, observedOther.snapshot.copy(revision = 1))
        val otherBytes = Files.readAllBytes(path)
        assertFailsWith<ManagedWorktreeStoreException> {
            store.compareAndSet(fresh, fresh.snapshot.copy(revision = 2))
        }
        assertContentEquals(otherBytes, Files.readAllBytes(path))
    }

    @Test
    fun restartPreservesFullIdentityHostHomeAndAnonymousPartitions() = withStore { path, anonymous ->
        val member = MemberIdentity(BackendTenant("https://member.example", 1), 1)
        val owners = listOf(anonymous, anonymous.copy(identity = member),
            anonymous.copy(identity = member.copy(userId = 2)),
            anonymous.copy(identity = member.copy(target = BackendTenant("https://member.example", 2))),
            anonymous.copy(hostId = HostId("another-host")),
            anonymous.copy(homeNamespace = path.parent.resolve("another-home").toString()))
        val store = DesktopManagedWorktreeStore(path)
        owners.forEachIndexed { index, owner ->
            val observed = store.read(owner)
            val intent = intent(path.parent, "operation-" + index)
            store.compareAndSet(observed, observed.snapshot.copy(revision = 1, intents = listOf(intent)))
        }
        val restarted = DesktopManagedWorktreeStore(path)
        owners.forEachIndexed { index, owner ->
            val snapshot = restarted.read(owner).snapshot
            assertEquals(owner, snapshot.owner)
            assertEquals(1L, snapshot.revision)
            assertEquals("operation-" + index, snapshot.intents.single().operationId)
        }
    }

    @Test
    fun realOsLeaseContenderCannotReadOrWriteUntilReleased() = withStore { path, owner ->
        val store = DesktopManagedWorktreeStore(path)
        val observed = store.read(owner)
        FileChannel.open(path.resolveSibling(path.fileName.toString() + ".lock"), CREATE, WRITE).use { channel ->
            channel.lock().use {
                assertFailsWith<ManagedWorktreeStoreException> { store.read(owner) }
                assertFailsWith<ManagedWorktreeStoreException> {
                    store.compareAndSet(observed, observed.snapshot.copy(revision = 1))
                }
                assertFalse(Files.exists(path))
            }
        }
        assertEquals(0L, store.read(owner).snapshot.revision)
    }

    @Test
    fun invalidSchemaDuplicateKeysAndMissingEvidenceAreNeverEmptySuccess() = withStore { path, owner ->
        val store = DesktopManagedWorktreeStore(path)
        for (text in listOf("{}", "{\"schemaVersion\":2,\"owners\":[]}",
            "{\"schemaVersion\":1,\"owners\":[],\"unexpected\":true}",
            "{\"schemaVersion\":1,\"schemaVersion\":1,\"owners\":[]}",
            "{\"schemaVersion\":1,\"schemaVer\\u0073ion\":1,\"owners\":[]}")) {
            val bytes = text.toByteArray()
            Files.write(path, bytes)
            assertFailsWith<ManagedWorktreeStoreException> { store.read(owner) }
            assertContentEquals(bytes, Files.readAllBytes(path))
        }
        Files.delete(path)
        val observed = store.read(owner)
        assertFailsWith<IllegalArgumentException> {
            store.compareAndSet(observed, observed.snapshot.copy(revision = 1,
                intents = listOf(intent(path.parent, "missing-evidence").copy(phase = ManagedRegistrationPhase.Registered))))
        }
        assertFalse(Files.exists(path))
    }

    @Test
    fun postRenameDirectorySyncFailureReturnsCommittedWarningAndRestartReadsRealBytes() = withStore { path, owner ->
        val store = DesktopManagedWorktreeStore(path) { throw IllegalStateException("directory sync failed") }
        val observed = store.read(owner)
        val committed = store.compareAndSet(observed, observed.snapshot.copy(revision = 1,
            intents = listOf(intent(path.parent, "committed"))))
        assertNotNull(committed.durabilityWarning)
        assertEquals(1L, committed.snapshot.revision)
        store.requireCurrent(committed)
        assertEquals(committed.snapshot, DesktopManagedWorktreeStore(path).read(owner).snapshot)
        Files.list(path.parent).use { entries ->
            assertFalse(entries.anyMatch { it.fileName.toString().endsWith(".pending") })
        }
    }

    private fun intent(home: Path, id: String) = ManagedWorktreeIntent(id,
        home.resolve("source/.git").toString(), home.resolve("source").toString(),
        home.resolve("source/.git").toString(), home.resolve("target").toString(), "codex/test", "HEAD", 1)

    private fun withStore(block: (Path, ManagedWorktreeOwner) -> Unit) {
        val home = Files.createTempDirectory("harness-managed-store-").toRealPath()
        try { block(home.resolve("managed.json"), ManagedWorktreeOwner(null, HostId("managed-local"), home.toString())) }
        finally { home.toFile().deleteRecursively() }
    }
}
