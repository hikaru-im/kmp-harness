package im.hikaru.ruoyi.framework.mq.redis.core.stream

import im.hikaru.ruoyi.framework.mq.redis.core.message.AbstractRedisMessage
import com.fasterxml.jackson.annotation.JsonIgnore

/**
 * Redis Stream Message 抽象类 (迁移自 Java, 去 Lombok)
 *
 * @author 芋道源码
 */
abstract class AbstractRedisStreamMessage : AbstractRedisMessage() {

    /**
     * 获得 Redis Stream Key，默认使用类名
     */
    @get:JsonIgnore // 避免序列化
    open val streamKey: String
        get() = this.javaClass.simpleName
}
