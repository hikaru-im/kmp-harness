package im.hikaru.ruoyi.framework.idempotent.config

import im.hikaru.ruoyi.framework.idempotent.core.aop.IdempotentAspect
import im.hikaru.ruoyi.framework.idempotent.core.keyresolver.IdempotentKeyResolver
import im.hikaru.ruoyi.framework.idempotent.core.keyresolver.impl.DefaultIdempotentKeyResolver
import im.hikaru.ruoyi.framework.idempotent.core.keyresolver.impl.ExpressionIdempotentKeyResolver
import im.hikaru.ruoyi.framework.idempotent.core.keyresolver.impl.UserIdempotentKeyResolver
import im.hikaru.ruoyi.framework.idempotent.core.redis.IdempotentRedisDAO
import im.hikaru.ruoyi.framework.redis.config.YudaoRedisAutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.data.redis.core.StringRedisTemplate

@AutoConfiguration(after = [YudaoRedisAutoConfiguration::class])
class YudaoIdempotentConfiguration {

    @Bean
    fun idempotentAspect(keyResolvers: List<IdempotentKeyResolver>, idempotentRedisDAO: IdempotentRedisDAO): IdempotentAspect =
        IdempotentAspect(keyResolvers, idempotentRedisDAO)

    @Bean
    fun idempotentRedisDAO(stringRedisTemplate: StringRedisTemplate): IdempotentRedisDAO =
        IdempotentRedisDAO(stringRedisTemplate)

    @Bean
    fun defaultIdempotentKeyResolver(): DefaultIdempotentKeyResolver = DefaultIdempotentKeyResolver()

    @Bean
    fun userIdempotentKeyResolver(): UserIdempotentKeyResolver = UserIdempotentKeyResolver()

    @Bean
    fun expressionIdempotentKeyResolver(): ExpressionIdempotentKeyResolver = ExpressionIdempotentKeyResolver()
}
