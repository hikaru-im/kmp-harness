package im.hikaru.ruoyi.module.system.service.permission

import im.hikaru.ruoyi.module.system.controller.admin.permission.vo.menu.MenuListReqVO
import im.hikaru.ruoyi.module.system.controller.admin.permission.vo.menu.MenuSaveVO
import im.hikaru.ruoyi.module.system.dal.dataobject.permission.MenuDO

interface MenuService {
    fun createMenu(req: MenuSaveVO): Long; fun updateMenu(req: MenuSaveVO); fun deleteMenu(id: Long); fun deleteMenuList(ids: List<Long>)
    fun getMenuList(): List<MenuDO>; fun getMenuListByTenant(req: MenuListReqVO): List<MenuDO> = getMenuList(req); fun filterDisableMenus(list: List<MenuDO>): List<MenuDO>
    fun getMenuList(req: MenuListReqVO): List<MenuDO>; fun getMenuIdListByPermissionFromCache(permission: String): List<Long>; fun getMenu(id: Long): MenuDO?; fun getMenuList(ids: Collection<Long>): List<MenuDO>
}
