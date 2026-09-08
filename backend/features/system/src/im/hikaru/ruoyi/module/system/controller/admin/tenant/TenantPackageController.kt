package im.hikaru.ruoyi.module.system.controller.admin.tenant

import im.hikaru.ruoyi.framework.common.enums.CommonStatusEnum
import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.pojo.PageResult
import im.hikaru.ruoyi.module.system.controller.admin.tenant.vo.packages.*
import im.hikaru.ruoyi.module.system.dal.dataobject.tenant.TenantPackageDO
import im.hikaru.ruoyi.module.system.service.tenant.TenantPackageService
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*

@RestController @RequestMapping("/system/tenant-package") @Validated
class TenantPackageController(private val service: TenantPackageService) {
    @PostMapping("/create") @PreAuthorize("@ss.hasPermission('system:tenant-package:create')") fun create(@Valid @RequestBody req: TenantPackageSaveReqVO) = CommonResult.success(service.createTenantPackage(req))
    @PutMapping("/update") @PreAuthorize("@ss.hasPermission('system:tenant-package:update')") fun update(@Valid @RequestBody req: TenantPackageSaveReqVO): CommonResult<Boolean> { service.updateTenantPackage(req); return CommonResult.success(true) }
    @DeleteMapping("/delete") @PreAuthorize("@ss.hasPermission('system:tenant-package:delete')") fun delete(@RequestParam("id") id: Long): CommonResult<Boolean> { service.deleteTenantPackage(id); return CommonResult.success(true) }
    @DeleteMapping("/delete-list") @PreAuthorize("@ss.hasPermission('system:tenant-package:delete')") fun deleteList(@RequestParam("ids") ids: List<Long>): CommonResult<Boolean> { service.deleteTenantPackageList(ids); return CommonResult.success(true) }
    @GetMapping("/get") @PreAuthorize("@ss.hasPermission('system:tenant-package:query')") fun get(@RequestParam("id") id: Long): CommonResult<TenantPackageRespVO?> = CommonResult.success(service.getTenantPackage(id)?.toResp())
    @GetMapping("/page") @PreAuthorize("@ss.hasPermission('system:tenant-package:query')") fun page(req: TenantPackagePageReqVO): CommonResult<PageResult<TenantPackageRespVO>> = CommonResult.success(service.getTenantPackagePage(req).let { PageResult(it.total ?: 0, it.list.map { pkg -> pkg.toResp() }) })
    @GetMapping("/get-simple-list", "/list-all-simple", "/simple-list") fun simpleList() = CommonResult.success(service.getTenantPackageListByStatus(CommonStatusEnum.ENABLE.status).map { TenantPackageSimpleRespVO(it.id, it.name) })
    private fun TenantPackageDO.toResp() = TenantPackageRespVO().apply { id = this@toResp.id; name = this@toResp.name; status = this@toResp.status; remark = this@toResp.remark; menuIds = this@toResp.menuIds; createTime = this@toResp.createTime?.let { java.time.LocalDateTime.of(it.year, it.month.ordinal + 1, it.day, it.hour, it.minute, it.second, it.nanosecond) } }
}
