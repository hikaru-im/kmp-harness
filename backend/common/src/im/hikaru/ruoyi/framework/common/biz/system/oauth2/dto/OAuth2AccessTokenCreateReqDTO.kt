package im.hikaru.ruoyi.framework.common.biz.system.oauth2.dto

import im.hikaru.ruoyi.framework.common.enums.UserTypeEnum
import im.hikaru.ruoyi.framework.common.validation.InEnum
import jakarta.validation.constraints.NotNull
import java.io.Serializable

class OAuth2AccessTokenCreateReqDTO : Serializable {
    @field:NotNull
    var userId: Long? = null

    @field:NotNull
    @field:InEnum(UserTypeEnum::class)
    var userType: Int? = null

    @field:NotNull
    var clientId: String? = null

    var scopes: List<String>? = null
}
