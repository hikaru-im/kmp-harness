package im.hikaru.harness.client.connection

import im.hikaru.contracts.common.ApiResult
import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.contracts.harness.relay.RelayResponse
import im.hikaru.contracts.harness.relay.RequestId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import kotlin.test.*

class SessionBackgroundMutationsRemoteTest {
    private val json = Json { encodeDefaults = true; explicitNulls = false; ignoreUnknownKeys = true }
    private val parent = SessionId("parent")
    private val process = BackgroundProcessId("incarnation:process")
    private val methods = listOf("session.background-processes.write", "session.background-processes.close-input", "session.background-processes.cancel")

    @Test
    fun mirroredLegacyDefaultsAreUnavailable() = runTest {
        val legacy = object : SessionApi {
            override suspend fun list(): List<SessionSummary> = error("Unexpected list")
            override suspend fun create(id: SessionId?, options: CreateSessionOptions, agentOptions: AgentOptions): SessionSummary = error("Unexpected create")
            override suspend fun history(id: SessionId): List<Message> = error("Unexpected history")
            override suspend fun prompt(id: SessionId, message: Message): SessionPrompt = error("Unexpected prompt")
            override suspend fun cancel(id: SessionId, keepInbox: Boolean) = error("Unexpected cancel")
        }
        val input = assertIs<BackgroundInputResult.Unavailable>(legacy.writeBackgroundInput(parent, process, ""))
        assertEquals(parent, input.parent); assertEquals(process, input.process)
        for (result in listOf(legacy.closeBackgroundInput(parent, process), legacy.cancelBackgroundProcess(parent, process))) {
            assertIs<BackgroundControlResult.Unavailable>(result)
            assertEquals(parent, result.parent); assertEquals(process, result.process)
        }
    }

    @Test
    fun wireCarriesExactInputAndIdentityAndPreservesEveryVariantWithOneSend() = runTest {
        var calls = 0
        var expectedMethod = methods.first()
        var expectedInput = "终端🙂\n\u0000"
        var data: JsonElement? = null
        val remote = RemoteSessionBackgroundProcesses(json) { method, payload ->
            calls++
            assertEquals(expectedMethod, method)
            if (method == methods.first()) {
                assertEquals(WireBackgroundInputRequest(parent, process, expectedInput),
                    json.decodeFromJsonElement(WireBackgroundInputRequest.serializer(), assertNotNull(payload)))
            } else {
                assertEquals(WireBackgroundControlRequest(parent, process),
                    json.decodeFromJsonElement(WireBackgroundControlRequest.serializer(), assertNotNull(payload)))
            }
            response(data)
        }
        for (result in inputs(parent, process)) {
            data = json.encodeToJsonElement(BackgroundInputResult.serializer(), result)
            assertEquals(result, remote.writeBackgroundInput(parent, process, expectedInput))
        }
        expectedInput = ""
        val empty = BackgroundInputResult.Accepted(parent, process, 0)
        data = json.encodeToJsonElement(BackgroundInputResult.serializer(), empty)
        assertEquals(empty, remote.writeBackgroundInput(parent, process, ""))
        for (method in methods.drop(1)) {
            expectedMethod = method
            for (result in controls(parent, process)) {
                data = json.encodeToJsonElement(BackgroundControlResult.serializer(), result)
                assertEquals(result, invoke(remote, method))
            }
        }
        assertEquals(16, calls)
    }

    @Test
    fun allWireVariantsRejectWrongParentOrProcessAsUnknownOutcome() = runTest {
        var calls = 0
        var data: JsonElement? = null
        val remote = RemoteSessionBackgroundProcesses(json) { _, _ -> calls++; response(data) }
        for ((wrongParent, wrongProcess) in listOf(SessionId("foreign") to process, parent to BackgroundProcessId("other:process"))) {
            for (result in inputs(wrongParent, wrongProcess)) {
                data = json.encodeToJsonElement(BackgroundInputResult.serializer(), result)
                checkUnknown(invoke(remote, methods.first()))
            }
            for (result in controls(wrongParent, wrongProcess)) {
                data = json.encodeToJsonElement(BackgroundControlResult.serializer(), result)
                for (method in methods.drop(1)) checkUnknown(invoke(remote, method))
            }
        }
        assertEquals(30, calls)
    }

    @Test
    fun onlyConfirmedMissingEndpointIsUnavailableAllOtherStatusesAreUnknown() = runTest {
        var calls = 0
        var code = 404
        var message = ""
        val remote = RemoteSessionBackgroundProcesses(json) { _, _ -> calls++; response(null, code, message) }
        for (method in methods) {
            for (missing in listOf("Harness endpoint unavailable", "Harness API endpoint '$method' 未注册")) {
                message = missing; code = 404
                val result = invoke(remote, method)
                if (method == methods.first()) assertIs<BackgroundInputResult.Unavailable>(result)
                else assertIs<BackgroundControlResult.Unavailable>(result)
            }
            for (failureCode in listOf(403, 404, 422, 429, 500)) {
                message = "private transport failure"; code = failureCode
                checkUnknown(invoke(remote, method))
            }
            code = 404; message = "Harness API endpoint 'session.other' 未注册"
            checkUnknown(invoke(remote, method))
        }
        assertEquals(24, calls)
    }

    @Test
    fun absentMalformedNegativeAndFutureAcknowledgementsAreUnknown() = runTest {
        var calls = 0
        var data: JsonElement? = null
        val remote = RemoteSessionBackgroundProcesses(json) { _, _ -> calls++; response(data) }
        val malformed = listOf(null, JsonNull, JsonObject(emptyMap()), JsonPrimitive("invalid"),
            json.parseToJsonElement("""{"type":"future-result","parent":"parent","process":"incarnation:process"}"""),
            json.parseToJsonElement("""{"type":"accepted","parent":"parent","process":"incarnation:process","bytes":-1}"""))
        for (payload in malformed) {
            data = payload
            for (method in methods) checkUnknown(invoke(remote, method))
        }
        assertEquals(18, calls)
        assertFailsWith<IllegalArgumentException> { BackgroundInputResult.Accepted(parent, process, -1) }
    }

    @Test
    fun disconnectServiceCancellationAndInvocationTimeoutAreUnknownWithoutRetry() = runTest {
        var calls = 0
        var fail: suspend () -> Nothing = { error("private transport") }
        val remote = RemoteSessionBackgroundProcesses(json) { _, _ -> calls++; fail() }
        val failures: List<suspend () -> Nothing> = listOf(
            { throw RemoteConnectionException("private connection closed") },
            { throw CancellationException("private service cancellation") },
            { withTimeout(1) { awaitCancellation() } },
        )
        for (failure in failures) {
            fail = failure
            for (method in methods) checkUnknown(invoke(remote, method))
        }
        assertEquals(9, calls)
    }

    @Test
    fun actualCallerCancellationPropagatesForAllThreeOperations() = runTest {
        var calls = 0
        var entered = CompletableDeferred<Unit>()
        val remote = RemoteSessionBackgroundProcesses(json) { _, _ ->
            calls++; entered.complete(Unit); awaitCancellation()
        }
        for (method in methods) {
            entered = CompletableDeferred()
            val pending = async { invoke(remote, method) }
            entered.await(); pending.cancel()
            assertFailsWith<CancellationException> { pending.await() }
            pending.join()
        }
        assertEquals(3, calls)
    }

    @Test
    fun oldReadPayloadsDefaultToUnsupportedAndAllInputStatesRoundTrip() {
        val metadata = BackgroundProcess(process, parent, "call", "command", "/cwd", 0, "incarnation", BackgroundProcessLifecycle.Running)
        val output = BackgroundOutputResult.Available(parent, process, parent, "", 0, false, BackgroundProcessLifecycle.Running)
        val legacyMetadata = JsonObject(json.encodeToJsonElement(BackgroundProcess.serializer(), metadata).jsonObject - "inputState")
        val legacyOutput = JsonObject(json.encodeToJsonElement(BackgroundOutputResult.serializer(), output).jsonObject - "inputState")
        assertEquals(BackgroundInputState.Unsupported, json.decodeFromJsonElement(BackgroundProcess.serializer(), legacyMetadata).inputState)
        assertEquals(BackgroundInputState.Unsupported, assertIs<BackgroundOutputResult.Available>(
            json.decodeFromJsonElement(BackgroundOutputResult.serializer(), legacyOutput)).inputState)
        for (state in BackgroundInputState.entries) {
            val processWithState = metadata.copy(inputState = state)
            val outputWithState: BackgroundOutputResult = output.copy(inputState = state)
            assertEquals(processWithState, json.decodeFromJsonElement(BackgroundProcess.serializer(), json.encodeToJsonElement(BackgroundProcess.serializer(), processWithState)))
            assertEquals(outputWithState, json.decodeFromJsonElement(BackgroundOutputResult.serializer(), json.encodeToJsonElement(BackgroundOutputResult.serializer(), outputWithState)))
        }
    }

    private suspend fun invoke(remote: RemoteSessionBackgroundProcesses, method: String): Any = when (method) {
        methods[0] -> remote.writeBackgroundInput(parent, process, "input")
        methods[1] -> remote.closeBackgroundInput(parent, process)
        methods[2] -> remote.cancelBackgroundProcess(parent, process)
        else -> error("Unexpected method")
    }

    private fun checkUnknown(result: Any) {
        when (result) {
            is BackgroundInputResult.UnknownOutcome -> {
                assertEquals(parent, result.parent); assertEquals(process, result.process)
                assertFalse(result.reason.contains("private"))
            }
            is BackgroundControlResult.UnknownOutcome -> {
                assertEquals(parent, result.parent); assertEquals(process, result.process)
                assertFalse(result.reason.contains("private"))
            }
            else -> fail("Expected UnknownOutcome, got $result")
        }
    }

    private fun response(data: JsonElement?, code: Int = ApiResult.SUCCESS_CODE, message: String = "") = RelayResponse(
        requestId = RequestId("mutation"), hostId = HostId("host"), result = ApiResult(code, message, data))

    private fun inputs(parent: SessionId, process: BackgroundProcessId): List<BackgroundInputResult> = listOf(
        BackgroundInputResult.Unavailable(parent, process, "unsupported"), BackgroundInputResult.NotFound(parent, process),
        BackgroundInputResult.Accepted(parent, process, 7), BackgroundInputResult.Rejected(parent, process, "closed"),
        BackgroundInputResult.UnknownOutcome(parent, process, "uncertain"))

    private fun controls(parent: SessionId, process: BackgroundProcessId): List<BackgroundControlResult> = listOf(
        BackgroundControlResult.Unavailable(parent, process, "unsupported"), BackgroundControlResult.NotFound(parent, process),
        BackgroundControlResult.Applied(parent, process), BackgroundControlResult.Rejected(parent, process, "closed"),
        BackgroundControlResult.UnknownOutcome(parent, process, "uncertain"))
}
