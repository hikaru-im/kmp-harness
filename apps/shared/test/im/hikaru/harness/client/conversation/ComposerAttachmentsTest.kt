package im.hikaru.harness.client.conversation

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ComposerAttachmentsTest {
    @Test
    fun formatterKeepsRequestAndEscapesFileMetadata() {
        val file = ComposerAttachment("id", "/tmp/a&b.kt", "a< b.kt", 3)
        val result = formatComposerPrompt(
            "请解释",
            listOf(MaterializedComposerAttachment(file, "one\ntwo")),
        )
        assertEquals(
            "请参考以下本地文件内容。\n\n<file path=\"/tmp/a&amp;b.kt\" name=\"a&lt; b.kt\">\none\ntwo\n</file>\n\n用户请求：\n请解释",
            result,
        )
    }

    @Test
    fun materializerRejectsTooManyFilesAndOversizedContent() = runTest {
        val files = (0..ComposerAttachmentLimits.MaxFiles).map { index ->
            ComposerAttachment(index.toString(), "/tmp/$index.txt", "$index.txt", 1)
        }
        assertFailsWith<IllegalArgumentException> {
            materializeComposerAttachments(files, object : ComposerAttachmentReader {
                override suspend fun read(attachment: ComposerAttachment) = "ok"
            })
        }
        val large = ComposerAttachment("large", "/tmp/large.txt", "large.txt", 1)
        assertFailsWith<IllegalArgumentException> {
            materializeComposerAttachments(listOf(large), object : ComposerAttachmentReader {
                override suspend fun read(attachment: ComposerAttachment) =
                    "x".repeat(ComposerAttachmentLimits.MaxCharactersPerFile + 1)
            })
        }
    }
}
