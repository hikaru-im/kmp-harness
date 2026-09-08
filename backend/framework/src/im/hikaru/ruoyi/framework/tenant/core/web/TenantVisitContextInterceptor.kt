package im.hikaru.ruoyi.framework.tenant.core.web

import im.hikaru.ruoyi.framework.common.exception.enums.GlobalErrorCodeConstants
import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception0
import im.hikaru.ruoyi.framework.security.core.service.SecurityFrameworkService
import im.hikaru.ruoyi.framework.security.core.util.SecurityFrameworkUtils
import im.hikaru.ruoyi.framework.tenant.config.TenantProperties
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.framework.web.core.util.WebFrameworkUtils
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.servlet.HandlerInterceptor

/**
 * 多租户访问上下文拦截器 (迁移自 Java, 去 Lombok/Hutool)
 *
 * @author 芋道源码
 */
class TenantVisitContextInterceptor(
    private val tenantProperties: TenantProperties,
    private val securityFrameworkService: SecurityFrameworkService,
) : HandlerInterceptor {

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val visitTenantId = WebFrameworkUtils.getVisitTenantId(request) ?: return true
        if (visitTenantId == TenantContextHolder.getTenantId()) {
            return true
        }
        val loginUser = SecurityFrameworkUtils.getLoginUser() ?: return true
        if (!securityFrameworkService.hasAnyPermissions(PERMISSION)) {
            throw exception0(GlobalErrorCodeConstants.FORBIDDEN.code, "您无权切换租户")
        }
        loginUser.visitTenantId = visitTenantId
        TenantContextHolder.setTenantId(visitTenantId)
        return true
    }

    override fun afterCompletion(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
        ex: Exception?,
    ) {
        val loginUser = SecurityFrameworkUtils.getLoginUser()
        if (loginUser?.tenantId != null) {
            TenantContextHolder.setTenantId(loginUser.tenantId)
        }
    }

    companion object {
        private const val PERMISSION = "system:tenant:visit"
    }
}
