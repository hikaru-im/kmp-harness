package im.hikaru.harness.client.conversation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A file selected for the next prompt. The path remains local to its owning Host. */
public data class ComposerAttachment(
    val id: String,
    val path: String,
    val name: String,
    val sizeBytes: Long,
) {
    init {
        require(id.isNotBlank())
        require(path.isNotBlank())
        require(name.isNotBlank())
        require(sizeBytes >= 0)
    }
}

public interface ComposerAttachmentReader {
    public suspend fun read(attachment: ComposerAttachment): String
}

public object UnavailableComposerAttachmentReader : ComposerAttachmentReader {
    override suspend fun read(attachment: ComposerAttachment): String {
        error("本地文件内容读取不可用")
    }
}

public object ComposerAttachmentLimits {
    public const val MaxFiles: Int = 8
    public const val MaxBytesPerFile: Long = 512 * 1024
    public const val MaxCharactersPerFile: Int = 200_000
}

public data class MaterializedComposerAttachment(
    val attachment: ComposerAttachment,
    val content: String,
)

public suspend fun materializeComposerAttachments(
    attachments: List<ComposerAttachment>,
    reader: ComposerAttachmentReader,
): List<MaterializedComposerAttachment> = withContext(Dispatchers.Default) {
    require(attachments.size <= ComposerAttachmentLimits.MaxFiles) { "附件数量超过限制" }
    attachments.map { attachment ->
        require(attachment.sizeBytes <= ComposerAttachmentLimits.MaxBytesPerFile) {
            "文件 ${attachment.name} 超过大小限制"
        }
        val content = reader.read(attachment)
        require(content.length <= ComposerAttachmentLimits.MaxCharactersPerFile) {
            "文件 ${attachment.name} 超过文本长度限制"
        }
        MaterializedComposerAttachment(attachment, content)
    }
}

/** Keeps the existing text-only Message/ContentBlock transport while preserving clear file boundaries. */
public fun formatComposerPrompt(
    draft: String,
    attachments: List<MaterializedComposerAttachment>,
): String {
    if (attachments.isEmpty()) return draft
    val context = attachments.joinToString("\n\n") { item ->
        "<file path=\"${escapeComposerPath(item.attachment.path)}\" name=\"${escapeComposerPath(item.attachment.name)}\">\n" +
            item.content +
            "\n</file>"
    }
    return "请参考以下本地文件内容。\n\n$context\n\n用户请求：\n$draft"
}

private fun escapeComposerPath(value: String): String = value
    .replace("&", "&amp;")
    .replace("\"", "&quot;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
