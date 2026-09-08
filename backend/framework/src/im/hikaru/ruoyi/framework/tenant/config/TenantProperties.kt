package im.hikaru.ruoyi.framework.tenant.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 多租户配置 (迁移自 Java, 去 Lombok)
 *
 * @author 芋道源码
 */
@ConfigurationProperties(prefix = "yudao.tenant")
class TenantProperties {

    /** 是否开启 (默认 true) */
    var enable: Boolean = ENABLE_DEFAULT

    /** 需要忽略多租户的请求 (如短信/支付回调等 Open API) */
    var ignoreUrls: MutableSet<String> = HashSet()

    /** 需要忽略跨（切换）租户访问的请求 */
    var ignoreVisitUrls: Set<String> = emptySet()

    /** 需要忽略多租户的表 */
    var ignoreTables: Set<String> = emptySet()

    /** 需要忽略多租户的 Spring Cache 缓存 */
    var ignoreCaches: Set<String> = emptySet()

    companion object {
        private const val ENABLE_DEFAULT = true
    }
}
