package im.hikaru.ruoyi.module.infra.framework.web.config

import im.hikaru.ruoyi.framework.swagger.config.YudaoSwaggerAutoConfiguration
import org.springdoc.core.models.GroupedOpenApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/** Infra module OpenAPI grouping. */
@Configuration(proxyBeanMethods = false)
class InfraWebConfiguration {

    @Bean
    fun infraGroupedOpenApi(): GroupedOpenApi =
        YudaoSwaggerAutoConfiguration.buildGroupedOpenApi("infra")
}
