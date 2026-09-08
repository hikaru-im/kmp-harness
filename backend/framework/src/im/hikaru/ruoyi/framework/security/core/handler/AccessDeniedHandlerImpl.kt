package im.hikaru.ruoyi.framework.security.core.handler

import im.hikaru.ruoyi.framework.common.exception.enums.GlobalErrorCodeConstants
import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.util.servlet.ServletUtils
import im.hikaru.ruoyi.framework.security.core.util.SecurityFrameworkUtils
import jakarta.servlet.ServletException
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.web.access.AccessDeniedHandler
import java.io.IOException

/**
 * 访问一个需要认证的 URL 资源，已经认证（登录）但是没有权限的情况下，
 * 返回 [GlobalErrorCodeConstants.FORBIDDEN] 错误码 (迁移自 Java, 去 Lombok)
 *
 * @author 芋道源码
 */
@Suppress("JavadocReference")
class AccessDeniedHandlerImpl : AccessDeniedHandler {

    @Throws(IOException::class, ServletException::class)
    override fun handle(request: HttpServletRequest, response: HttpServletResponse, e: AccessDeniedException) {
        // 打印 warn 的原因是，不定期合并 warn，看看有没恶意破坏
        log.warn("[commence][访问 URL({}) 时，用户({}) 权限不够]", request.requestURI, SecurityFrameworkUtils.getLoginUserId(), e)
        // 返回 403
        ServletUtils.writeJSON(response, CommonResult.error<Any>(GlobalErrorCodeConstants.FORBIDDEN))
    }

    companion object {
        private val log = LoggerFactory.getLogger(AccessDeniedHandlerImpl::class.java)
    }
}
