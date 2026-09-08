package im.hikaru.ruoyi.module.system.controller.admin.user.vo.user

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

class UserUpdatePasswordReqVO {
    @field:NotNull var id: Long? = null
    @field:NotBlank @field:Size(min = 4, max = 16) var password: String? = null
}
