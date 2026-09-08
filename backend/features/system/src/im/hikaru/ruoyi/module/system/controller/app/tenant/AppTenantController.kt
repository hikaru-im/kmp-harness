package im.hikaru.ruoyi.module.system.controller.app.tenant

import im.hikaru.ruoyi.framework.common.enums.CommonStatusEnum
import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.tenant.core.aop.TenantIgnore
import im.hikaru.ruoyi.module.system.controller.app.tenant.vo.AppTenantRespVO
import im.hikaru.ruoyi.module.system.service.tenant.TenantService
import jakarta.annotation.security.PermitAll
import jakarta.validation.constraints.Pattern
import org.springframework.web.bind.annotation.*

@RestController @RequestMapping("/system/tenant")
class AppTenantController(private val service: TenantService) {
    @GetMapping("/get-by-website") @PermitAll @TenantIgnore
    fun getByWebsite(@RequestParam("website") @Pattern(regexp = "^[a-zA-Z0-9.-]+(:\\d{1,5})?$") website: String): CommonResult<AppTenantRespVO?> { val tenant = service.getTenantByWebsite(website); return CommonResult.success(if (tenant?.status == CommonStatusEnum.ENABLE.status) AppTenantRespVO(tenant.id, tenant.name) else null) }
}
