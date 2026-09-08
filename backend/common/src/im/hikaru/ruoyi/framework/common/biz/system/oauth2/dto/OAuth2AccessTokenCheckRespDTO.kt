package im.hikaru.ruoyi.framework.common.biz.system.oauth2.dto

import java.io.Serializable
import java.time.LocalDateTime

class OAuth2AccessTokenCheckRespDTO : Serializable {
    var userId: Long? = null
    var userType: Int? = null
    var userInfo: Map<String, String>? = null
    var tenantId: Long? = null
    var scopes: List<String>? = null
    var expiresTime: LocalDateTime? = null
}
