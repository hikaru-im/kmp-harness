package im.hikaru.ruoyi.module.system.dal.mysql.user

import im.hikaru.ruoyi.module.system.controller.admin.user.vo.user.UserPageReqVO
import im.hikaru.ruoyi.module.system.dal.mysql.dept.PostTable
import im.hikaru.ruoyi.module.system.dal.mysql.dept.UserPostTable
import im.hikaru.ruoyi.module.system.dal.dataobject.user.AdminUserDO
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AdminUserDaoTest {
    @BeforeEach
    fun setUp() {
        Database.connect("jdbc:h2:mem:system_user_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
        transaction { SchemaUtils.create(AdminUserTable, PostTable, UserPostTable) }
    }

    @Test
    fun `user dao maps json post ids and supports paging`() {
        val user = AdminUserDO().apply {
            username = "admin"; password = "encoded"; nickname = "Administrator"; postIds = setOf(1L, 2L); status = 0; tenantId = 9
            createTime = LocalDateTime(2026, 7, 17, 0, 0); updateTime = createTime
        }
        val id = AdminUserDao.insert(user)
        assertEquals(id, user.id)
        assertEquals(setOf(1L, 2L), AdminUserDao.selectById(id)?.postIds)
        assertEquals(1L, AdminUserDao.selectPage(UserPageReqVO().apply { username = "adm" }).total)
        AdminUserDao.updateById(AdminUserDO().apply { this.id = id; loginIp = "127.0.0.1" })
        assertEquals("127.0.0.1", AdminUserDao.selectById(id)?.loginIp)
        AdminUserDao.deleteById(id)
        assertNull(AdminUserDao.selectById(id))
    }
}
