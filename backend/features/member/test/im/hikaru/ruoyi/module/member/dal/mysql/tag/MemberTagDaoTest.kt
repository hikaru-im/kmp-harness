package im.hikaru.ruoyi.module.member.dal.mysql.tag

import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.module.member.controller.admin.tag.vo.MemberTagPageReqVO
import im.hikaru.ruoyi.module.member.convert.tag.MemberTagConvert
import im.hikaru.ruoyi.module.member.dal.dataobject.tag.MemberTagDO
import java.time.LocalDateTime
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MemberTagDaoTest {
    @BeforeEach
    fun setUp() {
        Database.connect(
            "jdbc:h2:mem:member_tag_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
        )
        transaction { SchemaUtils.create(MemberTagTable) }
        TenantContextHolder.setTenantId(1L)
    }

    @AfterEach
    fun tearDown() {
        TenantContextHolder.clear()
    }

    @Test
    fun `tag CRUD keeps tenant filtering pagination and create time conversion`() {
        val tag = MemberTagDO().apply { name = "priority" }
        val id = MemberTagDao.insert(tag)

        val page = MemberTagDao.selectPage(MemberTagPageReqVO().apply {
            name = "prior"
            createTime = arrayOf(LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusMinutes(1))
        })
        assertEquals(1L, page.total)
        assertEquals(id, page.list.single().id)
        assertNotNull(MemberTagConvert.convert(page.list.single()).createTime)

        TenantContextHolder.setTenantId(2L)
        assertNull(MemberTagDao.selectById(id))

        TenantContextHolder.setTenantId(1L)
        assertEquals(1, MemberTagDao.updateById(MemberTagDO().apply {
            this.id = id
            name = "vip"
        }))
        assertEquals("vip", MemberTagDao.selectByName("vip")?.name)
        assertEquals(1, MemberTagDao.deleteById(id))
        assertNull(MemberTagDao.selectById(id))
    }
}
