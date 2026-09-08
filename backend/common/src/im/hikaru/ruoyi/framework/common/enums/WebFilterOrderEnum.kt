package im.hikaru.ruoyi.framework.common.enums

/**
 * Web 过滤器顺序的枚举类，保证过滤器按照符合我们的预期
 *
 * 考虑到每个 starter 都需要用到该工具类，所以放到 common 模块下的 enums 包下
 *
 * @author 芋道源码
 */
object WebFilterOrderEnum {
    const val CORS_FILTER = Int.MIN_VALUE

    const val TRACE_FILTER = CORS_FILTER + 1

    const val REQUEST_BODY_CACHE_FILTER = Int.MIN_VALUE + 500

    const val API_ENCRYPT_FILTER = REQUEST_BODY_CACHE_FILTER + 1

    // OrderedRequestContextFilter 默认为 -105，用于国际化上下文等等
    const val TENANT_CONTEXT_FILTER = -104 // 需要保证在 ApiAccessLogFilter 前面

    const val API_ACCESS_LOG_FILTER = -103 // 需要保证在 RequestBodyCacheFilter 后面

    const val XSS_FILTER = -102 // 需要保证在 RequestBodyCacheFilter 后面

    // Spring Security Filter 默认为 -100，可见 org.springframework.boot.autoconfigure.security.SecurityProperties 配置属性类
    const val TENANT_SECURITY_FILTER = -99 // 需要保证在 Spring Security 过滤器后面

    const val FLOWABLE_FILTER = -98 // 需要保证在 Spring Security 过滤后面

    const val DEMO_FILTER = Int.MAX_VALUE
}
