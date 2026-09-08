package im.hikaru.ruoyi.module.system.api.logger.dto

import java.time.LocalDateTime

class OperateLogRespDTO {
    var id: Long? = null
    var traceId: String? = null
    var userId: Long? = null
    var userName: String? = null
    var userType: Int? = null
    var type: String? = null
    var subType: String? = null
    var bizId: Long? = null
    var action: String? = null
    var extra: String? = null
    var requestMethod: String? = null
    var requestUrl: String? = null
    var userIp: String? = null
    var userAgent: String? = null
    var createTime: LocalDateTime? = null
}
