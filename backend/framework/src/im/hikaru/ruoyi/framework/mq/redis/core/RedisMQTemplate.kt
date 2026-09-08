package im.hikaru.ruoyi.framework.mq.redis.core

import im.hikaru.ruoyi.framework.common.util.json.JsonUtils
import im.hikaru.ruoyi.framework.mq.redis.core.interceptor.RedisMessageInterceptor
import im.hikaru.ruoyi.framework.mq.redis.core.message.AbstractRedisMessage
import im.hikaru.ruoyi.framework.mq.redis.core.pubsub.AbstractRedisChannelMessage
import im.hikaru.ruoyi.framework.mq.redis.core.stream.AbstractRedisStreamMessage
import org.springframework.data.redis.connection.stream.RecordId
import org.springframework.data.redis.connection.stream.StreamRecords
import org.springframework.data.redis.core.RedisTemplate

/**
 * Redis MQ 操作模板类 (迁移自 Java, 去 Lombok)
 *
 * @author 芋道源码
 */
class RedisMQTemplate(
    val redisTemplate: RedisTemplate<String, *>,
) {
    /** 拦截器数组 */
    val interceptors: MutableList<RedisMessageInterceptor> = ArrayList()

    /**
     * 获取 StreamOperations (显式指定泛型, 解决 RedisTemplate<String, *> 的原始类型推断问题)
     */
    @Suppress("UNCHECKED_CAST")
    fun opsForStream(): org.springframework.data.redis.core.StreamOperations<String, Any, Any> =
        (redisTemplate as RedisTemplate<String, Any>).opsForStream()

    /**
     * 发送 Redis 消息，基于 Redis pub/sub 实现
     */
    fun <T : AbstractRedisChannelMessage> send(message: T) {
        try {
            sendMessageBefore(message)
            // 发送消息
            redisTemplate.convertAndSend(message.channel, JsonUtils.toJsonString(message))
        } finally {
            sendMessageAfter(message)
        }
    }

    /**
     * 发送 Redis 消息，基于 Redis Stream 实现
     *
     * @return 消息记录的编号对象
     */
    fun <T : AbstractRedisStreamMessage> send(message: T): RecordId {
        try {
            sendMessageBefore(message)
            // 发送消息
            val ops = opsForStream()
            return ops.add(
                StreamRecords.newRecord()
                    .ofObject(JsonUtils.toJsonString(message)) // 设置内容
                    .withStreamKey(message.streamKey), // 设置 stream key
            )
        } finally {
            sendMessageAfter(message)
        }
    }

    /**
     * 添加拦截器
     */
    fun addInterceptor(interceptor: RedisMessageInterceptor) {
        interceptors.add(interceptor)
    }

    private fun sendMessageBefore(message: AbstractRedisMessage) {
        // 正序
        interceptors.forEach { it.sendMessageBefore(message) }
    }

    private fun sendMessageAfter(message: AbstractRedisMessage) {
        // 倒序
        interceptors.reversed().forEach { it.sendMessageAfter(message) }
    }
}
