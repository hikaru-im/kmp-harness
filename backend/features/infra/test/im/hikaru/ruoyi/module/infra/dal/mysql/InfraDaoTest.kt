package im.hikaru.ruoyi.module.infra.dal.mysql

import im.hikaru.ruoyi.module.infra.controller.admin.config.vo.ConfigPageReqVO
import im.hikaru.ruoyi.module.infra.controller.admin.file.vo.config.FileConfigPageReqVO
import im.hikaru.ruoyi.module.infra.dal.dataobject.config.ConfigDO
import im.hikaru.ruoyi.module.infra.dal.dataobject.file.FileDO
import im.hikaru.ruoyi.module.infra.dal.dataobject.file.FileConfigDO
import im.hikaru.ruoyi.module.infra.dal.mysql.config.ConfigDao
import im.hikaru.ruoyi.module.infra.dal.mysql.config.ConfigTable
import im.hikaru.ruoyi.module.infra.dal.mysql.file.FileConfigDao
import im.hikaru.ruoyi.module.infra.dal.mysql.file.FileConfigTable
import im.hikaru.ruoyi.module.infra.dal.mysql.file.FileDao
import im.hikaru.ruoyi.module.infra.dal.mysql.file.FileTable
import im.hikaru.ruoyi.module.infra.framework.file.core.client.db.DBFileClientConfig
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime as JavaLocalDateTime

class InfraDaoTest {

    @BeforeEach
    fun setUpDatabase() {
        Database.connect(
            "jdbc:h2:mem:infra_dao_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
        )
        transaction { SchemaUtils.create(ConfigTable, FileConfigTable, FileTable) }
    }

    @Test
    fun `config dao supports filtered CRUD`() {
        val first = config("feature.alpha", "Alpha", LocalDateTime(2026, 1, 10, 12, 0))
        val second = config("feature.beta", "Beta", LocalDateTime(2026, 2, 10, 12, 0))
        val firstId = ConfigDao.insert(first)
        val secondId = ConfigDao.insert(second)

        assertEquals(firstId, ConfigDao.selectByKey("feature.alpha")?.id)
        val page = ConfigDao.selectPage(
            ConfigPageReqVO().apply {
                key = "feature"
                type = 2
                createTime = arrayOf(
                    JavaLocalDateTime.of(2026, 2, 1, 0, 0),
                    JavaLocalDateTime.of(2026, 2, 28, 23, 59),
                )
            },
        )
        assertEquals(listOf(secondId), page.list.map(ConfigDO::id))

        ConfigDao.updateById(ConfigDO().apply {
            id = firstId
            name = "Alpha updated"
        })
        assertEquals("Alpha updated", ConfigDao.selectById(firstId)?.name)

        assertEquals(2, ConfigDao.deleteByIds(listOf(firstId, secondId)))
        assertNull(ConfigDao.selectById(firstId))
    }

    @Test
    fun `file config dao filters creation time and switches master`() {
        val oldId = FileConfigDao.insert(fileConfig("Old", true, LocalDateTime(2026, 1, 10, 12, 0)))
        val newId = FileConfigDao.insert(fileConfig("New", false, LocalDateTime(2026, 2, 10, 12, 0)))

        val page = FileConfigDao.selectPage(
            FileConfigPageReqVO().apply {
                createTime = arrayOf(
                    JavaLocalDateTime.of(2026, 2, 1, 0, 0),
                    JavaLocalDateTime.of(2026, 2, 28, 23, 59),
                )
            },
        )
        assertEquals(listOf(newId), page.list.map(FileConfigDO::id))

        FileConfigDao.updateMaster(newId)
        assertEquals(newId, FileConfigDao.selectByMaster()?.id)
        assertFalse(requireNotNull(FileConfigDao.selectById(oldId)?.master))
        assertTrue(requireNotNull(FileConfigDao.selectById(newId)?.master))
    }

    @Test
    fun `file size uses the postgresql int4 column without changing the long domain type`() {
        val maxSize = Int.MAX_VALUE.toLong()
        val id = FileDao.insert(file(maxSize))

        assertEquals(maxSize, FileDao.selectById(id)?.size)
        assertThrows(ArithmeticException::class.java) {
            FileDao.insert(file(maxSize + 1))
        }
    }

    private fun config(key: String, name: String, createTime: LocalDateTime) = ConfigDO().apply {
        category = "biz"
        this.name = name
        this.key = key
        value = "enabled"
        type = 2
        visible = true
        this.createTime = createTime
        updateTime = createTime
    }

    private fun fileConfig(name: String, master: Boolean, createTime: LocalDateTime) = FileConfigDO().apply {
        this.name = name
        storage = 11
        config = DBFileClientConfig("http://localhost:48080")
        this.master = master
        this.createTime = createTime
        updateTime = createTime
    }

    private fun file(size: Long) = FileDO().apply {
        path = "2026/07/file.txt"
        url = "https://example.test/file.txt"
        this.size = size
        createTime = LocalDateTime(2026, 7, 18, 14, 30)
        updateTime = createTime
    }
}
