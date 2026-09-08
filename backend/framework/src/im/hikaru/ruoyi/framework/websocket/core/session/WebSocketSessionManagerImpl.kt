package im.hikaru.ruoyi.framework.websocket.core.session

import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import im.hikaru.ruoyi.framework.websocket.core.util.WebSocketFrameworkUtils
import org.springframework.web.socket.WebSocketSession
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.CopyOnWriteArrayList

class WebSocketSessionManagerImpl : WebSocketSessionManager {

    private val idSessions: ConcurrentMap<String, WebSocketSession> = ConcurrentHashMap()

    private val userSessions:
        ConcurrentMap<Int, ConcurrentMap<Long, CopyOnWriteArrayList<WebSocketSession>>> = ConcurrentHashMap()

    override fun addSession(session: WebSocketSession) {
        idSessions[session.id] = session
        val user = WebSocketFrameworkUtils.getLoginUser(session) ?: return
        val userSessionsMap = userSessions.computeIfAbsent(user.userType!!) { ConcurrentHashMap() }
        val sessions = userSessionsMap.computeIfAbsent(user.id!!) { CopyOnWriteArrayList() }
        sessions.add(session)
    }

    override fun removeSession(session: WebSocketSession) {
        idSessions.remove(session.id)
        val user = WebSocketFrameworkUtils.getLoginUser(session) ?: return
        val userSessionsMap = userSessions[user.userType] ?: return
        val sessions = userSessionsMap[user.id] ?: return
        sessions.removeIf { it.id == session.id }
        if (sessions.isEmpty()) {
            userSessionsMap.remove(user.id, sessions)
        }
    }

    override fun getSession(id: String): WebSocketSession? = idSessions[id]

    override fun getSessionList(userType: Int?): Collection<WebSocketSession> {
        val userSessionsMap = userSessions[userType] ?: return ArrayList()
        val result = ArrayList<WebSocketSession>()
        val contextTenantId = TenantContextHolder.getTenantId().takeUnless { TenantContextHolder.isIgnore() }
        userSessionsMap.values.forEach { sessions ->
            sessions.filterTo(result) { session ->
                contextTenantId == null || WebSocketFrameworkUtils.getTenantId(session) == contextTenantId
            }
        }
        return result
    }

    override fun getSessionList(userType: Int?, userId: Long?): Collection<WebSocketSession> {
        val userSessionsMap = userSessions[userType] ?: return ArrayList()
        val sessions = userSessionsMap[userId] ?: return ArrayList()
        val contextTenantId = TenantContextHolder.getTenantId().takeUnless { TenantContextHolder.isIgnore() }
        return sessions.filter { session ->
            contextTenantId == null || WebSocketFrameworkUtils.getTenantId(session) == contextTenantId
        }
    }
}
