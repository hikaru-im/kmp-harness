package im.hikaru.ruoyi.module.system.service.permission

import im.hikaru.ruoyi.framework.common.enums.CommonStatusEnum
import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception
import im.hikaru.ruoyi.framework.common.util.spring.SpringUtils
import im.hikaru.ruoyi.module.system.controller.admin.permission.vo.role.RolePageReqVO
import im.hikaru.ruoyi.module.system.controller.admin.permission.vo.role.RoleSaveReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.permission.RoleDO
import im.hikaru.ruoyi.module.system.dal.mysql.permission.RoleDao
import im.hikaru.ruoyi.module.system.dal.mysql.permission.RoleMenuDao
import im.hikaru.ruoyi.module.system.dal.mysql.permission.UserRoleDao
import im.hikaru.ruoyi.module.system.dal.redis.RedisKeyConstants
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.ROLE_ADMIN_CODE_ERROR
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.ROLE_CAN_NOT_UPDATE_SYSTEM_TYPE_ROLE
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.ROLE_CODE_DUPLICATE
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.ROLE_IS_DISABLE
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.ROLE_NAME_DUPLICATE
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.ROLE_NOT_EXISTS
import im.hikaru.ruoyi.module.system.enums.permission.DataScopeEnum
import im.hikaru.ruoyi.module.system.enums.permission.RoleCodeEnum
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.cache.annotation.Caching
import org.springframework.stereotype.Service

@Service
class RoleServiceImpl : RoleService {
    override fun createRole(req: RoleSaveReqVO, type: Int): Long {
        validateUnique(null, requireNotNull(req.name), requireNotNull(req.code))
        validateCode(req.code)
        return RoleDao.insert(req.toEntity().apply {
            this.type = type
            dataScope = DataScopeEnum.ALL.scope
            dataScopeDeptIds = emptySet()
        })
    }

    @CacheEvict(cacheNames = [RedisKeyConstants.ROLE], key = "#req.id")
    override fun updateRole(req: RoleSaveReqVO) {
        val id = requireNotNull(req.id)
        val old = validateExists(id)
        validateCustom(old)
        validateUnique(id, requireNotNull(req.name), requireNotNull(req.code))
        validateCode(req.code)
        RoleDao.updateById(req.toEntity())
    }

    @Caching(
        evict = [
            CacheEvict(cacheNames = [RedisKeyConstants.ROLE], key = "#id"),
            CacheEvict(cacheNames = [RedisKeyConstants.MENU_ROLE_ID_LIST], allEntries = true),
            CacheEvict(cacheNames = [RedisKeyConstants.USER_ROLE_ID_LIST], allEntries = true),
        ],
    )
    override fun deleteRole(id: Long) {
        validateCustom(validateExists(id))
        RoleDao.deleteById(id)
        RoleMenuDao.deleteByRoleId(id)
        UserRoleDao.deleteByRoleId(id)
    }

    @Caching(
        evict = [
            CacheEvict(cacheNames = [RedisKeyConstants.ROLE], allEntries = true),
            CacheEvict(cacheNames = [RedisKeyConstants.MENU_ROLE_ID_LIST], allEntries = true),
            CacheEvict(cacheNames = [RedisKeyConstants.USER_ROLE_ID_LIST], allEntries = true),
        ],
    )
    override fun deleteRoleList(ids: List<Long>) = ids.forEach(::deleteRole)

    @CacheEvict(cacheNames = [RedisKeyConstants.ROLE], key = "#id")
    override fun updateRoleDataScope(id: Long, dataScope: Int, dataScopeDeptIds: Set<Long>) {
        validateExists(id)
        RoleDao.updateById(RoleDO().apply {
            this.id = id
            this.dataScope = dataScope
            this.dataScopeDeptIds = dataScopeDeptIds
        })
    }

    override fun getRole(id: Long): RoleDO? = RoleDao.selectById(id)

    @Cacheable(cacheNames = [RedisKeyConstants.ROLE], key = "#id", unless = "#result == null")
    override fun getRoleFromCache(id: Long): RoleDO? = RoleDao.selectById(id)

    override fun getRoleList(ids: Collection<Long>): List<RoleDO> =
        if (ids.isEmpty()) emptyList() else RoleDao.selectByIds(ids)

    override fun getRoleListFromCache(ids: Collection<Long>): List<RoleDO> {
        if (ids.isEmpty()) return emptyList()
        val self = self()
        return ids.mapNotNull(self::getRoleFromCache)
    }

    override fun getRoleListByStatus(statuses: Collection<Int>): List<RoleDO> = RoleDao.selectList(statuses)
    override fun getRoleList(): List<RoleDO> = RoleDao.selectList()
    override fun getRolePage(req: RolePageReqVO) = RoleDao.selectPage(req)

    override fun hasAnySuperAdmin(ids: Collection<Long>): Boolean =
        getRoleListFromCache(ids).any { RoleCodeEnum.isSuperAdmin(it.code) }

    override fun validateRoleList(ids: Collection<Long>) {
        val roleMap = getRoleList(ids).associateBy { it.id }
        ids.forEach { id ->
            val role = roleMap[id] ?: throw exception(ROLE_NOT_EXISTS)
            if (role.status != CommonStatusEnum.ENABLE.status) {
                throw exception(ROLE_IS_DISABLE, role.name ?: "")
            }
        }
    }

    private fun validateExists(id: Long): RoleDO = RoleDao.selectById(id) ?: throw exception(ROLE_NOT_EXISTS)

    private fun validateCustom(role: RoleDO) {
        if (role.type == 1) throw exception(ROLE_CAN_NOT_UPDATE_SYSTEM_TYPE_ROLE)
    }

    private fun validateUnique(id: Long?, name: String, code: String) {
        RoleDao.selectByName(name)?.let { if (it.id != id) throw exception(ROLE_NAME_DUPLICATE, name) }
        RoleDao.selectByCode(code)?.let { if (it.id != id) throw exception(ROLE_CODE_DUPLICATE, code) }
    }

    private fun validateCode(code: String?) {
        if (RoleCodeEnum.isSuperAdmin(code)) throw exception(ROLE_ADMIN_CODE_ERROR, code ?: "")
    }

    private fun self(): RoleServiceImpl = runCatching { SpringUtils.getBean(RoleServiceImpl::class.java) }.getOrDefault(this)

    private fun RoleSaveReqVO.toEntity() = RoleDO().apply {
        id = this@toEntity.id
        name = this@toEntity.name
        code = this@toEntity.code
        sort = this@toEntity.sort
        status = this@toEntity.status
        remark = this@toEntity.remark
    }
}
