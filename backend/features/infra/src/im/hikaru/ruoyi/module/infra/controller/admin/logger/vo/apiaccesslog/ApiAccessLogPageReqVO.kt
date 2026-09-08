package im.hikaru.ruoyi.module.infra.controller.admin.logger.vo.apiaccesslog

import im.hikaru.ruoyi.framework.common.pojo.PageParam
import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.format.annotation.DateTimeFormat
import java.time.LocalDateTime

@Schema(description = "管理后台 - API 访问日志分页 Request VO")
class ApiAccessLogPageReqVO : PageParam() {
    @Schema(description = "用户编号", example = "666")
    var userId: Long? = null
    @Schema(description = "用户类型", example = "1")
    var userType: Int? = null
    @Schema(description = "应用名", example = "dashboard")
    var applicationName: String? = null
    @Schema(description = "请求地址", example = "/xx/yy")
    var requestUrl: String? = null
    @Schema(description = "请求时间")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    var beginTime: Array<LocalDateTime>? = null
    @Schema(description = "执行时长", example = "100")
    var duration: Int? = null
    @Schema(description = "结果码", example = "0")
    var resultCode: Int? = null
}
