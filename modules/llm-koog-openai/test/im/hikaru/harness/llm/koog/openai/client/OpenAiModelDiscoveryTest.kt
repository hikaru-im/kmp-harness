package im.hikaru.harness.llm.koog.openai.client

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class OpenAiModelDiscoveryTest {
    @Test
    fun readsCompatibleEndpointAndMetadataWithoutInventingOutputDefaults() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var path = ""
        var authorization = ""
        server.createContext("/") { exchange ->
            path = exchange.requestURI.path
            authorization = exchange.requestHeaders.getFirst("Authorization")
            val bytes = """{"data":[{"id":"chat-a","name":"Chat A","context_window":64000,"max_output_tokens":8000},{"id":"chat-a"},{"id":"chat-b"}]}""".toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val models = OpenAiModelDiscovery().fetch("http://127.0.0.1:${server.address.port}/v1/", "fixture-key")
            assertEquals("/v1/models", path)
            assertEquals("Bearer fixture-key", authorization)
            assertEquals(listOf("chat-a", "chat-b"), models.map { it.id })
            assertEquals("Chat A", models.first().name)
            assertEquals(64000, models.first().contextWindow)
            assertNull(models.first().maxTokens)
        } finally { server.stop(0) }
    }

    @Test
    fun fallsBackOnlyForNotFoundAndNeverExposesResponseBody() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val paths = mutableListOf<String>()
        var unauthorized = false
        server.createContext("/") { exchange ->
            paths += exchange.requestURI.path
            val code = if (unauthorized) 401 else if (exchange.requestURI.path == "/v1/models") 404 else 200
            val body = if (code == 200) """{"data":[{"id":"vendor-model"}]}""" else "echoed-fixture-secret"
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(code, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try {
            val url = "http://127.0.0.1:${server.address.port}"
            assertEquals("vendor-model", OpenAiModelDiscovery().fetch(url, "fixture-key").single().id)
            assertEquals(listOf("/v1/models", "/models"), paths)
            paths.clear()
            unauthorized = true
            val failure = assertFailsWith<ModelDiscoveryException> { OpenAiModelDiscovery().fetch(url, "fixture-key") }
            assertEquals(listOf("/v1/models"), paths)
            assertFalse(failure.toString().contains("echoed-fixture-secret"))
            assertFalse(failure.toString().contains("fixture-key"))
        } finally { server.stop(0) }
    }

    @Test
    fun refusesEmptyMalformedAndPartialCatalogs() {
        val discovery = OpenAiModelDiscovery()
        listOf("""{"data":[]}""", """{"data":[{"name":"missing id"}]}""",
            """{"data":[{"id":"has spaces"}]}""", """{"data":[{"id":"a"}],"has_more":true}""")
            .forEach { body -> assertFails { discovery.parse(body) } }
    }
}
