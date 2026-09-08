package im.hikaru.ruoyi.module.system.service.auth

import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.module.infra.api.config.ConfigApi
import im.hikaru.ruoyi.module.system.api.sms.SmsCodeApi
import im.hikaru.ruoyi.module.system.api.sms.dto.code.SmsCodeSendReqDTO
import im.hikaru.ruoyi.module.system.api.sms.dto.code.SmsCodeUseReqDTO
import im.hikaru.ruoyi.module.system.api.sms.dto.code.SmsCodeValidateReqDTO
import im.hikaru.ruoyi.module.system.api.social.SocialUserApi
import im.hikaru.ruoyi.module.system.controller.admin.auth.vo.AuthRegisterReqVO
import im.hikaru.ruoyi.module.system.controller.admin.auth.vo.AuthResetPasswordReqVO
import im.hikaru.ruoyi.module.system.controller.admin.auth.vo.AuthSmsLoginReqVO
import im.hikaru.ruoyi.module.system.controller.admin.auth.vo.AuthSmsSendReqVO
import im.hikaru.ruoyi.module.system.controller.admin.oauth2.vo.client.OAuth2ClientSaveReqVO
import im.hikaru.ruoyi.module.system.controller.admin.user.vo.profile.UserProfileUpdateReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.user.AdminUserDO
import im.hikaru.ruoyi.module.system.dal.mysql.logger.LoginLogTable
import im.hikaru.ruoyi.module.system.dal.mysql.oauth2.OAuth2AccessTokenTable
import im.hikaru.ruoyi.module.system.dal.mysql.oauth2.OAuth2ApproveTable
import im.hikaru.ruoyi.module.system.dal.mysql.oauth2.OAuth2ClientTable
import im.hikaru.ruoyi.module.system.dal.mysql.oauth2.OAuth2CodeTable
import im.hikaru.ruoyi.module.system.dal.mysql.oauth2.OAuth2RefreshTokenTable
import im.hikaru.ruoyi.module.system.dal.mysql.user.AdminUserDao
import im.hikaru.ruoyi.module.system.dal.mysql.user.AdminUserTable
import im.hikaru.ruoyi.module.system.enums.sms.SmsSceneEnum
import im.hikaru.ruoyi.module.system.mq.producer.user.AdminUserProducer
import im.hikaru.ruoyi.module.system.service.dept.DeptService
import im.hikaru.ruoyi.module.system.service.dept.PostService
import im.hikaru.ruoyi.module.system.service.logger.LoginLogServiceImpl
import im.hikaru.ruoyi.module.system.service.member.MemberService
import im.hikaru.ruoyi.module.system.service.oauth2.OAuth2ClientServiceImpl
import im.hikaru.ruoyi.module.system.service.oauth2.OAuth2TokenService
import im.hikaru.ruoyi.module.system.service.oauth2.OAuth2TokenServiceImpl
import im.hikaru.ruoyi.module.system.service.permission.PermissionService
import im.hikaru.ruoyi.module.system.service.tenant.TenantService
import im.hikaru.ruoyi.module.system.service.user.AdminUserService
import im.hikaru.ruoyi.module.system.service.user.AdminUserServiceImpl
import com.anji.captcha.service.CaptchaService
import jakarta.validation.Validation
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.springframework.beans.factory.support.StaticListableBeanFactory
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.lang.reflect.Proxy

class AdminAuthRegistrationTest {
    private lateinit var authService: AdminAuthServiceImpl
    private lateinit var userService: AdminUserService
    private lateinit var smsCodeApi: RecordingSmsCodeApi
    private lateinit var adminUserProducer: AdminUserProducer

    @BeforeEach
    fun setUp() {
        Database.connect("jdbc:h2:mem:system_auth_register_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
        transaction {
            SchemaUtils.create(
                AdminUserTable,
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

        val beanFactory = StaticListableBeanFactory()
        adminUserProducer = mock(AdminUserProducer::class.java)
        userService = AdminUserServiceImpl(
            proxy(DeptService::class.java),
            proxy(PostService::class.java),
            proxy(PermissionService::class.java),
            beanFactory.getBeanProvider(TenantService::class.java),
            beanFactory.getBeanProvider(OAuth2TokenService::class.java),
            object : ConfigApi {
                override fun getConfigValueByKey(key: String) = "true"
            },
            adminUserProducer,
        )
        beanFactory.addBean("adminUserService", userService)
        smsCodeApi = RecordingSmsCodeApi()
        beanFactory.addBean("smsCodeApi", smsCodeApi)
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
    fun `register creates encoded user and access token`() {
        val response = authService.register(AuthRegisterReqVO().apply {
            username = "newuser"
            nickname = "New User"
            password = "password"
        })
        val user = AdminUserDao.selectByUsername("newuser")
        assertNotNull(user)
        assertEquals(user?.id, response.userId)
        assertTrue(userService.isPasswordMatch("password", requireNotNull(user?.password)))
        assertFalse(response.accessToken.isNullOrBlank())
    }

    @Test
    fun `reset password consumes sms code before updating password`() {
        val userId = AdminUserDao.insert(AdminUserDO().apply {
            username = "mobileuser"
            nickname = "Mobile User"
            password = "old-encoded"
            mobile = "13800000000"
            status = 0
            tenantId = 1L
        })
        authService.resetPassword(AuthResetPasswordReqVO().apply {
            mobile = "13800000000"
            code = "123456"
            password = "newpass"
        })
        assertEquals(SmsSceneEnum.ADMIN_MEMBER_RESET_PASSWORD.scene, smsCodeApi.lastUse?.scene)
        assertEquals("123456", smsCodeApi.lastUse?.code)
        assertTrue(userService.isPasswordMatch("newpass", requireNotNull(AdminUserDao.selectById(userId)?.password)))
    }

    @Test
    fun `send sms and sms login use admin scenes`() {
        AdminUserDao.insert(AdminUserDO().apply {
            username = "smsuser"
            nickname = "SMS User"
            password = "encoded"
            mobile = "13900000000"
            status = 0
            tenantId = 1L
        })
        authService.sendSmsCode(AuthSmsSendReqVO().apply {
            mobile = "13900000000"
            scene = SmsSceneEnum.ADMIN_MEMBER_LOGIN.scene
        })
        assertEquals(SmsSceneEnum.ADMIN_MEMBER_LOGIN.scene, smsCodeApi.lastSend?.scene)

        val login = authService.smsLogin(AuthSmsLoginReqVO().apply {
            mobile = "13900000000"
            code = "654321"
        })
        assertEquals(SmsSceneEnum.ADMIN_MEMBER_LOGIN.scene, smsCodeApi.lastUse?.scene)
        assertFalse(login.accessToken.isNullOrBlank())
    }

    @Test
    fun `profile changes publish the downstream user update event`() {
        val userId = AdminUserDao.insert(AdminUserDO().apply {
            username = "profileuser"
            nickname = "Old Name"
            avatar = "old-avatar"
            password = "encoded"
            status = 0
            tenantId = 1L
        })

        userService.updateUserProfile(userId, UserProfileUpdateReqVO().apply {
            nickname = "New Name"
            avatar = "new-avatar"
        })

        verify(adminUserProducer).sendUserProfileUpdateMessage(userId, "New Name", "new-avatar")
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> proxy(type: Class<T>): T = Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, _ ->
        when {
            method.returnType == Boolean::class.javaPrimitiveType -> false
            method.returnType == Long::class.javaPrimitiveType -> 0L
            Collection::class.java.isAssignableFrom(method.returnType) -> emptyList<Any>()
            else -> null
        }
    } as T

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

    private class RecordingSmsCodeApi : SmsCodeApi {
        var lastSend: SmsCodeSendReqDTO? = null
        var lastUse: SmsCodeUseReqDTO? = null
        override fun sendSmsCode(reqDTO: SmsCodeSendReqDTO) { lastSend = reqDTO }
        override fun useSmsCode(reqDTO: SmsCodeUseReqDTO) { lastUse = reqDTO }
        override fun validateSmsCode(reqDTO: SmsCodeValidateReqDTO) = Unit
    }
}
