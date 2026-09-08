package im.hikaru.ruoyi.module.infra.service.file

import im.hikaru.ruoyi.module.infra.dal.mysql.file.FileDao
import im.hikaru.ruoyi.module.infra.dal.mysql.file.FileTable
import im.hikaru.ruoyi.module.infra.framework.file.core.client.FileClient
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class FileServiceTest {
    private lateinit var client: RecordingFileClient
    private lateinit var service: FileServiceImpl

    @BeforeEach
    fun setUp() {
        Database.connect(
            "jdbc:h2:mem:file_service_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
        )
        transaction { SchemaUtils.create(FileTable) }
        client = RecordingFileClient()
        val configService = mock(FileConfigService::class.java)
        `when`(configService.getMasterFileClient()).thenReturn(client)
        service = FileServiceImpl(configService)
    }

    @Test
    fun `createFileRecord returns the inserted FileDO and URL method delegates to it`() {
        val content = "markdown".toByteArray()

        val file = service.createFileRecord(content, "result.md", "agent", "text/markdown")
        val delegatedUrl = service.createFile(content, "second.md", "agent", "text/markdown")

        val fileId = requireNotNull(file.id)
        assertEquals(file.url, FileDao.selectById(fileId)?.url)
        assertEquals(content.size.toLong(), FileDao.selectById(fileId)?.size)
        assertEquals(client.uploadedUrls.last(), delegatedUrl)
    }

    private class RecordingFileClient : FileClient {
        override val id: Long = 10L
        val uploadedUrls = mutableListOf<String>()

        override fun upload(content: ByteArray, path: String, type: String): String =
            "https://files.invalid/$path".also(uploadedUrls::add)

        override fun delete(path: String) = Unit
        override fun getContent(path: String): ByteArray? = null
    }
}
