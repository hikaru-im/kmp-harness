package im.hikaru.harness.api.gateway.session

import im.hikaru.harness.agent.AgentOptions
import im.hikaru.harness.api.gateway.ApiGateway
import im.hikaru.harness.api.gateway.GatewayResultCodes
import im.hikaru.harness.llm.CallId
import im.hikaru.harness.llm.Message
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.session.CreateSessionOptions
import im.hikaru.harness.session.SessionId
import im.hikaru.harness.session.api.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class SessionBackgroundProcessesGatewayTest {
    @Test
    fun missingApiCompatibleDefaultAndAvailableEmptyRemainDistinct() = runTest {
        val owner = Context()
        val gateway = ApiGateway()
        gateway.registerSessionApi(owner)
        val parent = SessionId("parent")
        val process = BackgroundProcessId("producer:process")
        val request = BackgroundOutputRequest(parent, process)
        try {
            val missingList = gateway.invoke(SessionBackgroundProcessesEndpoint, parent)
            val missingRead = gateway.invoke(SessionBackgroundOutputEndpoint, request)
            assertTrue(missingList.isSuccess)
            assertTrue(missingRead.isSuccess)
            assertEquals(BackgroundProcessesResult.Unavailable(parent, "Session API is unavailable"), missingList.data)
            assertEquals(BackgroundOutputResult.Unavailable(parent, process, "Session API is unavailable"), missingRead.data)

            val compatible = owner.provide(SessionApiKey, LegacyApi())
            val defaultList = gateway.invoke(SessionBackgroundProcessesEndpoint, parent)
            val defaultRead = gateway.invoke(SessionBackgroundOutputEndpoint, request)
            assertTrue(defaultList.isSuccess)
            assertTrue(defaultRead.isSuccess)
            assertEquals(BackgroundProcessesResult.Unavailable(parent, "Background process service is unavailable"), defaultList.data)
            assertEquals(BackgroundOutputResult.Unavailable(parent, process, "Background output service is unavailable"), defaultRead.data)
            compatible.dispose()

            owner.provide(SessionApiKey, object : LegacyApi() {
                override suspend fun backgroundProcesses(parent: SessionId): BackgroundProcessesResult =
                    BackgroundProcessesResult.Available(parent, emptyList())

                override suspend fun readBackgroundOutput(parent: SessionId, process: BackgroundProcessId): BackgroundOutputResult =
                    BackgroundOutputResult.Available(parent, process, parent, "", 0L, false, BackgroundProcessLifecycle.Running)
            })
            val emptyListing = gateway.invoke(SessionBackgroundProcessesEndpoint, parent)
            val emptyRead = gateway.invoke(SessionBackgroundOutputEndpoint, request)
            assertTrue(emptyListing.isSuccess)
            assertTrue(emptyRead.isSuccess)
            assertEquals(BackgroundProcessesResult.Available(parent, emptyList()), emptyListing.data)
            assertEquals(
                BackgroundOutputResult.Available(parent, process, parent, "", 0L, false, BackgroundProcessLifecycle.Running),
                emptyRead.data,
            )
        } finally {
            owner.dispose()
        }
    }

    @Test
    fun typedListAndReadPreserveProducerInformationWithoutInferringCompletion() = runTest {
        val owner = Context()
        val gateway = ApiGateway()
        gateway.registerSessionApi(owner)
        val parent = SessionId("actual-owner")
        val process = BackgroundProcessId("incarnation:actual-process")
        val lifecycles = listOf(BackgroundProcessLifecycle.Running, BackgroundProcessLifecycle.Unknown, BackgroundProcessLifecycle.Exited(17))
        val metadata = BackgroundProcess(
            process = process, ownerSession = parent, callId = CallId("actual-exec-call"),
            command = """["git","status","--porcelain"]""", cwd = "/actual/repository",
            startedAtEpochMillis = 1_791_331_200_123L, producerIncarnation = "incarnation",
            lifecycle = BackgroundProcessLifecycle.Running,
        )
        var listCalls = 0
        var readCalls = 0
        var lifecycle: BackgroundProcessLifecycle = BackgroundProcessLifecycle.Running
        owner.provide(SessionApiKey, object : LegacyApi() {
            override suspend fun backgroundProcesses(parent: SessionId): BackgroundProcessesResult {
                assertEquals(metadata.ownerSession, parent)
                listCalls++
                return BackgroundProcessesResult.Available(parent, listOf(metadata.copy(lifecycle = lifecycle)))
            }

            override suspend fun readBackgroundOutput(parent: SessionId, process: BackgroundProcessId): BackgroundOutputResult {
                assertEquals(metadata.ownerSession, parent)
                assertEquals(metadata.process, process)
                readCalls++
                return BackgroundOutputResult.Available(parent, process, parent, "actual stdout\n终端 tail\u0000", 42L, true, lifecycle)
            }
        })
        try {
            for (state in lifecycles) {
                lifecycle = state
                val listed = gateway.invoke(SessionBackgroundProcessesEndpoint, parent)
                val read = gateway.invoke(SessionBackgroundOutputEndpoint, BackgroundOutputRequest(parent, process))
                assertTrue(listed.isSuccess)
                assertTrue(read.isSuccess)
                assertEquals(BackgroundProcessesResult.Available(parent, listOf(metadata.copy(lifecycle = state))), listed.data)
                assertEquals(
                    BackgroundOutputResult.Available(parent, process, parent, "actual stdout\n终端 tail\u0000", 42L, true, state),
                    read.data,
                )
            }
            assertEquals(lifecycles.size, listCalls)
            assertEquals(lifecycles.size, readCalls)
        } finally {
            owner.dispose()
        }
    }

    @Test
    fun typedNotFoundAndUnavailableAreSuccessfulReadsWithTheirIdentitiesAndReasons() = runTest {
        val owner = Context()
        val gateway = ApiGateway()
        gateway.registerSessionApi(owner)
        val parent = SessionId("parent")
        val process = BackgroundProcessId("expired-incarnation:process")
        var output: BackgroundOutputResult = BackgroundOutputResult.NotFound(parent, process)
        val unavailable = BackgroundProcessesResult.Unavailable(parent, "Producer incarnation was disposed")
        owner.provide(SessionApiKey, object : LegacyApi() {
            override suspend fun backgroundProcesses(parent: SessionId): BackgroundProcessesResult = unavailable
            override suspend fun readBackgroundOutput(parent: SessionId, process: BackgroundProcessId): BackgroundOutputResult = output
        })
        try {
            val listed = gateway.invoke(SessionBackgroundProcessesEndpoint, parent)
            assertTrue(listed.isSuccess)
            assertEquals(unavailable, listed.data)
            val absent = gateway.invoke(SessionBackgroundOutputEndpoint, BackgroundOutputRequest(parent, process))
            assertTrue(absent.isSuccess)
            assertEquals(output, absent.data)
            output = BackgroundOutputResult.Unavailable(parent, process, "Output reader is unavailable")
            val unreadable = gateway.invoke(SessionBackgroundOutputEndpoint, BackgroundOutputRequest(parent, process))
            assertTrue(unreadable.isSuccess)
            assertEquals(output, unreadable.data)
        } finally {
            owner.dispose()
        }
    }

    @Test
    fun rejectionAndStaleIdentityRemainErrorsAndCancellationPreservesItsInstance() = runTest {
        val owner = Context()
        val gateway = ApiGateway()
        gateway.registerSessionApi(owner)
        val parent = SessionId("parent")
        val request = BackgroundOutputRequest(parent, BackgroundProcessId("producer:process"))
        var failure: Throwable = IllegalArgumentException("private rejected process owner")
        owner.provide(SessionApiKey, object : LegacyApi() {
            override suspend fun backgroundProcesses(parent: SessionId): BackgroundProcessesResult = throw failure
            override suspend fun readBackgroundOutput(parent: SessionId, process: BackgroundProcessId): BackgroundOutputResult = throw failure
        })
        try {
            for ((error, code) in listOf(
                IllegalArgumentException("private rejected process owner") to 422,
                IllegalStateException("private replaced session") to 404,
            )) {
                failure = error
                val results = listOf(
                    gateway.invoke(SessionBackgroundProcessesEndpoint, parent),
                    gateway.invoke(SessionBackgroundOutputEndpoint, request),
                )
                for (result in results) {
                    assertFalse(result.isSuccess)
                    assertEquals(code, result.code)
                    assertNull(result.data)
                    assertFalse(result.msg.orEmpty().contains("private"))
                }
            }
            failure = CancellationException("cancelled read")
            assertSame(failure, assertFailsWith<CancellationException> { gateway.invoke(SessionBackgroundProcessesEndpoint, parent) })
            assertSame(failure, assertFailsWith<CancellationException> { gateway.invoke(SessionBackgroundOutputEndpoint, request) })
        } finally {
            owner.dispose()
        }
    }

    @Test
    fun callerCancellationInterruptsSuspendedReadAndLeavesRegistrationUsable() = runTest {
        val owner = Context()
        val gateway = ApiGateway()
        gateway.registerSessionApi(owner)
        val parent = SessionId("parent")
        val process = BackgroundProcessId("producer:process")
        val entered = CompletableDeferred<Unit>()
        val never = CompletableDeferred<Unit>()
        var suspendRead = true
        owner.provide(SessionApiKey, object : LegacyApi() {
            override suspend fun readBackgroundOutput(parent: SessionId, process: BackgroundProcessId): BackgroundOutputResult {
                if (suspendRead) {
                    entered.complete(Unit)
                    never.await()
                }
                return BackgroundOutputResult.NotFound(parent, process)
            }
        })
        try {
            val pending = async { gateway.invoke(SessionBackgroundOutputEndpoint, BackgroundOutputRequest(parent, process)) }
            entered.await()
            pending.cancel(CancellationException("caller cancelled"))
            assertFailsWith<CancellationException> { pending.await() }
            pending.join()
            suspendRead = false
            val next = gateway.invoke(SessionBackgroundOutputEndpoint, BackgroundOutputRequest(parent, process))
            assertTrue(next.isSuccess)
            assertEquals(BackgroundOutputResult.NotFound(parent, process), next.data)
        } finally {
            owner.dispose()
        }
    }

    @Test
    fun explicitUnregisterAndOwnerDisposalRemoveBothBackgroundMethods() = runTest {
        for (disposeOwner in listOf(false, true)) {
            val owner = Context()
            val gateway = ApiGateway()
            val registration = gateway.registerSessionApi(owner)
            val parent = SessionId("parent")
            val request = BackgroundOutputRequest(parent, BackgroundProcessId("producer:process"))
            try {
                assertTrue(SessionBackgroundProcessesEndpoint.method in gateway.methods())
                assertTrue(SessionBackgroundOutputEndpoint.method in gateway.methods())
                if (disposeOwner) owner.dispose() else registration.dispose()
                assertFalse(SessionBackgroundProcessesEndpoint.method in gateway.methods())
                assertFalse(SessionBackgroundOutputEndpoint.method in gateway.methods())
                val listed = gateway.invoke(SessionBackgroundProcessesEndpoint, parent)
                val read = gateway.invoke(SessionBackgroundOutputEndpoint, request)
                assertEquals(GatewayResultCodes.NotFound, listed.code)
                assertEquals(GatewayResultCodes.NotFound, read.code)
                assertNull(listed.data)
                assertNull(read.data)
                registration.dispose()
            } finally {
                owner.dispose()
            }
        }
    }

    private open class LegacyApi : SessionApi {
        override suspend fun list(): List<SessionSummary> = error("Unexpected session list")
        override suspend fun create(id: SessionId?, options: CreateSessionOptions, agentOptions: AgentOptions): SessionSummary = error("Unexpected create")
        override suspend fun history(id: SessionId): List<Message> = error("Unexpected history")
        override suspend fun prompt(id: SessionId, message: Message): SessionPrompt = error("Unexpected prompt")
        override suspend fun cancel(id: SessionId, keepInbox: Boolean) = error("Unexpected cancel")
    }
}
