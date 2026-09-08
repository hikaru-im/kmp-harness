package im.hikaru.ruoyi.module.system.framework.sms.config

import jakarta.validation.constraints.Pattern
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component
import org.springframework.validation.annotation.Validated
import java.time.Duration

@Component
@ConfigurationProperties(prefix = "yudao.sms-code")
@Validated
class SmsCodeProperties {
    var expireTimes: Duration = Duration.ofMinutes(10)
    var sendFrequency: Duration = Duration.ofMinutes(1)
    var sendMaximumQuantityPerDay: Int = 10
    var beginCode: Int = 100_000
    var endCode: Int = 999_999

    @field:Pattern(regexp = "^[0-9]{4,6}$", message = "本地短信验证码必须为 4-6 位数字")
    var localFixedCode: String? = null
}
