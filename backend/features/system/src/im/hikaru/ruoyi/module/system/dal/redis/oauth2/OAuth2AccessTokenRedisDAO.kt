package im.hikaru.ruoyi.module.system.dal.redis.oauth2

import im.hikaru.ruoyi.framework.common.util.json.JsonUtils
import im.hikaru.ruoyi.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO
import im.hikaru.ruoyi.module.system.dal.redis.RedisKeyConstants.OAUTH2_ACCESS_TOKEN
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Repository
import java.time.Duration
import kotlin.time.Clock

@Repository
class OAuth2AccessTokenRedisDAO(
    private val stringRedisTemplate: StringRedisTemplate,
) {
    fun get(accessToken: String): OAuth2AccessTokenDO? =
        JsonUtils.parseObject(stringRedisTemplate.opsForValue().get(formatKey(accessToken)), CachedAccessToken::class.java)
            ?.toEntity()

    fun set(accessToken: OAuth2AccessTokenDO) {
        val token = accessToken.accessToken ?: return
        val expiresTime = accessToken.expiresTime ?: return
        val timeout = (expiresTime.toInstant(TimeZone.currentSystemDefault()) - Clock.System.now()).inWholeSeconds
        if (timeout <= 0) return
        stringRedisTemplate.opsForValue().set(
            formatKey(token),
            JsonUtils.toJsonString(CachedAccessToken.from(accessToken)),
            Duration.ofSeconds(timeout),
        )
    }

    fun delete(accessToken: String?) {
        if (accessToken == null) return
        stringRedisTemplate.delete(formatKey(accessToken))
    }

    fun deleteList(accessTokens: Collection<String?>) {
        val keys = accessTokens.mapNotNull { it?.let(::formatKey) }
        if (keys.isNotEmpty()) stringRedisTemplate.delete(keys)
    }

    private fun formatKey(accessToken: String): String = OAUTH2_ACCESS_TOKEN.format(accessToken)

    private class CachedAccessToken {
        var id: Long? = null
        var accessToken: String? = null
        var refreshToken: String? = null
        var userId: Long? = null
        var userType: Int? = null
        var userInfo: Map<String, String>? = null
        var clientId: String? = null
        var scopes: List<String>? = null
        var expiresTime: String? = null
        var tenantId: Long? = null

        fun toEntity() = OAuth2AccessTokenDO().also { target ->
            target.id = id
            target.accessToken = accessToken
            target.refreshToken = refreshToken
            target.userId = userId
            target.userType = userType
            target.userInfo = userInfo
            target.clientId = clientId
            target.scopes = scopes
            target.expiresTime = expiresTime?.let(LocalDateTime::parse)
            target.tenantId = tenantId
        }

        companion object {
            fun from(source: OAuth2AccessTokenDO) = CachedAccessToken().also { target ->
                target.id = source.id
                target.accessToken = source.accessToken
                target.refreshToken = source.refreshToken
                target.userId = source.userId
                target.userType = source.userType
                target.userInfo = source.userInfo
                target.clientId = source.clientId
                target.scopes = source.scopes
                target.expiresTime = source.expiresTime?.toString()
                target.tenantId = source.tenantId
            }
        }
    }
}
