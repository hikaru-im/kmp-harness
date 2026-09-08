package im.hikaru.ruoyi.module.system.controller.admin.permission

import im.hikaru.ruoyi.framework.apilog.core.annotation.ApiAccessLog
import im.hikaru.ruoyi.framework.apilog.core.enums.OperateTypeEnum
import im.hikaru.ruoyi.framework.common.enums.CommonStatusEnum
import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.excel.core.util.ExcelUtils
import im.hikaru.ruoyi.module.system.controller.admin.permission.vo.role.*
import im.hikaru.ruoyi.module.system.dal.dataobject.permission.RoleDO
import im.hikaru.ruoyi.module.system.enums.permission.RoleTypeEnum
import im.hikaru.ruoyi.module.system.service.permission.RoleService
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@Tag(name = "Admin - Role") @RestController @RequestMapping("/system/role") @Validated
class RoleController(private val service: RoleService) {
    @PostMapping("/create") @PreAuthorize("@ss.hasPermission('system:role:create')") fun create(@Valid @RequestBody req: RoleSaveReqVO) = CommonResult.success(service.createRole(req, RoleTypeEnum.CUSTOM.type))
    @PutMapping("/update") @PreAuthorize("@ss.hasPermission('system:role:update')") fun update(@Valid @RequestBody req: RoleSaveReqVO): CommonResult<Boolean> { service.updateRole(req); return CommonResult.success(true) }
    @DeleteMapping("/delete") @PreAuthorize("@ss.hasPermission('system:role:delete')") fun delete(@RequestParam("id") id: Long): CommonResult<Boolean> { service.deleteRole(id); return CommonResult.success(true) }
    @DeleteMapping("/delete-list") @PreAuthorize("@ss.hasPermission('system:role:delete')") fun deleteList(@RequestParam("ids") ids: List<Long>): CommonResult<Boolean> { service.deleteRoleList(ids); return CommonResult.success(true) }
    @GetMapping("/get") @PreAuthorize("@ss.hasPermission('system:role:query')") fun get(@RequestParam("id") id: Long): CommonResult<RoleRespVO?> = CommonResult.success(service.getRole(id)?.toResp())
    @GetMapping("/page") @PreAuthorize("@ss.hasPermission('system:role:query')") fun page(req: RolePageReqVO): CommonResult<PageResult<RoleRespVO>> = CommonResult.success(service.getRolePage(req).let { PageResult(it.total ?: 0, it.list.map { role -> role.toResp() }) })
    @GetMapping("/list-all-simple", "/simple-list") fun simpleList(): CommonResult<List<RoleSimpleRespVO>> = CommonResult.success(service.getRoleListByStatus(listOf(CommonStatusEnum.ENABLE.status)).sortedBy { it.sort }.map { RoleSimpleRespVO(it.id, it.name) })

    @GetMapping("/export-excel")
    @PreAuthorize("@ss.hasPermission('system:role:export')")
    @ApiAccessLog(operateType = [OperateTypeEnum.EXPORT])
    fun export(response: HttpServletResponse, req: RolePageReqVO) {
        req.pageSize = PageParam.PAGE_SIZE_NONE
        ExcelUtils.write(response, "roles.xls", "Roles", RoleRespVO::class.java, service.getRolePage(req).list.map { it.toResp() })
    }

    private fun RoleDO.toResp() = RoleRespVO().apply { id = this@toResp.id; name = this@toResp.name; code = this@toResp.code; sort = this@toResp.sort; status = this@toResp.status; type = this@toResp.type; remark = this@toResp.remark; dataScope = this@toResp.dataScope; dataScopeDeptIds = this@toResp.dataScopeDeptIds; createTime = this@toResp.createTime?.let { java.time.LocalDateTime.of(it.year, it.month.ordinal + 1, it.day, it.hour, it.minute, it.second, it.nanosecond) } }
}
