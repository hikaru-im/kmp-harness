package im.hikaru.ruoyi.module.system.controller.admin.permission

import im.hikaru.ruoyi.framework.common.enums.CommonStatusEnum
import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.module.system.controller.admin.permission.vo.menu.*
import im.hikaru.ruoyi.module.system.dal.dataobject.permission.MenuDO
import im.hikaru.ruoyi.module.system.service.permission.MenuService
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@Tag(name = "Admin - Menu") @RestController @RequestMapping("/system/menu") @Validated
class MenuController(private val service: MenuService) {
    @PostMapping("/create") @PreAuthorize("@ss.hasPermission('system:menu:create')") fun create(@Valid @RequestBody req: MenuSaveVO) = CommonResult.success(service.createMenu(req))
    @PutMapping("/update") @PreAuthorize("@ss.hasPermission('system:menu:update')") fun update(@Valid @RequestBody req: MenuSaveVO): CommonResult<Boolean> { service.updateMenu(req); return CommonResult.success(true) }
    @DeleteMapping("/delete") @PreAuthorize("@ss.hasPermission('system:menu:delete')") fun delete(@RequestParam("id") id: Long): CommonResult<Boolean> { service.deleteMenu(id); return CommonResult.success(true) }
    @DeleteMapping("/delete-list") @PreAuthorize("@ss.hasPermission('system:menu:delete')") fun deleteList(@RequestParam("ids") ids: List<Long>): CommonResult<Boolean> { service.deleteMenuList(ids); return CommonResult.success(true) }
    @GetMapping("/list") @PreAuthorize("@ss.hasPermission('system:menu:query')") fun list(req: MenuListReqVO): CommonResult<List<MenuRespVO>> = CommonResult.success(service.getMenuList(req).map { it.toResp() })
    @GetMapping("/list-all-simple", "/simple-list") fun simpleList(): CommonResult<List<MenuSimpleRespVO>> = CommonResult.success(service.filterDisableMenus(service.getMenuList(MenuListReqVO().apply { status = CommonStatusEnum.ENABLE.status })).map { MenuSimpleRespVO(it.id, it.name, it.parentId, it.type) })
    @GetMapping("/get") @PreAuthorize("@ss.hasPermission('system:menu:query')") fun get(@RequestParam("id") id: Long): CommonResult<MenuRespVO?> = CommonResult.success(service.getMenu(id)?.toResp())
    private fun MenuDO.toResp() = MenuRespVO().apply { id = this@toResp.id; name = this@toResp.name; permission = this@toResp.permission; type = this@toResp.type; sort = this@toResp.sort; parentId = this@toResp.parentId; path = this@toResp.path; icon = this@toResp.icon; component = this@toResp.component; componentName = this@toResp.componentName; status = this@toResp.status; visible = this@toResp.visible; keepAlive = this@toResp.keepAlive; alwaysShow = this@toResp.alwaysShow; createTime = this@toResp.createTime?.let { java.time.LocalDateTime.of(it.year, it.month.ordinal + 1, it.day, it.hour, it.minute, it.second, it.nanosecond) } }
}
