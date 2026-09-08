package im.hikaru.ruoyi.module.system.controller.admin.oauth2.vo.open

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Admin - OAuth2 open token check response")
class OAuth2OpenCheckTokenRespVO {
    @get:JsonProperty("user_id")
    var userId: Long? = null

    @get:JsonProperty("user_type")
    var userType: Int? = null

    @get:JsonProperty("tenant_id")
    var tenantId: Long? = null

    @get:JsonProperty("client_id")
    var clientId: String? = null

    var scopes: List<String>? = null

    @get:JsonProperty("access_token")
    var accessToken: String? = null

    var exp: Long? = null
}
