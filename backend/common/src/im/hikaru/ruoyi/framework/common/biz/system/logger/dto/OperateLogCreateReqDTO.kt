package im.hikaru.ruoyi.framework.common.biz.system.logger.dto

import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull

class OperateLogCreateReqDTO {
    var traceId: String? = null

    @field:NotNull(message = "User id must not be null")
    var userId: Long? = null

    @field:NotNull(message = "User type must not be null")
    var userType: Int? = null

    @field:NotEmpty(message = "Operation type must not be empty")
    var type: String? = null

    @field:NotEmpty(message = "Operation name must not be empty")
    var subType: String? = null

    @field:NotNull(message = "Business id must not be null")
    var bizId: Long? = null

    @field:NotEmpty(message = "Operation content must not be empty")
    var action: String? = null
    var extra: String? = null

    @field:NotEmpty(message = "Request method must not be empty")
    var requestMethod: String? = null

    @field:NotEmpty(message = "Request URL must not be empty")
    var requestUrl: String? = null

    @field:NotEmpty(message = "User IP must not be empty")
    var userIp: String? = null

    @field:NotEmpty(message = "User agent must not be empty")
    var userAgent: String? = null
}
