package im.hikaru.ruoyi.framework.websocket.core

import im.hikaru.ruoyi.framework.common.enums.UserTypeEnum
import im.hikaru.ruoyi.framework.common.util.json.JsonUtils
import im.hikaru.ruoyi.framework.security.core.LoginUser
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.framework.websocket.core.handler.JsonWebSocketMessageHandler
import im.hikaru.ruoyi.framework.websocket.core.listener.RawWebSocketMessageListener
import im.hikaru.ruoyi.framework.websocket.core.listener.WebSocketMessageListener
import im.hikaru.ruoyi.framework.websocket.core.message.JsonWebSocketMessage
import im.hikaru.ruoyi.framework.websocket.core.session.WebSocketSessionManagerImpl
import im.hikaru.ruoyi.framework.websocket.core.util.WebSocketFrameworkUtils
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.web.socket.TextMessage
import org.springframework.web.socket.WebSocketSession

class WebSocketTenantContextTest {

    @AfterEach
    fun tearDown() = TenantContextHolder.clear()

    @Test
    fun `message listener runs in session tenant and restores caller context`() {
        var observedTenantId: Long? = null
        var observedIgnore = true
        var observedValue: String? = null
        val listener = object : WebSocketMessageListener<TestPayload> {
            override fun getType(): String = "test"

            override fun onMessage(session: WebSocketSession, message: TestPayload) {
                observedTenantId = TenantContextHolder.getTenantId()
                observedIgnore = TenantContextHolder.isIgnore()
                observedValue = message.value
            }
        }
        val handler = JsonWebSocketMessageHandler(listOf(listener))
        val session = session("session-42", userId = 7L, tenantId = 42L)
        val payload = JsonUtils.toJsonString(
            JsonWebSocketMessage("test", JsonUtils.toJsonString(TestPayload("hello"))),
        )

        TenantContextHolder.setTenantId(9L)
        TenantContextHolder.setIgnore(true)
        handler.handleMessage(session, TextMessage(payload))

        assertEquals(42L, observedTenantId)
        assertFalse(observedIgnore)
        assertEquals("hello", observedValue)
        assertEquals(9L, TenantContextHolder.getTenantId())
        assertTrue(TenantContextHolder.isIgnore())
    }

    @Test
    fun `raw listener receives untouched content in session tenant`() {
        var observedTenantId: Long? = null
        var observedContent: String? = null
        val listener = object : RawWebSocketMessageListener {
            override fun getType(): String = "raw-test"

            override fun onMessage(session: WebSocketSession, message: String) {
                observedTenantId = TenantContextHolder.getTenantId()
                observedContent = message
            }
        }
        val handler = JsonWebSocketMessageHandler(listOf(listener))
        val session = session("raw-session", userId = 7L, tenantId = 42L)
        val content = "{\"hostId\":\"desktop-1\"}"
        val payload = JsonUtils.toJsonString(JsonWebSocketMessage("raw-test", content))

        handler.handleMessage(session, TextMessage(payload))

        assertEquals(42L, observedTenantId)
        assertEquals(content, observedContent)
    }

    @Test
    fun `session broadcasts are isolated by current tenant`() {
        val manager = WebSocketSessionManagerImpl()
        val tenantOne = session("tenant-one", userId = 1L, tenantId = 1L)
        val tenantTwo = session("tenant-two", userId = 2L, tenantId = 2L)
        manager.addSession(tenantOne)
        manager.addSession(tenantTwo)

        assertEquals(setOf("tenant-one", "tenant-two"), manager.getSessionList(UserTypeEnum.ADMIN.value).map { it.id }.toSet())

        TenantContextHolder.setTenantId(1L)
        assertEquals(listOf("tenant-one"), manager.getSessionList(UserTypeEnum.ADMIN.value).map { it.id })

        TenantContextHolder.setIgnore(true)
        assertEquals(setOf("tenant-one", "tenant-two"), manager.getSessionList(UserTypeEnum.ADMIN.value).map { it.id }.toSet())
    }

    @Test
    fun `targeted user sessions are isolated by current tenant`() {
        val manager = WebSocketSessionManagerImpl()
        val tenantOne = session("tenant-one-user", userId = 7L, tenantId = 1L)
        val tenantTwo = session("tenant-two-user", userId = 7L, tenantId = 2L)
        manager.addSession(tenantOne)
        manager.addSession(tenantTwo)

        TenantContextHolder.setTenantId(1L)
        assertEquals(
            listOf("tenant-one-user"),
            manager.getSessionList(UserTypeEnum.ADMIN.value, 7L).map { it.id },
        )

        TenantContextHolder.setIgnore(true)
        assertEquals(
            setOf("tenant-one-user", "tenant-two-user"),
            manager.getSessionList(UserTypeEnum.ADMIN.value, 7L).map { it.id }.toSet(),
        )
    }

    private fun session(id: String, userId: Long, tenantId: Long): WebSocketSession {
        val session = mock(WebSocketSession::class.java)
        val attributes = mutableMapOf<String, Any>()
        WebSocketFrameworkUtils.setLoginUser(
            LoginUser().apply {
                this.id = userId
                userType = UserTypeEnum.ADMIN.value
                this.tenantId = tenantId
            },
            attributes,
        )
        `when`(session.id).thenReturn(id)
        `when`(session.attributes).thenReturn(attributes)
        return session
    }

    class TestPayload(
        var value: String? = null,
    )
}
