package im.hikaru.ruoyi.framework.apilog.core.interceptor

import im.hikaru.ruoyi.framework.common.util.servlet.ServletUtils
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.core.env.Environment
import org.springframework.util.StopWatch
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor

class ApiAccessLogInterceptor(
    private val environment: Environment,
) : HandlerInterceptor {
    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val handlerMethod = handler as? HandlerMethod
        handlerMethod?.let { request.setAttribute(ATTRIBUTE_HANDLER_METHOD, it) }

        if (!isProd()) {
            val params = ServletUtils.getParamMap(request)
            val requestBody = ServletUtils.getBody(request)
            if (params.isEmpty() && requestBody.isNullOrEmpty()) {
                log.info("[preHandle] request URL({}) without parameters", request.requestURI)
            } else {
                log.info("[preHandle] request URL({}) parameters({})", request.requestURI, requestBody ?: params)
            }
            request.setAttribute(ATTRIBUTE_STOP_WATCH, StopWatch().apply { start() })
            handlerMethod?.let { log.info("[preHandle] controller method {}", it.shortLogMessage) }
        }
        return true
    }

    override fun afterCompletion(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
        ex: Exception?,
    ) {
        val stopWatch = request.getAttribute(ATTRIBUTE_STOP_WATCH) as? StopWatch ?: return
        if (stopWatch.isRunning) stopWatch.stop()
        log.info("[afterCompletion] request URL({}) took {} ms", request.requestURI, stopWatch.totalTimeMillis)
    }

    private fun isProd(): Boolean = environment.activeProfiles.any { it.equals("prod", ignoreCase = true) }

    companion object {
        const val ATTRIBUTE_HANDLER_METHOD = "HANDLER_METHOD"
        private const val ATTRIBUTE_STOP_WATCH = "ApiAccessLogInterceptor.StopWatch"
        private val log = LoggerFactory.getLogger(ApiAccessLogInterceptor::class.java)
    }
}
