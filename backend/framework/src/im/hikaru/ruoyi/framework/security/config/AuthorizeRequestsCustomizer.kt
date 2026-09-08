package im.hikaru.ruoyi.framework.security.config

import im.hikaru.ruoyi.framework.web.config.WebProperties
import org.springframework.core.Ordered
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer

/**
 * Extension point for module-specific URL authorization rules.
 *
 * Each module can expose one bean and add its public endpoints without
 * coupling the shared security auto-configuration to module packages.
 */
abstract class AuthorizeRequestsCustomizer :
    Customizer<AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry>,
    Ordered {

    @Autowired
    private lateinit var webProperties: WebProperties

    protected fun buildAdminApi(url: String): String = webProperties.adminApi.prefix + url

    protected fun buildAppApi(url: String): String = webProperties.appApi.prefix + url

    override fun getOrder(): Int = 0
}
