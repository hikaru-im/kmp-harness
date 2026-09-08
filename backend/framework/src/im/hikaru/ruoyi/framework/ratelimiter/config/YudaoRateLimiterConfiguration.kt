package im.hikaru.ruoyi.framework.ratelimiter.config

import im.hikaru.ruoyi.framework.ratelimiter.core.aop.RateLimiterAspect
import im.hikaru.ruoyi.framework.ratelimiter.core.keyresolver.RateLimiterKeyResolver
import im.hikaru.ruoyi.framework.ratelimiter.core.keyresolver.impl.ClientIpRateLimiterKeyResolver
import im.hikaru.ruoyi.framework.ratelimiter.core.keyresolver.impl.DefaultRateLimiterKeyResolver
import im.hikaru.ruoyi.framework.ratelimiter.core.keyresolver.impl.ExpressionRateLimiterKeyResolver
import im.hikaru.ruoyi.framework.ratelimiter.core.keyresolver.impl.ServerNodeRateLimiterKeyResolver
import im.hikaru.ruoyi.framework.ratelimiter.core.keyresolver.impl.UserRateLimiterKeyResolver
import im.hikaru.ruoyi.framework.ratelimiter.core.redis.RateLimiterRedisDAO
import im.hikaru.ruoyi.framework.redis.config.YudaoRedisAutoConfiguration
import org.redisson.api.RedissonClient
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.context.annotation.Bean

@AutoConfiguration(after = [YudaoRedisAutoConfiguration::class])
class YudaoRateLimiterConfiguration {

    @Bean
    fun rateLimiterAspect(keyResolvers: List<RateLimiterKeyResolver>, rateLimiterRedisDAO: RateLimiterRedisDAO): RateLimiterAspect =
        RateLimiterAspect(keyResolvers, rateLimiterRedisDAO)

    @Bean
    fun rateLimiterRedisDAO(redissonClient: RedissonClient): RateLimiterRedisDAO =
        RateLimiterRedisDAO(redissonClient)

    @Bean
    fun defaultRateLimiterKeyResolver(): DefaultRateLimiterKeyResolver = DefaultRateLimiterKeyResolver()

    @Bean
    fun userRateLimiterKeyResolver(): UserRateLimiterKeyResolver = UserRateLimiterKeyResolver()

    @Bean
    fun clientIpRateLimiterKeyResolver(): ClientIpRateLimiterKeyResolver = ClientIpRateLimiterKeyResolver()

    @Bean
    fun serverNodeRateLimiterKeyResolver(): ServerNodeRateLimiterKeyResolver = ServerNodeRateLimiterKeyResolver()

    @Bean
    fun expressionRateLimiterKeyResolver(): ExpressionRateLimiterKeyResolver = ExpressionRateLimiterKeyResolver()
}
