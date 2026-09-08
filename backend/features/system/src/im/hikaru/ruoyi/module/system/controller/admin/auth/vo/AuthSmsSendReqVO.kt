package im.hikaru.ruoyi.module.system.controller.admin.auth.vo

import im.hikaru.ruoyi.framework.common.validation.InEnum
import im.hikaru.ruoyi.framework.common.validation.Mobile
import im.hikaru.ruoyi.module.system.enums.sms.SmsSceneEnum
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull

class AuthSmsSendReqVO : CaptchaVerificationReqVO() {
    @field:NotEmpty
    @field:Mobile
    var mobile: String? = null

    @field:NotNull
    @field:InEnum(SmsSceneEnum::class)
    var scene: Int? = null
}
