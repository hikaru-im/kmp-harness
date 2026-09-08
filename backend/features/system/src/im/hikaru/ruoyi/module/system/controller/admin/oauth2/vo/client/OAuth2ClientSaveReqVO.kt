package im.hikaru.ruoyi.module.system.controller.admin.oauth2.vo.client

import im.hikaru.ruoyi.framework.common.util.json.JsonUtils
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull

class OAuth2ClientSaveReqVO {
    var id: Long? = null

    @field:NotNull
    var clientId: String? = null

    @field:NotNull
    var secret: String? = null

    @field:NotNull
    var name: String? = null

    @field:NotNull
    var logo: String? = null

    var description: String? = null

    @field:NotNull
    var status: Int? = null

    @field:NotNull
    var accessTokenValiditySeconds: Int? = null

    @field:NotNull
    var refreshTokenValiditySeconds: Int? = null

    @field:NotEmpty
    var redirectUris: List<String>? = null

    @field:NotEmpty
    var authorizedGrantTypes: List<String>? = null

    var scopes: List<String>? = null
    var autoApproveScopes: List<String>? = null
    var authorities: List<String>? = null
    var resourceIds: List<String>? = null
    var additionalInformation: String? = null

    @AssertTrue(message = "additionalInformation must be valid JSON")
    fun isAdditionalInformationJson(): Boolean =
        additionalInformation.isNullOrBlank() || JsonUtils.isJson(additionalInformation)
}
