package im.hikaru.ruoyi.framework.web.core.filter

import im.hikaru.ruoyi.framework.common.exception.enums.GlobalErrorCodeConstants.DEMO_DENY
import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.util.servlet.ServletUtils
import im.hikaru.ruoyi.framework.web.core.util.WebFrameworkUtils
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.filter.OncePerRequestFilter

/** Rejects authenticated write operations while demo mode is enabled. */
class DemoFilter : OncePerRequestFilter() {

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        request.method.uppercase() !in WRITE_METHODS || WebFrameworkUtils.getLoginUserId(request) == null

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        ServletUtils.writeJSON(response, CommonResult.error<Any>(DEMO_DENY))
    }

    companion object {
        private val WRITE_METHODS = setOf("POST", "PUT", "DELETE")
    }
}
