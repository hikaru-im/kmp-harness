package im.hikaru.ruoyi.framework.web.config

import im.hikaru.ruoyi.framework.common.biz.infra.logger.ApiErrorLogCommonApi
import im.hikaru.ruoyi.framework.common.enums.WebFilterOrderEnum
import im.hikaru.ruoyi.framework.web.core.handler.GlobalExceptionHandler
import im.hikaru.ruoyi.framework.web.core.handler.GlobalResponseBodyHandler
import im.hikaru.ruoyi.framework.web.core.filter.CacheRequestBodyFilter
import im.hikaru.ruoyi.framework.web.core.filter.DemoFilter
import im.hikaru.ruoyi.framework.web.core.util.WebFrameworkUtils
import jakarta.servlet.Filter
import org.springframework.beans.factory.annotation.Value
import org.springframework.beans.factory.ObjectProvider
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.boot.webmvc.autoconfigure.WebMvcRegistrations
import org.springframework.context.annotation.Bean
import org.springframework.core.annotation.Order
import org.springframework.util.AntPathMatcher
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.client.RestClient
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import org.springframework.web.filter.CorsFilter
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping
import java.util.function.Predicate

/**
 * Web 配置类 (迁移自 Java, 去 Hutool/Guava)
 *
 * 迁移说明：Hutool StrUtil → Kotlin；Guava Maps.newLinkedHashMapWithExpectedSize → LinkedHashMap；
 * RestTemplate (Spring Boot 4 已弃用) → RestClient。
 *
 * @author 芋道源码
 */
@AutoConfiguration
@EnableConfigurationProperties(WebProperties::class)
class YudaoWebAutoConfiguration {

    @Value("\${spring.application.name}")
    private lateinit var applicationName: String

    @Bean
    fun webMvcRegistrations(webProperties: WebProperties): WebMvcRegistrations = object : WebMvcRegistrations {
        override fun getRequestMappingHandlerMapping(): RequestMappingHandlerMapping {
            val mapping = RequestMappingHandlerMapping()
            // 实例化时就带上前缀
            mapping.setPathPrefixes(buildPathPrefixes(webProperties))
            return mapping
        }

        /**
         * 构建 prefix → 匹配条件的映射
         */
        private fun buildPathPrefixes(webProperties: WebProperties): Map<String, Predicate<Class<*>>> {
            val matcher = AntPathMatcher(".")
            // 替换 Guava Maps.newLinkedHashMapWithExpectedSize(2)
            val pathPrefixes = LinkedHashMap<String, Predicate<Class<*>>>()
            putPathPrefix(pathPrefixes, webProperties.adminApi, matcher)
            putPathPrefix(pathPrefixes, webProperties.appApi, matcher)
            return pathPrefixes
        }

        /**
         * 设置 API 前缀，仅仅匹配 controller 包下的
         */
        private fun putPathPrefix(
            pathPrefixes: MutableMap<String, Predicate<Class<*>>>,
            api: WebProperties.Api,
            matcher: AntPathMatcher,
        ) {
            if (api.prefix.isEmpty()) return
            pathPrefixes[api.prefix] = Predicate { clazz ->
                clazz.isAnnotationPresent(RestController::class.java) &&
                    matcher.match(api.controller, clazz.getPackageName())
            }
        }
    }

    @Bean
    @Suppress("SpringJavaInjectionPointsAutowiringInspection")
    fun globalExceptionHandler(apiErrorLogApi: ApiErrorLogCommonApi): GlobalExceptionHandler =
        GlobalExceptionHandler(applicationName, apiErrorLogApi)

    @Bean
    fun globalResponseBodyHandler(): GlobalResponseBodyHandler = GlobalResponseBodyHandler()

    @Bean
    @Suppress("InstantiationOfUtilityClass")
    fun webFrameworkUtils(webProperties: WebProperties): WebFrameworkUtils =
        // 由于 WebFrameworkUtils 需要使用到 webProperties 属性，所以注册为一个 Bean
        WebFrameworkUtils(webProperties)

    // ========== Filter 相关 ==========

    /**
     * 创建 CorsFilter Bean，解决跨域问题
     */
    @Bean
    @Order(WebFilterOrderEnum.CORS_FILTER)
    fun corsFilterBean(): FilterRegistrationBean<CorsFilter> {
        // 创建 CorsConfiguration 对象
        val config = CorsConfiguration()
        config.allowCredentials = true
        config.addAllowedOriginPattern("*") // 设置访问源地址
        config.addAllowedHeader("*") // 设置访问源请求头
        config.addAllowedMethod("*") // 设置访问源请求方法
        // 创建 UrlBasedCorsConfigurationSource 对象
        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", config) // 对接口配置跨域设置
        return createFilterBean(CorsFilter(source), WebFilterOrderEnum.CORS_FILTER)
    }

    @Bean
    fun requestBodyCacheFilter(): FilterRegistrationBean<CacheRequestBodyFilter> =
        createFilterBean(CacheRequestBodyFilter(), WebFilterOrderEnum.REQUEST_BODY_CACHE_FILTER)

    @Bean
    @ConditionalOnProperty(prefix = "yudao", name = ["demo"], havingValue = "true")
    fun demoFilter(): FilterRegistrationBean<DemoFilter> =
        createFilterBean(DemoFilter(), WebFilterOrderEnum.DEMO_FILTER)

    @Bean
    @ConditionalOnMissingBean
    fun restClient(restClientBuilderProvider: ObjectProvider<RestClient.Builder>): RestClient =
        (restClientBuilderProvider.ifAvailable ?: RestClient.builder()).build()

    companion object {
        fun <T : Filter> createFilterBean(filter: T, order: Int): FilterRegistrationBean<T> {
            val bean = FilterRegistrationBean(filter)
            bean.order = order
            return bean
        }
    }
}
