package im.hikaru.ruoyi.module.system.controller.admin.socail.vo.user

import java.time.LocalDateTime

class SocialUserRespVO {
    var id: Long? = null
    var type: Int? = null
    var openid: String? = null
    var token: String? = null
    var rawTokenInfo: String? = null
    var nickname: String? = null
    var avatar: String? = null
    var rawUserInfo: String? = null
    var code: String? = null
    var state: String? = null
    var createTime: LocalDateTime? = null
    var updateTime: LocalDateTime? = null
}
