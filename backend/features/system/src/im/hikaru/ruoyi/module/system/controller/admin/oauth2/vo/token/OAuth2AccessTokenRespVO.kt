package im.hikaru.ruoyi.module.system.controller.admin.oauth2.vo.token

import java.time.LocalDateTime

class OAuth2AccessTokenRespVO {
    var id: Long? = null
    var accessToken: String? = null
    var refreshToken: String? = null
    var userId: Long? = null
    var userType: Int? = null
    var clientId: String? = null
    var createTime: LocalDateTime? = null
    var expiresTime: LocalDateTime? = null
}
