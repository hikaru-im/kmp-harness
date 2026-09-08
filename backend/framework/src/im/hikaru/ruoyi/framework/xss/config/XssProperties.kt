package im.hikaru.ruoyi.framework.xss.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties(prefix = "yudao.xss")
class XssProperties {
    var enable: Boolean = true
    var excludeUrls: List<String> = emptyList()
}
