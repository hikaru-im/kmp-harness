package im.hikaru.harness.llm.koog.openai.providers

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import im.hikaru.harness.llm.*
import java.net.InetSocketAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import kotlin.test.*

class OfficialProviderModelDiscoveryTest {
    @Test
    fun descriptorsExposeExplicitRegionalProfilesAndUnverifiedGlmDiscovery() {
        val descriptors = BuiltinProviderContributions.descriptors().associateBy { it.id }
        assertEquals(setOf("openai", "anthropic", "google", "deepseek", "alibaba", "moonshot", "zhipu"), descriptors.keys)
        assertEquals(setOf("china", "international"), descriptors.getValue("moonshot").endpointProfiles.map { it.region }.toSet())
        assertTrue(descriptors.getValue("alibaba").endpointProfiles.single { it.id == "beijing" }.requiresWorkspace)
        assertEquals(setOf("standard", "coding-plan"), descriptors.getValue("zhipu").endpointProfiles.map { it.serviceVariant }.toSet())
        assertFalse(descriptors.getValue("zhipu").supportsDiscovery)
        assertEquals("unverified", descriptors.getValue("zhipu").discoveryStatus)
    }

    @Test
    fun openAiUsesOneExplicitEndpointAndAcceptsACompleteEmptyDirectory() = runBlocking {
        val paths = mutableListOf<String>()
        var empty = false
        fixture { exchange ->
            paths += exchange.requestURI.path
            assertEquals("Bearer fixture-key", exchange.requestHeaders.getFirst("Authorization"))
            exchange.respond(if (empty) """{"data":[]}""" else """{"data":[{"id":"chat-a","owned_by":"openai","shutdown_date":null},{"id":"chat-a"}]}""")
        }.use { server ->
            val discovery = OfficialProviderModelDiscovery("openai")
            val access = access("openai", server.base + "/v1/")
            val first = discovery.discover(access)
            assertEquals(listOf("chat-a"), first.models.map { it.id })
            assertNull(first.models.single().contextWindow)
            assertEquals(JsonNull, first.models.single().metadata["raw"]!!.jsonObject["shutdown_date"])
            empty = true
            assertEquals(LlmCatalogCompleteness.COMPLETE, discovery.discover(access).completeness)
            assertTrue(discovery.discover(access).models.isEmpty())
            assertTrue(paths.all { it == "/v1/models" })
        }
    }

    @Test
    fun claudeFollowsCursorAndSendsWorkspaceWithoutLeakingItIntoTheUrl() = runBlocking {
        val queries = mutableListOf<String>()
        fixture { exchange ->
            queries += exchange.requestURI.rawQuery
            assertEquals("Bearer fixture-key", exchange.requestHeaders.getFirst("Authorization"))
            assertEquals("2023-06-01", exchange.requestHeaders.getFirst("anthropic-version"))
            assertEquals("wrkspc_fixture", exchange.requestHeaders.getFirst("anthropic-workspace-id"))
            exchange.respond(if (queries.size == 1) """{"data":[{"id":"claude-a","display_name":"Claude A","max_input_tokens":200000,"max_tokens":64000,"capabilities":{"thinking":{"supported":true,"types":{"adaptive":{"supported":true},"disabled":{"supported":false}}},"effort":{"supported":true,"high":{"supported":true}}}}],"has_more":true,"last_id":"cursor/+?"}"""
                else """{"data":[{"id":"claude-b","capabilities":null,"max_tokens":null}],"has_more":false,"last_id":"claude-b"}""")
        }.use { server ->
            val result = OfficialProviderModelDiscovery("anthropic").discover(access("anthropic", server.base, workspace = "wrkspc_fixture"))
            assertEquals(LlmCatalogCompleteness.COMPLETE, result.completeness)
            assertEquals(listOf("claude-a", "claude-b"), result.models.map { it.id })
            assertEquals(64000L, result.models.first().maxOutputTokens)
            assertNull(result.models.last().maxOutputTokens)
            assertEquals("limit=1000&after_id=cursor%2F%2B%3F", queries.last())
            assertTrue(queries.none { "fixture" in it })
        }
    }

    @Test
    fun geminiUsesHeaderAuthenticationAndPreservesSupportedGenerationMethods() = runBlocking {
        val queries = mutableListOf<String>()
        fixture { exchange ->
            assertEquals("/v1beta/models", exchange.requestURI.path)
            queries += exchange.requestURI.rawQuery
            assertNull(exchange.requestHeaders.getFirst("Authorization"))
            assertEquals("fixture-key", exchange.requestHeaders.getFirst("x-goog-api-key"))
            exchange.respond(if (queries.size == 1) """{"models":[{"name":"models/gemini-a","displayName":"Gemini A","inputTokenLimit":1000000,"outputTokenLimit":64000,"supportedGenerationMethods":["generateContent","countTokens"],"thinking":true}],"nextPageToken":"p/2"}"""
                else """{"models":[{"name":"models/embedding-a","supportedGenerationMethods":["embedContent"]}]}""")
        }.use { server ->
            val result = OfficialProviderModelDiscovery("google").discover(access("google", server.base + "/v1beta"))
            assertEquals(listOf("gemini-a", "embedding-a"), result.models.map { it.id })
            assertEquals(listOf("embedContent"), result.models.last().supportedOperations)
            assertEquals("pageSize=1000&pageToken=p%2F2", queries.last())
            assertEquals("input", result.models.first().metadata["context_limit_kind"]?.jsonPrimitive?.content)
        }
    }

    @Test
    fun qwenUsesNativeCatalogAndRetrievesAllPagesWithRichFacts() = runBlocking {
        var page = 0
        fixture { exchange ->
            page++
            assertEquals("/api/v1/models", exchange.requestURI.path)
            assertEquals("page_no=$page&page_size=100", exchange.requestURI.rawQuery)
            exchange.respond(if (page == 1) """{"output":{"total":2,"page_no":1,"page_size":100,"models":[{"model":"qwen3.8-max","name":"Qwen Max","provider":"qwen","inference_provider":"aliyun-bailian","capabilities":["TG","Reasoning"],"features":["function-calling"],"model_info":{"context_window":1000000,"max_output_tokens":64000,"max_reasoning_tokens":null},"inference_metadata":{"request_modality":["Text","Image","Video"],"response_modality":["Text"]}}]}}"""
                else """{"output":{"total":2,"page_no":2,"page_size":100,"models":[{"model":"kimi-k3","provider":"moonshot-ai","inference_provider":"moonshot"}]}}""")
        }.use { server ->
            val result = OfficialProviderModelDiscovery("alibaba").discover(access("alibaba", server.base + "/compatible-mode/v1"))
            assertEquals(2, page)
            assertEquals(LlmCatalogCompleteness.COMPLETE, result.completeness)
            assertEquals(64000L, result.models.first().maxOutputTokens)
            assertEquals(listOf(ModelModality.TEXT, ModelModality.IMAGE), result.models.first().inputModalities)
            assertEquals(listOf("TG", "Reasoning", "function-calling"), result.models.first().supportedOperations)
            assertEquals(JsonPrimitive("video"), result.models.first().metadata["input_modalities"]!!.jsonArray.last())
            assertEquals(JsonNull, result.models.first().metadata["raw"]!!.jsonObject["model_info"]!!.jsonObject["max_reasoning_tokens"])
        }
    }

    @Test
    fun deepSeekAndKimiKeepTheirActualMetadataAndDoNotInventEffortSupport() = runBlocking {
        fixture { exchange -> exchange.respond("""{"data":[{"id":"deepseek-flash","context_window":1048576,"max_output_tokens":393216,"input_modalities":["text","image"],"output_modalities":["text"],"effort":{"supported_levels":["low","high","max"],"default_level":"high"}}]}""") }.use { server ->
            val result = OfficialProviderModelDiscovery("deepseek").discover(access("deepseek", server.base))
            assertEquals(393216L, result.models.single().maxOutputTokens)
            assertEquals("high", result.models.single().metadata["raw"]!!.jsonObject["effort"]!!.jsonObject["default_level"]!!.jsonPrimitive.content)
        }
        fixture { exchange -> exchange.respond("""{"data":[{"id":"kimi-k3","context_length":1048576,"supports_image_in":true,"supports_video_in":true,"supports_reasoning":true}]}""") }.use { server ->
            val result = OfficialProviderModelDiscovery("moonshot").discover(access("moonshot", server.base + "/v1"))
            assertEquals(1048576L, result.models.single().contextWindow)
            assertNull(result.models.single().reasoningCapabilities)
            assertNull(result.models.single().maxOutputTokens)
        }
    }

    @Test
    fun glmReturnsAnExplicitlyUnverifiedBundledCatalogWithoutMakingARequest() = runBlocking {
        fixture { error("GLM discovery must not probe guessed network endpoints") }.use { server ->
            val result = OfficialProviderModelDiscovery("zhipu").discover(LlmProviderAccess(connection("zhipu", server.base)))
            assertEquals("bundled:zhipu:2026-10-21", result.source)
            assertEquals(LlmCatalogCompleteness.UNKNOWN, result.completeness)
            assertTrue(result.warnings.single().contains("未验证"))
            assertTrue(result.models.all { it.metadata["account_availability"]?.jsonPrimitive?.content == "unverified" })
        }
    }

    @Test
    fun partialFailureAndRepeatedCursorNeverBecomeACompleteDirectory() = runBlocking {
        var page = 0
        fixture { exchange ->
            page++
            if (page == 1) exchange.respond("""{"data":[{"id":"claude-a"}],"has_more":true,"last_id":"a"}""")
            else exchange.respond("echoed-fixture-key", 429)
        }.use { server ->
            val result = OfficialProviderModelDiscovery("anthropic").discover(access("anthropic", server.base))
            assertEquals(LlmCatalogCompleteness.PARTIAL, result.completeness)
            assertEquals(listOf("claude-a"), result.models.map { it.id })
            assertTrue(result.warnings.single().contains("RATE_LIMITED"))
            assertFalse(result.toString().contains("fixture-key"))
        }
        var cursorPage = 0
        fixture { exchange ->
            cursorPage++
            exchange.respond("""{"models":[{"name":"models/model-$cursorPage"}],"nextPageToken":"same"}""")
        }.use { server ->
            val result = OfficialProviderModelDiscovery("google").discover(access("google", server.base))
            assertEquals(2, cursorPage)
            assertEquals(LlmCatalogCompleteness.PARTIAL, result.completeness)
            assertTrue(result.warnings.single().contains("INVALID_PAGINATION_CURSOR"))
        }
    }

    @Test
    fun responseLimitsErrorsAndNotFoundAreBoundedSanitizedAndNeverRetriedElsewhere() = runBlocking {
        var requests = 0
        fixture { exchange ->
            requests++
            exchange.respond("fixture-key echoed server secret", 404)
        }.use { server ->
            val failure = assertFailsWith<ProviderDiscoveryFailure> {
                OfficialProviderModelDiscovery("openai").discover(access("openai", server.base))
            }
            assertEquals("NOT_FOUND", failure.code)
            assertEquals(1, requests)
            assertFalse(failure.toString().contains("fixture-key"))
            assertNull(failure.cause)
        }
        fixture { exchange -> exchange.respond("x".repeat(2048)) }.use { server ->
            val failure = assertFailsWith<ProviderDiscoveryFailure> {
                OfficialProviderModelDiscovery("openai", ProviderDiscoveryLimits(maxResponseBytes = 128)).discover(access("openai", server.base))
            }
            assertEquals("RESPONSE_TOO_LARGE", failure.code)
        }
    }

    @Test
    fun cancellationInterruptsAnUnfinishedBodyAndDoesNotBecomeAPartialSuccess() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        fixture { exchange ->
            exchange.sendResponseHeaders(200, 0)
            exchange.responseBody.write("{".toByteArray())
            exchange.responseBody.flush()
            started.complete(Unit)
            release.await(5, TimeUnit.SECONDS)
            exchange.close()
        }.use { server ->
            try {
                val request = async { OfficialProviderModelDiscovery("openai").discover(access("openai", server.base)) }
                withTimeout(3000) { started.await(); request.cancelAndJoin() }
                assertTrue(request.isCancelled)
            } finally { release.countDown() }
        }
    }

    @Test
    fun endpointProfilesRequireWorkspaceAndRejectCredentialBearingUrls() {
        val discovery = OfficialProviderModelDiscovery("alibaba")
        val template = "https://{workspace}.cn-beijing.maas.aliyuncs.com/compatible-mode/v1"
        assertFailsWith<ProviderDiscoveryFailure> { discovery.endpoint(connection("alibaba", template)) }
        assertEquals("https://ws-123.cn-beijing.maas.aliyuncs.com/api/v1/models", discovery.endpoint(connection("alibaba", template, "ws-123")).toString())
        listOf("https://key@example.com/v1", "https://example.com/v1?key=secret", "https://example.com/v1#secret", "http://example.com/v1").forEach {
            assertFailsWith<ProviderDiscoveryFailure> { discovery.endpoint(connection("alibaba", it)) }
        }
    }

    private fun connection(vendor: String, base: String, workspace: String? = null) = LlmProviderConnection(
        provider = "fixture-$vendor", vendor = vendor, api = when (vendor) {
            "anthropic" -> BuiltinProviderContributions.MESSAGES
            "google" -> BuiltinProviderContributions.GENERATE_CONTENT
            else -> BuiltinProviderContributions.CHAT
        }, baseUrl = base, workspace = workspace,
    )

    private fun access(vendor: String, base: String, workspace: String? = null) =
        LlmProviderAccess(connection(vendor, base, workspace), "fixture-key")

    private fun fixture(handler: (HttpExchange) -> Unit): FixtureServer = FixtureServer(handler)

    private class FixtureServer(handler: (HttpExchange) -> Unit) : AutoCloseable {
        private val executor = Executors.newCachedThreadPool { runnable -> Thread(runnable, "discovery-fixture").apply { isDaemon = true } }
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            this.executor = this@FixtureServer.executor
            createContext("/") { exchange -> handler(exchange) }
            start()
        }
        val base: String get() = "http://127.0.0.1:${server.address.port}"
        override fun close() { server.stop(0); executor.shutdownNow() }
    }
}

private fun HttpExchange.respond(body: String, status: Int = 200) {
    val bytes = body.toByteArray()
    sendResponseHeaders(status, bytes.size.toLong())
    responseBody.use { it.write(bytes) }
}
