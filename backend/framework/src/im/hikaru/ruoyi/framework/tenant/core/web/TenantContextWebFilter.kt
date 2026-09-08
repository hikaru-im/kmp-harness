package im.hikaru.ruoyi.framework.tenant.core.web

import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.framework.web.core.util.WebFrameworkUtils
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.filter.OncePerRequestFilter

/**
 * 多租户 Context Web 过滤器 (迁移自 Java, 去 Lombok)
 *
 * @author 芋道源码
 */
class TenantContextWebFilter : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        val tenantId = WebFrameworkUtils.getTenantId(request)
        if (tenantId != null) {
            TenantContextHolder.setTenantId(tenantId)
        }
        try {
            chain.doFilter(request, response)
        } finally {
            TenantContextHolder.clear()
        }
    }
}
