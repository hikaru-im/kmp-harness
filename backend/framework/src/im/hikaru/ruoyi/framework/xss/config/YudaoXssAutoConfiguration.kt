package im.hikaru.ruoyi.framework.xss.config

import im.hikaru.ruoyi.framework.common.enums.WebFilterOrderEnum
import im.hikaru.ruoyi.framework.web.config.YudaoWebAutoConfiguration
import im.hikaru.ruoyi.framework.xss.core.clean.JsoupXssCleaner
import im.hikaru.ruoyi.framework.xss.core.clean.XssCleaner
import im.hikaru.ruoyi.framework.xss.core.filter.XssFilter
import im.hikaru.ruoyi.framework.xss.core.json.XssStringJsonDeserializer
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.util.PathMatcher
import tools.jackson.databind.module.SimpleModule

@AutoConfiguration
@EnableConfigurationProperties(XssProperties::class)
@ConditionalOnProperty(prefix = "yudao.xss", name = ["enable"], havingValue = "true", matchIfMissing = true)
class YudaoXssAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(XssCleaner::class)
    fun xssCleaner(): XssCleaner = JsoupXssCleaner()

    @Bean
    @ConditionalOnMissingBean(name = ["xssJacksonCustomizer"])
    fun xssJacksonCustomizer(
        properties: XssProperties,
        pathMatcher: PathMatcher,
        xssCleaner: XssCleaner,
    ): JsonMapperBuilderCustomizer = JsonMapperBuilderCustomizer { builder ->
        builder.addModule(
            SimpleModule("XssStringModule")
                .addDeserializer(
                    String::class.java,
                    XssStringJsonDeserializer(properties, pathMatcher, xssCleaner),
                ),
        )
    }

    @Bean
    @ConditionalOnBean(XssCleaner::class)
    fun xssFilter(
        properties: XssProperties,
        pathMatcher: PathMatcher,
        xssCleaner: XssCleaner,
    ): FilterRegistrationBean<XssFilter> = YudaoWebAutoConfiguration.createFilterBean(
        XssFilter(properties, pathMatcher, xssCleaner),
        WebFilterOrderEnum.XSS_FILTER,
    )
}
