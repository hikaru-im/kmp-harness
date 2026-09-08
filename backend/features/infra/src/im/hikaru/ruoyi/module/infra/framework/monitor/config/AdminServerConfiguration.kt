package im.hikaru.ruoyi.module.infra.framework.monitor.config

import de.codecentric.boot.admin.server.config.EnableAdminServer
import jakarta.servlet.DispatcherType
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.annotation.Order
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.core.userdetails.User
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.provisioning.InMemoryUserDetailsManager
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler
import org.springframework.security.web.csrf.CookieCsrfTokenRepository

@Configuration(proxyBeanMethods = false)
@EnableAdminServer
@ConditionalOnClass(name = ["de.codecentric.boot.admin.server.config.AdminServerProperties"])
class AdminServerConfiguration {
    @Value("\${spring.boot.admin.context-path:/admin}")
    private lateinit var adminServerContextPath: String

    @Value("\${spring.boot.admin.client.username:admin}")
    private lateinit var username: String

    @Value("\${spring.boot.admin.client.password:admin}")
    private lateinit var password: String

    @Value("\${spring.boot.admin.frame-ancestors:'self'}")
    private lateinit var frameAncestors: String

    @Bean("adminUserDetailsManager")
    fun adminUserDetailsManager(passwordEncoder: PasswordEncoder): InMemoryUserDetailsManager {
        val admin = User.builder()
            .username(username)
            .password(passwordEncoder.encode(password))
            .roles("ADMIN_SERVER")
            .build()
        return InMemoryUserDetailsManager(admin)
    }

    @Bean("adminServerSecurityFilterChain")
    @Order(1)
    fun adminServerSecurityFilterChain(
        http: HttpSecurity,
        adminUserDetailsManager: InMemoryUserDetailsManager,
    ): SecurityFilterChain {
        val successHandler = SavedRequestAwareAuthenticationSuccessHandler().apply {
            setTargetUrlParameter("redirectTo")
            setDefaultTargetUrl("$adminServerContextPath/")
        }

        http
            .securityMatcher("$adminServerContextPath/**")
            .userDetailsService(adminUserDetailsManager)
            .authorizeHttpRequests { requests ->
                requests
                    .requestMatchers("$adminServerContextPath/assets/**").permitAll()
                    .requestMatchers("$adminServerContextPath/login").permitAll()
                    .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                    .anyRequest().authenticated()
            }
            .formLogin { form ->
                form.loginPage("$adminServerContextPath/login")
                    .successHandler(successHandler)
                    .permitAll()
            }
            .logout { logout ->
                logout.logoutUrl("$adminServerContextPath/logout")
                    .logoutSuccessUrl("$adminServerContextPath/login")
            }
            .httpBasic(Customizer.withDefaults())
            .csrf { csrf ->
                csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .ignoringRequestMatchers(
                        "$adminServerContextPath/instances",
                        "$adminServerContextPath/actuator/**",
                    )
            }
            .headers { headers ->
                headers
                    .contentSecurityPolicy { policy ->
                        policy.policyDirectives(
                            "default-src 'self'; " +
                                "script-src 'self' 'unsafe-inline' 'unsafe-eval'; " +
                                "style-src 'self' 'unsafe-inline'; " +
                                "frame-ancestors $frameAncestors",
                        )
                    }
                    .frameOptions { it.sameOrigin() }
                    .cacheControl { it.disable() }
            }
        return http.build()
    }
}
