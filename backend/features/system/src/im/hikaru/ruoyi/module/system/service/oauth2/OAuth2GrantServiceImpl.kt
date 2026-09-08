package im.hikaru.ruoyi.module.system.service.oauth2

import im.hikaru.ruoyi.framework.common.enums.UserTypeEnum
import im.hikaru.ruoyi.framework.common.exception.util.ServiceExceptionUtil.exception
import im.hikaru.ruoyi.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.OAUTH2_GRANT_CLIENT_ID_MISMATCH
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.OAUTH2_GRANT_REDIRECT_URI_MISMATCH
import im.hikaru.ruoyi.module.system.enums.ErrorCodeConstants.OAUTH2_GRANT_STATE_MISMATCH
import im.hikaru.ruoyi.module.system.service.auth.AdminAuthService
import org.springframework.stereotype.Service

@Service
class OAuth2GrantServiceImpl(
    private val oauth2TokenService: OAuth2TokenService,
    private val oauth2CodeService: OAuth2CodeService,
    private val adminAuthService: AdminAuthService,
) : OAuth2GrantService {
    override fun grantImplicit(
        userId: Long,
        userType: Int,
        clientId: String,
        scopes: List<String>,
    ): OAuth2AccessTokenDO = oauth2TokenService.createAccessToken(userId, userType, clientId, scopes)

    override fun grantAuthorizationCodeForCode(
        userId: Long,
        userType: Int,
        clientId: String,
        scopes: List<String>,
        redirectUri: String?,
        state: String?,
    ): String = requireNotNull(
        oauth2CodeService.createAuthorizationCode(userId, userType, clientId, scopes, redirectUri, state).code,
    )

    override fun grantAuthorizationCodeForAccessToken(
        clientId: String,
        code: String,
        redirectUri: String?,
        state: String?,
    ): OAuth2AccessTokenDO {
        val authorizationCode = oauth2CodeService.consumeAuthorizationCode(code)
        if (clientId != authorizationCode.clientId) throw exception(OAUTH2_GRANT_CLIENT_ID_MISMATCH)
        if (redirectUri != authorizationCode.redirectUri) throw exception(OAUTH2_GRANT_REDIRECT_URI_MISMATCH)
        if (state.orEmpty() != authorizationCode.state) throw exception(OAUTH2_GRANT_STATE_MISMATCH)
        return oauth2TokenService.createAccessToken(
            requireNotNull(authorizationCode.userId),
            requireNotNull(authorizationCode.userType),
            requireNotNull(authorizationCode.clientId),
            authorizationCode.scopes.orEmpty(),
        )
    }

    override fun grantPassword(
        username: String,
        password: String,
        clientId: String,
        scopes: List<String>,
    ): OAuth2AccessTokenDO {
        val user = adminAuthService.authenticate(username, password)
        return oauth2TokenService.createAccessToken(
            requireNotNull(user.id),
            UserTypeEnum.ADMIN.value,
            clientId,
            scopes,
        )
    }

    override fun grantRefreshToken(refreshToken: String, clientId: String): OAuth2AccessTokenDO =
        oauth2TokenService.refreshAccessToken(refreshToken, clientId)

    override fun grantClientCredentials(clientId: String, scopes: List<String>): OAuth2AccessTokenDO =
        oauth2TokenService.createAccessToken(0L, UserTypeEnum.ADMIN.value, clientId, scopes)

    override fun revokeToken(clientId: String, accessToken: String): Boolean {
        val token = oauth2TokenService.getAccessToken(accessToken)
        if (token?.clientId != clientId) return false
        return oauth2TokenService.removeAccessToken(accessToken) != null
    }
}
