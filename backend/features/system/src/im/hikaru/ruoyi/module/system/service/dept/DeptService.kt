package im.hikaru.ruoyi.module.system.service.dept

import im.hikaru.ruoyi.module.system.controller.admin.dept.vo.dept.DeptListReqVO
import im.hikaru.ruoyi.module.system.controller.admin.dept.vo.dept.DeptSaveReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.dept.DeptDO

interface DeptService {
    fun createDept(req: DeptSaveReqVO): Long
    fun updateDept(req: DeptSaveReqVO)
    fun deleteDept(id: Long)
    fun deleteDeptList(ids: List<Long>)
    fun getDept(id: Long): DeptDO?
    fun getDeptList(ids: Collection<Long>): List<DeptDO>
    fun getDeptList(req: DeptListReqVO): List<DeptDO>
    fun getDeptMap(ids: Collection<Long>): Map<Long, DeptDO> = getDeptList(ids).mapNotNull { it.id?.let { id -> id to it } }.toMap()
    fun getChildDeptList(id: Long): List<DeptDO> = getChildDeptList(listOf(id))
    fun getChildDeptList(ids: Collection<Long>): List<DeptDO>
    fun getDeptListByLeaderUserId(id: Long): List<DeptDO>
    fun getChildDeptIdListFromCache(id: Long): Set<Long>
    fun validateDeptList(ids: Collection<Long>)
}
