package im.hikaru.ruoyi.framework.tenant.core.job

import im.hikaru.ruoyi.framework.common.util.json.JsonUtils
import im.hikaru.ruoyi.framework.tenant.core.service.TenantFrameworkService
import im.hikaru.ruoyi.framework.tenant.core.util.TenantUtils
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * 多租户 JobHandler AOP (迁移自 Java, 去 Lombok/Hutool)
 *
 * @author 芋道源码
 */
@Aspect
class TenantJobAspect(
    private val tenantFrameworkService: TenantFrameworkService,
) {

    @Around("@annotation(tenantJob)")
    fun around(joinPoint: ProceedingJoinPoint, tenantJob: TenantJob): String? {
        val tenantIds = tenantFrameworkService.getTenantIds()
        if (tenantIds.isEmpty()) {
            return null
        }
        val results = ConcurrentHashMap<Long, String>()
        tenantIds.parallelStream().forEach { tenantId ->
            TenantUtils.execute(tenantId) {
                try {
                    val result = joinPoint.proceed()
                    results[tenantId] = result?.toString() ?: ""
                } catch (e: Throwable) {
                    log.error("[execute][租户({}) 执行 Job 发生异常", tenantId, e)
                    results[tenantId] = rootCauseMessage(e)
                }
            }
        }
        return JsonUtils.toJsonString(results)
    }

    private fun rootCauseMessage(e: Throwable): String {
        var cur = e
        while (cur.cause != null && cur.cause !== cur) cur = cur.cause!!
        return cur.message ?: ""
    }

    companion object {
        private val log = LoggerFactory.getLogger(TenantJobAspect::class.java)
    }
}
