package im.hikaru.ruoyi.module.system.dal.redis.oauth2

import im.hikaru.ruoyi.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.ValueOperations
import java.time.Duration
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

class OAuth2AccessTokenRedisDAOTest {

    @Test
    fun `access tokens round trip with dynamic ttl and formatted keys`() {
        val template = mock(StringRedisTemplate::class.java)
        @Suppress("UNCHECKED_CAST")
        val operations = mock(ValueOperations::class.java) as ValueOperations<String, String>
        `when`(template.opsForValue()).thenReturn(operations)
        val dao = OAuth2AccessTokenRedisDAO(template)
        val token = OAuth2AccessTokenDO().apply {
            id = 1
            accessToken = "access"
            refreshToken = "refresh"
            userId = 2
            userType = 1
            userInfo = mapOf("nickname" to "Admin")
            clientId = "client"
            scopes = listOf("read")
            expiresTime = Clock.System.now().plus(120.seconds).toLocalDateTime(TimeZone.currentSystemDefault())
            tenantId = 3
        }

        dao.set(token)

        val json = ArgumentCaptor.forClass(String::class.java)
        verify(operations).set(eq("oauth2_access_token:access"), json.capture(), any(Duration::class.java))
        `when`(operations.get("oauth2_access_token:access")).thenReturn(json.value)
        val cached = requireNotNull(dao.get("access"))
        assertEquals(token.id, cached.id)
        assertEquals(token.userInfo, cached.userInfo)
        assertEquals(token.scopes, cached.scopes)
        assertEquals(token.expiresTime, cached.expiresTime)
        assertEquals(token.tenantId, cached.tenantId)

        dao.deleteList(listOf("access", "refresh", null))
        verify(template).delete(listOf("oauth2_access_token:access", "oauth2_access_token:refresh"))
        assertTrue(json.value.contains("\"expiresTime\""))
    }
}
