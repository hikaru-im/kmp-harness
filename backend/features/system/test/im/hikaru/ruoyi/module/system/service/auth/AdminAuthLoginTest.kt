package im.hikaru.ruoyi.module.system.service.auth

import im.hikaru.ruoyi.framework.common.exception.ServiceException
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.module.system.api.social.SocialUserApi
import im.hikaru.ruoyi.module.system.api.social.dto.SocialUserBindReqDTO
import im.hikaru.ruoyi.module.system.api.social.dto.SocialUserRespDTO
import im.hikaru.ruoyi.module.system.api.social.dto.SocialUserUnbindReqDTO
import im.hikaru.ruoyi.module.system.api.sms.SmsCodeApi
import im.hikaru.ruoyi.module.system.controller.admin.auth.vo.AuthLoginReqVO
import im.hikaru.ruoyi.module.system.controller.admin.auth.vo.AuthSocialLoginReqVO
import im.hikaru.ruoyi.module.system.controller.admin.logger.vo.loginlog.LoginLogPageReqVO
import im.hikaru.ruoyi.module.system.controller.admin.oauth2.vo.client.OAuth2ClientSaveReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.user.AdminUserDO
import im.hikaru.ruoyi.module.system.dal.mysql.logger.LoginLogDao
import im.hikaru.ruoyi.module.system.dal.mysql.logger.LoginLogTable
import im.hikaru.ruoyi.module.system.dal.mysql.oauth2.OAuth2AccessTokenTable
import im.hikaru.ruoyi.module.system.dal.mysql.oauth2.OAuth2ApproveTable
import im.hikaru.ruoyi.module.system.dal.mysql.oauth2.OAuth2ClientTable
import im.hikaru.ruoyi.module.system.dal.mysql.oauth2.OAuth2CodeTable
import im.hikaru.ruoyi.module.system.dal.mysql.oauth2.OAuth2RefreshTokenTable
import im.hikaru.ruoyi.module.system.enums.logger.LoginResultEnum
import im.hikaru.ruoyi.module.system.service.logger.LoginLogServiceImpl
import im.hikaru.ruoyi.module.system.service.member.MemberService
import im.hikaru.ruoyi.module.system.service.oauth2.OAuth2ClientServiceImpl
import im.hikaru.ruoyi.module.system.service.oauth2.OAuth2TokenServiceImpl
import im.hikaru.ruoyi.module.system.service.user.AdminUserService
import com.anji.captcha.service.CaptchaService
import jakarta.validation.Validation
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.support.StaticListableBeanFactory
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.lang.reflect.Proxy

class AdminAuthLoginTest {
    private lateinit var authService: AdminAuthServiceImpl

    @BeforeEach
    fun setUp() {
        Database.connect("jdbc:h2:mem:system_auth_login_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
        transaction {
            SchemaUtils.create(
                OAuth2ClientTable,
                OAuth2AccessTokenTable,
                OAuth2RefreshTokenTable,
                OAuth2CodeTable,
                OAuth2ApproveTable,
                LoginLogTable,
            )
        }
        TenantContextHolder.setTenantId(1L)
        RequestContextHolder.setRequestAttributes(ServletRequestAttributes(MockHttpServletRequest().apply {
            remoteAddr = "127.0.0.1"
            addHeader("User-Agent", "JUnit")
        }))

        val user = AdminUserDO().apply {
            id = 7L
            username = "admin"
            password = "encoded"
            nickname = "Administrator"
            status = 0
            tenantId = 1L
        }
        val adminUserService = Proxy.newProxyInstance(
            AdminUserService::class.java.classLoader,
            arrayOf(AdminUserService::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getUserByUsername" -> if (args?.get(0) == "admin") user else null
                "getUser" -> if (args?.get(0) == 7L) user else null
                "isPasswordMatch" -> args?.get(0) == "password" && args.get(1) == "encoded"
                "updateUserLogin" -> null
                "toString" -> "AdminUserServiceTestDouble"
                "hashCode" -> System.identityHashCode(this)
                "equals" -> false
                else -> null
            }
        } as AdminUserService
        val beanFactory = StaticListableBeanFactory()
        beanFactory.addBean("adminUserService", adminUserService)
        beanFactory.addBean("socialUserApi", object : SocialUserApi {
            override fun bindSocialUser(reqDTO: SocialUserBindReqDTO) = "openid"
            override fun unbindSocialUser(reqDTO: SocialUserUnbindReqDTO) = Unit
            override fun getSocialUserByUserId(userType: Int, userId: Long, socialType: Int) = null
            override fun getSocialUserByCode(userType: Int, socialType: Int, code: String, state: String) =
                SocialUserRespDTO(userId = 7L)
        })
        val userProvider = beanFactory.getBeanProvider(AdminUserService::class.java)
        val clientService = OAuth2ClientServiceImpl()
        clientService.createOAuth2Client(clientRequest())
        authService = AdminAuthServiceImpl(
            OAuth2TokenServiceImpl(clientService, userProvider),
            LoginLogServiceImpl(),
            userProvider,
            beanFactory.getBeanProvider(MemberService::class.java),
            beanFactory.getBeanProvider(SocialUserApi::class.java),
            beanFactory.getBeanProvider(CaptchaService::class.java),
            beanFactory.getBeanProvider(SmsCodeApi::class.java),
            Validation.buildDefaultValidatorFactory().validator,
            false,
        )
    }

    @AfterEach
    fun tearDown() {
        TenantContextHolder.clear()
        RequestContextHolder.resetRequestAttributes()
    }

    @Test
    fun `login creates token and success log and refreshes token`() {
        val login = authService.login(AuthLoginReqVO().apply {
            username = "admin"
            password = "password"
        })
        assertEquals(7L, login.userId)
        assertFalse(login.accessToken.isNullOrBlank())
        assertFalse(login.refreshToken.isNullOrBlank())
        assertEquals(LoginResultEnum.SUCCESS.result, LoginLogDao.selectPage(LoginLogPageReqVO()).list.single().result)

        val refreshed = authService.refreshToken(requireNotNull(login.refreshToken))
        assertNotEquals(login.accessToken, refreshed.accessToken)
        assertEquals(login.refreshToken, refreshed.refreshToken)
    }

    @Test
    fun `bad password writes failure log`() {
        assertThrows(ServiceException::class.java) {
            authService.login(AuthLoginReqVO().apply {
                username = "admin"
                password = "wrong"
            })
        }
        assertEquals(LoginResultEnum.BAD_CREDENTIALS.result, LoginLogDao.selectPage(LoginLogPageReqVO()).list.single().result)
    }

    @Test
    fun `social login resolves bound user and creates token`() {
        val login = authService.socialLogin(AuthSocialLoginReqVO().apply {
            type = 10
            code = "code"
            state = "state"
        })
        assertEquals(7L, login.userId)
        assertFalse(login.accessToken.isNullOrBlank())
    }

    private fun clientRequest() = OAuth2ClientSaveReqVO().apply {
        clientId = "default"
        secret = "secret"
        name = "Default"
        logo = "https://example.com/logo.png"
        status = 0
        accessTokenValiditySeconds = 600
        refreshTokenValiditySeconds = 3600
        redirectUris = listOf("https://example.com")
        authorizedGrantTypes = listOf("password", "refresh_token")
        scopes = emptyList()
        autoApproveScopes = emptyList()
    }
}
