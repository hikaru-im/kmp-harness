package im.hikaru.ruoyi.module.mp.framework.web.config

import im.hikaru.ruoyi.framework.swagger.config.YudaoSwaggerAutoConfiguration
import org.springdoc.core.models.GroupedOpenApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class MpWebConfiguration {
    @Bean
    fun mpGroupedOpenApi(): GroupedOpenApi = YudaoSwaggerAutoConfiguration.buildGroupedOpenApi("mp")
}
