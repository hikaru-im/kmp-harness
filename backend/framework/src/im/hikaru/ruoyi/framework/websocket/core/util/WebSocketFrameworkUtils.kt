package im.hikaru.ruoyi.framework.websocket.core.util

import im.hikaru.ruoyi.framework.security.core.LoginUser
import org.springframework.web.socket.WebSocketSession

object WebSocketFrameworkUtils {
    const val ATTRIBUTE_LOGIN_USER = "LOGIN_USER"

    fun setLoginUser(loginUser: LoginUser, attributes: MutableMap<String, Any>) {
        attributes[ATTRIBUTE_LOGIN_USER] = loginUser
    }

    fun getLoginUser(session: WebSocketSession): LoginUser? =
        session.attributes[ATTRIBUTE_LOGIN_USER] as? LoginUser

    fun getLoginUserId(session: WebSocketSession): Long? = getLoginUser(session)?.id

    fun getLoginUserType(session: WebSocketSession): Int? = getLoginUser(session)?.userType

    fun getTenantId(session: WebSocketSession): Long? = getLoginUser(session)?.tenantId
}
