package im.hikaru.harness.mcp.client

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class McpConfigTest {
    @Test
    fun strictDecodeAndDefaults() {
        val config = decode("""{"serverName":"demo","transport":{"type":"stdio","command":"node"}}""")
        assertEquals(McpTransportConfig.Stdio("node"), config.transport)
        assertEquals(10_000L, config.startupTimeoutMs)
        assertEquals(60_000L, config.toolCallTimeoutMs)
        assertEquals(32_768, config.maxInstructionBytes)
        assertEquals(McpReconnectConfig(true, 10, 500, 30_000, 30_000), config.reconnect)
        assertFalse(config.failOnStartupError)
        assertNull(config.enabledTools)
        assertTrue(config.disabledTools.isEmpty())
        assertFailsWith<IllegalArgumentException> {
            decode("""{"serverName":"demo","transport":{"type":"stdio","command":"node","env":{"TOKEN":"secret"}}}""")
        }
        assertFailsWith<IllegalArgumentException> {
            decode("""{"serverName":"demo","transport":{"type":"http","url":"https://example.com"},"extra":true}""")
        }
    }

    @Test
    fun onlyReferencesAreSerialized() {
        val config = McpServerConfig(
            "remote",
            McpTransportConfig.Http("https://example.com/mcp", mapOf("X-Api-Key" to "API_KEY"), "BEARER_TOKEN"),
            enabledTools = setOf("read"),
            disabledTools = setOf("delete"),
        )
        val encoded = Json.encodeToString(config)
        assertEquals(config, decode(encoded))
        assertTrue("API_KEY" in encoded)
        assertTrue("BEARER_TOKEN" in encoded)
        assertFailsWith<IllegalArgumentException> {
            McpTransportConfig.Http("https://example.com", mapOf("Authorization" to "Bearer literal-secret"))
        }
    }

    @Test
    fun validatesServerNamesDurationsAndProcessInputs() {
        val transport = McpTransportConfig.Stdio("node")
        for (name in listOf("", "has.dot", "a".repeat(33), "name\u0000")) {
            assertFailsWith<IllegalArgumentException> { McpServerConfig(name, transport) }
        }
        McpServerConfig("A_0-".repeat(8), transport)
        for (duration in listOf(0L, -1L, 3_600_001L, Long.MAX_VALUE)) {
            assertFailsWith<IllegalArgumentException> { McpServerConfig("demo", transport, startupTimeoutMs = duration) }
            assertFailsWith<IllegalArgumentException> { McpServerConfig("demo", transport, toolCallTimeoutMs = duration) }
            assertFailsWith<IllegalArgumentException> { McpReconnectConfig(stableAfterMs = duration) }
        }
        assertFailsWith<IllegalArgumentException> { McpReconnectConfig(maxAttempts = 0) }
        assertFailsWith<IllegalArgumentException> { McpReconnectConfig(initialDelayMs = 200, maxDelayMs = 100) }
        assertFailsWith<IllegalArgumentException> { McpServerConfig("demo", transport, maxInstructionBytes = 0) }
        assertFailsWith<IllegalArgumentException> { McpServerConfig("demo", transport, maxInstructionBytes = Int.MAX_VALUE) }
        assertFailsWith<IllegalArgumentException> { McpTransportConfig.Stdio(" ") }
        assertFailsWith<IllegalArgumentException> { McpTransportConfig.Stdio("node\u0000") }
        assertFailsWith<IllegalArgumentException> { McpTransportConfig.Stdio("node", listOf("arg\u0000")) }
        assertFailsWith<IllegalArgumentException> { McpTransportConfig.Stdio("node", cwd = "cwd\u0000") }
        assertFailsWith<IllegalArgumentException> { McpTransportConfig.Stdio("node", envFrom = mapOf("1KEY" to "TOKEN")) }
        assertFailsWith<IllegalArgumentException> { McpTransportConfig.Stdio("node", envFrom = mapOf("KEY" to "literal-secret!")) }
    }

    @Test
    fun httpAllowsOnlyHttpsOrLiteralLoopbackAndSafeHeaders() {
        for (url in listOf("https://example.com/mcp", "http://localhost:3000/mcp", "http://127.0.0.1/mcp", "http://[::1]/mcp")) {
            McpTransportConfig.Http(url)
        }
        for (url in listOf(
            "http://example.com", "http://localhost.evil", "http://127.0.0.2", "http://2130706433",
            "http://[::ffff:127.0.0.1]", "ftp://localhost", "https://user:secret@example.com", "https://example.com/#fragment",
            "https://example.com:0", "https://example.com:99999", "https://bad host", "https:///missing-host",
        )) {
            assertFailsWith<IllegalArgumentException> { McpTransportConfig.Http(url) }
        }
        for (header in listOf("Host", "CONTENT-LENGTH", "Connection", "Accept", "Content-Type", "Mcp-Session-Id", "MCP-Protocol-Version", "Bad Header", "X\r\nHeader")) {
            assertFailsWith<IllegalArgumentException> {
                McpTransportConfig.Http("https://example.com", mapOf(header to "TOKEN"))
            }
        }
        assertFailsWith<IllegalArgumentException> {
            McpTransportConfig.Http("https://example.com", mapOf("X-Key" to "TOKEN", "x-key" to "OTHER"))
        }
        assertFailsWith<IllegalArgumentException> {
            McpTransportConfig.Http("https://example.com", mapOf("Authorization" to "TOKEN"), "OTHER")
        }
    }

    @Test
    fun processEnvironmentHasNarrowBaseAndExplicitMappings() {
        val parent = mapOf(
            "PATH" to "/bin", "HOME" to "/home/test", "LANG" to "en_US.UTF-8", "SystemRoot" to "C:\\Windows",
            "TOKEN" to "secret-value", "UNRELATED_SECRET" to "not-inherited", "JAVA_TOOL_OPTIONS" to "not-inherited",
        )
        assertEquals(
            mapOf("PATH" to "/bin", "HOME" to "/home/test", "LANG" to "en_US.UTF-8", "SystemRoot" to "C:\\Windows", "API_KEY" to "secret-value"),
            mcpProcessEnvironment(parent, mapOf("API_KEY" to "TOKEN")),
        )
        assertEquals("secret-value", mcpProcessEnvironment(parent, mapOf("PATH" to "TOKEN"))["PATH"])
        val error = assertFailsWith<IllegalArgumentException> { mcpProcessEnvironment(parent, mapOf("KEY" to "MISSING")) }
        assertEquals("Missing environment variable MISSING", error.message)
    }

    @Test
    fun validationAndDecodeNeverExposeSecretValuesOrCauses() {
        val secret = "super-secret-credential!"
        val errors = listOf(
            assertFailsWith<IllegalArgumentException> { McpTransportConfig.Http("https://user:$secret@example.com") },
            assertFailsWith<IllegalArgumentException> { McpTransportConfig.Http("https://bad host/$secret") },
            assertFailsWith<IllegalArgumentException> { McpTransportConfig.Stdio("node", envFrom = mapOf("TOKEN" to secret)) },
            assertFailsWith<IllegalArgumentException> { mcpProcessEnvironment(mapOf("TOKEN" to "$secret\u0000"), mapOf("KEY" to "TOKEN")) },
            assertFailsWith<IllegalArgumentException> {
                decode("""{"serverName":"demo","transport":{"type":"http","url":"https://example.com","headers":{"Authorization":"$secret"}}}""")
            },
            assertFailsWith<IllegalArgumentException> {
                decode("""{"serverName":"demo","transport":{"type":"$secret"}}""")
            },
        )
        errors.forEach {
            assertFalse(secret in it.stackTraceToString())
            assertNull(it.cause)
        }
    }

    private fun decode(text: String): McpServerConfig = decodeMcpServerConfig(Json.parseToJsonElement(text) as JsonObject)
}
