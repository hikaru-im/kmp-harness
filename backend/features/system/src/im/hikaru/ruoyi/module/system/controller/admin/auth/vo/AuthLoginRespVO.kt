package im.hikaru.ruoyi.module.system.controller.admin.auth.vo

import java.time.LocalDateTime

class AuthLoginRespVO {
    var userId: Long? = null
    var accessToken: String? = null
    var refreshToken: String? = null
    var expiresTime: LocalDateTime? = null
}
