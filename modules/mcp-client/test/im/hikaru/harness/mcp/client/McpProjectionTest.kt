package im.hikaru.harness.mcp.client

import im.hikaru.harness.llm.TextBlock
import im.hikaru.harness.tools.ToolExecutionResult
import io.modelcontextprotocol.kotlin.sdk.types.AudioContent
import io.modelcontextprotocol.kotlin.sdk.types.BlobResourceContents
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.EmbeddedResource
import io.modelcontextprotocol.kotlin.sdk.types.ImageContent
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceResult
import io.modelcontextprotocol.kotlin.sdk.types.ResourceLink
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.TextResourceContents
import io.modelcontextprotocol.kotlin.sdk.types.UnknownResourceContents
import java.security.MessageDigest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class McpProjectionTest {
    @Test
    fun publicNamesPreserveSafeNamesAndHashNormalizedOrTruncatedNames() {
        assertEquals("mcp__demo__read-file_1", mcpPublicToolName("demo", "read-file_1"))
        val normalized = mcpPublicToolName("demo", "read.file")
        val expectedDigest = MessageDigest.getInstance("SHA-256")
            .digest("demo\u0000read.file".toByteArray())
            .take(6).joinToString("") { "%02x".format(it.toInt() and 0xff) }
        assertEquals("mcp__demo__read_file_$expectedDigest", normalized)
        assertNotEquals(normalized, mcpPublicToolName("demo", "read_file"))
        assertNotEquals(normalized, mcpPublicToolName("demo", "read file"))
        assertNotEquals(normalized, mcpPublicToolName("other", "read.file"))
        val longPrefix = "a".repeat(200)
        val first = mcpPublicToolName("s".repeat(32), longPrefix + "1")
        val second = mcpPublicToolName("s".repeat(32), longPrefix + "2")
        assertEquals(64, first.length)
        assertNotEquals(first, second)
        for (raw in listOf("你好", "read.file", "a/b", longPrefix)) {
            val name = mcpPublicToolName("demo", raw)
            assertTrue(name.length <= 64)
            assertTrue(Regex("[A-Za-z0-9_-]+").matches(name))
            assertEquals(name, mcpPublicToolName("demo", raw))
        }
        for (raw in listOf("", "  ", "bad\u0000name")) {
            assertFailsWith<IllegalArgumentException> { mcpPublicToolName("demo", raw) }
        }
        assertFailsWith<IllegalArgumentException> { mcpPublicToolName("invalid.server", "read") }
    }

    @Test
    fun instructionsAreLiteralAndUtf8BoundedIncludingHeading() {
        val text = "Keep \${TOKEN} and {{value}} literal.\n你好😀done"
        val full = mcpInstructionText("demo", text, 1000)
        assertEquals("## MCP server instructions: demo\n\n$text", full)
        for (budget in 1..full.toByteArray().size) {
            val bounded = mcpInstructionText("demo", text, budget)
            assertTrue(bounded.toByteArray().size <= budget)
            assertTrue(full.startsWith(bounded))
            assertFalse('�' in bounded)
        }
        assertFailsWith<IllegalArgumentException> { mcpInstructionText("demo", text, 0) }
    }

    @Test
    fun textStructuredContentAndToolErrorsArePreserved() {
        val structured = buildJsonObject { put("count", 2); put("answer", "yes") }
        val result = projectMcpResult(
            CallToolResult(
                content = listOf(TextContent("first\nunchanged"), TextContent("")),
                isError = true,
                structuredContent = structured,
                meta = buildJsonObject { put("private", "must-not-be-dumped") },
            ),
        )
        assertTrue(result.isError)
        assertEquals(listOf("first\nunchanged", "", "MCP structuredContent (JSON):\n$structured"), texts(result))
        assertFalse("must-not-be-dumped" in texts(result).joinToString())
        assertFalse(projectMcpResult(CallToolResult(emptyList())).isError)
        assertFalse(projectMcpResult(CallToolResult(emptyList(), isError = false)).isError)
    }

    @Test
    fun multimodalContentIsMetadataOnlyAndLinksAreNeverFetched() {
        val payload = "U0VDUkVUX1BBWUxPQUQ="
        val projected = projectMcpResult(
            CallToolResult(
                content = listOf(
                    ImageContent(payload, "image/png"),
                    AudioContent(payload, "audio/wav"),
                    EmbeddedResource(BlobResourceContents(payload, "file:///private/image", "application/octet-stream")),
                    ResourceLink(uri = "https://unreachable.invalid/resource", name = "untrusted-name"),
                    EmbeddedResource(TextResourceContents("embedded\ntext", "file:///text")),
                ),
            ),
        )
        val blocks = texts(projected)
        assertTrue("image/png" in blocks[0])
        assertTrue("audio/wav" in blocks[1])
        assertTrue("application/octet-stream" in blocks[2])
        assertTrue("file:///private/image" in blocks[2])
        assertEquals("MCP resource link: \"https://unreachable.invalid/resource\"", blocks[3])
        assertEquals("embedded\ntext", blocks[4])
        assertFalse(payload in blocks.joinToString())
        assertFalse(projected.isError)
    }

    @Test
    fun resourcesKeepTextButOmitBlobAndArbitraryMetadata() {
        val payload = "U0VDUkVUX0JMT0I="
        val result = projectMcpResource(
            ReadResourceResult(
                contents = listOf(
                    TextResourceContents("raw\ntext\t\"", "file:///text"),
                    BlobResourceContents(payload, "file:///blob", "image/png"),
                    BlobResourceContents(payload, "data:image/png;base64,$payload", "invalid\n$payload"),
                ),
                meta = buildJsonObject { put("sensitive", payload) },
            ),
        )
        val blocks = texts(result)
        assertEquals("raw\ntext\t\"", blocks[0])
        assertTrue("image/png" in blocks[1])
        assertTrue("file:///blob" in blocks[1])
        assertTrue("inline data URI omitted" in blocks[2])
        assertFalse(payload in blocks.joinToString())
        assertFalse(result.isError)
        assertTrue(projectMcpResource(ReadResourceResult(emptyList())).content.isEmpty())
        val unknown = projectMcpResource(
            ReadResourceResult(listOf(UnknownResourceContents("file:///unknown", meta = buildJsonObject { put("secret", payload) }))),
        )
        assertEquals(listOf("MCP resource content unavailable (uri=\"file:///unknown\")"), texts(unknown))
        assertFalse(payload in texts(unknown).joinToString())
    }

    private fun texts(result: ToolExecutionResult): List<String> = result.content.map { (it as TextBlock).text }
}
