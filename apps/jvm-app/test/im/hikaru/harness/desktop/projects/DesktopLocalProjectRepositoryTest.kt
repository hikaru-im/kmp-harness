package im.hikaru.harness.desktop.projects

import im.hikaru.contracts.common.ApiResult
import im.hikaru.contracts.harness.identity.HostDescription
import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.contracts.harness.protocol.ProtocolVersion
import im.hikaru.harness.api.gateway.ApiGateway
import im.hikaru.harness.api.gateway.GatewayResultCodes
import im.hikaru.harness.api.gateway.host.HostDescribeEndpoint
import im.hikaru.harness.api.gateway.session.SessionListEndpoint
import im.hikaru.harness.client.connection.ConnectionTransport
import im.hikaru.harness.client.connection.SessionId
import im.hikaru.harness.client.connection.SessionSummary
import im.hikaru.harness.client.projects.*
import im.hikaru.harness.desktop.connection.LocalConnection
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class DesktopLocalProjectRepositoryTest {
    @Test fun realDirectoryValidationCanonicalizesSymlinkAndNeverCreatesMissingRoot() = runTest {
        val directory = Files.createTempDirectory("project-directory-").toRealPath()
        try {
            val root = Files.createDirectory(directory.resolve("root"))
            val alias = Files.createSymbolicLink(directory.resolve("alias"), root)
            val access = DesktopProjectDirectoryAccess()
            assertEquals(LocalProjectRoot(root.toRealPath().toString()), assertIs<DesktopProjectDirectoryResult.Ready>(access.validate(alias.toString())).root)
            val file = Files.writeString(directory.resolve("file"), "not a directory")
            for (path in listOf("relative", "~/root", directory.resolve("missing").toString(), file.toString())) {
                assertIs<LocalProjectFailure.Rejected>(assertIs<DesktopProjectDirectoryResult.Failed>(access.validate(path)).failure)
            }
            assertFalse(Files.exists(directory.resolve("missing")))
        } finally { directory.toFile().deleteRecursively() }
    }

    @Test fun registerReplayRootReplacementAndConfirmedSessionMembershipPersist() = runTest {
        val f = fixture()
        try {
            assertTrue(assertIs<LocalProjectsResult.Ready>(f.repository.list(f.binding)).snapshot.projects.isEmpty())
            val request = CreateLocalProject("stable-create", "Project", f.root.toString())
            val created = assertIs<LocalProjectWriteResult.Committed>(f.repository.create(f.binding, request))
            val replay = assertIs<LocalProjectWriteResult.Committed>(f.repository.create(f.binding, request))
            assertTrue(replay.replayed); assertEquals(created.projectId, replay.projectId)
            assertEquals(1, replay.snapshot.projects.size)
            assertIs<LocalProjectFailure.Rejected>(assertIs<LocalProjectWriteResult.Failed>(f.repository.create(f.binding, request.copy(name = "Different"))).failure)
            val target = assertIs<LocalProjectRootResult.Ready>(f.repository.validateRoot(f.binding, created.projectId, created.snapshot.projects.single().roots.single())).target
            val session = SessionSummary(SessionId("confirmed"), 123, f.root.toString(), "preset")
            f.sessions = listOf(session)
            val attached = assertIs<LocalProjectWriteResult.Committed>(f.repository.attachSession(target, session))
            assertEquals(listOf(LocalProjectMembership(created.projectId, session)), attached.snapshot.memberships)
            assertTrue(assertIs<LocalProjectWriteResult.Committed>(f.repository.attachSession(target, session)).replayed)
            val replacement = Files.createDirectory(f.directory.resolve("replacement"))
            val rebound = assertIs<LocalProjectWriteResult.Committed>(f.repository.bindDirectory(f.binding, created.projectId, replacement.toString()))
            assertEquals(LocalProjectRoot(replacement.toString()), rebound.snapshot.projects.single().roots.single())
            assertEquals(session, rebound.snapshot.memberships.single().session)
            assertIs<LocalProjectFailure.Rejected>(assertIs<LocalProjectWriteResult.Failed>(f.repository.attachSession(target, session)).failure)
            val reloaded = DesktopLocalProjectRepository(f.host, DesktopLocalProjectStore(f.file), expectedConnection = f.connection)
            assertEquals(rebound.snapshot, assertIs<LocalProjectsResult.Ready>(reloaded.list(f.binding)).snapshot)
        } finally { f.close() }
    }

    @Test fun receiptReplaySurvivesDirectoryRemovalButSendValidationFails() = runTest {
        val f = fixture()
        try {
            val request = CreateLocalProject("durable-receipt", "Project", f.root.toString())
            val created = assertIs<LocalProjectWriteResult.Committed>(f.repository.create(f.binding, request))
            Files.delete(f.root)
            assertTrue(assertIs<LocalProjectWriteResult.Committed>(f.repository.create(f.binding, request)).replayed)
            assertIs<LocalProjectFailure.Rejected>(assertIs<LocalProjectRootResult.Failed>(f.repository.validateRoot(f.binding, created.projectId, created.snapshot.projects.single().roots.single())).failure)
        } finally { f.close() }
    }

    @Test fun fakeSummaryForeignHostRelayReferenceMismatchAndStaleFenceAreRejected() = runTest {
        val f = fixture()
        try {
            val created = assertIs<LocalProjectWriteResult.Committed>(f.repository.create(f.binding, CreateLocalProject("one", "Project", f.root.toString())))
            val target = assertIs<LocalProjectRootResult.Ready>(f.repository.validateRoot(f.binding, created.projectId, created.snapshot.projects.single().roots.single())).target
            val real = SessionSummary(SessionId("session"), 5, f.root.toString(), "real-preset")
            f.sessions = listOf(real)
            for (fake in listOf(real.copy(createdAt = 6), real.copy(agentPreset = "invented"), real.copy(id = SessionId("foreign")))) {
                assertIs<LocalProjectFailure.Rejected>(assertIs<LocalProjectWriteResult.Failed>(f.repository.attachSession(target, fake)).failure)
            }
            f.sessions = listOf(real, real)
            assertIs<LocalProjectWriteResult.Failed>(f.repository.attachSession(target, real))
            val wrongCwd = real.copy(cwd = f.directory.toString())
            f.sessions = listOf(wrongCwd)
            assertIs<LocalProjectFailure.Rejected>(assertIs<LocalProjectWriteResult.Failed>(f.repository.attachSession(target, wrongCwd)).failure)
            f.sessions = emptyList()
            assertIs<LocalProjectWriteResult.Failed>(f.repository.attachSession(target, real))
            assertIs<LocalProjectFailure.Unavailable>(assertIs<LocalProjectsResult.Failed>(f.repository.list(f.binding.copy(transport = ConnectionTransport.Relay))).failure)
            assertIs<LocalProjectFailure.Unavailable>(assertIs<LocalProjectsResult.Failed>(f.repository.list(f.binding.copy(scope = LocalProjectScope(HostId("foreign"), 1)))).failure)
            assertIs<LocalProjectFailure.Unavailable>(assertIs<LocalProjectsResult.Failed>(f.repository.list(f.binding.copy(connection = LocalConnection(f.gateway)))).failure)
            val bytes = Files.readAllBytes(f.file)
            assertEquals(LocalProjectFailure.StaleBinding, assertIs<LocalProjectWriteResult.Failed>(f.repository.create(f.binding, CreateLocalProject("late", "Late", f.root.toString())) { false }).failure)
            assertContentEquals(bytes, Files.readAllBytes(f.file))
            assertTrue(assertIs<LocalProjectsResult.Ready>(f.repository.list(f.binding)).snapshot.memberships.isEmpty())
        } finally { f.close() }
    }

    @Test fun missingSessionServiceErrorAndCancellationRemainExplicit() = runTest {
        val f = fixture(registerSessionList = false)
        try {
            val created = assertIs<LocalProjectWriteResult.Committed>(f.repository.create(f.binding, CreateLocalProject("one", "Project", f.root.toString())))
            val target = ValidatedLocalProjectTarget(f.binding, created.projectId, created.snapshot.projects.single().roots.single())
            val session = SessionSummary(SessionId("unconfirmed"), 1, f.root.toString())
            assertEquals(LocalProjectUnavailableReason.SessionServiceNotExposed,
                assertIs<LocalProjectFailure.Unavailable>(assertIs<LocalProjectWriteResult.Failed>(f.repository.attachSession(target, session)).failure).reason)
            val unavailable = f.gateway.register(SessionListEndpoint) { ApiResult(GatewayResultCodes.ConfigurationError, "Harness Session API Service 未注册") }
            assertIs<LocalProjectFailure.Unavailable>(assertIs<LocalProjectWriteResult.Failed>(f.repository.attachSession(target, session)).failure)
            unavailable.dispose()
            val failed = f.gateway.register(SessionListEndpoint) { ApiResult(GatewayResultCodes.InternalError, "actual read failure") }
            assertIs<LocalProjectFailure.Error>(assertIs<LocalProjectWriteResult.Failed>(f.repository.attachSession(target, session)).failure)
            failed.dispose()
            val cancelled = f.gateway.register(SessionListEndpoint) { throw CancellationException("real cancellation") }
            assertFailsWith<CancellationException> { f.repository.attachSession(target, session) }
            cancelled.dispose()
            assertTrue(assertIs<LocalProjectsResult.Ready>(f.repository.list(f.binding)).snapshot.memberships.isEmpty())
        } finally { f.close() }
    }

    private suspend fun fixture(registerSessionList: Boolean = true): Fixture {
        val directory = Files.createTempDirectory("project-repository-").toRealPath()
        val f = Fixture(directory)
        f.gateway.register(HostDescribeEndpoint) { ApiResult(ApiResult.SUCCESS_CODE, data = HostDescription(ProtocolVersion.Current, f.host, "Real local test host")) }
        if (registerSessionList) f.gateway.register(SessionListEndpoint) {
            ApiResult(ApiResult.SUCCESS_CODE, data = f.sessions.map {
                im.hikaru.harness.session.api.SessionSummary(im.hikaru.harness.session.SessionId(it.id.value), it.createdAt, it.cwd, it.agentPreset)
            })
        }
        return f
    }

    private class Fixture(val directory: Path) {
        val root: Path = Files.createDirectory(directory.resolve("existing-root"))
        val file: Path = directory.resolve("projects.json")
        val host = HostId("local-project-host")
        val gateway = ApiGateway()
        val connection = LocalConnection(gateway)
        val binding = LocalProjectBinding(connection, LocalProjectScope(host, 1), ConnectionTransport.Local)
        val repository = DesktopLocalProjectRepository(host, DesktopLocalProjectStore(file), expectedConnection = connection)
        var sessions = emptyList<SessionSummary>()
        fun close() { directory.toFile().deleteRecursively() }
    }
}
