package im.hikaru.ruoyi.module.system.service.permission

import im.hikaru.ruoyi.framework.common.enums.CommonStatusEnum
import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception
import im.hikaru.ruoyi.module.system.controller.admin.permission.vo.menu.MenuListReqVO
import im.hikaru.ruoyi.module.system.controller.admin.permission.vo.menu.MenuSaveVO
import im.hikaru.ruoyi.module.system.dal.dataobject.permission.MenuDO
import im.hikaru.ruoyi.module.system.dal.mysql.permission.MenuDao
import im.hikaru.ruoyi.module.system.dal.mysql.permission.RoleMenuDao
import im.hikaru.ruoyi.module.system.dal.redis.RedisKeyConstants
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.MENU_COMPONENT_NAME_DUPLICATE
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.MENU_EXISTS_CHILDREN
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.MENU_NAME_DUPLICATE
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.MENU_NOT_EXISTS
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.MENU_PARENT_ERROR
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.MENU_PARENT_NOT_EXISTS
import im.hikaru.ruoyi.module.system.service.tenant.TenantService
import im.hikaru.ruoyi.module.system.service.tenant.handler.TenantMenuHandler
import org.springframework.beans.factory.ObjectProvider
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.cache.annotation.Caching
import org.springframework.stereotype.Service

@Service
class MenuServiceImpl(private val tenantServiceProvider: ObjectProvider<TenantService>) : MenuService {
    @CacheEvict(cacheNames = [RedisKeyConstants.PERMISSION_MENU_ID_LIST], key = "#req.permission", condition = "#req.permission != null")
    override fun createMenu(req: MenuSaveVO): Long { val parent = req.parentId ?: MenuDO.ID_ROOT; validateParent(null, parent); validateUnique(null, parent, requireNotNull(req.name), req.componentName); return MenuDao.insert(req.toEntity().apply { parentId = parent }) }
    @CacheEvict(cacheNames = [RedisKeyConstants.PERMISSION_MENU_ID_LIST], allEntries = true)
    override fun updateMenu(req: MenuSaveVO) { val id = requireNotNull(req.id); validateExists(id); val parent = req.parentId ?: MenuDO.ID_ROOT; validateParent(id, parent); validateUnique(id, parent, requireNotNull(req.name), req.componentName); MenuDao.updateById(req.toEntity().apply { parentId = parent }) }
    @Caching(evict = [CacheEvict(cacheNames = [RedisKeyConstants.PERMISSION_MENU_ID_LIST], allEntries = true), CacheEvict(cacheNames = [RedisKeyConstants.MENU_ROLE_ID_LIST], key = "#id")])
    override fun deleteMenu(id: Long) { validateExists(id); if (MenuDao.selectCountByParentId(id) > 0) throw exception(MENU_EXISTS_CHILDREN); MenuDao.deleteById(id); RoleMenuDao.deleteByMenuId(id) }
    @Caching(evict = [CacheEvict(cacheNames = [RedisKeyConstants.PERMISSION_MENU_ID_LIST], allEntries = true), CacheEvict(cacheNames = [RedisKeyConstants.MENU_ROLE_ID_LIST], allEntries = true)])
    override fun deleteMenuList(ids: List<Long>) = ids.forEach(::deleteMenu)
    override fun getMenuList() = MenuDao.selectList()
    override fun getMenuListByTenant(req: MenuListReqVO): List<MenuDO> {
        val list = getMenuList(req)
        var allowed: Set<Long>? = null
        tenantServiceProvider.ifAvailable?.handleTenantMenu(TenantMenuHandler { allowed = it })
        return allowed?.let { ids -> list.filter { it.id in ids } } ?: list
    }
    override fun filterDisableMenus(list: List<MenuDO>): List<MenuDO> { val disabled = list.filter { it.status != CommonStatusEnum.ENABLE.status }.mapNotNull { it.id }.toMutableSet(); var changed: Boolean; do { changed = false; list.forEach { if (it.parentId in disabled && it.id != null && disabled.add(it.id!!)) changed = true } } while (changed); return list.filter { it.id !in disabled } }
    override fun getMenuList(req: MenuListReqVO) = MenuDao.selectList(req)
    @Cacheable(cacheNames = [RedisKeyConstants.PERMISSION_MENU_ID_LIST], key = "#permission")
    override fun getMenuIdListByPermissionFromCache(permission: String) = MenuDao.selectListByPermission(permission).mapNotNull { it.id }
    override fun getMenu(id: Long) = MenuDao.selectById(id)
    override fun getMenuList(ids: Collection<Long>) = MenuDao.selectByIds(ids)
    private fun validateExists(id: Long) = MenuDao.selectById(id) ?: throw exception(MENU_NOT_EXISTS)
    private fun validateParent(id: Long?, parentId: Long) { if (parentId == MenuDO.ID_ROOT) return; if (id == parentId) throw exception(MENU_PARENT_ERROR); MenuDao.selectById(parentId) ?: throw exception(MENU_PARENT_NOT_EXISTS) }
    private fun validateUnique(id: Long?, parentId: Long, name: String, componentName: String?) { MenuDao.selectByParentIdAndName(parentId, name)?.let { if (it.id != id) throw exception(MENU_NAME_DUPLICATE) }; if (!componentName.isNullOrBlank()) MenuDao.selectByComponentName(componentName)?.let { if (it.id != id) throw exception(MENU_COMPONENT_NAME_DUPLICATE) } }
    private fun MenuSaveVO.toEntity() = MenuDO().apply { id = this@toEntity.id; name = this@toEntity.name; permission = this@toEntity.permission; type = this@toEntity.type; sort = this@toEntity.sort; parentId = this@toEntity.parentId; path = this@toEntity.path; icon = this@toEntity.icon; component = this@toEntity.component; componentName = this@toEntity.componentName; status = this@toEntity.status; visible = this@toEntity.visible; keepAlive = this@toEntity.keepAlive; alwaysShow = this@toEntity.alwaysShow }
}
