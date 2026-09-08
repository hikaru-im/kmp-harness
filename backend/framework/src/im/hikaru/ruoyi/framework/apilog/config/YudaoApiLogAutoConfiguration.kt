package im.hikaru.ruoyi.framework.apilog.config

import im.hikaru.ruoyi.framework.apilog.core.filter.ApiAccessLogFilter
import im.hikaru.ruoyi.framework.apilog.core.interceptor.ApiAccessLogInterceptor
import im.hikaru.ruoyi.framework.common.biz.infra.logger.ApiAccessLogCommonApi
import im.hikaru.ruoyi.framework.common.enums.WebFilterOrderEnum
import im.hikaru.ruoyi.framework.web.config.WebProperties
import im.hikaru.ruoyi.framework.web.config.YudaoWebAutoConfiguration
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.core.env.Environment
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@AutoConfiguration(after = [YudaoWebAutoConfiguration::class])
class YudaoApiLogAutoConfiguration(
    private val environment: Environment,
) : WebMvcConfigurer {
    @Bean
    @ConditionalOnProperty(prefix = "yudao.access-log", name = ["enable"], matchIfMissing = true)
    fun apiAccessLogFilter(
        webProperties: WebProperties,
        @Value("\${spring.application.name}") applicationName: String,
        apiAccessLogApi: ApiAccessLogCommonApi,
    ): FilterRegistrationBean<ApiAccessLogFilter> = FilterRegistrationBean(
        ApiAccessLogFilter(webProperties, applicationName, apiAccessLogApi),
    ).apply {
        order = WebFilterOrderEnum.API_ACCESS_LOG_FILTER
    }

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(ApiAccessLogInterceptor(environment))
    }
}
