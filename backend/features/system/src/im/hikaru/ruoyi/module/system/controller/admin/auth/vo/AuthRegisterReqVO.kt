package im.hikaru.ruoyi.module.system.controller.admin.auth.vo

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

class AuthRegisterReqVO : CaptchaVerificationReqVO() {
    @field:NotBlank
    @field:Pattern(regexp = "^[a-zA-Z0-9]{4,30}$")
    @field:Size(min = 4, max = 30)
    var username: String? = null

    @field:NotBlank
    @field:Size(max = 30)
    var nickname: String? = null

    @field:NotEmpty
    @field:Size(min = 4, max = 16)
    var password: String? = null
}
