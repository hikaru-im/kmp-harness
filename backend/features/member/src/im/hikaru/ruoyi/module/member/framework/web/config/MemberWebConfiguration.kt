package im.hikaru.ruoyi.module.member.framework.web.config

import im.hikaru.ruoyi.framework.swagger.config.YudaoSwaggerAutoConfiguration
import org.springdoc.core.models.GroupedOpenApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class MemberWebConfiguration {
    @Bean
    fun memberGroupedOpenApi(): GroupedOpenApi =
        YudaoSwaggerAutoConfiguration.buildGroupedOpenApi("member")
}
