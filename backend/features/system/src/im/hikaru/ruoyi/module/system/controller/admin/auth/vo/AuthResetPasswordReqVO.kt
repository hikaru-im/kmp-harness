package im.hikaru.ruoyi.module.system.controller.admin.auth.vo

import im.hikaru.ruoyi.framework.common.validation.Mobile
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size

class AuthResetPasswordReqVO {
    @field:NotEmpty
    @field:Size(min = 4, max = 16)
    var password: String? = null

    @field:NotEmpty
    @field:Mobile
    var mobile: String? = null

    @field:NotEmpty
    var code: String? = null
}
