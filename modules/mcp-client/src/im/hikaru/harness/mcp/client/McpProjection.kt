package im.hikaru.harness.mcp.client

import im.hikaru.harness.llm.TextBlock
import im.hikaru.harness.tools.ToolExecutionResult
import io.modelcontextprotocol.kotlin.sdk.types.AudioContent
import io.modelcontextprotocol.kotlin.sdk.types.BlobResourceContents
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.EmbeddedResource
import io.modelcontextprotocol.kotlin.sdk.types.ImageContent
import io.modelcontextprotocol.kotlin.sdk.types.ReadResourceResult
import io.modelcontextprotocol.kotlin.sdk.types.ResourceContents
import io.modelcontextprotocol.kotlin.sdk.types.ResourceLink
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.TextResourceContents
import io.modelcontextprotocol.kotlin.sdk.types.UnknownResourceContents
import java.security.MessageDigest
import kotlinx.serialization.json.JsonPrimitive
import io.modelcontextprotocol.kotlin.sdk.types.ContentBlock as McpContentBlock

/** DSH-style public name, with a digest whenever normalization or truncation is necessary. */
fun mcpPublicToolName(serverName: String, rawName: String): String {
    validateMcpServerName(serverName)
    require(validMcpRawName(rawName)) { "Invalid MCP tool name" }
    val prefix = "mcp__${serverName}__"
    val normalized = rawName.map { if (it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' || it == '_' || it == '-') it else '_' }
        .joinToString("")
    if (normalized == rawName && prefix.length + normalized.length <= 64) return prefix + normalized
    val digest = MessageDigest.getInstance("SHA-256")
        .digest((serverName + '\u0000' + rawName).toByteArray(Charsets.UTF_8))
        .take(6).joinToString("") { "%02x".format(it.toInt() and 0xff) }
    val suffix = "_$digest"
    return prefix + normalized.take(64 - prefix.length - suffix.length) + suffix
}

/** Treat instructions as literal, untrusted text; the byte budget includes the heading. */
fun mcpInstructionText(serverName: String, text: String, maxBytes: Int): String {
    validateMcpServerName(serverName)
    require(maxBytes > 0) { "MCP instruction byte limit must be positive" }
    val bytes = ("## MCP server instructions: $serverName\n\n" + text).toByteArray(Charsets.UTF_8)
    if (bytes.size <= maxBytes) return bytes.toString(Charsets.UTF_8)
    var end = maxBytes
    // Do not emit a partial UTF-8 code point or a replacement character at the boundary.
    while (end > 0 && (bytes[end].toInt() and 0xc0) == 0x80) end--
    return String(bytes, 0, end, Charsets.UTF_8)
}

/** Preserve text and explicit structured JSON, never stringify an entire SDK object. */
fun projectMcpResult(result: CallToolResult): ToolExecutionResult = ToolExecutionResult(
    content = buildList {
        result.content.forEach { add(projectMcpContent(it)) }
        result.structuredContent?.let { add(TextBlock("MCP structuredContent (JSON):\n$it")) }
    },
    isError = result.isError == true,
)

fun projectMcpResource(result: ReadResourceResult): ToolExecutionResult = ToolExecutionResult(
    content = result.contents.map(::projectMcpResourceContents),
)

private fun projectMcpContent(content: McpContentBlock): TextBlock = when (content) {
    is TextContent -> TextBlock(content.text)
    is ImageContent -> binaryMetadata("image", content.mimeType, content.data.length)
    is AudioContent -> binaryMetadata("audio", content.mimeType, content.data.length)
    is EmbeddedResource -> projectMcpResourceContents(content.resource)
    is ResourceLink -> TextBlock("MCP resource link: ${metadataUri(content.uri)}")
}

private fun projectMcpResourceContents(resource: ResourceContents): TextBlock = when (resource) {
    is TextResourceContents -> TextBlock(resource.text)
    is BlobResourceContents -> binaryMetadata("blob", resource.mimeType, resource.blob.length, resource.uri)
    is UnknownResourceContents -> TextBlock("MCP resource content unavailable (uri=${metadataUri(resource.uri)})")
}

private fun binaryMetadata(kind: String, mimeType: String?, encodedCharacters: Int, uri: String? = null): TextBlock {
    val safeMimeType = mimeType?.takeIf { MCP_MIME_TYPE.matches(it) } ?: "unknown"
    val location = uri?.let { ", uri=${metadataUri(it)}" }.orEmpty()
    return TextBlock(
        "MCP $kind content omitted (mimeType=$safeMimeType, base64Characters=$encodedCharacters$location)",
    )
}

private fun metadataUri(uri: String): String =
    if (uri.startsWith("data:", ignoreCase = true)) "[inline data URI omitted]" else JsonPrimitive(uri).toString()

private val MCP_MIME_TYPE = Regex("[A-Za-z0-9!#$&^_.+-]+/[A-Za-z0-9!#$&^_.+-]+")
