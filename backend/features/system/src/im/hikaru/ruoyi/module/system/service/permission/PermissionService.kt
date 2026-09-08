package im.hikaru.ruoyi.module.system.service.permission

import im.hikaru.ruoyi.framework.common.biz.system.permission.dto.DeptDataPermissionRespDTO

interface PermissionService {
    fun hasAnyPermissions(userId: Long, vararg permissions: String): Boolean; fun hasAnyRoles(userId: Long, vararg roles: String): Boolean
    fun assignRoleMenu(roleId: Long, menuIds: Set<Long>); fun processRoleDeleted(roleId: Long); fun processMenuDeleted(menuId: Long)
    fun getRoleMenuListByRoleId(roleIds: Collection<Long>): Set<Long>; fun getMenuRoleIdListByMenuIdFromCache(menuId: Long): Set<Long>
    fun assignUserRole(userId: Long, roleIds: Set<Long>); fun processUserDeleted(userId: Long); fun getUserRoleIdListByRoleId(roleIds: Collection<Long>): Set<Long>
    fun getUserRoleIdListByUserId(userId: Long): Set<Long>; fun getUserRoleIdListByUserIdFromCache(userId: Long): Set<Long> = getUserRoleIdListByUserId(userId)
    fun assignRoleDataScope(roleId: Long, dataScope: Int, dataScopeDeptIds: Set<Long>); fun getDeptDataPermission(userId: Long): DeptDataPermissionRespDTO
}
