package im.hikaru.ruoyi.module.system.controller.admin.auth.vo

import im.hikaru.ruoyi.framework.common.validation.InEnum
import im.hikaru.ruoyi.module.system.enums.social.SocialTypeEnum
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

class AuthLoginReqVO : CaptchaVerificationReqVO() {
    @field:NotEmpty
    @field:Size(min = 4, max = 30)
    @field:Pattern(regexp = "^[a-zA-Z0-9]{4,30}$")
    var username: String? = null

    @field:NotEmpty
    @field:Size(min = 4, max = 16)
    var password: String? = null

    @field:InEnum(SocialTypeEnum::class)
    var socialType: Int? = null
    var socialCode: String? = null
    var socialState: String? = null

    @AssertTrue(message = "Social authorization code must not be empty")
    fun isSocialCodeValid() = socialType == null || !socialCode.isNullOrEmpty()

    @AssertTrue(message = "Social state must not be empty")
    fun isSocialStateValid() = socialType == null || !socialState.isNullOrEmpty()
}
