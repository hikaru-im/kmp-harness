package im.hikaru.ruoyi.module.system.service.oauth2

import im.hikaru.ruoyi.module.system.dal.dataobject.oauth2.OAuth2CodeDO

interface OAuth2CodeService {
    fun createAuthorizationCode(
        userId: Long,
        userType: Int,
        clientId: String,
        scopes: List<String>,
        redirectUri: String?,
        state: String?,
    ): OAuth2CodeDO

    fun consumeAuthorizationCode(code: String): OAuth2CodeDO
}
