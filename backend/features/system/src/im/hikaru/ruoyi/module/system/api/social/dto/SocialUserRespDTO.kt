package im.hikaru.ruoyi.module.system.api.social.dto

data class SocialUserRespDTO(
    var openid: String? = null,
    var nickname: String? = null,
    var avatar: String? = null,
    var userId: Long? = null,
)
