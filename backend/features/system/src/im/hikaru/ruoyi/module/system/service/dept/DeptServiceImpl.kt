package im.hikaru.ruoyi.module.system.service.dept

import im.hikaru.ruoyi.framework.common.enums.CommonStatusEnum
import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception
import im.hikaru.ruoyi.module.system.controller.admin.dept.vo.dept.DeptListReqVO
import im.hikaru.ruoyi.module.system.controller.admin.dept.vo.dept.DeptSaveReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.dept.DeptDO
import im.hikaru.ruoyi.module.system.dal.mysql.dept.DeptDao
import im.hikaru.ruoyi.module.system.dal.redis.RedisKeyConstants
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.DEPT_EXITS_CHILDREN
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.DEPT_NAME_DUPLICATE
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.DEPT_NOT_ENABLE
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.DEPT_NOT_FOUND
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.DEPT_PARENT_ERROR
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.DEPT_PARENT_IS_CHILD
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.DEPT_PARENT_NOT_EXITS
import org.springframework.stereotype.Service
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.validation.annotation.Validated

@Service
@Validated
class DeptServiceImpl : DeptService {
    @CacheEvict(cacheNames = [RedisKeyConstants.DEPT_CHILDREN_ID_LIST], allEntries = true)
    override fun createDept(req: DeptSaveReqVO): Long {
        val parent = req.parentId ?: DeptDO.PARENT_ID_ROOT
        validateParentDept(null, parent); validateNameUnique(null, parent, requireNotNull(req.name))
        return DeptDao.insert(req.toEntity().apply { parentId = parent })
    }
    @CacheEvict(cacheNames = [RedisKeyConstants.DEPT_CHILDREN_ID_LIST], allEntries = true)
    override fun updateDept(req: DeptSaveReqVO) {
        val id = requireNotNull(req.id); val parent = req.parentId ?: DeptDO.PARENT_ID_ROOT
        validateExists(id); validateParentDept(id, parent); validateNameUnique(id, parent, requireNotNull(req.name)); DeptDao.updateById(req.toEntity().apply { parentId = parent })
    }
    @CacheEvict(cacheNames = [RedisKeyConstants.DEPT_CHILDREN_ID_LIST], allEntries = true)
    override fun deleteDept(id: Long) { validateExists(id); if (DeptDao.selectCountByParentId(id) > 0) throw exception(DEPT_EXITS_CHILDREN); DeptDao.deleteById(id) }
    @CacheEvict(cacheNames = [RedisKeyConstants.DEPT_CHILDREN_ID_LIST], allEntries = true)
    override fun deleteDeptList(ids: List<Long>) { ids.forEach { if (DeptDao.selectCountByParentId(it) > 0) throw exception(DEPT_EXITS_CHILDREN) }; DeptDao.deleteByIds(ids) }
    override fun getDept(id: Long) = DeptDao.selectById(id)
    override fun getDeptList(ids: Collection<Long>) = DeptDao.selectByIds(ids)
    override fun getDeptList(req: DeptListReqVO) = DeptDao.selectList(req).sortedBy { it.sort ?: 0 }
    override fun getChildDeptList(ids: Collection<Long>): List<DeptDO> {
        val result = mutableListOf<DeptDO>(); var parents = ids.toSet()
        repeat(Short.MAX_VALUE.toInt()) { val children = DeptDao.selectListByParentId(parents); if (children.isEmpty()) return result; result += children; parents = children.mapNotNull { it.id }.toSet() }
        return result
    }
    override fun getDeptListByLeaderUserId(id: Long) = DeptDao.selectListByLeaderUserId(id)
    @Cacheable(cacheNames = [RedisKeyConstants.DEPT_CHILDREN_ID_LIST], key = "#id")
    override fun getChildDeptIdListFromCache(id: Long) = getChildDeptList(id).mapNotNull { it.id }.toSet()
    override fun validateDeptList(ids: Collection<Long>) { getDeptMap(ids).also { map -> ids.forEach { val dept = map[it] ?: throw exception(DEPT_NOT_FOUND); if (dept.status != CommonStatusEnum.ENABLE.status) throw exception(DEPT_NOT_ENABLE, dept.name ?: "") } } }

    private fun validateExists(id: Long) { if (DeptDao.selectById(id) == null) throw exception(DEPT_NOT_FOUND) }
    private fun validateParentDept(id: Long?, parentId: Long) {
        if (parentId == DeptDO.PARENT_ID_ROOT) return
        if (id == parentId) throw exception(DEPT_PARENT_ERROR)
        var parent = DeptDao.selectById(parentId) ?: throw exception(DEPT_PARENT_NOT_EXITS)
        if (id == null) return
        repeat(Short.MAX_VALUE.toInt()) {
            val next = parent.parentId ?: DeptDO.PARENT_ID_ROOT
            if (next == id) throw exception(DEPT_PARENT_IS_CHILD)
            if (next == DeptDO.PARENT_ID_ROOT) return
            parent = DeptDao.selectById(next) ?: return
        }
    }
    private fun validateNameUnique(id: Long?, parentId: Long, name: String) { val existing = DeptDao.selectByParentIdAndName(parentId, name); if (existing != null && existing.id != id) throw exception(DEPT_NAME_DUPLICATE) }
    private fun DeptSaveReqVO.toEntity() = DeptDO().apply { this.id = this@toEntity.id; name = this@toEntity.name; parentId = this@toEntity.parentId; sort = this@toEntity.sort; leaderUserId = this@toEntity.leaderUserId; phone = this@toEntity.phone; email = this@toEntity.email; status = this@toEntity.status }
}
