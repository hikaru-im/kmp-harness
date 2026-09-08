package im.hikaru.harness.llm

import im.hikaru.harness.runtime.Context
import im.hikaru.harness.runtime.Runtime
import im.hikaru.harness.runtime.effect.EffectScope
import im.hikaru.harness.runtime.plugin.SimplePlugin
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class LlmRuntimeTest {

    @Test
    fun pluginShouldBindServiceToFiberLifecycle() = runTest {
        val runtime = Runtime()
        val fiber = runtime.install(LlmPlugin())

        assertSame(runtime.context.require(LlmKey), runtime.context.llm)

        runtime.uninstall(fiber)

        assertFalse(runtime.context.has(LlmKey))
    }

    @Test
    fun shouldRouteStreamToRegisteredAdapter() = runTest {
        val fixture = installLlm()
        fixture.llm.registerAdapter(
            providers = listOf("test"),
            adapter = ScriptedAdapter(script("hello")),
        )

        val chunks = fixture.llm.stream(request()).toList()

        assertEquals(script("hello"), chunks)
        fixture.runtime.context.dispose()
    }

    @Test
    fun missingAdapterShouldBecomeTerminalFailure() = runTest {
        val fixture = installLlm()

        val finish = fixture.llm.stream(request(provider = "missing")).toList().single()
        val reason = assertIs<ErrorFinishReason>(assertIs<FinishChunk>(finish).reason)

        assertEquals(LlmErrorCode.NO_ADAPTER, reason.failure.code)
        fixture.runtime.context.dispose()
    }

    @Test
    fun adapterExceptionShouldKeepStructuredFailureFacts() = runTest {
        val fixture = installLlm()
        fixture.llm.registerAdapter(
            providers = listOf("test"),
            adapter = ThrowingAdapter(
                LlmException(
                    message = "provider busy",
                    code = "RATE_LIMIT",
                    status = 429,
                    providerRetryAfterMs = 1_500,
                    requestId = ProviderRequestId("request-7"),
                )
            ),
        )

        val finish = fixture.llm.stream(request()).toList().single()
        val reason = assertIs<ErrorFinishReason>(assertIs<FinishChunk>(finish).reason)

        assertEquals("RATE_LIMIT", reason.failure.code)
        assertEquals(429, reason.failure.status)
        assertEquals(1_500, reason.failure.providerRetryAfterMs)
        assertEquals(ProviderRequestId("request-7"), reason.failure.requestId)
        fixture.runtime.context.dispose()
    }

    @Test
    fun structuredCoroutineCancellationShouldNotBecomeAChunk() = runTest {
        val fixture = installLlm()
        fixture.llm.registerAdapter(
            providers = listOf("test"),
            adapter = object : LlmAdapter {
                override fun stream(options: GenerateOptions): Flow<StreamChunk> =
                    flow {
                        throw CancellationException("cancelled")
                    }
            },
        )

        assertFailsWith<CancellationException> {
            fixture.llm.stream(request()).toList()
        }

        fixture.runtime.context.dispose()
    }

    @Test
    fun downstreamConsumerFailureShouldPropagateUnchanged() = runTest {
        val fixture = installLlm()
        fixture.llm.registerAdapter(
            providers = listOf("test"),
            adapter = ScriptedAdapter(script("hello")),
        )
        val consumerFailure =
            IllegalStateException("consumer failed")

        val thrown =
            assertFailsWith<IllegalStateException> {
                fixture.llm.stream(request()).collect {
                    throw consumerFailure
                }
            }

        assertSame(consumerFailure, thrown)
        fixture.runtime.context.dispose()
    }

    @Test
    fun waterfallFailureShouldPropagateUnchanged() = runTest {
        val fixture = installLlm()
        fixture.llm.registerAdapter(
            providers = listOf("test"),
            adapter = ScriptedAdapter(script("hello")),
        )
        val middlewareFailure =
            IllegalStateException("middleware failed")
        fixture.runtime.context.on(LlmStreamEvent) { _, _ ->
            flow {
                throw middlewareFailure
            }
        }

        val thrown =
            assertFailsWith<IllegalStateException> {
                fixture.llm.stream(request()).toList()
            }

        assertSame(middlewareFailure, thrown)
        fixture.runtime.context.dispose()
    }

    @Test
    fun duplicateRegistrationShouldBeAllOrNothing() = runTest {
        val fixture = installLlm()
        fixture.llm.registerAdapter(
            providers = listOf("owned"),
            adapter = ScriptedAdapter(script("first")),
        )

        val error =
            assertFailsWith<LlmException> {
                fixture.llm.registerAdapter(
                    providers = listOf("candidate", "owned"),
                    adapter = ScriptedAdapter(script("second")),
                )
            }

        assertEquals(LlmErrorCode.DUPLICATE_ADAPTER, error.code)
        assertEquals(listOf("owned"), fixture.llm.listProviders().map { it.id })
        fixture.runtime.context.dispose()
    }

    @Test
    fun routeReplacementShouldBeAtomicAndRejectedCandidateShouldKeepOldRoutes() = runTest {
        val fixture = installLlm()
        val first =
            fixture.llm.registerAdapter(
                providers = listOf("first"),
                adapter = ScriptedAdapter(script("first")),
            )
        fixture.llm.registerAdapter(
            providers = listOf("occupied"),
            adapter = ScriptedAdapter(script("occupied")),
        )

        val error =
            assertFailsWith<LlmException> {
                first.replace(listOf("replacement", "occupied"))
            }

        assertEquals(LlmErrorCode.DUPLICATE_ADAPTER, error.code)
        assertEquals(
            listOf("first", "occupied"),
            fixture.llm.listProviders().map { it.id },
        )

        first.replace(listOf("replacement"))
        assertEquals(
            listOf("occupied", "replacement"),
            fixture.llm.listProviders().map { it.id },
        )

        fixture.runtime.context.dispose()
    }

    @Test
    fun configurableProviderDirectoryShouldBeIndependentAndDetached() = runTest {
        val fixture = installLlm()
        val mutablePath = mutableListOf("providers", "gateway")
        fixture.llm.registerConfigurableProviders(
            listOf(
                LlmConfigurableProvider(
                    provider = "gateway",
                    displayName = "Gateway",
                    settingsNamespace = "llm-test",
                    settingsPath = mutablePath,
                    declared = true,
                )
            )
        )

        mutablePath += "mutated"
        val listed = fixture.llm.listConfigurableProviders().single()

        assertEquals(listOf("providers", "gateway"), listed.settingsPath)
        assertTrue(fixture.llm.listProviders().isEmpty())
        fixture.runtime.context.dispose()
    }

    @Test
    fun directoryReplacementShouldBeAtomicAndMayBecomeDormant() = runTest {
        val fixture = installLlm()
        val first =
            fixture.llm.registerConfigurableProviders(
                listOf(configurableProvider("first"))
            )
        fixture.llm.registerConfigurableProviders(
            listOf(configurableProvider("occupied"))
        )

        val error =
            assertFailsWith<LlmException> {
                first.replace(
                    listOf(
                        configurableProvider("replacement"),
                        configurableProvider("occupied"),
                    )
                )
            }

        assertEquals(LlmErrorCode.DUPLICATE_DIRECTORY, error.code)
        assertEquals(
            listOf("first", "occupied"),
            fixture.llm.listConfigurableProviders().map { it.provider },
        )

        first.replace(emptyList())
        assertEquals(
            listOf("occupied"),
            fixture.llm.listConfigurableProviders().map { it.provider },
        )
        fixture.runtime.context.dispose()
    }

    @Test
    fun disposedDirectoryHandleShouldRejectReplacementBeforeValidation() = runTest {
        val fixture = installLlm()
        val handle =
            fixture.llm.registerConfigurableProviders(
                listOf(configurableProvider("test"))
            )
        handle.dispose()

        val error =
            assertFailsWith<LlmException> {
                handle.replace(
                    listOf(
                        configurableProvider(" ")
                    )
                )
            }

        assertEquals(LlmErrorCode.REGISTRATION_DISPOSED, error.code)
        assertTrue(fixture.llm.listConfigurableProviders().isEmpty())
        fixture.runtime.context.dispose()
    }

    @Test
    fun topologyNotificationShouldFollowEverySuccessfulCommitAndContainObservers() = runTest {
        val fixture = installLlm()
        var notifications = 0
        fixture.runtime.context.on(LlmAdaptersUpdatedEvent) {
            throw IllegalStateException("observer failed")
        }
        fixture.runtime.context.on(LlmAdaptersUpdatedEvent) {
            notifications += 1
        }

        val adapter =
            fixture.llm.registerAdapter(
                providers = listOf("test"),
                adapter = ScriptedAdapter(script("test")),
            )
        adapter.replace(emptyList())
        val directory =
            fixture.llm.registerConfigurableProviders(
                listOf(configurableProvider("test"))
            )
        directory.replace(emptyList())
        directory.dispose()

        assertEquals(5, notifications)
        assertTrue(fixture.llm.listProviders().isEmpty())
        assertTrue(fixture.llm.listConfigurableProviders().isEmpty())
        fixture.runtime.context.dispose()
    }

    @Test
    fun preparedCallShouldCaptureOldAdapterAndBeSingleUse() = runTest {
        val fixture = installLlm()
        val old =
            fixture.llm.registerAdapter(
                providers = listOf("test"),
                adapter = ScriptedAdapter(script("old")),
            )
        val prepared =
            fixture.llm.prepareCall(
                LlmCallConfig(provider = "test", model = "model")
            )

        old.dispose()
        fixture.llm.registerAdapter(
            providers = listOf("test"),
            adapter = ScriptedAdapter(script("new")),
        )

        assertEquals(script("old"), prepared.stream(request()).toList())

        val error =
            assertFailsWith<LlmException> {
                prepared.stream(request()).toList()
            }
        assertEquals(LlmErrorCode.INVALID_PREPARED_CALL, error.code)
        fixture.runtime.context.dispose()
    }

    @Test
    fun exactModelResolutionShouldMaterializeAdapterDefaults() = runTest {
        val fixture = installLlm()
        val effort = ReasoningEffortId("high")
        fixture.llm.registerAdapter(
            providers = listOf("test"),
            adapter =
                object : ScriptedAdapter(script("ok")) {
                    override suspend fun resolveModel(
                        provider: String,
                        model: String,
                    ): LlmResolvedModelInfo =
                        LlmResolvedModelInfo(
                            provider = provider,
                            id = model,
                            name = model,
                            context = LlmModelContext(128_000),
                            defaultMaxTokens = 4_096,
                            reasoning =
                                LlmModelReasoningInfo(
                                    efforts = listOf(
                                        LlmReasoningEffortInfo(effort, "High")
                                    ),
                                    defaultEffort = effort,
                                ),
                        )
                },
        )

        val prepared =
            fixture.llm.prepareCall(
                LlmCallConfig(provider = "test", model = "model")
            )

        assertEquals(4_096, prepared.config.maxTokens)
        assertEquals(effort, prepared.config.reasoningEffort)
        assertTrue(prepared.adapterDefaults.maxTokens)
        assertTrue(prepared.adapterDefaults.reasoningEffort)
        assertEquals(128_000, prepared.context?.contextWindow)
        fixture.runtime.context.dispose()
    }

    @Test
    fun waterfallShouldWrapOrShortCircuitAdapterStream() = runTest {
        val fixture = installLlm()
        fixture.llm.registerAdapter(
            providers = listOf("test"),
            adapter = ScriptedAdapter(script("adapter")),
        )

        fixture.runtime.context.on(LlmStreamEvent) { _, next ->
            next().map { chunk ->
                when (chunk) {
                    is TextDeltaChunk ->
                        chunk.copy(text = chunk.text.uppercase())

                    is BlockEndChunk -> {
                        val block = chunk.block
                        if (block is TextBlock) {
                            chunk.copy(
                                block = block.copy(text = block.text.uppercase())
                            )
                        } else {
                            chunk
                        }
                    }

                    else -> chunk
                }
            }
        }

        assertEquals(
            script("ADAPTER"),
            fixture.llm.stream(request()).toList(),
        )

        val shortCircuitOwner = fixture.runtime.context.child()
        shortCircuitOwner.on(LlmStreamEvent) { _, _ ->
            flowOf(FinishChunk(StopFinishReason))
        }

        assertEquals(
            listOf(FinishChunk(StopFinishReason)),
            fixture.llm.stream(request()).toList(),
        )

        shortCircuitOwner.dispose()
        fixture.runtime.context.dispose()
    }

    @Test
    fun adapterPluginDisposeShouldWithdrawOwnedRoutes() = runTest {
        val fixture = installLlm()
        val plugin =
            TestAdapterPlugin(
                adapter = ScriptedAdapter(script("plugin")),
            )
        val fiber = fixture.runtime.install(plugin)

        assertEquals(listOf("plugin"), fixture.llm.listProviders().map { it.id })

        fixture.runtime.uninstall(fiber)

        assertTrue(fixture.llm.listProviders().isEmpty())
        fixture.runtime.context.dispose()
    }

    @Test
    fun concurrentDuplicateRegistrationShouldHaveOneWinner() = runTest {
        val fixture = installLlm()

        val results =
            listOf("first", "second").map { value ->
                async {
                    runCatching {
                        fixture.llm.registerAdapter(
                            providers = listOf("same"),
                            adapter = ScriptedAdapter(script(value)),
                        )
                    }
                }
            }.awaitAll()

        assertEquals(1, results.count(Result<AdapterRegistrationHandle>::isSuccess))
        assertEquals(1, results.count(Result<AdapterRegistrationHandle>::isFailure))
        assertEquals(listOf("same"), fixture.llm.listProviders().map { it.id })
        fixture.runtime.context.dispose()
    }

    @Test
    fun disposedHandleShouldRejectReplacementBeforeCallingAdapter() = runTest {
        val fixture = installLlm()
        var metadataCalls = 0
        val handle =
            fixture.llm.registerAdapter(
                providers = listOf("test"),
                adapter =
                    object : ScriptedAdapter(script("value")) {
                        override fun providerInfo(provider: String): LlmProviderInfo {
                            metadataCalls++
                            return super.providerInfo(provider)
                        }
                    },
            )

        handle.dispose()

        val error =
            assertFailsWith<LlmException> {
                handle.replace(listOf("replacement"))
            }

        assertEquals(LlmErrorCode.REGISTRATION_DISPOSED, error.code)
        assertEquals(1, metadataCalls)
        assertTrue(fixture.llm.listProviders().isEmpty())
        fixture.runtime.context.dispose()
    }

    @Test
    fun runtimeShouldSnapshotRequestAndProviderPolicy() = runTest {
        val fixture = installLlm()
        val retryableCodes = mutableListOf("SERVER")
        val messages = mutableListOf(
            createUserMessage(listOf(TextBlock("before")))
        )
        var received: GenerateOptions? = null
        fixture.llm.registerAdapter(
            providers = listOf("test"),
            adapter =
                object : LlmAdapter {
                    override fun providerRetryPolicy(provider: String): RetryPolicy =
                        NormalRetryPolicy(retryableCodes = retryableCodes)

                    override fun stream(options: GenerateOptions): Flow<StreamChunk> =
                        flow {
                            received = options
                            emit(FinishChunk(StopFinishReason))
                        }
                },
        )

        val request =
            GenerateOptions(
                provider = "test",
                model = "model",
                messages = messages,
            )
        val stream = fixture.llm.stream(request)

        messages += createUserMessage(listOf(TextBlock("after")))
        retryableCodes += "TIMEOUT"
        stream.toList()

        assertEquals(1, received?.messages?.size)
        val prepared =
            fixture.llm.prepareCall(
                LlmCallConfig(provider = "test", model = "model")
            )
        assertEquals(
            listOf("SERVER"),
            (prepared.retryPolicy as NormalRetryPolicy).retryableCodes,
        )
        fixture.runtime.context.dispose()
    }

    @Test
    fun disposedRuntimeShouldRejectFurtherQueries() = runTest {
        val fixture = installLlm()

        fixture.runtime.context.dispose()

        val error =
            assertFailsWith<LlmException> {
                fixture.llm.listProviders()
            }
        assertEquals(LlmErrorCode.LLM_DISPOSED, error.code)
    }

    private suspend fun installLlm(): Fixture {
        val runtime = Runtime()
        runtime.install(LlmPlugin())
        return Fixture(runtime, runtime.context.llm)
    }

    private fun request(provider: String = "test"): GenerateOptions =
        GenerateOptions(
            provider = provider,
            model = "model",
            messages = emptyList(),
        )

    private fun script(text: String): List<StreamChunk> =
        listOf(
            BlockStartChunk(index = 0, blockType = "text"),
            TextDeltaChunk(index = 0, text = text),
            BlockEndChunk(index = 0, block = TextBlock(text)),
            FinishChunk(StopFinishReason),
        )

    private fun configurableProvider(provider: String): LlmConfigurableProvider =
        LlmConfigurableProvider(
            provider = provider,
            displayName = provider,
            settingsNamespace = "llm-test",
            settingsPath = listOf("providers", provider),
        )

    private data class Fixture(
        val runtime: Runtime,
        val llm: LlmRuntime,
    )

    private open class ScriptedAdapter(
        private val chunks: List<StreamChunk>,
    ) : LlmAdapter {
        override fun stream(options: GenerateOptions): Flow<StreamChunk> =
            flow {
                chunks.forEach { chunk -> emit(chunk) }
            }
    }

    private class ThrowingAdapter(
        private val error: Throwable,
    ) : LlmAdapter {
        override fun stream(options: GenerateOptions): Flow<StreamChunk> =
            flow {
                throw error
            }
    }

    private class TestAdapterPlugin(
        private val adapter: LlmAdapter,
    ) : SimplePlugin {
        override suspend fun apply(
            context: Context,
            scope: EffectScope,
        ) {
            scope.add(
                context.llm.registerAdapter(
                    providers = listOf("plugin"),
                    adapter = adapter,
                )
            )
        }
    }
}
