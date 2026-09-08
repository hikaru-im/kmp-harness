package im.hikaru.ruoyi.framework.security.config

import im.hikaru.ruoyi.framework.security.core.filter.TokenAuthenticationFilter
import jakarta.annotation.security.PermitAll
import jakarta.servlet.DispatcherType
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureOrder
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.http.HttpMethod
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.mvc.method.RequestMappingInfo
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping

/** Configures the stateless token-based security chain shared by all modules. */
@AutoConfiguration
@AutoConfigureOrder(-1)
@EnableMethodSecurity(securedEnabled = true)
class YudaoWebSecurityConfigurerAdapter(
    private val securityProperties: SecurityProperties,
    private val authenticationEntryPoint: AuthenticationEntryPoint,
    private val accessDeniedHandler: AccessDeniedHandler,
    private val authenticationTokenFilter: TokenAuthenticationFilter,
    private val authorizeRequestsCustomizers: List<AuthorizeRequestsCustomizer>,
    private val applicationContext: ApplicationContext,
) {

    @Bean
    fun authenticationManager(authenticationConfiguration: AuthenticationConfiguration): AuthenticationManager =
        authenticationConfiguration.authenticationManager

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .cors(Customizer.withDefaults())
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .headers { headers -> headers.frameOptions { it.disable() } }
            .exceptionHandling { exceptions ->
                exceptions
                    .authenticationEntryPoint(authenticationEntryPoint)
                    .accessDeniedHandler(accessDeniedHandler)
            }

        val permitAllUrls = getPermitAllUrlsFromAnnotations()
        http.authorizeHttpRequests { requests ->
            requests.requestMatchers(HttpMethod.GET, "/*.html", "/*.css", "/*.js").permitAll()
            permitAllUrls.forEach { (method, urls) ->
                if (urls.isNotEmpty()) {
                    requests.requestMatchers(method, *urls.toTypedArray()).permitAll()
                }
            }
            if (securityProperties.permitAllUrls.isNotEmpty()) {
                requests.requestMatchers(*securityProperties.permitAllUrls.toTypedArray()).permitAll()
            }
        }
        authorizeRequestsCustomizers.sortedBy(AuthorizeRequestsCustomizer::getOrder).forEach { customizer ->
            http.authorizeHttpRequests(customizer)
        }
        http.authorizeHttpRequests { requests ->
            requests.dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                .anyRequest().authenticated()
        }
        http.addFilterBefore(authenticationTokenFilter, UsernamePasswordAuthenticationFilter::class.java)
        return http.build()
    }

    private fun getPermitAllUrlsFromAnnotations(): Map<HttpMethod, Set<String>> {
        val result = linkedMapOf<HttpMethod, MutableSet<String>>()
        val mapping = applicationContext.getBean(
            "requestMappingHandlerMapping",
            RequestMappingHandlerMapping::class.java,
        )
        mapping.handlerMethods.forEach { (requestMappingInfo, handlerMethod) ->
            if (!handlerMethod.hasMethodAnnotation(PermitAll::class.java) &&
                !handlerMethod.beanType.isAnnotationPresent(PermitAll::class.java)
            ) {
                return@forEach
            }
            val urls = getUrls(requestMappingInfo)
            if (urls.isEmpty()) return@forEach

            val requestMethods = requestMappingInfo.methodsCondition.methods
            if (requestMethods.isEmpty()) {
                SUPPORTED_METHODS.forEach { method -> result.getOrPut(method) { linkedSetOf() }.addAll(urls) }
            } else {
                requestMethods.forEach { requestMethod ->
                    requestMethod.toHttpMethod()?.let { method ->
                        result.getOrPut(method) { linkedSetOf() }.addAll(urls)
                    }
                }
            }
        }
        return result
    }

    @Suppress("DEPRECATION")
    private fun getUrls(requestMappingInfo: RequestMappingInfo): Set<String> = buildSet {
        requestMappingInfo.patternsCondition?.patterns?.let(::addAll)
        requestMappingInfo.pathPatternsCondition?.patterns?.mapTo(this) { it.patternString }
    }

    private fun RequestMethod.toHttpMethod(): HttpMethod? = when (this) {
        RequestMethod.GET -> HttpMethod.GET
        RequestMethod.POST -> HttpMethod.POST
        RequestMethod.PUT -> HttpMethod.PUT
        RequestMethod.DELETE -> HttpMethod.DELETE
        RequestMethod.HEAD -> HttpMethod.HEAD
        RequestMethod.PATCH -> HttpMethod.PATCH
        else -> null
    }

    companion object {
        private val SUPPORTED_METHODS = listOf(
            HttpMethod.GET,
            HttpMethod.POST,
            HttpMethod.PUT,
            HttpMethod.DELETE,
            HttpMethod.HEAD,
            HttpMethod.PATCH,
        )
    }
}
