package im.hikaru.ruoyi.framework.websocket.core.sender.local

import im.hikaru.ruoyi.framework.websocket.core.sender.AbstractWebSocketMessageSender
import im.hikaru.ruoyi.framework.websocket.core.session.WebSocketSessionManager

class LocalWebSocketMessageSender(sessionManager: WebSocketSessionManager) :
    AbstractWebSocketMessageSender(sessionManager)
