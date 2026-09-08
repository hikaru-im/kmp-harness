package im.hikaru.ruoyi.module.system.api.permission

import im.hikaru.ruoyi.module.system.api.permission.dto.RoleRespDTO
import im.hikaru.ruoyi.module.system.service.permission.RoleService
import org.springframework.stereotype.Service

@Service
class RoleApiImpl(private val service: RoleService) : RoleApi {
    override fun validRoleList(ids: Collection<Long>) = service.validateRoleList(ids)
    override fun getRole(id: Long): RoleRespDTO? = service.getRole(id)?.toDto()
    override fun getRoleList(ids: Collection<Long>) = service.getRoleList(ids).map { it.toDto() }
    private fun im.hikaru.ruoyi.module.system.dal.dataobject.permission.RoleDO.toDto() = RoleRespDTO().apply { id = this@toDto.id; name = this@toDto.name; code = this@toDto.code; sort = this@toDto.sort; status = this@toDto.status }
}
