package im.hikaru.ruoyi.module.system.controller.admin.user.vo.profile

import im.hikaru.ruoyi.framework.common.validation.Mobile
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Size

class UserProfileUpdateReqVO {
    @field:Size(max = 30) var nickname: String? = null
    @field:Email @field:Size(max = 50) var email: String? = null
    @field:Mobile var mobile: String? = null
    var sex: Int? = null
    var avatar: String? = null
}
