package im.hikaru.ruoyi.module.system.controller.admin.auth.vo

import im.hikaru.ruoyi.framework.common.validation.Mobile
import jakarta.validation.constraints.NotEmpty

class AuthSmsLoginReqVO {
    @field:NotEmpty
    @field:Mobile
    var mobile: String? = null

    @field:NotEmpty
    var code: String? = null
}
