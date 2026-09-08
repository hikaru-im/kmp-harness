package im.hikaru.ruoyi.module.system.controller.admin.oauth2

import im.hikaru.ruoyi.framework.common.core.KeyValue
import im.hikaru.ruoyi.framework.common.enums.UserTypeEnum
import im.hikaru.ruoyi.framework.common.exception.enums.GlobalErrorCodeConstants.BAD_REQUEST
import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception0
import im.hikaru.ruoyi.framework.common.pojo.CommonResult
import im.hikaru.ruoyi.framework.common.util.http.HttpUtils
import im.hikaru.ruoyi.framework.common.util.json.JsonUtils
import im.hikaru.ruoyi.framework.security.core.util.SecurityFrameworkUtils
import im.hikaru.ruoyi.module.system.controller.admin.oauth2.vo.open.OAuth2OpenAccessTokenRespVO
import im.hikaru.ruoyi.module.system.controller.admin.oauth2.vo.open.OAuth2OpenAuthorizeInfoRespVO
import im.hikaru.ruoyi.module.system.controller.admin.oauth2.vo.open.OAuth2OpenCheckTokenRespVO
import im.hikaru.ruoyi.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO
import im.hikaru.ruoyi.module.system.dal.dataobject.oauth2.OAuth2ClientDO
import im.hikaru.ruoyi.module.system.enums.oauth2.OAuth2GrantTypeEnum
import im.hikaru.ruoyi.module.system.service.oauth2.OAuth2ApproveService
import im.hikaru.ruoyi.module.system.service.oauth2.OAuth2ClientService
import im.hikaru.ruoyi.module.system.service.oauth2.OAuth2GrantService
import im.hikaru.ruoyi.module.system.service.oauth2.OAuth2TokenService
import im.hikaru.ruoyi.module.system.util.oauth2.OAuth2Utils
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.annotation.security.PermitAll
import jakarta.servlet.http.HttpServletRequest
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Admin - OAuth2 authorization")
@RestController
@RequestMapping("/system/oauth2")
@Validated
class OAuth2OpenController(
    private val oauth2GrantService: OAuth2GrantService,
    private val oauth2ClientService: OAuth2ClientService,
    private val oauth2ApproveService: OAuth2ApproveService,
    private val oauth2TokenService: OAuth2TokenService,
) {
    @PostMapping("/token")
    @PermitAll
    @Operation(summary = "Obtain an access token")
    fun postAccessToken(
        request: HttpServletRequest,
        @RequestParam("grant_type") grantType: String,
        @RequestParam(value = "code", required = false) code: String?,
        @RequestParam(value = "redirect_uri", required = false) redirectUri: String?,
        @RequestParam(value = "state", required = false) state: String?,
        @RequestParam(value = "username", required = false) username: String?,
        @RequestParam(value = "password", required = false) password: String?,
        @RequestParam(value = "scope", required = false) scope: String?,
        @RequestParam(value = "refresh_token", required = false) refreshToken: String?,
    ): CommonResult<OAuth2OpenAccessTokenRespVO> {
        val scopes = OAuth2Utils.buildScopes(scope)
        val grantTypeEnum = OAuth2GrantTypeEnum.getByGrantType(grantType)
            ?: throw exception0(BAD_REQUEST.code, "Unknown grant type: {}", grantType)
        if (grantTypeEnum == OAuth2GrantTypeEnum.IMPLICIT) {
            throw exception0(BAD_REQUEST.code, "The token endpoint does not support the implicit grant")
        }

        val (clientId, clientSecret) = obtainBasicAuthorization(request)
        val client = oauth2ClientService.validOAuthClientFromCache(
            clientId,
            clientSecret,
            grantType,
            scopes,
            redirectUri,
        )
        val validatedClientId = requireNotNull(client.clientId)
        val accessToken = when (grantTypeEnum) {
            OAuth2GrantTypeEnum.AUTHORIZATION_CODE -> oauth2GrantService.grantAuthorizationCodeForAccessToken(
                validatedClientId,
                requireParameter(code, "code"),
                redirectUri,
                state,
            )

            OAuth2GrantTypeEnum.PASSWORD -> oauth2GrantService.grantPassword(
                requireParameter(username, "username"),
                requireParameter(password, "password"),
                validatedClientId,
                scopes,
            )

            OAuth2GrantTypeEnum.CLIENT_CREDENTIALS ->
                oauth2GrantService.grantClientCredentials(validatedClientId, scopes)

            OAuth2GrantTypeEnum.REFRESH_TOKEN -> oauth2GrantService.grantRefreshToken(
                requireParameter(refreshToken, "refresh_token"),
                validatedClientId,
            )

            OAuth2GrantTypeEnum.IMPLICIT -> error("Validated above")
        }
        return CommonResult.success(accessToken.toAccessTokenResponse())
    }

    @DeleteMapping("/token")
    @PermitAll
    @Operation(summary = "Revoke an access token")
    fun revokeToken(
        request: HttpServletRequest,
        @RequestParam("token") token: String,
    ): CommonResult<Boolean> {
        val (clientId, clientSecret) = obtainBasicAuthorization(request)
        val client = oauth2ClientService.validOAuthClientFromCache(clientId, clientSecret, null, null, null)
        return CommonResult.success(oauth2GrantService.revokeToken(requireNotNull(client.clientId), token))
    }

    @PostMapping("/check-token")
    @PermitAll
    @Operation(summary = "Check an access token")
    fun checkToken(
        request: HttpServletRequest,
        @RequestParam("token") token: String,
    ): CommonResult<OAuth2OpenCheckTokenRespVO> {
        val (clientId, clientSecret) = obtainBasicAuthorization(request)
        oauth2ClientService.validOAuthClientFromCache(clientId, clientSecret, null, null, null)
        return CommonResult.success(oauth2TokenService.checkAccessToken(token).toCheckTokenResponse())
    }

    @GetMapping("/authorize")
    @Operation(summary = "Get authorization information")
    fun authorize(@RequestParam("clientId") clientId: String): CommonResult<OAuth2OpenAuthorizeInfoRespVO> {
        val client = oauth2ClientService.validOAuthClientFromCache(clientId)
        val approves = oauth2ApproveService.getApproveList(requireLoginId(), USER_TYPE, clientId)
        val approveMap = approves.mapNotNull { approve -> approve.scope?.let { it to approve } }.toMap()
        return CommonResult.success(OAuth2OpenAuthorizeInfoRespVO().apply {
            this.client = OAuth2OpenAuthorizeInfoRespVO.Client().apply {
                name = client.name
                logo = client.logo
            }
            scopes = client.scopes.orEmpty().map { scope ->
                KeyValue(scope, approveMap[scope]?.approved == true)
            }
        })
    }

    @PostMapping("/authorize")
    @Operation(summary = "Approve or deny authorization")
    fun approveOrDeny(
        @RequestParam("response_type") responseType: String,
        @RequestParam("client_id") clientId: String,
        @RequestParam(value = "scope", required = false) scope: String?,
        @RequestParam("redirect_uri") redirectUri: String,
        @RequestParam("auto_approve") autoApprove: Boolean,
        @RequestParam(value = "state", required = false) state: String?,
    ): CommonResult<String?> {
        val scopes = if (scope.isNullOrBlank()) {
            emptyMap()
        } else {
            JsonUtils.parseObject(scope, SCOPE_TYPE).orEmpty()
        }
        val grantType = grantTypeForResponse(responseType)
        val client = oauth2ClientService.validOAuthClientFromCache(
            clientId,
            null,
            grantType.grantType,
            scopes.keys,
            redirectUri,
        )
        val userId = requireLoginId()

        if (autoApprove) {
            if (!oauth2ApproveService.checkForPreApproval(userId, USER_TYPE, clientId, scopes.keys)) {
                return CommonResult.success<String?>(null)
            }
        } else if (!oauth2ApproveService.updateAfterApproval(userId, USER_TYPE, clientId, scopes)) {
            return CommonResult.success(
                OAuth2Utils.buildUnsuccessfulRedirect(
                    redirectUri,
                    responseType,
                    state,
                    "access_denied",
                    "User denied access",
                ),
            )
        }

        val approvedScopes = scopes.filterValues { it }.keys.toList()
        val redirect = when (grantType) {
            OAuth2GrantTypeEnum.AUTHORIZATION_CODE -> getAuthorizationCodeRedirect(
                userId,
                client,
                approvedScopes,
                redirectUri,
                state,
            )

            OAuth2GrantTypeEnum.IMPLICIT -> getImplicitGrantRedirect(
                userId,
                client,
                approvedScopes,
                redirectUri,
                state,
            )

            else -> error("Unexpected response grant type")
        }
        return CommonResult.success(redirect)
    }

    private fun getImplicitGrantRedirect(
        userId: Long,
        client: OAuth2ClientDO,
        scopes: List<String>,
        redirectUri: String,
        state: String?,
    ): String {
        val accessToken = oauth2GrantService.grantImplicit(
            userId,
            USER_TYPE,
            requireNotNull(client.clientId),
            scopes,
        )
        return OAuth2Utils.buildImplicitRedirectUri(
            redirectUri,
            requireNotNull(accessToken.accessToken),
            state,
            accessToken.expiresTime,
            scopes,
            JsonUtils.parseMap(client.additionalInformation).orEmpty(),
        )
    }

    private fun getAuthorizationCodeRedirect(
        userId: Long,
        client: OAuth2ClientDO,
        scopes: List<String>,
        redirectUri: String,
        state: String?,
    ): String {
        val code = oauth2GrantService.grantAuthorizationCodeForCode(
            userId,
            USER_TYPE,
            requireNotNull(client.clientId),
            scopes,
            redirectUri,
            state,
        )
        return OAuth2Utils.buildAuthorizationCodeRedirectUri(redirectUri, code, state)
    }

    private fun OAuth2AccessTokenDO.toAccessTokenResponse() = OAuth2OpenAccessTokenRespVO().apply {
        accessToken = this@toAccessTokenResponse.accessToken
        refreshToken = this@toAccessTokenResponse.refreshToken
        tokenType = SecurityFrameworkUtils.AUTHORIZATION_BEARER.lowercase()
        expiresIn = this@toAccessTokenResponse.expiresTime?.let(OAuth2Utils::getExpiresIn)
        scope = OAuth2Utils.buildScopeStr(this@toAccessTokenResponse.scopes.orEmpty())
    }

    private fun OAuth2AccessTokenDO.toCheckTokenResponse() = OAuth2OpenCheckTokenRespVO().apply {
        userId = this@toCheckTokenResponse.userId
        userType = UserTypeEnum.ADMIN.value
        tenantId = this@toCheckTokenResponse.tenantId
        clientId = this@toCheckTokenResponse.clientId
        scopes = this@toCheckTokenResponse.scopes
        accessToken = this@toCheckTokenResponse.accessToken
        exp = this@toCheckTokenResponse.expiresTime
            ?.toInstant(TimeZone.currentSystemDefault())
            ?.epochSeconds
    }

    private fun obtainBasicAuthorization(request: HttpServletRequest): Array<String> =
        HttpUtils.obtainBasicAuthorization(request)
            ?: throw exception0(BAD_REQUEST.code, "client_id or client_secret was not provided correctly")

    private fun requireLoginId(): Long =
        requireNotNull(SecurityFrameworkUtils.getLoginUserId()) { "No authenticated user" }

    private fun requireParameter(value: String?, name: String): String =
        value?.takeIf { it.isNotBlank() }
            ?: throw exception0(BAD_REQUEST.code, "Missing required parameter: {}", name)

    private fun grantTypeForResponse(responseType: String): OAuth2GrantTypeEnum = when (responseType) {
        "code" -> OAuth2GrantTypeEnum.AUTHORIZATION_CODE
        "token" -> OAuth2GrantTypeEnum.IMPLICIT
        else -> throw exception0(BAD_REQUEST.code, "response_type must be code or token")
    }

    companion object {
        private val SCOPE_TYPE = object : tools.jackson.core.type.TypeReference<LinkedHashMap<String, Boolean>>() {}
        private val USER_TYPE = UserTypeEnum.ADMIN.value
    }
}
