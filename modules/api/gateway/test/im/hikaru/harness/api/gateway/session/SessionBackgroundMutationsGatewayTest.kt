package im.hikaru.harness.api.gateway.session

import im.hikaru.harness.agent.AgentOptions
import im.hikaru.harness.api.gateway.ApiGateway
import im.hikaru.harness.llm.Message
import im.hikaru.harness.runtime.Context
import im.hikaru.harness.session.CreateSessionOptions
import im.hikaru.harness.session.SessionId
import im.hikaru.harness.session.api.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlin.test.*

class SessionBackgroundMutationsGatewayTest {
    private val parent = SessionId("parent")
    private val process = BackgroundProcessId("incarnation:process")
    private val control = BackgroundControlRequest(parent, process)

    @Test
    fun missingServiceAndLegacyDefaultsReturnTypedUnavailable() = runTest {
        fixture { owner, gateway ->
            suspend fun checkUnavailable() {
                assertIs<BackgroundInputResult.Unavailable>(gateway.invoke(SessionBackgroundInputEndpoint,
                    BackgroundInputRequest(parent, process, "")).data)
                assertIs<BackgroundControlResult.Unavailable>(gateway.invoke(SessionBackgroundCloseInputEndpoint, control).data)
                assertIs<BackgroundControlResult.Unavailable>(gateway.invoke(SessionBackgroundCancelEndpoint, control).data)
            }
            checkUnavailable()
            owner.provide(SessionApiKey, LegacyApi())
            checkUnavailable()
        }
    }

    @Test
    fun forwardsExactInputAndAllTypedResultsOnce() = runTest {
        fixture { owner, gateway ->
            var inputResult: BackgroundInputResult = BackgroundInputResult.Accepted(parent, process, 0)
            var controlResult: BackgroundControlResult = BackgroundControlResult.Applied(parent, process)
            val writes = mutableListOf<String>()
            val controls = mutableListOf<String>()
            owner.provide(SessionApiKey, object : LegacyApi() {
                override suspend fun writeBackgroundInput(parent: SessionId, process: BackgroundProcessId, input: String): BackgroundInputResult {
                    assertEquals(this@SessionBackgroundMutationsGatewayTest.parent, parent)
                    assertEquals(this@SessionBackgroundMutationsGatewayTest.process, process)
                    writes += input
                    return inputResult
                }
                override suspend fun closeBackgroundInput(parent: SessionId, process: BackgroundProcessId): BackgroundControlResult {
                    assertEquals(control, BackgroundControlRequest(parent, process))
                    controls += "close"
                    return controlResult
                }
                override suspend fun cancelBackgroundProcess(parent: SessionId, process: BackgroundProcessId): BackgroundControlResult {
                    assertEquals(control, BackgroundControlRequest(parent, process))
                    controls += "cancel"
                    return controlResult
                }
            })
            val input = "终端\n\u0000"
            for (result in inputResults(parent, process)) {
                inputResult = result
                val response = gateway.invoke(SessionBackgroundInputEndpoint, BackgroundInputRequest(parent, process, input))
                assertTrue(response.isSuccess)
                assertEquals(result, response.data)
            }
            inputResult = BackgroundInputResult.Accepted(parent, process, 0)
            assertEquals(inputResult, gateway.invoke(SessionBackgroundInputEndpoint, BackgroundInputRequest(parent, process, "")).data)
            assertEquals(List(5) { input } + "", writes)
            for (result in controlResults(parent, process)) {
                controlResult = result
                assertEquals(result, gateway.invoke(SessionBackgroundCloseInputEndpoint, control).data)
                assertEquals(result, gateway.invoke(SessionBackgroundCancelEndpoint, control).data)
            }
            assertEquals(List(5) { listOf("close", "cancel") }.flatten(), controls)
        }
    }

    @Test
    fun everyVariantMustMatchBothRequestedIdentities() = runTest {
        fixture { owner, gateway ->
            var inputResult: BackgroundInputResult = BackgroundInputResult.NotFound(parent, process)
            var controlResult: BackgroundControlResult = BackgroundControlResult.NotFound(parent, process)
            var calls = 0
            owner.provide(SessionApiKey, object : LegacyApi() {
                override suspend fun writeBackgroundInput(parent: SessionId, process: BackgroundProcessId, input: String): BackgroundInputResult {
                    calls++; return inputResult
                }
                override suspend fun closeBackgroundInput(parent: SessionId, process: BackgroundProcessId): BackgroundControlResult {
                    calls++; return controlResult
                }
                override suspend fun cancelBackgroundProcess(parent: SessionId, process: BackgroundProcessId): BackgroundControlResult {
                    calls++; return controlResult
                }
            })
            for ((wrongParent, wrongProcess) in listOf(SessionId("other") to process, parent to BackgroundProcessId("other:process"))) {
                for (result in inputResults(wrongParent, wrongProcess)) {
                    inputResult = result
                    val unknown = assertIs<BackgroundInputResult.UnknownOutcome>(gateway.invoke(SessionBackgroundInputEndpoint,
                        BackgroundInputRequest(parent, process, "input")).data)
                    assertEquals(parent, unknown.parent); assertEquals(process, unknown.process)
                }
                for (result in controlResults(wrongParent, wrongProcess)) {
                    controlResult = result
                    for (endpoint in listOf(SessionBackgroundCloseInputEndpoint, SessionBackgroundCancelEndpoint)) {
                        val unknown = assertIs<BackgroundControlResult.UnknownOutcome>(gateway.invoke(endpoint, control).data)
                        assertEquals(parent, unknown.parent); assertEquals(process, unknown.process)
                    }
                }
            }
            assertEquals(30, calls)
        }
    }

    @Test
    fun serviceFailureAndServiceTimeoutAreUnknownWithoutRetryOrPrivateDetails() = runTest {
        fixture { owner, gateway ->
            var failure: suspend () -> Nothing = { error("private input") }
            var calls = 0
            owner.provide(SessionApiKey, object : LegacyApi() {
                override suspend fun writeBackgroundInput(parent: SessionId, process: BackgroundProcessId, input: String): BackgroundInputResult {
                    calls++; failure()
                }
                override suspend fun closeBackgroundInput(parent: SessionId, process: BackgroundProcessId): BackgroundControlResult {
                    calls++; failure()
                }
                override suspend fun cancelBackgroundProcess(parent: SessionId, process: BackgroundProcessId): BackgroundControlResult {
                    calls++; failure()
                }
            })
            val failures: List<suspend () -> Nothing> = listOf(
                { throw IllegalArgumentException("private input") },
                { throw IllegalStateException("private input") },
                { throw CancellationException("private service cancellation") },
                { withTimeout(1) { awaitCancellation() } },
            )
            for (next in failures) {
                failure = next
                val input = assertIs<BackgroundInputResult.UnknownOutcome>(gateway.invoke(SessionBackgroundInputEndpoint,
                    BackgroundInputRequest(parent, process, "private input")).data)
                assertFalse(input.reason.contains("private"))
                for (endpoint in listOf(SessionBackgroundCloseInputEndpoint, SessionBackgroundCancelEndpoint)) {
                    val result = assertIs<BackgroundControlResult.UnknownOutcome>(gateway.invoke(endpoint, control).data)
                    assertFalse(result.reason.contains("private"))
                }
            }
            assertEquals(12, calls)
        }
    }

    @Test
    fun actualCallerCancellationInterruptsEachMutationWithoutRetry() = runTest {
        fixture { owner, gateway ->
            var entered = CompletableDeferred<Unit>()
            var calls = 0
            suspend fun waitForever(): Nothing { calls++; entered.complete(Unit); awaitCancellation() }
            owner.provide(SessionApiKey, object : LegacyApi() {
                override suspend fun writeBackgroundInput(parent: SessionId, process: BackgroundProcessId, input: String): BackgroundInputResult = waitForever()
                override suspend fun closeBackgroundInput(parent: SessionId, process: BackgroundProcessId): BackgroundControlResult = waitForever()
                override suspend fun cancelBackgroundProcess(parent: SessionId, process: BackgroundProcessId): BackgroundControlResult = waitForever()
            })
            val actions: List<suspend () -> Unit> = listOf(
                { gateway.invoke(SessionBackgroundInputEndpoint, BackgroundInputRequest(parent, process, "input")); Unit },
                { gateway.invoke(SessionBackgroundCloseInputEndpoint, control); Unit },
                { gateway.invoke(SessionBackgroundCancelEndpoint, control); Unit },
            )
            for (action in actions) {
                entered = CompletableDeferred()
                val pending = async { action() }
                entered.await()
                pending.cancel()
                assertFailsWith<CancellationException> { pending.await() }
                pending.join()
            }
            assertEquals(3, calls)
        }
    }

    @Test
    fun registrationAndOwnerDisposalRemoveAllThreeMutations() = runTest {
        for (disposeOwner in listOf(false, true)) {
            val owner = Context()
            val gateway = ApiGateway()
            val registration = gateway.registerSessionApi(owner)
            try {
                val methods = setOf(SessionBackgroundInputEndpoint.method, SessionBackgroundCloseInputEndpoint.method, SessionBackgroundCancelEndpoint.method)
                assertTrue(gateway.methods().containsAll(methods))
                if (disposeOwner) owner.dispose() else registration.dispose()
                assertTrue(gateway.methods().intersect(methods).isEmpty())
                assertEquals(404, gateway.invoke(SessionBackgroundInputEndpoint, BackgroundInputRequest(parent, process, "input")).code)
                assertEquals(404, gateway.invoke(SessionBackgroundCloseInputEndpoint, control).code)
                assertEquals(404, gateway.invoke(SessionBackgroundCancelEndpoint, control).code)
            } finally { owner.dispose() }
        }
    }

    private suspend fun fixture(block: suspend (Context, ApiGateway) -> Unit) {
        val owner = Context()
        val gateway = ApiGateway()
        gateway.registerSessionApi(owner)
        try { block(owner, gateway) } finally { owner.dispose() }
    }

    private fun inputResults(parent: SessionId, process: BackgroundProcessId): List<BackgroundInputResult> = listOf(
        BackgroundInputResult.Unavailable(parent, process, "unsupported"), BackgroundInputResult.NotFound(parent, process),
        BackgroundInputResult.Accepted(parent, process, 7), BackgroundInputResult.Rejected(parent, process, "closed"),
        BackgroundInputResult.UnknownOutcome(parent, process, "producer uncertain"),
    )

    private fun controlResults(parent: SessionId, process: BackgroundProcessId): List<BackgroundControlResult> = listOf(
        BackgroundControlResult.Unavailable(parent, process, "unsupported"), BackgroundControlResult.NotFound(parent, process),
        BackgroundControlResult.Applied(parent, process), BackgroundControlResult.Rejected(parent, process, "closed"),
        BackgroundControlResult.UnknownOutcome(parent, process, "producer uncertain"),
    )

    private open class LegacyApi : SessionApi {
        override suspend fun list(): List<SessionSummary> = error("Unexpected list")
        override suspend fun create(id: SessionId?, options: CreateSessionOptions, agentOptions: AgentOptions): SessionSummary = error("Unexpected create")
        override suspend fun history(id: SessionId): List<Message> = error("Unexpected history")
        override suspend fun prompt(id: SessionId, message: Message): SessionPrompt = error("Unexpected prompt")
        override suspend fun cancel(id: SessionId, keepInbox: Boolean) = error("Unexpected cancel")
    }
}
