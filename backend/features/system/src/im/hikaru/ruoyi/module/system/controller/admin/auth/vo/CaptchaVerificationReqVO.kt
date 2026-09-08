package im.hikaru.ruoyi.module.system.controller.admin.auth.vo

import jakarta.validation.constraints.NotEmpty

open class CaptchaVerificationReqVO {
    @field:NotEmpty(groups = [CodeEnableGroup::class])
    var captchaVerification: String? = null

    interface CodeEnableGroup
}
