package im.hikaru.ruoyi.module.mp.controller.admin.statistics.vo

import im.hikaru.ruoyi.framework.common.util.date.DateUtils.FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import java.time.LocalDateTime
import org.springframework.format.annotation.DateTimeFormat

@Schema(description = "管理后台 - 获得统计数据 Request VO")
class MpStatisticsGetReqVO {
    @field:Schema(description = "公众号账号的编号", requiredMode = Schema.RequiredMode.REQUIRED, example = "1024")
    @field:NotNull(message = "公众号账号的编号不能为空")
    var accountId: Long? = null
    @field:Schema(description = "查询时间范围", example = "[2022-07-01 00:00:00, 2022-07-01 23:59:59]")
    @field:DateTimeFormat(pattern = FORMAT_YEAR_MONTH_DAY_HOUR_MINUTE_SECOND)
    @field:NotNull(message = "查询时间范围不能为空")
    var date: Array<LocalDateTime>? = null
}
