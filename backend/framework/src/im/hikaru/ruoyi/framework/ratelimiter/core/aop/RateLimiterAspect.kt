package im.hikaru.ruoyi.framework.ratelimiter.core.aop

import im.hikaru.ruoyi.framework.common.exception.ServiceException
import im.hikaru.ruoyi.framework.common.exception.enums.GlobalErrorCodeConstants
import im.hikaru.ruoyi.framework.common.util.collection.CollectionUtils
import im.hikaru.ruoyi.framework.ratelimiter.core.annotation.RateLimiter
import im.hikaru.ruoyi.framework.ratelimiter.core.keyresolver.RateLimiterKeyResolver
import im.hikaru.ruoyi.framework.ratelimiter.core.redis.RateLimiterRedisDAO
import org.aspectj.lang.JoinPoint
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Before
import org.slf4j.LoggerFactory
import org.springframework.util.Assert
import kotlin.reflect.KClass

/**
 * 拦截声明了 [RateLimiter] 注解的方法，实现限流操作 (迁移自 Java, 去 Lombok/Hutool)
 *
 * @author 芋道源码
 */
@Aspect
class RateLimiterAspect(
    keyResolvers: List<RateLimiterKeyResolver>,
    private val rateLimiterRedisDAO: RateLimiterRedisDAO,
) {
    private val keyResolvers: Map<KClass<out RateLimiterKeyResolver>, RateLimiterKeyResolver> =
        CollectionUtils.convertMap(keyResolvers) { it.javaClass.kotlin }

    @Before(value = "@annotation(rateLimiter)")
    fun beforePointCut(joinPoint: JoinPoint, rateLimiter: RateLimiter) {
        val keyResolver = keyResolvers[rateLimiter.keyResolver]
        Assert.notNull(keyResolver, "找不到对应的 RateLimiterKeyResolver")
        val key = keyResolver!!.resolver(joinPoint, rateLimiter)
        val success = rateLimiterRedisDAO.tryAcquire(key, rateLimiter.count, rateLimiter.time, rateLimiter.timeUnit)
        if (!success) {
            log.info("[beforePointCut][方法({}) 参数({}) 请求过于频繁]", joinPoint.signature.toString(), joinPoint.args)
            val message = rateLimiter.message.ifBlank { GlobalErrorCodeConstants.TOO_MANY_REQUESTS.msg }
            throw ServiceException(GlobalErrorCodeConstants.TOO_MANY_REQUESTS.code, message)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(RateLimiterAspect::class.java)
    }
}
