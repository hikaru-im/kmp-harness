package im.hikaru.ruoyi.module.system.api.permission

import im.hikaru.ruoyi.framework.common.biz.system.permission.dto.DeptDataPermissionRespDTO
import im.hikaru.ruoyi.module.system.service.permission.PermissionService
import org.springframework.stereotype.Service

@Service
class PermissionApiImpl(private val service: PermissionService) : PermissionApi {
    override fun getUserRoleIdListByRoleIds(roleIds: Collection<Long>) = service.getUserRoleIdListByRoleId(roleIds)
    override fun hasAnyPermissions(userId: Long, vararg permissions: String) = service.hasAnyPermissions(userId, *permissions)
    override fun hasAnyRoles(userId: Long, vararg roles: String) = service.hasAnyRoles(userId, *roles)
    override fun getDeptDataPermission(userId: Long): DeptDataPermissionRespDTO = service.getDeptDataPermission(userId)
}
