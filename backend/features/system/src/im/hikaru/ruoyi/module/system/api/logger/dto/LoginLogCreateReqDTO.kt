package im.hikaru.ruoyi.module.system.api.logger.dto

import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull

class LoginLogCreateReqDTO {
    @field:NotNull(message = "Log type must not be null")
    var logType: Int? = null
    var traceId: String? = null
    var userId: Long? = null

    @field:NotNull(message = "User type must not be null")
    var userType: Int? = null
    var username: String? = null

    @field:NotNull(message = "Login result must not be null")
    var result: Int? = null

    @field:NotEmpty(message = "User IP must not be empty")
    var userIp: String? = null
    var userAgent: String? = null
}
