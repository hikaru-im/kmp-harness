package im.hikaru.ruoyi.module.system.controller.admin.permission

import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.module.system.controller.admin.permission.vo.permission.*
import im.hikaru.ruoyi.module.system.service.permission.PermissionService
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@Tag(name = "Admin - Permission") @RestController @RequestMapping("/system/permission")
class PermissionController(private val service: PermissionService) {
    @GetMapping("/list-role-menus") @PreAuthorize("@ss.hasPermission('system:permission:assign-role-menu')") fun roleMenus(@RequestParam("roleId") roleId: Long) = CommonResult.success(service.getRoleMenuListByRoleId(listOf(roleId)))
    @PostMapping("/assign-role-menu") @PreAuthorize("@ss.hasPermission('system:permission:assign-role-menu')") fun assignRoleMenu(@Valid @RequestBody req: PermissionAssignRoleMenuReqVO): CommonResult<Boolean> { service.assignRoleMenu(requireNotNull(req.roleId), req.menuIds); return CommonResult.success(true) }
    @PostMapping("/assign-role-data-scope") @PreAuthorize("@ss.hasPermission('system:permission:assign-role-data-scope')") fun assignDataScope(@Valid @RequestBody req: PermissionAssignRoleDataScopeReqVO): CommonResult<Boolean> { service.assignRoleDataScope(requireNotNull(req.roleId), requireNotNull(req.dataScope), req.dataScopeDeptIds); return CommonResult.success(true) }
    @GetMapping("/list-user-roles") @PreAuthorize("@ss.hasPermission('system:permission:assign-user-role')") fun userRoles(@RequestParam("userId") userId: Long) = CommonResult.success(service.getUserRoleIdListByUserId(userId))
    @PostMapping("/assign-user-role") @PreAuthorize("@ss.hasPermission('system:permission:assign-user-role')") fun assignUserRole(@Valid @RequestBody req: PermissionAssignUserRoleReqVO): CommonResult<Boolean> { service.assignUserRole(requireNotNull(req.userId), req.roleIds); return CommonResult.success(true) }
}
