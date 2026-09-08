package im.hikaru.ruoyi.module.infra.framework.security.config

import im.hikaru.ruoyi.framework.security.config.AuthorizeRequestsCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer

/** Infra module public endpoint authorization rules. */
@Configuration(proxyBeanMethods = false, value = "infraSecurityConfiguration")
class SecurityConfiguration {

    @Bean("infraAuthorizeRequestsCustomizer")
    fun authorizeRequestsCustomizer(): AuthorizeRequestsCustomizer =
        object : AuthorizeRequestsCustomizer() {
            override fun customize(
                registry: AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry,
            ) {
                registry.requestMatchers("/v3/api-docs/**").permitAll()
                    .requestMatchers("/webjars/**").permitAll()
                    .requestMatchers("/swagger-ui.html").permitAll()
                    .requestMatchers("/swagger-ui/**").permitAll()
                    .requestMatchers("/actuator").permitAll()
                    .requestMatchers("/actuator/**").permitAll()
                    .requestMatchers("/druid/**").permitAll()
                    .requestMatchers(buildAdminApi("/infra/file/*/get/**")).permitAll()
            }
        }
}
