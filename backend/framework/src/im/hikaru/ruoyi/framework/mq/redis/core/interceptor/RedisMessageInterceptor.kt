package im.hikaru.ruoyi.framework.mq.redis.core.interceptor

import im.hikaru.ruoyi.framework.mq.redis.core.message.AbstractRedisMessage

/**
 * [AbstractRedisMessage] 消息拦截器 (迁移自 Java)
 *
 * 通过拦截器，作为插件机制，实现拓展。例如说，多租户场景下的 MQ 消息处理
 *
 * @author 芋道源码
 */
interface RedisMessageInterceptor {
    fun sendMessageBefore(message: AbstractRedisMessage) {}
    fun sendMessageAfter(message: AbstractRedisMessage) {}
    fun consumeMessageBefore(message: AbstractRedisMessage) {}
    fun consumeMessageAfter(message: AbstractRedisMessage) {}
}
