package im.hikaru.harness.mcp.client

import com.sun.net.httpserver.HttpServer
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.sse.SSE
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StdioClientTransport
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.shared.AbstractTransport
import io.modelcontextprotocol.kotlin.sdk.shared.RequestOptions
import io.modelcontextprotocol.kotlin.sdk.shared.TransportSendOptions
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCMessage
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCRequest
import io.modelcontextprotocol.kotlin.sdk.types.JSONRPCResponse
import io.modelcontextprotocol.kotlin.sdk.types.LATEST_PROTOCOL_VERSION
import io.modelcontextprotocol.kotlin.sdk.types.ListToolsRequest
import io.modelcontextprotocol.kotlin.sdk.types.ListToolsResult
import io.modelcontextprotocol.kotlin.sdk.types.McpJson
import io.modelcontextprotocol.kotlin.sdk.types.PaginatedRequestParams
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.io.Buffer
import kotlinx.io.writeString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Characterizes the explicitly selected SDK before any production client is installed.
 * Green tests document observed SDK behavior, not acceptance of the DSH compatibility gate.
 * In particular, the schema-loss assertion below records a BLOCKER, not a supported projection.
 */
class McpSdkCompatibilityTest {
    @Test
    fun stdioTransportCallbackAlreadyReceivesALossyTypedSchema() = runTest(timeout = 10.seconds) {
        withContext(Dispatchers.Default) {
            val received = CompletableDeferred<JSONRPCMessage>()
            val wire = response(JsonPrimitive(7), toolsPage()).toString()
            val transport = StdioClientTransport(
                input = Buffer().apply { writeString("$wire\n") },
                output = Buffer(),
            )
            transport.onMessage { received.complete(it) }
            try {
                withTimeout(3_000) {
                    transport.start()
                    val message = assertIs<JSONRPCResponse>(received.await())
                    // No Client.listTools call yet: narrowing happens BEFORE the public callback.
                    val result = assertIs<ListToolsResult>(message.result)
                    val schema = McpJson.encodeToJsonElement(result.tools.single().inputSchema).jsonObject
                    assertEquals(inputSchema["properties"], schema["properties"])
                    assertEquals(inputSchema["required"], schema["required"])
                    assertEquals(inputSchema["\$defs"], schema["\$defs"])
                    assertEquals(JsonPrimitive(false), inputSchema["additionalProperties"])
                    assertFalse("additionalProperties" in schema)
                    assertFalse("allOf" in schema)
                    assertFalse("\$schema" in schema)
                    assertEquals(setOf("type", "properties", "required", "\$defs"), schema.keys)
                }
            } finally {
                transport.close()
            }
        }
    }

    @Test
    fun streamableHttpInitializesPagesAndCallsWithRepositoryKtorVersion() = runTest(timeout = 15.seconds) {
        withContext(Dispatchers.Default) {
            val fixture = HttpFixture()
            val http = HttpClient(CIO) {
                followRedirects = false
                install(SSE)
            }
            val client = Client(Implementation("harness-sdk-compatibility-test", "1"))
            try {
                withTimeout(8_000) {
                    client.connect(StreamableHttpClientTransport(http, fixture.url))
                    assertEquals("fixture instructions", client.serverInstructions)
                    val first = client.listTools()
                    assertEquals(listOf("echo"), first.tools.map { it.name })
                    assertEquals("page-2", first.nextCursor)
                    val second = client.listTools(ListToolsRequest(PaginatedRequestParams(cursor = first.nextCursor)))
                    assertEquals(listOf("second"), second.tools.map { it.name })
                    assertEquals(null, second.nextCursor)
                    val result = client.callTool(CallToolRequest(CallToolRequestParams(
                        name = "echo", arguments = buildJsonObject { put("message", "hello") },
                    )))
                    assertEquals("hello", assertIs<TextContent>(result.content.single()).text)
                    // The HTTP path narrows the schema too, not just StdioClientTransport.
                    val schema = McpJson.encodeToJsonElement(first.tools.single().inputSchema).jsonObject
                    assertFalse("additionalProperties" in schema)
                    assertTrue("notifications/initialized" in fixture.methods)
                }
            } finally {
                try { withTimeout(2_000) { client.close() } } finally {
                    http.close()
                    fixture.close()
                }
            }
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun requestOptionsTimeoutDoesNotBoundAResponseAfterSendReturns() = runTest(timeout = 5.seconds) {
        val transport = HangingToolTransport()
        val client = Client(Implementation("harness-deadline-test", "1"))
        client.connect(transport)
        val pending = async {
            client.callTool(CallToolRequest(CallToolRequestParams("never-replies")), RequestOptions(timeout = 100.milliseconds))
        }
        try {
            runCurrent()
            assertTrue(transport.callSent)
            advanceTimeBy(500)
            runCurrent()
            // Protocol.request in 0.11.0 applies the deadline to send(), not result.await().
            assertFalse(pending.isCompleted)
        } finally {
            pending.cancelAndJoin()
            client.close()
        }
    }

    @Test
    fun aHostOwnedOuterDeadlineCanBoundTheSdkResponseWait() = runTest(timeout = 5.seconds) {
        val client = Client(Implementation("harness-outer-deadline-test", "1"))
        client.connect(HangingToolTransport())
        try {
            assertFailsWith<TimeoutCancellationException> {
                withTimeout(100) {
                    client.callTool(CallToolRequest(CallToolRequestParams("never-replies")))
                }
            }
        } finally {
            client.close()
        }
    }

    private class HangingToolTransport : AbstractTransport() {
        var callSent = false
        override suspend fun start() = Unit
        override suspend fun send(message: JSONRPCMessage, options: TransportSendOptions?) {
            if (message is JSONRPCRequest) {
                when (message.method) {
                    "initialize" -> _onMessage(McpJson.decodeFromJsonElement<JSONRPCMessage>(
                        response(McpJson.encodeToJsonElement(message.id), initializeResult()),
                    ))
                    "tools/call" -> callSent = true
                    else -> Unit
                }
            }
        }
        override suspend fun close() = invokeOnCloseCallback()
    }

    /** External wire fixture only; production transport/framing remains entirely SDK-owned. */
    private class HttpFixture : AutoCloseable {
        val methods = ConcurrentLinkedQueue<String>()
        private val executor = Executors.newCachedThreadPool { task ->
            Thread(task, "mcp-sdk-http-fixture").apply { isDaemon = true }
        }
        private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            executor = this@HttpFixture.executor
            createContext("/mcp") { exchange ->
                exchange.use {
                    if (exchange.requestMethod != "POST") {
                        exchange.sendResponseHeaders(404, -1)
                        return@createContext
                    }
                    val body = exchange.requestBody.readNBytes(1024 * 1024 + 1)
                    check(body.size <= 1024 * 1024)
                    val request = McpJson.parseToJsonElement(body.decodeToString()).jsonObject
                    val method = request.getValue("method").jsonPrimitive.content
                    methods.add(method)
                    val id = request["id"]
                    if (id == null) {
                        exchange.sendResponseHeaders(202, -1)
                        return@createContext
                    }
                    val result = when (method) {
                        "initialize" -> initializeResult()
                        "tools/list" -> if (request["params"]?.jsonObject?.get("cursor") == null) toolsPage() else
                            buildJsonObject {
                                put("tools", buildJsonArray {
                                    add(buildJsonObject {
                                        put("name", "second")
                                        put("inputSchema", buildJsonObject { put("type", "object") })
                                    })
                                })
                            }
                        "tools/call" -> buildJsonObject {
                            put("content", buildJsonArray {
                                add(buildJsonObject {
                                    put("type", "text")
                                    put("text", request.getValue("params").jsonObject.getValue("arguments").jsonObject.getValue("message"))
                                })
                            })
                        }
                        else -> error("Unexpected fixture method")
                    }
                    val bytes = response(id, result).toString().encodeToByteArray()
                    exchange.responseHeaders.add("Content-Type", "application/json")
                    exchange.sendResponseHeaders(200, bytes.size.toLong())
                    exchange.responseBody.write(bytes)
                }
            }
            start()
        }
        val url: String get() = "http://127.0.0.1:${server.address.port}/mcp"
        override fun close() {
            server.stop(0)
            executor.shutdownNow()
        }
    }

    private companion object {
        val inputSchema: JsonObject = McpJson.parseToJsonElement("""
            {
              "type": "object",
              "${'$'}schema": "https://json-schema.org/draft/2020-12/schema",
              "properties": {"message": {"${'$'}ref": "#/${'$'}defs/message"}},
              "required": ["message"],
              "${'$'}defs": {"message": {"type": "string"}},
              "additionalProperties": false,
              "allOf": [{"properties": {"message": {"minLength": 1}}}]
            }
        """).jsonObject

        fun toolsPage(): JsonObject = buildJsonObject {
            put("tools", JsonArray(listOf(buildJsonObject {
                put("name", "echo")
                put("description", "Controlled compatibility fixture")
                put("inputSchema", inputSchema)
            })))
            put("nextCursor", "page-2")
        }

        fun initializeResult(): JsonObject = buildJsonObject {
            put("protocolVersion", LATEST_PROTOCOL_VERSION)
            put("capabilities", buildJsonObject { put("tools", buildJsonObject {}) })
            put("serverInfo", buildJsonObject { put("name", "fixture"); put("version", "1") })
            put("instructions", "fixture instructions")
        }

        fun response(id: kotlinx.serialization.json.JsonElement, result: JsonObject): JsonObject = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put("result", result)
        }
    }
}
