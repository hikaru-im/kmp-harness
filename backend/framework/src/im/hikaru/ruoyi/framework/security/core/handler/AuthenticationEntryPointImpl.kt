package im.hikaru.ruoyi.framework.security.core.handler

import im.hikaru.ruoyi.framework.common.exception.enums.GlobalErrorCodeConstants
import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.util.servlet.ServletUtils
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint

/**
 * 访问一个需要认证的 URL 资源，但是此时自己尚未认证（登录）的情况下，
 * 返回 [GlobalErrorCodeConstants.UNAUTHORIZED] 错误码，从而使前端重定向到登录页 (迁移自 Java, 去 Lombok)
 *
 * @author ruoyi
 */
@Suppress("JavadocReference")
class AuthenticationEntryPointImpl : AuthenticationEntryPoint {

    override fun commence(request: HttpServletRequest, response: HttpServletResponse, e: AuthenticationException) {
        log.debug("[commence][访问 URL({}) 时，没有登录]", request.requestURI, e)
        // 返回 401
        ServletUtils.writeJSON(response, CommonResult.error<Any>(GlobalErrorCodeConstants.UNAUTHORIZED))
    }

    companion object {
        private val log = LoggerFactory.getLogger(AuthenticationEntryPointImpl::class.java)
    }
}
