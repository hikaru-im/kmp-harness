package im.hikaru.ruoyi.module.system.api.permission

import im.hikaru.ruoyi.module.system.api.permission.dto.RoleRespDTO

interface RoleApi {
    fun validRoleList(ids: Collection<Long>)
    fun getRole(id: Long): RoleRespDTO?
    fun getRoleList(ids: Collection<Long>): List<RoleRespDTO>
    fun getRoleMap(ids: Collection<Long>): Map<Long, RoleRespDTO> =
        getRoleList(ids).mapNotNull { dto -> dto.id?.let { it to dto } }.toMap()
}
