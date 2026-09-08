package im.hikaru.ruoyi.module.system.controller.admin.user.vo.profile

import java.time.LocalDateTime

class UserProfileRespVO {
    var id: Long? = null; var username: String? = null; var nickname: String? = null; var email: String? = null; var mobile: String? = null
    var sex: Int? = null; var avatar: String? = null; var loginIp: String? = null; var loginDate: LocalDateTime? = null; var createTime: LocalDateTime? = null
}
