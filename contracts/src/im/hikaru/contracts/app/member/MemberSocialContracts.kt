package im.hikaru.contracts.app.member

import kotlinx.serialization.Serializable

@Serializable
data class MemberWeixinJsapiSignatureResponse(
    val appId: String? = null,
    val nonceStr: String? = null,
    val timestamp: Long? = null,
    val url: String? = null,
    val signature: String? = null,
)

@Serializable
data class MemberSocialUserResponse(
    val openid: String? = null,
    val nickname: String? = null,
    val avatar: String? = null,
)

@Serializable
data class MemberSocialUserBindRequest(
    val type: Int,
    val code: String,
    val state: String,
)

@Serializable
data class MemberSocialUserUnbindRequest(
    val type: Int,
    val openid: String,
)

@Serializable
data class MemberSocialWxaQrcodeRequest(
    val scene: String? = null,
    val path: String,
    val width: Int? = null,
    val autoColor: Boolean? = null,
    val checkPath: Boolean? = null,
    val hyaline: Boolean? = null,
)

@Serializable
data class MemberSocialSubscribeTemplateResponse(
    val id: String? = null,
    val title: String? = null,
    val content: String? = null,
    val example: String? = null,
    val type: Int? = null,
)
