package im.hikaru.harness.client.connection

import im.hikaru.contracts.common.ApiResult
import im.hikaru.contracts.harness.identity.HostId
import im.hikaru.contracts.harness.relay.RelayResponse
import im.hikaru.contracts.harness.relay.RequestId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlin.test.*

class SessionBackgroundProcessesRemoteTest {
    private val json = Json { encodeDefaults = true; explicitNulls = false; ignoreUnknownKeys = true }
    private val parent = SessionId("parent")
    private val process = BackgroundProcessId("incarnation:process")

    @Test
    fun typedReadsCarryExactIdentityAndPreserveMetadataLifecycleAndOutput() = runTest {
        val calls = mutableListOf<String>()
        var lifecycle: BackgroundProcessLifecycle = BackgroundProcessLifecycle.Running
        val metadata = BackgroundProcess(process, parent, "exec-call", """["git","status"]""",
            "/actual/cwd", 1234L, "incarnation", lifecycle)
        val reader = RemoteSessionBackgroundProcesses(json) { method, payload ->
            calls += method
            when (method) {
                "session.background-processes.list" -> {
                    assertEquals(parent, json.decodeFromJsonElement(SessionId.serializer(), assertNotNull(payload)))
                    val result: BackgroundProcessesResult = BackgroundProcessesResult.Available(parent, listOf(metadata.copy(lifecycle = lifecycle)))
                    response(json.encodeToJsonElement(BackgroundProcessesResult.serializer(), result))
                }
                "session.background-processes.read" -> {
                    assertEquals(WireBackgroundOutputRequest(parent, process),
                        json.decodeFromJsonElement(WireBackgroundOutputRequest.serializer(), assertNotNull(payload)))
                    val result: BackgroundOutputResult = BackgroundOutputResult.Available(parent, process, parent,
                        "actual output\n终端\u0000", 27L, true, lifecycle)
                    response(json.encodeToJsonElement(BackgroundOutputResult.serializer(), result))
                }
                else -> error("Unexpected endpoint")
            }
        }
        for (state in listOf(BackgroundProcessLifecycle.Running, BackgroundProcessLifecycle.Unknown, BackgroundProcessLifecycle.Exited(9))) {
            lifecycle = state
            assertEquals(BackgroundProcessesResult.Available(parent, listOf(metadata.copy(lifecycle = state))), reader.backgroundProcesses(parent))
            assertEquals(BackgroundOutputResult.Available(parent, process, parent, "actual output\n终端\u0000", 27L, true, state),
                reader.readBackgroundOutput(parent, process))
        }
        assertEquals(List(3) { listOf("session.background-processes.list", "session.background-processes.read") }.flatten(), calls)
    }

    @Test
    fun onlyExactMissingEndpoint404IsUnavailableAndOtherFailuresThrow() = runTest {
        for (message in listOf("Harness endpoint unavailable", "Harness API endpoint 'session.background-processes.list' 未注册")) {
            val reader = RemoteSessionBackgroundProcesses(json) { _, _ -> response(null, 404, message) }
            assertIs<BackgroundProcessesResult.Unavailable>(reader.backgroundProcesses(parent)).also {
                assertEquals(parent, it.parent)
                assertTrue(it.reason.isNotBlank())
            }
        }
        for (message in listOf("Harness endpoint unavailable", "Harness API endpoint 'session.background-processes.read' 未注册")) {
            val reader = RemoteSessionBackgroundProcesses(json) { _, _ -> response(null, 404, message) }
            assertIs<BackgroundOutputResult.Unavailable>(reader.readBackgroundOutput(parent, process)).also {
                assertEquals(parent, it.parent)
                assertEquals(process, it.process)
                assertTrue(it.reason.isNotBlank())
            }
        }
        for (code in listOf(403, 404, 422, 500)) {
            val messages = listOf("Session operation unavailable", "Harness endpoint unavailable ",
                "Harness API endpoint 'session.descendants' 未注册") +
                if (code == 404) emptyList() else listOf("Harness endpoint unavailable")
            for (message in messages) {
                val reader = RemoteSessionBackgroundProcesses(json) { _, _ -> response(null, code, message) }
                assertEquals(code, assertFailsWith<RemoteConnectionException> { reader.backgroundProcesses(parent) }.code)
                assertEquals(code, assertFailsWith<RemoteConnectionException> { reader.readBackgroundOutput(parent, process) }.code)
            }
        }
        val wrongEndpoint = RemoteSessionBackgroundProcesses(json) { _, _ ->
            response(null, 404, "Harness API endpoint 'session.background-processes.list' 未注册")
        }
        assertFailsWith<RemoteConnectionException> { wrongEndpoint.readBackgroundOutput(parent, process) }
    }

    @Test
    fun successfulEmptyUnavailableAndNotFoundRemainTypedAndMalformedSuccessThrows() = runTest {
        for (result in listOf<BackgroundProcessesResult>(
            BackgroundProcessesResult.Available(parent, emptyList()),
            BackgroundProcessesResult.Unavailable(parent, "Producer is unavailable"),
        )) {
            val reader = RemoteSessionBackgroundProcesses(json) { _, _ ->
                response(json.encodeToJsonElement(BackgroundProcessesResult.serializer(), result))
            }
            assertEquals(result, reader.backgroundProcesses(parent))
        }
        for (result in listOf<BackgroundOutputResult>(
            BackgroundOutputResult.NotFound(parent, process),
            BackgroundOutputResult.Unavailable(parent, process, "Output service is unavailable"),
            BackgroundOutputResult.Available(parent, process, parent, "", 0L, false, BackgroundProcessLifecycle.Unknown),
        )) {
            val reader = RemoteSessionBackgroundProcesses(json) { _, _ ->
                response(json.encodeToJsonElement(BackgroundOutputResult.serializer(), result))
            }
            assertEquals(result, reader.readBackgroundOutput(parent, process))
        }
        val noData = RemoteSessionBackgroundProcesses(json) { _, _ -> response(null) }
        assertFailsWith<RemoteConnectionException> { noData.backgroundProcesses(parent) }
        assertFailsWith<RemoteConnectionException> { noData.readBackgroundOutput(parent, process) }
        val missingReason = RemoteSessionBackgroundProcesses(json) { _, _ ->
            response(json.parseToJsonElement("""{"type":"unavailable","parent":"parent","process":"incarnation:process"}"""))
        }
        assertFailsWith<SerializationException> { missingReason.backgroundProcesses(parent) }
        assertFailsWith<SerializationException> { missingReason.readBackgroundOutput(parent, process) }
    }

    @Test
    fun mismatchedParentOrProcessCannotPublishAnyTypedVariant() = runTest {
        val other = SessionId("old-parent")
        for (result in listOf<BackgroundProcessesResult>(
            BackgroundProcessesResult.Available(other, emptyList()),
            BackgroundProcessesResult.Unavailable(other, "Unavailable in old scope"),
        )) {
            val reader = RemoteSessionBackgroundProcesses(json) { _, _ ->
                response(json.encodeToJsonElement(BackgroundProcessesResult.serializer(), result))
            }
            assertFailsWith<IllegalArgumentException> { reader.backgroundProcesses(parent) }
        }
        for ((owner, id) in listOf(other to process, parent to BackgroundProcessId("old-incarnation:process"))) {
            for (result in listOf<BackgroundOutputResult>(
                BackgroundOutputResult.NotFound(owner, id),
                BackgroundOutputResult.Unavailable(owner, id, "Unavailable in old scope"),
                BackgroundOutputResult.Available(owner, id, owner, "old output", 1L, false, BackgroundProcessLifecycle.Running),
            )) {
                val reader = RemoteSessionBackgroundProcesses(json) { _, _ ->
                    response(json.encodeToJsonElement(BackgroundOutputResult.serializer(), result))
                }
                assertFailsWith<IllegalArgumentException> { reader.readBackgroundOutput(parent, process) }
            }
        }
    }

    @Test
    fun cancellationPropagatesUnchangedAndSuspendedReadCanBeCancelled() = runTest {
        val cancellation = CancellationException("cancelled")
        val reader = RemoteSessionBackgroundProcesses(json) { _, _ -> throw cancellation }
        assertSame(cancellation, assertFailsWith<CancellationException> { reader.backgroundProcesses(parent) })
        assertSame(cancellation, assertFailsWith<CancellationException> { reader.readBackgroundOutput(parent, process) })
        val entered = CompletableDeferred<Unit>()
        val never = CompletableDeferred<RelayResponse>()
        val suspended = RemoteSessionBackgroundProcesses(json) { _, _ -> entered.complete(Unit); never.await() }
        val pending = async { suspended.readBackgroundOutput(parent, process) }
        entered.await()
        pending.cancel()
        assertFailsWith<CancellationException> { pending.await() }
        pending.join()
    }

    private fun response(data: JsonElement?, code: Int = ApiResult.SUCCESS_CODE, message: String = ""): RelayResponse =
        RelayResponse(requestId = RequestId("background-read"), hostId = HostId("host"), result = ApiResult(code, message, data))
}
