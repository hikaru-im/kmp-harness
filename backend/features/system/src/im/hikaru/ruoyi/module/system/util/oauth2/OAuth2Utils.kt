package im.hikaru.ruoyi.module.system.util.oauth2

import im.hikaru.ruoyi.framework.common.util.http.HttpUtils
import im.hikaru.ruoyi.framework.security.core.util.SecurityFrameworkUtils
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Clock

object OAuth2Utils {
    @JvmStatic
    fun buildAuthorizationCodeRedirectUri(
        redirectUri: String,
        authorizationCode: String,
        state: String?,
    ): String {
        val query = linkedMapOf<String, Any>("code" to authorizationCode)
        state?.let { query["state"] = it }
        return HttpUtils.append(redirectUri, query)
    }

    @JvmStatic
    fun buildImplicitRedirectUri(
        redirectUri: String,
        accessToken: String,
        state: String?,
        expireTime: LocalDateTime?,
        scopes: Collection<String>,
        additionalInformation: Map<String, Any?>,
    ): String {
        val values = linkedMapOf<String, Any>(
            "access_token" to accessToken,
            "token_type" to SecurityFrameworkUtils.AUTHORIZATION_BEARER.lowercase(),
        )
        state?.let { values["state"] = it }
        expireTime?.let { values["expires_in"] = getExpiresIn(it) }
        if (scopes.isNotEmpty()) values["scope"] = buildScopeStr(scopes)

        val keys = mutableMapOf<String, String>()
        additionalInformation.forEach { (key, value) ->
            if (value != null) {
                val placeholder = "extra_$key"
                keys[placeholder] = key
                values[placeholder] = value
            }
        }
        return HttpUtils.append(redirectUri, values, keys, fragment = true)
    }

    @JvmStatic
    fun buildUnsuccessfulRedirect(
        redirectUri: String,
        responseType: String,
        state: String?,
        error: String,
        description: String,
    ): String {
        val query = linkedMapOf<String, Any>(
            "error" to error,
            "error_description" to description,
        )
        state?.let { query["state"] = it }
        return HttpUtils.append(redirectUri, query, fragment = !responseType.contains("code"))
    }

    @JvmStatic
    fun getExpiresIn(expireTime: LocalDateTime): Long =
        expireTime.toInstant(TimeZone.currentSystemDefault()).epochSeconds - Clock.System.now().epochSeconds

    @JvmStatic
    fun buildScopeStr(scopes: Collection<String>): String = scopes.joinToString(" ")

    @JvmStatic
    fun buildScopes(scope: String?): List<String> =
        scope?.split(' ')?.filter { it.isNotBlank() }.orEmpty()
}
