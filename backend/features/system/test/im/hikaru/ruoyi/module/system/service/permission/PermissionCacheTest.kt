package im.hikaru.ruoyi.module.system.service.permission

import im.hikaru.ruoyi.framework.common.enums.CommonStatusEnum
import im.hikaru.ruoyi.framework.common.util.spring.SpringUtils
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.module.system.controller.admin.dept.vo.dept.DeptSaveReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.dept.DeptDO
import im.hikaru.ruoyi.module.system.dal.dataobject.permission.MenuDO
import im.hikaru.ruoyi.module.system.dal.dataobject.permission.RoleDO
import im.hikaru.ruoyi.module.system.dal.dataobject.permission.RoleMenuDO
import im.hikaru.ruoyi.module.system.dal.dataobject.permission.UserRoleDO
import im.hikaru.ruoyi.module.system.dal.mysql.permission.MenuDao
import im.hikaru.ruoyi.module.system.dal.mysql.permission.MenuTable
import im.hikaru.ruoyi.module.system.dal.mysql.permission.RoleDao
import im.hikaru.ruoyi.module.system.dal.mysql.permission.RoleMenuDao
import im.hikaru.ruoyi.module.system.dal.mysql.permission.RoleMenuTable
import im.hikaru.ruoyi.module.system.dal.mysql.permission.RoleTable
import im.hikaru.ruoyi.module.system.dal.mysql.permission.UserRoleDao
import im.hikaru.ruoyi.module.system.dal.mysql.permission.UserRoleTable
import im.hikaru.ruoyi.module.system.dal.mysql.dept.DeptDao
import im.hikaru.ruoyi.module.system.dal.mysql.dept.DeptTable
import im.hikaru.ruoyi.module.system.dal.redis.RedisKeyConstants
import im.hikaru.ruoyi.module.system.enums.permission.DataScopeEnum
import im.hikaru.ruoyi.module.system.service.dept.DeptService
import im.hikaru.ruoyi.module.system.service.dept.DeptServiceImpl
import im.hikaru.ruoyi.module.system.service.tenant.TenantService
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.ObjectProvider
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

class PermissionCacheTest {
    private lateinit var context: AnnotationConfigApplicationContext
    private lateinit var roleService: RoleService
    private lateinit var menuService: MenuService
    private lateinit var permissionService: PermissionService
    private lateinit var deptService: DeptService

    @BeforeEach
    fun setUp() {
        Database.connect("jdbc:h2:mem:system_permission_cache_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
        transaction { SchemaUtils.create(RoleTable, MenuTable, UserRoleTable, RoleMenuTable, DeptTable) }
        TenantContextHolder.setTenantId(TENANT_ID)
        context = AnnotationConfigApplicationContext(CacheTestConfiguration::class.java)
        roleService = context.getBean(RoleService::class.java)
        menuService = context.getBean(MenuService::class.java)
        permissionService = context.getBean(PermissionService::class.java)
        deptService = context.getBean(DeptService::class.java)
    }

    @AfterEach
    fun tearDown() {
        context.close()
        TenantContextHolder.clear()
    }

    @Test
    fun `role cache reads through and data-scope updates evict`() {
        val roleId = createRole("operator")

        assertEquals(DataScopeEnum.ALL.scope, roleService.getRoleFromCache(roleId)?.dataScope)
        RoleDao.updateById(RoleDO().apply {
            id = roleId
            dataScope = DataScopeEnum.SELF.scope
        })
        assertEquals(DataScopeEnum.ALL.scope, roleService.getRoleFromCache(roleId)?.dataScope)

        roleService.updateRoleDataScope(roleId, DataScopeEnum.DEPT_ONLY.scope, emptySet())
        assertEquals(DataScopeEnum.DEPT_ONLY.scope, roleService.getRoleFromCache(roleId)?.dataScope)
    }

    @Test
    fun `department writes evict all cached descendant sets`() {
        val firstId = createDept("First")
        assertEquals(setOf(firstId), deptService.getChildDeptIdListFromCache(DeptDO.PARENT_ID_ROOT))

        val secondId = createDept("Second")
        assertEquals(setOf(firstId), deptService.getChildDeptIdListFromCache(DeptDO.PARENT_ID_ROOT))

        val thirdId = deptService.createDept(DeptSaveReqVO().apply {
            name = "Third"
            parentId = DeptDO.PARENT_ID_ROOT
            sort = 3
            status = CommonStatusEnum.ENABLE.status
        })
        assertEquals(
            setOf(firstId, secondId, thirdId),
            deptService.getChildDeptIdListFromCache(DeptDO.PARENT_ID_ROOT),
        )
    }

    @Test
    fun `permission relation writes evict user menu and permission caches`() {
        val firstRoleId = createRole("first")
        val secondRoleId = createRole("second")
        val firstMenuId = createMenu("system:user:query")
        UserRoleDao.insert(UserRoleDO().apply {
            userId = USER_ID
            roleId = firstRoleId
        })
        RoleMenuDao.insert(RoleMenuDO().apply {
            roleId = firstRoleId
            menuId = firstMenuId
        })

        assertEquals(setOf(firstRoleId), permissionService.getUserRoleIdListByUserIdFromCache(USER_ID))
        assertEquals(setOf(firstRoleId), permissionService.getMenuRoleIdListByMenuIdFromCache(firstMenuId))
        assertEquals(listOf(firstMenuId), menuService.getMenuIdListByPermissionFromCache("system:user:query"))

        UserRoleDao.insert(UserRoleDO().apply {
            userId = USER_ID
            roleId = secondRoleId
        })
        RoleMenuDao.insert(RoleMenuDO().apply {
            roleId = secondRoleId
            menuId = firstMenuId
        })
        val secondMenuId = createMenu("system:user:query")

        assertEquals(setOf(firstRoleId), permissionService.getUserRoleIdListByUserIdFromCache(USER_ID))
        assertEquals(setOf(firstRoleId), permissionService.getMenuRoleIdListByMenuIdFromCache(firstMenuId))
        assertEquals(listOf(firstMenuId), menuService.getMenuIdListByPermissionFromCache("system:user:query"))

        permissionService.assignUserRole(USER_ID, setOf(firstRoleId, secondRoleId))
        permissionService.assignRoleMenu(secondRoleId, setOf(firstMenuId, secondMenuId))

        assertEquals(setOf(firstRoleId, secondRoleId), permissionService.getUserRoleIdListByUserIdFromCache(USER_ID))
        assertEquals(setOf(firstRoleId, secondRoleId), permissionService.getMenuRoleIdListByMenuIdFromCache(firstMenuId))
        assertEquals(setOf(firstMenuId, secondMenuId), menuService.getMenuIdListByPermissionFromCache("system:user:query").toSet())
    }

    private fun createRole(code: String): Long = RoleDao.insert(RoleDO().apply {
        name = code.replaceFirstChar(Char::uppercase)
        this.code = code
        sort = 1
        status = CommonStatusEnum.ENABLE.status
        type = 2
        dataScope = DataScopeEnum.ALL.scope
        dataScopeDeptIds = emptySet()
    })

    private fun createMenu(permission: String): Long = MenuDao.insert(MenuDO().apply {
        name = "Menu-$permission-${System.nanoTime()}"
        this.permission = permission
        type = 3
        sort = 1
        parentId = MenuDO.ID_ROOT
        status = CommonStatusEnum.ENABLE.status
    })

    private fun createDept(name: String): Long = DeptDao.insert(DeptDO().apply {
        this.name = name
        parentId = DeptDO.PARENT_ID_ROOT
        sort = 1
        status = CommonStatusEnum.ENABLE.status
    })

    @Configuration(proxyBeanMethods = false)
    @EnableCaching(proxyTargetClass = true)
    class CacheTestConfiguration {
        @Bean
        fun cacheManager(): CacheManager = ConcurrentMapCacheManager(
            RedisKeyConstants.ROLE,
            RedisKeyConstants.USER_ROLE_ID_LIST,
            RedisKeyConstants.MENU_ROLE_ID_LIST,
            RedisKeyConstants.PERMISSION_MENU_ID_LIST,
            RedisKeyConstants.DEPT_CHILDREN_ID_LIST,
        )

        @Bean
        fun springUtils(): SpringUtils = SpringUtils()

        @Bean
        fun deptService(): DeptService = DeptServiceImpl()

        @Bean
        fun roleService(): RoleService = RoleServiceImpl()

        @Bean
        fun menuService(tenantServiceProvider: ObjectProvider<TenantService>): MenuService =
            MenuServiceImpl(tenantServiceProvider)

        @Bean
        fun permissionService(
            deptService: DeptService,
            roleService: RoleService,
            menuService: MenuService,
        ): PermissionService = PermissionServiceImpl(deptService, roleService, menuService)
    }

    companion object {
        private const val USER_ID = 100L
        private const val TENANT_ID = 1L
    }
}
