package im.hikaru.ruoyi.framework.web.core.filter

import im.hikaru.ruoyi.framework.web.config.WebProperties
import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.filter.OncePerRequestFilter

abstract class ApiRequestFilter(
    protected val webProperties: WebProperties,
) : OncePerRequestFilter() {
    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val apiUri = request.requestURI.removePrefix(request.contextPath)
        return listOf(webProperties.adminApi.prefix, webProperties.appApi.prefix)
            .filter(String::isNotEmpty)
            .none(apiUri::startsWith)
    }
}
