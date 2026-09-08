package im.hikaru.ruoyi.module.system.dal.mysql.permission

import im.hikaru.ruoyi.module.system.dal.dataobject.permission.MenuDO
import im.hikaru.ruoyi.module.system.dal.dataobject.permission.RoleDO
import im.hikaru.ruoyi.module.system.service.dept.DeptServiceImpl
import im.hikaru.ruoyi.module.system.service.permission.MenuServiceImpl
import im.hikaru.ruoyi.module.system.service.permission.PermissionServiceImpl
import im.hikaru.ruoyi.module.system.service.permission.RoleServiceImpl
import im.hikaru.ruoyi.module.system.service.tenant.TenantService
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.support.StaticListableBeanFactory

class PermissionDaoTest {
    @BeforeEach
    fun setUp() {
        Database.connect("jdbc:h2:mem:system_permission_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
        transaction { SchemaUtils.create(RoleTable, MenuTable, UserRoleTable, RoleMenuTable) }
    }

    @Test
    fun `permission service assigns roles and menus`() {
        val roleId = RoleDao.insert(RoleDO().apply { name = "Operator"; code = "operator"; sort = 1; status = 0; type = 2; dataScope = 2; dataScopeDeptIds = setOf(10, 11) })
        val menuId = MenuDao.insert(MenuDO().apply { name = "Users"; permission = "system:user:query"; type = 3; sort = 1; parentId = 0; status = 0 })
        assertEquals(setOf(10L, 11L), RoleDao.selectById(roleId)?.dataScopeDeptIds)

        val menuService = MenuServiceImpl(StaticListableBeanFactory().getBeanProvider(TenantService::class.java))
        val service = PermissionServiceImpl(DeptServiceImpl(), RoleServiceImpl(), menuService)
        service.assignUserRole(100, setOf(roleId))
        service.assignRoleMenu(roleId, setOf(menuId))
        assertEquals(setOf(roleId), service.getUserRoleIdListByUserId(100))
        assertEquals(setOf(menuId), service.getRoleMenuListByRoleId(listOf(roleId)))
        assertTrue(service.hasAnyPermissions(100, "system:user:query"))
        assertTrue(service.hasAnyRoles(100, "operator"))
    }
}
