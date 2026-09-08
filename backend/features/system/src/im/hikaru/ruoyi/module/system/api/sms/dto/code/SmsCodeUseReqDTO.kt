package im.hikaru.ruoyi.module.system.api.sms.dto.code

import im.hikaru.ruoyi.framework.common.validation.InEnum
import im.hikaru.ruoyi.framework.common.validation.Mobile
import im.hikaru.ruoyi.module.system.enums.sms.SmsSceneEnum
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull

class SmsCodeUseReqDTO {
    @field:Mobile
    @field:NotEmpty(message = "Mobile must not be empty")
    var mobile: String? = null

    @field:NotNull(message = "SMS scene must not be null")
    @field:InEnum(SmsSceneEnum::class)
    var scene: Int? = null

    @field:NotEmpty(message = "Code must not be empty")
    var code: String? = null

    @field:NotEmpty(message = "Used IP must not be empty")
    var usedIp: String? = null
}
