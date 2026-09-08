package im.hikaru.ruoyi.module.system.api.dept

import im.hikaru.ruoyi.module.system.api.dept.dto.DeptRespDTO
import im.hikaru.ruoyi.module.system.service.dept.DeptService
import org.springframework.stereotype.Service

@Service
class DeptApiImpl(private val service: DeptService) : DeptApi {
    override fun getDept(id: Long): DeptRespDTO? = service.getDept(id)?.toDto()
    override fun getDeptList(ids: Collection<Long>): List<DeptRespDTO> = service.getDeptList(ids).map { it.toDto() }
    override fun validateDeptList(ids: Collection<Long>) = service.validateDeptList(ids)
    override fun getChildDeptList(id: Long): List<DeptRespDTO> = service.getChildDeptList(id).map { it.toDto() }
    private fun im.hikaru.ruoyi.module.system.dal.dataobject.dept.DeptDO.toDto() = DeptRespDTO().apply { id = this@toDto.id; name = this@toDto.name; parentId = this@toDto.parentId; leaderUserId = this@toDto.leaderUserId; status = this@toDto.status }
}
