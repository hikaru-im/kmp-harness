package im.hikaru.ruoyi.framework.redis.config

import im.hikaru.ruoyi.framework.redis.core.TimeoutRedisCacheManager
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.cache.autoconfigure.CacheProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.cache.BatchStrategies
import org.springframework.data.redis.cache.RedisCacheConfiguration
import org.springframework.data.redis.cache.RedisCacheManager
import org.springframework.data.redis.cache.RedisCacheWriter
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.RedisSerializationContext
import org.springframework.util.StringUtils

/**
 * Cache 配置类，基于 Redis 实现 (迁移自 Java, 去 Hutool)
 *
 * 迁移说明：Hutool StrUtil.COLON → Kotlin 字符串常量
 */
@AutoConfiguration
@EnableConfigurationProperties(CacheProperties::class, YudaoCacheProperties::class)
@EnableCaching
class YudaoCacheAutoConfiguration {

    /**
     * RedisCacheConfiguration Bean
     *
     * 参考 org.springframework.boot.autoconfigure.cache.RedisCacheConfiguration 的 createConfiguration 方法
     */
    @Bean
    @Primary
    fun redisCacheConfiguration(cacheProperties: CacheProperties): RedisCacheConfiguration {
        var config = RedisCacheConfiguration.defaultCacheConfig()
        // 设置使用 : 单冒号，而不是双 :: 冒号，避免 Redis Desktop Manager 多余空格
        config = config.computePrefixWith { cacheName: String ->
            var keyPrefix: String? = cacheProperties.redis.keyPrefix
            if (StringUtils.hasText(keyPrefix)) {
                keyPrefix = if (keyPrefix!!.lastIndexOf(COLON) == -1) "$keyPrefix$COLON" else keyPrefix
                return@computePrefixWith keyPrefix + cacheName + COLON
            }
            cacheName + COLON
        }
        // 设置使用 JSON 序列化方式
        config = config.serializeValuesWith(
            RedisSerializationContext.SerializationPair.fromSerializer(YudaoRedisAutoConfiguration.buildRedisSerializer()),
        )

        // 设置 CacheProperties.Redis 的属性
        val redisProperties = cacheProperties.redis
        redisProperties.timeToLive?.let { config = config.entryTtl(it) }
        if (!redisProperties.isCacheNullValues) {
            config = config.disableCachingNullValues()
        }
        if (!redisProperties.isUseKeyPrefix) {
            config = config.disableKeyPrefix()
        }
        return config
    }

    @Bean
    fun redisCacheManager(
        redisTemplate: RedisTemplate<String, Any>,
        redisCacheConfiguration: RedisCacheConfiguration,
        yudaoCacheProperties: YudaoCacheProperties,
    ): RedisCacheManager {
        // 创建 RedisCacheWriter 对象
        val connectionFactory: RedisConnectionFactory = redisTemplate.connectionFactory!!
        val cacheWriter = RedisCacheWriter.nonLockingRedisCacheWriter(
            connectionFactory,
            BatchStrategies.scan(yudaoCacheProperties.redisScanBatchSize),
        )
        // 创建 TimeoutRedisCacheManager 对象
        val cacheManager = TimeoutRedisCacheManager(cacheWriter, redisCacheConfiguration)
        // 开启事务感知：@Transactional 方法内的 @CacheEvict / @CachePut 自动延迟到 afterCommit，
        // 避免事务未提交就清缓存被并发读穿写脏值；无事务时立即生效，行为不变
        cacheManager.isTransactionAware = true
        return cacheManager
    }

    companion object {
        private const val COLON = ":"
    }
}
