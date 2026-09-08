package im.hikaru.harness.llm.koog

import ai.koog.prompt.message.Message as KoogMessage
import ai.koog.prompt.params.LLMParams
import ai.koog.prompt.streaming.StreamFrame
import im.hikaru.harness.llm.BlockEndChunk
import im.hikaru.harness.llm.BlockStartChunk
import im.hikaru.harness.llm.CallId
import im.hikaru.harness.llm.ErrorFinishReason
import im.hikaru.harness.llm.FinishChunk
import im.hikaru.harness.llm.GenerateOptions
import im.hikaru.harness.llm.LlmErrorCode
import im.hikaru.harness.llm.LlmFailure
import im.hikaru.harness.llm.LlmException
import im.hikaru.harness.llm.LlmModelContext
import im.hikaru.harness.llm.LlmPlugin
import im.hikaru.harness.llm.ProviderRequestId
import im.hikaru.harness.llm.TextBlock
import im.hikaru.harness.llm.ToolCallBlock
import im.hikaru.harness.llm.ToolCallDeltaChunk
import im.hikaru.harness.llm.ToolCallsFinishReason
import im.hikaru.harness.llm.ToolSchema
import im.hikaru.harness.llm.createAssistantMessage
import im.hikaru.harness.llm.createUserMessage
import im.hikaru.harness.llm.llm
import im.hikaru.harness.runtime.Runtime
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class KoogLlmPluginTest {

    @Test
    fun pluginShouldRegisterMetadataAndCloseExecutorOnDispose() = runTest {
        val runtime = Runtime()
        runtime.install(LlmPlugin())
        lateinit var executor: RecordingPromptExecutor
        val plugin =
            KoogLlmPlugin(
                executorFactory = KoogPromptExecutorFactory {
                    RecordingPromptExecutor().also { created ->
                        executor = created
                    }
                },
                routes = listOf(testKoogRoute()),
            )
        val fiber = runtime.install(plugin)

        assertEquals(listOf("test"), runtime.context.llm.listProviders().map { it.id })
        assertEquals(
            listOf("test-model"),
            runtime.context.llm.listModels("test").map { it.id },
        )
        assertEquals(
            LlmModelContext(32_000),
            runtime.context.llm.resolveModelInfo("test", "test-model").context,
        )

        runtime.uninstall(fiber)

        assertTrue(executor.closed)
        assertTrue(runtime.context.llm.listProviders().isEmpty())
        runtime.context.dispose()
    }

    @Test
    fun failedRouteRegistrationShouldCloseOnlyTheNewExecutor() = runTest {
        val runtime = Runtime()
        runtime.install(LlmPlugin())
        lateinit var existingExecutor: RecordingPromptExecutor
        lateinit var rejectedExecutor: RecordingPromptExecutor
        val existingFiber =
            runtime.install(
                KoogLlmPlugin(
                    executorFactory =
                        KoogPromptExecutorFactory {
                            RecordingPromptExecutor().also { created ->
                                existingExecutor = created
                            }
                        },
                    routes = listOf(testKoogRoute()),
                )
            )

        val rejected =
            runtime.installCatching(
                KoogLlmPlugin(
                    executorFactory =
                        KoogPromptExecutorFactory {
                            RecordingPromptExecutor().also { created ->
                                rejectedExecutor = created
                            }
                        },
                    routes = listOf(testKoogRoute()),
                )
            )
        val failure = assertIs<LlmException>(rejected.failure)

        assertEquals(LlmErrorCode.DUPLICATE_ADAPTER, failure.code)
        assertTrue(rejectedExecutor.closed)
        assertFalse(existingExecutor.closed)
        assertEquals(
            listOf("test"),
            runtime.context.llm.listProviders().map { provider -> provider.id },
        )

        runtime.uninstall(rejected.fiber)
        runtime.uninstall(existingFiber)
        assertTrue(existingExecutor.closed)
        runtime.context.dispose()
    }

    @Test
    fun emptyProviderFlowShouldReachMissingEndValidationAfterRequestMapping() = runTest {
        val runtime = Runtime()
        runtime.install(LlmPlugin())
        lateinit var executor: RecordingPromptExecutor
        runtime.install(
            KoogLlmPlugin(
                executorFactory = KoogPromptExecutorFactory {
                    RecordingPromptExecutor().also { created ->
                        executor = created
                    }
                },
                routes = listOf(testKoogRoute()),
            )
        )

        val finish =
            runtime.context.llm.stream(
                GenerateOptions(
                    provider = "test",
                    model = "test-model",
                    messages = listOf(
                        createUserMessage(listOf(TextBlock("hello")))
                    ),
                )
            ).toList().single()
        val reason =
            assertIs<ErrorFinishReason>(
                assertIs<FinishChunk>(finish).reason
            )

        assertEquals(
            KoogLlmErrorCode.INVALID_STREAM_STATE,
            reason.failure.code,
        )
        assertEquals(1, executor.streamingCalls)
        runtime.context.dispose()
    }

    @Test
    fun adapterStreamShouldBeColdAndAttemptOncePerCollection() = runTest {
        val executor = RecordingPromptExecutor()
        val adapter =
            KoogLlmAdapter(
                executor = executor,
                routes = listOf(testKoogRoute()),
            )
        val stream =
            adapter.stream(
                GenerateOptions(
                    provider = "test",
                    model = "test-model",
                    messages = listOf(createUserMessage(listOf(TextBlock("hello")))),
                )
            )

        assertEquals(0, executor.streamingCalls)
        repeat(2) { collectionIndex ->
            val error =
                assertFailsWith<LlmException> {
                    stream.toList()
                }
            assertEquals(KoogLlmErrorCode.INVALID_STREAM_STATE, error.code)
            assertEquals(collectionIndex + 1, executor.streamingCalls)
        }
    }

    @Test
    fun routeShouldRejectDuplicateModels() {
        val model = testKoogModel()

        assertFailsWith<IllegalArgumentException> {
            KoogProviderRoute(
                id = "test",
                name = "Test",
                models = listOf(
                    KoogModelRoute(model),
                    KoogModelRoute(model),
                ),
            )
        }
    }

    @Test
    fun pluginShouldRejectDuplicateProviderRoutes() {
        val route = testKoogRoute()

        assertFailsWith<IllegalArgumentException> {
            KoogLlmPlugin(
                executorFactory = KoogPromptExecutorFactory {
                    error("Invalid plugin must not create an executor")
                },
                routes = listOf(route, route),
            )
        }
    }

    @Test
    fun settingsConstructorShouldRequireAnExactProviderMatch() {
        val route = testKoogRoute()
        val factory =
            KoogProviderExecutorFactory { _, _, _ ->
                error("Invalid plugin must not create an executor")
            }
        val credentials = KoogCredentialResolver { null }
        val invalidSettings =
            listOf(
                emptyList(),
                listOf(
                    KoogProviderSettings(provider = "test"),
                    KoogProviderSettings(provider = "test"),
                ),
                listOf(KoogProviderSettings(provider = "unknown")),
            )

        invalidSettings.forEach { settings ->
            assertFailsWith<IllegalArgumentException> {
                KoogLlmPlugin(
                    routes = listOf(route),
                    providerSettings = settings,
                    credentials = credentials,
                    providerExecutorFactory = factory,
                )
            }
        }
    }

    @Test
    fun semanticsConstructorShouldRequireAnExactProviderMatch() {
        val route = testKoogRoute()
        val executorFactory =
            KoogPromptExecutorFactory {
                error("Invalid plugin must not create an executor")
            }
        val invalidSemantics =
            listOf(
                emptyList(),
                listOf(
                    KoogProviderSemantics("test"),
                    KoogProviderSemantics("test"),
                ),
                listOf(KoogProviderSemantics("unknown")),
            )

        invalidSemantics.forEach { semantics ->
            assertFailsWith<IllegalArgumentException> {
                KoogLlmPlugin(
                    executorFactory = executorFactory,
                    routes = listOf(route),
                    providerSemantics = semantics,
                )
            }
        }
    }

    @Test
    fun missingRequiredCredentialShouldFailBeforeAdapterRegistration() = runTest {
        val runtime = Runtime()
        runtime.install(LlmPlugin())
        val reference = KoogCredentialRef("missing-api-key")
        val plugin =
            KoogLlmPlugin(
                routes = listOf(testKoogRoute()),
                providerSettings =
                    listOf(
                        KoogProviderSettings(
                            provider = "test",
                            credential = reference,
                        )
                    ),
                credentials = KoogCredentialResolver { null },
                providerExecutorFactory =
                    KoogProviderExecutorFactory { _, settings, credentials ->
                        credentials.resolveRequired(
                            provider = settings.single().provider,
                            reference = settings.single().credential!!,
                        )
                        error("Missing credential must prevent executor creation")
                    },
            )

        val error =
            assertFailsWith<LlmException> {
                runtime.install(plugin)
            }

        assertEquals(LlmErrorCode.INVALID_CREDENTIAL, error.code)
        assertTrue(runtime.context.llm.listProviders().isEmpty())
        runtime.context.dispose()
    }

    @Test
    fun providerFactoryShouldResolveCredentialsOnlyDuringActivation() = runTest {
        val runtime = Runtime()
        runtime.install(LlmPlugin())
        val mutableSettings =
            mutableListOf(
                KoogProviderSettings(
                    provider = "backup",
                ),
                KoogProviderSettings(
                    provider = "test",
                    credential = KoogCredentialRef("test-api-key"),
                )
            )
        var factoryCalls = 0
        var resolverCalls = 0
        lateinit var executor: RecordingPromptExecutor
        val plugin =
            KoogLlmPlugin(
                routes =
                    listOf(
                        testKoogRoute(),
                        testKoogRoute(
                            model =
                                testKoogModel(
                                    id = "backup-model",
                                    provider = "backup",
                                ),
                            provider = "backup",
                        ),
                    ),
                providerSettings = mutableSettings,
                credentials = KoogCredentialResolver { reference ->
                    resolverCalls++
                    assertEquals("test-api-key", reference.name)
                    "resolved-secret"
                },
                providerExecutorFactory =
                    KoogProviderExecutorFactory { _, settings, credentials ->
                        yield()
                        factoryCalls++
                        assertEquals(
                            listOf("test", "backup"),
                            settings.map { it.provider },
                        )
                        assertEquals(
                            "resolved-secret",
                            credentials.resolve(
                                settings.first { setting ->
                                    setting.provider == "test"
                                }.credential!!
                            ),
                        )
                        RecordingPromptExecutor().also { created ->
                            executor = created
                        }
                    },
            )

        mutableSettings.clear()
        assertEquals(0, factoryCalls)
        assertEquals(0, resolverCalls)

        val fiber = runtime.install(plugin)

        assertEquals(1, factoryCalls)
        assertEquals(1, resolverCalls)
        assertEquals(
            listOf("test", "backup"),
            runtime.context.llm.listProviders().map { it.id },
        )

        runtime.uninstall(fiber)
        assertTrue(executor.closed)
        runtime.context.dispose()
    }

    @Test
    fun pluginShouldInstallTheProviderOptionMapper() = runTest {
        val runtime = Runtime()
        runtime.install(LlmPlugin())
        lateinit var executor: RecordingPromptExecutor
        var mappedModelId: String? = null
        runtime.install(
            KoogLlmPlugin(
                executorFactory = KoogPromptExecutorFactory {
                    RecordingPromptExecutor().also { created -> executor = created }
                },
                routes = listOf(testKoogRoute()),
                optionMapper =
                    KoogOptionMapper { options, model ->
                        mappedModelId = model.id
                        LLMParams(
                            temperature = options.temperature,
                            user = "provider-owned",
                        )
                    },
            )
        )

        runtime.context.llm.stream(
            GenerateOptions(
                provider = "test",
                model = "test-model",
                messages = listOf(createUserMessage(listOf(TextBlock("hello")))),
                temperature = 0.3,
            )
        ).toList()

        assertEquals("test-model", mappedModelId)
        assertEquals("provider-owned", executor.lastStreamingPrompt?.params?.user)
        assertEquals("test-model", executor.lastStreamingModel?.id)
        runtime.context.dispose()
    }

    @Test
    fun semanticsConstructorShouldDispatchOptionsByHarnessRoute() = runTest {
        val runtime = Runtime()
        runtime.install(LlmPlugin())
        lateinit var executor: RecordingPromptExecutor
        var betaToolMapperCalls = 0
        val alphaModel = testKoogModel(id = "alpha-model", provider = "koog-alpha")
        val betaModel = testKoogModel(id = "beta-model", provider = "koog-beta")
        runtime.install(
            KoogLlmPlugin(
                executorFactory =
                    KoogPromptExecutorFactory {
                        RecordingPromptExecutor().also { created -> executor = created }
                    },
                routes =
                    listOf(
                        testKoogRoute(model = alphaModel, provider = "alpha"),
                        testKoogRoute(model = betaModel, provider = "beta"),
                    ),
                providerSemantics =
                    listOf(
                        KoogProviderSemantics(
                            provider = "alpha",
                            optionMapper =
                                KoogOptionMapper { _, _ ->
                                    LLMParams(user = "alpha-options")
                                },
                        ),
                        KoogProviderSemantics(
                            provider = "beta",
                            optionMapper =
                                KoogOptionMapper { _, _ ->
                                    LLMParams(user = "beta-options")
                                },
                            toolMapper =
                                KoogToolMapper { tools, context ->
                                    assertEquals("beta", context.provider)
                                    assertEquals(1, tools?.size)
                                    betaToolMapperCalls++
                                    emptyList()
                                },
                        ),
                    ),
            )
        )

        runtime.context.llm.stream(
            GenerateOptions(
                provider = "beta",
                model = betaModel.id,
                messages = listOf(createUserMessage(listOf(TextBlock("hello")))),
                tools =
                    listOf(
                        ToolSchema(
                            name = "lookup",
                            description = "Lookup",
                            parameters = buildJsonObject { put("type", "object") },
                        )
                    ),
            )
        ).toList()

        assertEquals("beta-options", executor.lastStreamingPrompt?.params?.user)
        assertEquals(betaModel.id, executor.lastStreamingModel?.id)
        assertEquals(1, betaToolMapperCalls)
        runtime.context.dispose()
    }

    @Test
    fun semanticsConstructorShouldInstallToolCallTerminalPolicyEndToEnd() = runTest {
        val runtime = Runtime()
        runtime.install(LlmPlugin())
        runtime.install(
            KoogLlmPlugin(
                executorFactory =
                    KoogPromptExecutorFactory {
                        RecordingPromptExecutor(
                            frames =
                                flowOf(
                                    StreamFrame.ToolCallDelta(
                                        id = "call-1",
                                        name = "lookup",
                                        content = "{}",
                                        index = 0,
                                    ),
                                    StreamFrame.End(finishReason = "provider-tools"),
                                )
                        )
                    },
                routes = listOf(testKoogRoute()),
                providerSemantics =
                    listOf(
                        KoogProviderSemantics(
                            provider = "test",
                            finishReasonMapper =
                                KoogFinishReasonMapper { reason, _ ->
                                    assertEquals("provider-tools", reason)
                                    ToolCallsFinishReason
                                },
                            toolCallTerminalPolicy =
                                KoogToolCallTerminalPolicy { frame, context ->
                                    assertEquals("provider-tools", frame.finishReason)
                                    assertEquals("test", context.provider)
                                    true
                                },
                        )
                    ),
            )
        )

        val chunks =
            runtime.context.llm.stream(
                GenerateOptions(
                    provider = "test",
                    model = "test-model",
                    messages = listOf(createUserMessage(listOf(TextBlock("hello")))),
                )
            ).toList()

        assertEquals(
            listOf(
                BlockStartChunk(index = 0, blockType = "tool-call"),
                ToolCallDeltaChunk(
                    index = 0,
                    id = CallId("call-1"),
                    name = "lookup",
                    argumentsDelta = "{}",
                ),
                BlockEndChunk(
                    index = 0,
                    block =
                        ToolCallBlock(
                            id = CallId("call-1"),
                            name = "lookup",
                            arguments = "{}",
                        ),
                ),
                FinishChunk(ToolCallsFinishReason),
            ),
            chunks,
        )
        runtime.context.dispose()
    }

    @Test
    fun semanticsConstructorShouldInstallReplayRestorer() = runTest {
        val runtime = Runtime()
        runtime.install(LlmPlugin())
        lateinit var executor: RecordingPromptExecutor
        runtime.install(
            KoogLlmPlugin(
                executorFactory =
                    KoogPromptExecutorFactory {
                        RecordingPromptExecutor().also { created -> executor = created }
                    },
                routes = listOf(testKoogRoute()),
                providerSemantics =
                    listOf(
                        KoogProviderSemantics(
                            provider = "test",
                            replayRestorer =
                                KoogReplayRestorer { _, base, context ->
                                    assertEquals("test", context.sourceProvider)
                                    assertEquals("test", context.targetProvider)
                                    base.copy(finishReason = "replayed")
                                },
                        )
                    ),
            )
        )

        runtime.context.llm.stream(
            GenerateOptions(
                provider = "test",
                model = "test-model",
                messages =
                    listOf(
                        createAssistantMessage(
                            content = listOf(TextBlock("previous")),
                            provider = "test",
                            model = "test-model",
                            replayState =
                                buildJsonObject {
                                    put("kind", "test-replay")
                                    put("version", 1)
                                },
                        )
                    ),
            )
        ).toList()

        assertEquals(
            "replayed",
            (executor.lastStreamingPrompt?.messages?.single() as KoogMessage.Assistant)
                .finishReason,
        )
        runtime.context.dispose()
    }

    @Test
    fun providerFailureClassifierShouldReachRuntimeErrorFinish() = runTest {
        val runtime = Runtime()
        runtime.install(LlmPlugin())
        val providerError = IllegalStateException("provider rejected the request")
        val expected =
            LlmFailure(
                message = "provider rate limit",
                code = "RATE_LIMIT",
                status = 429,
                providerRetryAfterMs = 2_000,
                requestId = ProviderRequestId("provider-request-9"),
            )
        runtime.install(
            KoogLlmPlugin(
                executorFactory = KoogPromptExecutorFactory {
                    RecordingPromptExecutor(streamingError = providerError)
                },
                routes = listOf(testKoogRoute()),
                failureClassifier =
                    KoogProviderFailureClassifier { error, context ->
                        assertEquals("test", context.provider)
                        assertEquals("test-model", context.model)
                        if (error === providerError) expected else null
                    },
            )
        )

        val finish =
            runtime.context.llm.stream(
                GenerateOptions(
                    provider = "test",
                    model = "test-model",
                    messages = listOf(createUserMessage(listOf(TextBlock("hello")))),
                )
            ).toList().single()

        assertEquals(
            expected,
            assertIs<ErrorFinishReason>(assertIs<FinishChunk>(finish).reason).failure,
        )
        runtime.context.dispose()
    }

}
