package im.hikaru.ruoyi.framework.tenant.core.security

import im.hikaru.ruoyi.framework.common.exception.enums.GlobalErrorCodeConstants
import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.util.servlet.ServletUtils
import im.hikaru.ruoyi.framework.security.core.util.SecurityFrameworkUtils
import im.hikaru.ruoyi.framework.tenant.config.TenantProperties
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.framework.tenant.core.service.TenantFrameworkService
import im.hikaru.ruoyi.framework.web.config.WebProperties
import im.hikaru.ruoyi.framework.web.core.filter.ApiRequestFilter
import im.hikaru.ruoyi.framework.web.core.handler.GlobalExceptionHandler
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.util.AntPathMatcher
import java.io.IOException

/**
 * 多租户 Security Web 过滤器 (迁移自 Java, 去 Lombok/Hutool)
 *
 * @author 芋道源码
 */
class TenantSecurityWebFilter(
    webProperties: WebProperties,
    private val tenantProperties: TenantProperties,
    private val ignoreUrls: Set<String>,
    private val globalExceptionHandler: GlobalExceptionHandler,
    private val tenantFrameworkService: TenantFrameworkService,
) : ApiRequestFilter(webProperties) {

    private val pathMatcher = AntPathMatcher()

    @Throws(ServletException::class, IOException::class)
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        var tenantId = TenantContextHolder.getTenantId()
        // 1. 登陆的用户，校验是否有权限访问该租户，避免越权
        val user = SecurityFrameworkUtils.getLoginUser()
        if (user != null) {
            if (tenantId == null) {
                tenantId = user.tenantId
                TenantContextHolder.setTenantId(tenantId)
            } else if (user.tenantId != TenantContextHolder.getTenantId()) {
                log.error(
                    "[doFilterInternal][租户({}) User({}/{}) 越权访问租户({}) URL({}/{})]",
                    user.tenantId, user.id, user.userType,
                    TenantContextHolder.getTenantId(), request.requestURI, request.method,
                )
                ServletUtils.writeJSON(
                    response,
                    CommonResult.error<Any>(GlobalErrorCodeConstants.FORBIDDEN.code, "您无权访问该租户的数据"),
                )
                return
            }
        }

        if (!isIgnoreUrl(request)) {
            // 2. 未带租户编号，不允许访问
            if (tenantId == null) {
                log.error("[doFilterInternal][URL({}/{}) 未传递租户编号]", request.requestURI, request.method)
                ServletUtils.writeJSON(
                    response,
                    CommonResult.error<Any>(GlobalErrorCodeConstants.BAD_REQUEST.code, "请求的租户标识未传递，请进行排查"),
                )
                return
            }
            // 3. 校验租户是否合法
            try {
                tenantFrameworkService.validTenant(tenantId)
            } catch (ex: Throwable) {
                val result = globalExceptionHandler.allExceptionHandler(request, ex)
                ServletUtils.writeJSON(response, result)
                return
            }
        } else {
            if (tenantId == null) {
                TenantContextHolder.setIgnore(true)
            }
        }
        chain.doFilter(request, response)
    }

    private fun isIgnoreUrl(request: HttpServletRequest): Boolean {
        val apiUri = request.requestURI.substring(request.contextPath.length)
        if (apiUri in tenantProperties.ignoreUrls || apiUri in ignoreUrls) {
            return true
        }
        for (url in tenantProperties.ignoreUrls) if (pathMatcher.match(url, apiUri)) return true
        for (url in ignoreUrls) if (pathMatcher.match(url, apiUri)) return true
        return false
    }

    companion object {
        private val log = LoggerFactory.getLogger(TenantSecurityWebFilter::class.java)
    }
}
