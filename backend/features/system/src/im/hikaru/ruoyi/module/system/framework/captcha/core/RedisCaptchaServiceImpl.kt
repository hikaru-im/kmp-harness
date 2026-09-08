package im.hikaru.ruoyi.module.system.framework.captcha.core

import com.anji.captcha.service.CaptchaCacheService
import org.springframework.data.redis.core.StringRedisTemplate
import java.time.Duration

class RedisCaptchaServiceImpl : CaptchaCacheService {
    lateinit var stringRedisTemplate: StringRedisTemplate

    override fun type() = "redis"

    override fun set(key: String, value: String, expiresInSeconds: Long) {
        stringRedisTemplate.opsForValue().set(key, value, Duration.ofSeconds(expiresInSeconds))
    }

    override fun exists(key: String): Boolean = stringRedisTemplate.hasKey(key)

    override fun delete(key: String) {
        stringRedisTemplate.delete(key)
    }

    override fun get(key: String): String? = stringRedisTemplate.opsForValue().get(key)

    override fun increment(key: String, value: Long): Long? =
        stringRedisTemplate.opsForValue().increment(key, value)
}
