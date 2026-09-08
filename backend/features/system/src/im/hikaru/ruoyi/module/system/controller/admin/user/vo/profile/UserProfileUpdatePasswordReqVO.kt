package im.hikaru.ruoyi.module.system.controller.admin.user.vo.profile

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

class UserProfileUpdatePasswordReqVO {
    @field:NotBlank var oldPassword: String? = null
    @field:NotBlank @field:Size(min = 4, max = 16) var newPassword: String? = null
}
