package im.hikaru.ruoyi.framework.websocket.core.security

import im.hikaru.ruoyi.framework.security.config.AuthorizeRequestsCustomizer
import im.hikaru.ruoyi.framework.websocket.config.WebSocketProperties
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer

/** Allows the HTTP upgrade request to reach the authenticated handshake interceptor. */
class WebSocketAuthorizeRequestsCustomizer(
    private val webSocketProperties: WebSocketProperties,
) : AuthorizeRequestsCustomizer() {

    override fun customize(
        registry: AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry,
    ) {
        registry.requestMatchers(webSocketProperties.path).permitAll()
    }
}
