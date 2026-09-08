package im.hikaru.ruoyi.framework.ratelimiter.core.annotation

import im.hikaru.ruoyi.framework.ratelimiter.core.keyresolver.RateLimiterKeyResolver
import im.hikaru.ruoyi.framework.ratelimiter.core.keyresolver.impl.DefaultRateLimiterKeyResolver
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass

/**
 * 限流注解 (迁移自 Java)
 *
 * @author 芋道源码
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class RateLimiter(
    val time: Int = 1,
    val timeUnit: TimeUnit = TimeUnit.SECONDS,
    val count: Int = 100,
    val message: String = "",
    val keyResolver: KClass<out RateLimiterKeyResolver> = DefaultRateLimiterKeyResolver::class,
    val keyArg: String = "",
)
