package im.hikaru.ruoyi.framework.websocket.core.security

import im.hikaru.ruoyi.framework.websocket.config.WebSocketProperties
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer

class WebSocketAuthorizeRequestsCustomizerTest {

    @Test
    fun `configured websocket path is permitted`() {
        val properties = WebSocketProperties().apply { path = "/socket" }
        @Suppress("UNCHECKED_CAST")
        val registry = Mockito.mock(
            AuthorizeHttpRequestsConfigurer.AuthorizationManagerRequestMatcherRegistry::class.java,
        ) as AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry
        @Suppress("UNCHECKED_CAST")
        val authorizedUrl = Mockito.mock(
            AuthorizeHttpRequestsConfigurer.AuthorizedUrl::class.java,
        ) as AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizedUrl
        Mockito.`when`(registry.requestMatchers("/socket")).thenReturn(authorizedUrl)

        WebSocketAuthorizeRequestsCustomizer(properties).customize(registry)

        Mockito.verify(registry).requestMatchers("/socket")
        Mockito.verify(authorizedUrl).permitAll()
    }
}
