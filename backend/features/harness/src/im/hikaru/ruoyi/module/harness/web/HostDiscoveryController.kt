package im.hikaru.ruoyi.module.harness.web

import im.hikaru.contracts.common.ApiResult
import im.hikaru.contracts.harness.identity.HostDescription
import im.hikaru.ruoyi.framework.security.core.util.SecurityFrameworkUtils
import im.hikaru.ruoyi.module.harness.relay.HarnessPrincipal
import im.hikaru.ruoyi.module.harness.relay.HostConnectionRegistry
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** Returns only Hosts owned by the authenticated Member identity. */
@RestController
@RequestMapping("/app-api/harness")
class HostDiscoveryController(private val registry: HostConnectionRegistry) {
    @GetMapping("/hosts")
    fun hosts(): ApiResult<List<HostDescription>> {
        val loginUser = SecurityFrameworkUtils.getLoginUser() ?: return ApiResult(401, "Unauthorized")
        val principal = HarnessPrincipal(
            tenantId = loginUser.tenantId ?: return ApiResult(401, "Unauthorized"),
            userType = loginUser.userType ?: return ApiResult(401, "Unauthorized"),
            userId = loginUser.id ?: return ApiResult(401, "Unauthorized"),
        )
        return ApiResult(ApiResult.SUCCESS_CODE, "", registry.findAll(principal).map { it.description })
    }
}
