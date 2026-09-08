package im.hikaru.ruoyi.framework.security.core.util

import im.hikaru.ruoyi.framework.security.core.LoginUser
import im.hikaru.ruoyi.framework.web.core.util.WebFrameworkUtils
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.util.StringUtils

/**
 * 安全服务工具类 (迁移自 Java, 去 Lombok/Hutool)
 *
 * 迁移说明：Hutool StrUtil.isEmpty/MapUtil.getStr/getLong/ObjUtil.notEqual → Kotlin stdlib
 *
 * @author 芋道源码
 */
object SecurityFrameworkUtils {

    /** HEADER 认证头 value 的前缀 */
    const val AUTHORIZATION_BEARER = "Bearer"

    /**
     * 从请求中，获得认证 Token
     *
     * @param request 请求
     * @param headerName 认证 Token 对应的 Header 名字
     * @param parameterName 认证 Token 对应的 Parameter 名字
     * @return 认证 Token
     */
    @JvmStatic
    fun obtainAuthorization(
        request: HttpServletRequest,
        headerName: String,
        parameterName: String,
    ): String? {
        // 1. 获得 Token。优先级：Header > Parameter
        var token: String? = request.getHeader(headerName)
        if (token.isNullOrEmpty()) {
            token = request.getParameter(parameterName)
        }
        if (!StringUtils.hasText(token)) {
            return null
        }
        // 2. 去除 Token 中带的 Bearer
        val index = token.indexOf("$AUTHORIZATION_BEARER ")
        return if (index >= 0) token!!.substring(index + 7).trim() else token
    }

    /**
     * 获得当前认证信息
     */
    @JvmStatic
    fun getAuthentication(): Authentication? {
        val context: SecurityContext = SecurityContextHolder.getContext() ?: return null
        return context.authentication
    }

    /**
     * 获取当前用户
     */
    @JvmStatic
    fun getLoginUser(): LoginUser? {
        val authentication = getAuthentication() ?: return null
        return (authentication.principal as? LoginUser)
    }

    /**
     * 获得当前用户的编号，从上下文中
     */
    @JvmStatic
    fun getLoginUserId(): Long? = getLoginUser()?.id

    /**
     * 获得当前用户的昵称，从上下文中
     */
    @JvmStatic
    fun getLoginUserNickname(): String? =
        getLoginUser()?.info?.get(LoginUser.INFO_KEY_NICKNAME)

    /**
     * 获得当前用户的部门编号，从上下文中
     */
    @JvmStatic
    fun getLoginUserDeptId(): Long? =
        getLoginUser()?.info?.get(LoginUser.INFO_KEY_DEPT_ID)?.toLongOrNull()

    /**
     * 设置当前用户
     */
    @JvmStatic
    fun setLoginUser(loginUser: LoginUser, request: HttpServletRequest?) {
        // 创建 Authentication，并设置到上下文
        val authentication = buildAuthentication(loginUser, request)
        SecurityContextHolder.getContext().authentication = authentication

        // 额外设置到 request 中，用于 ApiAccessLogFilter 可以获取到用户编号；
        // 原因是，Spring Security 的 Filter 在 ApiAccessLogFilter 后面，在它记录访问日志时，线上上下文已经没有用户编号等信息
        if (request != null) {
            WebFrameworkUtils.setLoginUserId(request!!, loginUser.id)
            WebFrameworkUtils.setLoginUserType(request, loginUser.userType)
        }
    }

    private fun buildAuthentication(loginUser: LoginUser, request: HttpServletRequest?): Authentication {
        // 创建 UsernamePasswordAuthenticationToken 对象
        val authenticationToken = UsernamePasswordAuthenticationToken(
            loginUser, null, emptyList(),
        )
        authenticationToken.details = WebAuthenticationDetailsSource().buildDetails(request!!)
        return authenticationToken
    }

    /**
     * 是否条件跳过权限校验，包括数据权限、功能权限
     */
    @JvmStatic
    fun skipPermissionCheck(): Boolean {
        val loginUser = getLoginUser() ?: return false
        if (loginUser.visitTenantId == null) return false
        // 重点：跨租户访问时，无法进行权限校验
        return loginUser.visitTenantId != loginUser.tenantId
    }
}
