package im.hikaru.ruoyi.module.system.controller.admin.logger.vo.loginlog

import java.time.LocalDateTime

class LoginLogRespVO {
    var id: Long? = null
    var logType: Int? = null
    var userId: Long? = null
    var userType: Int? = null
    var traceId: String? = null
    var username: String? = null
    var result: Int? = null
    var userIp: String? = null
    var userAgent: String? = null
    var createTime: LocalDateTime? = null
}
