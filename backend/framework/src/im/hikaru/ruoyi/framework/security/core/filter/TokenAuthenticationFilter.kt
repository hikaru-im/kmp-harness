package im.hikaru.ruoyi.framework.security.core.filter

import im.hikaru.ruoyi.framework.common.biz.system.oauth2.OAuth2TokenCommonApi
import im.hikaru.ruoyi.framework.common.exception.ServiceException
import im.hikaru.ruoyi.framework.common.util.servlet.ServletUtils
import im.hikaru.ruoyi.framework.security.config.SecurityProperties
import im.hikaru.ruoyi.framework.security.core.LoginUser
import im.hikaru.ruoyi.framework.security.core.util.SecurityFrameworkUtils
import im.hikaru.ruoyi.framework.web.core.handler.GlobalExceptionHandler
import im.hikaru.ruoyi.framework.web.core.util.WebFrameworkUtils
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.filter.OncePerRequestFilter

class TokenAuthenticationFilter(
    private val securityProperties: SecurityProperties,
    private val globalExceptionHandler: GlobalExceptionHandler,
    private val oauth2TokenApi: OAuth2TokenCommonApi,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val token = SecurityFrameworkUtils.obtainAuthorization(
            request,
            securityProperties.tokenHeader,
            securityProperties.tokenParameter,
        )
        if (!token.isNullOrEmpty()) {
            val userType = WebFrameworkUtils.getLoginUserType(request)
            try {
                val loginUser = buildLoginUserByToken(token, userType) ?: mockLoginUser(request, token, userType)
                if (loginUser != null) SecurityFrameworkUtils.setLoginUser(loginUser, request)
            } catch (ex: Throwable) {
                ServletUtils.writeJSON(response, globalExceptionHandler.allExceptionHandler(request, ex))
                return
            }
        }
        filterChain.doFilter(request, response)
    }

    private fun buildLoginUserByToken(token: String, requestUserType: Int?): LoginUser? = try {
        val accessToken = oauth2TokenApi.checkAccessToken(token)
        if (requestUserType != null && accessToken.userType != requestUserType) {
            throw AccessDeniedException("Incorrect user type")
        }
        LoginUser().apply {
            id = accessToken.userId
            userType = accessToken.userType
            info = accessToken.userInfo
            tenantId = accessToken.tenantId
            scopes = accessToken.scopes
            expiresTime = accessToken.expiresTime
        }
    } catch (_: ServiceException) {
        null
    }

    private fun mockLoginUser(request: HttpServletRequest, token: String, userType: Int?): LoginUser? {
        if (!securityProperties.mockEnable || !token.startsWith(securityProperties.mockSecret)) return null
        val userId = token.removePrefix(securityProperties.mockSecret).toLong()
        return LoginUser().apply {
            id = userId
            this.userType = userType
            tenantId = WebFrameworkUtils.getTenantId(request)
        }
    }
}
