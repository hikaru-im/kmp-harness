package im.hikaru.harness.llm.koog.openai.integration

import ai.koog.http.client.KoogHttpClient
import ai.koog.prompt.executor.model.PromptExecutor
import im.hikaru.harness.boot.HarnessHost
import im.hikaru.harness.credentials.local.LocalCredentialsConfig
import im.hikaru.harness.desktop.ConfigurationSource
import im.hikaru.harness.desktop.DesktopFileConfiguration
import im.hikaru.harness.desktop.DesktopLlmProviderPlugin
import im.hikaru.harness.desktop.createDesktopProfile
import im.hikaru.harness.llm.BlockEndChunk
import im.hikaru.harness.llm.BlockStartChunk
import im.hikaru.harness.llm.CallId
import im.hikaru.harness.llm.FinishChunk
import im.hikaru.harness.llm.GenerateOptions
import im.hikaru.harness.llm.StopFinishReason
import im.hikaru.harness.llm.TextBlock
import im.hikaru.harness.llm.TextDeltaChunk
import im.hikaru.harness.llm.TokenUsage
import im.hikaru.harness.llm.ToolCallBlock
import im.hikaru.harness.llm.ToolCallDeltaChunk
import im.hikaru.harness.llm.ToolCallsFinishReason
import im.hikaru.harness.llm.ToolSchema
import im.hikaru.harness.llm.UsageChunk
import im.hikaru.harness.llm.createUserMessage
import im.hikaru.harness.llm.llm
import im.hikaru.harness.llm.koog.KoogCredentialResolver
import im.hikaru.harness.llm.koog.KoogLlmAdapter
import im.hikaru.harness.llm.koog.KoogModelProfile
import im.hikaru.harness.llm.koog.resolveRoutes
import im.hikaru.harness.llm.koog.openai.OpenAiKoogPlugin
import im.hikaru.harness.llm.koog.openai.OpenAiKoogPluginConfig
import im.hikaru.harness.llm.koog.openai.catalog.OpenAiKoogCatalog
import im.hikaru.harness.llm.koog.openai.chat.OpenAiChatOptionMapper
import im.hikaru.harness.llm.koog.openai.client.OpenAiPromptExecutorFactory
import im.hikaru.harness.llm.koog.openai.responses.OpenAiResponsesOptionMapper
import im.hikaru.harness.llm.koog.openai.semantics.OpenAiKoogSemantics
import im.hikaru.harness.settings.file.SettingsFileConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.util.ArrayDeque
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class OpenAiClientFixtureTest {

    @Test
    fun factoryShouldNormalizeVersionedBaseUrlBeforeCreatingTheKoogClient() = runTest {
        val http =
            ScriptedKoogHttpClientFactory(
                expectedApiKey = TEST_API_KEY,
                rawResponses = ArrayDeque(),
            )
        val settings =
            OpenAiKoogCatalog.defaultSettings(
                baseUrl = "$BASE_URL/v1/",
                credentialName = CREDENTIAL_NAME,
            )
        val executor =
            OpenAiPromptExecutorFactory(httpClientFactory = http)
                .create(
                    routes = settings.resolveRoutes(OpenAiKoogCatalog.installedRoutes()),
                    settings = settings.providers.values.toList(),
                    credentials = KoogCredentialResolver { TEST_API_KEY },
                )

        try {
            assertEquals(BASE_URL, http.creation?.baseUrl)
        } finally {
            executor.close()
        }
        assertEquals(1, http.closeCalls)
    }

    @Test
    fun fileBackedReloadShouldSwapGenerationKeepInFlightRequestAndRejectInvalidSettings() = runTest {
        val home = Files.createTempDirectory("harness-openai-reload")
        val settingsPath = home.resolve("settings.yaml")
        val credentialsPath = home.resolve(".credentials.yaml")
        val oldResponseStarted = CompletableDeferred<Unit>()
        val releaseOldResponse = CompletableDeferred<Unit>()
        val http =
            ScriptedKoogHttpClientFactory(
                expectedApiKeys =
                    listOf(TEST_API_KEY, TEST_API_KEY, "fixture-openai-key-v3"),
                rawResponses =
                    ArrayDeque<Flow<String>>().apply {
                        add(
                            flow {
                                oldResponseStarted.complete(Unit)
                                releaseOldResponse.await()
                                emit(textDelta("old"))
                                emit(finish("stop"))
                                emit(usage(promptTokens = 1, completionTokens = 1))
                                emit(DONE)
                            }
                        )
                        add(
                            flowOf(
                                textDelta("new"),
                                finish("stop"),
                                usage(promptTokens = 1, completionTokens = 1),
                                DONE,
                            )
                        )
                        add(
                            flowOf(
                                textDelta("credential"),
                                finish("stop"),
                                usage(promptTokens = 1, completionTokens = 1),
                                DONE,
                            )
                        )
                        add(
                            flowOf(
                                textDelta("credential"),
                                finish("stop"),
                                usage(promptTokens = 1, completionTokens = 1),
                                DONE,
                            )
                        )
                        add(
                            flowOf(
                                textDelta("credential"),
                                finish("stop"),
                                usage(promptTokens = 1, completionTokens = 1),
                                DONE,
                            )
                        )
                    },
            )

        try {
            Files.writeString(settingsPath, fileSettingsYaml(baseUrl = BASE_URL))
            Files.writeString(credentialsPath, "$FILE_CREDENTIAL_NAME: $TEST_API_KEY\n")
            setOwnerOnly(settingsPath)
            setOwnerOnly(credentialsPath)
            val host =
                HarnessHost.start(
                    createDesktopProfile(
                        providerPlugins = listOf(openAiProviderPlugin(http)),
                        configurationSource = ConfigurationSource.Files,
                        fileConfiguration =
                            DesktopFileConfiguration(
                                settings =
                                    SettingsFileConfig(
                                        path = settingsPath,
                                        watch = true,
                                        debounceMillis = 10,
                                    ),
                                credentials =
                                    LocalCredentialsConfig(
                                        path = credentialsPath,
                                        projectDir = home.resolve("project"),
                                        environment = { null },
                                        watch = true,
                                        debounceMillis = 10,
                                    ),
                            ),
                    )
                )
            try {
                val oldCall =
                    launch {
                        host.runtime.context.llm.stream(textOptions()).toList()
                    }
                oldResponseStarted.await()

                Files.writeString(settingsPath, fileSettingsYaml(baseUrl = "$BASE_URL/v2"))
                awaitCondition { http.creations.size == 2 }
                assertEquals(0, http.closeCalls)
                assertEquals(BASE_URL, http.creations[0].baseUrl)
                assertEquals("$BASE_URL/v2", http.creations[1].baseUrl)
                assertTrue(http.creations.all(ClientCreation::authorizationMatchesExpected))

                releaseOldResponse.complete(Unit)
                oldCall.join()
                awaitCondition { http.closeCalls == 1 }

                val newChunks = host.runtime.context.llm.stream(textOptions()).toList()
                assertEquals(TextBlock("new"), (newChunks[newChunks.lastIndex - 2] as BlockEndChunk).block)

                Files.writeString(credentialsPath, "$FILE_CREDENTIAL_NAME: fixture-openai-key-v3\n")
                awaitCondition { http.creations.size == 3 }
                assertEquals(2, http.closeCalls)
                assertTrue(http.creations[2].authorizationMatchesExpected)
                val credentialChunks = host.runtime.context.llm.stream(textOptions()).toList()
                assertEquals(
                    TextBlock("credential"),
                    (credentialChunks[credentialChunks.lastIndex - 2] as BlockEndChunk).block,
                )

                Files.writeString(settingsPath, "llm-koog: [invalid]\n")
                yield()
                assertEquals(3, http.creations.size)
                val fallbackChunks = host.runtime.context.llm.stream(textOptions()).toList()
                assertEquals(
                    TextBlock("credential"),
                    (fallbackChunks[fallbackChunks.lastIndex - 2] as BlockEndChunk).block,
                )

                Files.writeString(credentialsPath, "invalid-key: ignored\n")
                awaitCondition { http.closeCalls >= 2 }
                assertEquals(3, http.creations.size)
                assertEquals(2, http.closeCalls)
                val invalidCredentialFallback = host.runtime.context.llm.stream(textOptions()).toList()
                assertEquals(
                    TextBlock("credential"),
                    (invalidCredentialFallback[invalidCredentialFallback.lastIndex - 2] as BlockEndChunk)
                        .block,
                )
            } finally {
                host.close()
            }
            assertEquals(3, http.closeCalls)
        } finally {
            home.toFile().deleteRecursively()
        }
    }

    @Test
    fun fileBackedHostShouldRouteChatAndResponsesThroughFileConfiguration() = runTest {
        val home = Files.createTempDirectory("harness-openai-host")
        val settingsPath = home.resolve("settings.yaml")
        val credentialsPath = home.resolve(".credentials.yaml")
        val http =
            ScriptedKoogHttpClientFactory(
                expectedApiKey = TEST_API_KEY,
                rawResponses =
                    ArrayDeque<Flow<String>>().apply {
                        add(
                            flowOf(
                                textDelta("chat"),
                                finish("stop"),
                                usage(promptTokens = 2, completionTokens = 1),
                                DONE,
                            )
                        )
                        add(
                            flowOf(
                                responsesTextDelta("responses", sequenceNumber = 0),
                                responsesCompleted(
                                    inputTokens = 3,
                                    outputTokens = 1,
                                    sequenceNumber = 1,
                                ),
                                DONE,
                            )
                        )
                    },
            )

        try {
            Files.writeString(
                settingsPath,
                fileSettingsWithCustomChatModelYaml(baseUrl = BASE_URL),
            )
            Files.writeString(credentialsPath, "$FILE_CREDENTIAL_NAME: $TEST_API_KEY\n")
            setOwnerOnly(settingsPath)
            setOwnerOnly(credentialsPath)

            val host =
                HarnessHost.start(
                    createDesktopProfile(
                        providerPlugins =
                            listOf(
                                openAiProviderPlugin(
                                    http = http,
                                    baseUrl = "https://code-default.invalid",
                                    credentialName = "CODE_DEFAULT_KEY",
                                )
                            ),
                        configurationSource = ConfigurationSource.Files,
                        fileConfiguration =
                            DesktopFileConfiguration(
                                settings = SettingsFileConfig(path = settingsPath, watch = false),
                                credentials =
                                    LocalCredentialsConfig(
                                        path = credentialsPath,
                                        projectDir = home.resolve("project"),
                                        environment = { null },
                                        watch = false,
                                    ),
                            ),
                    )
                )
            try {
                assertEquals(
                    listOf(CUSTOM_CHAT_MODEL, "o3-mini"),
                    host.runtime.context.llm
                        .listModels(OpenAiKoogCatalog.OPENAI_PROVIDER_ID)
                        .map { model -> model.id },
                )
                val chat =
                    host.runtime.context.llm.stream(
                        textOptions(model = CUSTOM_CHAT_MODEL)
                    ).toList()
                val responses =
                    host.runtime.context.llm
                        .stream(responsesTextOptions(model = "o3-mini"))
                        .toList()

                assertEquals(TextBlock("chat"), (chat[chat.lastIndex - 2] as BlockEndChunk).block)
                assertEquals(StopFinishReason, (chat.last() as FinishChunk).reason)
                assertEquals(
                    TextBlock("responses"),
                    (responses[responses.lastIndex - 2] as BlockEndChunk).block,
                )
                assertEquals(StopFinishReason, (responses.last() as FinishChunk).reason)
                assertEquals(
                    listOf("v1/chat/completions", "v1/responses"),
                    http.requests.map(SseRequest::path),
                )
                assertEquals(
                    CUSTOM_CHAT_MODEL,
                    Json.parseToJsonElement(http.requests.first().body).jsonObject.string("model"),
                )
                http.requests.forEach { request ->
                    assertSafeTransportConfiguration(http, request)
                }
                assertEquals(0, http.closeCalls)
            } finally {
                host.close()
            }

            assertEquals(1, http.closeCalls)
        } finally {
            home.toFile().deleteRecursively()
        }
    }

    @Test
    fun responsesClientShouldSerializeTextRequestAndMapSseWithoutNetwork() = runTest {
        val fixture =
            responsesFixture(
                flowOf(
                    responsesTextDelta("hel", sequenceNumber = 0),
                    responsesTextDelta("lo", sequenceNumber = 1),
                    responsesCompleted(
                        inputTokens = 4,
                        outputTokens = 2,
                        sequenceNumber = 2,
                    ),
                    DONE,
                )
            )

        val chunks =
            try {
                fixture.adapter.stream(responsesTextOptions()).toList()
            } finally {
                fixture.executor.close()
            }

        assertEquals(
            listOf(
                BlockStartChunk(index = 0, blockType = "text"),
                TextDeltaChunk(index = 0, text = "hel"),
                TextDeltaChunk(index = 0, text = "lo"),
                BlockEndChunk(index = 0, block = TextBlock("hello")),
                UsageChunk(TokenUsage(inputTokens = 4, outputTokens = 2)),
                FinishChunk(StopFinishReason),
            ),
            chunks,
        )
        assertEquals(1, fixture.credentialResolutions)
        assertEquals(1, fixture.http.closeCalls)
        // Responses terminates on response.completed; unlike Chat Completions it does not
        // require a separate [DONE] sentinel to be filtered by the client.
        assertEquals(0, fixture.http.filteredDataCount)
        assertEquals(3, fixture.http.decodeCalls)

        val request = fixture.http.requests.single()
        assertSafeTransportConfiguration(fixture.http, request)
        assertEquals("v1/responses", request.path)
        assertEquals(String::class.qualifiedName, request.requestBodyType)
        assertEquals(emptyMap(), request.parameters)
        assertEquals(emptySet(), request.headerNames)

        val body = Json.parseToJsonElement(request.body).jsonObject
        assertEquals("gpt-4o-mini", body.string("model"))
        assertEquals(true, body.boolean("stream"))
        assertEquals(false, body.boolean("store"))
        assertEquals(0.2, body.double("temperature"))
        assertEquals(64, body.int("max_output_tokens"))
        assertFalse(body.containsKey("tools"))
        assertEquals(
            "hello",
            body.array("input")
                .single()
                .jsonObject
                .array("content")
                .single()
                .jsonObject
                .string("text"),
        )
    }

    @Test
    fun responsesClientShouldSerializeToolSchemaAndMapArgumentSseWithoutNetwork() = runTest {
        val fixture =
            responsesFixture(
                flowOf(
                    responsesToolDelta("fc-item-1", "{\"query\":", sequenceNumber = 0),
                    responsesToolDelta("fc-item-1", "\"Kotlin\"}", sequenceNumber = 1),
                    responsesToolDone(
                        id = "fc-item-1",
                        callId = "call-1",
                        name = "lookup",
                        arguments = "{\"query\":\"Kotlin\"}",
                        sequenceNumber = 2,
                    ),
                    responsesCompleted(
                        inputTokens = 12,
                        outputTokens = 5,
                        sequenceNumber = 3,
                    ),
                    DONE,
                )
            )

        val chunks =
            try {
                fixture.adapter
                    .stream(
                        responsesTextOptions(
                            tools = listOf(lookupTool()),
                        )
                    ).toList()
            } finally {
                fixture.executor.close()
            }

        assertEquals(
            listOf(
                BlockStartChunk(index = 0, blockType = "tool-call"),
                BlockEndChunk(
                    index = 0,
                    block =
                        ToolCallBlock(
                            id = CallId("call-1"),
                            name = "lookup",
                            arguments = "{\"query\":\"Kotlin\"}",
                        ),
                ),
                UsageChunk(TokenUsage(inputTokens = 12, outputTokens = 5)),
                FinishChunk(StopFinishReason),
            ),
            chunks,
        )

        val body = Json.parseToJsonElement(fixture.http.requests.single().body).jsonObject
        val tool = body.array("tools").single().jsonObject
        assertEquals("function", tool.string("type"))
        assertEquals("lookup", tool.string("name"))
        assertEquals("Look up a value", tool.string("description"))
        assertEquals("object", tool.objectValue("parameters").string("type"))
    }

    @Test
    fun responsesDownstreamEarlyStopShouldCancelRealClientTransport() = runTest {
        var transportStopped = false
        val fixture =
            responsesFixture(
                flow {
                    try {
                        emit(responsesTextDelta("hello", sequenceNumber = 0))
                        awaitCancellation()
                    } finally {
                        transportStopped = true
                    }
                }
            )

        val chunks =
            try {
                fixture.adapter.stream(responsesTextOptions()).take(1).toList()
            } finally {
                fixture.executor.close()
            }

        assertEquals(
            listOf(BlockStartChunk(index = 0, blockType = "text")),
            chunks,
        )
        assertTrue(transportStopped)
        assertEquals(1, fixture.http.closeCalls)
        assertEquals(1, fixture.http.requests.size)
    }

    @Test
    fun responsesCallerCancellationShouldCancelRealClientTransport() = runTest {
        val transportStarted = CompletableDeferred<Unit>()
        var transportStopped = false
        val fixture =
            responsesFixture(
                flow {
                    try {
                        transportStarted.complete(Unit)
                        awaitCancellation()
                    } finally {
                        transportStopped = true
                    }
                }
            )

        try {
            val collection =
                launch {
                    fixture.adapter.stream(responsesTextOptions()).collect()
                }
            transportStarted.await()
            collection.cancelAndJoin()
        } finally {
            fixture.executor.close()
        }

        assertTrue(transportStopped)
        assertEquals(1, fixture.http.closeCalls)
        assertEquals(1, fixture.http.requests.size)
    }

    @Test
    fun realClientShouldSerializeTextRequestAndMapSseWithoutNetwork() = runTest {
        val fixture =
            fixture(
                flowOf(
                    textDelta("hel"),
                    textDelta("lo"),
                    finish("stop"),
                    usage(promptTokens = 4, completionTokens = 2),
                    DONE,
                )
            )

        val chunks =
            try {
                fixture.adapter.stream(textOptions()).toList()
            } finally {
                fixture.executor.close()
            }

        assertEquals(
            listOf(
                BlockStartChunk(index = 0, blockType = "text"),
                TextDeltaChunk(index = 0, text = "hel"),
                TextDeltaChunk(index = 0, text = "lo"),
                BlockEndChunk(index = 0, block = TextBlock("hello")),
                UsageChunk(TokenUsage(inputTokens = 4, outputTokens = 2)),
                FinishChunk(StopFinishReason),
            ),
            chunks,
        )
        assertEquals(1, fixture.credentialResolutions)
        assertEquals(1, fixture.http.closeCalls)
        assertEquals(1, fixture.http.filteredDataCount)
        assertEquals(4, fixture.http.decodeCalls)

        val request = fixture.http.requests.single()
        assertSafeTransportConfiguration(fixture.http, request)
        assertEquals("v1/chat/completions", request.path)
        assertEquals(String::class.qualifiedName, request.requestBodyType)
        assertEquals(emptyMap(), request.parameters)
        assertEquals(emptySet(), request.headerNames)

        val body = Json.parseToJsonElement(request.body).jsonObject
        assertEquals("gpt-4o-mini", body.string("model"))
        assertEquals(true, body.boolean("stream"))
        assertEquals(0.2, body.double("temperature"))
        assertEquals(64, body.int("max_completion_tokens"))
        assertEquals(listOf("END"), body.array("stop").map { it.jsonPrimitive.content })
        assertEquals(true, body.objectValue("stream_options").boolean("include_usage"))
        assertEquals(emptyList(), body.array("tools"))

        val messages = body.array("messages")
        assertEquals(1, messages.size)
        assertEquals("user", messages.single().jsonObject.string("role"))
        assertEquals("hello", messages.single().jsonObject.string("content"))
    }

    @Test
    fun realClientShouldSerializeToolSchemaAndMapToolSseWithoutNetwork() = runTest {
        val fixture =
            fixture(
                flowOf(
                    toolDelta(
                        id = "call-1",
                        name = "lookup",
                        arguments = "{\"query\":",
                    ),
                    toolDelta(
                        id = null,
                        name = null,
                        arguments = "\"Kotlin\"}",
                    ),
                    finish("tool_calls"),
                    usage(promptTokens = 12, completionTokens = 5),
                    DONE,
                )
            )

        val chunks =
            try {
                fixture.adapter
                    .stream(
                        textOptions(
                            tools = listOf(lookupTool()),
                        )
                    ).toList()
            } finally {
                fixture.executor.close()
            }

        assertEquals(
            listOf(
                BlockStartChunk(index = 0, blockType = "tool-call"),
                ToolCallDeltaChunk(
                    index = 0,
                    id = CallId("call-1"),
                    name = "lookup",
                    argumentsDelta = "{\"query\":",
                ),
                ToolCallDeltaChunk(
                    index = 0,
                    id = CallId("call-1"),
                    name = null,
                    argumentsDelta = "\"Kotlin\"}",
                ),
                BlockEndChunk(
                    index = 0,
                    block =
                        ToolCallBlock(
                            id = CallId("call-1"),
                            name = "lookup",
                            arguments = "{\"query\":\"Kotlin\"}",
                        ),
                ),
                UsageChunk(TokenUsage(inputTokens = 12, outputTokens = 5)),
                FinishChunk(ToolCallsFinishReason),
            ),
            chunks,
        )
        assertEquals(1, fixture.http.closeCalls)

        val request = fixture.http.requests.single()
        assertSafeTransportConfiguration(fixture.http, request)
        val body = Json.parseToJsonElement(request.body).jsonObject
        val tools = body.array("tools")
        assertEquals(1, tools.size)
        val function = tools.single().jsonObject.objectValue("function")
        assertEquals("lookup", function.string("name"))
        assertEquals("Look up a value", function.string("description"))
        val parameters = function.objectValue("parameters")
        assertEquals("object", parameters.string("type"))
        assertEquals(
            listOf("query"),
            parameters.array("required").map { it.jsonPrimitive.content },
        )
        val query = parameters.objectValue("properties").objectValue("query")
        assertEquals("string", query.string("type"))
        assertEquals("Search query", query.string("description"))
    }

    @Test
    fun downstreamEarlyStopShouldCancelRealClientTransport() = runTest {
        var transportStopped = false
        val fixture =
            fixture(
                flow {
                    try {
                        emit(textDelta("hello"))
                        awaitCancellation()
                    } finally {
                        transportStopped = true
                    }
                }
            )

        val chunks =
            try {
                fixture.adapter.stream(textOptions()).take(1).toList()
            } finally {
                fixture.executor.close()
            }

        assertEquals(
            listOf(BlockStartChunk(index = 0, blockType = "text")),
            chunks,
        )
        assertTrue(transportStopped)
        assertEquals(1, fixture.http.closeCalls)
        assertEquals(1, fixture.http.requests.size)
    }

    @Test
    fun callerCancellationShouldCancelRealClientTransport() = runTest {
        val transportStarted = CompletableDeferred<Unit>()
        var transportStopped = false
        val fixture =
            fixture(
                flow {
                    try {
                        transportStarted.complete(Unit)
                        awaitCancellation()
                    } finally {
                        transportStopped = true
                    }
                }
            )

        try {
            val collection =
                launch {
                    fixture.adapter.stream(textOptions()).collect()
                }
            transportStarted.await()
            collection.cancelAndJoin()
        } finally {
            fixture.executor.close()
        }

        assertTrue(transportStopped)
        assertEquals(1, fixture.http.closeCalls)
        assertEquals(1, fixture.http.requests.size)
    }

    private suspend fun fixture(rawResponses: Flow<String>): Fixture {
        val http =
            ScriptedKoogHttpClientFactory(
                expectedApiKey = TEST_API_KEY,
                rawResponses = ArrayDeque<Flow<String>>().apply { add(rawResponses) },
            )
        var credentialResolutions = 0
        val credentials =
            KoogCredentialResolver { reference ->
                    assertEquals(CREDENTIAL_NAME, reference.name)
                    credentialResolutions++
                    TEST_API_KEY
                }
        val installedRoutes = OpenAiKoogCatalog.installedRoutes()
        val settings =
            OpenAiKoogCatalog.defaultSettings(
                baseUrl = "$BASE_URL/",
                credentialName = CREDENTIAL_NAME,
            )
        val routes = settings.resolveRoutes(installedRoutes)
        val executor =
            OpenAiPromptExecutorFactory(httpClientFactory = http)
                .create(
                    routes = routes,
                    settings = settings.providers.values.toList(),
                    credentials = credentials,
                )
        val semantics =
            OpenAiKoogSemantics.bundles().single {
                it.provider == OpenAiChatOptionMapper.OPENAI_CHAT_COMPLETIONS_API_ID
            }
        val adapter =
            KoogLlmAdapter(
                executor = executor,
                routes = routes,
                optionMapper = semantics.optionMapper,
                usageMapper = semantics.usageMapper,
                finishReasonMapper = semantics.finishReasonMapper,
                replayRestorer = semantics.replayRestorer,
                failureClassifier = semantics.failureClassifier,
                messageMapper = semantics.messageMapper,
                toolMapper = semantics.toolMapper,
                reasoningMapper = semantics.reasoningMapper,
                toolStreamMapper = semantics.toolStreamMapper,
                toolCallTerminalPolicy = semantics.toolCallTerminalPolicy,
            )
        return Fixture(
            adapter = adapter,
            executor = executor,
            http = http,
            credentialResolutions = credentialResolutions,
        )
    }

    private fun openAiProviderPlugin(
        http: KoogHttpClient.Factory,
        baseUrl: String? = null,
        credentialName: String = "OPENAI_API_KEY",
    ): DesktopLlmProviderPlugin =
        DesktopLlmProviderPlugin(
            id = "llm-koog-openai",
            plugin =
                OpenAiKoogPlugin(
                    OpenAiKoogPluginConfig(
                        baseUrl = baseUrl,
                        credentialName = credentialName,
                        httpClientFactory = http,
                    )
                ),
        )

    private suspend fun responsesFixture(rawResponses: Flow<String>): Fixture {
        val http =
            ScriptedKoogHttpClientFactory(
                expectedApiKey = TEST_API_KEY,
                rawResponses = ArrayDeque<Flow<String>>().apply { add(rawResponses) },
            )
        var credentialResolutions = 0
        val credentials =
            KoogCredentialResolver { reference ->
                    assertEquals(CREDENTIAL_NAME, reference.name)
                    credentialResolutions++
                    TEST_API_KEY
                }
        val installedRoutes = OpenAiKoogCatalog.installedRoutes()
        val settings =
            OpenAiKoogCatalog.defaultSettings(
                baseUrl = "$BASE_URL/",
                credentialName = CREDENTIAL_NAME,
                chatModels = emptyList(),
                responsesModels = listOf(KoogModelProfile(id = "gpt-4o-mini")),
            )
        val routes = settings.resolveRoutes(installedRoutes)
        val executor =
            OpenAiPromptExecutorFactory(
                httpClientFactory = http,
                providerId = OpenAiResponsesOptionMapper.OPENAI_RESPONSES_API_ID,
            ).create(
                routes = routes,
                settings = settings.providers.values.toList(),
                credentials = credentials,
            )
        val semantics =
            OpenAiKoogSemantics.bundles().single {
                it.provider == OpenAiResponsesOptionMapper.OPENAI_RESPONSES_API_ID
            }
        val adapter =
            KoogLlmAdapter(
                executor = executor,
                routes = routes,
                optionMapper = semantics.optionMapper,
                usageMapper = semantics.usageMapper,
                finishReasonMapper = semantics.finishReasonMapper,
                replayRestorer = semantics.replayRestorer,
                failureClassifier = semantics.failureClassifier,
                messageMapper = semantics.messageMapper,
                toolMapper = semantics.toolMapper,
                reasoningMapper = semantics.reasoningMapper,
                toolStreamMapper = semantics.toolStreamMapper,
                toolCallTerminalPolicy = semantics.toolCallTerminalPolicy,
                textTerminalPolicy = semantics.textTerminalPolicy,
            )
        return Fixture(
            adapter = adapter,
            executor = executor,
            http = http,
            credentialResolutions = credentialResolutions,
        )
    }

    private fun textOptions(
        tools: List<ToolSchema>? = null,
        model: String = "gpt-4o-mini",
    ): GenerateOptions =
        GenerateOptions(
            provider = OpenAiKoogCatalog.OPENAI_PROVIDER_ID,
            model = model,
            messages = listOf(createUserMessage(listOf(TextBlock("hello")))),
            tools = tools,
            temperature = 0.2,
            maxTokens = 64,
            stop = listOf("END"),
        )

    private fun responsesTextOptions(
        tools: List<ToolSchema>? = null,
        model: String = "gpt-4o-mini",
    ): GenerateOptions =
        GenerateOptions(
            provider = OpenAiKoogCatalog.OPENAI_PROVIDER_ID,
            model = model,
            messages = listOf(createUserMessage(listOf(TextBlock("hello")))),
            tools = tools,
            temperature = 0.2,
            maxTokens = 64,
        )

    private fun lookupTool(): ToolSchema =
        ToolSchema(
            name = "lookup",
            description = "Look up a value",
            parameters =
                buildJsonObject {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("query") {
                            put("type", "string")
                            put("description", "Search query")
                        }
                    }
                    putJsonArray("required") {
                        add("query")
                    }
                },
        )

    private fun assertSafeTransportConfiguration(
        http: ScriptedKoogHttpClientFactory,
        request: SseRequest,
    ) {
        val creation = assertNotNull(http.creation)
        assertEquals("OpenAILLMClient", creation.clientName)
        assertEquals(BASE_URL, creation.baseUrl)
        assertTrue(creation.authorizationMatchesExpected)
        assertEquals(emptySet(), creation.otherHeaderNames)
        assertEquals(emptyMap(), creation.queryParameters)
        assertFalse(request.bodyContainsCredential)
        assertFalse(request.pathContainsCredential)
        assertFalse(request.headersContainCredential)
    }

    private data class Fixture(
        val adapter: KoogLlmAdapter,
        val executor: PromptExecutor,
        val http: ScriptedKoogHttpClientFactory,
        val credentialResolutions: Int,
    )

    private companion object {
        private const val TEST_API_KEY = "fixture-openai-key"
        private const val CREDENTIAL_NAME = "openai-fixture-key"
        private const val FILE_CREDENTIAL_NAME = "OPENAI_FIXTURE_KEY"
        private const val CUSTOM_CHAT_MODEL = "fixture-chat-model"
        private const val BASE_URL = "https://fixture.openai.test"
        private const val DONE = "[DONE]"
    }

    private fun fileSettingsYaml(baseUrl: String): String =
        """
        llm-koog:
          providers:
            openai:
              displayName: OpenAI
              api: openai-chat-completions
              baseUrl: $baseUrl
              credential:
                name: $FILE_CREDENTIAL_NAME
              models:
                - id: gpt-4o-mini
                  api: openai-chat-completions
                - id: o3-mini
                  api: openai-responses
        """.trimIndent() + "\n"

    private fun fileSettingsWithCustomChatModelYaml(baseUrl: String): String =
        """
        llm-koog:
          providers:
            openai:
              displayName: OpenAI
              api: openai-chat-completions
              baseUrl: $baseUrl
              credential:
                name: $FILE_CREDENTIAL_NAME
              models:
                - id: $CUSTOM_CHAT_MODEL
                  api: openai-chat-completions
                  name: Fixture Chat Model
                  contextWindow: 65536
                  maxTokens: 4096
                  input: [text]
                - id: o3-mini
                  api: openai-responses
        """.trimIndent() + "\n"

    private fun setOwnerOnly(path: java.nio.file.Path) {
        Files.setPosixFilePermissions(
            path,
            setOf(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
            ),
        )
    }

    private suspend fun awaitCondition(condition: () -> Boolean) {
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(5_000) {
                while (!condition()) {
                    kotlinx.coroutines.delay(25)
                }
            }
        }
    }
}

private data class ClientCreation(
    val clientName: String,
    val baseUrl: String,
    val authorizationMatchesExpected: Boolean,
    val otherHeaderNames: Set<String>,
    val queryParameters: Map<String, String>,
)

private data class SseRequest(
    val path: String,
    val body: String,
    val requestBodyType: String?,
    val parameters: Map<String, String>,
    val headerNames: Set<String>,
    val bodyContainsCredential: Boolean,
    val pathContainsCredential: Boolean,
    val headersContainCredential: Boolean,
)

private class ScriptedKoogHttpClientFactory(
    expectedApiKey: String? = null,
    private val expectedApiKeys: List<String> = listOfNotNull(expectedApiKey),
    private val rawResponses: ArrayDeque<Flow<String>>,
) : KoogHttpClient.Factory {

    var creation: ClientCreation? = null
        private set

    val creations: MutableList<ClientCreation> = mutableListOf()

    val requests: MutableList<SseRequest> = mutableListOf()

    var filteredDataCount: Int = 0
        private set

    var decodeCalls: Int = 0
        private set

    var closeCalls: Int = 0
        private set

    override fun create(
        clientName: String,
        baseUrl: String,
        headers: Map<String, String>,
        queryParameters: Map<String, String>,
        requestTimeoutMillis: Long,
        connectTimeoutMillis: Long,
        socketTimeoutMillis: Long,
        json: Json,
    ): KoogHttpClient {
        val expectedApiKey =
            expectedApiKeys.getOrElse(creations.size) {
                expectedApiKeys.lastOrNull() ?: error("Fixture requires an expected API key")
            }
        val authorization =
            headers.entries.firstOrNull { (name, _) ->
                name.equals("Authorization", ignoreCase = true)
            }
        val clientCreation =
            ClientCreation(
                clientName = clientName,
                baseUrl = baseUrl,
                authorizationMatchesExpected =
                    authorization?.value == "Bearer $expectedApiKey",
                otherHeaderNames =
                    headers.keys.filterNot { name ->
                        name.equals("Authorization", ignoreCase = true)
                    }.toSet(),
                queryParameters = queryParameters.toMap(),
            )
        creations += clientCreation
        if (creation == null) creation = clientCreation
        return object : KoogHttpClient {
            override val clientName: String = clientName

            override suspend fun <R : Any> get(
                path: String,
                responseType: KClass<R>,
                parameters: Map<String, String>,
                headers: Map<String, String>,
            ): R = error("Fixture does not support GET")

            override suspend fun <T : Any, R : Any> post(
                path: String,
                requestBody: T,
                requestBodyType: KClass<T>,
                responseType: KClass<R>,
                parameters: Map<String, String>,
                headers: Map<String, String>,
            ): R = error("Fixture does not support POST")

            override fun <T : Any, R : Any, O : Any> sse(
                path: String,
                requestBody: T,
                requestBodyType: KClass<T>,
                dataFilter: (String?) -> Boolean,
                decodeStreamingResponse: (String) -> R,
                processStreamingChunk: (R) -> O?,
                parameters: Map<String, String>,
                headers: Map<String, String>,
            ): Flow<O> {
                val body = requestBody as? String
                    ?: error("OpenAI Chat Completions fixture expects a serialized String body")
                requests +=
                    SseRequest(
                        path = path,
                        body = body,
                        requestBodyType = requestBodyType.qualifiedName,
                        parameters = parameters.toMap(),
                        headerNames = headers.keys.toSet(),
                        bodyContainsCredential = body.contains(expectedApiKey),
                        pathContainsCredential = path.contains(expectedApiKey),
                        headersContainCredential =
                            headers.values.any { value -> value.contains(expectedApiKey) },
                    )
                val rawResponse =
                    rawResponses.pollFirst()
                        ?: error("No scripted OpenAI SSE response remains")
                return flow {
                    rawResponse.collect { data ->
                        if (dataFilter(data)) {
                            decodeCalls++
                            processStreamingChunk(decodeStreamingResponse(data))
                                ?.let { chunk -> emit(chunk) }
                        } else {
                            filteredDataCount++
                        }
                    }
                }
            }

            override fun <T : Any> lines(
                path: String,
                requestBody: T,
                requestBodyType: KClass<T>,
                parameters: Map<String, String>,
                headers: Map<String, String>,
            ): Flow<String> = error("Fixture does not support line streaming")

            override fun close() {
                closeCalls++
            }
        }
    }
}

private fun textDelta(text: String): String =
    streamChunk(
        choices =
            buildJsonArray {
                addJsonObject {
                    put("index", 0)
                    putJsonObject("delta") {
                        put("content", text)
                    }
                }
            }
    )

private fun toolDelta(
    id: String?,
    name: String?,
    arguments: String,
): String =
    streamChunk(
        choices =
            buildJsonArray {
                addJsonObject {
                    put("index", 0)
                    putJsonObject("delta") {
                        putJsonArray("tool_calls") {
                            addJsonObject {
                                put("index", 0)
                                put("id", id?.let(::JsonPrimitive) ?: JsonNull)
                                put("type", "function")
                                putJsonObject("function") {
                                    put("name", name?.let(::JsonPrimitive) ?: JsonNull)
                                    put("arguments", arguments)
                                }
                            }
                        }
                    }
                }
            }
    )

private fun finish(reason: String): String =
    streamChunk(
        choices =
            buildJsonArray {
                addJsonObject {
                    put("index", 0)
                    putJsonObject("delta") { }
                    put("finish_reason", reason)
                }
            }
    )

private fun usage(
    promptTokens: Int,
    completionTokens: Int,
): String =
    streamChunk(
        choices = buildJsonArray { },
        usage =
            buildJsonObject {
                put("prompt_tokens", promptTokens)
                put("completion_tokens", completionTokens)
                put("total_tokens", promptTokens + completionTokens)
            },
    )

private fun streamChunk(
    choices: JsonArray,
    usage: JsonObject? = null,
): String =
    buildJsonObject {
        put("id", "chatcmpl-fixture")
        put("object", "chat.completion.chunk")
        put("created", 1)
        put("model", "gpt-4o-mini")
        put("choices", choices)
        usage?.let { value -> put("usage", value) }
    }.toString()

private fun responsesTextDelta(
    text: String,
    sequenceNumber: Int,
): String =
    buildJsonObject {
        put("type", "response.output_text.delta")
        put("item_id", "msg-fixture")
        put("output_index", 0)
        put("content_index", 0)
        put("delta", text)
        put("sequence_number", sequenceNumber)
    }.toString()

private fun responsesToolDelta(
    itemId: String,
    delta: String,
    sequenceNumber: Int,
): String =
    buildJsonObject {
        put("type", "response.function_call_arguments.delta")
        put("item_id", itemId)
        put("output_index", 0)
        put("delta", delta)
        put("sequence_number", sequenceNumber)
    }.toString()

private fun responsesToolDone(
    id: String,
    callId: String,
    name: String,
    arguments: String,
    sequenceNumber: Int,
): String =
    buildJsonObject {
        put("type", "response.output_item.done")
        putJsonObject("item") {
            put("type", "function_call")
            put("arguments", arguments)
            put("call_id", callId)
            put("name", name)
            put("id", id)
            put("status", "completed")
        }
        put("output_index", 0)
        put("sequence_number", sequenceNumber)
    }.toString()

private fun responsesCompleted(
    inputTokens: Int,
    outputTokens: Int,
    sequenceNumber: Int,
): String =
    buildJsonObject {
        put("type", "response.completed")
        putJsonObject("response") {
            put("created_at", 1)
            put("id", "resp-fixture")
            put("model", "gpt-4o-mini")
            putJsonArray("output") { }
            put("parallel_tool_calls", false)
            put("status", "completed")
            putJsonObject("text") { }
            putJsonObject("usage") {
                put("input_tokens", inputTokens)
                putJsonObject("input_tokens_details") {
                    put("cached_tokens", 0)
                }
                put("output_tokens", outputTokens)
                putJsonObject("output_tokens_details") {
                    put("reasoning_tokens", 0)
                }
                put("total_tokens", inputTokens + outputTokens)
            }
        }
        put("sequence_number", sequenceNumber)
    }.toString()

private fun JsonObject.string(key: String): String =
    getValue(key).jsonPrimitive.content

private fun JsonObject.boolean(key: String): Boolean =
    getValue(key).jsonPrimitive.boolean

private fun JsonObject.double(key: String): Double =
    getValue(key).jsonPrimitive.double

private fun JsonObject.int(key: String): Int =
    getValue(key).jsonPrimitive.int

private fun JsonObject.array(key: String): JsonArray =
    getValue(key).jsonArray

private fun JsonObject.objectValue(key: String): JsonObject =
    getValue(key).jsonObject
