package im.hikaru.ruoyi.framework.mq.redis.core.pubsub

import im.hikaru.ruoyi.framework.common.util.json.JsonUtils
import im.hikaru.ruoyi.framework.mq.redis.core.RedisMQTemplate
import im.hikaru.ruoyi.framework.mq.redis.core.interceptor.RedisMessageInterceptor
import im.hikaru.ruoyi.framework.mq.redis.core.message.AbstractRedisMessage
import org.springframework.data.redis.connection.Message
import org.springframework.data.redis.connection.MessageListener
import java.lang.reflect.ParameterizedType

/**
 * Redis Pub/Sub 监听器抽象类，用于实现广播消费 (迁移自 Java, 去 Lombok/Hutool)
 *
 * @param T 消息类型。一定要填写噢，不然会报错
 *
 * 迁移说明：Hutool TypeUtil.getTypeArgument → JDK ParameterizedType 反射
 *
 * @author 芋道源码
 */
abstract class AbstractRedisChannelMessageListener<T : AbstractRedisChannelMessage> : MessageListener {

    /** 消息类型 */
    private val messageType: Class<T>
    /** Redis Channel */
    private val channel: String
    /** RedisMQTemplate */
    lateinit var redisMQTemplate: RedisMQTemplate

    init {
        this.messageType = getMessageClass()
        this.channel = messageType.getDeclaredConstructor().newInstance().channel
    }

    /**
     * 获得 Sub 订阅的 Redis Channel 通道
     */
    fun getChannel(): String = channel

    override fun onMessage(message: Message, pattern: ByteArray?) {
        val messageObj = JsonUtils.parseObject(message.body, messageType)
        try {
            consumeMessageBefore(messageObj!!)
            // 消费消息
            this.onMessage(messageObj)
        } finally {
            consumeMessageAfter(messageObj!!)
        }
    }

    /**
     * 处理消息
     */
    abstract fun onMessage(message: T)

    /**
     * 通过解析类上的泛型，获得消息类型 (替代 Hutool TypeUtil)
     */
    @Suppress("UNCHECKED_CAST")
    private fun getMessageClass(): Class<T> {
        // 找到 AbstractRedisChannelMessageListener<T> 的 ParameterizedType
        var clazz: Class<*> = this.javaClass
        while (clazz != Any::class.java) {
            val genericSuper = clazz.genericSuperclass
            if (genericSuper is ParameterizedType) {
                val rawType = genericSuper.rawType
                // 匹配 AbstractRedisChannelMessageListener 本身
                if (rawType == AbstractRedisChannelMessageListener::class.java) {
                    val typeArg = genericTypeArgument(genericSuper)
                    if (typeArg != null) {
                        return typeArg as Class<T>
                    }
                }
            }
            // 检查实现的接口
            for (genericInterface in clazz.genericInterfaces) {
                if (genericInterface is ParameterizedType &&
                    genericInterface.rawType == AbstractRedisChannelMessageListener::class.java
                ) {
                    val typeArg = genericTypeArgument(genericInterface)
                    if (typeArg != null) {
                        return typeArg as Class<T>
                    }
                }
            }
            clazz = clazz.superclass
        }
        throw IllegalStateException("类型(${this.javaClass.name}) 需要设置消息类型")
    }

    private fun genericTypeArgument(type: ParameterizedType): Class<*>? {
        val args = type.actualTypeArguments
        if (args.isNotEmpty() && args[0] is Class<*>) {
            return args[0] as Class<*>
        }
        return null
    }

    private fun consumeMessageBefore(message: AbstractRedisMessage) {
        val interceptors: List<RedisMessageInterceptor> = redisMQTemplate.interceptors
        // 正序
        interceptors.forEach { it.consumeMessageBefore(message) }
    }

    private fun consumeMessageAfter(message: AbstractRedisMessage) {
        val interceptors: List<RedisMessageInterceptor> = redisMQTemplate.interceptors
        // 倒序
        interceptors.reversed().forEach { it.consumeMessageAfter(message) }
    }
}
