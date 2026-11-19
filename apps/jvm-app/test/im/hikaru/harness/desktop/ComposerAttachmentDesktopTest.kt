package im.hikaru.harness.desktop

import im.hikaru.harness.client.conversation.ComposerAttachment
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class ComposerAttachmentDesktopTest {
    @Test
    fun readerReturnsStrictUtf8TextForSelectedRegularFile() = runTest {
        val path = Files.createTempFile("harness-attachment", ".txt")
        try {
            Files.writeString(path, "hello\nworld")
            val attachment = ComposerAttachment("test", path.toRealPath().toString(), path.fileName.toString(), Files.size(path))
            assertEquals("hello\nworld", DesktopComposerAttachmentReader.read(attachment))
        } finally {
            Files.deleteIfExists(path)
        }
    }
}
