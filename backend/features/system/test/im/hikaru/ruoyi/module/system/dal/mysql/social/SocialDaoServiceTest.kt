package im.hikaru.ruoyi.module.system.dal.mysql.social

import im.hikaru.ruoyi.framework.common.exception.ServiceException
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.module.system.api.social.dto.SocialUserBindReqDTO
import im.hikaru.ruoyi.module.system.controller.admin.socail.vo.client.SocialClientPageReqVO
import im.hikaru.ruoyi.module.system.controller.admin.socail.vo.client.SocialClientSaveReqVO
import im.hikaru.ruoyi.module.system.controller.admin.socail.vo.user.SocialUserPageReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.social.SocialClientDO
import im.hikaru.ruoyi.module.system.dal.dataobject.social.SocialUserDO
import im.hikaru.ruoyi.module.system.service.social.SocialAuthClient
import im.hikaru.ruoyi.module.system.service.social.SocialClientServiceImpl
import im.hikaru.ruoyi.module.system.service.social.SocialUserServiceImpl
import im.hikaru.ruoyi.module.system.framework.justauth.core.AuthRequestFactory
import cn.binarywang.wx.miniapp.api.WxMaService
import me.chanjar.weixin.mp.api.WxMpService
import me.zhyd.oauth.model.AuthToken
import me.zhyd.oauth.model.AuthUser
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.SchemaUtils
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.support.StaticListableBeanFactory

class SocialDaoServiceTest {
    private lateinit var authClient: FakeSocialClientService
    private lateinit var socialClientService: SocialClientServiceImpl
    private lateinit var socialUserService: SocialUserServiceImpl

    @BeforeEach
    fun setUp() {
        Database.connect("jdbc:h2:mem:system_social_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
        transaction { SchemaUtils.create(SocialClientTable, SocialUserTable, SocialUserBindTable) }
        TenantContextHolder.setTenantId(TENANT_ID)
        authClient = FakeSocialClientService()
        val beanFactory = StaticListableBeanFactory()
        socialClientService = SocialClientServiceImpl(
            beanFactory.getBeanProvider(AuthRequestFactory::class.java),
            beanFactory.getBeanProvider(WxMpService::class.java),
            beanFactory.getBeanProvider(WxMaService::class.java),
        )
        socialUserService = SocialUserServiceImpl(authClient)
    }

    @AfterEach
    fun tearDown() {
        TenantContextHolder.clear()
    }

    @Test
    fun `all social tables enforce tenant filtering`() {
        val clientId = SocialClientDao.insert(client("tenant-42"))
        val userId = SocialUserDao.insert(socialUser("openid-42", "code-42"))

        TenantContextHolder.setTenantId(84L)
        assertNull(SocialClientDao.selectById(clientId))
        assertNull(SocialUserDao.selectById(userId))
        assertNull(SocialClientDao.selectBySocialTypeAndUserType(SOCIAL_TYPE, USER_TYPE))

        TenantContextHolder.setTenantId(TENANT_ID)
        assertEquals("tenant-42", SocialClientDao.selectById(clientId)?.name)
        assertEquals("openid-42", SocialUserDao.selectById(userId)?.openid)
    }

    @Test
    fun `binding moves a social account and replaces the same social type`() {
        authClient.users["code-a"] = authUser("openid-a", "Alice")
        authClient.users["code-b"] = authUser("openid-b", "Bob")

        socialUserService.bindSocialUser(bindRequest(100L, "code-a"))
        socialUserService.bindSocialUser(bindRequest(200L, "code-a"))
        assertNull(socialUserService.getSocialUserByUserId(USER_TYPE, 100L, SOCIAL_TYPE))
        assertEquals("openid-a", socialUserService.getSocialUserByUserId(USER_TYPE, 200L, SOCIAL_TYPE)?.openid)

        socialUserService.bindSocialUser(bindRequest(200L, "code-b"))
        assertEquals("openid-b", socialUserService.getSocialUserByUserId(USER_TYPE, 200L, SOCIAL_TYPE)?.openid)
        assertNull(SocialUserBindDao.selectByUserTypeAndSocialUserId(USER_TYPE, requireNotNull(
            SocialUserDao.selectByTypeAndOpenid(SOCIAL_TYPE, "openid-a")?.id,
        )))
    }

    @Test
    fun `social client CRUD enforces tenant scoped uniqueness and paging`() {
        val id = socialClientService.createSocialClient(clientRequest("Primary client", SOCIAL_TYPE, USER_TYPE))
        assertThrows(ServiceException::class.java) {
            socialClientService.createSocialClient(clientRequest("Duplicate", SOCIAL_TYPE, USER_TYPE))
        }

        val page = socialClientService.getSocialClientPage(SocialClientPageReqVO().apply {
            name = "Primary"
            socialType = SOCIAL_TYPE
            pageNo = 1
            pageSize = 10
        })
        assertEquals(1L, page.total)
        assertEquals(id, page.list.single().id)

        socialClientService.updateSocialClient(clientRequest("Updated client", SOCIAL_TYPE, USER_TYPE).apply {
            this.id = id
            status = 1
        })
        assertEquals("Updated client", socialClientService.getSocialClient(id)?.name)
        assertEquals(1, socialClientService.getSocialClient(id)?.status)

        TenantContextHolder.setTenantId(84L)
        val otherTenantId = socialClientService.createSocialClient(clientRequest("Other tenant", SOCIAL_TYPE, USER_TYPE))
        assertNull(socialClientService.getSocialClient(id))
        assertEquals("Other tenant", socialClientService.getSocialClient(otherTenantId)?.name)

        TenantContextHolder.setTenantId(TENANT_ID)
        socialClientService.deleteSocialClient(id)
        assertNull(socialClientService.getSocialClient(id))
        assertThrows(ServiceException::class.java) { socialClientService.deleteSocialClient(id) }
    }

    @Test
    fun `authorization reuses code and upserts an existing openid`() {
        authClient.users["first-code"] = authUser("same-openid", "First name")
        val first = socialUserService.authSocialUser(SOCIAL_TYPE, USER_TYPE, "first-code", "state")
        val cached = socialUserService.authSocialUser(SOCIAL_TYPE, USER_TYPE, "first-code", "state")
        assertEquals(first.id, cached.id)
        assertEquals(1, authClient.calls)

        authClient.users["second-code"] = authUser("same-openid", "Updated name")
        val updated = socialUserService.authSocialUser(SOCIAL_TYPE, USER_TYPE, "second-code", "state")
        assertEquals(first.id, updated.id)
        assertEquals("Updated name", SocialUserDao.selectById(requireNotNull(first.id))?.nickname)
        assertEquals(2, authClient.calls)
        assertEquals(1L, transaction { SocialUserTable.selectAll().count() })

        val page = socialUserService.getSocialUserPage(SocialUserPageReqVO().apply {
            nickname = "Updated"
            openid = "same-openid"
            pageNo = 1
            pageSize = 10
        })
        assertEquals(1L, page.total)
        assertEquals(first.id, page.list.single().id)
    }

    private fun client(name: String) = SocialClientDO().apply {
        this.name = name
        socialType = SOCIAL_TYPE
        userType = USER_TYPE
        clientId = "client-id"
        clientSecret = "client-secret"
        status = 0
    }

    private fun clientRequest(name: String, socialType: Int, userType: Int) = SocialClientSaveReqVO().apply {
        this.name = name
        this.socialType = socialType
        this.userType = userType
        clientId = "client-$socialType-$userType"
        clientSecret = "secret"
        status = 0
    }

    private fun socialUser(openid: String, code: String) = SocialUserDO().apply {
        type = SOCIAL_TYPE
        this.openid = openid
        token = "token"
        rawTokenInfo = "{}"
        nickname = "nickname"
        rawUserInfo = "{}"
        this.code = code
        state = "state"
    }

    private fun bindRequest(userId: Long, code: String) = SocialUserBindReqDTO().apply {
        this.userId = userId
        userType = USER_TYPE
        socialType = SOCIAL_TYPE
        this.code = code
        state = "state"
    }

    private fun authUser(openid: String, nickname: String) = AuthUser().apply {
        uuid = openid
        username = nickname
        this.nickname = nickname
        avatar = "https://example.com/$openid.png"
        token = AuthToken().apply { accessToken = "token-$openid" }
    }

    private class FakeSocialClientService : SocialAuthClient {
        val users = mutableMapOf<String, AuthUser>()
        var calls = 0

        override fun getAuthorizeUrl(socialType: Int, userType: Int, redirectUri: String): String = redirectUri

        override fun getAuthUser(socialType: Int, userType: Int, code: String, state: String): AuthUser {
            calls++
            return requireNotNull(users[code])
        }
    }

    private companion object {
        const val TENANT_ID = 42L
        const val USER_TYPE = 2
        const val SOCIAL_TYPE = 10
    }
}
