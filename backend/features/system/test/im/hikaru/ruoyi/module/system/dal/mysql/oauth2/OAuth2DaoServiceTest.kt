package im.hikaru.ruoyi.module.system.dal.mysql.oauth2

import im.hikaru.ruoyi.framework.common.enums.UserTypeEnum
import im.hikaru.ruoyi.framework.common.exception.ServiceException
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.module.system.controller.admin.oauth2.vo.client.OAuth2ClientSaveReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO
import im.hikaru.ruoyi.module.system.dal.dataobject.oauth2.OAuth2RefreshTokenDO
import im.hikaru.ruoyi.module.system.dal.redis.oauth2.OAuth2AccessTokenRedisDAO
import im.hikaru.ruoyi.module.system.service.oauth2.OAuth2ApproveServiceImpl
import im.hikaru.ruoyi.module.system.service.oauth2.OAuth2ClientServiceImpl
import im.hikaru.ruoyi.module.system.service.oauth2.OAuth2CodeServiceImpl
import im.hikaru.ruoyi.module.system.service.oauth2.OAuth2GrantServiceImpl
import im.hikaru.ruoyi.module.system.service.oauth2.OAuth2TokenServiceImpl
import im.hikaru.ruoyi.module.system.service.auth.AdminAuthService
import im.hikaru.ruoyi.module.system.service.user.AdminUserService
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.support.StaticListableBeanFactory
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.OAUTH2_GRANT_CLIENT_ID_MISMATCH
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.OAUTH2_GRANT_REDIRECT_URI_MISMATCH
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.OAUTH2_GRANT_STATE_MISMATCH

class OAuth2DaoServiceTest {
    private lateinit var clientService: OAuth2ClientServiceImpl
    private lateinit var tokenService: OAuth2TokenServiceImpl
    private lateinit var codeService: OAuth2CodeServiceImpl
    private lateinit var grantService: OAuth2GrantServiceImpl
    private lateinit var accessTokenRedisDao: OAuth2AccessTokenRedisDAO

    @BeforeEach
    fun setUp() {
        Database.connect("jdbc:h2:mem:system_oauth2_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
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
        clientService = OAuth2ClientServiceImpl()
        val userProvider = StaticListableBeanFactory().getBeanProvider(AdminUserService::class.java)
        accessTokenRedisDao = mock(OAuth2AccessTokenRedisDAO::class.java)
        tokenService = OAuth2TokenServiceImpl(clientService, userProvider, accessTokenRedisDao)
        codeService = OAuth2CodeServiceImpl()
        grantService = OAuth2GrantServiceImpl(tokenService, codeService, mock(AdminAuthService::class.java))
    }

    @Test
    fun `access token lookup uses redis before the database`() {
        val cached = OAuth2AccessTokenDO().apply {
            id = 99
            accessToken = "cached-access"
            expiresTime = LocalDateTime(2099, 1, 1, 0, 0)
            tenantId = TENANT_ID
        }
        `when`(accessTokenRedisDao.get("cached-access")).thenReturn(cached)

        assertEquals(cached, tokenService.getAccessToken("cached-access"))
    }

    @AfterEach
    fun tearDown() {
        TenantContextHolder.clear()
    }

    @Test
    fun `client json fields and token lifecycle round trip`() {
        val id = clientService.createOAuth2Client(clientRequest())
        val client = OAuth2ClientDao.selectById(id)
        assertEquals(listOf("https://app.example.com"), client?.redirectUris)
        assertEquals(listOf("password", "refresh_token"), client?.authorizedGrantTypes)

        val first = tokenService.createAccessToken(100L, UserTypeEnum.ADMIN.value, "test-client", listOf("user.read"))
        assertNotNull(first.id)
        assertEquals(TENANT_ID, first.tenantId)
        assertFalse(first.accessToken.isNullOrBlank())
        assertFalse(first.refreshToken.isNullOrBlank())

        TenantContextHolder.clear()
        assertEquals(first.id, tokenService.getAccessToken(requireNotNull(first.accessToken))?.id)
        val refreshed = tokenService.refreshAccessToken(requireNotNull(first.refreshToken), "test-client")
        assertNotEquals(first.accessToken, refreshed.accessToken)
        assertEquals(TENANT_ID, refreshed.tenantId)
        assertNull(OAuth2AccessTokenDao.selectByAccessToken(requireNotNull(first.accessToken)))

        assertEquals(refreshed.id, tokenService.removeAccessToken(requireNotNull(refreshed.accessToken))?.id)
        assertNull(tokenService.getAccessToken(requireNotNull(refreshed.accessToken)))
        assertNull(OAuth2RefreshTokenDao.selectByRefreshToken(requireNotNull(refreshed.refreshToken)))
    }

    @Test
    fun `authorization code approvals and physical cleanup preserve semantics`() {
        clientService.createOAuth2Client(clientRequest())
        val code = codeService.createAuthorizationCode(
            100L,
            UserTypeEnum.ADMIN.value,
            "test-client",
            listOf("user.read"),
            "https://app.example.com/callback",
            "state",
        )
        assertEquals(code.id, codeService.consumeAuthorizationCode(requireNotNull(code.code)).id)
        assertThrows(ServiceException::class.java) { codeService.consumeAuthorizationCode(requireNotNull(code.code)) }

        val approveService = OAuth2ApproveServiceImpl(clientService)
        assertTrue(approveService.checkForPreApproval(100L, UserTypeEnum.ADMIN.value, "test-client", listOf("user.read")))
        assertEquals(setOf("user.read"), approveService.getApproveList(100L, UserTypeEnum.ADMIN.value, "test-client").mapNotNull { it.scope }.toSet())

        val expiredAccess = OAuth2AccessTokenDO().apply {
            userId = 100L
            userType = UserTypeEnum.ADMIN.value
            userInfo = emptyMap()
            accessToken = "expired-access"
            refreshToken = "expired-refresh"
            clientId = "test-client"
            scopes = listOf("user.read")
            expiresTime = LocalDateTime(2020, 1, 1, 0, 0)
            tenantId = TENANT_ID
        }
        val accessId = OAuth2AccessTokenDao.insert(expiredAccess)
        OAuth2AccessTokenDao.deleteById(accessId)
        assertEquals(1, tokenService.cleanAccessToken(0, 100))
        assertEquals(0L, transaction {
            OAuth2AccessTokenTable.selectAll().where { OAuth2AccessTokenTable.id eq accessId }.count()
        })

        val expiredRefresh = OAuth2RefreshTokenDO().apply {
            userId = 100L
            userType = UserTypeEnum.ADMIN.value
            refreshToken = "old-refresh-token-0000000000000"
            clientId = "test-client"
            scopes = listOf("user.read")
            expiresTime = LocalDateTime(2020, 1, 1, 0, 0)
            tenantId = TENANT_ID
        }
        val refreshId = OAuth2RefreshTokenDao.insert(expiredRefresh)
        OAuth2RefreshTokenDao.deleteById(refreshId)
        assertEquals(1, tokenService.cleanRefreshToken(0, 100))
        assertEquals(0L, transaction {
            OAuth2RefreshTokenTable.selectAll().where { OAuth2RefreshTokenTable.id eq refreshId }.count()
        })
    }

    @Test
    fun `authorization grant validates code binding and revokes only matching client tokens`() {
        clientService.createOAuth2Client(clientRequest())

        val accessToken = grantService.grantAuthorizationCodeForAccessToken(
            "test-client",
            createCode("https://app.example.com/callback", "state"),
            "https://app.example.com/callback",
            "state",
        )
        assertEquals(100L, accessToken.userId)
        assertEquals(listOf("user.read"), accessToken.scopes)

        assertGrantError(OAUTH2_GRANT_CLIENT_ID_MISMATCH.code) {
            grantService.grantAuthorizationCodeForAccessToken(
                "different-client",
                createCode("https://app.example.com/callback", "state"),
                "https://app.example.com/callback",
                "state",
            )
        }
        assertGrantError(OAUTH2_GRANT_REDIRECT_URI_MISMATCH.code) {
            grantService.grantAuthorizationCodeForAccessToken(
                "test-client",
                createCode("https://app.example.com/callback", "state"),
                "https://app.example.com/other",
                "state",
            )
        }
        assertGrantError(OAUTH2_GRANT_STATE_MISMATCH.code) {
            grantService.grantAuthorizationCodeForAccessToken(
                "test-client",
                createCode("https://app.example.com/callback", null),
                "https://app.example.com/callback",
                "different-state",
            )
        }

        val tokenValue = requireNotNull(accessToken.accessToken)
        assertFalse(grantService.revokeToken("different-client", tokenValue))
        assertTrue(grantService.revokeToken("test-client", tokenValue))
        assertNull(tokenService.getAccessToken(tokenValue))

        val clientCredentials = grantService.grantClientCredentials("test-client", listOf("user.read"))
        assertEquals(0L, clientCredentials.userId)
        assertEquals(UserTypeEnum.ADMIN.value, clientCredentials.userType)
    }

    private fun createCode(redirectUri: String, state: String?): String = requireNotNull(
        codeService.createAuthorizationCode(
            100L,
            UserTypeEnum.ADMIN.value,
            "test-client",
            listOf("user.read"),
            redirectUri,
            state,
        ).code,
    )

    private fun assertGrantError(code: Int, block: () -> Unit) {
        val exception = assertThrows(ServiceException::class.java, block)
        assertEquals(code, exception.code)
    }

    private fun clientRequest() = OAuth2ClientSaveReqVO().apply {
        clientId = "test-client"
        secret = "secret"
        name = "Test Client"
        logo = "https://app.example.com/logo.png"
        status = 0
        accessTokenValiditySeconds = 600
        refreshTokenValiditySeconds = 3600
        redirectUris = listOf("https://app.example.com")
        authorizedGrantTypes = listOf("password", "refresh_token")
        scopes = listOf("user.read", "user.write")
        autoApproveScopes = listOf("user.read")
        authorities = emptyList()
        resourceIds = emptyList()
        additionalInformation = "{\"test\":true}"
    }

    companion object {
        private const val TENANT_ID = 42L
    }
}
