package im.hikaru.ruoyi.module.infra.controller.admin.logger.vo.apiaccesslog

import io.swagger.v3.oas.annotations.media.Schema
import java.time.LocalDateTime

@Schema(description = "管理后台 - API 访问日志 Response VO")
class ApiAccessLogRespVO {
    @Schema(description = "日志编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
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
    @Schema(description = "响应结果", example = "{}")
    var responseBody: String? = null
    @Schema(description = "用户 IP", requiredMode = Schema.RequiredMode.REQUIRED, example = "127.0.0.1")
    var userIp: String? = null
    @Schema(description = "浏览器 UA", requiredMode = Schema.RequiredMode.REQUIRED, example = "Mozilla")
    var userAgent: String? = null
    @Schema(description = "操作模块", example = "订单")
    var operateModule: String? = null
    @Schema(description = "操作名", example = "创建订单")
    var operateName: String? = null
    @Schema(description = "操作分类", example = "1")
    var operateType: Int? = null
    @Schema(description = "开始请求时间", requiredMode = Schema.RequiredMode.REQUIRED)
    var beginTime: LocalDateTime? = null
    @Schema(description = "结束请求时间", requiredMode = Schema.RequiredMode.REQUIRED)
    var endTime: LocalDateTime? = null
    @Schema(description = "执行时长", requiredMode = Schema.RequiredMode.REQUIRED, example = "100")
    var duration: Int? = null
    @Schema(description = "结果码", requiredMode = Schema.RequiredMode.REQUIRED)
    var resultCode: Int? = null
    @Schema(description = "结果提示", example = " Payload 参数缺失!")
    var resultMsg: String? = null
    @Schema(description = "创建时间")
    var createTime: LocalDateTime? = null
}
