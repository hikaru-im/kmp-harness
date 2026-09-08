package im.hikaru.ruoyi.module.system.service.oauth2

import im.hikaru.ruoyi.framework.common.enums.UserTypeEnum
import im.hikaru.ruoyi.framework.common.exception.enums.GlobalErrorCodeConstants.BAD_REQUEST
import im.hikaru.ruoyi.framework.common.exception.enums.GlobalErrorCodeConstants.UNAUTHORIZED
import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception0
import im.hikaru.ruoyi.framework.security.core.LoginUser
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.module.system.controller.admin.oauth2.vo.token.OAuth2AccessTokenPageReqVO
import im.hikaru.ruoyi.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO
import im.hikaru.ruoyi.module.system.dal.dataobject.oauth2.OAuth2ClientDO
import im.hikaru.ruoyi.module.system.dal.dataobject.oauth2.OAuth2RefreshTokenDO
import im.hikaru.ruoyi.module.system.dal.mysql.oauth2.OAuth2AccessTokenDao
import im.hikaru.ruoyi.module.system.dal.mysql.oauth2.OAuth2RefreshTokenDao
import im.hikaru.ruoyi.module.system.dal.redis.oauth2.OAuth2AccessTokenRedisDAO
import im.hikaru.ruoyi.module.system.service.user.AdminUserService
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service
import java.util.UUID
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

@Service
class OAuth2TokenServiceImpl(
    private val oauth2ClientService: OAuth2ClientService,
    private val adminUserServiceProvider: ObjectProvider<AdminUserService>,
    private val accessTokenRedisDao: OAuth2AccessTokenRedisDAO? = null,
) : OAuth2TokenService {
    override fun createAccessToken(userId: Long, userType: Int, clientId: String, scopes: List<String>): OAuth2AccessTokenDO {
        val client = oauth2ClientService.validOAuthClientFromCache(clientId)
        val refreshToken = createOAuth2RefreshToken(userId, userType, client, scopes)
        return createOAuth2AccessToken(refreshToken, client)
    }

    override fun refreshAccessToken(refreshToken: String, clientId: String): OAuth2AccessTokenDO {
        val refresh = OAuth2RefreshTokenDao.selectByRefreshToken(refreshToken)
            ?: throw exception0(BAD_REQUEST.code, "Invalid refresh token")
        val client = oauth2ClientService.validOAuthClientFromCache(clientId)
        if (refresh.clientId != clientId) throw exception0(BAD_REQUEST.code, "Refresh token client id mismatch")

        val oldAccessTokens = OAuth2AccessTokenDao.selectListByRefreshToken(refreshToken)
        oldAccessTokens.forEach {
            OAuth2AccessTokenDao.deleteById(requireNotNull(it.id))
        }
        accessTokenRedisDao?.deleteList(oldAccessTokens.map { it.accessToken })
        if (isExpired(refresh.expiresTime)) {
            OAuth2RefreshTokenDao.deleteById(requireNotNull(refresh.id))
            throw exception0(UNAUTHORIZED.code, "Refresh token has expired")
        }
        return createOAuth2AccessToken(refresh, client)
    }

    override fun getAccessToken(accessToken: String): OAuth2AccessTokenDO? {
        accessTokenRedisDao?.get(accessToken)?.let { return it }
        var token = OAuth2AccessTokenDao.selectByAccessToken(accessToken)
        if (token == null) {
            val refresh = OAuth2RefreshTokenDao.selectByRefreshToken(accessToken) ?: return null
            if (isExpired(refresh.expiresTime)) return null
            token = convertToAccessToken(refresh)
        }
        if (!isExpired(token.expiresTime)) accessTokenRedisDao?.set(token)
        return token
    }

    override fun checkAccessToken(accessToken: String): OAuth2AccessTokenDO {
        val token = getAccessToken(accessToken) ?: throw exception0(UNAUTHORIZED.code, "Access token does not exist")
        if (isExpired(token.expiresTime)) throw exception0(UNAUTHORIZED.code, "Access token has expired")
        return token
    }

    override fun removeAccessToken(accessToken: String): OAuth2AccessTokenDO? {
        val token = OAuth2AccessTokenDao.selectByAccessToken(accessToken) ?: return null
        OAuth2AccessTokenDao.deleteById(requireNotNull(token.id))
        accessTokenRedisDao?.delete(accessToken)
        token.refreshToken?.let(OAuth2RefreshTokenDao::deleteByRefreshToken)
        accessTokenRedisDao?.delete(token.refreshToken)
        return token
    }

    override fun removeAccessToken(userId: Long, userType: Int) {
        val accessTokens = OAuth2AccessTokenDao.selectListByUserIdAndUserType(userId, userType)
        accessTokens.forEach {
            OAuth2AccessTokenDao.deleteById(requireNotNull(it.id))
            it.refreshToken?.let(OAuth2RefreshTokenDao::deleteByRefreshToken)
        }
        accessTokenRedisDao?.deleteList(accessTokens.flatMap { listOf(it.accessToken, it.refreshToken) })
    }

    override fun getAccessTokenPage(req: OAuth2AccessTokenPageReqVO) = OAuth2AccessTokenDao.selectPage(req, now())

    override fun cleanRefreshToken(exceedDay: Int, deleteLimit: Int): Int =
        cleanExpired(exceedDay, deleteLimit, OAuth2RefreshTokenDao::deleteByExpiresTimeLt)

    override fun cleanAccessToken(exceedDay: Int, deleteLimit: Int): Int =
        cleanExpired(exceedDay, deleteLimit, OAuth2AccessTokenDao::deleteByExpiresTimeLt)

    private fun createOAuth2AccessToken(refresh: OAuth2RefreshTokenDO, client: OAuth2ClientDO): OAuth2AccessTokenDO {
        val entity = OAuth2AccessTokenDO().apply {
            accessToken = generateToken()
            userId = refresh.userId
            userType = refresh.userType
            userInfo = buildUserInfo(refresh.userId, refresh.userType)
            clientId = client.clientId
            scopes = refresh.scopes
            refreshToken = refresh.refreshToken
            expiresTime = afterSeconds(requireNotNull(client.accessTokenValiditySeconds))
            tenantId = refresh.tenantId ?: TenantContextHolder.getTenantId()
        }
        OAuth2AccessTokenDao.insert(entity)
        accessTokenRedisDao?.set(entity)
        return entity
    }

    private fun createOAuth2RefreshToken(
        userId: Long,
        userType: Int,
        client: OAuth2ClientDO,
        scopes: List<String>,
    ): OAuth2RefreshTokenDO {
        val entity = OAuth2RefreshTokenDO().apply {
            refreshToken = generateToken()
            this.userId = userId
            this.userType = userType
            clientId = client.clientId
            this.scopes = scopes
            expiresTime = afterSeconds(requireNotNull(client.refreshTokenValiditySeconds))
            tenantId = TenantContextHolder.getTenantId()
        }
        OAuth2RefreshTokenDao.insert(entity)
        return entity
    }

    private fun convertToAccessToken(refresh: OAuth2RefreshTokenDO) = OAuth2AccessTokenDO().apply {
        accessToken = refresh.refreshToken
        refreshToken = refresh.refreshToken
        userId = refresh.userId
        userType = refresh.userType
        userInfo = buildUserInfo(refresh.userId, refresh.userType)
        clientId = refresh.clientId
        scopes = refresh.scopes
        expiresTime = refresh.expiresTime
        tenantId = refresh.tenantId
    }

    private fun buildUserInfo(userId: Long?, userType: Int?): Map<String, String> {
        if (userId == null || userId <= 0) return emptyMap()
        return when (userType) {
            UserTypeEnum.ADMIN.value -> {
                val user = adminUserServiceProvider.ifAvailable?.getUser(userId) ?: return emptyMap()
                buildMap {
                    user.nickname?.let { put(LoginUser.INFO_KEY_NICKNAME, it) }
                    user.deptId?.let { put(LoginUser.INFO_KEY_DEPT_ID, it.toString()) }
                }
            }
            UserTypeEnum.MEMBER.value -> emptyMap()
            else -> throw IllegalArgumentException("Unknown user type: $userType")
        }
    }

    private fun cleanExpired(
        exceedDay: Int,
        deleteLimit: Int,
        delete: (kotlinx.datetime.LocalDateTime, Int) -> Int,
    ): Int {
        require(deleteLimit > 0)
        val expiresTime = Clock.System.now()
            .minus((exceedDay.toLong() * 24 * 60 * 60).seconds)
            .toLocalDateTime(TimeZone.currentSystemDefault())
        var count = 0
        repeat(Short.MAX_VALUE.toInt()) {
            val deleted = delete(expiresTime, deleteLimit)
            count += deleted
            if (deleted < deleteLimit) return count
        }
        return count
    }

    private fun isExpired(time: kotlinx.datetime.LocalDateTime?) = time == null || time <= now()
    private fun now() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    private fun afterSeconds(seconds: Int) =
        Clock.System.now().plus(seconds.seconds).toLocalDateTime(TimeZone.currentSystemDefault())

    private fun generateToken() = UUID.randomUUID().toString().replace("-", "")
}
