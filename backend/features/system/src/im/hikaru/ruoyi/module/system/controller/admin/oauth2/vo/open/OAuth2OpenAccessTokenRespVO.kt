package im.hikaru.ruoyi.module.system.controller.admin.oauth2.vo.open

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Admin - OAuth2 open access token response")
class OAuth2OpenAccessTokenRespVO {
    @get:JsonProperty("access_token")
    var accessToken: String? = null

    @get:JsonProperty("refresh_token")
    var refreshToken: String? = null

    @get:JsonProperty("token_type")
    var tokenType: String? = null

    @get:JsonProperty("expires_in")
    var expiresIn: Long? = null

    var scope: String? = null
}
