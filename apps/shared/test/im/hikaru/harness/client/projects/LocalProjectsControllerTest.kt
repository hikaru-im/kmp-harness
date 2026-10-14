package im.hikaru.harness.client.projects

import im.hikaru.contracts.common.ApiResult
import im.hikaru.contracts.harness.identity.HostDescription
import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.contracts.harness.protocol.ProtocolVersion
import im.hikaru.harness.client.connection.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class LocalProjectsControllerTest {
    @Test fun unavailableEmptyAndRelayAreDistinct() = runTest {
        val owner = binding()
        val unavailable = LocalProjectsController(backgroundScope)
        unavailable.bind(owner)
        assertEquals(LocalProjectsPhase.Unavailable, unavailable.state.value.phase)
        assertFalse(unavailable.state.value.canRefresh)
        assertIs<LocalProjectFailure.Unavailable>(unavailable.state.value.failure)
        var reads = 0
        val repository = FixtureRepository().apply { read = { reads++; LocalProjectsResult.Ready(LocalProjectSnapshot(it.owner)) } }
        val controller = LocalProjectsController(backgroundScope, repository)
        controller.bind(owner.copy(transport = ConnectionTransport.Relay))
        runCurrent()
        assertEquals(0, reads)
        assertEquals(LocalProjectUnavailableReason.LocalOnly, assertIs<LocalProjectFailure.Unavailable>(controller.state.value.failure).reason)
        controller.bind(owner)
        assertEquals(LocalProjectsPhase.Loading, controller.state.value.phase)
        runCurrent()
        assertEquals(LocalProjectsPhase.Empty, controller.state.value.phase)
        assertEquals(1, reads)
        controller.bind(null)
        assertEquals(LocalProjectsPhase.Unbound, controller.state.value.phase)
        assertNull(controller.state.value.snapshot)
    }

    @Test fun everyBindingDimensionFencesNonCooperativeRead() = runTest {
        val original = binding()
        val replacements = listOf(original.copy(connection = EqualConnection()),
            original.copy(scope = original.scope.copy(generation = 2)),
            original.copy(scope = original.scope.copy(hostId = HostId("other"))), null)
        for (replacement in replacements) {
            val gate = CompletableDeferred<Unit>()
            var reads = 0
            val repository = FixtureRepository().apply { read = { owner ->
                val first = ++reads == 1
                if (first) withContext(NonCancellable) { gate.await() }
                LocalProjectsResult.Ready(snapshot(owner, if (first) "secret" else "current"))
            } }
            val controller = LocalProjectsController(backgroundScope, repository)
            controller.bind(original); runCurrent()
            controller.bind(replacement); runCurrent()
            gate.complete(Unit); runCurrent()
            assertTrue(controller.isBoundTo(replacement))
            assertTrue(controller.state.value.projects.none { it.name == "secret" })
            controller.close()
        }
    }

    @Test fun latestReadAndMutationRevisionFenceOldResponses() = runTest {
        val owner = binding()
        val late = CompletableDeferred<Unit>()
        var reads = 0
        val repository = FixtureRepository().apply {
            read = { reads++; if (reads == 2) withContext(NonCancellable) { late.await() }; LocalProjectsResult.Ready(snapshot(it)) }
            createAction = { b, _ -> LocalProjectWriteResult.Committed(snapshot(b).copy(revision = 2), LocalProjectId("a")) }
        }
        val controller = LocalProjectsController(backgroundScope, repository)
        controller.bind(owner); runCurrent()
        controller.refresh(); runCurrent()
        controller.create(CreateLocalProject("stable", "a", "/a")); runCurrent()
        late.complete(Unit); runCurrent()
        assertEquals(2, controller.state.value.snapshot?.revision)
        assertEquals(LocalProjectsPhase.Ready, controller.state.value.phase)
        assertNull(controller.state.value.pendingCreate)
    }

    @Test fun ambiguousCreateRetainsExactRequestAcrossErrorAndCancellation() = runTest {
        val owner = binding()
        val request = CreateLocalProject("stable", "a", "/a")
        val attempts = mutableListOf<CreateLocalProject>()
        val repository = FixtureRepository().apply { createAction = { b, r ->
            attempts += r
            when (attempts.size) {
                1 -> LocalProjectWriteResult.Failed(LocalProjectFailure.Error("ambiguous"))
                2 -> throw CancellationException("ambiguous cancelled return")
                else -> LocalProjectWriteResult.Committed(snapshot(b), LocalProjectId("a"), replayed = true)
            }
        } }
        val controller = LocalProjectsController(backgroundScope, repository)
        controller.bind(owner); runCurrent()
        controller.create(request); runCurrent()
        assertEquals(request, controller.state.value.pendingCreate)
        assertNull(controller.create(request.copy(requestId = "unsafe-new-id")))
        controller.retryCreate(); runCurrent()
        assertEquals(request, controller.state.value.pendingCreate)
        controller.retryCreate(); runCurrent()
        assertEquals(listOf(request, request, request), attempts)
        assertNull(controller.state.value.pendingCreate)
    }

    @Test fun rootPreparationFencesSelectionAndCapturedAttachQueuesBehindMutation() = runTest {
        val owner = binding()
        val rootGate = CompletableDeferred<Unit>()
        val writeGate = CompletableDeferred<Unit>()
        val attached = mutableListOf<LocalProjectId>()
        val repository = FixtureRepository().apply {
            read = { LocalProjectsResult.Ready(snapshot(it, "a", "b")) }
            root = { b, id, r -> rootGate.await(); LocalProjectRootResult.Ready(ValidatedLocalProjectTarget(b, id, r)) }
            replace = { b, id -> writeGate.await(); LocalProjectWriteResult.Committed(snapshot(b, "a", "b").copy(revision = 1), id) }
            attach = { target, _ -> attached += target.projectId; LocalProjectWriteResult.Committed(snapshot(target.binding, "a", "b").copy(revision = 2), target.projectId) }
        }
        val controller = LocalProjectsController(backgroundScope, repository)
        controller.bind(owner); runCurrent(); controller.select(LocalProjectId("a"))
        val pendingRoot = async { controller.prepareSelectedRoot() }; runCurrent()
        controller.select(LocalProjectId("b")); rootGate.complete(Unit); runCurrent()
        assertEquals(LocalProjectFailure.StaleBinding, assertIs<LocalProjectRootResult.Failed>(pendingRoot.await()).failure)
        controller.select(LocalProjectId("a"))
        val target = assertIs<LocalProjectRootResult.Ready>(controller.prepareSelectedRoot()).target
        controller.bindDirectory(LocalProjectId("b"), "/b"); runCurrent()
        controller.select(LocalProjectId("b"))
        assertNotNull(controller.attachCreatedSession(target, SessionSummary(SessionId("real"), 1, "/a")))
        runCurrent(); assertTrue(attached.isEmpty())
        writeGate.complete(Unit); runCurrent()
        assertEquals(listOf(LocalProjectId("a")), attached)
        controller.bind(null); controller.bind(owner); runCurrent()
        assertNull(controller.attachCreatedSession(target, SessionSummary(SessionId("late"), 2, "/a")))
        assertEquals(1, attached.size)
    }

    @Test fun createdSessionRevalidationUsesOriginalProjectAndRootAfterSameOwnerReconnect() = runTest {
        val owner = binding()
        val replacements = listOf(
            owner.copy(scope = owner.scope.copy(generation = 2)),
            owner.copy(connection = EqualConnection()),
            owner.copy(connection = EqualConnection(), scope = owner.scope.copy(generation = 2)),
        )
        for (replacement in replacements) {
            val validations = mutableListOf<Triple<LocalProjectBinding, LocalProjectId, LocalProjectRoot>>()
            val attached = mutableListOf<Pair<ValidatedLocalProjectTarget, SessionSummary>>()
            val repository = FixtureRepository().apply {
                read = { LocalProjectsResult.Ready(snapshot(it, "a", "b")) }
                attach = { target, session ->
                    attached += target to session
                    LocalProjectWriteResult.Committed(snapshot(target.binding, "a", "b").copy(
                        revision = 1, memberships = listOf(LocalProjectMembership(target.projectId, session)),
                    ), target.projectId)
                }
            }
            val controller = LocalProjectsController(backgroundScope, repository)
            controller.bind(owner); runCurrent(); controller.select(LocalProjectId("a"))
            val original = assertIs<LocalProjectRootResult.Ready>(controller.prepareSelectedRoot()).target
            repository.read = { b ->
                val current = snapshot(b, "a", "b")
                LocalProjectsResult.Ready(current.copy(projects = current.projects.map { project ->
                    if (project.id == original.projectId) project.copy(roots = listOf(LocalProjectRoot("/a-rebound"))) else project
                }))
            }
            controller.bind(replacement); runCurrent(); controller.select(LocalProjectId("b"))
            val pickerTarget = assertIs<LocalProjectRootResult.Ready>(controller.prepareSelectedRoot()).target
            assertNotEquals(original.controllerEpoch, pickerTarget.controllerEpoch)
            assertEquals(LocalProjectRoot("/a-rebound"), controller.state.value.snapshot?.project(original.projectId)?.roots?.single())
            repository.root = { b, id, root ->
                validations += Triple(b, id, root)
                LocalProjectRootResult.Ready(ValidatedLocalProjectTarget(b, id, root, original.controllerEpoch))
            }
            val session = SessionSummary(SessionId("created"), 1, original.root.path)
            assertNull(controller.attachCreatedSession(original, session))
            assertNull(controller.attachCreatedSession(original.copy(binding = replacement), session))
            assertTrue(attached.isEmpty())

            val revalidated = assertIs<LocalProjectRootResult.Ready>(controller.revalidateCreatedSessionTarget(original)).target
            assertEquals(listOf(Triple(replacement, original.projectId, original.root)), validations)
            assertSame(replacement.connection, revalidated.binding.connection)
            assertEquals(original.copy(binding = replacement, controllerEpoch = pickerTarget.controllerEpoch), revalidated)
            assertNotNull(revalidated.controllerEpoch)
            assertEquals(LocalProjectId("b"), controller.state.value.selectedProjectId)
            assertNotNull(controller.attachCreatedSession(revalidated, session))
            runCurrent()
            assertEquals(listOf(revalidated to session), attached)
            assertEquals(listOf(LocalProjectMembership(original.projectId, session)), controller.state.value.snapshot?.memberships)
            assertNull(controller.state.value.mutationFailure)
            controller.close()
        }
    }

    @Test fun createdSessionRevalidationRejectsDifferentOwnerOrTransportBeforeRepositoryCall() = runTest {
        val owner = binding()
        val replacements = listOf(
            owner.copy(scope = owner.scope.copy(hostId = HostId("other"))),
            owner.copy(transport = ConnectionTransport.Relay),
        )
        for (replacement in replacements) {
            val repository = FixtureRepository()
            val controller = LocalProjectsController(backgroundScope, repository)
            controller.bind(owner); runCurrent(); controller.select(LocalProjectId("a"))
            val original = assertIs<LocalProjectRootResult.Ready>(controller.prepareSelectedRoot()).target
            var validations = 0
            repository.root = { b, id, root ->
                validations++
                LocalProjectRootResult.Ready(ValidatedLocalProjectTarget(b, id, root))
            }
            controller.bind(replacement); runCurrent()
            val result = controller.revalidateCreatedSessionTarget(original)
            assertEquals(LocalProjectFailure.StaleBinding, assertIs<LocalProjectRootResult.Failed>(result).failure)
            assertEquals(0, validations)
            assertNull(controller.attachCreatedSession(original, SessionSummary(SessionId("foreign"), 1, "/a")))
            controller.close()
        }
    }

    @Test fun createdSessionRevalidationDiscardsNonCooperativeResponseAfterRebind() = runTest {
        val owner = binding()
        val reconnect = owner.copy(connection = EqualConnection(), scope = owner.scope.copy(generation = 2))
        val replacements = listOf(
            reconnect.copy(connection = EqualConnection()),
            reconnect.copy(scope = reconnect.scope.copy(generation = 3)),
            reconnect,
        )
        for (replacement in replacements) {
            val repository = FixtureRepository()
            val controller = LocalProjectsController(backgroundScope, repository)
            controller.bind(owner); runCurrent(); controller.select(LocalProjectId("a"))
            val original = assertIs<LocalProjectRootResult.Ready>(controller.prepareSelectedRoot()).target
            controller.bind(reconnect); runCurrent()
            val started = CompletableDeferred<Unit>()
            val release = CompletableDeferred<Unit>()
            var returned = false
            repository.root = { b, id, root ->
                assertSame(reconnect.connection, b.connection)
                assertEquals(reconnect.scope, b.scope)
                assertEquals(original.projectId, id)
                assertEquals(original.root, root)
                started.complete(Unit)
                withContext(NonCancellable) { release.await() }
                returned = true
                LocalProjectRootResult.Ready(ValidatedLocalProjectTarget(b, id, root))
            }
            val pending = async { controller.revalidateCreatedSessionTarget(original) }
            runCurrent()
            assertTrue(started.isCompleted)
            assertFalse(pending.isCompleted)
            if (replacement === reconnect) controller.bind(null)
            controller.bind(replacement); runCurrent()
            val reboundState = controller.state.value
            release.complete(Unit); runCurrent()
            assertTrue(returned)
            assertFalse(pending.isCancelled)
            assertEquals(LocalProjectFailure.StaleBinding, assertIs<LocalProjectRootResult.Failed>(pending.await()).failure)
            assertEquals(reboundState, controller.state.value)
            assertTrue(controller.isBoundTo(replacement))
            controller.close()
        }
    }

    @Test fun createdSessionRevalidationRejectsMismatchedProjectOrRootInResponse() = runTest {
        val owner = binding()
        val repository = FixtureRepository().apply { read = { LocalProjectsResult.Ready(snapshot(it, "a", "b")) } }
        val controller = LocalProjectsController(backgroundScope, repository)
        controller.bind(owner); runCurrent(); controller.select(LocalProjectId("a"))
        val original = assertIs<LocalProjectRootResult.Ready>(controller.prepareSelectedRoot()).target
        val reconnect = owner.copy(connection = EqualConnection(), scope = owner.scope.copy(generation = 2))
        controller.bind(reconnect); runCurrent(); controller.select(LocalProjectId("b"))
        val responses = listOf(
            original.copy(binding = reconnect, projectId = LocalProjectId("b")),
            original.copy(binding = reconnect, root = LocalProjectRoot("/b")),
        )
        val validations = mutableListOf<Triple<LocalProjectBinding, LocalProjectId, LocalProjectRoot>>()
        for (response in responses) {
            repository.root = { b, id, root ->
                validations += Triple(b, id, root)
                LocalProjectRootResult.Ready(response)
            }
            val result = controller.revalidateCreatedSessionTarget(original)
            assertIs<LocalProjectFailure.Rejected>(assertIs<LocalProjectRootResult.Failed>(result).failure)
            assertEquals(LocalProjectId("b"), controller.state.value.selectedProjectId)
        }
        assertEquals(List(responses.size) { Triple(reconnect, original.projectId, original.root) }, validations)
        controller.close()
    }

    @Test fun failedReadHidesOldRowsAndSearchUsesOnlyLoadedData() = runTest {
        val repository = FixtureRepository()
        val controller = LocalProjectsController(backgroundScope, repository)
        controller.bind(binding()); runCurrent()
        controller.setQuery("/a"); assertEquals(1, controller.state.value.visibleProjects.size)
        for (failure in listOf(LocalProjectFailure.Error("read error"), LocalProjectFailure.UnsupportedVersion(9), LocalProjectFailure.AccessDenied("denied"))) {
            repository.read = { LocalProjectsResult.Failed(failure) }
            controller.refresh(); runCurrent()
            assertEquals(failure, controller.state.value.failure)
            assertTrue(controller.state.value.visibleProjects.isEmpty())
            assertTrue(controller.state.value.canRefresh)
        }
    }

    private fun binding() = LocalProjectBinding(EqualConnection(), LocalProjectScope(HostId("local"), 1), ConnectionTransport.Local)
    private fun snapshot(binding: LocalProjectBinding, vararg names: String = arrayOf("a")) = LocalProjectSnapshot(binding.owner,
        projects = names.map { LocalProject(LocalProjectId(it), it, binding.owner, listOf(LocalProjectRoot("/" + it))) })

    private inner class FixtureRepository : LocalProjectRepository {
        var read: suspend (LocalProjectBinding) -> LocalProjectsResult = { LocalProjectsResult.Ready(snapshot(it)) }
        var createAction: suspend (LocalProjectBinding, CreateLocalProject) -> LocalProjectWriteResult = { _, _ -> error("unused") }
        var replace: suspend (LocalProjectBinding, LocalProjectId) -> LocalProjectWriteResult = { _, _ -> error("unused") }
        var root: suspend (LocalProjectBinding, LocalProjectId, LocalProjectRoot) -> LocalProjectRootResult = { b, id, r -> LocalProjectRootResult.Ready(ValidatedLocalProjectTarget(b, id, r)) }
        var attach: suspend (ValidatedLocalProjectTarget, SessionSummary) -> LocalProjectWriteResult = { _, _ -> error("unused") }
        override suspend fun list(binding: LocalProjectBinding, isCurrent: () -> Boolean) = read(binding)
        override suspend fun create(binding: LocalProjectBinding, request: CreateLocalProject, isCurrent: () -> Boolean) = createAction(binding, request)
        override suspend fun bindDirectory(binding: LocalProjectBinding, id: LocalProjectId, directory: String, isCurrent: () -> Boolean) = replace(binding, id)
        override suspend fun validateRoot(binding: LocalProjectBinding, id: LocalProjectId, root: LocalProjectRoot, isCurrent: () -> Boolean) = this.root(binding, id, root)
        override suspend fun attachSession(target: ValidatedLocalProjectTarget, session: SessionSummary, isCurrent: () -> Boolean) = attach(target, session)
    }

    private class EqualConnection : Connection {
        override val host = object : HostApi {
            override suspend fun describe() = ApiResult(ApiResult.SUCCESS_CODE, data = HostDescription(ProtocolVersion.Current, HostId("local"), "Local"))
        }
        override val session = object : SessionApi {
            override suspend fun list(): List<SessionSummary> = error("controller must use repository")
            override suspend fun create(id: SessionId?, options: CreateSessionOptions, agentOptions: AgentOptions): SessionSummary = error("no session creation")
            override suspend fun history(id: SessionId): List<Message> = error("no history")
            override suspend fun prompt(id: SessionId, message: Message): SessionPrompt = error("no prompt")
            override suspend fun cancel(id: SessionId, keepInbox: Boolean) = error("no cancellation")
        }
        override fun equals(other: Any?) = other is EqualConnection
        override fun hashCode() = 0
    }
}
