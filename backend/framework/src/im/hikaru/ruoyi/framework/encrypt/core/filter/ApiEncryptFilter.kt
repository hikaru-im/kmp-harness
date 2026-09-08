package im.hikaru.ruoyi.framework.encrypt.core.filter

import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.invalidParamException
import im.hikaru.ruoyi.framework.common.util.servlet.ServletUtils
import im.hikaru.ruoyi.framework.encrypt.config.ApiEncryptProperties
import im.hikaru.ruoyi.framework.encrypt.core.annotation.ApiEncrypt
import im.hikaru.ruoyi.framework.encrypt.core.crypto.ApiCryptoFactory
import im.hikaru.ruoyi.framework.web.config.WebProperties
import im.hikaru.ruoyi.framework.web.core.filter.ApiRequestFilter
import im.hikaru.ruoyi.framework.web.core.handler.GlobalExceptionHandler
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping
import org.springframework.web.util.ServletRequestPathUtils

class ApiEncryptFilter(
    webProperties: WebProperties,
    private val properties: ApiEncryptProperties,
    private val requestMappingHandlerMapping: RequestMappingHandlerMapping,
    private val globalExceptionHandler: GlobalExceptionHandler,
) : ApiRequestFilter(webProperties) {
    private val requestDecryptor = ApiCryptoFactory.createDecryptor(properties.algorithm, properties.requestKey)
    private val responseEncryptor = ApiCryptoFactory.createEncryptor(properties.algorithm, properties.responseKey)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val annotation = getApiEncrypt(request)
        val requestEnable = annotation?.request == true
        val responseEnable = annotation?.response == true
        val encryptHeader = request.getHeader(properties.header)
        if (!requestEnable && !responseEnable && encryptHeader.isNullOrBlank()) {
            filterChain.doFilter(request, response)
            return
        }

        var processedRequest = request
        if (request.method.uppercase() in BODY_METHODS) {
            try {
                processedRequest = when {
                    !encryptHeader.isNullOrBlank() -> ApiDecryptRequestWrapper(request, requestDecryptor)
                    requestEnable -> throw invalidParamException("请求未包含加密标头，请检查是否正确配置了加密标头")
                    else -> request
                }
            } catch (ex: Exception) {
                ServletUtils.writeJSON(response, globalExceptionHandler.allExceptionHandler(request, ex))
                return
            }
        }

        val responseWrapper = if (responseEnable) ApiEncryptResponseWrapper(response) else null
        filterChain.doFilter(processedRequest, responseWrapper ?: response)
        responseWrapper?.encrypt(properties, responseEncryptor)
    }

    private fun getApiEncrypt(request: HttpServletRequest): ApiEncrypt? = try {
        if (!ServletRequestPathUtils.hasParsedRequestPath(request)) {
            ServletRequestPathUtils.parseAndCache(request)
        }
        val handler = requestMappingHandlerMapping.getHandler(request)?.handler as? HandlerMethod ?: return null
        AnnotatedElementUtils.findMergedAnnotation(handler.method, ApiEncrypt::class.java)
            ?: AnnotatedElementUtils.findMergedAnnotation(handler.beanType, ApiEncrypt::class.java)
    } catch (ex: Exception) {
        log.error(
            "[getApiEncrypt][url({}/{}) 获取 @ApiEncrypt 注解失败]",
            request.requestURI,
            request.method,
            ex,
        )
        null
    }

    companion object {
        private val log = LoggerFactory.getLogger(ApiEncryptFilter::class.java)
        private val BODY_METHODS = setOf("POST", "PUT", "DELETE")
    }
}
