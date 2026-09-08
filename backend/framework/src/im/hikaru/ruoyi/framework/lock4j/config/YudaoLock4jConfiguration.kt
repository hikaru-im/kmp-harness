package im.hikaru.ruoyi.framework.lock4j.config

import im.hikaru.ruoyi.framework.lock4j.core.DefaultLockFailureStrategy
import com.baomidou.lock.spring.boot.autoconfigure.LockAutoConfiguration
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.context.annotation.Bean

@AutoConfiguration(before = [LockAutoConfiguration::class])
@ConditionalOnClass(name = ["com.baomidou.lock.annotation.Lock4j"])
class YudaoLock4jConfiguration {

    @Bean
    fun lockFailureStrategy(): DefaultLockFailureStrategy = DefaultLockFailureStrategy()
}
