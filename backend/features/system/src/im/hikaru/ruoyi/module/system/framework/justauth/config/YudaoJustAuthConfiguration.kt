package im.hikaru.ruoyi.module.system.framework.justauth.config

import im.hikaru.ruoyi.module.system.framework.justauth.core.AuthRequestFactory
import com.xkcoding.justauth.autoconfigure.JustAuthProperties
import com.xkcoding.justauth.support.cache.RedisStateCache
import me.zhyd.oauth.cache.AuthStateCache
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.core.RedisTemplate

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JustAuthProperties::class)
class YudaoJustAuthConfiguration {
    @Bean
    @ConditionalOnProperty(
        prefix = "justauth",
        value = ["enabled"],
        havingValue = "true",
        matchIfMissing = true,
    )
    fun authRequestFactory(
        properties: JustAuthProperties,
        authStateCache: AuthStateCache,
    ): AuthRequestFactory = AuthRequestFactory(properties, authStateCache)

    @Bean
    fun authStateCache(
        justAuthRedisCacheTemplate: RedisTemplate<String, String>,
        justAuthProperties: JustAuthProperties,
    ): AuthStateCache = RedisStateCache(justAuthRedisCacheTemplate, justAuthProperties.cache)
}
