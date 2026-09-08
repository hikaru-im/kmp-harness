package im.hikaru.ruoyi.module.system.controller.admin.tenant

import im.hikaru.ruoyi.framework.apilog.core.annotation.ApiAccessLog
import im.hikaru.ruoyi.framework.apilog.core.enums.OperateTypeEnum
import im.hikaru.ruoyi.framework.common.enums.CommonStatusEnum
import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.pojo.PageParam
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.framework.excel.core.util.ExcelUtils
import im.hikaru.ruoyi.framework.tenant.core.aop.TenantIgnore
import im.hikaru.ruoyi.module.system.controller.admin.tenant.vo.tenant.*
import im.hikaru.ruoyi.module.system.dal.dataobject.tenant.TenantDO
import im.hikaru.ruoyi.module.system.service.tenant.TenantService
import jakarta.annotation.security.PermitAll
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import jakarta.validation.constraints.Pattern
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@RestController @RequestMapping("/system/tenant") @Validated
class TenantController(private val service: TenantService) {
    @GetMapping("/get-id-by-name") @PermitAll @TenantIgnore
    fun getIdByName(@RequestParam("name") name: String): CommonResult<Long?> = CommonResult.success(service.getTenantByName(name)?.id)

    @GetMapping("/simple-list") @PermitAll @TenantIgnore
    fun simpleList(): CommonResult<List<TenantRespVO>> = CommonResult.success(
        service.getTenantListByStatus(CommonStatusEnum.ENABLE.status).map { tenant ->
            TenantRespVO().apply { id = tenant.id; name = tenant.name }
        },
    )

    @GetMapping("/get-by-website") @PermitAll @TenantIgnore
    fun getByWebsite(
        @RequestParam("website") @Pattern(regexp = "^[a-zA-Z0-9.-]+(:\\d{1,5})?$") website: String,
    ): CommonResult<TenantRespVO?> {
        val tenant = service.getTenantByWebsite(website)
        return CommonResult.success(
            if (tenant?.status == CommonStatusEnum.ENABLE.status) {
                TenantRespVO().apply { id = tenant.id; name = tenant.name }
            } else {
                null
            },
        )
    }

    @PostMapping("/create") @PreAuthorize("@ss.hasPermission('system:tenant:create')") fun create(@Valid @RequestBody req: TenantSaveReqVO) = CommonResult.success(service.createTenant(req))
    @PutMapping("/update") @PreAuthorize("@ss.hasPermission('system:tenant:update')") fun update(@Valid @RequestBody req: TenantSaveReqVO): CommonResult<Boolean> { service.updateTenant(req); return CommonResult.success(true) }
    @DeleteMapping("/delete") @PreAuthorize("@ss.hasPermission('system:tenant:delete')") fun delete(@RequestParam("id") id: Long): CommonResult<Boolean> { service.deleteTenant(id); return CommonResult.success(true) }
    @DeleteMapping("/delete-list") @PreAuthorize("@ss.hasPermission('system:tenant:delete')") fun deleteList(@RequestParam("ids") ids: List<Long>): CommonResult<Boolean> { service.deleteTenantList(ids); return CommonResult.success(true) }
    @GetMapping("/get") @PreAuthorize("@ss.hasPermission('system:tenant:query')") fun get(@RequestParam("id") id: Long): CommonResult<TenantRespVO?> = CommonResult.success(service.getTenant(id)?.toResp())
    @GetMapping("/page") @PreAuthorize("@ss.hasPermission('system:tenant:query')") fun page(req: TenantPageReqVO): CommonResult<PageResult<TenantRespVO>> = CommonResult.success(service.getTenantPage(req).let { PageResult(it.total ?: 0, it.list.map { tenant -> tenant.toResp() }) })

    @GetMapping("/export-excel")
    @PreAuthorize("@ss.hasPermission('system:tenant:export')")
    @ApiAccessLog(operateType = [OperateTypeEnum.EXPORT])
    fun export(req: TenantPageReqVO, response: HttpServletResponse) {
        req.pageSize = PageParam.PAGE_SIZE_NONE
        ExcelUtils.write(response, "tenants.xls", "Tenants", TenantRespVO::class.java, service.getTenantPage(req).list.map { it.toResp() })
    }

    private fun TenantDO.toResp() = TenantRespVO().apply { id = this@toResp.id; name = this@toResp.name; contactName = this@toResp.contactName; contactMobile = this@toResp.contactMobile; status = this@toResp.status; websites = this@toResp.websites; packageId = this@toResp.packageId; accountCount = this@toResp.accountCount; expireTime = this@toResp.expireTime?.toJava(); createTime = this@toResp.createTime?.toJava() }
    private fun kotlinx.datetime.LocalDateTime.toJava() = java.time.LocalDateTime.of(year, month.ordinal + 1, day, hour, minute, second, nanosecond)
}
