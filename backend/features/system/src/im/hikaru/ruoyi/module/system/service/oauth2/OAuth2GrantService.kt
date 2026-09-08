package im.hikaru.ruoyi.module.system.service.oauth2

import im.hikaru.ruoyi.module.system.dal.dataobject.oauth2.OAuth2AccessTokenDO

interface OAuth2GrantService {
    fun grantImplicit(userId: Long, userType: Int, clientId: String, scopes: List<String>): OAuth2AccessTokenDO

    fun grantAuthorizationCodeForCode(
        userId: Long,
        userType: Int,
        clientId: String,
        scopes: List<String>,
        redirectUri: String?,
        state: String?,
    ): String

    fun grantAuthorizationCodeForAccessToken(
        clientId: String,
        code: String,
        redirectUri: String?,
        state: String?,
    ): OAuth2AccessTokenDO

    fun grantPassword(
        username: String,
        password: String,
        clientId: String,
        scopes: List<String>,
    ): OAuth2AccessTokenDO

    fun grantRefreshToken(refreshToken: String, clientId: String): OAuth2AccessTokenDO

    fun grantClientCredentials(clientId: String, scopes: List<String>): OAuth2AccessTokenDO

    fun revokeToken(clientId: String, accessToken: String): Boolean
}
