package im.hikaru.ruoyi.framework.tenant.core.service

import im.hikaru.ruoyi.framework.common.biz.system.tenant.TenantCommonApi
import im.hikaru.ruoyi.framework.common.exception.ServiceException
import im.hikaru.ruoyi.framework.common.util.cache.CacheUtils
import com.github.benmanes.caffeine.cache.CacheLoader
import com.github.benmanes.caffeine.cache.LoadingCache
import java.time.Duration

/**
 * Tenant 框架 Service 实现类 (迁移自 Java, 去 Lombok/Guava)
 *
 * 迁移说明：Guava LoadingCache → Caffeine LoadingCache (决策表 #17b)
 *
 * @author 芋道源码
 */
class TenantFrameworkServiceImpl(
    private val tenantApi: TenantCommonApi,
) : TenantFrameworkService {

    /** 针对 [getTenantIds] 的缓存 (1 分钟) */
    private val getTenantIdsCache: LoadingCache<Any, List<Long>> = buildTenantIdsCache()

    /** 针对 [validTenant] 的缓存 (1 分钟)，缓存异常本身以避免重复 RPC */
    private val validTenantCache: LoadingCache<Long, ServiceExceptionWrapper> = buildValidTenantCache()

    override fun getTenantIds(): List<Long> = getTenantIdsCache.get(java.lang.Boolean.TRUE)

    override fun validTenant(id: Long) {
        validTenantCache.get(id)?.exception?.let { throw it }
    }

    private fun buildTenantIdsCache(): LoadingCache<Any, List<Long>> {
        val loader = object : CacheLoader<Any, List<Long>> {
            override fun load(key: Any): List<Long> = tenantApi.getTenantIdList()
        }
        return CacheUtils.buildAsyncReloadingCache<Any, List<Long>>(Duration.ofMinutes(1), loader)
    }

    private fun buildValidTenantCache(): LoadingCache<Long, ServiceExceptionWrapper> {
        val loader = object : CacheLoader<Long, ServiceExceptionWrapper> {
            override fun load(id: Long): ServiceExceptionWrapper = try {
                tenantApi.validateTenant(id)
                ServiceExceptionWrapper(null)
            } catch (ex: ServiceException) {
                ServiceExceptionWrapper(ex)
            }
        }
        return CacheUtils.buildAsyncReloadingCache<Long, ServiceExceptionWrapper>(Duration.ofMinutes(1), loader)
    }

    /** 包装 ServiceException (Caffeine 不支持 nullable value) */
    private class ServiceExceptionWrapper(val exception: ServiceException?)
}
