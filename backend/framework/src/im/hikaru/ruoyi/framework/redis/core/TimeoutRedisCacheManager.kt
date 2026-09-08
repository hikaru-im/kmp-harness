package im.hikaru.ruoyi.framework.redis.core

import org.springframework.cache.annotation.Cacheable
import org.springframework.data.redis.cache.RedisCache
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.cache.RedisCacheWriter
import java.time.Duration

/**
 * 支持自定义过期时间的 [RedisCacheManager] 实现类
 *
 * 在 [Cacheable.cacheNames] 格式为 "key#ttl" 时，# 后面的 ttl 为过期时间。
 * 单位为最后一个字母（支持的单位有：d 天，h 小时，m 分钟，s 秒），默认单位为 s 秒
 *
 * 迁移说明：Hutool StrUtil/NumberUtil → Kotlin stdlib。
 *
 * @author 芋道源码
 */
class TimeoutRedisCacheManager(
    cacheWriter: RedisCacheWriter,
    defaultCacheConfiguration: RedisCacheConfiguration,
) : RedisCacheManager(cacheWriter, defaultCacheConfiguration) {

    override fun createRedisCache(name: String, cacheConfig: RedisCacheConfiguration?): RedisCache {
        if (name.isEmpty()) {
            return super.createRedisCache(name, cacheConfig)
        }
        // 如果使用 # 分隔，大小不为 2，则说明不使用自定义过期时间
        val names = name.split(SPLIT)
        if (names.size != 2) {
            return super.createRedisCache(name, cacheConfig)
        }

        // 核心：通过修改 cacheConfig 的过期时间，实现自定义过期时间
        var newConfig = cacheConfig
        if (newConfig != null) {
            // 移除 # 后面的 : 以及后面的内容，避免影响解析
            val ttlStr = names[1].substringBefore(COLON) // 获得 ttlStr 时间部分
            val suffix = names[1].substringAfter(ttlStr, "") // 移除掉 ttlStr 时间部分后剩余
            // 解析时间
            val duration = parseDuration(ttlStr)
            newConfig = newConfig.entryTtl(duration)
            // 创建 RedisCache 对象，需要忽略掉 ttlStr
            return super.createRedisCache(names[0] + suffix, newConfig)
        }
        return super.createRedisCache(name, cacheConfig)
    }

    /**
     * 解析过期时间 Duration
     *
     * @param ttlStr 过期时间字符串
     * @return 过期时间 Duration
     */
    private fun parseDuration(ttlStr: String): Duration {
        val timeUnit = ttlStr.takeLast(1)
        return when (timeUnit) {
            "d" -> Duration.ofDays(removeDurationSuffix(ttlStr))
            "h" -> Duration.ofHours(removeDurationSuffix(ttlStr))
            "m" -> Duration.ofMinutes(removeDurationSuffix(ttlStr))
            "s" -> Duration.ofSeconds(removeDurationSuffix(ttlStr))
            else -> Duration.ofSeconds(ttlStr.toLong())
        }
    }

    /**
     * 移除多余的后缀，返回具体的时间
     */
    private fun removeDurationSuffix(ttlStr: String): Long =
        ttlStr.dropLast(1).toLong()

    companion object {
        private const val SPLIT = "#"
        private const val COLON = ":"
    }
}
