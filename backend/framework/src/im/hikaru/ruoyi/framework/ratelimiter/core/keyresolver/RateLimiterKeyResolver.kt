package im.hikaru.ruoyi.framework.ratelimiter.core.keyresolver

import im.hikaru.ruoyi.framework.ratelimiter.core.annotation.RateLimiter
import org.aspectj.lang.JoinPoint

/**
 * 限流 Key 解析器接口 (迁移自 Java)
 *
 * @author 芋道源码
 */
interface RateLimiterKeyResolver {
    fun resolver(joinPoint: JoinPoint, rateLimiter: RateLimiter): String
}
