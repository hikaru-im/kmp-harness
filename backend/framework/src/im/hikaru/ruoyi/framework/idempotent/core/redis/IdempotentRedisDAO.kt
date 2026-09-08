package im.hikaru.ruoyi.framework.idempotent.core.redis

import org.springframework.data.redis.core.StringRedisTemplate
import java.util.concurrent.TimeUnit

/**
 * 幂等 Redis DAO (迁移自 Java, 去 Lombok)
 *
 * @author 芋道源码
 */
class IdempotentRedisDAO(
    private val redisTemplate: StringRedisTemplate,
) {
    fun setIfAbsent(key: String, timeout: Long, timeUnit: TimeUnit): Boolean? {
        val redisKey = formatKey(key)
        return redisTemplate.opsForValue().setIfAbsent(redisKey, "", timeout, timeUnit)
    }

    fun delete(key: String) {
        val redisKey = formatKey(key)
        redisTemplate.delete(redisKey)
    }

    companion object {
        private const val IDEMPOTENT = "idempotent:%s"
        private fun formatKey(key: String): String = String.format(IDEMPOTENT, key)
    }
}
