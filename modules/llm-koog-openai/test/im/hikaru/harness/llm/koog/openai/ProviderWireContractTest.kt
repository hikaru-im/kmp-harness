package im.hikaru.harness.llm.koog.openai

import com.sun.net.httpserver.HttpServer
import im.hikaru.harness.credentials.*
import im.hikaru.harness.llm.*
import im.hikaru.harness.llm.koog.*
import im.hikaru.harness.llm.koog.openai.providers.BuiltinProviderContributions
import im.hikaru.harness.runtime.Runtime
import im.hikaru.harness.settings.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import kotlin.test.*

/** Final serialized HTTP requests, including SDK transformation and history conversion. No real API. */
class ProviderWireContractTest {
    @Test
    fun repeatedAndConcurrentReasoningFramesMustRemainBoundToTheirOwnStream() = runTest {
        WireServer(reasoningByPrompt = true).use { server ->
            val runtime = runtime(listOf(profile("deepseek", "deepseek", "deepseek-v4-pro", server.base + "/v1")))
            try {
                val calls = listOf("marker-a", "marker-b").map { marker -> async {
                    val chunks = runtime.context.llm.stream(options("deepseek", "deepseek-v4-pro").copy(
                        messages = listOf(createUserMessage(listOf(TextBlock(marker)))))).toList()
                    assertCompleted(chunks)
                    assertEquals(marker + marker, chunks.filterIsInstance<BlockEndChunk>().map { it.block }.filterIsInstance<ReasoningBlock>().single().text)
                } }
                calls.forEach { it.await() }
            } finally { runtime.context.dispose() }
        }
    }
    @Test
    fun vendorControlsMustReachTheWireWithTheirNativeShapes() = runTest {
        WireServer().use { server ->
            val cases = listOf(
                Triple("deepseek", "deepseek-v4-pro", ReasoningOptions(ReasoningModeId.ENABLED, ReasoningEffortId("max"))),
                Triple("alibaba", "qwen3.8-max", ReasoningOptions(ReasoningModeId.ENABLED, budgetTokens = 0)),
                Triple("moonshot", "kimi-k3", ReasoningOptions(ReasoningModeId.ENABLED, ReasoningEffortId("max"))),
                Triple("zhipu", "glm-5.2", ReasoningOptions(ReasoningModeId.DISABLED)),
            )
            val profiles = cases.map { (vendor, model, _) -> profile(vendor, vendor, model, server.base + "/$vendor/v1") }
            val runtime = runtime(profiles)
            try {
                cases.forEach { (vendor, model, reasoning) ->
                    assertCompleted(runtime.context.llm.stream(options(vendor, model).copy(reasoning = reasoning, maxTokens = 4096)).toList())
                }
                val requests = server.requests.associateBy { it.path.split('/')[1] }
                val deepseek = requests.getValue("deepseek").body
                assertEquals("enabled", deepseek.getValue("thinking").jsonObject.getValue("type").jsonPrimitive.content)
                assertEquals("max", deepseek.getValue("reasoning_effort").jsonPrimitive.content)
                assertEquals(4096, deepseek.getValue("max_tokens").jsonPrimitive.int)
                val qwen = requests.getValue("alibaba").body
                assertEquals(true, qwen.getValue("enable_thinking").jsonPrimitive.boolean)
                assertEquals(0, qwen.getValue("thinking_budget").jsonPrimitive.int)
                assertEquals(4096, qwen.getValue("max_completion_tokens").jsonPrimitive.int)
                assertFalse("reasoning_effort" in qwen)
                val kimi = requests.getValue("moonshot").body
                assertEquals("max", kimi.getValue("reasoning_effort").jsonPrimitive.content)
                assertFalse("thinking" in kimi)
                val glm = requests.getValue("zhipu").body
                assertEquals("disabled", glm.getValue("thinking").jsonObject.getValue("type").jsonPrimitive.content)
                requests.forEach { (vendor, request) ->
                    assertEquals("/$vendor/v1/chat/completions", request.path)
                    assertEquals("Bearer test-$vendor", request.authorization)
                    assertFalse("additionalProperties" in request.body)
                }
            } finally { runtime.context.dispose() }
        }
    }

    @Test
    fun responsesEffortAndExplicitProviderDefaultMustNotBeLostOrRefilled() = runTest {
        WireServer().use { server ->
            val profile = profile("openai", "openai", "gpt-6.1-sol", server.base + "/v1", BuiltinProviderContributions.RESPONSES)
                .copy(models = listOf(KoogModelProfile("gpt-6.1-sol", reasoningEfforts = KoogReasoningEfforts.Supported(
                    listOf("low", "medium", "high", "xhigh", "max").map(::ReasoningEffortId), defaultEffort = ReasoningEffortId("high")))))
            val runtime = runtime(listOf(profile))
            try {
                assertCompleted(runtime.context.llm.stream(options("openai", "gpt-6.1-sol").copy(reasoning = ReasoningOptions(effort = ReasoningEffortId("max")))).toList())
                assertCompleted(runtime.context.llm.stream(options("openai", "gpt-6.1-sol").copy(reasoning = ReasoningOptions())).toList())
                assertCompleted(runtime.context.llm.stream(options("openai", "gpt-6.1-sol")).toList())
                assertEquals("/v1/responses", server.requests[0].path)
                assertEquals("max", server.requests[0].body.getValue("reasoning").jsonObject.getValue("effort").jsonPrimitive.content)
                assertEquals(false, server.requests[0].body.getValue("store").jsonPrimitive.boolean)
                assertTrue(JsonPrimitive("reasoning.encrypted_content") in server.requests[0].body.getValue("include").jsonArray)
                assertFalse("reasoning" in server.requests[1].body)
                assertFalse("reasoning_effort" in server.requests[0].body)
                assertEquals("high", server.requests[2].body.getValue("reasoning").jsonObject.getValue("effort").jsonPrimitive.content)
            } finally { runtime.context.dispose() }
        }
    }

    @Test
    fun compatibleResponsesMustUseNestedReasoningAndKeepTheirEndpointPrefix() = runTest {
        WireServer().use { server ->
            val runtime = runtime(listOf(
                profile("deepseek", "deepseek", "deepseek-v4-pro", server.base + "/deepseek", BuiltinProviderContributions.RESPONSES),
                profile("alibaba", "alibaba", "qwen3.8-max", server.base + "/compatible-mode/v1", BuiltinProviderContributions.RESPONSES)))
            try {
                assertCompleted(runtime.context.llm.stream(options("deepseek", "deepseek-v4-pro").copy(reasoning = ReasoningOptions(effort = ReasoningEffortId("max")))).toList())
                assertCompleted(runtime.context.llm.stream(options("alibaba", "qwen3.8-max").copy(reasoning = ReasoningOptions(ReasoningModeId.DISABLED))).toList())
                assertEquals(listOf("/deepseek/responses", "/compatible-mode/v1/responses"), server.requests.map { it.path })
                assertEquals(listOf("max", "none"), server.requests.map { it.body.getValue("reasoning").jsonObject.getValue("effort").jsonPrimitive.content })
                server.requests.forEach { request ->
                    assertFalse("reasoning_effort" in request.body)
                    assertFalse("thinking" in request.body)
                    assertFalse("enable_thinking" in request.body)
                    assertFalse("include" in request.body)
                }
            } finally { runtime.context.dispose() }
        }
    }

    @Test
    fun compatibleToolLoopMustPreserveReturnedReasoningAndToolCallIds() = runTest {
        WireServer(toolFirst = true).use { server ->
            val runtime = runtime(listOf(profile("deepseek", "deepseek", "deepseek-v4-pro", server.base + "/v1")))
            try {
                val first = runtime.context.llm.stream(options("deepseek", "deepseek-v4-pro")).toList()
                assertEquals(ToolCallsFinishReason, (first.last() as FinishChunk).reason)
                val blocks = first.filterIsInstance<BlockEndChunk>().map { it.block }
                assertEquals("private-step", blocks.filterIsInstance<ReasoningBlock>().single().text)
                val call = blocks.filterIsInstance<ToolCallBlock>().single()
                val history = listOf(createUserMessage(listOf(TextBlock("start"))),
                    createAssistantMessage(blocks, "deepseek", "deepseek-v4-pro"),
                    createToolResultMessage(call.id, listOf(TextBlock("tool-result")), false))
                assertCompleted(runtime.context.llm.stream(options("deepseek", "deepseek-v4-pro").copy(messages = history)).toList())
                val messages = server.requests[1].body.getValue("messages").jsonArray.map { it.jsonObject }
                val assistant = messages.single { it.getValue("role").jsonPrimitive.content == "assistant" }
                assertEquals("private-step", assistant.getValue("reasoning_content").jsonPrimitive.content)
                assertEquals(call.id.value, assistant.getValue("tool_calls").jsonArray.single().jsonObject.getValue("id").jsonPrimitive.content)
                val result = messages.single { it.getValue("role").jsonPrimitive.content == "tool" }
                assertEquals(call.id.value, result.getValue("tool_call_id").jsonPrimitive.content)
            } finally { runtime.context.dispose() }
        }
    }

    @Test
    fun connectionsAndPreparedCallsKeepTheirOwnEndpointAndCredentialAcrossReload() = runTest {
        WireServer().use { server ->
            val a = profile("a", "deepseek", "deepseek-v4-pro", server.base + "/a/v1")
            val b = profile("b", "deepseek", "deepseek-v4-pro", server.base + "/b/v1")
            val nativeDraft = KoogProviderSettings("draft", vendor = "anthropic", api = BuiltinProviderContributions.MESSAGES,
                baseUrl = "https://api.anthropic.com", models = listOf(KoogModelProfile("claude-unavailable")))
            val runtime = runtime(listOf(a, b, nativeDraft))
            try {
                assertEquals(setOf("a", "b"), runtime.context.llm.listProviders().map { it.id }.toSet())
                val prepared = runtime.context.llm.prepareCall(LlmCallConfig("a", "deepseek-v4-pro"))
                val scope = runtime.context.require(SettingsKey).scope(settingsNamespace("llm-koog"))
                scope.replace(KoogLlmSettings(mapOf("a" to a.copy(baseUrl = server.base + "/new/v1", credential = KoogCredentialRef("b")), "b" to b)).toJson())
                assertCompleted(prepared.stream(options("a", "deepseek-v4-pro")).toList())
                assertCompleted(runtime.context.llm.stream(options("a", "deepseek-v4-pro")).toList())
                assertCompleted(runtime.context.llm.stream(options("b", "deepseek-v4-pro")).toList())
                assertEquals(listOf("/a/v1/chat/completions", "/new/v1/chat/completions", "/b/v1/chat/completions"), server.requests.map { it.path })
                assertEquals(listOf("Bearer test-a", "Bearer test-b", "Bearer test-b"), server.requests.map { it.authorization })
                // A failed replacement must leave both active generations unchanged.
                scope.replace(KoogLlmSettings(mapOf("a" to a, "b" to b.copy(credential = KoogCredentialRef("missing")))).toJson())
                assertCompleted(runtime.context.llm.stream(options("a", "deepseek-v4-pro")).toList())
                assertEquals("/new/v1/chat/completions", server.requests.last().path)
            } finally { runtime.context.dispose() }
        }
    }

    private suspend fun runtime(profiles: List<KoogProviderSettings>): Runtime {
        val runtime = Runtime()
        runtime.install(SettingsPlugin(InMemorySettingsDocumentStore()))
        runtime.install(CredentialsPlugin(InMemoryCredentialProvider(profiles.associate { CredentialRef(it.provider) to "test-${it.provider}" })))
        runtime.install(LlmPlugin())
        runtime.install(OpenAiKoogPlugin())
        runtime.context.require(SettingsKey).scope(settingsNamespace("llm-koog")).replace(KoogLlmSettings(profiles.associateBy { it.provider }).toJson())
        return runtime
    }

    private fun profile(id: String, vendor: String, model: String, base: String, api: String = BuiltinProviderContributions.CHAT) =
        KoogProviderSettings(id, vendor = vendor, api = api, baseUrl = base, credential = KoogCredentialRef(id), models = listOf(KoogModelProfile(model)))

    private fun options(provider: String, model: String) = GenerateOptions(provider, model, listOf(createUserMessage(listOf(TextBlock("hello")))))
    private fun assertCompleted(chunks: List<StreamChunk>) { assertEquals(StopFinishReason, (chunks.last() as FinishChunk).reason) }
}

private data class CapturedRequest(val path: String, val authorization: String?, val body: JsonObject)
private class WireServer(private val toolFirst: Boolean = false, private val reasoningByPrompt: Boolean = false) : AutoCloseable {
    val requests = CopyOnWriteArrayList<CapturedRequest>()
    private val executor = Executors.newCachedThreadPool()
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        executor = this@WireServer.executor
        createContext("/") { exchange ->
            val body = Json.parseToJsonElement(exchange.requestBody.use { it.readAllBytes().decodeToString() }).jsonObject
            requests += CapturedRequest(exchange.requestURI.path, exchange.requestHeaders.getFirst("Authorization"), body)
            exchange.responseHeaders.add("Content-Type", "text/event-stream")
            exchange.sendResponseHeaders(200, 0)
            val frames = if (exchange.requestURI.path.endsWith("/responses")) responsesFrames
                else if (toolFirst && requests.size == 1) toolFrames
                else if (reasoningByPrompt) {
                    val marker = if (body.toString().contains("marker-a")) "marker-a" else "marker-b"
                    val reasoning = delta(buildJsonObject { put("reasoning_content", marker) }.toString())
                    listOf(reasoning, reasoning) + chatFrames
                } else chatFrames
            exchange.responseBody.bufferedWriter().use { writer ->
                frames.forEach { writer.append("data: ").append(it).append("\n\n"); writer.flush() }
                writer.append("data: [DONE]\n\n"); writer.flush()
            }
        }
        start()
    }
    val base = "http://127.0.0.1:${server.address.port}"
    override fun close() { server.stop(0); executor.shutdownNow() }
}

private fun delta(value: String, finish: String? = null): String =
    """{"id":"fixture","object":"chat.completion.chunk","created":1,"model":"fixture","choices":[{"index":0,"delta":$value${finish?.let { ",\"finish_reason\":\"$it\"" } ?: ""}}]}"""
private val chatFrames = listOf(delta("""{"content":"ok"}"""), delta("{}", "stop"))
private val toolFrames = listOf(delta("""{"reasoning_content":"private-step"}"""),
    delta("""{"tool_calls":[{"index":0,"id":"call-1","type":"function","function":{"name":"lookup","arguments":"{}"}}]}"""), delta("{}", "tool_calls"))
private val responsesFrames = listOf(
    """{"type":"response.output_text.delta","item_id":"msg-1","output_index":0,"content_index":0,"delta":"ok","sequence_number":0}""",
    """{"type":"response.completed","response":{"created_at":1,"id":"resp-1","model":"fixture","instructions":"fixture","output":[],"parallel_tool_calls":false,"status":"completed","text":{},"usage":{"input_tokens":1,"output_tokens":1,"total_tokens":2,"input_tokens_details":{"cached_tokens":0},"output_tokens_details":{"reasoning_tokens":0}}},"sequence_number":1}""",
)
