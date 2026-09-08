package im.hikaru.ruoyi.framework.apilog.core.filter

import im.hikaru.ruoyi.framework.apilog.core.annotation.ApiAccessLog
import im.hikaru.ruoyi.framework.apilog.core.enums.OperateTypeEnum
import im.hikaru.ruoyi.framework.apilog.core.interceptor.ApiAccessLogInterceptor
import im.hikaru.ruoyi.framework.common.biz.infra.logger.ApiAccessLogCommonApi
import im.hikaru.ruoyi.framework.common.biz.infra.logger.dto.ApiAccessLogCreateReqDTO
import im.hikaru.ruoyi.framework.common.exception.enums.GlobalErrorCodeConstants
import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.util.json.JsonUtils
import im.hikaru.ruoyi.framework.common.util.monitor.TracerUtils
import im.hikaru.ruoyi.framework.common.util.servlet.ServletUtils
import im.hikaru.ruoyi.framework.web.config.WebProperties
import im.hikaru.ruoyi.framework.web.core.filter.ApiRequestFilter
import im.hikaru.ruoyi.framework.web.core.util.WebFrameworkUtils
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.web.method.HandlerMethod
import tools.jackson.databind.JsonNode
import java.time.Duration
import java.time.LocalDateTime

class ApiAccessLogFilter(
    webProperties: WebProperties,
    private val applicationName: String,
    private val apiAccessLogApi: ApiAccessLogCommonApi,
) : ApiRequestFilter(webProperties) {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val beginTime = LocalDateTime.now()
        val queryString = ServletUtils.getParamMap(request)
        val requestBody = ServletUtils.getBody(request)
        try {
            filterChain.doFilter(request, response)
            createApiAccessLog(request, beginTime, queryString, requestBody, null)
        } catch (ex: Exception) {
            createApiAccessLog(request, beginTime, queryString, requestBody, ex)
            throw ex
        }
    }

    private fun createApiAccessLog(
        request: HttpServletRequest,
        beginTime: LocalDateTime,
        queryString: Map<String, String>,
        requestBody: String?,
        exception: Exception?,
    ) {
        val accessLog = ApiAccessLogCreateReqDTO()
        try {
            if (!buildApiAccessLog(accessLog, request, beginTime, queryString, requestBody, exception)) return
            apiAccessLogApi.createApiAccessLogAsync(accessLog)
        } catch (throwable: Throwable) {
            log.error(
                "[createApiAccessLog] failed for URL({}) log({})",
                request.requestURI,
                runCatching { JsonUtils.toJsonString(accessLog) }.getOrDefault("<unavailable>"),
                throwable,
            )
        }
    }

    private fun buildApiAccessLog(
        accessLog: ApiAccessLogCreateReqDTO,
        request: HttpServletRequest,
        beginTime: LocalDateTime,
        queryString: Map<String, String>,
        requestBody: String?,
        exception: Exception?,
    ): Boolean {
        val handlerMethod = request.getAttribute(ApiAccessLogInterceptor.ATTRIBUTE_HANDLER_METHOD) as? HandlerMethod
        val annotation = handlerMethod?.getMethodAnnotation(ApiAccessLog::class.java)
        if (annotation?.enable == false) return false

        accessLog.userId = WebFrameworkUtils.getLoginUserId(request)
        accessLog.userType = WebFrameworkUtils.getLoginUserType(request)
        val result = WebFrameworkUtils.getCommonResult(request)
        when {
            result != null -> {
                accessLog.resultCode = result.code ?: GlobalErrorCodeConstants.SUCCESS.code
                accessLog.resultMsg = result.msg
            }

            exception != null -> {
                accessLog.resultCode = GlobalErrorCodeConstants.INTERNAL_SERVER_ERROR.code
                accessLog.resultMsg = rootCauseMessage(exception)
            }

            else -> {
                accessLog.resultCode = GlobalErrorCodeConstants.SUCCESS.code
                accessLog.resultMsg = ""
            }
        }

        accessLog.traceId = TracerUtils.getTraceId()
        accessLog.applicationName = applicationName
        accessLog.requestUrl = request.requestURI
        accessLog.requestMethod = request.method
        accessLog.userAgent = ServletUtils.getUserAgent(request)
        accessLog.userIp = ServletUtils.getClientIP(request)

        val sanitizeKeys = annotation?.sanitizeKeys.orEmpty().toSet()
        if (annotation?.requestEnable != false) {
            accessLog.requestParams = JsonUtils.toJsonString(
                linkedMapOf(
                    "query" to sanitizeMap(queryString, sanitizeKeys),
                    "body" to sanitizeJson(requestBody, sanitizeKeys),
                ),
            )
        }
        if (annotation?.responseEnable == true) {
            accessLog.responseBody = sanitizeResult(result, sanitizeKeys)
        }

        val endTime = LocalDateTime.now()
        accessLog.beginTime = beginTime
        accessLog.endTime = endTime
        accessLog.duration = Duration.between(beginTime, endTime).toMillis()
            .coerceIn(0, Int.MAX_VALUE.toLong())
            .toInt()

        handlerMethod?.let {
            val tag = it.beanType.getAnnotation(Tag::class.java)
            val operation = it.getMethodAnnotation(Operation::class.java)
            accessLog.operateModule = annotation?.operateModule?.takeIf(String::isNotBlank)
                ?: tag?.name?.takeIf(String::isNotBlank)
                ?: tag?.description?.takeIf(String::isNotBlank)
            accessLog.operateName = annotation?.operateName?.takeIf(String::isNotBlank)
                ?: operation?.summary?.takeIf(String::isNotBlank)
            accessLog.operateType = annotation?.operateType?.firstOrNull()?.type
                ?: parseOperateType(request.method).type
        }
        return true
    }

    private fun sanitizeMap(map: Map<String, *>, customKeys: Set<String>): String? {
        if (map.isEmpty()) return null
        val sanitizeKeys = DEFAULT_SANITIZE_KEYS + customKeys
        return JsonUtils.toJsonString(map.filterKeys { it !in sanitizeKeys })
    }

    private fun sanitizeJson(json: String?, customKeys: Set<String>): String? {
        if (json.isNullOrEmpty()) return null
        return try {
            val root = JsonUtils.parseTree(json)
            sanitizeNode(root, DEFAULT_SANITIZE_KEYS + customKeys)
            JsonUtils.toJsonString(root)
        } catch (exception: Exception) {
            log.error("[sanitizeJson] failed to sanitize request JSON", exception)
            json
        }
    }

    private fun sanitizeResult(result: CommonResult<*>?, customKeys: Set<String>): String? {
        if (result == null) return null
        val json = JsonUtils.toJsonString(result)
        return try {
            val root = JsonUtils.parseTree(json)
            sanitizeNode(root.get("data"), DEFAULT_SANITIZE_KEYS + customKeys)
            JsonUtils.toJsonString(root)
        } catch (exception: Exception) {
            log.error("[sanitizeResult] failed to sanitize response JSON", exception)
            json
        }
    }

    private fun sanitizeNode(node: JsonNode?, sanitizeKeys: Set<String>) {
        if (node == null) return
        if (node.isArray) {
            node.forEach { sanitizeNode(it, sanitizeKeys) }
            return
        }
        if (!node.isObject) return
        val iterator = node.properties().iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key in sanitizeKeys) {
                iterator.remove()
            } else {
                sanitizeNode(entry.value, sanitizeKeys)
            }
        }
    }

    private fun parseOperateType(method: String): OperateTypeEnum = when (method.uppercase()) {
        "GET" -> OperateTypeEnum.GET
        "POST" -> OperateTypeEnum.CREATE
        "PUT" -> OperateTypeEnum.UPDATE
        "DELETE" -> OperateTypeEnum.DELETE
        else -> OperateTypeEnum.OTHER
    }

    private fun rootCauseMessage(exception: Throwable): String {
        val root = generateSequence(exception) { it.cause }.last()
        return root.message ?: root.javaClass.simpleName
    }

    companion object {
        private val DEFAULT_SANITIZE_KEYS = setOf("password", "token", "accessToken", "refreshToken")
        private val log = LoggerFactory.getLogger(ApiAccessLogFilter::class.java)
    }
}
