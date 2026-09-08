package im.hikaru.ruoyi.framework.ratelimiter.core.redis

import org.redisson.api.RateIntervalUnit
import org.redisson.api.RateLimiterConfig
import org.redisson.api.RateType
import org.redisson.api.RRateLimiter
import org.redisson.api.RedissonClient
import java.time.Duration
import java.util.Objects
import java.util.concurrent.TimeUnit

/**
 * 限流 Redis DAO (迁移自 Java, 去 Lombok)
 *
 * @author 芋道源码
 */
class RateLimiterRedisDAO(
    private val redissonClient: RedissonClient,
) {
    fun tryAcquire(key: String, count: Int, time: Int, timeUnit: TimeUnit): Boolean {
        val rateLimiter = getRRateLimiter(key, count.toLong(), time, timeUnit)
        return rateLimiter.tryAcquire()
    }

    private fun getRRateLimiter(key: String, count: Long, time: Int, timeUnit: TimeUnit): RRateLimiter {
        val redisKey = formatKey(key)
        val rateLimiter = redissonClient.getRateLimiter(redisKey)
        val rateInterval = timeUnit.toSeconds(time.toLong())
        val duration = Duration.ofSeconds(rateInterval)
        // 1. 如果不存在，设置 rate 速率
        val config: RateLimiterConfig? = rateLimiter.config
        if (config == null) {
            rateLimiter.trySetRate(RateType.OVERALL, count, duration)
            rateLimiter.expire(duration)
            return rateLimiter
        }
        // 2. 如果存在，并且配置相同，则直接返回
        if (config.rateType == RateType.OVERALL &&
            Objects.equals(config.rate, count) &&
            Objects.equals(config.rateInterval, TimeUnit.SECONDS.toMillis(rateInterval))
        ) {
            return rateLimiter
        }
        // 3. 如果存在，并且配置不同，则进行新建
        rateLimiter.setRate(RateType.OVERALL, count, duration)
        rateLimiter.expire(duration)
        return rateLimiter
    }

    companion object {
        private const val RATE_LIMITER = "rate_limiter:%s"
        private fun formatKey(key: String): String = String.format(RATE_LIMITER, key)
    }
}
