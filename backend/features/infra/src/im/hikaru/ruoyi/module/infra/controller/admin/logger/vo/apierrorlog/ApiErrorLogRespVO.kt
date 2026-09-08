package im.hikaru.ruoyi.module.infra.controller.admin.logger.vo.apierrorlog

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "管理后台 - API 错误日志 Response VO")
class ApiErrorLogRespVO {
    @Schema(description = "编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    var id: Long? = null
    @Schema(description = "链路追踪编号", example = "66600cb6-7852-11ec-aa3e-0242ac110032")
    var traceId: String? = null
    @Schema(description = "用户编号", example = "666")
    var userId: Long? = null
    @Schema(description = "用户类型", example = "1")
    var userType: Int? = null
    @Schema(description = "应用名", requiredMode = Schema.RequiredMode.REQUIRED, example = "dashboard")
    var applicationName: String? = null
    @Schema(description = "请求方法名", requiredMode = Schema.RequiredMode.REQUIRED, example = "GET")
    var requestMethod: String? = null
    @Schema(description = "请求地址", requiredMode = Schema.RequiredMode.REQUIRED, example = "/xx/yy")
    var requestUrl: String? = null
    @Schema(description = "请求参数", example = "{}")
    var requestParams: String? = null
    @Schema(description = "用户 IP", requiredMode = Schema.RequiredMode.REQUIRED, example = "127.0.0.1")
    var userIp: String? = null
    @Schema(description = "浏览器 UA", requiredMode = Schema.RequiredMode.REQUIRED, example = "Mozilla")
    var userAgent: String? = null
    @Schema(description = "异常时间", requiredMode = Schema.RequiredMode.REQUIRED)
    var exceptionTime: LocalDateTime? = null
    @Schema(description = "异常名", requiredMode = Schema.RequiredMode.REQUIRED)
    var exceptionName: String? = null
    @Schema(description = "异常导致的消息")
    var exceptionMessage: String? = null
    @Schema(description = "异常导致的根消息")
    var exceptionRootCauseMessage: String? = null
    @Schema(description = "异常的栈轨迹")
    var exceptionStackTrace: String? = null
    @Schema(description = "异常发生的类全名")
    var exceptionClassName: String? = null
    @Schema(description = "异常发生的类文件")
    var exceptionFileName: String? = null
    @Schema(description = "异常发生的方法名")
    var exceptionMethodName: String? = null
    @Schema(description = "异常发生的方法所在行")
    var exceptionLineNumber: Int? = null
    @Schema(description = "处理状态", requiredMode = Schema.RequiredMode.REQUIRED, example = "0")
    var processStatus: Int? = null
    @Schema(description = "处理时间")
    var processTime: LocalDateTime? = null
    @Schema(description = "处理用户编号")
    var processUserId: Long? = null
    @Schema(description = "创建时间")
    var createTime: LocalDateTime? = null
}
