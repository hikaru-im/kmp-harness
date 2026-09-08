package im.hikaru.ruoyi.module.system.service.permission

import im.hikaru.ruoyi.framework.common.biz.system.permission.dto.DeptDataPermissionRespDTO
import im.hikaru.ruoyi.framework.common.enums.CommonStatusEnum
import im.hikaru.ruoyi.framework.common.util.spring.SpringUtils
import im.hikaru.ruoyi.framework.datapermission.core.annotation.DataPermission
import im.hikaru.ruoyi.module.system.dal.dataobject.permission.RoleMenuDO
import im.hikaru.ruoyi.module.system.dal.dataobject.permission.UserRoleDO
import im.hikaru.ruoyi.module.system.dal.mysql.permission.RoleMenuDao
import im.hikaru.ruoyi.module.system.dal.mysql.permission.UserRoleDao
import im.hikaru.ruoyi.module.system.dal.mysql.user.AdminUserDao
import im.hikaru.ruoyi.module.system.dal.redis.RedisKeyConstants
import im.hikaru.ruoyi.module.system.enums.permission.DataScopeEnum
import im.hikaru.ruoyi.module.system.enums.permission.RoleCodeEnum
import im.hikaru.ruoyi.module.system.service.dept.DeptService
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.cache.annotation.Caching
import org.springframework.stereotype.Service

@Service
class PermissionServiceImpl(
    private val deptService: DeptService,
    private val roleService: RoleService,
    private val menuService: MenuService,
) : PermissionService {
    override fun hasAnyPermissions(userId: Long, vararg permissions: String): Boolean {
        if (permissions.isEmpty()) return true
        val roles = getEnabledUserRoles(userId)
        if (roles.isEmpty()) return false

        val roleIds = roles.mapNotNull { it.id }.toSet()
        permissions.forEach { permission ->
            val menuIds = menuService.getMenuIdListByPermissionFromCache(permission)
            if (menuIds.any { menuId -> self().getMenuRoleIdListByMenuIdFromCache(menuId).any(roleIds::contains) }) {
                return true
            }
        }
        return roles.any { RoleCodeEnum.isSuperAdmin(it.code) }
    }

    override fun hasAnyRoles(userId: Long, vararg roles: String): Boolean {
        if (roles.isEmpty()) return true
        val roleCodes = roles.toSet()
        return getEnabledUserRoles(userId).any { it.code in roleCodes }
    }

    @Caching(
        evict = [
            CacheEvict(cacheNames = [RedisKeyConstants.MENU_ROLE_ID_LIST], allEntries = true),
            CacheEvict(cacheNames = [RedisKeyConstants.PERMISSION_MENU_ID_LIST], allEntries = true),
        ],
    )
    override fun assignRoleMenu(roleId: Long, menuIds: Set<Long>) {
        val current = getRoleMenuListByRoleId(listOf(roleId))
        (menuIds - current).forEach { menuId ->
            RoleMenuDao.insert(RoleMenuDO().apply {
                this.roleId = roleId
                this.menuId = menuId
            })
        }
        val removed = current - menuIds
        if (removed.isNotEmpty()) RoleMenuDao.deleteByRoleIdAndMenuIds(roleId, removed)
    }

    @Caching(
        evict = [
            CacheEvict(cacheNames = [RedisKeyConstants.MENU_ROLE_ID_LIST], allEntries = true),
            CacheEvict(cacheNames = [RedisKeyConstants.USER_ROLE_ID_LIST], allEntries = true),
        ],
    )
    override fun processRoleDeleted(roleId: Long) {
        RoleMenuDao.deleteByRoleId(roleId)
        UserRoleDao.deleteByRoleId(roleId)
    }

    @CacheEvict(cacheNames = [RedisKeyConstants.MENU_ROLE_ID_LIST], key = "#menuId")
    override fun processMenuDeleted(menuId: Long) {
        RoleMenuDao.deleteByMenuId(menuId)
    }

    override fun getRoleMenuListByRoleId(roleIds: Collection<Long>): Set<Long> {
        if (roleIds.isEmpty()) return emptySet()
        if (roleService.hasAnySuperAdmin(roleIds)) return menuService.getMenuList().mapNotNull { it.id }.toSet()
        return RoleMenuDao.selectListByRoleIds(roleIds).mapNotNull { it.menuId }.toSet()
    }

    @Cacheable(cacheNames = [RedisKeyConstants.MENU_ROLE_ID_LIST], key = "#menuId")
    override fun getMenuRoleIdListByMenuIdFromCache(menuId: Long): Set<Long> =
        RoleMenuDao.selectListByMenuId(menuId).mapNotNull { it.roleId }.toSet()

    @CacheEvict(cacheNames = [RedisKeyConstants.USER_ROLE_ID_LIST], key = "#userId")
    override fun assignUserRole(userId: Long, roleIds: Set<Long>) {
        val current = getUserRoleIdListByUserId(userId)
        (roleIds - current).forEach { roleId ->
            UserRoleDao.insert(UserRoleDO().apply {
                this.userId = userId
                this.roleId = roleId
            })
        }
        val removed = current - roleIds
        if (removed.isNotEmpty()) UserRoleDao.deleteByUserIdAndRoleIds(userId, removed)
    }

    @CacheEvict(cacheNames = [RedisKeyConstants.USER_ROLE_ID_LIST], key = "#userId")
    override fun processUserDeleted(userId: Long) {
        UserRoleDao.deleteByUserId(userId)
    }

    override fun getUserRoleIdListByRoleId(roleIds: Collection<Long>): Set<Long> =
        UserRoleDao.selectListByRoleIds(roleIds).mapNotNull { it.userId }.toSet()

    override fun getUserRoleIdListByUserId(userId: Long): Set<Long> =
        UserRoleDao.selectListByUserId(userId).mapNotNull { it.roleId }.toSet()

    @Cacheable(cacheNames = [RedisKeyConstants.USER_ROLE_ID_LIST], key = "#userId")
    override fun getUserRoleIdListByUserIdFromCache(userId: Long): Set<Long> = getUserRoleIdListByUserId(userId)

    override fun assignRoleDataScope(roleId: Long, dataScope: Int, dataScopeDeptIds: Set<Long>) {
        roleService.updateRoleDataScope(roleId, dataScope, dataScopeDeptIds)
    }

    @DataPermission(enable = false)
    override fun getDeptDataPermission(userId: Long): DeptDataPermissionRespDTO {
        val result = DeptDataPermissionRespDTO().apply {
            all = false
            self = false
            deptIds = emptySet()
        }
        val roles = getEnabledUserRoles(userId)
        if (roles.isEmpty()) {
            result.self = true
            return result
        }

        val userDeptId by lazy { AdminUserDao.selectById(userId)?.deptId }
        val deptIds = mutableSetOf<Long>()
        roles.forEach { role ->
            when (role.dataScope) {
                DataScopeEnum.ALL.scope -> result.all = true
                DataScopeEnum.SELF.scope -> result.self = true
                DataScopeEnum.DEPT_CUSTOM.scope -> {
                    deptIds += role.dataScopeDeptIds.orEmpty()
                    userDeptId?.let(deptIds::add)
                }
                DataScopeEnum.DEPT_ONLY.scope -> userDeptId?.let(deptIds::add)
                DataScopeEnum.DEPT_AND_CHILD.scope -> userDeptId?.let { id ->
                    deptIds += id
                    deptIds += deptService.getChildDeptList(id).mapNotNull { it.id }
                }
            }
        }
        result.deptIds = deptIds
        return result
    }

    private fun getEnabledUserRoles(userId: Long) =
        roleService.getRoleListFromCache(self().getUserRoleIdListByUserIdFromCache(userId))
            .filter { it.status == CommonStatusEnum.ENABLE.status }

    private fun self(): PermissionServiceImpl =
        runCatching { SpringUtils.getBean(PermissionServiceImpl::class.java) }.getOrDefault(this)
}
