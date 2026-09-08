package im.hikaru.ruoyi.framework.web.core.handler

import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.web.core.util.WebFrameworkUtils
import org.springframework.core.MethodParameter
import org.springframework.http.MediaType
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.http.server.ServletServerHttpRequest
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice

/**
 * 全局响应结果（ResponseBody）处理器 (迁移自 Java, 去 Lombok)
 *
 * Controller 在返回时主动包上 [CommonResult]。本类主要作用是记录返回结果，
 * 方便 ApiAccessLogFilter 记录访问日志。
 *
 * @author 芋道源码
 */
@ControllerAdvice
class GlobalResponseBodyHandler : ResponseBodyAdvice<Any> {

    override fun supports(returnType: MethodParameter, converterType: Class<out org.springframework.http.converter.HttpMessageConverter<*>>): Boolean {
        val method = returnType.method ?: return false
        // 只拦截返回结果为 CommonResult 类型
        return method.returnType == CommonResult::class.java
    }

    override fun beforeBodyWrite(
        body: Any?,
        returnType: MethodParameter,
        selectedContentType: MediaType,
        selectedConverterType: Class<out org.springframework.http.converter.HttpMessageConverter<*>>,
        request: ServerHttpRequest,
        response: ServerHttpResponse,
    ): Any? {
        // 记录 Controller 结果
        val servletRequest = (request as ServletServerHttpRequest).servletRequest
        if (body is CommonResult<*>) {
            WebFrameworkUtils.setCommonResult(servletRequest, body)
        }
        return body
    }
}
