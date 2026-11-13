package im.hikaru.harness.desktop

import im.hikaru.harness.client.conversation.ComposerAttachment
import im.hikaru.harness.client.conversation.ComposerAttachmentLimits
import im.hikaru.harness.client.conversation.ComposerAttachmentReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

/** Native desktop picker; it returns only paths the user explicitly selected. */
public fun chooseComposerAttachments(): List<ComposerAttachment> {
    val dialog = FileDialog(null as Frame?, "添加文件", FileDialog.LOAD).apply { isMultipleMode = true }
    dialog.isVisible = true
    return dialog.files.orEmpty().mapNotNull { file ->
        runCatching {
            val path = file.toPath().toRealPath()
            require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
            val size = Files.size(path)
            ComposerAttachment(
                id = path.toString(),
                path = path.toString(),
                name = path.fileName.toString(),
                sizeBytes = size,
            )
        }.getOrNull()
    }.distinctBy { it.path }.take(ComposerAttachmentLimits.MaxFiles)
}

/** Reads only bounded UTF-8 regular files on the same Desktop host as the agent. */
public object DesktopComposerAttachmentReader : ComposerAttachmentReader {
    override suspend fun read(attachment: ComposerAttachment): String = withContext(Dispatchers.IO) {
        val path = Path.of(attachment.path).toRealPath()
        require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) { "文件不可读：${attachment.name}" }
        val size = Files.size(path)
        require(size <= ComposerAttachmentLimits.MaxBytesPerFile) { "文件 ${attachment.name} 超过大小限制" }
        val bytes = Files.readAllBytes(path)
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        decoder.decode(ByteBuffer.wrap(bytes)).toString()
    }
}
