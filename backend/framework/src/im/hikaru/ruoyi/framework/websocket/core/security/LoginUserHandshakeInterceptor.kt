package im.hikaru.ruoyi.framework.websocket.core.security

import im.hikaru.ruoyi.framework.security.core.util.SecurityFrameworkUtils
import im.hikaru.ruoyi.framework.websocket.core.util.WebSocketFrameworkUtils
import org.springframework.http.server.ServerHttpRequest
import org.springframework.http.server.ServerHttpResponse
import org.springframework.web.socket.WebSocketHandler
import org.springframework.web.socket.server.HandshakeInterceptor

class LoginUserHandshakeInterceptor : HandshakeInterceptor {
    override fun beforeHandshake(
        request: ServerHttpRequest, response: ServerHttpResponse,
        wsHandler: WebSocketHandler, attributes: MutableMap<String, Any>,
    ): Boolean {
        val loginUser = SecurityFrameworkUtils.getLoginUser() ?: return false
        WebSocketFrameworkUtils.setLoginUser(loginUser, attributes)
        return true
    }

    override fun afterHandshake(
        request: ServerHttpRequest, response: ServerHttpResponse,
        wsHandler: WebSocketHandler, exception: Exception?,
    ) {
        // do nothing
    }
}
