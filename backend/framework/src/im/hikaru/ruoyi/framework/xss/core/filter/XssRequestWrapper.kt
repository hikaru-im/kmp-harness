package im.hikaru.ruoyi.framework.xss.core.filter

import im.hikaru.ruoyi.framework.xss.core.clean.XssCleaner
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper

class XssRequestWrapper(
    request: HttpServletRequest,
    private val xssCleaner: XssCleaner,
) : HttpServletRequestWrapper(request) {

    override fun getParameterMap(): Map<String, Array<String>> =
        super.getParameterMap().mapValues { (_, values) -> clean(values) }

    override fun getParameterValues(name: String): Array<String>? =
        super.getParameterValues(name)?.let(::clean)

    override fun getParameter(name: String): String? =
        super.getParameter(name)?.let(xssCleaner::clean)

    override fun getAttribute(name: String): Any? =
        when (val value = super.getAttribute(name)) {
            is String -> xssCleaner.clean(value)
            else -> value
        }

    override fun getHeader(name: String): String? =
        super.getHeader(name)?.let(xssCleaner::clean)

    override fun getQueryString(): String? =
        super.getQueryString()?.let(xssCleaner::clean)

    private fun clean(values: Array<String>): Array<String> =
        Array(values.size) { index -> xssCleaner.clean(values[index]) }
}
