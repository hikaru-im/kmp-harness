package im.hikaru.ruoyi.module.system.service.auth

import im.hikaru.ruoyi.framework.common.enums.UserTypeEnum
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.module.system.controller.admin.logger.vo.loginlog.LoginLogPageReqVO
import im.hikaru.ruoyi.module.system.controller.admin.oauth2.vo.client.OAuth2ClientSaveReqVO
import im.hikaru.ruoyi.module.system.dal.mysql.logger.LoginLogDao
import im.hikaru.ruoyi.module.system.dal.mysql.logger.LoginLogTable
import im.hikaru.ruoyi.module.system.dal.mysql.oauth2.OAuth2AccessTokenTable
import im.hikaru.ruoyi.module.system.dal.mysql.oauth2.OAuth2ApproveTable
import im.hikaru.ruoyi.module.system.dal.mysql.oauth2.OAuth2ClientTable
import im.hikaru.ruoyi.module.system.dal.mysql.oauth2.OAuth2CodeTable
import im.hikaru.ruoyi.module.system.dal.mysql.oauth2.OAuth2RefreshTokenTable
import im.hikaru.ruoyi.module.system.enums.logger.LoginLogTypeEnum
import im.hikaru.ruoyi.module.system.service.logger.LoginLogServiceImpl
import im.hikaru.ruoyi.module.system.service.member.MemberService
import im.hikaru.ruoyi.module.system.service.oauth2.OAuth2ClientServiceImpl
import im.hikaru.ruoyi.module.system.service.oauth2.OAuth2TokenServiceImpl
import im.hikaru.ruoyi.module.system.service.user.AdminUserService
import im.hikaru.ruoyi.module.system.api.social.SocialUserApi
import im.hikaru.ruoyi.module.system.api.sms.SmsCodeApi
import com.anji.captcha.service.CaptchaService
import jakarta.validation.Validation
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.support.StaticListableBeanFactory
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

class AdminAuthLogoutTest {
    @BeforeEach
    fun setUp() {
        Database.connect("jdbc:h2:mem:system_auth_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
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
    }

    @AfterEach
    fun tearDown() {
        TenantContextHolder.clear()
        RequestContextHolder.resetRequestAttributes()
    }

    @Test
    fun `logout revokes token and writes login log`() {
        val beanFactory = StaticListableBeanFactory()
        val userProvider = beanFactory.getBeanProvider(AdminUserService::class.java)
        val memberProvider = beanFactory.getBeanProvider(MemberService::class.java)
        val socialProvider = beanFactory.getBeanProvider(SocialUserApi::class.java)
        val captchaProvider = beanFactory.getBeanProvider(CaptchaService::class.java)
        val smsProvider = beanFactory.getBeanProvider(SmsCodeApi::class.java)
        val clientService = OAuth2ClientServiceImpl()
        clientService.createOAuth2Client(clientRequest())
        val tokenService = OAuth2TokenServiceImpl(clientService, userProvider)
        val token = tokenService.createAccessToken(99L, UserTypeEnum.ADMIN.value, "default", emptyList())
        val authService = AdminAuthServiceImpl(
            tokenService,
            LoginLogServiceImpl(),
            userProvider,
            memberProvider,
            socialProvider,
            captchaProvider,
            smsProvider,
            Validation.buildDefaultValidatorFactory().validator,
            false,
        )

        authService.logout(requireNotNull(token.accessToken), LoginLogTypeEnum.LOGOUT_DELETE.type)

        assertNull(tokenService.getAccessToken(requireNotNull(token.accessToken)))
        val logs = LoginLogDao.selectPage(LoginLogPageReqVO())
        assertEquals(1L, logs.total)
        assertEquals(LoginLogTypeEnum.LOGOUT_DELETE.type, logs.list.single().logType)
        assertEquals(99L, logs.list.single().userId)
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
