package im.hikaru.ruoyi.module.infra.controller.admin.job.vo.log

import im.hikaru.ruoyi.framework.common.pojo.PageParam
import io.swagger.v3.oas.annotations.media.Schema
import org.springframework.format.annotation.DateTimeFormat
import java.time.LocalDateTime

@Schema(description = "管理后台 - 定时任务日志分页 Request VO")
class JobLogPageReqVO : PageParam() {
    @Schema(description = "任务编号", example = "10")
    var jobId: Long? = null

    @Schema(description = "处理器的名字，模糊匹配")
    var handlerName: String? = null

    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    @Schema(description = "开始执行时间")
    var beginTime: LocalDateTime? = null

    @DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    @Schema(description = "结束执行时间")
    var endTime: LocalDateTime? = null

    @Schema(description = "任务状态，参见 JobLogStatusEnum 枚举")
    var status: Int? = null

    companion object {
        const val FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND = "yyyy-MM-dd HH:mm:ss"
    }
}
