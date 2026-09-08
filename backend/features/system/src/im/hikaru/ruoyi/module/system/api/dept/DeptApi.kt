package im.hikaru.ruoyi.module.system.api.dept

import im.hikaru.ruoyi.module.system.api.dept.dto.DeptRespDTO

interface DeptApi {
    fun getDept(id: Long): DeptRespDTO?
    fun getDeptList(ids: Collection<Long>): List<DeptRespDTO>
    fun validateDeptList(ids: Collection<Long>)
    fun getDeptMap(ids: Collection<Long>): Map<Long, DeptRespDTO> =
        getDeptList(ids).mapNotNull { dto -> dto.id?.let { it to dto } }.toMap()
    fun getChildDeptList(id: Long): List<DeptRespDTO>
}
