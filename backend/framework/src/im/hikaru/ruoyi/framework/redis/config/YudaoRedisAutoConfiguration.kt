package im.hikaru.ruoyi.framework.redis.config

import im.hikaru.ruoyi.framework.jackson.config.YudaoJacksonAutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer
import org.springframework.data.redis.serializer.RedisSerializer

/**
 * Redis 配置类 (迁移自 Java, Redisson/Spring Data Redis 保留, 去 Lombok)
 *
 * 目的：使用自定义的 RedisTemplate Bean，先于 Redisson 自动配置生效。
 */
@AutoConfiguration(before = [org.redisson.spring.starter.RedissonAutoConfigurationV4::class])
class YudaoRedisAutoConfiguration {

    /**
     * 创建 RedisTemplate Bean，使用 JSON 序列化方式
     */
    @Bean
    fun redisTemplate(factory: RedisConnectionFactory): RedisTemplate<String, Any> {
        // 创建 RedisTemplate 对象
        val template = RedisTemplate<String, Any>()
        // 设置 RedisConnection 工厂
        template.setConnectionFactory(factory)
        // 使用 String 序列化方式，序列化 KEY
        template.keySerializer = RedisSerializer.string()
        template.hashKeySerializer = RedisSerializer.string()
        // 使用 JSON 序列化方式，序列化 VALUE
        val redisSerializer = buildRedisSerializer()
        template.valueSerializer = redisSerializer
        template.hashValueSerializer = redisSerializer
        return template
    }

    companion object {
        /**
         * 构建 Redis 值序列化器。
         *
         * 特殊：Spring Boot 4.x 无需解决 LocalDateTime 的序列化
         * 原因：Spring Data Redis 4 使用 Jackson 3，RedisSerializer.json() 已支持 Java Time 类型
         */
        @JvmStatic
        fun buildRedisSerializer(): RedisSerializer<Any> =
            GenericJacksonJsonRedisSerializer.create { builder ->
                builder
                .enableSpringCacheNullValueSupport()
                .enableUnsafeDefaultTyping()
                    .customize { it.addModule(YudaoJacksonAutoConfiguration.timestampSupportModule()) }
            }
    }
}
