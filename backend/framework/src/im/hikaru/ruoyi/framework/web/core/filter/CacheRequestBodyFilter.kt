package im.hikaru.ruoyi.framework.web.core.filter

import im.hikaru.ruoyi.framework.common.util.servlet.ServletUtils
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.filter.OncePerRequestFilter

class CacheRequestBodyFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        filterChain.doFilter(CacheRequestBodyWrapper(request), response)
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        if (IGNORE_URIS.any(request.requestURI::startsWith)) return true
        return !ServletUtils.isJsonRequest(request)
    }

    companion object {
        private val IGNORE_URIS = arrayOf("/admin/", "/actuator/")
    }
}
