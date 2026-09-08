package im.hikaru.ruoyi.framework.common.biz.system.oauth2.dto

import java.io.Serializable
import java.time.LocalDateTime

class OAuth2AccessTokenRespDTO : Serializable {
    var accessToken: String? = null
    var refreshToken: String? = null
    var userId: Long? = null
    var userType: Int? = null
    var expiresTime: LocalDateTime? = null
}
