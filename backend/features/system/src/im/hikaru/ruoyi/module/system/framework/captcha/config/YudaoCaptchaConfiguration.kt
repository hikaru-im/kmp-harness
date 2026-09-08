package im.hikaru.ruoyi.module.system.framework.captcha.config

import im.hikaru.ruoyi.module.system.framework.captcha.core.RedisCaptchaServiceImpl
import com.anji.captcha.config.AjCaptchaAutoConfiguration
import com.anji.captcha.properties.AjCaptchaProperties
import com.anji.captcha.service.CaptchaCacheService
import com.anji.captcha.service.impl.CaptchaServiceFactory
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.core.StringRedisTemplate

@Configuration(proxyBeanMethods = false)
@ImportAutoConfiguration(AjCaptchaAutoConfiguration::class)
class YudaoCaptchaConfiguration {
    @Bean(name = ["AjCaptchaCacheService"])
    @Primary
    fun captchaCacheService(
        config: AjCaptchaProperties,
        stringRedisTemplate: StringRedisTemplate,
    ): CaptchaCacheService {
        val service = CaptchaServiceFactory.getCache(config.cacheType.name)
        if (service is RedisCaptchaServiceImpl) {
            service.stringRedisTemplate = stringRedisTemplate
        }
        return service
    }
}
