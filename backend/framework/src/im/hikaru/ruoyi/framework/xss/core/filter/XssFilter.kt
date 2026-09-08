package im.hikaru.ruoyi.framework.xss.core.filter

import im.hikaru.ruoyi.framework.xss.config.XssProperties
import im.hikaru.ruoyi.framework.xss.core.clean.XssCleaner
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.util.PathMatcher
import org.springframework.web.filter.OncePerRequestFilter

class XssFilter(
    private val properties: XssProperties,
    private val pathMatcher: PathMatcher,
    private val xssCleaner: XssCleaner,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        filterChain.doFilter(XssRequestWrapper(request, xssCleaner), response)
    }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        !properties.enable || properties.excludeUrls.any { pathMatcher.match(it, request.requestURI) }
}
