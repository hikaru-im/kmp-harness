package im.hikaru.ruoyi.module.system.controller.admin.user.vo.user

import im.hikaru.ruoyi.framework.common.validation.Mobile
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size

class UserSaveReqVO {
    var id: Long? = null
    @field:NotBlank @field:Pattern(regexp = "^[a-zA-Z0-9]{4,30}$") var username: String? = null
    @field:Size(max = 30) var nickname: String? = null
    var remark: String? = null
    var deptId: Long? = null
    var postIds: Set<Long>? = null
    @field:Email @field:Size(max = 50) var email: String? = null
    @field:Mobile var mobile: String? = null
    var sex: Int? = null
    var avatar: String? = null
    @field:Size(min = 4, max = 16) var password: String? = null
}
