package im.hikaru.ruoyi.framework.mq.redis.core.pubsub

import im.hikaru.ruoyi.framework.mq.redis.core.message.AbstractRedisMessage
import com.fasterxml.jackson.annotation.JsonIgnore

/**
 * Redis Channel Message 抽象类 (迁移自 Java, 去 Lombok)
 *
 * @author 芋道源码
 */
abstract class AbstractRedisChannelMessage : AbstractRedisMessage() {

    /**
     * 获得 Redis Channel，默认使用类名
     */
    @get:JsonIgnore // 避免序列化
    open val channel: String
        get() = this.javaClass.simpleName
}
