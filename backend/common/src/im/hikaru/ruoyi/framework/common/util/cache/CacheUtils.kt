package im.hikaru.ruoyi.framework.common.util.cache

import com.github.benmanes.caffeine.cache.CacheLoader
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import java.time.Duration

/**
 * Cache 工具类 (迁移自 Java)
 *
 * 迁移说明 (决策表 #17b): Guava Cache/CacheLoader/LoadingCache → Caffeine
 * Caffeine 是 Spring Boot 默认缓存实现，API 与 Guava 类似但性能更优。
 *
 * @author 芋道源码
 */
object CacheUtils {

    /**
     * 异步刷新的 LoadingCache 最大缓存数量
     */
    private const val CACHE_MAX_SIZE: Long = 10000L

    /**
     * 构建异步刷新的 LoadingCache 对象
     *
     * 注意：如果你的缓存和 ThreadLocal 有关系，要么自己处理 ThreadLocal 的传递，要么使用 [buildCache] 方法
     *
     * @param duration 过期时间
     * @param loader CacheLoader 对象
     * @return LoadingCache 对象
     */
    @JvmStatic
    fun <K : Any, V : Any> buildAsyncReloadingCache(duration: Duration, loader: CacheLoader<K, V>): LoadingCache<K, V> =
        Caffeine.newBuilder()
            .maximumSize(CACHE_MAX_SIZE)
            .refreshAfterWrite(duration)
            .build(loader)

    /**
     * 构建同步刷新的 LoadingCache 对象
     *
     * @param duration 过期时间
     * @param loader CacheLoader 对象
     * @return LoadingCache 对象
     */
    @JvmStatic
    fun <K : Any, V : Any> buildCache(duration: Duration, loader: CacheLoader<K, V>): LoadingCache<K, V> =
        Caffeine.newBuilder()
            .maximumSize(CACHE_MAX_SIZE)
            .refreshAfterWrite(duration)
            .build(loader)
}
