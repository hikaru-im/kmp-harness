package im.hikaru.ruoyi.framework.mq.redis.config

import im.hikaru.ruoyi.framework.mq.redis.core.RedisMQTemplate
import im.hikaru.ruoyi.framework.mq.redis.core.interceptor.RedisMessageInterceptor
import im.hikaru.ruoyi.framework.redis.config.YudaoRedisAutoConfiguration
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.data.redis.core.StringRedisTemplate

/**
 * Redis 消息队列 Producer 配置类 (迁移自 Java, 去 Lombok)
 *
 * @author 芋道源码
 */
@AutoConfiguration(after = [YudaoRedisAutoConfiguration::class])
class YudaoRedisMQProducerAutoConfiguration {

    @Bean
    fun redisMQTemplate(
        redisTemplate: StringRedisTemplate,
        interceptors: List<RedisMessageInterceptor>,
    ): RedisMQTemplate {
        val redisMQTemplate = RedisMQTemplate(redisTemplate)
        // 添加拦截器
        interceptors.forEach { redisMQTemplate.addInterceptor(it) }
        return redisMQTemplate
    }

    companion object {
        private val log = LoggerFactory.getLogger(YudaoRedisMQProducerAutoConfiguration::class.java)
    }
}
