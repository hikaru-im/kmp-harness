package im.hikaru.ruoyi.module.infra.framework.file

import im.hikaru.ruoyi.framework.common.util.json.JsonUtils
import im.hikaru.ruoyi.module.infra.framework.file.core.client.FileClientConfig
import im.hikaru.ruoyi.module.infra.framework.file.core.client.FileClientFactoryImpl
import im.hikaru.ruoyi.module.infra.framework.file.core.client.db.DBFileClientConfig
import im.hikaru.ruoyi.module.infra.framework.file.core.client.local.LocalFileClientConfig
import im.hikaru.ruoyi.module.infra.framework.file.core.utils.FilePathUtils
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class FileClientTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `local client uploads reads and deletes content`() {
        val factory = FileClientFactoryImpl()
        factory.createOrUpdateFileClient(
            7,
            10,
            LocalFileClientConfig(tempDir.toString(), "http://localhost:48080"),
        )
        val client = requireNotNull(factory.getFileClient(7))
        val content = "hello kotlin".toByteArray()

        val url = client.upload(content, "20260717/test.txt", "text/plain")

        assertEquals(
            "http://localhost:48080/admin-api/infra/file/7/get/20260717/test.txt",
            url,
        )
        assertArrayEquals(content, client.getContent("20260717/test.txt"))
        assertTrue(Files.exists(tempDir.resolve("20260717/test.txt")))
        client.delete("20260717/test.txt")
        assertFalse(Files.exists(tempDir.resolve("20260717/test.txt")))
        assertEquals(null, client.getContent("20260717/test.txt"))
    }

    @Test
    fun `polymorphic config JSON remains compatible with database data`() {
        val json = JsonUtils.toJsonString(DBFileClientConfig("http://localhost:48080"))

        assertTrue(json.contains("\"@class\""))
        val parsed = JsonUtils.parseObject(json, FileClientConfig::class.java)
        val dbConfig = assertInstanceOf(DBFileClientConfig::class.java, parsed)
        assertEquals("http://localhost:48080", dbConfig.domain)
    }

    @Test
    fun `path validation rejects traversal and absolute paths`() {
        assertTrue(FilePathUtils.isDirectoryValid("images/avatar"))
        assertFalse(FilePathUtils.isDirectoryValid("../secret"))
        assertFalse(FilePathUtils.isDirectoryValid("images//avatar"))
        assertThrows(RuntimeException::class.java) { FilePathUtils.validatePath("/etc/passwd") }
        assertThrows(RuntimeException::class.java) { FilePathUtils.validatePath("images/../secret") }
        assertThrows(RuntimeException::class.java) { FilePathUtils.validateFileName("images/avatar.png") }
    }
}
