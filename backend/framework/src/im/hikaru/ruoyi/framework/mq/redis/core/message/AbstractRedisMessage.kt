package im.hikaru.ruoyi.framework.mq.redis.core.message

/**
 * Redis 消息抽象基类 (迁移自 Java, 去 Lombok)
 *
 * @author 芋道源码
 */
abstract class AbstractRedisMessage {

    /** 头 */
    var headers: MutableMap<String, String> = HashMap()

    fun getHeader(key: String): String? = headers[key]

    fun addHeader(key: String, value: String) {
        headers[key] = value
    }
}
