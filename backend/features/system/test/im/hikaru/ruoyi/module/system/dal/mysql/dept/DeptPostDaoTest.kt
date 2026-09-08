package im.hikaru.ruoyi.module.system.dal.mysql.dept

import im.hikaru.ruoyi.module.system.controller.admin.dept.vo.dept.DeptListReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.dept.DeptDO
import im.hikaru.ruoyi.module.system.dal.dataobject.dept.PostDO
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DeptPostDaoTest {
    @BeforeEach
    fun setUp() {
        Database.connect("jdbc:h2:mem:system_dept_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
        transaction { SchemaUtils.create(DeptTable, PostTable, UserPostTable) }
    }

    @Test
    fun `department and post dao support tenant aware crud`() {
        val root = DeptDO().apply { name = "Root"; parentId = 0; sort = 1; status = 0; tenantId = 7; createTime = LocalDateTime(2026, 7, 17, 0, 0); updateTime = createTime }
        val rootId = DeptDao.insert(root)
        DeptDao.insert(DeptDO().apply { name = "Child"; parentId = rootId; sort = 2; status = 0; tenantId = 7 })
        assertEquals(1L, DeptDao.selectCountByParentId(rootId))
        assertEquals(2, DeptDao.selectList(DeptListReqVO()).size)
        assertEquals(1, DeptDao.selectList(DeptListReqVO().apply { name = "Child" }).size)
        DeptDao.deleteById(rootId)
        assertNull(DeptDao.selectById(rootId))

        val post = PostDO().apply { name = "Engineer"; code = "eng"; sort = 1; status = 0; tenantId = 7 }
        val postId = PostDao.insert(post)
        assertEquals(postId, PostDao.selectByCode("eng")?.id)
        PostDao.deleteById(postId)
        assertNull(PostDao.selectById(postId))
    }
}
