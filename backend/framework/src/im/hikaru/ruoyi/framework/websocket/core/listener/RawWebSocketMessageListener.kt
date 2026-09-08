package im.hikaru.ruoyi.framework.websocket.core.listener

/**
 * 直接消费 RuoYi WebSocket 外层消息中的原始 `content`。
 *
 * 适用于由其他序列化器拥有 wire contract 的模块，避免框架先通过 Jackson 转换一次。
 */
interface RawWebSocketMessageListener : WebSocketMessageListener<String>
