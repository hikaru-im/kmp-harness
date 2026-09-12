package im.hikaru.ruoyi.module.member.service.auth

import im.hikaru.ruoyi.framework.common.enums.UserTypeEnum
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.module.member.controller.app.auth.vo.AppAuthLoginReqVO
import im.hikaru.ruoyi.module.member.dal.dataobject.user.MemberUserDO
import im.hikaru.ruoyi.module.member.service.user.MemberUserService
import im.hikaru.ruoyi.module.system.api.logger.LoginLogApi
import im.hikaru.ruoyi.module.system.api.logger.dto.LoginLogCreateReqDTO
import im.hikaru.ruoyi.module.system.api.oauth2.OAuth2TokenApiImpl
import im.hikaru.ruoyi.module.system.api.sms.SmsCodeApi
import im.hikaru.ruoyi.module.system.api.social.SocialClientApi
import im.hikaru.ruoyi.module.system.api.social.SocialUserApi
import im.hikaru.ruoyi.module.system.controller.admin.oauth2.vo.client.OAuth2ClientSaveReqVO
import im.hikaru.ruoyi.module.system.dal.mysql.oauth2.OAuth2AccessTokenTable
import im.hikaru.ruoyi.module.system.dal.mysql.oauth2.OAuth2ApproveTable
import im.hikaru.ruoyi.module.system.dal.mysql.oauth2.OAuth2ClientTable
import im.hikaru.ruoyi.module.system.dal.mysql.oauth2.OAuth2CodeTable
import im.hikaru.ruoyi.module.system.dal.mysql.oauth2.OAuth2RefreshTokenTable
import im.hikaru.ruoyi.module.system.service.oauth2.OAuth2ClientServiceImpl
import im.hikaru.ruoyi.module.system.service.oauth2.OAuth2TokenServiceImpl
import im.hikaru.ruoyi.module.system.service.user.AdminUserService
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.support.StaticListableBeanFactory
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.lang.reflect.Proxy

class MemberAuthLoginTest {
    private lateinit var authService: MemberAuthServiceImpl
    private lateinit var tokenApi: OAuth2TokenApiImpl
    private val loginLogs = mutableListOf<LoginLogCreateReqDTO>()
    private var loginUpdates = 0

    @BeforeEach
    fun setUp() {
        Database.connect(
            "jdbc:h2:mem:member_auth_login_" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1",
            driver = "org.h2.Driver",
        )
        transaction {
            SchemaUtils.create(
                OAuth2ClientTable,
                OAuth2AccessTokenTable,
                OAuth2RefreshTokenTable,
                OAuth2CodeTable,
                OAuth2ApproveTable,
            )
        }
        TenantContextHolder.setTenantId(TENANT_ID)
        RequestContextHolder.setRequestAttributes(ServletRequestAttributes(MockHttpServletRequest().apply {
            remoteAddr = "127.0.0.1"
            addHeader("User-Agent", "JUnit")
        }))

        val clientService = OAuth2ClientServiceImpl()
        clientService.createOAuth2Client(clientRequest())
        val adminUserProvider = StaticListableBeanFactory().getBeanProvider(AdminUserService::class.java)
        tokenApi = OAuth2TokenApiImpl(OAuth2TokenServiceImpl(clientService, adminUserProvider))
        authService = MemberAuthServiceImpl(
            memberUserService(),
            unusedApi(SmsCodeApi::class.java),
            object : LoginLogApi {
                override fun createLoginLog(reqDTO: LoginLogCreateReqDTO) {
                    loginLogs += reqDTO
                }
            },
            unusedApi(SocialUserApi::class.java),
            unusedApi(SocialClientApi::class.java),
            tokenApi,
        )
    }

    @AfterEach
    fun tearDown() {
        TenantContextHolder.clear()
        RequestContextHolder.resetRequestAttributes()
    }

    @Test
    fun memberPasswordLoginAndRefreshPreserveMemberTenantIdentity() {
        val login = authService.login(AppAuthLoginReqVO().apply {
            mobile = MOBILE
            password = PASSWORD
        })

        assertEquals(USER_ID, login.userId)
        assertFalse(login.accessToken.isNullOrBlank())
        assertFalse(login.refreshToken.isNullOrBlank())
        assertEquals(1, loginUpdates)
        assertEquals(UserTypeEnum.MEMBER.value, loginLogs.single().userType)

        val refreshed = authService.refreshToken(requireNotNull(login.refreshToken))
        assertNotEquals(login.accessToken, refreshed.accessToken)
        assertEquals(login.refreshToken, refreshed.refreshToken)
        tokenApi.checkAccessToken(requireNotNull(refreshed.accessToken)).also {
            assertEquals(USER_ID, it.userId)
            assertEquals(UserTypeEnum.MEMBER.value, it.userType)
            assertEquals(TENANT_ID, it.tenantId)
        }
    }

    private fun memberUserService(): MemberUserService {
        val user = MemberUserDO().apply {
            id = USER_ID
            tenantId = TENANT_ID
            mobile = MOBILE
            password = ENCODED_PASSWORD
            status = 0
            nickname = "Member"
        }
        return Proxy.newProxyInstance(
            MemberUserService::class.java.classLoader,
            arrayOf(MemberUserService::class.java),
        ) { _, method, args ->
            when (method.name) {
                "getUserByMobile" -> if (args?.get(0) == MOBILE) user else null
                "getUser" -> if (args?.get(0) == USER_ID) user else null
                "isPasswordMatch" -> args?.get(0) == PASSWORD && args.get(1) == ENCODED_PASSWORD
                "updateUserLogin" -> {
                    loginUpdates += 1
                    Unit
                }
                "toString" -> "MemberUserServiceTestDouble"
                "hashCode" -> System.identityHashCode(this)
                "equals" -> false
                else -> null
            }
        } as MemberUserService
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> unusedApi(type: Class<T>): T = Proxy.newProxyInstance(
        type.classLoader,
        arrayOf(type),
    ) { _, method, _ ->
        when (method.name) {
            "toString" -> type.simpleName + "TestDouble"
            "hashCode" -> System.identityHashCode(this)
            "equals" -> false
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

    private companion object {
        const val TENANT_ID = 42L
        const val USER_ID = 17L
        const val MOBILE = "13800000000"
        const val PASSWORD = "member-password"
        const val ENCODED_PASSWORD = "encoded-password"
    }
}
