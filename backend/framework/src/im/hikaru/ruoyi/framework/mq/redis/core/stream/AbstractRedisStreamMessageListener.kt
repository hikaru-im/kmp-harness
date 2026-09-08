package im.hikaru.ruoyi.framework.mq.redis.core.stream

import im.hikaru.ruoyi.framework.common.util.json.JsonUtils
import im.hikaru.ruoyi.framework.mq.redis.core.RedisMQTemplate
import im.hikaru.ruoyi.framework.mq.redis.core.interceptor.RedisMessageInterceptor
import im.hikaru.ruoyi.framework.mq.redis.core.message.AbstractRedisMessage
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.connection.stream.ObjectRecord
import org.springframework.data.redis.stream.StreamListener
import java.lang.reflect.ParameterizedType

/**
 * Redis Stream 监听器抽象类，用于实现集群消费 (迁移自 Java, 去 Lombok/Hutool)
 *
 * @param T 消息类型。一定要填写噢，不然会报错
 *
 * 迁移说明：Hutool TypeUtil.getTypeArgument → JDK ParameterizedType 反射
 *
 * @author 芋道源码
 */
abstract class AbstractRedisStreamMessageListener<T : AbstractRedisStreamMessage> :
    StreamListener<String, ObjectRecord<String, String>> {

    /** 消息类型 */
    private val messageType: Class<T>?
    /** Redis Stream Key */
    val streamKey: String

    /** Redis 消费者分组，默认使用 spring.application.name 名字 */
    @Value("\${spring.application.name}")
    lateinit var group: String

    /** RedisMQTemplate */
    lateinit var redisMQTemplate: RedisMQTemplate

    constructor() {
        this.messageType = getMessageClass()
        this.streamKey = messageType!!.getDeclaredConstructor().newInstance().streamKey
    }

    constructor(streamKey: String, group: String) {
        this.messageType = null
        this.streamKey = streamKey
        this.group = group
    }

    @Suppress("UNCHECKED_CAST")
    override fun onMessage(message: ObjectRecord<String, String>) {
        // 消费消息
        val messageObj = JsonUtils.parseObject(message.value, messageType!!)
        try {
            consumeMessageBefore(messageObj!!)
            // 消费消息
            this.onMessage(messageObj)
            // ack 消息消费完成
            val ackOps = redisMQTemplate.opsForStream()
            ackOps.acknowledge(group, message as org.springframework.data.redis.connection.stream.Record<String, *>)
        } finally {
            consumeMessageAfter(messageObj!!)
        }
    }

    /**
     * 处理消息
     */
    abstract fun onMessage(message: T)

    @Suppress("UNCHECKED_CAST")
    private fun getMessageClass(): Class<T>? {
        var clazz: Class<*> = this.javaClass
        while (clazz != Any::class.java) {
            val genericSuper = clazz.genericSuperclass
            if (genericSuper is ParameterizedType &&
                genericSuper.rawType == AbstractRedisStreamMessageListener::class.java
            ) {
                val args = genericSuper.actualTypeArguments
                if (args.isNotEmpty() && args[0] is Class<*>) {
                    return args[0] as Class<T>
                }
            }
            clazz = clazz.superclass
        }
        throw IllegalStateException("类型(${this.javaClass.name}) 需要设置消息类型")
    }

    private fun consumeMessageBefore(message: AbstractRedisMessage) {
        val interceptors: List<RedisMessageInterceptor> = redisMQTemplate.interceptors
        interceptors.forEach { it.consumeMessageBefore(message) }
    }

    private fun consumeMessageAfter(message: AbstractRedisMessage) {
        val interceptors: List<RedisMessageInterceptor> = redisMQTemplate.interceptors
        interceptors.reversed().forEach { it.consumeMessageAfter(message) }
    }
}
