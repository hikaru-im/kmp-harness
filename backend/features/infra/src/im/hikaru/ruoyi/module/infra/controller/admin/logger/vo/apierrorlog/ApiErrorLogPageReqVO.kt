package im.hikaru.ruoyi.module.infra.controller.admin.logger.vo.apierrorlog

import im.hikaru.ruoyi.framework.common.pojo.PageParam
import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.format.annotation.DateTimeFormat
import java.time.LocalDateTime

@Schema(description = "管理后台 - API 错误日志分页 Request VO")
class ApiErrorLogPageReqVO : PageParam() {
    @Schema(description = "用户编号", example = "666")
    var userId: Long? = null
    @Schema(description = "用户类型", example = "1")
    var userType: Int? = null
    @Schema(description = "应用名", example = "dashboard")
    var applicationName: String? = null
    @Schema(description = "请求地址", example = "/xx/yy")
    var requestUrl: String? = null
    @Schema(description = "异常时间")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    var exceptionTime: Array<LocalDateTime>? = null
    @Schema(description = "处理状态", example = "0")
    var processStatus: Int? = null
}
