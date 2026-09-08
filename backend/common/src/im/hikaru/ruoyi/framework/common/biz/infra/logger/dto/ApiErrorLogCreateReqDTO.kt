package im.hikaru.ruoyi.framework.common.biz.infra.logger.dto

import jakarta.validation.constraints.NotNull
import java.io.Serializable
import java.time.LocalDateTime

/**
 * API 错误日志 (迁移自 Java, 去 Lombok)
 *
 * @author 芋道源码
 */
class ApiErrorLogCreateReqDTO : Serializable {
    /** 链路编号 */
    var traceId: String? = null

    /** 账号编号 */
    var userId: Long? = null

    /** 用户类型 */
    var userType: Int? = null

    /** 应用名 */
    @field:NotNull(message = "应用名不能为空")
    var applicationName: String? = null

    /** 请求方法名 */
    @field:NotNull(message = "http 请求方法不能为空")
    var requestMethod: String? = null

    /** 访问地址 */
    @field:NotNull(message = "访问地址不能为空")
    var requestUrl: String? = null

    /** 请求参数 */
    @field:NotNull(message = "请求参数不能为空")
    var requestParams: String? = null

    /** 用户 IP */
    @field:NotNull(message = "ip 不能为空")
    var userIp: String? = null

    /** 浏览器 UA */
    @field:NotNull(message = "User-Agent 不能为空")
    var userAgent: String? = null

    /** 异常时间 */
    @field:NotNull(message = "异常时间不能为空")
    var exceptionTime: LocalDateTime? = null

    /** 异常名 */
    @field:NotNull(message = "异常名不能为空")
    var exceptionName: String? = null

    /** 异常发生的类全名 */
    @field:NotNull(message = "异常发生的类全名不能为空")
    var exceptionClassName: String? = null

    /** 异常发生的类文件 */
    @field:NotNull(message = "异常发生的类文件不能为空")
    var exceptionFileName: String? = null

    /** 异常发生的方法名 */
    @field:NotNull(message = "异常发生的方法名不能为空")
    var exceptionMethodName: String? = null

    /** 异常发生的方法所在行 */
    @field:NotNull(message = "异常发生的方法所在行不能为空")
    var exceptionLineNumber: Int? = null

    /** 异常的栈轨迹 */
    @field:NotNull(message = "异常的栈轨迹不能为空")
    var exceptionStackTrace: String? = null

    /** 异常导致的根消息 */
    @field:NotNull(message = "异常导致的根消息不能为空")
    var exceptionRootCauseMessage: String? = null

    /** 异常导致的消息 */
    @field:NotNull(message = "异常导致的消息不能为空")
    var exceptionMessage: String? = null
}
