package im.hikaru.ruoyi.framework.common.biz.infra.logger.dto

import jakarta.validation.constraints.NotNull
import java.io.Serializable
import java.time.LocalDateTime

/**
 * API 访问日志 (迁移自 Java, 去 Lombok)
 *
 * @author 芋道源码
 */
class ApiAccessLogCreateReqDTO : Serializable {
    var traceId: String? = null
    var userId: Long? = null
    var userType: Int? = null

    @field:NotNull(message = "应用名不能为空")
    var applicationName: String? = null

    @field:NotNull(message = "http 请求方法不能为空")
    var requestMethod: String? = null

    @field:NotNull(message = "访问地址不能为空")
    var requestUrl: String? = null

    var requestParams: String? = null
    var responseBody: String? = null

    @field:NotNull(message = "ip 不能为空")
    var userIp: String? = null

    @field:NotNull(message = "User-Agent 不能为空")
    var userAgent: String? = null

    var operateModule: String? = null
    var operateName: String? = null
    var operateType: Int? = null

    @field:NotNull(message = "开始请求时间不能为空")
    var beginTime: LocalDateTime? = null

    @field:NotNull(message = "结束请求时间不能为空")
    var endTime: LocalDateTime? = null

    @field:NotNull(message = "执行时长不能为空")
    var duration: Int? = null

    @field:NotNull(message = "错误码不能为空")
    var resultCode: Int? = null

    var resultMsg: String? = null
}
