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
import im.hikaru.harness.client.connection.BackgroundInputResult as ClientInput
import im.hikaru.harness.client.connection.BackgroundControlResult as ClientControl
import im.hikaru.harness.client.connection.BackgroundInputState as ClientInputState
import im.hikaru.harness.client.connection.BackgroundProcessesResult as ClientProcesses
import im.hikaru.harness.client.connection.BackgroundOutputResult as ClientOutput
import im.hikaru.harness.client.connection.BackgroundProcessId as ClientProcessId
import im.hikaru.harness.client.connection.SessionId as ClientSessionId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class SessionBackgroundMutationsTransportTest {
    private val json = Json { encodeDefaults = true; explicitNulls = false; ignoreUnknownKeys = true }
    private val parent = SessionId("parent")
    private val process = BackgroundProcessId("incarnation:process")
    private val clientParent = ClientSessionId(parent.value)
    private val clientProcess = ClientProcessId(process.value)
    private val input = "你好🙂\n\u0000"
    private val methods = listOf(SessionBackgroundInputEndpoint.method, SessionBackgroundCloseInputEndpoint.method, SessionBackgroundCancelEndpoint.method)

    @Test
    fun localAndRelayDistinguishMissingEndpointsMissingApiAndLegacyUnavailable() = runTest {
        val owner = Context()
        val gateway = ApiGateway()
        val local = LocalConnection(gateway)
        suspend fun checkUnavailable(missingEndpoint: Boolean) {
            assertIs<ClientInput.Unavailable>(local.session.writeBackgroundInput(clientParent, clientProcess, input))
            assertIs<ClientControl.Unavailable>(local.session.closeBackgroundInput(clientParent, clientProcess))
            assertIs<ClientControl.Unavailable>(local.session.cancelBackgroundProcess(clientParent, clientProcess))
            for (method in methods) {
                val result = relay(gateway, method)
                if (missingEndpoint) {
                    assertEquals(404, result.code)
                    assertEquals("Harness API endpoint '$method' 未注册", result.msg)
                    assertNull(result.data)
                } else {
                    assertTrue(result.isSuccess)
                    if (method == methods.first()) assertIs<ClientInput.Unavailable>(decodeInput(result))
                    else assertIs<ClientControl.Unavailable>(decodeControl(result))
                }
            }
        }
        try {
            checkUnavailable(true)
            val registration = gateway.registerSessionApi(owner)
            checkUnavailable(false)
            owner.provide(SessionApiKey, LegacyApi())
            checkUnavailable(false)
            registration.dispose()
            checkUnavailable(true)
        } finally { owner.dispose() }
    }

    @Test
    fun localMappingAndCoreClientWireRoundTripAllResultsWithExactArguments() = runTest {
        val owner = Context()
        val gateway = ApiGateway()
        gateway.registerSessionApi(owner)
        val local = LocalConnection(gateway)
        var writeResult: BackgroundInputResult = BackgroundInputResult.NotFound(parent, process)
        var controlResult: BackgroundControlResult = BackgroundControlResult.NotFound(parent, process)
        val calls = mutableListOf<String>()
        owner.provide(SessionApiKey, object : LegacyApi() {
            override suspend fun writeBackgroundInput(parent: SessionId, process: BackgroundProcessId, input: String): BackgroundInputResult {
                assertEquals(this@SessionBackgroundMutationsTransportTest.parent, parent)
                assertEquals(this@SessionBackgroundMutationsTransportTest.process, process)
                assertEquals(this@SessionBackgroundMutationsTransportTest.input, input)
                calls += methods[0]; return writeResult
            }
            override suspend fun closeBackgroundInput(parent: SessionId, process: BackgroundProcessId): BackgroundControlResult {
                assertEquals(BackgroundControlRequest(this@SessionBackgroundMutationsTransportTest.parent, this@SessionBackgroundMutationsTransportTest.process), BackgroundControlRequest(parent, process))
                calls += methods[1]; return controlResult
            }
            override suspend fun cancelBackgroundProcess(parent: SessionId, process: BackgroundProcessId): BackgroundControlResult {
                assertEquals(BackgroundControlRequest(this@SessionBackgroundMutationsTransportTest.parent, this@SessionBackgroundMutationsTransportTest.process), BackgroundControlRequest(parent, process))
                calls += methods[2]; return controlResult
            }
        })
        try {
            for (result in inputs(parent, process)) {
                writeResult = result
                val expected = json.decodeFromJsonElement(ClientInput.serializer(), json.encodeToJsonElement(BackgroundInputResult.serializer(), result))
                assertEquals(expected, result.toClient())
                assertEquals(expected, local.session.writeBackgroundInput(clientParent, clientProcess, input))
                assertEquals(expected, decodeInput(relay(gateway, methods[0])))
                assertEquals(result, json.decodeFromJsonElement(BackgroundInputResult.serializer(), json.encodeToJsonElement(ClientInput.serializer(), expected)))
            }
            for (result in controls(parent, process)) {
                controlResult = result
                val expected = json.decodeFromJsonElement(ClientControl.serializer(), json.encodeToJsonElement(BackgroundControlResult.serializer(), result))
                assertEquals(expected, result.toClient())
                assertEquals(expected, local.session.closeBackgroundInput(clientParent, clientProcess))
                assertEquals(expected, local.session.cancelBackgroundProcess(clientParent, clientProcess))
                assertEquals(expected, decodeControl(relay(gateway, methods[1])))
                assertEquals(expected, decodeControl(relay(gateway, methods[2])))
                assertEquals(result, json.decodeFromJsonElement(BackgroundControlResult.serializer(), json.encodeToJsonElement(ClientControl.serializer(), expected)))
            }
            assertEquals(10, calls.count { it == methods[0] })
            assertEquals(10, calls.count { it == methods[1] })
            assertEquals(10, calls.count { it == methods[2] })
        } finally { owner.dispose() }
    }

    @Test
    fun localAndRelayFenceAllVariantsEvenWithCustomGatewayHandlers() = runTest {
        val gateway = ApiGateway()
        val local = LocalConnection(gateway)
        var writeResult: BackgroundInputResult = BackgroundInputResult.NotFound(parent, process)
        var controlResult: BackgroundControlResult = BackgroundControlResult.NotFound(parent, process)
        var calls = 0
        val registrations = listOf(
            gateway.register(SessionBackgroundInputEndpoint) { calls++; ApiResult(ApiResult.SUCCESS_CODE, "", writeResult) },
            gateway.register(SessionBackgroundCloseInputEndpoint) { calls++; ApiResult(ApiResult.SUCCESS_CODE, "", controlResult) },
            gateway.register(SessionBackgroundCancelEndpoint) { calls++; ApiResult(ApiResult.SUCCESS_CODE, "", controlResult) },
        )
        try {
            for ((wrongParent, wrongProcess) in listOf(SessionId("foreign") to process, parent to BackgroundProcessId("other:process"))) {
                for (result in inputs(wrongParent, wrongProcess)) {
                    writeResult = result
                    checkUnknown(local.session.writeBackgroundInput(clientParent, clientProcess, input))
                    checkUnknown(decodeInput(relay(gateway, methods[0])))
                }
                for (result in controls(wrongParent, wrongProcess)) {
                    controlResult = result
                    checkUnknown(local.session.closeBackgroundInput(clientParent, clientProcess))
                    checkUnknown(local.session.cancelBackgroundProcess(clientParent, clientProcess))
                    for (method in methods.drop(1)) checkUnknown(decodeControl(relay(gateway, method)))
                }
            }
            assertEquals(60, calls)
        } finally { registrations.asReversed().forEach { it.dispose() } }
    }

    @Test
    fun malformedWireRequestsNeverReachMutationHandlers() = runTest {
        val gateway = ApiGateway()
        var calls = 0
        val registrations = listOf(
            gateway.register(SessionBackgroundInputEndpoint) { calls++; error("Malformed input reached gateway") },
            gateway.register(SessionBackgroundCloseInputEndpoint) { calls++; error("Malformed EOF reached gateway") },
            gateway.register(SessionBackgroundCancelEndpoint) { calls++; error("Malformed cancel reached gateway") },
        )
        try {
            val malformed = listOf(null, JsonNull, JsonObject(emptyMap()), JsonPrimitive(""),
                json.parseToJsonElement("""{"parent":"","process":"incarnation:process","input":"hello"}"""),
                json.parseToJsonElement("""{"parent":"parent","process":"","input":"hello"}"""))
            for (method in methods) for (payload in malformed) {
                val result = invokeSessionBackgroundProcessesMutation(gateway, method, payload, json)
                assertEquals(422, result.code); assertNull(result.data)
            }
            val noInput = json.parseToJsonElement("""{"parent":"parent","process":"incarnation:process"}""")
            assertEquals(422, invokeSessionBackgroundProcessesMutation(gateway, methods[0], noInput, json).code)
            assertEquals(0, calls)
        } finally { registrations.asReversed().forEach { it.dispose() } }
    }

    @Test
    fun gatewayErrorsNullSuccessAndServiceCancellationHaveUnknownOutcomesWithoutRetry() = runTest {
        val gateway = ApiGateway()
        val local = LocalConnection(gateway)
        var code = 500
        var cancellation: CancellationException? = null
        var calls = 0
        val registrations = listOf(
            gateway.register(SessionBackgroundInputEndpoint) { calls++; cancellation?.let { throw it }; ApiResult(code, "private error") },
            gateway.register(SessionBackgroundCloseInputEndpoint) { calls++; cancellation?.let { throw it }; ApiResult(code, "private error") },
            gateway.register(SessionBackgroundCancelEndpoint) { calls++; cancellation?.let { throw it }; ApiResult(code, "private error") },
        )
        suspend fun checkAll() {
            checkUnknown(local.session.writeBackgroundInput(clientParent, clientProcess, input))
            checkUnknown(local.session.closeBackgroundInput(clientParent, clientProcess))
            checkUnknown(local.session.cancelBackgroundProcess(clientParent, clientProcess))
            checkUnknown(decodeInput(relay(gateway, methods[0])))
            for (method in methods.drop(1)) checkUnknown(decodeControl(relay(gateway, method)))
        }
        try {
            for (failureCode in listOf(403, 404, 422, 500, ApiResult.SUCCESS_CODE)) { code = failureCode; checkAll() }
            cancellation = CancellationException("private service cancellation"); checkAll()
            assertEquals(36, calls)
        } finally { registrations.asReversed().forEach { it.dispose() } }
    }

    @Test
    fun actualCallerCancellationPropagatesThroughLocalAndRelayForAllMethods() = runTest {
        val gateway = ApiGateway()
        val local = LocalConnection(gateway)
        var entered = CompletableDeferred<Unit>()
        var calls = 0
        suspend fun waitForever(): Nothing { calls++; entered.complete(Unit); awaitCancellation() }
        val registrations = listOf(
            gateway.register(SessionBackgroundInputEndpoint) { waitForever() },
            gateway.register(SessionBackgroundCloseInputEndpoint) { waitForever() },
            gateway.register(SessionBackgroundCancelEndpoint) { waitForever() },
        )
        val actions: List<suspend () -> Unit> = listOf<suspend () -> Unit>(
            { local.session.writeBackgroundInput(clientParent, clientProcess, input); Unit },
            { local.session.closeBackgroundInput(clientParent, clientProcess); Unit },
            { local.session.cancelBackgroundProcess(clientParent, clientProcess); Unit },
        ) + methods.map { method -> suspend { relay(gateway, method); Unit } }
        try {
            for (action in actions) {
                entered = CompletableDeferred()
                val pending = async { action() }
                entered.await(); pending.cancel()
                assertFailsWith<CancellationException> { pending.await() }
                pending.join()
            }
            assertEquals(6, calls)
        } finally { registrations.asReversed().forEach { it.dispose() } }
    }

    @Test
    fun everyInputStateSurvivesListOutputMappingAndCoreWireSerialization() {
        for (state in BackgroundInputState.entries) {
            val clientState = ClientInputState.valueOf(state.name)
            val metadata = BackgroundProcess(process, parent, CallId("call"), "command", "/cwd", 0, "incarnation", BackgroundProcessLifecycle.Running, state)
            val listing: BackgroundProcessesResult = BackgroundProcessesResult.Available(parent, listOf(metadata))
            val output: BackgroundOutputResult = BackgroundOutputResult.Available(parent, process, parent, "", 0, false, BackgroundProcessLifecycle.Running, state)
            val mappedList = assertIs<ClientProcesses.Available>(listing.toClient())
            val mappedOutput = assertIs<ClientOutput.Available>(output.toClient())
            assertEquals(clientState, mappedList.processes.single().inputState)
            assertEquals(clientState, mappedOutput.inputState)
            assertEquals(mappedList, json.decodeFromJsonElement(ClientProcesses.serializer(), json.encodeToJsonElement(BackgroundProcessesResult.serializer(), listing)))
            assertEquals(mappedOutput, json.decodeFromJsonElement(ClientOutput.serializer(), json.encodeToJsonElement(BackgroundOutputResult.serializer(), output)))
        }
    }

    private suspend fun relay(gateway: ApiGateway, method: String): ApiResult<JsonElement> {
        val payload = if (method == methods[0]) json.encodeToJsonElement(BackgroundInputRequest.serializer(), BackgroundInputRequest(parent, process, input))
        else json.encodeToJsonElement(BackgroundControlRequest.serializer(), BackgroundControlRequest(parent, process))
        return invokeSessionBackgroundProcessesMutation(gateway, method, payload, json)
    }

    private fun decodeInput(result: ApiResult<JsonElement>): ClientInput {
        assertTrue(result.isSuccess)
        return json.decodeFromJsonElement(ClientInput.serializer(), assertNotNull(result.data))
    }

    private fun decodeControl(result: ApiResult<JsonElement>): ClientControl {
        assertTrue(result.isSuccess)
        return json.decodeFromJsonElement(ClientControl.serializer(), assertNotNull(result.data))
    }

    private fun checkUnknown(result: Any) {
        when (result) {
            is ClientInput.UnknownOutcome -> {
                assertEquals(clientParent, result.parent); assertEquals(clientProcess, result.process)
                assertFalse(result.reason.contains("private"))
            }
            is ClientControl.UnknownOutcome -> {
                assertEquals(clientParent, result.parent); assertEquals(clientProcess, result.process)
                assertFalse(result.reason.contains("private"))
            }
            else -> fail("Expected UnknownOutcome, got $result")
        }
    }

    private fun inputs(parent: SessionId, process: BackgroundProcessId): List<BackgroundInputResult> = listOf(
        BackgroundInputResult.Unavailable(parent, process, "unsupported"), BackgroundInputResult.NotFound(parent, process),
        BackgroundInputResult.Accepted(parent, process, 7), BackgroundInputResult.Rejected(parent, process, "closed"),
        BackgroundInputResult.UnknownOutcome(parent, process, "uncertain"))

    private fun controls(parent: SessionId, process: BackgroundProcessId): List<BackgroundControlResult> = listOf(
        BackgroundControlResult.Unavailable(parent, process, "unsupported"), BackgroundControlResult.NotFound(parent, process),
        BackgroundControlResult.Applied(parent, process), BackgroundControlResult.Rejected(parent, process, "closed"),
        BackgroundControlResult.UnknownOutcome(parent, process, "uncertain"))

    private open class LegacyApi : SessionApi {
        override suspend fun list(): List<SessionSummary> = error("Unexpected list")
        override suspend fun create(id: SessionId?, options: CreateSessionOptions, agentOptions: AgentOptions): SessionSummary = error("Unexpected create")
        override suspend fun history(id: SessionId): List<Message> = error("Unexpected history")
        override suspend fun prompt(id: SessionId, message: Message): SessionPrompt = error("Unexpected prompt")
        override suspend fun cancel(id: SessionId, keepInbox: Boolean) = error("Unexpected cancel")
    }
}
