package im.hikaru.ruoyi.module.system.dal.mysql.dict

import im.hikaru.ruoyi.module.system.controller.admin.dict.vo.data.DictDataPageReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.dict.DictDataDO
import im.hikaru.ruoyi.module.system.dal.dataobject.dict.DictTypeDO
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DictDaoTest {
    @BeforeEach
    fun setUp() {
        Database.connect(
            url = "jdbc:h2:mem:system_dict_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
        )
        transaction { SchemaUtils.create(DictTypeTable, DictDataTable) }
    }

    @Test
    fun `dictionary data supports CRUD filtering and type soft delete`() {
        val type = DictTypeDO().apply {
            name = "Status"
            this.type = "test_status"
            status = 0
        }
        val typeId = DictTypeDao.insert(type)
        assertEquals(typeId, type.id)

        val data = DictDataDO().apply {
            sort = 1
            label = "Enabled"
            value = "0"
            dictType = "test_status"
            status = 0
            colorType = "success"
            cssClass = "badge"
        }
        val dataId = DictDataDao.insert(data)
        assertEquals(dataId, data.id)
        assertEquals(1L, DictDataDao.selectCountByDictType("test_status"))
        assertEquals(dataId, DictDataDao.selectByDictTypeAndValue("test_status", "0")?.id)

        val page = DictDataDao.selectPage(DictDataPageReqVO().apply { dictType = "test_status" })
        assertEquals(1L, page.total)
        assertEquals("Enabled", page.list.single().label)

        DictDataDao.updateById(DictDataDO().apply {
            id = dataId
            label = "Enabled now"
            colorType = null
            cssClass = null
        })
        val updated = requireNotNull(DictDataDao.selectById(dataId))
        assertEquals("Enabled now", updated.label)
        assertNull(updated.colorType)
        assertNull(updated.cssClass)

        DictDataDao.deleteById(dataId)
        assertEquals(0L, DictDataDao.selectCountByDictType("test_status"))
        DictTypeDao.updateToDelete(typeId, LocalDateTime(2026, 7, 17, 0, 0))
        assertNull(DictTypeDao.selectById(typeId))
    }
}
