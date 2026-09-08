package im.hikaru.ruoyi.module.system.framework.justauth.core

import com.xkcoding.justauth.autoconfigure.JustAuthProperties
import me.zhyd.oauth.cache.AuthStateCache
import me.zhyd.oauth.enums.AuthResponseStatus
import me.zhyd.oauth.exception.AuthException
import me.zhyd.oauth.request.AuthRequest

class AuthRequestFactory(
    private val properties: JustAuthProperties,
    @Suppress("UNUSED_PARAMETER")
    private val authStateCache: AuthStateCache,
) {
    fun oauthList(): List<String> =
        properties.type.keys.toList() + properties.extend?.config?.keys.orEmpty()

    fun get(source: String?): AuthRequest {
        if (source.isNullOrBlank()) {
            throw AuthException(AuthResponseStatus.NO_AUTH_SOURCE)
        }
        throw AuthException(AuthResponseStatus.UNSUPPORTED)
    }
}
