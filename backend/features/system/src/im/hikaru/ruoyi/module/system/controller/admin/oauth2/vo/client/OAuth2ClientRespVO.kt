package im.hikaru.ruoyi.module.system.controller.admin.oauth2.vo.client

import java.time.LocalDateTime

class OAuth2ClientRespVO {
    var id: Long? = null
    var clientId: String? = null
    var secret: String? = null
    var name: String? = null
    var logo: String? = null
    var description: String? = null
    var status: Int? = null
    var accessTokenValiditySeconds: Int? = null
    var refreshTokenValiditySeconds: Int? = null
    var redirectUris: List<String>? = null
    var authorizedGrantTypes: List<String>? = null
    var scopes: List<String>? = null
    var autoApproveScopes: List<String>? = null
    var authorities: List<String>? = null
    var resourceIds: List<String>? = null
    var additionalInformation: String? = null
    var createTime: LocalDateTime? = null
}
