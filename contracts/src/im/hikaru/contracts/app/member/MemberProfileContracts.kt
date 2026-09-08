package im.hikaru.contracts.app.member

import kotlinx.serialization.Serializable

/** Network shape returned by GET /app-api/member/user/get. */
@Serializable
data class MemberProfileResponse(
    val id: Long? = null,
    val nickname: String? = null,
    val avatar: String? = null,
    val profileVersion: Long? = null,
    val mobile: String? = null,
    val email: String? = null,
    val sex: Int? = null,
    val point: Int? = null,
    val experience: Int? = null,
    val brokerageEnabled: Boolean? = null,
    val level: MemberProfileLevelResponse? = null,
)

@Serializable
data class MemberProfileLevelResponse(
    val id: Long? = null,
    val name: String? = null,
    val level: Int? = null,
    val icon: String? = null,
)

@Serializable
data class MemberProfileUpdateRequest(
    val nickname: String? = null,
    val avatar: String? = null,
    val email: String? = null,
    val sex: Int? = null,
)

@Serializable
data class MemberUpdateMobileRequest(
    val code: String,
    val mobile: String,
    val oldCode: String? = null,
)

@Serializable
data class MemberUpdateMobileByWeixinRequest(
    val code: String,
)

@Serializable
data class MemberUpdatePasswordRequest(
    val password: String,
    val code: String,
)

@Serializable
data class MemberResetPasswordRequest(
    val password: String,
    val code: String,
    val mobile: String,
)
