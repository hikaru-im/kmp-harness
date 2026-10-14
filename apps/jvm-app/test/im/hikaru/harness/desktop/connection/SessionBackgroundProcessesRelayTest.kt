package im.hikaru.harness.desktop.connection

import im.hikaru.contracts.common.ApiResult
import im.hikaru.harness.agent.AgentOptions
import im.hikaru.harness.api.gateway.ApiGateway
import im.hikaru.harness.api.gateway.session.*
import im.hikaru.harness.llm.CallId
import im.hikaru.harness.llm.Message
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.session.CreateSessionOptions
import im.hikaru.harness.session.SessionId
import im.hikaru.harness.session.api.*
import im.hikaru.harness.client.connection.BackgroundOutputResult as ClientOutput
import im.hikaru.harness.client.connection.BackgroundProcessesResult as ClientProcesses
import im.hikaru.harness.client.connection.BackgroundProcessLifecycle as ClientLifecycle
import im.hikaru.harness.client.connection.BackgroundProcessId as ClientProcessId
import im.hikaru.harness.client.connection.SessionId as ClientSessionId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.*

class SessionBackgroundProcessesRelayTest {
    private val json = Json { encodeDefaults = true; explicitNulls = false; ignoreUnknownKeys = true }
    private val parent = SessionId("parent")
    private val process = BackgroundProcessId("incarnation:process")

    @Test
    fun coreResponsesRoundTripIntoClientTypesWithoutLosingProducerEvidence() = runTest {
        val owner = Context()
        val gateway = ApiGateway()
        gateway.registerSessionApi(owner)
        val metadata = BackgroundProcess(process, parent, CallId("actual-call"), """["git","status"]""",
            "/actual/cwd", 1234L, "incarnation", BackgroundProcessLifecycle.Running)
        var lifecycle: BackgroundProcessLifecycle = BackgroundProcessLifecycle.Running
        owner.provide(SessionApiKey, object : LegacyApi() {
            override suspend fun backgroundProcesses(parent: SessionId): BackgroundProcessesResult {
                assertEquals(metadata.ownerSession, parent)
                return BackgroundProcessesResult.Available(parent, listOf(metadata.copy(lifecycle = lifecycle)))
            }
            override suspend fun readBackgroundOutput(parent: SessionId, process: BackgroundProcessId): BackgroundOutputResult {
                assertEquals(metadata.ownerSession, parent)
                assertEquals(metadata.process, process)
                return BackgroundOutputResult.Available(parent, process, parent, "actual output\n终端\u0000", 42L, true, lifecycle)
            }
        })
        try {
            for ((coreState, clientState) in listOf(
                BackgroundProcessLifecycle.Running to ClientLifecycle.Running,
                BackgroundProcessLifecycle.Unknown to ClientLifecycle.Unknown,
                BackgroundProcessLifecycle.Exited(7) to ClientLifecycle.Exited(7),
            )) {
                lifecycle = coreState
                val listed = invokeSessionBackgroundProcessesRead(gateway, SessionBackgroundProcessesEndpoint.method,
                    json.encodeToJsonElement(SessionId.serializer(), parent), json)
                val output = invokeSessionBackgroundProcessesRead(gateway, SessionBackgroundOutputEndpoint.method,
                    json.encodeToJsonElement(BackgroundOutputRequest.serializer(), BackgroundOutputRequest(parent, process)), json)
                assertTrue(listed.isSuccess)
                assertTrue(output.isSuccess)
                val clientList = assertIs<ClientProcesses.Available>(json.decodeFromJsonElement(ClientProcesses.serializer(), assertNotNull(listed.data)))
                val clientProcess = clientList.processes.single()
                assertEquals(ClientSessionId(parent.value), clientList.parent)
                assertEquals(ClientSessionId(parent.value), clientProcess.ownerSession)
                assertEquals(ClientProcessId(process.value), clientProcess.process)
                assertEquals(metadata.callId.value, clientProcess.callId)
                assertEquals(metadata.command, clientProcess.command)
                assertEquals(metadata.cwd, clientProcess.cwd)
                assertEquals(metadata.startedAtEpochMillis, clientProcess.startedAtEpochMillis)
                assertEquals(metadata.producerIncarnation, clientProcess.producerIncarnation)
                assertEquals(clientState, clientProcess.lifecycle)
                assertEquals(ClientOutput.Available(ClientSessionId(parent.value), ClientProcessId(process.value),
                    ClientSessionId(parent.value), "actual output\n终端\u0000", 42L, true, clientState),
                    json.decodeFromJsonElement(ClientOutput.serializer(), assertNotNull(output.data)))
            }
        } finally {
            owner.dispose()
        }
    }

    @Test
    fun missingEndpointMissingApiAndCompatibleDefaultsStayDistinctFromAvailableEmpty() = runTest {
        val owner = Context()
        val gateway = ApiGateway()
        val parentPayload = json.encodeToJsonElement(SessionId.serializer(), parent)
        val readPayload = json.encodeToJsonElement(BackgroundOutputRequest.serializer(), BackgroundOutputRequest(parent, process))
        val missingList = invokeSessionBackgroundProcessesRead(gateway, SessionBackgroundProcessesEndpoint.method, parentPayload, json)
        val missingRead = invokeSessionBackgroundProcessesRead(gateway, SessionBackgroundOutputEndpoint.method, readPayload, json)
        assertEquals(404, missingList.code)
        assertEquals("Harness API endpoint 'session.background-processes.list' 未注册", missingList.msg)
        assertEquals(404, missingRead.code)
        assertEquals("Harness API endpoint 'session.background-processes.read' 未注册", missingRead.msg)
        gateway.registerSessionApi(owner)
        try {
            val missingApi = invokeSessionBackgroundProcessesRead(gateway, SessionBackgroundProcessesEndpoint.method, parentPayload, json)
            val missingApiRead = invokeSessionBackgroundProcessesRead(gateway, SessionBackgroundOutputEndpoint.method, readPayload, json)
            assertTrue(missingApi.isSuccess)
            assertTrue(missingApiRead.isSuccess)
            assertEquals(ClientProcesses.Unavailable(ClientSessionId(parent.value), "Session API is unavailable"),
                json.decodeFromJsonElement(ClientProcesses.serializer(), assertNotNull(missingApi.data)))
            assertEquals(ClientOutput.Unavailable(ClientSessionId(parent.value), ClientProcessId(process.value), "Session API is unavailable"),
                json.decodeFromJsonElement(ClientOutput.serializer(), assertNotNull(missingApiRead.data)))
            val legacy = owner.provide(SessionApiKey, LegacyApi())
            val compatible = invokeSessionBackgroundProcessesRead(gateway, SessionBackgroundProcessesEndpoint.method, parentPayload, json)
            val compatibleRead = invokeSessionBackgroundProcessesRead(gateway, SessionBackgroundOutputEndpoint.method, readPayload, json)
            assertEquals(ClientProcesses.Unavailable(ClientSessionId(parent.value), "Background process service is unavailable"),
                json.decodeFromJsonElement(ClientProcesses.serializer(), assertNotNull(compatible.data)))
            assertEquals(ClientOutput.Unavailable(ClientSessionId(parent.value), ClientProcessId(process.value), "Background output service is unavailable"),
                json.decodeFromJsonElement(ClientOutput.serializer(), assertNotNull(compatibleRead.data)))
            legacy.dispose()
            owner.provide(SessionApiKey, object : LegacyApi() {
                override suspend fun backgroundProcesses(parent: SessionId): BackgroundProcessesResult =
                    BackgroundProcessesResult.Available(parent, emptyList())
                override suspend fun readBackgroundOutput(parent: SessionId, process: BackgroundProcessId): BackgroundOutputResult =
                    BackgroundOutputResult.NotFound(parent, process)
            })
            val empty = invokeSessionBackgroundProcessesRead(gateway, SessionBackgroundProcessesEndpoint.method, parentPayload, json)
            val absent = invokeSessionBackgroundProcessesRead(gateway, SessionBackgroundOutputEndpoint.method, readPayload, json)
            assertTrue(empty.isSuccess)
            assertTrue(absent.isSuccess)
            assertEquals(ClientProcesses.Available(ClientSessionId(parent.value), emptyList()),
                json.decodeFromJsonElement(ClientProcesses.serializer(), assertNotNull(empty.data)))
            assertEquals(ClientOutput.NotFound(ClientSessionId(parent.value), ClientProcessId(process.value)),
                json.decodeFromJsonElement(ClientOutput.serializer(), assertNotNull(absent.data)))
        } finally {
            owner.dispose()
        }
    }

    @Test
    fun malformedRequestsAreRejectedBeforeCallingGateway() = runTest {
        val gateway = ApiGateway()
        var invoked = false
        val listRegistration = gateway.register(SessionBackgroundProcessesEndpoint) {
            invoked = true
            error("Malformed list reached gateway")
        }
        val readRegistration = gateway.register(SessionBackgroundOutputEndpoint) {
            invoked = true
            error("Malformed read reached gateway")
        }
        try {
            for (method in listOf(SessionBackgroundProcessesEndpoint.method, SessionBackgroundOutputEndpoint.method)) {
                for (payload in listOf(null, JsonObject(emptyMap()), JsonPrimitive(""))) {
                    val rejected = invokeSessionBackgroundProcessesRead(gateway, method, payload, json)
                    assertEquals(422, rejected.code)
                    assertNull(rejected.data)
                }
            }
            val blankProcess = json.parseToJsonElement("""{"parent":"parent","process":""}""")
            assertEquals(422, invokeSessionBackgroundProcessesRead(gateway, SessionBackgroundOutputEndpoint.method, blankProcess, json).code)
            assertFalse(invoked)
        } finally {
            readRegistration.dispose()
            listRegistration.dispose()
        }
    }

    @Test
    fun backendErrorsAndCancellationPropagateWithoutBecomingUnavailable() = runTest {
        val gateway = ApiGateway()
        var code = 422
        var cancellation: CancellationException? = null
        val listRegistration = gateway.register(SessionBackgroundProcessesEndpoint) {
            cancellation?.let { throw it }
            ApiResult(code, "Session operation unavailable")
        }
        val readRegistration = gateway.register(SessionBackgroundOutputEndpoint) {
            cancellation?.let { throw it }
            ApiResult(code, "Session operation unavailable")
        }
        val requests = listOf(
            SessionBackgroundProcessesEndpoint.method to json.encodeToJsonElement(SessionId.serializer(), parent),
            SessionBackgroundOutputEndpoint.method to json.encodeToJsonElement(BackgroundOutputRequest.serializer(), BackgroundOutputRequest(parent, process)),
        )
        try {
            for (failureCode in listOf(403, 404, 422, 500)) {
                code = failureCode
                for ((method, payload) in requests) {
                    val result = invokeSessionBackgroundProcessesRead(gateway, method, payload, json)
                    assertEquals(code, result.code)
                    assertEquals("Session operation unavailable", result.msg)
                    assertNull(result.data)
                }
            }
            val cancelled = CancellationException("cancelled background read")
            cancellation = cancelled
            for ((method, payload) in requests) {
                assertSame(cancelled, assertFailsWith<CancellationException> {
                    invokeSessionBackgroundProcessesRead(gateway, method, payload, json)
                })
            }
        } finally {
            readRegistration.dispose()
            listRegistration.dispose()
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
