package im.hikaru.harness.desktop.projects

import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.harness.client.projects.*
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class DesktopLocalProjectStoreTest {
    @Test fun missingIsConfirmedEmptyAndAtomicRegistrationReloads() = runTest {
        val directory = Files.createTempDirectory("local-project-store-").toRealPath()
        try {
            val path = directory.resolve("projects.json")
            val store = DesktopLocalProjectStore(path)
            val initial = assertIs<DesktopLocalProjectReadResult.Ready>(store.read(owner))
            assertEquals(LocalProjectSnapshot(owner), initial.snapshot)
            assertFalse(Files.exists(path))
            val snapshot = snapshot(directory)
            assertIs<DesktopLocalProjectStoreWriteResult.Committed>(store.compareAndSet(initial.token, snapshot))
            assertEquals(snapshot, assertIs<DesktopLocalProjectReadResult.Ready>(DesktopLocalProjectStore(path).read(owner)).snapshot)
            assertTrue(Files.readString(path).contains("schemaVersion"))
        } finally { directory.toFile().deleteRecursively() }
    }

    @Test fun perReadTokensByteCasAndExternalEditsPreserveExistingBytes() = runTest {
        val directory = Files.createTempDirectory("local-project-cas-").toRealPath()
        try {
            val path = directory.resolve("projects.json")
            val store = DesktopLocalProjectStore(path)
            val first = assertIs<DesktopLocalProjectReadResult.Ready>(store.read(owner))
            val concurrent = assertIs<DesktopLocalProjectReadResult.Ready>(DesktopLocalProjectStore(path).read(owner))
            assertIs<DesktopLocalProjectStoreWriteResult.Committed>(store.compareAndSet(first.token, snapshot(directory)))
            val committed = Files.readAllBytes(path)
            assertIs<LocalProjectFailure.Conflict>(assertIs<DesktopLocalProjectStoreWriteResult.Failed>(store.compareAndSet(concurrent.token, snapshot(directory))).failure)
            assertContentEquals(committed, Files.readAllBytes(path))
            val read = assertIs<DesktopLocalProjectReadResult.Ready>(store.read(owner))
            val external = committed + byteArrayOf(10)
            Files.write(path, external)
            assertIs<LocalProjectFailure.Conflict>(assertIs<DesktopLocalProjectStoreWriteResult.Failed>(store.compareAndSet(read.token, read.snapshot.copy(revision = 2))).failure)
            assertContentEquals(external, Files.readAllBytes(path))
        } finally { directory.toFile().deleteRecursively() }
    }

    @Test fun corruptionUnsupportedVersionAndIoErrorsNeverBecomeEmpty() = runTest {
        val directory = Files.createTempDirectory("local-project-invalid-").toRealPath()
        try {
            val path = directory.resolve("projects.json")
            val store = DesktopLocalProjectStore(path)
            for (text in listOf("broken json", """{"schemaVersion":1,"owners":[],"unknown":true}""", """{"schemaVersion":1,"owners":null}""")) {
                Files.writeString(path, text)
                assertIs<LocalProjectFailure.Error>(assertIs<DesktopLocalProjectReadResult.Failed>(store.read(owner)).failure)
                assertEquals(text, Files.readString(path))
            }
            Files.writeString(path, """{"schemaVersion":99,"owners":[]}""")
            assertEquals(LocalProjectFailure.UnsupportedVersion(99), assertIs<DesktopLocalProjectReadResult.Failed>(store.read(owner)).failure)
            assertIs<DesktopLocalProjectReadResult.Failed>(DesktopLocalProjectStore(directory).read(owner))
        } finally { directory.toFile().deleteRecursively() }
    }

    @Test fun staleBindingLocksAndCancellationDoNotWrite() = runTest {
        val directory = Files.createTempDirectory("local-project-fence-").toRealPath()
        try {
            val path = directory.resolve("projects.json")
            val store = DesktopLocalProjectStore(path)
            val read = assertIs<DesktopLocalProjectReadResult.Ready>(store.read(owner))
            assertEquals(LocalProjectFailure.StaleBinding, assertIs<DesktopLocalProjectStoreWriteResult.Failed>(store.compareAndSet(read.token, snapshot(directory)) { false }).failure)
            assertFalse(Files.exists(path))
            FileChannel.open(directory.resolve("projects.json.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { channel ->
                channel.lock().use {
                    assertIs<LocalProjectFailure.Conflict>(assertIs<DesktopLocalProjectStoreWriteResult.Failed>(store.compareAndSet(read.token, snapshot(directory))).failure)
                }
            }
            assertFalse(Files.exists(path))
            var checks = 0
            assertEquals(LocalProjectFailure.StaleBinding, assertIs<DesktopLocalProjectStoreWriteResult.Failed>(store.compareAndSet(read.token, snapshot(directory)) { ++checks < 3 }).failure)
            assertFalse(Files.exists(path))
            val cancelled = async { store.compareAndSet(read.token, snapshot(directory)) }
            cancelled.cancel()
            assertFailsWith<CancellationException> { cancelled.await() }
            assertFalse(Files.exists(path))
        } finally { directory.toFile().deleteRecursively() }
    }

    @Test fun ownerDocumentsArePreservedAndReceiptsCannotBeErased() = runTest {
        val directory = Files.createTempDirectory("local-project-owner-").toRealPath()
        try {
            val store = DesktopLocalProjectStore(directory.resolve("projects.json"))
            val read = assertIs<DesktopLocalProjectReadResult.Ready>(store.read(owner))
            val first = snapshot(directory)
            assertIs<DesktopLocalProjectStoreWriteResult.Committed>(store.compareAndSet(read.token, first))
            val secondOwner = LocalProjectOwner(HostId("other-local"))
            val otherRead = assertIs<DesktopLocalProjectReadResult.Ready>(store.read(secondOwner))
            assertTrue(otherRead.snapshot.projects.isEmpty())
            assertIs<DesktopLocalProjectStoreWriteResult.Committed>(store.compareAndSet(otherRead.token, LocalProjectSnapshot(secondOwner, revision = 1)))
            assertEquals(first, assertIs<DesktopLocalProjectReadResult.Ready>(store.read(owner)).snapshot)
            val fresh = assertIs<DesktopLocalProjectReadResult.Ready>(store.read(owner))
            assertIs<LocalProjectFailure.Rejected>(assertIs<DesktopLocalProjectStoreWriteResult.Failed>(store.compareAndSet(fresh.token, first.copy(revision = 2, createReceipts = emptyList()))).failure)
            assertEquals(first, assertIs<DesktopLocalProjectReadResult.Ready>(store.read(owner)).snapshot)
        } finally { directory.toFile().deleteRecursively() }
    }

    private val owner = LocalProjectOwner(HostId("local"))
    private fun snapshot(root: Path): LocalProjectSnapshot {
        val id = LocalProjectId("project")
        return LocalProjectSnapshot(owner, 1,
            projects = listOf(LocalProject(id, "Real directory", owner, listOf(LocalProjectRoot(root.toString())))),
            createReceipts = listOf(LocalProjectCreateReceipt(CreateLocalProject("stable-request", "Real directory", root.toString()), id)))
    }
}
