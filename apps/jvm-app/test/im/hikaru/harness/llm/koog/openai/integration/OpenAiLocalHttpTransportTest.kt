package im.hikaru.harness.llm.koog.openai.integration

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import im.hikaru.harness.bundle.desktop.startDesktopProfile
import im.hikaru.harness.llm.BlockStartChunk
import im.hikaru.harness.boot.HarnessHost
import im.hikaru.harness.credentials.local.LocalCredentialsConfig
import im.hikaru.harness.desktop.ConfigurationSource
import im.hikaru.harness.desktop.DesktopFileConfiguration
import im.hikaru.harness.desktop.DesktopLlmProviderPlugin
import im.hikaru.harness.desktop.createDesktopProfile
import im.hikaru.harness.llm.BlockEndChunk
import im.hikaru.harness.llm.ErrorFinishReason
import im.hikaru.harness.llm.FinishChunk
import im.hikaru.harness.llm.GenerateOptions
import im.hikaru.harness.llm.LlmErrorCode
import im.hikaru.harness.llm.StopFinishReason
import im.hikaru.harness.llm.TextBlock
import im.hikaru.harness.llm.createUserMessage
import im.hikaru.harness.llm.llm
import im.hikaru.harness.llm.koog.openai.OpenAiKoogPlugin
import im.hikaru.harness.llm.koog.openai.catalog.OpenAiKoogCatalog
import im.hikaru.harness.llm.koog.openai.chat.OpenAiChatOptionMapper
import im.hikaru.harness.llm.koog.openai.responses.OpenAiResponsesOptionMapper
import im.hikaru.harness.settings.file.SettingsFileConfig
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.net.InetSocketAddress
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Exercises the default Koog/Ktor HTTP transport against a loopback server. */
class OpenAiLocalHttpTransportTest {

    @Test
    fun productionFileBackedProfileShouldUseRealHttpForChatAndResponses() = runTest {
        val home = Files.createTempDirectory("harness-openai-local-http")
        val settingsPath = home.resolve("settings.yaml")
        val credentialsPath = home.resolve(".credentials.yaml")
        val requests = CopyOnWriteArrayList<LocalHttpRequest>()
        val executor = Executors.newCachedThreadPool()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = executor
        server.createContext("/v1/chat/completions") { exchange ->
            handleSse(exchange, requests, chatResponse())
        }
        server.createContext("/v1/responses") { exchange ->
            handleSse(exchange, requests, responsesResponse())
        }
        server.start()

        val baseUrl = "http://127.0.0.1:${server.address.port}"
        try {
            Files.writeString(settingsPath, fileSettingsYaml("$baseUrl/v1"))
            Files.writeString(credentialsPath, "$CREDENTIAL_NAME: $TEST_API_KEY\n")
            setOwnerOnly(settingsPath)
            setOwnerOnly(credentialsPath)

            val session = startDesktopProfile(harnessHome = home, pollMillis = 25)
            try {
                val chat = session.host.runtime.context.llm.stream(chatOptions()).toList()
                val responses = session.host.runtime.context.llm.stream(responsesOptions()).toList()

                assertEquals(
                    TextBlock("chat-local"),
                    (chat.single { it is BlockEndChunk } as BlockEndChunk).block,
                )
                assertEquals(StopFinishReason, (chat.last() as FinishChunk).reason)
                assertEquals(
                    TextBlock("responses-local"),
                    (responses.single { it is BlockEndChunk } as BlockEndChunk).block,
                )
                assertEquals(StopFinishReason, (responses.last() as FinishChunk).reason)
            } finally {
                session.close()
            }

            assertEquals(listOf("POST", "POST"), requests.map(LocalHttpRequest::method))
            assertEquals(
                listOf("/v1/chat/completions", "/v1/responses"),
                requests.map(LocalHttpRequest::path),
            )
            requests.forEach { request ->
                assertEquals("Bearer $TEST_API_KEY", request.authorization)
                assertFalse(request.body.contains(TEST_API_KEY))
                assertFalse(request.path.contains(TEST_API_KEY))
                assertTrue(request.body.contains("\"stream\":true"))
            }
            assertTrue(requests[0].body.contains("\"messages\""))
            assertTrue(requests[1].body.contains("\"input\""))
        } finally {
            server.stop(0)
            executor.shutdownNow()
            home.toFile().deleteRecursively()
        }
    }

    @Test
    fun realHttpCallerCancellationShouldCloseChatConnection() = runTest {
        withStalledLocalHost(
            path = "/v1/chat/completions",
            initialFrame = chatResponse().first(),
        ) { host, transport ->
            val firstChunkObserved = CompletableDeferred<Unit>()
            val collection =
                launch {
                    host.runtime.context.llm.stream(chatOptions()).collect {
                        firstChunkObserved.complete(Unit)
                    }
                }
            firstChunkObserved.await()
            collection.cancelAndJoin()
            awaitTransportDisconnect(transport)

            assertEquals("POST", transport.request?.method)
            assertEquals("/v1/chat/completions", transport.request?.path)
            assertEquals("Bearer $TEST_API_KEY", transport.request?.authorization)
        }
    }

    @Test
    fun realHttpDownstreamEarlyStopShouldCloseResponsesConnection() = runTest {
        withStalledLocalHost(
            path = "/v1/responses",
            initialFrame = responsesResponse().first(),
        ) { host, transport ->
            val chunks = host.runtime.context.llm.stream(responsesOptions()).take(1).toList()
            assertEquals(listOf(BlockStartChunk(index = 0, blockType = "text")), chunks)
            awaitTransportDisconnect(transport)

            assertEquals("POST", transport.request?.method)
            assertEquals("/v1/responses", transport.request?.path)
            assertEquals("Bearer $TEST_API_KEY", transport.request?.authorization)
        }
    }

    @Test
    fun realHttpReadIdleTimeoutShouldFailAndCloseChatConnection() = runTest {
        withIdleLocalHost(
            path = "/v1/chat/completions",
            initialFrame = chatResponse().first(),
            socketTimeoutMillis = 150,
        ) { host, transport ->
            val chunks =
                withContext(Dispatchers.Default.limitedParallelism(1)) {
                    withTimeout(5_000) { host.runtime.context.llm.stream(chatOptions()).toList() }
                }

            assertTrue(chunks.isNotEmpty())
            val reason = (chunks.last() as FinishChunk).reason as ErrorFinishReason
            assertEquals("TIMEOUT", reason.failure.code)
            awaitTransportDisconnect(transport)
        }
    }

    @Test
    fun realHttpErrorsShouldMapStructuredChatAndResponsesFailures() = runTest {
        val cases =
            listOf(
                LocalErrorCase(
                    path = "/v1/chat/completions",
                    provider = OpenAiChatOptionMapper.OPENAI_CHAT_COMPLETIONS_API_ID,
                    status = 401,
                    body = """{"error":{"code":"invalid_api_key","message":"bad key"}}""",
                    expectedCode = LlmErrorCode.INVALID_CREDENTIAL,
                    expectedMessage = "bad key",
                ),
                LocalErrorCase(
                    path = "/v1/chat/completions",
                    provider = OpenAiChatOptionMapper.OPENAI_CHAT_COMPLETIONS_API_ID,
                    status = 429,
                    body = """{"error":{"type":"rate_limit_error","message":"slow down"}}""",
                    expectedCode = "RATE_LIMIT",
                    expectedMessage = "slow down",
                ),
                LocalErrorCase(
                    path = "/v1/chat/completions",
                    provider = OpenAiChatOptionMapper.OPENAI_CHAT_COMPLETIONS_API_ID,
                    status = 400,
                    body = """{"error":{"code":"context_length_exceeded","message":"too long"}}""",
                    expectedCode = LlmErrorCode.CONTEXT_WINDOW_EXCEEDED,
                    expectedMessage = "too long",
                ),
                LocalErrorCase(
                    path = "/v1/chat/completions",
                    provider = OpenAiChatOptionMapper.OPENAI_CHAT_COMPLETIONS_API_ID,
                    status = 503,
                    body = """{"error":{"message":"backend unavailable"}}""",
                    expectedCode = "SERVER",
                    expectedMessage = "backend unavailable",
                ),
                LocalErrorCase(
                    path = "/v1/responses",
                    provider = OpenAiResponsesOptionMapper.OPENAI_RESPONSES_API_ID,
                    status = 429,
                    body = """{"error":{"type":"rate_limit_error","message":"responses slow"}}""",
                    expectedCode = "RATE_LIMIT",
                    expectedMessage = "responses slow",
                ),
            )

        cases.forEach { testCase ->
            val request =
                withErrorLocalHost(
                    path = testCase.path,
                    status = testCase.status,
                    responseBody = testCase.body,
                ) { host ->
                    val chunks =
                        host.runtime.context.llm.stream(optionsFor(testCase.provider)).toList()
                    assertEquals(1, chunks.size)
                    val finish = chunks.single() as FinishChunk
                    val reason = finish.reason as ErrorFinishReason
                    assertEquals(testCase.expectedCode, reason.failure.code)
                    assertEquals(testCase.status, reason.failure.status)
                    assertEquals(testCase.expectedMessage, reason.failure.message)
                }

            assertEquals("POST", request?.method)
            assertEquals(testCase.path, request?.path)
            assertEquals("Bearer $TEST_API_KEY", request?.authorization)
            assertFalse(request?.body.orEmpty().contains(TEST_API_KEY))
        }
    }

    private suspend fun withStalledLocalHost(
        path: String,
        initialFrame: String,
        block: suspend (HarnessHost, StalledHttpTransport) -> Unit,
    ) {
        val home = Files.createTempDirectory("harness-openai-local-http-cancel")
        val settingsPath = home.resolve("settings.yaml")
        val credentialsPath = home.resolve(".credentials.yaml")
        val transport = StalledHttpTransport(initialFrame)
        val executor = Executors.newCachedThreadPool()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = executor
        server.createContext(path) { exchange -> transport.handle(exchange) }
        server.start()

        try {
            val baseUrl = "http://127.0.0.1:${server.address.port}"
            Files.writeString(settingsPath, fileSettingsYaml(baseUrl))
            Files.writeString(credentialsPath, "$CREDENTIAL_NAME: $TEST_API_KEY\n")
            setOwnerOnly(settingsPath)
            setOwnerOnly(credentialsPath)

            val host =
                HarnessHost.start(
                    createDesktopProfile(
                        providerPlugins = listOf(openAiProviderPlugin()),
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
                    ),
                )
            try {
                block(host, transport)
            } finally {
                host.close()
            }
        } finally {
            transport.stop()
            server.stop(0)
            executor.shutdownNow()
            home.toFile().deleteRecursively()
        }
    }

    private suspend fun withIdleLocalHost(
        path: String,
        initialFrame: String,
        socketTimeoutMillis: Long,
        block: suspend (HarnessHost, IdleHttpTransport) -> Unit,
    ) {
        val home = Files.createTempDirectory("harness-openai-local-http-idle")
        val settingsPath = home.resolve("settings.yaml")
        val credentialsPath = home.resolve(".credentials.yaml")
        val transport = IdleHttpTransport(initialFrame)
        val executor = Executors.newCachedThreadPool()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = executor
        server.createContext(path) { exchange -> transport.handle(exchange) }
        server.start()

        try {
            val baseUrl = "http://127.0.0.1:${server.address.port}"
            Files.writeString(settingsPath, fileSettingsYaml(baseUrl, socketTimeoutMillis))
            Files.writeString(credentialsPath, "$CREDENTIAL_NAME: $TEST_API_KEY\n")
            setOwnerOnly(settingsPath)
            setOwnerOnly(credentialsPath)

            val host =
                HarnessHost.start(
                    createDesktopProfile(
                        providerPlugins = listOf(openAiProviderPlugin()),
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
                    ),
                )
            try {
                block(host, transport)
            } finally {
                host.close()
            }
        } finally {
            transport.stop()
            server.stop(0)
            executor.shutdownNow()
            home.toFile().deleteRecursively()
        }
    }

    private suspend fun withErrorLocalHost(
        path: String,
        status: Int,
        responseBody: String,
        block: suspend (HarnessHost) -> Unit,
    ): LocalHttpRequest? {
        val home = Files.createTempDirectory("harness-openai-local-http-error")
        val settingsPath = home.resolve("settings.yaml")
        val credentialsPath = home.resolve(".credentials.yaml")
        val captured = AtomicReference<LocalHttpRequest?>()
        val executor = Executors.newCachedThreadPool()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.executor = executor
        server.createContext(path) { exchange ->
            val requestBody = exchange.requestBody.use { it.readAllBytes().decodeToString() }
            captured.set(
                LocalHttpRequest(
                    method = exchange.requestMethod,
                    path = exchange.requestURI.path,
                    authorization = exchange.requestHeaders.getFirst("Authorization"),
                    body = requestBody,
                )
            )
            val bytes = responseBody.encodeToByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { output -> output.write(bytes) }
        }
        server.start()

        try {
            val baseUrl = "http://127.0.0.1:${server.address.port}"
            Files.writeString(settingsPath, fileSettingsYaml(baseUrl))
            Files.writeString(credentialsPath, "$CREDENTIAL_NAME: $TEST_API_KEY\n")
            setOwnerOnly(settingsPath)
            setOwnerOnly(credentialsPath)

            val host =
                HarnessHost.start(
                    createDesktopProfile(
                        providerPlugins = listOf(openAiProviderPlugin()),
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
                    ),
                )
            try {
                block(host)
            } finally {
                host.close()
            }
        } finally {
            server.stop(0)
            executor.shutdownNow()
            home.toFile().deleteRecursively()
        }
        return captured.get()
    }

    private suspend fun awaitTransportDisconnect(transport: StalledHttpTransport) {
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(5_000) { transport.disconnected.await() }
        }
    }

    private suspend fun awaitTransportDisconnect(transport: IdleHttpTransport) {
        withContext(Dispatchers.Default.limitedParallelism(1)) {
            withTimeout(5_000) { transport.disconnected.await() }
        }
    }

    private fun openAiProviderPlugin(): DesktopLlmProviderPlugin =
        DesktopLlmProviderPlugin(
            id = "llm-koog-openai",
            plugin = OpenAiKoogPlugin(),
        )

    private fun chatOptions(): GenerateOptions =
        GenerateOptions(
            provider = OpenAiKoogCatalog.OPENAI_PROVIDER_ID,
            model = "gpt-4o-mini",
            messages = listOf(createUserMessage(listOf(TextBlock("hello")))),
            temperature = 0.2,
            maxTokens = 64,
        )

    private fun responsesOptions(): GenerateOptions =
        GenerateOptions(
            provider = OpenAiKoogCatalog.OPENAI_PROVIDER_ID,
            model = "o3-mini",
            messages = listOf(createUserMessage(listOf(TextBlock("hello")))),
            temperature = 0.2,
            maxTokens = 64,
        )

    private fun optionsFor(provider: String): GenerateOptions =
        if (provider == OpenAiResponsesOptionMapper.OPENAI_RESPONSES_API_ID) {
            responsesOptions()
        } else {
            chatOptions()
        }

    private fun fileSettingsYaml(baseUrl: String, socketTimeoutMillis: Long? = null): String =
        """
        llm-koog:
          providers:
            openai:
              displayName: OpenAI
              api: openai-chat-completions
              baseUrl: $baseUrl
              ${socketTimeoutMillis?.let { "socketTimeoutMillis: $it\n              " }.orEmpty()}credential:
                name: $CREDENTIAL_NAME
              models:
                - id: gpt-4o-mini
                  api: openai-chat-completions
                - id: o3-mini
                  api: openai-responses
        """.trimIndent() + "\n"

    private fun setOwnerOnly(path: Path) {
        runCatching {
            Files.setPosixFilePermissions(
                path,
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
            )
        }
    }

    private companion object {
        const val TEST_API_KEY = "fixture-local-http-key"
        const val CREDENTIAL_NAME = "OPENAI_LOCAL_HTTP_KEY"
    }
}

private data class LocalHttpRequest(
    val method: String,
    val path: String,
    val authorization: String?,
    val body: String,
)

private data class LocalErrorCase(
    val path: String,
    val provider: String,
    val status: Int,
    val body: String,
    val expectedCode: String,
    val expectedMessage: String,
)

private class StalledHttpTransport(
    private val initialFrame: String,
) {
    val disconnected = CompletableDeferred<Unit>()

    @Volatile
    var request: LocalHttpRequest? = null
        private set

    private val running = AtomicBoolean(true)

    fun handle(exchange: HttpExchange) {
        try {
            val body = exchange.requestBody.use { it.readAllBytes().decodeToString() }
            request =
                LocalHttpRequest(
                    method = exchange.requestMethod,
                    path = exchange.requestURI.path,
                    authorization = exchange.requestHeaders.getFirst("Authorization"),
                    body = body,
                )
            exchange.responseHeaders.add("Content-Type", "text/event-stream")
            exchange.responseHeaders.add("Cache-Control", "no-cache")
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.bufferedWriter().use { writer ->
                writer.append("data: ").append(initialFrame).append("\n\n")
                writer.flush()
                while (running.get()) {
                    writer.append("data: ").append(initialFrame).append("\n\n")
                    writer.flush()
                    Thread.sleep(20)
                }
            }
        } catch (error: IOException) {
            disconnected.complete(Unit)
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
        } finally {
            exchange.close()
        }
    }

    fun stop() {
        running.set(false)
    }
}

private class IdleHttpTransport(
    private val initialFrame: String,
) {
    val disconnected = CompletableDeferred<Unit>()

    @Volatile
    var request: LocalHttpRequest? = null
        private set

    private val running = AtomicBoolean(true)

    fun handle(exchange: HttpExchange) {
        try {
            val body = exchange.requestBody.use { it.readAllBytes().decodeToString() }
            request =
                LocalHttpRequest(
                    method = exchange.requestMethod,
                    path = exchange.requestURI.path,
                    authorization = exchange.requestHeaders.getFirst("Authorization"),
                    body = body,
                )
            exchange.responseHeaders.add("Content-Type", "text/event-stream")
            exchange.responseHeaders.add("Cache-Control", "no-cache")
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.bufferedWriter().use { writer ->
                writer.append("data: ").append(initialFrame).append("\n\n")
                writer.flush()
                Thread.sleep(1_000)
                while (running.get()) {
                    writer.append("data: ").append(initialFrame).append("\n\n")
                    writer.flush()
                    Thread.sleep(20)
                }
            }
        } catch (error: IOException) {
            disconnected.complete(Unit)
        } catch (error: InterruptedException) {
            Thread.currentThread().interrupt()
        } finally {
            exchange.close()
        }
    }

    fun stop() {
        running.set(false)
    }
}

private fun handleSse(
    exchange: HttpExchange,
    requests: MutableList<LocalHttpRequest>,
    frames: List<String>,
) {
    val body = exchange.requestBody.use { it.readAllBytes().decodeToString() }
    requests +=
        LocalHttpRequest(
            method = exchange.requestMethod,
            path = exchange.requestURI.path,
            authorization = exchange.requestHeaders.getFirst("Authorization"),
            body = body,
        )
    exchange.responseHeaders.add("Content-Type", "text/event-stream")
    exchange.responseHeaders.add("Cache-Control", "no-cache")
    exchange.sendResponseHeaders(200, 0)
    exchange.responseBody.bufferedWriter().use { writer ->
        frames.forEach { frame ->
            writer.append("data: ").append(frame).append("\n\n")
            writer.flush()
        }
        writer.append("data: [DONE]\n\n")
        writer.flush()
    }
}

private fun chatResponse(): List<String> =
    listOf(
        buildJsonObject {
            put("id", "chatcmpl-local")
            put("object", "chat.completion.chunk")
            put("created", 1)
            put("model", "gpt-4o-mini")
            putJsonArray("choices") {
                addJsonObject {
                    put("index", 0)
                    putJsonObject("delta") { put("content", "chat-local") }
                }
            }
        }.toString(),
        buildJsonObject {
            put("id", "chatcmpl-local")
            put("object", "chat.completion.chunk")
            put("created", 1)
            put("model", "gpt-4o-mini")
            putJsonArray("choices") {
                addJsonObject {
                    put("index", 0)
                    putJsonObject("delta") {}
                    put("finish_reason", "stop")
                }
            }
        }.toString(),
    )

private fun responsesResponse(): List<String> =
    listOf(
        buildJsonObject {
            put("type", "response.output_text.delta")
            put("item_id", "msg-local")
            put("output_index", 0)
            put("content_index", 0)
            put("delta", "responses-local")
            put("sequence_number", 0)
        }.toString(),
        buildJsonObject {
            put("type", "response.completed")
            putJsonObject("response") {
                put("created_at", 1)
                put("id", "resp-local")
                put("model", "gpt-4o-mini")
                putJsonArray("output") {}
                put("parallel_tool_calls", false)
                put("status", "completed")
                putJsonObject("text") {}
                putJsonObject("usage") {
                    put("input_tokens", 1)
                    put("output_tokens", 1)
                    put("total_tokens", 2)
                    putJsonObject("input_tokens_details") { put("cached_tokens", 0) }
                    putJsonObject("output_tokens_details") { put("reasoning_tokens", 0) }
                }
            }
            put("sequence_number", 1)
        }.toString(),
    )
