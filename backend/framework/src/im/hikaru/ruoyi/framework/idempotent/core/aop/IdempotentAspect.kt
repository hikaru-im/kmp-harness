package im.hikaru.ruoyi.framework.idempotent.core.aop

import im.hikaru.ruoyi.framework.common.exception.ServiceException
import im.hikaru.ruoyi.framework.common.exception.enums.GlobalErrorCodeConstants
import im.hikaru.ruoyi.framework.common.util.collection.CollectionUtils
import im.hikaru.ruoyi.framework.idempotent.core.annotation.Idempotent
import im.hikaru.ruoyi.framework.idempotent.core.keyresolver.IdempotentKeyResolver
import im.hikaru.ruoyi.framework.idempotent.core.redis.IdempotentRedisDAO
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.slf4j.LoggerFactory
import org.springframework.util.Assert
import kotlin.reflect.KClass

/**
 * 拦截声明了 [Idempotent] 注解的方法，实现幂等操作 (迁移自 Java, 去 Lombok)
 *
 * @author 芋道源码
 */
@Aspect
class IdempotentAspect(
    keyResolvers: List<IdempotentKeyResolver>,
    private val idempotentRedisDAO: IdempotentRedisDAO,
) {
    private val keyResolvers: Map<KClass<out IdempotentKeyResolver>, IdempotentKeyResolver> =
        CollectionUtils.convertMap(keyResolvers) { it.javaClass.kotlin }

    @Around(value = "@annotation(idempotent)")
    @Throws(Throwable::class)
    fun aroundPointCut(joinPoint: ProceedingJoinPoint, idempotent: Idempotent): Any? {
        // 获得 IdempotentKeyResolver
        val keyResolver = keyResolvers[idempotent.keyResolver]
        Assert.notNull(keyResolver, "找不到对应的 IdempotentKeyResolver")
        // 解析 Key
        val key = keyResolver!!.resolver(joinPoint, idempotent)
        // 1. 锁定 Key
        val success = idempotentRedisDAO.setIfAbsent(key, idempotent.timeout.toLong(), idempotent.timeUnit)
        if (success != true) {
            log.info("[aroundPointCut][方法({}) 参数({}) 存在重复请求]", joinPoint.signature.toString(), joinPoint.args)
            throw ServiceException(GlobalErrorCodeConstants.REPEATED_REQUESTS.code, idempotent.message)
        }
        // 2. 执行逻辑
        return try {
            joinPoint.proceed()
        } catch (throwable: Throwable) {
            // 3. 异常时删除 Key
            if (idempotent.deleteKeyWhenException) {
                idempotentRedisDAO.delete(key)
            }
            throw throwable
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(IdempotentAspect::class.java)
    }
}
