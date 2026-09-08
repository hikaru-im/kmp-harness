package im.hikaru.ruoyi.module.system.controller.admin.oauth2.vo.user

import im.hikaru.ruoyi.framework.common.validation.Mobile
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Size

@Schema(description = "Admin - OAuth2 user update request")
class OAuth2UserUpdateReqVO {
    @field:Size(max = 30)
    var nickname: String? = null

    @field:Email
    @field:Size(max = 50)
    var email: String? = null

    @field:Mobile
    var mobile: String? = null

    var sex: Int? = null
}
