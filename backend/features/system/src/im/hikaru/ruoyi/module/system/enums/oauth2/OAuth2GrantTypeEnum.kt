package im.hikaru.ruoyi.module.system.enums.oauth2

enum class OAuth2GrantTypeEnum(val grantType: String) {
    PASSWORD("password"),
    AUTHORIZATION_CODE("authorization_code"),
    IMPLICIT("implicit"),
    CLIENT_CREDENTIALS("client_credentials"),
    REFRESH_TOKEN("refresh_token");

    companion object {
        fun getByGrantType(grantType: String?): OAuth2GrantTypeEnum? = entries.firstOrNull { it.grantType == grantType }
    }
}
