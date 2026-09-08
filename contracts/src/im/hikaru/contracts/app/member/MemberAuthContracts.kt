package im.hikaru.contracts.app.member

import kotlinx.serialization.Serializable

@Serializable
data class MemberPasswordLoginRequest(
    val mobile: String,
    val password: String,
    val socialType: Int? = null,
    val socialCode: String? = null,
    val socialState: String? = null,
)

@Serializable
data class MemberSmsLoginRequest(
    val mobile: String,
    val code: String,
    val socialType: Int? = null,
    val socialCode: String? = null,
    val socialState: String? = null,
)

@Serializable
data class MemberSmsSendRequest(
    val mobile: String? = null,
    val scene: Int,
)

@Serializable
data class MemberSmsValidateRequest(
    val mobile: String? = null,
    val scene: Int,
    val code: String,
)

@Serializable
data class MemberSocialLoginRequest(
    val type: Int,
    val code: String,
    val state: String,
)

@Serializable
data class MemberWeixinMiniAppLoginRequest(
    val phoneCode: String,
    val loginCode: String,
    val state: String,
)

/** Response shared by password login, SMS login and token refresh. */
@Serializable
data class MemberAuthLoginResponse(
    val userId: Long? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val expiresTime: Long? = null,
    val openid: String? = null,
)
