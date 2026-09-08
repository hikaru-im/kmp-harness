package im.hikaru.ruoyi.framework.encrypt.config

import im.hikaru.ruoyi.framework.common.enums.WebFilterOrderEnum
import im.hikaru.ruoyi.framework.encrypt.core.filter.ApiEncryptFilter
import im.hikaru.ruoyi.framework.web.config.WebProperties
import im.hikaru.ruoyi.framework.web.config.YudaoWebAutoConfiguration
import im.hikaru.ruoyi.framework.web.core.handler.GlobalExceptionHandler
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping

@AutoConfiguration
@EnableConfigurationProperties(ApiEncryptProperties::class)
@ConditionalOnProperty(prefix = "yudao.api-encrypt", name = ["enable"], havingValue = "true")
class YudaoApiEncryptAutoConfiguration {
    @Bean
    fun apiEncryptFilter(
        webProperties: WebProperties,
        apiEncryptProperties: ApiEncryptProperties,
        requestMappingHandlerMapping: RequestMappingHandlerMapping,
        globalExceptionHandler: GlobalExceptionHandler,
    ): FilterRegistrationBean<ApiEncryptFilter> = YudaoWebAutoConfiguration.createFilterBean(
        ApiEncryptFilter(
            webProperties,
            apiEncryptProperties,
            requestMappingHandlerMapping,
            globalExceptionHandler,
        ),
        WebFilterOrderEnum.API_ENCRYPT_FILTER,
    )
}
