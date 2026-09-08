package im.hikaru.ruoyi.framework.signature.config

import im.hikaru.ruoyi.framework.redis.config.YudaoRedisAutoConfiguration
import im.hikaru.ruoyi.framework.signature.core.aop.ApiSignatureAspect
import im.hikaru.ruoyi.framework.signature.core.redis.ApiSignatureRedisDAO
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.data.redis.core.StringRedisTemplate

/**
 * HTTP API 签名的自动配置类 (迁移自 Java)
 *
 * @author Zhougang
 */
@AutoConfiguration(after = [YudaoRedisAutoConfiguration::class])
class YudaoApiSignatureAutoConfiguration {

    @Bean
    fun signatureAspect(signatureRedisDAO: ApiSignatureRedisDAO): ApiSignatureAspect =
        ApiSignatureAspect(signatureRedisDAO)

    @Bean
    fun signatureRedisDAO(stringRedisTemplate: StringRedisTemplate): ApiSignatureRedisDAO =
        ApiSignatureRedisDAO(stringRedisTemplate)
}
