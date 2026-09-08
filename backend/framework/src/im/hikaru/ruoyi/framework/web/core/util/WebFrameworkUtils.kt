package im.hikaru.ruoyi.framework.web.core.util

import im.hikaru.ruoyi.framework.common.enums.TerminalEnum
import im.hikaru.ruoyi.framework.common.enums.UserTypeEnum
import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.web.config.WebProperties
import jakarta.servlet.ServletRequest
import jakarta.servlet.http.HttpServletRequest
import org.springframework.web.context.request.RequestAttributes
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

/**
 * 专属于 web 包的工具类 (迁移自 Java, 去 Hutool NumberUtil)
 *
 * 迁移说明：Hutool NumberUtil.isNumber/parseInt → Kotlin 字符串/数字解析
 *
 * @author 芋道源码
 */
class WebFrameworkUtils(webProperties: WebProperties) {

    init {
        properties = webProperties
    }

    companion object {
        private const val REQUEST_ATTRIBUTE_LOGIN_USER_ID = "login_user_id"
        private const val REQUEST_ATTRIBUTE_LOGIN_USER_TYPE = "login_user_type"
        private const val REQUEST_ATTRIBUTE_COMMON_RESULT = "common_result"

        const val HEADER_TENANT_ID = "tenant-id"
        const val HEADER_VISIT_TENANT_ID = "visit-tenant-id"

        /** 终端的 Header ([im.hikaru.ruoyi.framework.common.enums.TerminalEnum]) */
        const val HEADER_TERMINAL = "terminal"

        private lateinit var properties: WebProperties

        /**
         * 获得租户编号，从 header 中
         */
        @JvmStatic
        fun getTenantId(request: HttpServletRequest): Long? = request.getHeader(HEADER_TENANT_ID)?.toLongOrNull()

        /**
         * 获得访问的租户编号，从 header 中
         */
        @JvmStatic
        fun getVisitTenantId(request: HttpServletRequest): Long? = request.getHeader(HEADER_VISIT_TENANT_ID)?.toLongOrNull()

        @JvmStatic
        fun setLoginUserId(request: ServletRequest, userId: Long?) {
            request.setAttribute(REQUEST_ATTRIBUTE_LOGIN_USER_ID, userId)
        }

        @JvmStatic
        fun setLoginUserType(request: ServletRequest, userType: Int?) {
            request.setAttribute(REQUEST_ATTRIBUTE_LOGIN_USER_TYPE, userType)
        }

        /**
         * 获得当前用户的编号，从请求中
         * 注意：该方法仅限于 framework 框架使用！！！
         */
        @JvmStatic
        fun getLoginUserId(request: HttpServletRequest?): Long? {
            if (request == null) return null
            return request.getAttribute(REQUEST_ATTRIBUTE_LOGIN_USER_ID) as? Long
        }

        /**
         * 获得当前用户的类型
         */
        @JvmStatic
        fun getLoginUserType(request: HttpServletRequest?): Int? {
            if (request == null) return null
            // 1. 优先，从 Attribute 中获取
            val userType = request.getAttribute(REQUEST_ATTRIBUTE_LOGIN_USER_TYPE) as? Int
            if (userType != null) return userType
            // 2. 其次，基于 URL 前缀的约定
            val path = request.servletPath
            if (path.startsWith(properties.adminApi.prefix)) {
                return UserTypeEnum.ADMIN.value
            }
            if (path.startsWith(properties.appApi.prefix)) {
                return UserTypeEnum.MEMBER.value
            }
            return null
        }

        @JvmStatic
        fun getLoginUserType(): Int? = getLoginUserType(getRequest())

        @JvmStatic
        fun getLoginUserId(): Long? = getLoginUserId(getRequest())

        @JvmStatic
        fun getTerminal(): Int {
            val request = getRequest() ?: return TerminalEnum.UNKNOWN.terminal
            return request.getHeader(HEADER_TERMINAL)?.toIntOrNull() ?: TerminalEnum.UNKNOWN.terminal
        }

        @JvmStatic
        fun setCommonResult(request: ServletRequest, result: CommonResult<*>) {
            request.setAttribute(REQUEST_ATTRIBUTE_COMMON_RESULT, result)
        }

        @JvmStatic
        @Suppress("UNCHECKED_CAST")
        fun getCommonResult(request: ServletRequest): CommonResult<*>? =
            request.getAttribute(REQUEST_ATTRIBUTE_COMMON_RESULT) as? CommonResult<*>

        @JvmStatic
        fun getRequest(): HttpServletRequest? {
            val requestAttributes: RequestAttributes? = RequestContextHolder.getRequestAttributes()
            if (requestAttributes !is ServletRequestAttributes) return null
            return requestAttributes.request
        }
    }
}
