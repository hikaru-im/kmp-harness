package im.hikaru.ruoyi.module.system.service.permission

import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.system.controller.admin.permission.vo.role.RolePageReqVO
import im.hikaru.ruoyi.module.system.controller.admin.permission.vo.role.RoleSaveReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.permission.RoleDO

interface RoleService {
    fun createRole(req: RoleSaveReqVO, type: Int): Long; fun updateRole(req: RoleSaveReqVO); fun deleteRole(id: Long); fun deleteRoleList(ids: List<Long>)
    fun updateRoleDataScope(id: Long, dataScope: Int, dataScopeDeptIds: Set<Long>); fun getRole(id: Long): RoleDO?; fun getRoleFromCache(id: Long): RoleDO? = getRole(id)
    fun getRoleList(ids: Collection<Long>): List<RoleDO>; fun getRoleListFromCache(ids: Collection<Long>): List<RoleDO> = getRoleList(ids)
    fun getRoleListByStatus(statuses: Collection<Int>): List<RoleDO>; fun getRoleList(): List<RoleDO>; fun getRolePage(req: RolePageReqVO): PageResult<RoleDO>
    fun hasAnySuperAdmin(ids: Collection<Long>): Boolean; fun validateRoleList(ids: Collection<Long>)
}
