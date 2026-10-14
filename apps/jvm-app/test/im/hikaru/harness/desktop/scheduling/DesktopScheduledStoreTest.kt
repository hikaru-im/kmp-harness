package im.hikaru.harness.desktop.scheduling

import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.harness.client.account.BackendTenant
import im.hikaru.harness.client.account.MemberIdentity
import im.hikaru.harness.client.connection.SessionId
import im.hikaru.harness.client.scheduling.*
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitAll
import kotlinx.serialization.json.*
import kotlin.test.*

class DesktopScheduledStoreTest {
    private val owner = ScheduledOwner(HostId("local-test"), null)
    private val model = ScheduledModelRef("test-provider", "test-model")
    private val target = ScheduledTarget.Cron(ScheduledDestination.Projectless)
    private val json = Json { encodeDefaults = true }
    private fun task(id: String = "task-1") = ScheduledTask(id, 1, "name", "prompt", target,
        ScheduledRule.Daily(9, 0, "UTC"), model)
    private fun initial(owner: ScheduledOwner = this.owner) = ScheduledSnapshot(owner, 1, listOf(task()))

    @Test fun realAtomicStoreReloadsPausedCrudAcrossNewInstances() = runBlocking {
        temporary { path ->
            val repository = ScheduledRepository(DesktopScheduledStore(path), ScheduleEngine(DesktopScheduleCalendar()))
            val scope = ScheduledScope(owner.hostId, owner.memberIdentity, 1)
            val context = ScheduledValidationContext(listOf(target), listOf(model))
            repository.bind(scope, context)
            repository.load()
            assertFalse(Files.exists(path))
            repository.beginCreate(target)
            repository.updateDraft { it.copy(name = "created", prompt = "test prompt", model = model) }
            assertTrue(repository.save())
            val saved = repository.state.value.tasks.single()
            assertEquals(ScheduledTaskStatus.Paused, saved.status)
            repository.beginEdit(saved.id)
            repository.updateDraft { it.copy(name = "edited") }
            assertTrue(repository.save())
            val loaded = assertIs<ScheduledReadResult.Ready>(DesktopScheduledStore(path).read(owner)).snapshot
            assertEquals("edited", loaded.tasks.single().name)
            assertEquals(2L, loaded.revision)
            assertTrue(repository.delete(saved.id))
            val deleted = assertIs<ScheduledReadResult.Ready>(DesktopScheduledStore(path).read(owner)).snapshot
            assertTrue(deleted.tasks.isEmpty())
            assertEquals(listOf(saved.id), deleted.deletedTaskIds)
            assertTrue(Files.exists(path))
        }
    }

    @Test fun memberAndHostOwnersAreRetainedWithoutPersistingGeneration() = runBlocking {
        temporary { path ->
            val member = MemberIdentity(BackendTenant("https://example.test", 1), 7)
            val owners = listOf(owner, owner.copy(memberIdentity = member), ScheduledOwner(HostId("other-test"), null))
            owners.forEach { current ->
                val store = DesktopScheduledStore(path)
                assertEquals(ScheduledReadResult.Missing, store.read(current))
                assertIs<ScheduledWriteResult.Committed>(store.compareAndSet(current, 0, initial(current)) { true })
            }
            owners.forEach { current ->
                assertEquals(initial(current), assertIs<ScheduledReadResult.Ready>(DesktopScheduledStore(path).read(current)).snapshot)
            }
            assertFalse(Files.readString(path).contains("generation"))
        }
    }

    @Test fun malformedUnknownVersionAndUnknownFieldsRemainByteIdentical() = runBlocking {
        val valid = document(initial())
        val invalid = listOf(
            "not json", "{}", "[]",
            "{\"schemaVersion\":99,\"owners\":[]}",
            "{\"schemaVersion\":\"1\",\"owners\":[]}",
            "{\"schemaVersion\":1,\"owners\":[],\"future\":true}",
            valid.replace("daily", "custom_rrule"),
            valid.replace("UTC", "Missing/Zone"),
            valid.replace("\"prompt\":\"prompt\"", "\"prompt\":\"prompt\",\"future\":true"),
        )
        invalid.forEach { content ->
            temporary { path ->
                val bytes = content.toByteArray()
                Files.write(path, bytes)
                val store = DesktopScheduledStore(path)
                val read = store.read(owner)
                assertTrue(read is ScheduledReadResult.Invalid || read is ScheduledReadResult.UnsupportedVersion, content)
                assertIs<ScheduledWriteResult.Failed>(store.compareAndSet(owner, 0, initial()) { true })
                assertContentEquals(bytes, Files.readAllBytes(path))
            }
        }
    }

    @Test fun malformedUtf8AndInvalidOtherOwnerBlockWholeDocumentWrites() = runBlocking {
        temporary { path ->
            val bytes = byteArrayOf(0xc3.toByte(), 0x28)
            Files.write(path, bytes)
            val store = DesktopScheduledStore(path)
            assertIs<ScheduledReadResult.Invalid>(store.read(owner))
            assertIs<ScheduledWriteResult.Failed>(store.compareAndSet(owner, 0, initial()) { true })
            assertContentEquals(bytes, Files.readAllBytes(path))
        }
        temporary { path ->
            val bad = initial(owner.copy(hostId = HostId("other-test"))).copy(tasks = listOf(task().copy(revision = 3)))
            val content = document(initial(), bad)
            Files.writeString(path, content)
            val store = DesktopScheduledStore(path)
            assertIs<ScheduledReadResult.Invalid>(store.read(owner))
            assertIs<ScheduledWriteResult.Failed>(store.compareAndSet(owner, 1, initial().copy(revision = 2)) { true })
            assertEquals(content, Files.readString(path))
        }
    }

    @Test fun externalEditStaleScopeAndWriterLockCannotReplaceExistingDocument() = runBlocking {
        temporary { path ->
            val store = DesktopScheduledStore(path)
            store.read(owner)
            assertIs<ScheduledWriteResult.Committed>(store.compareAndSet(owner, 0, initial()) { true })
            val external = Files.readString(path) + "\n"
            Files.writeString(path, external)
            val next = initial().copy(revision = 2, tasks = listOf(task().copy(revision = 2, name = "next")))
            assertIs<ScheduledWriteResult.Failed>(store.compareAndSet(owner, 1, next) { true })
            assertEquals(external, Files.readString(path))
            store.read(owner)
            assertEquals(ScheduledWriteResult.StaleScope, store.compareAndSet(owner, 1, next) { false })
            assertEquals(external, Files.readString(path))
            FileChannel.open(path.resolveSibling(path.fileName.toString() + ".lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE).use { channel ->
                channel.lock().use {
                    assertIs<ScheduledWriteResult.Failed>(store.compareAndSet(owner, 1, next) { true })
                    assertEquals(external, Files.readString(path))
                }
            }
            assertIs<ScheduledWriteResult.Committed>(store.compareAndSet(owner, 1, next) { true })
        }
    }

    @Test fun unreadStoreActivationAndTombstoneResurrectionAreRejected() = runBlocking {
        temporary { path ->
            val store = DesktopScheduledStore(path)
            assertIs<ScheduledWriteResult.Failed>(store.compareAndSet(owner, 0, initial()) { true })
            store.read(owner)
            assertIs<ScheduledWriteResult.Failed>(store.compareAndSet(owner, 0,
                initial().copy(tasks = listOf(task().copy(status = ScheduledTaskStatus.Active)))) { true })
            assertFalse(Files.exists(path))
            assertIs<ScheduledWriteResult.Committed>(store.compareAndSet(owner, 0, initial()) { true })
            val deleted = ScheduledSnapshot(owner, 2, emptyList(), listOf("task-1"))
            assertIs<ScheduledWriteResult.Committed>(store.compareAndSet(owner, 1, deleted) { true })
            val bytes = Files.readAllBytes(path)
            assertIs<ScheduledWriteResult.Failed>(store.compareAndSet(owner, 2, initial().copy(revision = 3)) { true })
            assertContentEquals(bytes, Files.readAllBytes(path))
        }
    }

    @Test fun activeConfigurationCanBePausedAndPersistedWithoutRunningAnything() = runBlocking {
        temporary { path ->
            Files.writeString(path, document(initial().copy(
                tasks = listOf(task().copy(status = ScheduledTaskStatus.Active)),
                cursors = listOf(ScheduledCursor("task-1", 1, 1_000)),
            )))
            val repository = ScheduledRepository(DesktopScheduledStore(path), ScheduleEngine(DesktopScheduleCalendar()))
            repository.bind(ScheduledScope(owner.hostId, null, 1), ScheduledValidationContext(listOf(target), listOf(model)))
            repository.load()
            assertFalse(repository.runNow("task-1"))
            assertTrue(repository.setPaused("task-1", true))
            val persisted = assertIs<ScheduledReadResult.Ready>(DesktopScheduledStore(path).read(owner)).snapshot.tasks.single()
            assertEquals(ScheduledTaskStatus.Paused, persisted.status)
            assertNotNull(persisted.updatedAt)
        }
    }

    @Test fun originalV1ReadDoesNotRewriteAndNextCommitMigratesToV3() = runBlocking {
        temporary { path ->
            Files.writeString(path, desktopScheduledStoreV1Fixture)
            val store = DesktopScheduledStore(path)
            assertEquals(initial(), assertIs<ScheduledReadResult.Ready>(store.read(owner)).snapshot)
            assertEquals(desktopScheduledStoreV1Fixture, Files.readString(path))
            val next = initial().copy(revision = 2, tasks = listOf(task().copy(revision = 2, name = "migrated")))
            assertIs<ScheduledWriteResult.Committed>(store.compareAndSet(owner, 1, next) { true })
            assertEquals(3, Json.parseToJsonElement(Files.readString(path)).jsonObject["schemaVersion"]!!.jsonPrimitive.int)
            assertEquals(next, assertIs<ScheduledReadResult.Ready>(DesktopScheduledStore(path).read(owner)).snapshot)
        }
    }

    @Test fun taskTimestampIsOptionalInV2AndRequiredShapeInV3Writes() = runBlocking {
        temporary { path ->
            // A v2 document has no task timestamp; it reads as null and is not rewritten on read.
            Files.writeString(path, document(initial()))
            val store = DesktopScheduledStore(path)
            val v2 = assertIs<ScheduledReadResult.Ready>(store.read(owner)).snapshot
            assertNull(v2.tasks.single().updatedAt)
            assertEquals(document(initial()), Files.readString(path))
            // The next commit upgrades the document to v3 and persists the recorded write time.
            val stamped = task().copy(revision = 2, updatedAt = 1_700_000_000_000L)
            val next = ScheduledSnapshot(owner, 2, listOf(stamped))
            assertIs<ScheduledWriteResult.Committed>(store.compareAndSet(owner, 1, next) { true })
            val written = Json.parseToJsonElement(Files.readString(path)).jsonObject
            assertEquals(3, written["schemaVersion"]!!.jsonPrimitive.int)
            assertEquals(1_700_000_000_000L, assertIs<ScheduledReadResult.Ready>(DesktopScheduledStore(path).read(owner))
                .snapshot.tasks.single().updatedAt)
        }
    }

    @Test fun v1CannotSmuggleLedgerOrActiveExecution() = runBlocking {
        listOf(
            desktopScheduledStoreV1Fixture.replace("\"deletedTaskIds\": []", "\"deletedTaskIds\": [], \"runs\": []"),
            desktopScheduledStoreV1Fixture.replace("\"Paused\"", "\"Active\""),
        ).forEach { content ->
            temporary { path ->
                Files.writeString(path, content)
                val store = DesktopScheduledStore(path)
                assertIs<ScheduledReadResult.Invalid>(store.read(owner))
                assertIs<ScheduledWriteResult.Failed>(store.compareAndSet(owner, 1, initial().copy(revision = 2)) { true })
                assertEquals(content, Files.readString(path))
            }
        }
    }

    @Test fun competingReadersCommitExactlyOnceThenRequireReread() = runBlocking {
        temporary { path ->
            val stores = List(2) { DesktopScheduledStore(path) }
            stores.forEach { assertEquals(ScheduledReadResult.Missing, it.read(owner)) }
            val gate = CompletableDeferred<Unit>()
            val results = coroutineScope {
                val writers = stores.map { store -> async {
                    gate.await()
                    store.compareAndSet(owner, 0, initial()) { true }
                } }
                gate.complete(Unit)
                writers.awaitAll()
            }
            assertEquals(1, results.count { it is ScheduledWriteResult.Committed })
            assertEquals(1, results.count { it is ScheduledWriteResult.Failed })
            val loser = stores[results.indexOfFirst { it is ScheduledWriteResult.Failed }]
            assertEquals(initial(), assertIs<ScheduledReadResult.Ready>(loser.read(owner)).snapshot)
            assertIs<ScheduledWriteResult.Committed>(loser.compareAndSet(owner, 1, initial().copy(revision = 2)) { true })
        }
    }

    @Test fun ownerAndRevisionCasRejectWithoutReplacingBytes() = runBlocking {
        temporary { path ->
            val store = DesktopScheduledStore(path)
            store.read(owner)
            assertIs<ScheduledWriteResult.Committed>(store.compareAndSet(owner, 0, initial()) { true })
            val bytes = Files.readAllBytes(path)
            val next = initial().copy(revision = 2)
            assertIs<ScheduledWriteResult.Failed>(store.compareAndSet(owner, 1, next.copy(owner = owner.copy(hostId = HostId("foreign")))) { true })
            assertIs<ScheduledWriteResult.Failed>(store.compareAndSet(owner, 1, next.copy(revision = 3)) { true })
            assertIs<ScheduledWriteResult.Failed>(store.compareAndSet(owner, 0, initial()) { true })
            assertContentEquals(bytes, Files.readAllBytes(path))
        }
    }

    @Test fun scopeLostAfterTemporaryFsyncPreservesOriginalAndCleansTemporaryFile() = runBlocking {
        temporary { path ->
            val store = DesktopScheduledStore(path)
            store.read(owner)
            assertIs<ScheduledWriteResult.Committed>(store.compareAndSet(owner, 0, initial()) { true })
            val bytes = Files.readAllBytes(path)
            var checks = 0
            assertEquals(ScheduledWriteResult.StaleScope,
                store.compareAndSet(owner, 1, initial().copy(revision = 2)) { ++checks == 1 })
            assertEquals(2, checks)
            assertContentEquals(bytes, Files.readAllBytes(path))
            assertNoTemporaryFiles(path)
            assertIs<ScheduledWriteResult.Committed>(store.compareAndSet(owner, 1, initial().copy(revision = 2)) { true })
        }
    }

    @Test fun externalEditImmediatelyBeforeAtomicMoveIsPreserved() = runBlocking {
        temporary { path ->
            val store = DesktopScheduledStore(path)
            store.read(owner)
            assertIs<ScheduledWriteResult.Committed>(store.compareAndSet(owner, 0, initial()) { true })
            val external = Files.readString(path) + "\n"
            var checks = 0
            assertIs<ScheduledWriteResult.Failed>(store.compareAndSet(owner, 1, initial().copy(revision = 2)) {
                if (++checks == 2) Files.writeString(path, external)
                true
            })
            assertEquals(2, checks)
            assertEquals(external, Files.readString(path))
            assertNoTemporaryFiles(path)
        }
    }

    @Test fun ledgerPersistsExactDeliveryAndTerminalAcrossReloadAndTaskDeletion() = runBlocking {
        temporary { path ->
            val store = DesktopScheduledStore(path)
            store.read(owner)
            var snapshot = initial()
            suspend fun commit(next: ScheduledSnapshot) {
                assertIs<ScheduledWriteResult.Committed>(store.compareAndSet(owner, snapshot.revision, next) { true })
                snapshot = next
                assertEquals(next, assertIs<ScheduledReadResult.Ready>(DesktopScheduledStore(path).read(owner)).snapshot)
            }
            assertIs<ScheduledWriteResult.Committed>(store.compareAndSet(owner, 0, snapshot) { true })
            val active = task().copy(revision = 2, status = ScheduledTaskStatus.Active)
            commit(snapshot.copy(revision = 2, tasks = listOf(active), cursors = listOf(ScheduledCursor(active.id, 2, 1_000))))
            var run = ScheduledRun("run-1", active, "request-1", 1_000, 1_000, SessionId("candidate-1"), "input-1")
            commit(snapshot.copy(revision = 3, runs = listOf(run), cursors = listOf(ScheduledCursor(active.id, 2, 2_000, 1_000))))
            for (phase in listOf(ScheduledRunPhase.CreatingSession, ScheduledRunPhase.SessionCreated,
                ScheduledRunPhase.Dispatching, ScheduledRunPhase.Accepted)) {
                run = run.copy(phase = phase,
                    sessionRef = if (phase == ScheduledRunPhase.CreatingSession) null else run.candidateSessionId,
                    deliveryIntent = phase in listOf(ScheduledRunPhase.Dispatching, ScheduledRunPhase.Accepted),
                    receiptId = if (phase == ScheduledRunPhase.Accepted) "inbox-1" else null)
                commit(snapshot.copy(revision = snapshot.revision + 1, runs = listOf(run)))
            }
            val terminal = ScheduledTerminalEvidence(1, 2, 8, 1_100, ScheduledRunPhase.Succeeded, "completed")
            run = run.copy(phase = ScheduledRunPhase.Succeeded, terminal = terminal)
            commit(snapshot.copy(revision = snapshot.revision + 1, runs = listOf(run)))
            commit(snapshot.copy(revision = snapshot.revision + 1, tasks = emptyList(), cursors = emptyList(), deletedTaskIds = listOf(active.id)))
            assertEquals(active, snapshot.runs.single().task)
            assertEquals("inbox-1", snapshot.runs.single().receiptId)
            val bytes = Files.readAllBytes(path)
            assertIs<ScheduledWriteResult.Failed>(store.compareAndSet(owner, snapshot.revision,
                snapshot.copy(revision = snapshot.revision + 1, runs = listOf(run.copy(terminal = terminal.copy(endedAt = 1_200))))) { true })
            assertContentEquals(bytes, Files.readAllBytes(path))
        }
    }

    @Test fun claimedLedgerCannotLoseIntentOverlapOrJumpToSuccess() = runBlocking {
        temporary { path ->
            val store = DesktopScheduledStore(path)
            store.read(owner)
            assertIs<ScheduledWriteResult.Committed>(store.compareAndSet(owner, 0, initial()) { true })
            val run = ScheduledRun("run-1", task(), "request-1", null, 1_000, SessionId("candidate-1"), "input-1")
            val claimed = initial().copy(revision = 2, runs = listOf(run))
            assertIs<ScheduledWriteResult.Committed>(store.compareAndSet(owner, 1, claimed) { true })
            val invalid = listOf(emptyList(), listOf(run.copy(inputMessageId = "changed")),
                listOf(run.copy(candidateSessionId = SessionId("changed"))),
                listOf(run, run.copy(id = "run-2", requestId = "request-2", candidateSessionId = SessionId("candidate-2"), inputMessageId = "input-2")),
                listOf(run.copy(phase = ScheduledRunPhase.Accepted, sessionRef = run.candidateSessionId, receiptId = "inbox", deliveryIntent = true)),
                listOf(run.copy(phase = ScheduledRunPhase.Succeeded)))
            val bytes = Files.readAllBytes(path)
            invalid.forEach { runs ->
                assertIs<ScheduledWriteResult.Failed>(store.compareAndSet(owner, 2, claimed.copy(revision = 3, runs = runs)) { true })
                assertContentEquals(bytes, Files.readAllBytes(path))
            }
        }
    }

    private fun assertNoTemporaryFiles(path: Path) {
        Files.list(path.parent).use { paths ->
            assertFalse(paths.anyMatch { it.fileName.toString().startsWith(".scheduled-") })
        }
    }

    private fun document(vararg snapshots: ScheduledSnapshot): String = buildJsonObject {
        put("schemaVersion", 2)
        put("owners", JsonArray(snapshots.map { json.encodeToJsonElement(ScheduledSnapshot.serializer(), it) }))
    }.toString()

    private suspend fun temporary(block: suspend (Path) -> Unit) {
        val directory = Files.createTempDirectory("harness-scheduling-test-")
        try { block(directory.resolve("tasks.json")) }
        finally { Files.walk(directory).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) } } }
    }
}
