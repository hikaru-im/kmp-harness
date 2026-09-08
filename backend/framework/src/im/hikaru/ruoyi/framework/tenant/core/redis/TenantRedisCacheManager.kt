package im.hikaru.ruoyi.framework.tenant.core.redis

import im.hikaru.ruoyi.framework.redis.core.TimeoutRedisCacheManager
import im.hikaru.ruoyi.framework.tenant.core.context.TenantContextHolder
import org.springframework.cache.Cache

/**
 * 多租户的 RedisCacheManager (迁移自 Java, 去 Lombok/Hutool)
 *
 * 迁移说明：原 Java 版 extends TimeoutRedisCacheManager。但 TimeoutRedisCacheManager 在 Kotlin 中是 final
 * (Kotlin class 默认 final, 且未加 open)。改为组合模式: 包装 delegate, 在 getCache 时追加租户后缀。
 *
 * @author airhead
 */
class TenantRedisCacheManager(
    private val delegate: TimeoutRedisCacheManager,
    private val ignoreCaches: Set<String>,
) : org.springframework.cache.support.SimpleCacheManager() {

    override fun getCache(name: String): Cache {
        var cacheName = name
        val names = name.split(SPLIT)
        if (!TenantContextHolder.isIgnore()
            && TenantContextHolder.getTenantId() != null
            && names[0] !in ignoreCaches
        ) {
            cacheName = name + ":" + TenantContextHolder.getTenantId()
        }
        return delegate.getCache(cacheName)!!
    }

    companion object {
        private const val SPLIT = "#"
    }
}
