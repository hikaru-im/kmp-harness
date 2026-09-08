package im.hikaru.ruoyi.framework.tracer.core.filter

import im.hikaru.ruoyi.framework.common.util.monitor.TracerUtils
import jakarta.servlet.FilterChain
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.web.filter.OncePerRequestFilter
import java.io.IOException

/**
 * Trace 过滤器，打印 traceId 到 header 中返回 (迁移自 Java)
 *
 * @author 芋道源码
 */
class TraceFilter : OncePerRequestFilter() {

    @Throws(IOException::class, ServletException::class)
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        // 设置响应 traceId
        response.addHeader(HEADER_NAME_TRACE_ID, TracerUtils.getTraceId())
        // 继续过滤
        chain.doFilter(request, response)
    }

    companion object {
        /** Header 名 - 链路追踪编号 */
        private const val HEADER_NAME_TRACE_ID = "trace-id"
    }
}
